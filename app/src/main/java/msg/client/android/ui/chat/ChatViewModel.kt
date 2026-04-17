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
    
    fun connect(serverAddress: String, useTls: Boolean = false) {
        viewModelScope.launch {
            grpcClient.connect(serverAddress, useTls)
        }
    }
    
    fun disconnect() {
        viewModelScope.launch {
            grpcClient.disconnect()
        }
    }
    
    fun startChat(username: String, onMessageReceived: (Message) -> Unit) {
        grpcClient.startChat(username, onMessageReceived)
    }
    
    fun sendMessage(message: Message) {
        grpcClient.sendMessage(message)
    }
    
    fun testConnection(): Boolean {
        return grpcClient.testConnection()
    }
}
