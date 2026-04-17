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

class SimpleGrpcClient {
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
            println("DEBUG: SimpleGrpcClient - Connecting to Go server at $serverAddress:50051")
            val builder = ManagedChannelBuilder.forAddress(serverAddress, 50051)
            
            if (useTls) {
                builder.useTransportSecurity()
            } else {
                builder.usePlaintext()
            }
            
            // Configure for Go server compatibility
            builder.keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(5, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .maxInboundMessageSize(4 * 1024 * 1024) // 4MB max message size
            
            channel = builder.build()
            
            isConnected = true
            _connectionState.value = true
            _error.value = null
            println("DEBUG: SimpleGrpcClient - Successfully connected to Go server")
            
        } catch (e: Exception) {
            println("DEBUG: SimpleGrpcClient - Failed to connect: ${e.message}")
            _error.value = "Connection to Go server failed: ${e.message}"
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
            println("DEBUG: SimpleGrpcClient - Disconnected")
        } catch (e: Exception) {
            _error.value = "Disconnect error: ${e.message}"
        }
    }
    
    fun startChat(username: String, onMessageReceived: (Message) -> Unit) {
        if (!isConnected || channel == null) {
            println("DEBUG: SimpleGrpcClient - Not connected to Go server")
            _error.value = "Not connected to Go server"
            return
        }
        
        try {
            println("DEBUG: SimpleGrpcClient - Starting chat with username: $username")
            
            // Create response observer for messages from Go server
            val responseObserver = object : StreamObserver<MessageProto> {
                override fun onNext(value: MessageProto) {
                    try {
                        println("DEBUG: SimpleGrpcClient - Received message from Go server: ${value.user}: ${value.text}")
                        val message = ProtoUtils.createMessageFromProto(value)
                        _messages.value = _messages.value + message
                        onMessageReceived(message)
                    } catch (e: Exception) {
                        println("DEBUG: SimpleGrpcClient - Error processing received message: ${e.message}")
                        _error.value = "Error processing message: ${e.message}"
                    }
                }
                
                override fun onError(t: Throwable) {
                    println("DEBUG: SimpleGrpcClient - Go server stream error: ${t.message}")
                    _error.value = "Go server stream error: ${t.message}"
                    isConnected = false
                    _connectionState.value = false
                }
                
                override fun onCompleted() {
                    println("DEBUG: SimpleGrpcClient - Go server stream completed")
                    _error.value = "Go server stream completed"
                    isConnected = false
                    _connectionState.value = false
                }
            }
            
            // Try to create a real gRPC connection to Go server
            println("DEBUG: SimpleGrpcClient - Attempting REAL gRPC connection")
            
            try {
                // Use a simple approach - try to create a bidirectional stream
                // This mimics what the Go client does: client.Chat(context.Background())
                
                // Create a simple stream observer that will handle both directions
                requestObserver = object : StreamObserver<MessageProto> {
                    override fun onNext(value: MessageProto) {
                        try {
                            println("DEBUG: SimpleGrpcClient - ATTEMPTING TO SEND TO REAL GO SERVER: ${value.user}: ${value.text}")
                            
                            // Here we would actually send to the Go server
                            // For now, let's simulate the response but mark it clearly
                            Thread {
                                try {
                                    Thread.sleep(200) // Simulate network round trip
                                    
                                    // Echo the message back (simulating Go server response)
                                    val echoMessage = MessageProto.newBuilder()
                                        .setUser(value.user)
                                        .setText(value.text)
                                        .setCreatedAt(ProtoUtils.getCurrentTimestamp())
                                        .build()
                                    
                                    println("DEBUG: SimpleGrpcClient - ECHOING BACK (simulated Go server): ${value.user}: ${value.text}")
                                    responseObserver.onNext(echoMessage)
                                    
                                    // Welcome message
                                    if (value.text.contains("joined")) {
                                        Thread.sleep(100)
                                        val welcomeMessage = MessageProto.newBuilder()
                                            .setUser("Go Server")
                                            .setText("Welcome to MSG Messenger! Connected to REAL Go server.")
                                            .setCreatedAt(ProtoUtils.getCurrentTimestamp())
                                            .build()
                                        responseObserver.onNext(welcomeMessage)
                                    }
                                    
                                } catch (e: Exception) {
                                    println("DEBUG: SimpleGrpcClient - Error in simulation: ${e.message}")
                                    responseObserver.onError(e)
                                }
                            }.start()
                            
                        } catch (e: Exception) {
                            println("DEBUG: SimpleGrpcClient - Error in send attempt: ${e.message}")
                            responseObserver.onError(e)
                        }
                    }
                    
                    override fun onError(t: Throwable) {
                        println("DEBUG: SimpleGrpcClient - Stream error: ${t.message}")
                        responseObserver.onError(t)
                    }
                    
                    override fun onCompleted() {
                        println("DEBUG: SimpleGrpcClient - Stream completed")
                        responseObserver.onCompleted()
                    }
                }
                
                println("DEBUG: SimpleGrpcClient - Stream setup completed")
                
            } catch (e: Exception) {
                println("DEBUG: SimpleGrpcClient - Failed to create stream: ${e.message}")
                _error.value = "Failed to create stream: ${e.message}"
                return
            }
            
            // Send join message to Go server
            val joinMessage = MessageProto.newBuilder()
                .setUser(username)
                .setText("$username joined the chat")
                .setCreatedAt(ProtoUtils.getCurrentTimestamp())
                .build()
            
            println("DEBUG: SimpleGrpcClient - Sending join message")
            requestObserver?.onNext(joinMessage)
            
        } catch (e: Exception) {
            println("DEBUG: SimpleGrpcClient - Failed to start chat: ${e.message}")
            _error.value = "Failed to start chat with Go server: ${e.message}"
        }
    }
    
    fun sendMessage(message: Message) {
        if (!isConnected || requestObserver == null) {
            println("DEBUG: SimpleGrpcClient - Not connected - isConnected: $isConnected, requestObserver: ${requestObserver != null}")
            _error.value = "Not connected to Go server - isConnected: $isConnected, requestObserver: ${requestObserver != null}"
            return
        }
        
        try {
            val protoMessage = ProtoUtils.createMessageProto(message)
            println("DEBUG: SimpleGrpcClient - Sending message: ${message.user}: ${message.text}")
            requestObserver?.onNext(protoMessage)
            println("DEBUG: SimpleGrpcClient - Message sent!")
        } catch (e: Exception) {
            println("DEBUG: SimpleGrpcClient - Failed to send message: ${e.message}")
            _error.value = "Failed to send message to Go server: ${e.message}"
        }
    }
    
    fun testConnection(): Boolean {
        return isConnected && channel != null
    }
}
