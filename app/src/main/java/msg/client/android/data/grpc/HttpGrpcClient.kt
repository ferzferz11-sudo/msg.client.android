package msg.client.android.data.grpc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import msg.client.android.data.models.Message
import msg.client.android.data.proto.MessageProto
import msg.client.android.data.proto.ProtoUtils
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import java.io.IOException
import java.util.concurrent.TimeUnit

class HttpGrpcClient {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private var isConnected = false
    private var serverAddress: String = ""
    
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages
    
    fun connect(serverAddress: String, useTls: Boolean = false) {
        try {
            this.serverAddress = serverAddress
            // Test connection with a simple HTTP request
            testGrpcConnection()
        } catch (e: Exception) {
            _error.value = "Connection failed: ${e.message}"
            isConnected = false
            _connectionState.value = false
        }
    }
    
    private fun testGrpcConnection() {
        try {
            // Create a simple gRPC-like request to test connection
            val url = "http://$serverAddress:50051"
            
            // For now, we'll simulate successful connection
            // In real implementation, this would be actual gRPC handshake
            isConnected = true
            _connectionState.value = true
            _error.value = null
            
        } catch (e: Exception) {
            _error.value = "Connection test failed: ${e.message}"
            isConnected = false
            _connectionState.value = false
        }
    }
    
    fun disconnect() {
        isConnected = false
        _connectionState.value = false
    }
    
    fun startChat(username: String, onMessageReceived: (Message) -> Unit) {
        if (!isConnected) {
            _error.value = "Not connected to server"
            return
        }
        
        try {
            // Send join message
            val joinMessage = Message(
                user = username,
                text = "$username joined the chat",
                timestamp = System.currentTimeMillis()
            )
            
            _messages.value = _messages.value + joinMessage
            onMessageReceived(joinMessage)
            
            // Simulate server welcome
            val welcomeMessage = Message(
                user = "Server",
                text = "Welcome to the chat! Connected to Go server.",
                timestamp = System.currentTimeMillis()
            )
            
            _messages.value = _messages.value + welcomeMessage
            onMessageReceived(welcomeMessage)
            
        } catch (e: Exception) {
            _error.value = "Failed to start chat: ${e.message}"
        }
    }
    
    fun sendMessage(message: Message) {
        if (!isConnected) {
            _error.value = "Not connected to server"
            return
        }
        
        try {
            // Convert message to protobuf format
            val protoMessage = ProtoUtils.createMessageProto(message)
            
            // Send to Go server via HTTP (simulated)
            sendToGoServer(protoMessage) { success ->
                if (success) {
                    // Add message to local list
                    _messages.value = _messages.value + message
                } else {
                    _error.value = "Failed to send message to server"
                }
            }
            
        } catch (e: Exception) {
            _error.value = "Failed to send message: ${e.message}"
        }
    }
    
    private fun sendToGoServer(protoMessage: MessageProto, callback: (Boolean) -> Unit) {
        // Simulate sending message to Go server
        // In real implementation, this would be actual gRPC call
        Thread {
            try {
                // Simulate network delay
                Thread.sleep(100)
                
                // Simulate successful send to Go server
                callback(true)
                
                // Simulate receiving echo from other clients
                val echoMessage = Message(
                    user = "Other User",
                    text = "Echo: ${protoMessage.text}",
                    timestamp = System.currentTimeMillis()
                )
                
                _messages.value = _messages.value + echoMessage
                
            } catch (e: Exception) {
                callback(false)
            }
        }.start()
    }
    
    fun testConnection(): Boolean {
        return isConnected
    }
}
