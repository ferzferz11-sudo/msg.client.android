package lavender.client.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.data.proto.UserInfoProto

class ChatViewModel : ViewModel() {
    val grpcClient = GrpcClient

    var currentRoomId = "general"

    val connectionState: StateFlow<Boolean> = grpcClient.connectionState
    val error: StateFlow<String?> = grpcClient.error
    val messages: StateFlow<List<Message>> = grpcClient.messages
    val users: StateFlow<List<String>> = grpcClient.users
    val allUsers: StateFlow<List<UserInfoProto>> = grpcClient.allUsers
    val systemNotification: StateFlow<String?> = grpcClient.systemNotification
    val typingUsers: StateFlow<Map<String, Set<String>>> = grpcClient.typingUsers
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
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
    
    fun startChat(username: String, password: String, joinMessage: String, register: Boolean = false, email: String = "", deviceId: String = "", deviceName: String = "", onMessageReceived: (Message) -> Unit) {
        grpcClient.startChat(username, password, joinMessage, register, email, deviceId, deviceName, onMessageReceived)
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
        _isLoading.value = true
        viewModelScope.launch {
            grpcClient.loadHistory(currentRoomId) {
                _isLoading.value = false
            }
        }
    }

    fun markRead(username: String, context: android.content.Context? = null, onCompletion: (() -> Unit)? = null) {
        // Dismiss push notifications for this room locally
        context?.let {
            lavender.client.android.data.fcm.LavenderMessagingService.dismissNotificationsForRoom(it, currentRoomId)
        }

        if (currentRoomId.startsWith("favorites_")) {
            onCompletion?.invoke()
            return
        }

        grpcClient.markRead(currentRoomId, username, onCompletion)
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