package lavender.client.android.ui.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.ui.chat.widget.ChatMessageAdapter
import lavender.client.android.ui.chat.widget.ChatMessageItem
import lavender.client.android.ui.chat.widget.ChatWidget

class RemoteAgentActivity : AppCompatActivity() {

    private lateinit var viewModel: RemoteAgentViewModel
    private lateinit var toolbar: MaterialToolbar
    private lateinit var statusIndicator: View
    private lateinit var statusText: TextView
    private lateinit var chatWidget: ChatWidget
    private lateinit var progressBar: ProgressBar
    private lateinit var btnGenerateToken: com.google.android.material.button.MaterialButton
    private lateinit var btnRevokeToken: com.google.android.material.button.MaterialButton

    private lateinit var adapter: ChatMessageAdapter
    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val theme = ThemeStore.currentTheme()
        val bgColor = ThemeUtils.parseSafeColor(theme.backgroundColor)
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor)

        setContentView(R.layout.activity_remote_agent)

        userId = SessionManager.session.value.userId

        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory).get(RemoteAgentViewModel::class.java)

        toolbar = findViewById(R.id.toolbar)
        statusIndicator = findViewById(R.id.statusIndicator)
        statusText = findViewById(R.id.statusText)
        chatWidget = findViewById(R.id.chatWidget)
        progressBar = findViewById(R.id.progressBar)
        btnGenerateToken = findViewById(R.id.btnGenerateToken)
        btnRevokeToken = findViewById(R.id.btnRevokeToken)

        // Toolbar setup
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = "Удалённые агенты"
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Apply theme colors to toolbar
        toolbar.setBackgroundColor(bgColor)
        toolbar.setTitleTextColor(txtColor)
        toolbar.setNavigationIconTint(txtColor)

        // Status bar
        updateStatus(false)

        // RecyclerView via ChatWidget
        setupChatWidget()
        setupButtons()
        observeState()

        // Window insets
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            chatWidget.bottomPanel.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom
            }
            insets
        }

        // Load agents
        viewModel.loadAgents()
        viewModel.loadTokens(userId)
    }

    private fun setupChatWidget() {
        adapter = ChatMessageAdapter(
            currentUserId = userId,
            showAvatars = false,
            showNames = false
        )
        chatWidget.setAdapter(adapter)
        chatWidget.messageInput.hint = "Отправить задачу агенту..."

        chatWidget.setOnSendMessageListener { text ->
            if (text.isNotBlank()) {
                viewModel.sendMessage(text.trim(), userId)
            }
        }
    }

    private fun setupButtons() {
        val primColor = ThemeUtils.parseSafeColor(ThemeStore.currentTheme().primaryColor)

        btnGenerateToken.setOnClickListener {
            showTokenDialog()
        }

        btnRevokeToken.setOnClickListener {
            showRevokeDialog()
        }
    }

    private fun observeState() {
        // Messages
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.messages.collect { msgs ->
                    val items = msgs.map { msg ->
                        ChatMessageItem(
                            id = msg.id,
                            content = msg.content,
                            senderId = if (msg.isUser) userId else "remote_agent",
                            senderName = if (msg.isUser) "Вы" else "Агент",
                            senderEmoji = if (msg.isUser) "" else "🖥",
                            timestamp = msg.timestamp,
                            isCurrentUser = msg.isUser,
                            isRead = true
                        )
                    }
                    adapter.submitList(items)
                }
            }
        }

        // Typing
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.isTyping.collect { isTyping ->
                    val typingText = if (isTyping) "Агент печатает..." else ""
                    chatWidget.setToolbarSubtitle(typingText, isTyping)
                }
            }
        }

        // Loading
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { loading ->
                    progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                }
            }
        }

        // Connection status
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { connected ->
                    updateStatus(connected)
                }
            }
        }

        // Generated token — show one-time dialog
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.generatedToken.collect { token ->
                    if (token != null) {
                        showTokenResultDialog(token)
                        viewModel.clearGeneratedToken()
                    }
                }
            }
        }

        // Error
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.error.collect { error ->
                    error?.let {
                        Toast.makeText(this@RemoteAgentActivity, it, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    private fun updateStatus(connected: Boolean) {
        val dotColor = if (connected) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
        statusIndicator.background.setTint(dotColor)
        statusText.text = if (connected) "Агент подключён" else "Агент отключён"
        val txtColor = ThemeUtils.parseSafeColor(ThemeStore.currentTheme().textSecondaryColor)
        statusText.setTextColor(txtColor)
    }

    /**
     * Show dialog to generate a new agent token
     */
    private fun showTokenDialog() {
        val dialog = TokenDialog(
            context = this,
            theme = ThemeStore.currentTheme(),
            onGenerate = { agentName, capabilities, ttlHours ->
                val agentId = "agent_${System.currentTimeMillis()}"
                viewModel.generateToken(
                    agentId = agentId,
                    agentName = agentName,
                    capabilities = capabilities,
                    ttlHours = ttlHours,
                    adminUserId = userId
                )
            }
        )
        dialog.show()
    }

    /**
     * Show token result with copy button
     */
    private fun showTokenResultDialog(token: String) {
        val theme = ThemeStore.currentTheme()
        val bgColor = ThemeUtils.parseSafeColor(theme.surfaceColor)
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor)

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
            setBackgroundColor(bgColor)
        }

        val label = TextView(this).apply {
            text = "Токен агента (скопируйте — он показывается только  einmal):"
            setTextColor(txtColor)
            textSize = 14f
        }

        val tokenView = TextView(this).apply {
            text = token
            setTextColor(txtColor)
            textSize = 13f
            setPadding(0, 16, 0, 16)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        container.addView(label)
        container.addView(tokenView)

        AlertDialog.Builder(this)
            .setTitle("Токен сгенерирован")
            .setView(container)
            .setPositiveButton("Копировать") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Agent Token", token))
                Toast.makeText(this, "Токен скопирован", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    /**
     * Show dialog to select and revoke a token
     */
    private fun showRevokeDialog() {
        lifecycleScope.launch {
            val tokens = viewModel.tokenList.value
            if (tokens.isEmpty()) {
                Toast.makeText(this@RemoteAgentActivity, "Нет активных токенов", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val activeTokens = tokens.filter { !it.revoked }
            if (activeTokens.isEmpty()) {
                Toast.makeText(this@RemoteAgentActivity, "Нет активных токенов", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val items = activeTokens.map { "${it.agentName} (${it.tokenHash.take(8)}...)"}.toTypedArray()

            AlertDialog.Builder(this@RemoteAgentActivity)
                .setTitle("Отозвать токен")
                .setItems(items) { _, which ->
                    val token = activeTokens[which]
                    confirmRevoke(token.agentId, token.agentName)
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    private fun confirmRevoke(agentId: String, agentName: String) {
        AlertDialog.Builder(this)
            .setTitle("Отозвать токен?")
            .setMessage("Токен для \"$agentName\" будет отозван. Агент потеряет доступ.")
            .setPositiveButton("Отозвать") { _, _ ->
                viewModel.revokeToken(agentId, userId)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}
