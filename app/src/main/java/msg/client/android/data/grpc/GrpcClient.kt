package msg.client.android.data.grpc

import kotlinx.coroutines.flow.StateFlow
import msg.client.android.data.models.Message

class GrpcClient {
    private val realGrpcClient = RealGrpcClient()
    
    val connectionState: StateFlow<Boolean> = realGrpcClient.connectionState
    val messages: StateFlow<List<Message>> = realGrpcClient.messages
    val users: StateFlow<List<String>> = realGrpcClient.users
    val error: StateFlow<String?> = realGrpcClient.error
    
    fun connect(serverAddress: String, useTls: Boolean = false, port: Int = 50051, context: android.content.Context? = null) {
        realGrpcClient.connect(serverAddress, useTls, port, context)
    }
    
    fun disconnect() {
        realGrpcClient.disconnect()
    }
    
    fun startChat(username: String, joinMessage: String, onMessageReceived: (Message) -> Unit) {
        realGrpcClient.startChat(username, joinMessage, onMessageReceived)
    }
    
    fun sendMessage(message: Message) {
        realGrpcClient.sendMessage(message)
    }
    
    fun deleteMessage(message: Message) {
        realGrpcClient.deleteMessage(message)
    }

    fun setReaction(messageId: String, username: String, emoji: String) {
        realGrpcClient.setReaction(messageId, username, emoji)
    }

    fun registerToken(user: String, token: String) {
        realGrpcClient.registerToken(user, token)
    }
    
    fun testConnection(): Boolean {
        return realGrpcClient.testConnection()
    }
}
