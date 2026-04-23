package lavender.client.android.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ChatViewModelFactory(private val roomId: String) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            val viewModel = ChatViewModel()
            viewModel.switchRoom(roomId)
            return viewModel as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
