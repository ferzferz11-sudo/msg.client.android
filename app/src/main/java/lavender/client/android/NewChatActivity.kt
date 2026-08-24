package lavender.client.android

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.RealGrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.chat.ChatViewModel
import lavender.client.android.ui.chat.ChatViewModelFactory
import lavender.client.android.ui.chat.NewChatViewModel
import lavender.client.android.ui.chat.ChatMetadataState
import lavender.client.android.ui.chat.ChatIntentData
import lavender.client.android.ui.chat.message.*
import lavender.client.android.ui.adapter.MessageAdapter
import lavender.client.android.ui.adapter.MessageSwipeController
import lavender.client.android.data.calls.CallMessageHelper
import androidx.core.graphics.toColorInt
import kotlin.time.Duration.Companion.seconds

class NewChatActivity : AppCompatActivity() {

    private lateinit var chatViewModel: ChatViewModel
    private lateinit var newChatViewModel: NewChatViewModel
    private lateinit var adapter: MessageAdapter
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var historyLoadingProgress: ProgressBar

    private val grpcClient = GrpcClient
    private val toolbarDelegate = ChatToolbarDelegate(this, grpcClient)
    private val inputDelegate = ChatInputDelegate(this, grpcClient)
    private val selectionDelegate = ChatSelectionDelegate(this, grpcClient)
    private val searchDelegate = ChatSearchDelegate(this, lifecycleScope)
    private val messageMenuDelegate = ChatMessageMenuDelegate(this, grpcClient)
    private val e2eeDelegate = ChatE2EEDelegate(this, grpcClient)

    private var lastMessageCount = 0
    private var shouldScrollToBottom = true
    private var isChatMuted = false
    private var selfDestructTimer = 0

