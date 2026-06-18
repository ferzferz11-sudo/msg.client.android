package lavender.client.android

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.audio.AudioUploader
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
import java.io.File
import java.util.Locale
import lavender.client.android.data.grpc.GrpcClientExtensions.*

/**
 * Chat screen — thin Activity delegating to specialized modules.
 *
 * Architecture:
 * - ChatToolbarDelegate: toolbar, avatar, subtitle, navigation
 * - ChatInputDelegate: text input, send, attachments, audio, emoji, mentions
 * - ChatSelectionDelegate: selection mode, copy/pin/delete/forward
 * - ChatSearchDelegate: in-chat search
 * - ChatE2EEDelegate: end-to-end encryption for secret chats
 * - ChatMessageMenuDelegate: reactions, context menu
 * - ChatViewModel: messages, history, typing, drafts
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

    // Delegates
    private lateinit var toolbarDelegate: ChatToolbarDelegate
    private lateinit var inputDelegate: ChatInputDelegate
    private lateinit var selectionDelegate: ChatSelectionDelegate
    private lateinit var searchDelegate: ChatSearchDelegate
    private lateinit var e2eeDelegate: ChatE2EEDelegate
    private lateinit var messageMenuDelegate: ChatMessageMenuDelegate

    // State
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

    // Views (shared)
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private lateinit var replyPreview: View
    private lateinit var replyUser: TextView
    private lateinit var replyText: TextView
    private lateinit var cancelReply: android.widget.ImageButton
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var historyLoadingProgress: ProgressBar
    private lateinit var uploadProgressBar: ProgressBar
    private lateinit var uploadProgressContainer: com.google.android.material.card.MaterialCardView
    private lateinit var uploadProgressText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // Init delegates
        initDelegates()
        initSharedViews()

        ThemeUi.bind(this, username)
        setupTheme()
        setupRecyclerView()

        setupDelegates()
        setupObservers()
        setupKeyboardHandling()
        fetchChatMetadataIfNeeded()

        viewModel.switchRoom(roomId)

        SessionManager.updateDeviceInfo(this)
        val session = SessionManager.session.value
        viewModel.startChat(username, password, "", deviceId = session.deviceId, deviceName = session.deviceName) { _ ->
            viewModel.markRead(username)
        }
        viewModel.markRead(username)
        ensureUserIdSet { loadDraft() }

        lifecycleScope.launch {
            SessionManager.logoutEvent.collect { runOnUiThread { finish() } }
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

    // ======= Delegates Initialization =======

    private fun initDelegates() {
        toolbarDelegate = ChatToolbarDelegate(this, grpcClient)
        toolbarDelegate.initViews()

        inputDelegate = ChatInputDelegate(this, grpcClient)
        inputDelegate.initViews()

        selectionDelegate = ChatSelectionDelegate(this, grpcClient)
        selectionDelegate.initViews()

        searchDelegate = ChatSearchDelegate(this)
        searchDelegate.initViews()

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
        uploadProgressBar = findViewById(R.id.uploadProgressBar)
        uploadProgressContainer = findViewById(R.id.uploadProgressContainer)
        uploadProgressText = findViewById(R.id.uploadProgressText)
    }

    private fun setupDelegates() {
        // Toolbar
        toolbarDelegate.configure(roomId, username, chatName, isDirect, chatType, participantsJson, creator, chatAvatarUrl, chatFullAvatarUrl, isSecret)
        toolbarDelegate.setup()

        // Input
        inputDelegate.configure(roomId, username, isDirect, participantsJson, isSecret)
        inputDelegate.onSendMessage = { text, imageUrl -> sendMessage(text, imageUrl) }
        inputDelegate.onTypingSignal = { isTyping -> grpcClient.sendTypingSignal(username, isTyping) }
        inputDelegate.onAudioRecord = { file, dur -> uploadAudio(file, dur) }
        inputDelegate.onReplyChanged = { m ->
            if (m != null) {
                replyPreview.isVisible = true
                replyUser.text = m.user
                replyText.text = if (m.imageUrl.isNotEmpty()) "Photo" else m.text
                inputDelegate.messageInput.requestFocus()
            } else {
                replyPreview.isVisible = false
            }
        }
        inputDelegate.setupListeners(audioRecordHandler = { file, dur -> uploadAudio(file, dur) })

        // Selection
        selectionDelegate.configure(roomId, username)
        selectionDelegate.setAdapter(adapter)
        selectionDelegate.onSelectionModeChanged = { inMode -> invalidateOptionsMenu() }
        selectionDelegate.getToolbarDelegate = { toolbarDelegate }
        selectionDelegate.onReplySelected = { m -> inputDelegate.showReplyPreview(m) }
        selectionDelegate.setupListeners()

        // Search
        searchDelegate.setAdapter(adapter)
        searchDelegate.getToolbarDelegate = { toolbarDelegate }
        searchDelegate.setupListeners()

        // E2EE
        e2eeDelegate.configure(roomId, isSecret, toolbarDelegate.toolbarSubtitle)
        e2eeDelegate.onKeyExchangeComplete = { success ->
            if (success) inputDelegate.setSecretState(true)
        }

        // Message menu
        messageMenuDelegate.configure(username)

        // Cross-delegate: selection -> input for reply
        // (handled via adapter callbacks)
    }

    // ======= Theme =======

    private fun setupTheme() {
        val customTheme = ThemeStore.currentTheme()
        try {
            val pColor = customTheme.primaryColor.toColorInt()
            historyLoadingProgress.indeterminateTintList = ColorStateList.valueOf(pColor)
            swipeRefreshLayout.setColorSchemeColors(pColor)
        } catch (_: Exception) {}

        swipeRefreshLayout.setOnRefreshListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val db = lavender.client.android.data.db.AppDatabase.getDatabase(this@NewChatActivity)
                    db.messageDao().clearRoom(roomId)
                } catch (_: Exception) {}
                withContext(Dispatchers.Main) {
                    viewModel.switchRoom(roomId)
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    // ======= RecyclerView =======

    private fun setupRecyclerView() {
        adapter = MessageAdapter(
            currentUsername = username,
            isGroupChat = !isDirect,
            adminUsername = creator,
            onMessageClick = { message ->
                val text = message.text.trim().lowercase()
                val isCall = text.contains("📹") || text.contains("конференция") || text.contains("conference")
                val isEnded = text.contains("завершена") || text.contains("завершен") ||
                        text.contains("удалена") || text.contains("удален") ||
                        text.contains("ended") || text.contains("deleted")
                if (isCall && !isEnded) {
                    joinConference()
                } else {
                    messageMenuDelegate.showReactionsDialog(message) { m ->
                        inputDelegate.showReplyPreview(m)
                    }
                }
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
            if (direction == ItemTouchHelper.LEFT) {
                inputDelegate.showReplyPreview(adapter.currentList[position])
                adapter.notifyItemChanged(position)
            } else if (direction == ItemTouchHelper.RIGHT) {
                finish()
            }
        }
        ItemTouchHelper(swipeController).attachToRecyclerView(messagesRecyclerView)
    }

    // ======= Observers =======

    private fun setupObservers() {
        viewModel = ViewModelProvider(this, ChatViewModelFactory(roomId))[ChatViewModel::class.java]

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { roomMessages ->
                    val hasNewMessages = roomMessages.size > lastMessageCount
                    adapter.submitList(roomMessages) {
                        if (shouldScrollToBottom) {
                            shouldScrollToBottom = false
                            messagesRecyclerView.post {
                                messagesRecyclerView.scrollToPosition(roomMessages.size - 1)
                            }
                        }
                    }
                    if (hasNewMessages && roomMessages.any { it.user != username && !it.isRead }) {
                        viewModel.markRead(username)
                    }
                    lastMessageCount = roomMessages.size
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
                combine(
                    grpcClient.users,
                    grpcClient.connectionStatus,
                    grpcClient.typingUsers
                ) { onlineUsers, status, typingMap ->
                    val currentTypists = typingMap[roomId]?.filter { it != username } ?: emptyList()
                    Triple(onlineUsers, status, currentTypists)
                }.collect { (onlineUsers, status, currentTypists) ->
                    val isConnected = status == ConnectionStatus.READY
                    val isConnecting = status == ConnectionStatus.CONNECTING
                    toolbarDelegate.updateSubtitle(onlineUsers, isConnected, currentTypists)

                    inputDelegate.messageInput.isEnabled = !isConnecting
                    inputDelegate.sendButton.isEnabled = !isConnecting
                    inputDelegate.attachButton.isEnabled = !isConnecting
                    inputDelegate.audioButton.isEnabled = !isConnecting

                    if (isConnected) {
                        syncChatListIfNeeded()
                        if (adapter.currentList.isEmpty()) viewModel.loadHistory()
                        loadPinnedMessages()
                    }
                }
            }
        }
    }

    // ======= Keyboard =======

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

    // ======= Intent Data =======

    private fun loadDataFromIntent() {
        roomId = intent.getStringExtra("ROOM_ID") ?: intent.getStringExtra("roomId") ?: (if (roomId.isEmpty()) "general" else roomId)
        val incomingUser = intent.getStringExtra("USERNAME")
        val incomingPass = intent.getStringExtra("PASSWORD")
        if (!incomingUser.isNullOrEmpty()) username = incomingUser
        if (!incomingPass.isNullOrEmpty()) password = incomingPass

        if (username.isEmpty() || password.isEmpty()) {
            val session = SessionManager.session.value
            if (session.isLoggedIn) {
                username = session.username
                password = session.password
            } else {
                val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                username = prefs.getString("username", "") ?: ""
                password = prefs.getString("password", "") ?: ""
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

    // ======= Chat Metadata =======

    private fun fetchChatMetadataIfNeeded() {
        if (roomId.startsWith("favorites_")) return
        if (!isDirect || participantsJson == "[]" || chatName == "Chat") {
            grpcClient.getChats(username) { chats ->
                val chat = chats.find { it.id == roomId }
                if (chat != null) runOnUiThread {
                    chatName = chat.getDisplayName(username)
                    isDirect = chat.type == "direct"
                    chatType = chat.type
                    participantsJson = chat.participants
                    creator = chat.creator
                    chatAvatarUrl = chat.avatarUrl
                    chatFullAvatarUrl = chat.fullAvatarUrl
                    toolbarDelegate.configure(roomId, username, chatName, isDirect, chatType, participantsJson, creator, chatAvatarUrl, chatFullAvatarUrl, isSecret)
                    toolbarDelegate.setup()
                    adapter.isGroupChat = !isDirect
                    adapter.adminUsername = creator
                }
            }
        }
    }

    // ======= Pinned Messages =======

    private fun loadPinnedMessages() {
        lifecycleScope.launch {
            try {
                val pinned = GrpcClient.getPinnedMessages(this@NewChatActivity, roomId)
                val pinnedIds = pinned.map { it.id }.toSet()
                selectionDelegate.setPinnedMessageIds(pinnedIds)
                adapter.updatePinnedMessages(pinnedIds)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load pinned messages", e)
            }
        }
    }

    // ======= Calls =======

    private fun startConference() {
        if (roomId.isEmpty()) return
        lavender.client.android.data.calls.CallManager.initiateConference(roomId)
        lavender.client.android.data.calls.CallNavigator.startConference(this, roomId)
    }

    private fun joinConference() {
        if (roomId.isEmpty()) return
        lavender.client.android.data.calls.CallManager.joinConference(roomId)
        lavender.client.android.data.calls.CallNavigator.joinConference(this, roomId)
    }

    private fun startVideoCall() {
        val otherUser = toolbarDelegate.getOtherParticipant() ?: return
        lavender.client.android.data.calls.CallManager.initiateCall(otherUser)
        lavender.client.android.data.calls.CallNavigator.startCall(this, otherUser)
    }

    // ======= Send Message =======

    private fun sendMessage(text: String, imageUrl: String) {
        if (isSecret && e2eeDelegate.isKeyExchanged()) {
            e2eeDelegate.encryptAndSend(text) { success ->
                if (success) {
                    if (roomId.startsWith("favorites_")) viewModel.markRead(username)
                    grpcClient.deleteDraft(roomId)
                    inputDelegate.resetInput()
                } else {
                    Toast.makeText(this, "E2EE encryption failed", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        val et = when {
            text.isEmpty() && imageUrl.isEmpty() -> "Message"
            imageUrl.isNotEmpty() && text.isEmpty() -> ""
            else -> text
        }
        val msg = Message(
            id = java.util.UUID.randomUUID().toString(), user = username, text = et,
            timestamp = System.currentTimeMillis(), roomId = roomId, imageUrl = imageUrl,
            repliedToMessageId = inputDelegate.getReplyingTo()?.id ?: "",
            repliedToUser = inputDelegate.getReplyingTo()?.user ?: "",
            repliedToText = inputDelegate.getReplyingTo()?.text ?: "",
            userId = grpcClient.getUserId() ?: "", isSent = false
        )
        shouldScrollToBottom = true
        grpcClient.addLocalMessage(msg)
        grpcClient.sendMessage(msg)
        if (roomId.startsWith("favorites_")) viewModel.markRead(username)
        grpcClient.deleteDraft(roomId)
        inputDelegate.resetInput()
    }

    // ======= Audio =======

    private fun uploadAudio(file: File, duration: Int) {
        uploadProgressBar.isVisible = true
        inputDelegate.audioButton.isVisible = false
        lifecycleScope.launch {
            val result = AudioUploader(this@NewChatActivity).uploadAudio(file, duration)
            runOnUiThread {
                uploadProgressBar.isVisible = false
                inputDelegate.audioButton.isVisible = true
                if (result.success && result.url.isNotEmpty() && !result.url.contains("404")) {
                    grpcClient.sendMessage(Message(
                        user = username, text = "Voice message", timestamp = System.currentTimeMillis(),
                        roomId = roomId, voiceUrl = result.url, duration = result.duration,
                        userId = grpcClient.getUserId() ?: ""
                    ))
                } else {
                    Toast.makeText(this@NewChatActivity,
                        "Failed to upload audio: ${if (result.url.contains("404")) "Server error 404" else result.error}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // ======= Retry =======

    private fun retryMessage(message: Message) {
        Toast.makeText(this, getString(R.string.checking_server), Toast.LENGTH_SHORT).show()
        grpcClient.loadHistory(roomId) {
            runOnUiThread {
                val updated = grpcClient.messages.value.find { it.id == message.id }
                if (updated == null || !updated.isSent) {
                    Toast.makeText(this, getString(R.string.resending), Toast.LENGTH_SHORT).show()
                    grpcClient.sendMessage(message)
                } else {
                    Toast.makeText(this, getString(R.string.message_already_sent), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ======= Menu =======

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        menu.findItem(R.id.action_search)?.isVisible = !selectionDelegate.isInSelectionMode()
        menu.findItem(R.id.action_video_call)?.isVisible = !selectionDelegate.isInSelectionMode() && isDirect && !roomId.startsWith("favorites_") && !isSecret
        menu.findItem(R.id.action_conference)?.isVisible = false

        val iconColor = run {
            val customTheme = ThemeStore.currentTheme()
            try { customTheme.onPrimaryColor.toColorInt() } catch (_: Exception) {
                val typedValue = TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
                typedValue.data
            }
        }

        menu.findItem(R.id.action_search)?.iconTintList = ColorStateList.valueOf(iconColor)
        menu.findItem(R.id.action_video_call)?.iconTintList = ColorStateList.valueOf(iconColor)
        menu.findItem(R.id.action_conference)?.iconTintList = ColorStateList.valueOf(iconColor)

        val customTheme = ThemeStore.currentTheme()
        try {
            val textColor = customTheme.onPrimaryColor.toColorInt()
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                val span = android.text.SpannableString(item.title)
                span.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, span.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                item.title = span
            }
        } catch (_: Exception) {}

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> { searchDelegate.show(); true }
            R.id.action_video_call -> { startVideoCall(); true }
            R.id.action_conference -> { startConference(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ======= Drafts =======

    private fun loadDraft() {
        if (roomId.isEmpty() || username.isEmpty() || grpcClient.getUserId() == null) return
        grpcClient.getDraft(roomId) { dt, rmi, ru, rt, hd ->
            runOnUiThread {
                if (hd && (dt.isNotEmpty() || rmi.isNotEmpty())) {
                    if (dt.isNotEmpty()) inputDelegate.setDraftText(dt)
                    if (rmi.isNotEmpty()) {
                        val replyMsg = Message(id = rmi, user = ru, text = rt, timestamp = System.currentTimeMillis(), roomId = roomId)
                        inputDelegate.showReplyPreview(replyMsg)
                    }
                }
            }
        }
    }

    private fun saveDraft() {
        if (roomId.isEmpty() || username.isEmpty()) return
        if (grpcClient.getUserId() == null) { ensureUserIdSet { saveDraft() }; return }
        val dt = inputDelegate.getDraftText()
        val replyingTo = inputDelegate.getReplyingTo()
        if (dt.isNotEmpty() || replyingTo != null) {
            grpcClient.saveDraft(
                roomId = roomId, draftText = dt,
                repliedToMessageId = replyingTo?.id ?: "",
                repliedToUser = replyingTo?.user ?: "",
                repliedToText = replyingTo?.text ?: ""
            ) { _, _ -> }
        } else if (grpcClient.getUserId() != null) {
            grpcClient.deleteDraft(roomId)
        }
    }

    // ======= User ID =======

    private fun ensureUserIdSet(onReady: () -> Unit) {
        val s = getSavedUserId()
        if (s != null) { grpcClient.setUserId(s); onReady() }
        else if (username.isNotEmpty()) {
            grpcClient.fetchUserId(username) { uid, f ->
                if (f && uid != null) { saveUserId(uid); grpcClient.setUserId(uid) }
                runOnUiThread { onReady() }
            }
        } else onReady()
    }

    private fun getSavedUserId(): String? = getSharedPreferences("lavender_prefs", MODE_PRIVATE).getString("user_id", null)
    private fun saveUserId(userId: String) { getSharedPreferences("lavender_prefs", MODE_PRIVATE).edit { putString("user_id", userId) } }

    // ======= Lifecycle =======

    override fun onResume() {
        super.onResume()
        ThemeStore.refresh(this, username)
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false
        if (grpcClient.shouldForceReconnect()) {
            val sa = intent.getStringExtra("SERVER_ADDRESS") ?: getSharedPreferences("lavender_prefs", MODE_PRIVATE).getString("server_address", "")
            if (!sa.isNullOrEmpty()) {
                val p = sa.split(":")
                grpcClient.connect(p[0], false, p.getOrNull(1)?.toIntOrNull() ?: 50051, this, true)
            }
        }
        fetchChatMetadataIfNeeded()
        loadPinnedMessages()
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
        loadDataFromIntent()
        grpcClient.setRoomId(roomId)
        grpcClient.clearMessages()
        grpcClient.loadHistory(roomId)
        viewModel.switchRoom(roomId)
        loadDraft()
    }

    // ======= E2EE Public API =======

    fun handleIncomingE2EEMessage(msg: Message) {
        if (!msg.isE2EE || msg.e2eePayload.isEmpty()) return
        val decrypted = e2eeDelegate.decryptMessage(msg)
        if (decrypted != null) {
            val decryptedMsg = msg.copy(text = decrypted, isE2EE = false)
            runOnUiThread {
                val current = grpcClient.messages.value.toMutableList()
                current.add(decryptedMsg)
            }
        }
    }

    // ======= Utils =======

    private fun syncChatListIfNeeded() {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val local = prefs.getLong("chat_list_version", 0L)
        val u = grpcClient.getCurrentUsername() ?: return
        grpcClient.getChatListVersion(u) { server ->
            if (server > local) {
                grpcClient.getChats(u) { prefs.edit { putLong("chat_list_version", server) } }
            }
        }
    }

    private fun clearCacheForCurrentRoom() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                lavender.client.android.data.db.AppDatabase.getDatabase(this@NewChatActivity).messageDao().clearRoom(roomId)
            } catch (_: Exception) {}
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
