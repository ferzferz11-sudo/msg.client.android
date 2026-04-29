package lavender.client.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.io.File
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.audio.AudioUploader
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.ui.adapter.MessageAdapter
import lavender.client.android.ui.adapter.MessageSwipeController
import lavender.client.android.ui.adapter.MentionAdapter
import lavender.client.android.ui.audio.AudioRecordingView
import lavender.client.android.ui.chat.ChatViewModel
import lavender.client.android.ui.chat.ChatViewModelFactory
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import android.content.pm.PackageManager

class NewChatActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
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
    private lateinit var closeSelection: ImageButton
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
    private lateinit var closeSearch: ImageButton
    private lateinit var searchNext: ImageButton
    private lateinit var searchPrev: ImageButton
    private lateinit var searchResultsCount: TextView

    private lateinit var adapter: MessageAdapter

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uris = mutableSetOf<Uri>()
            result.data?.data?.let { uris.add(it) }
            result.data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) uris.add(clipData.getItemAt(i).uri)
            }
            if (uris.isNotEmpty()) uploadFiles(uris.toList(), isImage = true)
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
        if (success) currentPhotoUri?.let { uploadFiles(listOf(it), isImage = true) }
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
        applySavedColorScheme()
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_chat)
        loadDataFromIntent(); initViews()
        lavender.client.android.ui.ThemeManager.loadTheme(this, username) {
            runOnUiThread { lavender.client.android.ui.ThemeManager.applyTheme(this); applyChatBackground() }
        }
        setupToolbar(); setupRecyclerView(); setupObservers(); setupListeners(); setupKeyboardHandling(); fetchChatMetadataIfNeeded()
        viewModel.switchRoom(roomId)
        viewModel.startChat(username, password, "") { _ -> viewModel.markRead(username, this) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectionMode) hideSelectionToolbar()
                else if (searchBar.isVisible) hideSearchBar()
                else if (mentionContainer.isVisible) mentionContainer.isVisible = false
                else finish()
            }
        })
    }

    private fun fetchChatMetadataIfNeeded() {
        if (participantsJson == "[]" || chatName == "Chat") {
            grpcClient.getChats(username) { chats ->
                val chat = chats.find { it.id == roomId }
                if (chat != null) runOnUiThread {
                    chatName = chat.getDisplayName(username); isDirect = chat.type == "direct"; participantsJson = chat.participants; creator = chat.creator; chatAvatarUrl = chat.avatarUrl
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
        selectionCountText = findViewById(R.id.selectionCountText); closeSelection = findViewById(R.id.closeSelection); copyMessages = findViewById(R.id.copyMessages)
        replyMessage = findViewById(R.id.replyMessage); deleteMessages = findViewById(R.id.deleteMessages); forwardMessages = findViewById(R.id.forwardMessages)
        toolbarContent = findViewById(R.id.toolbarContent); messagesRecyclerView = findViewById(R.id.messagesRecyclerView); swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        messageInput = findViewById(R.id.messageInput); sendButton = findViewById(R.id.sendButton); attachButton = findViewById(R.id.attachButton); audioButton = findViewById(R.id.audioButton)
        uploadProgressBar = findViewById(R.id.uploadProgressBar); audioRecordingView = findViewById(R.id.audioRecordingView); replyPreview = findViewById(R.id.replyPreview)
        replyUser = findViewById(R.id.replyUser); replyText = findViewById(R.id.replyText); cancelReply = findViewById(R.id.cancelReply); emojiButton = findViewById(R.id.emojiButton)
        mentionContainer = findViewById(R.id.mentionContainer); mentionList = findViewById(R.id.mentionList); mentionAdapter = MentionAdapter { insertMention(it) }
        mentionList.layoutManager = LinearLayoutManager(this); mentionList.adapter = mentionAdapter
        searchBar = findViewById(R.id.searchBar); searchInput = findViewById(R.id.searchInput); closeSearch = findViewById(R.id.closeSearch)
        searchNext = findViewById(R.id.searchNext); searchPrev = findViewById(R.id.searchPrev); searchResultsCount = findViewById(R.id.searchResultsCount)
        audioButton.isVisible = true
    }

    private fun loadDataFromIntent() {
        username = intent.getStringExtra("USERNAME") ?: ""; password = intent.getStringExtra("PASSWORD") ?: ""; roomId = intent.getStringExtra("ROOM_ID") ?: ""
        chatName = intent.getStringExtra("CHAT_NAME") ?: "Chat"; isDirect = intent.getBooleanExtra("IS_DIRECT", false)
        participantsJson = intent.getStringExtra("PARTICIPANTS") ?: "[]"; creator = intent.getStringExtra("CREATOR") ?: ""; chatAvatarUrl = intent.getStringExtra("AVATAR_URL") ?: ""
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar); supportActionBar?.setDisplayHomeAsUpEnabled(true); supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { if (selectionMode) hideSelectionToolbar() else finish() }
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
            } catch (e: Exception) { null }
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
            val intent = Intent(this, ProfileActivity::class.java).apply {
                putExtra("username", chatName)
                putExtra("is_group", !isDirect)
                putExtra("room_id", roomId)
                putExtra("avatar_url", if (isDirect) effectiveAvatarUrl else chatAvatarUrl)
                putExtra("participants", participantsJson)
                putExtra("creator", creator)
            }
            startActivity(intent)
        }
    }

    private fun setupGroupAvatars() {
        groupParticipantsContainer.removeAllViews()
        val arr = JSONArray(participantsJson)
        for (i in 0 until arr.length().coerceAtMost(3)) {
            val u = arr.getString(i)
            val iv = CircleImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(34.dpToPx(), 34.dpToPx()).apply { marginStart = if (i > 0) (-10).dpToPx() else 0 }
                borderWidth = 1.dpToPx(); borderColor = ContextCompat.getColor(this@NewChatActivity, R.color.lavender_mist)
            }
            val cache = grpcClient.getAvatarCache(); val url = cache[u]
            if (!url.isNullOrEmpty()) com.bumptech.glide.Glide.with(this).load(url).placeholder(R.drawable.ic_default_avatar).circleCrop().into(iv)
            else iv.setImageResource(R.drawable.ic_default_avatar)
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
        lifecycleScope.launch {
            viewModel.messages.collect { roomMessages ->
                val wasAtBottom = (messagesRecyclerView.layoutManager as? LinearLayoutManager)?.let { it.findLastVisibleItemPosition() >= lastMessageCount - 2 } ?: true
                val isFirstLoad = lastMessageCount == 0; val hasNewMessages = roomMessages.size > lastMessageCount
                adapter.submitList(roomMessages) { if (roomMessages.isNotEmpty() && (isFirstLoad || (hasNewMessages && wasAtBottom))) messagesRecyclerView.scrollToPosition(roomMessages.size - 1) }
                
                if (hasNewMessages) {
                    val incomingMessages = roomMessages.filter { it.user != username && !it.isRead }
                    if (incomingMessages.isNotEmpty()) {
                        viewModel.markRead(username, this@NewChatActivity)
                    }
                }

                lastMessageCount = roomMessages.size
            }
        }
        lifecycleScope.launch {
            grpcClient.typingUsers.collect { _ ->
                updateSubtitle(grpcClient.users.value, grpcClient.connectionState.value)
            }
        }
        lifecycleScope.launch {
            grpcClient.connectionState.collect { connected ->
                updateSubtitle(grpcClient.users.value, connected)
            }
        }
        lifecycleScope.launch {
            grpcClient.users.collect { onlineUsers ->
                updateSubtitle(onlineUsers, grpcClient.connectionState.value)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean { menuInflater.inflate(R.menu.chat_menu, menu); return true }
    override fun onPrepareOptionsMenu(menu: Menu): Boolean { menu.findItem(R.id.action_search)?.isVisible = !selectionMode; return super.onPrepareOptionsMenu(menu) }
    override fun onOptionsItemSelected(item: MenuItem): Boolean { return when (item.itemId) { R.id.action_search -> { showSearchBar(); true }; else -> super.onOptionsItemSelected(item) } }

    private fun setupListeners() {
        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text, "")
                messageInput.text.clear()
                hideReplyPreview()
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
        closeSearch.setOnClickListener { hideSearchBar() }
        audioButton.setOnClickListener { showAudioRecordingView() }
        emojiButton.setOnClickListener { showEmojiPicker() }

        closeSelection.setOnClickListener { hideSelectionToolbar() }
        copyMessages.setOnClickListener { copySelectedMessages() }
        replyMessage.setOnClickListener { replyToSelectedMessage() }
        deleteMessages.setOnClickListener { deleteSelectedMessages() }
        forwardMessages.setOnClickListener { forwardSelectedMessages() }
    }

    private fun showEmojiPicker() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_emoji_picker, null)
        val emojiGrid = dialogView.findViewById<android.widget.GridLayout>(R.id.emojiGrid)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

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
                layoutParams = android.view.ViewGroup.LayoutParams(size, size)
                val typedValue = android.util.TypedValue()
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

        dialog.show()
    }

    private fun showAttachmentSheet() {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_attachments, null)
        
        view.findViewById<LinearLayout>(R.id.attachCamera).setOnClickListener {
            bottomSheet.dismiss()
            currentPhotoUri = createImageUri()
            currentPhotoUri?.let { takePhotoLauncher.launch(it) }
        }
        
        view.findViewById<LinearLayout>(R.id.attachGallery).setOnClickListener {
            bottomSheet.dismiss()
            val intent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
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

    private fun showSearchBar() { searchBar.isVisible = true; toolbarContent.isVisible = false; searchInput.requestFocus(); (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(searchInput, 0) }
    private fun hideSearchBar() { searchBar.isVisible = false; toolbarContent.isVisible = true; searchInput.text.clear(); adapter.setSearchHighlight(null); (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchInput.windowToken, 0) }

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

    private fun showFullScreenImage(imageUrl: String) { val intent = Intent(this, FullScreenImageActivity::class.java).apply { putExtra("image_url", imageUrl) }; startActivity(intent) }
    private fun showSelectionToolbar(count: Int) { selectionMode = true; invalidateOptionsMenu(); toolbarContent.isVisible = false; selectionToolbar.isVisible = true; selectionCountText.text = count.toString(); supportActionBar?.setDisplayHomeAsUpEnabled(false); replyMessage.isVisible = count == 1; forwardMessages.isVisible = count > 0 }
    private fun hideSelectionToolbar() { if (!selectionMode) return; selectionMode = false; adapter.toggleSelectionMode(false); invalidateOptionsMenu(); selectionToolbar.isVisible = false; toolbarContent.isVisible = true; supportActionBar?.setDisplayHomeAsUpEnabled(true); toolbar.setNavigationIcon(R.drawable.ic_back_arrow); toolbar.navigationIcon?.let { val wrapped = DrawableCompat.wrap(it); DrawableCompat.setTint(wrapped, ContextCompat.getColor(this, R.color.white)); toolbar.navigationIcon = wrapped } }
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
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.delete_messages_title)
        builder.setMessage(getString(R.string.delete_messages_confirm, selectedMessages.size))
        builder.setPositiveButton(R.string.delete) { _, _ ->
            selectedMessages.forEach { grpcClient.deleteMessage(it) }
            hideSelectionToolbar()
        }
        builder.setNegativeButton(R.string.cancel, null)
        val dialog = builder.create()
        dialog.show()

        // Apply theme colors to dialog
        val theme = lavender.client.android.ui.ThemeManager.getCurrentTheme()
        if (theme != null) {
            try {
                val textColor = theme.textPrimaryColor.toColorInt()
                val titleView = dialog.findViewById<TextView>(android.R.id.title)
                val messageView = dialog.findViewById<TextView>(android.R.id.message)
                titleView?.setTextColor(textColor)
                messageView?.setTextColor(textColor)
            } catch (_: Exception) {}
        }
    }

    private fun forwardSelectedMessages() {
        val selectedMessages = adapter.getSelectedMessages()
        // TODO: Implement forwarding to another chat
        showToast("Forwarding not implemented yet")
        hideSelectionToolbar()
    }

    private fun fullReloadHistory() { swipeRefreshLayout.isRefreshing = true; grpcClient.clearMessages(); grpcClient.loadHistory(roomId) { runOnUiThread { swipeRefreshLayout.isRefreshing = false } } }
    private fun sendMessage(text: String, imageUrl: String) { 
        val effectiveText = if (text.isEmpty() && imageUrl.isNotEmpty()) "Image" else if (text.isEmpty()) "Message" else text
        val msg = Message(user = username, text = effectiveText, timestamp = System.currentTimeMillis(), roomId = roomId, imageUrl = imageUrl, repliedToMessageId = replyingTo?.id ?: "", repliedToUser = replyingTo?.user ?: "", repliedToText = replyingTo?.text ?: "")
        grpcClient.sendMessage(msg)
        viewModel.markRead(username, this)
    }
    private fun showReactionsDialog(message: Message) {
        val root = findViewById<ViewGroup>(android.R.id.content)
        val dialogView = layoutInflater.inflate(R.layout.dialog_reactions, root, false)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        
        val reactionsContainer = dialogView.findViewById<LinearLayout>(R.id.reactionsContainer)
        val emojis = listOf("👍", "❤️", "🔥", "😂", "😮", "😢", "🙏", "✅")
        
        for (emoji in emojis) {
            val textView = TextView(this).apply {
                text = emoji
                textSize = 30f
                setPadding(16, 8, 16, 8)
                val typedValue = android.util.TypedValue()
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
        
        dialog.show()
    }

    private fun showEditMessageDialog(message: Message) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_message, null)
        val editText = dialogView.findViewById<EditText>(R.id.editMessageInput)
        editText.setText(message.text)
        editText.setSelection(message.text.length)
        
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
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1001); return }
        audioRecordingView.visibility = View.VISIBLE; messageInput.visibility = View.GONE; sendButton.visibility = View.GONE; attachButton.visibility = View.GONE; audioButton.visibility = View.GONE; setupAudioRecordingView()
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
                            try { org.json.JSONObject(responseBody).getString("url") } catch (e: Exception) { "" }
                        } else if (responseBody.startsWith("http")) responseBody else ""
                        
                        runOnUiThread { 
                            uploadProgressBar.isVisible = false
                            if (url.isNotEmpty() && !url.contains("404")) {
                                if (isImage) sendMessage("Image", url)
                                else sendMessage("File: $fileName\n$url", "")
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

    private fun applySavedColorScheme() { setTheme(if (getSharedPreferences("ChatPrefs", MODE_PRIVATE).getString("color_scheme", null) == "light") R.style.Theme_Lavender_Light_NoActionBar else R.style.Theme_Lavender_Dark_NoActionBar) }
    private fun applyChatBackground() {
        val theme = lavender.client.android.ui.ThemeManager.getCurrentTheme() ?: return
        if (theme.backgroundImageUrl.isNotEmpty()) findViewById<ImageView>(R.id.chatBackground)?.let { com.bumptech.glide.Glide.with(this).load(theme.backgroundImageUrl).centerCrop().into(it); messagesRecyclerView.setBackgroundColor(android.graphics.Color.TRANSPARENT); swipeRefreshLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT) }
    }

    private fun updateSubtitle(onlineUsers: List<String>, isConnected: Boolean) {
        runOnUiThread {
            if (!isConnected) {
                toolbarSubtitle.isVisible = true
                toolbarSubtitle.text = getString(R.string.connecting)
                val typedValue = TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
                toolbarSubtitle.setTextColor(typedValue.data)
                return@runOnUiThread
            }

            val otherTyping = (grpcClient.typingUsers.value[roomId] ?: emptySet()).filter { it != username }
            if (otherTyping.isNotEmpty()) {
                toolbarSubtitle.isVisible = true
                toolbarSubtitle.text = if (otherTyping.size == 1) getString(R.string.user_is_typing, otherTyping.first()) else getString(R.string.users_are_typing, otherTyping.size)
                val typedValue = TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
                toolbarSubtitle.setTextColor(typedValue.data)
                return@runOnUiThread
            }

            if (isDirect) {
                val otherUser = try {
                    JSONArray(participantsJson).let { arr ->
                        (0 until arr.length()).asSequence().map { arr.getString(it) }.find { it != username }
                    }
                } catch (e: Exception) { null } ?: return@runOnUiThread
                
                val isOnline = onlineUsers.contains(otherUser)
                toolbarSubtitle.isVisible = true
                toolbarSubtitle.text = if (isOnline) getString(R.string.connected) else getString(R.string.offline)
                toolbarSubtitle.setTextColor(
                    if (isOnline) getColor(android.R.color.holo_green_light)
                    else {
                        val typedValue = TypedValue()
                        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
                        typedValue.data
                    }
                )
            } else {
                updateGroupSubtitle(onlineUsers)
            }
        }
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
        } catch (e: Exception) {
            toolbarSubtitle.isVisible = false
        }
    }

    private fun showToast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
