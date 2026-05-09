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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import lavender.client.android.audio.AudioUploader
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.data.session.SessionManager
import lavender.client.android.ui.adapter.MentionAdapter
import lavender.client.android.ui.adapter.MessageAdapter
import lavender.client.android.ui.adapter.MessageSwipeController
import lavender.client.android.ui.audio.AudioRecordingView
import lavender.client.android.ui.chat.ChatViewModel
import lavender.client.android.ui.chat.ChatViewModelFactory
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.data.ThemeMappers
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

class NewChatActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru" // Default to Russian for first launch
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
    private lateinit var toolbarLoadingIcon: ImageView
    private lateinit var groupParticipantsContainer: LinearLayout
    private lateinit var selectionToolbar: LinearLayout
    private lateinit var selectionCountText: TextView
    private lateinit var copyMessages: ImageButton
    private lateinit var replyMessage: ImageButton
    private lateinit var deleteMessages: ImageButton
    private lateinit var forwardMessages: ImageButton
    private lateinit var emojiButton: ImageButton
    private lateinit var toolbarContent: View
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var attachButton: ImageButton
    private lateinit var audioButton: ImageButton
    private lateinit var uploadProgressBar: ProgressBar
    private lateinit var audioRecordingView: AudioRecordingView

    private lateinit var replyPreview: View
    private lateinit var replyUser: TextView
    private lateinit var replyText: TextView
    private lateinit var cancelReply: ImageButton
    private lateinit var swipeRefreshLayout: androidx.swiperefreshlayout.widget.SwipeRefreshLayout

    private lateinit var mentionContainer: View
    private lateinit var mentionList: RecyclerView
    private lateinit var mentionAdapter: MentionAdapter

    private lateinit var searchBar: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchNext: ImageButton
    private lateinit var searchPrev: ImageButton
    private lateinit var searchResultsCount: TextView

    private lateinit var adapter: MessageAdapter
    
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
            if (uris.isNotEmpty()) uploadFiles(uris.toList(), isImage = false)
        }
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) currentPhotoUri?.let {
            selectedImageUris.addAll(listOf(it))
            showImagePreview()
            //uploadFiles(listOf(it), isImage = true)
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
        // 1. Настройка прозрачности и темы (до super.onCreate)
        // applySavedColorScheme()
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_chat)

        // 2. Извлекаем данные. Убедись, что loadDataFromIntent() проверяет "roomId" из extras!
        loadDataFromIntent()

        // 3. СИНХРОНИЗАЦИЯ: Говорим клиенту, где мы сейчас.
        // Очищаем старые сообщения, чтобы не было "фантомных" текстов из другого чата.
        grpcClient.setRoomId(roomId)
        grpcClient.clearMessages()

        initViews()

        // 4. Темизация
        ThemeUi.bind(this, username)

        // 5. Инициализация компонентов
        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupListeners()
        setupKeyboardHandling()
        fetchChatMetadataIfNeeded()

        // 6. Запуск логики ViewModel
        viewModel.switchRoom(roomId)

        // Если мы уже READY (например, вернулись из фона), принудительно грузим историю
        if (grpcClient.connectionStatus.value == ConnectionStatus.READY) {
            grpcClient.loadHistory(roomId)
        }

        viewModel.startChat(username, password, "") { _ ->
            viewModel.markRead(username, this)
        }
        
        // Mark as read immediately upon entry
        viewModel.markRead(username, this)

        // Load draft message when entering chat (after ensuring userId is set)
        ensureUserIdSet { loadDraft() }

        // 7. Обработка кнопки "Назад" через When (так чище)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    audioRecordingView.isVisible -> audioRecordingView.cancel()
                    selectionMode -> hideSelectionToolbar()
                    searchBar.isVisible -> hideSearchBar()
                    mentionContainer.isVisible -> mentionContainer.isVisible = false
                    else -> finish()
                }
            }
        })
    }

    private fun fetchChatMetadataIfNeeded() {
        if (participantsJson == "[]" || chatName == "Chat") {
            grpcClient.getChats(username) { chats ->
                val chat = chats.find { it.id == roomId }
                if (chat != null) runOnUiThread {
                    chatName = chat.getDisplayName(username); isDirect = chat.type == "direct"; participantsJson = chat.participants; creator = chat.creator; chatAvatarUrl = chat.avatarUrl; chatFullAvatarUrl = chat.fullAvatarUrl
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
            toolbar.updatePadding(top = systemBars.top)
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            bottomPanel.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = if (isImeVisible) imeInsets.bottom else systemBars.bottom }
            bottomPanelContent.updatePadding(bottom = 4.dpToPx()); insets
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar); toolbarTitle = findViewById(R.id.toolbarTitle); toolbarSubtitle = findViewById(R.id.toolbarSubtitle)
        toolbarAvatar = findViewById(R.id.toolbarAvatar); toolbarLoadingIcon = findViewById(R.id.toolbarLoadingIcon)
        groupParticipantsContainer = findViewById(R.id.groupParticipantsContainer); selectionToolbar = findViewById(R.id.selectionToolbar)
        selectionCountText = findViewById(R.id.selectionCountText); copyMessages = findViewById(R.id.copyMessages)
        replyMessage = findViewById(R.id.replyMessage); deleteMessages = findViewById(R.id.deleteMessages); forwardMessages = findViewById(R.id.forwardMessages)
        toolbarContent = findViewById(R.id.toolbarContent); messagesRecyclerView = findViewById(R.id.messagesRecyclerView); swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        messageInput = findViewById(R.id.messageInput); sendButton = findViewById(R.id.sendButton); attachButton = findViewById(R.id.attachButton); audioButton = findViewById(R.id.audioButton)
        uploadProgressBar = findViewById(R.id.uploadProgressBar); audioRecordingView = findViewById(R.id.audioRecordingView); replyPreview = findViewById(R.id.replyPreview)
        replyUser = findViewById(R.id.replyUser); replyText = findViewById(R.id.replyText); cancelReply = findViewById(R.id.cancelReply); emojiButton = findViewById(R.id.emojiButton)
        mentionContainer = findViewById(R.id.mentionContainer); mentionList = findViewById(R.id.mentionList); mentionAdapter = MentionAdapter { insertMention(it) }
        mentionList.layoutManager = LinearLayoutManager(this); mentionList.adapter = mentionAdapter
        searchBar = findViewById(R.id.searchBar); searchInput = findViewById(R.id.searchInput)
        searchNext = findViewById(R.id.searchNext); searchPrev = findViewById(R.id.searchPrev); searchResultsCount = findViewById(R.id.searchResultsCount)
        imagePreviewScroll = findViewById(R.id.imagePreviewScroll); imagePreviewContainer = findViewById(R.id.imagePreviewContainer)
        audioButton.isVisible = true
    }

    private fun loadDataFromIntent() {
        // 1. Извлекаем Room ID (сначала из Пуша, потом из обычного интента)
        // Если ничего не пришло — по умолчанию идем в "general"
        roomId = intent.getStringExtra("ROOM_ID")
            ?: intent.getStringExtra("roomId")
                    ?: (if (roomId.isEmpty()) "general" else roomId)

        // 2. Безопасное извлечение USERNAME и PASSWORD
        // Если интент пустой (например, при открытии с иконки), берем данные из SharedPreferences
        val incomingUser = intent.getStringExtra("USERNAME")
        val incomingPass = intent.getStringExtra("PASSWORD")

        if (!incomingUser.isNullOrEmpty()) {
            username = incomingUser
        }
        if (!incomingPass.isNullOrEmpty()) {
            password = incomingPass
        }

        // если после интента данных всё еще нет, тянем из префов или сессии
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

        // 3. Остальные поля (обновляем только если они переданы)
        intent.getStringExtra("CHAT_NAME")?.let { chatName = it }

        // Для Boolean: если в интенте нет ключа, сохраняем текущее значение isDirect
        isDirect = intent.getBooleanExtra("IS_DIRECT", isDirect)

        intent.getStringExtra("PARTICIPANTS")?.let { participantsJson = it }
        intent.getStringExtra("CREATOR")?.let { creator = it }
        intent.getStringExtra("AVATAR_URL")?.let { chatAvatarUrl = it }
        intent.getStringExtra("FULL_AVATAR_URL")?.let { chatFullAvatarUrl = it }

        android.util.Log.d("ChatData", "Room: $roomId, User: $username, Direct: $isDirect")
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar); supportActionBar?.setDisplayShowTitleEnabled(false)
        setToolbarNavigationIcon(R.drawable.ic_back_arrow) // Set initial icon
        toolbar.setNavigationOnClickListener {
            if (selectionMode) hideSelectionToolbar()
            else if (searchBar.isVisible) hideSearchBar()
            else finish()
        }
        toolbar.layoutParams.height = resources.getDimensionPixelSize(R.dimen.custom_toolbar_height)

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
            com.bumptech.glide.Glide.with(this).load(effectiveAvatarUrl ?: R.drawable.ic_default_avatar)
                .placeholder(R.drawable.ic_default_avatar).circleCrop().into(toolbarAvatar)
        } else {
            toolbarAvatar.isVisible = false; groupParticipantsContainer.isVisible = true; setupGroupAvatars()
        }

        toolbarTitle.text = chatName
        toolbarContent.setOnClickListener {
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
    }

    private fun setToolbarNavigationIcon(iconResId: Int) {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationIcon(iconResId)
        toolbar.navigationIcon?.let {
            val wrapped = DrawableCompat.wrap(it)
            val theme = ThemeStore.currentTheme()
            val iconColor = try { theme.textPrimaryColor.toColorInt() } catch (_: Exception) { ContextCompat.getColor(this, R.color.white) }
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
            onMessageClick = { showReactionsDialog(it) },
            onSelectionChanged = { if (it > 0) showSelectionToolbar(it) else hideSelectionToolbar() },
            onMessageLongClick = { enterSelectionMode(it) }
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
        swipeRefreshLayout.setOnRefreshListener { fullReloadHistory() }
    }

    private fun setupObservers() {
        viewModel = ViewModelProvider(this, ChatViewModelFactory(roomId))[ChatViewModel::class.java]

        // 1. Наблюдатель за сообщениями (скролл и отметки о прочтении)
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
                        if (roomMessages.isNotEmpty() && (isFirstLoad || (hasNewMessages && wasAtBottom))) {
                            messagesRecyclerView.scrollToPosition(roomMessages.size - 1)
                        }
                    }

                    if (hasNewMessages && roomMessages.any { it.user != username && !it.isRead }) {
                        viewModel.markRead(username, this@NewChatActivity)
                    }
                    lastMessageCount = roomMessages.size
                }
            }
        }

        // 2. Объединенный наблюдатель: Сеть + Юзеры + Тайпинг
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    grpcClient.users,
                    grpcClient.connectionStatus,
                    grpcClient.typingUsers
                ) { onlineUsers, status, typingMap ->
                    // Фильтруем печатающих (кроме себя) прямо в потоке
                    val currentTypists = typingMap[roomId]?.filter { it != username } ?: emptyList()
                    Triple(onlineUsers, status, currentTypists)
                }.collect { (onlineUsers, status, currentTypists) ->
                    val isConnected = status == ConnectionStatus.READY

                    // Обновляем весь заголовок одной функцией
                    updateSubtitle(onlineUsers, isConnected, currentTypists)

                    if (isConnected) {
                        syncChatListIfNeeded()
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean { menuInflater.inflate(R.menu.chat_menu, menu); return true }
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_search)?.isVisible = !selectionMode
        
        // Apply custom theme color to search icon
        val iconColor = run {
            val customTheme = ThemeStore.currentTheme()
            try {
                customTheme.onPrimaryColor.toColorInt()
            } catch (_: Exception) {
                val typedValue = TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
                typedValue.data
            }
        }
        
        menu.findItem(R.id.action_search)?.iconTintList = ColorStateList.valueOf(iconColor)
        
        // Style menu items with custom theme
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
            else -> super.onOptionsItemSelected(item)
        }
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
        attachButton.setOnClickListener {
            showAttachmentSheet()
        }
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                handleMention(s)
                if (!isTypingSignalSent) {
                    isTypingSignalSent = true; grpcClient.sendTypingSignal(username, true)
                    lifecycleScope.launch { delay(3000); isTypingSignalSent = false }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        cancelReply.setOnClickListener { hideReplyPreview() }
        audioButton.setOnClickListener { showAudioRecordingView() }
        emojiButton.setOnClickListener { showEmojiPicker() }

        copyMessages.setOnClickListener { copySelectedMessages() }
        replyMessage.setOnClickListener { replyToSelectedMessage() }
        deleteMessages.setOnClickListener { deleteSelectedMessages() }
        forwardMessages.setOnClickListener { forwardSelectedMessages() }
    }

    private fun showEmojiPicker() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_emoji_picker, null)
        val emojiGrid = dialogView.findViewById<android.widget.GridLayout>(R.id.emojiGrid)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        val customTheme = ThemeStore.currentTheme()
        try {
            val shapeDrawable = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
                floatArrayOf(18f, 18f, 18f, 18f, 18f, 18f, 18f, 18f), null, null
            ))
            shapeDrawable.paint.color = customTheme.backgroundColor.toColorInt()
            dialogView.background = shapeDrawable
        } catch (_: Exception) {}

        val emojis = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
            "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
            "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
            "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
            "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤔",
            "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦",
            "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴",
            "🤢", "🤮", "🤧", "🥵", "🥶", "😷", "🤒", "🤕", "🤑", "🤠",
            "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽",
            "👾", "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀",
            "😿", "😾", "👋", "🤚", "🖐", "✋", "🖖", "👌", "🤏", "✌️",
            "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️",
            "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲",
            "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦵", "🦿", "🦶"
        )

        val size = (48 * resources.displayMetrics.density).toInt()
        for (emoji in emojis) {
            val textView = TextView(this).apply {
                text = emoji
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(size, size)
                val typedValue = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
                setBackgroundResource(typedValue.resourceId)
                setOnClickListener {
                    val cursorPosition = messageInput.selectionStart
                    val currentText = messageInput.text.toString()
                    val newText = currentText.substring(0, cursorPosition) + emoji + currentText.substring(cursorPosition)
                    messageInput.setText(newText)
                    messageInput.setSelection(cursorPosition + emoji.length)
                    dialog.dismiss()
                }
            }
            emojiGrid.addView(textView)
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showAttachmentSheet() {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_attachments, null)

        val customTheme = ThemeStore.currentTheme()
        try {
            val bgColor = customTheme.backgroundColor.toColorInt()
            val textColor = customTheme.textPrimaryColor.toColorInt()
            val primColor = customTheme.primaryColor.toColorInt()
            view.setBackgroundColor(bgColor)

            // Color the top handle with primaryColor
            view.findViewById<View>(R.id.dragHandle)?.backgroundTintList = ColorStateList.valueOf(primColor)

            // Theme all items - icons with primaryColor, text with textPrimaryColor
            val itemIds = listOf(R.id.attachCamera, R.id.attachGallery, R.id.attachFile, R.id.attachLocation)
            itemIds.forEach { id ->
                view.findViewById<LinearLayout>(id)?.let { layout ->
                    for (i in 0 until layout.childCount) {
                        val child = layout.getChildAt(i)
                        if (child is TextView) child.setTextColor(textColor)
                        if (child is ImageView) {
                            child.imageTintList = ColorStateList.valueOf(primColor)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback for built-in themes: match ChatListActivity sheet styling
            try {
                val typedValue = TypedValue()

                // Set background to match ChatListActivity (colorSurfaceContainer)
                theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
                val bgColor = typedValue.data
                view.setBackgroundColor(bgColor)

                // Use colorPrimary for handle and icons
                theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                val primaryColor = typedValue.data
                view.findViewById<View>(R.id.dragHandle)?.backgroundTintList = ColorStateList.valueOf(primaryColor)

                // Color icons with primaryColor
                val itemIds = listOf(R.id.attachCamera, R.id.attachGallery, R.id.attachFile, R.id.attachLocation)
                itemIds.forEach { id ->
                    view.findViewById<LinearLayout>(id)?.let { layout ->
                        for (i in 0 until layout.childCount) {
                            val child = layout.getChildAt(i)
                            if (child is ImageView) child.imageTintList = ColorStateList.valueOf(primaryColor)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        
        view.findViewById<LinearLayout>(R.id.attachCamera).setOnClickListener {
            bottomSheet.dismiss()
            currentPhotoUri = createImageUri()
            currentPhotoUri?.let { takePhotoLauncher.launch(it) }
        }
        
        view.findViewById<LinearLayout>(R.id.attachGallery).setOnClickListener {
            bottomSheet.dismiss()
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            pickImageLauncher.launch(intent)
        }
        
        view.findViewById<LinearLayout>(R.id.attachFile).setOnClickListener {
            bottomSheet.dismiss()
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            pickFileLauncher.launch(intent)
        }
        
        view.findViewById<LinearLayout>(R.id.attachLocation).setOnClickListener {
            bottomSheet.dismiss()
            val intent = Intent(this, MapPickerActivity::class.java)
            pickLocationLauncher.launch(intent)
        }
        
        bottomSheet.setContentView(view)
        bottomSheet.show()
    }

    private fun showSearchBar() {
        searchBar.isVisible = true
        toolbarContent.isVisible = false
        setToolbarNavigationIcon(R.drawable.ic_close) // Set close icon for search mode

        // Apply custom theme colors to search bar
        val customTheme = ThemeStore.currentTheme()
        try {
            // Set background to transparent for custom themes
            searchBar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            val textColor = customTheme.textPrimaryColor.toColorInt()
            searchInput.setTextColor(textColor)
            searchInput.setHintTextColor(textColor)
            searchResultsCount.setTextColor(textColor)
        } catch (_: Exception) {
            // Fallback to default behavior if custom theme colors fail
            val typedValue = TypedValue()
            theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
            searchBar.setBackgroundColor(typedValue.data)
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
            val textColor = typedValue.data
            searchInput.setTextColor(textColor)
            searchInput.setHintTextColor(textColor)
            searchResultsCount.setTextColor(textColor)
        }
        
        searchInput.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(searchInput, 0)
    }
    private fun hideSearchBar() {
        searchBar.isVisible = false
        toolbarContent.isVisible = true
        searchInput.text.clear()
        adapter.setSearchHighlight(null)
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchInput.windowToken, 0)
        setToolbarNavigationIcon(R.drawable.ic_back_arrow) // Restore back icon
    }

    private fun handleMention(s: CharSequence?) {
        if (isDirect) return
        val cursorPosition = messageInput.selectionStart; val text = s?.toString() ?: ""; if (cursorPosition <= 0) { mentionContainer.isVisible = false; return }
        var lastAt = -1
        for (i in (cursorPosition - 1) downTo 0) { if (text[i] == '@') { lastAt = i; break }; if (text[i] == ' ') break }
        if (lastAt != -1) {
            val query = text.substring(lastAt + 1, cursorPosition).lowercase(); val participants = try { JSONArray(participantsJson) } catch (_: Exception) { JSONArray() }
            val filteredUsers = mutableListOf<String>(); val avatarCache = grpcClient.getAvatarCache()
            for (i in 0 until participants.length()) { val u = participants.getString(i); if (u != username && u.lowercase().contains(query)) filteredUsers.add(u) }
            if (filteredUsers.isNotEmpty()) { mentionAdapter.setUsers(filteredUsers, avatarCache); mentionContainer.isVisible = true } else mentionContainer.isVisible = false
        } else mentionContainer.isVisible = false
    }

    private fun insertMention(selectedUser: String) {
        val cursorPosition = messageInput.selectionStart; val text = messageInput.text.toString(); var lastAt = -1
        for (i in (cursorPosition - 1) downTo 0) { if (text[i] == '@') { lastAt = i; break }; if (text[i] == ' ') break }
        if (lastAt != -1) { val newText = text.substring(0, lastAt + 1) + selectedUser + " " + text.substring(cursorPosition); messageInput.setText(newText); messageInput.setSelection(lastAt + selectedUser.length + 1) }
        mentionContainer.isVisible = false
    }

    private fun showSelectionToolbar(count: Int) {
        selectionMode = true
        invalidateOptionsMenu()
        toolbarContent.isVisible = false
        selectionToolbar.isVisible = true
        selectionCountText.text = count.toString()
        setToolbarNavigationIcon(R.drawable.ic_close) // Set close icon for selection mode
        replyMessage.isVisible = count == 1
        forwardMessages.isVisible = count > 0
        
        // Apply theme color to selection toolbar
        val theme = ThemeStore.currentTheme()
        try {
            val primaryColor = theme.primaryColor.toColorInt()
            selectionToolbar.setBackgroundColor(primaryColor)
        } catch (_: Exception) {}
    }
    private fun hideSelectionToolbar() {
        if (!selectionMode) return
        selectionMode = false
        adapter.toggleSelectionMode(false)
        invalidateOptionsMenu()
        selectionToolbar.isVisible = false
        toolbarContent.isVisible = true
        setToolbarNavigationIcon(R.drawable.ic_back_arrow) // Restore back icon
    }
    private fun showReplyPreview(message: Message) { replyingTo = message; replyPreview.isVisible = true; replyUser.text = message.user; replyText.text = if (message.imageUrl.isNotEmpty()) "Photo" else message.text; messageInput.requestFocus() }
    private fun hideReplyPreview() { replyingTo = null; replyPreview.isVisible = false }
    private fun enterSelectionMode(message: Message) {
        val position = adapter.currentList.indexOf(message)
        if (position != -1) {
            adapter.toggleSelectionMode(true)
            adapter.toggleSelection(position)
            showSelectionToolbar(adapter.getSelectedMessages().size)
        }
    }

    private fun copySelectedMessages() {
        val selectedMessages = adapter.getSelectedMessages()
        val textToCopy = selectedMessages.joinToString("\n\n") { "${it.user}: ${it.text}" }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("messages", textToCopy))
        showToast(getString(R.string.copied_to_clipboard))
        hideSelectionToolbar()
    }

    private fun replyToSelectedMessage() {
        val selectedMessages = adapter.getSelectedMessages()
        if (selectedMessages.size == 1) {
            showReplyPreview(selectedMessages[0])
            hideSelectionToolbar()
        }
    }

    private fun deleteSelectedMessages() {
        val selectedMessages = adapter.getSelectedMessages()
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_messages, null)
        val titleText = dialogView.findViewById<TextView>(R.id.titleText)
        val messageText = dialogView.findViewById<TextView>(R.id.messageText)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDelete)
        
        messageText.text = getString(R.string.delete_messages_confirm, selectedMessages.size)
        
        // Apply theme colors - custom theme or built-in theme
        val customTheme = ThemeStore.currentTheme()
        try {
            val onPrimaryContainerColor = customTheme.textPrimaryColor.toColorInt()
            titleText.setTextColor(onPrimaryContainerColor)
            messageText.setTextColor(onPrimaryContainerColor)
            btnCancel.setTextColor(onPrimaryContainerColor)
            // Create a shape drawable with custom color for rounded background
            val shapeDrawable = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
                floatArrayOf(18f, 18f, 18f, 18f, 18f, 18f, 18f, 18f), null, null
            ))
            shapeDrawable.paint.color = customTheme.surfaceColor.toColorInt()
            dialogView.background = shapeDrawable
        } catch (_: Exception) {
            // Use Material Design attributes for built-in themes
            val typedValue = TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
            val bgColor = ContextCompat.getColor(this, typedValue.resourceId)
            
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true)
            val textColor = ContextCompat.getColor(this, typedValue.resourceId)
            
            titleText.setTextColor(textColor)
            messageText.setTextColor(textColor)
            btnCancel.setTextColor(textColor)
            
            // Create a shape drawable with built-in theme color for rounded background
            val shapeDrawable = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
                floatArrayOf(18f, 18f, 18f, 18f, 18f, 18f, 18f, 18f), null, null
            ))
            shapeDrawable.paint.color = bgColor
            dialogView.background = shapeDrawable
        }
        
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnDelete.setOnClickListener {
            selectedMessages.forEach { grpcClient.deleteMessage(it) }
            hideSelectionToolbar()
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun forwardSelectedMessages() {
        val selectedMessages = adapter.getSelectedMessages()
        if (selectedMessages.isEmpty()) {
            hideSelectionToolbar()
            return
        }
        
        grpcClient.getChats(username) { chats ->
            runOnUiThread {
                val otherChats = chats.filter { it.id != roomId }
                if (otherChats.isEmpty()) {
                    showToast(getString(R.string.no_other_chats))
                    return@runOnUiThread
                }
                
                val chatNames = otherChats.map { it.getDisplayName(username) }.toTypedArray()
                val customTheme = ThemeStore.currentTheme()
                val builder = AlertDialog.Builder(this)
                    .setTitle(R.string.forward_to)
                    .setItems(chatNames) { _, which ->
                        val targetChat = otherChats[which]
                        selectedMessages.forEach { message ->
                            val forwardedMessage = Message(
                                user = username,
                                text = message.text,
                                timestamp = System.currentTimeMillis(),
                                roomId = targetChat.id,
                                imageUrl = message.imageUrl,
                                voiceUrl = message.voiceUrl,
                                duration = message.duration
                            )
                            grpcClient.sendMessage(forwardedMessage)
                        }
                        showToast(getString(R.string.messages_forwarded))
                        hideSelectionToolbar()
                    }
                    .setNegativeButton(R.string.cancel, null)
                
                try {
                    val textColor = customTheme.textPrimaryColor.toColorInt()
                    val dialog = builder.show()
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    val titleView = dialog.findViewById<TextView>(android.R.id.title)
                    titleView?.setTextColor(textColor)
                } catch (_: Exception) {
                    builder.show()
                }
            }
        }
    }

    private fun fullReloadHistory() { swipeRefreshLayout.isRefreshing = true; grpcClient.clearMessages(); grpcClient.loadHistory(roomId) { runOnUiThread { swipeRefreshLayout.isRefreshing = false } } }
    private fun sendMessage(text: String, imageUrl: String) {
        val effectiveText = when {
            text.isEmpty() && imageUrl.isEmpty() -> "Message"
            imageUrl.isNotEmpty() && text.isEmpty() -> "" // Empty text for image-only messages
            else -> text
        }
        val msg = Message(user = username, text = effectiveText, timestamp = System.currentTimeMillis(), roomId = roomId, imageUrl = imageUrl, repliedToMessageId = replyingTo?.id ?: "", repliedToUser = replyingTo?.user ?: "", repliedToText = replyingTo?.text ?: "")
        grpcClient.sendMessage(msg)
        viewModel.markRead(username, this)
        // Delete draft after successful send
        grpcClient.deleteDraft(roomId)
    }
    private fun showReactionsDialog(message: Message) {
        val root = findViewById<ViewGroup>(android.R.id.content)
        val dialogView = layoutInflater.inflate(R.layout.dialog_reactions, root, false)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        
        val customTheme = ThemeStore.currentTheme()
        try {
            val bgColor = customTheme.backgroundColor.toColorInt()
            val textColor = customTheme.textPrimaryColor.toColorInt()
            val shapeDrawable = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
                floatArrayOf(18f, 18f, 18f, 18f, 18f, 18f, 18f, 18f), null, null
            ))
            shapeDrawable.paint.color = bgColor
            dialogView.background = shapeDrawable
            val childCount = (dialogView as? LinearLayout)?.childCount ?: 0
            for (i in 0 until childCount) {
                val child = (dialogView as LinearLayout).getChildAt(i)
                if (child is LinearLayout) {
                    for (j in 0 until child.childCount) {
                        val subChild = child.getChildAt(j)
                        if (subChild is TextView) subChild.setTextColor(textColor)
                        if (subChild is ImageView) subChild.setColorFilter(textColor)
                    }
                }
            }
        } catch (_: Exception) {}
        
        val reactionsContainer = dialogView.findViewById<LinearLayout>(R.id.reactionsContainer)
        val emojis = listOf("👍", "❤️", "🔥", "😂", "😮", "😢", "🙏", "✅")
        
        for (emoji in emojis) {
            val textView = TextView(this).apply {
                text = emoji
                textSize = 30f
                setPadding(16, 8, 16, 8)
                val typedValue = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
                setBackgroundResource(typedValue.resourceId)
                setOnClickListener {
                    grpcClient.setReaction(message.id, username, emoji)
                    dialog.dismiss()
                }
            }
            reactionsContainer.addView(textView)
        }

        dialogView.findViewById<LinearLayout>(R.id.menuReply).setOnClickListener {
            dialog.dismiss()
            showReplyPreview(message)
        }
        dialogView.findViewById<LinearLayout>(R.id.menuCopy).setOnClickListener {
            dialog.dismiss()
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("message", message.text))
            showToast(getString(R.string.copied_to_clipboard))
        }
        
        val editButton = dialogView.findViewById<LinearLayout>(R.id.menuEdit)
        if (message.user == username) {
            editButton.isVisible = true
            editButton.setOnClickListener {
                dialog.dismiss()
                showEditMessageDialog(message)
            }
        } else {
            editButton.isVisible = false
        }
        
        dialogView.findViewById<LinearLayout>(R.id.menuDelete).setOnClickListener {
            dialog.dismiss()
            grpcClient.deleteMessage(message)
        }
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showEditMessageDialog(message: Message) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_message, null)
        val editText = dialogView.findViewById<EditText>(R.id.editMessageInput)
        editText.setText(message.text)
        editText.setSelection(message.text.length)
        
        val customTheme = ThemeStore.currentTheme()
        try {
            val textColor = customTheme.textPrimaryColor.toColorInt()
            val bgColor = customTheme.backgroundColor.toColorInt()
            editText.setTextColor(textColor)
            editText.setBackgroundColor(bgColor)
            val shapeDrawable = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
                floatArrayOf(18f, 18f, 18f, 18f, 18f, 18f, 18f, 18f), null, null
            ))
            shapeDrawable.paint.color = bgColor
            dialogView.background = shapeDrawable
        } catch (_: Exception) {}
        
        AlertDialog.Builder(this)
            .setTitle(R.string.edit_message)
            .setView(dialogView)
            .setPositiveButton(R.string.change_bio) { d, _ ->
                val newText = editText.text.toString().trim()
                if (newText.isNotEmpty() && newText != message.text) {
                    grpcClient.editMessage(message.id, newText) { success, msg ->
                        if (!success) runOnUiThread { showToast(msg) }
                    }
                }
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAudioRecordingView() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1001)
            return
        }
        audioRecordingView.visibility = View.VISIBLE
        messageInput.visibility = View.GONE
        sendButton.visibility = View.GONE
        attachButton.visibility = View.GONE
        audioButton.visibility = View.GONE

        val customTheme = ThemeStore.currentTheme()
        audioRecordingView.applyCustomTheme(ThemeMappers.toProto(customTheme))

        setupAudioRecordingView()
    }

    private fun setupAudioRecordingView() {
        audioRecordingView.setOnRecordingFinished { file, duration ->
            hideAudioRecordingView()
            file?.let { uploadAudio(it, duration) }
        }
        audioRecordingView.setOnRecordingCancelled {
            hideAudioRecordingView()
        }
    }

    private fun hideAudioRecordingView() {
        audioRecordingView.visibility = View.GONE
        messageInput.visibility = View.VISIBLE
        sendButton.visibility = View.VISIBLE
        attachButton.visibility = View.VISIBLE
        audioButton.visibility = View.VISIBLE
    }
    private fun uploadAudio(file: File, duration: Int) {
        uploadProgressBar.isVisible = true; audioButton.isVisible = false
        lifecycleScope.launch {
            val uploader = AudioUploader(this@NewChatActivity)
            val result = uploader.uploadAudio(file, duration)
            runOnUiThread {
                uploadProgressBar.isVisible = false; audioButton.isVisible = true
                if (result.success && result.url.isNotEmpty() && !result.url.contains("404")) {
                    grpcClient.sendMessage(Message(user = username, text = "Voice message", timestamp = System.currentTimeMillis(), roomId = roomId, voiceUrl = result.url, duration = result.duration))
                } else {
                    showToast("Failed to upload audio: ${if (result.url.contains("404")) "Server error 404" else result.error}")
                }
            }
        }
    }

    private fun showImagePreview() {
        imagePreviewContainer.removeAllViews()
        
        for ((index, uri) in selectedImageUris.withIndex()) {
            val previewView = layoutInflater.inflate(R.layout.image_preview_container, imagePreviewContainer, false)
            val imageView = previewView.findViewById<ImageView>(R.id.previewImage)
            val removeButton = previewView.findViewById<ImageButton>(R.id.removeImageButton)
            
            com.bumptech.glide.Glide.with(this)
                .load(uri)
                .centerCrop()
                .into(imageView)
            
            removeButton.setOnClickListener {
                selectedImageUris.removeAt(index)
                showImagePreview()
            }
            
            imagePreviewContainer.addView(previewView)
        }
        
        imagePreviewScroll.isVisible = selectedImageUris.isNotEmpty()
    }
    
    private fun sendSelectedImages() {
        val messageText = messageInput.text.toString().trim()
        var isFirstImage = true
        
        selectedImageUris.forEach { uri ->
            uploadProgressBar.isVisible = true
            val stream = contentResolver.openInputStream(uri)
            val bytes = stream?.readBytes()
            stream?.close()
            
            if (bytes != null) {
                val fileName = getFileName(uri) ?: "image.jpg"
                val body = MultipartBody.Part.createFormData("image", fileName, bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                val request = Request.Builder().url("http://159.195.38.145:8082/upload-image").post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(body).build()).build()
                
                OkHttpClient().newCall(request).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        runOnUiThread { uploadProgressBar.isVisible = false; showToast("Upload failed") }
                    }
                    
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        val responseBody = response.body.string()
                        if (!response.isSuccessful || responseBody.contains("404")) {
                            runOnUiThread { uploadProgressBar.isVisible = false; showToast("Server error: 404 or ${response.code}") }
                            return
                        }
                        
                        val url = if (responseBody.contains("\"url\":")) {
                            try { org.json.JSONObject(responseBody).getString("url") } catch (_: Exception) { "" }
                        } else if (responseBody.startsWith("http")) responseBody else ""
                        
                        runOnUiThread {
                            uploadProgressBar.isVisible = false
                            if (url.isNotEmpty() && !url.contains("404")) {
                                val textToSend = if (isFirstImage && messageText.isNotEmpty()) messageText else ""
                                // Ensure connection is active before sending message
                                if (grpcClient.connectionState.value) {
                                    sendMessage(textToSend, url)
                                    isFirstImage = false
                                    hideReplyPreview()
                                } else {
                                    android.util.Log.w("NewChatActivity", "Connection lost after image upload, will retry on reconnect")
                                    // Message will be sent when connection is restored
                                    sendMessage(textToSend, url)
                                }
                            } else showToast("Upload failed: Invalid server response")
                        }
                    }
                })
            }
        }
        
        messageInput.text.clear()
        selectedImageUris.clear()
        imagePreviewScroll.isVisible = false
    }

    private fun uploadFiles(uris: List<Uri>, isImage: Boolean) {
        uris.forEach { uri ->
            uploadProgressBar.isVisible = true; val stream = contentResolver.openInputStream(uri); val bytes = stream?.readBytes(); stream?.close()
            if (bytes != null) {
                val fileName = getFileName(uri) ?: (if (isImage) "image.jpg" else "file")
                val formKey = if (isImage) "image" else "file"
                val endpoint = if (isImage) "upload-image" else "upload-file"
                
                val body = MultipartBody.Part.createFormData(formKey, fileName, bytes.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                val request = Request.Builder().url("http://159.195.38.145:8082/$endpoint").post(MultipartBody.Builder().setType(MultipartBody.FORM).addPart(body).build()).build()
                OkHttpClient().newCall(request).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        runOnUiThread { uploadProgressBar.isVisible = false; showToast("Upload failed") }
                    }
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        val responseBody = response.body.string()
                        if (!response.isSuccessful || responseBody.contains("404")) {
                            runOnUiThread { uploadProgressBar.isVisible = false; showToast("Server error: 404 or ${response.code}") }
                            return
                        }
                        
                        val url = if (responseBody.contains("\"url\":")) {
                            try { org.json.JSONObject(responseBody).getString("url") } catch (_: Exception) { "" }
                        } else if (responseBody.startsWith("http")) responseBody else ""
                        
                        runOnUiThread {
                            uploadProgressBar.isVisible = false
                            if (url.isNotEmpty() && !url.contains("404")) {
                                if (isImage) {
                                    // Ensure connection is active before sending message
                                    if (grpcClient.connectionState.value) {
                                        sendMessage("", url)
                                    } else {
                                        android.util.Log.w("NewChatActivity", "Connection lost after image upload, will retry on reconnect")
                                        sendMessage("", url)
                                    }
                                } else {
                                    if (grpcClient.connectionState.value) {
                                        sendMessage("File: $fileName\n$url", "")
                                    } else {
                                        android.util.Log.w("NewChatActivity", "Connection lost after file upload, will retry on reconnect")
                                        sendMessage("File: $fileName\n$url", "")
                                    }
                                }
                            } else showToast("Upload failed: Invalid server response")
                        }
                    }
                })
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    /*private fun applySavedColorScheme() {
        // Твоя дефолтная темная тема
        setTheme(R.style.Theme_Lavender_Dark_NoActionBar)
    }*/

    private fun applyChatBackground() {
        // Теперь фон применяется централизованно через ThemeApplier.
        // Здесь оставляем только прозрачность контейнеров, чтобы картинка была видна.
        messagesRecyclerView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        swipeRefreshLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    private fun updateSubtitle(onlineUsers: List<String>, isConnected: Boolean, typists: List<String>) {
        val colorOnPrimary = getThemeColor(com.google.android.material.R.attr.colorOnPrimary)
        val colorGreen = getColor(android.R.color.holo_green_light)

        toolbarSubtitle.isVisible = true
        // Сбрасываем курсив, если он был от тайпинга
        toolbarSubtitle.setTypeface(null, android.graphics.Typeface.NORMAL)

        when {
            // Приоритет 1: Нет сети
            !isConnected -> {
                toolbarSubtitle.text = getString(R.string.connecting)
                toolbarSubtitle.setTextColor(colorOnPrimary)
            }

            // Приоритет 2: Кто-то печатает
            typists.isNotEmpty() -> {
                toolbarSubtitle.text = if (typists.size == 1) {
                    getString(R.string.user_is_typing, typists.first())
                } else {
                    getString(R.string.users_are_typing, typists.size)
                }
                toolbarSubtitle.setTextColor(colorOnPrimary)
                toolbarSubtitle.setTypeface(null, android.graphics.Typeface.ITALIC)
            }

            // Приоритет 3: Статус собеседника (Online/Offline) в личке
            isDirect -> {
                val otherUser = getOtherParticipant()
                val isOnline = onlineUsers.contains(otherUser)

                toolbarSubtitle.text = if (isOnline) getString(R.string.connected) else getString(R.string.offline)
                toolbarSubtitle.setTextColor(if (isOnline) colorGreen else colorOnPrimary)
            }

            // Приоритет 4: Групповой чат
            else -> {
                updateGroupSubtitle(onlineUsers)
            }
        }
    }

    private var cachedOtherUser: String? = null

    private fun getOtherParticipant(): String? {
        if (cachedOtherUser != null) return cachedOtherUser
        return try {
            org.json.JSONArray(participantsJson).let { arr ->
                (0 until arr.length()).asSequence()
                    .map { arr.getString(it) }
                    .find { it != username }
                    .also { cachedOtherUser = it }
            }
        } catch (_: Exception) { null }
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun updateGroupSubtitle(onlineUsers: List<String>) {
        if (isDirect) return
        try {
            val arr = JSONArray(participantsJson)
            val total = arr.length()
            var onlineCount = 0
            for (i in 0 until total) {
                if (onlineUsers.contains(arr.getString(i))) onlineCount++
            }
            toolbarSubtitle.isVisible = true
            toolbarSubtitle.text = getString(R.string.participants_online_count, total, onlineCount)
            
            val typedValue = TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
            toolbarSubtitle.setTextColor(typedValue.data)
        } catch (_: Exception) {
            toolbarSubtitle.isVisible = false
        }
    }

    private fun showToast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun syncChatListIfNeeded() {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val localVersion = prefs.getLong("chat_list_version", 0L)
        val username = grpcClient.getCurrentUsername() ?: return

        android.util.Log.d("ChatSync", "Checking version... Local: $localVersion")

        grpcClient.getChatListVersion(username) { serverVersion ->
            if (serverVersion > localVersion) {
                android.util.Log.d("ChatSync", "New version $serverVersion found. Fetching chats...")

                grpcClient.getChats(username) { chats ->
                    // Здесь обновляй свой адаптер со списком чатов
                    // chatAdapter.submitList(chats)

                    prefs.edit { putLong("chat_list_version", serverVersion) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadDataFromIntent()

        grpcClient.setRoomId(roomId)
        grpcClient.clearMessages() // Очищаем экран
        grpcClient.loadHistory(roomId) // 🛠️ Форсируем загрузку для НОВОЙ комнаты

        viewModel.switchRoom(roomId)
        // Load draft for the new room
        loadDraft()
    }

    private fun loadDraft() {
        if (roomId.isEmpty() || username.isEmpty()) return
        if (grpcClient.getUserId() == null) return

        grpcClient.getDraft(roomId) { draftText, repliedToMessageId, repliedToUser, repliedToText, hasDraft ->
            runOnUiThread {
                if (hasDraft && draftText.isNotEmpty()) {
                    // Restore draft text
                    messageInput.setText(draftText)
                    messageInput.setSelection(draftText.length)

                    // Restore reply if present
                    if (repliedToMessageId.isNotEmpty()) {
                        replyingTo = Message(
                            id = repliedToMessageId,
                            user = repliedToUser,
                            text = repliedToText,
                            timestamp = System.currentTimeMillis(),
                            roomId = roomId
                        )
                        showReplyPreview(replyingTo!!)
                    }

                    android.util.Log.d("Draft", "Draft loaded for room $roomId: ${draftText.take(50)}...")
                }
            }
        }
    }

    private fun saveDraft() {
        if (roomId.isEmpty() || username.isEmpty()) return
        if (grpcClient.getUserId() == null) {
            // Try to ensure userId is set before saving
            ensureUserIdSet { saveDraft() }
            return
        }

        val draftText = messageInput.text?.toString()?.trim() ?: ""
        val hasReply = replyingTo != null

        // Only save if there's text or a reply
        if (draftText.isNotEmpty() || hasReply) {
            grpcClient.saveDraft(
                roomId = roomId,
                draftText = draftText,
                repliedToMessageId = replyingTo?.id ?: "",
                repliedToUser = replyingTo?.user ?: "",
                repliedToText = replyingTo?.text ?: ""
            ) { success, message ->
                if (success) {
                    android.util.Log.d("Draft", "Draft saved for room $roomId")
                } else {
                    android.util.Log.e("Draft", "Failed to save draft: $message")
                }
            }
        } else {
            // If no text and no reply, delete any existing draft
            if (grpcClient.getUserId() != null) {
                grpcClient.deleteDraft(roomId)
            }
        }
    }

    private fun ensureUserIdSet(onReady: () -> Unit) {
        val savedUserId = getSavedUserId()
        if (savedUserId != null) {
            grpcClient.setUserId(savedUserId)
            onReady()
        } else if (username.isNotEmpty()) {
            // Fetch userId from server
            grpcClient.fetchUserId(username) { userId, found ->
                if (found && userId != null) {
                    saveUserId(userId)
                    grpcClient.setUserId(userId)
                }
                runOnUiThread { onReady() }
            }
        } else {
            onReady()
        }
    }

    private fun getSavedUserId(): String? {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        return prefs.getString("user_id", null)
    }

    private fun saveUserId(userId: String) {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        prefs.edit { putString("user_id", userId) }
    }

    override fun onResume() {
        super.onResume()
        ThemeStore.refresh(this, username)
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false
    }

    override fun onPause() {
        super.onPause()
        // Save draft when leaving the chat (but not when sending)
        saveDraft()
    }
}
