package lavender.client.android.ui.ai

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.ai.AiV2Agent
import lavender.client.android.data.ai.MarketplaceAgent
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi

class AiV2AgentListActivity : AppCompatActivity() {

    private lateinit var viewModel: AiV2AgentListViewModel
    private lateinit var marketplaceViewModel: MarketplaceViewModel
    private lateinit var usageStatsViewModel: UsageStatsViewModel
    private lateinit var agentAdapter: AiV2AgentListAdapter
    private lateinit var marketplaceAdapter: MarketplaceAgentAdapter
    private lateinit var usageStatsAdapter: UsageStatsAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var searchLayout: TextInputLayout
    private lateinit var searchInput: TextInputEditText
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var sortFilterBar: View
    private lateinit var sortSpinner: Spinner
    private lateinit var filterChipGroup: ChipGroup
    private var currentTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_v2_agent_list)

        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory).get(AiV2AgentListViewModel::class.java)
        marketplaceViewModel = ViewModelProvider(this, factory).get(MarketplaceViewModel::class.java)
        usageStatsViewModel = ViewModelProvider(this, factory).get(UsageStatsViewModel::class.java)

        recyclerView = findViewById(R.id.agentsRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        tabLayout = findViewById(R.id.tabLayout)
        searchLayout = findViewById(R.id.searchLayout)
        searchInput = findViewById(R.id.searchInput)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        sortFilterBar = findViewById(R.id.sortFilterBar)
        sortSpinner = findViewById(R.id.sortSpinner)
        filterChipGroup = findViewById(R.id.filterChipGroup)

        emptyView = TextView(this).apply {
            text = getString(R.string.marketplace_empty)
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
            visibility = View.GONE
        }

        setupToolbar()
        setupTabs()
        setupRecyclerView()
        setupSearch()
        setupSortFilter()
        setupSwipeRefresh()
        setupFab()
        observeState()

        viewModel.loadPresets()

        ThemeUi.bind(this, SessionManager.session.value.username)

        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        intent?.data?.let { uri ->
            if (uri.host == "marketplace" && uri.pathSegments.firstOrNull() == "install") {
                val code = uri.getQueryParameter("code") ?: return
                InstallAgentBottomSheet.show(this) { shareCode ->
                    marketplaceViewModel.loadAgents()
                }
            }
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.title = getString(R.string.ai_v2_agents)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.position?.let { position ->
                    currentTab = position
                    when (position) {
                        0 -> {
                            switchToPresets()
                        }
                        1 -> {
                            switchToMyAgents()
                        }
                        2 -> {
                            switchToDiscover()
                        }
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun switchToPresets() {
        recyclerView.adapter = agentAdapter
        searchLayout.visibility = View.GONE
        sortFilterBar.visibility = View.GONE
        viewModel.loadPresets()
    }

    private fun switchToMyAgents() {
        recyclerView.adapter = agentAdapter
        searchLayout.visibility = View.GONE
        sortFilterBar.visibility = View.GONE
        viewModel.loadMyAgents()
    }

    private fun switchToDiscover() {
        recyclerView.adapter = marketplaceAdapter
        searchLayout.visibility = View.VISIBLE
        sortFilterBar.visibility = View.VISIBLE
        searchInput.text?.clear()
        marketplaceViewModel.loadAgents()
    }

    private fun setupRecyclerView() {
        agentAdapter = AiV2AgentListAdapter(
            onItemClick = { agent -> openAgentChat(agent) },
            onDeleteClick = { agent -> confirmDelete(agent) }
        )
        marketplaceAdapter = MarketplaceAgentAdapter { agent ->
            openMarketplaceAgentDetail(agent)
        }
        usageStatsAdapter = UsageStatsAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = agentAdapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (currentTab == 2) {
                    val layoutManager = rv.layoutManager as LinearLayoutManager
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    val totalItems = layoutManager.itemCount
                    if (lastVisible >= totalItems - 5 && !marketplaceViewModel.isLoadingMore.value) {
                        marketplaceViewModel.loadMore()
                    }
                }
            }
        })
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                if (query.length >= 2 || query.isEmpty()) {
                    marketplaceViewModel.loadAgents(query)
                }
            }
        })
    }

    private fun setupSortFilter() {
        val sortOptions = SortOption.entries.map { it.label }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sortSpinner.adapter = spinnerAdapter

        sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                marketplaceViewModel.setSortOption(SortOption.entries[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        filterChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            var providerFilter: String? = null
            var toolsFilter: Boolean? = null

            for (chipId in checkedIds) {
                when (chipId) {
                    R.id.chipFilterTools -> toolsFilter = true
                    R.id.chipFilterOpenRouter -> providerFilter = "openrouter"
                    R.id.chipFilterMimo -> providerFilter = "mimo"
                    R.id.chipFilterLocal -> providerFilter = "local"
                }
            }

            marketplaceViewModel.setFilterProvider(providerFilter)
            marketplaceViewModel.setFilterToolsEnabled(toolsFilter)
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            when (currentTab) {
                2 -> marketplaceViewModel.loadAgents(searchInput.text?.toString() ?: "")
                0 -> viewModel.loadPresets()
                1 -> viewModel.loadMyAgents()
            }
        }
    }

    private fun setupFab() {
        findViewById<View>(R.id.fab).setOnClickListener {
            when (currentTab) {
                2 -> InstallAgentBottomSheet.show(this) { shareCode ->
                    marketplaceViewModel.loadAgents()
                }
                else -> {
                    val intent = Intent(this, AiV2AgentCreateEditActivity::class.java)
                    startActivity(intent)
                }
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.agents.collect { agents ->
                        if (currentTab in 0..1) {
                            agentAdapter.submitList(agents)
                        }
                    }
                }

                launch {
                    marketplaceViewModel.agents.collect { agents ->
                        if (currentTab == 2) {
                            marketplaceAdapter.submitList(agents)
                            if (agents.isEmpty() && !marketplaceViewModel.isLoading.value) {
                                recyclerView.visibility = View.GONE
                                emptyView.visibility = View.VISIBLE
                            } else {
                                recyclerView.visibility = View.VISIBLE
                                emptyView.visibility = View.GONE
                            }
                        }
                    }
                }

                launch {
                    viewModel.isLoading.collect { loading ->
                        if (currentTab in 0..1) {
                            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
                        }
                    }
                }

                launch {
                    marketplaceViewModel.isLoading.collect { loading ->
                        if (currentTab == 2) {
                            if (loading && marketplaceViewModel.agents.value.isEmpty()) {
                                marketplaceAdapter.showSkeleton()
                                recyclerView.visibility = View.VISIBLE
                                emptyView.visibility = View.GONE
                            }
                            progressBar.visibility = if (loading && marketplaceViewModel.agents.value.isNotEmpty()) View.VISIBLE else View.GONE
                            swipeRefresh.isRefreshing = loading
                        }
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

                launch {
                    marketplaceViewModel.error.collect { error ->
                        error?.let {
                            Toast.makeText(this@AiV2AgentListActivity, it, Toast.LENGTH_SHORT).show()
                            marketplaceViewModel.clearError()
                        }
                    }
                }
            }
        }
    }

    private fun formatNumber(n: Int): String {
        return when {
            n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
            n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
            else -> n.toString()
        }
    }

    private fun openAgentChat(agent: AiV2Agent) {
        val intent = Intent(this, AiV2ChatActivity::class.java).apply {
            putExtra("AGENT_ID", agent.id)
            putExtra("AGENT_NAME", agent.name)
        }
        startActivity(intent)
    }

    private fun openMarketplaceAgentDetail(agent: MarketplaceAgent) {
        val intent = Intent(this, AgentDetailActivity::class.java).apply {
            putExtra("AGENT_ID", agent.id)
            putExtra("AGENT_NAME", agent.name)
            putExtra("AGENT_DESCRIPTION", agent.description)
            putExtra("AGENT_MODEL", agent.model)
            putExtra("AGENT_PROVIDER", agent.providerType.value)
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
