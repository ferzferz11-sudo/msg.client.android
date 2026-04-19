package msg.client.android.data.grpc

import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import msg.client.android.data.models.Message
import msg.client.android.data.proto.MessageProto
import msg.client.android.data.proto.ProtoUtils
import msg.client.android.data.proto.GetHistoryRequestProto
import msg.client.android.data.proto.GetHistoryResponseProto
import java.util.concurrent.TimeUnit

class RealGrpcClient {
    private var channel: ManagedChannel? = null
    private var requestObserver: StreamObserver<MessageProto>? = null
    private var currentServerAddress: String? = null
    
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _users = MutableStateFlow<List<String>>(emptyList())
    val users: StateFlow<List<String>> = _users

    private var isChatStarted = false
    private val sentMessageHashes = mutableSetOf<String>() // Track sent messages to prevent echo
    
    fun connect(serverAddress: String, useTls: Boolean = false, port: Int = 50051) {
        if (_connectionState.value && currentServerAddress == serverAddress) {
            println("DEBUG: RealGrpcClient - Already connected to $serverAddress:$port")
            return
        }

        try {
            println("DEBUG: RealGrpcClient - Connecting to Go server at $serverAddress:$port")
            disconnect()
            
            val builder = OkHttpChannelBuilder.forAddress(serverAddress, port)
            if (useTls) builder.useTransportSecurity() else builder.usePlaintext()
            
            builder.keepAliveTime(60, TimeUnit.SECONDS)
                .keepAliveTimeout(20, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .idleTimeout(24, TimeUnit.HOURS)
            
            channel = builder.build()
            currentServerAddress = serverAddress
            _connectionState.value = true
            _error.value = null
            
        } catch (e: Exception) {
            println("DEBUG: RealGrpcClient - Connection failed: ${e.message}")
            _error.value = "Connection failed: ${e.message}"
            _connectionState.value = false
        }
    }

    private fun loadHistory() {
        val currentChannel = channel ?: return
        
        println("DEBUG: RealGrpcClient - Loading history...")
        
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetHistoryRequestProto, GetHistoryResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetHistory")
            .setRequestMarshaller(GetHistoryRequestMarshaller())
            .setResponseMarshaller(GetHistoryResponseMarshaller())
            .build()
            
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        
        // Загружаем историю для общей комнаты (пустая строка или "general")
        val request = GetHistoryRequestProto(limit = 100, room = "")
        
        call.start(object : io.grpc.ClientCall.Listener<GetHistoryResponseProto>() {
            override fun onMessage(message: GetHistoryResponseProto) {
                println("DEBUG: RealGrpcClient - Received history: ${message.messages.size} messages")
                if (message.messages.isEmpty()) {
                    println("DEBUG: RealGrpcClient - History is empty from server")
                }
                val historyMessages = message.messages
                    .filterNot { it.text.endsWith(" joined") || it.text.endsWith(" присоединился") }
                    .map { ProtoUtils.createMessageFromProto(it) }
                _messages.update { currentList ->
                    val combined = (historyMessages + currentList).distinctBy { 
                        // Используем более надежный ключ для дедупликации (с точностью до секунды)
                        "${it.user}:${it.text}:${it.timestamp / 1000}" 
                    }.sortedBy { it.timestamp }
                    println("DEBUG: RealGrpcClient - Total messages after merge: ${combined.size}")
                    combined
                }
            }
            
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    println("DEBUG: RealGrpcClient - History error: ${status.code} - ${status.description}")
                    _error.value = "History load failed: ${status.code}"
                }
            }
        }, io.grpc.Metadata())
        
