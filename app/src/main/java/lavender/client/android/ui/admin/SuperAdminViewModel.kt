package lavender.client.android.ui.admin

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.proto.AdminUserInfoProto
import lavender.client.android.data.proto.AdminUserSessionProto
import lavender.client.android.data.session.SessionManager

data class SuperAdminUiState(
    val isLoading: Boolean = false,
    val adminUsers: List<AdminUserInfoProto> = emptyList(),
    val allChats: List<ChatInfo> = emptyList(),
    val currentMode: Mode = Mode.USERS,
    val currentCursor: String = "",
    val hasMore: Boolean = true,
    val selectedUsernames: Set<String> = emptySet(),
    val selectedChatIds: Set<String> = emptySet(),
    val expandedUserSessions: Map<String, List<AdminUserSessionProto>> = emptyMap(),
    val expandedUsers: Set<String> = emptySet(),
    val error: String? = null,
    val successMessage: String? = null
)

enum class Mode { USERS, GROUPS }

class SuperAdminViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SuperAdminUiState())
    val uiState: StateFlow<SuperAdminUiState> = _uiState.asStateFlow()

    private val grpcClient = GrpcClient
    private val username: String = SessionManager.session.value.username

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, currentCursor = "", hasMore = true)
            try {
                grpcClient.getAdminUserList("", "", 50, "last_message") { response ->
                    viewModelScope.launch {
                        val filtered = response.users.filter { it.username != "[deleted]" }
                        _uiState.value = _uiState.value.copy(
                            adminUsers = filtered,
                            currentCursor = response.nextCursor,
                            hasMore = response.hasMore
                        )

                        grpcClient.getAllChats { chats ->
                            viewModelScope.launch {
                                _uiState.value = _uiState.value.copy(
                                    allChats = chats,
                                    isLoading = false
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SuperAdmin", "Failed to load data", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun loadMoreUsers() {
        val state = _uiState.value
        if (!state.hasMore || state.currentCursor.isEmpty()) return

        viewModelScope.launch {
            try {
                grpcClient.getAdminUserList("", state.currentCursor, 50, "last_message") { response ->
                    viewModelScope.launch {
                        val filtered = response.users.filter { it.username != "[deleted]" }
                        _uiState.value = _uiState.value.copy(
                            adminUsers = state.adminUsers + filtered,
                            currentCursor = response.nextCursor,
                            hasMore = response.hasMore
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("SuperAdmin", "Failed to load more users", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun loadUserSessions(user: AdminUserInfoProto) {
        viewModelScope.launch {
            try {
                grpcClient.getAdminUserSessions(user.userId) { response ->
                    viewModelScope.launch {
                        val newExpanded = _uiState.value.expandedUserSessions.toMutableMap()
                        newExpanded[user.username] = response.sessions
                        val newExpandedUsers = _uiState.value.expandedUsers.toMutableSet()
                        if (newExpandedUsers.contains(user.username)) {
                            newExpandedUsers.remove(user.username)
                        } else {
                            newExpandedUsers.add(user.username)
                        }
                        _uiState.value = _uiState.value.copy(
                            expandedUserSessions = newExpanded,
                            expandedUsers = newExpandedUsers
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("SuperAdmin", "Failed to load user sessions", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun setMode(mode: Mode) {
        _uiState.value = _uiState.value.copy(currentMode = mode)
        clearSelection()
    }

    fun toggleUserSelection(username: String) {
        val current = _uiState.value.selectedUsernames.toMutableSet()
        if (current.contains(username)) {
            current.remove(username)
        } else {
            current.add(username)
        }
        _uiState.value = _uiState.value.copy(selectedUsernames = current)
    }

    fun toggleChatSelection(chatId: String) {
        val current = _uiState.value.selectedChatIds.toMutableSet()
        if (current.contains(chatId)) {
            current.remove(chatId)
        } else {
            current.add(chatId)
        }
        _uiState.value = _uiState.value.copy(selectedChatIds = current)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedUsernames = emptySet(),
            selectedChatIds = emptySet()
        )
    }

    fun filterCurrentList(query: String) {
        val q = query.lowercase()
        val state = _uiState.value
        if (state.currentMode == Mode.USERS) {
            val filtered = state.adminUsers.filter { it.username.lowercase().contains(q) }
            _uiState.value = state.copy(adminUsers = filtered)
        } else {
            val filtered = state.allChats.filter { it.name.lowercase().contains(q) || it.id.lowercase().contains(q) }
            _uiState.value = state.copy(allChats = filtered)
        }
    }

    fun deleteSelectedUsers() {
        val usernames = _uiState.value.selectedUsernames.filter { it != "[deleted]" }.toList()
        if (usernames.isEmpty()) { clearSelection(); return }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            var deletedCount = 0
            usernames.forEach { targetUser ->
                grpcClient.deleteProfile(targetUser) { _, _ ->
                    viewModelScope.launch {
                        deletedCount++
                        if (deletedCount == usernames.size) {
                            clearSelection()
                            loadData()
                        }
                    }
                }
            }
        }
    }

    fun deleteSelectedChats() {
        val chatIds = _uiState.value.selectedChatIds.toList()
        if (chatIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            var deletedCount = 0
            chatIds.forEach { targetId ->
                grpcClient.deleteChat(targetId, username) { _, _ ->
                    viewModelScope.launch {
                        deletedCount++
                        if (deletedCount == chatIds.size) {
                            clearSelection()
                            loadData()
                        }
                    }
                }
            }
        }
    }

    fun changePassword(targetUser: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                grpcClient.adminUpdatePassword(targetUser, newPassword, username) { success, message ->
                    viewModelScope.launch {
                        if (success) {
                            clearSelection()
                            loadData()
                            _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Password updated")
                        } else {
                            _uiState.value = _uiState.value.copy(isLoading = false, error = message)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SuperAdmin", "Failed to change password", e)
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
