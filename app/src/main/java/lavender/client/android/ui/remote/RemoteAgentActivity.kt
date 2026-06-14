package lavender.client.android.ui.remote

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.models.AppLog
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.chat.widget.ChatMessageAdapter
import lavender.client.android.ui.chat.widget.ChatMessageItem
import lavender.client.android.ui.chat.widget.ChatWidget
import lavender.client.android.ui.widget.CommandBottomSheet

class RemoteAgentActivity : AppCompatActivity(),
    RemoteAgentManager.RemoteAgentStateListener {

    private lateinit var viewModel: RemoteAgentViewModel
    private lateinit var toolbar: MaterialToolbar
    private lateinit var statusIndicator: View
    private lateinit var statusText: TextView
    private lateinit var chatWidget: ChatWidget
    private lateinit var progressBar: ProgressBar
    private lateinit var taskTypeChipGroup: ChipGroup
    private lateinit var btnStartAgent: Button
    private lateinit var btnStopAgent: Button

    private lateinit var adapter: ChatMessageAdapter
    private var userId: String = ""
    private var selectedTaskType: String = "shell"
    private var serviceBound = false

    private lateinit var taskTypes: List<Pair<String, String>>

    private lateinit var agentCommands: List<CommandBottomSheet.CommandInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_agent)

        userId = SessionManager.session.value.userId
        RemoteAgentManager.init(applicationContext)
        ThemeUi.bind(this, userId)

        agentCommands = listOf(
            CommandBottomSheet.CommandInfo("/help", getString(R.string.cmd_help)),
            CommandBottomSheet.CommandInfo("/status", getString(R.string.cmd_status)),
            CommandBottomSheet.CommandInfo("/logs", getString(R.string.cmd_logs)),
            CommandBottomSheet.CommandInfo("/deploy", getString(R.string.cmd_deploy)),
            CommandBottomSheet.CommandInfo("/restart", getString(R.string.cmd_restart)),
            CommandBottomSheet.CommandInfo("/git pull", getString(R.string.cmd_git_pull)),
            CommandBottomSheet.CommandInfo("/git status", getString(R.string.cmd_git_status)),
            CommandBottomSheet.CommandInfo("/docker ps", getString(R.string.cmd_docker_ps)),
            CommandBottomSheet.CommandInfo("/docker logs", getString(R.string.cmd_docker_logs)),
            CommandBottomSheet.CommandInfo("/ps", getString(R.string.cmd_ps)),
            CommandBottomSheet.CommandInfo("/df", getString(R.string.cmd_df)),
            CommandBottomSheet.CommandInfo("/uptime", getString(R.string.cmd_uptime))
        )

        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory).get(RemoteAgentViewModel::class.java)

        initViews()
        taskTypes = listOf(
            "shell" to getString(R.string.task_shell),
            "git" to getString(R.string.task_git),
            "build" to getString(R.string.task_build),
            "deploy" to getString(R.string.task_deploy),
            "file" to getString(R.string.task_file),
            "docker" to getString(R.string.task_docker),
            "ai" to getString(R.string.task_ai)
        )
        setupToolbar()
        setupStatusBar()
        setupTaskTypeChips()
        setupChatWidget()
        observeState()

        // Load agents once on create
        viewModel.loadAgents()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        statusIndicator = findViewById(R.id.statusIndicator)
        statusText = findViewById(R.id.statusText)
        chatWidget = findViewById(R.id.chatWidget)
        progressBar = findViewById(R.id.progressBar)
        taskTypeChipGroup = findViewById(R.id.taskTypeChipGroup)
        btnStartAgent = findViewById(R.id.btnStartAgent)
        btnStopAgent = findViewById(R.id.btnStopAgent)
    }

    private fun setupToolbar() {
        toolbar.title = getString(R.string.remote_agent_title)
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
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
    }

    private fun setupStatusBar() {
        val theme = ThemeStore.currentTheme()
        val surfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
        val statusBar = findViewById<LinearLayout>(R.id.statusBar)
        statusBar.setBackgroundColor(surfaceColor)

        btnStartAgent.setOnClickListener {
            viewModel.loadAgents()
            Toast.makeText(this, R.string.agent_starting, Toast.LENGTH_SHORT).show()
        }
        btnStopAgent.setOnClickListener {
            Toast.makeText(this, R.string.agent_stopping, Toast.LENGTH_SHORT).show()
        }

        val isConnected = RemoteAgentManager.isConnected()
        updateStatus(isConnected)
        updateStartStopButtons(isConnected)
    }

    private fun setupTaskTypeChips() {
        val theme = ThemeStore.currentTheme()
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val surfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)

        taskTypeChipGroup.removeAllViews()
        taskTypeChipGroup.isSingleSelection = true

        taskTypes.forEachIndexed { _, (key, label) ->
            val chip = Chip(this).apply {
                text = label
                isCheckable = true
                isChecked = key == "shell"
                setTextColor(txtColor)
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(surfaceColor)
                chipStrokeColor = android.content.res.ColorStateList.valueOf(primColor)
                chipStrokeWidth = 2f
                id = View.generateViewId()
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedTaskType = key
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
        chatWidget.messageInput.hint = getString(R.string.send_task_hint)
        chatWidget.findViewById<View>(R.id.toolbar)?.visibility = View.GONE

        chatWidget.setOnSendMessageListener { text ->
            if (text.isNotBlank()) {
                viewModel.sendMessageStreaming(text.trim(), userId, selectedTaskType)
            }
        }

        chatWidget.commandButton.setOnClickListener {
            showAgentCommandMenu()
        }

        chatWidget.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = s?.toString()?.trim()?.isNotEmpty() == true
                chatWidget.sendButton.visibility = if (hasText) View.VISIBLE else View.GONE
                chatWidget.audioButton.visibility = if (hasText) View.GONE else View.VISIBLE
            }
        })

        chatWidget.sendButton.visibility = View.GONE
        chatWidget.audioButton.visibility = View.VISIBLE
    }

    private fun showAgentCommandMenu() {
        val sheet = CommandBottomSheet(
            context = this,
            commands = agentCommands,
            onCommandSelected = { cmd ->
                chatWidget.messageInput.setText(cmd.command + " ")
                chatWidget.messageInput.setSelection(cmd.command.length + 1)
            }
        )
        sheet.buildAndShow()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.messages.collect { msgs ->
                    val items = msgs.map { msg ->
                        val content = if (msg.isUser && msg.taskType.isNotEmpty()) {
                            "[${msg.taskType.uppercase()}] ${msg.content}"
                        } else msg.content
                        ChatMessageItem(
                            id = msg.id, content = content,
                            senderId = if (msg.isUser) userId else "remote_agent",
                            senderName = if (msg.isUser) getString(R.string.you) else getString(R.string.agent),
                            senderEmoji = if (msg.isUser) "" else "\uD83D\uDDC2",
                            timestamp = msg.timestamp, isCurrentUser = msg.isUser, isRead = true
                        )
                    }
                    adapter.submitList(items)
                    if (items.isNotEmpty()) {
                        chatWidget.messagesRecyclerView.scrollToPosition(items.size - 1)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.isTyping.collect { isTyping ->
                    val typingText = if (isTyping) getString(R.string.agent_executing) else ""
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
                    updateStartStopButtons(connected)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.error.collect { error ->
                    error?.let {
                        AppLog.error("RemoteAgentActivity", "Error: $it")
                        Toast.makeText(this@RemoteAgentActivity, it, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }

        // Observe agents for gateway info
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.agents.collect { agents ->
                    val tunnelActive = RemoteAgentManager.isTunnelActive()
                    val selectedAgent = viewModel.selectedAgent.value
                    val isConnected = viewModel.isConnected.value

                    if (tunnelActive) {
                        val settings = HermesGatewayManager(this@RemoteAgentActivity).loadSettings()
                        updateStatus(true, getString(R.string.agent_connected_via_gateway, settings.sshHost))
                    } else if (selectedAgent != null && isConnected) {
                        updateStatus(true, getString(R.string.agent_connected_token, selectedAgent.name))
                    } else if (agents.isNotEmpty()) {
                        updateStatus(false, getString(R.string.agent_disconnected_name, agents.first().name))
                    } else {
                        updateStatus(false, getString(R.string.agent_not_connected))
                    }
                }
            }
        }
    }

    private fun updateStatus(connected: Boolean, customText: String? = null) {
        val dotColor = if (connected) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()
        val dotDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(dotColor)
            setSize(
                (10 * resources.displayMetrics.density).toInt(),
                (10 * resources.displayMetrics.density).toInt()
            )
        }
        statusIndicator.background = dotDrawable
        statusText.text = customText ?: if (connected) getString(R.string.agent_connected) else getString(R.string.agent_disconnected)
        val theme = ThemeStore.currentTheme()
        statusText.setTextColor(ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE))
    }

    private fun updateStartStopButtons(connected: Boolean) {
        val hasAgent = RemoteAgentManager.isTunnelActive()
            || viewModel.selectedAgent.value != null
            || viewModel.agents.value.isNotEmpty()
        btnStartAgent.visibility = if (connected || !hasAgent) View.GONE else View.VISIBLE
        btnStopAgent.visibility = if (connected) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        RemoteAgentManager.bind(this)
        serviceBound = true
    }

    override fun onPause() {
        super.onPause()
        if (serviceBound) {
            RemoteAgentManager.unbind(this)
            serviceBound = false
        }
    }

    override fun onStateChanged(state: RemoteAgentManager.AgentConnectionState) {
        runOnUiThread {
            updateStatus(state.isConnected)
            updateStartStopButtons(state.isConnected)
        }
    }
}
