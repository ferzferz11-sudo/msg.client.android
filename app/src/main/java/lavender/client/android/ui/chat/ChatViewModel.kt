package lavender.client.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message

class ChatViewModel : ViewModel() {
    val grpcClient = GrpcClient

    var currentRoomId = "general"

    val connectionState: StateFlow<Boolean> = grpcClient.connectionState
    val error: StateFlow<String?> = grpcClient.error
    val messages: StateFlow<List<Message>> = grpcClient.messages
    val users: StateFlow<List<String>> = grpcClient.users
    val allUsers: StateFlow<List<String>> = grpcClient.allUsers
    val systemNotification: StateFlow<String?> = grpcClient.systemNotification
    val typingUsers: StateFlow<Map<String, Set<String>>> = grpcClient.typingUsers
    
    fun connect(serverAddress: String, useTls: Boolean = false, port: Int = 50051, context: android.content.Context? = null) {
        viewModelScope.launch {
            grpcClient.connect(serverAddress, useTls, port, context)
        }
    }
    
    fun disconnect() {
        viewModelScope.launch {
            grpcClient.disconnect()
        }
    }
    
    fun startChat(username: String, password: String, joinMessage: String, onMessageReceived: (Message) -> Unit) {
        grpcClient.startChat(username, password, joinMessage, onMessageReceived)
    }
    
    fun sendMessage(message: Message) {
        grpcClient.sendMessage(message)
    }

    fun updateMessage(message: Message) {
        grpcClient.updateMessage(message)
    }

    fun deleteMessage(message: Message) {
        grpcClient.deleteMessage(message)
    }

    fun setReaction(messageId: String, username: String, emoji: String) {
        grpcClient.setReaction(messageId, username, emoji)
    }

    fun registerToken(username: String, token: String) {
        grpcClient.registerToken(username, token)
    }

    fun clearSystemNotification() {
        grpcClient.clearSystemNotification()
    }

    fun switchRoom(roomId: String) {
        currentRoomId = roomId
        grpcClient.setRoomId(roomId)
        // Clear messages before loading new room history
        grpcClient.clearMessages()
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            grpcClient.loadHistory(currentRoomId)
        }
    }

    fun markRead(username: String, context: android.content.Context? = null, onCompletion: (() -> Unit)? = null) {
        grpcClient.markRead(currentRoomId, username, onCompletion)
        
        // Dismiss push notifications for this room locally
        context?.let {
            lavender.client.android.data.fcm.LavenderMessagingService.dismissNotificationsForRoom(it, currentRoomId)
        }
    }

    fun sendTypingSignal(username: String, isTyping: Boolean) {
        grpcClient.sendTypingSignal(username, isTyping)
    }

    fun saveDraft(draftText: String, repliedToMessageId: String = "", repliedToUser: String = "", repliedToText: String = "", callback: (Boolean, String) -> Unit = { _, _ -> }) {
        grpcClient.saveDraft(currentRoomId, draftText, repliedToMessageId, repliedToUser, repliedToText, callback)
    }

    fun getDraft(callback: (String, String, String, String, Boolean) -> Unit) {
        grpcClient.getDraft(currentRoomId, callback)
    }

    fun deleteDraft(callback: (Boolean) -> Unit = {}) {
        grpcClient.deleteDraft(currentRoomId, callback)
    }
}
