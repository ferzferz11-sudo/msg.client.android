package lavender.client.android.data.grpc

import android.content.Context
import android.util.Log
import com.google.protobuf.Timestamp
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
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
import lavender.client.android.data.models.Reaction
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.models.AIChatInfo
import lavender.client.android.data.proto.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    READY,
    RECONNECTING,  // Transient state: channel lost, auto-retry in progress
    FAILED
}

object RealGrpcClient {
    private const val TAG = "RealGrpcClient"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    // channel is now managed by GrpcConnectionManager
    
    // ====== StateFlow declarations (must come before module initialization) ======
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    private var isRetrying = false
    val messages: StateFlow<List<Message>> = _messages

    private val _users = MutableStateFlow<List<String>>(emptyList())
    val users: StateFlow<List<String>> = _users

    private val _allUsers = MutableStateFlow<List<UserInfoProto>>(emptyList())
    val allUsers: StateFlow<List<UserInfoProto>> = _allUsers

    private val _serverTime = MutableStateFlow<Timestamp?>(null)
    val serverTime: StateFlow<Timestamp?> = _serverTime

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

    /** Emitted when a new message arrives for a non-active chat (roomId, messageId). */
    private val _newMessageEvent = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 64)
    val newMessageEvent: SharedFlow<Pair<String, String>> = _newMessageEvent

    var hasCheckedForUpdates = false
    var isAppInBackground = false
        set(value) {
            field = value
            if (value) {
                backgroundStartTime = System.currentTimeMillis()
            }
        }
    private var backgroundStartTime: Long = 0

    // ====== Avatar cache (must be declared before module initialization) ======
    private val avatarCache = mutableMapOf<String, String>()
    private val fullAvatarCache = mutableMapOf<String, String>()
    val avatarCacheFlow = MutableStateFlow<Map<String, String>>(emptyMap())

    private val deletedMessageHashes = mutableSetOf<String>()
    private val pendingReads = mutableSetOf<String>()

    private val connectionManager = GrpcConnectionManager(
        scope = scope,
        connectionStatus = _connectionStatus,
        onFetchServerInfo = { host: String, httpPort: Int, ctx: Context ->
            scope.launch {
                ProfileClient.fetchServerInfo(ctx, host, httpPort, currentServerPort)
            }
        },
        onAutoResumeChat = {
            lastChatRequest?.let {
                Log.d(TAG, "Resuming last chat for ${it.u}")
                startChat(it.u, it.p, it.j, it.r, it.did, it.dn, it.cb)
            }
        }
    )

    // Proxy channel access to connection manager
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
        onCallStreamError = { /* handled by callClient internally */ }
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

    private var database: AppDatabase? = null
    private fun db() = database ?: appContext?.let { 
        val d = AppDatabase.getDatabase(it)
        database = d
        d
    }
    private var requestObserver: StreamObserver<MessageProto>? = null
    // typingRequestObserver and callRequestObserver are now owned by typingClient and callClient
    
    // currentServerAddress and currentServerPort are now proxied to connectionManager (lines 298-303)
    var currentRoomId = ""
        internal set

    /** Tracks whether the last Chat stream used JWT auth. Used for fallback to password on auth failure. */
    private var lastAuthWasJwt: Boolean = false



    fun fetchServersList(context: android.content.Context, cb: (List<ServerInfoProto>) -> Unit) {
        val bootstrapHost = "13.140.25.249"
        val bootstrapPort = 50051
        val savedAddress = lavender.client.android.data.session.CredentialStore.getServerAddress(context)
        val (host, port) = if (savedAddress.isNotEmpty()) {
            val parts = savedAddress.split(":")
            Pair(parts[0], parts.getOrNull(1)?.toIntOrNull() ?: bootstrapPort)
        } else {
            Pair(bootstrapHost, bootstrapPort)
        }
        fetchServersFromHost(host, port, cb)
    }

    private fun fetchServersFromHost(host: String, port: Int, cb: (List<ServerInfoProto>) -> Unit) {
        val tempChannel = io.grpc.okhttp.OkHttpChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .build()
        try {
            val methodDesc = io.grpc.MethodDescriptor.newBuilder<com.google.protobuf.ByteString, com.google.protobuf.ByteString>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ServerService/ListServers")
                .setRequestMarshaller(object : io.grpc.MethodDescriptor.Marshaller<com.google.protobuf.ByteString> {
                    override fun stream(value: com.google.protobuf.ByteString) = value.newInput()
                    override fun parse(stream: java.io.InputStream) = com.google.protobuf.ByteString.readFrom(stream)
                })
                .setResponseMarshaller(object : io.grpc.MethodDescriptor.Marshaller<com.google.protobuf.ByteString> {
                    override fun stream(value: com.google.protobuf.ByteString) = value.newInput()
                    override fun parse(stream: java.io.InputStream) = com.google.protobuf.ByteString.readFrom(stream)
                })
                .build()

            val call = tempChannel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
            val responseHolder = mutableListOf<com.google.protobuf.ByteString>()

            call.start(object : io.grpc.ClientCall.Listener<com.google.protobuf.ByteString>() {
                override fun onMessage(message: com.google.protobuf.ByteString) { responseHolder.add(message) }
                override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                    try { tempChannel.shutdownNow() } catch (_: Exception) {}
                    if (!status.isOk || responseHolder.isEmpty()) { cb(emptyList()); return }
                    try { cb(parseServerList(responseHolder[0])) } catch (e: Exception) { cb(emptyList()) }
                }
            }, io.grpc.Metadata())
            call.sendMessage(com.google.protobuf.ByteString.EMPTY)
            call.halfClose()
            call.request(1)
        } catch (e: Exception) {
            try { tempChannel.shutdownNow() } catch (_: Exception) {}
            cb(emptyList())
        }
    }

    // ======= Proto parser for ServerInfo =======

    private fun parseServerList(data: com.google.protobuf.ByteString): List<ServerInfoProto> {
        val servers = mutableListOf<ServerInfoProto>()
        val bytes = data.toByteArray()
        var i = 0
        while (i < bytes.size) {
            val (tag, newI) = readVarint(bytes, i); i = newI
            val fieldNumber = (tag ushr 3).toInt()
            val wireType = (tag and 0x7).toInt()
            if (fieldNumber == 1 && wireType == 2) {
                val (len, newI2) = readVarint(bytes, i); i = newI2
                val msgLen = len.toInt()
                val msgBytes = bytes.copyOfRange(i, i + msgLen)
                i += msgLen
                servers.add(parseServerInfo(msgBytes))
            } else {
                i = skipField(bytes, i, wireType)
            }
        }
        return servers
    }

    private fun parseServerInfo(data: ByteArray): ServerInfoProto {
        var id = ""; var name = ""; var host = ""; var port = 50051; var isDefault = false
        var i = 0
        while (i < data.size) {
            val (tag, newI) = readVarint(data, i); i = newI
            val field = (tag ushr 3).toInt()
            val wireType = (tag and 0x7).toInt()
            when {
                field == 1 && wireType == 2 -> { val (len, p) = readVarint(data, i); i = p; id = String(data, i, len.toInt()); i += len.toInt() }
                field == 2 && wireType == 2 -> { val (len, p) = readVarint(data, i); i = p; name = String(data, i, len.toInt()); i += len.toInt() }
                field == 3 && wireType == 2 -> { val (len, p) = readVarint(data, i); i = p; host = String(data, i, len.toInt()); i += len.toInt() }
                field == 4 && wireType == 0 -> { val (v, p) = readVarint(data, i); i = p; port = v.toInt() }
                field == 5 && wireType == 0 -> { val (v, p) = readVarint(data, i); i = p; isDefault = v != 0L }
                else -> i = skipField(data, i, wireType)
            }
        }
        return ServerInfoProto(id = id, name = name, host = host, port = port, isDefault = isDefault)
    }

    private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int> {
        var value = 0L; var shift = 0; var i = start
        while (i < data.size) {
            val b = data[i].toInt() and 0xFF
            value = value or ((b and 0x7F).toLong() shl shift)
            i++
            if (b and 0x80 == 0) break
            shift += 7
        }
        return Pair(value, i)
    }

    private fun skipField(data: ByteArray, start: Int, wireType: Int): Int {
        var i = start
        when (wireType) {
            0 -> { while (i < data.size && (data[i].toInt() and 0x80) != 0) i++; i++ }
            1 -> i += 8
            2 -> { val (len, p) = readVarint(data, i); i = p + len.toInt() }
            5 -> i += 4
        }
        return i
    }
    
    
    fun shouldForceReconnect(): Boolean {
        // Force reconnect if app was in background for more than 5 minutes
        return isAppInBackground && (System.currentTimeMillis() - backgroundStartTime) > 5 * 60 * 1000
    }

    private var appContext: Context? = null
    private var currentUsername: String? = null
    private var currentUserId: String? = null
    


    fun connect(serverAddress: String, useTls: Boolean = false, port: Int = 50051, context: Context? = null, forceReconnect: Boolean = false) {
        // Delegate channel management to GrpcConnectionManager
        appContext = context?.applicationContext
        _isSuperAdmin.value = false
        loadDeletedMessages()
        connectionManager.connect(serverAddress, useTls, port, context, forceReconnect)
    }

    // Legacy fields — proxied to connectionManager
    var currentServerAddress: String? = null
        get() = connectionManager.currentServerAddress
        private set
    var currentServerPort: Int = 50051
        get() = connectionManager.currentServerPort
        private set

    /**
     * Returns gRPC Metadata with JWT Bearer token if available.
     * Used by all authenticated unary calls.
     */
    private fun getAuthMetadata(context: Context? = appContext): io.grpc.Metadata {
        val metadata = io.grpc.Metadata()
        if (context != null) {
            val bearerToken = AuthManager.getBearerToken(context)
            if (bearerToken != null) {
                metadata.put(
                    io.grpc.Metadata.Key.of("authorization", io.grpc.Metadata.ASCII_STRING_MARSHALLER),
                    bearerToken
                )
            }
        }
        return metadata
    }

    fun reconnect() {
        connectionManager.reconnect()
    }

    fun disconnect() {
        connectionManager.disconnect()
        requestObserver = null
        typingClient.clearTypingObserver()
        callClient.clearCallObserver()
        lastChatRequest = null
    }

    private var lastChatRequest: LastChatRequest? = null
    private data class LastChatRequest(val u: String, val p: String, val j: String, val roomId: String, val r: Boolean, val did: String, val dn: String, val cb: (Message) -> Unit)

    fun startChat(username: String, password: String, joinMessage: String, register: Boolean = false, deviceId: String = "", deviceName: String = "", onMessageReceived: (Message) -> Unit) {
        val oldRequest = lastChatRequest
        lastChatRequest = LastChatRequest(username, password, joinMessage, currentRoomId, register, deviceId, deviceName, onMessageReceived)
        
        // If connection is FAILED or DISCONNECTED, we must allow restart regardless of current observer
        val shouldRestart = _connectionStatus.value != ConnectionStatus.READY || requestObserver == null
        
        if (!shouldRestart && oldRequest != null && oldRequest.u == username && oldRequest.r == register) {
            // Logic to prevent unnecessary stream restarts:
            // 1. If we are on a specific room and the new request is for the "general" list (empty roomId), just stay on the specific room.
            //    The stream is already authenticated and receiving all global events.
            if (currentRoomId.isEmpty() && oldRequest.roomId.isNotEmpty()) {
                Log.d(TAG, "Staying on active room stream (${oldRequest.roomId}) instead of restarting for general list")
                return
            }

            // 2. If the room is the same, definitely skip.
            if (oldRequest.roomId == currentRoomId) {
                Log.d(TAG, "Chat stream already active for $username in $currentRoomId, skipping restart")
                return
            }
            
            // 3. If we are switching to a DIFFERENT room, send the switch signal to the existing stream.
            Log.d(TAG, "Switching room signal to existing stream: $currentRoomId")
            val switchMessage = MessageProto.newBuilder()
                .setUser(username)
                .setRoomId(currentRoomId)
                .setCreatedAt(ProtoUtils.getCurrentTimestamp())
                .setClientVersion(BuildConfig.VERSION_NAME)
                .setDeviceId(deviceId)
                .setDeviceName(deviceName)
                .build()
            try {
                requestObserver?.onNext(switchMessage)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send switch room signal, will restart stream", e)
                requestObserver = null
                // fall through to full restart
            }
        }

        if (_connectionStatus.value == ConnectionStatus.FAILED || _connectionStatus.value == ConnectionStatus.DISCONNECTED) {
            // If we are in retry loop, stay in FAILED status until we actually succeed
            // this prevents flickering "Connecting" -> "Failed" -> "Connecting"
            if (!isRetrying) {
                _connectionStatus.value = ConnectionStatus.CONNECTING
            }
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
        
        // IMPORTANT: We only complete the previous stream if we are actually replacing it
        try {
            requestObserver?.onCompleted()
        } catch (_: Exception) {
            // Ignore if stream was already closed
        }
        requestObserver = null

        // Local check for super admin (server still sends SET_SUPER_ADMIN for verification)
        if (username == "ferz") {
            _isSuperAdmin.value = true
        }

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<MessageProto, MessageProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
            .setFullMethodName("messenger.ChatService/Chat")
            .setRequestMarshaller(MessageProtoMarshaller())
            .setResponseMarshaller(MessageProtoMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        
        requestObserver = call.startChatStream(onMessageReceived)

        // Build first message: v2 uses jwt_token, v1 uses password
        val firstMessageBuilder = MessageProto.newBuilder()
            .setUser(username)
            .setText(joinMessage)
            .setRoomId(currentRoomId)
            .setCreatedAt(ProtoUtils.getCurrentTimestamp())
            .setClientVersion(BuildConfig.VERSION_NAME)
            .setRegister(register)
            .setDeviceId(deviceId)
            .setDeviceName(deviceName)

        if (ProfileClient.isChatV2Supported()) {
            // ChatStream v2: use JWT token for auth
            // Use getAccessToken (not getBearerToken) — JWT field expects raw token without "Bearer " prefix
            val accessToken = AuthManager.getAccessToken(appContext ?: return)
            if (accessToken != null && accessToken.isNotEmpty()) {
                firstMessageBuilder.setJwtToken(accessToken)
                lastAuthWasJwt = true
                Log.d(TAG, "ChatStream v2: using JWT token auth for $username")
            } else {
                // No JWT token — fallback to password auth even on v2 server
                firstMessageBuilder.setPassword(password)
                lastAuthWasJwt = false
                Log.d(TAG, "ChatStream v2: no JWT token, falling back to password auth for $username")
            }
        } else {
            // ChatStream v1: use password auth
            firstMessageBuilder.setPassword(password)
            lastAuthWasJwt = false
        }

        val firstMessage = firstMessageBuilder.build()

        _authStatus.value = null // Reset auth status on new stream

        requestObserver?.onNext(firstMessage)
        
        // Only resend pending data AFTER sending authentication signal
        resendPendingMessages()
        resendPendingReads()

        startTypingStream()
    }

    fun getDevices(uid: String, cb: (List<DeviceInfoProto>) -> Unit) {
        profileClient.getDevices(uid, cb)
    }

    fun deleteDevice(uid: String, did: String, cb: (Boolean, String) -> Unit) {
        profileClient.deleteDevice(uid, did, cb)
    }

    fun deleteOtherDevices(uid: String, currentDid: String, cb: (Boolean, String) -> Unit) {
        profileClient.deleteOtherDevices(uid, currentDid, cb)
    }

    private fun io.grpc.ClientCall<MessageProto, MessageProto>.startChatStream(onMessageReceived: (Message) -> Unit): StreamObserver<MessageProto> {
        val responseObserver = object : StreamObserver<MessageProto> {
            override fun onNext(value: MessageProto) {
                // Any message from server acts as a keepalive signal
                if (_connectionStatus.value != ConnectionStatus.READY) {
                    _connectionStatus.value = ConnectionStatus.READY
                }

                // 1. Check Admin status from any message or specific signal
                if (value.isSuperAdmin || value.text == "SET_SUPER_ADMIN") {
                    if (!_isSuperAdmin.value) {
                        Log.d(TAG, "Super Admin status activated")
                        _isSuperAdmin.value = true
                    }
                }

                // 2. Handle system signals
                if (value.text == "SET_SUPER_ADMIN") return

                if (value.text == "AUTH_FAILED" || value.text == "USER_NOT_FOUND" || value.text == "REGISTRATION_SUCCESS") {
                    _authStatus.value = value.text
                    if (value.text == "AUTH_FAILED") disconnect()
                    return
                }

                // If authenticated and no call session active, start it
                if (callClient.callRequestObserver == null && currentUsername != null) {
                    startCallSession()
                }
                
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
                    val currentDeviceId = lastChatRequest?.did ?: ""
                    if (deviceToDisconnect == currentDeviceId) {
                        _authStatus.value = "FORCE_LOGOUT"
                        disconnect()
                    }
                    return
                }

                if (value.text.startsWith("FORCE_LOGOUT_EXCEPT:")) {
                    val deviceToKeep = value.text.removePrefix("FORCE_LOGOUT_EXCEPT:")
                    val currentDeviceId = lastChatRequest?.did ?: ""
                    if (deviceToKeep != currentDeviceId) {
                        _authStatus.value = "FORCE_LOGOUT"
                        disconnect()
                    }
                    return
                }

                if (value.text.startsWith("DELETE_MESSAGE:")) {
                    val deletedId = value.text.removePrefix("DELETE_MESSAGE:")
                    deletedMessageHashes.add("id:$deletedId")

                    // Always remove from persistent cache regardless of current room
                    scope.launch(Dispatchers.IO) {
                        db()?.messageDao()?.deleteMessage(deletedId)
                    }

                    // If this is the current room, also remove from memory state
                    if (value.roomId.isEmpty() || value.roomId == currentRoomId) {
                        _messages.update { current -> current.filterNot { it.id == deletedId } }
                    }
                    return
                }

                if (value.text.startsWith("READ_ALL:")) {
                    val reader = value.text.removePrefix("READ_ALL:")
                    val targetRoomId = if (value.roomId.isNotEmpty()) value.roomId else currentRoomId
                    Log.d(TAG, "Received READ_ALL signal from $reader for room $targetRoomId (current: $currentRoomId)")
                    
                    if (targetRoomId == currentRoomId) {
                        // Update memory state for the active room
                        _messages.update { current ->
                            if (current.all { it.isRead }) current 
                            else current.map { it.copy(isRead = true) }
                        }
                    }

                    // Always sync to local cache regardless of current room
                    if (targetRoomId.isNotEmpty()) {
                        scope.launch(Dispatchers.IO) {
                            db()?.messageDao()?.markRoomAsRead(targetRoomId)
                        }
                    }
                    return
                }

                if (value.text.startsWith("CLEAR_CACHE:")) {
                    val chatId = value.text.removePrefix("CLEAR_CACHE:")
                    // Clear local cache for this chat
                    scope.launch(Dispatchers.IO) {
                        db()?.messageDao()?.clearRoom(chatId)
                        db()?.chatDao()?.deleteChat(chatId)
                        // If this is the current room, also clear memory state
                        if (chatId == currentRoomId) {
                            _messages.update { emptyList() }
                        }
                    }
                    return
                }

                if (value.text.startsWith("CHAT_DELETED:")) {
                    val chatId = value.text.removePrefix("CHAT_DELETED:")
                    // Notify that the chat was deleted
                    _chatDeletedEvent.value = chatId
                    return
                }

                if (value.text.startsWith("ONLINE_USERS_UPDATE:")) {
                    try {
                        val usersJson = value.text.removePrefix("ONLINE_USERS_UPDATE:")
                        val jsonArray = org.json.JSONArray(usersJson)
                        val userList = mutableListOf<String>()
                        for (i in 0 until jsonArray.length()) {
                            userList.add(jsonArray.getString(i))
                        }
                        _users.value = userList
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing online users update", e)
                    }
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
                        val decryptedMsg = message.copy(text = decrypted, isE2EE = false, e2eePayload = "")
                        onMessageReceived(decryptedMsg)
                    } else {
                        // Decryption failed — show placeholder
                        val errorMsg = message.copy(text = "🔒 Encrypted message", isE2EE = false, e2eePayload = "")
                        onMessageReceived(errorMsg)
                    }
                    return
                }
                
                // Only process messages for the current room
                // For Favorites virtual room, we also accept any message that the server explicitly sent to us
                // during this session (server broadcasts starred messages).
                val isFavoriteSession = currentRoomId.startsWith("favorites_")
                
                if (message.roomId != currentRoomId && !isFavoriteSession) {
                    // Still cache background messages
                    scope.launch(Dispatchers.IO) {
                        db()?.messageDao()?.insertMessages(listOf(message.toEntity()))
                    }
                    // Notify chat list to increment unread count for this room
                    scope.launch {
                        _newMessageEvent.emit(Pair(message.roomId, message.id))
                    }
                    return
                }

                onMessageReceived(message)
                var msgToCache = message
                _messages.update { current ->
                    val hash = getMessageHash(message)
                    val dedupHash = getMessageHashForDedup(message)
                    val list = current.toMutableList()
                    
                    // First check by ID hash
                    var index = list.indexOfFirst { getMessageHash(it) == hash }
                    
                    // If not found by ID, check by content hash (for deduplication of echoed messages)
                    if (index == -1) {
                        index = list.indexOfFirst { getMessageHashForDedup(it) == dedupHash }
                    }
                    
                    if (index != -1) {
                        // Merge local state with incoming from server
                        // For example, if we marked it as read locally, don't revert it
                        val existing = list[index]
                        val merged = message.copy(isRead = existing.isRead || message.isRead)
                        
                        // If timestamp changed (e.g., due to server correction), reposition the message
                        if (existing.timestamp != merged.timestamp) {
                            list.removeAt(index)
                            val insertIndex = list.indexOfFirst { it.timestamp > merged.timestamp }
                            if (insertIndex == -1) {
                                list.add(merged) // Message is newest, add to end
                            } else {
                                list.add(insertIndex, merged) // Insert at correct position
                            }
                        } else {
                            list[index] = merged
                        }
                        msgToCache = merged
                        list
                    } else {
                        // Insert new message in correct position without re-sorting entire list
                        val insertIndex = list.indexOfFirst { it.timestamp > message.timestamp }
                        if (insertIndex == -1) {
                            list.add(message) // Message is newest, add to end
                        } else {
                            list.add(insertIndex, message) // Insert at correct position
                        }
                        list
                    }
                }
                
                // Save to cache
                scope.launch(Dispatchers.IO) {
                    db()?.messageDao()?.insertMessages(listOf(msgToCache.toEntity()))
                }
            }

            override fun onError(t: Throwable) {
                Log.e(TAG, "Chat stream error", t)

                // Do not retry on authentication errors
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
                    // JWT auth failure — if we used JWT and have a password, retry with password
                    if (description.contains("authentication failed", ignoreCase = true) ||
                        description.contains("JWT validation failed", ignoreCase = true) ||
                        description.contains("token is malformed", ignoreCase = true) ||
                        description.contains("token is expired", ignoreCase = true)) {

                        if (lastAuthWasJwt) {
                            // JWT failed — clear tokens and retry with password if available
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

                        // No JWT fallback available — mark as auth failure
                        Log.w(TAG, "Auth failure — not retrying: $description")
                        _authStatus.value = "AUTH_FAILED"
                        _connectionStatus.value = ConnectionStatus.FAILED
                        requestObserver = null
                        return
                    }
                    // Do not retry on shutdownNow (our own reconnect)
                    if (description.contains("shutdownNow")) {
                        Log.d(TAG, "shutdownNow — scheduling stream restart via onAutoResumeChat")
                        requestObserver = null
                        // Force restart the chat stream after channel rebuild.
                        // onAutoResumeChat was called synchronously in connect(), but the
                        // old observer may still be non-null (race condition). Schedule a
                        // delayed restart to ensure the stream is reconnected.
                        scope.launch {
                            delay(2000)
                            Log.d(TAG, "shutdownNow: forcing chat stream restart")
                            lastChatRequest?.let { req ->
                                // Force restart: clear observer so startChat doesn't skip
                                requestObserver = null
                                startChat(req.u, req.p, req.j, req.r, req.did, req.dn, req.cb)
                            }
                        }
                        return
                    }
                }

                // Already retrying? Don't start another loop.
                if (isRetrying) {
                    Log.d(TAG, "Already in retry loop, skipping")
                    return
                }

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

                            // Don't retry if app is in background for too long
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
                    } finally {
                        isRetrying = false
                    }
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
                // If we get headers back, the stream is essentially established
                _connectionStatus.value = ConnectionStatus.READY
            }
            override fun onMessage(message: MessageProto) {
                // If we receive ANY message, we are definitely READY
                if (_connectionStatus.value != ConnectionStatus.READY) {
                    _connectionStatus.value = ConnectionStatus.READY
                }
                responseObserver.onNext(message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (status.isOk) {
                    Log.d(TAG, "Chat stream completed normally")
                    responseObserver.onCompleted()
                    return
                }
                // Log the error
                Log.w(TAG, "Chat stream onClose error: ${status.code} - ${status.description}")
                // Clear observer to prevent broken stream reuse
                requestObserver = null
                // Let onError handle all reconnect logic — don't duplicate it here
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

    fun addLocalMessage(message: Message) {
        // Persist local message so it's not lost on app restart
        scope.launch(Dispatchers.IO) {
            db()?.messageDao()?.insertMessages(listOf(message.toEntity()))
        }

        _messages.update { current ->
            val list = current.toMutableList()
            // Remove any existing message with same hash to avoid duplicates
            val existingIndex = list.indexOfFirst { getMessageHash(it) == getMessageHash(message) }
            if (existingIndex != -1) {
                val existing = list[existingIndex]
                // If timestamp changed (e.g., due to server correction), reposition the message
                if (existing.timestamp != message.timestamp) {
                    list.removeAt(existingIndex)
                    val insertIndex = list.indexOfFirst { it.timestamp > message.timestamp }
                    if (insertIndex == -1) {
                        list.add(message) // Message is newest, add to end
                    } else {
                        list.add(insertIndex, message) // Insert at correct position
                    }
                } else {
                    list[existingIndex] = message
                }
            } else {
                // Insert new message in correct position without re-sorting entire list
                val insertIndex = list.indexOfFirst { it.timestamp > message.timestamp }
                if (insertIndex == -1) {
                    list.add(message) // Message is newest, add to end
                } else {
                    list.add(insertIndex, message) // Insert at correct position
                }
            }
            list
        }
    }

    fun sendMessage(message: Message) {
        val observer = requestObserver
        if (observer == null) {
            Log.e(TAG, "Cannot send message: requestObserver is null. Message is already saved locally and will be resent on reconnection.")
            return
        }
        
        try {
            val proto = ProtoUtils.createMessageProto(message)
            observer.onNext(proto)
            Log.d(TAG, "Message sent via stream: ${message.text.take(20)}...")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            _error.value = "Failed to send message: ${e.message}"
        }
    }

    private fun resendPendingMessages() {
        scope.launch(Dispatchers.IO) {
            val pending = db()?.messageDao()?.getPendingMessages() ?: emptyList()
            if (pending.isNotEmpty()) {
                Log.d(TAG, "Resending ${pending.size} pending messages")
                pending.forEach { entity ->
                    sendMessage(entity.toDomain())
                }
            }
        }
    }

    private fun startTypingStream() {
        typingClient.startTypingStream()
    }

    fun sendTypingSignal(username: String, isTyping: Boolean) {
        typingClient.sendTypingSignal(username, isTyping, currentRoomId, currentUserId ?: "")
    }

    fun startCallSession() {
        callClient.startCallSession()
    }

    fun sendCallSignal(signal: CallMessageProto) {
        callClient.sendCallSignal(signal)
    }

    fun loadHistory(roomId: String, onCompletion: () -> Unit = {}) {
        // First, load from cache
        scope.launch(Dispatchers.IO) {
            val cached = db()?.messageDao()?.getMessagesForRoom(roomId)?.map { it.toDomain() } ?: emptyList()
            if (cached.isNotEmpty() && _messages.value.isEmpty()) {
                _messages.update { cached }
                Log.d(TAG, "Loaded ${cached.size} messages from cache for $roomId")
            }
        }

        val currentChannel = getChannel() ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetHistoryRequestProto, GetHistoryResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetHistory")
            .setRequestMarshaller(GetHistoryRequestMarshaller())
            .setResponseMarshaller(GetHistoryResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetHistoryResponseProto>() {
            override fun onMessage(message: GetHistoryResponseProto) {
                val history = message.messages.map { ProtoUtils.createMessageFromProto(it) }
                    .filterNot { deletedMessageHashes.contains(getMessageHash(it)) }

                _messages.update { current ->
                    // Merge incoming history with current state to preserve optimistic UI updates
                    val currentMap = current.associateBy { getMessageHash(it) }
                    val mergedHistory = history.map { serverMsg ->
                        val localMsg = currentMap[getMessageHash(serverMsg)]
                        if (localMsg != null) {
                            // Preserve local read status if it's already read, but also accept server's read status
                            serverMsg.copy(isRead = localMsg.isRead || serverMsg.isRead)
                        } else {
                            serverMsg
                        }
                    }
                    
                    // Use mergedHistory to update existing messages and keep current (optimistic) ones that aren't on server yet
                    val historyHashes = mergedHistory.map { getMessageHash(it) }.toSet()
                    val optimisticOnly = current.filterNot { getMessageHash(it) in historyHashes }
                    
                    (mergedHistory + optimisticOnly).sortedBy { it.timestamp }
                }
                
                // Save to cache
                scope.launch(Dispatchers.IO) {
                    val toCache = _messages.value.filter { it.roomId == roomId || (roomId.startsWith("favorites_") && it.roomId.startsWith("favorites_")) }
                    if (toCache.isNotEmpty()) {
                        db()?.messageDao()?.insertMessages(toCache.map { it.toEntity() })
                    }
                }
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { onCompletion() }
        }, io.grpc.Metadata())
        call.sendMessage(GetHistoryRequestProto(limit = 100, room = roomId))
        call.halfClose()
        call.request(1)
    }

    fun getChats(username: String, skipCache: Boolean = false, callback: (List<ChatInfo>) -> Unit) {
        chatListClient.getChats(username, skipCache, callback)
    }

    fun setRoomId(roomId: String) {
        currentRoomId = roomId
        _messages.value = emptyList()
    }

    fun clearMessages() { _messages.value = emptyList() }

    fun registerToken(user: String, token: String, pushEnabled: Boolean) {
        chatListClient.registerToken(user, token, pushEnabled)
    }

    // ======= AuthService V2 Methods (JWT) =======

    /**
     * SignInV2 — authenticates via AuthService v2 with JWT tokens.
     * Returns AuthResponseV2Proto with access_token + refresh_token.
     * Passes device info for server-side device management.
     */
    fun signInV2(
        username: String,
        password: String,
        deviceId: String,
        deviceName: String,
        deviceType: String = "android",
        clientVersion: String = "",
        callback: (AuthResponseV2Proto?, String?) -> Unit
    ) = authClient.signInV2(username, password, deviceId, deviceName, deviceType, clientVersion, callback)

    /** SignUpV2 — delegates to GrpcAuthClient */
    fun signUpV2(
        username: String,
        password: String,
        email: String,
        deviceId: String,
        deviceName: String,
        deviceType: String = "android",
        clientVersion: String = "",
        callback: (AuthResponseV2Proto?, String?) -> Unit
    ) = authClient.signUpV2(username, password, email, deviceId, deviceName, deviceType, clientVersion, callback)

    /** RefreshToken — delegates to GrpcAuthClient */
    fun refreshToken(
        refreshToken: String,
        callback: (RefreshTokenResponseProto?, String?) -> Unit
    ) = authClient.refreshToken(refreshToken, callback)

    /** SignOut — delegates to GrpcAuthClient */
    fun signOut(
        refreshToken: String = "",
        allDevices: Boolean = false,
        callback: (Boolean, String) -> Unit
    ) = authClient.signOut(refreshToken, allDevices, callback)

    /** RevokeDevice — delegates to GrpcAuthClient */
    fun revokeDevice(
        deviceId: String,
        callback: (Boolean, String) -> Unit
    ) = authClient.revokeDevice(deviceId, callback)

    fun saveDraft(roomId: String, text: String, replyId: String, replyUser: String, replyText: String, callback: (Boolean, String) -> Unit) {
        draftClient.saveDraft(roomId, text, replyId, replyUser, replyText, callback)
    }

    fun getDraft(roomId: String, callback: (String, String, String, String, Boolean) -> Unit) {
        draftClient.getDraft(roomId, callback)
    }

    fun deleteDraft(roomId: String, callback: (Boolean) -> Unit) {
        draftClient.deleteDraft(roomId, callback)
    }

    fun getMutedChats(callback: (List<String>) -> Unit) {
        chatListClient.getMutedChats(callback)
    }

    fun setMutedChat(roomId: String, muted: Boolean, callback: (Boolean) -> Unit) {
        chatListClient.setMutedChat(roomId, muted, callback)
    }

    fun addFavorite(userId: String, messageId: String, callback: (Boolean, String) -> Unit) {
        favoritesClient.addFavorite(userId, messageId, callback)
    }

    fun removeFavorite(userId: String, messageId: String, callback: (Boolean) -> Unit) {
        favoritesClient.removeFavorite(userId, messageId, callback)
    }

    fun getFavorites(userId: String, callback: (List<Message>) -> Unit) {
        favoritesClient.getFavorites(userId, callback)
    }

    fun saveFavoriteMessage(message: Message, callback: (Boolean, String) -> Unit) {
        favoritesClient.saveFavoriteMessage(message, callback)
    }

    fun fetchUserId(username: String, callback: (String?, Boolean) -> Unit) {
        chatListClient.fetchUserId(username, callback)
    }

    fun setUserId(userId: String) { currentUserId = userId }
    fun getUserId(): String? = currentUserId
    fun getCurrentUsername(): String? = currentUsername

    private fun getMessageHash(message: Message): String = if (message.id.isNotEmpty()) "id:${message.id}" else "${message.user}:${message.text}:${message.timestamp / 1000}"

    private fun getMessageHashForDedup(message: Message): String = "${message.user}:${message.text}:${message.timestamp / 1000}"

    private fun loadDeletedMessages() {
        appContext?.getSharedPreferences("deleted_messages", Context.MODE_PRIVATE)?.let { prefs ->
            deletedMessageHashes.addAll(prefs.getStringSet("hashes", emptySet()) ?: emptySet())
        }
    }

    fun editMessage(id: String, text: String, cb: (Boolean, String) -> Unit) {
        // Delegated to chat message client (kept inline for now — needs message cache access)
        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<EditMessageRequestProto, EditMessageResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/EditMessage")
                .setRequestMarshaller(EditMessageRequestMarshaller())
                .setResponseMarshaller(EditMessageResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<EditMessageResponseProto>() {
            override fun onMessage(message: EditMessageResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(EditMessageRequestProto(id, text))
        call.halfClose()
        call.request(1)
    }

    fun updateAvatar(username: String, avatarUrl: String, fullAvatarUrl: String, callback: (Boolean, String) -> Unit) {
        profileClient.updateAvatar(username, avatarUrl, fullAvatarUrl, callback)
    }

    fun getUserAvatar(username: String, userId: String = "", callback: (String) -> Unit) {
        profileClient.getUserAvatar(username, userId, callback)
    }

    fun updateProfile(username: String, bio: String, status: String, callback: (Boolean, String) -> Unit) {
        profileClient.updateProfile(username, bio, status, callback)
    }

    fun getUserProfile(userId: String, callback: (GetUserProfileResponseProto?) -> Unit) {
        profileClient.getUserProfile(userId, callback)
    }

    fun deleteMessage(m: Message) {
        // Optimistic UI: remove locally first
        deletedMessageHashes.add(getMessageHash(m))
        _messages.update { current -> current.filterNot { it.id == m.id } }
        scope.launch(Dispatchers.IO) { db()?.messageDao()?.deleteMessage(m.id) }

        val currentChannel = getChannel() ?: return
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<DeleteMessagesRequestProto, DeleteMessagesResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/DeleteMessages")
                .setRequestMarshaller(DeleteMessagesRequestMarshaller())
                .setResponseMarshaller(DeleteMessagesResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<DeleteMessagesResponseProto>() {}, io.grpc.Metadata())
        call.sendMessage(DeleteMessagesRequestProto(listOf(ProtoUtils.createMessageProto(m)), currentUsername ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun deleteProfile(u: String, cb: (Boolean, String) -> Unit) {
        profileClient.deleteProfile(u, cb)
    }

    fun clearSystemNotification() { _systemNotification.value = null }

    fun updateUsername(ou: String, nu: String, cb: (Boolean, String) -> Unit) {
        profileClient.updateUsername(ou, nu, cb)
    }

    fun updatePassword(u: String, op: String, np: String, cb: (Boolean, String) -> Unit) {
        profileClient.updatePassword(u, op, np, cb)
    }

    fun adminUpdatePassword(tu: String, np: String, au: String, cb: (Boolean, String) -> Unit) {
        profileClient.adminUpdatePassword(tu, np, au, cb)
    }

    fun requestPasswordReset(email: String, cb: (Boolean, String) -> Unit) {
        profileClient.requestPasswordReset(email, cb)
    }

    fun resetPassword(token: String, newPw: String, cb: (Boolean, String) -> Unit) {
        profileClient.resetPassword(token, newPw, cb)
    }

    fun markRead(rid: String, u: String, onComp: (() -> Unit)?) {
        appContext?.let { lavender.client.android.data.fcm.LavenderMessagingService.dismissNotificationsForRoom(it, rid) }
        val currentChannel = getChannel()
        if (currentChannel == null || _connectionStatus.value != ConnectionStatus.READY) {
            Log.d(TAG, "Queueing markRead for $rid because getChannel() is not ready")
            pendingReads.add(rid)
            onComp?.invoke()
            return
        }
        val call = currentChannel.newCall(
            io.grpc.MethodDescriptor.newBuilder<MarkReadRequestProto, MarkReadResponseProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("messenger.ChatService/MarkRead")
                .setRequestMarshaller(MarkReadRequestMarshaller())
                .setResponseMarshaller(MarkReadResponseMarshaller())
                .build(),
            io.grpc.CallOptions.DEFAULT
        )
        call.start(object : io.grpc.ClientCall.Listener<MarkReadResponseProto>() {
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (status.isOk) pendingReads.remove(rid) else pendingReads.add(rid)
                onComp?.invoke()
            }
        }, io.grpc.Metadata())
        call.sendMessage(MarkReadRequestProto(rid, u, currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    private fun resendPendingReads() {
        val username = currentUsername ?: return
        val rooms = pendingReads.toList()
        if (rooms.isEmpty()) return
        
        Log.d(TAG, "Resending ${rooms.size} pending read signals")
        rooms.forEach { rid ->
            markRead(rid, username, null)
        }
    }

    fun deleteChat(cid: String, requesterUsername: String, cb: (Boolean, String) -> Unit) {
        chatListClient.deleteChat(cid, requesterUsername, cb)
    }

    fun deleteChatWithUserId(cid: String, userId: String, username: String, cb: (Boolean, String) -> Unit) {
        chatListClient.deleteChatWithUserId(cid, userId, username, cb)
    }

    fun createDirectChat(u1: String, u2: String, cb: (String?) -> Unit) {
        chatListClient.createDirectChat(u1, u2, cb)
    }

    fun createGroupChat(n: String, ps: List<String>, c: String, type: String = "group", cb: (String?) -> Unit) {
        chatListClient.createGroupChat(n, ps, c, type, cb)
    }

    fun updateChatAvatar(cid: String, a: String, u: String, fa: String, cb: (Boolean, String) -> Unit) {
        chatListClient.updateChatAvatar(cid, a, u, fa, cb)
    }

    fun updateChatSettings(chatId: String, allowAdd: Boolean, callback: (Boolean, String) -> Unit) {
        chatListClient.updateChatSettings(chatId, allowAdd, callback)
    }

    fun updateChatName(cid: String, n: String, cb: (Boolean, String) -> Unit) {
        chatListClient.updateChatName(cid, n, cb)
    }

    fun addParticipants(cid: String, us: List<String>, cb: (Boolean, String) -> Unit) {
        chatListClient.addParticipants(cid, us, cb)
    }

    fun addParticipant(cid: String, u: String, cb: (Boolean, String) -> Unit) {
        chatListClient.addParticipant(cid, u, cb)
    }

    fun removeParticipant(cid: String, u: String, cb: (Boolean, String) -> Unit) {
        chatListClient.removeParticipant(cid, u, cb)
    }

    fun addContact(u: String, cu: String, cb: (Boolean, String) -> Unit) {
        profileClient.addContact(u, cu, cb)
    }

    fun removeContact(u: String, cu: String, cb: (Boolean, String) -> Unit) {
        profileClient.removeContact(u, cu, cb)
    }

    fun getContacts(u: String, cb: (List<String>) -> Unit) {
        profileClient.getContacts(u, cb)
    }

    fun loadAllUsers(cb: (List<UserInfoProto>) -> Unit) {
        chatListClient.loadAllUsers(cb)
    }

    fun getAllChats(callback: (List<ChatInfo>) -> Unit) {
        chatListClient.getAllChats(callback)
    }

    fun getAIChats(userId: String, callback: (List<AIChatInfo>) -> Unit) {
        chatListClient.getAIChats(userId, callback)
    }

    fun renameAIChat(chatId: String, userId: String, newName: String, callback: (Boolean, String) -> Unit) {
        chatListClient.renameAIChat(chatId, userId, newName, callback)
    }

    fun getChatListVersion(u: String, cb: (Long) -> Unit) {
        chatListClient.getChatListVersion(u, cb)
    }

    fun getThemes(u: String, cb: (String, List<CustomThemeProto>) -> Unit) {
        profileClient.getThemes(u, cb)
    }

    fun saveTheme(u: String, t: CustomThemeProto, cb: (Boolean, String) -> Unit) {
        profileClient.saveTheme(u, t, cb)
    }

    fun setCurrentTheme(u: String, tid: String, cb: (Boolean) -> Unit) {
        profileClient.setCurrentTheme(u, tid, cb)
    }

    fun deleteTheme(u: String, tid: String, cb: (Boolean) -> Unit) {
        profileClient.deleteTheme(u, tid, cb)
    }

    fun getFCMLogs(cb: (List<FCMLogEntryProto>) -> Unit) {
        profileClient.getFCMLogs(cb)
    }

    // ======= FCM Logs (kept for reference) =======
    private fun _getFCMLogsInline(cb: (List<FCMLogEntryProto>) -> Unit) {
        val currentChannel = getChannel() ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetFCMLogsRequestProto, GetFCMLogsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetFCMLogs")
            .setRequestMarshaller(GetFCMLogsRequestMarshaller())
            .setResponseMarshaller(GetFCMLogsResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetFCMLogsResponseProto>() {
            override fun onMessage(message: GetFCMLogsResponseProto) { cb(message.logs) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetFCMLogsRequestProto())
        call.halfClose()
        call.request(1)
    }

    fun setReaction(messageId: String, username: String, emoji: String) {
        val currentChannel = getChannel() ?: return
        
        // Optimistic UI: update locally first
        _messages.update { current ->
            val list = current.toMutableList()
            val index = list.indexOfFirst { it.id == messageId }
            if (index != -1) {
                val msg = list[index]
                val newReactions = msg.reactions.toMutableList()
                newReactions.removeAll { it.user == username }
                newReactions.add(Reaction(username, emoji))
                val newMsg = msg.copy(reactions = newReactions)
                list[index] = newMsg

                // Save optimistic update to local cache
                scope.launch(Dispatchers.IO) {
                    db()?.messageDao()?.insertMessages(listOf(newMsg.toEntity()))
                }

                list
            } else current
        }

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<ReactionRequestProto, ReactionResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SetReaction")
            .setRequestMarshaller(ReactionRequestMarshaller())
            .setResponseMarshaller(ReactionResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<ReactionResponseProto>() {}, io.grpc.Metadata())
        call.sendMessage(ReactionRequestProto(messageId, ReactionProto(username, emoji)))
        call.halfClose()
        call.request(1)
    }

    fun loadUsers() { loadAllUsers {} }
    fun updateMessage(m: Message) {} // Local update mostly

    fun getAvatarCache(): Map<String, String> = avatarCache.toMap()
    fun getFullAvatarCache(): Map<String, String> = fullAvatarCache.toMap()
    fun getFullAvatarUrl(u: String): String? = fullAvatarCache[u]
    fun updateAvatarCache(u: String, a: String, fa: String) {
        avatarCache[u] = a
        if (fa.isNotEmpty()) fullAvatarCache[u] = fa
        avatarCacheFlow.value = avatarCache.toMap()
    }

    // ======= ChatList v2: PinChat / UnPinChat =======

    suspend fun pinChat(chatId: String): Boolean {
        val userId = currentUserId ?: return false
        return unaryCallChatListV2(
            fullMethod = "messenger.ChatService/PinChat",
            request = PinChatRequestProto(userId = userId, chatId = chatId),
            responseType = PinChatResponseProto::class.java
        )?.success ?: false
    }

    suspend fun unpinChat(chatId: String): Boolean {
        val userId = currentUserId ?: return false
        return unaryCallChatListV2(
            fullMethod = "messenger.ChatService/UnPinChat",
            request = UnPinChatRequestProto(userId = userId, chatId = chatId),
            responseType = UnPinChatResponseProto::class.java
        )?.success ?: false
    }

    suspend fun searchChats(query: String, limit: Int, offset: Int): List<ChatInfo> {
        val userId = currentUserId ?: return emptyList()
        val response = unaryCallChatListV2(
            fullMethod = "messenger.ChatService/SearchChats",
            request = SearchChatsRequestProto(userId = userId, query = query, limit = limit, offset = offset),
            responseType = SearchChatsResponseProto::class.java
        )
        return response?.chats?.map { proto ->
            ChatInfo(
                id = proto.id,
                name = proto.name,
                type = proto.type,
                participants = proto.participants,
                createdAt = proto.createdAt?.seconds ?: 0L,
                unreadCount = proto.unreadCount,
                lastMessageTime = proto.lastMessageTime?.seconds ?: 0L,
                creator = proto.creator,
                lastMessageText = proto.lastMessageText,
                avatarUrl = proto.avatarUrl,
                fullAvatarUrl = proto.fullAvatarUrl,
                lastMessageUsername = proto.lastMessageUsername,
                lastMessageHasImage = proto.lastMessageHasImage,
                allowMembersToAdd = proto.allowMembersToAdd,
                isPinned = proto.isPinned,
                isMuted = proto.isMuted,
                isArchived = proto.isArchived,
                pinnedAt = proto.pinnedAt
            )
        } ?: emptyList()
    }

    suspend fun archiveChat(chatId: String): Boolean {
        val userId = currentUserId ?: return false
        return unaryCallChatListV2(
            fullMethod = "messenger.ChatService/ArchiveChat",
            request = ArchiveChatRequestProto(userId = userId, chatId = chatId),
            responseType = ArchiveChatResponseProto::class.java
        )?.success ?: false
    }

    suspend fun unarchiveChat(chatId: String): Boolean {
        val userId = currentUserId ?: return false
        return unaryCallChatListV2(
            fullMethod = "messenger.ChatService/UnarchiveChat",
            request = UnarchiveChatRequestProto(userId = userId, chatId = chatId),
            responseType = UnarchiveChatResponseProto::class.java
        )?.success ?: false
    }

    // ======= Pin Message =======

    suspend fun pinMessage(chatId: String, messageId: String): Boolean {
        val userId = currentUserId ?: return false
        return unaryCallChatListV2(
            fullMethod = "messenger.ChatService/PinMessage",
            request = PinMessageRequestProto(userId = userId, chatId = chatId, messageId = messageId),
            responseType = PinMessageResponseProto::class.java
        )?.success ?: false
    }

    suspend fun unpinMessage(chatId: String, messageId: String): Boolean {
        val userId = currentUserId ?: return false
        return unaryCallChatListV2(
            fullMethod = "messenger.ChatService/UnPinMessage",
            request = UnPinMessageRequestProto(userId = userId, chatId = chatId, messageId = messageId),
            responseType = UnPinMessageResponseProto::class.java
        )?.success ?: false
    }

    suspend fun getPinnedMessages(chatId: String): List<Message> {
        val userId = currentUserId ?: return emptyList()
        val response = unaryCallChatListV2(
            fullMethod = "messenger.ChatService/GetPinnedMessages",
            request = GetPinnedMessagesRequestProto(userId = userId, chatId = chatId),
            responseType = GetPinnedMessagesResponseProto::class.java
        )
        return response?.messages?.map { proto ->
            Message(
                id = proto.id,
                user = proto.user,
                text = proto.text,
                timestamp = proto.createdAt?.seconds ?: 0L
            )
        } ?: emptyList()
    }

    // ======= ChatList v2: Low-level unary call helper =======

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private suspend fun <ReqT, RespT> unaryCallChatListV2(
        fullMethod: String,
        request: ReqT,
        responseType: Class<RespT>
    ): RespT? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val ch = getChannel()
        if (ch == null) {
            cont.resume(null, onCancellation = {})
            return@suspendCancellableCoroutine
        }
        val method = io.grpc.MethodDescriptor.newBuilder<ReqT, RespT>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(fullMethod)
            .setRequestMarshaller(object : io.grpc.MethodDescriptor.Marshaller<ReqT> {
                override fun stream(value: ReqT): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))
                override fun parse(stream: java.io.InputStream): ReqT = request
            })
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
                if (status.isOk) {
                    cont.resume(response, onCancellation = {})
                } else {
                    Log.w("RealGrpcClient", "ChatList V2 call failed: ${status.code}")
                    cont.resume(null, onCancellation = {})
                }
            }
        }
        call.start(listener, io.grpc.Metadata())
        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }
}

// MARSHALLERS — moved to GrpcMarshallers.kt (v1.1.3.27)
