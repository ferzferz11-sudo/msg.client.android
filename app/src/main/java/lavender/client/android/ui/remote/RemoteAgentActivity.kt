package lavender.client.android.ui.remote

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
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

class RemoteAgentActivity : AppCompatActivity(),
    RemoteAgentManager.RemoteAgentStateListener {

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
    private var serviceBound = false

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

        // Инициализация RemoteAgentManager (единоразово, идемпотентна)
        RemoteAgentManager.init(applicationContext)

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
        toolbar.title = ""
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.navigationIcon?.setTint(
            ThemeUtils.parseSafeColor(ThemeStore.currentTheme().textPrimaryColor, Color.WHITE)
        )
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

        // Agent spinner in toolbar
        setupAgentSpinner()

        // Status bar
        updateStatus(false)

        // Task type chips
        setupTaskTypeChips()

        // Observe agents for spinner and connection status
        observeAgents()

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

        // Auto-refresh agent status every 30 seconds
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
                while (true) {
                    kotlinx.coroutines.delay(30000)
                    viewModel.refreshAgentStatus()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Привязываемся к сервису
        RemoteAgentManager.bind(this)
        serviceBound = true
        viewModel.refreshAgentStatus()
    }

    override fun onPause() {
        super.onPause()
        // Отвязываемся от сервиса (сервис продолжает работать)
        if (serviceBound) {
            RemoteAgentManager.unbind(this)
            serviceBound = false
        }
    }

    // ===== RemoteAgentStateListener =====

    override fun onStateChanged(state: RemoteAgentManager.AgentConnectionState) {
        runOnUiThread {
            updateStatus(state.isConnected)
        }
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
        chatWidget.findViewById<View>(R.id.toolbar)?.visibility = View.GONE

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

    private var agentSpinner: Spinner? = null

    private fun setupAgentSpinner() {
        val theme = ThemeStore.currentTheme()
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val surfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)

        agentSpinner = Spinner(this, Spinner.MODE_DROPDOWN).apply {
            setBackgroundColor(surfaceColor)
            setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(surfaceColor))
        }
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, mutableListOf()) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val v = super.getView(position, convertView, parent)
                (v as? TextView)?.setTextColor(txtColor)
                return v
            }
            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val v = super.getDropDownView(position, convertView, parent)
                (v as? TextView)?.setTextColor(txtColor)
                (v as? TextView)?.setBackgroundColor(surfaceColor)
                return v
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        agentSpinner?.adapter = adapter

        agentSpinner?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val agents = viewModel.agents.value
                if (position < agents.size) {
                    viewModel.selectAgent(agents[position])
                    updateStatus(agents[position].status == "connected")
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val params = androidx.appcompat.widget.Toolbar.LayoutParams(
            androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT,
            androidx.appcompat.widget.Toolbar.LayoutParams.WRAP_CONTENT
        )
        toolbar.addView(agentSpinner, params)
    }

    private fun observeAgents() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.agents.collect { agents ->
                    @Suppress("UNCHECKED_CAST")
                    val spinnerAdapter = agentSpinner?.adapter as? ArrayAdapter<String>
                    spinnerAdapter?.clear()
                    agents.forEach { spinnerAdapter?.add(it.name) }
                    spinnerAdapter?.notifyDataSetChanged()
                    
                    // Update connection status based on selected agent
                    val selectedAgent = viewModel.selectedAgent.value
                    if (selectedAgent != null) {
                        val agent = agents.find { it.id == selectedAgent.id }
                        val isConnected = agent?.status == "connected"
                        updateStatus(isConnected)
                    } else if (agents.isNotEmpty()) {
                        // Auto-select first agent if none selected
                        viewModel.selectAgent(agents.first())
                        updateStatus(agents.first().status == "connected")
                    } else {
                        updateStatus(false)
                    }
                }
            }
        }
    }

    private fun updateStatus(connected: Boolean) {
        val dotColor = if (connected) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
        statusIndicator.background.setTint(dotColor)
        val agent = viewModel.selectedAgent.value
        val statusStr = if (connected) {
            if (agent != null) "${agent.name} • подключён" else "Агент подключён"
        } else {
            if (agent != null) "${agent.name} • отключён" else "Агент отключён"
        }
        statusText.text = statusStr
        val txtColor = ThemeUtils.parseSafeColor(ThemeStore.currentTheme().textSecondaryColor, Color.GRAY)
        statusText.setTextColor(txtColor)
    }
}
