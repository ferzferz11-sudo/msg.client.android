package msg.client.android

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
    
    private var username: String = ""
    private lateinit var connectivityTest: ServerConnectivityTest
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        
        initViews()
        setupRecyclerView()
        setupObservers()
        
        // Get username from intent
        username = intent.getStringExtra("USERNAME") ?: "User"
        
        try {
            // Initialize connectivity test
            connectivityTest = ServerConnectivityTest()
            
            // Test server connectivity first
            connectivityTest.testServerReachability("192.168.1.135")
            
            // Connect to server
            viewModel.connect("192.168.1.135", false) // Your laptop IP
            
            // Start chat session
            viewModel.startChat(username) { message ->
                // Handle incoming messages
            }
            
            Toast.makeText(this, "Connecting as $username...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            addMessage("System", "Error during initialization: ${e.message}")
        }
    }
    
    private fun initViews() {
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
        try {
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
                        // Не вызываем addMessage здесь, чтобы не плодить дубли
                        println("DEBUG: ChatActivity - Error: $it")
                    }
                }
            }
            
            lifecycleScope.launch {
                viewModel.messages.collect { messages ->
                    println("DEBUG: ChatActivity - Received ${messages.size} messages from ViewModel")
                    messageAdapter.submitList(messages) {
                        messagesRecyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            }
            
            // Observe connectivity test results
            lifecycleScope.launch {
                connectivityTest.testResult.collect { result ->
                    result?.let {
                        try {
                            addMessage("Connectivity Test", it)
                        } catch (e: Exception) {
                            addMessage("System", "Error showing test result: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            addMessage("System", "Error setting up observers: ${e.message}")
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
    
    private fun addMessage(user: String, text: String) {
        try {
            if (!::messageAdapter.isInitialized || !::messagesRecyclerView.isInitialized) {
                return
            }
            
            val message = Message(user = user, text = text)
            // Add directly to UI for connectivity test messages
            val currentMessages = viewModel.messages.value.toMutableList()
            currentMessages.add(message)
            messageAdapter.submitList(currentMessages)
            messagesRecyclerView.scrollToPosition(currentMessages.size - 1)
        } catch (e: Exception) {
            // Can't show message if UI not ready
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        viewModel.disconnect()
    }
}
