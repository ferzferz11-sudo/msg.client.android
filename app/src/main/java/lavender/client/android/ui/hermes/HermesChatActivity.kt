package lavender.client.android.ui.hermes

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.models.AgentInfo
import lavender.client.android.data.models.HermesMessage
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.chat.widget.ChatMessageAdapter
import lavender.client.android.ui.chat.widget.ChatMessageItem
import lavender.client.android.ui.chat.widget.ChatWidget

/**
 * HermesChatActivity — чат с оркестратором агентов.
 *
 * Использует единый ChatWidget (как и групповой чат).
 * Агенты отображаются как участники группового чата:
 * - Каждый агент имеет emoji-иконку и имя
 * - Сообщения от разных агентов визуально различаются
 * - Тап по чипу агента → переключение на прямой чат
 */
class HermesChatActivity : AppCompatActivity() {

    private lateinit var viewModel: HermesChatViewModel
    private lateinit var adapter: ChatMessageAdapter
    private lateinit var chatWidget: ChatWidget
    private lateinit var progressBar: ProgressBar

    private var userId: String = ""
    private var chatId: String = ""

    // Agent registry — агенты как участники
    private val agents = mutableListOf<AgentInfo>()
    private var activeAgentId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hermes_chat)

        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        userId = SessionManager.session.value.username

        viewModel = androidx.lifecycle.ViewModelProvider(this)[HermesChatViewModel::class.java]

        chatWidget = findViewById(R.id.chatWidget)
        progressBar = findViewById(R.id.progressBar)

        setupToolbar()
        setupRecyclerView()
        setupInput()
        observeState()
        ThemeUi.bind(this, userId)

        // Handle window insets
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

        android.util.Log.d("HermesChatActivity", "onCreate: chatId=$chatId userId=$userId")

        // Initialize preset agents if empty
        if (viewModel.agents.value.isEmpty()) {
            viewModel.initPresetAgents()
        }
        // Sync local agents list from ViewModel
        agents.clear()
        agents.addAll(viewModel.agents.value)
        updateAgentParticipants()

        if (chatId.isEmpty()) {
            viewModel.createSession(userId)
        } else {
            viewModel.loadHistory()
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        chatWidget.setToolbarTitle("Hermes")
        chatWidget.setToolbarAgentIcon("🎼", true)
        chatWidget.setToolbarAvatar(false)

        // Show agents as participants in header
        updateAgentParticipants()
    }

    /**
     * Обновить список агентов-участников в тулбаре.
     * Каждый агент отображается как emoji-чип.
     * Активный агент визуально выделен.
     */
    private fun updateAgentParticipants() {
        chatWidget.clearParticipantChips()

        agents.forEach { agent ->
            val chip = createAgentChip(agent, agent.id == activeAgentId)
            chatWidget.addParticipantChip(chip)
        }
    }

    private fun createAgentChip(agent: AgentInfo, isActive: Boolean): com.google.android.material.chip.Chip {
        val chip = com.google.android.material.chip.Chip(this).apply {
            text = "${agent.icon.ifEmpty { "🤖" }} ${agent.name}"
            textSize = 11f
            isClickable = true
            isCheckable = false

            if (isActive) {
                // Active agent: highlighted
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.chip_background_active, null)
                )
                setTextColor(resources.getColor(R.color.chip_text_active, null))
                chipStrokeWidth = 2f
                chipStrokeColor = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.chip_stroke_active, null)
                )
            } else {
                // Inactive agent: subtle
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.chip_background, null)
                )
                setTextColor(resources.getColor(R.color.chip_text, null))
                chipStrokeWidth = 0f
            }

            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 4.dpToPx()
            }
            setOnClickListener {
                switchToAgent(agent)
            }
        }
        return chip
    }

    private fun switchToAgent(agent: AgentInfo) {
        activeAgentId = agent.id
        viewModel.switchAgent(agent.id, agents)
        chatWidget.setToolbarTitle(agent.name)
        chatWidget.setToolbarAgentIcon(agent.icon.ifEmpty { "🤖" }, true)
        chatWidget.setToolbarAvatar(false)

        // Re-render chips to update active state
        updateAgentParticipants()

        Toast.makeText(this, "Переключение на ${agent.name}", Toast.LENGTH_SHORT).show()
    }

    private fun setupRecyclerView() {
        adapter = ChatMessageAdapter(
            currentUserId = userId,
            showAvatars = false,     // Hermes uses emoji icons
            showNames = true         // Show agent names
        )
        chatWidget.setAdapter(adapter)
    }

    private fun setupInput() {
        chatWidget.setOnSendMessageListener { text ->
            val session = viewModel.currentSession.value
            if (session == null) {
                Toast.makeText(this, "Сессия не создана", Toast.LENGTH_SHORT).show()
                return@setOnSendMessageListener
            }

            // Add user message immediately
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

            // Send via ViewModel
            val currentAgent = viewModel.currentAgent.value
            viewModel.sendMessage(
                text = text,
                agentId = currentAgent?.id ?: ""
            )
        }
    }

    private fun observeState() {
        // Messages from ViewModel → convert to ChatMessageItems
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { hermesMessages ->
                    val items = hermesMessages.map { it.toChatMessageItem() }
                    adapter.submitList(items)
                    if (items.isNotEmpty()) {
                        chatWidget.scrollToBottom()
                    }
                }
            }
        }

        // Session
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentSession.collect { session ->
                    session?.let {
                        if (chatId.isNotEmpty() && it.id.isNotEmpty()) {
                            viewModel.loadHistory()
                        }
                    }
                }
            }
        }

        // Typing
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isTyping.collect { isTyping ->
                    val typingText = if (isTyping) {
                        "${viewModel.currentAgent.value?.name ?: "Агент"} печатает..."
                    } else ""
                    chatWidget.setToolbarSubtitle(typingText, isTyping)

                    if (isTyping) {
                        val typingItem = ChatMessageItem(
                            id = "typing",
                            content = "",
                            senderId = "typing",
                            senderName = viewModel.currentAgent.value?.name ?: "Агент",
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

        // Current agent → update toolbar
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentAgent.collect { agent ->
                    if (agent != null) {
                        activeAgentId = agent.id
                        chatWidget.setToolbarTitle(agent.name)
                        chatWidget.setToolbarAgentIcon(agent.icon.ifEmpty { "🤖" }, true)
                        updateAgentParticipants()
                    } else {
                        activeAgentId = ""
                        chatWidget.setToolbarTitle("Hermes")
                        chatWidget.setToolbarAgentIcon("🎼", true)
                        updateAgentParticipants()
                    }
                }
            }
        }

        // Loading state
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
                        Toast.makeText(this@HermesChatActivity, it, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    /**
     * Добавить агента как участника чата.
     */
    fun addAgent(agent: AgentInfo) {
        if (agents.none { it.id == agent.id }) {
            agents.add(agent)
            updateAgentParticipants()
        }
    }

    /**
     * Удалить агента из участников.
     */
    fun removeAgent(agentId: String) {
        agents.removeAll { it.id == agentId }
        updateAgentParticipants()
    }

    /**
     * Получить список текущих агентов-участников.
     */
    fun getAgents(): List<AgentInfo> = agents.toList()

    private fun HermesMessage.toChatMessageItem(): ChatMessageItem {
        val agent = viewModel.getAgent(this.agentId)
        return ChatMessageItem(
            id = this.id,
            content = this.content,
            senderId = this.agentId.ifEmpty { "hermes" },
            senderName = this.agentName.ifEmpty { agent?.name ?: "Hermes" },
            senderEmoji = this.agentIcon.ifEmpty { agent?.icon ?: "🤖" },
            timestamp = this.timestamp,
            isCurrentUser = this.role == "user",
            isRead = true
        )
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
