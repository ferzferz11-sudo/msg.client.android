package lavender.client.android.ui.chatlist

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.ProfileClient
import lavender.client.android.data.models.ChatInfo

/**
 * ChatListViewModel — ViewModel для ChatListActivity.
 *
 * Управляет:
 * - Загрузкой и фильтрацией чатов
 * - Секциями (Pinned/Favorites/All)
 * - Действиями (Pin, Archive, Mute, Delete)
 * - Поиском
 */
class ChatListViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatListViewModel"
    }

    private val _sections = MutableStateFlow<List<SectionItem>>(emptyList())
    val sections: StateFlow<List<SectionItem>> = _sections.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _tabFilter = MutableStateFlow("all")
    val tabFilter: StateFlow<String> = _tabFilter.asStateFlow()

    private var allChats: List<ChatInfo> = emptyList()

    init {
        viewModelScope.launch {
            GrpcClient.connectionStatus.collect { status ->
                _connectionStatus.value = status
                if (status == ConnectionStatus.READY) {
                    loadChats()
                }
            }
        }
    }

    fun loadChats() {
        if (_isLoading.value) return
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val username = lavender.client.android.data.session.SessionManager.session.value.username
                val fetchedChats = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    kotlinx.coroutines.suspendCancellableCoroutine<List<ChatInfo>> { cont ->
                        GrpcClient.getChats(username, skipCache = true) { chats ->
                            if (cont.isActive) cont.resumeWith(Result.success(chats))
                        }
                    }
                } ?: emptyList()

                allChats = fetchedChats
                loadFavorites(username)
                buildSections(fetchedChats)
                Log.d(TAG, "Loaded ${fetchedChats.size} chats")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load chats", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadFavorites(username: String) {
        val userId = lavender.client.android.data.session.SessionManager.session.value.userId
        if (userId.isEmpty()) return
        viewModelScope.launch {
            try {
                GrpcClient.getFavorites(userId) { messages ->
                    // Favorites chat exists as type="favorites" in chats list
                    // Just ensure it's present
                    val favoritesId = "favorites_$username"
                    val hasFavorites = allChats.any { it.id == favoritesId }
                    if (!hasFavorites) {
                        val favoritesChat = lavender.client.android.data.models.ChatInfo(
                            id = favoritesId,
                            name = "Favorites",
                            type = "favorites",
                            lastMessageText = "",
                            lastMessageTime = 0L
                        )
                        allChats = allChats + favoritesChat
                        buildSections(allChats)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load favorites", e)
            }
        }
    }

    fun refreshChats() {
        loadChats()
    }

    fun searchChats(query: String) {
        _searchQuery.value = query
        if (query.isEmpty()) {
            buildSections(allChats)
        } else {
            val filtered = allChats.filter { chat ->
                chat.name.lowercase().contains(query.lowercase()) ||
                chat.lastMessageText.lowercase().contains(query.lowercase())
            }
            buildSections(filtered)
        }
    }

    fun pinChat(chatId: String) {
        viewModelScope.launch {
            try {
                val success = GrpcClient.pinChat(getApplication(), chatId)
                if (success) {
                    // Update local state
                    allChats = allChats.map {
                        if (it.id == chatId) it.copy(isPinned = true, pinnedAt = System.currentTimeMillis()) else it
                    }
                    buildSections(allChats)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pin chat $chatId", e)
            }
        }
    }

    fun unpinChat(chatId: String) {
        viewModelScope.launch {
            try {
                val success = GrpcClient.unpinChat(getApplication(), chatId)
                if (success) {
                    allChats = allChats.map {
                        if (it.id == chatId) it.copy(isPinned = false, pinnedAt = 0L) else it
                    }
                    buildSections(allChats)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unpin chat $chatId", e)
            }
        }
    }

    fun archiveChat(chatId: String) {
        viewModelScope.launch {
            try {
                val success = GrpcClient.archiveChat(getApplication(), chatId)
                if (success) {
                    allChats = allChats.map {
                        if (it.id == chatId) it.copy(isArchived = true) else it
                    }
                    buildSections(allChats)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to archive chat $chatId", e)
            }
        }
    }

    fun unarchiveChat(chatId: String) {
        viewModelScope.launch {
            try {
                val success = GrpcClient.unarchiveChat(getApplication(), chatId)
                if (success) {
                    allChats = allChats.map {
                        if (it.id == chatId) it.copy(isArchived = false) else it
                    }
                    buildSections(allChats)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unarchive chat $chatId", e)
            }
        }
    }

    fun toggleMute(chatId: String, mute: Boolean) {
        viewModelScope.launch {
            try {
                GrpcClient.setMutedChat(chatId, mute) { success ->
                    if (success) {
                        allChats = allChats.map {
                            if (it.id == chatId) it.copy(isMuted = mute) else it
                        }
                        buildSections(allChats)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle mute for $chatId", e)
            }
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            try {
                val username = lavender.client.android.data.session.SessionManager.session.value.username
                GrpcClient.deleteChat(chatId, username) { success, _ ->
                    if (success) {
                        allChats = allChats.filter { it.id != chatId }
                        buildSections(allChats)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete chat $chatId", e)
            }
        }
    }

    fun setTabFilter(filter: String) {
        _tabFilter.value = filter
        buildSections(allChats)
    }

    fun getChats(): List<ChatInfo> = allChats

    fun onChatClick(chat: ChatInfo) {
        Log.d(TAG, "Chat clicked: ${chat.name} (${chat.id})")
    }

    /**
     * Mark chat as read: clear unread count locally + send MarkAsRead to server.
     */
    fun markAsRead(chatId: String) {
        viewModelScope.launch {
            try {
                val username = lavender.client.android.data.session.SessionManager.session.value.username
                GrpcClient.markRead(chatId, username) {
                    // Server acknowledged — clear unread locally
                    allChats = allChats.map {
                        if (it.id == chatId) it.copy(unreadCount = 0) else it
                    }
                    buildSections(allChats)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark as read: $chatId", e)
            }
        }
    }

    /**
     * Increment unread count for a chat (called when new message arrives in non-active chat).
     */
    fun incrementUnreadCount(chatId: String) {
        allChats = allChats.map {
            if (it.id == chatId) it.copy(unreadCount = it.unreadCount + 1) else it
        }
        buildSections(allChats)
    }

    private fun buildSections(chats: List<ChatInfo>) {
        val tab = _tabFilter.value

        // Apply tab filter
        val filteredChats = when (tab) {
            "ai" -> chats.filter { it.type == "owl" || it.type == "hermes" }
            "groups" -> chats.filter { it.type == "group" || it.type == "general" || it.type == "conference" }
            else -> chats // "all"
        }

        val pinned = filteredChats.filter { it.isPinned && !it.isArchived }
            .sortedByDescending { it.pinnedAt }
        val favorites = filteredChats.filter { it.type == "favorites" }
        val allRegular = filteredChats.filter { !it.isPinned && !it.isArchived && it.type != "favorites" }
            .sortedByDescending { it.lastMessageTime }

        val sectionList = mutableListOf<SectionItem>()

        if (pinned.isNotEmpty()) {
            sectionList.add(SectionItem(Section.PINNED, pinned))
        }
        if (favorites.isNotEmpty()) {
            sectionList.add(SectionItem(Section.FAVORITES, favorites))
        }
        if (allRegular.isNotEmpty()) {
            sectionList.add(SectionItem(Section.ALL_CHATS, allRegular))
        }

        _sections.value = sectionList
    }
}
