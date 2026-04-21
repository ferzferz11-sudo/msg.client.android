package lavender.client.android

import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.edit
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.MediaStore
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lavender.client.android.data.models.Message
import lavender.client.android.ui.adapter.MessageAdapter
import lavender.client.android.ui.chat.ChatViewModel
import lavender.client.android.data.grpc.ServerConnectivityTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class ChatActivity : AppCompatActivity() {
    
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messageInput: EditText
    private lateinit var sendButton: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var toolbar: Toolbar
    private lateinit var toolbarTitle: TextView
    private lateinit var roomNameTextView: TextView
    private var mainMenu: Menu? = null
    private var colorSchemeMenuItem: MenuItem? = null
    
    private var username: String = ""
    private var serverAddress: String = ""
    private var password: String = ""
    private var connectivityTest: ServerConnectivityTest? = null

    private var replyingToMessageId: String = ""
    private var replyingToUser: String = ""
    private var replyingToText: String = ""

    private var selectedImageUri: Uri? = null
    private var imageUploadProgressBar: android.widget.ProgressBar? = null
    private var attachImageButton: android.widget.ImageButton? = null
    private companion object {
        private const val PICK_IMAGE_REQUEST = 1002
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        val toast = Toast.makeText(this, message, duration)
        toast.setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 100)
        toast.show()
    }
    
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                showToast("Image selected")
                updateSendButtonState()
            }
        } else if (result.resultCode == RESULT_CANCELED) {
            selectedImageUri = null
            updateSendButtonState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedColorScheme()
        applySavedLanguage()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        
        // 1. Initialize views first
        initViews()
        setupRecyclerView()
        
        // 2. Initialize connectivity test
        connectivityTest = ServerConnectivityTest()
        
        // 3. Setup observers (now connectivityTest is not null)
        setupObservers()
        
        toolbar.setNavigationOnClickListener {
            handleBackNavigation()
        }

        // Setup back press dispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })
        
        // Restore username from savedInstanceState or get from intent
        username = savedInstanceState?.getString("username") ?: intent.getStringExtra("username") ?: "User"
        serverAddress = savedInstanceState?.getString("serverAddress") ?: intent.getStringExtra("serverAddress") ?: "159.195.38.145:50051"
        password = savedInstanceState?.getString("password") ?: intent.getStringExtra("password") ?: ""
        val roomId = savedInstanceState?.getString("roomId") ?: intent.getStringExtra("roomId") ?: "general"

        // Set room ID BEFORE connecting to ensure joinMessage uses correct room
        viewModel.currentRoomId = roomId
        viewModel.grpcClient.setRoomId(roomId)

        // Update room name display
        updateRoomName(roomId)

        // Update adapter with username (trimmed to avoid whitespace issues)
        android.util.Log.d("ChatActivity", "Calling updateUsername with: '$username' (trimmed: '${username.trim()}')")
        messageAdapter.updateUsername(username.trim())

        // Only connect to server on first creation, not on theme change (recreate)
        if (savedInstanceState == null) {
            android.util.Log.d("ChatActivity", "Using server address: $serverAddress")

            // Parse server address (format: host:port)
            val parts = serverAddress.split(":")
            val host = if (parts.size >= 1) parts[0] else "localhost"
            val port = if (parts.size >= 2) parts[1].toIntOrNull() ?: 50051 else 50051

            android.util.Log.d("ChatActivity", "Parsed host: $host, port: $port")

            try {
                // Test server connectivity
                connectivityTest?.testServerReachability(host, port)

                // Connect to server with context for persistent deletion
                viewModel.connect(host, false, port, this)

                // Start chat session (callback is empty because we use StateFlow for messages)
                val joinMessage = getString(R.string.joined, username)
                viewModel.startChat(username, password, joinMessage) { }

                // Load message history for this room after connection
                android.util.Log.d("ChatActivity", "Calling switchRoom for room: $roomId")
                viewModel.switchRoom(roomId)
                viewModel.markRead(username)

                // Register FCM Token for push notifications
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val token = task.result
                            viewModel.registerToken(username, token)
                        }
                    }

                val roomDisplayName = intent.getStringExtra("roomName") ?: roomId
                showToast(getString(R.string.connected_to_room, username, roomDisplayName), Toast.LENGTH_LONG)
            } catch (e: Exception) {
                e.printStackTrace()
                addMessage("Error: ${e.message}")
            }
        } else {
            // If recreating (e.g., theme change), set room ID and load history after a short delay
            viewModel.currentRoomId = roomId
            viewModel.grpcClient.setRoomId(roomId)
            android.util.Log.d("ChatActivity", "Recreating Activity, will load history after delay")
            lifecycleScope.launch {
                delay(500)
                android.util.Log.d("ChatActivity", "Calling switchRoom after delay for room: $roomId")
                viewModel.switchRoom(roomId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Get new roomId from intent
        val newRoomId = intent.getStringExtra("roomId") ?: "general"
        val newRoomName = intent.getStringExtra("roomName")

        android.util.Log.d("ChatActivity", "onNewIntent: switching to room $newRoomId")

        // Clear messages and switch to new room
        viewModel.switchRoom(newRoomId)
        updateRoomName(newRoomId)

        if (newRoomName != null) {
            roomNameTextView.text = newRoomName
        }

        // Show toast about room change
        val roomDisplayName = newRoomName ?: newRoomId
        showToast(getString(R.string.connected_to_room, username, roomDisplayName))
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        roomNameTextView = findViewById(R.id.roomNameTextView)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "" // Clear default title
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_back_arrow)
        }

        setupEmojiPanel()

        // animateToolbarTitle()

        // Remove redundant setNavigationOnClickListener here, it's handled in onCreate

        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

        attachImageButton = findViewById(R.id.attachImageButton)
        imageUploadProgressBar = findViewById(R.id.imageUploadProgressBar)

        attachImageButton?.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        attachImageButton?.setOnLongClickListener {
            if (selectedImageUri != null) {
                selectedImageUri = null
                showToast("Image removed")
                updateSendButtonState()
                true
            } else {
                false
            }
        }

        val closeReply = findViewById<android.widget.ImageButton>(R.id.closeReply)

        closeReply.setOnClickListener {
            clearReply()
        }
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)

        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty() || selectedImageUri != null) {
                // If text is empty but image is selected, add a space
                val textToSend = if (messageText.isEmpty() && selectedImageUri != null) " " else messageText
                sendMessage(textToSend)
                messageInput.text.clear()
            }
        }

        // Update send button state based on text and image selection
        messageInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                updateSendButtonState()
            }
        })

        // Initialize send button state
        updateSendButtonState()
    }

    private fun updateSendButtonState() {
        val messageText = messageInput.text.toString().trim()
        sendButton.isEnabled = messageText.isNotEmpty() || selectedImageUri != null
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            // Объединяем состояние подключения и список пользователей
            kotlinx.coroutines.flow.combine(
                viewModel.connectionState,
                viewModel.users
            ) { isConnected, users ->
                isConnected to users.size
            }.collect { (isConnected, usersCount) ->
                if (isConnected) {
                    if (usersCount > 0) {
                        resources.getQuantityString(R.plurals.online_count, usersCount, usersCount)
                        // TODO: Use the status somewhere or remove the variable if not needed
                    }
                }
            }
        }

        // Periodically refresh users list
        lifecycleScope.launch {
            while (true) {
                delay(10000) // Refresh every 10 seconds
                if (viewModel.connectionState.value) {
                    viewModel.grpcClient.loadUsers()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    showErrorDialog(it)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.systemNotification.collect { notification ->
                notification?.let {
                    when (it) {
                        "registration_success" -> {
                            showToast(getString(R.string.registration_success), Toast.LENGTH_LONG)
                        }
                        "auth_failed" -> {
                            showToast(getString(R.string.auth_failed), Toast.LENGTH_LONG)
                            // Disconnect and return to login
                            viewModel.disconnect()
                            finish()
                        }
                    }
                    // Clear notification after showing
                    viewModel.clearSystemNotification()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.messages.collect { messages ->
                android.util.Log.d("ChatActivity", "UI received ${messages.size} messages")
                messageAdapter.submitList(messages) {
                    if (messages.isNotEmpty()) {
                        messagesRecyclerView.scrollToPosition(messages.size - 1)
                        // If we received new messages while in the chat, mark as read
                        viewModel.markRead(username)
                    }
                }
            }
        }
        
        lifecycleScope.launch {
            connectivityTest?.testResult?.collect { result ->
                result?.let { addMessage(it) }
            }
        }
    }
    
    private fun setupRecyclerView() {
        android.util.Log.d("ChatActivity", "Creating MessageAdapter with username: '$username' (trimmed: '${username.trim()}')")
        messageAdapter = MessageAdapter(username.trim(), { selectedCount ->
            val hasSelection = selectedCount > 0
            mainMenu?.let { menu ->
                menu.findItem(R.id.action_delete)?.isVisible = hasSelection
                menu.findItem(R.id.action_language)?.isVisible = !hasSelection
                menu.findItem(R.id.action_color_scheme)?.isVisible = !hasSelection
            }

            if (hasSelection) {
                toolbarTitle.text = getString(R.string.selected_count, selectedCount)
                roomNameTextView.visibility = View.GONE
            } else {
                toolbarTitle.text = getString(R.string.app_name)
                roomNameTextView.visibility = View.VISIBLE
            }
        }, { message ->
            showReactionPicker(message)
        }, { message ->
            // Swipe to reply - only on other users' messages
            if (message.user.trim() != username.trim()) {
                setReply(message.id, message.user, message.text)
            }
        })
        messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = messageAdapter
        }

        // Add swipe gesture for reply
        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val message = messageAdapter.currentList.getOrNull(position)
                    if (message != null && message.user.trim() != username.trim()) {
                        setReply(message.id, message.user, message.text)
                    }
                    messageAdapter.notifyItemChanged(position)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(messagesRecyclerView)
    }

    private fun showReactionPicker(message: Message) {
        if (message.id.isEmpty()) {
            showToast("Message ID is missing, cannot react")
            return
        }

        val emojis = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")
        val emojiView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(16, 16, 16, 16)
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(emojiView)
            .create()

        for (emoji in emojis) {
            val textView = TextView(this).apply {
                text = emoji
                textSize = 32f
                setPadding(16, 16, 16, 16)
                isClickable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener {
                    viewModel.setReaction(message.id, username, emoji)
                    dialog.dismiss()
                }
            }
            emojiView.addView(textView)
        }

        dialog.show()
    }
    
    
    private fun sendMessage(text: String) {
        if (text.isBlank() && selectedImageUri == null) {
            showToast("Message cannot be empty")
            return
        }

        val imageUri = selectedImageUri
        if (imageUri != null) {
            uploadImageToServer(imageUri) { imageUrl ->
                if (imageUrl.isNotEmpty()) {
                    val message = Message(
                        user = username,
                        text = text,
                        timestamp = System.currentTimeMillis(),
                        repliedToMessageId = replyingToMessageId,
                        repliedToUser = replyingToUser,
                        repliedToText = replyingToText,
                        roomId = viewModel.currentRoomId,
                        imageUrl = imageUrl
                    )
                    viewModel.sendMessage(message)
                    clearReply()
                    selectedImageUri = null
                    updateSendButtonState()
                } else {
                    showToast("Failed to upload image")
                }
            }
        } else {
            val message = Message(
                user = username,
                text = text,
                timestamp = System.currentTimeMillis(),
                repliedToMessageId = replyingToMessageId,
                repliedToUser = replyingToUser,
                repliedToText = replyingToText,
                roomId = viewModel.currentRoomId
            )
            viewModel.sendMessage(message)
            clearReply()
        }
    }

    private fun setReply(messageId: String, user: String, text: String) {
        replyingToMessageId = messageId
        replyingToUser = user
        replyingToText = text

        val replyQuoteView = findViewById<LinearLayout>(R.id.replyQuoteView)
        val replyUser = findViewById<TextView>(R.id.replyUser)
        val replyText = findViewById<TextView>(R.id.replyText)

        replyUser.text = user
        replyText.text = text
        replyQuoteView.visibility = android.view.View.VISIBLE
    }

    private fun clearReply() {
        replyingToMessageId = ""
        replyingToUser = ""
        replyingToText = ""

        val replyQuoteView = findViewById<LinearLayout>(R.id.replyQuoteView)
        replyQuoteView.visibility = android.view.View.GONE
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("username", username)
        outState.putString("PASSWORD", password)
        outState.putString("SERVER_ADDRESS", serverAddress)
        outState.putString("roomId", viewModel.currentRoomId)
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        mainMenu = menu
        menuInflater.inflate(R.menu.main_menu, menu)
        
        // Update language indicator text and add click handler
        val languageItem = menu?.findItem(R.id.action_language)
        val languageView = languageItem?.actionView
        val languageText = languageView?.findViewById<TextView>(R.id.languageText)
        val currentLang = getSavedLanguage() ?: "en"
        languageText?.text = if (currentLang == "en") "EN" else "RU"
        
        // Add click handler for language indicator
        languageView?.setOnClickListener {
            val newLang = if (currentLang == "en") "ru" else "en"
            updateLocale(newLang)
        }

        // Save reference to color scheme menu item
        colorSchemeMenuItem = menu?.findItem(R.id.action_color_scheme)
        updateThemeIcon()

        return true
    }
    
    private fun updateThemeIcon() {
        val currentScheme = getSavedColorScheme() ?: "dark"
        val iconRes = if (currentScheme == "dark") {
            R.drawable.ic_theme_dark
        } else {
            R.drawable.ic_theme_toggle
        }
        colorSchemeMenuItem?.setIcon(iconRes)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                handleBackNavigation()
                true
            }
            R.id.action_delete -> {
                val selected = messageAdapter.getSelectedMessages()
                if (selected.isNotEmpty()) {
                    selected.forEach { viewModel.deleteMessage(it) }
                    messageAdapter.clearSelection()
                }
                true
            }
            R.id.action_color_scheme -> {
                toggleColorScheme()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun handleBackNavigation() {
        if (messageAdapter.getSelectedMessages().isNotEmpty()) {
            messageAdapter.clearSelection()
        } else {
            finish()
        }
    }

    private fun updateLocale(langCode: String) {
        saveLanguage(langCode)
        setLocale(langCode)
        recreate()
    }
    
    private fun applySavedLanguage() {
        val savedLanguage = getSavedLanguage()
        if (savedLanguage != null) {
            setLocale(savedLanguage)
        }
    }
    
    private fun applySavedColorScheme() {
        val theme = when (getSavedColorScheme()) {
            "light" -> R.style.Base_Theme_MsgClientAndroid
            else -> R.style.Theme_MsgClientAndroid_Dark
        }
        setTheme(theme)
    }
    
    private fun getSavedLanguage(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("language", null)
    }
    
    private fun saveLanguage(languageCode: String) {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit {
            putString("language", languageCode)
        }
    }
    
    private fun getSavedColorScheme(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("color_scheme", null)
    }
    
    private fun setLocale(languageCode: String) {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        
        val resources: Resources = resources
        val config: Configuration = resources.configuration
        
        config.setLocale(locale)
        createConfigurationContext(config)
        
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun toggleColorScheme() {
        val schemes = listOf("light", "dark")
        val currentScheme = getSavedColorScheme() ?: "dark"
        val currentIndex = schemes.indexOf(currentScheme)
        val nextIndex = (currentIndex + 1) % schemes.size
        val newScheme = schemes[nextIndex]

        saveColorScheme(newScheme)
        recreate()
    }
    
    private fun saveColorScheme(scheme: String) {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit {
            putString("color_scheme", scheme)
        }
    }

    private fun setupEmojiPanel() {
        val emojiContainer = findViewById<LinearLayout>(R.id.emojiContainer)
        val emojis = listOf("😀", "😂", "🥰", "😎", "🤔", "👍", "🔥", "✨", "🙌", "🎉", "🚀", "❤️")
        
        for (emoji in emojis) {
            val textView = TextView(this).apply {
                text = emoji
                textSize = 24f
                setPadding(12, 8, 12, 8)
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener {
                    messageInput.append(emoji)
                }
            }
            emojiContainer.addView(textView)
        }
    }

    private fun animateToolbarTitle() {
        val animSet = AnimationSet(true)

        // Более спокойная и редкая анимация
        val scaleAnim = ScaleAnimation(
            0.95f, 1.05f, 0.95f, 1.05f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 4000
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }

        val alphaAnim = AlphaAnimation(0.8f, 1.0f).apply {
            duration = 4000
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }

        animSet.addAnimation(scaleAnim)
        animSet.addAnimation(alphaAnim)
        toolbarTitle.startAnimation(animSet)
    }

    private fun showErrorDialog(message: String) {
        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
        val textView = dialogView.findViewById<TextView>(android.R.id.text1)
        textView.text = message
        textView.setPadding(40, 40, 40, 40)
        textView.setTextIsSelectable(true) // Позволяет выделять и копировать текст

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.error_title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.copy) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Error Message", message)
                clipboard.setPrimaryClip(clip)
                showToast(getString(R.string.copied_to_clipboard))
            }
            .show()
    }

    private fun addMessage(text: String) {
        // This is only for system messages that are NOT in the gRPC stream
        println("DEBUG: System Message: System: $text")
    }

    private fun uploadImageToServer(uri: Uri, callback: (String) -> Unit) {
        runOnUiThread {
            imageUploadProgressBar?.visibility = android.view.View.VISIBLE
            attachImageButton?.visibility = android.view.View.GONE
        }

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Check file type
                    val mimeType = contentResolver.getType(uri)
                    val isGif = mimeType == "image/gif"

                    val bytes: ByteArray
                    val mediaType: String

                    if (isGif) {
                        // Send GIF as-is to preserve animation
                        val inputStream = contentResolver.openInputStream(uri)
                        bytes = inputStream?.readBytes() ?: byteArrayOf()
                        inputStream?.close()
                        mediaType = "image/gif"
                    } else {
                        // Resize other images
                        val resizedBytes = resizeImage(uri, 1024, 1024) // Max 1024x1024

                        if (resizedBytes == null) {
                            runOnUiThread {
                                imageUploadProgressBar?.visibility = android.view.View.GONE
                                attachImageButton?.visibility = android.view.View.VISIBLE
                                showToast("Failed to resize image")
                                callback("")
                            }
                            return@withContext
                        }

                        bytes = resizedBytes
                        mediaType = "image/jpeg"
                    }

                    if (bytes.isEmpty()) {
                        runOnUiThread {
                            imageUploadProgressBar?.visibility = android.view.View.GONE
                            attachImageButton?.visibility = android.view.View.VISIBLE
                            showToast("Failed to read image")
                            callback("")
                        }
                        return@withContext
                    }

                    val requestBody = okhttp3.MultipartBody.Builder()
                        .setType(okhttp3.MultipartBody.FORM)
                        .addFormDataPart("image", if (isGif) "image.gif" else "image.jpg", bytes.toRequestBody(mediaType.toMediaTypeOrNull()))
                        .build()

                    val request = okhttp3.Request.Builder()
                        .url("http://159.195.38.145:8082/upload-image")
                        .post(requestBody)
                        .build()

                    val client = okhttp3.OkHttpClient()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val url = extractUrlFromResponse(responseBody ?: "")
                        runOnUiThread {
                            imageUploadProgressBar?.visibility = android.view.View.GONE
                            attachImageButton?.visibility = android.view.View.VISIBLE
                        }
                        callback(url)
                    } else {
                        runOnUiThread {
                            imageUploadProgressBar?.visibility = android.view.View.GONE
                            attachImageButton?.visibility = android.view.View.VISIBLE
                            showToast("Failed to upload image (HTTP ${response.code})")
                            callback("")
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    imageUploadProgressBar?.visibility = android.view.View.GONE
                    attachImageButton?.visibility = android.view.View.VISIBLE
                    showToast("Error: ${e.message}")
                    callback("")
                }
            }
        }
    }

    private fun resizeImage(uri: Uri, maxWidth: Int, maxHeight: Int): ByteArray? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val options = android.graphics.BitmapFactory.Options()
            options.inJustDecodeBounds = true
            android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            options.inJustDecodeBounds = false

            val inputStream2 = contentResolver.openInputStream(uri) ?: return null
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream2, null, options)
            inputStream2.close()

            if (bitmap == null) {
                return null
            }

            // Resize to exact dimensions
            val width = bitmap.width
            val height = bitmap.height
            val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)

            val scaledWidth = (width * scale).toInt()
            val scaledHeight = (height * scale).toInt()

            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
            bitmap.recycle()

            // Compress to JPEG with 85% quality
            val outputStream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
            scaledBitmap.recycle()

            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun extractUrlFromResponse(response: String): String {
        val regex = """"url":\s*"([^"]+)"""".toRegex()
        val match = regex.find(response)
        return match?.groupValues?.get(1) ?: ""
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // We don't necessarily want to disconnect on rotate if using ViewModel
    }

    private fun updateRoomName(roomId: String) {
        if (roomId == "general") {
            roomNameTextView.text = getString(R.string.general_chat)
            return
        }
        
        // Try to get chat name from intent first (passed from ChatListActivity)
        val intentName = intent.getStringExtra("roomName")
        if (!intentName.isNullOrEmpty()) {
            roomNameTextView.text = intentName
            return
        }

        // For private chats, try to extract the other user's name
        // Room ID format is typically "user1_user2_direct"
        if (roomId.endsWith("_direct")) {
            val parts = roomId.removeSuffix("_direct").split("_")
            if (parts.size >= 2) {
                val otherUser = if (parts[0] == username) parts[1] else parts[0]
                roomNameTextView.text = getString(R.string.private_chat_with, otherUser)
                return
            }
        }
        
        // Fallback: fetch chats to find the name for this roomId
        viewModel.grpcClient.getChats(username) { chats ->
            val chat = chats.find { it.id == roomId }
            runOnUiThread {
                if (chat != null) {
                    roomNameTextView.text = chat.name
                } else {
                    roomNameTextView.text = roomId
                }
            }
        }
    }

}
