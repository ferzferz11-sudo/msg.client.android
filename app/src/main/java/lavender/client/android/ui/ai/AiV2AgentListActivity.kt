package lavender.client.android.ui.ai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.ai.AiV2Agent
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi

/**
 * AiV2AgentListActivity — list of AI v2 agents.
 * Tabs: Presets, My Agents, Public.
 */
class AiV2AgentListActivity : AppCompatActivity() {

    private lateinit var viewModel: AiV2AgentListViewModel
    private lateinit var adapter: AiV2AgentListAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tabLayout: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_v2_agent_list)

        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory).get(AiV2AgentListViewModel::class.java)

        recyclerView = findViewById(R.id.agentsRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        tabLayout = findViewById(R.id.tabLayout)

        setupToolbar()
        setupTabs()
        setupRecyclerView()
        setupFab()
        observeState()

        ThemeUi.bind(this, SessionManager.session.value.username)

        viewModel.loadAgents(0)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.position?.let { viewModel.loadAgents(it) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        adapter = AiV2AgentListAdapter(
            onItemClick = { agent -> openAgentChat(agent) },
            onDeleteClick = { agent -> confirmDelete(agent) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupFab() {
        findViewById<View>(R.id.fab).setOnClickListener {
            val intent = Intent(this, AiV2AgentCreateEditActivity::class.java)
            startActivity(intent)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.agents.collect { agents ->
                        adapter.submitList(agents)
                    }
                }

                launch {
                    viewModel.isLoading.collect { loading ->
                        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.error.collect { error ->
                        error?.let {
                            Toast.makeText(this@AiV2AgentListActivity, it, Toast.LENGTH_SHORT).show()
                            viewModel.clearError()
                        }
                    }
                }
            }
        }
    }

    private fun openAgentChat(agent: AiV2Agent) {
        val intent = Intent(this, AiV2ChatActivity::class.java).apply {
            putExtra("AGENT_ID", agent.id)
            putExtra("AGENT_NAME", agent.name)
        }
        startActivity(intent)
    }

    private fun confirmDelete(agent: AiV2Agent) {
        AlertDialog.Builder(this)
            .setTitle(R.string.ai_v2_delete_agent)
            .setMessage("Delete agent '${agent.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteAgent(agent.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
