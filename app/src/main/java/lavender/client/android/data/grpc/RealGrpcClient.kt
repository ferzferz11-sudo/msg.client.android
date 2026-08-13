package lavender.client.android.data.grpc

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.db.AppDatabase
import lavender.client.android.data.db.toEntity
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.models.ErrorHandler
import lavender.client.android.data.models.Message
import lavender.client.android.data.proto.AuthResponseV2Proto
import lavender.client.android.data.proto.CallMessageProto
import lavender.client.android.data.proto.ChatV2MessageProto
import lavender.client.android.data.proto.ChatV2TypingProto
import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.data.proto.DeviceInfoProto
import lavender.client.android.data.proto.FCMLogEntryProto
import lavender.client.android.data.proto.GetAdminUserListResponseProto
import lavender.client.android.data.proto.GetAdminUserSessionsResponseProto
import lavender.client.android.data.proto.GetUserProfileResponseProto
import lavender.client.android.data.proto.MarkReadRequestProto
import lavender.client.android.data.proto.MarkReadResponseProto
import lavender.client.android.data.proto.MessageProto
import lavender.client.android.data.proto.MessageV2Proto
import lavender.client.android.data.proto.RefreshTokenResponseProto
import lavender.client.android.data.proto.ServerInfoProto
import lavender.client.android.data.proto.UserInfoProto

