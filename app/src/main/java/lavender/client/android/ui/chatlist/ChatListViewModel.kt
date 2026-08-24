package lavender.client.android.ui.chatlist

import android.app.Application
import android.util.Log
import lavender.client.android.R
import lavender.client.android.data.models.ErrorHandler
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.session.SessionManager
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.db.toEntity
import lavender.client.android.data.db.toDomain
import lavender.client.android.data.ai.AiV2ChatUseCase
import androidx.core.content.edit

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

        /** Returns true if the message is a system message that should not appear in chat list preview. */
        fun isSystemMessage(message: lavender.client.android.data.models.Message): Boolean {
            if (message.user.isEmpty()) return true
            if (message.id.startsWith("sd_timer_")) return true
            val text = message.text
            if (text.startsWith("\uD83D\uDD25")) return true  // 🔥 self-destruct timer
            if (text.startsWith("\uD83D\uDCF9") || text.startsWith("\uD83D\uDCDE")) return true  // 📹 📞 call messages
            return false
        }
    }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    private val _sections = MutableStateFlow<List<SectionItem>>(emptyList())
    val sections: StateFlow<List<SectionItem>> = _sections.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _chatsLoaded = MutableStateFlow(false)
    val chatsLoaded: StateFlow<Boolean> = _chatsLoaded.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _forceLogoutEvent = MutableSharedFlow<String>()
    val forceLogoutEvent: SharedFlow<String> = _forceLogoutEvent.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _tabFilter = MutableStateFlow("all")

    /** Cache of company names keyed by companyId. Populated from session + company chats. */
    val companyNameCache = mutableMapOf<String, String>()
    val tabFilter: StateFlow<String> = _tabFilter.asStateFlow()

    private val _scrollToTopEvent = MutableStateFlow(0L)
    val scrollToTopEvent: StateFlow<Long> = _scrollToTopEvent.asStateFlow()

    private val locallyReadChats: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    // Multi-company: companyId → positionLevel cache
    private val companyPositionCache: MutableMap<String, Int> = java.util.concurrent.ConcurrentHashMap()

    private var allChats: List<ChatInfo> = emptyList()
    private val loadChatsMutex = kotlinx.coroutines.sync.Mutex()
    private var syncJob: Job? = null
    private var nextCursor: String = ""
    private var hasMore: Boolean = true
    private var isLoadingMore: Boolean = false

    init {
        // Load cached chats on startup (offline-first)
        viewModelScope.launch {
            if (allChats.isEmpty()) {
                try {
                    val db = lavender.client.android.data.db.AppDatabase.getDatabase(getApplication())
                    val cached = withContext(Dispatchers.IO) {
                        db.chatDao().getAllChats().map { it.toDomain() }
                    }
                    if (cached.isNotEmpty()) {
                        allChats = cached
                        buildSections(cached)
                        Log.d(TAG, "Loaded ${cached.size} chats from cache on startup")
                    }
                } catch (e: Exception) {
                    ErrorHandler.handle(TAG, "Failed to load chats from cache on startup", e)
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
        // Listen for read receipts — when current user reads messages in a chat,
        // add it to locallyReadChats so the chat list shows 0 unread on return.
        viewModelScope.launch {
            GrpcClient.readReceiptEvent.collect { (roomId, readerId) ->
                val myUserId = SessionManager.session.value.userId
                if (roomId.isNotEmpty() && readerId == myUserId) {
                    locallyReadChats.add(roomId)
                    allChats = allChats.map {
                        if (it.id == roomId && it.unreadCount > 0) it.copy(unreadCount = 0) else it
                    }
                    scheduleBuildSections()
                }
            }
        }
        // Listen for new messages in other rooms — update chat list in real-time
        viewModelScope.launch {
            GrpcClient.newMessageEvent.collect { message ->
                // Skip system messages (self-destruct timer, call messages) — don't show in chat list preview
                if (isSystemMessage(message)) return@collect

                val currentUsername = SessionManager.session.value.username
                val isFromOther = message.user != currentUsername
                val ctx = getApplication<Application>()
                val preview = if (message.imageUrl.isNotEmpty()) ctx.getString(R.string.chat_preview_image)
                else if (message.voiceUrl.isNotEmpty()) ctx.getString(R.string.chat_preview_voice)
                else message.text.take(100)
                val chatIdx = allChats.indexOfFirst { it.id == message.roomId }

                if (chatIdx >= 0) {
                    val chat = allChats[chatIdx]
                    val shouldIncrement = isFromOther && !message.isRead
                    val newUnread = if (shouldIncrement) chat.unreadCount + 1 else chat.unreadCount
                    val updatedChat = chat.copy(
                        lastMessageText = preview,
                        lastMessageTime = message.timestamp,
                        lastMessageUsername = message.user,
                        lastMessageHasImage = message.imageUrl.isNotEmpty(),
                        unreadCount = newUnread
                    )
                    val mutable = allChats.toMutableList()
                    mutable.removeAt(chatIdx)
                    mutable.add(0, updatedChat)
                    allChats = mutable
                } else {
                    loadChats(silent = true)
                    return@collect
                }
                scheduleBuildSections()
            }
        }
        // Listen for deleted chats — remove from list in real-time
        viewModelScope.launch {
            GrpcClient.chatDeletedEvent.collect { deletedChatId ->
                if (deletedChatId != null && deletedChatId.isNotEmpty()) {
                    allChats = allChats.filter { it.id != deletedChatId }
                    scheduleBuildSections()
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val db = lavender.client.android.data.db.AppDatabase.getDatabase(getApplication())
                            db.chatDao().deleteChat(deletedChatId)
                        } catch (e: Exception) { ErrorHandler.handle(TAG, "Failed to delete chat from cache", e) }
                    }
                    Log.d(TAG, "Chat deleted: $deletedChatId")
                }
            }
        }
        // Listen for history cleared event — refresh that chat locally and move to top
        viewModelScope.launch {
            GrpcClient.historyClearedEvent.collect { roomId ->
                Log.d(TAG, "historyClearedEvent received for room: $roomId")
                val chatIdx = allChats.indexOfFirst { it.id == roomId }
                if (chatIdx >= 0) {
                    val chat = allChats[chatIdx]
                    val updatedChat = chat.copy(
                        lastMessageText = "",
                        lastMessageTime = System.currentTimeMillis(),
                        lastMessageUsername = "",
                        lastMessageHasImage = false,
                        unreadCount = 0
                    )
                    val mutable = allChats.toMutableList()
                    mutable.removeAt(chatIdx)
                    mutable.add(0, updatedChat)
                    allChats = mutable
                    scheduleBuildSections()
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
                delay(30.seconds)
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
        if (loadChatsMutex.isLocked) return

        viewModelScope.launch {
            if (!loadChatsMutex.tryLock()) return@launch
            if (!silent) _isLoading.value = true
            try {
                val startTime = System.currentTimeMillis()
                nextCursor = ""
                hasMore = true
                // Ensure JWT is fresh before any gRPC call
                withContext(Dispatchers.IO) {
                    SessionManager.ensureFreshToken(getApplication())
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
                        ErrorHandler.handle(TAG, "Failed to load chats from cache", e)
                    }
                }

                // Launch regular chats and AI chats in parallel using separate scope
                var fetchedPage: lavender.client.android.data.grpc.ChatListPage? = null
                var aiSessions: List<lavender.client.android.data.ai.AiV2ChatSession> = emptyList()
                kotlinx.coroutines.supervisorScope {
                    val pageDeferred = kotlinx.coroutines.CompletableDeferred<lavender.client.android.data.grpc.ChatListPage?>()
                    val aiDeferred = kotlinx.coroutines.CompletableDeferred<List<lavender.client.android.data.ai.AiV2ChatSession>>()
                    launch(Dispatchers.IO) {
                        pageDeferred.complete(
                            kotlinx.coroutines.withTimeoutOrNull(10.seconds) {
                                kotlinx.coroutines.suspendCancellableCoroutine<lavender.client.android.data.grpc.ChatListPage> { cont ->
                                    GrpcClient.getChats(username, skipCache = true) { page ->
                                        if (cont.isActive) cont.resumeWith(Result.success(page))
                                    }
                                }
                            }
                        )
                    }
                    launch(Dispatchers.IO) {
                        aiDeferred.complete(
                            kotlinx.coroutines.withTimeoutOrNull(10.seconds) {
                                try { AiV2ChatUseCase.listAIChats() } catch (_: Exception) { null }
                            } ?: emptyList()
                        )
                    }
                    fetchedPage = pageDeferred.await()
                    aiSessions = aiDeferred.await()
                }

                // Process regular chats
                if (fetchedPage != null) {
                    // Check for auth errors — retry token refresh once before force logout
                    // INTERNAL/NOT_CONNECTED are server availability errors, NOT auth errors — don't logout
                    if (fetchedPage!!.error != null && fetchedPage!!.chats.isEmpty() && allChats.isEmpty()) {
                        val error = fetchedPage!!.error
                        if (error == "UNAUTHENTICATED" || error == "PERMISSION_DENIED") {
                            Log.w(TAG, "loadChats: auth error ($error) — retrying token refresh before logout")
                            withContext(Dispatchers.IO) {
                                SessionManager.forceTokenRefresh(getApplication())
                            }
                            // Retry getChats once with refreshed token
                            val retriedPage = kotlinx.coroutines.withTimeoutOrNull(10.seconds) {
                                kotlinx.coroutines.suspendCancellableCoroutine<lavender.client.android.data.grpc.ChatListPage> { cont ->
                                    GrpcClient.getChats(username, skipCache = true) { page ->
                                        if (cont.isActive) cont.resumeWith(Result.success(page))
                                    }
                                }
                            }
                            // Only force logout if retry also failed with auth error
                            if (retriedPage != null && retriedPage.error != null && retriedPage.chats.isEmpty()) {
                                val retryError = retriedPage.error
                                if (retryError == "UNAUTHENTICATED" || retryError == "PERMISSION_DENIED") {
                                    Log.w(TAG, "loadChats: auth error ($retryError) after token refresh — forcing logout")
                                    _forceLogoutEvent.emit(retryError!!)
                                    return@launch
                                }
                            }
                            // Retry succeeded or got non-auth error — use retried result
                            fetchedPage = retriedPage ?: fetchedPage
                        }
                    }

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
                            if (serverChat.id in locallyReadChats) {
                                locallyReadChats.remove(serverChat.id)
                                serverChat.copy(unreadCount = 0)
                            } else {
                                val localChat = allChats.find { it.id == serverChat.id }
                                if (localChat != null && localChat.unreadCount > serverChat.unreadCount) {
                                    serverChat.copy(unreadCount = localChat.unreadCount)
                                } else {
                                    serverChat
                                }
                            }
                        }.distinctBy { it.id }
                        allChats = mergedChats
                        val mergedUnread = mergedChats.filter { it.unreadCount > 0 }
                        Log.d(TAG, "Synced ${mergedChats.size} chats (${mergedUnread.size} unread)")
                    } else {
                        Log.d(TAG, "No changes — keeping ${allChats.size} chats")
                    }
                    // Sync to local DB
                    try {
                        val db = lavender.client.android.data.db.AppDatabase.getDatabase(getApplication())
                        db.chatDao().syncChats(allChats.map { it.toEntity() })
                    } catch (e: Exception) {
                        ErrorHandler.handle(TAG, "Failed to sync chats to cache", e)
                    }
                    Log.d(TAG, "Loaded ${fetchedChats.size} chats from server (${System.currentTimeMillis() - startTime}ms)")
                } else {
                    Log.w(TAG, "loadChats timeout — keeping ${allChats.size} existing chats")
                }

                // Merge AI chats (already loaded in parallel)
                if (aiSessions.isNotEmpty()) {
                    val prefs = getApplication<android.app.Application>().getSharedPreferences("lavender_prefs", android.content.Context.MODE_PRIVATE)
                    val deletedAiChats = prefs.getStringSet("deleted_ai_chats", emptySet()) ?: emptySet()
                    val aiChatInfos = aiSessions
                        .filter { it.id !in deletedAiChats }
                        .map { aiChat ->
                            ChatInfo(
                                id = aiChat.id, name = aiChat.agentName, type = "hermes",
                                participants = "[]", createdAt = aiChat.createdAt,
                                lastMessageTime = aiChat.updatedAt, activeAgentId = aiChat.agentId,
                                isPinned = false, isArchived = false
                            )
                        }
                    val existingIds = allChats.map { it.id }.toSet()
                    val newAiChats = aiChatInfos.filter { it.id !in existingIds }
                    if (newAiChats.isNotEmpty()) {
                        allChats = allChats + newAiChats
                        Log.d(TAG, "Merged ${newAiChats.size} AI chats (total=${allChats.size})")
                    }
                }

                // Load company positions for multi-company access control
                loadCompanyPositions()

                // Apply pending mute update from NewChatActivity
                ChatListSharedState.pendingMuteUpdate?.let { (muteRoomId, muteState) ->
                    allChats = allChats.map { if (it.id == muteRoomId) it.copy(isMuted = muteState) else it }
                    ChatListSharedState.pendingMuteUpdate = null
                }

                buildSections(allChats)
            } catch (e: Exception) {
                ErrorHandler.handle(TAG, "Failed to load chats", e)
            } finally {
                _isLoading.value = false
                _chatsLoaded.value = true
                loadChatsMutex.unlock()
            }
        }
    }

    private suspend fun loadCompanyPositions() {
        try {
            val response = withContext(Dispatchers.IO) {
                lavender.client.android.data.grpc.GrpcCompanyClient.getUserCompanies()
            }
            if (response?.companies != null) {
                companyPositionCache.clear()
                companyNameCache.clear()
                for (entry in response.companies) {
                    val companyId = entry.company?.id ?: continue
                    val positionLevel = entry.member?.position?.level ?: 0
                    companyPositionCache[companyId] = positionLevel
                    val companyName = entry.company?.name ?: ""
                    if (companyName.isNotEmpty()) {
                        companyNameCache[companyId] = companyName
                    }
                }
                Log.d(TAG, "Loaded ${companyPositionCache.size} company positions, ${companyNameCache.size} names")
            }
        } catch (e: Exception) {
            Log.w(TAG, "loadCompanyPositions failed: ${e.message}")
        }
    }

    fun loadMoreChats() {
        if (isLoadingMore || !hasMore || nextCursor.isEmpty()) return
        isLoadingMore = true

        viewModelScope.launch {
            try {
                val username = SessionManager.session.value.username
                val fetchedPage = kotlinx.coroutines.withTimeoutOrNull(10.seconds) {
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
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    SessionManager.forceTokenRefresh(getApplication())
                }

                if (GrpcClient.connectionStatus.value != ConnectionStatus.READY) {
                    Log.d(TAG, "gRPC not READY, reconnecting...")
                    val serverAddress = CredentialStore.getServerAddress(getApplication()) ?: ""
                    if (serverAddress.isNotEmpty()) {
                        val parts = serverAddress.split(":")
                        val host = parts.firstOrNull() ?: serverAddress
                        val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
                        GrpcClient.connect(
                            serverAddress = host,
                            useTls = false,
                            port = port,
                            context = getApplication(),
                            forceReconnect = true
                        )
                        val startWait = System.currentTimeMillis()
                        while (GrpcClient.connectionStatus.value != ConnectionStatus.READY
                            && System.currentTimeMillis() - startWait < 5000) {
                            delay(200.milliseconds)
                        }
                        Log.d(TAG, "gRPC status after wait: ${GrpcClient.connectionStatus.value}")
                    }
                }
            } catch (e: Exception) {
                ErrorHandler.handle(TAG, "Refresh preparation failed: ${e.message}", e)
            }
            loadChats()
        }
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
                val success = GrpcClient.pinChat(chatId)
                if (success) {
                    // Update local state
                    allChats = allChats.map {
                        if (it.id == chatId) it.copy(isPinned = true, pinnedAt = System.currentTimeMillis()) else it
                    }
                    buildSections(allChats)
                }
            } catch (e: Exception) {
                ErrorHandler.handle(TAG, "Failed to pin chat $chatId", e)
            }
        }
    }

    fun unpinChat(chatId: String) {
        viewModelScope.launch {
            try {
                val success = GrpcClient.unpinChat(chatId)
                if (success) {
                    allChats = allChats.map {
                        if (it.id == chatId) it.copy(isPinned = false, pinnedAt = 0L) else it
                    }
                    buildSections(allChats)
                }
            } catch (e: Exception) {
                ErrorHandler.handle(TAG, "Failed to unpin chat $chatId", e)
            }
        }
    }

    fun archiveChat(chatId: String, onResult: ((Boolean) -> Unit)? = null) {
        // Optimistic update — remove from visible list immediately
        val previousChats = allChats
        allChats = allChats.map {
            if (it.id == chatId) it.copy(isArchived = true) else it
        }
        buildSections(allChats)

        viewModelScope.launch {
            try {
                val success = GrpcClient.archiveChat(chatId)
                if (!success) {
                    // Revert on failure
                    allChats = previousChats
                    buildSections(allChats)
                }
                onResult?.invoke(success)
            } catch (e: Exception) {
                // Revert on error
                allChats = previousChats
                buildSections(allChats)
                ErrorHandler.handle(TAG, "Failed to archive chat $chatId", e)
                onResult?.invoke(false)
            }
        }
    }

    fun unarchiveChat(chatId: String, onResult: ((Boolean) -> Unit)? = null) {
        val previousChats = allChats
        allChats = allChats.map {
            if (it.id == chatId) it.copy(isArchived = false) else it
        }
        buildSections(allChats)

        viewModelScope.launch {
            try {
                val success = GrpcClient.unarchiveChat(chatId)
                if (!success) {
                    allChats = previousChats
                    buildSections(allChats)
                }
                onResult?.invoke(success)
            } catch (e: Exception) {
                allChats = previousChats
                buildSections(allChats)
                ErrorHandler.handle(TAG, "Failed to unarchive chat $chatId", e)
                onResult?.invoke(false)
            }
        }
    }

    fun toggleMute(chatId: String, mute: Boolean) {
        viewModelScope.launch {
            try {
                GrpcClient.setMutedChat(chatId, mute) { success ->
                    if (success) {
                        viewModelScope.launch(Dispatchers.Main) {
                            allChats = allChats.map {
                                if (it.id == chatId) it.copy(isMuted = mute) else it
                            }
                            buildSections(allChats)
                        }
                    }
                }
            } catch (e: Exception) {
                ErrorHandler.handle(TAG, "Failed to toggle mute for $chatId", e)
            }
        }
    }

    fun deleteChat(chatId: String, onResult: (String?) -> Unit = { _ -> }) {
        viewModelScope.launch {
            try {
                val isAiChat = chatId.startsWith("ai-chat-")
                if (isAiChat) {
                    allChats = allChats.filter { it.id != chatId }
                    buildSections(allChats)
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val db = lavender.client.android.data.db.AppDatabase.getDatabase(getApplication())
                            db.chatDao().deleteChat(chatId)
                        } catch (e: Exception) { ErrorHandler.handle(TAG, "Failed to delete AI chat from cache", e) }
                        val prefs = getApplication<android.app.Application>().getSharedPreferences("lavender_prefs", android.content.Context.MODE_PRIVATE)
                        val deleted = prefs.getStringSet("deleted_ai_chats", emptySet()) ?: emptySet()
                        prefs.edit { putStringSet("deleted_ai_chats", deleted + chatId) }
                    }
                    onResult(null)
                    return@launch
                }

                val username = SessionManager.session.value.username
                GrpcClient.deleteChat(chatId, username) { success, message ->
                    viewModelScope.launch(Dispatchers.Main) {
                        if (success) {
                            allChats = allChats.filter { it.id != chatId }
                            buildSections(allChats)
                            kotlinx.coroutines.withContext(Dispatchers.IO) {
                                try {
                                    val db = lavender.client.android.data.db.AppDatabase.getDatabase(getApplication())
                                    db.chatDao().deleteChat(chatId)
                                } catch (e: Exception) { ErrorHandler.handle(TAG, "Failed to delete chat from cache", e) }
                            }
                            onResult(null)
                        } else {
                            onResult(message.ifEmpty { "Failed to delete chat" })
                        }
                    }
                }
            } catch (e: Exception) {
                ErrorHandler.handle(TAG, "Failed to delete chat $chatId", e)
                onResult(e.message ?: "Failed to delete chat")
            }
        }
    }

    fun setTabFilter(filter: String) {
        _tabFilter.value = filter
        _scrollToTopEvent.value = System.currentTimeMillis()
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
        locallyReadChats.add(chatId)
        allChats = allChats.map {
            if (it.id == chatId) it.copy(unreadCount = 0) else it
        }
        buildSections(allChats)
        viewModelScope.launch {
            try {
                val username = SessionManager.session.value.username
                GrpcClient.markRead(chatId, username) {
                    Log.d(TAG, "markAsRead: chatId=$chatId — server confirmed")
                }
            } catch (e: Exception) {
                ErrorHandler.handle(TAG, "Failed to mark as read: $chatId", e)
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

    private var buildSectionsJob: kotlinx.coroutines.Job? = null
    private fun scheduleBuildSections() {
        buildSectionsJob?.cancel()
        buildSectionsJob = viewModelScope.launch {
            kotlinx.coroutines.delay(100.milliseconds)
            buildSections(allChats)
        }
    }

    private fun buildSections(chats: List<ChatInfo>) {
        viewModelScope.launch(Dispatchers.Default) {
            // Update global unread count for background notification
            val totalUnread = chats.sumOf { it.unreadCount }
            GrpcClient.setTotalUnreadCount(totalUnread)

            val tab = _tabFilter.value
            val userPositionLevel = lavender.client.android.data.session.SessionManager.session.value.positionLevel
            val userCompanyId = lavender.client.android.data.session.SessionManager.session.value.companyId

            // Apply tab filter
            val filteredChats = when {
                tab == "ai" -> chats.filter { it.type == "owl" || it.type == "hermes" }
                tab == "groups" -> chats.filter { it.type == "group" || it.type == "general" || it.type == "conference" }
                tab == "company" -> chats.filter { it.companyId.isNotEmpty() }
                tab.startsWith("company:") -> {
                    val targetCompanyId = tab.removePrefix("company:")
                    chats.filter { it.companyId == targetCompanyId }
                }
                tab == "archive" -> chats.filter { it.isArchived }
                else -> chats // "all"
            }.filter { chat ->
                // Hide saved_messages chat — accessed via toolbar star button
                chat.type != "saved_messages"
            }.filter { chat ->
                // Company chat access control (per-company)
                if (chat.companyId.isNotEmpty()) {
                    // Look up position level from cache (populated by loadCompanyPositions)
                    val positionLevel = companyPositionCache[chat.companyId] ?: userPositionLevel
                    when {
                        chat.companyMinPositionLevel > 0 -> positionLevel >= chat.companyMinPositionLevel
                        chat.companyChatAccess == "management" -> positionLevel >= 1
                        chat.companyChatAccess == "owner_only" -> positionLevel >= 3
                        else -> true // "member" access — all company employees
                    }
                } else true
            }

            val maskedChats = filteredChats.map {
                if (it.isSecret) it.copy(lastMessageText = "") else it
            }

            val isArchiveTab = tab == "archive"
            val pinned = maskedChats.filter { it.isPinned && (isArchiveTab || !it.isArchived) }
                .sortedByDescending { it.pinnedAt }
            val allRegular = maskedChats.filter { !it.isPinned && (isArchiveTab || !it.isArchived) }
                .sortedByDescending { it.lastMessageTime }

            val sectionList = mutableListOf<SectionItem>()

            if (pinned.isNotEmpty()) {
                sectionList.add(SectionItem(Section.PINNED, pinned))
            }
            if (allRegular.isNotEmpty()) {
                sectionList.add(SectionItem(Section.ALL_CHATS, allRegular))
            }

            withContext(Dispatchers.Main) {
                _sections.value = sectionList
            }
        }
    }
}
