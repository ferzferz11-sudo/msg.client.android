package lavender.client.android.ui.hermes

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.models.AgentInfo
import lavender.client.android.data.models.HermesMessage
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.chat.widget.ChatMessageAdapter
import lavender.client.android.ui.chat.widget.ChatMessageItem

/**
 * HermesChatActivity — чат с оркестратором агентов.
 * 
 * Использует единый ChatWidget (как и групповой чат).
 * Агенты отображаются как участники группового чата:
 * - Каждый агент имеет emoji-иконку и имя
 * - Сообщения от разных агентов визуально различаются
 * - Можно добавлять новых агентов как участников
 */
class HermesChatActivity : AppCompatActivity() {

    private lateinit var viewModel: HermesChatViewModel
    private lateinit var adapter: ChatMessageAdapter

    // Widget views
    private lateinit var messagesRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var messageInput: android.widget.EditText
    private lateinit var sendButton: android.widget.ImageButton
    private lateinit var toolbarTitle: android.widget.TextView
    private lateinit var toolbarSubtitle: android.widget.TextView
    private lateinit var toolbarAgentIcon: android.widget.TextView
    private lateinit var groupHeader: android.view.View
    private lateinit var groupParticipantsContainer: android.widget.LinearLayout
    private lateinit var bottomPanel: com.google.android.material.card.MaterialCardView

    private var userId: String = ""
    private var chatId: String = ""

    // Agent registry — агенты как участники
    private val agents = mutableListOf<AgentInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hermes_chat)

        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        userId = SessionManager.session.value.username

        viewModel = androidx.lifecycle.ViewModelProvider(this)[HermesChatViewModel::class.java]

        initViews()
        setupRecyclerView()
        setupInput()
        setupToolbar()
        observeState()
        ThemeUi.bind(this, userId)

        // Handle window insets
        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            bottomPanel.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = if (isImeVisible) imeInsets.bottom else systemBars.bottom
            }
            insets
        }

        // Create session and load history
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

    private fun initViews() {
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        toolbarSubtitle = findViewById(R.id.toolbarSubtitle)
        toolbarAgentIcon = findViewById(R.id.toolbarAgentIcon)
        groupHeader = findViewById(R.id.groupHeader)
        groupParticipantsContainer = findViewById(R.id.groupParticipantsContainer)
        bottomPanel = findViewById(R.id.bottomPanel)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        toolbarTitle.text = "Hermes"
        toolbarAgentIcon.text = "🎼"
        toolbarAgentIcon.visibility = View.VISIBLE

        // Show agents as participants in header
        updateAgentParticipants()
    }

    /**
     * Обновить список агентов-участников в тулбаре.
     * Каждый агент отображается как emoji-чип.
     */
    private fun updateAgentParticipants() {
        groupParticipantsContainer.removeAllViews()

        agents.forEach { agent ->
            val chip = createAgentChip(agent)
            groupParticipantsContainer.addView(chip)
        }

        // Always show group header for Hermes (it's a multi-agent chat)
        groupHeader.visibility = View.VISIBLE
        groupParticipantsContainer.visibility = View.VISIBLE
    }

    private fun createAgentChip(agent: AgentInfo): com.google.android.material.chip.Chip {
        val chip = com.google.android.material.chip.Chip(this).apply {
            text = "${agent.icon.ifEmpty { "🤖" }} ${agent.name}"
            textSize = 11f
            isClickable = true
            isCheckable = false
            chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                resources.getColor(R.color.chip_background, null)
            )
            setTextColor(resources.getColor(R.color.chip_text, null))
            chipStrokeWidth = 0f
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
        viewModel.switchAgent(agent.id, agents)
        toolbarTitle.text = agent.name
        toolbarAgentIcon.text = agent.icon.ifEmpty { "🤖" }
        Toast.makeText(this, "Переключение на ${agent.name}", Toast.LENGTH_SHORT).show()
    }

    private fun setupRecyclerView() {
        adapter = ChatMessageAdapter(
            currentUserId = userId,
            showAvatars = false,     // Hermes uses emoji icons
            showNames = true         // Show agent names
        )
        messagesRecyclerView.adapter = adapter
        messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
    }

    private fun setupInput() {
        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener

            val session = viewModel.currentSession.value
            if (session == null) {
                Toast.makeText(this, "Сессия не создана", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
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
            scrollToBottom()

            messageInput.setText("")

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
                        scrollToBottom()
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
                    toolbarSubtitle.visibility = if (isTyping) View.VISIBLE else View.GONE
                    toolbarSubtitle.text = "печатает..."

                    if (isTyping) {
                        // Add typing indicator as last item
                        val typingItem = ChatMessageItem(
                            id = "typing",
                            content = "",
                            senderId = "typing",
                            senderName = viewModel.currentAgent.value?.name ?: "Агент",
                            isTyping = true,
                            timestamp = System.currentTimeMillis()
                        )
                        adapter.submitList(adapter.currentList + typingItem)
                        scrollToBottom()
                    } else {
                        // Remove typing indicator
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
                        toolbarTitle.text = agent.name
                        toolbarAgentIcon.text = agent.icon.ifEmpty { "🤖" }
                    } else {
                        toolbarTitle.text = "Hermes"
                        toolbarAgentIcon.text = "🎼"
                    }
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

    private fun scrollToBottom() {
        if (adapter.itemCount > 0) {
            messagesRecyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }

    /**
     * Добавить агента как участника чата.
     * Вызывается из AgentListActivity или при маршрутизации оркестратора.
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
        // Find agent info from ViewModel agents list
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
