package lavender.client.android.ui.hermes

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
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
import lavender.client.android.ui.chat.widget.MentionItem

/**
 * HermesChatActivity — чат с оркестратором агентов.
 *
 * Использует единый ChatWidget.
 * Агенты отображаются как участники группового чата.
 * Поддержка меншена: @ → popup с выбором агента.
 */
class HermesChatActivity : AppCompatActivity() {

    private lateinit var viewModel: HermesChatViewModel
    private lateinit var adapter: ChatMessageAdapter
    private lateinit var chatWidget: ChatWidget
    private lateinit var progressBar: ProgressBar

    private var userId: String = ""
    private var chatId: String = ""

    // Agent registry
    private val agents = mutableListOf<AgentInfo>()
    private var activeAgentId: String = ""

    // Mention state
    private var mentionQuery: String = ""
    private var mentionStartPos: Int = -1
    private var isMentionActive = false

    // Mention items derived from agents
    private val mentionItems: List<MentionItem>
        get() = agents.map { agent ->
            MentionItem(
                id = agent.id,
                name = agent.name,
                description = agent.description,
                emoji = agent.icon.ifEmpty { "🤖" },
                mentionTag = agent.name.lowercase().replace(" ", "_")
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hermes_chat)

        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        userId = SessionManager.session.value.username

        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory).get(HermesChatViewModel::class.java)

        chatWidget = findViewById(R.id.chatWidget)
        progressBar = findViewById(R.id.progressBar)

        setupToolbar()
        setupRecyclerView()
        setupInput()
        setupMentionListener()
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

        if (viewModel.agents.value.isEmpty()) {
            viewModel.initPresetAgents()
        }
        agents.clear()
        agents.addAll(viewModel.agents.value)
        updateAgentParticipants()

        if (chatId.isEmpty()) {
            // New session — create on server
            viewModel.createSession(userId)
        } else {
            // Existing session from chat list
            val agentId = intent.getStringExtra("ACTIVE_AGENT_ID") ?: ""
            val mode = intent.getStringExtra("AGENT_MODE") ?: "single"
            viewModel.setExistingSession(chatId, userId, agentId, mode)
            viewModel.loadHistory()
        }

        intent.getStringExtra("PREFILL_MESSAGE")?.let {
            chatWidget.messageInput.setText(it)
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        chatWidget.setToolbarTitle(getString(R.string.hermes_chat_title))
        chatWidget.setToolbarAgentIcon("🎼", true)
        chatWidget.setToolbarAvatar(false)

        updateAgentParticipants()
    }

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
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.chip_background_active, null)
                )
                setTextColor(resources.getColor(R.color.chip_text_active, null))
                chipStrokeWidth = 2f
                chipStrokeColor = android.content.res.ColorStateList.valueOf(
                    resources.getColor(R.color.chip_stroke_active, null)
                )
            } else {
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
        updateAgentParticipants()
        Toast.makeText(this, "Переключение на ${agent.name}", Toast.LENGTH_SHORT).show()
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
        // Show attach/audio buttons for Hermes chat
        chatWidget.attachButton.visibility = View.VISIBLE
        chatWidget.audioButton.visibility = View.VISIBLE

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

        chatWidget.setOnEmojiClickListener {
            chatWidget.showEmojiPicker()
        }

        chatWidget.attachButton.setOnClickListener {
            showAttachmentSheet()
        }

        chatWidget.audioButton.setOnClickListener {
            showVoiceRecorder()
        }
    }

    private fun showAttachmentSheet() {
        val sheet = lavender.client.android.ui.widget.StandardBottomSheet(this, R.layout.dialog_emoji_picker)
        // TODO: replace with proper attachment sheet
        Toast.makeText(this, "Вложения — в разработке", Toast.LENGTH_SHORT).show()
    }

    private fun showVoiceRecorder() {
        Toast.makeText(this, "Голосовые — в разработке", Toast.LENGTH_SHORT).show()
    }

    // ===== Mention logic =====

    private fun setupMentionListener() {
        // Track @ in input
        chatWidget.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s == null) return
                detectMention(s, start + count)
            }

            override fun afterTextChanged(s: Editable?) {
                val hasText = s?.toString()?.trim()?.isNotEmpty() == true
                chatWidget.sendButton.visibility = if (hasText) View.VISIBLE else View.GONE
                chatWidget.audioButton.visibility = if (hasText) View.GONE else View.VISIBLE
            }
        })

        // Handle mention selection
        chatWidget.setOnMentionSelectedListener { item ->
            insertMention(item)
        }
    }

    private fun detectMention(text: CharSequence, cursorPos: Int) {
        // Safety: toString() bypasses SpannableBuilder issues
        val textStr = text.toString()
        val len = textStr.length
        
        if (cursorPos <= 0 || cursorPos > len) {
            hideMention()
            return
        }

        // Find the last @ before cursor
        val beforeCursor = textStr.substring(0, cursorPos)
        val atPos = beforeCursor.lastIndexOf('@')

        if (atPos == -1) {
            hideMention()
            return
        }

        // Check that @ is at word boundary or start of text
        if (atPos > 0 && !beforeCursor[atPos - 1].isWhitespace()) {
            hideMention()
            return
        }

        // Extract query after @
        val query = beforeCursor.substring(atPos + 1)

        // If space typed after @, close mention
        if (query.contains(" ")) {
            hideMention()
            return
        }

        // Show mention popup
        mentionStartPos = atPos
        mentionQuery = query
        isMentionActive = true

        if (agents.isNotEmpty()) {
            chatWidget.showMentionList(mentionItems, query)
        }
    }

    private fun hideMention() {
        isMentionActive = false
        mentionQuery = ""
        mentionStartPos = -1
        chatWidget.hideMentionList()
    }

    private fun insertMention(item: MentionItem) {
        val input = chatWidget.messageInput
        val text = input.text.toString() // toString() for safety

        // Replace @query with @mentionTag
        if (mentionStartPos >= 0) {
            val before = text.substring(0, mentionStartPos)
            val after = if (mentionStartPos + mentionQuery.length + 1 < text.length) {
                text.substring(mentionStartPos + mentionQuery.length + 1)
            } else ""

            val mentionTag = "@${item.mentionTag} "
            val newText = before + mentionTag + after

            input.setText(newText)
            input.setSelection(before.length + mentionTag.length)
        } else {
            // Fallback: append
            val mentionTag = "@${item.mentionTag} "
            input.append(mentionTag)
        }

        hideMention()
    }

    // ===== State observation =====

    private fun observeState() {
        // Messages
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
                        chatWidget.setToolbarTitle(getString(R.string.hermes_chat_title))
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

    // ===== Public API =====

    fun addAgent(agent: AgentInfo) {
        if (agents.none { it.id == agent.id }) {
            agents.add(agent)
            updateAgentParticipants()
        }
    }

    fun removeAgent(agentId: String) {
        agents.removeAll { it.id == agentId }
        updateAgentParticipants()
    }

    fun getAgents(): List<AgentInfo> = agents.toList()

    // ===== Helpers =====

    private fun HermesMessage.toChatMessageItem(): ChatMessageItem {
        val agent = viewModel.getAgent(this.agentId)
        return ChatMessageItem(
            id = this.id,
            content = this.content,
            senderId = this.agentId.ifEmpty { "hermes" },
            senderName = this.agentName.ifEmpty { agent?.name ?: getString(R.string.hermes_chat_title) },
            senderEmoji = this.agentIcon.ifEmpty { agent?.icon ?: "🤖" },
            timestamp = this.timestamp,
            isCurrentUser = this.role == "user",
            isRead = true
        )
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}