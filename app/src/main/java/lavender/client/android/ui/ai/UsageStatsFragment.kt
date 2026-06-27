package lavender.client.android.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import lavender.client.android.R

class UsageStatsFragment : Fragment() {

    private lateinit var viewModel: UsageStatsViewModel
    private lateinit var adapter: UsageStatsAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var totalTokensValue: TextView
    private lateinit var totalRequestsValue: TextView
    private lateinit var avgTokensValue: TextView

    private val autoRefreshIntervalMs = 30_000L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_usage_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[UsageStatsViewModel::class.java]

        progressBar = view.findViewById(R.id.progressBar)
        recyclerView = view.findViewById(R.id.recyclerView)
        emptyView = view.findViewById(R.id.emptyView)
        totalTokensValue = view.findViewById(R.id.totalTokensValue)
        totalRequestsValue = view.findViewById(R.id.totalRequestsValue)
        avgTokensValue = view.findViewById(R.id.avgTokensValue)

        adapter = UsageStatsAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        observeStats()
        viewModel.loadStats()
        startAutoRefresh()
    }

    private fun observeStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stats.collect { stats ->
                adapter.submitList(stats)
                emptyView.visibility = if (stats.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (stats.isEmpty()) View.GONE else View.VISIBLE
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.totalTokens.collect { tokens ->
                totalTokensValue.text = formatNumber(tokens)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.totalRequests.collect { requests ->
                totalRequestsValue.text = requests.toString()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { loading ->
                progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stats.collect { stats ->
                val totalTokens = stats.sumOf { it.totalTokens }
                val totalRequests = stats.sumOf { it.requestCount }
                val avg = if (totalRequests > 0) totalTokens / totalRequests else 0
                avgTokensValue.text = formatNumber(avg)
            }
        }
    }

    private fun startAutoRefresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(autoRefreshIntervalMs)
                if (isResumed) {
                    viewModel.loadStats()
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
}
