package msg.client.android.data.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import msg.client.android.data.models.Message
import msg.client.android.data.proto.MessageProto
import msg.client.android.data.proto.ProtoUtils
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

    private var isChatStarted = false
    
    fun connect(serverAddress: String, useTls: Boolean = false) {
        // Если уже подключены к этому же адресу, ничего не делаем
        if (_connectionState.value && currentServerAddress == serverAddress) {
            println("DEBUG: RealGrpcClient - Already connected to $serverAddress")
            return
        }

        try {
            println("DEBUG: RealGrpcClient - Connecting to Go server at $serverAddress:50051")
            
            // Закрываем старое соединение если есть
            disconnect()
            
            val builder = ManagedChannelBuilder.forAddress(serverAddress, 50051)
            if (useTls) builder.useTransportSecurity() else builder.usePlaintext()
            
            builder.keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
            
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
    
    fun disconnect() {
        try {
            isChatStarted = false
            requestObserver?.onCompleted()
            requestObserver = null
            channel?.shutdownNow() // Используем shutdownNow для быстрой очистки
            channel = null
            currentServerAddress = null
            _connectionState.value = false
            println("DEBUG: RealGrpcClient - Disconnected and cleaned up")
        } catch (e: Exception) {
            println("DEBUG: RealGrpcClient - Disconnect error: ${e.message}")
        }
    }
    
    fun startChat(username: String, onMessageReceived: (Message) -> Unit) {
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
                    val incoming = ProtoUtils.createMessageFromProto(value)
                    
                    // Атомарная проверка и добавление
                    _messages.update { currentList ->
                        // Игнорируем, если сообщение идентично любому из последних 3-х (защита от тройного эха)
                        val isDuplicate = currentList.takeLast(5).any { 
                            it.text == incoming.text && it.user == incoming.user && 
                            Math.abs(it.timestamp - incoming.timestamp) < 2000 // в пределах 2 секунд
                        }
                        
                        if (isDuplicate) {
                            println("DEBUG: RealGrpcClient - Ignored duplicate: ${incoming.text}")
                            currentList
                        } else {
                            println("DEBUG: RealGrpcClient - New message: ${incoming.user}: ${incoming.text}")
                            onMessageReceived(incoming)
                            currentList + incoming
                        }
                    }
                }
                
                override fun onError(t: Throwable) {
                    println("DEBUG: RealGrpcClient - Stream error: ${t.message}")
                    isChatStarted = false
                    _connectionState.value = false
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
                    if (!status.isOk) responseObserver.onError(RuntimeException(status.description))
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
            
            // Приветственное сообщение
            requestObserver?.onNext(MessageProto.newBuilder()
                .setUser(username)
                .setText("$username joined the chat")
                .setCreatedAt(ProtoUtils.getCurrentTimestamp())
                .build())
            
        } catch (e: Exception) {
            isChatStarted = false
            _error.value = "StartChat failed: ${e.message}"
        }
    }
    
    fun sendMessage(message: Message) {
        if (requestObserver == null) return
        try {
            requestObserver?.onNext(ProtoUtils.createMessageProto(message))
        } catch (e: Exception) {
            println("DEBUG: RealGrpcClient - Send error: ${e.message}")
        }
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
