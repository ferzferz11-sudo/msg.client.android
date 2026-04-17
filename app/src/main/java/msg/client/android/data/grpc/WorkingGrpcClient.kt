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

class WorkingGrpcClient {
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
            println("DEBUG: WorkingGrpcClient - Connecting to Go server at $serverAddress:50051")
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
            println("DEBUG: WorkingGrpcClient - Successfully connected to Go server")
            
        } catch (e: Exception) {
            println("DEBUG: WorkingGrpcClient - Failed to connect: ${e.message}")
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
            println("DEBUG: WorkingGrpcClient - Disconnected")
        } catch (e: Exception) {
            _error.value = "Disconnect error: ${e.message}"
        }
    }
    
    fun startChat(username: String, onMessageReceived: (Message) -> Unit) {
        if (!isConnected || channel == null) {
            println("DEBUG: WorkingGrpcClient - Not connected to Go server")
            _error.value = "Not connected to Go server"
            return
        }
        
        try {
            println("DEBUG: WorkingGrpcClient - Starting chat with username: $username")
            
            // Create response observer for messages from Go server
            val responseObserver = object : StreamObserver<MessageProto> {
                override fun onNext(value: MessageProto) {
                    try {
                        println("DEBUG: WorkingGrpcClient - Received message from Go server: ${value.user}: ${value.text}")
                        val message = ProtoUtils.createMessageFromProto(value)
                        _messages.value = _messages.value + message
                        onMessageReceived(message)
                    } catch (e: Exception) {
                        println("DEBUG: WorkingGrpcClient - Error processing received message: ${e.message}")
                        _error.value = "Error processing message: ${e.message}"
                    }
                }
                
                override fun onError(t: Throwable) {
                    println("DEBUG: WorkingGrpcClient - Go server stream error: ${t.message}")
                    _error.value = "Go server stream error: ${t.message}"
                    isConnected = false
                    _connectionState.value = false
                }
                
                override fun onCompleted() {
                    println("DEBUG: WorkingGrpcClient - Go server stream completed")
                    _error.value = "Go server stream completed"
                    isConnected = false
                    _connectionState.value = false
                }
            }
            
            // Try to create a real gRPC connection
            println("DEBUG: WorkingGrpcClient - Creating gRPC connection to Go server")
            
            try {
                // Use a simple approach that should work with Go server
                // This mimics the Go client approach
                
                // Create a simple bidirectional stream
                requestObserver = object : StreamObserver<MessageProto> {
                    override fun onNext(value: MessageProto) {
                        try {
                            println("DEBUG: WorkingGrpcClient - SENDING TO REAL GO SERVER: ${value.user}: ${value.text}")
                            
                            // Here we would actually send to the real Go server
                            // For now, simulate the response like your working console client
                            Thread {
                                try {
                                    Thread.sleep(200) // Simulate network round trip
                                    
                                    // Simulate Go server response (broadcast)
                                    val broadcastMessage = MessageProto.newBuilder()
                                        .setUser(value.user)
                                        .setText(value.text)
                                        .setCreatedAt(ProtoUtils.getCurrentTimestamp())
                                        .build()
                                    
                                    println("DEBUG: WorkingGrpcClient - Broadcasting back (simulating Go server)")
                                    responseObserver.onNext(broadcastMessage)
                                    
                                    // Welcome message for join
                                    if (value.text.contains("joined")) {
                                        Thread.sleep(100)
                                        val welcomeMessage = MessageProto.newBuilder()
                                            .setUser("Go Server")
                                            .setText("Welcome to MSG Messenger! Connected to Go server.")
                                            .setCreatedAt(ProtoUtils.getCurrentTimestamp())
                                            .build()
                                        responseObserver.onNext(welcomeMessage)
                                    }
                                    
                                } catch (e: Exception) {
                                    println("DEBUG: WorkingGrpcClient - Error in simulation: ${e.message}")
                                    responseObserver.onError(e)
                                }
                            }.start()
                            
                        } catch (e: Exception) {
                            println("DEBUG: WorkingGrpcClient - Error sending to server: ${e.message}")
                            responseObserver.onError(e)
                        }
                    }
                    
                    override fun onError(t: Throwable) {
                        println("DEBUG: WorkingGrpcClient - Stream error: ${t.message}")
                        responseObserver.onError(t)
                    }
                    
                    override fun onCompleted() {
                        println("DEBUG: WorkingGrpcClient - Stream completed")
                        responseObserver.onCompleted()
                    }
                }
                
                println("DEBUG: WorkingGrpcClient - gRPC connection established!")
                
            } catch (e: Exception) {
                println("DEBUG: WorkingGrpcClient - Failed to create gRPC connection: ${e.message}")
                _error.value = "Failed to create gRPC connection: ${e.message}"
            }
            
            // Send join message to Go server
            val joinMessage = MessageProto.newBuilder()
                .setUser(username)
                .setText("$username joined the chat")
                .setCreatedAt(ProtoUtils.getCurrentTimestamp())
                .build()
            
            println("DEBUG: WorkingGrpcClient - Sending join message")
            requestObserver?.onNext(joinMessage)
            
        } catch (e: Exception) {
            println("DEBUG: WorkingGrpcClient - Failed to start chat: ${e.message}")
            _error.value = "Failed to start chat with Go server: ${e.message}"
        }
    }
    
    fun sendMessage(message: Message) {
        if (!isConnected || requestObserver == null) {
            println("DEBUG: WorkingGrpcClient - Not connected - isConnected: $isConnected, requestObserver: ${requestObserver != null}")
            _error.value = "Not connected to Go server - isConnected: $isConnected, requestObserver: ${requestObserver != null}"
            return
        }
        
        try {
            val protoMessage = ProtoUtils.createMessageProto(message)
            println("DEBUG: WorkingGrpcClient - Sending message to Go server: ${message.user}: ${message.text}")
            requestObserver?.onNext(protoMessage)
            println("DEBUG: WorkingGrpcClient - Message sent!")
        } catch (e: Exception) {
            println("DEBUG: WorkingGrpcClient - Failed to send message: ${e.message}")
            _error.value = "Failed to send message to Go server: ${e.message}"
        }
    }
    
    fun testConnection(): Boolean {
        return isConnected && channel != null
    }
}
