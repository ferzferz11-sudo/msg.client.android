package msg.client.android

import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
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
import msg.client.android.R
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
    
    private var username: String = ""
    private var connectivityTest: ServerConnectivityTest? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        
        // 1. Initialize views first
        initViews()
        setupRecyclerView()
        
        // 2. Initialize connectivity test
        connectivityTest = ServerConnectivityTest()
        
        // 3. Setup observers (now connectivityTest is not null)
        setupObservers()
        
        // Get username from intent
        username = intent.getStringExtra("USERNAME") ?: "User"
        
        try {
            // Test server connectivity
            connectivityTest?.testServerReachability("192.168.1.135")
            
            // Connect to server
            viewModel.connect("192.168.1.135", false)
            
            // Start chat session (callback is empty because we use StateFlow for messages)
            viewModel.startChat(username) { }
            
            Toast.makeText(this, "Connecting as $username...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            addMessage("System", "Error: ${e.message}")
        }
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "" // Clear default title
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(android.R.drawable.ic_menu_revert)
        }
        
        animateToolbarTitle()
        
        toolbar.setNavigationOnClickListener {
            logout()
        }

        connectionStatus = findViewById(R.id.connectionStatus)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        
        connectionStatus.text = "Connecting..."
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
            viewModel.connectionState.collect { isConnected ->
                connectionStatus.text = if (isConnected) "Connected" else "Disconnected"
                connectionStatus.setTextColor(
                    getColor(if (isConnected) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
                )
            }
        }
        
        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(this@ChatActivity, it, Toast.LENGTH_LONG).show()
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
                result?.let { addMessage("System", it) }
            }
        }
    }
    
    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter()
        messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            adapter = messageAdapter
        }
    }
    
    private fun sendMessage(text: String) {
        val message = Message(user = username, text = text)
        viewModel.sendMessage(message)
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.lang_en -> {
                updateLocale("en")
                true
            }
            R.id.lang_ru -> {
                updateLocale("ru")
                true
            }
            R.id.lang_zh -> {
                updateLocale("zh")
                true
            }
            R.id.color_light -> {
                applyTheme("light")
                true
            }
            R.id.color_dark -> {
                applyTheme("dark")
                true
            }
            R.id.color_blue -> {
                applyTheme("blue")
                true
            }
            R.id.action_expert_mode -> {
                item.isChecked = !item.isChecked
                val message = if (item.isChecked) {
                    getString(R.string.expert_mode_enabled)
                } else {
                    getString(R.string.expert_mode_disabled)
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_test_connection -> {
                connectivityTest?.testServerReachability("192.168.1.135")
                true
            }
            R.id.action_settings -> {
                // Settings action if needed
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateLocale(langCode: String) {
        val locale = java.util.Locale(langCode)
        java.util.Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
        recreate()
    }

    private fun applyTheme(themeName: String) {
        Toast.makeText(this, "Theme applied: $themeName", Toast.LENGTH_SHORT).show()
        // Here you would normally change the theme in SharedPreferences and recreate
        // Since I don't see a custom theme engine, I'll just show the toast for now
        // If you have specific Theme resource IDs, we can use setTheme()
    }

    private fun logout() {
        viewModel.disconnect()
        // Очищаем сохраненное имя, чтобы MainActivityMinimal не перекидывал обратно в чат
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit().remove("username").apply()

        val intent = Intent(this, MainActivityMinimal::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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

    private fun addMessage(user: String, text: String) {
        // This is only for system messages that are NOT in the gRPC stream
        println("DEBUG: System Message: $user: $text")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // We don't necessarily want to disconnect on rotate if using ViewModel
    }
}
