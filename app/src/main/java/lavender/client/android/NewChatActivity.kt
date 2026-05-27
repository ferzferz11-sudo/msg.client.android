package lavender.client.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
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
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.audio.AudioUploader
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.ProtoUtils
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.session.SessionManager
import lavender.client.android.ui.adapter.MentionAdapter
import lavender.client.android.ui.adapter.MessageAdapter
import lavender.client.android.ui.adapter.MessageSwipeController
import lavender.client.android.ui.audio.AudioRecordingView
import lavender.client.android.ui.chat.ChatViewModel
import lavender.client.android.ui.chat.ChatViewModelFactory
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.data.ThemeMappers
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.theme.ui.ThemeUi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.io.File
import java.util.Locale
import androidx.core.view.get
import androidx.core.view.size
import androidx.core.content.edit

import lavender.client.android.ui.widget.ActionBottomSheet
import lavender.client.android.ui.widget.SheetAction
import lavender.client.android.ui.widget.StandardBottomSheet
import lavender.client.android.ui.widget.ListBottomSheet
import lavender.client.android.ui.widget.WidgetManager

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

    private lateinit var viewModel: ChatViewModel
    private val grpcClient = GrpcClient
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

    private var isTypingSignalSent = false
    private var selectionMode = false
    private var replyingTo: Message? = null
    private var lastMessageCount = 0
    private var currentPhotoUri: Uri? = null

    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var toolbarTitle: TextView
    private lateinit var toolbarSubtitle: TextView
    private lateinit var toolbarAvatar: CircleImageView
    private lateinit var groupParticipantsContainer: LinearLayout
    private lateinit var selectionToolbar: LinearLayout
    private lateinit var selectionCountText: TextView
    private lateinit var copyMessages: ImageButton
    private lateinit var replyMessage: ImageButton
    private lateinit var deleteMessages: ImageButton
    private lateinit var forwardMessages: ImageButton
    private lateinit var toolbarContent: View
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var attachButton: ImageButton
    private lateinit var audioButton: ImageButton
    private lateinit var uploadProgressBar: ProgressBar
    private lateinit var uploadProgressContainer: com.google.android.material.card.MaterialCardView
    private lateinit var uploadProgressText: TextView

    private lateinit var replyPreview: View
    private lateinit var replyUser: TextView
    private lateinit var replyText: TextView
    private lateinit var cancelReply: ImageButton
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var historyLoadingProgress: ProgressBar

    private lateinit var mentionContainer: View
    private lateinit var mentionList: RecyclerView
    private lateinit var mentionAdapter: MentionAdapter

    private lateinit var searchBar: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchNext: ImageButton
    private lateinit var searchPrev: ImageButton
    private lateinit var searchResultsCount: TextView
    private lateinit var btnLobby: ImageView

    private var isSecret = false
    private var secretKeyExchanged = false

    private lateinit var adapter: MessageAdapter
    private var searchResults = listOf<Int>()
    private var currentSearchIndex = -1

    private lateinit var imagePreviewScroll: HorizontalScrollView
    private lateinit var imagePreviewContainer: LinearLayout
    private val selectedImageUris = mutableListOf<Uri>()

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uris = mutableSetOf<Uri>()
            result.data?.data?.let { uris.add(it) }
            result.data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) uris.add(clipData.getItemAt(i).uri)
            }
            if (uris.isNotEmpty()) {
                selectedImageUris.addAll(uris)
                showImagePreview()
            }
        }
    }

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uris = mutableSetOf<Uri>()
            result.data?.data?.let { uris.add(it) }
            result.data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) uris.add(clipData.getItemAt(i).uri)
            }
            if (uris.isNotEmpty()) {
                val imageUris = uris.filter { uri ->
                    val mimeType = contentResolver.getType(uri)
                    mimeType?.startsWith("image/") == true
                }
                
                if (imageUris.isNotEmpty()) {
                    selectedImageUris.addAll(imageUris)
                    showImagePreview()
                } else {
                    uploadFiles(uris.toList(), isImage = false)
                }
            }
        }
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) currentPhotoUri?.let {
            selectedImageUris.addAll(listOf(it))
            showImagePreview()
        }
    }

    private val pickLocationLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val lat = result.data?.getDoubleExtra("lat", 0.0) ?: 0.0
            val lng = result.data?.getDoubleExtra("lng", 0.0) ?: 0.0
            if (lat != 0.0 || lng != 0.0) {
                sendMessage("geo:$lat,$lng", "")
            }
        }
    }

    private fun createImageUri(): Uri? {
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "temp_photo_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

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
        initViews()

        ThemeUi.bind(this, username)
        val customTheme = ThemeStore.currentTheme()
        try {
            val pColor = customTheme.primaryColor.toColorInt()
            historyLoadingProgress.indeterminateTintList = ColorStateList.valueOf(pColor)
            swipeRefreshLayout.setColorSchemeColors(pColor)
        } catch (_: Exception) {}

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupListeners()
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
            SessionManager.logoutEvent.collect {
                runOnUiThread { finish() }
            }
        }
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                grpcClient.chatDeletedEvent.collect { deletedChatId ->
                    if (deletedChatId == roomId) {
                        finish()
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    selectionMode -> hideSelectionToolbar()
                    searchBar.isVisible -> hideSearchBar()
                    mentionContainer.isVisible -> mentionContainer.isVisible = false
                    else -> finish()
                }
            }
        })
    }

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
                    setupToolbar()
                    if (!isDirect) {
                        updateGroupSubtitle(grpcClient.users.value)
                    }
                    adapter.isGroupChat = !isDirect
                    adapter.adminUsername = creator
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
            
            bottomPanel.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = if (isImeVisible) imeInsets.bottom else systemBars.bottom }
            bottomPanelContent.updatePadding(bottom = 4.dpToPx()); insets
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar); toolbarTitle = findViewById(R.id.toolbarTitle); toolbarSubtitle = findViewById(R.id.toolbarSubtitle)
        toolbarAvatar = findViewById(R.id.toolbarAvatar); findViewById<ImageView>(R.id.toolbarLoadingIcon)
        groupParticipantsContainer = findViewById(R.id.groupParticipantsContainer); selectionToolbar = findViewById(R.id.selectionToolbar)
        selectionCountText = findViewById(R.id.selectionCountText); findViewById<ImageButton>(R.id.starMessages); copyMessages = findViewById(R.id.copyMessages)
        replyMessage = findViewById(R.id.replyMessage); deleteMessages = findViewById(R.id.deleteMessages); forwardMessages = findViewById(R.id.forwardMessages)
        toolbarContent = findViewById(R.id.toolbarContent); messagesRecyclerView = findViewById(R.id.messagesRecyclerView); swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        messageInput = findViewById(R.id.messageInput); sendButton = findViewById(R.id.sendButton); attachButton = findViewById(R.id.attachButton); audioButton = findViewById(R.id.audioButton)
        uploadProgressBar = findViewById(R.id.uploadProgressBar); uploadProgressContainer = findViewById(R.id.uploadProgressContainer); uploadProgressText = findViewById(R.id.uploadProgressText); replyPreview = findViewById(R.id.replyPreview)
        replyUser = findViewById(R.id.replyUser); replyText = findViewById(R.id.replyText); cancelReply = findViewById(R.id.cancelReply); findViewById<ImageButton>(R.id.emojiButton)
        mentionContainer = findViewById(R.id.mentionContainer); mentionList = findViewById(R.id.mentionList); mentionAdapter = MentionAdapter { insertMention(it) }
        mentionList.layoutManager = LinearLayoutManager(this); mentionList.adapter = mentionAdapter
        searchBar = findViewById(R.id.searchBar); searchInput = findViewById(R.id.searchInput)
        searchNext = findViewById(R.id.searchNext); searchPrev = findViewById(R.id.searchPrev); searchResultsCount = findViewById(R.id.searchResultsCount)
        btnLobby = findViewById(R.id.btnLobby)
        imagePreviewScroll = findViewById(R.id.imagePreviewScroll); imagePreviewContainer = findViewById(R.id.imagePreviewContainer)
        historyLoadingProgress = findViewById(R.id.historyLoadingProgress)
        audioButton.isVisible = true
        sendButton.isVisible = false

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

        // Secret chat handling
        isSecret = intent.getStringExtra("IS_SECRET") == "true"
        if (isSecret) {
            chatType = "secret"
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar); supportActionBar?.setDisplayShowTitleEnabled(false)
        setToolbarNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.setNavigationOnClickListener {
            if (selectionMode) hideSelectionToolbar()
            else if (searchBar.isVisible) hideSearchBar()
            else finish()
        }
        
        toolbarSubtitle.setOnClickListener {
            if (grpcClient.connectionStatus.value != ConnectionStatus.READY) {
                showToast(getString(R.string.connecting))
                viewModel.startChat(username, password, "") { _ ->
                    viewModel.markRead(username)
                }
            }
        }

        // Secret chat indicator and E2EE setup
        if (isSecret) {
            toolbarAvatar.isVisible = true
            groupParticipantsContainer.isVisible = false
            toolbarAvatar.setImageResource(R.drawable.ic_lock)
            val secretTheme = ThemeStore.currentTheme()
            toolbarAvatar.imageTintList = ColorStateList.valueOf(secretTheme.primaryColor.toColorInt())
            val secretBg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(secretTheme.surfaceContainer.toColorInt())
            }
            toolbarAvatar.background = secretBg
            val secretPad = 8.dpToPx()
            toolbarAvatar.setPadding(secretPad, secretPad, secretPad, secretPad)
            toolbarSubtitle.text = getString(R.string.e2ee_enabled)
            toolbarSubtitle.setTextColor(secretTheme.primaryColor.toColorInt())
            initE2EE()
        } else if (roomId.startsWith("favorites_")) {
            toolbarAvatar.isVisible = true; groupParticipantsContainer.isVisible = false
            toolbarAvatar.setImageResource(R.drawable.ic_star)
            clearCacheForCurrentRoom()
            val theme = ThemeStore.currentTheme()
            val primColor = theme.primaryColor.toColorInt()
            toolbarAvatar.imageTintList = ColorStateList.valueOf(theme.onPrimaryColor.toColorInt())
            
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(primColor)
            }
            toolbarAvatar.background = bg
            val p = 8.dpToPx()
            toolbarAvatar.setPadding(p, p, p, p)

            toolbarTitle.text = getString(R.string.favorites)
            toolbarSubtitle.isVisible = true
            toolbarSubtitle.text = getString(R.string.favorites)
            toolbarContent.setOnClickListener(null)
            return
        }

        val effectiveAvatarUrl = if (chatAvatarUrl.isNotEmpty()) chatAvatarUrl else if (isDirect) {
            try {
                val arr = JSONArray(participantsJson)
                var other = ""
                for (i in 0 until arr.length()) {
                    val p = arr.getString(i)
                    if (p != username) { other = p; break }
                }
                if (other.isNotEmpty()) grpcClient.getAvatarCache()[other] else null
            } catch (_: Exception) { null }
        } else null

        if (isDirect || chatAvatarUrl.isNotEmpty()) {
            toolbarAvatar.isVisible = true; groupParticipantsContainer.isVisible = false
            if (!effectiveAvatarUrl.isNullOrEmpty()) {
                com.bumptech.glide.Glide.with(this).load(effectiveAvatarUrl)
                    .placeholder(R.drawable.ic_default_avatar).circleCrop().into(toolbarAvatar)
            } else {
                ThemeUtils.applyDefaultAvatar(toolbarAvatar, ThemeStore.currentTheme())
            }
        } else {
            toolbarAvatar.isVisible = false; groupParticipantsContainer.isVisible = true; setupGroupAvatars()
        }

        toolbarTitle.text = chatName
        val openProfile = View.OnClickListener {
            if (chatType == "conference") {
                val intent = Intent(this, ConferenceLobbyActivity::class.java).apply {
                    putExtra("ROOM_ID", roomId)
                    putExtra("CHAT_NAME", chatName)
                    putExtra("PARTICIPANTS", participantsJson)
                    putExtra("CREATOR", creator)
                }
                startActivity(intent)
                return@OnClickListener
            }

            val profileUsername = if (isDirect) {
                try {
                    val arr = JSONArray(participantsJson)
                    var other = chatName
                    for (i in 0 until arr.length()) {
                        val p = arr.getString(i)
                        if (p != username) { other = p; break }
                    }
                    other
                } catch (_: Exception) { chatName }
            } else chatName

            val intent = Intent(this, ProfileActivity::class.java).apply {
                putExtra("username", profileUsername)
                putExtra("is_group", !isDirect)
                putExtra("room_id", roomId)
                putExtra("avatar_url", if (isDirect) effectiveAvatarUrl else chatAvatarUrl)
                putExtra("full_avatar_url", chatFullAvatarUrl)
                putExtra("participants", participantsJson)
                putExtra("creator", creator)
            }
            startActivity(intent)
        }
        toolbarContent.setOnClickListener(openProfile)
        toolbarTitle.setOnClickListener(openProfile)
        toolbarAvatar.setOnClickListener(openProfile)
        groupParticipantsContainer.setOnClickListener(openProfile)

        // Lobby entry button logic
        if (chatType == "conference") {
            val isMeAdmin = username.trim().equals(creator.trim(), ignoreCase = true) && creator.isNotEmpty()
            btnLobby.isVisible = isMeAdmin // Initial visibility: only for admin
            btnLobby.setOnClickListener {
                val intent = Intent(this, ConferenceLobbyActivity::class.java).apply {
                    putExtra("ROOM_ID", roomId)
                    putExtra("CHAT_NAME", chatName)
                    putExtra("PARTICIPANTS", participantsJson)
                    putExtra("CREATOR", creator)
                }
                startActivity(intent)
            }
            
            // Listen for signals to potentially show it to others if conference becomes active
            lifecycleScope.launch {
                lavender.client.android.data.calls.CallManager.incomingSignals.collect { signal ->
                    if (signal.roomId == roomId && signal.type == lavender.client.android.data.proto.CallMessageProto.Type.JOIN_CONFERENCE) {
                        runOnUiThread { 
                            if (!btnLobby.isVisible) {
                                btnLobby.isVisible = true
                                btnLobby.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this@NewChatActivity, android.R.anim.fade_in))
                            }
                        }
                    }
                }
            }
        } else {
            btnLobby.isVisible = false
        }
    }

    private fun setToolbarNavigationIcon(iconResId: Int) {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationIcon(iconResId)
        toolbar.navigationIcon?.let {
            val wrapped = DrawableCompat.wrap(it)
            val theme = ThemeStore.currentTheme()
            val iconColor = try { theme.onPrimaryColor.toColorInt() } catch (_: Exception) { ContextCompat.getColor(this, R.color.white) }
            DrawableCompat.setTint(wrapped, iconColor)
            toolbar.navigationIcon = wrapped
        }
    }

    private fun setupGroupAvatars() {
        groupParticipantsContainer.removeAllViews()
        val arr = JSONArray(participantsJson)
        for (i in 0 until arr.length().coerceAtMost(3)) {
            val u = arr.getString(i)
            val iv = CircleImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(34.dpToPx(), 34.dpToPx()).apply { marginStart = if (i > 0) (-10).dpToPx() else 0 }
                borderWidth = 1.dpToPx()
                val theme = ThemeStore.currentTheme()
                borderColor = try { theme.onPrimaryColor.toColorInt() } catch (_: Exception) { ContextCompat.getColor(this@NewChatActivity, R.color.white) }
            }
            val cache = grpcClient.getAvatarCache(); val url = cache[u]
            if (!url.isNullOrEmpty()) {
                com.bumptech.glide.Glide.with(this).load(url).placeholder(R.drawable.ic_default_avatar).circleCrop().into(iv)
                iv.clearColorFilter()
            } else {
                ThemeUtils.applyDefaultAvatar(iv, ThemeStore.currentTheme())
            }
            groupParticipantsContainer.addView(iv)
        }
    }

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
                    showReactionsDialog(message)
                }
            },
            onSelectionChanged = { if (it > 0) showSelectionToolbar(it) else hideSelectionToolbar() },
            onMessageLongClick = { enterSelectionMode(it) },
            chatId = roomId,
            onRetrySendMessage = { retryMessage(it) }
        )
        messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messagesRecyclerView.adapter = adapter
        val swipeController = MessageSwipeController(this) { position, direction ->
            if (direction == ItemTouchHelper.LEFT) {
                showReplyPreview(adapter.currentList[position])
                adapter.notifyItemChanged(position)
            } else if (direction == ItemTouchHelper.RIGHT) {
                finish()
            }
        }
        ItemTouchHelper(swipeController).attachToRecyclerView(messagesRecyclerView)
    }

    private fun setupObservers() {
        viewModel = ViewModelProvider(this, ChatViewModelFactory(roomId))[ChatViewModel::class.java]

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { roomMessages ->
                    val layoutManager = messagesRecyclerView.layoutManager as? LinearLayoutManager
                    val wasAtBottom = layoutManager?.let {
                        it.findLastVisibleItemPosition() >= lastMessageCount - 2
                    } ?: true

                    val isFirstLoad = lastMessageCount == 0
                    val hasNewMessages = roomMessages.size > lastMessageCount
                    
                    adapter.submitList(roomMessages) {
                        val isFromMe = roomMessages.lastOrNull()?.user == username
                        if (roomMessages.isNotEmpty() && (isFirstLoad || (hasNewMessages && isFromMe) || (hasNewMessages && wasAtBottom))) {
                            messagesRecyclerView.scrollToPosition(roomMessages.size - 1)
                        }
                    }

                    if ((isFirstLoad || hasNewMessages) && roomMessages.any { it.user != username && !it.isRead }) {
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
                    updateSubtitle(onlineUsers, isConnected, currentTypists)

                    messageInput.isEnabled = !isConnecting
                    sendButton.isEnabled = !isConnecting
                    attachButton.isEnabled = !isConnecting
                    audioButton.isEnabled = !isConnecting

                    if (isConnected) {
                        syncChatListIfNeeded()
                        if (adapter.currentList.isEmpty()) {
                            viewModel.loadHistory()
                        }
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean { menuInflater.inflate(R.menu.chat_menu, menu); return true }
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_search)?.isVisible = !selectionMode
        menu.findItem(R.id.action_video_call)?.isVisible = !selectionMode && isDirect && !roomId.startsWith("favorites_")
        menu.findItem(R.id.action_conference)?.isVisible = false
        
        val iconColor = run {
            val customTheme = ThemeStore.currentTheme()
            try { customTheme.onPrimaryColor.toColorInt() } catch (_: Exception) {
                val typedValue = TypedValue()
                this@NewChatActivity.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
                typedValue.data
            }
        }
        
        menu.findItem(R.id.action_search)?.iconTintList = ColorStateList.valueOf(iconColor)
        menu.findItem(R.id.action_video_call)?.iconTintList = ColorStateList.valueOf(iconColor)
        menu.findItem(R.id.action_conference)?.iconTintList = ColorStateList.valueOf(iconColor)
        
        val customTheme = ThemeStore.currentTheme()
        try {
            val textColor = customTheme.onPrimaryColor.toColorInt()
            for (i in 0 until menu.size) {
                val item = menu[i]
                val span = android.text.SpannableString(item.title)
                span.setSpan(android.text.style.ForegroundColorSpan(textColor), 0, span.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                item.title = span
            }
        } catch (_: Exception) {}
        
        return super.onPrepareOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> { showSearchBar(); true }
            R.id.action_video_call -> { startVideoCall(); true }
            R.id.action_conference -> { startConference(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

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
        val otherUser = getOtherParticipant() ?: return
        lavender.client.android.data.calls.CallManager.initiateCall(otherUser)
        lavender.client.android.data.calls.CallNavigator.startCall(this, otherUser)
    }

    private fun setupListeners() {
        sendButton.setOnClickListener {
            if (selectedImageUris.isNotEmpty()) {
                sendSelectedImages()
            } else {
                val text = messageInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendMessage(text, "")
                    messageInput.text.clear()
                    hideReplyPreview()
                }
            }
        }

        attachButton.setOnClickListener { showAttachmentSheet() }
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                handleMention(s)
                val text = s?.toString() ?: ""
                val hasText = text.trim().isNotEmpty()
                val hasImages = selectedImageUris.isNotEmpty()
                sendButton.isVisible = hasText || hasImages
                audioButton.isVisible = !hasText && !hasImages

                if (roomId.startsWith("favorites_")) return
                if (!isTypingSignalSent && hasText) {
                    isTypingSignalSent = true
                    grpcClient.sendTypingSignal(username, true)
                }
                typingJob?.cancel()
                typingJob = lifecycleScope.launch {
                    delay(3000)
                    if (isTypingSignalSent) {
                        grpcClient.sendTypingSignal(username, false)
                        isTypingSignalSent = false
                    }
                }
                if (!hasText && isTypingSignalSent) {
                    typingJob?.cancel()
                    grpcClient.sendTypingSignal(username, false)
                    isTypingSignalSent = false
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        cancelReply.setOnClickListener { hideReplyPreview() }
        findViewById<ImageButton>(R.id.starMessages).setOnClickListener { starSelectedMessages() }
        audioButton.setOnClickListener { showAudioRecordingView() }
        findViewById<ImageButton>(R.id.emojiButton).setOnClickListener { showEmojiPicker() }
        copyMessages.setOnClickListener { copySelectedMessages() }
        replyMessage.setOnClickListener { replyToSelectedMessage() }
        deleteMessages.setOnClickListener { deleteSelectedMessages() }
        forwardMessages.setOnClickListener { forwardSelectedMessages() }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { performSearch(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        searchNext.setOnClickListener { navigateSearch(1) }
        searchPrev.setOnClickListener { navigateSearch(-1) }
    }

    private fun performSearch(query: String) {
        adapter.setSearchHighlight(query)
        if (query.isEmpty()) {
            searchResults = emptyList(); currentSearchIndex = -1; searchResultsCount.text = ""; return
        }
        val results = mutableListOf<Int>()
        val messages = adapter.currentList
        for (i in messages.indices) if (messages[i].text.contains(query, ignoreCase = true)) results.add(i)
        searchResults = results
        if (searchResults.isNotEmpty()) {
            currentSearchIndex = searchResults.size - 1; navigateSearch(0)
        } else {
            currentSearchIndex = -1; searchResultsCount.text = "0/0"
        }
    }

    private fun navigateSearch(direction: Int) {
        if (searchResults.isEmpty()) return
        currentSearchIndex += direction
        if (currentSearchIndex < 0) currentSearchIndex = searchResults.size - 1
        if (currentSearchIndex >= searchResults.size) currentSearchIndex = 0
        messagesRecyclerView.scrollToPosition(searchResults[currentSearchIndex])
        searchResultsCount.text = getString(R.string.search_results_format, currentSearchIndex + 1, searchResults.size)
    }

    private fun showEmojiPicker() {
        val sheet = StandardBottomSheet(this, R.layout.dialog_emoji_picker)
        val emojiGrid = sheet.findViewById<android.widget.GridLayout>(R.id.emojiGrid)
        
        val emojis = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
            "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
            "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤔",
            "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴",
            "🤢", "🤮", "🤧", "🥵", "🥶", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽",
            "👾", "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾", "👋", "🤚", "🖐", "✋", "🖖", "👌", "🤏", "✌️",
            "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲",
            "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦵", "🦿", "🦶"
        )
        
        val size = (48 * resources.displayMetrics.density).toInt()
        for (emoji in emojis) {
            val tv = TextView(this).apply {
                text = emoji
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(size, size)
                val v = TypedValue()
                this@NewChatActivity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, v, true)
                setBackgroundResource(v.resourceId)
                setOnClickListener {
                    val cp = messageInput.selectionStart
                    val ct = messageInput.text.toString()
                    messageInput.setText(ct.substring(0, cp) + emoji + ct.substring(cp))
                    messageInput.setSelection(cp + emoji.length)
                    sheet.dismiss()
                }
            }
            emojiGrid?.addView(tv)
        }
        sheet.show()
    }

    private fun showAttachmentSheet() {
        WidgetManager.getOrCreate("attachment_sheet") { ActionBottomSheet(this) }
            .setActions(listOf(
                SheetAction(R.id.attachCamera, R.drawable.ic_mic, getString(R.string.attach_camera)) {
                    try {
                        currentPhotoUri = createImageUri()
                        if (currentPhotoUri != null) {
                            takePhotoLauncher.launch(currentPhotoUri!!)
                        } else {
                            showToast("Failed to create image file")
                        }
                    } catch (e: Exception) {
                        showToast("Could not open camera app")
                        android.util.Log.e("NewChatActivity", "Camera launch error", e)
                    }
                },
                SheetAction(R.id.attachGallery, R.drawable.ic_gallery, getString(R.string.attach_gallery)) {
                    pickImageLauncher.launch(Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply { putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) })
                },
                SheetAction(R.id.attachFile, R.drawable.attach_file_add_24, getString(R.string.attach_file_label)) {
                    pickFileLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) })
                },
                SheetAction(R.id.attachLocation, R.drawable.ic_location, getString(R.string.attach_location)) {
                    pickLocationLauncher.launch(Intent(this, MapPickerActivity::class.java))
                }
            )).show()
    }

    private fun showSearchBar() {
        searchBar.isVisible = true; toolbarContent.isVisible = false; setToolbarNavigationIcon(R.drawable.ic_close)
        val theme = ThemeStore.currentTheme()
        try {
            val prim = theme.primaryColor.toColorInt()
            val onPrim = theme.onPrimaryColor.toColorInt()
            
            searchBar.setBackgroundColor(prim)
            
            searchInput.setTextColor(onPrim)
            searchInput.setHintTextColor(ThemeUtils.adjustAlpha(onPrim, 0.6f))
            searchResultsCount.setTextColor(onPrim)
            
            // Cursor and selection
            searchInput.highlightColor = ThemeUtils.adjustAlpha(onPrim, 0.3f)
            searchInput.textCursorDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setSize((2 * resources.displayMetrics.density).toInt(), 0)
                setColor(onPrim)
            }

            // Theme buttons in search bar
            val tint = ColorStateList.valueOf(onPrim)
            findViewById<ImageButton>(R.id.searchPrev)?.imageTintList = tint
            findViewById<ImageButton>(R.id.searchNext)?.imageTintList = tint

        } catch (_: Exception) {}
        searchInput.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(searchInput, 0)
    }
    private fun hideSearchBar() {
        searchBar.isVisible = false; toolbarContent.isVisible = true; searchInput.text.clear(); searchResults = emptyList(); currentSearchIndex = -1; searchResultsCount.text = ""
        adapter.setSearchHighlight(null); (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchInput.windowToken, 0); setToolbarNavigationIcon(R.drawable.ic_back_arrow)
    }

    private fun handleMention(s: CharSequence?) {
        if (isDirect) return
        val cp = messageInput.selectionStart; val t = s?.toString() ?: ""; if (cp <= 0) { mentionContainer.isVisible = false; return }
        var la = -1; for (i in (cp - 1) downTo 0) { if (t[i] == '@') { la = i; break }; if (t[i] == ' ') break }
        if (la != -1) {
            val q = t.substring(la + 1, cp).lowercase(); val p = try { JSONArray(participantsJson) } catch (_: Exception) { JSONArray() }
            val f = mutableListOf<String>(); val ac = grpcClient.getAvatarCache()
            for (i in 0 until p.length()) { val u = p.getString(i); if (u != username && u.lowercase().contains(q)) f.add(u) }
            if (f.isNotEmpty()) { mentionAdapter.setUsers(f, ac); mentionContainer.isVisible = true } else mentionContainer.isVisible = false
        } else mentionContainer.isVisible = false
    }

    private fun insertMention(u: String) {
        val cp = messageInput.selectionStart; val t = messageInput.text.toString(); var la = -1
        for (i in (cp - 1) downTo 0) { if (t[i] == '@') { la = i; break }; if (t[i] == ' ') break }
        if (la != -1) { val nt = t.substring(0, la + 1) + u + " " + t.substring(cp); messageInput.setText(nt); messageInput.setSelection(la + u.length + 1) }
        mentionContainer.isVisible = false
    }

    private fun showSelectionToolbar(count: Int) {
        selectionMode = true; invalidateOptionsMenu(); toolbarContent.isVisible = false; selectionToolbar.isVisible = true; selectionCountText.text = count.toString()
        setToolbarNavigationIcon(R.drawable.ic_close); replyMessage.isVisible = count == 1; forwardMessages.isVisible = count > 0
        try { selectionToolbar.setBackgroundColor(ThemeStore.currentTheme().primaryColor.toColorInt()) } catch (_: Exception) {}
    }
    private fun hideSelectionToolbar() { if (!selectionMode) return; selectionMode = false; adapter.toggleSelectionMode(false); invalidateOptionsMenu(); selectionToolbar.isVisible = false; toolbarContent.isVisible = true; setToolbarNavigationIcon(R.drawable.ic_back_arrow) }
    private fun showReplyPreview(m: Message) { replyingTo = m; replyPreview.isVisible = true; replyUser.text = m.user; replyText.text = if (m.imageUrl.isNotEmpty()) "Photo" else m.text; messageInput.requestFocus() }
    private fun hideReplyPreview() { replyingTo = null; replyPreview.isVisible = false }
    private fun enterSelectionMode(m: Message) { val p = adapter.currentList.indexOf(m); if (p != -1) { adapter.toggleSelectionMode(true); adapter.toggleSelection(p); showSelectionToolbar(adapter.getSelectedMessages().size) } }
    private fun copySelectedMessages() { val sm = adapter.getSelectedMessages(); val tc = sm.joinToString("\n\n") { "${it.user}: ${it.text}" }; (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("messages", tc)); showToast(getString(R.string.copied_to_clipboard)); hideSelectionToolbar() }
    private fun replyToSelectedMessage() { val sm = adapter.getSelectedMessages(); if (sm.size == 1) { showReplyPreview(sm[0]); hideSelectionToolbar() } }

    private fun deleteSelectedMessages() {
        val sm = adapter.getSelectedMessages()
        val sheet = StandardBottomSheet(this, R.layout.dialog_delete_messages)
        sheet.setTitle(getString(R.string.delete_messages_title))

        sheet.findViewById<TextView>(R.id.messageText)?.text = 
            getString(R.string.delete_messages_confirm, sm.size)

        sheet.findViewById<View>(R.id.btnCancel)?.setOnClickListener { sheet.dismiss() }
        sheet.findViewById<View>(R.id.btnDelete)?.setOnClickListener {
            sm.forEach { grpcClient.deleteMessage(it) }
            hideSelectionToolbar()
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun forwardSelectedMessages() {
        val sm = adapter.getSelectedMessages(); if (sm.isEmpty()) { hideSelectionToolbar(); return }
        grpcClient.getChats(username) { chats ->
            runOnUiThread {
                val oc = chats.toMutableList(); if (!roomId.startsWith("favorites_")) oc.add(0, ChatInfo(id = "favorites_$username", name = getString(R.string.favorites), type = "favorites"))
                val f = oc.filter { it.id != roomId }; if (f.isEmpty()) { showToast(getString(R.string.no_other_chats)); return@runOnUiThread }
                
                val sheet = WidgetManager.getOrCreate("forward_sheet") { ListBottomSheet(this) }
                    .setTitle(getString(R.string.forward_to))
                
                val forwardAdapter = lavender.client.android.ui.adapter.ForwardChatAdapter(
                    chats = f,
                    currentUsername = username,
                    avatarCache = grpcClient.getAvatarCache(),
                    onChatSelected = { target ->
                        sheet.dismiss()
                        sm.forEach { m ->
                            grpcClient.sendMessage(Message(
                                user = username,
                                text = m.text,
                                timestamp = System.currentTimeMillis(),
                                roomId = target.id,
                                imageUrl = m.imageUrl,
                                voiceUrl = m.voiceUrl,
                                duration = m.duration,
                                userId = grpcClient.getUserId() ?: ""
                            ))
                        }
                        showToast(getString(R.string.messages_forwarded))
                        hideSelectionToolbar()
                    }
                )
                sheet.setAdapter(forwardAdapter)
                sheet.show()
            }
        }
    }

    private fun starSelectedMessages() { val sm = adapter.getSelectedMessages(); val uid = grpcClient.getUserId() ?: ""; if (uid.isEmpty()) { showToast("User ID not loaded. Please wait."); return }; var c = 0; sm.forEach { m -> grpcClient.addFavorite(uid, m.id) { _, _ -> c++; if (c == sm.size) runOnUiThread { showToast(getString(R.string.added_to_favorites)); hideSelectionToolbar() } } } }
    private fun clearCacheForCurrentRoom() { lifecycleScope.launch(Dispatchers.IO) { try { lavender.client.android.data.db.AppDatabase.getDatabase(this@NewChatActivity).messageDao().clearRoom(roomId) } catch (_: Exception) {} } }
    private var typingJob: Job? = null

    private fun sendMessage(text: String, imageUrl: String) {
        typingJob?.cancel(); if (isTypingSignalSent) { isTypingSignalSent = false; grpcClient.sendTypingSignal(username, false) }
        val et = when { text.isEmpty() && imageUrl.isEmpty() -> "Message"; imageUrl.isNotEmpty() && text.isEmpty() -> ""; else -> text }
        val msg = Message(id = java.util.UUID.randomUUID().toString(), user = username, text = et, timestamp = System.currentTimeMillis(), roomId = roomId, imageUrl = imageUrl, repliedToMessageId = replyingTo?.id ?: "", repliedToUser = replyingTo?.user ?: "", repliedToText = replyingTo?.text ?: "", userId = grpcClient.getUserId() ?: "", isSent = false)
        grpcClient.addLocalMessage(msg); grpcClient.sendMessage(msg)
        if (roomId.startsWith("favorites_")) viewModel.markRead(username)
        grpcClient.deleteDraft(roomId); messageInput.text.clear(); hideReplyPreview(); sendButton.isVisible = false; audioButton.isVisible = true
    }

    private fun showReactionsDialog(m: Message) {
        val sheet = StandardBottomSheet(this, R.layout.dialog_reactions)
        val container = sheet.findViewById<LinearLayout>(R.id.reactionsContainer)
        
        listOf("👍", "💯", "🔥", "✅", "❤️", "😂", "😮", "😢", "🙏").forEach { e ->
            val tv = TextView(this).apply {
                text = e
                textSize = 30f
                setPadding(16, 8, 16, 8)
                val v2 = TypedValue()
                this@NewChatActivity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, v2, true)
                setBackgroundResource(v2.resourceId)
                setOnClickListener {
                    grpcClient.setReaction(m.id, username, e)
                    sheet.dismiss()
                }
            }
            container?.addView(tv)
        }

        sheet.findViewById<View>(R.id.menuReply)?.setOnClickListener { 
            sheet.dismiss()
            showReplyPreview(m) 
        }
        
        sheet.findViewById<View>(R.id.menuCopy)?.setOnClickListener { 
            sheet.dismiss()
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("message", m.text))
            showToast(getString(R.string.copied_to_clipboard))
        }
        
        val edit = sheet.findViewById<View>(R.id.menuEdit)
        if (m.user == username) {
            edit?.isVisible = true
            edit?.setOnClickListener { 
                sheet.dismiss()
                showEditMessageDialog(m) 
            }
        } else {
            edit?.isVisible = false
        }
        
        sheet.findViewById<View>(R.id.menuDelete)?.setOnClickListener { 
            sheet.dismiss()
            grpcClient.deleteMessage(m) 
        }
        
        sheet.show()
    }

    private fun showEditMessageDialog(m: Message) {
        val sheet = StandardBottomSheet(this, R.layout.dialog_edit_message)
        sheet.setTitle(getString(R.string.edit_message))
        
        val edit = sheet.findViewById<EditText>(R.id.editMessageInput)
        val cancel = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val save = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)

        edit?.setText(m.text)
        edit?.setSelection(m.text.length)
        edit?.requestFocus()

        cancel?.setOnClickListener { sheet.dismiss() }
        save?.setOnClickListener {
            val nt = edit?.text.toString().trim()
            if (nt.isNotEmpty() && nt != m.text) {
                grpcClient.editMessage(m.id, nt) { s, msg ->
                    if (!s) runOnUiThread { showToast(msg) }
                }
            }
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun showAudioRecordingView() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1001)
            return
        }
        val sheet = StandardBottomSheet(this)
        val recording = AudioRecordingView(this)
        recording.applyCustomTheme(ThemeMappers.toProto(ThemeStore.currentTheme()))
        sheet.setContent(recording)
        
        recording.setOnRecordingFinished { file, dur ->
            sheet.dismiss()
            file?.let { uploadAudio(it, dur) }
        }
        recording.setOnRecordingCancelled { sheet.dismiss() }
        sheet.show()
    }

    private fun uploadAudio(file: File, duration: Int) {
        uploadProgressBar.isVisible = true; audioButton.isVisible = false
        lifecycleScope.launch { val result = AudioUploader(this@NewChatActivity).uploadAudio(file, duration); runOnUiThread { uploadProgressBar.isVisible = false; audioButton.isVisible = true; if (result.success && result.url.isNotEmpty() && !result.url.contains("404")) grpcClient.sendMessage(Message(user = username, text = "Voice message", timestamp = System.currentTimeMillis(), roomId = roomId, voiceUrl = result.url, duration = result.duration, userId = grpcClient.getUserId() ?: "")) else showToast("Failed to upload audio: ${if (result.url.contains("404")) "Server error 404" else result.error}") } }
    }

    private fun showImagePreview() {
        imagePreviewContainer.removeAllViews()
        for ((index, uri) in selectedImageUris.withIndex()) {
            val v = layoutInflater.inflate(R.layout.image_preview_container, imagePreviewContainer, false); val iv = v.findViewById<ImageView>(R.id.previewImage); val rb = v.findViewById<ImageButton>(R.id.removeImageButton)
            com.bumptech.glide.Glide.with(this).load(uri).centerCrop().into(iv); rb.setOnClickListener { selectedImageUris.removeAt(index); showImagePreview() }; imagePreviewContainer.addView(v)
        }
        imagePreviewScroll.isVisible = selectedImageUris.isNotEmpty(); val hasT = messageInput.text.trim().isNotEmpty(); val hasI = selectedImageUris.isNotEmpty(); sendButton.isVisible = hasT || hasI; audioButton.isVisible = !hasT && !hasI
    }
    
    private fun sendSelectedImages() {
        val text = messageInput.text.toString().trim(); val urls = mutableListOf<String>(); var count = 0; val total = selectedImageUris.size
        uploadProgressContainer.isVisible = true; uploadProgressText.text = "Загрузка изображений... (0/$total)"; uploadProgressBar.progress = 0
        selectedImageUris.forEach { uri ->
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val body = MultipartBody.Part.createFormData("image", getFileName(uri) ?: "image.jpg", bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                val req = Request.Builder().url("http://159.195.38.145:8082/upload-image").post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(body).build()).build()
                OkHttpClient().newCall(req).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { runOnUiThread { uploadProgressContainer.isVisible = false; showToast("Upload failed") } }
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        val rb = response.body.string(); if (!response.isSuccessful || rb.contains("404")) { runOnUiThread { uploadProgressContainer.isVisible = false; showToast("Server error: 404") }; return }
                        val url = if (rb.contains("\"url\":")) try { org.json.JSONObject(rb).getString("url") } catch (_: Exception) { "" } else if (rb.startsWith("http")) rb else ""
                        if (url.isNotEmpty() && !url.contains("404")) urls.add(url); count++
                        runOnUiThread { uploadProgressBar.progress = ((count.toFloat() / total) * 100).toInt(); uploadProgressText.text = "Загрузка изображений... ($count/$total)"
                            if (count == total) { uploadProgressContainer.isVisible = false; if (urls.isNotEmpty()) sendGalleryMessage(text, urls) else showToast("Upload failed") }
                        }
                    }
                })
            }
        }
    }
    
    private fun sendGalleryMessage(text: String, imageUrls: List<String>) {
        typingJob?.cancel(); if (isTypingSignalSent) { isTypingSignalSent = false; grpcClient.sendTypingSignal(username, false) }
        val et = when { text.isEmpty() && imageUrls.isEmpty() -> "Message"; imageUrls.isNotEmpty() && text.isEmpty() -> ""; else -> text }
        val msg = Message(id = java.util.UUID.randomUUID().toString(), user = username, text = et, timestamp = System.currentTimeMillis(), roomId = roomId, imageUrl = imageUrls.firstOrNull() ?: "", imageUrls = imageUrls, repliedToMessageId = replyingTo?.id ?: "", repliedToUser = replyingTo?.user ?: "", repliedToText = replyingTo?.text ?: "", userId = grpcClient.getUserId() ?: "", isSent = false)
        grpcClient.addLocalMessage(msg); grpcClient.sendMessage(msg)
        if (roomId.startsWith("favorites_")) viewModel.markRead(username)
        grpcClient.deleteDraft(roomId); messageInput.text.clear(); selectedImageUris.clear(); imagePreviewScroll.isVisible = false; hideReplyPreview(); sendButton.isVisible = false; audioButton.isVisible = true
    }

    private fun uploadFiles(uris: List<Uri>, isImage: Boolean) {
        if (isImage && uris.size > 1) { selectedImageUris.addAll(uris); showImagePreview(); return }
        uris.forEach { uri ->
            uploadProgressBar.isVisible = true; val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val fn = getFileName(uri) ?: (if (isImage) "image.jpg" else "file")
                val body = MultipartBody.Part.createFormData(if (isImage) "image" else "file", fn, bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                val req = Request.Builder().url("http://159.195.38.145:8082/${if (isImage) "upload-image" else "upload-file"}").post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(body).build()).build()
                OkHttpClient().newCall(req).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { runOnUiThread { uploadProgressBar.isVisible = false; showToast("Upload failed") } }
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        val rb = response.body.string(); if (!response.isSuccessful || rb.contains("404")) { runOnUiThread { uploadProgressBar.isVisible = false; showToast("Server error: 404") }; return }
                        val url = if (rb.contains("\"url\":")) try { org.json.JSONObject(rb).getString("url") } catch (_: Exception) { "" } else if (rb.startsWith("http")) rb else ""
                        runOnUiThread { uploadProgressBar.isVisible = false; if (url.isNotEmpty() && !url.contains("404")) { if (isImage) sendGalleryMessage("", listOf(url)) else sendMessage("File: $fn\n$url", "") } else showToast("Upload failed") }
                    }
                })
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var r: String? = null; if (uri.scheme == "content") contentResolver.query(uri, null, null, null, null)?.use { if (it.moveToFirst()) r = it.getString(it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME).takeIf { idx -> idx != -1 } ?: -1) }
        if (r == null) { r = uri.path; val c = r?.lastIndexOf('/') ?: -1; if (c != -1) r = r?.substring(c + 1) }
        return r
    }

    private fun updateSubtitle(onlineUsers: List<String>, isConnected: Boolean, typists: List<String>) {
        if (roomId.startsWith("favorites_")) { toolbarSubtitle.isVisible = true; toolbarSubtitle.text = getString(R.string.favorites); toolbarSubtitle.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnPrimary)); return }
        val cop = getThemeColor(com.google.android.material.R.attr.colorOnPrimary); val cg = getColor(android.R.color.holo_green_light); toolbarSubtitle.isVisible = true; toolbarSubtitle.setTypeface(null, android.graphics.Typeface.NORMAL)
        when {
            !isConnected -> { toolbarSubtitle.text = getString(R.string.connecting); toolbarSubtitle.setTextColor(cop) }
            typists.isNotEmpty() -> { toolbarSubtitle.text = if (typists.size == 1) getString(R.string.user_is_typing, typists.first()) else getString(R.string.users_are_typing, typists.size); toolbarSubtitle.setTextColor(cop); toolbarSubtitle.setTypeface(null, android.graphics.Typeface.ITALIC) }
            isDirect -> {
                val other = getOtherParticipant(); val isO = onlineUsers.contains(other)
                if (isO) { toolbarSubtitle.text = getString(R.string.connected); toolbarSubtitle.setTextColor(cg) }
                else {
                    val lst = if (cachedLastSeenText != null) cachedLastSeenText else {
                        val otherNonNull = other ?: return@updateSubtitle
                        grpcClient.fetchUserId(otherNonNull) { uid, s -> if (s && !uid.isNullOrEmpty()) grpcClient.getUserProfile(uid) { p -> if (p?.lastSeenAt != null) { cachedLastSeenText = ProtoUtils.formatLastSeen(p.lastSeenAt, this@NewChatActivity); runOnUiThread { if (!onlineUsers.contains(other)) { toolbarSubtitle.text = cachedLastSeenText; toolbarSubtitle.setTextColor(cop) } } } } }
                        getString(R.string.offline)
                    }
                    toolbarSubtitle.text = lst; toolbarSubtitle.setTextColor(cop)
                }
            }
            else -> updateGroupSubtitle(onlineUsers)
        }
    }

    private var cachedOtherUser: String? = null
    private var cachedLastSeenText: String? = null

    private fun getOtherParticipant(): String? { if (cachedOtherUser != null) return cachedOtherUser; return try { JSONArray(participantsJson).let { a -> (0 until a.length()).asSequence().map { a.getString(it) }.find { it != username }.also { cachedOtherUser = it } } } catch (_: Exception) { null } }

    private fun getThemeColor(attr: Int): Int { val v = TypedValue(); this@NewChatActivity.theme.resolveAttribute(attr, v, true); return v.data }

    private fun updateGroupSubtitle(onlineUsers: List<String>) {
        if (isDirect) return
        try {
            val a = JSONArray(participantsJson); val t = a.length(); var o = 0; for (i in 0 until t) if (onlineUsers.contains(a.getString(i))) o++
            toolbarSubtitle.isVisible = true; toolbarSubtitle.text = getString(R.string.participants_online_count, t, o); toolbarSubtitle.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnPrimary))
        } catch (_: Exception) { toolbarSubtitle.isVisible = false }
    }

    private fun retryMessage(message: Message) {
        showToast(getString(R.string.checking_server)); grpcClient.loadHistory(roomId) { runOnUiThread { val updated = grpcClient.messages.value.find { it.id == message.id }; if (updated == null || !updated.isSent) { showToast(getString(R.string.resending)); grpcClient.sendMessage(message) } else showToast(getString(R.string.message_already_sent)) } }
    }

    private fun showToast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun syncChatListIfNeeded() {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE); val local = prefs.getLong("chat_list_version", 0L); val u = grpcClient.getCurrentUsername() ?: return
        grpcClient.getChatListVersion(u) { server -> if (server > local) grpcClient.getChats(u) { prefs.edit { putLong("chat_list_version", server) } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); loadDataFromIntent()
        grpcClient.setRoomId(roomId); grpcClient.clearMessages(); grpcClient.loadHistory(roomId); viewModel.switchRoom(roomId); loadDraft()
    }

    private fun loadDraft() {
        if (roomId.isEmpty() || username.isEmpty() || grpcClient.getUserId() == null) return
        grpcClient.getDraft(roomId) { dt, rmi, ru, rt, hd -> runOnUiThread { if (hd && (dt.isNotEmpty() || rmi.isNotEmpty())) { if (dt.isNotEmpty()) { messageInput.setText(dt); messageInput.setSelection(dt.length) }; if (rmi.isNotEmpty()) { replyingTo = Message(id = rmi, user = ru, text = rt, timestamp = System.currentTimeMillis(), roomId = roomId); showReplyPreview(replyingTo!!) } } } }
    }

    private fun saveDraft() {
        if (roomId.isEmpty() || username.isEmpty()) return
        if (grpcClient.getUserId() == null) { ensureUserIdSet { saveDraft() }; return }
        val dt = messageInput.text?.toString()?.trim() ?: ""; val hr = replyingTo != null
        if (dt.isNotEmpty() || hr) grpcClient.saveDraft(roomId = roomId, draftText = dt, repliedToMessageId = replyingTo?.id ?: "", repliedToUser = replyingTo?.user ?: "", repliedToText = replyingTo?.text ?: "") { _, _ -> }
        else if (grpcClient.getUserId() != null) grpcClient.deleteDraft(roomId)
    }

    private fun ensureUserIdSet(onReady: () -> Unit) {
        val s = getSavedUserId(); if (s != null) { grpcClient.setUserId(s); onReady() }
        else if (username.isNotEmpty()) grpcClient.fetchUserId(username) { uid, f -> if (f && uid != null) { saveUserId(uid); grpcClient.setUserId(uid) }; runOnUiThread { onReady() } }
        else onReady()
    }

    private fun getSavedUserId(): String? = getSharedPreferences("lavender_prefs", MODE_PRIVATE).getString("user_id", null)
    private fun saveUserId(userId: String) { getSharedPreferences("lavender_prefs", MODE_PRIVATE).edit { putString("user_id", userId) } }

    override fun onResume() { super.onResume(); ThemeStore.refresh(this, username); lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false; if (grpcClient.shouldForceReconnect()) { val sa = intent.getStringExtra("SERVER_ADDRESS") ?: getSharedPreferences("lavender_prefs", MODE_PRIVATE).getString("server_address", ""); if (!sa.isNullOrEmpty()) { val p = sa.split(":"); grpcClient.connect(p[0], false, p.getOrNull(1)?.toIntOrNull() ?: 50051, this, true) } }; fetchChatMetadataIfNeeded() }
    override fun onPause() { super.onPause(); lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = true; if (isTypingSignalSent) { isTypingSignalSent = false; grpcClient.sendTypingSignal(username, false) }; saveDraft() }

    // ======= E2EE Methods =======

    private fun initE2EE() {
        if (!isSecret) return
        val publicKey = lavender.client.android.data.crypto.E2EEManager.getPublicKeyBase64(this)
        grpcClient.exchangeSecretKey(roomId, publicKey) { success, peerKey, peerHasKey ->
            runOnUiThread {
                if (success && peerHasKey && peerKey.isNotEmpty()) {
                    // Derive shared secret
                    lavender.client.android.data.crypto.E2EEManager.deriveAndStoreSharedSecret(this, roomId, peerKey)
                    secretKeyExchanged = true
                    toolbarSubtitle.text = getString(R.string.e2ee_verified)
                    android.util.Log.d("E2EE", "Key exchange complete for chat: $roomId")
                } else {
                    toolbarSubtitle.text = getString(R.string.e2ee_pending)
                    // Retry key exchange after delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ initE2EE() }, 3000)
                }
            }
        }
    }

    /**
     * Handle incoming E2EE message from the chat stream.
     * Called from RealGrpcClient message handler when isE2ee flag is set.
     */
    fun handleIncomingE2EEMessage(msg: lavender.client.android.data.models.Message) {
        if (!msg.isE2EE || msg.e2eePayload.isEmpty()) return
        val decrypted = lavender.client.android.data.crypto.E2EEManager.decryptMessage(this, roomId, msg.e2eePayload)
        if (decrypted != null) {
            val decryptedMsg = msg.copy(text = decrypted, isE2EE = false)
            // Add to message list
            runOnUiThread {
                val current = grpcClient.messages.value.toMutableList()
                current.add(decryptedMsg)
                // Update via ViewModel
            }
        }
    }

    /**
     * Send an E2EE-encrypted message.
     */
    private fun sendE2EEMessage(plainText: String) {
        if (!isSecret) {
            // Fallback to normal send
            return
        }
        if (!secretKeyExchanged) {
            showToast(getString(R.string.e2ee_not_ready))
            return
        }
        val encrypted = lavender.client.android.data.crypto.E2EEManager.encryptMessage(this, roomId, plainText)
        if (encrypted != null) {
            grpcClient.sendE2EEMessage(roomId, encrypted)
        } else {
            showToast("E2EE encryption failed")
        }
    }
}
