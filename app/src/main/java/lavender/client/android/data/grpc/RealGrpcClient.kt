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
    private var channel: ManagedChannel? = null

    fun getChannel(): ManagedChannel? = channel
    
    private var database: AppDatabase? = null
    private fun db() = database ?: appContext?.let { 
        val d = AppDatabase.getDatabase(it)
        database = d
        d
    }
    private var requestObserver: StreamObserver<MessageProto>? = null
    private var typingRequestObserver: StreamObserver<TypingRequestProto>? = null
    private var callRequestObserver: StreamObserver<CallMessageProto>? = null
    
    var currentServerAddress: String? = null
    var currentRoomId = ""
        private set



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

    var hasCheckedForUpdates = false
    var isAppInBackground = false
        set(value) {
            field = value
            if (value) {
                backgroundStartTime = System.currentTimeMillis()
            }
        }
    private var backgroundStartTime: Long = 0

    fun shouldForceReconnect(): Boolean {
        // Force reconnect if app was in background for more than 5 minutes
        return isAppInBackground && (System.currentTimeMillis() - backgroundStartTime) > 5 * 60 * 1000
    }

    private var appContext: Context? = null
    private var currentUsername: String? = null
    private var currentUserId: String? = null
    
    private val avatarCache = mutableMapOf<String, String>()
    private val fullAvatarCache = mutableMapOf<String, String>()
    val avatarCacheFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    
    private val deletedMessageHashes = mutableSetOf<String>()
    private val pendingReads = mutableSetOf<String>()

    fun connect(serverAddress: String, useTls: Boolean = false, port: Int = 50051, context: Context? = null, forceReconnect: Boolean = false) {
        Log.d(TAG, "connect() called: addr=$serverAddress:$port force=$forceReconnect status=${_connectionStatus.value}")

        val channelDead = channel?.isShutdown == true || channel?.isTerminated == true
        val addressMatch = currentServerAddress == serverAddress
        val channelAlive = channel != null && !channelDead && _connectionStatus.value == ConnectionStatus.READY

        // If channel is alive and address matches, keep it — active streams must not be killed.
        if (addressMatch && channelAlive) {
            Log.d(TAG, "Connection already READY (force=$forceReconnect), keeping active streams")
            return
        }

        // CRITICAL: Do not reset channel if a call is in progress
        if (lavender.client.android.data.calls.CallManager.currentCall.value != null) {
            Log.w(TAG, "Call in progress, preventing channel reset")
            return
        }

        appContext = context?.applicationContext
        currentServerAddress = serverAddress
        _isSuperAdmin.value = false
        loadDeletedMessages()

        Log.d(TAG, "Connecting to $serverAddress:$port (TLS: $useTls)")
        // Use RECONNECTING for transient state (was already connected before)
        val wasConnected = _connectionStatus.value == ConnectionStatus.READY ||
                           _connectionStatus.value == ConnectionStatus.RECONNECTING
        _connectionStatus.value = if (wasConnected) ConnectionStatus.RECONNECTING else ConnectionStatus.CONNECTING

        try {
            val builder = OkHttpChannelBuilder.forAddress(serverAddress, port)
            if (useTls) {
                builder.useTransportSecurity()
            } else {
                builder.usePlaintext()
            }

            // Aggressive keep-alive for fast detection of dead connections on mobile
            builder.keepAliveTime(10, TimeUnit.SECONDS)    // Ping every 10s
            builder.keepAliveTimeout(5, TimeUnit.SECONDS)   // 5s to detect dead connection
            builder.keepAliveWithoutCalls(true)
            // Max idle — allow server to clean up (server MaxConnectionAge = 30min)
            builder.maxInboundMessageSize(64 * 1024 * 1024)

            // Bearer token interceptor — attaches JWT to all calls (skipped for AuthService + Chat stream)
            val appCtx = context?.applicationContext
            if (appCtx != null) {
                builder.intercept(BearerTokenInterceptor(appCtx))
            }

            channel?.shutdownNow()
            val newChannel = builder.build()
            channel = newChannel

            _connectionStatus.value = ConnectionStatus.READY
            resetReconnectBackoff()
            Log.d(TAG, "Channel built successfully to $serverAddress")

            // Auto-resume last chat if it exists
            lastChatRequest?.let {
                Log.d(TAG, "Resuming last chat for ${it.u}")
                startChat(it.u, it.p, it.j, it.r, it.did, it.dn, it.cb)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            _connectionStatus.value = ConnectionStatus.RECONNECTING
            _error.value = e.message

            // Graceful retry with exponential backoff
            scheduleReconnect(serverAddress, useTls, port, context)
        }
    }

    private var reconnectJob: Job? = null

    private fun scheduleReconnect(serverAddress: String, useTls: Boolean, port: Int, context: Context?) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = reconnectDelayMs.coerceAtMost(30000L)
            Log.d(TAG, "Scheduling reconnect in ${delayMs}ms...")
            delay(delayMs)
            reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(60000L)
            Log.d(TAG, "Retrying connection to $serverAddress...")
            connect(serverAddress, useTls, port, context)
        }
    }

    private var reconnectDelayMs = 5000L

    // Reset reconnect backoff on successful connection
    private fun resetReconnectBackoff() {
        reconnectDelayMs = 5000L
    }

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
        val addr = currentServerAddress
        if (!addr.isNullOrEmpty()) {
            Log.d(TAG, "reconnect() called, reconnecting to $addr")
            connect(addr, false, 50051, appContext, true)
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        resetReconnectBackoff()
        channel?.shutdown()
        channel = null
        requestObserver = null
        typingRequestObserver = null
        callRequestObserver = null
        lastChatRequest = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
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

        val currentChannel = channel
        if (currentChannel == null || currentChannel.isShutdown || currentChannel.isTerminated) {
            val addr = currentServerAddress
            if (!addr.isNullOrEmpty()) {
                Log.w(TAG, "Channel is not available, attempting reconnect to $addr")
                connect(addr)
            } else {
                Log.e(TAG, "Cannot start chat: channel and server address are null")
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
        
        val firstMessage = MessageProto.newBuilder()
            .setUser(username)
            .setPassword(password)
            .setText(joinMessage)
            .setRoomId(currentRoomId) // Set current room on start
            .setCreatedAt(ProtoUtils.getCurrentTimestamp())
            .setClientVersion(BuildConfig.VERSION_NAME)
            .setRegister(register)
            .setDeviceId(deviceId)
            .setDeviceName(deviceName)
            .build()
        
        _authStatus.value = null // Reset auth status on new stream
        
        requestObserver?.onNext(firstMessage)
        
        // Only resend pending data AFTER sending authentication signal
        resendPendingMessages()
        resendPendingReads()

        startTypingStream()
    }

    fun getDevices(uid: String, cb: (List<DeviceInfoProto>) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetDevicesRequestProto, GetDevicesResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetDevices")
            .setRequestMarshaller(GetDevicesRequestMarshaller())
            .setResponseMarshaller(GetDevicesResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetDevicesResponseProto>() {
            override fun onMessage(message: GetDevicesResponseProto) { cb(message.devices) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(emptyList()) }
        }, io.grpc.Metadata())
        call.sendMessage(GetDevicesRequestProto(uid))
        call.halfClose()
        call.request(1)
    }

    fun deleteDevice(uid: String, did: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<DeleteDeviceRequestProto, DeleteDeviceResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/DeleteDevice")
            .setRequestMarshaller(DeleteDeviceRequestMarshaller())
            .setResponseMarshaller(DeleteDeviceResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<DeleteDeviceResponseProto>() {
            override fun onMessage(message: DeleteDeviceResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(DeleteDeviceRequestProto(uid, did))
        call.halfClose()
        call.request(1)
    }

    fun deleteOtherDevices(uid: String, currentDid: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<DeleteDeviceRequestProto, DeleteDeviceResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/DeleteOtherDevices")
            .setRequestMarshaller(DeleteDeviceRequestMarshaller())
            .setResponseMarshaller(DeleteDeviceResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<DeleteDeviceResponseProto>() {
            override fun onMessage(message: DeleteDeviceResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(DeleteDeviceRequestProto(uid, currentDid))
        call.halfClose()
        call.request(1)
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
                if (callRequestObserver == null && currentUsername != null) {
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
                        // We don't call disconnect() here to allow the user to see the error and decide
                        // what to do next, e.g., try to register. Calling disconnect() would kill the channel.
                        return
                    }
                }

                _connectionStatus.value = ConnectionStatus.RECONNECTING

                // Clear observer immediately to prevent broken stream reuse
                requestObserver = null

                if (isRetrying) return // Already in retry loop

                scope.launch {
                    isRetrying = true
                    var retryDelay = 3000L // Start with 3 seconds for stream reconnect
                    val maxRetryDelay = 30000L // Max 30 seconds for streams
                    var retryCount = 0
                    val maxRetries = 50

                    try {
                        while (retryCount < maxRetries && requestObserver == null) {
                            delay(retryDelay)

                            // Don't retry if app is in background for too long
                            if (isAppInBackground && System.currentTimeMillis() - backgroundStartTime > 300000) {
                                 Log.d(TAG, "App in background for too long, stopping stream retry loop")
                                 break
                            }

                            // If server was unavailable, fully reset the channel before retrying
                            if (t is io.grpc.StatusRuntimeException && t.status.code == io.grpc.Status.Code.UNAVAILABLE) {
                                Log.w(TAG, "Server UNAVAILABLE, resetting connection channel")
                                currentServerAddress?.let { connect(it, forceReconnect = true) }
                            }

                            Log.d(TAG, "Attempting stream reconnect (attempt ${retryCount + 1}, delay=${retryDelay}ms)...")

                            lastChatRequest?.let { req ->
                                startChat(req.u, req.p, req.j, req.r, req.did, req.dn, req.cb)
                                if (_connectionStatus.value == ConnectionStatus.READY) {
                                    Log.d(TAG, "Stream reconnection successful")
                                    return@launch
                                }
                            }

                            retryCount++
                            retryDelay = (retryDelay * 1.5).toLong().coerceAtMost(maxRetryDelay)
                        }
                    } finally {
                        isRetrying = false
                    }

                    if (retryCount >= maxRetries) {
                        Log.e(TAG, "Failed to reconnect stream after $maxRetries attempts")
                        _connectionStatus.value = ConnectionStatus.FAILED
                        _error.value = "Connection lost. Please check your internet connection."
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
                if (!status.isOk) {
                    _connectionStatus.value = ConnectionStatus.FAILED
                    requestObserver = null
                }
                if (status.isOk) responseObserver.onCompleted() else responseObserver.onError(status.asRuntimeException())
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
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<TypingRequestProto, TypingSignalProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
            .setFullMethodName("messenger.ChatService/Typing")
            .setRequestMarshaller(TypingRequestMarshaller())
            .setResponseMarshaller(TypingSignalMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        typingRequestObserver = call.startTypingStream()
    }

    private fun io.grpc.ClientCall<TypingRequestProto, TypingSignalProto>.startTypingStream(): StreamObserver<TypingRequestProto> {
        val responseObserver = object : StreamObserver<TypingSignalProto> {
            override fun onNext(value: TypingSignalProto) {
                _typingUsers.update { current ->
                    val roomTyping = current[value.roomId]?.toMutableSet() ?: mutableSetOf()
                    if (value.isTyping) roomTyping.add(value.username) else roomTyping.remove(value.username)
                    current + (value.roomId to roomTyping)
                }
            }
            override fun onError(t: Throwable) { 
                Log.e(TAG, "Typing stream error", t) 
                scope.launch {
                    delay(5000)
                    startTypingStream()
                }
            }
            override fun onCompleted() {}
        }

        this.start(object : io.grpc.ClientCall.Listener<TypingSignalProto>() {
            override fun onMessage(message: TypingSignalProto) = responseObserver.onNext(message)
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        this.request(Int.MAX_VALUE)

        return object : StreamObserver<TypingRequestProto> {
            override fun onNext(value: TypingRequestProto) = this@startTypingStream.sendMessage(value)
            override fun onError(t: Throwable) = this@startTypingStream.cancel("Error", t)
            override fun onCompleted() = this@startTypingStream.halfClose()
        }
    }

    fun sendTypingSignal(username: String, isTyping: Boolean) {
        typingRequestObserver?.onNext(TypingRequestProto(currentRoomId, username, isTyping, currentUserId ?: ""))
    }

    fun startCallSession() {
        val currentChannel = channel
        if (currentChannel == null || currentChannel.isShutdown) {
            Log.e(TAG, "Cannot start call session: channel not ready")
            return
        }
        if (callRequestObserver != null) return // Already started

        Log.d(TAG, "Starting CallSession stream")
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<CallMessageProto, CallMessageProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
            .setFullMethodName("messenger.ChatService/CallSession")
            .setRequestMarshaller(CallMessageProtoMarshaller())
            .setResponseMarshaller(CallMessageProtoMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        callRequestObserver = call.startCallStream()

        // Send an identity signal to register with the hub on the server
        val identityId = currentUserId ?: currentUsername
        identityId?.let { id ->
            callRequestObserver?.onNext(CallMessageProto(
                senderId = id,
                type = CallMessageProto.Type.ICE_CANDIDATE, // Use as heartbeat/identity
                payload = "IDENTITY"
            ))
        }
    }

    private fun io.grpc.ClientCall<CallMessageProto, CallMessageProto>.startCallStream(): StreamObserver<CallMessageProto> {
        val responseObserver = object : StreamObserver<CallMessageProto> {
            override fun onNext(value: CallMessageProto) {
                scope.launch { _callSignals.emit(value) }
            }
            override fun onError(t: Throwable) {
                Log.e(TAG, "Call session stream error", t)
                callRequestObserver = null
                lavender.client.android.data.calls.CallManager.clearCurrentCall()
                scope.launch {
                    delay(5000)
                    startCallSession()
                }
            }
            override fun onCompleted() {
                Log.d(TAG, "Call session stream completed")
                callRequestObserver = null
                lavender.client.android.data.calls.CallManager.clearCurrentCall()
            }
        }

        this.start(object : io.grpc.ClientCall.Listener<CallMessageProto>() {
            override fun onMessage(message: CallMessageProto) = responseObserver.onNext(message)
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    _connectionStatus.value = ConnectionStatus.FAILED
                    requestObserver = null
                }
                if (status.isOk) responseObserver.onCompleted() else responseObserver.onError(status.asRuntimeException())
            }
        }, io.grpc.Metadata())
        this.request(Int.MAX_VALUE)

        return object : StreamObserver<CallMessageProto> {
            override fun onNext(value: CallMessageProto) = this@startCallStream.sendMessage(value)
            override fun onError(t: Throwable) = this@startCallStream.cancel("Error", t)
            override fun onCompleted() = this@startCallStream.halfClose()
        }
    }

    fun sendCallSignal(signal: CallMessageProto) {
        if (callRequestObserver == null) {
            startCallSession()
            // Give it a bit of time or queue it? For now just try to send
        }
        callRequestObserver?.onNext(signal)
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

        val currentChannel = channel ?: return
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
        // Load from cache first (if not skipped) and show immediately
        if (!skipCache) {
            scope.launch(Dispatchers.IO) {
                val cached = db()?.chatDao()?.getAllChats()?.map { it.toDomain() } ?: emptyList()
                if (cached.isNotEmpty()) {
                    withContext(Dispatchers.Main) { callback(cached) }
                }
            }
        }

        val currentChannel = channel
        if (currentChannel == null || currentChannel.isShutdown || currentChannel.isTerminated) {
            Log.w(TAG, "getChats: channel is null or dead, attempting reconnect")
            _connectionStatus.value = ConnectionStatus.FAILED
            val addr = currentServerAddress
            if (!addr.isNullOrEmpty()) {
                connect(addr)
            }
            return
        }
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetChatsRequestProto, GetChatsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetChats")
            .setRequestMarshaller(GetChatsRequestMarshaller())
            .setResponseMarshaller(GetChatsResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetChatsResponseProto>() {
            override fun onMessage(message: GetChatsResponseProto) {
                val chats = message.chats.map { proto ->
                    ChatInfo(proto.id, proto.name, proto.type, proto.participants,
                             proto.createdAt?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                             proto.unreadCount,
                             proto.lastMessageTime?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                             proto.creator, proto.lastMessageText, proto.avatarUrl, proto.fullAvatarUrl, proto.lastMessageUsername, false, proto.lastMessageHasImage, proto.allowMembersToAdd,
                             proto.conferenceStartTime?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                             proto.isSecret, proto.peerPublicKey, proto.e2eeReady,
                             proto.activeAgentId, proto.agentMode)
                }
                scope.launch(Dispatchers.IO) {
                    db()?.chatDao()?.syncChats(chats.map { it.toEntity() })
                }
                // Only update UI with network result if it's not empty
                if (chats.isNotEmpty()) {
                    scope.launch(Dispatchers.Main) { callback(chats) }
                }
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    Log.w(TAG, "getChats: onClose error: ${status.code}")
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(GetChatsRequestProto(username = username, userId = currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun setRoomId(roomId: String) {
        currentRoomId = roomId
        _messages.value = emptyList()
    }

    fun clearMessages() { _messages.value = emptyList() }

    fun registerToken(user: String, token: String, pushEnabled: Boolean) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<TokenRequestProto, TokenResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/RegisterToken")
            .setRequestMarshaller(TokenRequestMarshaller())
            .setResponseMarshaller(TokenResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<TokenResponseProto>() {}, io.grpc.Metadata())
        call.sendMessage(TokenRequestProto(user, token, pushEnabled, currentUserId ?: ""))
        call.halfClose()
        call.request(1)
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
    ) {
        val currentChannel = channel ?: run {
            callback(null, "Not connected")
            return
        }

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<SignInRequestV2Proto, AuthResponseV2Proto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.AuthService/SignInV2")
            .setRequestMarshaller(SignInRequestV2Marshaller())
            .setResponseMarshaller(AuthResponseV2Marshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<AuthResponseV2Proto>() {
            override fun onMessage(message: AuthResponseV2Proto) {
                if (message.success) {
                    callback(message, null)
                } else {
                    callback(null, message.message)
                }
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    callback(null, status.description ?: "Auth failed")
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(SignInRequestV2Proto(
            username = username,
            password = password,
            deviceId = deviceId,
            deviceName = deviceName,
            deviceType = deviceType,
            clientVersion = clientVersion
        ))
        call.halfClose()
        call.request(1)
    }

    /**
     * SignUpV2 — registers a new user via AuthService v2 with JWT tokens.
     */
    fun signUpV2(
        username: String,
        password: String,
        email: String,
        deviceId: String,
        deviceName: String,
        deviceType: String = "android",
        clientVersion: String = "",
        callback: (AuthResponseV2Proto?, String?) -> Unit
    ) {
        val currentChannel = channel ?: run {
            callback(null, "Not connected")
            return
        }

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<SignUpRequestV2Proto, AuthResponseV2Proto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.AuthService/SignUpV2")
            .setRequestMarshaller(SignUpRequestV2Marshaller())
            .setResponseMarshaller(AuthResponseV2Marshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<AuthResponseV2Proto>() {
            override fun onMessage(message: AuthResponseV2Proto) {
                if (message.success) {
                    callback(message, null)
                } else {
                    callback(null, message.message)
                }
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    callback(null, status.description ?: "Registration failed")
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(SignUpRequestV2Proto(
            username = username,
            password = password,
            email = email,
            deviceId = deviceId,
            deviceName = deviceName,
            deviceType = deviceType,
            clientVersion = clientVersion
        ))
        call.halfClose()
        call.request(1)
    }

    /**
     * RefreshToken — exchanges a refresh token for a new access+refresh pair.
     */
    fun refreshToken(
        refreshToken: String,
        callback: (RefreshTokenResponseProto?, String?) -> Unit
    ) {
        val currentChannel = channel ?: run {
            callback(null, "Not connected")
            return
        }

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<RefreshTokenRequestProto, RefreshTokenResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.AuthService/RefreshToken")
            .setRequestMarshaller(RefreshTokenRequestMarshaller())
            .setResponseMarshaller(RefreshTokenResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<RefreshTokenResponseProto>() {
            override fun onMessage(message: RefreshTokenResponseProto) {
                callback(message, null)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    callback(null, status.description ?: "Token refresh failed")
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(RefreshTokenRequestProto(refreshToken = refreshToken))
        call.halfClose()
        call.request(1)
    }

    /**
     * SignOut — revokes a device session or all sessions.
     */
    fun signOut(
        refreshToken: String = "",
        allDevices: Boolean = false,
        callback: (Boolean, String) -> Unit
    ) {
        val currentChannel = channel ?: run {
            callback(false, "Not connected")
            return
        }

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<SignOutRequestProto, SimpleAuthResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.AuthService/SignOut")
            .setRequestMarshaller(SignOutRequestMarshaller())
            .setResponseMarshaller(SimpleAuthResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<SimpleAuthResponseProto>() {
            override fun onMessage(message: SimpleAuthResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    callback(false, status.description ?: "Sign out failed")
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(SignOutRequestProto(refreshToken = refreshToken, allDevices = allDevices))
        call.halfClose()
        call.request(1)
    }

    /**
     * RevokeDevice — deactivates a specific device for the authenticated user.
     */
    fun revokeDevice(
        deviceId: String,
        callback: (Boolean, String) -> Unit
    ) {
        val currentChannel = channel ?: run {
            callback(false, "Not connected")
            return
        }

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<RevokeDeviceRequestProto, SimpleAuthResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.AuthService/RevokeDevice")
            .setRequestMarshaller(RevokeDeviceRequestMarshaller())
            .setResponseMarshaller(SimpleAuthResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<SimpleAuthResponseProto>() {
            override fun onMessage(message: SimpleAuthResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    callback(false, status.description ?: "Revoke failed")
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(RevokeDeviceRequestProto(deviceId = deviceId))
        call.halfClose()
        call.request(1)
    }

    fun saveDraft(roomId: String, text: String, replyId: String, replyUser: String, replyText: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<SaveDraftRequestProto, SaveDraftResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SaveDraft")
            .setRequestMarshaller(SaveDraftRequestMarshaller())
            .setResponseMarshaller(SaveDraftResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<SaveDraftResponseProto>() {
            override fun onMessage(message: SaveDraftResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) callback(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(SaveDraftRequestProto(currentUserId ?: "", roomId, text, replyId, replyUser, replyText))
        call.halfClose()
        call.request(1)
    }

    fun getDraft(roomId: String, callback: (String, String, String, String, Boolean) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetDraftRequestProto, GetDraftResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetDraft")
            .setRequestMarshaller(GetDraftRequestMarshaller())
            .setResponseMarshaller(GetDraftResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetDraftResponseProto>() {
            override fun onMessage(message: GetDraftResponseProto) { callback(message.draftText, message.repliedToMessageId, message.repliedToUser, message.repliedToText, message.hasDraft) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetDraftRequestProto(currentUserId ?: "", roomId))
        call.halfClose()
        call.request(1)
    }

    fun deleteDraft(roomId: String, callback: (Boolean) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<DeleteDraftRequestProto, DeleteDraftResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/DeleteDraft")
            .setRequestMarshaller(DeleteDraftRequestMarshaller())
            .setResponseMarshaller(DeleteDraftResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<DeleteDraftResponseProto>() {
            override fun onMessage(message: DeleteDraftResponseProto) { callback(message.success) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(DeleteDraftRequestProto(currentUserId ?: "", roomId))
        call.halfClose()
        call.request(1)
    }

    fun getMutedChats(callback: (List<String>) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetMutedChatsRequestProto, GetMutedChatsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetMutedChats")
            .setRequestMarshaller(GetMutedChatsRequestMarshaller())
            .setResponseMarshaller(GetMutedChatsResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetMutedChatsResponseProto>() {
            override fun onMessage(message: GetMutedChatsResponseProto) { callback(message.roomIds) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetMutedChatsRequestProto(currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun setMutedChat(roomId: String, muted: Boolean, callback: (Boolean) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<SetMutedChatRequestProto, SetMutedChatResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SetMutedChat")
            .setRequestMarshaller(SetMutedChatRequestMarshaller())
            .setResponseMarshaller(SetMutedChatResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<SetMutedChatResponseProto>() {
            override fun onMessage(message: SetMutedChatResponseProto) { callback(message.success) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(SetMutedChatRequestProto(currentUserId ?: "", roomId, muted))
        call.halfClose()
        call.request(1)
    }

    fun addFavorite(userId: String, messageId: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<AddFavoriteRequestProto, AddFavoriteResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/AddFavorite")
            .setRequestMarshaller(AddFavoriteRequestMarshaller())
            .setResponseMarshaller(AddFavoriteResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<AddFavoriteResponseProto>() {
            override fun onMessage(message: AddFavoriteResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(AddFavoriteRequestProto(userId, messageId))
        call.halfClose()
        call.request(1)
    }

    fun removeFavorite(userId: String, messageId: String, callback: (Boolean) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<RemoveFavoriteRequestProto, RemoveFavoriteResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/RemoveFavorite")
            .setRequestMarshaller(RemoveFavoriteRequestMarshaller())
            .setResponseMarshaller(RemoveFavoriteResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<RemoveFavoriteResponseProto>() {
            override fun onMessage(message: RemoveFavoriteResponseProto) { callback(message.success) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(RemoveFavoriteRequestProto(userId, messageId))
        call.halfClose()
        call.request(1)
    }

    fun getFavorites(userId: String, callback: (List<Message>) -> Unit) {
        // Load from cache first
        scope.launch(Dispatchers.IO) {
            // Find messages belonging to favorites virtual room or starred
            val favoritesRoomId = "favorites_" + (currentUsername ?: "")
            val cached = db()?.messageDao()?.getFavorites(favoritesRoomId)?.map { it.toDomain() } ?: emptyList()
            if (cached.isNotEmpty()) {
                withContext(Dispatchers.Main) { callback(cached) }
            }
        }

        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetFavoritesRequestProto, GetFavoritesResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetFavorites")
            .setRequestMarshaller(GetFavoritesRequestMarshaller())
            .setResponseMarshaller(GetFavoritesResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetFavoritesResponseProto>() {
            override fun onMessage(message: GetFavoritesResponseProto) {
                val msgs = message.messages.map { ProtoUtils.createMessageFromProto(it) }
                
                // Save to persistent cache
                scope.launch(Dispatchers.IO) {
                    db()?.messageDao()?.insertMessages(msgs.map { it.toEntity() })
                }
                
                callback(msgs)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetFavoritesRequestProto(userId))
        call.halfClose()
        call.request(1)
    }

    fun saveFavoriteMessage(message: Message, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<MessageProto, AddFavoriteResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SaveFavoriteMessage")
            .setRequestMarshaller(MessageProtoMarshaller())
            .setResponseMarshaller(AddFavoriteResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<AddFavoriteResponseProto>() {
            override fun onMessage(message: AddFavoriteResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(ProtoUtils.createMessageProto(message))
        call.halfClose()
        call.request(1)
    }

    fun fetchUserId(username: String, callback: (String?, Boolean) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetUserIdRequestProto, GetUserIdResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetUserId")
            .setRequestMarshaller(GetUserIdRequestMarshaller())
            .setResponseMarshaller(GetUserIdResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetUserIdResponseProto>() {
            override fun onMessage(message: GetUserIdResponseProto) { callback(message.userId, message.found) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetUserIdRequestProto(username))
        call.halfClose()
        call.request(1)
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
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<EditMessageRequestProto, EditMessageResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/EditMessage")
            .setRequestMarshaller(EditMessageRequestMarshaller())
            .setResponseMarshaller(EditMessageResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<EditMessageResponseProto>() {
            override fun onMessage(message: EditMessageResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(EditMessageRequestProto(id, text))
        call.halfClose()
        call.request(1)
    }

    fun updateAvatar(username: String, avatarUrl: String, fullAvatarUrl: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<UpdateAvatarRequestProto, UpdateAvatarResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/UpdateAvatar")
            .setRequestMarshaller(UpdateAvatarRequestMarshaller())
            .setResponseMarshaller(UpdateAvatarResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<UpdateAvatarResponseProto>() {
            override fun onMessage(message: UpdateAvatarResponseProto) { 
                if (message.success) {
                    updateAvatarCache(username, avatarUrl, fullAvatarUrl)
                }
                callback(message.success, message.message) 
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) callback(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(UpdateAvatarRequestProto(username = username, avatarUrl = avatarUrl, fullAvatarUrl = fullAvatarUrl, userId = currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun getUserAvatar(username: String, userId: String = "", callback: (String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetUserAvatarRequestProto, GetUserAvatarResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetUserAvatar")
            .setRequestMarshaller(GetUserAvatarRequestMarshaller())
            .setResponseMarshaller(GetUserAvatarResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetUserAvatarResponseProto>() {
            override fun onMessage(message: GetUserAvatarResponseProto) { 
                updateAvatarCache(username, message.avatarUrl, message.fullAvatarUrl)
                callback(message.avatarUrl) 
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetUserAvatarRequestProto(username, userId))
        call.halfClose()
        call.request(1)
    }

    fun updateProfile(username: String, bio: String, status: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<UpdateProfileRequestProto, UpdateProfileResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/UpdateProfile")
            .setRequestMarshaller(UpdateProfileRequestMarshaller())
            .setResponseMarshaller(UpdateProfileResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<UpdateProfileResponseProto>() {
            override fun onMessage(message: UpdateProfileResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) callback(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(UpdateProfileRequestProto(username = username, bio = bio, status = status, userId = currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun getUserProfile(userId: String, callback: (GetUserProfileResponseProto?) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetUserProfileRequestProto, GetUserProfileResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetUserProfile")
            .setRequestMarshaller(GetUserProfileRequestMarshaller())
            .setResponseMarshaller(GetUserProfileResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetUserProfileResponseProto>() {
            override fun onMessage(message: GetUserProfileResponseProto) { callback(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) callback(null) }
        }, io.grpc.Metadata())
        call.sendMessage(GetUserProfileRequestProto(userId = userId))
        call.halfClose()
        call.request(1)
    }

    fun deleteMessage(m: Message) {
        val currentChannel = channel ?: return
        
        // Optimistic UI: remove locally first
        deletedMessageHashes.add(getMessageHash(m))
        _messages.update { current -> current.filterNot { it.id == m.id } }
        
        // Remove from persistent cache
        scope.launch(Dispatchers.IO) {
            db()?.messageDao()?.deleteMessage(m.id)
        }

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<DeleteMessagesRequestProto, DeleteMessagesResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/DeleteMessages")
            .setRequestMarshaller(DeleteMessagesRequestMarshaller())
            .setResponseMarshaller(DeleteMessagesResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<DeleteMessagesResponseProto>() {}, io.grpc.Metadata())
        call.sendMessage(DeleteMessagesRequestProto(listOf(ProtoUtils.createMessageProto(m)), currentUsername ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun deleteProfile(u: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<DeleteProfileRequestProto, DeleteProfileResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/DeleteProfile")
            .setRequestMarshaller(DeleteProfileRequestMarshaller())
            .setResponseMarshaller(DeleteProfileResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<DeleteProfileResponseProto>() {
            override fun onMessage(message: DeleteProfileResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(DeleteProfileRequestProto(username = u, userId = currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun clearSystemNotification() { _systemNotification.value = null }

    fun updateUsername(ou: String, nu: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<UpdateUsernameRequestProto, UpdateUsernameResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/UpdateUsername")
            .setRequestMarshaller(UpdateUsernameRequestMarshaller())
            .setResponseMarshaller(UpdateUsernameResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<UpdateUsernameResponseProto>() {
            override fun onMessage(message: UpdateUsernameResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(UpdateUsernameRequestProto(ou, nu, currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun updatePassword(u: String, op: String, np: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<UpdatePasswordRequestProto, UpdatePasswordResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/UpdatePassword")
            .setRequestMarshaller(UpdatePasswordRequestMarshaller())
            .setResponseMarshaller(UpdatePasswordResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<UpdatePasswordResponseProto>() {
            override fun onMessage(message: UpdatePasswordResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(UpdatePasswordRequestProto(u, op, np, currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun adminUpdatePassword(tu: String, np: String, au: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<AdminUpdatePasswordRequestProto, AdminUpdatePasswordResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/AdminUpdatePassword")
            .setRequestMarshaller(AdminUpdatePasswordRequestMarshaller())
            .setResponseMarshaller(AdminUpdatePasswordResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<AdminUpdatePasswordResponseProto>() {
            override fun onMessage(message: AdminUpdatePasswordResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(AdminUpdatePasswordRequestProto(tu, np, au, currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun requestPasswordReset(email: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<RequestPasswordResetRequestProto, RequestPasswordResetResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/RequestPasswordReset")
            .setRequestMarshaller(RequestPasswordResetRequestMarshaller())
            .setResponseMarshaller(RequestPasswordResetResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<RequestPasswordResetResponseProto>() {
            override fun onMessage(message: RequestPasswordResetResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(RequestPasswordResetRequestProto(email))
        call.halfClose()
        call.request(1)
    }

    fun resetPassword(token: String, newPw: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<ResetPasswordRequestProto, ResetPasswordResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/ResetPassword")
            .setRequestMarshaller(ResetPasswordRequestMarshaller())
            .setResponseMarshaller(ResetPasswordResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<ResetPasswordResponseProto>() {
            override fun onMessage(message: ResetPasswordResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(ResetPasswordRequestProto(token, newPw))
        call.halfClose()
        call.request(1)
    }

    fun markRead(rid: String, u: String, onComp: (() -> Unit)?) {
        // Dismiss push notifications for this room locally
        appContext?.let {
            lavender.client.android.data.fcm.LavenderMessagingService.dismissNotificationsForRoom(it, rid)
        }

        val currentChannel = channel
        if (currentChannel == null || _connectionStatus.value != ConnectionStatus.READY) {
            Log.d(TAG, "Queueing markRead for $rid because channel is not ready")
            pendingReads.add(rid)
            onComp?.invoke()
            return
        }

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<MarkReadRequestProto, MarkReadResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/MarkRead")
            .setRequestMarshaller(MarkReadRequestMarshaller())
            .setResponseMarshaller(MarkReadResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<MarkReadResponseProto>() {
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { 
                if (status.isOk) {
                    pendingReads.remove(rid)
                } else {
                    pendingReads.add(rid)
                }
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
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<DeleteChatRequestProto, DeleteChatResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/DeleteChat")
            .setRequestMarshaller(DeleteChatRequestMarshaller())
            .setResponseMarshaller(DeleteChatResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<DeleteChatResponseProto>() {
            override fun onMessage(message: DeleteChatResponseProto) {
                if (message.success) {
                    // Clear local messages when chat is successfully deleted
                    // Don't delete chat entry to avoid sync conflicts when chat is recreated with same ID
                    scope.launch(Dispatchers.IO) {
                        db()?.messageDao()?.clearRoom(cid)
                        Log.d(TAG, "Cleared local messages for deleted chat: $cid")
                    }
                }
                cb(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(DeleteChatRequestProto(cid, requesterUsername, currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun deleteChatWithUserId(cid: String, userId: String, username: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<DeleteChatRequestProto, DeleteChatResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/DeleteChat")
            .setRequestMarshaller(DeleteChatRequestMarshaller())
            .setResponseMarshaller(DeleteChatResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<DeleteChatResponseProto>() {
            override fun onMessage(message: DeleteChatResponseProto) {
                if (message.success) {
                    scope.launch(Dispatchers.IO) {
                        db()?.messageDao()?.clearRoom(cid)
                        Log.d(TAG, "Cleared local messages for deleted chat: $cid")
                    }
                }
                cb(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(DeleteChatRequestProto(cid, username, userId))
        call.halfClose()
        call.request(1)
    }

    fun createDirectChat(u1: String, u2: String, cb: (String?) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<CreateDirectChatRequestProto, CreateDirectChatResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/CreateDirectChat")
            .setRequestMarshaller(CreateDirectChatRequestMarshaller())
            .setResponseMarshaller(CreateDirectChatResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<CreateDirectChatResponseProto>() {
            override fun onMessage(message: CreateDirectChatResponseProto) { if (message.success) cb(message.chatId) else cb(null) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(null) }
        }, io.grpc.Metadata())
        
        // We know u1 is the current user if called from most places, but let's be careful.
        // Actually, usually user1 is the one who initiates.
        val u1Id = if (u1 == currentUsername) currentUserId ?: "" else ""
        call.sendMessage(CreateDirectChatRequestProto(u1, u2, u1Id, ""))
        call.halfClose()
        call.request(1)
    }

    fun createGroupChat(n: String, ps: List<String>, c: String, type: String = "group", cb: (String?) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<CreateGroupChatRequestProto, CreateGroupChatResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/CreateGroupChat")
            .setRequestMarshaller(CreateGroupChatRequestMarshaller())
            .setResponseMarshaller(CreateGroupChatResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<CreateGroupChatResponseProto>() {
            override fun onMessage(message: CreateGroupChatResponseProto) { if (message.success) cb(message.chatId) else cb(null) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(null) }
        }, io.grpc.Metadata())
        call.sendMessage(CreateGroupChatRequestProto(n, ps, c, currentUserId ?: "", emptyList(), type))
        call.halfClose()
        call.request(1)
    }

    fun updateChatAvatar(cid: String, a: String, u: String, fa: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<UpdateChatAvatarRequestProto, UpdateChatAvatarResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/UpdateChatAvatar")
            .setRequestMarshaller(UpdateChatAvatarRequestMarshaller())
            .setResponseMarshaller(UpdateChatAvatarResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<UpdateChatAvatarResponseProto>() {
            override fun onMessage(message: UpdateChatAvatarResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(UpdateChatAvatarRequestProto(cid, a, u, fa, currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun updateChatSettings(chatId: String, allowAdd: Boolean, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<UpdateChatSettingsRequestProto, UpdateChatSettingsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/UpdateChatSettings")
            .setRequestMarshaller(UpdateChatSettingsRequestMarshaller())
            .setResponseMarshaller(UpdateChatSettingsResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<UpdateChatSettingsResponseProto>() {
            override fun onMessage(message: UpdateChatSettingsResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) callback(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(UpdateChatSettingsRequestProto(chatId, allowAdd, currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun updateChatName(cid: String, n: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<UpdateChatNameRequestProto, UpdateChatNameResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/UpdateChatName")
            .setRequestMarshaller(UpdateChatNameRequestMarshaller())
            .setResponseMarshaller(UpdateChatNameResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<UpdateChatNameResponseProto>() {
            override fun onMessage(message: UpdateChatNameResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(UpdateChatNameRequestProto(cid, n))
        call.halfClose()
        call.request(1)
    }

    fun addParticipants(cid: String, us: List<String>, cb: (Boolean, String) -> Unit) {
        var completed = 0; var allSuccess = true; var lastMsg = ""
        if (us.isEmpty()) { cb(true, ""); return }
        us.forEach { u ->
            addParticipant(cid, u) { success, msg ->
                completed++; if (!success) allSuccess = false; lastMsg = msg
                if (completed == us.size) cb(allSuccess, lastMsg)
            }
        }
    }

    fun addParticipant(cid: String, u: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<AddParticipantRequestProto, AddParticipantResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/AddParticipant")
            .setRequestMarshaller(AddParticipantRequestMarshaller())
            .setResponseMarshaller(AddParticipantResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<AddParticipantResponseProto>() {
            override fun onMessage(message: AddParticipantResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        
        // Check if u is current user (unlikely for addParticipant but good for consistency)
        val uId = if (u == currentUsername) currentUserId ?: "" else ""
        call.sendMessage(AddParticipantRequestProto(cid, u, uId))
        call.halfClose()
        call.request(1)
    }

    fun removeParticipant(cid: String, u: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<RemoveParticipantRequestProto, RemoveParticipantResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/RemoveParticipant")
            .setRequestMarshaller(RemoveParticipantRequestMarshaller())
            .setResponseMarshaller(RemoveParticipantResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<RemoveParticipantResponseProto>() {
            override fun onMessage(message: RemoveParticipantResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        
        val uId = if (u == currentUsername) currentUserId ?: "" else ""
        call.sendMessage(RemoveParticipantRequestProto(cid, u, uId))
        call.halfClose()
        call.request(1)
    }

    fun addContact(u: String, cu: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<AddContactRequestProto, AddContactResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/AddContact")
            .setRequestMarshaller(AddContactRequestMarshaller())
            .setResponseMarshaller(AddContactResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<AddContactResponseProto>() {
            override fun onMessage(message: AddContactResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(AddContactRequestProto(username = u, contactUsername = cu, userId = currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun removeContact(u: String, cu: String, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<RemoveContactRequestProto, RemoveContactResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/RemoveContact")
            .setRequestMarshaller(RemoveContactRequestMarshaller())
            .setResponseMarshaller(RemoveContactResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<RemoveContactResponseProto>() {
            override fun onMessage(message: RemoveContactResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(RemoveContactRequestProto(username = u, contactUsername = cu, userId = currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun getContacts(u: String, cb: (List<String>) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetContactsRequestProto, GetContactsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetContacts")
            .setRequestMarshaller(GetContactsRequestMarshaller())
            .setResponseMarshaller(GetContactsResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetContactsResponseProto>() {
            override fun onMessage(message: GetContactsResponseProto) { cb(message.contacts) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetContactsRequestProto(username = u, userId = currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun loadAllUsers(cb: (List<UserInfoProto>) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetAllUsersRequestProto, GetAllUsersResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetAllUsers")
            .setRequestMarshaller(GetAllUsersRequestMarshaller())
            .setResponseMarshaller(GetAllUsersResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetAllUsersResponseProto>() {
            override fun onMessage(message: GetAllUsersResponseProto) { 
                Log.d(TAG, "GetAllUsers: received ${message.users.size} users")
                _allUsers.value = message.users
                _serverTime.value = message.serverTime
                cb(message.users) 
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    Log.e(TAG, "GetAllUsers failed: ${status.code} - ${status.description}")
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(GetAllUsersRequestProto())
        call.halfClose()
        call.request(1)
    }

    fun getAllChats(callback: (List<ChatInfo>) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetAllChatsRequestProto, GetAllChatsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetAllChats")
            .setRequestMarshaller(GetAllChatsRequestMarshaller())
            .setResponseMarshaller(GetAllChatsResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetAllChatsResponseProto>() {
            override fun onMessage(message: GetAllChatsResponseProto) {
                Log.d(TAG, "GetAllChats: received ${message.chats.size} chats")
                callback(message.chats.map { proto ->
                    ChatInfo(
                        id = proto.id,
                        name = proto.name,
                        type = proto.type,
                        participants = proto.participants,
                        createdAt = proto.createdAt?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                        unreadCount = proto.unreadCount,
                        lastMessageTime = proto.lastMessageTime?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                        creator = proto.creator,
                        lastMessageText = proto.lastMessageText,
                        avatarUrl = proto.avatarUrl,
                        fullAvatarUrl = proto.fullAvatarUrl,
                        lastMessageUsername = proto.lastMessageUsername,
                        isMuted = false,
                        lastMessageHasImage = proto.lastMessageHasImage,
                        allowMembersToAdd = proto.allowMembersToAdd,
                        conferenceStartTime = proto.conferenceStartTime?.let { it.seconds * 1000 + it.nanos / 1000000 } ?: 0L,
                        isSecret = proto.isSecret,
                        peerPublicKey = proto.peerPublicKey,
                        e2eeReady = proto.e2eeReady
                    )
                })
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    Log.e(TAG, "GetAllChats failed: ${status.code} - ${status.description}")
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(GetAllChatsRequestProto())
        call.halfClose()
        call.request(1)
    }

    fun getAIChats(userId: String, callback: (List<AIChatInfo>) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetAIChatsRequestProto, GetAIChatsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetAIChats")
            .setRequestMarshaller(GetAIChatsRequestMarshaller())
            .setResponseMarshaller(GetAIChatsResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetAIChatsResponseProto>() {
            override fun onMessage(message: GetAIChatsResponseProto) {
                Log.d(TAG, "GetAIChats: received ${message.chats.size} chats")
                callback(message.chats.map { proto ->
                    AIChatInfo(
                        id = proto.id,
                        name = proto.name,
                        type = proto.type,
                        createdAt = proto.createdAt
                    )
                })
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    Log.e(TAG, "GetAIChats failed: ${status.code} - ${status.description}")
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(GetAIChatsRequestProto().apply { this.userId = userId })
        call.halfClose()
        call.request(1)
    }

    fun renameAIChat(chatId: String, userId: String, newName: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<RenameAIChatRequestProto, RenameAIChatResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/RenameAIChat")
            .setRequestMarshaller(RenameAIChatRequestMarshaller())
            .setResponseMarshaller(RenameAIChatResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<RenameAIChatResponseProto>() {
            override fun onMessage(message: RenameAIChatResponseProto) {
                callback(message.success, message.error)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())
        call.sendMessage(RenameAIChatRequestProto().apply {
            this.chatId = chatId
            this.userId = userId
            this.newName = newName
        })
        call.halfClose()
        call.request(1)
    }

    fun getChatListVersion(u: String, cb: (Long) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetChatListVersionRequestProto, GetChatListVersionResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetChatListVersion")
            .setRequestMarshaller(GetChatListVersionRequestMarshaller())
            .setResponseMarshaller(GetChatListVersionResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetChatListVersionResponseProto>() {
            override fun onMessage(message: GetChatListVersionResponseProto) { cb(message.version) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetChatListVersionRequestProto(username = u, userId = currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun getThemes(u: String, cb: (String, List<CustomThemeProto>) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetThemesRequestProto, GetThemesResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetThemes")
            .setRequestMarshaller(GetThemesRequestMarshaller())
            .setResponseMarshaller(GetThemesResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetThemesResponseProto>() {
            override fun onMessage(message: GetThemesResponseProto) { cb(message.currentThemeId, message.customThemes) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(GetThemesRequestProto(username = u, userId = currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun saveTheme(u: String, t: CustomThemeProto, cb: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<SaveThemeRequestProto, SaveThemeResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SaveTheme")
            .setRequestMarshaller(SaveThemeRequestMarshaller())
            .setResponseMarshaller(SaveThemeResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<SaveThemeResponseProto>() {
            override fun onMessage(message: SaveThemeResponseProto) { cb(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) { if (!status.isOk) cb(false, status.description ?: "Error") }
        }, io.grpc.Metadata())
        call.sendMessage(SaveThemeRequestProto(username = u, theme = t, userId = currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun setCurrentTheme(u: String, tid: String, cb: (Boolean) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<SetCurrentThemeRequestProto, SetCurrentThemeResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SetCurrentTheme")
            .setRequestMarshaller(SetCurrentThemeRequestMarshaller())
            .setResponseMarshaller(SetCurrentThemeResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<SetCurrentThemeResponseProto>() {
            override fun onMessage(message: SetCurrentThemeResponseProto) { cb(message.success) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(SetCurrentThemeRequestProto(username = u, themeId = tid, userId = currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun deleteTheme(u: String, tid: String, cb: (Boolean) -> Unit) {
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<DeleteThemeRequestProto, DeleteThemeResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/DeleteTheme")
            .setRequestMarshaller(DeleteThemeRequestMarshaller())
            .setResponseMarshaller(DeleteThemeResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<DeleteThemeResponseProto>() {
            override fun onMessage(message: DeleteThemeResponseProto) { cb(message.success) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
        }, io.grpc.Metadata())
        call.sendMessage(DeleteThemeRequestProto(username = u, themeId = tid, userId = currentUserId ?: ""))
        call.halfClose()
        call.request(1)
    }

    fun getFCMLogs(cb: (List<FCMLogEntryProto>) -> Unit) {
        val currentChannel = channel ?: return
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
        val currentChannel = channel ?: return
        
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
}

// MARSHALLERS
class MessageProtoMarshaller : io.grpc.MethodDescriptor.Marshaller<MessageProto> {
    override fun stream(value: MessageProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.id.isNotEmpty()) cos.writeString(1, value.id)
        if (value.user.isNotEmpty()) cos.writeString(2, value.user)
        if (value.text.isNotEmpty()) cos.writeString(3, value.text)
        value.createdAt?.let { cos.writeTag(4, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED); val b = it.toByteArray(); cos.writeUInt32NoTag(b.size); cos.writeRawBytes(b) }

        // Serialize reactions if they exist
        if (value.reactions.isNotEmpty()) {
            val rbaos = java.io.ByteArrayOutputStream()
            val rcos = com.google.protobuf.CodedOutputStream.newInstance(rbaos)
            for (reaction in value.reactions) {
                val singleRbaos = java.io.ByteArrayOutputStream()
                val singleRcos = com.google.protobuf.CodedOutputStream.newInstance(singleRbaos)
                if (reaction.user.isNotEmpty()) singleRcos.writeString(1, reaction.user)
                if (reaction.emoji.isNotEmpty()) singleRcos.writeString(2, reaction.emoji)
                singleRcos.flush()
                val rb = singleRbaos.toByteArray()
                cos.writeTag(5, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
                cos.writeUInt32NoTag(rb.size)
                cos.writeRawBytes(rb)
            }
        }

        if (value.password.isNotEmpty()) cos.writeString(6, value.password)
        if (value.repliedToMessageId.isNotEmpty()) cos.writeString(7, value.repliedToMessageId)
        if (value.repliedToUser.isNotEmpty()) cos.writeString(8, value.repliedToUser)
        if (value.repliedToText.isNotEmpty()) cos.writeString(9, value.repliedToText)
        if (value.roomId.isNotEmpty()) cos.writeString(10, value.roomId)
        if (value.isRead) cos.writeBool(11, value.isRead)
        if (value.avatarUrl.isNotEmpty()) cos.writeString(12, value.avatarUrl)
        if (value.imageUrl.isNotEmpty()) cos.writeString(13, value.imageUrl)
        if (value.edited) cos.writeBool(14, value.edited)
        if (value.clientVersion.isNotEmpty()) cos.writeString(15, value.clientVersion)
        if (value.isSuperAdmin) cos.writeBool(16, value.isSuperAdmin)
        if (value.voiceUrl.isNotEmpty()) cos.writeString(17, value.voiceUrl)
        if (value.duration != 0) cos.writeInt32(18, value.duration)
        if (value.register) cos.writeBool(19, value.register)
        // Serialize imageUrls for gallery support (field 20)
        if (value.imageUrls.isNotEmpty()) {
            for (imageUrl in value.imageUrls) {
                cos.writeString(20, imageUrl)
            }
        }
        if (value.deviceId.isNotEmpty()) cos.writeString(21, value.deviceId)
        if (value.deviceName.isNotEmpty()) cos.writeString(22, value.deviceName)
        if (value.userId.isNotEmpty()) cos.writeString(23, value.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): MessageProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream); val builder = MessageProto.newBuilder()
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> builder.setId(cis.readString()); 2 -> builder.setUser(cis.readString()); 3 -> builder.setText(cis.readString())
                4 -> { val l = cis.readUInt32(); builder.setCreatedAt(Timestamp.parseFrom(cis.readRawBytes(l))) }
                5 -> {
                    val l = cis.readUInt32()
                    val reactionCis = com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(l))
                    var rUser = ""
                    var rEmoji = ""
                    while (!reactionCis.isAtEnd) {
                        val rTag = reactionCis.readTag()
                        if (rTag == 0) break
                        when (com.google.protobuf.WireFormat.getTagFieldNumber(rTag)) {
                            1 -> rUser = reactionCis.readString()
                            2 -> rEmoji = reactionCis.readString()
                            else -> reactionCis.skipField(rTag)
                        }
                    }
                    if (rUser.isNotEmpty() && rEmoji.isNotEmpty()) {
                        builder.addReaction(ReactionProto(rUser, rEmoji))
                    }
                }
                6 -> builder.setPassword(cis.readString()); 7 -> builder.setRepliedToMessageId(cis.readString()); 8 -> builder.setRepliedToUser(cis.readString()); 9 -> builder.setRepliedToText(cis.readString())
                10 -> builder.setRoomId(cis.readString()); 11 -> builder.setIsRead(cis.readBool()); 12 -> builder.setAvatarUrl(cis.readString()); 13 -> builder.setImageUrl(cis.readString())
                14 -> builder.setEdited(cis.readBool()); 15 -> builder.setClientVersion(cis.readString()); 16 -> builder.setIsSuperAdmin(cis.readBool()); 17 -> builder.setVoiceUrl(cis.readString()); 18 -> builder.setDuration(cis.readInt32())
                19 -> builder.setRegister(cis.readBool())
                20 -> builder.addImageUrls(cis.readString()) // Parse imageUrls for gallery support
                21 -> builder.setDeviceId(cis.readString())
                22 -> builder.setDeviceName(cis.readString())
                23 -> builder.setUserId(cis.readString())
                else -> cis.skipField(tag)
            }
        }
        return builder.build()
    }
}

class TypingRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<TypingRequestProto> {
    override fun stream(v: TypingRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.roomId.isNotEmpty()) cos.writeString(1, v.roomId); if (v.username.isNotEmpty()) cos.writeString(2, v.username); if (v.isTyping) cos.writeBool(3, v.isTyping)
        if (v.userId.isNotEmpty()) cos.writeString(4, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): TypingRequestProto = TypingRequestProto()
}

class TypingSignalMarshaller : io.grpc.MethodDescriptor.Marshaller<TypingSignalProto> {
    override fun stream(v: TypingSignalProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): TypingSignalProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var rid = ""; var u = ""; var it = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> rid = cis.readString(); 2 -> u = cis.readString(); 3 -> it = cis.readBool(); else -> cis.skipField(tag) } }
        return TypingSignalProto(rid, u, it)
    }
}

class GetHistoryRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetHistoryRequestProto> {
    override fun stream(v: GetHistoryRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.limit != 0) cos.writeInt32(1, v.limit); if (v.room.isNotEmpty()) cos.writeString(2, v.room)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetHistoryRequestProto = GetHistoryRequestProto()
}

class GetHistoryResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetHistoryResponseProto> {
    override fun stream(v: GetHistoryResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetHistoryResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val msgs = mutableListOf<MessageProto>(); val mm = MessageProtoMarshaller()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) { val len = cis.readUInt32(); msgs.add(mm.parse(java.io.ByteArrayInputStream(cis.readRawBytes(len)))) } else cis.skipField(tag) }
        return GetHistoryResponseProto(msgs)
    }
}

class GetChatsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetChatsRequestProto> {
    override fun stream(v: GetChatsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetChatsRequestProto = GetChatsRequestProto()
}

class GetChatsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetChatsResponseProto> {
    override fun stream(v: GetChatsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetChatsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val chats = mutableListOf<ChatInfoProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break;             if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                val len = cis.readUInt32(); val b = cis.readRawBytes(len); val cisis = com.google.protobuf.CodedInputStream.newInstance(b)
                var id = ""; var n = ""; var t = ""; var p = ""; var ca: Timestamp? = null; var uc = 0; var lmt: Timestamp? = null; var cr = ""; var lmtxt = ""; var au = ""; var fau = ""; var lmu = ""; var lmhi = false; var amta = false; var cst: Timestamp? = null; var isSecret = false; var peerKey = ""; var e2eeReady = false; var activeAgentId = ""; var agentMode = ""
                while (!cisis.isAtEnd) { val t2 = cisis.readTag(); if (t2 == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(t2)) { 1 -> id = cisis.readString(); 2 -> n = cisis.readString(); 3 -> t = cisis.readString(); 4 -> p = cisis.readString(); 5 -> { val l = cisis.readUInt32(); ca = Timestamp.parseFrom(cisis.readRawBytes(l)) }; 6 -> uc = cisis.readInt32(); 7 -> { val l = cisis.readUInt32(); lmt = Timestamp.parseFrom(cisis.readRawBytes(l)) }; 8 -> cr = cisis.readString(); 9 -> lmtxt = cisis.readString(); 10 -> au = cisis.readString(); 11 -> fau = cisis.readString(); 12 -> lmu = cisis.readString(); 13 -> lmhi = cisis.readBool(); 14 -> amta = cisis.readBool(); 15 -> isSecret = cisis.readBool(); 16 -> peerKey = cisis.readString(); 17 -> e2eeReady = cisis.readBool(); 20 -> activeAgentId = cisis.readString(); 21 -> agentMode = cisis.readString(); else -> cisis.skipField(t2) } }
                chats.add(ChatInfoProto(id, n, t, p, ca, uc, lmt, cr, lmtxt, au, fau, lmu, lmhi, amta, cst, isSecret, peerKey, e2eeReady, activeAgentId, agentMode))
            } else cis.skipField(tag)
        }
        return GetChatsResponseProto(chats)
    }
}

class TokenRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<TokenRequestProto> {
    override fun stream(v: TokenRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.user.isNotEmpty()) cos.writeString(1, v.user); if (v.token.isNotEmpty()) cos.writeString(2, v.token); if (v.pushEnabled) cos.writeBool(3, v.pushEnabled)
        if (v.userId.isNotEmpty()) cos.writeString(4, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): TokenRequestProto = TokenRequestProto()
}

class TokenResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<TokenResponseProto> {
    override fun stream(v: TokenResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): TokenResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return TokenResponseProto(ok)
    }
}

class SaveDraftRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SaveDraftRequestProto> {
    override fun stream(v: SaveDraftRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(1, v.userId)
        cos.writeString(2, v.roomId)
        cos.writeString(3, v.draftText)
        cos.writeString(4, v.repliedToMessageId)
        cos.writeString(5, v.repliedToUser)
        cos.writeString(6, v.repliedToText)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SaveDraftRequestProto = SaveDraftRequestProto()
}

class SaveDraftResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SaveDraftResponseProto> {
    override fun stream(v: SaveDraftResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SaveDraftResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return SaveDraftResponseProto(ok, msg)
    }
}

class GetDraftRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetDraftRequestProto> {
    override fun stream(v: GetDraftRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(1, v.userId)
        cos.writeString(2, v.roomId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetDraftRequestProto = GetDraftRequestProto()
}

class GetDraftResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetDraftResponseProto> {
    override fun stream(v: GetDraftResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetDraftResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var dt = ""; var rmid = ""; var ru = ""; var rt = ""; var hd = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> dt = cis.readString(); 2 -> rmid = cis.readString(); 3 -> ru = cis.readString(); 4 -> rt = cis.readString(); 5 -> hd = cis.readBool(); else -> cis.skipField(tag) } }
        return GetDraftResponseProto(dt, rmid, ru, rt, hd)
    }
}

class DeleteDraftRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteDraftRequestProto> {
    override fun stream(v: DeleteDraftRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(1, v.userId)
        cos.writeString(2, v.roomId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteDraftRequestProto = DeleteDraftRequestProto()
}

class DeleteDraftResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteDraftResponseProto> {
    override fun stream(v: DeleteDraftResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteDraftResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return DeleteDraftResponseProto(ok)
    }
}

class GetMutedChatsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetMutedChatsRequestProto> {
    override fun stream(v: GetMutedChatsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetMutedChatsRequestProto = GetMutedChatsRequestProto()
}

class GetMutedChatsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetMutedChatsResponseProto> {
    override fun stream(v: GetMutedChatsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetMutedChatsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val ids = mutableListOf<String>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ids.add(cis.readString()) else cis.skipField(tag) }
        return GetMutedChatsResponseProto(ids)
    }
}

class SetMutedChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SetMutedChatRequestProto> {
    override fun stream(v: SetMutedChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId); if (v.roomId.isNotEmpty()) cos.writeString(2, v.roomId); if (v.muted) cos.writeBool(3, v.muted)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SetMutedChatRequestProto = SetMutedChatRequestProto()
}

class SetMutedChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SetMutedChatResponseProto> {
    override fun stream(v: SetMutedChatResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SetMutedChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return SetMutedChatResponseProto(ok)
    }
}

class GetUserIdRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserIdRequestProto> {
    override fun stream(v: GetUserIdRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetUserIdRequestProto = GetUserIdRequestProto()
}

class GetUserIdResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserIdResponseProto> {
    override fun stream(v: GetUserIdResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetUserIdResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var uid = ""; var f = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> uid = cis.readString(); 2 -> f = cis.readBool(); else -> cis.skipField(tag) } }
        return GetUserIdResponseProto(uid, f)
    }
}

class AddFavoriteRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<AddFavoriteRequestProto> {
    override fun stream(v: AddFavoriteRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId); if (v.messageId.isNotEmpty()) cos.writeString(2, v.messageId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): AddFavoriteRequestProto = AddFavoriteRequestProto()
}

class AddFavoriteResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<AddFavoriteResponseProto> {
    override fun stream(v: AddFavoriteResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): AddFavoriteResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return AddFavoriteResponseProto(ok, msg)
    }
}

class RemoveFavoriteRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveFavoriteRequestProto> {
    override fun stream(v: RemoveFavoriteRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId); if (v.messageId.isNotEmpty()) cos.writeString(2, v.messageId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RemoveFavoriteRequestProto = RemoveFavoriteRequestProto()
}

class RemoveFavoriteResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveFavoriteResponseProto> {
    override fun stream(v: RemoveFavoriteResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): RemoveFavoriteResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return RemoveFavoriteResponseProto(ok)
    }
}

class GetFavoritesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetFavoritesRequestProto> {
    override fun stream(v: GetFavoritesRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetFavoritesRequestProto = GetFavoritesRequestProto()
}

class GetFavoritesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetFavoritesResponseProto> {
    override fun stream(v: GetFavoritesResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetFavoritesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val msgs = mutableListOf<MessageProto>(); val mm = MessageProtoMarshaller()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) { val len = cis.readUInt32(); msgs.add(mm.parse(java.io.ByteArrayInputStream(cis.readRawBytes(len)))) } else cis.skipField(tag) }
        return GetFavoritesResponseProto(msgs)
    }
}

class EditMessageRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<EditMessageRequestProto> {
    override fun stream(v: EditMessageRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.messageId.isNotEmpty()) cos.writeString(1, v.messageId); if (v.text.isNotEmpty()) cos.writeString(2, v.text)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): EditMessageRequestProto = EditMessageRequestProto()
}

class EditMessageResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<EditMessageResponseProto> {
    override fun stream(v: EditMessageResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): EditMessageResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return EditMessageResponseProto(ok, msg)
    }
}

class MarkReadRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<MarkReadRequestProto> {
    override fun stream(v: MarkReadRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.roomId.isNotEmpty()) cos.writeString(1, v.roomId); if (v.username.isNotEmpty()) cos.writeString(2, v.username); if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): MarkReadRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var rid = ""; var u = ""; var uid = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> rid = cis.readString(); 2 -> u = cis.readString(); 3 -> uid = cis.readString(); else -> cis.skipField(tag) } }
        return MarkReadRequestProto(rid, u, uid)
    }
}

class MarkReadResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<MarkReadResponseProto> {
    override fun stream(v: MarkReadResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): MarkReadResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return MarkReadResponseProto(ok)
    }
}

class DeleteChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteChatRequestProto> {
    override fun stream(v: DeleteChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId)
        if (v.requesterUsername.isNotEmpty()) cos.writeString(2, v.requesterUsername)
        if (v.requesterUserId.isNotEmpty()) cos.writeString(3, v.requesterUserId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteChatRequestProto = DeleteChatRequestProto()
}

class DeleteChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteChatResponseProto> {
    override fun stream(v: DeleteChatResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return DeleteChatResponseProto(ok, msg)
    }
}

class UpdateAvatarRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateAvatarRequestProto> {
    override fun stream(v: UpdateAvatarRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.avatarUrl.isNotEmpty()) cos.writeString(2, v.avatarUrl); if (v.fullAvatarUrl.isNotEmpty()) cos.writeString(3, v.fullAvatarUrl); if (v.userId.isNotEmpty()) cos.writeString(4, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateAvatarRequestProto = UpdateAvatarRequestProto()
}

class UpdateAvatarResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateAvatarResponseProto> {
    override fun stream(v: UpdateAvatarResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateAvatarResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return UpdateAvatarResponseProto(ok, msg)
    }
}

class GetUserAvatarRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserAvatarRequestProto> {
    override fun stream(v: GetUserAvatarRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username)
        if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetUserAvatarRequestProto = GetUserAvatarRequestProto()
}

class GetUserAvatarResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserAvatarResponseProto> {
    override fun stream(v: GetUserAvatarResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetUserAvatarResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var au = ""; var fau = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> au = cis.readString(); 2 -> fau = cis.readString(); else -> cis.skipField(tag) } }
        return GetUserAvatarResponseProto(au, fau)
    }
}

class UpdateProfileRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateProfileRequestProto> {
    override fun stream(v: UpdateProfileRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.bio.isNotEmpty()) cos.writeString(2, v.bio); if (v.status.isNotEmpty()) cos.writeString(3, v.status); if (v.userId.isNotEmpty()) cos.writeString(4, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateProfileRequestProto = UpdateProfileRequestProto()
}

class UpdateProfileResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateProfileResponseProto> {
    override fun stream(v: UpdateProfileResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateProfileResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return UpdateProfileResponseProto(ok, msg)
    }
}

class GetUserProfileRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserProfileRequestProto> {
    override fun stream(v: GetUserProfileRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetUserProfileRequestProto = GetUserProfileRequestProto()
}

class GetUserProfileResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserProfileResponseProto> {
    override fun stream(v: GetUserProfileResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetUserProfileResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var u = ""; var b = ""; var st = ""; var au = ""; var ls: com.google.protobuf.Timestamp? = null; var fau = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> u = cis.readString(); 2 -> b = cis.readString(); 3 -> st = cis.readString(); 4 -> au = cis.readString(); 5 -> { val len = cis.readUInt32(); ls = ProtoUtils.parseTimestampFromProto(java.io.ByteArrayInputStream(cis.readRawBytes(len))) }; 6 -> fau = cis.readString(); else -> cis.skipField(tag) } }
        return GetUserProfileResponseProto(u, b, st, au, ls, fau)
    }
}

class DeleteMessagesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteMessagesRequestProto> {
    override fun stream(v: DeleteMessagesRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos); val mm = MessageProtoMarshaller()
        for (m in v.messages) { val b = mm.stream(m).readBytes(); cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED); cos.writeUInt32NoTag(b.size); cos.writeRawBytes(b) }
        if (v.requesterUsername.isNotEmpty()) cos.writeString(2, v.requesterUsername)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteMessagesRequestProto = DeleteMessagesRequestProto()
}

class DeleteMessagesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteMessagesResponseProto> {
    override fun stream(v: DeleteMessagesResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteMessagesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return DeleteMessagesResponseProto(ok)
    }
}

class UpdateUsernameRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateUsernameRequestProto> {
    override fun stream(v: UpdateUsernameRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.oldUsername.isNotEmpty()) cos.writeString(1, v.oldUsername); if (v.newUsername.isNotEmpty()) cos.writeString(2, v.newUsername)
        if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateUsernameRequestProto = UpdateUsernameRequestProto()
}

class UpdateUsernameResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateUsernameResponseProto> {
    override fun stream(v: UpdateUsernameResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateUsernameResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return UpdateUsernameResponseProto(ok, msg)
    }
}

class UpdatePasswordRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdatePasswordRequestProto> {
    override fun stream(v: UpdatePasswordRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.oldPassword.isNotEmpty()) cos.writeString(2, v.oldPassword); if (v.newPassword.isNotEmpty()) cos.writeString(3, v.newPassword)
        if (v.userId.isNotEmpty()) cos.writeString(4, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdatePasswordRequestProto = UpdatePasswordRequestProto()
}

class UpdatePasswordResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdatePasswordResponseProto> {
    override fun stream(v: UpdatePasswordResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdatePasswordResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return UpdatePasswordResponseProto(ok, msg)
    }
}

class CreateDirectChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateDirectChatRequestProto> {
    override fun stream(v: CreateDirectChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.user1.isNotEmpty()) cos.writeString(1, v.user1); if (v.user2.isNotEmpty()) cos.writeString(2, v.user2)
        if (v.user1Id.isNotEmpty()) cos.writeString(3, v.user1Id); if (v.user2Id.isNotEmpty()) cos.writeString(4, v.user2Id)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): CreateDirectChatRequestProto = CreateDirectChatRequestProto()
}

class CreateDirectChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateDirectChatResponseProto> {
    override fun stream(v: CreateDirectChatResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): CreateDirectChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var cid = ""; var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> cid = cis.readString(); 2 -> ok = cis.readBool(); else -> cis.skipField(tag) } }
        return CreateDirectChatResponseProto(cid, ok)
    }
}

class CreateGroupChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateGroupChatRequestProto> {
    override fun stream(v: CreateGroupChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.name.isNotEmpty()) cos.writeString(1, v.name); for (p in v.participants) cos.writeString(2, p); if (v.creator.isNotEmpty()) cos.writeString(3, v.creator)
        if (v.creatorId.isNotEmpty()) cos.writeString(4, v.creatorId); for (pid in v.participantIds) cos.writeString(5, pid)
        if (v.type.isNotEmpty()) cos.writeString(6, v.type)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): CreateGroupChatRequestProto = CreateGroupChatRequestProto()
}

class CreateGroupChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateGroupChatResponseProto> {
    override fun stream(v: CreateGroupChatResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): CreateGroupChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var cid = ""; var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> cid = cis.readString(); 2 -> ok = cis.readBool(); else -> cis.skipField(tag) } }
        return CreateGroupChatResponseProto(cid, ok)
    }
}

class GetAllUsersRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetAllUsersRequestProto> {
    override fun stream(v: GetAllUsersRequestProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetAllUsersRequestProto = GetAllUsersRequestProto()
}

class UserInfoProtoMarshaller : io.grpc.MethodDescriptor.Marshaller<UserInfoProto> {
    override fun stream(v: UserInfoProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username)
        if (v.avatarUrl.isNotEmpty()) cos.writeString(2, v.avatarUrl)
        if (v.lastClientVersion.isNotEmpty()) cos.writeString(3, v.lastClientVersion)
        v.lastSeenAt?.let { cos.writeTag(4, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED); val b = it.toByteArray(); cos.writeUInt32NoTag(b.size); cos.writeRawBytes(b) }
        if (v.email.isNotEmpty()) cos.writeString(5, v.email)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UserInfoProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var u = ""; var a = ""; var v = ""; var ls: Timestamp? = null; var e = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> u = cis.readString(); 2 -> a = cis.readString(); 3 -> v = cis.readString()
                4 -> { val l = cis.readUInt32(); ls = Timestamp.parseFrom(cis.readRawBytes(l)) }
                5 -> e = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return UserInfoProto(u, a, v, ls, e)
    }
}

class GetAllUsersResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetAllUsersResponseProto> {
    override fun stream(v: GetAllUsersResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetAllUsersResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val users = mutableListOf<UserInfoProto>(); val um = UserInfoProtoMarshaller(); var serverTime: com.google.protobuf.Timestamp? = null
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> { val len = cis.readUInt32(); users.add(um.parse(java.io.ByteArrayInputStream(cis.readRawBytes(len)))) } 2 -> { val len = cis.readUInt32(); serverTime = ProtoUtils.parseTimestampFromProto(java.io.ByteArrayInputStream(cis.readRawBytes(len))) } else -> cis.skipField(tag) } }
        return GetAllUsersResponseProto(users, serverTime)
    }
}

class UpdateChatAvatarRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatAvatarRequestProto> {
    override fun stream(v: UpdateChatAvatarRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId); if (v.avatarUrl.isNotEmpty()) cos.writeString(2, v.avatarUrl); if (v.username.isNotEmpty()) cos.writeString(3, v.username); if (v.fullAvatarUrl.isNotEmpty()) cos.writeString(4, v.fullAvatarUrl)
        if (v.userId.isNotEmpty()) cos.writeString(5, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateChatAvatarRequestProto = UpdateChatAvatarRequestProto()
}

class UpdateChatAvatarResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatAvatarResponseProto> {
    override fun stream(v: UpdateChatAvatarResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateChatAvatarResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return UpdateChatAvatarResponseProto(ok, msg)
    }
}

class AddParticipantRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<AddParticipantRequestProto> {
    override fun stream(v: AddParticipantRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId); if (v.username.isNotEmpty()) cos.writeString(2, v.username)
        if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): AddParticipantRequestProto = AddParticipantRequestProto()
}

class AddParticipantResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<AddParticipantResponseProto> {
    override fun stream(v: AddParticipantResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): AddParticipantResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return AddParticipantResponseProto(ok, msg)
    }
}

class RemoveParticipantRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveParticipantRequestProto> {
    override fun stream(v: RemoveParticipantRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId); if (v.username.isNotEmpty()) cos.writeString(2, v.username)
        if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RemoveParticipantRequestProto = RemoveParticipantRequestProto()
}

class RemoveParticipantResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveParticipantResponseProto> {
    override fun stream(v: RemoveParticipantResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): RemoveParticipantResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return RemoveParticipantResponseProto(ok, msg)
    }
}

class AddContactRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<AddContactRequestProto> {
    override fun stream(v: AddContactRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.contactUsername.isNotEmpty()) cos.writeString(2, v.contactUsername); if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): AddContactRequestProto = AddContactRequestProto()
}

class AddContactResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<AddContactResponseProto> {
    override fun stream(v: AddContactResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): AddContactResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return AddContactResponseProto(ok, msg)
    }
}

class RemoveContactRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveContactRequestProto> {
    override fun stream(v: RemoveContactRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.contactUsername.isNotEmpty()) cos.writeString(2, v.contactUsername); if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RemoveContactRequestProto = RemoveContactRequestProto()
}

class RemoveContactResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveContactResponseProto> {
    override fun stream(v: RemoveContactResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): RemoveContactResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return RemoveContactResponseProto(ok, msg)
    }
}

class GetContactsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetContactsRequestProto> {
    override fun stream(v: GetContactsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetContactsRequestProto = GetContactsRequestProto()
}

class GetContactsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetContactsResponseProto> {
    override fun stream(v: GetContactsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetContactsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val contacts = mutableListOf<String>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) contacts.add(cis.readString()) else cis.skipField(tag) }
        return GetContactsResponseProto(contacts)
    }
}

class GetAllChatsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetAllChatsRequestProto> {
    override fun stream(v: GetAllChatsRequestProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetAllChatsRequestProto = GetAllChatsRequestProto()
}

class GetAllChatsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetAllChatsResponseProto> {
    override fun stream(v: GetAllChatsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetAllChatsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val chats = mutableListOf<ChatInfoProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                val len = cis.readUInt32(); val b = cis.readRawBytes(len); val cisis = com.google.protobuf.CodedInputStream.newInstance(b)
                var id = ""; var n = ""; var t = ""; var p = ""; var ca: Timestamp? = null; var uc = 0; var lmt: Timestamp? = null; var cr = ""; var lmtxt = ""; var au = ""; var fau = ""; var lmu = ""; var lmhi = false; var amta = false; var cst: Timestamp? = null; var isSecret = false; var peerKey = ""; var e2eeReady = false; var activeAgentId = ""; var agentMode = ""
                while (!cisis.isAtEnd) { val t2 = cisis.readTag(); if (t2 == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(t2)) { 1 -> id = cisis.readString(); 2 -> n = cisis.readString(); 3 -> t = cisis.readString(); 4 -> p = cisis.readString(); 5 -> { val l = cisis.readUInt32(); ca = Timestamp.parseFrom(cisis.readRawBytes(l)) }; 6 -> uc = cisis.readInt32(); 7 -> { val l = cisis.readUInt32(); lmt = Timestamp.parseFrom(cisis.readRawBytes(l)) }; 8 -> cr = cisis.readString(); 9 -> lmtxt = cisis.readString(); 10 -> au = cisis.readString(); 11 -> fau = cisis.readString(); 12 -> lmu = cisis.readString(); 13 -> lmhi = cisis.readBool(); 14 -> amta = cisis.readBool(); 15 -> isSecret = cisis.readBool(); 16 -> peerKey = cisis.readString(); 17 -> e2eeReady = cisis.readBool(); 20 -> activeAgentId = cisis.readString(); 21 -> agentMode = cisis.readString(); else -> cisis.skipField(t2) } }
                chats.add(ChatInfoProto(id, n, t, p, ca, uc, lmt, cr, lmtxt, au, fau, lmu, lmhi, amta, cst, isSecret, peerKey, e2eeReady, activeAgentId, agentMode))
            } else cis.skipField(tag)
        }
        return GetAllChatsResponseProto(chats)
    }
}

class GetChatListVersionRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetChatListVersionRequestProto> {
    override fun stream(v: GetChatListVersionRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetChatListVersionRequestProto = GetChatListVersionRequestProto()
}

class GetChatListVersionResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetChatListVersionResponseProto> {
    override fun stream(v: GetChatListVersionResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetChatListVersionResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var v = 0L
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) v = cis.readInt64() else cis.skipField(tag) }
        return GetChatListVersionResponseProto(v)
    }
}

class GetThemesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetThemesRequestProto> {
    override fun stream(v: GetThemesRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetThemesRequestProto = GetThemesRequestProto()
}

class GetThemesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetThemesResponseProto> {
    override fun stream(v: GetThemesResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetThemesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var tid = ""; val themes = mutableListOf<CustomThemeProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> tid = cis.readString()
                2 -> {
                    val len = cis.readUInt32(); val b = cis.readRawBytes(len); val cisis = com.google.protobuf.CodedInputStream.newInstance(b)
                    var id = ""; var name = ""; var pc = ""; var opc = ""; var sc = ""; var osc = ""; var bc = ""; var tpc = ""; var tsc = ""; var clbu = ""; var cbu = ""; var bpc = ""; var obpc = ""; var sctr = ""; var obc = ""; var ibc = ""; var idark = false
                    while (!cisis.isAtEnd) { val t2 = cisis.readTag(); if (t2 == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(t2)) { 1 -> id = cisis.readString(); 2 -> name = cisis.readString(); 3 -> pc = cisis.readString(); 4 -> opc = cisis.readString(); 5 -> sc = cisis.readString(); 6 -> osc = cisis.readString(); 7 -> bc = cisis.readString(); 8 -> tpc = cisis.readString(); 9 -> tsc = cisis.readString(); 10 -> idark = cisis.readBool(); 11 -> cbu = cisis.readString(); 12 -> clbu = cisis.readString(); 13 -> bpc = cisis.readString(); 14 -> obpc = cisis.readString(); 15 -> sctr = cisis.readString(); 16 -> obc = cisis.readString(); 17 -> ibc = cisis.readString(); else -> cisis.skipField(t2) } }
                    themes.add(CustomThemeProto(id, name, pc, opc, sc, osc, bc, tpc, tsc, clbu, cbu, bpc, obpc, sctr, obc, ibc))
                }
                else -> cis.skipField(tag)
            } }
        return GetThemesResponseProto(tid, themes)
    }
}

class SaveThemeRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SaveThemeRequestProto> {
    override fun stream(v: SaveThemeRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username)
        cos.writeTag(2, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED); val tbaos = java.io.ByteArrayOutputStream(); val tcos = com.google.protobuf.CodedOutputStream.newInstance(tbaos); val th = v.theme
        if (th.id.isNotEmpty()) tcos.writeString(1, th.id); if (th.name.isNotEmpty()) tcos.writeString(2, th.name); if (th.primaryColor.isNotEmpty()) tcos.writeString(3, th.primaryColor); if (th.onPrimaryColor.isNotEmpty()) tcos.writeString(4, th.onPrimaryColor); if (th.surfaceColor.isNotEmpty()) tcos.writeString(5, th.surfaceColor); if (th.onSurfaceColor.isNotEmpty()) tcos.writeString(6, th.onSurfaceColor); if (th.backgroundColor.isNotEmpty()) tcos.writeString(7, th.backgroundColor); if (th.textPrimaryColor.isNotEmpty()) tcos.writeString(8, th.textPrimaryColor); if (th.textSecondaryColor.isNotEmpty()) tcos.writeString(9, th.textSecondaryColor); if (th.chatBackgroundImageUrl.isNotEmpty()) tcos.writeString(11, th.chatBackgroundImageUrl); if (th.chatListBackgroundImageUrl.isNotEmpty()) tcos.writeString(12, th.chatListBackgroundImageUrl); if (th.bottomPanelColor.isNotEmpty()) tcos.writeString(13, th.bottomPanelColor); if (th.onBottomPanelColor.isNotEmpty()) tcos.writeString(14, th.onBottomPanelColor); if (th.surfaceContainer.isNotEmpty()) tcos.writeString(15, th.surfaceContainer); if (th.outgoingBubbleColor.isNotEmpty()) tcos.writeString(16, th.outgoingBubbleColor); if (th.incomingBubbleColor.isNotEmpty()) tcos.writeString(17, th.incomingBubbleColor)
        tcos.flush(); val tb = tbaos.toByteArray(); cos.writeUInt32NoTag(tb.size); cos.writeRawBytes(tb)
        if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SaveThemeRequestProto = SaveThemeRequestProto()
}

class SaveThemeResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SaveThemeResponseProto> {
    override fun stream(v: SaveThemeResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SaveThemeResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return SaveThemeResponseProto(ok, msg)
    }
}

class SetCurrentThemeRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SetCurrentThemeRequestProto> {
    override fun stream(v: SetCurrentThemeRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.themeId.isNotEmpty()) cos.writeString(2, v.themeId); if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SetCurrentThemeRequestProto = SetCurrentThemeRequestProto()
}

class SetCurrentThemeResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SetCurrentThemeResponseProto> {
    override fun stream(v: SetCurrentThemeResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SetCurrentThemeResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return SetCurrentThemeResponseProto(ok)
    }
}

class DeleteThemeRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteThemeRequestProto> {
    override fun stream(v: DeleteThemeRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.themeId.isNotEmpty()) cos.writeString(2, v.themeId); if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteThemeRequestProto = DeleteThemeRequestProto()
}

class DeleteThemeResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteThemeResponseProto> {
    override fun stream(v: DeleteThemeResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteThemeResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return DeleteThemeResponseProto(ok)
    }
}

class GetFCMLogsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetFCMLogsRequestProto> {
    override fun stream(v: GetFCMLogsRequestProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetFCMLogsRequestProto = GetFCMLogsRequestProto()
}

class GetFCMLogsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetFCMLogsResponseProto> {
    override fun stream(v: GetFCMLogsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetFCMLogsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val logs = mutableListOf<FCMLogEntryProto>()
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                val len = cis.readUInt32(); val b = cis.readRawBytes(len); val cisis = com.google.protobuf.CodedInputStream.newInstance(b)
                var ts = ""; var lvl = ""; var msg = ""
                while (!cisis.isAtEnd) { val t2 = cisis.readTag(); if (t2 == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(t2)) { 1 -> ts = cisis.readString(); 2 -> lvl = cisis.readString(); 3 -> msg = cisis.readString(); else -> cisis.skipField(t2) } }
                logs.add(FCMLogEntryProto(ts, lvl, msg))
            } else cis.skipField(tag) }
        return GetFCMLogsResponseProto(logs)
    }
}

class ReactionRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<ReactionRequestProto> {
    override fun stream(v: ReactionRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.messageId.isNotEmpty()) cos.writeString(1, v.messageId)
        cos.writeTag(2, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED); val rbaos = java.io.ByteArrayOutputStream(); val rcos = com.google.protobuf.CodedOutputStream.newInstance(rbaos)
        if (v.reaction.user.isNotEmpty()) rcos.writeString(1, v.reaction.user); if (v.reaction.emoji.isNotEmpty()) rcos.writeString(2, v.reaction.emoji)
        rcos.flush(); val rb = rbaos.toByteArray(); cos.writeUInt32NoTag(rb.size); cos.writeRawBytes(rb)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): ReactionRequestProto = ReactionRequestProto()
}

class ReactionResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<ReactionResponseProto> {
    override fun stream(v: ReactionResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ReactionResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return ReactionResponseProto(ok)
    }
}

class DeleteProfileRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteProfileRequestProto> {
    override fun stream(v: DeleteProfileRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username); if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteProfileRequestProto = DeleteProfileRequestProto()
}

class DeleteProfileResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteProfileResponseProto> {
    override fun stream(v: DeleteProfileResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteProfileResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return DeleteProfileResponseProto(ok, msg)
    }
}

class UpdateChatSettingsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatSettingsRequestProto> {
    override fun stream(v: UpdateChatSettingsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId)
        cos.writeBool(2, v.allowMembersToAdd)
        if (v.userId.isNotEmpty()) cos.writeString(3, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateChatSettingsRequestProto = UpdateChatSettingsRequestProto()
}

class UpdateChatSettingsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatSettingsResponseProto> {
    override fun stream(v: UpdateChatSettingsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateChatSettingsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return UpdateChatSettingsResponseProto(ok, msg)
    }
}

class UpdateChatNameRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatNameRequestProto> {
    override fun stream(v: UpdateChatNameRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId); if (v.newName.isNotEmpty()) cos.writeString(2, v.newName)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): UpdateChatNameRequestProto = UpdateChatNameRequestProto()
}

class UpdateChatNameResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatNameResponseProto> {
    override fun stream(v: UpdateChatNameResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): UpdateChatNameResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return UpdateChatNameResponseProto(ok, msg)
    }
}

class RequestPasswordResetRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RequestPasswordResetRequestProto> {
    override fun stream(v: RequestPasswordResetRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.email.isNotEmpty()) cos.writeString(1, v.email)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RequestPasswordResetRequestProto = RequestPasswordResetRequestProto()
}

class RequestPasswordResetResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RequestPasswordResetResponseProto> {
    override fun stream(v: RequestPasswordResetResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): RequestPasswordResetResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return RequestPasswordResetResponseProto(ok, msg)
    }
}

class ResetPasswordRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<ResetPasswordRequestProto> {
    override fun stream(v: ResetPasswordRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.token.isNotEmpty()) cos.writeString(1, v.token)
        if (v.newPassword.isNotEmpty()) cos.writeString(2, v.newPassword)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): ResetPasswordRequestProto = ResetPasswordRequestProto()
}

class GetDevicesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetDevicesRequestProto> {
    override fun stream(v: GetDevicesRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetDevicesRequestProto = GetDevicesRequestProto()
}

class GetDevicesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetDevicesResponseProto> {
    override fun stream(v: GetDevicesResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetDevicesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); val devices = mutableListOf<DeviceInfoProto>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                val len = cis.readUInt32(); val b = cis.readRawBytes(len); val cisis = com.google.protobuf.CodedInputStream.newInstance(b)
                var id = ""; var name = ""; var cv = ""; var ts: com.google.protobuf.Timestamp? = null; var ip = ""
                while (!cisis.isAtEnd) {
                    val t2 = cisis.readTag(); if (t2 == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(t2)) {
                        1 -> id = cisis.readString(); 2 -> name = cisis.readString(); 3 -> cv = cisis.readString()
                        4 -> { val l2 = cisis.readUInt32(); ts = com.google.protobuf.Timestamp.parseFrom(cisis.readRawBytes(l2)) }
                        5 -> ip = cisis.readString(); else -> cisis.skipField(t2)
                    }
                }
                devices.add(DeviceInfoProto(id, name, cv, ts, ip))
            } else cis.skipField(tag)
        }
        return GetDevicesResponseProto(devices)
    }
}

class DeleteDeviceRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteDeviceRequestProto> {
    override fun stream(v: DeleteDeviceRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId); if (v.deviceId.isNotEmpty()) cos.writeString(2, v.deviceId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteDeviceRequestProto = DeleteDeviceRequestProto()
}

class DeleteDeviceResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteDeviceResponseProto> {
    override fun stream(v: DeleteDeviceResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteDeviceResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag)
            }
        }
        return DeleteDeviceResponseProto(ok, msg)
    }
}

class ResetPasswordResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<ResetPasswordResponseProto> {
    override fun stream(v: ResetPasswordResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): ResetPasswordResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return ResetPasswordResponseProto(ok, msg)
    }
}

class AdminUpdatePasswordRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<AdminUpdatePasswordRequestProto> {
    override fun stream(v: AdminUpdatePasswordRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.targetUsername.isNotEmpty()) cos.writeString(1, v.targetUsername)
        if (v.newPassword.isNotEmpty()) cos.writeString(2, v.newPassword)
        if (v.adminUsername.isNotEmpty()) cos.writeString(3, v.adminUsername)
        if (v.adminUserId.isNotEmpty()) cos.writeString(4, v.adminUserId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): AdminUpdatePasswordRequestProto = AdminUpdatePasswordRequestProto()
}

class AdminUpdatePasswordResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<AdminUpdatePasswordResponseProto> {
    override fun stream(v: AdminUpdatePasswordResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): AdminUpdatePasswordResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return AdminUpdatePasswordResponseProto(ok, msg)
    }
}

class CallMessageProtoMarshaller : io.grpc.MethodDescriptor.Marshaller<CallMessageProto> {
    override fun stream(v: CallMessageProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.callId.isNotEmpty()) cos.writeString(1, v.callId)
        if (v.senderId.isNotEmpty()) cos.writeString(2, v.senderId)
        if (v.receiverId.isNotEmpty()) cos.writeString(3, v.receiverId)
        cos.writeEnum(4, v.type.value)
        if (v.payload.isNotEmpty()) cos.writeString(5, v.payload)
        if (v.senderName.isNotEmpty()) cos.writeString(6, v.senderName)
        if (v.receiverName.isNotEmpty()) cos.writeString(7, v.receiverName)
        if (v.roomId.isNotEmpty()) cos.writeString(8, v.roomId)
        cos.flush(); return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): CallMessageProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var cid = ""; var sid = ""; var rid = ""; var t = 0; var p = ""; var sn = ""; var rn = ""; var rm = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> cid = cis.readString()
                2 -> sid = cis.readString()
                3 -> rid = cis.readString()
                4 -> t = cis.readEnum()
                5 -> p = cis.readString()
                6 -> sn = cis.readString()
                7 -> rn = cis.readString()
                8 -> rm = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        val result = CallMessageProto(cid, sid, rid, CallMessageProto.Type.fromInt(t), p, sn, rn, rm)
        return result
    }

    // Flag to control E2EE support based on client version
    var isE2EEMessageEnabled: Boolean = true
}

// ======= AI Chats Marshallers =======

class GetAIChatsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetAIChatsRequestProto> {
    override fun stream(v: GetAIChatsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetAIChatsRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var userId = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> userId = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return GetAIChatsRequestProto(userId)
    }
}

class GetAIChatsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetAIChatsResponseProto> {
    override fun stream(v: GetAIChatsResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetAIChatsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val chats = mutableListOf<AIChatInfoProto>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                val len = cis.readUInt32()
                val b = cis.readRawBytes(len)
                val inner = com.google.protobuf.CodedInputStream.newInstance(b)
                var id = ""; var name = ""; var type = ""; var createdAt = ""
                var isUsingCustomKey = false; var model = ""
                while (!inner.isAtEnd) {
                    val t2 = inner.readTag()
                    if (t2 == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(t2)) {
                        1 -> id = inner.readString()
                        2 -> name = inner.readString()
                        3 -> type = inner.readString()
                        4 -> createdAt = inner.readString()
                        5 -> isUsingCustomKey = inner.readBool()
                        6 -> model = inner.readString()
                        else -> inner.skipField(t2)
                    }
                }
                chats.add(AIChatInfoProto(id, name, type, createdAt, isUsingCustomKey, model))
            } else {
                cis.skipField(tag)
            }
        }
        return GetAIChatsResponseProto(chats)
    }
}

class RenameAIChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RenameAIChatRequestProto> {
    override fun stream(v: RenameAIChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId)
        if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
        if (v.newName.isNotEmpty()) cos.writeString(3, v.newName)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RenameAIChatRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var chatId = ""; var userId = ""; var newName = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> chatId = cis.readString()
                2 -> userId = cis.readString()
                3 -> newName = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return RenameAIChatRequestProto(chatId, userId, newName)
    }
}

class RenameAIChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RenameAIChatResponseProto> {
    override fun stream(v: RenameAIChatResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): RenameAIChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false; var error = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> error = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return RenameAIChatResponseProto(success, error)
    }
}

// ======= Auth V2 Marshallers =======

class SignInRequestV2Marshaller : io.grpc.MethodDescriptor.Marshaller<SignInRequestV2Proto> {
    override fun stream(v: SignInRequestV2Proto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username)
        if (v.password.isNotEmpty()) cos.writeString(2, v.password)
        if (v.deviceId.isNotEmpty() || v.deviceName.isNotEmpty()) {
            val deviceBytes = java.io.ByteArrayOutputStream()
            val deviceCos = com.google.protobuf.CodedOutputStream.newInstance(deviceBytes)
            if (v.deviceId.isNotEmpty()) deviceCos.writeString(1, v.deviceId)
            if (v.deviceName.isNotEmpty()) deviceCos.writeString(2, v.deviceName)
            deviceCos.flush()
            cos.writeByteArray(3, deviceBytes.toByteArray())
        }
        if (v.clientVersion.isNotEmpty()) cos.writeString(4, v.clientVersion)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SignInRequestV2Proto = SignInRequestV2Proto()
}

class AuthResponseV2Marshaller : io.grpc.MethodDescriptor.Marshaller<AuthResponseV2Proto> {
    override fun stream(v: AuthResponseV2Proto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): AuthResponseV2Proto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false
        var message = ""
        var accessToken = ""
        var refreshToken = ""
        var accessExpiresAt = 0L
        var refreshExpiresAt = 0L
        var userId = ""
        var username = ""
        var email = ""
        var avatarUrl = ""
        var bio = ""
        var status = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> message = cis.readString()
                3 -> accessToken = cis.readString()
                4 -> refreshToken = cis.readString()
                5 -> accessExpiresAt = cis.readInt64()
                6 -> refreshExpiresAt = cis.readInt64()
                7 -> {
                    val userLen = cis.readRawVarint32()
                    val userBytes = cis.readRawBytes(userLen)
                    val userCis = com.google.protobuf.CodedInputStream.newInstance(userBytes)
                    while (!userCis.isAtEnd) {
                        val utag = userCis.readTag()
                        if (utag == 0) break
                        when (com.google.protobuf.WireFormat.getTagFieldNumber(utag)) {
                            1 -> userId = userCis.readString()
                            2 -> username = userCis.readString()
                            3 -> email = userCis.readString()
                            5 -> avatarUrl = userCis.readString()
                            6 -> bio = userCis.readString()
                            7 -> status = userCis.readString()
                            else -> userCis.skipField(utag)
                        }
                    }
                }
                else -> cis.skipField(tag)
            }
        }
        return AuthResponseV2Proto(
            success = success, message = message,
            accessToken = accessToken, refreshToken = refreshToken,
            accessExpiresAt = accessExpiresAt, refreshExpiresAt = refreshExpiresAt,
            userId = userId, username = username, email = email,
            avatarUrl = avatarUrl, bio = bio, status = status
        )
    }
}

class SignUpRequestV2Marshaller : io.grpc.MethodDescriptor.Marshaller<SignUpRequestV2Proto> {
    override fun stream(v: SignUpRequestV2Proto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.username.isNotEmpty()) cos.writeString(1, v.username)
        if (v.password.isNotEmpty()) cos.writeString(2, v.password)
        if (v.email.isNotEmpty()) cos.writeString(3, v.email)
        if (v.deviceId.isNotEmpty() || v.deviceName.isNotEmpty()) {
            val deviceBytes = java.io.ByteArrayOutputStream()
            val deviceCos = com.google.protobuf.CodedOutputStream.newInstance(deviceBytes)
            if (v.deviceId.isNotEmpty()) deviceCos.writeString(1, v.deviceId)
            if (v.deviceName.isNotEmpty()) deviceCos.writeString(2, v.deviceName)
            deviceCos.flush()
            cos.writeByteArray(4, deviceBytes.toByteArray())
        }
        if (v.clientVersion.isNotEmpty()) cos.writeString(5, v.clientVersion)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SignUpRequestV2Proto = SignUpRequestV2Proto()
}

class RefreshTokenRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RefreshTokenRequestProto> {
    override fun stream(v: RefreshTokenRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.refreshToken.isNotEmpty()) cos.writeString(1, v.refreshToken)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RefreshTokenRequestProto = RefreshTokenRequestProto()
}

class RefreshTokenResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RefreshTokenResponseProto> {
    override fun stream(v: RefreshTokenResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): RefreshTokenResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var accessToken = ""
        var refreshToken = ""
        var accessExpiresAt = 0L
        var refreshExpiresAt = 0L
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> accessToken = cis.readString()
                2 -> refreshToken = cis.readString()
                3 -> accessExpiresAt = cis.readInt64()
                4 -> refreshExpiresAt = cis.readInt64()
                else -> cis.skipField(tag)
            }
        }
        return RefreshTokenResponseProto(
            accessToken = accessToken, refreshToken = refreshToken,
            accessExpiresAt = accessExpiresAt, refreshExpiresAt = refreshExpiresAt
        )
    }
}

class SignOutRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SignOutRequestProto> {
    override fun stream(v: SignOutRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.refreshToken.isNotEmpty()) cos.writeString(1, v.refreshToken)
        if (v.allDevices) cos.writeBool(2, v.allDevices)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SignOutRequestProto = SignOutRequestProto()
}

class SimpleAuthResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SimpleAuthResponseProto> {
    override fun stream(v: SimpleAuthResponseProto): java.io.InputStream = java.io.ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SimpleAuthResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var success = false
        var message = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> message = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return SimpleAuthResponseProto(success = success, message = message)
    }
}

class RevokeDeviceRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RevokeDeviceRequestProto> {
    override fun stream(v: RevokeDeviceRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.deviceId.isNotEmpty()) cos.writeString(1, v.deviceId)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): RevokeDeviceRequestProto = RevokeDeviceRequestProto()
}
