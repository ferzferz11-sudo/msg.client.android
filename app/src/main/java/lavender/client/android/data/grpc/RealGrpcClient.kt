package lavender.client.android.data.grpc

import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.Reaction
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
import lavender.client.android.data.proto.GetAllChatsRequestProto
import lavender.client.android.data.proto.GetAllChatsResponseProto
import java.util.concurrent.TimeUnit

object RealGrpcClient {
    private var channel: ManagedChannel? = null
    private var requestObserver: StreamObserver<MessageProto>? = null
    private var typingRequestObserver: StreamObserver<TypingRequestProto>? = null
    private var currentServerAddress: String? = null
    var currentRoomId = ""
        private set
    
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState
    
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

    // Avatar cache
    private val avatarCache = mutableMapOf<String, String>()

    private var isChatStarted = false
    private val sentMessageHashes = mutableSetOf<String>() // Track sent messages to prevent echo
    private val deletedMessageHashes = mutableSetOf<String>()
    private var appContext: android.content.Context? = null

    var hasCheckedForUpdates = false
    
    fun connect(serverAddress: String, useTls: Boolean = false, port: Int = 50051, context: android.content.Context? = null) {
        if (context != null) {
            this.appContext = context
            loadDeletedMessages()
        }
        
        if (_connectionState.value && currentServerAddress == serverAddress) {
            android.util.Log.d("GrpcClient", "Already connected to $serverAddress:$port")
            return
        }

        try {
            android.util.Log.d("GrpcClient", "Connecting to Go server at $serverAddress:$port")
            disconnect()
            
            val builder = OkHttpChannelBuilder.forAddress(serverAddress, port)
            if (useTls) builder.useTransportSecurity() else builder.usePlaintext()
            
            builder.directExecutor()
            builder.maxInboundMessageSize(16 * 1024 * 1024)
            builder.maxInboundMetadataSize(1024 * 1024)
            
            builder.keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .idleTimeout(24, TimeUnit.HOURS)
            
            channel = builder.build()
            currentServerAddress = serverAddress
            _connectionState.value = true
            _error.value = null
            
        } catch (e: Exception) {
            android.util.Log.e("GrpcClient", "Connection failed: ${e.message}")
            _error.value = "Connection failed: ${e.message}"
            _connectionState.value = false
        }
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
        return "${message.user}:${message.text}:${message.imageUrl}:${message.timestamp / 1000}"
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
        val request = GetHistoryRequestProto(limit = 100, room = roomId)

        call.start(object : io.grpc.ClientCall.Listener<GetHistoryResponseProto>() {
            override fun onMessage(message: GetHistoryResponseProto) {
                val historyMessages = message.messages
                    .filterNot { it.text.endsWith(" joined") || it.text.endsWith(" присоединился") }
                    .map { ProtoUtils.createMessageFromProto(it) }
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
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        loadHistory(currentRoomId, onComplete)
                    }, 3000)
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

    fun loadAllUsers(callback: (List<String>) -> Unit = {}) {
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
            _connectionState.value = false
            _isSuperAdmin.value = false
            sentMessageHashes.clear()
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
                    .setClientVersion(lavender.client.android.BuildConfig.VERSION_NAME)
                    .build())
            }
        }
    }
    
    private var lastUsername: String? = null
    private var lastJoinMessage: String? = null
    private var lastPassword: String? = null
    private var lastOnMessageReceived: ((Message) -> Unit)? = null

    fun startChat(username: String, password: String, joinMessage: String, onMessageReceived: (Message) -> Unit) {
        lastUsername = username
        lastPassword = password
        lastJoinMessage = joinMessage
        lastOnMessageReceived = onMessageReceived

        // Load history for the current room if set
        if (currentRoomId.isNotEmpty()) {
            loadHistory(currentRoomId)
        }

        // Load current user avatar
        getUserAvatar(username) { avatarUrl ->
            if (avatarUrl.isNotEmpty()) {
                avatarCache[username] = avatarUrl
            }
        }
        
        if (!_connectionState.value || channel == null || isChatStarted) return
        
        try {
            isChatStarted = true
            val responseObserver = object : StreamObserver<MessageProto> {
                override fun onNext(value: MessageProto) {
                    // Handle system notifications
                    if (value.user == "SYSTEM") {
                        when (value.text) {
                            "REGISTRATION_SUCCESS" -> {
                                _systemNotification.value = "registration_success"
                                return
                            }
                            "AUTH_FAILED" -> {
                                _systemNotification.value = "auth_failed"
                                return
                            }
                            "SET_SUPER_ADMIN" -> {
                                _isSuperAdmin.value = true
                                return
                            }
                        }
                        if (value.text.startsWith("SERVER_INFO:")) {
                            _serverVersion.value = value.text.substring("SERVER_INFO:".length)
                            return
                        }
                        if (value.text.startsWith("ONLINE_USERS_UPDATE:")) {
                            try {
                                val jsonStr = value.text.substring("ONLINE_USERS_UPDATE:".length)
                                val jsonArray = org.json.JSONArray(jsonStr)
                                val usersList = mutableListOf<String>()
                                for (i in 0 until jsonArray.length()) {
                                    usersList.add(jsonArray.getString(i))
                                }
                                _users.value = usersList
                            } catch (e: Exception) {
                                android.util.Log.e("GrpcClient", "Error parsing online users", e)
                            }
                            return
                        }
                        if (value.text.startsWith("FORCE_DISCONNECT:")) {
                            val target = value.text.substring("FORCE_DISCONNECT:".length)
                            if (target == lastUsername) {
                                disconnect()
                            }
                            return
                        }
                    }

                    if (value.text.endsWith(" joined") || value.text.endsWith(" присоединился")) return

                    val incoming = ProtoUtils.createMessageFromProto(value)

                    // Load avatar for incoming message if not cached and avatarUrl is empty
                    if (incoming.avatarUrl.isEmpty() && !avatarCache.containsKey(incoming.user)) {
                        getUserAvatar(incoming.user) { avatarUrl ->
                            if (avatarUrl.isNotEmpty()) {
                                avatarCache[incoming.user] = avatarUrl
                                // Update message with avatar URL, preserving imageUrl
                                _messages.update { currentList ->
                                    currentList.map { if (it.id == incoming.id && it.id.isNotEmpty()) it.copy(avatarUrl = avatarUrl, imageUrl = it.imageUrl) else it }
                                }
                            }
                        }
                    } else if (incoming.avatarUrl.isEmpty() && avatarCache.containsKey(incoming.user)) {
                        // Use cached avatar URL
                        avatarCache[incoming.user]?.let { cachedUrl ->
                            val incomingWithAvatar = incoming.copy(avatarUrl = cachedUrl, imageUrl = incoming.imageUrl)
                            _messages.update { currentList ->
                                val existingIndex = currentList.indexOfFirst {
                                    (it.id == incomingWithAvatar.id && it.id.isNotEmpty()) ||
                                    (it.user == incomingWithAvatar.user && it.text == incomingWithAvatar.text && it.imageUrl == incomingWithAvatar.imageUrl && Math.abs(it.timestamp - incomingWithAvatar.timestamp) < 5000)
                                }

                                if (existingIndex != -1) {
                                    val updatedList = currentList.toMutableList()
                                    // Handle message updates (like edited text)
                                    val existingMessage = updatedList[existingIndex]
                                    if (existingMessage.id == incomingWithAvatar.id && incomingWithAvatar.id.isNotEmpty()) {
                                        updatedList[existingIndex] = incomingWithAvatar
                                    }
                                    updatedList
                                } else {
                                    onMessageReceived(incomingWithAvatar)
                                    currentList + incomingWithAvatar
                                }
                            }
                            return@onNext
                        }
                    }

                    _messages.update { currentList ->
                        // Ищем, нет ли уже такого сообщения в списке (по тексту и пользователю, если ID еще нет)
                        val existingIndex = currentList.indexOfFirst { 
                            (it.id == incoming.id && it.id.isNotEmpty()) || 
                            (it.user == incoming.user && it.text == incoming.text && it.imageUrl == incoming.imageUrl && Math.abs(it.timestamp - incoming.timestamp) < 5000)
                        }

                        if (existingIndex != -1) {
                            // Если нашли, обновляем его (теперь у него есть ID от сервера)
                            val updatedList = currentList.toMutableList()
                            updatedList[existingIndex] = incoming
                            updatedList
                        } else {
                            // Если не нашли (чужое сообщение), добавляем в конец
                            onMessageReceived(incoming)
                            currentList + incoming
                        }
                    }
                }
                
                override fun onError(t: Throwable) {
                    android.util.Log.e("RealGrpcClient", "Chat stream error: ${t.message}", t)
                    isChatStarted = false
                    _connectionState.value = false
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (lastUsername != null && lastPassword != null && lastJoinMessage != null && lastOnMessageReceived != null) {
                            android.util.Log.d("GrpcClient", "Attempting automatic reconnection...")
                            startChat(lastUsername!!, lastPassword!!, lastJoinMessage!!, lastOnMessageReceived!!)
                        }
                    }, 3000)
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
                override fun onHeaders(headers: io.grpc.Metadata) { call.request(100) }
                override fun onMessage(message: MessageProto) { responseObserver.onNext(message) }
                override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                    if (!status.isOk) {
                        _connectionState.value = false
                        responseObserver.onError(status.asRuntimeException())
                    } else responseObserver.onCompleted()
                }
            }, io.grpc.Metadata())
            
            requestObserver = object : StreamObserver<MessageProto> {
                override fun onNext(value: MessageProto) {
                    call.sendMessage(value)
                    call.request(1)
                }
                override fun onError(t: Throwable) { call.cancel("Error", t) }
                override fun onCompleted() { call.halfClose() }
            }
            
            requestObserver?.onNext(MessageProto.newBuilder()
                .setUser(username)
                .setText(joinMessage)
                .setPassword(password)
                .setRoomId(currentRoomId)
                .setCreatedAt(ProtoUtils.getCurrentTimestamp())
                .setClientVersion(lavender.client.android.BuildConfig.VERSION_NAME)
                .build())
            
            startTypingStream(username)

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                loadHistory(currentRoomId) { loadUsers() }
            }, 300)
            
        } catch (_: Exception) {
            isChatStarted = false
        }
    }
    
    fun sendMessage(message: Message) {
        if (requestObserver == null) return
        try {
            // Use the message's roomId if it's set, otherwise use currentRoomId
            val messageWithRoom = if (message.roomId.isNotEmpty()) message else message.copy(roomId = currentRoomId)

            // Get avatar URL for current user if not cached
            if (!avatarCache.containsKey(message.user) && message.avatarUrl.isEmpty()) {
                getUserAvatar(message.user) { avatarUrl ->
                    if (avatarUrl.isNotEmpty()) {
                        avatarCache[message.user] = avatarUrl
                        // Update message with avatar URL
                        val messageWithAvatar = messageWithRoom.copy(avatarUrl = avatarUrl)
                        _messages.update { currentList ->
                            currentList.map { if (getMessageHash(it) == getMessageHash(messageWithRoom)) messageWithAvatar else it }
                        }
                    }
                }
            } else if (avatarCache.containsKey(message.user)) {
                // Use cached avatar URL
                val messageWithAvatar = messageWithRoom.copy(avatarUrl = avatarCache[message.user] ?: "", imageUrl = messageWithRoom.imageUrl)
                _messages.update { currentList -> currentList + messageWithAvatar }
                val protoMessage = ProtoUtils.createMessageProto(messageWithAvatar)
                sentMessageHashes.add(getMessageHash(messageWithAvatar))
                requestObserver?.onNext(protoMessage)
                return@sendMessage
            }

            _messages.update { currentList -> currentList + messageWithRoom }
            val protoMessage = ProtoUtils.createMessageProto(messageWithRoom)
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

    fun getChats(username: String, callback: (List<lavender.client.android.data.models.ChatInfo>) -> Unit) {
        val currentChannel = channel
        if (currentChannel == null) {
            callback(emptyList())
            return
        }

        val request = GetChatsRequestProto(username)

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
                    lavender.client.android.data.models.ChatInfo(
                        id = proto.id,
                        name = proto.name,
                        type = proto.type,
                        participants = proto.participants,
                        createdAt = proto.createdAt?.let { it.seconds * 1000 + (it.nanos / 1000000) } ?: 0,
                        unreadCount = proto.unreadCount,
                        lastMessageTime = proto.lastMessageTime?.let { it.seconds * 1000 + (it.nanos / 1000000) } ?: 0,
                        creator = proto.creator
                    )
                }
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
                    android.util.Log.d("GrpcClient", "Successfully marked room $roomId as read for $username")
                    // Instead of reloading history, we can locally update all unread messages
                    // belonging to others (incoming messages) to read
                    _messages.update { currentList ->
                        var hasChanges = false
                        val updatedList = currentList.map { msg ->
                            // Update read status for incoming messages in this room that aren't marked read yet
                            // Note: we can't tell which exact messages are marked read without changing proto,
                            // but markRead endpoint marks ALL unread messages as read for this room
                            if (msg.roomId == roomId && msg.user != username && !msg.isRead) {
                                hasChanges = true
                                msg.copy(isRead = true)
                            } else {
                                msg
                            }
                        }
                        if (hasChanges) updatedList else currentList
                    }
                    onCompletion?.invoke()
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
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun updateAvatar(username: String, avatarUrl: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel ?: return

        val request = UpdateAvatarRequestProto(username, avatarUrl)

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
                    updateAvatarCache(username, avatarUrl)
                }
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    callback(false, status.description ?: "Unknown error")
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
                callback(message)
            }

            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    callback(null)
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun addParticipant(chatId: String, username: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel
        if (currentChannel == null) {
            callback(false, "Channel is null")
            return
        }
        val request = AddParticipantRequestProto(chatId, username)
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<AddParticipantRequestProto, AddParticipantResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/AddParticipant")
            .setRequestMarshaller(AddParticipantRequestMarshaller())
            .setResponseMarshaller(AddParticipantResponseMarshaller())
            .build()
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<AddParticipantResponseProto>() {
            override fun onMessage(message: AddParticipantResponseProto) { callback(message.success, message.message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) callback(false, status.description ?: "Unknown error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun addParticipants(chatId: String, usernames: List<String>, callback: (Boolean, String) -> Unit) {
        if (usernames.isEmpty()) {
            callback(true, "No users to add")
            return
        }

        var successCount = 0
        var processedCount = 0
        var lastError = ""

        usernames.forEach { user ->
            addParticipant(chatId, user) { success, msg ->
                synchronized(this) {
                    processedCount++
                    if (success) successCount++ else lastError = msg
                    
                    if (processedCount == usernames.size) {
                        callback(successCount > 0, if (successCount == usernames.size) "Added all users" else "Added $successCount users. Last error: $lastError")
                    }
                }
            }
        }
    }

    fun removeParticipant(chatId: String, username: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel
        if (currentChannel == null) {
            callback(false, "Channel is null")
            return
        }

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
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getUserAvatar(username: String, callback: (String) -> Unit) {
        val currentChannel = channel
        if (currentChannel == null) {
            callback("")
            return
        }

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
                callback(message.avatarUrl)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    callback("")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun deleteChat(chatId: String, callback: (Boolean, String) -> Unit) {
        val currentChannel = channel
        android.util.Log.d("RealGrpcClient", "deleteChat called for roomId: $chatId")
        if (currentChannel == null) {
            android.util.Log.e("RealGrpcClient", "deleteChat failed: channel is null")
            callback(false, "Channel is null")
            return
        }

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
                android.util.Log.d("RealGrpcClient", "deleteChat response: success=${message.success}")
                callback(message.success, message.message)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("RealGrpcClient", "deleteChat error: ${status.code} - ${status.description}")
                    callback(false, status.description ?: "Unknown error")
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun startTypingStream(username: String) {
        if (!_connectionState.value || channel == null || typingRequestObserver != null) return

        try {
            val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<TypingRequestProto, TypingSignalProto>()
                .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
                .setFullMethodName("messenger.ChatService/Typing")
                .setRequestMarshaller(TypingRequestMarshaller())
                .setResponseMarshaller(TypingSignalMarshaller())
                .build()

            val call = channel!!.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
            call.start(object : io.grpc.ClientCall.Listener<TypingSignalProto>() {
                override fun onHeaders(headers: io.grpc.Metadata) { call.request(100) }
                override fun onMessage(message: TypingSignalProto) {
                    _typingUsers.update { currentMap ->
                        val roomTypists = currentMap[message.roomId]?.toMutableSet() ?: mutableSetOf()
                        if (message.isTyping) {
                            if (message.username != username) { // Don't show ourselves
                                roomTypists.add(message.username)
                            }
                        } else {
                            roomTypists.remove(message.username)
                        }
                        currentMap + (message.roomId to roomTypists)
                    }
                }
                override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                    typingRequestObserver = null
                }
            }, io.grpc.Metadata())

            typingRequestObserver = object : StreamObserver<TypingRequestProto> {
                override fun onNext(value: TypingRequestProto) {
                    call.sendMessage(value)
                    call.request(1)
                }
                override fun onError(t: Throwable) { call.cancel("Error", t) }
                override fun onCompleted() { call.halfClose() }
            }
        } catch (_: Exception) {}
    }

    fun sendTypingSignal(username: String, isTyping: Boolean) {
        typingRequestObserver?.onNext(TypingRequestProto(currentRoomId, username, isTyping))
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
                    callback(emptyList())
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getChatListVersion(username: String, callback: (Long) -> Unit) {
        val currentChannel = channel
        if (currentChannel == null) {
            callback(0L)
            return
        }

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
                    callback(0L)
                }
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getThemes(username: String, callback: (String, List<CustomThemeProto>) -> Unit) {
        val currentChannel = channel ?: return
        val request = GetThemesRequestProto(username)
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
                if (!status.isOk) callback("dark", emptyList())
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
                if (!status.isOk) callback(false, status.description ?: "Unknown error")
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
                if (!status.isOk) callback(false)
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
                if (!status.isOk) callback(false)
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
                if (!status.isOk) callback(false, status.description ?: "Unknown error")
            }
        }, io.grpc.Metadata())
        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }

    fun getAllChats(callback: (List<lavender.client.android.data.models.ChatInfo>) -> Unit) {
        val currentChannel = channel
        if (currentChannel == null) {
            callback(emptyList())
            return
        }

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
                val chats = message.chats.map { proto ->
                    lavender.client.android.data.models.ChatInfo(
                        id = proto.id,
                        name = proto.name,
                        type = proto.type,
                        participants = proto.participants,
                        createdAt = proto.createdAt?.let { it.seconds * 1000 + (it.nanos / 1000000) } ?: 0,
                        unreadCount = proto.unreadCount,
                        lastMessageTime = proto.lastMessageTime?.let { it.seconds * 1000 + (it.nanos / 1000000) } ?: 0,
                        creator = proto.creator
                    )
                }
                callback(chats)
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    android.util.Log.e("RealGrpcClient", "GetAllChats failed: ${status.code} - ${status.description}")
                    callback(emptyList())
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

    fun updateAvatarCache(username: String, avatarUrl: String) {
        android.util.Log.d("GrpcClient", "Updating avatar cache for $username: $avatarUrl")
        avatarCache[username] = avatarUrl
    }

    fun clearMessages() {
        _messages.update { emptyList() }
    }

    fun getCurrentUsername(): String? = lastUsername
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

class MessageProtoMarshaller : io.grpc.MethodDescriptor.Marshaller<MessageProto> {
    private val reactionMarshaller = ReactionProtoMarshaller()
    override fun stream(value: MessageProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.id.isNotEmpty()) cos.writeString(1, value.id)
        if (value.user.isNotEmpty()) cos.writeString(2, value.user)
        if (value.text.isNotEmpty()) cos.writeString(3, value.text)
        if (value.createdAt != null) {
            cos.writeTag(4, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(value.createdAt.serializedSize)
            value.createdAt.writeTo(cos)
        }
        for (reaction in value.reactions) {
            val msgBytes = reactionMarshaller.stream(reaction).readBytes()
            cos.writeTag(5, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(msgBytes.size)
            cos.writeRawBytes(msgBytes)
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
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): MessageProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var id = ""
        var user = ""
        var text = ""
        var createdAt: com.google.protobuf.Timestamp? = null
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
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> id = cis.readString()
                2 -> user = cis.readString()
                3 -> text = cis.readString()
                4 -> {
                    val length = cis.readUInt32()
                    val oldLimit = cis.pushLimit(length)
                    createdAt = com.google.protobuf.Timestamp.parseFrom(cis)
                    cis.popLimit(oldLimit)
                }
                5 -> {
                    val length = cis.readUInt32()
                    reactions.add(reactionMarshaller.parse(java.io.ByteArrayInputStream(cis.readRawBytes(length))))
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
                else -> cis.skipField(tag)
            }
        }
        return MessageProto(id, user, text, createdAt, reactions, password, repliedToMessageId, repliedToUser, repliedToText, roomId, isRead, avatarUrl, imageUrl, edited, clientVersion, isSuperAdmin)
    }
}

class ReactionRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<ReactionRequestProto> {
    private val reactionMarshaller = ReactionProtoMarshaller()
    override fun stream(value: ReactionRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.messageId.isNotEmpty()) cos.writeString(1, value.messageId)
        val reactionBytes = reactionMarshaller.stream(value.reaction).readBytes()
        cos.writeTag(2, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        cos.writeUInt32NoTag(reactionBytes.size)
        cos.writeRawBytes(reactionBytes)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): ReactionRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var messageId = ""
        var reaction = ReactionProto("", "")
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> messageId = cis.readString()
                2 -> {
                    val length = cis.readUInt32()
                    reaction = reactionMarshaller.parse(java.io.ByteArrayInputStream(cis.readRawBytes(length)))
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
        var limit = 0
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
    private val messageMarshaller = MessageProtoMarshaller()
    override fun stream(value: GetHistoryResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        for (msg in value.messages) {
            val msgBytes = messageMarshaller.stream(msg).readBytes()
            cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(msgBytes.size)
            cos.writeRawBytes(msgBytes)
        }
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): GetHistoryResponseProto {
        return try {
            val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
            val messages = mutableListOf<MessageProto>()
            while (!cis.isAtEnd) {
                val tag = cis.readTag()
                if (tag == 0) break
                if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                    val length = cis.readUInt32()
                    messages.add(messageMarshaller.parse(java.io.ByteArrayInputStream(cis.readRawBytes(length))))
                } else cis.skipField(tag)
            }
            GetHistoryResponseProto(messages)
        } catch (_: Exception) { GetHistoryResponseProto(emptyList()) }
    }
}

class EmptyMarshaller : io.grpc.MethodDescriptor.Marshaller<Unit> {
    override fun stream(value: Unit): java.io.InputStream = java.io.ByteArrayInputStream(ByteArray(0))
    override fun parse(stream: java.io.InputStream) = Unit
}

class ClientListResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<List<String>> {
    override fun stream(value: List<String>): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        for (client in value) cos.writeString(1, client)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): List<String> {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        val clients = mutableListOf<String>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) clients.add(cis.readString())
            else cis.skipField(tag)
        }
        return clients
    }
}

class DeleteMessagesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteMessagesRequestProto> {
    private val messageMarshaller = MessageProtoMarshaller()
    override fun stream(value: DeleteMessagesRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        for (msg in value.messages) {
            val msgBytes = messageMarshaller.stream(msg).readBytes()
            cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
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
                    messages.add(messageMarshaller.parse(java.io.ByteArrayInputStream(cis.readRawBytes(length))))
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
        if (value.message.isNotEmpty()) cos.writeString(2, value.message)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): EditMessageResponseProto {
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
        return EditMessageResponseProto(success, message)
    }
}

class TokenRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<TokenRequestProto> {
    override fun stream(value: TokenRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(1, value.user)
        cos.writeString(2, value.token)
        cos.writeBool(3, value.pushEnabled)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): TokenRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var user = ""
        var token = ""
        var pushEnabled = true
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

class ChatInfoMarshaller : io.grpc.MethodDescriptor.Marshaller<ChatInfoProto> {
    override fun stream(value: ChatInfoProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.id.isNotEmpty()) cos.writeString(1, value.id)
        if (value.name.isNotEmpty()) cos.writeString(2, value.name)
        if (value.type.isNotEmpty()) cos.writeString(3, value.type)
        if (value.participants.isNotEmpty()) cos.writeString(4, value.participants)
        value.createdAt?.let {
            val length = it.serializedSize
            cos.writeTag(5, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(length)
            cos.writeRawBytes(it.toByteArray())
        }
        if (value.unreadCount != 0) cos.writeInt32(6, value.unreadCount)
        if (value.lastMessageTime != null) {
            val length = value.lastMessageTime.serializedSize
            cos.writeTag(7, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(length)
            cos.writeRawBytes(value.lastMessageTime.toByteArray())
        }
        if (value.creator.isNotEmpty()) cos.writeString(8, value.creator)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): ChatInfoProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var id = ""
        var name = ""
        var type = ""
        var participants = ""
        var createdAt: com.google.protobuf.Timestamp? = null
        var unreadCount = 0
        var lastMessageTime: com.google.protobuf.Timestamp? = null
        var creator = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> id = cis.readString()
                2 -> name = cis.readString()
                3 -> type = cis.readString()
                4 -> participants = cis.readString()
                5 -> {
                    val length = cis.readUInt32()
                    val oldLimit = cis.pushLimit(length)
                    createdAt = com.google.protobuf.Timestamp.parseFrom(cis)
                    cis.popLimit(oldLimit)
                }
                6 -> unreadCount = cis.readInt32()
                7 -> {
                    val length = cis.readUInt32()
                    val oldLimit = cis.pushLimit(length)
                    lastMessageTime = com.google.protobuf.Timestamp.parseFrom(cis)
                    cis.popLimit(oldLimit)
                }
                8 -> creator = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return ChatInfoProto(id, name, type, participants, createdAt, unreadCount, lastMessageTime, creator)
    }
}

class GetChatsRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetChatsRequestProto> {
    override fun stream(value: GetChatsRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): GetChatsRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) username = cis.readString()
            else cis.skipField(tag)
        }
        return GetChatsRequestProto(username)
    }
}

class GetChatsResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetChatsResponseProto> {
    override fun stream(value: GetChatsResponseProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        for (chat in value.chats) {
            val chatBytes = ChatInfoMarshaller().stream(chat).readBytes()
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
                2 -> success = Map.Entry::class.java.getMethod("getValue").invoke(null) as? Boolean ?: cis.readBool() // Dummy fix for weird error if any, but actually cis.readBool() is fine
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

class UpdateAvatarRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<UpdateAvatarRequestProto> {
    override fun stream(value: UpdateAvatarRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.username.isNotEmpty()) cos.writeString(1, value.username)
        if (value.avatarUrl.isNotEmpty()) cos.writeString(2, value.avatarUrl)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): UpdateAvatarRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        var avatarUrl = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> username = cis.readString()
                2 -> avatarUrl = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return UpdateAvatarRequestProto(username, avatarUrl)
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
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> username = cis.readString()
                else -> cis.skipField(tag)
            }
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
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): GetUserAvatarResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var avatarUrl = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) avatarUrl = cis.readString()
            else cis.skipField(tag)
        }
        return GetUserAvatarResponseProto(avatarUrl)
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
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> username = cis.readString()
                else -> cis.skipField(tag)
            }
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
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): GetThemesRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var username = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) username = cis.readString()
            else cis.skipField(tag)
        }
        return GetThemesRequestProto(username)
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
    if (theme.isDark) cos.writeBool(10, theme.isDark)
    if (theme.backgroundImageUrl.isNotEmpty()) cos.writeString(11, theme.backgroundImageUrl)
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
    var isDark = false
    var backgroundImageUrl = ""
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
            10 -> isDark = cis.readBool()
            11 -> backgroundImageUrl = cis.readString()
            else -> cis.skipField(tag)
        }
    }
    return CustomThemeProto(id, name, primaryColor, onPrimaryColor, surfaceColor, onSurfaceColor, backgroundColor, textPrimaryColor, textSecondaryColor, isDark, backgroundImageUrl)
}
