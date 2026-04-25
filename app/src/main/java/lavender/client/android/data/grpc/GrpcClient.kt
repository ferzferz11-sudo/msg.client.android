package lavender.client.android.data.grpc

import kotlinx.coroutines.flow.StateFlow
import lavender.client.android.data.models.Message
import lavender.client.android.data.proto.GetUserProfileResponseProto

object GrpcClient {
    private val realGrpcClient = RealGrpcClient
    
    val connectionState: StateFlow<Boolean> = realGrpcClient.connectionState
    val messages: StateFlow<List<Message>> = realGrpcClient.messages
    val users: StateFlow<List<String>> = realGrpcClient.users
    val allUsers: StateFlow<List<String>> = realGrpcClient.allUsers
    val error: StateFlow<String?> = realGrpcClient.error
    val systemNotification: StateFlow<String?> = realGrpcClient.systemNotification
    val typingUsers: StateFlow<Map<String, Set<String>>> = realGrpcClient.typingUsers
    
    var hasCheckedForUpdates: Boolean
        get() = realGrpcClient.hasCheckedForUpdates
        set(value) { realGrpcClient.hasCheckedForUpdates = value }

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

    fun editMessage(messageId: String, text: String, callback: (Boolean, String) -> Unit = { _, _ -> }) {
        realGrpcClient.editMessage(messageId, text, callback)
    }

    fun updateMessage(message: Message) {
        realGrpcClient.updateMessage(message)
    }

    fun setReaction(messageId: String, username: String, emoji: String) {
        realGrpcClient.setReaction(messageId, username, emoji)
    }

    fun registerToken(user: String, token: String) {
        realGrpcClient.registerToken(user, token)
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

    fun getChats(username: String, callback: (List<lavender.client.android.data.models.ChatInfo>) -> Unit) {
        realGrpcClient.getChats(username, callback)
    }

    fun createDirectChat(user1: String, user2: String, callback: (String?) -> Unit) {
        realGrpcClient.createDirectChat(user1, user2, callback)
    }

    fun createGroupChat(name: String, participants: List<String>, callback: (String?) -> Unit) {
        realGrpcClient.createGroupChat(name, participants, callback)
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

    fun markRead(roomId: String, username: String, onCompletion: (() -> Unit)? = null) {
        realGrpcClient.markRead(roomId, username, onCompletion)
    }

    fun sendTypingSignal(username: String, isTyping: Boolean) {
        realGrpcClient.sendTypingSignal(username, isTyping)
    }

    fun updateAvatar(username: String, avatarUrl: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.updateAvatar(username, avatarUrl, callback)
    }

    fun getUserAvatar(username: String, callback: (String) -> Unit) {
        realGrpcClient.getUserAvatar(username, callback)
    }

    fun deleteChat(chatId: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.deleteChat(chatId, callback)
    }

    fun deleteProfile(username: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.deleteProfile(username, callback)
    }

    fun getUserProfile(username: String, callback: (GetUserProfileResponseProto?) -> Unit) {
        realGrpcClient.getUserProfile(username, callback)
    }

    fun updateProfile(username: String, bio: String, status: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.updateProfile(username, bio, status, callback)
    }

    fun addContact(username: String, contactUsername: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.addContact(username, contactUsername, callback)
    }

    fun removeContact(username: String, contactUsername: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.removeContact(username, contactUsername, callback)
    }

    fun getContacts(username: String, callback: (List<String>) -> Unit) {
        realGrpcClient.getContacts(username, callback)
    }

    fun getAvatarCache(): Map<String, String> {
        return realGrpcClient.getAvatarCache()
    }

    fun updateAvatarCache(username: String, avatarUrl: String) {
        realGrpcClient.updateAvatarCache(username, avatarUrl)
    }

    fun clearMessages() {
        realGrpcClient.clearMessages()
    }
}