    private val data: ChatIntentData
        get() = newChatViewModel.intentData.value

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru"
        val locale = java.util.Locale.forLanguageTag(languageCode)
        java.util.Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "setDecorFitsSystemWindows failed: ${e.message}")
        }
        setContentView(R.layout.activity_new_chat)

        newChatViewModel = ViewModelProvider(this)[NewChatViewModel::class.java]
        newChatViewModel.parseIntent(intent, null)
        isChatMuted = intent.getBooleanExtra("IS_MUTED", false)
        selfDestructTimer = intent.getIntExtra("SELF_DESTRUCT_TIMER", 0)

        // Immediate notification dismissal when opening chat
        if (data.roomId.isNotEmpty()) {
            newChatViewModel.dismissNotifications(data.roomId)
        }

        if (grpcClient.connectionStatus.value != ConnectionStatus.READY) {
            newChatViewModel.ensureConnection()
        }

        newChatViewModel.setRoomId(data.roomId)

        try {
            initDelegates()
            initSharedViews()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "initDelegates/initSharedViews failed: ${e.message}", e)
            Toast.makeText(this, getString(R.string.chat_init_error), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        ThemeUi.bind(this, data.username)
        setupTheme()
        setupRecyclerView()
        try {
            setupDelegates()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "setupDelegates failed: ${e.message}", e)
        }

        setupObservers()
        setupKeyboardHandling()
        injectSelfDestructMessageIfNeeded()

        chatViewModel.fetchChatMetadata(data.username, data.roomId, data.isDirect, data.participantsJson, data.chatName) { meta ->
            try {
                lifecycleScope.launch {
                    if (isFinishing || isDestroyed) return@launch
                    newChatViewModel.updateMetadata(
                        ChatMetadataState(
                            chatName = meta.chatName, isDirect = meta.isDirect, chatType = meta.chatType,
                            participantsJson = meta.participantsJson, creator = meta.creator,
                            avatarUrl = meta.avatarUrl, fullAvatarUrl = meta.fullAvatarUrl,
                        )
                    )
                    val updated = newChatViewModel.metadata.value
                    toolbarDelegate.configure(
                        data.roomId, data.username, updated.chatName, updated.isDirect, updated.chatType,
                        updated.participantsJson, updated.creator, updated.avatarUrl, updated.fullAvatarUrl, data.isSecret
                    )
                    toolbarDelegate.setup()
                    toolbarDelegate.refreshSubtitle()
                    adapter.isGroupChat = !updated.isDirect
                    adapter.adminUsername = updated.creator
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "fetchChatMetadata callback failed: ${e.message}")
            }
        }
        if (data.isSecret) e2eeDelegate.initE2EE()

        messageMenuDelegate.configure(data.username)
    }

    private fun initDelegates() {
        toolbarDelegate.initViews()
        inputDelegate.initViews()
        selectionDelegate.initViews()
        searchDelegate.initViews()
    }

    private fun initSharedViews() {
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        historyLoadingProgress = findViewById(R.id.historyLoadingProgress)
    }

    private fun setupDelegates() {
        val d = newChatViewModel.intentData.value
        toolbarDelegate.configure(
            d.roomId, d.username, d.chatName, d.isDirect, d.chatType,
            d.participantsJson, d.creator, d.chatAvatarUrl, d.chatFullAvatarUrl, d.isSecret
        )
        toolbarDelegate.setup()
        toolbarDelegate.refreshSubtitle()
        
        adapter.isGroupChat = !d.isDirect
        adapter.adminUsername = d.creator

        inputDelegate.configure(d.roomId, d.username, d.isDirect, d.participantsJson, d.isSecret)
        inputDelegate.initViews()
        inputDelegate.setupListeners { file, duration ->
            chatViewModel.uploadAudio(this, file, duration, d.roomId) { audioUrl ->
                val msg = Message(
                    id = java.util.UUID.randomUUID().toString(),
                    user = d.username,
                    text = "",
                    timestamp = System.currentTimeMillis() / 1000,
                    roomId = d.roomId,
                    voiceUrl = audioUrl,
                    duration = duration,
                    userId = grpcClient.getUserId() ?: ""
                )
                chatViewModel.sendMessage(msg)
            }
        }
        selectionDelegate.configure(d.roomId, d.username)
        selectionDelegate.setupListeners()
        searchDelegate.setAdapter(adapter)
        searchDelegate.setupListeners()
    }

    private fun setupTheme() {
        try {
            val customTheme = ThemeStore.currentTheme()
            val pColor = customTheme.primaryColor.toColorInt()
            historyLoadingProgress.indeterminateTintList = ColorStateList.valueOf(pColor)
            swipeRefreshLayout.setColorSchemeColors(pColor)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "setupTheme failed: ${e.message}")
        }

        swipeRefreshLayout.setOnRefreshListener {
            chatViewModel.forceLoadHistory()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun setupRecyclerView() {
        val m = newChatViewModel.metadata.value
        adapter = MessageAdapter(
            currentUsername = data.username,
            isGroupChat = !m.isDirect,
            adminUsername = m.creator,
            onMessageClick = { message ->
                if (message.stickerUrl.isNotEmpty()) {
                    val intent = Intent(this, FullScreenImageActivity::class.java).apply {
                        putExtra("image_url", message.stickerThumbnailUrl.ifEmpty { message.stickerUrl })
                        putExtra("sticker_url", message.stickerUrl)
                    }
                    startActivity(intent)
                } else {
                    val text = message.text.trim().lowercase()
                    val isConference = CallMessageHelper.isConferenceMessage(text)
                    val isEnded = CallMessageHelper.isCallEnded(text)
                    if (isConference && !isEnded) joinConference()
                    else messageMenuDelegate.showReactionsDialog(message) { msg -> inputDelegate.showReplyPreview(msg) }
                }
            },
            onSelectionChanged = { if (it > 0) selectionDelegate.showSelectionToolbar(it) else selectionDelegate.hideSelectionToolbar() },
            onMessageLongClick = { selectionDelegate.enterSelectionMode(it) },
            chatId = data.roomId,
            onRetrySendMessage = { retryMessage(it) }
        ) { repliedToMessageId -> scrollToMessage(repliedToMessageId) }
        messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messagesRecyclerView.adapter = adapter
        selectionDelegate.setAdapter(adapter)

        val swipeController = MessageSwipeController(this) { position, direction ->
            if (direction == ItemTouchHelper.LEFT) {
                val msg = adapter.currentList.getOrNull(position)
                if (msg != null) { inputDelegate.showReplyPreview(msg); adapter.notifyItemChanged(position) }
            }
            else if (direction == ItemTouchHelper.RIGHT) finish()
        }
        ItemTouchHelper(swipeController).attachToRecyclerView(messagesRecyclerView)
    }

    private fun setupObservers() {
        chatViewModel = ViewModelProvider(this, ChatViewModelFactory(data.roomId))[ChatViewModel::class.java]

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatViewModel.messages.collect { roomMessages ->
                    try {
                        val hasNewMessages = roomMessages.size > lastMessageCount
                        val isNewFromOther = hasNewMessages && (roomMessages.lastOrNull()?.user != data.username)
                        adapter.submitList(roomMessages) {
                            if (shouldScrollToBottom || (isNewFromOther && isNearBottom())) {
                                shouldScrollToBottom = false
                                messagesRecyclerView.post { messagesRecyclerView.scrollToPosition(roomMessages.size - 1) }
                            }
                        }
                        if (hasNewMessages && roomMessages.any { it.user != data.username && !it.isRead }) {
                            chatViewModel.markRead(data.username)
                            newChatViewModel.dismissNotifications(data.roomId)
                        }
                        lastMessageCount = roomMessages.size
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "messages.collect error: ${e.message}", e)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatViewModel.isLoading.collect { loading ->
                    historyLoadingProgress.isVisible = loading && adapter.currentList.isEmpty()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(grpcClient.users, grpcClient.connectionStatus, grpcClient.typingUsers, grpcClient.allUsers) { onlineUsers, status, typingMap, _ ->
                    val currentUserId = grpcClient.getUserId() ?: ""
                    val currentTypists = typingMap[data.roomId]?.filter { it != data.username && it != currentUserId } ?: emptyList()
                    Triple(onlineUsers, status, currentTypists)
                }.collect { (onlineUsers, status, currentTypists) ->
                    try {
                        val isConnected = status == ConnectionStatus.READY
                        val isConnecting = status == ConnectionStatus.CONNECTING
                        val otherUser = toolbarDelegate.getOtherParticipant()
                        val otherUserLastSeenAt = otherUser?.let { u ->
                            grpcClient.allUsers.value.find { it.username == u }?.lastSeenAt
                        }
                        toolbarDelegate.updateSubtitle(onlineUsers, isConnected, currentTypists, otherUserLastSeenAt, grpcClient.serverShuttingDown.value)
                        inputDelegate.messageInput.isEnabled = !isConnecting
                        inputDelegate.sendButton.isEnabled = !isConnecting
                        inputDelegate.attachButton.isEnabled = !isConnecting
                        inputDelegate.audioButton.isEnabled = !isConnecting
                        if (isConnected) {
                            chatViewModel.syncChatListIfNeeded(this@NewChatActivity)
                            if (adapter.currentList.isEmpty()) {
                                shouldScrollToBottom = true
                            }
                            chatViewModel.loadHistory()
                            chatViewModel.loadPinnedMessages(this@NewChatActivity)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Connection observer error: ${e.message}", e)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatViewModel.pinnedMessageIds.collect { pinnedIds ->
                    selectionDelegate.setPinnedMessageIds(pinnedIds)
                    adapter.updatePinnedMessages(pinnedIds)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                grpcClient.selfDestructTimer.collect { timers ->
                    val newVal = timers[data.roomId] ?: 0
                    if (newVal != selfDestructTimer) {
                        selfDestructTimer = newVal
                        invalidateOptionsMenu()
                        injectSelfDestructMessageIfNeeded()
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(60.seconds)
                    if (grpcClient.connectionStatus.value == ConnectionStatus.READY) {
                        grpcClient.loadUsers()
                    }
                }
            }
        }

        // Retry fetchChatMetadata when connection becomes READY (handles notification-open case)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                grpcClient.connectionStatus.collect { status ->
                    if (status == ConnectionStatus.READY) {
                        val m = newChatViewModel.metadata.value
                        if (m.avatarUrl.isEmpty() && m.participantsJson == "[]" && data.roomId.startsWith("saved_messages_").not()) {
                            chatViewModel.fetchChatMetadata(data.username, data.roomId, data.isDirect, data.participantsJson, data.chatName) { meta ->
                                lifecycleScope.launch {
                                    if (isFinishing || isDestroyed) return@launch
                                    newChatViewModel.updateMetadata(
                                        ChatMetadataState(
                                            chatName = meta.chatName, isDirect = meta.isDirect, chatType = meta.chatType,
                                            participantsJson = meta.participantsJson, creator = meta.creator,
                                            avatarUrl = meta.avatarUrl, fullAvatarUrl = meta.fullAvatarUrl
                                        )
                                    )
                                    val updated = newChatViewModel.metadata.value
                                    toolbarDelegate.configure(
                                        data.roomId, data.username, updated.chatName, updated.isDirect, updated.chatType,
                                        updated.participantsJson, updated.creator, updated.avatarUrl, updated.fullAvatarUrl, data.isSecret
                                    )
                                    toolbarDelegate.setup()
                                    toolbarDelegate.refreshSubtitle()
                                    adapter.isGroupChat = !updated.isDirect
                                    adapter.adminUsername = updated.creator
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun injectSelfDestructMessageIfNeeded() {
        if (selfDestructTimer <= 0) return
        val timerLabel = when (selfDestructTimer) {
            30 -> getString(R.string.self_destruct_30s)
            60 -> getString(R.string.self_destruct_1m)
            300 -> getString(R.string.self_destruct_5m)
            3600 -> getString(R.string.self_destruct_1h)
            86400 -> getString(R.string.self_destruct_24h)
            else -> "${selfDestructTimer}s"
        }
        val text = "\uD83D\uDD25 ${getString(R.string.self_destruct_set, timerLabel)}"
        val sysMsgId = "sd_timer_${data.roomId}_current"
        val lastTs = chatViewModel.messages.value.lastOrNull()?.timestamp ?: (System.currentTimeMillis() / 1000)
        val sysMsg = Message(
            id = sysMsgId,
            user = "",
            text = text,
            timestamp = lastTs + 1,
            roomId = data.roomId,
        )
        grpcClient.addLocalMessage(sysMsg)
    }

    private fun setupKeyboardHandling() {
        val root = findViewById<View>(android.R.id.content)
        val bottomPanel = findViewById<View>(R.id.bottomPanel)
        val bottomPanelContent = findViewById<View>(R.id.bottomPanelContent)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            bottomPanel.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = if (isImeVisible) imeInsets.bottom else systemBars.bottom
            }
            bottomPanelContent.updatePadding(bottom = 4.dpToPx())
            insets
        }
    }

    private fun retryMessage(message: Message) {
        chatViewModel.retryMessage(message)
    }

    private fun scrollToMessage(messageId: String) {
        val pos = adapter.currentList.indexOfFirst { it.id == messageId }
        if (pos != -1) {
            messagesRecyclerView.smoothScrollToPosition(pos)
        }
    }

    private fun isNearBottom(): Boolean {
        val layoutManager = messagesRecyclerView.layoutManager as LinearLayoutManager
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        return lastVisible >= adapter.itemCount - 3
    }

    private fun joinConference() {
        val d = newChatViewModel.intentData.value
        val intent = Intent(this, ConferenceLobbyActivity::class.java).apply {
            putExtra("ROOM_ID", d.roomId)
            putExtra("CHAT_NAME", d.chatName)
            putExtra("PARTICIPANTS", d.participantsJson)
        }
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        try {
            val theme = ThemeStore.currentTheme()
            val iconColor = theme.onPrimaryColor.toColorInt()
            toolbarDelegate.toolbar.overflowIcon?.let {
                val wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(it)
                androidx.core.graphics.drawable.DrawableCompat.setTint(wrapped, iconColor)
                toolbarDelegate.toolbar.overflowIcon = wrapped
            }
        } catch (_: Exception) {}
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val inSelection = selectionDelegate.isInSelectionMode()
        val inSearch = searchDelegate.isVisible()
        val callItem = menu.findItem(R.id.action_video_call)
        val searchItem = menu.findItem(R.id.action_search)
        val conferenceItem = menu.findItem(R.id.action_conference)
        val pinnedItem = menu.findItem(R.id.action_pinned_messages)
        val muteItem = menu.findItem(R.id.action_mute_chat)
        val selfDestructItem = menu.findItem(R.id.action_self_destruct)
        val clearItem = menu.findItem(R.id.action_clear_history)
        val deleteItem = menu.findItem(R.id.action_delete_chat)
        val isSavedMessages = data.roomId.startsWith("saved_messages_")
        callItem?.isVisible = !inSelection && !inSearch && data.isDirect && !isSavedMessages && !data.isSecret
        conferenceItem?.isVisible = false
        searchItem?.isVisible = !inSelection && !inSearch
        pinnedItem?.isVisible = !inSelection && !inSearch && chatViewModel.pinnedMessageIds.value.isNotEmpty()
        muteItem?.isVisible = !inSelection && !inSearch && !isSavedMessages
        muteItem?.title = if (isChatMuted) getString(R.string.unmute_chat) else getString(R.string.mute_chat)
        selfDestructItem?.isVisible = !inSelection && !inSearch && !isSavedMessages
        selfDestructItem?.title = getSelfDestructLabel(selfDestructTimer)
        clearItem?.isVisible = !inSelection && !inSearch && !isSavedMessages
        deleteItem?.isVisible = !inSelection && !inSearch && !isSavedMessages
        return true
    }

    private fun getSelfDestructLabel(timer: Int): String {
        return when (timer) {
            0 -> getString(R.string.self_destruct_timer)
            30 -> getString(R.string.self_destruct_active, getString(R.string.self_destruct_30s))
            60 -> getString(R.string.self_destruct_active, getString(R.string.self_destruct_1m))
            300 -> getString(R.string.self_destruct_active, getString(R.string.self_destruct_5m))
            3600 -> getString(R.string.self_destruct_active, getString(R.string.self_destruct_1h))
            86400 -> getString(R.string.self_destruct_active, getString(R.string.self_destruct_24h))
            else -> getString(R.string.self_destruct_timer)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_video_call -> {
                val other = toolbarDelegate.getOtherParticipant()
                if (!other.isNullOrEmpty()) {
                    val otherUserProto = GrpcClient.allUsers.value.firstOrNull { it.username == other }
                    val otherUserId = otherUserProto?.userId ?: other
                    val avatarUrl = otherUserProto?.avatarUrl?.takeIf { it.isNotEmpty() }
                    val lastSeenAt = otherUserProto?.lastSeenAt
                    PreCallSheet(
                        activity = this,
                        username = other,
                        userId = otherUserId,
                        avatarUrl = avatarUrl,
                        lastSeenAt = lastSeenAt,
                        onAudioCall = { userId, name ->
                            lavender.client.android.data.calls.CallManager.initiateCall(name)
                            lavender.client.android.data.calls.CallNavigator.startCall(this, userId, name, isVideo = false)
                        },
                        onVideoCall = { userId, name ->
                            lavender.client.android.data.calls.CallManager.initiateCall(name)
                            lavender.client.android.data.calls.CallNavigator.startCall(this, userId, name, isVideo = true)
                        }
                    ).show()
                }
                true
            }
            R.id.action_pinned_messages -> {
                showPinnedMessagesSheet()
                true
            }
            R.id.action_search -> {
                searchDelegate.show()
                true
            }
            R.id.action_mute_chat -> {
                val newMuted = !isChatMuted
                val roomId = data.roomId
                if (roomId.isNotEmpty()) {
                    GrpcClient.setMutedChat(roomId, newMuted) { success ->
                        runOnUiThread {
                            if (success) {
                                isChatMuted = newMuted
                                invalidateOptionsMenu()
                                lavender.client.android.ui.chatlist.ChatListSharedState.pendingMuteUpdate = Pair(roomId, newMuted)
                                Toast.makeText(this, if (newMuted) getString(R.string.muted) else getString(R.string.unmuted), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, getString(R.string.error_colon, "Failed to update mute"), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                true
            }
            R.id.action_self_destruct -> {
                showSelfDestructTimerPicker()
                true
            }
            R.id.action_clear_history -> {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.clear_history)
                    .setMessage(R.string.clear_history_confirm)
                    .setPositiveButton(R.string.clear) { _, _ ->
                        grpcClient.clearRoomHistory(data.roomId) { success ->
                            runOnUiThread {
                                if (success) {
                                    Toast.makeText(this, getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, getString(R.string.failed_to_clear_history), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            R.id.action_delete_chat -> {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.delete_chat)
                    .setMessage(R.string.delete_chat_confirm)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        grpcClient.deleteChat(data.roomId, data.username) { success, _ ->
                            runOnUiThread {
                                if (success) {
                                    finish()
                                } else {
                                    Toast.makeText(this, getString(R.string.failed_to_delete_chat), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showPinnedMessagesSheet() {
        val sheet = PinnedMessagesSheet(this, grpcClient)
        sheet.show()
    }

    private fun showSelfDestructTimerPicker() {
        val options = listOf(0, 30, 60, 300, 3600, 86400)
        val labels = arrayOf(
            getString(R.string.self_destruct_off),
            getString(R.string.self_destruct_30s),
            getString(R.string.self_destruct_1m),
            getString(R.string.self_destruct_5m),
            getString(R.string.self_destruct_1h),
            getString(R.string.self_destruct_24h)
        )
        val currentIdx = options.indexOf(selfDestructTimer).coerceAtLeast(0)

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.self_destruct_timer)
            .setSingleChoiceItems(labels, currentIdx) { dialog, which ->
                val timerSeconds = options[which]
                grpcClient.setSelfDestructTimer(data.roomId, timerSeconds) { success, _ ->
                    runOnUiThread {
                        if (success) {
                            selfDestructTimer = timerSeconds
                            invalidateOptionsMenu()
                            injectSelfDestructMessageIfNeeded()
                            val msg = if (timerSeconds == 0) getString(R.string.self_destruct_disabled)
                            else getString(R.string.self_destruct_set, labels[which])
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        RealGrpcClient.isAppInBackground = false
        if (data.roomId.isNotEmpty()) {
            grpcClient.markRead(data.roomId, data.username)
            newChatViewModel.dismissNotifications(data.roomId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        newChatViewModel.parseIntent(intent, null)
        val roomId = intent.getStringExtra("ROOM_ID") ?: intent.getStringExtra("room_id") ?: ""
        if (roomId.isNotEmpty()) {
            newChatViewModel.dismissNotifications(roomId)
            chatViewModel.switchRoom(roomId)
            newChatViewModel.setRoomId(roomId)
            
            // Re-setup delegates for the new chat immediately
            setupDelegates()
        }
    }

    override fun onPause() {
        super.onPause()
        RealGrpcClient.isAppInBackground = true
    }

    companion object {
        private const val TAG = "NewChatActivity"
    }
}
