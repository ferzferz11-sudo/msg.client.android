package msg.client.android

import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.LinearLayout
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.edit
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import msg.client.android.data.models.Message
import msg.client.android.ui.adapter.MessageAdapter
import msg.client.android.ui.chat.ChatViewModel
import msg.client.android.data.grpc.ServerConnectivityTest

class ChatActivity : AppCompatActivity() {
    
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var connectionStatus: TextView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: Button
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var toolbar: Toolbar
    private lateinit var toolbarTitle: TextView
    private lateinit var deleteSelectedButton: android.widget.ImageButton
    
    private var username: String = ""
    private var serverAddress: String = ""
    private var connectivityTest: ServerConnectivityTest? = null
    
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
        
        // Restore username from savedInstanceState or get from intent
        username = savedInstanceState?.getString("USERNAME") ?: intent.getStringExtra("USERNAME") ?: "User"
        serverAddress = savedInstanceState?.getString("SERVER_ADDRESS") ?: intent.getStringExtra("SERVER_ADDRESS") ?: "159.195.38.145:50051"
        
        // Update adapter with username
        messageAdapter.updateUsername(username)
        
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
                
                // Connect to server with correct port
                viewModel.connect(host, false, port)
                
                // Start chat session (callback is empty because we use StateFlow for messages)
                val joinMessage = getString(R.string.joined, username)
                viewModel.startChat(username, joinMessage) { }
                
                Toast.makeText(this, "Connecting to $serverAddress as $username...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                addMessage("Error: ${e.message}")
            }
        }
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        deleteSelectedButton = findViewById(R.id.deleteSelectedButton)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "" // Clear default title
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_back_arrow)
        }
        
        animateToolbarTitle()
        deleteSelectedButton.setOnClickListener {
            val selectedMessages = messageAdapter.getSelectedMessages()
            selectedMessages.forEach { message ->
                viewModel.deleteMessage(message)
            }
            messageAdapter.clearSelection()
        }
        
        setupEmojiPanel()
        
        toolbar.setNavigationOnClickListener {
            logout()
        }

        connectionStatus = findViewById(R.id.connectionStatus)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        
        connectionStatus.text = getString(R.string.connecting)
        connectionStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
        
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
                val statusText = if (isConnected) {
                    val connectedStr = getString(R.string.connected)
                    if (usersCount > 0) {
                        val onlineCountStr = resources.getQuantityString(R.plurals.online_count, usersCount, usersCount)
                        "$connectedStr ($onlineCountStr)"
                    } else {
                        connectedStr
                    }
                } else {
                    getString(R.string.disconnected)
                }
                
                connectionStatus.text = statusText
                connectionStatus.setTextColor(
                    getColor(if (isConnected) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
                )
                
                // Очищаем subtitle тулбара, если он был установлен ранее
                supportActionBar?.subtitle = null
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
            viewModel.messages.collect { messages ->
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
        messageAdapter = MessageAdapter(username) { selectedCount ->
            if (selectedCount > 0) {
                deleteSelectedButton.visibility = android.view.View.VISIBLE
                toolbarTitle.visibility = android.view.View.GONE
            } else {
                deleteSelectedButton.visibility = android.view.View.GONE
                toolbarTitle.visibility = android.view.View.VISIBLE
            }
        }
        messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = messageAdapter
        }
    }
    
    private fun deleteMessage(message: Message) {
        viewModel.deleteMessage(message)
        messageAdapter.clearSelection()
    }
    
    private fun sendMessage(text: String) {
        val message = Message(user = username, text = text)
        viewModel.sendMessage(message)
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("USERNAME", username)
        outState.putString("SERVER_ADDRESS", serverAddress)
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
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

    private fun logout() {
        viewModel.disconnect()
        // Don't remove username - keep it for next login
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Error Message", message)
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
}
