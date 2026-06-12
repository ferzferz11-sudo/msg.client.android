package lavender.client.android.ui.hermes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.models.AgentPreset
import lavender.client.android.data.models.AgentInfo
import lavender.client.android.data.session.SessionManager

class AgentListActivity : AppCompatActivity() {

    private lateinit var viewModel: AgentListViewModel
    private lateinit var adapter: AgentListAdapter
    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var fab: FloatingActionButton
    private lateinit var defaultsContainer: FrameLayout

    private var userId: String = ""
    private var currentTab = 0 // 0 = presets, 1 = my agents, 2 = defaults, 3 = remote

    private val availableModels = arrayOf(
        "google/gemini-pro",
        "openai/gpt-4o",
        "anthropic/claude-3-haiku",
        "mistralai/mistral-large"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_list)

        userId = SessionManager.session.value.username
        viewModel = ViewModelProvider(this)[AgentListViewModel::class.java]

        setupToolbar()
        setupViews()
        observeState()
        viewModel.loadAgents()
        lavender.client.android.theme.ui.ThemeUi.bind(this, userId)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupViews() {
        tabLayout = findViewById(R.id.tabLayout)
        recyclerView = findViewById(R.id.agentsRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        fab = findViewById(R.id.fab)
        defaultsContainer = findViewById(R.id.defaultsContainer)

        adapter = AgentListAdapter(
            onAgentClick = { agent -> openAgentChat(agent) },
            onDeleteClick = { agent -> confirmDeleteAgent(agent) },
            showDeleteButton = false
        )
        adapter.onAgentLongClick = { agent ->
            showAgentSettingsSheet(agent)
        }
        adapter.onModelClick = { agent ->
            showModelSelectionDialog(agent)
        }
        adapter.onModelLongClick = { agent ->
            openChatWithModelMention(agent)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                refreshTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        fab.setOnClickListener {
            when (currentTab) {
                1 -> {
                    val intent = Intent(this, AgentSettingsActivity::class.java)
                    intent.putExtra("USER_ID", userId)
                    intent.putExtra("MODE", "create")
                    startActivity(intent)
                }
                3 -> {
                    // Open Remote Agent settings (token management)
                    startActivity(Intent(this, lavender.client.android.ui.remote.RemoteAgentSettingsActivity::class.java))
                }
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.presets.collect { presets ->
                    if (currentTab == 0) {
                        adapter.setItems(presets.map { AgentListItem.PresetItem(it) }, showDelete = false)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.customAgents.collect { agents ->
                    if (currentTab == 1) {
                        adapter.setItems(agents.map { AgentListItem.AgentItem(it) }, showDelete = true)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoading.collect { isLoading ->
                    progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.error.collect { error ->
                    error?.let {
                        Toast.makeText(this@AgentListActivity, it, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    private fun refreshTab() {
        when (currentTab) {
            0 -> {
                recyclerView.visibility = View.VISIBLE
                defaultsContainer.visibility = View.GONE
                adapter.setItems(viewModel.presets.value.map { AgentListItem.PresetItem(it) }, showDelete = false)
                fab.hide()
            }
            1 -> {
                recyclerView.visibility = View.VISIBLE
                defaultsContainer.visibility = View.GONE
                viewModel.loadUserAgents(userId)
                adapter.setItems(viewModel.customAgents.value.map { AgentListItem.AgentItem(it) }, showDelete = true)
                fab.show()
            }
            2 -> {
                recyclerView.visibility = View.GONE
                defaultsContainer.visibility = View.VISIBLE
                fab.hide()
                setupDefaultsTab()
            }
            3 -> {
                // Remote agents tab — open RemoteAgentActivity
                recyclerView.visibility = View.VISIBLE
                defaultsContainer.visibility = View.GONE
                fab.show()
                loadRemoteAgents()
            }
        }
    }

    private fun loadRemoteAgents() {
        startActivity(Intent(this, lavender.client.android.ui.remote.RemoteAgentActivity::class.java))
        // Reset to first tab after opening
        tabLayout.getTabAt(0)?.select()
    }

    private fun setupDefaultsTab() {
        defaultsContainer.removeAllViews()
        val view = layoutInflater.inflate(R.layout.fragment_agent_defaults, defaultsContainer, true)
        val input = view.findViewById<TextInputEditText>(R.id.defaultModelInput)
        val saveButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.saveDefaultsButton)

        val prefs = getSharedPreferences("hermes_prefs", Context.MODE_PRIVATE)
        input.setText(prefs.getString("default_model", "openai/gpt-oss-120b:free"))

        saveButton.setOnClickListener {
            val newModel = input.text.toString()
            prefs.edit().putString("default_model", newModel).apply()
            Toast.makeText(this, "Default model saved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAgentChat(agent: Any) {
        // Open HermesChatActivity with agent pre-selected
        val intent = Intent(this, HermesChatActivity::class.java)
        // Pass agent info as needed
        startActivity(intent)
    }

    private fun confirmDeleteAgent(agent: Any) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Удалить агента?")
            .setMessage("Это действие необратимо.")
            .setPositiveButton("Удалить") { _, _ ->
                // viewModel.deleteAgent(agent.id, userId) { success -> ... }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showAgentSettingsSheet(agent: AgentInfo) {
        val sheet = AgentSettingsBottomSheet(
            context = this,
            agent = agent,
            userId = userId,
            onSaved = {
                viewModel.loadUserAgents(userId)
            }
        )
        sheet.show()
    }

    private fun showModelSelectionDialog(agent: AgentInfo) {
        val currentModelIndex = availableModels.indexOf(agent.model)
        MaterialAlertDialogBuilder(this)
            .setTitle("Select Model")
            .setSingleChoiceItems(availableModels, currentModelIndex) { dialog, which ->
                val selectedModel = availableModels[which]
                viewModel.updateAgentModel(agent.id, userId, selectedModel)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openChatWithModelMention(agent: AgentInfo) {
        val intent = Intent(this, HermesChatActivity::class.java)
        intent.putExtra("PREFILL_MESSAGE", "Расскажи подробнее о модели ${agent.model}")
        startActivity(intent)
    }
}