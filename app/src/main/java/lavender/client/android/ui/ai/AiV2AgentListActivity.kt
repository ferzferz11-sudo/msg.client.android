package lavender.client.android.ui.ai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.ai.AiV2Agent
import lavender.client.android.data.ai.AiV2ChatUseCase
import lavender.client.android.data.session.SessionManager
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.remote.RemoteAgentSettingsActivity
import lavender.client.android.ui.remote.RemoteAgentSettingsFragment

class AiV2AgentListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AiV2AgentListAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tabLayout: TabLayout
    private lateinit var searchLayout: com.google.android.material.textfield.TextInputLayout
    private lateinit var searchInput: com.google.android.material.textfield.TextInputEditText
    private lateinit var sortFilterBar: View
    private lateinit var marketplaceViewModel: MarketplaceViewModel
    private lateinit var remoteAgentContainer: View
    private lateinit var usageStatsContainer: View

    private var allAgents = listOf<AiV2Agent>()
    private var currentTab = 0
    private var showingRemoteSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_v2_agent_list)

        marketplaceViewModel = ViewModelProvider(this)[MarketplaceViewModel::class.java]

        initViews()
        setupToolbar()
        setupTabs()
        setupRecyclerView()
        setupSwipeRefresh()
        setupFab()
        setupSearch()
        setupFilters()
        observeMarketplace()
        ThemeUi.bind(this, SessionManager.session.value.username)

        loadAgents()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.agentsRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        tabLayout = findViewById(R.id.tabLayout)
        searchLayout = findViewById(R.id.searchLayout)
        searchInput = findViewById(R.id.searchInput)
        sortFilterBar = findViewById(R.id.sortFilterBar)
        remoteAgentContainer = findViewById(R.id.remoteAgentContainer)
        usageStatsContainer = findViewById(R.id.usageStatsContainer)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.title = getString(R.string.ai_v2_agents)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                onTabChanged()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun onTabChanged() {
        usageStatsContainer.visibility = View.GONE
        remoteAgentContainer.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        swipeRefresh.visibility = View.VISIBLE
        showingRemoteSettings = false

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.ai_v2_agents)
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.setNavigationOnClickListener { finish() }
        tabLayout.visibility = View.VISIBLE

        when (currentTab) {
            0 -> {
                searchLayout.visibility = View.GONE
                sortFilterBar.visibility = View.GONE
                showPresets()
            }
            1 -> {
                searchLayout.visibility = View.GONE
                sortFilterBar.visibility = View.GONE
                showMyAgents()
            }
            2 -> {
                searchLayout.visibility = View.VISIBLE
                sortFilterBar.visibility = View.VISIBLE
                showMarketplace()
            }
            3 -> {
                searchLayout.visibility = View.GONE
                sortFilterBar.visibility = View.GONE
                showRemoteAgent()
            }
            4 -> {
                searchLayout.visibility = View.GONE
                sortFilterBar.visibility = View.GONE
                showUsageStats()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = AiV2AgentListAdapter(
            onItemClick = { agent -> onAgentClick(agent) },
            onDeleteClick = { agent -> confirmDelete(agent) },
            onItemLongClick = { agent -> onAgentLongClick(agent) },
            onAddClick = { agent -> clonePresetAgent(agent) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            when (currentTab) {
                0, 1 -> loadAgents()
                2 -> marketplaceViewModel.loadAgents()
            }
        }
    }

    private fun setupFab() {
        findViewById<View>(R.id.fab).setOnClickListener {
            startActivity(Intent(this, AiAgentSetupActivity::class.java))
        }
    }

    private fun setupSearch() {
        var searchJob: kotlinx.coroutines.Job? = null
        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(300)
                    val query = s?.toString()?.trim() ?: ""
                    if (query.length >= 2 || query.isEmpty()) {
                        marketplaceViewModel.loadAgents(query)
                    }
                }
            }
        })
    }

    private fun setupFilters() {
        findViewById<com.google.android.material.chip.Chip>(R.id.chipFilterFavorites).setOnCheckedChangeListener { _, _ ->
            marketplaceViewModel.toggleFavoritesOnly()
        }
    }

    private fun observeMarketplace() {
        lifecycleScope.launch {
            marketplaceViewModel.agents.collect { agents ->
                if (currentTab == 2) {
                    adapter.submitList(agents.map { mp ->
                        AiV2Agent(
                            id = mp.id,
                            name = mp.name,
                            description = mp.description,
                            providerType = mp.providerType,
                            model = mp.model,
                            toolsEnabled = mp.toolsEnabled,
                            ragEnabled = mp.ragEnabled,
                            isPreset = mp.isPreset,
                            isPublic = mp.isPublic
                        )
                    }, showFavorites = true)
                }
            }
        }
        lifecycleScope.launch {
            marketplaceViewModel.isLoading.collect { loading ->
                progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            }
        }
        lifecycleScope.launch {
            swipeRefresh.isRefreshing = false
        }
    }

    private fun loadAgents() {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            swipeRefresh.isRefreshing = true
            try {
                allAgents = AiV2ChatUseCase.listAgents(includePublic = true)
                when (currentTab) {
                    0 -> showPresets()
                    1 -> showMyAgents()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AiV2AgentListActivity, e.message ?: "Failed to load agents", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun showPresets() {
        val presets = allAgents.filter { it.isPreset }
        adapter.submitList(presets)
    }

    private fun showMyAgents() {
        val myAgents = allAgents.filter { !it.isPreset }
        adapter.submitList(myAgents)
    }

    private fun showMarketplace() {
        marketplaceViewModel.loadAgents()
    }

    private fun showRemoteAgent() {
        adapter.submitList(emptyList())
        lifecycleScope.launch {
            try {
                val agents = GrpcClient.listRemoteAgents()
                val agentItems = agents.map { agent ->
                    AiV2Agent(
                        id = agent.id,
                        name = agent.name,
                        description = "Host: ${agent.host} | Status: ${agent.status}",
                        providerType = lavender.client.android.data.ai.AiProviderType.LOCAL,
                        model = agent.os,
                        toolsEnabled = agent.capabilities.isNotEmpty(),
                        isPreset = false,
                        isPublic = false
                    )
                }
                adapter.submitList(agentItems)
                if (agentItems.isEmpty()) {
                    Toast.makeText(this@AiV2AgentListActivity, "No remote agents connected", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@AiV2AgentListActivity, "Failed to load remote agents", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUsageStats() {
        recyclerView.visibility = View.GONE
        swipeRefresh.visibility = View.GONE
        usageStatsContainer.visibility = View.VISIBLE

        if (supportFragmentManager.findFragmentById(R.id.usageStatsContainer) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.usageStatsContainer, UsageStatsFragment())
                .commit()
        }
    }

    private fun hideUsageStats() {
        supportFragmentManager.fragments.filterIsInstance<UsageStatsFragment>().forEach { f ->
            supportFragmentManager.beginTransaction().remove(f).commit()
        }
        usageStatsContainer.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        swipeRefresh.visibility = View.VISIBLE
    }

    private fun onAgentClick(agent: AiV2Agent) {
        if (currentTab == 0 && agent.isPreset) {
            clonePresetAgent(agent)
        } else {
            openAgentSetup(agent)
        }
    }

    private fun onAgentLongClick(agent: AiV2Agent) {
        if (currentTab == 0 && agent.isPreset) {
            openAgentSetup(agent)
        }
    }

    private fun clonePresetAgent(agent: AiV2Agent) {
        lifecycleScope.launch {
            val result = AiV2ChatUseCase.cloneAgent(agent.id, agent.name)
            result.fold(
                onSuccess = {
                    Toast.makeText(
                        this@AiV2AgentListActivity,
                        getString(R.string.ai_preset_added, agent.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    loadAgents()
                },
                onFailure = { e ->
                    Toast.makeText(
                        this@AiV2AgentListActivity,
                        e.message ?: "Failed to add agent",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    private fun openAgentSetup(agent: AiV2Agent) {
        if (currentTab == 3) {
            showRemoteAgentSettings(agent)
        } else {
            val intent = Intent(this, AiAgentSetupActivity::class.java)
            intent.putExtra("AGENT_ID", agent.id)
            startActivity(intent)
        }
    }

    private fun showRemoteAgentSettings(agent: AiV2Agent) {
        showingRemoteSettings = true
        recyclerView.visibility = View.GONE
        swipeRefresh.visibility = View.GONE
        remoteAgentContainer.visibility = View.VISIBLE

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = agent.name
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.setNavigationOnClickListener { hideRemoteAgentSettings() }

        tabLayout.visibility = View.GONE

        val fragment = RemoteAgentSettingsFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.remoteAgentContainer, fragment)
            .commit()
    }

    private fun hideRemoteAgentSettings() {
        showingRemoteSettings = false
        supportFragmentManager.fragments.forEach { f ->
            supportFragmentManager.beginTransaction().remove(f).commit()
        }
        remoteAgentContainer.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
        swipeRefresh.visibility = View.VISIBLE
        tabLayout.visibility = View.VISIBLE

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.ai_v2_agents)
        toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        toolbar.setNavigationOnClickListener { finish() }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (showingRemoteSettings) {
            hideRemoteAgentSettings()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun confirmDelete(agent: AiV2Agent) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.ai_v2_delete_agent))
            .setMessage("Delete agent '${agent.name}'?")
            .setPositiveButton("Delete") { _, _ -> deleteAgent(agent) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAgent(agent: AiV2Agent) {
        lifecycleScope.launch {
            val result = AiV2ChatUseCase.deleteAgent(agent.id)
            result.fold(
                onSuccess = {
                    Toast.makeText(this@AiV2AgentListActivity, "Agent deleted", Toast.LENGTH_SHORT).show()
                    loadAgents()
                },
                onFailure = { e ->
                    Toast.makeText(this@AiV2AgentListActivity, e.message ?: "Failed to delete", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
