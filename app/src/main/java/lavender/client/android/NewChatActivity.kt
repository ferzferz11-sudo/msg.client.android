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
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.data.session.SessionManager
import lavender.client.android.ui.adapter.MessageAdapter
import lavender.client.android.ui.adapter.MessageSwipeController
import lavender.client.android.ui.chat.ChatViewModel
import lavender.client.android.ui.chat.ChatViewModelFactory
import lavender.client.android.ui.chat.message.ChatE2EEDelegate
import lavender.client.android.ui.chat.message.ChatInputDelegate
import lavender.client.android.ui.chat.message.ChatMessageMenuDelegate
import lavender.client.android.ui.chat.message.ChatSearchDelegate
import lavender.client.android.ui.chat.message.ChatSelectionDelegate
import lavender.client.android.ui.chat.message.ChatToolbarDelegate
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeUi
import java.util.Locale

/**
 * Chat screen — thin Activity delegating to delegates + ChatViewModel for business logic.
 */
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
    private lateinit var viewModel: ChatViewModel

    private lateinit var toolbarDelegate: ChatToolbarDelegate
    private lateinit var inputDelegate: ChatInputDelegate
    private lateinit var selectionDelegate: ChatSelectionDelegate
    private lateinit var searchDelegate: ChatSearchDelegate
    private lateinit var e2eeDelegate: ChatE2EEDelegate
    private lateinit var messageMenuDelegate: ChatMessageMenuDelegate

    private var username = ""
    private var password = ""
    private var roomId = ""
    private var chatName = ""
    private var isDirect = false
    private var chatType = "group"
    private var participantsJson = "[]"
    private var creator = ""
    private var chatAvatarUrl = ""
    private var chatFullAvatarUrl = ""
    private var isSecret = false
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        } catch (e: Exception) {
            android.util.Log.e("NewChatActivity", "setDecorFitsSystemWindows failed: ${e.message}")
        }
        setContentView(R.layout.activity_new_chat)

        loadDataFromIntent()

        if (grpcClient.connectionStatus.value != ConnectionStatus.READY) {
            val serverAddress = intent.getStringExtra("SERVER_ADDRESS")
                ?: getSharedPreferences("lavender_prefs", MODE_PRIVATE).getString("server_address", "")
            if (!serverAddress.isNullOrEmpty()) {
                val parts = serverAddress.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
                grpcClient.connect(host, false, port, this)
            }
        }

        grpcClient.setRoomId(roomId)

        try {
            initDelegates()
            initSharedViews()
        } catch (e: Exception) {
            android.util.Log.e("NewChatActivity", "initDelegates/initSharedViews failed: ${e.message}", e)
            Toast.makeText(this, "Chat init error", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        ThemeUi.bind(this, username)
        setupTheme()
        setupRecyclerView()
        try {
            setupDelegates()
        } catch (e: Exception) {
            android.util.Log.e("NewChatActivity", "setupDelegates failed: ${e.message}", e)
        }
        setupObservers()
        setupKeyboardHandling()

        viewModel.fetchChatMetadata(username, roomId, isDirect, participantsJson, chatName) { meta ->
            try {
                lifecycleScope.launch {
                    if (isFinishing || isDestroyed) return@launch
                    chatName = meta.chatName; isDirect = meta.isDirect; chatType = meta.chatType
                    participantsJson = meta.participantsJson; creator = meta.creator
                    chatAvatarUrl = meta.avatarUrl; chatFullAvatarUrl = meta.fullAvatarUrl
                    toolbarDelegate.configure(roomId, username, chatName, isDirect, chatType, participantsJson, creator, chatAvatarUrl, chatFullAvatarUrl, isSecret)
                    toolbarDelegate.setup()
                    adapter.isGroupChat = !isDirect; adapter.adminUsername = creator
                }
            } catch (e: Exception) {
                android.util.Log.e("NewChatActivity", "fetchChatMetadata callback error: ${e.message}", e)
            }
        }

        viewModel.switchRoom(roomId)

        lavender.client.android.data.fcm.LavenderMessagingService.dismissNotificationsForRoom(this, roomId)

        SessionManager.updateDeviceInfo(this)
        val session = SessionManager.session.value
        viewModel.startChatV2(roomId) { _ ->
            viewModel.markRead(username)
        }
        viewModel.markRead(username)
        viewModel.ensureUserIdSet(this) { loadDraft() }

        lifecycleScope.launch {
            SessionManager.logoutEvent.collect { finish() }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                grpcClient.chatDeletedEvent.collect { deletedChatId ->
                    if (deletedChatId == roomId) finish()
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
        toolbarDelegate.configure(roomId, username, chatName, isDirect, chatType, participantsJson, creator, chatAvatarUrl, chatFullAvatarUrl, isSecret)
        toolbarDelegate.setup()

        inputDelegate.configure(roomId, username, isDirect, participantsJson, isSecret)
        inputDelegate.onSendMessage = { text, imageUrl -> sendMessage(text, imageUrl) }
        inputDelegate.onTypingSignal = { isTyping -> grpcClient.sendTypingSignal(username, isTyping) }
        inputDelegate.onAudioRecord = { file, dur -> viewModel.uploadAudio(this, file, dur, username) { msg -> lifecycleScope.launch { Toast.makeText(this@NewChatActivity, msg, Toast.LENGTH_SHORT).show() } } }
        inputDelegate.onReplyChanged = { m ->
            if (m != null) {
                replyPreview.isVisible = true; replyUser.text = m.user
                replyText.text = if (m.imageUrl.isNotEmpty()) "Photo" else m.text
                inputDelegate.messageInput.requestFocus()
            } else { replyPreview.isVisible = false }
        }
        cancelReply.setOnClickListener { inputDelegate.hideReplyPreview() }
        inputDelegate.setupListeners()

        selectionDelegate.configure(roomId, username)
        selectionDelegate.setAdapter(adapter)

        searchDelegate.roomId = roomId
        selectionDelegate.onSelectionModeChanged = { invalidateOptionsMenu() }
        selectionDelegate.getToolbarDelegate = { toolbarDelegate }
        selectionDelegate.onReplySelected = { m -> inputDelegate.showReplyPreview(m) }
        selectionDelegate.setupListeners()

        searchDelegate.setAdapter(adapter)
        searchDelegate.getToolbarDelegate = { toolbarDelegate }
        searchDelegate.setupListeners()

        e2eeDelegate.configure(roomId, isSecret, toolbarDelegate.toolbarSubtitle)
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
                        db.messageDao().clearRoom(roomId)
                    } catch (_: Exception) {}
                    withContext(Dispatchers.Main) {
                        grpcClient.clearMessages()
                        viewModel.loadHistory()
                    }
                }
            }
        }
        if (isSecret) e2eeDelegate.initE2EE()

        messageMenuDelegate.configure(username)
    }

    private fun setupTheme() {
        try {
            val customTheme = ThemeStore.currentTheme()
            try {
                val pColor = customTheme.primaryColor.toColorInt()
                historyLoadingProgress.indeterminateTintList = ColorStateList.valueOf(pColor)
                swipeRefreshLayout.setColorSchemeColors(pColor)
            } catch (_: Exception) {}
        } catch (e: Exception) {
            android.util.Log.e("NewChatActivity", "setupTheme failed: ${e.message}")
        }

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.forceLoadHistory()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun setupRecyclerView() {
        adapter = MessageAdapter(
            currentUsername = username, isGroupChat = !isDirect, adminUsername = creator,
            onMessageClick = { message ->
                val text = message.text.trim().lowercase()
                val isCall = text.contains("📹") || text.contains("конференция") || text.contains("conference")
                val isEnded = text.contains("завершена") || text.contains("завершен") || text.contains("удалена") || text.contains("удален") || text.contains("ended") || text.contains("deleted")
                if (isCall && !isEnded) joinConference()
                else messageMenuDelegate.showReactionsDialog(message) { m -> inputDelegate.showReplyPreview(m) }
            },
            onSelectionChanged = { if (it > 0) selectionDelegate.showSelectionToolbar(it) else selectionDelegate.hideSelectionToolbar() },
            onMessageLongClick = { selectionDelegate.enterSelectionMode(it) },
            chatId = roomId,
            onRetrySendMessage = { retryMessage(it) }
        )
        messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messagesRecyclerView.adapter = adapter
        selectionDelegate.setAdapter(adapter)

        val swipeController = MessageSwipeController(this) { position, direction ->
            if (direction == ItemTouchHelper.LEFT) { inputDelegate.showReplyPreview(adapter.currentList[position]); adapter.notifyItemChanged(position) }
            else if (direction == ItemTouchHelper.RIGHT) finish()
        }
        ItemTouchHelper(swipeController).attachToRecyclerView(messagesRecyclerView)
    }

    private fun setupObservers() {
        viewModel = ViewModelProvider(this, ChatViewModelFactory(roomId))[ChatViewModel::class.java]

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { roomMessages ->
                    try {
                        val hasNewMessages = roomMessages.size > lastMessageCount
                        val isNewFromOther = hasNewMessages && roomMessages.lastOrNull()?.user != username
                        adapter.submitList(roomMessages) {
                            if (shouldScrollToBottom || (isNewFromOther && isNearBottom())) {
                                shouldScrollToBottom = false
                                messagesRecyclerView.post { messagesRecyclerView.scrollToPosition(roomMessages.size - 1) }
                            }
                        }
                        if (hasNewMessages && roomMessages.any { it.user != username && !it.isRead }) viewModel.markRead(username)
                        lastMessageCount = roomMessages.size
                    } catch (e: Exception) {
                        android.util.Log.e("NewChatActivity", "messages.collect error: ${e.message}", e)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { loading ->
                    historyLoadingProgress.isVisible = loading && adapter.currentList.isEmpty()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(grpcClient.users, grpcClient.connectionStatus, grpcClient.typingUsers, grpcClient.allUsers) { onlineUsers, status, typingMap, _ ->
                    val currentUserId = grpcClient.getUserId() ?: ""
                    val currentTypists = typingMap[roomId]?.filter { it != username && it != currentUserId } ?: emptyList()
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
                            viewModel.syncChatListIfNeeded(this@NewChatActivity)
                            if (adapter.currentList.isEmpty()) {
                                shouldScrollToBottom = true
                            }
                            viewModel.loadHistory()
                            viewModel.loadPinnedMessages(this@NewChatActivity)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("NewChatActivity", "Connection observer error: ${e.message}", e)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pinnedMessageIds.collect { pinnedIds ->
                    selectionDelegate.setPinnedMessageIds(pinnedIds)
                    adapter.updatePinnedMessages(pinnedIds)
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

    private fun loadDataFromIntent() {
        roomId = intent.getStringExtra("ROOM_ID") ?: intent.getStringExtra("roomId") ?: (if (roomId.isEmpty()) "general" else roomId)
        val incomingUser = intent.getStringExtra("USERNAME")
        val incomingPass = intent.getStringExtra("PASSWORD")
        if (!incomingUser.isNullOrEmpty()) username = incomingUser
        if (!incomingPass.isNullOrEmpty()) password = incomingPass
        if (username.isEmpty() || password.isEmpty()) {
            val session = SessionManager.session.value
            if (session.isLoggedIn) { username = session.username; password = session.password }
            else {
                val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                username = prefs.getString("username", "") ?: ""; password = prefs.getString("password", "") ?: ""
            }
        }
        intent.getStringExtra("CHAT_NAME")?.let { chatName = it }
        isDirect = intent.getBooleanExtra("IS_DIRECT", isDirect)
        chatType = intent.getStringExtra("CHAT_TYPE") ?: (if (isDirect) "direct" else "group")
        intent.getStringExtra("PARTICIPANTS")?.let { participantsJson = it }
        intent.getStringExtra("CREATOR")?.let { creator = it }
        intent.getStringExtra("AVATAR_URL")?.let { chatAvatarUrl = it }
        intent.getStringExtra("FULL_AVATAR_URL")?.let { chatFullAvatarUrl = it }
        isSecret = intent.getStringExtra("IS_SECRET") == "true"
        if (isSecret) chatType = "secret"
    }

    private fun sendMessage(text: String, imageUrl: String) {
        if (isSecret && e2eeDelegate.isKeyExchanged()) {
            viewModel.sendMessageWithE2EE(text, { t, cb -> e2eeDelegate.encryptAndSend(t, cb) },
                onSuccess = { inputDelegate.resetInput() },
                onError = { Toast.makeText(this, "E2EE encryption failed", Toast.LENGTH_SHORT).show() }
            )
            return
        }
        val et = when { text.isEmpty() && imageUrl.isEmpty() -> "Message"; imageUrl.isNotEmpty() && text.isEmpty() -> ""; else -> text }
        val msg = Message(
            id = java.util.UUID.randomUUID().toString(), user = username, text = et,
            timestamp = System.currentTimeMillis(), roomId = roomId, imageUrl = imageUrl,
            repliedToMessageId = inputDelegate.getReplyingTo()?.id ?: "",
            repliedToUser = inputDelegate.getReplyingTo()?.user ?: "",
            repliedToText = inputDelegate.getReplyingTo()?.text ?: "",
            userId = grpcClient.getUserId() ?: "", isSent = false
        )
        shouldScrollToBottom = true
        viewModel.sendMessage(msg)
        inputDelegate.resetInput()
    }

    private fun joinConference() {
        if (roomId.isEmpty()) return
        lavender.client.android.data.calls.CallManager.joinConference(roomId)
        lavender.client.android.data.calls.CallNavigator.joinConference(this, roomId)
    }

    private fun retryMessage(message: Message) {
        Toast.makeText(this, getString(R.string.checking_server), Toast.LENGTH_SHORT).show()
        viewModel.retryMessage(message)
    }

    private fun isNearBottom(): Boolean {
        val lm = messagesRecyclerView.layoutManager as? LinearLayoutManager ?: return true
        val lastVisible = lm.findLastCompletelyVisibleItemPosition()
        val total = lm.itemCount
        return lastVisible >= total - 3
    }

    private fun loadDraft() {
        if (roomId.isEmpty() || username.isEmpty() || grpcClient.getUserId() == null) return
        viewModel.getDraft { dt, rmi, ru, rt, hd ->
            lifecycleScope.launch {
                if (hd && (dt.isNotEmpty() || rmi.isNotEmpty())) {
                    if (dt.isNotEmpty()) inputDelegate.setDraftText(dt)
                    if (rmi.isNotEmpty()) inputDelegate.showReplyPreview(Message(id = rmi, user = ru, text = rt, timestamp = System.currentTimeMillis(), roomId = roomId))
                }
            }
        }
    }

    private fun saveDraft() {
        if (roomId.isEmpty() || username.isEmpty()) return
        if (grpcClient.getUserId() == null) { viewModel.ensureUserIdSet(this) { saveDraft() }; return }
        val dt = inputDelegate.getDraftText()
        val replyingTo = inputDelegate.getReplyingTo()
        if (dt.isNotEmpty() || replyingTo != null) {
            viewModel.saveDraft(dt, replyingTo?.id ?: "", replyingTo?.user ?: "", replyingTo?.text ?: "")
        } else if (grpcClient.getUserId() != null) {
            viewModel.deleteDraft()
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeStore.refresh(this, username)
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false
        if (lavender.client.android.data.auth.AuthManager.isJwtAuthenticated(this)
            && lavender.client.android.data.auth.AuthManager.needsRefresh(this)) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { lavender.client.android.data.session.SessionManager.ensureFreshToken(this@NewChatActivity) }
        }
        if (grpcClient.connectionStatus.value == ConnectionStatus.READY) {
            grpcClient.loadUsers()
        }
        if (grpcClient.shouldForceReconnect()) {
            val sa = intent.getStringExtra("SERVER_ADDRESS") ?: getSharedPreferences("lavender_prefs", MODE_PRIVATE).getString("server_address", "")
            if (!sa.isNullOrEmpty()) {
                val p = sa.split(":")
                grpcClient.connect(p[0], false, p.getOrNull(1)?.toIntOrNull() ?: 50051, this, true)
            }
        }
        viewModel.fetchChatMetadata(username, roomId, isDirect, participantsJson, chatName) { meta ->
            try {
                lifecycleScope.launch {
                    if (isFinishing || isDestroyed) return@launch
                    chatName = meta.chatName; isDirect = meta.isDirect; chatType = meta.chatType
                    participantsJson = meta.participantsJson; creator = meta.creator
                    chatAvatarUrl = meta.avatarUrl; chatFullAvatarUrl = meta.fullAvatarUrl
                    toolbarDelegate.configure(roomId, username, chatName, isDirect, chatType, participantsJson, creator, chatAvatarUrl, chatFullAvatarUrl, isSecret)
                    toolbarDelegate.setup()
                    adapter.isGroupChat = !isDirect; adapter.adminUsername = creator
                }
            } catch (e: Exception) {
                android.util.Log.e("NewChatActivity", "fetchChatMetadata callback error: ${e.message}", e)
            }
        }
        viewModel.loadPinnedMessages(this)
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
        val newRoomId = intent.getStringExtra("ROOM_ID") ?: intent.getStringExtra("roomId") ?: return

        lavender.client.android.data.fcm.LavenderMessagingService.dismissNotificationsForRoom(this, newRoomId)

        if (newRoomId == roomId) return
        roomId = newRoomId
        loadDataFromIntent()
        grpcClient.setRoomId(roomId)
        grpcClient.clearMessages()
        viewModel.switchRoom(roomId)
        viewModel.startChatV2(roomId) { _ -> viewModel.markRead(username) }
        viewModel.markRead(username)
        intent.getStringExtra("CHAT_NAME")?.let {
            chatName = it
            toolbarDelegate.configure(roomId, username, chatName, isDirect, chatType, participantsJson, creator, chatAvatarUrl, chatFullAvatarUrl, isSecret)
            toolbarDelegate.setup()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val inSelection = selectionDelegate.isInSelectionMode()
        val callItem = menu.findItem(R.id.action_video_call)
        val searchItem = menu.findItem(R.id.action_search)
        val conferenceItem = menu.findItem(R.id.action_conference)
        callItem?.isVisible = !inSelection && isDirect && !roomId.startsWith("favorites_") && !isSecret
        conferenceItem?.isVisible = false
        searchItem?.isVisible = !inSelection
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_video_call -> {
                val other = toolbarDelegate.getOtherParticipant()
                if (!other.isNullOrEmpty()) {
                    val otherUserId = lavender.client.android.data.grpc.GrpcClient.allUsers.value
                        .firstOrNull { it.username == other }?.userId ?: other
                    lavender.client.android.data.calls.CallManager.initiateCall(other)
                    lavender.client.android.data.calls.CallNavigator.startCall(this, otherUserId, other)
                }
                true
            }
            R.id.action_search -> {
                searchDelegate.show()
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

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
