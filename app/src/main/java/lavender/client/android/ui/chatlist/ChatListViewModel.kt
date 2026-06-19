package lavender.client.android.ui.chatlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.ProfileClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.session.SessionManager
import lavender.client.android.data.grpc.*
import lavender.client.android.data.db.toEntity
import lavender.client.android.data.db.toDomain

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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

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
    private var syncJob: Job? = null

    init {
        viewModelScope.launch {
            GrpcClient.connectionStatus.collect { status ->
                _connectionStatus.value = status
                if (status == ConnectionStatus.READY) {
                    loadChats()
                }
            }
        }
        // Listen for errors — show in UI
        viewModelScope.launch {
            GrpcClient.error.collect { errorMsg ->
                _error.value = errorMsg
            }
        }
        // Listen for read receipts — clear unread count in chat list
        viewModelScope.launch {
            GrpcClient.readReceiptEvent.collect { (roomId, reader) ->
                val currentUsername = SessionManager.session.value.username
                // Only clear unread if another user read our messages (not ourselves)
                if (reader != currentUsername) {
                    val idx = allChats.indexOfFirst { it.id == roomId }
                    if (idx >= 0) {
                        allChats = allChats.toMutableList().also {
                            it[idx] = it[idx].copy(unreadCount = 0)
                        }
                        buildSections(allChats)
                        Log.d(TAG, "Read receipt from $reader for room $roomId — cleared unread")
                    }
                }
            }
        }
        // Listen for new messages in other rooms — update chat list in real-time
        viewModelScope.launch {
            GrpcClient.newMessageEvent.collect { message ->
                val chatIdx = allChats.indexOfFirst { it.id == message.roomId }
                if (chatIdx >= 0) {
                    val chat = allChats[chatIdx]
                    val preview = if (message.imageUrl.isNotEmpty()) "[image]"
                        else if (message.voiceUrl.isNotEmpty()) "[voice]"
                        else message.text.take(100)
                    allChats = allChats.toMutableList().also {
                        it[chatIdx] = chat.copy(
                            lastMessageText = preview,
                            lastMessageTime = message.timestamp,
                            lastMessageUsername = message.user,
                            lastMessageHasImage = message.imageUrl.isNotEmpty(),
                            unreadCount = if (!message.isRead) chat.unreadCount + 1 else chat.unreadCount
                        )
                    }
                    buildSections(allChats)
                    Log.d(TAG, "New message in ${message.roomId} — updated chat list")
                }
            }
        }
        // Periodic sync: refresh chat list every 30s when connected
        viewModelScope.launch {
            GrpcClient.connectionStatus.collect { status ->
                if (status == ConnectionStatus.READY) {
                    startPeriodicSync()
                } else {
                    stopPeriodicSync()
                }
            }
        }
    }

    private fun startPeriodicSync() {
        stopPeriodicSync()
        syncJob = viewModelScope.launch {
            while (true) {
                delay(30_000)
                if (GrpcClient.connectionStatus.value == ConnectionStatus.READY && !_isLoading.value) {
                    loadChats()
                }
            }
        }
    }

    private fun stopPeriodicSync() {
        syncJob?.cancel()
        syncJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPeriodicSync()
    }

    fun loadChats() {
        if (_isLoading.value) return
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val username = lavender.client.android.data.session.SessionManager.session.value.username

                // First: load from cache for instant display
                if (allChats.isEmpty()) {
                    try {
                        val db = lavender.client.android.data.db.AppDatabase.getDatabase(getApplication())
                        val cached = db.chatDao().getAllChats().map { it.toDomain() }
                        if (cached.isNotEmpty()) {
                            allChats = cached
                            buildSections(cached)
                            Log.d(TAG, "Loaded ${cached.size} chats from cache")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load chats from cache", e)
                    }
                }

                // Then: fetch from server
                val fetchedChats = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    kotlinx.coroutines.suspendCancellableCoroutine<List<ChatInfo>> { cont ->
                        GrpcClient.getChats(username, skipCache = true) { chats ->
                            if (cont.isActive) cont.resumeWith(Result.success(chats))
                        }
                    }
                }

                if (fetchedChats != null) {
                    allChats = fetchedChats
                    buildSections(fetchedChats)
                    // Sync to local DB
                    try {
                        val db = lavender.client.android.data.db.AppDatabase.getDatabase(getApplication())
                        db.chatDao().syncChats(fetchedChats.map { it.toEntity() })
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to sync chats to cache", e)
                    }
                    Log.d(TAG, "Loaded ${fetchedChats.size} chats from server")
                } else {
                    // Timeout — keep existing chats, don't clear the list
                    Log.w(TAG, "loadChats timeout — keeping ${allChats.size} existing chats")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load chats", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshChats() {
        loadChats()
    }

    fun searchChats(query: String) {
        _searchQuery.value = query
        val currentUsername = SessionManager.session.value.username
        if (query.isEmpty()) {
            buildSections(allChats)
        } else {
            val filtered = allChats.filter { chat ->
                chat.getDisplayName(currentUsername).lowercase().contains(query.lowercase()) ||
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
                Log.d(TAG, "Chat clicked: ${chat.getDisplayName(SessionManager.session.value.username)} (${chat.id})")
    }

    /**
     * Mark chat as read: clear unread count locally + send MarkAsRead to server.
     */
    fun markAsRead(chatId: String) {
        viewModelScope.launch {
            try {
                val username = SessionManager.session.value.username
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
        val allRegular = filteredChats.filter { !it.isPinned && !it.isArchived }
            .sortedByDescending { it.lastMessageTime }

        val sectionList = mutableListOf<SectionItem>()

        if (pinned.isNotEmpty()) {
            sectionList.add(SectionItem(Section.PINNED, pinned))
        }
        if (allRegular.isNotEmpty()) {
            sectionList.add(SectionItem(Section.ALL_CHATS, allRegular))
        }

        _sections.value = sectionList
    }
}