private const val DELETED_HASHES_MAX_SIZE = 10000

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

    val _serverTime = MutableStateFlow<com.google.protobuf.Timestamp?>(null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val _systemNotification = MutableStateFlow<String?>(null)
    val systemNotification: StateFlow<String?> = _systemNotification

    val _isSuperAdmin = MutableStateFlow(false)
    val isSuperAdmin: StateFlow<Boolean> = _isSuperAdmin

    private val _adminUserId = MutableStateFlow<String?>(null)
    val adminUserId: StateFlow<String?> = _adminUserId

    private val _serverVersion = MutableStateFlow("")
    val serverVersion: StateFlow<String> = _serverVersion

    private val _authStatus = MutableStateFlow<String?>(null)
    val authStatus: StateFlow<String?> = _authStatus

    private val _serverShuttingDown = MutableStateFlow(false)
    val serverShuttingDown: StateFlow<Boolean> = _serverShuttingDown

    private val _typingUsers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val typingUsers: StateFlow<Map<String, Set<String>>> = _typingUsers

    private val _chatDeletedEvent = MutableStateFlow<String?>(null)
    val chatDeletedEvent: StateFlow<String?> = _chatDeletedEvent

    private val _selfDestructTimer = MutableStateFlow(0)
    val selfDestructTimer: StateFlow<Int> = _selfDestructTimer

    private val _callSignals = MutableSharedFlow<CallMessageProto>(extraBufferCapacity = 64)
    val callSignals: SharedFlow<CallMessageProto> = _callSignals

    private val _newMessageEvent = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val newMessageEvent: SharedFlow<Message> = _newMessageEvent

    // Read receipts broadcast: roomId → reader username
    private val _readReceiptEvent = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 64)
    val readReceiptEvent: SharedFlow<Pair<String, String>> = _readReceiptEvent

    @Volatile var hasCheckedForUpdates = false
    @Volatile var isAppInBackground = false
        set(value) {
            field = value
            if (value) backgroundStartTime = System.currentTimeMillis()
        }
    @Volatile private var backgroundStartTime: Long = 0

    // ====== Avatar cache (must be declared before module initialization) ======
    private val avatarCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val fullAvatarCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    val avatarCacheFlow = MutableStateFlow<Map<String, String>>(emptyMap())

    private val deletedMessageHashes: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val pendingReads: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private var persistDeletedHashesJob: kotlinx.coroutines.Job? = null

    private fun addDeletedHash(hash: String) {
        deletedMessageHashes.add(hash)
        if (deletedMessageHashes.size > DELETED_HASHES_MAX_SIZE) {
            val toRemove = deletedMessageHashes.take(DELETED_HASHES_MAX_SIZE / 2)
            deletedMessageHashes.removeAll(toRemove.toSet())
        }
        // Debounce SharedPreferences write (batch multiple rapid deletes)
        persistDeletedHashesJob?.cancel()
        persistDeletedHashesJob = scope.launch {
            kotlinx.coroutines.delay(500)
            appContext?.getSharedPreferences("deleted_messages", Context.MODE_PRIVATE)?.edit()
                ?.putStringSet("hashes", deletedMessageHashes.toSet())
                ?.apply()
        }
        // Persist to Room DB (extract message ID from hash)
        val messageId = hash.removePrefix("id:")
        if (messageId != hash && messageId.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                try { db()?.deletedMessageDao()?.insert(lavender.client.android.data.db.DeletedMessageEntity(messageId)) } catch (_: Exception) {}
            }
        }
    }

    // ====== Module: Connection Manager ======
    private val connectionManager = GrpcConnectionManager(
        scope = scope,
        connectionStatus = _connectionStatus,
        onFetchServerInfo = { host: String, httpPort: Int, ctx: Context ->
            scope.launch { ProfileClient.fetchServerInfo(ctx, host, httpPort) }
        },
        onAutoResumeChat = {
            if (lastChatRequest == null) restoreLastChatRequest()
            lastChatRequest?.let {
                startChatV2(it.roomId, it.cb)
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

    // ====== Module: Chat Client (core chat ops) ======
    private val chatClient = GrpcChatClient(
        getChannel = { getChannel() },
        getUserId = { currentUserId },
        getUsername = { currentUsername },
        scope = scope
    )

    // ====== Module: ChatList v2 Client ======
    private val chatListV2Client = GrpcChatListV2Client(
        getChannel = { getChannel() },
        getUserId = { currentUserId }
    )

    // ====== Module: Chat Aux Client (users/AI/FCM/mute) ======
    private val chatAuxClient = GrpcChatAuxClient(
        getChannel = { getChannel() },
        getUserId = { currentUserId },
        allUsers = _allUsers,
        serverTime = _serverTime,
        reconnect = { connectionManager.reconnect() },
        refreshToken = {
            appContext?.let { ctx ->
                lavender.client.android.data.session.SessionManager.ensureFreshToken(ctx)
            }
        }
    )

    // ====== Module: Profile Client ======
    private val profileClient = GrpcProfileClient(
        getChannel = { getChannel() },
        getUserId = { currentUserId },
        getUsername = { currentUsername },
        avatarCache = avatarCache,
        fullAvatarCache = fullAvatarCache,
        avatarCacheFlow = avatarCacheFlow,
        scope = scope,
        fetchUserId = { username, cb -> chatAuxClient.fetchUserId(username, cb) },
        setUserId = { id -> currentUserId = id }
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
        scope = scope,
        allUsers = { _allUsers.value }
    )

    // ====== Module: Message V2 Client ======
    val messageV2Client = GrpcMessageV2Client(
        getChannel = { getChannel() },
        getUserId = { currentUserId },
        getUsername = { currentUsername },
        messages = _messages,
        allUsers = { _allUsers.value },
        deletedMessageHashes = deletedMessageHashes,
        scope = scope,
        appContext = { appContext },
        onReadReceipt = { roomId, reader ->
            scope.launch { _readReceiptEvent.emit(Pair(roomId, reader)) }
        },
        reconnect = { connectionManager.reconnect() }
    )

    // ====== Module: Server Discovery Client ======
    private val serverDiscoveryClient = GrpcServerDiscoveryClient(
        getSavedServerAddress = {
            appContext?.let { lavender.client.android.data.session.CredentialStore.getServerAddress(it) }
        }
    )

    // ====== Module: AI v2 Client ======
    val aiV2Client = GrpcAIv2Client(
        getChannel = { getChannel() },
        getUserId = { currentUserId },
        scope = scope
    )

    private val METHOD_MARK_READ = MethodDescriptor.newBuilder<MarkReadRequestProto, MarkReadResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/MarkRead")
        .setRequestMarshaller(MarkReadRequestMarshaller())
        .setResponseMarshaller(MarkReadResponseMarshaller())
        .build()

    // ====== State (kept in orchestrator) ======
    @Volatile internal var appContext: Context? = null
    @Volatile private var currentUsername: String? = null
    @Volatile private var currentUserId: String? = null
    @Volatile private var requestObserver: StreamObserver<MessageProto>? = null
    private val chatV2Lock = Any()
    private var chatV2RequestObserver: StreamObserver<ChatV2MessageProto>? = null
    @Volatile private var lastAuthWasJwt: Boolean = false
    @Volatile private var lastChatRequest: LastChatRequest? = null
    private data class LastChatRequest(
        val roomId: String, val cb: (Message) -> Unit
    )

    private fun saveLastChatRequestPrefs() {
        val req = lastChatRequest ?: return
        val ctx = appContext ?: return
        ctx.getSharedPreferences("chat_keepalive", Context.MODE_PRIVATE).edit {
            putString("roomId", req.roomId)
            putBoolean("has_request", true)
        }
    }

    private fun restoreLastChatRequest(): Boolean {
        val ctx = appContext ?: return false
        val prefs = ctx.getSharedPreferences("chat_keepalive", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("has_request", false)) return false
        val roomId = prefs.getString("roomId", "") ?: ""
        if (roomId.isEmpty()) return false
        lastChatRequest = LastChatRequest(roomId = roomId, cb = {})
        Log.d(TAG, "Restored lastChatRequest from prefs: roomId=$roomId")
        return true
    }

    fun clearLastChatRequestPrefs() {
        appContext?.getSharedPreferences("chat_keepalive", Context.MODE_PRIVATE)?.edit {
            clear()
        }
        lastChatRequest = null
    }

    @Volatile var currentRoomId = ""
        internal set

    // ====== Connection (delegated) ======
    var currentServerAddress: String? = null
        get() = connectionManager.currentServerAddress
        private set
    private val currentServerPort: Int
        get() = connectionManager.currentServerPort

    fun connect(serverAddress: String, useTls: Boolean = false, port: Int = 50051, context: Context? = null, forceReconnect: Boolean = false) {
        appContext = context?.applicationContext
        if (context != null) {
            val prefs = context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
            if (_adminUserId.value == null) {
                val savedId = prefs.getString("admin_user_id", null)
                if (!savedId.isNullOrEmpty()) _adminUserId.value = savedId
            }
            if (!_isSuperAdmin.value) {
                val savedAdmin = prefs.getBoolean("is_super_admin", false)
                if (savedAdmin) _isSuperAdmin.value = true
            }
        }
        loadDeletedMessages()
        connectionManager.connect(serverAddress, useTls, port, context, forceReconnect)
    }

    fun disconnect() {
        connectionManager.disconnect()
        requestObserver = null
        synchronized(chatV2Lock) { chatV2RequestObserver = null }
        typingClient.clearTypingObserver()
        callClient.clearCallObserver()
        lastChatRequest = null
    }

    fun shouldForceReconnect(): Boolean =
        isAppInBackground && (System.currentTimeMillis() - backgroundStartTime) > 5 * 60 * 1000

    fun clearServerShuttingDown() {
        _serverShuttingDown.value = false
    }

    private fun fetchAdminStatus() {
        val ctx = appContext ?: return
        scope.launch {
            try {
                val profile = ProfileClient.getProfile(ctx)
                if (profile != null) {
                    _isSuperAdmin.value = profile.isSuperAdmin
                    ctx.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE).edit {
                        putBoolean("is_super_admin", profile.isSuperAdmin)
                        if (profile.isSuperAdmin && profile.userId.isNotEmpty()) {
                            if (_adminUserId.value == null) _adminUserId.value = profile.userId
                            putString("admin_user_id", profile.userId)
                        }
                    }
                    // Store company info in session
                    lavender.client.android.data.session.SessionManager.updateSession(
                        companyId = profile.companyId,
                        companyName = profile.companyName,
                        positionTitle = profile.positionTitle,
                        positionLevel = profile.positionLevel
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "fetchAdminStatus failed: ${e.message}")
            }
        }
    }

    private suspend fun checkServerHealth(): Boolean {
        val address = connectionManager.currentServerAddress ?: return false
        val port = connectionManager.currentServerPort
        val httpPort = if (port == 50052) 8083 else 8082
        return withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("http://$address:$httpPort/health")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                conn.disconnect()
                code == 200
            } catch (_: Exception) {
                false
            }
        }
    }

    // ====== Chat Stream V2 ======

    // ====== Typing (via ChatV2 stream) ======
    fun sendTypingSignal(username: String, isTyping: Boolean) {
        val observer = synchronized(chatV2Lock) { chatV2RequestObserver }
        if (observer == null) {
            Log.w(TAG, "sendTypingSignal: chatV2RequestObserver is null, dropping signal")
            return
        }
        Log.d(TAG, "sendTypingSignal: roomId=$currentRoomId, isTyping=$isTyping")
        observer.onNext(
            ChatV2MessageProto(
                roomId = currentRoomId,
                typing = ChatV2TypingProto(isTyping = isTyping)
            )
        )
    }

    // ====== ChatV2 Stream ======

    fun startChatV2(roomId: String, onMessageReceived: (Message) -> Unit) {
        lastChatRequest = LastChatRequest(roomId = roomId, cb = onMessageReceived)
        saveLastChatRequestPrefs()
        currentRoomId = roomId

        val currentChannel = getChannel() ?: return
        val existingObserver = synchronized(chatV2Lock) { chatV2RequestObserver }
        if (existingObserver != null) {
            val switchMsg = ChatV2MessageProto(roomId = roomId)
            try { existingObserver.onNext(switchMsg) } catch (e: Exception) { Log.e("RealGrpcClient", "Failed to send room switch message for $roomId", e) }
            return
        }

        if (_connectionStatus.value == ConnectionStatus.FAILED || _connectionStatus.value == ConnectionStatus.DISCONNECTED) {
            _connectionStatus.value = ConnectionStatus.CONNECTING
        }

        val methodDescriptor = MethodDescriptor.newBuilder<ChatV2MessageProto, ChatV2MessageProto>()
            .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
            .setFullMethodName("messenger.ChatService/ChatV2")
            .setRequestMarshaller(ChatV2MessageMarshaller())
            .setResponseMarshaller(ChatV2MessageMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, CallOptions.DEFAULT)
        val observer = call.startChatV2Stream(onMessageReceived)
        synchronized(chatV2Lock) { chatV2RequestObserver = observer }

        val ctx = appContext
        if (ctx != null) {
            lavender.client.android.data.auth.AuthManager.getAccessToken(ctx)?.let { accessToken ->
                if (accessToken.isNotEmpty()) {
                    val firstMsg = ChatV2MessageProto(jwtToken = accessToken, roomId = roomId, clientVersion = lavender.client.android.BuildConfig.VERSION_NAME)
                    observer.onNext(firstMsg)
                    _authStatus.value = null
                } else {
                    Log.w(TAG, "ChatV2: no JWT token available")
                    _authStatus.value = "AUTH_FAILED"
                }
            }
        }
    }

    private fun ClientCall<ChatV2MessageProto, ChatV2MessageProto>.startChatV2Stream(onMessageReceived: (Message) -> Unit): StreamObserver<ChatV2MessageProto> {
        val responseObserver = object : StreamObserver<ChatV2MessageProto> {
            override fun onNext(value: ChatV2MessageProto) {
                if (_connectionStatus.value != ConnectionStatus.READY) {
                    _connectionStatus.value = ConnectionStatus.READY
                    fetchAdminStatus()
                    if (callClient.callRequestObserver == null && currentUsername != null) startCallSession()
                }
                // Handle typing
                if (value.typing != null) {
                    return
                }
                // Handle system messages
                if (value.system != null) {
                    val sysType = value.system.type
                    val sysMessage = value.system.message

                    when (sysType) {
                        "DELETE_MESSAGE" -> {
                            addDeletedHash("id:$sysMessage")
                            scope.launch(Dispatchers.IO) { db()?.messageDao()?.deleteMessage(sysMessage) }
                            if (sysMessage.isNotEmpty()) {
                                _messages.update { current -> current.filterNot { it.id == sysMessage } }
                            }
                        }
                        "READ_ALL" -> {
                            val targetRoomId = value.roomId.ifEmpty { currentRoomId }
                            if (targetRoomId == currentRoomId) {
                                _messages.update { current ->
                                    if (current.all { it.isRead }) current
                                    else current.map { it.copy(isRead = true) }
                                }
                            }
                            if (targetRoomId.isNotEmpty()) {
                                scope.launch(Dispatchers.IO) { db()?.messageDao()?.markRoomAsRead(targetRoomId) }
                                scope.launch { _readReceiptEvent.emit(Pair(targetRoomId, sysMessage)) }
                            }
                        }
                        "SERVER_SHUTTINGDOWN" -> {
                            _serverShuttingDown.value = true
                            _connectionStatus.value = ConnectionStatus.RECONNECTING
                        }
                        "SET_SUPER_ADMIN" -> {
                            if (!_isSuperAdmin.value) {
                                _isSuperAdmin.value = true
                                appContext?.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
                                    ?.edit { putBoolean("is_super_admin", true) }
                            }
                            if (sysMessage.isNotEmpty() && _adminUserId.value == null) {
                                _adminUserId.value = sysMessage
                                appContext?.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
                                    ?.edit { putString("admin_user_id", sysMessage) }
                            }
                        }
                        "AUTH_FAILED", "USER_NOT_FOUND", "REGISTRATION_SUCCESS" -> {
                            _authStatus.value = sysType
                            if (sysType == "AUTH_FAILED") disconnect()
                        }
                        "SYSTEM_NOTIFICATION" -> {
                            _systemNotification.value = sysMessage
                        }
                        "SERVER_INFO" -> {
                            _serverVersion.value = sysMessage
                        }
                        "FORCE_DISCONNECT" -> {
                            if (sysMessage == currentUsername) disconnect()
                        }
                        "FORCE_LOGOUT" -> {
                            _authStatus.value = "FORCE_LOGOUT"
                            disconnect()
                        }
                        "FORCE_DISCONNECT_DEVICE" -> {
                            // Device-specific disconnect not supported in v2
                        }
                        "FORCE_LOGOUT_EXCEPT" -> {
                            _authStatus.value = "FORCE_LOGOUT"
                            disconnect()
                        }
                        "CLEAR_CACHE" -> {
                            scope.launch(Dispatchers.IO) {
                                db()?.messageDao()?.clearRoom(sysMessage)
                                db()?.chatDao()?.deleteChat(sysMessage)
                            }
                            if (sysMessage == currentRoomId) {
                                _messages.update { emptyList() }
                            }
                        }
                        "CHAT_DELETED" -> {
                            _chatDeletedEvent.value = sysMessage
                        }
                        "ONLINE_USERS_UPDATE" -> {
                            try {
                                if (sysMessage.isNotEmpty() && sysMessage != "null") {
                                    val jsonArray = org.json.JSONArray(sysMessage)
                                    val userList = mutableListOf<String>()
                                    for (i in 0 until jsonArray.length()) userList.add(jsonArray.getString(i))
                                    _users.value = userList
                                }
                            } catch (e: Exception) { Log.e(TAG, "Error parsing online users update", e) }
                        }
                        "TYPING" -> {
                            try {
                                val parts = sysMessage.split("|", limit = 2)
                                if (parts.size == 2) {
                                    val typist = parts[0]
                                    val isTyping = parts[1].toBooleanStrictOrNull() ?: false
                                    val targetRoom = value.roomId.ifEmpty { currentRoomId }
                                    Log.d(TAG, "TYPING received: typist=$typist, isTyping=$isTyping, targetRoom=$targetRoom, currentRoomId=$currentRoomId")
                                    _typingUsers.update { current ->
                                        val roomTyping = current[targetRoom]?.toMutableSet() ?: mutableSetOf()
                                        if (isTyping) roomTyping.add(typist) else roomTyping.remove(typist)
                                        current + (targetRoom to roomTyping)
                                    }
                                }
                            } catch (e: Exception) { Log.e(TAG, "Error parsing typing signal", e) }
                        }
                        "REACTION_V2" -> {
                            try {
                                val parts = sysMessage.split("|", limit = 2)
                                if (parts.size == 2) {
                                    val messageId = parts[0]
                                    val reactionsJson = parts[1]
                                    val reactions = messageV2Client.parseReactions(reactionsJson.toByteArray())
                                    val reactionsDbJson = org.json.JSONArray().apply {
                                        reactions.forEach { r ->
                                            put(org.json.JSONObject().apply {
                                                put("user", r.user)
                                                put("emoji", r.emoji)
                                            })
                                        }
                                    }.toString()
                                    _messages.update { current ->
                                        val idx = current.indexOfFirst { it.id == messageId }
                                        if (idx != -1) {
                                            val list = current.toMutableList()
                                            list[idx] = list[idx].copy(reactions = reactions)
                                            scope.launch(Dispatchers.IO) {
                                                db()?.messageDao()?.insertMessages(listOf(list[idx].toEntity()))
                                            }
                                            list
                                        } else {
                                            scope.launch(Dispatchers.IO) {
                                                db()?.messageDao()?.updateReactions(messageId, reactionsDbJson)
                                            }
                                            current
                                        }
                                    }
                                }
                            } catch (e: Exception) { Log.e(TAG, "Error parsing reaction update", e) }
                        }
                        "SELF_DESTRUCT_TIMER" -> {
                            val timerValue = sysMessage.toIntOrNull() ?: 0
                            _selfDestructTimer.value = timerValue
                            val targetRoomId = value.roomId.ifEmpty { currentRoomId }
                            if (targetRoomId.isNotEmpty()) {
                                val ctx = appContext
                                val timerLabel = if (ctx != null) {
                                    val res = ctx.resources
                                    when (timerValue) {
                                        0 -> res.getString(lavender.client.android.R.string.self_destruct_off)
                                        30 -> res.getString(lavender.client.android.R.string.self_destruct_30s)
                                        60 -> res.getString(lavender.client.android.R.string.self_destruct_1m)
                                        300 -> res.getString(lavender.client.android.R.string.self_destruct_5m)
                                        3600 -> res.getString(lavender.client.android.R.string.self_destruct_1h)
                                        86400 -> res.getString(lavender.client.android.R.string.self_destruct_24h)
                                        else -> "${timerValue}s"
                                    }
                                } else "${timerValue}s"
                                val text = if (timerValue == 0) {
                                    val d = ctx?.resources?.getString(lavender.client.android.R.string.self_destruct_disabled) ?: "Timer disabled"
                                    "\uD83D\uDD25 $d"
                                } else {
                                    val t = ctx?.resources?.getString(lavender.client.android.R.string.self_destruct_set, timerLabel) ?: "Timer set: $timerLabel"
                                    "\uD83D\uDD25 $t"
                                }
                                // Use timestamp after the last message to ensure correct ordering
                                val lastMsgTimestamp = _messages.value.lastOrNull { it.roomId == targetRoomId }?.timestamp ?: (System.currentTimeMillis() / 1000)
                                val sysMsgTimestamp = lastMsgTimestamp + 1
                                val sysMsg = Message(
                                    id = "sd_timer_${targetRoomId}_${System.currentTimeMillis()}",
                                    user = "",
                                    text = text,
                                    timestamp = sysMsgTimestamp,
                                    roomId = targetRoomId
                                )
                                if (targetRoomId == currentRoomId) {
                                    _messages.update { current ->
                                        if (current.any { it.id == sysMsg.id }) current
                                        else current.toMutableList().apply { add(sysMsg) }
                                    }
                                }
                                // Persist system message to Room DB
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        db()?.messageDao()?.insertMessages(listOf(sysMsg.toEntity()))
                                    } catch (e: Exception) { Log.e(TAG, "Failed to persist self-destruct system message", e) }
                                }
                            }
                        }
                        "DELETE_MESSAGE_V2" -> {
                            addDeletedHash("id:$sysMessage")
                            scope.launch(Dispatchers.IO) { db()?.messageDao()?.deleteMessage(sysMessage) }
                            if (sysMessage.isNotEmpty()) {
                                _messages.update { current -> current.filterNot { it.id == sysMessage } }
                            }
                        }
                    }
                    return
                }
                // Handle message
                if (value.message != null) {
                    val msg = messageV2Client.messageV2ToDomain(value.message)
                    if (msg.roomId == currentRoomId) {
                        _messages.update { current ->
                            if (current.any { it.id == msg.id }) current
                            else {
                                val insertIndex = current.indexOfFirst { it.timestamp > msg.timestamp }
                                val list = current.toMutableList()
                                if (insertIndex == -1) list.add(msg) else list.add(insertIndex, msg)
                                list
                            }
                        }
                        scope.launch(Dispatchers.IO) {
                            db()?.messageDao()?.insertMessages(listOf(msg.toEntity()))
                        }
                        val myUsername = currentUsername ?: ""
                        if (msg.user.isNotEmpty() && msg.user != myUsername) {
                            val isScreenOn = appContext?.let {
                                val pm = it.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                                pm.isInteractive
                            } ?: true
                            if (isScreenOn) {
                                scheduleMarkRead(currentRoomId, myUsername)
                            } else {
                                appContext?.let { ctx ->
                                    val title = msg.user.ifEmpty { "New message" }
                                    val body = msg.text.take(200)
                                    lavender.client.android.data.fcm.LavenderMessagingService.showNotificationFromStream(ctx, title, body, currentRoomId, msg.id)
                                }
                            }
                        }
                    }
                    onMessageReceived(msg)
                    _newMessageEvent.tryEmit(msg)
                }
            }

            override fun onError(t: Throwable) {
                ErrorHandler.handle("RealGrpcClient.chatV2Stream", t)
                synchronized(chatV2Lock) { chatV2RequestObserver = null }
                _connectionStatus.value = ConnectionStatus.FAILED
            }

            override fun onCompleted() {
                synchronized(chatV2Lock) { chatV2RequestObserver = null }
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        }

        this.start(object : ClientCall.Listener<ChatV2MessageProto>() {
            override fun onMessage(message: ChatV2MessageProto) { responseObserver.onNext(message) }
            override fun onClose(status: Status, trailers: Metadata) {
                synchronized(chatV2Lock) { chatV2RequestObserver = null }
                if (status.isOk) {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                } else {
                    Log.w(TAG, "ChatV2 stream closed: ${status.code} ${status.description ?: ""}")
                    _connectionStatus.value = ConnectionStatus.FAILED
                }
            }
        }, Metadata())
        this.request(Int.MAX_VALUE)

        return object : StreamObserver<ChatV2MessageProto> {
            override fun onNext(value: ChatV2MessageProto) = this@startChatV2Stream.sendMessage(value)
            override fun onError(t: Throwable) = this@startChatV2Stream.cancel("Error in request stream", t)
            override fun onCompleted() = this@startChatV2Stream.halfClose()
        }
    }

    // ====== ChatV2 delegated methods ======

    fun loadHistoryV2(roomId: String, cursor: String = "", limit: Int = 100, onCompletion: (String, Boolean) -> Unit = { _, _ -> }) {
        messageV2Client.loadHistoryV2(roomId, cursor, limit, onCompletion)
    }

    fun sendMessageV2(message: Message, onResult: ((MessageV2Proto?) -> Unit)? = null) {
        messageV2Client.sendMessageV2(message, onResult)
    }

    fun editMessageV2(id: String, text: String, cb: (Boolean, String) -> Unit) {
        messageV2Client.editMessageV2(id, text) { success, msg ->
            if (success) {
                _messages.update { current ->
                    current.map { m -> if (m.id == id) m.copy(text = text, edited = true) else m }
                }
                scope.launch(Dispatchers.IO) {
                    db()?.messageDao()?.updateMessageText(id, text, edited = true)
                }
            }
            cb(success, msg)
        }
    }

    fun deleteMessageV2(messageIds: List<String>, cb: (Boolean) -> Unit = {}) {
        messageV2Client.deleteMessageV2(messageIds, cb)
    }

    fun setReactionV2(messageId: String, username: String, emoji: String) {
        messageV2Client.setReactionV2(messageId, username, emoji)
    }

    // ====== Calls (delegated) ======
    fun startCallSession() { callClient.startCallSession() }
    fun sendCallSignal(signal: CallMessageProto) { callClient.sendCallSignal(signal) }

    // ====== Messages V2 (delegated) ======
    fun addLocalMessage(message: Message) {
        // Don't save to Room DB here — sendMessageV2 response handler saves with the correct server ID.
        // Saving here creates a race condition where addLocalMessage's DB write
        // overwrites sendMessageV2's DB write, leaving a stale UUID record.
        _messages.update { current ->
            val list = current.toMutableList()
            val existingIndex = list.indexOfFirst { it.id == message.id }
            if (existingIndex != -1) list[existingIndex] = message
            else {
                val insertIndex = list.indexOfFirst { it.timestamp > message.timestamp }
                if (insertIndex == -1) list.add(message) else list.add(insertIndex, message)
            }
            list
        }
    }
    fun updateMessage(message: Message) {
        _messages.update { current ->
            val list = current.toMutableList()
            val index = list.indexOfFirst { it.id == message.id }
            if (index != -1) list[index] = message
            list
        }
    }
    fun clearMessages() { _messages.value = emptyList() }

    @Volatile private var markReadJob: kotlinx.coroutines.Job? = null
    @Volatile private var pendingMarkReadRoom: String? = null
    @Volatile private var pendingMarkReadUser: String? = null
    private fun scheduleMarkRead(roomId: String, username: String) {
        pendingMarkReadRoom = roomId
        pendingMarkReadUser = username
        markReadJob?.cancel()
        markReadJob = scope.launch {
            kotlinx.coroutines.delay(1000)
            val rid = pendingMarkReadRoom ?: return@launch
            val u = pendingMarkReadUser ?: return@launch
            pendingMarkReadRoom = null
            pendingMarkReadUser = null
            markRead(rid, u, null)
        }
    }

    fun markRead(rid: String, u: String, onComp: (() -> Unit)?) {
        appContext?.let { lavender.client.android.data.fcm.LavenderMessagingService.dismissNotificationsForRoom(it, rid) }
        val channel = getChannel()
        if (channel == null) {
            onComp?.invoke()
            return
        }
        val userId = currentUserId ?: ""
        val call = channel.newCall(METHOD_MARK_READ, CallOptions.DEFAULT)
        call.start(object : ClientCall.Listener<MarkReadResponseProto>() {
            override fun onMessage(response: MarkReadResponseProto) {}
            override fun onClose(status: Status, trailers: Metadata) {
                if (!status.isOk) {
                    ErrorHandler.handle("$TAG.markRead", StatusRuntimeException(status))
                }
                onComp?.invoke()
            }
        }, Metadata())
        call.sendMessage(MarkReadRequestProto(roomId = rid, username = u, userId = userId))
        call.halfClose()
        call.request(1)
    }

    // ====== Chat List (delegated) ======
    fun getChats(username: String, skipCache: Boolean = false, limit: Int = 100, cursor: String = "", callback: (ChatListPage) -> Unit) { chatClient.getChats(username, skipCache, limit, cursor, callback) }
    fun getAllChats(callback: (List<ChatInfo>) -> Unit) { chatClient.getAllChats(callback) }
    fun getChatListVersion(u: String, cb: (Long) -> Unit) { chatClient.getChatListVersion(u, cb) }
    fun registerToken(user: String, token: String, pushEnabled: Boolean) { chatAuxClient.registerToken(user, token, pushEnabled) }
    fun fetchUserId(username: String, callback: (String?, Boolean) -> Unit) { chatAuxClient.fetchUserId(username, callback) }
    fun loadAllUsers(cb: (List<UserInfoProto>) -> Unit) { chatAuxClient.loadAllUsers(cb) }
    fun getAdminUserList(query: String, cursor: String, limit: Int, sortBy: String, callback: (GetAdminUserListResponseProto) -> Unit) { chatAuxClient.getAdminUserList(query, cursor, limit, sortBy, callback) }
    fun getAdminUserSessions(userId: String, callback: (GetAdminUserSessionsResponseProto) -> Unit) { chatAuxClient.getAdminUserSessions(userId, callback) }
    fun getMutedChats(callback: (List<String>) -> Unit) { chatAuxClient.getMutedChats(callback) }
    fun setMutedChat(roomId: String, muted: Boolean, callback: (Boolean) -> Unit) { chatAuxClient.setMutedChat(roomId, muted, callback) }
    fun setSelfDestructTimer(roomId: String, timerSeconds: Int, callback: (Boolean, String?) -> Unit) { chatAuxClient.setSelfDestructTimer(roomId, timerSeconds, callback) }
    fun deleteChat(cid: String, requesterUsername: String, cb: (Boolean, String) -> Unit) { chatClient.deleteChat(cid, requesterUsername, cb) }
    fun deleteChatWithUserId(cid: String, userId: String, username: String, cb: (Boolean, String) -> Unit) { chatClient.deleteChatWithUserId(cid, userId, username, cb) }
    fun createDirectChat(u1: String, u2: String, cb: (String?) -> Unit) { chatClient.createDirectChat(u1, u2, cb) }
    fun createGroupChat(n: String, ps: List<String>, c: String, type: String = "group", cb: (String?) -> Unit) { chatClient.createGroupChat(n, ps, c, type, cb) }
    fun updateChatAvatar(cid: String, a: String, u: String, fa: String, cb: (Boolean, String) -> Unit) { chatClient.updateChatAvatar(cid, a, u, fa, cb) }
    fun updateChatSettings(chatId: String, allowAdd: Boolean, callback: (Boolean, String) -> Unit) { chatClient.updateChatSettings(chatId, allowAdd, callback) }
    fun updateChatName(cid: String, n: String, cb: (Boolean, String) -> Unit) { chatClient.updateChatName(cid, n, cb) }
    fun addParticipants(cid: String, us: List<String>, cb: (Boolean, String) -> Unit) { chatClient.addParticipants(cid, us, cb) }
    fun addParticipant(cid: String, u: String, cb: (Boolean, String) -> Unit) { chatClient.addParticipant(cid, u, cb) }
    fun removeParticipant(cid: String, u: String, cb: (Boolean, String) -> Unit) { chatClient.removeParticipant(cid, u, cb) }

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
    fun removeFavorite(userId: String, messageId: String, callback: (Boolean, String) -> Unit) { favoritesClient.removeFavorite(userId, messageId, callback) }
    fun getFavorites(userId: String, callback: (List<Message>) -> Unit) { favoritesClient.getFavorites(userId, callback) }

    // ====== Auth V2 (delegated) ======
    fun signInV2(username: String, password: String, deviceId: String, deviceName: String, deviceType: String = "android", clientVersion: String = "", callback: (AuthResponseV2Proto?, String?) -> Unit) = authClient.signInV2(username, password, deviceId, deviceName, deviceType, clientVersion, callback)
    fun signUpV2(username: String, password: String, email: String, deviceId: String, deviceName: String, deviceType: String = "android", clientVersion: String = "", callback: (AuthResponseV2Proto?, String?) -> Unit) = authClient.signUpV2(username, password, email, deviceId, deviceName, deviceType, clientVersion, callback)
    fun refreshToken(refreshToken: String, callback: (RefreshTokenResponseProto?, String?) -> Unit) = authClient.refreshToken(refreshToken, callback)
    fun signOut(refreshToken: String = "", allDevices: Boolean = false, callback: (Boolean, String) -> Unit) = authClient.signOut(refreshToken, allDevices, callback)
    fun revokeDevice(deviceId: String, callback: (Boolean, String) -> Unit) = authClient.revokeDevice(deviceId, callback)

    // ====== Server Discovery (delegated) ======
    fun fetchServersList(cb: (List<ServerInfoProto>) -> Unit) {
        serverDiscoveryClient.fetchServersList(cb)
    }

    // ====== Room / Messages state ======
    fun setRoomId(roomId: String) {
        currentRoomId = roomId
        _messages.value = emptyList()
    }
    fun clearSystemNotification() { _systemNotification.value = null }
    fun setUserId(userId: String) { currentUserId = userId }
    fun setUsername(username: String) { currentUsername = username }
    fun getUserId(): String? = currentUserId
    fun getCurrentUsername(): String? = currentUsername

    // ====== Avatar cache ======
    private var avatarFlowUpdateJob: kotlinx.coroutines.Job? = null

    fun getAvatarCache(): Map<String, String> = avatarCache.toMap()
    fun getFullAvatarCache(): Map<String, String> = fullAvatarCache.toMap()
    fun getFullAvatarUrl(u: String): String? = fullAvatarCache[u]
    fun updateAvatarCache(u: String, a: String, fa: String) {
        avatarCache[u] = a
        if (fa.isNotEmpty()) fullAvatarCache[u] = fa
        // Debounce flow update (batch rapid avatar loads)
        avatarFlowUpdateJob?.cancel()
        avatarFlowUpdateJob = scope.launch {
            kotlinx.coroutines.delay(500)
            avatarCacheFlow.value = avatarCache.toMap()
        }
    }

    // ====== ChatList v2 (delegated to unaryCallChatListV2) ======
    suspend fun pinChat(chatId: String): Boolean = chatListV2Client.pinChat(chatId)

    suspend fun unpinChat(chatId: String): Boolean = chatListV2Client.unpinChat(chatId)

    suspend fun searchChats(query: String, limit: Int, offset: Int): List<ChatInfo> = chatListV2Client.searchChats(query, limit, offset)

    suspend fun archiveChat(chatId: String): Boolean = chatListV2Client.archiveChat(chatId)

    suspend fun unarchiveChat(chatId: String): Boolean = chatListV2Client.unarchiveChat(chatId)

    suspend fun pinMessage(chatId: String, messageId: String): Boolean = chatListV2Client.pinMessage(chatId, messageId)

    suspend fun unpinMessage(chatId: String, messageId: String): Boolean = chatListV2Client.unpinMessage(chatId, messageId)

    suspend fun getPinnedMessages(chatId: String): List<Message> = chatListV2Client.getPinnedMessages(chatId)

    // ====== Helpers ======
    private fun getMessageHash(message: Message): String =
        if (message.id.isNotEmpty()) "id:${message.id}"
        else "${message.user}:${message.text}:${message.timestamp / 1000}"

    private fun getMessageHashForDedup(message: Message): String =
        "${message.user}:${message.text}:${message.timestamp / 1000}"

    @Volatile private var deletedMessagesLoaded = false

    private fun loadDeletedMessages() {
        if (deletedMessagesLoaded) return
        deletedMessagesLoaded = true
        // Load from SharedPreferences
        appContext?.getSharedPreferences("deleted_messages", Context.MODE_PRIVATE)?.let { prefs ->
            deletedMessageHashes.addAll(prefs.getStringSet("hashes", emptySet()) ?: emptySet())
        }
        // Load from Room DB
        scope.launch(Dispatchers.IO) {
            try {
                val ids = db()?.deletedMessageDao()?.getAllIds() ?: emptyList()
                ids.forEach { deletedMessageHashes.add("id:$it") }
                // Cleanup old entries (> 30 days)
                val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                db()?.deletedMessageDao()?.cleanupOlderThan(cutoff)
            } catch (_: Exception) {}
        }
    }

    @Volatile private var database: AppDatabase? = null
    private fun db(): AppDatabase? {
        database?.let { return it }
        val ctx = appContext ?: return null
        val d = AppDatabase.getDatabase(ctx)
        database = d
        return d
    }

    fun loadUsers() {
        loadAllUsers { users ->
            val admin = users.firstOrNull { it.isSuperAdmin && it.userId.isNotEmpty() }
            if (admin != null && _adminUserId.value == null) {
                _adminUserId.value = admin.userId
                appContext?.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
                    ?.edit { putString("admin_user_id", admin.userId) }
            }
        }
    }
}
