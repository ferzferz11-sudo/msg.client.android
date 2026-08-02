package lavender.client.android.ui.contacts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.UserInfoProto

data class ContactsUiState(
    val isLoading: Boolean = false,
    val contacts: List<String> = emptyList(),
    val allUsers: List<UserInfoProto> = emptyList(),
    val error: String? = null,
    val chatCreated: ChatCreatedEvent? = null
)

data class ChatCreatedEvent(
    val chatId: String,
    val chatName: String,
    val isDirect: Boolean,
    val participants: String,
    val creator: String = ""
)

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private val grpcClient = GrpcClient

    val onlineUsers: StateFlow<List<String>> = grpcClient.users

    fun loadContacts(username: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        grpcClient.getContacts(username) { list ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    contacts = list.filter { it != username }
                )
            }
        }
    }

    fun addContact(username: String, contactUsername: String, onComplete: (Boolean) -> Unit) {
        grpcClient.addContact(username, contactUsername) { success, _ ->
            onComplete(success)
        }
    }

    fun removeContacts(username: String, contacts: List<String>, onComplete: () -> Unit) {
        var completed = 0
        if (contacts.isEmpty()) {
            onComplete()
            return
        }
        contacts.forEach { contact ->
            grpcClient.removeContact(username, contact) { _, _ ->
                completed++
                if (completed == contacts.size) {
                    onComplete()
                }
            }
        }
    }

    fun loadAllUsers() {
        grpcClient.loadAllUsers { users ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(allUsers = users)
            }
        }
    }

    fun observeAllUsers(onCollect: (List<UserInfoProto>) -> Unit) {
        viewModelScope.launch {
            grpcClient.allUsers.collect { users ->
                onCollect(users)
            }
        }
    }

    fun createDirectChat(username: String, targetUser: String) {
        grpcClient.createDirectChat(username, targetUser) { chatId ->
            if (chatId != null) {
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(
                        chatCreated = ChatCreatedEvent(
                            chatId = chatId,
                            chatName = targetUser,
                            isDirect = true,
                            participants = """["$username", "$targetUser"]"""
                        )
                    )
                }
            }
        }
    }

    fun createGroupChat(name: String, participants: List<String>, username: String) {
        grpcClient.createGroupChat(name, participants, username) { chatId ->
            if (chatId != null) {
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(
                        chatCreated = ChatCreatedEvent(
                            chatId = chatId,
                            chatName = name,
                            isDirect = false,
                            participants = org.json.JSONArray(participants).toString(),
                            creator = username
                        )
                    )
                }
            }
        }
    }

    fun consumeChatCreatedEvent() {
        _uiState.value = _uiState.value.copy(chatCreated = null)
    }

    fun getAvatarCache(): Map<String, String> = grpcClient.getAvatarCache()
}