        call.sendMessage(request)
        call.halfClose()
        call.request(1)
    }
    
    fun loadUsers() {
        val currentChannel = channel ?: return

        println("DEBUG: RealGrpcClient - Loading users...")

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<Unit, List<String>>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetClients")
            .setRequestMarshaller(EmptyMarshaller())
            .setResponseMarshaller(ClientListResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)

        call.start(object : io.grpc.ClientCall.Listener<List<String>>() {
            override fun onMessage(message: List<String>) {
                println("DEBUG: RealGrpcClient - Received users: ${message.size}")
                _users.value = message
            }

            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk) {
                    println("DEBUG: RealGrpcClient - Load users error: ${status.code} - ${status.description}")
                    // Don't set global error - users list is optional, server may not support GetClients
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
            channel?.shutdownNow()
            channel = null
            currentServerAddress = null
            _connectionState.value = false
            sentMessageHashes.clear()
            println("DEBUG: RealGrpcClient - Disconnected and cleaned up")
        } catch (e: Exception) {
            println("DEBUG: RealGrpcClient - Disconnect error: ${e.message}")
        }
    }
    
    private var lastUsername: String? = null
    private var lastJoinMessage: String? = null
    private var lastOnMessageReceived: ((Message) -> Unit)? = null

    fun startChat(username: String, joinMessage: String, onMessageReceived: (Message) -> Unit) {
        lastUsername = username
        lastJoinMessage = joinMessage
        lastOnMessageReceived = onMessageReceived
        
        if (!_connectionState.value || channel == null) {
            _error.value = "Not connected"
            return
        }
        
        if (isChatStarted) {
            println("DEBUG: RealGrpcClient - Chat stream already active, ignoring start request")
            return
        }
        
        try {
            isChatStarted = true
            
            val responseObserver = object : StreamObserver<MessageProto> {
                override fun onNext(value: MessageProto) {
                    // Пропускаем системные сообщения о присоединении
                    if (value.text.endsWith(" joined") || value.text.endsWith(" присоединился")) {
                        println("DEBUG: RealGrpcClient - Skipping system message: ${value.text}")
                        return
                    }

                    val incoming = ProtoUtils.createMessageFromProto(value)
                    val messageHash = "${value.user}:${value.text}:${value.createdAt?.seconds}:${value.createdAt?.nanos}"
                    
                    _messages.update { currentList ->
                        if (sentMessageHashes.contains(messageHash)) {
                            sentMessageHashes.remove(messageHash)
                            currentList
                        } else if (currentList.takeLast(5).any {
                            it.text == incoming.text && it.user == incoming.user &&
                            Math.abs(it.timestamp - incoming.timestamp) < 2000
                        }) {
                            currentList
                        } else {
                            onMessageReceived(incoming)
                            currentList + incoming
                        }
                    }
                }
                
                override fun onError(t: Throwable) {
                    println("DEBUG: RealGrpcClient - Chat stream error: ${t.message}. Attempting reconnect in 5s...")
                    isChatStarted = false
                    // Не ставим _connectionState.value = false сразу, попробуем переподключиться
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (lastUsername != null && lastJoinMessage != null && lastOnMessageReceived != null) {
                            println("DEBUG: RealGrpcClient - Retrying startChat...")
                            startChat(lastUsername!!, lastJoinMessage!!, lastOnMessageReceived!!)
                        }
                    }, 5000)
                }
                
                override fun onCompleted() {
                    isChatStarted = false
                }
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
                    println("DEBUG: RealGrpcClient - Chat stream closed. Status: ${status.code}, Description: ${status.description}, Cause: ${status.cause}")
                    if (!status.isOk) responseObserver.onError(status.asRuntimeException())
                    else responseObserver.onCompleted()
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
                .setCreatedAt(ProtoUtils.getCurrentTimestamp())
                .build())
            
            // Загружаем историю и пользователей после успешного старта чата
            loadHistory()
            loadUsers()
            
        } catch (e: Exception) {
            isChatStarted = false
            _error.value = "StartChat failed: ${e.message}"
        }
    }
    
    fun sendMessage(message: Message) {
        if (requestObserver == null) return
        try {
            _messages.update { currentList ->
                currentList + message
            }
            
            val protoMessage = ProtoUtils.createMessageProto(message)
            val messageHash = "${protoMessage.user}:${protoMessage.text}:${protoMessage.createdAt?.seconds}:${protoMessage.createdAt?.nanos}"
            sentMessageHashes.add(messageHash)
            
            requestObserver?.onNext(protoMessage)
        } catch (e: Exception) {
            println("DEBUG: RealGrpcClient - Send error: ${e.message}")
        }
    }
    
    fun deleteMessage(message: Message) {
        // Remove message from local list
        _messages.update { currentList ->
            currentList.filterNot { it.timestamp == message.timestamp && it.user == message.user && it.text == message.text }
        }
        
        // TODO: Send delete request to server when server supports it
        println("DEBUG: RealGrpcClient - Deleted message locally: ${message.user}: ${message.text}")
    }
    
    fun testConnection(): Boolean = _connectionState.value
}

class MessageProtoMarshaller : io.grpc.MethodDescriptor.Marshaller<MessageProto> {
    override fun stream(value: MessageProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.user.isNotEmpty()) cos.writeString(1, value.user)
        if (value.text.isNotEmpty()) cos.writeString(2, value.text)
        if (value.createdAt != null) cos.writeMessage(3, value.createdAt)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): MessageProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var user = ""
        var text = ""
        var createdAt: com.google.protobuf.Timestamp? = null
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> user = cis.readString()
                2 -> text = cis.readString()
                3 -> {
                    val builder = com.google.protobuf.Timestamp.newBuilder()
                    cis.readMessage(builder, com.google.protobuf.ExtensionRegistryLite.getEmptyRegistry())
                    createdAt = builder.build()
                }
                else -> cis.skipField(tag)
            }
        }
        return MessageProto(user, text, createdAt)
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
            cos.writeRawVarint32(msgBytes.size)
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
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> {
                    val length = cis.readRawVarint32()
                    val msgBytes = cis.readRawBytes(length)
                    messages.add(messageMarshaller.parse(java.io.ByteArrayInputStream(msgBytes)))
                }
                else -> cis.skipField(tag)
            }
        }
        return GetHistoryResponseProto(messages)
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
            // В ClientListResponse поле clients имеет номер 1
            if (com.google.protobuf.WireFormat.getTagFieldNumber(tag) == 1) {
                clients.add(cis.readString())
            } else {
                cis.skipField(tag)
            }
        }
        return clients
    }
}
