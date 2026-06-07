package lavender.client.android.ui.owl

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.grpc.getBotCommands
import lavender.client.android.data.grpc.getOWLStatus
import lavender.client.android.data.grpc.processBotCommand
import lavender.client.android.data.models.OwlMessage
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.chat.widget.ChatMessageAdapter
import lavender.client.android.ui.chat.widget.ChatMessageItem
import lavender.client.android.ui.chat.widget.ChatWidget
import androidx.appcompat.app.AppCompatActivity

/**
 * OwlChatActivity — чат с OWL AI ассистентом.
 *
 * Пользователь может:
 * - Писать сообщения OWL AI напрямую
 * - Использовать бот-команды (/status, /help, /ai и т.д.)
 * - Получать ответы в реальном времени
 */
class OwlChatActivity : AppCompatActivity() {

    private lateinit var viewModel: OwlChatViewModel
    private lateinit var adapter: ChatMessageAdapter
    private lateinit var chatWidget: ChatWidget
    private lateinit var progressBar: ProgressBar

    private var userId: String = ""
    private var username: String = ""
    private var chatId: String = ""

    // Bot command state
    private var availableCommands = emptyList<lavender.client.android.data.proto.BotCommandInfoProto>()
    private var isCommandPopupVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owl_chat)

        userId = SessionManager.session.value.userId
        username = SessionManager.session.value.username
        chatId = "owl-$userId"

        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory).get(OwlChatViewModel::class.java)

        chatWidget = findViewById(R.id.chatWidget)
        progressBar = findViewById(R.id.progressBar)

        setupToolbar()
        setupRecyclerView()
        setupInput()
        setupSlashCommandDetection()
        observeState()
        ThemeUi.bind(this, userId)

        // Window insets
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            chatWidget.bottomPanel.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = if (isImeVisible) imeInsets.bottom else systemBars.bottom
            }
            insets
        }

        Log.d("OwlChatActivity", "onCreate: chatId=$chatId userId=$userId")

        // Load available commands
        loadBotCommands()

        // Check OWL status
        checkOwlStatus()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        chatWidget.setToolbarTitle("🦞 OWL AI")
        chatWidget.setToolbarAgentIcon("🦉", true)
        chatWidget.setToolbarAvatar(false)
    }

    private fun setupRecyclerView() {
        adapter = ChatMessageAdapter(
            currentUserId = userId,
            showAvatars = false,
            showNames = true
        )
        chatWidget.setAdapter(adapter)
    }

    private fun setupInput() {
        chatWidget.commandButton.setOnClickListener {
            showCommandMenu()
        }

        chatWidget.setOnSendMessageListener { text ->
            // Check if it's a bot command
            if (text.trimStart().startsWith("/")) {
                handleBotCommand(text.trim())
                return@setOnSendMessageListener
            }

            // Regular message to OWL
            val userMessage = ChatMessageItem(
                id = java.util.UUID.randomUUID().toString(),
                content = text,
                senderId = userId,
                senderName = "Вы",
                isCurrentUser = true,
                timestamp = System.currentTimeMillis()
            )
            adapter.submitList(adapter.currentList + userMessage)
            chatWidget.scrollToBottom()

            viewModel.sendToOwl(text, userId, chatId)
        }
    }

    private fun setupSlashCommandDetection() {
        chatWidget.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s == null) return
                detectSlashCommand(s, start + count)
            }

            override fun afterTextChanged(s: Editable?) {
                val hasText = s?.toString()?.trim()?.isNotEmpty() == true
                chatWidget.sendButton.visibility = if (hasText) View.VISIBLE else View.GONE
                chatWidget.audioButton.visibility = if (hasText) View.GONE else View.VISIBLE
            }
        })
    }

    private fun detectSlashCommand(text: CharSequence, cursorPos: Int) {
        val textStr = text.toString()
        if (cursorPos <= 0 || cursorPos > textStr.length) {
            hideCommandPopup()
            return
        }

        val beforeCursor = textStr.substring(0, cursorPos)

        // Only trigger if / is at the very start of the input
        if (!beforeCursor.startsWith("/")) {
            hideCommandPopup()
            return
        }

        // Don't show popup for /ai (it needs arguments)
        if (beforeCursor.startsWith("/ai ")) {
            hideCommandPopup()
            return
        }

        // Show command suggestions
        showCommandPopup(beforeCursor)
    }

    private fun showCommandPopup(query: String) {
        if (availableCommands.isEmpty()) return

        val filtered = availableCommands.filter {
            it.command.startsWith(query.lowercase())
        }

        if (filtered.isEmpty()) {
            hideCommandPopup()
            return
        }

        isCommandPopupVisible = true
        // Show command suggestions in a popup below the input
        // For now, we'll use a simple approach: show first matching command as hint
        val firstMatch = filtered.first()
        if (firstMatch.command.length > query.length) {
            // Show autocomplete hint
            chatWidget.messageInput.hint = firstMatch.command.substring(query.length)
        }
    }

    private fun hideCommandPopup() {
        isCommandPopupVisible = false
        chatWidget.messageInput.hint = ""
    }

    private fun handleBotCommand(text: String) {
        val parts = text.split("\\s+".toRegex())
        val command = parts[0]
        val args = if (parts.size > 1) parts.subList(1, parts.size) else emptyList()

        // Add user command to chat
        val userMessage = ChatMessageItem(
            id = java.util.UUID.randomUUID().toString(),
            content = text,
            senderId = userId,
            senderName = "Вы",
            isCurrentUser = true,
            timestamp = System.currentTimeMillis()
        )
        adapter.submitList(adapter.currentList + userMessage)
        chatWidget.scrollToBottom()

        // Send to server
        lifecycleScope.launch {
            try {
                val response = processBotCommand(
                    userId = userId,
                    username = username,
                    chatId = chatId,
                    command = command,
                    args = args
                )

                val botMessage = ChatMessageItem(
                    id = java.util.UUID.randomUUID().toString(),
                    content = if (response.isError) "⚠️ ${response.errorMessage}" else response.responseText,
                    senderId = "owl-bot",
                    senderName = "🤖 OWL Bot",
                    isCurrentUser = false,
                    timestamp = System.currentTimeMillis()
                )
                adapter.submitList(adapter.currentList + botMessage)
                chatWidget.scrollToBottom()
            } catch (e: Exception) {
                Log.e("OwlChatActivity", "Bot command error", e)
                val errorMessage = ChatMessageItem(
                    id = java.util.UUID.randomUUID().toString(),
                    content = "⚠️ Ошибка: ${e.message}",
                    senderId = "owl-bot",
                    senderName = "🤖 OWL Bot",
                    isCurrentUser = false,
                    timestamp = System.currentTimeMillis()
                )
                adapter.submitList(adapter.currentList + errorMessage)
                chatWidget.scrollToBottom()
            }
        }
    }

    private fun showCommandMenu() {
        if (availableCommands.isEmpty()) {
            Toast.makeText(this, "Загрузка команд...", Toast.LENGTH_SHORT).show()
            loadBotCommands()
            return
        }

        val items = availableCommands.map { "${it.command} — ${it.description}" }.toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("📋 Команды OWL Bot")
            .setItems(items) { _, which ->
                val cmd = availableCommands[which]
                if (cmd.command == "/ai") {
                    chatWidget.messageInput.setText("/ai ")
                    chatWidget.messageInput.setSelection(4)
                } else {
                    chatWidget.messageInput.setText(cmd.command + " ")
                    chatWidget.messageInput.setSelection(cmd.command.length + 1)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadBotCommands() {
        lifecycleScope.launch {
            try {
                availableCommands = getBotCommands(userId)
                Log.d("OwlChatActivity", "Loaded ${availableCommands.size} bot commands")
            } catch (e: Exception) {
                Log.e("OwlChatActivity", "Failed to load bot commands", e)
            }
        }
    }

    private fun checkOwlStatus() {
        lifecycleScope.launch {
            try {
                val status = getOWLStatus(userId)
                if (!status.available) {
                    val warningMessage = ChatMessageItem(
                        id = "owl-status-warning",
                        content = "⚠️ OWL AI временно недоступен. Попробуйте позже.",
                        senderId = "system",
                        senderName = "Система",
                        isCurrentUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                    adapter.submitList(adapter.currentList + warningMessage)
                    chatWidget.scrollToBottom()
                }
            } catch (e: Exception) {
                Log.e("OwlChatActivity", "Failed to check OWL status", e)
            }
        }
    }

    private fun observeState() {
        // OWL messages
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.owlMessages.collect { messages ->
                    val items = messages.map { it.toChatMessageItem() }
                    adapter.submitList(items)
                    if (items.isNotEmpty()) {
                        chatWidget.scrollToBottom()
                    }
                }
            }
        }

        // Typing indicator
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isTyping.collect { isTyping ->
                    val typingText = if (isTyping) "OWL печатает..." else ""
                    chatWidget.setToolbarSubtitle(typingText, isTyping)

                    if (isTyping) {
                        val typingItem = ChatMessageItem(
                            id = "typing",
                            content = "",
                            senderId = "owl",
                            senderName = "🦉 OWL",
                            isTyping = true,
                            timestamp = System.currentTimeMillis()
                        )
                        adapter.submitList(adapter.currentList + typingItem)
                        chatWidget.scrollToBottom()
                    } else {
                        val filtered = adapter.currentList.filter { !it.isTyping }
                        adapter.submitList(filtered)
                    }
                }
            }
        }

        // Loading
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { isLoading ->
                    progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                }
            }
        }

        // Error
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.error.collect { error ->
                    error?.let {
                        Toast.makeText(this@OwlChatActivity, it, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    private fun OwlMessage.toChatMessageItem(): ChatMessageItem {
        return ChatMessageItem(
            id = this.id,
            content = this.content,
            senderId = this.senderId,
            senderName = this.senderName,
            senderEmoji = this.senderEmoji,
            timestamp = this.timestamp,
            isCurrentUser = this.isCurrentUser,
            isRead = true
        )
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
