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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import lavender.client.android.data.calls.CallMessageHelper
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.data.session.SessionManager
import lavender.client.android.ui.adapter.MessageAdapter
import lavender.client.android.ui.adapter.MessageSwipeController
import lavender.client.android.ui.chat.ChatMetadataState
import lavender.client.android.ui.chat.ChatViewModel
import lavender.client.android.ui.chat.ChatViewModelFactory
import lavender.client.android.ui.chat.NewChatViewModel
import lavender.client.android.ui.chat.message.ChatE2EEDelegate
import lavender.client.android.ui.chat.message.ChatInputDelegate
import lavender.client.android.ui.chat.message.ChatMessageMenuDelegate
import lavender.client.android.ui.chat.message.ChatSearchDelegate
import lavender.client.android.ui.chat.message.ChatSelectionDelegate
import lavender.client.android.ui.chat.message.ChatToolbarDelegate
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeUi
import java.util.Locale

class NewChatActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    companion object {
        private const val TAG = "NewChatActivity"
    }

    private val grpcClient = GrpcClient
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var newChatViewModel: NewChatViewModel

    private lateinit var toolbarDelegate: ChatToolbarDelegate
    private lateinit var inputDelegate: ChatInputDelegate
    private lateinit var selectionDelegate: ChatSelectionDelegate
    private lateinit var searchDelegate: ChatSearchDelegate
    private lateinit var e2eeDelegate: ChatE2EEDelegate
    private lateinit var messageMenuDelegate: ChatMessageMenuDelegate

    private var lastMessageCount = 0
    private var shouldScrollToBottom = false

    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private lateinit var replyPreview: View
    private lateinit var replyUser: TextView
    private lateinit var replyText: TextView
    private lateinit var cancelReply: android.widget.ImageButton
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var historyLoadingProgress: ProgressBar

    private var isChatMuted = false
    private var selfDestructTimer = 0

    private val data get() = newChatViewModel.intentData.value

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

        chatViewModel.fetchChatMetadata(data.username, data.roomId, data.isDirect, data.participantsJson, data.chatName) { meta ->
            try {
                lifecycleScope.launch {
                    if (isFinishing || isDestroyed) return@launch
                    newChatViewModel.updateMetadata(lavender.client.android.ui.chat.ChatMetadataState(
                        chatName = meta.chatName, isDirect = meta.isDirect, chatType = meta.chatType,
                        participantsJson = meta.participantsJson, creator = meta.creator,
                        avatarUrl = meta.avatarUrl, fullAvatarUrl = meta.fullAvatarUrl
                    ))
                    val m = newChatViewModel.metadata.value
                    toolbarDelegate.configure(data.roomId, data.username, m.chatName, m.isDirect, m.chatType, m.participantsJson, m.creator, m.avatarUrl, m.fullAvatarUrl, data.isSecret)
                    toolbarDelegate.setup()
                    toolbarDelegate.refreshSubtitle()
                    adapter.isGroupChat = !m.isDirect; adapter.adminUsername = m.creator
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "fetchChatMetadata callback error: ${e.message}", e)
            }
        }

        chatViewModel.switchRoom(data.roomId)
        newChatViewModel.dismissNotifications(data.roomId)

        SessionManager.updateDeviceInfo(this)
        chatViewModel.startChatV2(data.roomId) { _ ->
            chatViewModel.markRead(data.username)
        }
        chatViewModel.markRead(data.username)
        chatViewModel.ensureUserIdSet(this) { loadDraft() }
        registerMarkReadReceiver()

        lifecycleScope.launch {
            SessionManager.logoutEvent.collect { finish() }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                grpcClient.chatDeletedEvent.collect { deletedChatId ->
                    if (deletedChatId == data.roomId) finish()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    selectionDelegate.isInSelectionMode() -> selectionDelegate.hideSelectionToolbar()
                    searchDelegate.isVisible() -> searchDelegate.hide()
                    else -> finish()
                }
            }
        })
    }

    private fun initDelegates() {
        toolbarDelegate = ChatToolbarDelegate(this, grpcClient); toolbarDelegate.initViews()
        inputDelegate = ChatInputDelegate(this, grpcClient); inputDelegate.initViews()
        selectionDelegate = ChatSelectionDelegate(this, grpcClient); selectionDelegate.initViews()
        searchDelegate = ChatSearchDelegate(this, lifecycleScope); searchDelegate.initViews()
        e2eeDelegate = ChatE2EEDelegate(this, grpcClient)
        messageMenuDelegate = ChatMessageMenuDelegate(this, grpcClient)
    }

    private fun initSharedViews() {
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        replyPreview = findViewById(R.id.replyPreview)
        replyUser = findViewById(R.id.replyUser)
        replyText = findViewById(R.id.replyText)
        cancelReply = findViewById(R.id.cancelReply)
        historyLoadingProgress = findViewById(R.id.historyLoadingProgress)
    }

    private fun setupDelegates() {
        val m = newChatViewModel.metadata.value
        toolbarDelegate.configure(data.roomId, data.username, m.chatName, m.isDirect, m.chatType, m.participantsJson, m.creator, m.avatarUrl, m.fullAvatarUrl, data.isSecret)
        toolbarDelegate.setup()

        inputDelegate.configure(data.roomId, data.username, m.isDirect, m.participantsJson, data.isSecret)
        inputDelegate.onSendMessage = { text, imageUrl -> sendMessage(text, imageUrl) }
        inputDelegate.onStickerSent = { shouldScrollToBottom = true }
        inputDelegate.onTypingSignal = { isTyping -> grpcClient.sendTypingSignal(data.username, isTyping) }
        inputDelegate.onAudioRecord = { file, dur -> chatViewModel.uploadAudio(this, file, dur, data.username) { msg -> lifecycleScope.launch { Toast.makeText(this@NewChatActivity, msg, Toast.LENGTH_SHORT).show() } } }
        inputDelegate.onReplyChanged = { m ->
            if (m != null) {
                replyPreview.isVisible = true; replyUser.text = m.user
                replyText.text = if (m.imageUrl.isNotEmpty()) getString(R.string.photo) else m.text
                inputDelegate.messageInput.requestFocus()
            } else { replyPreview.isVisible = false }
        }
        cancelReply.setOnClickListener { inputDelegate.hideReplyPreview() }
        inputDelegate.setupListeners()

        selectionDelegate.configure(data.roomId, data.username)
        selectionDelegate.setAdapter(adapter)

        searchDelegate.roomId = data.roomId
        selectionDelegate.onSelectionModeChanged = { invalidateOptionsMenu() }
        selectionDelegate.getToolbarDelegate = { toolbarDelegate }
        selectionDelegate.onReplySelected = { m -> inputDelegate.showReplyPreview(m) }
        selectionDelegate.setupListeners()

        searchDelegate.setAdapter(adapter)
        searchDelegate.getToolbarDelegate = { toolbarDelegate }
        searchDelegate.setupListeners()

        e2eeDelegate.configure(data.roomId, data.isSecret, toolbarDelegate.toolbarSubtitle)
        e2eeDelegate.onKeyExchangeStart = {
            toolbarDelegate.isE2eeInProgress = true
            toolbarDelegate.refreshSubtitle()
        }
        e2eeDelegate.onKeyExchangeComplete = { success ->
            toolbarDelegate.isE2eeInProgress = false
            toolbarDelegate.refreshSubtitle()
            if (success) {
                inputDelegate.setSecretState(true)
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val db = lavender.client.android.data.db.AppDatabase.getDatabase(this@NewChatActivity)
                        db.messageDao().clearRoom(data.roomId)
                    } catch (e: Exception) { android.util.Log.w(TAG, "clearRoom failed: ${e.message}") }
                    withContext(Dispatchers.Main) {
                        grpcClient.clearMessages()
                        chatViewModel.loadHistory()
                    }
                }
            }
        }
        if (data.isSecret) e2eeDelegate.initE2EE()

        messageMenuDelegate.configure(data.username)
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
            currentUsername = data.username, isGroupChat = !m.isDirect, adminUsername = m.creator,
            onMessageClick = { message ->
                if (message.stickerUrl.isNotEmpty()) {
                    // Open sticker fullscreen
                    val intent = android.content.Intent(this, FullScreenImageActivity::class.java).apply {
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
            onRetrySendMessage = { retryMessage(it) },
            onReplyQuoteClick = { repliedToMessageId -> scrollToMessage(repliedToMessageId) }
        )
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
                        val isNewFromOther = hasNewMessages && roomMessages.lastOrNull()?.user != data.username
                        adapter.submitList(roomMessages) {
                            if (shouldScrollToBottom || (isNewFromOther && isNearBottom())) {
                                shouldScrollToBottom = false
                                messagesRecyclerView.post { messagesRecyclerView.scrollToPosition(roomMessages.size - 1) }
                            }
                        }
                        if (hasNewMessages && roomMessages.any { it.user != data.username && !it.isRead }) chatViewModel.markRead(data.username)
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
                grpcClient.selfDestructTimer.collect { timer ->
                    if (timer != selfDestructTimer) {
                        selfDestructTimer = timer
                        invalidateOptionsMenu()
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    kotlinx.coroutines.delay(60.seconds)
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
                        if (m.avatarUrl.isEmpty() && m.participantsJson == "[]" && data.roomId.startsWith("favorites_").not()) {
                            chatViewModel.fetchChatMetadata(data.username, data.roomId, data.isDirect, data.participantsJson, data.chatName) { meta ->
                                lifecycleScope.launch {
                                    if (isFinishing || isDestroyed) return@launch
                                    newChatViewModel.updateMetadata(ChatMetadataState(
                                        chatName = meta.chatName, isDirect = meta.isDirect, chatType = meta.chatType,
                                        participantsJson = meta.participantsJson, creator = meta.creator,
                                        avatarUrl = meta.avatarUrl, fullAvatarUrl = meta.fullAvatarUrl
                                    ))
                                    val updated = newChatViewModel.metadata.value
                                    toolbarDelegate.configure(data.roomId, data.username, updated.chatName, updated.isDirect, updated.chatType, updated.participantsJson, updated.creator, updated.avatarUrl, updated.fullAvatarUrl, data.isSecret)
                                    toolbarDelegate.setup()
                                    toolbarDelegate.refreshSubtitle()
                                    adapter.isGroupChat = !updated.isDirect; adapter.adminUsername = updated.creator
                                }
                            }
                        }
                    }
                }
            }
        }
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

    private fun sendMessage(text: String, imageUrl: String) {
        if (data.isSecret && e2eeDelegate.isKeyExchanged()) {
            chatViewModel.sendMessageWithE2EE(text, { t, cb -> e2eeDelegate.encryptAndSend(t, cb) },
                onSuccess = { inputDelegate.resetInput() },
                onError = { Toast.makeText(this, getString(R.string.e2ee_encryption_failed), Toast.LENGTH_SHORT).show() }
            )
            return
        }
        val et = when { text.isEmpty() && imageUrl.isEmpty() -> "Message"; imageUrl.isNotEmpty() && text.isEmpty() -> ""; else -> text }
        val msg = Message(
            id = java.util.UUID.randomUUID().toString(), user = data.username, text = et,
            timestamp = System.currentTimeMillis(), roomId = data.roomId, imageUrl = imageUrl,
            repliedToMessageId = inputDelegate.getReplyingTo()?.id ?: "",
            repliedToUser = inputDelegate.getReplyingTo()?.user ?: "",
            repliedToText = inputDelegate.getReplyingTo()?.text ?: "",
            userId = grpcClient.getUserId() ?: "", isSent = false
        )
        shouldScrollToBottom = true
        chatViewModel.sendMessage(msg)
        inputDelegate.resetInput()
    }

    private fun joinConference() {
        if (data.roomId.isEmpty()) return
        lavender.client.android.data.calls.CallManager.joinConference(data.roomId)
        lavender.client.android.data.calls.CallNavigator.joinConference(this, data.roomId)
    }

    private fun retryMessage(message: Message) {
        Toast.makeText(this, getString(R.string.checking_server), Toast.LENGTH_SHORT).show()
        chatViewModel.retryMessage(message)
    }

    private fun isNearBottom(): Boolean {
        val lm = messagesRecyclerView.layoutManager as? LinearLayoutManager ?: return true
        val lastVisible = lm.findLastCompletelyVisibleItemPosition()
        val total = lm.itemCount
        return lastVisible >= total - 3
    }

    private fun loadDraft() {
        if (data.roomId.isEmpty() || data.username.isEmpty() || grpcClient.getUserId() == null) return
        chatViewModel.getDraft { dt, rmi, ru, rt, hd ->
            lifecycleScope.launch {
                if (hd && (dt.isNotEmpty() || rmi.isNotEmpty())) {
                    if (dt.isNotEmpty()) inputDelegate.setDraftText(dt)
                    if (rmi.isNotEmpty()) inputDelegate.showReplyPreview(Message(id = rmi, user = ru, text = rt, timestamp = System.currentTimeMillis(), roomId = data.roomId))
                }
            }
        }
    }

    private fun saveDraft() {
        if (data.roomId.isEmpty() || data.username.isEmpty()) return
        if (grpcClient.getUserId() == null) { chatViewModel.ensureUserIdSet(this) { saveDraft() }; return }
        val dt = inputDelegate.getDraftText()
        val replyingTo = inputDelegate.getReplyingTo()
        if (dt.isNotEmpty() || replyingTo != null) {
            chatViewModel.saveDraft(dt, replyingTo?.id ?: "", replyingTo?.user ?: "", replyingTo?.text ?: "")
        } else if (grpcClient.getUserId() != null) {
            chatViewModel.deleteDraft()
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeStore.refresh(this, data.username)
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false
        if (lavender.client.android.data.auth.AuthManager.isJwtAuthenticated(this)
            && lavender.client.android.data.auth.AuthManager.needsRefresh(this)) {
            lifecycleScope.launch(Dispatchers.IO) { SessionManager.ensureFreshToken(this@NewChatActivity) }
        }
        if (grpcClient.connectionStatus.value == ConnectionStatus.READY) {
            grpcClient.loadUsers()
        }
        if (newChatViewModel.shouldForceReconnect()) {
            newChatViewModel.forceReconnect()
        }
        chatViewModel.fetchChatMetadata(data.username, data.roomId, data.isDirect, data.participantsJson, data.chatName) { meta ->
            try {
                lifecycleScope.launch {
                    if (isFinishing || isDestroyed) return@launch
                    newChatViewModel.updateMetadata(
                        ChatMetadataState(
                            chatName = meta.chatName, isDirect = meta.isDirect, chatType = meta.chatType,
                            participantsJson = meta.participantsJson, creator = meta.creator,
                            avatarUrl = meta.avatarUrl, fullAvatarUrl = meta.fullAvatarUrl
                        )
                    )
                    val m = newChatViewModel.metadata.value
                    toolbarDelegate.configure(data.roomId, data.username, m.chatName, m.isDirect, m.chatType, m.participantsJson, m.creator, m.avatarUrl, m.fullAvatarUrl, data.isSecret)
                    toolbarDelegate.setup()
                    toolbarDelegate.refreshSubtitle()
                    adapter.isGroupChat = !m.isDirect; adapter.adminUsername = m.creator
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "fetchChatMetadata callback error: ${e.message}", e)
            }
        }
        chatViewModel.loadPinnedMessages(this)
    }

    override fun onPause() {
        super.onPause()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = true
        inputDelegate.clearTypingState()
        saveDraft()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val oldRoomId = data.roomId
        newChatViewModel.parseIntent(intent, null)
        val newRoomId = data.roomId
        if (newRoomId.isEmpty()) return

        newChatViewModel.dismissNotifications(newRoomId)

        if (newRoomId == oldRoomId) return
        newChatViewModel.setRoomId(newRoomId)
        grpcClient.clearMessages()
        chatViewModel.switchRoom(newRoomId)
        chatViewModel.startChatV2(newRoomId) { _ -> chatViewModel.markRead(data.username) }
        chatViewModel.markRead(data.username)

        val m = newChatViewModel.metadata.value
        if (m.chatName.isNotEmpty()) {
            toolbarDelegate.configure(newRoomId, data.username, m.chatName, m.isDirect, m.chatType, m.participantsJson, m.creator, m.avatarUrl, m.fullAvatarUrl, data.isSecret)
            toolbarDelegate.setup()
        }
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
        val isFavorites = data.roomId.startsWith("favorites_")
        callItem?.isVisible = !inSelection && !inSearch && data.isDirect && !isFavorites && !data.isSecret
        conferenceItem?.isVisible = false
        searchItem?.isVisible = !inSelection && !inSearch
        pinnedItem?.isVisible = !inSelection && !inSearch && chatViewModel.pinnedMessageIds.value.isNotEmpty()
        muteItem?.isVisible = !inSelection && !inSearch && !isFavorites
        muteItem?.setTitle(if (isChatMuted) R.string.unmute_chat else R.string.mute_chat)
        selfDestructItem?.isVisible = !inSelection && !inSearch && !isFavorites
        selfDestructItem?.setTitle(getSelfDestructLabel(selfDestructTimer))
        clearItem?.isVisible = !inSelection && !inSearch && !isFavorites
        deleteItem?.isVisible = !inSelection && !inSearch && !isFavorites
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_video_call -> {
                val other = toolbarDelegate.getOtherParticipant()
                if (!other.isNullOrEmpty()) {
                    val otherUser = GrpcClient.allUsers.value.firstOrNull { it.username == other }
                    val otherUserId = otherUser?.userId ?: other
                    val avatarUrl = otherUser?.avatarUrl?.takeIf { it.isNotEmpty() }
                    val lastSeenAt = otherUser?.lastSeenAt
                    lavender.client.android.ui.chat.message.PreCallSheet(
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
                                // Update chat list state so mute icon shows when navigating back
                                lavender.client.android.ui.chatlist.ChatListSharedState.pendingMuteUpdate = Pair(roomId, newMuted)
                                Toast.makeText(this, if (newMuted) getString(R.string.muted) else getString(R.string.unmuted), Toast.LENGTH_SHORT).show()
                            } else {
                                android.util.Log.e("NewChatActivity", "setMutedChat failed for room=$roomId muted=$newMuted")
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
                val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.clear_history)
                    .setMessage(R.string.clear_history_confirm)
                    .setPositiveButton(R.string.clear) { _, _ ->
                        chatViewModel.clearRoomMessages(this)
                        chatViewModel.loadHistory()
                        Toast.makeText(this, getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.cancel_dialog, null)
                    .create()
                dlg.show()
                applyDialogButtonColors(dlg)
                true
            }
            R.id.action_delete_chat -> {
                val chatName = newChatViewModel.metadata.value.chatName.ifEmpty { data.chatName }
                val message = if (data.isDirect) {
                    getString(R.string.delete_chat_confirmation)
                } else {
                    getString(R.string.delete_group) + ": \"$chatName\"?"
                }
                val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.delete_chat)
                    .setMessage(message)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        val username = SessionManager.session.value.username
                        GrpcClient.deleteChat(data.roomId, username) { success, errorMsg ->
                            runOnUiThread {
                                if (success) {
                                    Toast.makeText(this, getString(R.string.chat_deleted), Toast.LENGTH_SHORT).show()
                                    val intent = android.content.Intent(this, lavender.client.android.ui.chatlist.ChatListActivity::class.java).apply {
                                        flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    }
                                    startActivity(intent)
                                    finish()
                                } else {
                                    Toast.makeText(this, getString(R.string.error_colon, errorMsg ?: "Failed"), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton(R.string.cancel_dialog, null)
                    .create()
                dlg.show()
                applyDialogButtonColors(dlg)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun handleIncomingE2EEMessage(msg: Message) {
        if (!msg.isE2EE || msg.e2eePayload.isEmpty()) return
        val decrypted = e2eeDelegate.decryptMessage(msg)
        if (decrypted != null) {
            val decryptedMsg = msg.copy(text = decrypted, isE2EE = false)
            lifecycleScope.launch {
                grpcClient.addLocalMessage(decryptedMsg)
            }
        }
    }

    private fun showPinnedMessagesSheet() {
        lifecycleScope.launch {
            val pinnedMessages = withContext(Dispatchers.IO) {
                try {
                    GrpcClient.getPinnedMessages(data.roomId)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            if (pinnedMessages.isEmpty()) {
                Toast.makeText(this@NewChatActivity, getString(R.string.no_pinned_messages), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val sheet = lavender.client.android.ui.widget.StandardBottomSheet(this@NewChatActivity, R.layout.dialog_pinned_messages)
            sheet.setTitle(getString(R.string.pinned_messages))
            val rv = sheet.dialog?.findViewById<RecyclerView>(R.id.recyclerView)
            rv?.layoutManager = LinearLayoutManager(this@NewChatActivity)
            lateinit var pinnedMsgAdapter: lavender.client.android.ui.adapter.PinnedMessageAdapter
            pinnedMsgAdapter = lavender.client.android.ui.adapter.PinnedMessageAdapter { msg ->
                sheet.dismiss()
                val pos = pinnedMsgAdapter.currentList.indexOfFirst { item -> item.id == msg.id }
                if (pos != -1) {
                    messagesRecyclerView.scrollToPosition(pos)
                }
            }
            pinnedMsgAdapter.submitList(pinnedMessages)
            rv?.adapter = pinnedMsgAdapter
            sheet.show()
        }
    }

    private fun scrollToMessage(messageId: String) {
        val pos = adapter.currentList.indexOfFirst { it.id == messageId }
        if (pos != -1) {
            messagesRecyclerView.scrollToPosition(pos)
            messagesRecyclerView.post {
                val holder = messagesRecyclerView.findViewHolderForAdapterPosition(pos)
                holder?.itemView?.let { view ->
                    view.setBackgroundColor(android.graphics.Color.parseColor("#33FFFFFF"))
                    view.postDelayed({ view.setBackgroundColor(android.graphics.Color.TRANSPARENT) }, 1500)
                }
            }
        }
    }

    private var markReadReceiver: android.content.BroadcastReceiver? = null

    @Suppress("UnprotectedRegisterReceiver")
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerMarkReadReceiver() {
        markReadReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val roomId = intent.getStringExtra("room_id") ?: return
                if (roomId == data.roomId) {
                    chatViewModel.forceLoadHistory()
                }
            }
        }
        val filter = android.content.IntentFilter(lavender.client.android.data.fcm.NotificationMarkReadReceiver.ACTION_CHAT_MARKED_READ)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(markReadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(markReadReceiver, filter)
        }
    }

    private fun showSelfDestructTimerPicker() {
        val options = intArrayOf(0, 30, 60, 300, 3600, 86400)
        val labels = arrayOf(
            getString(R.string.self_destruct_off),
            getString(R.string.self_destruct_30s),
            getString(R.string.self_destruct_1m),
            getString(R.string.self_destruct_5m),
            getString(R.string.self_destruct_1h),
            getString(R.string.self_destruct_24h)
        )
        val checkedIndex = options.indexOf(selfDestructTimer).coerceAtLeast(0)
        val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.self_destruct_timer)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                val timerSeconds = options[which]
                GrpcClient.setSelfDestructTimer(data.roomId, timerSeconds) { success, error ->
                    runOnUiThread {
                        if (success) {
                            selfDestructTimer = timerSeconds
                            invalidateOptionsMenu()
                            val msg = if (timerSeconds == 0) getString(R.string.self_destruct_disabled)
                            else getString(R.string.self_destruct_set, labels[which])
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, getString(R.string.error_colon, error ?: "Failed"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel_dialog, null)
            .create()
        dlg.show()
        applyDialogButtonColors(dlg)
    }

    private fun getSelfDestructLabel(seconds: Int): String = when (seconds) {
        30 -> getString(R.string.self_destruct_30s)
        60 -> getString(R.string.self_destruct_1m)
        300 -> getString(R.string.self_destruct_5m)
        3600 -> getString(R.string.self_destruct_1h)
        86400 -> getString(R.string.self_destruct_24h)
        else -> getString(R.string.self_destruct_timer)
    }

    private fun applyDialogButtonColors(dialog: androidx.appcompat.app.AlertDialog) {
        try {
            val theme = ThemeStore.currentTheme()
            val primaryColor = theme.primaryColor.toColorInt()
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(primaryColor)
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(primaryColor)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        markReadReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        e2eeDelegate.cancelPendingRetries()
        super.onDestroy()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
