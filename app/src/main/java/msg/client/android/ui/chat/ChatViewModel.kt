package msg.client.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import msg.client.android.data.grpc.GrpcClient
import msg.client.android.data.models.Message

class ChatViewModel : ViewModel() {
    private val grpcClient = GrpcClient()
    
    val connectionState: StateFlow<Boolean> = grpcClient.connectionState
    val error: StateFlow<String?> = grpcClient.error
    val messages: StateFlow<List<Message>> = grpcClient.messages
    val users: StateFlow<List<String>> = grpcClient.users
    
    fun connect(serverAddress: String, useTls: Boolean = false, port: Int = 50051) {
        viewModelScope.launch {
            grpcClient.connect(serverAddress, useTls, port)
        }
    }
    
    fun disconnect() {
        viewModelScope.launch {
            grpcClient.disconnect()
        }
    }
    
    fun startChat(username: String, joinMessage: String, onMessageReceived: (Message) -> Unit) {
        grpcClient.startChat(username, joinMessage, onMessageReceived)
    }
    
    fun sendMessage(message: Message) {
        grpcClient.sendMessage(message)
    }
    
    fun deleteMessage(message: Message) {
        grpcClient.deleteMessage(message)
    }
    
    fun testConnection(): Boolean {
        return grpcClient.testConnection()
    }
}
