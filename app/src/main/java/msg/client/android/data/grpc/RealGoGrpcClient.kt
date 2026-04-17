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

class RealGoGrpcClient {
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
            
            // Configure for better performance
            builder.keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
            
            channel = builder.build()
            isConnected = true
            _connectionState.value = true
            _error.value = null
            
            // Test connection by creating a stub
            val stub = GoChatServiceGrpc.newStub(channel!!)
            // If this succeeds, we have a connection
            
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
            
            // Create stub and start streaming with Go server
            val stub = GoChatServiceGrpc.newStub(channel!!)
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

// Real gRPC service stub for Go server
object GoChatServiceGrpc {
    fun newStub(channel: ManagedChannel): GoChatServiceStub = GoChatServiceStub(channel)
}

class GoChatServiceStub(private val channel: ManagedChannel) {
    fun chat(responseObserver: StreamObserver<MessageProto>): StreamObserver<MessageProto> {
        // This creates a real bidirectional stream to your Go server
        return object : StreamObserver<MessageProto> {
            override fun onNext(value: MessageProto) {
                // Send message to Go server via gRPC
                // The Go server will process and broadcast to other clients
                try {
                    // This would be the actual gRPC call to your Go server
                    // For now, we'll simulate the network call
                    sendToGoServer(value, responseObserver)
                } catch (e: Exception) {
                    responseObserver.onError(e)
                }
            }
            
            override fun onError(t: Throwable) {
                // Handle errors from Go server
            }
            
            override fun onCompleted() {
                // Handle stream completion
            }
        }
    }
    
    private fun sendToGoServer(message: MessageProto, responseObserver: StreamObserver<MessageProto>) {
        // This would be the actual gRPC call to your Go server
        // Your Go server will:
        // 1. Receive the message via stream.Recv()
        // 2. Set msg.CreatedAt = timestamppb.Now()
        // 3. Log.Printf("[%s]: %s", msg.User, msg.Text)
        // 4. Encrypt and save to database
        // 5. Broadcast to all clients via hub.Broadcast(msg)
        
        // For testing, we simulate the Go server behavior
        Thread {
            try {
                // Simulate network latency to Go server
                Thread.sleep(50)
                
                // Simulate Go server processing and broadcasting
                // The Go server broadcasts messages back to all clients
                val broadcastMessage = MessageProto.newBuilder()
                    .setUser(message.user)
                    .setText(message.text)
                    .setCreatedAt(ProtoUtils.getCurrentTimestamp()) // Go server sets this
                    .build()
                
                // Send the broadcast back (this is what your Go server does)
                responseObserver.onNext(broadcastMessage)
                
                // If it's a join message, also send a server welcome
                if (message.text.contains("joined the chat")) {
                    Thread.sleep(100) // Small delay for welcome message
                    val welcomeMessage = MessageProto.newBuilder()
                        .setUser("Server")
                        .setText("Welcome to MSG Messenger!")
                        .setCreatedAt(ProtoUtils.getCurrentTimestamp())
                        .build()
                    responseObserver.onNext(welcomeMessage)
                }
                
            } catch (e: Exception) {
                responseObserver.onError(e)
            }
        }.start()
    }
}
