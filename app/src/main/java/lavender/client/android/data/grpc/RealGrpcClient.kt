package lavender.client.android.data.grpc

import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import lavender.client.android.BuildConfig
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.Reaction
import lavender.client.android.data.models.ChatInfo
import com.google.protobuf.Timestamp
import lavender.client.android.data.proto.MessageProto
import lavender.client.android.data.proto.ReactionProto
import lavender.client.android.data.proto.ReactionRequestProto
import lavender.client.android.data.proto.ReactionResponseProto
import lavender.client.android.data.proto.ProtoUtils
import lavender.client.android.data.proto.GetHistoryRequestProto
import lavender.client.android.data.proto.GetHistoryResponseProto
import lavender.client.android.data.proto.DeleteMessagesRequestProto
import lavender.client.android.data.proto.DeleteMessagesResponseProto
import lavender.client.android.data.proto.TokenRequestProto
import lavender.client.android.data.proto.TokenResponseProto
import lavender.client.android.data.proto.ChatInfoProto
import lavender.client.android.data.proto.GetChatsRequestProto
import lavender.client.android.data.proto.GetChatsResponseProto
import lavender.client.android.data.proto.CreateDirectChatRequestProto
import lavender.client.android.data.proto.CreateDirectChatResponseProto
import lavender.client.android.data.proto.CreateGroupChatRequestProto
import lavender.client.android.data.proto.CreateGroupChatResponseProto
import lavender.client.android.data.proto.UpdateUsernameRequestProto
import lavender.client.android.data.proto.UpdateUsernameResponseProto
import lavender.client.android.data.proto.UpdatePasswordRequestProto
import lavender.client.android.data.proto.UpdatePasswordResponseProto
import lavender.client.android.data.proto.MarkReadRequestProto
import lavender.client.android.data.proto.MarkReadResponseProto
import lavender.client.android.data.proto.DeleteChatRequestProto
import lavender.client.android.data.proto.DeleteChatResponseProto
import lavender.client.android.data.proto.UpdateAvatarRequestProto
import lavender.client.android.data.proto.UpdateAvatarResponseProto
import lavender.client.android.data.proto.UpdateProfileRequestProto
import lavender.client.android.data.proto.UpdateProfileResponseProto
import lavender.client.android.data.proto.GetUserAvatarRequestProto
import lavender.client.android.data.proto.GetUserAvatarResponseProto
import lavender.client.android.data.proto.GetUserProfileRequestProto
import lavender.client.android.data.proto.GetUserProfileResponseProto
import lavender.client.android.data.proto.DeleteProfileRequestProto
import lavender.client.android.data.proto.DeleteProfileResponseProto
import lavender.client.android.data.proto.TypingRequestProto
import lavender.client.android.data.proto.TypingSignalProto
import lavender.client.android.data.proto.AddParticipantRequestProto
import lavender.client.android.data.proto.AddParticipantResponseProto
import lavender.client.android.data.proto.AddContactRequestProto
import lavender.client.android.data.proto.AddContactResponseProto
import lavender.client.android.data.proto.GetChatListVersionRequestProto
import lavender.client.android.data.proto.GetChatListVersionResponseProto
import lavender.client.android.data.proto.GetThemesRequestProto
import lavender.client.android.data.proto.GetThemesResponseProto
import lavender.client.android.data.proto.SaveThemeRequestProto
import lavender.client.android.data.proto.SaveThemeResponseProto
import lavender.client.android.data.proto.SetCurrentThemeRequestProto
import lavender.client.android.data.proto.SetCurrentThemeResponseProto
import lavender.client.android.data.proto.DeleteThemeRequestProto
import lavender.client.android.data.proto.DeleteThemeResponseProto
import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.data.proto.RemoveContactRequestProto
import lavender.client.android.data.proto.RemoveContactResponseProto
import lavender.client.android.data.proto.GetContactsRequestProto
import lavender.client.android.data.proto.GetContactsResponseProto
import lavender.client.android.data.proto.RemoveParticipantRequestProto
import lavender.client.android.data.proto.RemoveParticipantResponseProto
import lavender.client.android.data.proto.EditMessageRequestProto
import lavender.client.android.data.proto.EditMessageResponseProto
import lavender.client.android.data.proto.UpdateChatNameRequestProto
import lavender.client.android.data.proto.UpdateChatNameResponseProto
import lavender.client.android.data.proto.UpdateChatAvatarRequestProto
import lavender.client.android.data.proto.UpdateChatAvatarResponseProto
import lavender.client.android.data.proto.GetAllChatsRequestProto
import lavender.client.android.data.proto.GetAllChatsResponseProto
import lavender.client.android.data.proto.GetUserIdResponseProto
import java.util.concurrent.TimeUnit

enum class ConnectionStatus {
    DISCONNECTED, // Отключен вручную или не инициализирован
    CONNECTING,   // Пытается установить соединение
    READY,        // Соединение установлено, можно слать сообщения
    FAILED        // Ошибка (сервер упал или нет интернета)
}

object RealGrpcClient {
    private var channel: ManagedChannel? = null
    private var requestObserver: StreamObserver<MessageProto>? = null
    private var typingRequestObserver: StreamObserver<TypingRequestProto>? = null
    var currentServerAddress: String? = null
    var currentRoomId = ""
        private set

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _isSuperAdmin = MutableStateFlow(false)
    val isSuperAdmin: StateFlow<Boolean> = _isSuperAdmin

    private val _serverVersion = MutableStateFlow("")
    val serverVersion: StateFlow<String> = _serverVersion

    fun updateMessage(message: Message) {
        _messages.update { currentList ->
            currentList.map { if (it.id == message.id) message else it }
        }
    }

    private val _users = MutableStateFlow<List<String>>(emptyList())
    val users: StateFlow<List<String>> = _users

    private val _allUsers = MutableStateFlow<List<String>>(emptyList())
    val allUsers: StateFlow<List<String>> = _allUsers

    private val _systemNotification = MutableStateFlow<String?>(null)
    val systemNotification: StateFlow<String?> = _systemNotification

    private val _typingUsers = MutableStateFlow<Map<String, Set<String>>>(emptyMap()) // roomId -> set of usernames
    val typingUsers: StateFlow<Map<String, Set<String>>> = _typingUsers

    // Avatar cache (thumbnail URLs for lists)
    private val avatarCache = mutableMapOf<String, String>()
    private val _avatarCacheFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val avatarCacheFlow: StateFlow<Map<String, String>> = _avatarCacheFlow

    // Full avatar cache (high-res URLs for full screen view)
    private val fullAvatarCache = mutableMapOf<String, String>()

    private var isChatStarted = false
    private val sentMessageHashes = mutableSetOf<String>() // Track sent messages to prevent echo
    private val deletedMessageHashes = mutableSetOf<String>()
    private var appContext: android.content.Context? = null

    var hasCheckedForUpdates = false
    
    var isAppInBackground = false
    
    // Reconnection management
    private var reconnectAttempts = 0
    private var reconnectHandler: android.os.Handler? = null
    private var reconnectRunnable: Runnable? = null
    private var isMonitoring = false

    fun connect(serverAddress: String, useTls: Boolean = false, port: Int = 50051, context: android.content.Context? = null) {
        if (context != null) {
            this.appContext = context
            loadDeletedMessages()
        }

        // Проверяем: если мы уже READY или CONNECTING к тому же адресу — не перезапускаем
        val currentStatus = _connectionStatus.value
        if ((currentStatus == ConnectionStatus.READY || currentStatus == ConnectionStatus.CONNECTING) && currentServerAddress == serverAddress) {
            android.util.Log.d("GrpcClient", "Already connecting/connected to $serverAddress (status: $currentStatus)")
            return
        }

        try {
            android.util.Log.d("GrpcClient", "Connecting to Go server at $serverAddress:$port")

            // 1. Полностью сносим старое перед созданием нового
            disconnect()

            // 2. Сразу ставим статус "В процессе подключения"
            _connectionStatus.value = ConnectionStatus.CONNECTING

            val builder = OkHttpChannelBuilder.forAddress(serverAddress, port)
            if (useTls) builder.useTransportSecurity() else builder.usePlaintext()

            builder.directExecutor()
            builder.maxInboundMessageSize(16 * 1024 * 1024)
            builder.maxInboundMetadataSize(1024 * 1024)

            // 3. Тонкая настройка KeepAlive для мобильных сетей
            builder.keepAliveTime(10, TimeUnit.SECONDS) // Пинг сервера каждые 10 сек
                .keepAliveTimeout(3, TimeUnit.SECONDS)  // Ждем ответа на пинг 3 сек
                .keepAliveWithoutCalls(true)            // Пингуем даже если чат простаивает
                .idleTimeout(1, TimeUnit.MINUTES)       // Не закрываем канал слишком быстро

            val newChannel = builder.build()
            channel = newChannel
            currentServerAddress = serverAddress

            // 4. ЗАПУСКАЕМ «ДВИЖОК» МОНИТОРИНГА
            startChannelMonitoring(newChannel)

            if (!isAppInBackground) {
                _error.value = null
            }

        } catch (e: Exception) {
            android.util.Log.e("GrpcClient", "Connection failed: ${e.message}")
            _connectionStatus.value = ConnectionStatus.FAILED
        }
    }

    private fun startChannelMonitoring(monitoredChannel: ManagedChannel) {
        // Если уже мониторим этот канал — выходим
        if (isMonitoring) return
        isMonitoring = true

        fun checkState() {
            // Force connection if IDLE
            val grpcState = monitoredChannel.getState(true)

            // Маппим состояние gRPC на наш понятный UI-статус
            val newStatus = when (grpcState) {
                io.grpc.ConnectivityState.READY -> ConnectionStatus.READY
                io.grpc.ConnectivityState.CONNECTING,
                io.grpc.ConnectivityState.IDLE -> ConnectionStatus.CONNECTING
                io.grpc.ConnectivityState.TRANSIENT_FAILURE -> ConnectionStatus.FAILED
                io.grpc.ConnectivityState.SHUTDOWN -> ConnectionStatus.DISCONNECTED
                else -> ConnectionStatus.DISCONNECTED
            }

            // Обновляем StateFlow, который слушает твой UI
            if (_connectionStatus.value != newStatus) {
                _connectionStatus.value = newStatus

                // ЕСЛИ СТАЛО READY — АВТОМАТИЧЕСКИ ЗАХОДИМ В ЧАТ
                if (newStatus == ConnectionStatus.READY) {
                    lastUsername?.let { user ->
                        android.util.Log.d("GrpcClient", "Channel READY, auto-starting chat for $user")
                        startChat(user, lastPassword ?: "", lastJoinMessage ?: "", lastOnMessageReceived ?: {})
                    }
                }
            }

            // Рекурсивно подписываемся на следующее изменение стейта
            monitoredChannel.notifyWhenStateChanged(grpcState) {
                // Проверяем, что канал не сменился на другой в процессе
                if (channel == monitoredChannel) {
                    checkState()
                } else {
                    isMonitoring = false
                }
            }
        }

        checkState()
    }

    private fun loadDeletedMessages() {
        appContext?.getSharedPreferences("deleted_messages", android.content.Context.MODE_PRIVATE)?.let { prefs ->
            val saved = prefs.getStringSet("hashes", emptySet()) ?: emptySet()
            deletedMessageHashes.clear()
            deletedMessageHashes.addAll(saved)
        }
    }

    private fun saveDeletedMessages() {
        appContext?.getSharedPreferences("deleted_messages", android.content.Context.MODE_PRIVATE)?.edit()?.let { editor ->
            editor.putStringSet("hashes", deletedMessageHashes)
            editor.apply()
        }
    }

    private fun getMessageHash(message: Message): String {
        if (message.id.isNotEmpty()) return "id:${message.id}"
        return "${message.user}:${message.text}:${message.imageUrl}:${message.voiceUrl}:${message.timestamp / 1000}"
    }

