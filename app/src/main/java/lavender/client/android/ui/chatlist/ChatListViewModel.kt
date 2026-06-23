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
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.session.SessionManager
import lavender.client.android.data.db.toEntity
import lavender.client.android.data.db.toDomain
import lavender.client.android.data.ai.AiV2ChatUseCase

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
    private var nextCursor: String = ""
    private var hasMore: Boolean = true
    private var isLoadingMore: Boolean = false

    init {
        // Load cached chats on startup (offline-first)
        viewModelScope.launch(Dispatchers.IO) {
            if (allChats.isEmpty()) {
                try {
                    val db = lavender.client.android.data.db.AppDatabase.getDatabase(getApplication())
                    val cached = db.chatDao().getAllChats().map { it.toDomain() }
                    if (cached.isNotEmpty()) {
                        allChats = cached
                        buildSections(cached)
                        Log.d(TAG, "Loaded ${cached.size} chats from cache on startup")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load chats from cache on startup", e)
                }
            }
        }
        viewModelScope.launch {
            GrpcClient.connectionStatus.collect { status ->
                _connectionStatus.value = status
                if (status == ConnectionStatus.READY) {
                    loadChats(silent = true)
                }
            }
        }
        // Listen for errors — show in UI
        viewModelScope.launch {
            GrpcClient.error.collect { errorMsg ->
                _error.value = errorMsg
            }
        }
        // Listen for read receipts from OTHER users — DO NOT clear current user's unread count.
        // Unread count = messages I haven't read. Other users' READ_ALL signals don't affect my unread.
        // My own markAsRead() already clears unread locally + server sync handles persistence.
        // Listen for new messages in other rooms — update chat list in real-time
        viewModelScope.launch {
            GrpcClient.newMessageEvent.collect { message ->
                val currentUsername = SessionManager.session.value.username
                val isFromOther = message.user != currentUsername
                val preview = if (message.imageUrl.isNotEmpty()) "[image]"
                else if (message.voiceUrl.isNotEmpty()) "[voice]"
                else message.text.take(100)
                val chatIdx = allChats.indexOfFirst { it.id == message.roomId }

                if (chatIdx >= 0) {
                    val chat = allChats[chatIdx]
                    val shouldIncrement = isFromOther && !message.isRead
                    val newUnread = if (shouldIncrement) chat.unreadCount + 1 else chat.unreadCount
                    allChats = allChats.toMutableList().also {
                        it[chatIdx] = chat.copy(
                            lastMessageText = preview,
                            lastMessageTime = message.timestamp,
                            lastMessageUsername = message.user,
                            lastMessageHasImage = message.imageUrl.isNotEmpty(),
                            unreadCount = newUnread
                        )
                    }
                    // Reorder: move updated chat to top of its section
                    val updatedChat = allChats[chatIdx]
                    allChats = allChats.toMutableList().also {
                        it.removeAt(chatIdx)
                        it.add(0, updatedChat)
                    }
                } else {
                    // New chat not in list — reload to pick it up
                    loadChats(silent = true)
                    return@collect
                }
                buildSections(allChats)
            }
        }
        // Listen for deleted chats — remove from list in real-time
        viewModelScope.launch {
            GrpcClient.chatDeletedEvent.collect { deletedChatId ->
                if (deletedChatId != null && deletedChatId.isNotEmpty()) {
                    allChats = allChats.filter { it.id != deletedChatId }
                    buildSections(allChats)
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val db = lavender.client.android.data.db.AppDatabase.getDatabase(getApplication())
                            db.chatDao().deleteChat(deletedChatId)
                        } catch (e: Exception) { Log.w(TAG, "Failed to delete chat from cache", e) }
                    }
                    Log.d(TAG, "Chat deleted: $deletedChatId")
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
                    loadChats(silent = true)
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

    fun loadChats(silent: Boolean = false) {
        if (_isLoading.value) return
        if (!silent) _isLoading.value = true
        val startTime = System.currentTimeMillis()
        nextCursor = ""
        hasMore = true

        viewModelScope.launch {
            try {
                // Ensure JWT is fresh before any gRPC call
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    lavender.client.android.data.session.SessionManager.ensureFreshToken(getApplication())
                }

                val username = SessionManager.session.value.username

                // First: load from cache for instant display
                if (allChats.isEmpty()) {
                    try {
                        val db = lavender.client.android.data.db.AppDatabase.getDatabase(getApplication())
                        val cached = db.chatDao().getAllChats().map { it.toDomain() }
                        if (cached.isNotEmpty()) {
                            allChats = cached
                            buildSections(cached)
                    val unreadChats = cached.filter { it.unreadCount > 0 }
                    Log.d(TAG, "Loaded ${cached.size} chats from cache (${unreadChats.size} unread)")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load chats from cache", e)
                    }
                }

                // Fetch first page from server
                val fetchedPage = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    kotlinx.coroutines.suspendCancellableCoroutine<lavender.client.android.data.grpc.ChatListPage> { cont ->
                        GrpcClient.getChats(username, skipCache = true) { page ->
                            if (cont.isActive) cont.resumeWith(Result.success(page))
                        }
                    }
                }

                if (fetchedPage != null) {
                    val fetchedChats = fetchedPage.chats
                    nextCursor = fetchedPage.nextCursor
                    hasMore = fetchedPage.hasMore
                    val serverUnread = fetchedChats.filter { it.unreadCount > 0 }
                    Log.d(TAG, "Server returned ${fetchedChats.size} chats (${serverUnread.size} unread, hasMore=$hasMore)")

                    val hasChanges = allChats.size != fetchedChats.size ||
                        allChats.map { it.id }.toSet() != fetchedChats.map { it.id }.toSet() ||
                        fetchedChats.any { server ->
                            val local = allChats.find { it.id == server.id }
                            local == null || local.lastMessageTime != server.lastMessageTime ||
                                local.unreadCount != server.unreadCount || local.isPinned != server.isPinned ||
                                local.isArchived != server.isArchived || local.lastMessageText != server.lastMessageText
                        }
                    if (hasChanges || allChats.isEmpty()) {
                        val mergedChats = fetchedChats.map { serverChat ->
                            val localChat = allChats.find { it.id == serverChat.id }
                            if (localChat != null && localChat.unreadCount > serverChat.unreadCount) {
                                serverChat.copy(unreadCount = localChat.unreadCount)
                            } else {
                                serverChat
                            }
                        }
                        allChats = mergedChats
                        buildSections(mergedChats)
                val mergedUnread = mergedChats.filter { it.unreadCount > 0 }
                Log.d(TAG, "Synced ${mergedChats.size} chats (${mergedUnread.size} unread)")
                    } else {
                        Log.d(TAG, "No changes — keeping ${allChats.size} chats")
                    }
                    // Sync to local DB — use allChats (merged) to preserve locally-incremented unread counts
                    try {
                        val db = lavender.client.android.data.db.AppDatabase.getDatabase(getApplication())
                        db.chatDao().syncChats(allChats.map { it.toEntity() })
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to sync chats to cache", e)
                    }
                    Log.d(TAG, "Loaded ${fetchedChats.size} chats from server (${System.currentTimeMillis() - startTime}ms)")
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

    /**
     * Load AI chats from server and merge into the chat list.
     * Called separately after main chats are loaded to avoid blocking startup.
     */
    fun loadAiChats() {
        viewModelScope.launch {
            try {
                val aiChats = AiV2ChatUseCase.listAIChats()
                val aiChatInfos = aiChats.map { aiChat ->
                    ChatInfo(
                        id = aiChat.id,
                        name = aiChat.agentName,
                        type = "hermes",
                        participants = "[]",
                        createdAt = aiChat.createdAt,
                        lastMessageTime = aiChat.updatedAt,
                        activeAgentId = aiChat.agentId,
                        isPinned = false,
                        isArchived = false
                    )
                }
                val existingIds = allChats.map { it.id }.toSet()
                val newAiChats = aiChatInfos.filter { it.id !in existingIds }
                if (newAiChats.isNotEmpty()) {
                    allChats = allChats + newAiChats
                    buildSections(allChats)
                    Log.d(TAG, "Merged ${newAiChats.size} AI chats (total=${allChats.size})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load AI chats: ${e.message}")
            }
        }
    }

    fun loadMoreChats() {
        if (isLoadingMore || !hasMore || nextCursor.isEmpty()) return
        isLoadingMore = true

        viewModelScope.launch {
            try {
                val username = SessionManager.session.value.username
                val fetchedPage = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    kotlinx.coroutines.suspendCancellableCoroutine<lavender.client.android.data.grpc.ChatListPage> { cont ->
                        GrpcClient.getChats(username, skipCache = true, limit = 100, cursor = nextCursor) { page ->
                            if (cont.isActive) cont.resumeWith(Result.success(page))
                        }
                    }
                }

                if (fetchedPage != null && fetchedPage.chats.isNotEmpty()) {
                    val existingIds = allChats.map { it.id }.toSet()
                    val newChats = fetchedPage.chats.filter { it.id !in existingIds }
                    if (newChats.isNotEmpty()) {
                        allChats = allChats + newChats
                        buildSections(allChats)
                        Log.d(TAG, "Loaded ${newChats.size} more chats (total=${allChats.size})")
                    }
                    nextCursor = fetchedPage.nextCursor
                    hasMore = fetchedPage.hasMore
                } else {
                    hasMore = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load more chats", e)
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun refreshChats() {
        _isLoading.value = false
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

    fun deleteChat(chatId: String, onResult: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                val username = SessionManager.session.value.username
                GrpcClient.deleteChat(chatId, username) { success, _ ->
                    if (success) {
                        allChats = allChats.filter { it.id != chatId }
                        buildSections(allChats)
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val db = lavender.client.android.data.db.AppDatabase.getDatabase(getApplication())
                                db.chatDao().deleteChat(chatId)
                            } catch (e: Exception) { Log.w(TAG, "Failed to delete chat from cache", e) }
                        }
                    }
                    onResult?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete chat $chatId", e)
                onResult?.invoke()
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
        val chat = allChats.find { it.id == chatId }
        Log.d(TAG, "markAsRead: chatId=$chatId name=${chat?.name} currentUnread=${chat?.unreadCount}")
        viewModelScope.launch {
            try {
                val username = SessionManager.session.value.username
                GrpcClient.markRead(chatId, username) {
                    allChats = allChats.map {
                        if (it.id == chatId) it.copy(unreadCount = 0) else it
                    }
                    buildSections(allChats)
                    Log.d(TAG, "markAsRead: chatId=$chatId — cleared to 0")
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

        val maskedChats = filteredChats.map {
            if (it.isSecret) it.copy(lastMessageText = "") else it
        }

        val pinned = maskedChats.filter { it.isPinned && !it.isArchived }
            .sortedByDescending { it.pinnedAt }
        val allRegular = maskedChats.filter { !it.isPinned && !it.isArchived }
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
