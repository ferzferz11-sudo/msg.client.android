package lavender.client.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.grpc.*
import lavender.client.android.data.grpc.GrpcClientExtensions.*

class ChatListViewModel : ViewModel() {
    var currentChats: List<ChatInfo> = emptyList()
    var lastChatListVersion: Long = -1
    var isInitialLoadComplete: Boolean = false
    var avatarCache: Map<String, String> = emptyMap()

    fun loadChats(username: String, callback: (Boolean, String) -> Unit) {
        GrpcClient.getChats(username) { chats ->
            currentChats = chats.sortedByDescending { maxOf(it.lastMessageTime, it.createdAt) }
            isInitialLoadComplete = true
            callback(true, "")
        }
    }
}
