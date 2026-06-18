package lavender.client.android.data.grpc

import android.content.Context
import android.util.Log
import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import lavender.client.android.BuildConfig
import lavender.client.android.data.auth.AuthManager
import lavender.client.android.data.db.*
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.models.AIChatInfo
import lavender.client.android.data.models.ErrorHandler
import lavender.client.android.data.proto.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    READY,
    RECONNECTING,
    FAILED
}

/**
 * Thin orchestrator for gRPC client modules.
 *
 * Owns: StateFlow declarations, module initialization, chat stream lifecycle.
 * Delegates: all domain operations to specialized modules.
 *
 * Target: ~200 lines (down from 3810).
 */
object RealGrpcClient {
    private const val TAG = "RealGrpcClient"
    internal val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ====== StateFlow declarations (must come before module initialization) ======
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _users = MutableStateFlow<List<String>>(emptyList())
    val users: StateFlow<List<String>> = _users

    private val _allUsers = MutableStateFlow<List<UserInfoProto>>(emptyList())
    val allUsers: StateFlow<List<UserInfoProto>> = _allUsers

    private val _serverTime = MutableStateFlow<com.google.protobuf.Timestamp?>(null)
    val serverTime: StateFlow<com.google.protobuf.Timestamp?> = _serverTime

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val _systemNotification = MutableStateFlow<String?>(null)
    val systemNotification: StateFlow<String?> = _systemNotification

    val _isSuperAdmin = MutableStateFlow(false)
    val isSuperAdmin: StateFlow<Boolean> = _isSuperAdmin

    private val _serverVersion = MutableStateFlow("")
    val serverVersion: StateFlow<String> = _serverVersion

    private val _authStatus = MutableStateFlow<String?>(null)
    val authStatus: StateFlow<String?> = _authStatus

    private val _typingUsers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val typingUsers: StateFlow<Map<String, Set<String>>> = _typingUsers

    private val _chatDeletedEvent = MutableStateFlow<String?>(null)
    val chatDeletedEvent: StateFlow<String?> = _chatDeletedEvent

    private val _callSignals = MutableSharedFlow<CallMessageProto>(extraBufferCapacity = 64)
    val callSignals: SharedFlow<CallMessageProto> = _callSignals

