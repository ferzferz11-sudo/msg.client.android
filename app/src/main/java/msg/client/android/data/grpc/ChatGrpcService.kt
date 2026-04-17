package msg.client.android.data.grpc

import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import msg.client.android.data.models.Message
import msg.client.android.data.proto.MessageProto
import msg.client.android.data.proto.ProtoUtils
import java.util.concurrent.TimeUnit

class ChatGrpcService {
    private var channel: ManagedChannel? = null
    private var requestObserver: StreamObserver<MessageProto>? = null
    private var isConnected = false
    
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    
    fun connect(serverAddress: String, useTls: Boolean = false) {
        try {
            val builder = ManagedChannelBuilder.forAddress(serverAddress, 50051)
            
            if (useTls) {
                builder.useTransportSecurity()
            } else {
                builder.usePlaintext()
            }
            
            channel = builder.build()
            isConnected = true
            _connectionState.value = true
            _error.value = null
        } catch (e: Exception) {
            _error.value = "Connection failed: ${e.message}"
            isConnected = false
            _connectionState.value = false
        }
    }
    
    fun disconnect() {
        try {
            requestObserver?.onCompleted()
            channel?.shutdown()?.awaitTermination(5, TimeUnit.SECONDS)
            channel = null
            requestObserver = null
            isConnected = false
            _connectionState.value = false
        } catch (e: Exception) {
            _error.value = "Disconnect error: ${e.message}"
        }
    }
    
    fun startChat(username: String, onMessageReceived: (Message) -> Unit) {
        if (!isConnected || channel == null) {
            _error.value = "Not connected to server"
            return
        }
        
        try {
            // Create response observer for incoming messages
            val responseObserver = object : StreamObserver<MessageProto> {
                override fun onNext(value: MessageProto) {
                    val message = ProtoUtils.createMessageFromProto(value)
                    _messages.value = _messages.value + message
                    onMessageReceived(message)
                }
                
                override fun onError(t: Throwable) {
                    _error.value = "Stream error: ${t.message}"
                    isConnected = false
                    _connectionState.value = false
                }
                
                override fun onCompleted() {
                    _error.value = "Stream completed"
                    isConnected = false
                    _connectionState.value = false
                }
            }
            
            // Create stub and start streaming
            val stub = ChatServiceGrpc.newStub(channel!!)
            requestObserver = stub.chat(responseObserver)
            
            // Send initial message to join chat
            val joinMessage = MessageProto.newBuilder()
                .setUser(username)
                .setText("$username joined the chat")
                .setCreatedAt(ProtoUtils.getCurrentTimestamp())
                .build()
            
            requestObserver?.onNext(joinMessage)
            
        } catch (e: Exception) {
            _error.value = "Failed to start chat: ${e.message}"
        }
    }
    
    fun sendMessage(message: Message) {
        if (!isConnected || requestObserver == null) {
            _error.value = "Not connected to server"
            return
        }
        
        try {
            val protoMessage = ProtoUtils.createMessageProto(message)
            requestObserver?.onNext(protoMessage)
        } catch (e: Exception) {
            _error.value = "Failed to send message: ${e.message}"
        }
    }
    
    fun testConnection(): Boolean {
        return isConnected && channel != null
    }
}

// Simplified gRPC service stub
object ChatServiceGrpc {
    fun newStub(channel: ManagedChannel): ChatServiceStub = ChatServiceStub(channel)
}

class ChatServiceStub(private val channel: ManagedChannel) {
    fun chat(responseObserver: StreamObserver<MessageProto>): StreamObserver<MessageProto> {
        // Simplified implementation - simulate bidirectional streaming
        return object : StreamObserver<MessageProto> {
            override fun onNext(value: MessageProto) {
                // In real implementation, this would send to server
                // For now, simulate server echo
                try {
                    // Simulate server processing delay
                    Thread.sleep(100)
                    
                    // Echo message back (simulating server response)
                    val echoMessage = MessageProto.newBuilder()
                        .setUser("Server")
                        .setText("Received: ${value.text}")
                        .setCreatedAt(value.createdAt ?: ProtoUtils.getCurrentTimestamp())
                        .build()
                    
                    responseObserver.onNext(echoMessage)
                } catch (e: InterruptedException) {
                    responseObserver.onError(e)
                }
            }
            
            override fun onError(t: Throwable) {
                // Handle errors
            }
            
            override fun onCompleted() {
                // Handle completion
            }
        }
    }
}
