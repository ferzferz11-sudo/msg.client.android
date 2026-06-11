package lavender.client.android.ui.remote

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeUi
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
    private lateinit var taskTypeChipGroup: ChipGroup

    private lateinit var adapter: ChatMessageAdapter
    private var userId: String = ""
    private var selectedTaskType: String = "shell"

    private val taskTypes = listOf(
        "shell" to "Shell",
        "git" to "Git",
        "build" to "Сборка",
        "deploy" to "Деплой",
        "file" to "Файлы",
        "docker" to "Docker",
        "ai" to "AI"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_remote_agent)

        userId = SessionManager.session.value.userId

        // Apply theme
        ThemeUi.bind(this, userId)

        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory).get(RemoteAgentViewModel::class.java)

        toolbar = findViewById(R.id.toolbar)
        statusIndicator = findViewById(R.id.statusIndicator)
        statusText = findViewById(R.id.statusText)
        chatWidget = findViewById(R.id.chatWidget)
        progressBar = findViewById(R.id.progressBar)
        taskTypeChipGroup = findViewById(R.id.taskTypeChipGroup)

        // Toolbar setup
        toolbar.title = "Агенты"
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.remote_agent_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, RemoteAgentSettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Status bar
        updateStatus(false)

        // Task type chips
        setupTaskTypeChips()

        // Chat
        setupChatWidget()
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

        // Load agents and refresh status
        viewModel.loadAgents()
        lifecycleScope.launch {
            kotlinx.coroutines.delay(1000)
            viewModel.refreshAgentStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAgentStatus()
    }

    private fun setupTaskTypeChips() {
        val theme = ThemeStore.currentTheme()
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val surfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)

        taskTypeChipGroup.removeAllViews()
        taskTypeChipGroup.isSingleSelection = true

        taskTypes.forEachIndexed { index, (key, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = key == "shell" // default
                setTextColor(txtColor)
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(surfaceColor)
                chipStrokeColor = android.content.res.ColorStateList.valueOf(primColor)
                chipStrokeWidth = 2f
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedTaskType = key
                        // Uncheck others
                        for (i in 0 until taskTypeChipGroup.childCount) {
                            val other = taskTypeChipGroup.getChildAt(i) as? Chip
                            if (other != null && other != this) other.isChecked = false
                        }
                    }
                }
            }
            taskTypeChipGroup.addView(chip)
        }
    }

    private fun setupChatWidget() {
        adapter = ChatMessageAdapter(
            currentUserId = userId,
            showAvatars = false,
            showNames = false
        )
        chatWidget.setAdapter(adapter)
        chatWidget.messageInput.hint = "Отправить задачу агенту..."

        // Hide ChatWidget's own toolbar — we have our own in the activity
        chatWidget.toolbar.visibility = View.GONE

        chatWidget.setOnSendMessageListener { text ->
            if (text.isNotBlank()) {
                viewModel.sendMessage(text.trim(), userId, selectedTaskType)
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.messages.collect { msgs ->
                    val items = msgs.map { msg ->
                        val content = if (msg.isUser && msg.taskType.isNotEmpty()) {
                            "[${msg.taskType.uppercase()}] ${msg.content}"
                        } else {
                            msg.content
                        }
                        ChatMessageItem(
                            id = msg.id,
                            content = content,
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

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.isTyping.collect { isTyping ->
                    val typingText = if (isTyping) "Агент выполняет..." else ""
                    chatWidget.setToolbarSubtitle(typingText, isTyping)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { loading ->
                    progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.isConnected.collect { connected ->
                    updateStatus(connected)
                }
            }
        }

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
        val txtColor = ThemeUtils.parseSafeColor(ThemeStore.currentTheme().textSecondaryColor, Color.GRAY)
        statusText.setTextColor(txtColor)
    }
}
