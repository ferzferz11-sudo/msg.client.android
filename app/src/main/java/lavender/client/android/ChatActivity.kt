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
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lavender.client.android.data.models.Message
import lavender.client.android.ui.adapter.MessageAdapter
import lavender.client.android.ui.chat.ChatViewModel
import lavender.client.android.data.grpc.ServerConnectivityTest

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
    
    private var username: String = ""
    private var serverAddress: String = ""
    private var password: String = ""
    private var connectivityTest: ServerConnectivityTest? = null

    private var replyingToMessageId: String = ""
    private var replyingToUser: String = ""
    private var replyingToText: String = ""
    
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
        
        // Setup back press dispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToChatList()
            }
        })
        
        // Restore username from savedInstanceState or get from intent
        username = savedInstanceState?.getString("username") ?: intent.getStringExtra("username") ?: "User"
        serverAddress = savedInstanceState?.getString("SERVER_ADDRESS") ?: intent.getStringExtra("SERVER_ADDRESS") ?: getString(R.string.server_address)
        password = savedInstanceState?.getString("PASSWORD") ?: intent.getStringExtra("PASSWORD") ?: ""
        val roomId = savedInstanceState?.getString("roomId") ?: intent.getStringExtra("roomId") ?: "general"

        // Set room in ViewModel
        viewModel.grpcClient.setRoomId(roomId)
        viewModel.currentRoomId = roomId

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
                android.util.Log.d("ChatActivity", "Calling loadHistory for room: $roomId")
                viewModel.loadHistory()

                // Register FCM Token for push notifications
                com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val token = task.result
                            viewModel.registerToken(username, token)
                        }
                    }

                Toast.makeText(this, "Connecting to $serverAddress as $username...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                addMessage("Error: ${e.message}")
            }
        } else {
            // If recreating (e.g., theme change), load history after a short delay
            android.util.Log.d("ChatActivity", "Recreating Activity, will load history after delay")
            lifecycleScope.launch {
                delay(500)
                android.util.Log.d("ChatActivity", "Calling loadHistory after delay for room: $roomId")
                viewModel.loadHistory()
            }
        }
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        roomNameTextView = findViewById(R.id.roomNameTextView)
        val usersButton = findViewById<android.widget.ImageButton>(R.id.usersButton)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "" // Clear default title
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_back_arrow)
        }

        setupEmojiPanel()

        animateToolbarTitle()

        toolbar.setNavigationOnClickListener {
            navigateToChatList()
        }

        usersButton.setOnClickListener {
            showUsersDialog()
        }

        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)

        val closeReply = findViewById<android.widget.ImageButton>(R.id.closeReply)

        closeReply.setOnClickListener {
            clearReply()
        }
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)

        sendButton.setOnClickListener {
            val messageText = messageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                sendMessage(messageText)
                messageInput.text.clear()
            }
        }
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
                            Toast.makeText(this@ChatActivity, getString(R.string.registration_success), Toast.LENGTH_LONG).show()
                        }
                        "auth_failed" -> {
                            Toast.makeText(this@ChatActivity, getString(R.string.auth_failed), Toast.LENGTH_LONG).show()
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
                println("DEBUG: ChatActivity - UI received ${messages.size} messages")
                messageAdapter.submitList(messages) {
                    if (messages.isNotEmpty()) {
                        messagesRecyclerView.scrollToPosition(messages.size - 1)
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
            Toast.makeText(this, "Message ID is missing, cannot react", Toast.LENGTH_SHORT).show()
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
        if (text.isBlank()) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
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
        
        // Update theme icon based on current theme
        updateThemeIcon(menu)
        
        return true
    }
    
    private fun updateThemeIcon(menu: Menu?) {
        val themeItem = menu?.findItem(R.id.action_color_scheme)
        val currentScheme = getSavedColorScheme() ?: "light"
        val iconRes = if (currentScheme == "dark") {
            R.drawable.ic_theme_dark
        } else {
            R.drawable.ic_theme_toggle
        }
        themeItem?.setIcon(iconRes)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Navigate back to chat list instead of logout
                navigateToChatList()
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
                // Toggle color scheme
                val currentScheme = getSavedColorScheme() ?: "light"
                val newScheme = if (currentScheme == "light") "dark" else "light"
                applyTheme(newScheme)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun navigateToChatList() {
        val intent = Intent(this, ChatListActivity::class.java)
        intent.putExtra("username", username)
        intent.putExtra("password", password)
        startActivity(intent)
        finish()
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
        val savedScheme = getSavedColorScheme()
        if (savedScheme != null) {
            val theme = when (savedScheme) {
                "dark" -> R.style.Theme_MsgClientAndroid_Dark
                else -> R.style.Theme_MsgClientAndroid
            }
            setTheme(theme)
        }
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

    private fun applyTheme(themeName: String) {
        saveColorScheme(themeName)
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
                Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun addMessage(text: String) {
        // This is only for system messages that are NOT in the gRPC stream
        println("DEBUG: System Message: System: $text")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // We don't necessarily want to disconnect on rotate if using ViewModel
    }

    private fun updateRoomName(roomId: String) {
        val roomName = if (roomId == "general") {
            getString(R.string.general_chat)
        } else {
            // For private chats, try to extract the other user's name
            // Room ID format is typically "user1_user2" or similar
            val parts = roomId.split("_")
            if (parts.size >= 2) {
                val otherUser = if (parts[0] == username) parts[1] else parts[0]
                getString(R.string.private_chat_with, otherUser)
            } else {
                roomId
            }
        }
        roomNameTextView.text = roomName
    }

    private fun showUsersDialog() {
        // Load all users and online users
        viewModel.grpcClient.loadAllUsers()
        viewModel.grpcClient.loadUsers()

        lifecycleScope.launch {
            // Wait a bit for users to load
            kotlinx.coroutines.delay(500)

            val allUsers = viewModel.allUsers.value
            val onlineUsers = viewModel.users.value

            if (allUsers.isEmpty()) {
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, getString(R.string.no_users_available), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            runOnUiThread {
                val container = android.widget.LinearLayout(this@ChatActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(16, 16, 16, 16)
                }

                // Sort users: online first, then offline
                val sortedUsers = allUsers.sortedWith(compareByDescending<String> { onlineUsers.contains(it) }.thenBy { it })

                for (user in sortedUsers) {
                    val userView = layoutInflater.inflate(R.layout.item_user, container, false)
                    val statusIndicator = userView.findViewById<View>(R.id.statusIndicator)
                    val usernameText = userView.findViewById<TextView>(R.id.usernameText)

                    val isOnline = onlineUsers.contains(user)
                    statusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        if (isOnline) getColor(android.R.color.holo_green_dark)
                        else getColor(android.R.color.darker_gray)
                    )

                    usernameText.text = user
                    container.addView(userView)
                }

                val dialog = android.app.AlertDialog.Builder(this@ChatActivity)
                    .setTitle(getString(R.string.select_user))
                    .setView(container)
                    .setPositiveButton(android.R.string.cancel, null)
                    .show()

                // Set click listeners after dialog is created
                for (i in 0 until container.childCount) {
                    val userView = container.getChildAt(i)
                    val usernameText = userView.findViewById<TextView>(R.id.usernameText)
                    val user = usernameText.text.toString()

                    userView.setOnClickListener {
                        if (user != username) {
                            createDirectChat(user)
                        }
                        dialog.dismiss()
                    }
                }
            }
        }
    }

    private fun createDirectChat(targetUser: String) {
        if (targetUser == username) {
            Toast.makeText(this, getString(R.string.cannot_chat_with_yourself), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            viewModel.grpcClient.createDirectChat(username, targetUser) { chatId ->
                if (chatId != null) {
                    runOnUiThread {
                        viewModel.switchRoom(chatId)
                        updateRoomName(chatId)
                        Toast.makeText(this@ChatActivity, getString(R.string.chat_created_with, targetUser), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@ChatActivity, getString(R.string.failed_to_create_chat), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
