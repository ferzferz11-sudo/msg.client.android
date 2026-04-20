package msg.client.android.data.grpc

import kotlinx.coroutines.flow.StateFlow
import msg.client.android.data.models.Message

class GrpcClient {
    private val realGrpcClient = RealGrpcClient()
    
    val connectionState: StateFlow<Boolean> = realGrpcClient.connectionState
    val messages: StateFlow<List<Message>> = realGrpcClient.messages
    val users: StateFlow<List<String>> = realGrpcClient.users
    val allUsers: StateFlow<List<String>> = realGrpcClient.allUsers
    val error: StateFlow<String?> = realGrpcClient.error
    val systemNotification: StateFlow<String?> = realGrpcClient.systemNotification
    
    fun connect(serverAddress: String, useTls: Boolean = false, port: Int = 50051, context: android.content.Context? = null) {
        realGrpcClient.connect(serverAddress, useTls, port, context)
    }
    
    fun disconnect() {
        realGrpcClient.disconnect()
    }
    
    fun startChat(username: String, password: String, joinMessage: String, onMessageReceived: (Message) -> Unit) {
        realGrpcClient.startChat(username, password, joinMessage, onMessageReceived)
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

    fun clearSystemNotification() {
        realGrpcClient.clearSystemNotification()
    }

    fun loadHistory(roomId: String) {
        realGrpcClient.loadHistory(roomId)
    }

    fun setRoomId(roomId: String) {
        realGrpcClient.setRoomId(roomId)
    }

    fun getChats(username: String, callback: (List<msg.client.android.data.models.ChatInfo>) -> Unit) {
        realGrpcClient.getChats(username, callback)
    }

    fun createDirectChat(user1: String, user2: String, callback: (String?) -> Unit) {
        realGrpcClient.createDirectChat(user1, user2, callback)
    }

    fun loadUsers() {
        realGrpcClient.loadUsers()
    }

    fun loadAllUsers() {
        realGrpcClient.loadAllUsers()
    }

    fun updateUsername(oldUsername: String, newUsername: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.updateUsername(oldUsername, newUsername, callback)
    }

    fun updatePassword(username: String, oldPassword: String, newPassword: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.updatePassword(username, oldPassword, newPassword, callback)
    }
}
