package msg.client.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import msg.client.android.data.grpc.GrpcClient
import msg.client.android.data.models.Message

class ChatViewModel : ViewModel() {
    val grpcClient = GrpcClient()

    var currentRoomId = "general"

    val connectionState: StateFlow<Boolean> = grpcClient.connectionState
    val error: StateFlow<String?> = grpcClient.error
    val messages: StateFlow<List<Message>> = grpcClient.messages
    val users: StateFlow<List<String>> = grpcClient.users
    val allUsers: StateFlow<List<String>> = grpcClient.allUsers
    val systemNotification: StateFlow<String?> = grpcClient.systemNotification
    
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
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            grpcClient.loadHistory(currentRoomId)
        }
    }
}
