package msg.client.android.data.grpc

import kotlinx.coroutines.flow.StateFlow
import msg.client.android.data.models.Message

class GrpcClient {
    private val realGrpcClient = RealGrpcClient()
    
    val connectionState: StateFlow<Boolean> = realGrpcClient.connectionState
    val messages: StateFlow<List<Message>> = realGrpcClient.messages
    val error: StateFlow<String?> = realGrpcClient.error
    
    fun connect(serverAddress: String, useTls: Boolean = false) {
        realGrpcClient.connect(serverAddress, useTls)
    }
    
    fun disconnect() {
        realGrpcClient.disconnect()
    }
    
    fun startChat(username: String, onMessageReceived: (Message) -> Unit) {
        realGrpcClient.startChat(username, onMessageReceived)
    }
    
    fun sendMessage(message: Message) {
        realGrpcClient.sendMessage(message)
    }
    
    fun testConnection(): Boolean {
        return realGrpcClient.testConnection()
    }
}
