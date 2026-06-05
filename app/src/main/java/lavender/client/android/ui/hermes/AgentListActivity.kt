package lavender.client.android.ui.hermes

import android.content.Intent
import android.os.Bundle
import android.view.View
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

    private var userId: String = ""
    private var currentTab = 0 // 0 = presets, 1 = my agents

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

        adapter = AgentListAdapter(
            onAgentClick = { agent -> openAgentChat(agent) },
            onDeleteClick = { agent -> confirmDeleteAgent(agent) },
            showDeleteButton = false
        )
        adapter.onAgentLongClick = { agent ->
            showAgentSettingsSheet(agent)
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
            if (currentTab == 1) {
                val intent = Intent(this, AgentSettingsActivity::class.java)
                intent.putExtra("USER_ID", userId)
                intent.putExtra("MODE", "create")
                startActivity(intent)
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
                adapter.setItems(viewModel.presets.value.map { AgentListItem.PresetItem(it) }, showDelete = false)
                fab.hide()
            }
            1 -> {
                viewModel.loadUserAgents(userId)
                adapter.setItems(viewModel.customAgents.value.map { AgentListItem.AgentItem(it) }, showDelete = true)
                fab.show()
            }
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
}