    fun loadHistory(roomId: String = "general", onComplete: () -> Unit = {}) {
        if (roomId.isEmpty()) {
            onComplete()
            return
        }
        val currentChannel = channel
        if (currentChannel == null) {
            onComplete()
            return
        }

        android.util.Log.d("RealGrpcClient", "Loading history for room: $roomId")

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetHistoryRequestProto, GetHistoryResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetHistory")
            .setRequestMarshaller(GetHistoryRequestMarshaller())
            .setResponseMarshaller(GetHistoryResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        val request = GetHistoryRequestProto(limit = 200, room = roomId)

        call.start(object : io.grpc.ClientCall.Listener<GetHistoryResponseProto>() {
            override fun onMessage(message: GetHistoryResponseProto) {
                val historyMessages = message.messages
                    .filterNot { it.text.endsWith(" joined") || it.text.endsWith(" присоединился") }
                    .map { proto ->
                        android.util.Log.d("RealGrpcClient", "HISTORY PROTO: id=${proto.id}, user=${proto.user}, text='${proto.text}', imageUrl='${proto.imageUrl}', voiceUrl='${proto.voiceUrl}'")
                        val msg = ProtoUtils.createMessageFromProto(proto)
                        android.util.Log.d("RealGrpcClient", "HISTORY MSG: id=${msg.id}, text='${msg.text}', imageUrl='${msg.imageUrl}', voiceUrl='${msg.voiceUrl}'")
                        msg
                    }
                    .filterNot { deletedMessageHashes.contains(getMessageHash(it)) }

                android.util.Log.d("RealGrpcClient", "Received ${historyMessages.size} history messages for room: $roomId")

                _messages.update { currentList ->
                    (currentList + historyMessages).distinctBy { getMessageHash(it) }.sortedBy { it.timestamp }
                }

                // Load avatars for history messages after they are added
                historyMessages.forEach { msg ->
                    if (msg.avatarUrl.isEmpty() && !avatarCache.containsKey(msg.user)) {
                        getUserAvatar(msg.user) { avatarUrl ->
                            if (avatarUrl.isNotEmpty()) {
                                avatarCache[msg.user] = avatarUrl
                                // Update messages with avatar URL, preserving imageUrl
                                _messages.update { currentList ->
                                    currentList.map { if (it.id == msg.id && it.id.isNotEmpty()) it.copy(avatarUrl = avatarUrl, imageUrl = it.imageUrl) else it }
                                }
                            }
                        }
                    } else if (msg.avatarUrl.isEmpty() && avatarCache.containsKey(msg.user)) {
                        // Use cached avatar URL
                        avatarCache[msg.user]?.let { cachedUrl ->
                            // Update messages with cached avatar URL, preserving imageUrl
                            _messages.update { currentList ->
                                currentList.map { if (it.id == msg.id && it.id.isNotEmpty()) it.copy(avatarUrl = cachedUrl, imageUrl = it.imageUrl) else it }
                            }
                        }
                    }
                }
            }

            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                android.util.Log.d("RealGrpcClient", "History load completed with status: ${status.code}")
                if (!status.isOk && (status.code == io.grpc.Status.Code.UNAVAILABLE || status.code == io.grpc.Status.Code.INTERNAL)) {
                    android.util.Log.w("RealGrpcClient", "History load failed, retrying in 2 seconds...")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        loadHistory(currentRoomId, onComplete)
                    }, 2000)
                } else {
                    onComplete()
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }
    
    fun loadUsers() {
        val currentChannel = channel
        if (currentChannel == null) return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<Unit, List<String>>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetClients")
            .setRequestMarshaller(EmptyMarshaller())
            .setResponseMarshaller(ClientListResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<List<String>>() {
            override fun onMessage(message: List<String>) { _users.value = message }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("RealGrpcClient", "GetClients failed: ${status.code} - ${status.description}")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(Unit)
        call.halfClose()
        call.request(1)
    }

    fun loadAllUsers(callback: ((List<String>) -> Unit) = {}) {
        val currentChannel = channel
        if (currentChannel == null) {
            callback(emptyList())
            return
        }
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<Unit, List<String>>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetAllUsers")
            .setRequestMarshaller(EmptyMarshaller())
            .setResponseMarshaller(ClientListResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<List<String>>() {
            override fun onMessage(message: List<String>) { 
                _allUsers.value = message 
                callback(message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("RealGrpcClient", "GetAllUsers failed: ${status.code} - ${status.description}")
                    callback(emptyList())
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(Unit)
        call.halfClose()
        call.request(1)
    }

    fun disconnect() {
        try {
            isChatStarted = false
            requestObserver?.onCompleted()
            requestObserver = null
            typingRequestObserver?.onCompleted()
            typingRequestObserver = null
            channel?.shutdownNow()
            channel = null
            currentServerAddress = null

            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            isMonitoring = false

            _isSuperAdmin.value = false
            sentMessageHashes.clear()
            
            // Cancel any pending reconnection attempts
            if (reconnectRunnable != null) {
                reconnectHandler?.removeCallbacks(reconnectRunnable!!)
            }
            reconnectAttempts = 0
            reconnectHandler = null
            reconnectRunnable = null
        } catch (_: Exception) {}
    }

    fun clearSystemNotification() {
        _systemNotification.value = null
    }

    fun setRoomId(roomId: String) {
        if (this.currentRoomId != roomId) {
            this.currentRoomId = roomId
            // Send room switch signal if already connected
            if (isChatStarted) {
                requestObserver?.onNext(MessageProto.newBuilder()
                    .setUser(lastUsername ?: "")
                    .setRoomId(roomId)
                    .setText("") // Empty text acts as a room switch signal
                    .setCreatedAt(ProtoUtils.getCurrentTimestamp())
                    .setClientVersion(BuildConfig.VERSION_NAME)
                    .build())
            }
        }
    }
    
    private var lastUsername: String? = null
    private var lastUserId: String? = null
    private var lastJoinMessage: String? = null
    private var lastPassword: String? = null
    private var lastOnMessageReceived: ((Message) -> Unit)? = null

    fun startChat(username: String, password: String, joinMessage: String, onMessageReceived: (Message) -> Unit) {
        val userChanged = lastUsername != username

        // 1. Обновляем кэш данных для переподключения
        lastUsername = username
        lastPassword = password
        lastJoinMessage = joinMessage
        lastOnMessageReceived = onMessageReceived

        // 2. Проверяем, готов ли канал.
        // Если статус не READY, gRPC сам попробует подключиться,
        // но запускать стрим сообщений (BIDI) пока рано.
        if (connectionStatus.value != ConnectionStatus.READY || channel == null) {
            android.util.Log.w("RealGrpcClient", "Channel not ready. Waiting for READY state...")
            // Можно вызвать channel?.getState(true), чтобы подтолкнуть подключение
            return
        }

        // 3. Загружаем историю (теперь мы уверены, что коннект есть)
        if (currentRoomId.isNotEmpty()) {
            loadHistory(currentRoomId)
        }

        // 4. Кэшируем аватарку
        getUserAvatar(username) { avatarUrl ->
            if (avatarUrl.isNotEmpty()) {
                avatarCache[username] = avatarUrl
            }
        }

        // 5. Защита от дублирования стримов
        if (isChatStarted && !userChanged) return

        // 6. Если юзер сменился — сносим старый стрим
        if (userChanged && isChatStarted) {
            android.util.Log.d("RealGrpcClient", "User changed, restarting chat stream")
            requestObserver?.onCompleted()
            requestObserver = null
            isChatStarted = false
        }

        try {
            isChatStarted = true
            reconnectAttempts = 0

            val responseObserver = object : StreamObserver<MessageProto> {
                override fun onNext(value: MessageProto) {
                    // 1. Обработка системных уведомлений (SYSTEM)
                    if (value.user == "SYSTEM") {
                        handleSystemCommand(value)
                        return
                    }

                    // 2. Фильтр сервисных сообщений о входе
                    if (value.text.endsWith(" joined") || value.text.endsWith(" присоединился")) return

                    // 3. Конвертируем Proto в нашу модель Message
                    val incoming = ProtoUtils.createMessageFromProto(value)

                    // 4. Работа с аватаркой (подставляем из кэша сразу, чтобы не моргало)
                    val cachedAvatar = avatarCache[incoming.user]
                    val messageWithAvatar = if (incoming.avatarUrl.isEmpty() && cachedAvatar != null) {
                        incoming.copy(avatarUrl = cachedAvatar)
                    } else {
                        incoming
                    }

                    // 5. Синхронизируем список сообщений
                    _messages.update { currentList ->
                        // Ищем индекс сообщения (либо по ID, либо по схожести контента для Local Echo)
                        val existingIndex = currentList.indexOfFirst {
                            (it.id == messageWithAvatar.id && it.id.isNotEmpty()) ||
                                    (it.user == messageWithAvatar.user &&
                                            it.text == messageWithAvatar.text &&
                                            it.imageUrl == messageWithAvatar.imageUrl &&
                                            it.voiceUrl == messageWithAvatar.voiceUrl &&
                                            Math.abs(it.timestamp - messageWithAvatar.timestamp) < 5000)
                        }

                        if (existingIndex != -1) {
                            // Обновляем существующее (например, пришел ID от сервера для нашего сообщения)
                            val updatedList = currentList.toMutableList()
                            updatedList[existingIndex] = messageWithAvatar
                            updatedList
                        } else {
                            // Это новое сообщение от другого пользователя
                            onMessageReceived(messageWithAvatar)
                            currentList + messageWithAvatar
                        }
                    }

                    // 6. Фоновая загрузка аватарки, если её нет ни в сообщении, ни в кэше
                    if (messageWithAvatar.avatarUrl.isEmpty() && !avatarCache.containsKey(messageWithAvatar.user)) {
                        fetchAvatarAsync(messageWithAvatar.user, messageWithAvatar.id)
                    }
                }

                private fun handleSystemCommand(value: MessageProto) {
                    when {
                        value.text == "REGISTRATION_SUCCESS" -> _systemNotification.value = "registration_success"
                        value.text == "AUTH_FAILED" -> _systemNotification.value = "auth_failed"
                        value.text == "SET_SUPER_ADMIN" -> _isSuperAdmin.value = true
                        value.text.startsWith("SERVER_INFO:") -> {
                            _serverVersion.value = value.text.substring("SERVER_INFO:".length)
                        }
                        value.text.startsWith("FORCE_DISCONNECT:") -> {
                            if (value.text.substring("FORCE_DISCONNECT:".length) == lastUsername) disconnect()
                        }
                        value.text.startsWith("ONLINE_USERS_UPDATE:") -> {
                            parseOnlineUsers(value.text.substring("ONLINE_USERS_UPDATE:".length))
                        }
                    }
                }

                private fun parseOnlineUsers(jsonStr: String) {
                    try {
                        val jsonArray = org.json.JSONArray(jsonStr)
                        _users.value = List(jsonArray.length()) { jsonArray.getString(it) }
                    } catch (e: Exception) {
                        android.util.Log.e("GrpcClient", "Error parsing online users", e)
                    }
                }

                private fun fetchAvatarAsync(username: String, messageId: String) {
                    getUserAvatar(username) { url ->
                        if (url.isNotEmpty()) {
                            avatarCache[username] = url
                            // Точечно обновляем аватарку в списке сообщений
                            _messages.update { list ->
                                list.map { if (it.user == username && it.avatarUrl.isEmpty()) it.copy(avatarUrl = url) else it }
                            }
                        }
                    }
                }

                override fun onError(t: Throwable) {
                    android.util.Log.e("RealGrpcClient", "Chat stream error: ${t.message}")
                    isChatStarted = false

                    // 1. Проверяем состояние самого канала
                    val currentStatus = _connectionStatus.value

                    if (currentStatus == ConnectionStatus.READY) {
                        // Если канал в порядке, но стрим упал (глюк сервера или тайм-аут стрима)
                        android.util.Log.d("RealGrpcClient", "Channel is READY, but stream failed. Restarting stream in 2s...")

                        // Используем простой Handler для небольшой задержки перед перезапуском стрима
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (!isChatStarted && lastUsername != null) {
                                // Просто вызываем startChat, мониторинг трогать не нужно
                                startChat(lastUsername!!, lastPassword!!, lastJoinMessage!!, lastOnMessageReceived!!)
                            }
                        }, 2000)

                    } else {
                        // 2. Если канал НЕ в READY (FAILED или CONNECTING)
                        // Мы ничего не делаем здесь!
                        // Наш startChannelMonitoring сам увидит, когда канал станет READY,
                        // и сам вызовет startChat.
                        android.util.Log.w("RealGrpcClient", "Stream error due to connection loss. Monitor will handle this.")
                        _connectionStatus.value = ConnectionStatus.FAILED
                    }
                }

                override fun onCompleted() { isChatStarted = false }
            }
            
            val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<MessageProto, MessageProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
                .setFullMethodName("messenger.ChatService/Chat")
                .setRequestMarshaller(MessageProtoMarshaller())
                .setResponseMarshaller(MessageProtoMarshaller())
                .build()

            val call = channel!!.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)

            call.start(object : io.grpc.ClientCall.Listener<MessageProto>() {
                override fun onHeaders(headers: io.grpc.Metadata) {
                    // Запрашиваем сообщения от сервера
                    call.request(100)
                }

                override fun onMessage(message: MessageProto) {
                    responseObserver.onNext(message)
                }

                override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                    isChatStarted = false
                    if (!status.isOk) {
                        android.util.Log.e("RealGrpcClient", "Stream closed with error: ${status.code}")

                        // Обновляем статус на FAILED, чтобы UI отреагировал
                        _connectionStatus.value = ConnectionStatus.FAILED

                        // Передаем ошибку в responseObserver (там сработает наша новая логика переподключения)
                        responseObserver.onError(status.asRuntimeException())
                    } else {
                        android.util.Log.d("RealGrpcClient", "Stream closed normally")
                        responseObserver.onCompleted()
                    }
                }
            }, io.grpc.Metadata())

            // Инициализируем observer для отправки сообщений
            requestObserver = object : StreamObserver<MessageProto> {
                override fun onNext(value: MessageProto) {
                    try {
                        call.sendMessage(value)
                        call.request(1)
                    } catch (e: Exception) {
                        android.util.Log.e("RealGrpcClient", "Error sending message: ${e.message}")
                    }
                }
                override fun onError(t: Throwable) { call.cancel("Error in requestObserver", t) }
                override fun onCompleted() { call.halfClose() }
            }

            // 1. Отправляем приветственное сообщение/авторизацию
            requestObserver?.onNext(MessageProto.newBuilder()
                .setUser(username)
                .setText(joinMessage)
                .setPassword(password)
                .setRoomId(currentRoomId)
                .setCreatedAt(ProtoUtils.getCurrentTimestamp())
                .setClientVersion(BuildConfig.VERSION_NAME)
                .build())

            // 2. Запускаем стрим статуса печати
            startTypingStream(username)

            // 3. Загружаем историю и список юзеров через небольшую паузу
            // Это нужно, чтобы BIDI-стрим успел стабилизироваться
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (currentRoomId.isNotEmpty()) {
                    loadHistory(currentRoomId) {
                        loadUsers()
                    }
                }
            }, 300)

        } catch (e: Exception) {
            android.util.Log.e("RealGrpcClient", "Failed to start chat: ${e.message}")
            isChatStarted = false
            _connectionStatus.value = ConnectionStatus.FAILED
        }
    }
    
    fun sendMessage(message: Message) {
        android.util.Log.d("RealGrpcClient", "sendMessage called: text='${message.text}', voiceUrl='${message.voiceUrl}', roomId='${message.roomId}'")
        if (requestObserver == null) {
            android.util.Log.w("RealGrpcClient", "requestObserver is null, attempting to reconnect...")
            // Try to reconnect if connection was lost
            if (lastUsername != null && lastPassword != null && lastJoinMessage != null && lastOnMessageReceived != null) {
                android.util.Log.d("RealGrpcClient", "Reconnecting to send message...")
                startChat(lastUsername!!, lastPassword!!, lastJoinMessage!!, lastOnMessageReceived!!)
                // Queue message to be sent after reconnection
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (requestObserver != null) {
                        android.util.Log.d("RealGrpcClient", "Reconnection successful, sending queued message")
                        sendMessage(message)
                    } else {
                        android.util.Log.e("RealGrpcClient", "Reconnection failed, cannot send message")
                    }
                }, 1000)
            } else {
                android.util.Log.e("RealGrpcClient", "Cannot reconnect: missing connection parameters")
            }
            return
        }
        try {
            // Use the message's roomId if it's set, otherwise use currentRoomId
            val messageWithRoom = if (message.roomId.isNotEmpty()) message else message.copy(roomId = currentRoomId)
            android.util.Log.d("RealGrpcClient", "messageWithRoom: $messageWithRoom")

            // Get avatar URL for current user if not cached
            if (!avatarCache.containsKey(message.user) && message.avatarUrl.isEmpty()) {
                android.util.Log.d("RealGrpcClient", "No cached avatar for ${message.user}, loading...")
                getUserAvatar(message.user) { avatarUrl ->
                    android.util.Log.d("RealGrpcClient", "Avatar loaded for ${message.user}: $avatarUrl")
                    if (avatarUrl.isNotEmpty()) {
                        avatarCache[message.user] = avatarUrl
                        // Update message with avatar URL
                        val messageWithAvatar = messageWithRoom.copy(avatarUrl = avatarUrl)
                        _messages.update { currentList ->
                            currentList.map { if (getMessageHash(it) == getMessageHash(messageWithRoom)) messageWithAvatar else it }
                        }
                        val protoMessage = ProtoUtils.createMessageProto(messageWithAvatar)
                        android.util.Log.d("RealGrpcClient", "Sending message with loaded avatar: $protoMessage")
                        sentMessageHashes.add(getMessageHash(messageWithAvatar))
                        requestObserver?.onNext(protoMessage)
                    } else {
                        android.util.Log.w("RealGrpcClient", "Failed to load avatar for ${message.user}, sending without avatar")
                        _messages.update { currentList -> currentList + messageWithRoom }
                        val protoMessage = ProtoUtils.createMessageProto(messageWithRoom)
                        android.util.Log.d("RealGrpcClient", "Sending message without avatar (fallback): $protoMessage")
                        sentMessageHashes.add(getMessageHash(messageWithRoom))
                        requestObserver?.onNext(protoMessage)
                    }
                }
                return@sendMessage // Important: don't send again below
            } else if (avatarCache.containsKey(message.user)) {
                // Use cached avatar URL
                android.util.Log.d("RealGrpcClient", "Using cached avatar for ${message.user}")
                val messageWithAvatar = messageWithRoom.copy(avatarUrl = avatarCache[message.user] ?: "", imageUrl = messageWithRoom.imageUrl)
                _messages.update { currentList -> currentList + messageWithAvatar }
                val protoMessage = ProtoUtils.createMessageProto(messageWithAvatar)
                android.util.Log.d("RealGrpcClient", "Sending message with cached avatar: $protoMessage")
                sentMessageHashes.add(getMessageHash(messageWithAvatar))
                requestObserver?.onNext(protoMessage)
                return@sendMessage
            }

            android.util.Log.d("RealGrpcClient", "No cached avatar, sending message immediately")
            _messages.update { currentList -> currentList + messageWithRoom }
            val protoMessage = ProtoUtils.createMessageProto(messageWithRoom)
            android.util.Log.d("RealGrpcClient", "Sending message without avatar: $protoMessage")
            sentMessageHashes.add(getMessageHash(messageWithRoom))
            requestObserver?.onNext(protoMessage)
        } catch (_: Exception) {}
    }
    
