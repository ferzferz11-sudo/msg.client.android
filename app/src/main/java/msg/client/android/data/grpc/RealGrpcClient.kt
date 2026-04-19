package msg.client.android.data.grpc

import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import msg.client.android.data.models.Message
import msg.client.android.data.proto.MessageProto
import msg.client.android.data.proto.ReactionProto
import msg.client.android.data.proto.ReactionRequestProto
import msg.client.android.data.proto.ReactionResponseProto
import msg.client.android.data.proto.ProtoUtils
import msg.client.android.data.proto.GetHistoryRequestProto
import msg.client.android.data.proto.GetHistoryResponseProto
import msg.client.android.data.proto.DeleteMessagesRequestProto
import msg.client.android.data.proto.DeleteMessagesResponseProto
import msg.client.android.data.proto.TokenRequestProto
import msg.client.android.data.proto.TokenResponseProto
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
    private val deletedMessageHashes = mutableSetOf<String>()
    private var appContext: android.content.Context? = null
    
    fun connect(serverAddress: String, useTls: Boolean = false, port: Int = 50051, context: android.content.Context? = null) {
        if (context != null) {
            this.appContext = context
            loadDeletedMessages()
        }
        
        if (_connectionState.value && currentServerAddress == serverAddress) {
            println("DEBUG: RealGrpcClient - Already connected to $serverAddress:$port")
            return
        }

        try {
            println("DEBUG: RealGrpcClient - Connecting to Go server at $serverAddress:$port")
            disconnect()
            
            val builder = OkHttpChannelBuilder.forAddress(serverAddress, port)
            if (useTls) builder.useTransportSecurity() else builder.usePlaintext()
            
            builder.directExecutor()
            builder.maxInboundMessageSize(16 * 1024 * 1024)
            builder.maxInboundMetadataSize(1024 * 1024)
            
            builder.keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(false)
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
        return "${message.user}:${message.text}:${message.timestamp / 1000}"
    }

    private fun loadHistory(onComplete: () -> Unit = {}) {
        val currentChannel = channel ?: return
        
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<GetHistoryRequestProto, GetHistoryResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetHistory")
            .setRequestMarshaller(GetHistoryRequestMarshaller())
            .setResponseMarshaller(GetHistoryResponseMarshaller())
            .build()
            
        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        val request = GetHistoryRequestProto(limit = 100, room = "")
        
        call.start(object : io.grpc.ClientCall.Listener<GetHistoryResponseProto>() {
            override fun onMessage(message: GetHistoryResponseProto) {
                val historyMessages = message.messages
                    .filterNot { it.text.endsWith(" joined") || it.text.endsWith(" присоединился") }
                    .map { ProtoUtils.createMessageFromProto(it) }
                    .filterNot { deletedMessageHashes.contains(getMessageHash(it)) }

                _messages.update { currentList ->
                    (historyMessages + currentList).distinctBy { getMessageHash(it) }.sortedBy { it.timestamp }
                }
            }
            
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!status.isOk && (status.code == io.grpc.Status.Code.UNAVAILABLE || status.code == io.grpc.Status.Code.INTERNAL)) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        loadHistory(onComplete)
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
        val currentChannel = channel ?: return
        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<Unit, List<String>>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetClients")
            .setRequestMarshaller(EmptyMarshaller())
            .setResponseMarshaller(ClientListResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<List<String>>() {
            override fun onMessage(message: List<String>) { _users.value = message }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {}
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
        } catch (e: Exception) {}
    }
    
    private var lastUsername: String? = null
    private var lastJoinMessage: String? = null
    private var lastOnMessageReceived: ((Message) -> Unit)? = null

    fun startChat(username: String, joinMessage: String, onMessageReceived: (Message) -> Unit) {
        lastUsername = username
        lastJoinMessage = joinMessage
        lastOnMessageReceived = onMessageReceived
        
        if (!_connectionState.value || channel == null || isChatStarted) return
        
        try {
            isChatStarted = true
            val responseObserver = object : StreamObserver<MessageProto> {
                override fun onNext(value: MessageProto) {
                    if (value.text.endsWith(" joined") || value.text.endsWith(" присоединился")) return

                    val incoming = ProtoUtils.createMessageFromProto(value)
                    
                    _messages.update { currentList ->
                        // Ищем, нет ли уже такого сообщения в списке (по тексту и пользователю, если ID еще нет)
                        val existingIndex = currentList.indexOfFirst { 
                            (it.id == incoming.id && it.id.isNotEmpty()) || 
                            (it.user == incoming.user && it.text == incoming.text && Math.abs(it.timestamp - incoming.timestamp) < 5000)
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
                    isChatStarted = false
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (lastUsername != null && lastJoinMessage != null && lastOnMessageReceived != null) {
                            startChat(lastUsername!!, lastJoinMessage!!, lastOnMessageReceived!!)
                        }
                    }, 5000)
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
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                loadHistory { loadUsers() }
            }, 300)
            
        } catch (e: Exception) {
            isChatStarted = false
        }
    }
    
    fun sendMessage(message: Message) {
        if (requestObserver == null) return
        try {
            _messages.update { currentList -> currentList + message }
            val protoMessage = ProtoUtils.createMessageProto(message)
            sentMessageHashes.add(getMessageHash(message))
            requestObserver?.onNext(protoMessage)
        } catch (e: Exception) {}
    }
    
    fun deleteMessage(message: Message) {
        val hash = getMessageHash(message)
        deletedMessageHashes.add(hash)
        saveDeletedMessages()
        _messages.update { currentList -> currentList.filterNot { getMessageHash(it) == hash } }

        // Send delete request to server
        val proto = ProtoUtils.createMessageProto(message)
        val request = DeleteMessagesRequestProto(listOf(proto))
        
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

    fun setReaction(messageId: String, username: String, emoji: String) {
        val currentChannel = channel ?: return
        
        val reaction = msg.client.android.data.proto.ReactionProto(username, emoji)
        val request = msg.client.android.data.proto.ReactionRequestProto(messageId, reaction)

        val methodDescriptor = io.grpc.MethodDescriptor.newBuilder<msg.client.android.data.proto.ReactionRequestProto, msg.client.android.data.proto.ReactionResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/SetReaction")
            .setRequestMarshaller(ReactionRequestMarshaller())
            .setResponseMarshaller(ReactionResponseMarshaller())
            .build()

        val call = currentChannel.newCall(methodDescriptor, io.grpc.CallOptions.DEFAULT)
        call.start(object : io.grpc.ClientCall.Listener<msg.client.android.data.proto.ReactionResponseProto>() {
            override fun onMessage(message: msg.client.android.data.proto.ReactionResponseProto) {
                if (message.success) {
                    println("DEBUG: RealGrpcClient - Successfully set reaction")
                    _messages.update { currentList ->
                        currentList.map { m ->
                            if (m.id == messageId) {
                                val newReactions = m.reactions.filterNot { it.user == username } + msg.client.android.data.models.Reaction(username, emoji)
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

    fun registerToken(user: String, token: String) {
        val currentChannel = channel ?: return
        
        val request = TokenRequestProto(user, token)

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
    
    fun testConnection(): Boolean = _connectionState.value
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
                else -> cis.skipField(tag)
            }
        }
        return MessageProto(id, user, text, createdAt, reactions)
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
        } catch (e: Exception) { GetHistoryResponseProto(emptyList()) }
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
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): DeleteMessagesRequestProto {
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
        return DeleteMessagesRequestProto(messages)
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

class TokenRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<TokenRequestProto> {
    override fun stream(value: TokenRequestProto): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(1, value.user)
        cos.writeString(2, value.token)
        cos.flush()
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(stream: java.io.InputStream): TokenRequestProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var user = ""
        var token = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> user = cis.readString()
                2 -> token = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return TokenRequestProto(user, token)
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
