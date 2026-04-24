package lavender.client.android

import android.content.ClipData
import android.content.Context
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.ui.adapter.MessageAdapter
import lavender.client.android.ui.adapter.MessageSwipeController
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

class NewChatActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }
    private val okHttpClient = OkHttpClient()
    private lateinit var toolbar: Toolbar
    private lateinit var toolbarTitle: TextView
    private lateinit var toolbarSubtitle: TextView
    private lateinit var toolbarAvatar: CircleImageView
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
    private lateinit var uploadProgressBar: ProgressBar

    private lateinit var replyPreview: View
    private lateinit var replyUser: TextView
    private lateinit var replyText: TextView
    private lateinit var cancelReply: ImageButton

    private lateinit var searchBar: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var closeSearch: ImageButton
    private lateinit var searchNext: ImageButton
    private lateinit var searchPrev: ImageButton
    private lateinit var searchResultsCount: TextView
    private var searchResults = listOf<Int>()
    private var currentSearchIndex = -1

    private var selectionMode = false
    private var replyingTo: Message? = null
    private var editingMessage: Message? = null

    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: MessageAdapter

    private var typingJob: Job? = null
    private var isTypingSignalSent = false
    private val grpcClient = GrpcClient

    private lateinit var username: String
    private lateinit var password: String
    private lateinit var roomId: String
    private lateinit var chatName: String
    private var isDirect: Boolean = false
    private lateinit var participantsJson: String

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uris = mutableListOf<Uri>()
            result.data?.data?.let { uris.add(it) }
            result.data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            }
            if (uris.isNotEmpty()) uploadFiles(uris)
        }
    }

    private val pickFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uris = mutableListOf<Uri>()
            result.data?.data?.let { uris.add(it) }
            result.data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            }
            if (uris.isNotEmpty()) uploadFiles(uris)
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            sendCurrentLocation()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            currentPhotoUri?.let { uploadFiles(listOf(it)) }
        }
    }

    private var currentPhotoUri: Uri? = null

    private fun createImageUri(): Uri? {
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "temp_photo_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySavedColorScheme()
        setContentView(R.layout.activity_new_chat)

        loadDataFromIntent()
        initViews()
        setupToolbar()
        setupRecyclerView()
        setupObservers()
        setupListeners()
        setupKeyboardHandling()

        // Start chat and load history for the current room
        viewModel.switchRoom(roomId)
        viewModel.startChat(username, password, "") { _ -> }
        viewModel.markRead(username)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectionMode) hideSelectionToolbar()
                else if (searchBar.visibility == View.VISIBLE) hideSearchBar()
                else finish()
            }
        })
    }

    private fun setupKeyboardHandling() {
        val bottomPanel = findViewById<View>(R.id.bottomPanel)
        ViewCompat.setOnApplyWindowInsetsListener(bottomPanel) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                // Поднимаем панель на высоту клавиатуры, учитывая системные отступы (navigation bar)
                bottomMargin = if (imeInsets.bottom > 0) {
                    imeInsets.bottom - systemBarsInsets.bottom
                } else {
                    0
                }
            }
            insets
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        toolbarSubtitle = findViewById(R.id.toolbarSubtitle)
        toolbarAvatar = findViewById(R.id.toolbarAvatar)
        groupParticipantsContainer = findViewById(R.id.groupParticipantsContainer)
        selectionToolbar = findViewById(R.id.selectionToolbar)
        selectionCountText = findViewById(R.id.selectionCountText)
        closeSelection = findViewById(R.id.closeSelection)
        copyMessages = findViewById(R.id.copyMessages)
        replyMessage = findViewById(R.id.replyMessage)
        deleteMessages = findViewById(R.id.deleteMessages)
        forwardMessages = findViewById(R.id.forwardMessages)
        toolbarContent = findViewById(R.id.toolbarContent)
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        attachButton = findViewById(R.id.attachButton)
        uploadProgressBar = findViewById(R.id.uploadProgressBar)
        replyPreview = findViewById(R.id.replyPreview)
        replyUser = findViewById(R.id.replyUser)
        replyText = findViewById(R.id.replyText)
        cancelReply = findViewById(R.id.cancelReply)
        emojiButton = findViewById(R.id.emojiButton)

        searchBar = findViewById(R.id.searchBar)
        searchInput = findViewById(R.id.searchInput)
        closeSearch = findViewById(R.id.closeSearch)
        searchNext = findViewById(R.id.searchNext)
        searchPrev = findViewById(R.id.searchPrev)
        searchResultsCount = findViewById(R.id.searchResultsCount)
    }

    private fun loadDataFromIntent() {
        username = intent.getStringExtra("USERNAME") ?: ""
        password = intent.getStringExtra("PASSWORD") ?: ""
        roomId = intent.getStringExtra("ROOM_ID") ?: ""
        chatName = intent.getStringExtra("CHAT_NAME") ?: "Chat"
        isDirect = intent.getBooleanExtra("IS_DIRECT", false)
        participantsJson = intent.getStringExtra("PARTICIPANTS") ?: "[]"
    }

    private var otherUserAvatarUrl: String = ""

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        toolbar.setNavigationOnClickListener {
            if (selectionMode) hideSelectionToolbar() else finish()
        }

        if (isDirect) {
            toolbarAvatar.isVisible = true
            groupParticipantsContainer.isVisible = false
            val arr = JSONArray(participantsJson)
            val otherUser = if (arr.length() > 0) {
                var found = ""
                for (i in 0 until arr.length()) {
                    val u = arr.getString(i)
                    if (u != username) { found = u; break }
                }
                found.ifEmpty { arr.getString(0) }
            } else chatName

            toolbarTitle.text = otherUser
            
            grpcClient.getUserAvatar(otherUser) { url ->
                otherUserAvatarUrl = url
                runOnUiThread {
                    if (url.isNotEmpty()) {
                        com.bumptech.glide.Glide.with(this).load(url)
                            .placeholder(R.drawable.ic_default_avatar).into(toolbarAvatar)
                    } else toolbarAvatar.setImageResource(R.drawable.ic_default_avatar)
                }
            }

            val openProfile = {
                val intent = Intent(this, ProfileActivity::class.java)
                    .putExtra("username", otherUser)
                    .putExtra("avatar_url", otherUserAvatarUrl)
                startActivity(intent)
            }

            toolbarAvatar.setOnClickListener { openProfile() }
            toolbarTitle.setOnClickListener { openProfile() }
            toolbarSubtitle.setOnClickListener { openProfile() }

        } else {
            toolbarTitle.text = chatName
            toolbarAvatar.isVisible = false
            groupParticipantsContainer.isVisible = true
            setupGroupAvatars()

            val openGroupInfo = {
                // For group chat, we can also show a profile-like activity or group info
                val intent = Intent(this, ProfileActivity::class.java)
                    .putExtra("username", chatName)
                    .putExtra("is_group", true)
                    .putExtra("room_id", roomId)
                    .putExtra("participants", participantsJson)
                startActivity(intent)
            }
            toolbarTitle.setOnClickListener { openGroupInfo() }
            groupParticipantsContainer.setOnClickListener { openGroupInfo() }
            toolbarSubtitle.setOnClickListener { openGroupInfo() }
        }
    }

    private fun setupGroupAvatars() {
        groupParticipantsContainer.removeAllViews()
        val arr = JSONArray(participantsJson)
        for (i in 0 until arr.length().coerceAtMost(3)) {
            val u = arr.getString(i)
            val iv = CircleImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(24.dpToPx(), 24.dpToPx()).apply {
                    marginStart = if (i > 0) (-8).dpToPx() else 0
                }
                borderWidth = 1.dpToPx()
                borderColor = ContextCompat.getColor(this@NewChatActivity, R.color.lavender_mist)
            }
            grpcClient.getUserAvatar(u) { url ->
                runOnUiThread {
                    if (url.isNotEmpty()) {
                        com.bumptech.glide.Glide.with(this).load(url)
                            .placeholder(R.drawable.ic_default_avatar).into(iv)
                    } else iv.setImageResource(R.drawable.ic_default_avatar)
                }
            }
            groupParticipantsContainer.addView(iv)
        }
        toolbarSubtitle.text = getString(R.string.participants_count, arr.length())
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun getSavedColorScheme(): String? = getSharedPreferences("ChatPrefs", MODE_PRIVATE).getString("color_scheme", "dark")

    private fun setupRecyclerView() {
        adapter = MessageAdapter(
            currentUsername = username,
            isGroupChat = !isDirect,
            onSelectionChanged = { count ->
                if (count > 0) showSelectionToolbar(count) else hideSelectionToolbar()
            },
            onMessageClick = { message ->
                if (selectionMode) return@MessageAdapter
                if (message.imageUrl.isNotEmpty()) showFullScreenImage(message.imageUrl)
                else if (message.text.startsWith("geo:")) openLocation(message.text)
                else showReactionsDialog(message)
            }
        )
        messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        messagesRecyclerView.adapter = adapter

        val swipeController = MessageSwipeController(this) { position ->
            val message = adapter.currentList[position]
            showReplyPreview(message)
            adapter.notifyItemChanged(position)
        }
        androidx.recyclerview.widget.ItemTouchHelper(swipeController).attachToRecyclerView(messagesRecyclerView)

        // Mark as read when scrolling to bottom
        messagesRecyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount

                if (lastVisiblePosition == totalItemCount - 1) {
                    viewModel.markRead(username)
                }
            }
        })
    }

    private fun setupObservers() {
        val factory = ChatViewModelFactory(roomId)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]

        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                adapter.submitList(messages) {
                    if (messages.isNotEmpty()) messagesRecyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }

        lifecycleScope.launch {
            grpcClient.typingUsers.collect { typingMap ->
                val users = typingMap[roomId] ?: emptySet()
                val otherTyping = users.filter { it != username }
                runOnUiThread {
                    if (otherTyping.isNotEmpty()) {
                        toolbarSubtitle.isVisible = true
                        toolbarSubtitle.text = if (otherTyping.size == 1) getString(R.string.user_is_typing, otherTyping.first())
                        else getString(R.string.users_are_typing, otherTyping.size)
                    } else {
                        if (isDirect) toolbarSubtitle.isVisible = false
                        else {
                            val arr = JSONArray(participantsJson)
                            toolbarSubtitle.text = getString(R.string.participants_count, arr.length())
                        }
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        val searchItem = menu.findItem(R.id.action_search)
        searchItem?.isVisible = !selectionMode
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                showSearchBar()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSearchBar() {
        toolbarContent.isVisible = false
        searchBar.isVisible = true
        toolbar.navigationIcon = null
        searchInput.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSearchBar() {
        searchBar.isVisible = false
        toolbarContent.isVisible = true
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        searchInput.setText("")
        searchResults = emptyList()
        currentSearchIndex = -1
        adapter.setSearchHighlight(null)
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    private fun performSearch(query: String) {
        if (query.isEmpty()) {
            searchResults = emptyList()
            currentSearchIndex = -1
            searchResultsCount.text = ""
            adapter.setSearchHighlight(null)
            return
        }

        val messages = adapter.currentList
        searchResults = messages.indices.filter { i ->
            messages[i].text.contains(query, ignoreCase = true)
        }

        if (searchResults.isNotEmpty()) {
            currentSearchIndex = searchResults.size - 1 // Start from most recent
            updateSearchNavigation()
        } else {
            currentSearchIndex = -1
            searchResultsCount.text = getString(R.string.no_results)
            adapter.setSearchHighlight(null)
        }
    }

    private fun updateSearchNavigation() {
        if (searchResults.isEmpty()) return
        searchResultsCount.text = getString(R.string.search_results_format, currentSearchIndex + 1, searchResults.size)
        val targetPos = searchResults[currentSearchIndex]
        messagesRecyclerView.scrollToPosition(targetPos)
        adapter.setSearchHighlight(searchInput.text.toString())
    }

    private fun setupListeners() {
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isTypingSignalSent) {
                    grpcClient.sendTypingSignal(username, true)
                    isTypingSignalSent = true
                }
                typingJob?.cancel()
                typingJob = lifecycleScope.launch {
                    delay(3000)
                    grpcClient.sendTypingSignal(username, false)
                    isTypingSignalSent = false
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty() || replyingTo != null) {
                if (editingMessage != null) {
                    val originalMessage = editingMessage!!
                    if (text != originalMessage.text) {
                        android.util.Log.d("NewChatActivity", "Editing message ${originalMessage.id} to: $text")
                        grpcClient.editMessage(originalMessage.id, text) { success, errorMsg ->
                            android.util.Log.d("NewChatActivity", "Edit result: success=$success, error=$errorMsg")
                            if (success) {
                                // Update local message immediately with edited flag
                                val updatedMessage = originalMessage.copy(text = text, timestamp = System.currentTimeMillis(), edited = true)
                                viewModel.updateMessage(updatedMessage)
                            } else {
                                android.util.Log.e("NewChatActivity", "Failed to edit message: $errorMsg")
                            }
                        }
                    }
                    editingMessage = null
                    messageInput.setText("")
                    sendButton.setImageResource(R.drawable.send_24)
                } else {
                    sendMessage(text, "")
                    messageInput.setText("")
                    hideReplyPreview()
                }
            }
        }

        attachButton.setOnClickListener {
            showAttachmentDialog()
        }

        closeSelection.setOnClickListener { hideSelectionToolbar() }

        emojiButton.setOnClickListener {
            showEmojiDialog()
        }

        copyMessages.setOnClickListener {
            val selected = adapter.getSelectedMessages()
            val text = selected.joinToString("\n") { "[${it.user}]: ${it.text}" }
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("messages", text))
            Toast.makeText(this, "Copied ${selected.size} messages", Toast.LENGTH_SHORT).show()
            hideSelectionToolbar()
        }

        replyMessage.setOnClickListener {
            val selected = adapter.getSelectedMessages()
            if (selected.size == 1) {
                showReplyPreview(selected[0])
                hideSelectionToolbar()
            }
        }

        deleteMessages.setOnClickListener {
            val selected = adapter.getSelectedMessages()
            AlertDialog.Builder(this)
                .setTitle("Delete Messages")
                .setMessage("Are you sure you want to delete ${selected.size} messages?")
                .setPositiveButton("Delete") { _, _ ->
                    selected.forEach { grpcClient.deleteMessage(it) }
                    hideSelectionToolbar()
                }
                .setNegativeButton("Cancel", null).show()
        }

        forwardMessages.setOnClickListener {
            val selected = adapter.getSelectedMessages()
            if (selected.isNotEmpty()) {
                showForwardDialog(selected)
                hideSelectionToolbar()
            }
        }

        cancelReply.setOnClickListener { hideReplyPreview() }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        closeSearch.setOnClickListener { hideSearchBar() }
        searchNext.setOnClickListener {
            if (searchResults.isNotEmpty()) {
                currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
                updateSearchNavigation()
            }
        }
        searchPrev.setOnClickListener {
            if (searchResults.isNotEmpty()) {
                currentSearchIndex = if (currentSearchIndex > 0) currentSearchIndex - 1 else searchResults.size - 1
                updateSearchNavigation()
            }
        }
    }

    private fun showAttachmentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_attachment_picker, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(dialogView)

        dialogView.findViewById<View>(R.id.attachCamera).setOnClickListener {
            currentPhotoUri = createImageUri()
            currentPhotoUri?.let { takePhotoLauncher.launch(it) }
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.attachGallery).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            pickImageLauncher.launch(intent)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.attachFile).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            pickFileLauncher.launch(intent)
            dialog.dismiss()
        }
        dialogView.findViewById<View>(R.id.attachLocation).setOnClickListener {
            locationPermissionLauncher.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showFullScreenImage(imageUrl: String) {
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen).create()
        val layout = RelativeLayout(this)
        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleLarge)
        
        layout.addView(imageView, RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        layout.addView(progressBar, RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            addRule(RelativeLayout.CENTER_IN_PARENT)
        })

        dialog.setView(layout)
        imageView.setOnClickListener { dialog.dismiss() }
        
        com.bumptech.glide.Glide.with(this)
            .load(imageUrl)
            .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(e: com.bumptech.glide.load.engine.GlideException?, model: Any?, target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean): Boolean {
                    progressBar.isVisible = false
                    return false
                }
                override fun onResourceReady(resource: android.graphics.drawable.Drawable, model: Any, target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>, dataSource: com.bumptech.glide.load.DataSource, isFirstResource: Boolean): Boolean {
                    progressBar.isVisible = false
                    return false
                }
            })
            .into(imageView)

        dialog.show()
    }

    private fun showSelectionToolbar(count: Int) {
        selectionMode = true
        invalidateOptionsMenu()
        toolbarContent.isVisible = false
        selectionToolbar.isVisible = true
        selectionCountText.text = count.toString()
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        replyMessage.isVisible = count == 1
        forwardMessages.isVisible = count > 0
    }

    private fun hideSelectionToolbar() {
        if (!selectionMode) return
        selectionMode = false
        adapter.toggleSelectionMode(false)
        invalidateOptionsMenu()
        selectionToolbar.isVisible = false
        toolbarContent.isVisible = true
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.navigationIcon?.let {
            val wrapped = DrawableCompat.wrap(it)
            DrawableCompat.setTint(wrapped, ContextCompat.getColor(this, R.color.white))
            toolbar.navigationIcon = wrapped
        }
    }

    private fun showReplyPreview(message: Message) {
        replyingTo = message
        replyPreview.isVisible = true
        replyUser.text = message.user
        replyText.text = if (message.imageUrl.isNotEmpty()) "Photo" else message.text
        messageInput.requestFocus()
    }

    private fun hideReplyPreview() {
        replyingTo = null
        replyPreview.isVisible = false
    }

    private fun sendMessage(text: String, imageUrl: String) {
        val msg = Message(
            user = username, text = text, timestamp = System.currentTimeMillis(),
            roomId = roomId, imageUrl = imageUrl,
            repliedToMessageId = replyingTo?.id ?: "",
            repliedToUser = replyingTo?.user ?: "",
            repliedToText = replyingTo?.text ?: ""
        )
        grpcClient.sendMessage(msg)
    }

    private fun showReactionsDialog(message: Message) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reactions, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.reactionsContainer)
        val menuReply = dialogView.findViewById<LinearLayout>(R.id.menuReply)
        val menuCopy = dialogView.findViewById<LinearLayout>(R.id.menuCopy)
        val menuEdit = dialogView.findViewById<LinearLayout>(R.id.menuEdit)
        val menuDelete = dialogView.findViewById<LinearLayout>(R.id.menuDelete)

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()

        // Add emoji reactions (top)
        val emojis = listOf("❤️", "👍", "🔥", "😂", "😮", "😢", "🙏")
        emojis.forEach { emoji ->
            container.addView(TextView(this).apply {
                text = emoji
                textSize = 30f
                setPadding(16, 16, 16, 16)
                setOnClickListener {
                    grpcClient.setReaction(message.id, username, emoji)
                    dialog.dismiss()
                }
            })
        }

        // Setup menu items
        menuReply.setOnClickListener {
            replyingTo = message
            showReplyPreview(message)
            dialog.dismiss()
        }

        menuCopy.setOnClickListener {
            val text = "[${message.user}]: ${message.text}"
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("message", text))
            Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        // Show edit only for own messages
        menuEdit.isVisible = message.user == username
        menuEdit.setOnClickListener {
            editingMessage = message
            val cleanText = message.text.removeSuffix(" (edited)")
            messageInput.setText(cleanText)
            messageInput.setSelection(cleanText.length)
            messageInput.requestFocus()
            sendButton.setImageResource(R.drawable.ic_checked)
            dialog.dismiss()
        }

        // Show delete only for own messages
        menuDelete.isVisible = message.user == username
        menuDelete.setOnClickListener {
            grpcClient.deleteMessage(message)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEmojiDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_emoji_picker, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(dialogView)

        val emojis = listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
            "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
            "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
            "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
            "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
            "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗",
            "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯",
            "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐",
            "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈",
            "👿", "👹", "👺", "🤡", "👻", "💀", "☠️", "👽", "👾", "🤖",
            "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾")

        val container = dialogView.findViewById<GridLayout>(R.id.emojiGrid)
        container.columnCount = 7

        emojis.forEach { emoji ->
            val textView = TextView(this).apply {
                text = emoji
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(8, 8, 8, 8)
                isClickable = true
                isFocusable = true
                val outValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
                setOnClickListener {
                    val cursorPosition = messageInput.selectionStart
                    val text = messageInput.text.toString()
                    val newText = text.substring(0, cursorPosition) + emoji + text.substring(cursorPosition)
                    messageInput.setText(newText)
                    messageInput.setSelection(cursorPosition + emoji.length)
                    dialog.dismiss()
                }
            }
            container.addView(textView)
        }

        dialog.show()
    }

    private fun uploadFiles(uris: List<Uri>) {
        attachButton.isVisible = false
        uploadProgressBar.isVisible = true
        lifecycleScope.launch {
            try {
                uris.forEach { uri ->
                    uploadFile(uri)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@NewChatActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
            } finally {
                withContext(Dispatchers.Main) {
                    attachButton.isVisible = true
                    uploadProgressBar.isVisible = false
                }
            }
        }
    }

    private suspend fun uploadFile(uri: Uri) {
        val fileName = getFileName(uri) ?: "file"
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val isImage = mimeType.startsWith("image/")

        val bytes = withContext(Dispatchers.IO) { 
            contentResolver.openInputStream(uri)?.use { it.readBytes() } 
        } ?: return

        val formKey = if (isImage) "image" else "file"
        val uploadEndpoint = if (isImage) "upload-image" else "upload-file"

        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(formKey, fileName, bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
            .build()
        
        val request = Request.Builder()
            .url("http://159.195.38.145:8082/$uploadEndpoint")
            .post(requestBody)
            .build()
        
        val response = withContext(Dispatchers.IO) { okHttpClient.newCall(request).execute() }
        if (response.isSuccessful) {
            val responseBody = response.body.string()
            val fileUrl = JSONObject(responseBody).getString("url")
            withContext(Dispatchers.Main) {
                if (isImage) {
                    sendMessage("", fileUrl)
                } else {
                    sendMessage("File: $fileName\n$fileUrl", "")
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@NewChatActivity, "Upload failed: ${response.code}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = it.getString(index)
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result
    }

    private fun openLocation(geoUri: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(geoUri))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app to open maps", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendCurrentLocation() {
        val lm = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        try {
            val location = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            if (location != null) {
                sendMessage("geo:${location.latitude},${location.longitude}", "")
            } else {
                Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showForwardDialog(messagesToForward: List<Message>) {
        grpcClient.getChats(username) { chats ->
            val chatNames = chats.map { it.name }.toTypedArray()
            runOnUiThread {
                AlertDialog.Builder(this@NewChatActivity)
                    .setTitle(R.string.forward_to)
                    .setItems(chatNames) { _, which ->
                        val targetChat = chats[which]
                        messagesToForward.forEach { msg ->
                            val forwardedMsg = msg.copy(
                                id = "",
                                roomId = targetChat.id,
                                timestamp = System.currentTimeMillis(),
                                user = username,
                                isRead = false,
                                reactions = emptyList()
                            )
                            grpcClient.sendMessage(forwardedMsg)
                        }
                        Toast.makeText(this@NewChatActivity, "Forwarded to ${targetChat.name}", Toast.LENGTH_SHORT).show()

                        // Update current activity with new chat data
                        roomId = targetChat.id
                        chatName = targetChat.name
                        isDirect = targetChat.type == "direct"
                        toolbarTitle.text = chatName
                        toolbarSubtitle.text = if (isDirect) "" else getString(R.string.general_chat)

                        // Switch to the new room
                        viewModel.switchRoom(roomId)
                        viewModel.startChat(username, password, "") { _ -> }
                        viewModel.markRead(username)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun applySavedColorScheme() {
        val themeId = when (getSavedColorScheme()) {
            "light" -> R.style.Theme_MsgClientAndroid
            "dark" -> R.style.Theme_Lavender_Dark_NoActionBar
            else -> R.style.Theme_Lavender_Dark_NoActionBar
        }
        setTheme(themeId)
    }
}