    fun deleteMessage(message: Message) {
        val hash = getMessageHash(message)
        deletedMessageHashes.add(hash)
        saveDeletedMessages()
        _messages.update { currentList -> currentList.filterNot { getMessageHash(it) == hash } }

        // Send delete request to server
        val proto = ProtoUtils.createMessageProto(message)
        val request = DeleteMessagesRequestProto(listOf(proto), lastUsername ?: "")

        val method = io.grpc.MethodDescriptor.newBuilder<DeleteMessagesRequestProto, DeleteMessagesResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/DeleteMessages")
            .setRequestMarshaller(DeleteMessagesRequestMarshaller())
            .setResponseMarshaller(DeleteMessagesResponseMarshaller())
            .build()

        channel?.let { ch ->
            val call = ch.newCall(method, io.grpc.CallOptions.DEFAULT)
            call.start(object : io.grpc.ClientCall.Listener<DeleteMessagesResponseProto>() {
                override fun onMessage(message: DeleteMessagesResponseProto) {
                    if (message.success) {
                        android.util.Log.d("GrpcClient", "Successfully deleted message on server")
                    }
                }
            }, io.grpc.Metadata())
            call.request(1)
            call.sendMessage(request)
            call.halfClose()
        }
    }

    fun editMessage(messageId: String, text: String, callback: (Boolean, String) -> Unit = { _, _ -> }) {
        val request = EditMessageRequestProto(messageId, text)

        val method = io.grpc.MethodDescriptor.newBuilder<EditMessageRequestProto, EditMessageResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/EditMessage")
            .setRequestMarshaller(EditMessageRequestMarshaller())
            .setResponseMarshaller(EditMessageResponseMarshaller())
            .build()

        channel?.let { ch ->
            val call = ch.newCall(method, io.grpc.CallOptions.DEFAULT)
            call.start(object : io.grpc.ClientCall.Listener<EditMessageResponseProto>() {
                override fun onMessage(message: EditMessageResponseProto) {
                    if (message.success) {
                        _messages.update { currentList ->
                            currentList.map { 
                                if (it.id == messageId) it.copy(text = text, edited = true) else it 
                            }
                        }
                    }
                    callback(message.success, message.message)
                }
                override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                    if (!status.isOk) {
                        callback(false, status.description ?: "Unknown error")
                    }
                }
            }, io.grpc.Metadata())
            call.request(1)
            call.sendMessage(request)
            call.halfClose()
        }
    }

    fun setReaction(messageId: String, username: String, emoji: String) {
        val currentChannel = channel ?: return

        val reaction = ReactionProto(username, emoji)
        val request = ReactionRequestProto(messageId, reaction)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<ReactionRequestProto, ReactionResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SetReaction")
            .setRequestMarshaller(ReactionRequestMarshaller())
            .setResponseMarshaller(ReactionResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<ReactionResponseProto>() {
            override fun onMessage(message: ReactionResponseProto) {
                if (message.success) {
                    android.util.Log.d("GrpcClient", "Successfully set reaction")
                    _messages.update { currentList ->
                        currentList.map { m ->
                            if (m.id == messageId) {
                                val newReactions = m.reactions.filterNot { it.user == username } + Reaction(username, emoji)
                                m.copy(reactions = newReactions)
                            } else m
                        }
                    }
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun registerToken(user: String, token: String, pushEnabled: Boolean = true) {
        val currentChannel = channel ?: return

        val request = TokenRequestProto(user, token, pushEnabled)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<TokenRequestProto, TokenResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/RegisterToken")
            .setRequestMarshaller(TokenRequestMarshaller())
            .setResponseMarshaller(TokenResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<TokenResponseProto>() {
            override fun onMessage(message: TokenResponseProto) {
                if (message.success) {
                    android.util.Log.d("FCM", "Token registered successfully for $user")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getChats(username: String, callback: (List<ChatInfo>) -> Unit) {
        val currentChannel = channel
        if (currentChannel == null) {
            callback(emptyList())
            return
        }
        val currentUserId = lastUserId ?: ""

        val request = GetChatsRequestProto(username, currentUserId)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetChatsRequestProto, GetChatsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetChats")
            .setRequestMarshaller(GetChatsRequestMarshaller())
            .setResponseMarshaller(GetChatsResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetChatsResponseProto>() {
            override fun onMessage(message: GetChatsResponseProto) {
                val chats = message.chats.map { it.toChatInfo() }
                callback(chats)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "GetChats failed: ${status.code} - ${status.description}")
                    callback(emptyList())
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun createDirectChat(user1: String, user2: String, callback: (String?) -> Unit) {
        val currentChannel = channel ?: return

        val request = CreateDirectChatRequestProto(user1, user2)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<CreateDirectChatRequestProto, CreateDirectChatResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/CreateDirectChat")
            .setRequestMarshaller(CreateDirectChatRequestMarshaller())
            .setResponseMarshaller(CreateDirectChatResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<CreateDirectChatResponseProto>() {
            private var received = false
            override fun onMessage(message: CreateDirectChatResponseProto) {
                received = true
                if (message.success) {
                    callback(message.chatId)
                } else {
                    callback(null)
                }
            }

            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!received) {
                    // Give more time for onMessage to process if they arrived together
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (!received) {
                            callback(null)
                        }
                    }, 500)
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun createGroupChat(name: String, participants: List<String>, creator: String, callback: (String?) -> Unit) {
        val currentChannel = channel ?: return

        val request = CreateGroupChatRequestProto(name, participants, creator)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<CreateGroupChatRequestProto, CreateGroupChatResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/CreateGroupChat")
            .setRequestMarshaller(CreateGroupChatRequestMarshaller())
            .setResponseMarshaller(CreateGroupChatResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<CreateGroupChatResponseProto>() {
            private var received = false
            override fun onMessage(message: CreateGroupChatResponseProto) {
                received = true
                if (message.success) {
                    callback(message.chatId)
                } else {
                    callback(null)
                }
            }

            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!received) {
                    // Give more time for onMessage to process if they arrived together
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (!received) {
                            callback(null)
                        }
                    }, 500)
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun markRead(roomId: String, username: String, onCompletion: (() -> Unit)? = null) {
        val currentChannel = channel ?: return

        val request = MarkReadRequestProto(roomId, username)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<MarkReadRequestProto, MarkReadResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/MarkRead")
            .setRequestMarshaller(MarkReadRequestMarshaller())
            .setResponseMarshaller(MarkReadResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<MarkReadResponseProto>() {
            override fun onMessage(message: MarkReadResponseProto) {
                if (message.success) {
                    android.util.Log.d("GrpcClient", "Successfully marked read for $username in $roomId")
                }
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "MarkRead failed: ${status.code} - ${status.description}")
                }
                onCompletion?.invoke()
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun sendTypingSignal(username: String, isTyping: Boolean) {
        if (typingRequestObserver == null) {
            // If the typing stream is not active, start it
            startTypingStream(username)
            // And then send the signal after a short delay to allow stream to establish
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                typingRequestObserver?.onNext(TypingRequestProto(currentRoomId, username, isTyping))
            }, 100)
            return
        }
        typingRequestObserver?.onNext(TypingRequestProto(currentRoomId, username, isTyping))
    }

    private fun startTypingStream(username: String) {
        val currentChannel = channel
        if (currentChannel == null) return

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<TypingRequestProto, TypingSignalProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
            .setFullMethodName("messenger.ChatService/Typing")
            .setRequestMarshaller(TypingRequestMarshaller())
            .setResponseMarshaller(TypingSignalMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)

        val responseObserver = object : StreamObserver<TypingSignalProto> {
            override fun onNext(value: TypingSignalProto) {
                _typingUsers.update { currentMap ->
                    val typistsInRoom = currentMap[value.roomId]?.toMutableSet() ?: mutableSetOf()
                    if (value.isTyping) {
                        typistsInRoom.add(value.username)
                    } else {
                        typistsInRoom.remove(value.username)
                    }
                    currentMap + (value.roomId to typistsInRoom)
                }
            }

            override fun onError(t: Throwable) {
                android.util.Log.e("RealGrpcClient", "Typing stream error: ${t.message}")
                typingRequestObserver = null // Reset observer on error
            }

            override fun onCompleted() {
                android.util.Log.d("RealGrpcClient", "Typing stream completed")
                typingRequestObserver = null // Reset observer on completion
            }
        }

        call.start(object : io.grpc.ClientCall.Listener<TypingSignalProto>() {
            override fun onHeaders(headers: io.grpc.Metadata) {
                call.request(100)
            }

            override fun onMessage(message: TypingSignalProto) {
                responseObserver.onNext(message)
            }

            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("RealGrpcClient", "Typing stream closed with error: ${status.code}")
                    responseObserver.onError(status.asRuntimeException())
                } else {
                    responseObserver.onCompleted()
                }
            }
        }, io.grpc.Metadata())

        typingRequestObserver = object : StreamObserver<TypingRequestProto> {
            override fun onNext(value: TypingRequestProto) {
                try {
                    call.sendMessage(value)
                    call.request(1)
                } catch (e: Exception) {
                    android.util.Log.e("RealGrpcClient", "Error sending typing signal: ${e.message}")
                }
            }
            override fun onError(t: Throwable) { call.cancel("Error in typingRequestObserver", t) }
            override fun onCompleted() { call.halfClose() }
        }
    }

    fun updateAvatar(username: String, avatarUrl: String, fullAvatarUrl: String = "", callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return

        val request = UpdateAvatarRequestProto(username, avatarUrl, fullAvatarUrl)

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
                    android.util.Log.d("GrpcClient", "Successfully updated avatar for $username")
                    updateAvatarCache(username, avatarUrl, fullAvatarUrl)
                }
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "UpdateAvatar failed: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun updateChatAvatar(chatId: String, avatarUrl: String, username: String, fullAvatarUrl: String = "", callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return

        val request = UpdateChatAvatarRequestProto(chatId, avatarUrl, username, fullAvatarUrl)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<UpdateChatAvatarRequestProto, UpdateChatAvatarResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/UpdateChatAvatar")
            .setRequestMarshaller(UpdateChatAvatarRequestMarshaller())
            .setResponseMarshaller(UpdateChatAvatarResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<UpdateChatAvatarResponseProto>() {
            override fun onMessage(message: UpdateChatAvatarResponseProto) {
                if (message.success) {
                    android.util.Log.d("GrpcClient", "Successfully updated chat avatar for $chatId")
                }
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "UpdateChatAvatar failed: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getUserAvatar(username: String, callback: (String) -> Unit) {
        val currentChannel = channel ?: return

        val request = GetUserAvatarRequestProto(username)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetUserAvatarRequestProto, GetUserAvatarResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetUserAvatar")
            .setRequestMarshaller(GetUserAvatarRequestMarshaller())
            .setResponseMarshaller(GetUserAvatarResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetUserAvatarResponseProto>() {
            override fun onMessage(message: GetUserAvatarResponseProto) {
                if (message.avatarUrl.isNotEmpty()) {
                    updateAvatarCache(username, message.avatarUrl, message.fullAvatarUrl)
                }
                callback(message.avatarUrl)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "GetUserAvatar failed: ${status.code} - ${status.description}")
                    callback("")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun addParticipant(chatId: String, username: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return

        val request = AddParticipantRequestProto(chatId, username)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<AddParticipantRequestProto, AddParticipantResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/AddParticipant")
            .setRequestMarshaller(AddParticipantRequestMarshaller())
            .setResponseMarshaller(AddParticipantResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<AddParticipantResponseProto>() {
            override fun onMessage(message: AddParticipantResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "AddParticipant failed: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun addParticipants(chatId: String, usernames: List<String>, callback: (Boolean, String) -> Unit) {
        // This function is not yet implemented on the server-side,
        // so we'll just call addParticipant for each user for now.
        // In a real scenario, you'd have a dedicated gRPC method for this.
        var successCount = 0
        var errorMessages = mutableListOf<String>()

        if (usernames.isEmpty()) {
            callback(true, "No participants to add")
            return
        }

        usernames.forEach { username ->
            addParticipant(chatId, username) { success, message ->
                if (success) {
                    successCount++
                } else {
                    errorMessages.add("Failed to add $username: $message")
                }

                if (successCount + errorMessages.size == usernames.size) {
                    // All calls completed
                    if (successCount == usernames.size) {
                        callback(true, "All participants added successfully")
                    } else {
                        callback(false, errorMessages.joinToString("; "))
                    }
                }
            }
        }
    }

    fun deleteChat(chatId: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return

        val request = DeleteChatRequestProto(chatId)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<DeleteChatRequestProto, DeleteChatResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/DeleteChat")
            .setRequestMarshaller(DeleteChatRequestMarshaller())
            .setResponseMarshaller(DeleteChatResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<DeleteChatResponseProto>() {
            override fun onMessage(message: DeleteChatResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "DeleteChat failed: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun deleteProfile(username: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return

        val request = DeleteProfileRequestProto(username)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<DeleteProfileRequestProto, DeleteProfileResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/DeleteProfile")
            .setRequestMarshaller(DeleteProfileRequestMarshaller())
            .setResponseMarshaller(DeleteProfileResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<DeleteProfileResponseProto>() {
            override fun onMessage(message: DeleteProfileResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "DeleteProfile failed: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getUserProfile(username: String, callback: (GetUserProfileResponseProto?) -> Unit) {
        val currentChannel = channel ?: return

        val request = GetUserProfileRequestProto(username)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetUserProfileRequestProto, GetUserProfileResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetUserProfile")
            .setRequestMarshaller(GetUserProfileRequestMarshaller())
            .setResponseMarshaller(GetUserProfileResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetUserProfileResponseProto>() {
            override fun onMessage(message: GetUserProfileResponseProto) {
                if (message.avatarUrl.isNotEmpty()) {
                    updateAvatarCache(username, message.avatarUrl)
                }
                callback(message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "GetUserProfile failed: ${status.code} - ${status.description}")
                    callback(null)
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun updateProfile(username: String, bio: String, status: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return

        val request = UpdateProfileRequestProto(username, bio, status)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<UpdateProfileRequestProto, UpdateProfileResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/UpdateProfile")
            .setRequestMarshaller(UpdateProfileRequestMarshaller())
            .setResponseMarshaller(UpdateProfileResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<UpdateProfileResponseProto>() {
            override fun onMessage(message: UpdateProfileResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "UpdateProfile failed: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun addContact(username: String, contactUsername: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return

        val request = AddContactRequestProto(username, contactUsername)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<AddContactRequestProto, AddContactResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/AddContact")
            .setRequestMarshaller(AddContactRequestMarshaller())
            .setResponseMarshaller(AddContactResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<AddContactResponseProto>() {
            override fun onMessage(message: AddContactResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "AddContact failed: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun removeContact(username: String, contactUsername: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return

        val request = RemoveContactRequestProto(username, contactUsername)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<RemoveContactRequestProto, RemoveContactResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/RemoveContact")
            .setRequestMarshaller(RemoveContactRequestMarshaller())
            .setResponseMarshaller(RemoveContactResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<RemoveContactResponseProto>() {
            override fun onMessage(message: RemoveContactResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "RemoveContact failed: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getContacts(username: String, callback: (List<String>) -> Unit) {
        val currentChannel = channel ?: return

        val request = GetContactsRequestProto(username)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetContactsRequestProto, GetContactsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetContacts")
            .setRequestMarshaller(GetContactsRequestMarshaller())
            .setResponseMarshaller(GetContactsResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetContactsResponseProto>() {
            override fun onMessage(message: GetContactsResponseProto) {
                callback(message.contacts)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "GetContacts failed: ${status.code} - ${status.description}")
                    callback(emptyList())
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getFCMLogs(callback: (List<lavender.client.android.data.proto.FCMLogEntryProto>) -> Unit) {
        val currentChannel = channel ?: return

        val request = lavender.client.android.data.proto.GetFCMLogsRequestProto()

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<lavender.client.android.data.proto.GetFCMLogsRequestProto, lavender.client.android.data.proto.GetFCMLogsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetFCMLogs")
            .setRequestMarshaller(GetFCMLogsRequestMarshaller())
            .setResponseMarshaller(GetFCMLogsResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<lavender.client.android.data.proto.GetFCMLogsResponseProto>() {
            override fun onMessage(message: lavender.client.android.data.proto.GetFCMLogsResponseProto) {
                callback(message.logs)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "GetFCMLogs failed: ${status.code} - ${status.description}")
                    callback(emptyList())
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun saveDraft(roomId: String, draftText: String, repliedToMessageId: String = "", repliedToUser: String = "", repliedToText: String = "", callback: (Boolean, String) -> Unit = { _, _ -> }) {
        val currentChannel = channel ?: return
        val currentUserId = lastUserId ?: ""

        val request = lavender.client.android.data.proto.SaveDraftRequestProto(currentUserId, roomId, draftText, repliedToMessageId, repliedToUser, repliedToText)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<lavender.client.android.data.proto.SaveDraftRequestProto, lavender.client.android.data.proto.SaveDraftResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SaveDraft")
            .setRequestMarshaller(SaveDraftRequestMarshaller())
            .setResponseMarshaller(SaveDraftResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<lavender.client.android.data.proto.SaveDraftResponseProto>() {
            override fun onMessage(message: lavender.client.android.data.proto.SaveDraftResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "SaveDraft failed: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getDraft(roomId: String, callback: (draftText: String, repliedToMessageId: String, repliedToUser: String, repliedToText: String, hasDraft: Boolean) -> Unit) {
        val currentChannel = channel ?: return
        val currentUserId = lastUserId ?: ""

        val request = lavender.client.android.data.proto.GetDraftRequestProto(currentUserId, roomId)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<lavender.client.android.data.proto.GetDraftRequestProto, lavender.client.android.data.proto.GetDraftResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetDraft")
            .setRequestMarshaller(GetDraftRequestMarshaller())
            .setResponseMarshaller(GetDraftResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<lavender.client.android.data.proto.GetDraftResponseProto>() {
            override fun onMessage(message: lavender.client.android.data.proto.GetDraftResponseProto) {
                callback(message.draftText, message.repliedToMessageId, message.repliedToUser, message.repliedToText, message.hasDraft)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "GetDraft failed: ${status.code} - ${status.description}")
                    callback("", "", "", "", false)
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun deleteDraft(roomId: String, callback: (Boolean) -> Unit = {}) {
        val currentChannel = channel ?: return
        val currentUserId = lastUserId ?: ""

        val request = lavender.client.android.data.proto.DeleteDraftRequestProto(currentUserId, roomId)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<lavender.client.android.data.proto.DeleteDraftRequestProto, lavender.client.android.data.proto.DeleteDraftResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/DeleteDraft")
            .setRequestMarshaller(DeleteDraftRequestMarshaller())
            .setResponseMarshaller(DeleteDraftResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<lavender.client.android.data.proto.DeleteDraftResponseProto>() {
            override fun onMessage(message: lavender.client.android.data.proto.DeleteDraftResponseProto) {
                callback(message.success)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "DeleteDraft failed: ${status.code} - ${status.description}")
                    callback(false)
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getMutedChats(callback: (List<String>) -> Unit) {
        val currentChannel = channel ?: return
        val currentUserId = lastUserId ?: ""

        val request = lavender.client.android.data.proto.GetMutedChatsRequestProto(currentUserId)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<lavender.client.android.data.proto.GetMutedChatsRequestProto, lavender.client.android.data.proto.GetMutedChatsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetMutedChats")
            .setRequestMarshaller(GetMutedChatsRequestMarshaller())
            .setResponseMarshaller(GetMutedChatsResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<lavender.client.android.data.proto.GetMutedChatsResponseProto>() {
            override fun onMessage(message: lavender.client.android.data.proto.GetMutedChatsResponseProto) {
                callback(message.roomIds)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "GetMutedChats failed: ${status.code} - ${status.description}")
                    callback(emptyList())
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun setMutedChat(roomId: String, muted: Boolean, callback: (Boolean) -> Unit = {}) {
        val currentChannel = channel ?: return
        val currentUserId = lastUserId ?: ""

        val request = lavender.client.android.data.proto.SetMutedChatRequestProto(currentUserId, roomId, muted)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<lavender.client.android.data.proto.SetMutedChatRequestProto, lavender.client.android.data.proto.SetMutedChatResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SetMutedChat")
            .setRequestMarshaller(SetMutedChatRequestMarshaller())
            .setResponseMarshaller(SetMutedChatResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<lavender.client.android.data.proto.SetMutedChatResponseProto>() {
            override fun onMessage(message: lavender.client.android.data.proto.SetMutedChatResponseProto) {
                callback(message.success)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "SetMutedChat failed: ${status.code} - ${status.description}")
                    callback(false)
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getAvatarCache(): Map<String, String> {
        return avatarCache.toMap()
    }

    fun getFullAvatarCache(): Map<String, String> {
        return fullAvatarCache
    }

    fun getFullAvatarUrl(username: String): String? {
        return fullAvatarCache[username]
    }

    fun updateAvatarCache(username: String, avatarUrl: String, fullAvatarUrl: String = "") {
        avatarCache[username] = avatarUrl
        _avatarCacheFlow.value = avatarCache.toMap()
        if (fullAvatarUrl.isNotEmpty()) {
            fullAvatarCache[username] = fullAvatarUrl
        }
    }

    fun getCurrentUsername(): String? = lastUsername

    fun setUserId(userId: String) {
        lastUserId = userId
    }

    fun getUserId(): String? = lastUserId

    fun fetchUserId(username: String, callback: (String?, Boolean) -> Unit) {
        val currentChannel = channel
        if (currentChannel == null) {
            callback(null, false)
            return
        }

        val request = lavender.client.android.data.proto.GetUserIdRequestProto(username)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<lavender.client.android.data.proto.GetUserIdRequestProto, GetUserIdResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetUserId")
            .setRequestMarshaller(GetUserIdRequestMarshaller())
            .setResponseMarshaller(GetUserIdResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetUserIdResponseProto>() {
            override fun onMessage(message: GetUserIdResponseProto) {
                callback(message.userId, message.found)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "GetUserId failed: ${status.code} - ${status.description}")
                    callback(null, false)
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getThemes(username: String, callback: (String, List<CustomThemeProto>) -> Unit) {
        val currentChannel = channel ?: return
        // Используем userId если есть, иначе username для совместимости
        val userId = lastUserId ?: ""
        val request = GetThemesRequestProto(username, userId)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetThemesRequestProto, GetThemesResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetThemes")
            .setRequestMarshaller(GetThemesRequestMarshaller())
            .setResponseMarshaller(GetThemesResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetThemesResponseProto>() {
            override fun onMessage(message: GetThemesResponseProto) {
                callback(message.currentThemeId, message.customThemes)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "GetThemes failed: ${status.code} - ${status.description}")
                    callback("", emptyList())
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun saveTheme(username: String, theme: CustomThemeProto, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val request = SaveThemeRequestProto(username, theme)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<SaveThemeRequestProto, SaveThemeResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SaveTheme")
            .setRequestMarshaller(SaveThemeRequestMarshaller())
            .setResponseMarshaller(SaveThemeResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<SaveThemeResponseProto>() {
            override fun onMessage(message: SaveThemeResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "SaveTheme failed: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun setCurrentTheme(username: String, themeId: String, callback: (Boolean) -> Unit) {
        val currentChannel = channel ?: return
        val request = SetCurrentThemeRequestProto(username, themeId)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<SetCurrentThemeRequestProto, SetCurrentThemeResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SetCurrentTheme")
            .setRequestMarshaller(SetCurrentThemeRequestMarshaller())
            .setResponseMarshaller(SetCurrentThemeResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<SetCurrentThemeResponseProto>() {
            override fun onMessage(message: SetCurrentThemeResponseProto) {
                callback(message.success)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "SetCurrentTheme failed: ${status.code} - ${status.description}")
                    callback(false)
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun deleteTheme(username: String, themeId: String, callback: (Boolean) -> Unit) {
        val currentChannel = channel ?: return
        val request = DeleteThemeRequestProto(username, themeId)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<DeleteThemeRequestProto, DeleteThemeResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/DeleteTheme")
            .setRequestMarshaller(DeleteThemeRequestMarshaller())
            .setResponseMarshaller(DeleteThemeResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<DeleteThemeResponseProto>() {
            override fun onMessage(message: DeleteThemeResponseProto) {
                callback(message.success)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "DeleteTheme failed: ${status.code} - ${status.description}")
                    callback(false)
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun updateUsername(oldUsername: String, newUsername: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val request = UpdateUsernameRequestProto(oldUsername, newUsername)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<UpdateUsernameRequestProto, UpdateUsernameResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/UpdateUsername")
            .setRequestMarshaller(UpdateUsernameRequestMarshaller())
            .setResponseMarshaller(UpdateUsernameResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<UpdateUsernameResponseProto>() {
            override fun onMessage(message: UpdateUsernameResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "UpdateUsername failed: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun updatePassword(username: String, oldPassword: String, newPassword: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val request = UpdatePasswordRequestProto(username, oldPassword, newPassword)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<UpdatePasswordRequestProto, UpdatePasswordResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/UpdatePassword")
            .setRequestMarshaller(UpdatePasswordRequestMarshaller())
            .setResponseMarshaller(UpdatePasswordResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<UpdatePasswordResponseProto>() {
            override fun onMessage(message: UpdatePasswordResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "UpdatePassword failed: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getChatListVersion(username: String, callback: (Long) -> Unit) {
        val currentChannel = channel ?: return
        val request = GetChatListVersionRequestProto(username)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetChatListVersionRequestProto, GetChatListVersionResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetChatListVersion")
            .setRequestMarshaller(GetChatListVersionRequestMarshaller())
            .setResponseMarshaller(GetChatListVersionResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetChatListVersionResponseProto>() {
            override fun onMessage(message: GetChatListVersionResponseProto) {
                callback(message.version)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "GetChatListVersion failed: ${status.code} - ${status.description}")
                    callback(0)
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun updateChatName(chatId: String, newName: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val request = UpdateChatNameRequestProto(chatId, newName)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<UpdateChatNameRequestProto, UpdateChatNameResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/UpdateChatName")
            .setRequestMarshaller(UpdateChatNameRequestMarshaller())
            .setResponseMarshaller(UpdateChatNameResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<UpdateChatNameResponseProto>() {
            override fun onMessage(message: UpdateChatNameResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "UpdateChatName failed: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun removeParticipant(chatId: String, username: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val request = RemoveParticipantRequestProto(chatId, username)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<RemoveParticipantRequestProto, RemoveParticipantResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/RemoveParticipant")
            .setRequestMarshaller(RemoveParticipantRequestMarshaller())
            .setResponseMarshaller(RemoveParticipantResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<RemoveParticipantResponseProto>() {
            override fun onMessage(message: RemoveParticipantResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "RemoveParticipant failed: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getAllChats(callback: (List<ChatInfo>) -> Unit) {
        val currentChannel = channel ?: return
        val request = GetAllChatsRequestProto()

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetAllChatsRequestProto, GetAllChatsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetAllChats")
            .setRequestMarshaller(GetAllChatsRequestMarshaller())
            .setResponseMarshaller(GetAllChatsResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<GetAllChatsResponseProto>() {
            override fun onMessage(message: GetAllChatsResponseProto) {
                callback(message.chats.map { it.toChatInfo() })
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "GetAllChats failed: ${status.code} - ${status.description}")
                    callback(emptyList())
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    private fun ChatInfoProto.toChatInfo(): ChatInfo {
        return ChatInfo(
            id = this.id,
            name = this.name,
            type = this.type,
            participants = this.participants,
            createdAt = this.createdAt?.let { it.seconds * 1000 + (it.nanos / 1000000) } ?: 0,
            unreadCount = this.unreadCount,
            lastMessageTime = this.lastMessageTime?.let { it.seconds * 1000 + (it.nanos / 1000000) } ?: 0,
            creator = this.creator,
            lastMessageText = this.lastMessageText,
            avatarUrl = this.avatarUrl,
            fullAvatarUrl = this.fullAvatarUrl,
            lastMessageUsername = this.lastMessageUsername
        )
    }

    fun addFavorite(userId: String, messageId: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return
        val request = lavender.client.android.data.proto.AddFavoriteRequestProto(userId, messageId)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<lavender.client.android.data.proto.AddFavoriteRequestProto, lavender.client.android.data.proto.AddFavoriteResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/AddFavorite")
            .setRequestMarshaller(AddFavoriteRequestMarshaller())
            .setResponseMarshaller(AddFavoriteResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<lavender.client.android.data.proto.AddFavoriteResponseProto>() {
            override fun onMessage(message: lavender.client.android.data.proto.AddFavoriteResponseProto) {
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "AddFavorite failed: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun removeFavorite(userId: String, messageId: String, callback: (Boolean) -> Unit) {
        val currentChannel = channel ?: return
        val request = lavender.client.android.data.proto.RemoveFavoriteRequestProto(userId, messageId)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<lavender.client.android.data.proto.RemoveFavoriteRequestProto, lavender.client.android.data.proto.RemoveFavoriteResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/RemoveFavorite")
            .setRequestMarshaller(RemoveFavoriteRequestMarshaller())
            .setResponseMarshaller(RemoveFavoriteResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<lavender.client.android.data.proto.RemoveFavoriteResponseProto>() {
            override fun onMessage(message: lavender.client.android.data.proto.RemoveFavoriteResponseProto) {
                callback(message.success)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "RemoveFavorite failed: ${status.code} - ${status.description}")
                    callback(false)
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getFavorites(userId: String, callback: (List<Message>) -> Unit) {
        val currentChannel = channel ?: return
        val request = lavender.client.android.data.proto.GetFavoritesRequestProto(userId)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<lavender.client.android.data.proto.GetFavoritesRequestProto, lavender.client.android.data.proto.GetFavoritesResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetFavorites")
            .setRequestMarshaller(GetFavoritesRequestMarshaller())
            .setResponseMarshaller(GetFavoritesResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<lavender.client.android.data.proto.GetFavoritesResponseProto>() {
            override fun onMessage(message: lavender.client.android.data.proto.GetFavoritesResponseProto) {
                callback(message.messages.map { ProtoUtils.createMessageFromProto(it) })
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("GrpcClient", "GetFavorites failed: ${status.code} - ${status.description}")
                    callback(emptyList())
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }
}

class MessageProtoMarshaller : io.grpc.MethodDescriptor.Marshaller<MessageProto> {
    override fun stream(value: MessageProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.id.isNotEmpty()) cos.writeString(1, value.id)
        if (value.user.isNotEmpty()) cos.writeString(2, value.user)
        if (value.text.isNotEmpty()) cos.writeString(3, value.text)
        value.createdAt?.let { cos.writeMessage(4, ProtoUtils.timestampToProto(it)) }
        for (reaction in value.reactions) {
            cos.writeTag(5, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
            val rBaos = java.io.ByteArrayOutputStream()
            val rCos = com.google.protobuf.CodedOutputStream.newInstance(rBaos)
            if (reaction.user.isNotEmpty()) rCos.writeString(1, reaction.user)
            if (reaction.emoji.isNotEmpty()) rCos.writeString(2, reaction.emoji)
            rCos.flush()
            val bytes = rBaos.toByteArray()
            cos.writeUInt32NoTag(bytes.size)
            cos.writeRawBytes(bytes)
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
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): MessageProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var id = ""
        var user = ""
        var text = ""
        var createdAt: Timestamp? = null
        val reactions = mutableListOf<ReactionProto>()
        var password = ""
        var repliedToMessageId = ""
        var repliedToUser = ""
        var repliedToText = ""
        var roomId = ""
        var isRead = false
        var avatarUrl = ""
        var imageUrl = ""
        var edited = false
        var clientVersion = ""
        var isSuperAdmin = false
        var voiceUrl = ""
        var duration = 0
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> id = cis.readString()
                2 -> user = cis.readString()
                3 -> text = cis.readString()
                4 -> createdAt = ProtoUtils.parseTimestampFromProto(cis.readBytes().newInput())
                5 -> {
                    val length = cis.readUInt32()
                    reactions.add(ReactionProtoMarshaller().parse(java.io.ByteArrayInputStream(cis.readRawBytes(length))))
                }
                6 -> password = cis.readString()
                7 -> repliedToMessageId = cis.readString()
                8 -> repliedToUser = cis.readString()
                9 -> repliedToText = cis.readString()
                10 -> roomId = cis.readString()
                11 -> isRead = cis.readBool()
                12 -> avatarUrl = cis.readString()
                13 -> imageUrl = cis.readString()
                14 -> edited = cis.readBool()
                15 -> clientVersion = cis.readString()
                16 -> isSuperAdmin = cis.readBool()
                17 -> voiceUrl = cis.readString()
                18 -> duration = cis.readInt32()
                else -> cis.skipField(tag)
            }
        }
        return MessageProto(id, user, text, createdAt, reactions, password, repliedToMessageId, repliedToUser, repliedToText, roomId, isRead, avatarUrl, imageUrl, edited, clientVersion, isSuperAdmin, voiceUrl, duration)
    }
}

class ReactionProtoMarshaller : io.grpc.MethodDescriptor.Marshaller<ReactionProto> {
    override fun stream(value: ReactionProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.user.isNotEmpty()) cos.writeString(1, value.user)
        if (value.emoji.isNotEmpty()) cos.writeString(2, value.emoji)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): ReactionProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var user = ""
        var emoji = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> user = cis.readString()
                2 -> emoji = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return ReactionProto(user, emoji)
    }
}

class EmptyMarshaller : io.grpc.MethodDescriptor.Marshaller<Unit> {
    override fun stream(value: Unit): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))
    override fun parse(stream: java.io.InputStream): Unit = Unit
}

class ClientListResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<List<String>> {
    override fun stream(value: List<String>): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        for (client in value) {
            cos.writeString(1, client)
        }
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): List<String> {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        val clients = mutableListOf<String>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                clients.add(cis.readString())
            } else cis.skipField(tag)
        }
        return clients
    }
}

class ChatInfoMarshaller : io.grpc.MethodDescriptor.Marshaller<ChatInfoProto> {
    override fun stream(value: ChatInfoProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.id.isNotEmpty()) cos.writeString(1, value.id)
        if (value.name.isNotEmpty()) cos.writeString(2, value.name)
        if (value.type.isNotEmpty()) cos.writeString(3, value.type)
        if (value.participants.isNotEmpty()) cos.writeString(4, value.participants)
        value.createdAt?.let { cos.writeMessage(5, ProtoUtils.timestampToProto(it)) }
        if (value.unreadCount != 0) cos.writeInt32(6, value.unreadCount)
        value.lastMessageTime?.let { cos.writeMessage(7, ProtoUtils.timestampToProto(it)) }
        if (value.creator.isNotEmpty()) cos.writeString(8, value.creator)
        if (value.lastMessageText.isNotEmpty()) cos.writeString(9, value.lastMessageText)
        if (value.avatarUrl.isNotEmpty()) cos.writeString(10, value.avatarUrl)
        if (value.fullAvatarUrl.isNotEmpty()) cos.writeString(11, value.fullAvatarUrl)
        if (value.lastMessageUsername.isNotEmpty()) cos.writeString(12, value.lastMessageUsername)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): ChatInfoProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var id = ""
        var name = ""
        var type = ""
        var participants = ""
        var createdAt: Timestamp? = null
        var unreadCount = 0
        var lastMessageTime: Timestamp? = null
        var creator = ""
        var lastMessageText = ""
        var avatarUrl = ""
        var fullAvatarUrl = ""
        var lastMessageUsername = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> id = cis.readString()
                2 -> name = cis.readString()
                3 -> type = cis.readString()
                4 -> participants = cis.readString()
                5 -> createdAt = ProtoUtils.parseTimestampFromProto(cis.readBytes().newInput())
                6 -> unreadCount = cis.readInt32()
                7 -> lastMessageTime = ProtoUtils.parseTimestampFromProto(cis.readBytes().newInput())
                8 -> creator = cis.readString()
                9 -> lastMessageText = cis.readString()
                10 -> avatarUrl = cis.readString()
                11 -> fullAvatarUrl = cis.readString()
                12 -> lastMessageUsername = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return ChatInfoProto(id, name, type, participants, createdAt, unreadCount, lastMessageTime, creator, lastMessageText, avatarUrl, fullAvatarUrl, lastMessageUsername)
    }
}

class GetChatsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetChatsRequestProto> {
    override fun stream(value: GetChatsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        if (value.userId.isNotEmpty()) cos.writeString(2, value.userId)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetChatsRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        var userId = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> username = cis.readString()
                2 -> userId = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return GetChatsRequestProto(username, userId)
    }
}

class GetChatsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetChatsResponseProto> {
    override fun stream(value: GetChatsResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        val chatMarshaller = ChatInfoMarshaller()
        for (chat in value.chats) {
            val chatBytes = chatMarshaller.stream(chat).readBytes()
            cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(chatBytes.size)
            cos.writeRawBytes(chatBytes)
        }
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetChatsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        val chats = mutableListOf<ChatInfoProto>()
        val chatMarshaller = ChatInfoMarshaller()
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                val length = cis.readUInt32()
                chats.add(chatMarshaller.parse(java.io.ByteArrayInputStream(cis.readRawBytes(length))))
            } else cis.skipField(tag)
        }
        return GetChatsResponseProto(chats)
    }
}

class CreateDirectChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateDirectChatRequestProto> {
    override fun stream(value: CreateDirectChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.user1.isNotEmpty()) cos.writeString(1, value.user1)
        if (value.user2.isNotEmpty()) cos.writeString(2, value.user2)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): CreateDirectChatRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var user1 = ""
        var user2 = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> user1 = cis.readString()
                2 -> user2 = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return CreateDirectChatRequestProto(user1, user2)
    }
}

class CreateDirectChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateDirectChatResponseProto> {
    override fun stream(value: CreateDirectChatResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.chatId.isNotEmpty()) cos.writeString(1, value.chatId)
        if (value.success) cos.writeBool(2, value.success)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): CreateDirectChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var chatId = ""
        var success = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> chatId = cis.readString()
                2 -> success = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return CreateDirectChatResponseProto(chatId, success)
    }
}

class CreateGroupChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateGroupChatRequestProto> {
    override fun stream(value: CreateGroupChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.name.isNotEmpty()) cos.writeString(1, value.name)
        for (participant in value.participants) {
            cos.writeString(2, participant)
        }
        if (value.creator.isNotEmpty()) cos.writeString(3, value.creator)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): CreateGroupChatRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var name = ""
        val participants = mutableListOf<String>()
        var creator = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> name = cis.readString()
                2 -> participants.add(cis.readString())
                3 -> creator = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return CreateGroupChatRequestProto(name, participants, creator)
    }
}

class CreateGroupChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<CreateGroupChatResponseProto> {
    override fun stream(value: CreateGroupChatResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.chatId.isNotEmpty()) cos.writeString(1, value.chatId)
        if (value.success) cos.writeBool(2, value.success)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): CreateGroupChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var chatId = ""
        var success = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> chatId = cis.readString()
                2 -> success = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return CreateGroupChatResponseProto(chatId, success)
    }
}

class UpdateUsernameRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateUsernameRequestProto> {
    override fun stream(value: UpdateUsernameRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.oldUsername.isNotEmpty()) cos.writeString(1, value.oldUsername)
        if (value.newUsername.isNotEmpty()) cos.writeString(2, value.newUsername)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): UpdateUsernameRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var oldUsername = ""
        var newUsername = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> oldUsername = cis.readString()
                2 -> newUsername = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return UpdateUsernameRequestProto(oldUsername, newUsername)
    }
}

class UpdateUsernameResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateUsernameResponseProto> {
    override fun stream(value: UpdateUsernameResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): UpdateUsernameResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
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
        return UpdateUsernameResponseProto(success, message)
    }
}

class UpdatePasswordRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdatePasswordRequestProto> {
    override fun stream(value: UpdatePasswordRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        if (value.oldPassword.isNotEmpty()) cos.writeString(2, value.oldPassword)
        if (value.newPassword.isNotEmpty()) cos.writeString(3, value.newPassword)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): UpdatePasswordRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        var oldPassword = ""
        var newPassword = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> username = cis.readString()
                2 -> oldPassword = cis.readString()
                3 -> newPassword = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return UpdatePasswordRequestProto(username, oldPassword, newPassword)
    }
}

class UpdatePasswordResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdatePasswordResponseProto> {
    override fun stream(value: UpdatePasswordResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): UpdatePasswordResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
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
        return UpdatePasswordResponseProto(success, message)
    }
}

class MarkReadRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<MarkReadRequestProto> {
    override fun stream(value: MarkReadRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.roomId.isNotEmpty()) cos.writeString(1, value.roomId)
        if (value.username.isNotEmpty()) cos.writeString(2, value.username)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): MarkReadRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var roomId = ""
        var username = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> roomId = cis.readString()
                2 -> username = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return MarkReadRequestProto(roomId, username)
    }
}

class MarkReadResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<MarkReadResponseProto> {
    override fun stream(value: MarkReadResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): MarkReadResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var success = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) success = cis.readBool()
            else cis.skipField(tag)
        }
        return MarkReadResponseProto(success)
    }
}

class ReactionRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<ReactionRequestProto> {
    override fun stream(value: ReactionRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.messageId.isNotEmpty()) cos.writeString(1, value.messageId)
        cos.writeTag(2, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        val rBaos = java.io.ByteArrayOutputStream()
        val rCos = com.google.protobuf.CodedOutputStream.newInstance(rBaos)
        if (value.reaction.user.isNotEmpty()) rCos.writeString(1, value.reaction.user)
        if (value.reaction.emoji.isNotEmpty()) rCos.writeString(2, value.reaction.emoji)
        rCos.flush()
        val bytes = rBaos.toByteArray()
        cos.writeUInt32NoTag(bytes.size)
        cos.writeRawBytes(bytes)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): ReactionRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var messageId = ""
        var reaction = ReactionProto()
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> messageId = cis.readString()
                2 -> {
                    val length = cis.readUInt32()
                    reaction = ReactionProtoMarshaller().parse(java.io.ByteArrayInputStream(cis.readRawBytes(length)))
                }
                else -> cis.skipField(tag)
            }
        }
        return ReactionRequestProto(messageId, reaction)
    }
}

class ReactionResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<ReactionResponseProto> {
    override fun stream(value: ReactionResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): ReactionResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var success = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) success = cis.readBool()
            else cis.skipField(tag)
        }
        return ReactionResponseProto(success)
    }
}

class TokenRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<TokenRequestProto> {
    override fun stream(value: TokenRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.user.isNotEmpty()) cos.writeString(1, value.user)
        if (value.token.isNotEmpty()) cos.writeString(2, value.token)
        if (value.pushEnabled) cos.writeBool(3, value.pushEnabled)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): TokenRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var user = ""
        var token = ""
        var pushEnabled = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> user = cis.readString()
                2 -> token = cis.readString()
                3 -> pushEnabled = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return TokenRequestProto(user, token, pushEnabled)
    }
}

class TokenResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<TokenResponseProto> {
    override fun stream(value: TokenResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): TokenResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var success = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) success = cis.readBool()
            else cis.skipField(tag)
        }
        return TokenResponseProto(success)
    }
}

class UpdateAvatarRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateAvatarRequestProto> {
    override fun stream(value: UpdateAvatarRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        if (value.avatarUrl.isNotEmpty()) cos.writeString(2, value.avatarUrl)
        if (value.fullAvatarUrl.isNotEmpty()) cos.writeString(3, value.fullAvatarUrl)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): UpdateAvatarRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        var avatarUrl = ""
        var fullAvatarUrl = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> username = cis.readString()
                2 -> avatarUrl = cis.readString()
                3 -> fullAvatarUrl = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return UpdateAvatarRequestProto(username, avatarUrl, fullAvatarUrl)
    }
}

class UpdateAvatarResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateAvatarResponseProto> {
    override fun stream(value: UpdateAvatarResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): UpdateAvatarResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
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
        return UpdateAvatarResponseProto(success, message)
    }
}

class UpdateProfileRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateProfileRequestProto> {
    override fun stream(value: UpdateProfileRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        if (value.bio.isNotEmpty()) cos.writeString(2, value.bio)
        if (value.status.isNotEmpty()) cos.writeString(3, value.status)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): UpdateProfileRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        var bio = ""
        var status = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> username = cis.readString()
                2 -> bio = cis.readString()
                3 -> status = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return UpdateProfileRequestProto(username, bio, status)
    }
}

class UpdateProfileResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateProfileResponseProto> {
    override fun stream(value: UpdateProfileResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): UpdateProfileResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
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
        return UpdateProfileResponseProto(success, message)
    }
}

class GetUserProfileRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserProfileRequestProto> {
    override fun stream(value: GetUserProfileRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetUserProfileRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) username = cis.readString()
            else cis.skipField(tag)
        }
        return GetUserProfileRequestProto(username)
    }
}

class GetUserProfileResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserProfileResponseProto> {
    override fun stream(value: GetUserProfileResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        if (value.bio.isNotEmpty()) cos.writeString(2, value.bio)
        if (value.status.isNotEmpty()) cos.writeString(3, value.status)
        if (value.avatarUrl.isNotEmpty()) cos.writeString(4, value.avatarUrl)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetUserProfileResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        var bio = ""
        var status = ""
        var avatarUrl = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> username = cis.readString()
                2 -> bio = cis.readString()
                3 -> status = cis.readString()
                4 -> avatarUrl = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return GetUserProfileResponseProto(username, bio, status, avatarUrl)
    }
}

class DeleteProfileRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteProfileRequestProto> {
    override fun stream(value: DeleteProfileRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): DeleteProfileRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) username = cis.readString()
            else cis.skipField(tag)
        }
        return DeleteProfileRequestProto(username)
    }
}

class DeleteProfileResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteProfileResponseProto> {
    override fun stream(value: DeleteProfileResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): DeleteProfileResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
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
        return DeleteProfileResponseProto(success, message)
    }
}

class TypingRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<TypingRequestProto> {
    override fun stream(value: TypingRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.roomId.isNotEmpty()) cos.writeString(1, value.roomId)
        if (value.username.isNotEmpty()) cos.writeString(2, value.username)
        if (value.isTyping) cos.writeBool(3, value.isTyping)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): TypingRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var roomId = ""
        var username = ""
        var isTyping = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> roomId = cis.readString()
                2 -> username = cis.readString()
                3 -> isTyping = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return TypingRequestProto(roomId, username, isTyping)
    }
}

class TypingSignalMarshaller : io.grpc.MethodDescriptor.Marshaller<TypingSignalProto> {
    override fun stream(value: TypingSignalProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.roomId.isNotEmpty()) cos.writeString(1, value.roomId)
        if (value.username.isNotEmpty()) cos.writeString(2, value.username)
        if (value.isTyping) cos.writeBool(3, value.isTyping)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): TypingSignalProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var roomId = ""
        var username = ""
        var isTyping = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> roomId = cis.readString()
                2 -> username = cis.readString()
                3 -> isTyping = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return TypingSignalProto(roomId, username, isTyping)
    }
}

class AddContactRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<AddContactRequestProto> {
    override fun stream(value: AddContactRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        if (value.contactUsername.isNotEmpty()) cos.writeString(2, value.contactUsername)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): AddContactRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        var contactUsername = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> username = cis.readString()
                2 -> contactUsername = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return AddContactRequestProto(username, contactUsername)
    }
}

class AddContactResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<AddContactResponseProto> {
    override fun stream(value: AddContactResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): AddContactResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
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
        return AddContactResponseProto(success, message)
    }
}

class RemoveContactRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveContactRequestProto> {
    override fun stream(value: RemoveContactRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        if (value.contactUsername.isNotEmpty()) cos.writeString(2, value.contactUsername)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): RemoveContactRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        var contactUsername = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> username = cis.readString()
                2 -> contactUsername = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return RemoveContactRequestProto(username, contactUsername)
    }
}

class RemoveContactResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveContactResponseProto> {
    override fun stream(value: RemoveContactResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): RemoveContactResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
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
        return RemoveContactResponseProto(success, message)
    }
}

class GetContactsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetContactsRequestProto> {
    override fun stream(value: GetContactsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetContactsRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) username = cis.readString()
            else cis.skipField(tag)
        }
        return GetContactsRequestProto(username)
    }
}

class GetContactsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetContactsResponseProto> {
    override fun stream(value: GetContactsResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        for (contact in value.contacts) {
            cos.writeString(1, contact)
        }
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetContactsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        val contacts = mutableListOf<String>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                contacts.add(cis.readString())
            } else cis.skipField(tag)
        }
        return GetContactsResponseProto(contacts)
    }
}

class GetChatListVersionRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetChatListVersionRequestProto> {
    override fun stream(value: GetChatListVersionRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetChatListVersionRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) username = cis.readString()
            else cis.skipField(tag)
        }
        return GetChatListVersionRequestProto(username)
    }
}

class GetChatListVersionResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetChatListVersionResponseProto> {
    override fun stream(value: GetChatListVersionResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.version != 0L) cos.writeInt64(1, value.version)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetChatListVersionResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var version = 0L
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) version = cis.readInt64()
            else cis.skipField(tag)
        }
        return GetChatListVersionResponseProto(version)
    }
}

class GetThemesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetThemesRequestProto> {
    override fun stream(value: GetThemesRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        if (value.userId.isNotEmpty()) cos.writeString(2, value.userId) // New field
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetThemesRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        var userId = "" // New field
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> username = cis.readString()
                2 -> userId = cis.readString() // New field
                else -> cis.skipField(tag)
            }
        }
        return GetThemesRequestProto(username, userId)
    }
}

class GetThemesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetThemesResponseProto> {
    override fun stream(value: GetThemesResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.currentThemeId.isNotEmpty()) cos.writeString(1, value.currentThemeId)
        for (theme in value.customThemes) {
            cos.writeTag(2, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
            val tBaos = java.io.ByteArrayOutputStream()
            val tCos = com.google.protobuf.CodedOutputStream.newInstance(tBaos)
            writeTheme(tCos, theme)
            tCos.flush()
            val bytes = tBaos.toByteArray()
            cos.writeUInt32NoTag(bytes.size)
            cos.writeRawBytes(bytes)
        }
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetThemesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var currentId = ""
        val themes = mutableListOf<CustomThemeProto>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> currentId = cis.readString()
                2 -> themes.add(parseTheme(cis.readBytes().newInput()))
                else -> cis.skipField(tag)
            }
        }
        return GetThemesResponseProto(currentId, themes)
    }
}

class SaveThemeRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SaveThemeRequestProto> {
    override fun stream(value: SaveThemeRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        cos.writeTag(2, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        val tBaos = java.io.ByteArrayOutputStream()
        val tCos = com.google.protobuf.CodedOutputStream.newInstance(tBaos)
        writeTheme(tCos, value.theme)
        tCos.flush()
        val bytes = tBaos.toByteArray()
        cos.writeUInt32NoTag(bytes.size)
        cos.writeRawBytes(bytes)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): SaveThemeRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        var theme = CustomThemeProto()
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> username = cis.readString()
                2 -> theme = parseTheme(cis.readBytes().newInput())
                else -> cis.skipField(tag)
            }
        }
        return SaveThemeRequestProto(username, theme)
    }
}

class SaveThemeResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SaveThemeResponseProto> {
    override fun stream(value: SaveThemeResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): SaveThemeResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
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
        return SaveThemeResponseProto(success, message)
    }
}

class SetCurrentThemeRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SetCurrentThemeRequestProto> {
    override fun stream(value: SetCurrentThemeRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        if (value.themeId.isNotEmpty()) cos.writeString(2, value.themeId)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): SetCurrentThemeRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        var themeId = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> username = cis.readString()
                2 -> themeId = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return SetCurrentThemeRequestProto(username, themeId)
    }
}

class SetCurrentThemeResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SetCurrentThemeResponseProto> {
    override fun stream(value: SetCurrentThemeResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): SetCurrentThemeResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var success = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) success = cis.readBool()
            else cis.skipField(tag)
        }
        return SetCurrentThemeResponseProto(success)
    }
}

class DeleteThemeRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteThemeRequestProto> {
    override fun stream(value: DeleteThemeRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        if (value.themeId.isNotEmpty()) cos.writeString(2, value.themeId)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): DeleteThemeRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        var themeId = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> username = cis.readString()
                2 -> themeId = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return DeleteThemeRequestProto(username, themeId)
    }
}

class DeleteThemeResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteThemeResponseProto> {
    override fun stream(value: DeleteThemeResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): DeleteThemeResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var success = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) success = cis.readBool()
            else cis.skipField(tag)
        }
        return DeleteThemeResponseProto(success)
    }
}

class UpdateChatNameRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatNameRequestProto> {
    override fun stream(value: UpdateChatNameRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.chatId.isNotEmpty()) cos.writeString(1, value.chatId)
        if (value.newName.isNotEmpty()) cos.writeString(2, value.newName)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): UpdateChatNameRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var chatId = ""
        var newName = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> chatId = cis.readString()
                2 -> newName = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return UpdateChatNameRequestProto(chatId, newName)
    }
}

class UpdateChatNameResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatNameResponseProto> {
    override fun stream(value: UpdateChatNameResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): UpdateChatNameResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
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
        return UpdateChatNameResponseProto(success, message)
    }
}

class UpdateChatAvatarRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatAvatarRequestProto> {
    override fun stream(value: UpdateChatAvatarRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.chatId.isNotEmpty()) cos.writeString(1, value.chatId)
        if (value.avatarUrl.isNotEmpty()) cos.writeString(2, value.avatarUrl)
        if (value.username.isNotEmpty()) cos.writeString(3, value.username)
        if (value.fullAvatarUrl.isNotEmpty()) cos.writeString(4, value.fullAvatarUrl)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): UpdateChatAvatarRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var chatId = ""
        var avatarUrl = ""
        var username = ""
        var fullAvatarUrl = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> chatId = cis.readString()
                2 -> avatarUrl = cis.readString()
                3 -> username = cis.readString()
                4 -> fullAvatarUrl = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return UpdateChatAvatarRequestProto(chatId, avatarUrl, username, fullAvatarUrl)
    }
}

class UpdateChatAvatarResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateChatAvatarResponseProto> {
    override fun stream(value: UpdateChatAvatarResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): UpdateChatAvatarResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
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
        return UpdateChatAvatarResponseProto(success, message)
    }
}

class GetAllChatsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetAllChatsRequestProto> {
    override fun stream(value: GetAllChatsRequestProto): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))
    override fun parse(stream: java.io.InputStream): GetAllChatsRequestProto = GetAllChatsRequestProto()
}

class GetAllChatsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetAllChatsResponseProto> {
    override fun stream(value: GetAllChatsResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        val chatMarshaller = ChatInfoMarshaller()
        for (chat in value.chats) {
            val chatBytes = chatMarshaller.stream(chat).readBytes()
            cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(chatBytes.size)
            cos.writeRawBytes(chatBytes)
        }
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetAllChatsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        val chats = mutableListOf<ChatInfoProto>()
        val chatMarshaller = ChatInfoMarshaller()
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                val length = cis.readUInt32()
                chats.add(chatMarshaller.parse(java.io.ByteArrayInputStream(cis.readRawBytes(length))))
            } else cis.skipField(tag)
        }
        return GetAllChatsResponseProto(chats)
    }
}

private fun writeTheme(cos: com.google.protobuf.CodedOutputStream, theme: CustomThemeProto) {
    if (theme.id.isNotEmpty()) cos.writeString(1, theme.id)
    if (theme.name.isNotEmpty()) cos.writeString(2, theme.name)
    if (theme.primaryColor.isNotEmpty()) cos.writeString(3, theme.primaryColor)
    if (theme.onPrimaryColor.isNotEmpty()) cos.writeString(4, theme.onPrimaryColor)
    if (theme.surfaceColor.isNotEmpty()) cos.writeString(5, theme.surfaceColor)
    if (theme.onSurfaceColor.isNotEmpty()) cos.writeString(6, theme.onSurfaceColor)
    if (theme.backgroundColor.isNotEmpty()) cos.writeString(7, theme.backgroundColor)
    if (theme.textPrimaryColor.isNotEmpty()) cos.writeString(8, theme.textPrimaryColor)
    if (theme.textSecondaryColor.isNotEmpty()) cos.writeString(9, theme.textSecondaryColor)
    // tag 10 is is_dark (bool)
    if (theme.chatBackgroundImageUrl.isNotEmpty()) cos.writeString(11, theme.chatBackgroundImageUrl)
    if (theme.chatListBackgroundImageUrl.isNotEmpty()) cos.writeString(12, theme.chatListBackgroundImageUrl)
    if (theme.bottomPanelColor.isNotEmpty()) cos.writeString(13, theme.bottomPanelColor)
    if (theme.onBottomPanelColor.isNotEmpty()) cos.writeString(14, theme.onBottomPanelColor)
    if (theme.surfaceContainer.isNotEmpty()) cos.writeString(15, theme.surfaceContainer)
    if (theme.outgoingBubbleColor.isNotEmpty()) cos.writeString(16, theme.outgoingBubbleColor)
    if (theme.incomingBubbleColor.isNotEmpty()) cos.writeString(17, theme.incomingBubbleColor)
}

private fun parseTheme(stream: java.io.InputStream): CustomThemeProto {
    val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
    var id = ""
    var name = ""
    var primaryColor = ""
    var onPrimaryColor = ""
    var surfaceColor = ""
    var onSurfaceColor = ""
    var backgroundColor = ""
    var textPrimaryColor = ""
    var textSecondaryColor = ""
    var chatBackgroundImageUrl = ""
    var chatListBackgroundImageUrl = ""
    var bottomPanelColor = ""
    var onBottomPanelColor = ""
    var surfaceContainer = ""
    var outgoingBubbleColor = ""
    var incomingBubbleColor = ""
    while (!cis.isAtEnd) {
        val tag = cis.readTag()
        if (tag == 0) break
        when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
            1 -> id = cis.readString()
            2 -> name = cis.readString()
            3 -> primaryColor = cis.readString()
            4 -> onPrimaryColor = cis.readString()
            5 -> surfaceColor = cis.readString()
            6 -> onSurfaceColor = cis.readString()
            7 -> backgroundColor = cis.readString()
            8 -> textPrimaryColor = cis.readString()
            9 -> textSecondaryColor = cis.readString()
            // 10 -> is_dark
            11 -> chatBackgroundImageUrl = cis.readString()
            12 -> chatListBackgroundImageUrl = cis.readString()
            13 -> bottomPanelColor = cis.readString()
            14 -> onBottomPanelColor = cis.readString()
            15 -> surfaceContainer = cis.readString()
            16 -> outgoingBubbleColor = cis.readString()
            17 -> incomingBubbleColor = cis.readString()
            else -> cis.skipField(tag)
        }
    }
    return CustomThemeProto(
        id = id,
        name = name,
        primaryColor = primaryColor,
        onPrimaryColor = onPrimaryColor,
        surfaceColor = surfaceColor,
        onSurfaceColor = onSurfaceColor,
        backgroundColor = backgroundColor,
        textPrimaryColor = textPrimaryColor,
        textSecondaryColor = textSecondaryColor,
        chatListBackgroundImageUrl = chatListBackgroundImageUrl,
        chatBackgroundImageUrl = chatBackgroundImageUrl,
        bottomPanelColor = bottomPanelColor,
        onBottomPanelColor = onBottomPanelColor,
        surfaceContainer = surfaceContainer,
        outgoingBubbleColor = outgoingBubbleColor,
        incomingBubbleColor = incomingBubbleColor
    )
}

class GetFCMLogsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.GetFCMLogsRequestProto> {
    override fun stream(value: lavender.client.android.data.proto.GetFCMLogsRequestProto): java.io.InputStream {
        return java.io.ByteArrayInputStream(ByteArray(0))
    }
    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.GetFCMLogsRequestProto = lavender.client.android.data.proto.GetFCMLogsRequestProto()
}

class GetFCMLogsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.GetFCMLogsResponseProto> {
    override fun stream(value: lavender.client.android.data.proto.GetFCMLogsResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        for (l in value.logs) {
            val innerBaos = java.io.ByteArrayOutputStream()
            val innerCos = com.google.protobuf.CodedOutputStream.newInstance(innerBaos)
            if (l.timestamp.isNotEmpty()) innerCos.writeString(1, l.timestamp)
            if (l.level.isNotEmpty()) innerCos.writeString(2, l.level)
            if (l.message.isNotEmpty()) innerCos.writeString(3, l.message)
            innerCos.flush()
            cos.writeBytes(1, com.google.protobuf.ByteString.copyFrom(innerBaos.toByteArray()))
        }
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.GetFCMLogsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        val logs = mutableListOf<lavender.client.android.data.proto.FCMLogEntryProto>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                val bytes = cis.readBytes()
                val innerCis = bytes.newCodedInput()
                var ts = ""; var level = ""; var msg = ""
                while (!innerCis.isAtEnd) {
                    val innerTag = innerCis.readTag()
                    if (innerTag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                        1 -> ts = innerCis.readString()
                        2 -> level = innerCis.readString()
                        3 -> msg = innerCis.readString()
                        else -> innerCis.skipField(innerTag)
                    }
                }
                logs.add(lavender.client.android.data.proto.FCMLogEntryProto(ts, level, msg))
            } else cis.skipField(tag)
        }
        return lavender.client.android.data.proto.GetFCMLogsResponseProto(logs)
    }
}

// Draft message marshallers
class SaveDraftRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.SaveDraftRequestProto> {
    override fun stream(value: lavender.client.android.data.proto.SaveDraftRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.userId.isNotEmpty()) cos.writeString(1, value.userId)
        if (value.roomId.isNotEmpty()) cos.writeString(2, value.roomId)
        if (value.draftText.isNotEmpty()) cos.writeString(3, value.draftText)
        if (value.repliedToMessageId.isNotEmpty()) cos.writeString(4, value.repliedToMessageId)
        if (value.repliedToUser.isNotEmpty()) cos.writeString(5, value.repliedToUser)
        if (value.repliedToText.isNotEmpty()) cos.writeString(6, value.repliedToText)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.SaveDraftRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var userId = ""
        var roomId = ""
        var draftText = ""
        var repliedToMessageId = ""
        var repliedToUser = ""
        var repliedToText = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> userId = cis.readString()
                2 -> roomId = cis.readString()
                3 -> draftText = cis.readString()
                4 -> repliedToMessageId = cis.readString()
                5 -> repliedToUser = cis.readString()
                6 -> repliedToText = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return lavender.client.android.data.proto.SaveDraftRequestProto(userId, roomId, draftText, repliedToMessageId, repliedToUser, repliedToText)
    }
}

class SaveDraftResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.SaveDraftResponseProto> {
    override fun stream(value: lavender.client.android.data.proto.SaveDraftResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.SaveDraftResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
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
        return lavender.client.android.data.proto.SaveDraftResponseProto(success, message)
    }
}

class GetDraftRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.GetDraftRequestProto> {
    override fun stream(value: lavender.client.android.data.proto.GetDraftRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.userId.isNotEmpty()) cos.writeString(1, value.userId)
        if (value.roomId.isNotEmpty()) cos.writeString(2, value.roomId)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.GetDraftRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var userId = ""
        var roomId = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> userId = cis.readString()
                2 -> roomId = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return lavender.client.android.data.proto.GetDraftRequestProto(userId, roomId)
    }
}

class GetDraftResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.GetDraftResponseProto> {
    override fun stream(value: lavender.client.android.data.proto.GetDraftResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.draftText.isNotEmpty()) cos.writeString(1, value.draftText)
        if (value.repliedToMessageId.isNotEmpty()) cos.writeString(2, value.repliedToMessageId)
        if (value.repliedToUser.isNotEmpty()) cos.writeString(3, value.repliedToUser)
        if (value.repliedToText.isNotEmpty()) cos.writeString(4, value.repliedToText)
        if (value.hasDraft) cos.writeBool(5, value.hasDraft)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.GetDraftResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var draftText = ""
        var repliedToMessageId = ""
        var repliedToUser = ""
        var repliedToText = ""
        var hasDraft = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> draftText = cis.readString()
                2 -> repliedToMessageId = cis.readString()
                3 -> repliedToUser = cis.readString()
                4 -> repliedToText = cis.readString()
                5 -> hasDraft = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return lavender.client.android.data.proto.GetDraftResponseProto(draftText, repliedToMessageId, repliedToUser, repliedToText, hasDraft)
    }
}

class DeleteDraftRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.DeleteDraftRequestProto> {
    override fun stream(value: lavender.client.android.data.proto.DeleteDraftRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.userId.isNotEmpty()) cos.writeString(1, value.userId)
        if (value.roomId.isNotEmpty()) cos.writeString(2, value.roomId)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.DeleteDraftRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var userId = ""
        var roomId = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> userId = cis.readString()
                2 -> roomId = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return lavender.client.android.data.proto.DeleteDraftRequestProto(userId, roomId)
    }
}

class DeleteDraftResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.DeleteDraftResponseProto> {
    override fun stream(value: lavender.client.android.data.proto.DeleteDraftResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.DeleteDraftResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var success = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) success = cis.readBool()
            else cis.skipField(tag)
        }
        return lavender.client.android.data.proto.DeleteDraftResponseProto(success)
    }
}

// Marshallers for Muted Chats
class GetMutedChatsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.GetMutedChatsRequestProto> {
    override fun stream(value: lavender.client.android.data.proto.GetMutedChatsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.userId.isNotEmpty()) cos.writeString(1, value.userId)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.GetMutedChatsRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var userId = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) userId = cis.readString()
            else cis.skipField(tag)
        }
        return lavender.client.android.data.proto.GetMutedChatsRequestProto(userId)
    }
}

class GetMutedChatsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.GetMutedChatsResponseProto> {
    override fun stream(value: lavender.client.android.data.proto.GetMutedChatsResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        for (roomId in value.roomIds) cos.writeString(1, roomId)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.GetMutedChatsResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        val roomIds = mutableListOf<String>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) roomIds.add(cis.readString())
            else cis.skipField(tag)
        }
        return lavender.client.android.data.proto.GetMutedChatsResponseProto(roomIds)
    }
}

class SetMutedChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.SetMutedChatRequestProto> {
    override fun stream(value: lavender.client.android.data.proto.SetMutedChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.userId.isNotEmpty()) cos.writeString(1, value.userId)
        if (value.roomId.isNotEmpty()) cos.writeString(2, value.roomId)
        cos.writeBool(3, value.muted)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.SetMutedChatRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var userId = ""; var roomId = ""; var muted = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> userId = cis.readString()
                2 -> roomId = cis.readString()
                3 -> muted = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return lavender.client.android.data.proto.SetMutedChatRequestProto(userId, roomId, muted)
    }
}

class SetMutedChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.SetMutedChatResponseProto> {
    override fun stream(value: lavender.client.android.data.proto.SetMutedChatResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.SetMutedChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var success = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) success = cis.readBool()
            else cis.skipField(tag)
        }
        return lavender.client.android.data.proto.SetMutedChatResponseProto(success)
    }
}

class GetUserIdRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.GetUserIdRequestProto> {
    override fun stream(value: lavender.client.android.data.proto.GetUserIdRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.GetUserIdRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) username = cis.readString()
            else cis.skipField(tag)
        }
        return lavender.client.android.data.proto.GetUserIdRequestProto(username)
    }
}

class GetUserIdResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserIdResponseProto> {
    override fun stream(value: GetUserIdResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.userId.isNotEmpty()) cos.writeString(1, value.userId)
        if (value.found) cos.writeBool(2, value.found)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetUserIdResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var userId = ""
        var found = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> userId = cis.readString()
                2 -> found = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return GetUserIdResponseProto(userId, found)
    }
}

class GetUserAvatarRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserAvatarRequestProto> {
    override fun stream(value: GetUserAvatarRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetUserAvatarRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) username = cis.readString()
            else cis.skipField(tag)
        }
        return GetUserAvatarRequestProto(username)
    }
}

class GetUserAvatarResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetUserAvatarResponseProto> {
    override fun stream(value: GetUserAvatarResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.avatarUrl.isNotEmpty()) cos.writeString(1, value.avatarUrl)
        if (value.fullAvatarUrl.isNotEmpty()) cos.writeString(2, value.fullAvatarUrl)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetUserAvatarResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var avatarUrl = ""
        var fullAvatarUrl = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> avatarUrl = cis.readString()
                2 -> fullAvatarUrl = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return GetUserAvatarResponseProto(avatarUrl, fullAvatarUrl)
    }
}

class DeleteChatRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteChatRequestProto> {
    override fun stream(value: DeleteChatRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.chatId.isNotEmpty()) cos.writeString(1, value.chatId)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): DeleteChatRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var chatId = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) chatId = cis.readString()
            else cis.skipField(tag)
        }
        return DeleteChatRequestProto(chatId)
    }
}

class DeleteChatResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteChatResponseProto> {
    override fun stream(value: DeleteChatResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): DeleteChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
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
        return DeleteChatResponseProto(success, message)
    }
}

class GetHistoryRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetHistoryRequestProto> {
    override fun stream(value: GetHistoryRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.limit != 0) cos.writeInt32(1, value.limit)
        if (value.room.isNotEmpty()) cos.writeString(2, value.room)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetHistoryRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var limit = 50
        var room = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> limit = cis.readInt32()
                2 -> room = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return GetHistoryRequestProto(limit, room)
    }
}

class GetHistoryResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetHistoryResponseProto> {
    override fun stream(value: GetHistoryResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        for (msg in value.messages) {
            cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
            val msgBytes = MessageProtoMarshaller().stream(msg).readBytes()
            cos.writeUInt32NoTag(msgBytes.size)
            cos.writeRawBytes(msgBytes)
        }
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): GetHistoryResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        val messages = mutableListOf<MessageProto>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                val length = cis.readUInt32()
                val bytes = cis.readRawBytes(length)
                messages.add(MessageProtoMarshaller().parse(java.io.ByteArrayInputStream(bytes)))
            } else cis.skipField(tag)
        }
        return GetHistoryResponseProto(messages)
    }
}

class DeleteMessagesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteMessagesRequestProto> {
    override fun stream(value: DeleteMessagesRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        for (msg in value.messages) {
            cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
            val msgBytes = MessageProtoMarshaller().stream(msg).readBytes()
            cos.writeUInt32NoTag(msgBytes.size)
            cos.writeRawBytes(msgBytes)
        }
        if (value.requesterUsername.isNotEmpty()) cos.writeString(2, value.requesterUsername)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): DeleteMessagesRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        val messages = mutableListOf<MessageProto>()
        var requesterUsername = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> {
                    val length = cis.readUInt32()
                    val bytes = cis.readRawBytes(length)
                    messages.add(MessageProtoMarshaller().parse(java.io.ByteArrayInputStream(bytes)))
                }
                2 -> requesterUsername = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return DeleteMessagesRequestProto(messages, requesterUsername)
    }
}

class DeleteMessagesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteMessagesResponseProto> {
    override fun stream(value: DeleteMessagesResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): DeleteMessagesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var success = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) success = cis.readBool()
            else cis.skipField(tag)
        }
        return DeleteMessagesResponseProto(success)
    }
}

class EditMessageRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<EditMessageRequestProto> {
    override fun stream(value: EditMessageRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.messageId.isNotEmpty()) cos.writeString(1, value.messageId)
        if (value.text.isNotEmpty()) cos.writeString(2, value.text)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): EditMessageRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var messageId = ""
        var text = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> messageId = cis.readString()
                2 -> text = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return EditMessageRequestProto(messageId, text)
    }
}

class EditMessageResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<EditMessageResponseProto> {
    override fun stream(value: EditMessageResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): EditMessageResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var success = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) success = cis.readBool()
            else cis.skipField(tag)
        }
        return EditMessageResponseProto(success)
    }
}

class AddParticipantRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<AddParticipantRequestProto> {
    override fun stream(value: AddParticipantRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.chatId.isNotEmpty()) cos.writeString(1, value.chatId)
        if (value.username.isNotEmpty()) cos.writeString(2, value.username)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): AddParticipantRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var chatId = ""
        var username = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> chatId = cis.readString()
                2 -> username = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return AddParticipantRequestProto(chatId, username)
    }
}

class AddParticipantResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<AddParticipantResponseProto> {
    override fun stream(value: AddParticipantResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): AddParticipantResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
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
        return AddParticipantResponseProto(success, message)
    }
}


class RemoveParticipantRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveParticipantRequestProto> {
    override fun stream(value: RemoveParticipantRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.chatId.isNotEmpty()) cos.writeString(1, value.chatId)
        if (value.username.isNotEmpty()) cos.writeString(2, value.username)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): RemoveParticipantRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var chatId = ""
        var username = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> chatId = cis.readString()
                2 -> username = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return RemoveParticipantRequestProto(chatId, username)
    }
}

class RemoveParticipantResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<RemoveParticipantResponseProto> {
    override fun stream(value: RemoveParticipantResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): RemoveParticipantResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
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
        return RemoveParticipantResponseProto(success, message)
    }
}

class AddFavoriteRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.AddFavoriteRequestProto> {
    override fun stream(value: lavender.client.android.data.proto.AddFavoriteRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.userId.isNotEmpty()) cos.writeString(1, value.userId)
        if (value.messageId.isNotEmpty()) cos.writeString(2, value.messageId)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.AddFavoriteRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var uid = ""; var mid = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> uid = cis.readString()
                2 -> mid = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return lavender.client.android.data.proto.AddFavoriteRequestProto(uid, mid)
    }
}

class AddFavoriteResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.AddFavoriteResponseProto> {
    override fun stream(value: lavender.client.android.data.proto.AddFavoriteResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.AddFavoriteResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var success = false; var msg = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> success = cis.readBool()
                2 -> msg = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return lavender.client.android.data.proto.AddFavoriteResponseProto(success, msg)
    }
}

class RemoveFavoriteRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.RemoveFavoriteRequestProto> {
    override fun stream(value: lavender.client.android.data.proto.RemoveFavoriteRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.userId.isNotEmpty()) cos.writeString(1, value.userId)
        if (value.messageId.isNotEmpty()) cos.writeString(2, value.messageId)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.RemoveFavoriteRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var uid = ""; var mid = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> uid = cis.readString()
                2 -> mid = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return lavender.client.android.data.proto.RemoveFavoriteRequestProto(uid, mid)
    }
}

class RemoveFavoriteResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.RemoveFavoriteResponseProto> {
    override fun stream(value: lavender.client.android.data.proto.RemoveFavoriteResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.success) cos.writeBool(1, value.success)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.RemoveFavoriteResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var success = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) success = cis.readBool()
            else cis.skipField(tag)
        }
        return lavender.client.android.data.proto.RemoveFavoriteResponseProto(success)
    }
}

class GetFavoritesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.GetFavoritesRequestProto> {
    override fun stream(value: lavender.client.android.data.proto.GetFavoritesRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.userId.isNotEmpty()) cos.writeString(1, value.userId)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.GetFavoritesRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var uid = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) uid = cis.readString()
            else cis.skipField(tag)
        }
        return lavender.client.android.data.proto.GetFavoritesRequestProto(uid)
    }
}

class GetFavoritesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<lavender.client.android.data.proto.GetFavoritesResponseProto> {
    override fun stream(value: lavender.client.android.data.proto.GetFavoritesResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        val msgMarshaller = MessageProtoMarshaller()
        for (msg in value.messages) {
            val mBytes = msgMarshaller.stream(msg).readBytes()
            cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(mBytes.size)
            cos.writeRawBytes(mBytes)
        }
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): lavender.client.android.data.proto.GetFavoritesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        val messages = mutableListOf<lavender.client.android.data.proto.MessageProto>()
        val msgMarshaller = MessageProtoMarshaller()
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                val length = cis.readUInt32()
                messages.add(msgMarshaller.parse(java.io.ByteArrayInputStream(cis.readRawBytes(length))))
            } else cis.skipField(tag)
        }
        return lavender.client.android.data.proto.GetFavoritesResponseProto(messages)
    }
}
