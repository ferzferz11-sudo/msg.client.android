package lavender.client.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo

class ChatListViewModel : ViewModel() {
    var currentChats: List<ChatInfo> = emptyList()
    var lastChatListVersion: Long = -1
    var isInitialLoadComplete: Boolean = false
    var avatarCache: Map<String, String> = emptyMap()

    fun loadChats(username: String, callback: (Boolean, String) -> Unit) {
        GrpcClient.getChats(username) { chats ->
            currentChats = chats
            isInitialLoadComplete = true
            callback(true, "")
        }
    }
}