    private val _newMessageEvent = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 64)
    val newMessageEvent: SharedFlow<Pair<String, String>> = _newMessageEvent

    // Read receipts broadcast: roomId → reader username
    private val _readReceiptEvent = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 64)
    val readReceiptEvent: SharedFlow<Pair<String, String>> = _readReceiptEvent

    var hasCheckedForUpdates = false
    var isAppInBackground = false
        set(value) {
            field = value
            if (value) backgroundStartTime = System.currentTimeMillis()
        }
    private var backgroundStartTime: Long = 0

    // ====== Avatar cache (must be declared before module initialization) ======
    private val avatarCache = mutableMapOf<String, String>()
    private val fullAvatarCache = mutableMapOf<String, String>()
    val avatarCacheFlow = MutableStateFlow<Map<String, String>>(emptyMap())

    private val deletedMessageHashes = mutableSetOf<String>()
    private val pendingReads = mutableSetOf<String>()

    // ====== Module: Connection Manager ======
    private val connectionManager = GrpcConnectionManager(
        scope = scope,
        connectionStatus = _connectionStatus,
        onFetchServerInfo = { host: String, httpPort: Int, ctx: Context ->
            scope.launch { ProfileClient.fetchServerInfo(ctx, host, httpPort, currentServerPort) }
        },
        onAutoResumeChat = {
            lastChatRequest?.let {
                Log.d(TAG, "Resuming last chat for ${it.u}")
                startChat(it.u, it.p, it.j, it.r, it.did, it.dn, it.cb)
            }
        }
    )

    fun getChannel(): ManagedChannel? = connectionManager.channel

    // ====== Module: Auth Client ======
    private val authClient = GrpcAuthClient(
        getChannel = { getChannel() },
        connectionStatus = _connectionStatus,
        authStatus = _authStatus,
        setAuthFailure = { connectionManager.isAuthFailure = it }
    )

    // ====== Module: Typing Client ======
    private val typingClient = GrpcTypingClient(
        getChannel = { getChannel() },
        typingUsers = _typingUsers,
        scope = scope
    )

    // ====== Module: Call Client ======
    private val callClient = GrpcCallClient(
        getChannel = { getChannel() },
        getUsername = { currentUsername },
        getUserId = { currentUserId },
        callSignals = _callSignals,
        connectionStatus = _connectionStatus,
        requestObserverRef = { requestObserver },
        scope = scope,
        onCallStreamError = { }
    )

    // ====== Module: Chat List Client ======
    private val chatListClient = GrpcChatListClient(
        getChannel = { getChannel() },
        getUserId = { currentUserId },
        getUsername = { currentUsername },
        chatDeletedEvent = _chatDeletedEvent,
        allUsers = _allUsers,
        serverTime = _serverTime,
        scope = scope
    )

    // ====== Module: Profile Client ======
    private val profileClient = GrpcProfileClient(
        getChannel = { getChannel() },
        getUserId = { currentUserId },
        getUsername = { currentUsername },
        avatarCache = avatarCache,
        fullAvatarCache = fullAvatarCache,
        avatarCacheFlow = avatarCacheFlow,
        scope = scope
    )

    // ====== Module: Draft Client ======
    private val draftClient = GrpcDraftClient(
        getChannel = { getChannel() },
        getUserId = { currentUserId }
    )

    // ====== Module: Favorites Client ======
    private val favoritesClient = GrpcFavoritesClient(
        getChannel = { getChannel() },
        getUserId = { currentUserId },
        getUsername = { currentUsername },
        scope = scope
    )

    // ====== Module: Message Client ======
    private val messageClient = GrpcMessageClient(
        getChannel = { getChannel() },
        getUserId = { currentUserId },
        getUsername = { currentUsername },
        messages = _messages,
        deletedMessageHashes = deletedMessageHashes,
        pendingReads = pendingReads,
        scope = scope,
        appContext = { appContext },
        onReadReceipt = { roomId, reader ->
            scope.launch { _readReceiptEvent.emit(Pair(roomId, reader)) }
        }
    )

    // ====== Module: Server Discovery Client ======
    private val serverDiscoveryClient = GrpcServerDiscoveryClient(
        getSavedServerAddress = {
            appContext?.let { lavender.client.android.data.session.CredentialStore.getServerAddress(it) }
        }
    )

    // ====== State (kept in orchestrator) ======
    private var appContext: Context? = null
    private var currentUsername: String? = null
    private var currentUserId: String? = null
    private var requestObserver: StreamObserver<MessageProto>? = null
    private var lastAuthWasJwt: Boolean = false
    private var isRetrying = false
    private var lastChatRequest: LastChatRequest? = null
    private data class LastChatRequest(
        val u: String, val p: String, val j: String, val roomId: String,
        val r: Boolean, val did: String, val dn: String, val cb: (Message) -> Unit
    )

    var currentRoomId = ""
        internal set

    // ====== Connection (delegated) ======
    var currentServerAddress: String? = null
        get() = connectionManager.currentServerAddress
        private set
    var currentServerPort: Int = 50051
        get() = connectionManager.currentServerPort
        private set

    fun connect(serverAddress: String, useTls: Boolean = false, port: Int = 50051, context: Context? = null, forceReconnect: Boolean = false) {
        appContext = context?.applicationContext
        _isSuperAdmin.value = false
        loadDeletedMessages()
        connectionManager.connect(serverAddress, useTls, port, context, forceReconnect)
    }

    fun reconnect() = connectionManager.reconnect()

    fun disconnect() {
        connectionManager.disconnect()
        requestObserver = null
        typingClient.clearTypingObserver()
        callClient.clearCallObserver()
        lastChatRequest = null
    }

    fun shouldForceReconnect(): Boolean =
        isAppInBackground && (System.currentTimeMillis() - backgroundStartTime) > 5 * 60 * 1000

    // ====== Chat Stream (core — kept in orchestrator) ======

    fun startChat(username: String, password: String, joinMessage: String, register: Boolean = false, deviceId: String = "", deviceName: String = "", onMessageReceived: (Message) -> Unit) {
        val oldRequest = lastChatRequest
        lastChatRequest = LastChatRequest(username, password, joinMessage, currentRoomId, register, deviceId, deviceName, onMessageReceived)

        val shouldRestart = _connectionStatus.value != ConnectionStatus.READY || requestObserver == null

        if (!shouldRestart && oldRequest != null && oldRequest.u == username && oldRequest.r == register) {
            if (currentRoomId.isEmpty() && oldRequest.roomId.isNotEmpty()) {
                Log.d(TAG, "Staying on active room stream (${oldRequest.roomId}) instead of restarting for general list")
                return
            }
            if (oldRequest.roomId == currentRoomId) {
                Log.d(TAG, "Chat stream already active for $username in $currentRoomId, skipping restart")
                return
            }
            Log.d(TAG, "Switching room signal to existing stream: $currentRoomId")
            val switchMessage = MessageProto.newBuilder()
                .setUser(username).setRoomId(currentRoomId)
                .setCreatedAt(ProtoUtils.getCurrentTimestamp())
                .setClientVersion(BuildConfig.VERSION_NAME)
                .setDeviceId(deviceId).setDeviceName(deviceName).build()
            try {
                requestObserver?.onNext(switchMessage)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send switch room signal, will restart stream", e)
                requestObserver = null
            }
        }

        if (_connectionStatus.value == ConnectionStatus.FAILED || _connectionStatus.value == ConnectionStatus.DISCONNECTED) {
            if (!isRetrying) _connectionStatus.value = ConnectionStatus.CONNECTING
        }

        val currentChannel = getChannel()
        if (currentChannel == null || currentChannel.isShutdown || currentChannel.isTerminated) {
            val addr = currentServerAddress
            if (!addr.isNullOrEmpty()) {
                Log.w(TAG, "Channel is not available, attempting reconnect to $addr")
                connect(addr)
            } else {
                Log.e(TAG, "Cannot start chat: getChannel() and server address are null")
            }
            return
        }

        currentUsername = username
        if (username == "ferz") _isSuperAdmin.value = true

        try { requestObserver?.onCompleted() } catch (_: Exception) {}
        requestObserver = null

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<MessageProto, MessageProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
            .setFullMethodName("messenger.ChatService/Chat")
            .setRequestMarshaller(MessageProtoMarshaller())
            .setResponseMarshaller(MessageProtoMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        requestObserver = call.startChatStream(onMessageReceived)

        val firstMessageBuilder = MessageProto.newBuilder()
            .setUser(username).setText(joinMessage).setRoomId(currentRoomId)
            .setCreatedAt(ProtoUtils.getCurrentTimestamp())
            .setClientVersion(BuildConfig.VERSION_NAME)
            .setRegister(register).setDeviceId(deviceId).setDeviceName(deviceName)

        if (ProfileClient.isChatV2Supported()) {
            val accessToken = AuthManager.getAccessToken(appContext ?: return)
            if (!accessToken.isNullOrEmpty()) {
                firstMessageBuilder.setJwtToken(accessToken)
                lastAuthWasJwt = true
                Log.d(TAG, "ChatStream v2: using JWT token auth for $username")
            } else {
                firstMessageBuilder.setPassword(password)
                lastAuthWasJwt = false
                Log.d(TAG, "ChatStream v2: no JWT token, falling back to password auth for $username")
            }
        } else {
            firstMessageBuilder.setPassword(password)
            lastAuthWasJwt = false
        }

        _authStatus.value = null
        requestObserver?.onNext(firstMessageBuilder.build())

        // Resend pending data AFTER sending authentication signal
        messageClient.resendPendingMessages({ requestObserver })
        messageClient.resendPendingReads(currentUsername ?: "", _connectionStatus.value)
        startTypingStream()
    }

    private fun io.grpc.ClientCall<MessageProto, MessageProto>.startChatStream(onMessageReceived: (Message) -> Unit): StreamObserver<MessageProto> {
        val responseObserver = object : StreamObserver<MessageProto> {
            override fun onNext(value: MessageProto) {
                if (_connectionStatus.value != ConnectionStatus.READY) {
                    _connectionStatus.value = ConnectionStatus.READY
                }

                if (value.isSuperAdmin || value.text == "SET_SUPER_ADMIN") {
                    if (!_isSuperAdmin.value) {
                        Log.d(TAG, "Super Admin status activated")
                        _isSuperAdmin.value = true
                    }
                }

                // System signals
                if (value.text == "SET_SUPER_ADMIN") return
                if (value.text == "AUTH_FAILED" || value.text == "USER_NOT_FOUND" || value.text == "REGISTRATION_SUCCESS") {
                    _authStatus.value = value.text
                    if (value.text == "AUTH_FAILED") disconnect()
                    return
                }
                if (callClient.callRequestObserver == null && currentUsername != null) startCallSession()
                if (value.text.startsWith("SYSTEM_NOTIFICATION:")) {
                    _systemNotification.value = value.text.removePrefix("SYSTEM_NOTIFICATION:")
                    return
                }
                if (value.text.startsWith("SERVER_INFO:")) {
                    _serverVersion.value = value.text.removePrefix("SERVER_INFO:")
                    return
                }
                if (value.text.startsWith("FORCE_DISCONNECT:")) {
                    val targetUser = value.text.removePrefix("FORCE_DISCONNECT:")
                    if (targetUser == currentUsername) disconnect()
                    return
                }
                if (value.text == "FORCE_LOGOUT") {
                    _authStatus.value = "FORCE_LOGOUT"
                    disconnect()
                    return
                }
                if (value.text.startsWith("FORCE_DISCONNECT_DEVICE:")) {
                    val deviceToDisconnect = value.text.removePrefix("FORCE_DISCONNECT_DEVICE:")
                    if (deviceToDisconnect == (lastChatRequest?.did ?: "")) {
                        _authStatus.value = "FORCE_LOGOUT"
                        disconnect()
                    }
                    return
                }
                if (value.text.startsWith("FORCE_LOGOUT_EXCEPT:")) {
                    val deviceToKeep = value.text.removePrefix("FORCE_LOGOUT_EXCEPT:")
                    if (deviceToKeep != (lastChatRequest?.did ?: "")) {
                        _authStatus.value = "FORCE_LOGOUT"
                        disconnect()
                    }
                    return
                }
                if (value.text.startsWith("DELETE_MESSAGE:")) {
                    val deletedId = value.text.removePrefix("DELETE_MESSAGE:")
                    messageClient.handleDeleteMessageSignal(deletedId)
                    if (value.roomId.isEmpty() || value.roomId == currentRoomId) {
                        _messages.update { current -> current.filterNot { it.id == deletedId } }
                    }
                    return
                }
                if (value.text.startsWith("READ_ALL:")) {
                    val reader = value.text.removePrefix("READ_ALL:")
                    val targetRoomId = if (value.roomId.isNotEmpty()) value.roomId else currentRoomId
                    messageClient.handleReadAllSignal(reader, targetRoomId, currentRoomId)
                    return
                }
                if (value.text.startsWith("CLEAR_CACHE:")) {
                    val chatId = value.text.removePrefix("CLEAR_CACHE:")
                    messageClient.handleClearCacheSignal(chatId, currentRoomId)
                    return
                }
                if (value.text.startsWith("CHAT_DELETED:")) {
                    _chatDeletedEvent.value = value.text.removePrefix("CHAT_DELETED:")
                    return
                }
                if (value.text.startsWith("ONLINE_USERS_UPDATE:")) {
                    try {
                        val usersJson = value.text.removePrefix("ONLINE_USERS_UPDATE:")
                        val jsonArray = org.json.JSONArray(usersJson)
                        val userList = mutableListOf<String>()
                        for (i in 0 until jsonArray.length()) userList.add(jsonArray.getString(i))
                        _users.value = userList
                    } catch (e: Exception) { Log.e(TAG, "Error parsing online users update", e) }
                    return
                }

                val message = ProtoUtils.createMessageFromProto(value)
                if (deletedMessageHashes.contains(getMessageHash(message))) return

                // E2EE: decrypt secret chat messages
                if (message.isE2EE && message.e2eePayload.isNotEmpty()) {
                    val decrypted = lavender.client.android.data.crypto.E2EEManager.decryptMessage(
                        appContext ?: return, message.roomId, message.e2eePayload
                    )
                    if (decrypted != null) {
                        onMessageReceived(message.copy(text = decrypted, isE2EE = false, e2eePayload = ""))
                    } else {
                        onMessageReceived(message.copy(text = "🔒 Encrypted message", isE2EE = false, e2eePayload = ""))
                    }
                    return
                }

                val isFavoriteSession = currentRoomId.startsWith("favorites_")
                if (message.roomId != currentRoomId && !isFavoriteSession) {
                    scope.launch(Dispatchers.IO) { db()?.messageDao()?.insertMessages(listOf(message.toEntity())) }
                    scope.launch { _newMessageEvent.emit(Pair(message.roomId, message.id)) }
                    return
                }

                onMessageReceived(message)
                var msgToCache = message
                _messages.update { current ->
                    val hash = getMessageHash(message)
                    val dedupHash = getMessageHashForDedup(message)
                    val list = current.toMutableList()
                    var index = list.indexOfFirst { getMessageHash(it) == hash }
                    if (index == -1) index = list.indexOfFirst { getMessageHashForDedup(it) == dedupHash }
                    if (index != -1) {
                        val existing = list[index]
                        val merged = message.copy(isRead = existing.isRead || message.isRead)
                        if (existing.timestamp != merged.timestamp) {
                            list.removeAt(index)
                            val insertIndex = list.indexOfFirst { it.timestamp > merged.timestamp }
                            if (insertIndex == -1) list.add(merged) else list.add(insertIndex, merged)
                        } else list[index] = merged
                        msgToCache = merged
                        list
                    } else {
                        val insertIndex = list.indexOfFirst { it.timestamp > message.timestamp }
                        if (insertIndex == -1) list.add(message) else list.add(insertIndex, message)
                        list
                    }
                }
                scope.launch(Dispatchers.IO) { db()?.messageDao()?.insertMessages(listOf(msgToCache.toEntity())) }
            }

            override fun onError(t: Throwable) {
                ErrorHandler.handle("RealGrpcClient.chatStream", t)
                if (t is io.grpc.StatusRuntimeException) {
                    val description = t.status.description ?: ""
                    if (description.contains("user not found", ignoreCase = true) ||
                        description.contains("auth failed", ignoreCase = true) ||
                        t.status.code == io.grpc.Status.Code.UNAUTHENTICATED) {
                        Log.w(TAG, "Authentication error, not retrying: $description")
                        _authStatus.value = if (description.contains("user not found")) "USER_NOT_FOUND" else "AUTH_FAILED"
                        _connectionStatus.value = ConnectionStatus.FAILED
                        requestObserver = null
                        return
                    }
                    if (description.contains("authentication failed", ignoreCase = true) ||
                        description.contains("JWT validation failed", ignoreCase = true) ||
                        description.contains("token is malformed", ignoreCase = true) ||
                        description.contains("token is expired", ignoreCase = true)) {
                        if (lastAuthWasJwt) {
                            Log.w(TAG, "JWT auth failed — clearing tokens, will retry with password: $description")
                            appContext?.let { AuthManager.clearTokens(it) }
                            _authStatus.value = null
                            lastAuthWasJwt = false
                            requestObserver = null
                            _connectionStatus.value = ConnectionStatus.RECONNECTING
                            scope.launch {
                                delay(1000)
                                lastChatRequest?.let { req ->
                                    Log.d(TAG, "Retrying chat stream with password auth for ${req.u}")
                                    startChat(req.u, req.p, req.j, req.r, req.did, req.dn, req.cb)
                                }
                            }
                            return
                        }
                        Log.w(TAG, "Auth failure — not retrying: $description")
                        _authStatus.value = "AUTH_FAILED"
                        _connectionStatus.value = ConnectionStatus.FAILED
                        requestObserver = null
                        return
                    }
                    if (description.contains("shutdownNow")) {
                        Log.d(TAG, "shutdownNow — scheduling stream restart via onAutoResumeChat")
                        requestObserver = null
                        scope.launch {
                            delay(2000)
                            Log.d(TAG, "shutdownNow: forcing chat stream restart")
                            lastChatRequest?.let { req ->
                                requestObserver = null
                                startChat(req.u, req.p, req.j, req.r, req.did, req.dn, req.cb)
                            }
                        }
                        return
                    }
                }

                if (isRetrying) { Log.d(TAG, "Already in retry loop, skipping"); return }

                _connectionStatus.value = ConnectionStatus.RECONNECTING
                requestObserver = null
                isRetrying = true
                scope.launch {
                    try {
                        var retryDelay = 3000L
                        val maxRetryDelay = 30000L
                        var retryCount = 0
                        val maxRetries = 50
                        while (retryCount < maxRetries && requestObserver == null) {
                            delay(retryDelay)
                            if (isAppInBackground && System.currentTimeMillis() - backgroundStartTime > 300000) {
                                Log.d(TAG, "App in background for too long, stopping stream retry loop")
                                break
                            }
                            Log.d(TAG, "Attempting stream reconnect (attempt ${retryCount + 1}, delay=${retryDelay}ms)...")
                            lastChatRequest?.let { req ->
                                startChat(req.u, req.p, req.j, req.r, req.did, req.dn, req.cb)
                                if (_connectionStatus.value == ConnectionStatus.READY && requestObserver != null) {
                                    Log.d(TAG, "Stream reconnection successful")
                                    return@launch
                                }
                            }
                            retryCount++
                            retryDelay = (retryDelay * 1.5).toLong().coerceAtMost(maxRetryDelay)
                        }
                        if (retryCount >= maxRetries) {
                            Log.e(TAG, "Failed to reconnect stream after $maxRetries attempts")
                            _connectionStatus.value = ConnectionStatus.FAILED
                            _error.value = "Connection lost. Please check your internet connection."
                        }
                    } finally { isRetrying = false }
                }
            }

            override fun onCompleted() {
                Log.d(TAG, "Chat stream completed")
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        }

        this.start(object : io.grpc.ClientCall.Listener<MessageProto>() {
            override fun onHeaders(headers: io.grpc.Metadata?) {
                super.onHeaders(headers)
                _connectionStatus.value = ConnectionStatus.READY
            }
            override fun onMessage(message: MessageProto) {
                if (_connectionStatus.value != ConnectionStatus.READY) _connectionStatus.value = ConnectionStatus.READY
                responseObserver.onNext(message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (status.isOk) {
                    Log.d(TAG, "Chat stream completed normally")
                    responseObserver.onCompleted()
                    return
                }
                Log.w(TAG, "Chat stream onClose error: ${status.code} - ${status.description}")
                requestObserver = null
                responseObserver.onError(status.asRuntimeException())
            }
        }, io.grpc.Metadata())
        this.request(Int.MAX_VALUE)

        return object : StreamObserver<MessageProto> {
            override fun onNext(value: MessageProto) = this@startChatStream.sendMessage(value)
            override fun onError(t: Throwable) = this@startChatStream.cancel("Error in request stream", t)
            override fun onCompleted() = this@startChatStream.halfClose()
        }
    }

    // ====== Typing (delegated) ======
    private fun startTypingStream() { typingClient.startTypingStream() }
    fun sendTypingSignal(username: String, isTyping: Boolean) {
        typingClient.sendTypingSignal(username, isTyping, currentRoomId, currentUserId ?: "")
    }

    // ====== Calls (delegated) ======
    fun startCallSession() { callClient.startCallSession() }
    fun sendCallSignal(signal: CallMessageProto) { callClient.sendCallSignal(signal) }

    // ====== Messages (delegated) ======
    fun sendMessage(message: Message) { messageClient.sendMessage(message, requestObserver) }
    fun addLocalMessage(message: Message) { messageClient.addLocalMessage(message) }
    fun loadHistory(roomId: String, onCompletion: () -> Unit = {}) { messageClient.loadHistory(roomId, onCompletion) }
    fun editMessage(id: String, text: String, cb: (Boolean, String) -> Unit) { messageClient.editMessage(id, text, cb) }
    fun deleteMessage(m: Message) { messageClient.deleteMessage(m, currentUsername) }
    fun setReaction(messageId: String, username: String, emoji: String) { messageClient.setReaction(messageId, username, emoji) }
    fun markRead(rid: String, u: String, onComp: (() -> Unit)?) { messageClient.markRead(rid, u, _connectionStatus.value, onComp) }

    // ====== Chat List (delegated) ======
    fun getChats(username: String, skipCache: Boolean = false, callback: (List<ChatInfo>) -> Unit) { chatListClient.getChats(username, skipCache, callback) }
    fun getAllChats(callback: (List<ChatInfo>) -> Unit) { chatListClient.getAllChats(callback) }
    fun getChatListVersion(u: String, cb: (Long) -> Unit) { chatListClient.getChatListVersion(u, cb) }
    fun registerToken(user: String, token: String, pushEnabled: Boolean) { chatListClient.registerToken(user, token, pushEnabled) }
    fun fetchUserId(username: String, callback: (String?, Boolean) -> Unit) { chatListClient.fetchUserId(username, callback) }
    fun loadAllUsers(cb: (List<UserInfoProto>) -> Unit) { chatListClient.loadAllUsers(cb) }
    fun getAIChats(userId: String, callback: (List<AIChatInfo>) -> Unit) { chatListClient.getAIChats(userId, callback) }
    fun renameAIChat(chatId: String, userId: String, newName: String, callback: (Boolean, String) -> Unit) { chatListClient.renameAIChat(chatId, userId, newName, callback) }
    fun getMutedChats(callback: (List<String>) -> Unit) { chatListClient.getMutedChats(callback) }
    fun setMutedChat(roomId: String, muted: Boolean, callback: (Boolean) -> Unit) { chatListClient.setMutedChat(roomId, muted, callback) }
    fun deleteChat(cid: String, requesterUsername: String, cb: (Boolean, String) -> Unit) { chatListClient.deleteChat(cid, requesterUsername, cb) }
    fun deleteChatWithUserId(cid: String, userId: String, username: String, cb: (Boolean, String) -> Unit) { chatListClient.deleteChatWithUserId(cid, userId, username, cb) }
    fun createDirectChat(u1: String, u2: String, cb: (String?) -> Unit) { chatListClient.createDirectChat(u1, u2, cb) }
    fun createGroupChat(n: String, ps: List<String>, c: String, type: String = "group", cb: (String?) -> Unit) { chatListClient.createGroupChat(n, ps, c, type, cb) }
    fun updateChatAvatar(cid: String, a: String, u: String, fa: String, cb: (Boolean, String) -> Unit) { chatListClient.updateChatAvatar(cid, a, u, fa, cb) }
    fun updateChatSettings(chatId: String, allowAdd: Boolean, callback: (Boolean, String) -> Unit) { chatListClient.updateChatSettings(chatId, allowAdd, callback) }
    fun updateChatName(cid: String, n: String, cb: (Boolean, String) -> Unit) { chatListClient.updateChatName(cid, n, cb) }
    fun addParticipants(cid: String, us: List<String>, cb: (Boolean, String) -> Unit) { chatListClient.addParticipants(cid, us, cb) }
    fun addParticipant(cid: String, u: String, cb: (Boolean, String) -> Unit) { chatListClient.addParticipant(cid, u, cb) }
    fun removeParticipant(cid: String, u: String, cb: (Boolean, String) -> Unit) { chatListClient.removeParticipant(cid, u, cb) }

    // ====== Profile (delegated) ======
    fun getDevices(uid: String, cb: (List<DeviceInfoProto>) -> Unit) { profileClient.getDevices(uid, cb) }
    fun deleteDevice(uid: String, did: String, cb: (Boolean, String) -> Unit) { profileClient.deleteDevice(uid, did, cb) }
    fun deleteOtherDevices(uid: String, currentDid: String, cb: (Boolean, String) -> Unit) { profileClient.deleteOtherDevices(uid, currentDid, cb) }
    fun updateAvatar(username: String, avatarUrl: String, fullAvatarUrl: String, callback: (Boolean, String) -> Unit) { profileClient.updateAvatar(username, avatarUrl, fullAvatarUrl, callback) }
    fun getUserAvatar(username: String, userId: String = "", callback: (String) -> Unit) { profileClient.getUserAvatar(username, userId, callback) }
    fun updateProfile(username: String, bio: String, status: String, callback: (Boolean, String) -> Unit) { profileClient.updateProfile(username, bio, status, callback) }
    fun getUserProfile(userId: String, callback: (GetUserProfileResponseProto?) -> Unit) { profileClient.getUserProfile(userId, callback) }
    fun deleteProfile(u: String, cb: (Boolean, String) -> Unit) { profileClient.deleteProfile(u, cb) }
    fun updateUsername(ou: String, nu: String, cb: (Boolean, String) -> Unit) { profileClient.updateUsername(ou, nu, cb) }
    fun updatePassword(u: String, op: String, np: String, cb: (Boolean, String) -> Unit) { profileClient.updatePassword(u, op, np, cb) }
    fun adminUpdatePassword(tu: String, np: String, au: String, cb: (Boolean, String) -> Unit) { profileClient.adminUpdatePassword(tu, np, au, cb) }
    fun requestPasswordReset(email: String, cb: (Boolean, String) -> Unit) { profileClient.requestPasswordReset(email, cb) }
    fun resetPassword(token: String, newPw: String, cb: (Boolean, String) -> Unit) { profileClient.resetPassword(token, newPw, cb) }
    fun addContact(u: String, cu: String, cb: (Boolean, String) -> Unit) { profileClient.addContact(u, cu, cb) }
    fun removeContact(u: String, cu: String, cb: (Boolean, String) -> Unit) { profileClient.removeContact(u, cu, cb) }
    fun getContacts(u: String, cb: (List<String>) -> Unit) { profileClient.getContacts(u, cb) }
    fun getThemes(u: String, cb: (String, List<CustomThemeProto>) -> Unit) { profileClient.getThemes(u, cb) }
    fun saveTheme(u: String, t: CustomThemeProto, cb: (Boolean, String) -> Unit) { profileClient.saveTheme(u, t, cb) }
    fun setCurrentTheme(u: String, tid: String, cb: (Boolean) -> Unit) { profileClient.setCurrentTheme(u, tid, cb) }
    fun deleteTheme(u: String, tid: String, cb: (Boolean) -> Unit) { profileClient.deleteTheme(u, tid, cb) }
    fun getFCMLogs(cb: (List<FCMLogEntryProto>) -> Unit) { profileClient.getFCMLogs(cb) }

    // ====== Drafts (delegated) ======
    fun saveDraft(roomId: String, text: String, replyId: String, replyUser: String, replyText: String, callback: (Boolean, String) -> Unit) { draftClient.saveDraft(roomId, text, replyId, replyUser, replyText, callback) }
    fun getDraft(roomId: String, callback: (String, String, String, String, Boolean) -> Unit) { draftClient.getDraft(roomId, callback) }
    fun deleteDraft(roomId: String, callback: (Boolean) -> Unit) { draftClient.deleteDraft(roomId, callback) }

    // ====== Favorites (delegated) ======
    fun addFavorite(userId: String, messageId: String, callback: (Boolean, String) -> Unit) { favoritesClient.addFavorite(userId, messageId, callback) }
    fun removeFavorite(userId: String, messageId: String, callback: (Boolean) -> Unit) { favoritesClient.removeFavorite(userId, messageId, callback) }
    fun getFavorites(userId: String, callback: (List<Message>) -> Unit) { favoritesClient.getFavorites(userId, callback) }
    fun saveFavoriteMessage(message: Message, callback: (Boolean, String) -> Unit) { favoritesClient.saveFavoriteMessage(message, callback) }

    // ====== Auth V2 (delegated) ======
    fun signInV2(username: String, password: String, deviceId: String, deviceName: String, deviceType: String = "android", clientVersion: String = "", callback: (AuthResponseV2Proto?, String?) -> Unit) = authClient.signInV2(username, password, deviceId, deviceName, deviceType, clientVersion, callback)
    fun signUpV2(username: String, password: String, email: String, deviceId: String, deviceName: String, deviceType: String = "android", clientVersion: String = "", callback: (AuthResponseV2Proto?, String?) -> Unit) = authClient.signUpV2(username, password, email, deviceId, deviceName, deviceType, clientVersion, callback)
    fun refreshToken(refreshToken: String, callback: (RefreshTokenResponseProto?, String?) -> Unit) = authClient.refreshToken(refreshToken, callback)
    fun signOut(refreshToken: String = "", allDevices: Boolean = false, callback: (Boolean, String) -> Unit) = authClient.signOut(refreshToken, allDevices, callback)
    fun revokeDevice(deviceId: String, callback: (Boolean, String) -> Unit) = authClient.revokeDevice(deviceId, callback)

    // ====== Server Discovery (delegated) ======
    fun fetchServersList(context: android.content.Context, cb: (List<ServerInfoProto>) -> Unit) {
        serverDiscoveryClient.fetchServersList(cb)
    }

    // ====== Room / Messages state ======
    fun setRoomId(roomId: String) {
        currentRoomId = roomId
        _messages.value = emptyList()
    }
    fun clearMessages() { _messages.value = emptyList() }
    fun clearSystemNotification() { _systemNotification.value = null }
    fun setUserId(userId: String) { currentUserId = userId }
    fun getUserId(): String? = currentUserId
    fun getCurrentUsername(): String? = currentUsername

    // ====== Avatar cache ======
    fun getAvatarCache(): Map<String, String> = avatarCache.toMap()
    fun getFullAvatarCache(): Map<String, String> = fullAvatarCache.toMap()
    fun getFullAvatarUrl(u: String): String? = fullAvatarCache[u]
    fun updateAvatarCache(u: String, a: String, fa: String) {
        avatarCache[u] = a
        if (fa.isNotEmpty()) fullAvatarCache[u] = fa
        avatarCacheFlow.value = avatarCache.toMap()
    }

    // ====== ChatList v2 (delegated to unaryCallChatListV2) ======
    suspend fun pinChat(chatId: String): Boolean {
        val userId = currentUserId ?: return false
        return unaryCallChatListV2(
            fullMethod = "messenger.ChatService/PinChat",
            request = PinChatRequestProto(userId = userId, chatId = chatId),
            responseType = PinChatResponseProto::class.java,
            requestMarshaller = PinChatRequestMarshaller()
        )?.success ?: false
    }

    suspend fun unpinChat(chatId: String): Boolean {
        val userId = currentUserId ?: return false
        return unaryCallChatListV2(
            fullMethod = "messenger.ChatService/UnPinChat",
            request = UnPinChatRequestProto(userId = userId, chatId = chatId),
            responseType = UnPinChatResponseProto::class.java,
            requestMarshaller = UnPinChatRequestMarshaller()
        )?.success ?: false
    }

    suspend fun searchChats(query: String, limit: Int, offset: Int): List<ChatInfo> {
        val userId = currentUserId ?: return emptyList()
        val response = unaryCallChatListV2(
            fullMethod = "messenger.ChatService/SearchChats",
            request = SearchChatsRequestProto(userId = userId, query = query, limit = limit, offset = offset),
            responseType = SearchChatsResponseProto::class.java,
            requestMarshaller = SearchChatsRequestMarshaller()
        )
        return response?.chats?.map { proto ->
            ChatInfo(
                id = proto.id, name = proto.name, type = proto.type,
                participants = proto.participants,
                createdAt = proto.createdAt?.seconds ?: 0L,
                unreadCount = proto.unreadCount,
                lastMessageTime = proto.lastMessageTime?.seconds ?: 0L,
                creator = proto.creator, lastMessageText = proto.lastMessageText,
                avatarUrl = proto.avatarUrl, fullAvatarUrl = proto.fullAvatarUrl,
                lastMessageUsername = proto.lastMessageUsername,
                lastMessageHasImage = proto.lastMessageHasImage,
                allowMembersToAdd = proto.allowMembersToAdd,
                isPinned = proto.isPinned, isMuted = proto.isMuted,
                isArchived = proto.isArchived, pinnedAt = proto.pinnedAt
            )
        } ?: emptyList()
    }

    suspend fun archiveChat(chatId: String): Boolean {
        val userId = currentUserId ?: return false
        return unaryCallChatListV2(
            fullMethod = "messenger.ChatService/ArchiveChat",
            request = ArchiveChatRequestProto(userId = userId, chatId = chatId),
            responseType = ArchiveChatResponseProto::class.java,
            requestMarshaller = ArchiveChatRequestMarshaller()
        )?.success ?: false
    }

    suspend fun unarchiveChat(chatId: String): Boolean {
        val userId = currentUserId ?: return false
        return unaryCallChatListV2(
            fullMethod = "messenger.ChatService/UnarchiveChat",
            request = UnarchiveChatRequestProto(userId = userId, chatId = chatId),
            responseType = UnarchiveChatResponseProto::class.java,
            requestMarshaller = UnarchiveChatRequestMarshaller()
        )?.success ?: false
    }

    suspend fun pinMessage(chatId: String, messageId: String): Boolean {
        val userId = currentUserId ?: return false
        return unaryCallChatListV2(
            fullMethod = "messenger.ChatService/PinMessage",
            request = PinMessageRequestProto(userId = userId, chatId = chatId, messageId = messageId),
            responseType = PinMessageResponseProto::class.java,
            requestMarshaller = PinMessageRequestMarshaller()
        )?.success ?: false
    }

    suspend fun unpinMessage(chatId: String, messageId: String): Boolean {
        val userId = currentUserId ?: return false
        return unaryCallChatListV2(
            fullMethod = "messenger.ChatService/UnPinMessage",
            request = UnPinMessageRequestProto(userId = userId, chatId = chatId, messageId = messageId),
            responseType = UnPinMessageResponseProto::class.java,
            requestMarshaller = UnPinMessageRequestMarshaller()
        )?.success ?: false
    }

    suspend fun getPinnedMessages(chatId: String): List<Message> {
        val userId = currentUserId ?: return emptyList()
        val response = unaryCallChatListV2(
            fullMethod = "messenger.ChatService/GetPinnedMessages",
            request = GetPinnedMessagesRequestProto(userId = userId, chatId = chatId),
            responseType = GetPinnedMessagesResponseProto::class.java,
            requestMarshaller = GetPinnedMessagesRequestMarshaller()
        )
        return response?.messages?.map { proto ->
            Message(id = proto.id, user = proto.user, text = proto.text, timestamp = proto.createdAt?.seconds ?: 0L)
        } ?: emptyList()
    }

    // ====== Low-level unary call helper (kept for v2 ChatList methods) ======
    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private suspend fun <ReqT, RespT> unaryCallChatListV2(
        fullMethod: String, request: ReqT, responseType: Class<RespT>,
        requestMarshaller: io.grpc.MethodDescriptor.Marshaller<ReqT>? = null
    ): RespT? = suspendCancellableCoroutine { cont ->
        val ch = getChannel()
        if (ch == null) { cont.resume(null, onCancellation = {}); return@suspendCancellableCoroutine }
        val rm = requestMarshaller ?: object : io.grpc.MethodDescriptor.Marshaller<ReqT> {
            override fun stream(value: ReqT): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))
            override fun parse(stream: java.io.InputStream): ReqT = request
        }
        val method = io.grpc.MethodDescriptor.newBuilder<ReqT, RespT>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(fullMethod)
            .setRequestMarshaller(rm)
            .setResponseMarshaller(object : io.grpc.MethodDescriptor.Marshaller<RespT> {
                override fun stream(value: RespT): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))
                @Suppress("DEPRECATION")
                override fun parse(stream: java.io.InputStream): RespT = responseType.getDeclaredConstructor().newInstance()
            })
            .build()
        val call = ch.newCall(method, io.grpc.CallOptions.DEFAULT)
        val listener = object : io.grpc.ClientCall.Listener<RespT>() {
            private var response: RespT? = null
            override fun onMessage(message: RespT) { response = message }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (status.isOk) cont.resume(response, onCancellation = {})
                else { Log.w(TAG, "ChatList V2 call failed: ${status.code}"); cont.resume(null, onCancellation = {}) }
            }
        }
        call.start(listener, io.grpc.Metadata())
        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    // ====== Helpers ======
    private fun getMessageHash(message: Message): String =
        if (message.id.isNotEmpty()) "id:${message.id}"
        else "${message.user}:${message.text}:${message.timestamp / 1000}"

    private fun getMessageHashForDedup(message: Message): String =
        "${message.user}:${message.text}:${message.timestamp / 1000}"

    private fun loadDeletedMessages() {
        appContext?.getSharedPreferences("deleted_messages", Context.MODE_PRIVATE)?.let { prefs ->
            deletedMessageHashes.addAll(prefs.getStringSet("hashes", emptySet()) ?: emptySet())
        }
    }

    private var database: AppDatabase? = null
    private fun db() = database ?: appContext?.let {
        val d = AppDatabase.getDatabase(it)
        database = d
        d
    }

    private fun getAuthMetadata(context: Context? = appContext): io.grpc.Metadata {
        val metadata = io.grpc.Metadata()
        if (context != null) {
            val bearerToken = AuthManager.getBearerToken(context)
            if (bearerToken != null) {
                metadata.put(io.grpc.Metadata.Key.of("authorization", io.grpc.Metadata.ASCII_STRING_MARSHALLER), bearerToken)
            }
        }
        return metadata
    }

    fun loadUsers() { loadAllUsers {} }
    fun updateMessage(m: Message) {} // Local update mostly
}
