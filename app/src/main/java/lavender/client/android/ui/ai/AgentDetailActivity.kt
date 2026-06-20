package lavender.client.android.ui.ai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi

class AgentDetailActivity : AppCompatActivity() {

    private lateinit var viewModel: AgentDetailViewModel
    private lateinit var reviewAdapter: ReviewAdapter
    private var agentId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agent_detail)

        agentId = intent.getStringExtra("AGENT_ID") ?: ""
        val agentName = intent.getStringExtra("AGENT_NAME") ?: ""
        val agentDescription = intent.getStringExtra("AGENT_DESCRIPTION") ?: ""
        val agentModel = intent.getStringExtra("AGENT_MODEL") ?: ""
        val agentProvider = intent.getStringExtra("AGENT_PROVIDER") ?: ""

        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory).get(AgentDetailViewModel::class.java)

        setupToolbar(agentName)
        setupAgentInfo(agentName, agentDescription, agentModel, agentProvider)
        setupRecyclerView()
        setupButtons()
        observeState()

        ThemeUi.bind(this, SessionManager.session.value.username)

        viewModel.loadAgentDetails(agentId)
    }

    private fun setupToolbar(name: String) {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.title = name
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupAgentInfo(name: String, description: String, model: String, provider: String) {
        findViewById<TextView>(R.id.agentName).text = name
        findViewById<TextView>(R.id.agentDescription).text = description
        findViewById<TextView>(R.id.agentModel).text = model
        findViewById<TextView>(R.id.agentProvider).text = provider
    }

    private fun setupRecyclerView() {
        reviewAdapter = ReviewAdapter()
        val recyclerView = findViewById<RecyclerView>(R.id.reviewsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = reviewAdapter
    }

    private fun setupButtons() {
        findViewById<MaterialButton>(R.id.btnRate).setOnClickListener {
            RateAgentBottomSheet.show(this, agentId) { rating, review ->
                viewModel.rateAgent(agentId, rating, review)
            }
        }

        findViewById<MaterialButton>(R.id.btnShare).setOnClickListener {
            viewModel.shareAgent(agentId)
        }

        findViewById<MaterialButton>(R.id.btnInstall).setOnClickListener {
            InstallAgentBottomSheet.show(this) { shareCode ->
                viewModel.installAgent(shareCode)
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isLoading.collect { loading ->
                        findViewById<ProgressBar>(R.id.progressBar).visibility =
                            if (loading) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.stats.collect { stats ->
                        stats?.let {
                            findViewById<TextView>(R.id.installCount).text = "${it.installCount} installs"
                            findViewById<TextView>(R.id.reviewCount).text = "${it.reviewCount} reviews"
                        }
                    }
                }

                launch {
                    viewModel.avgRating.collect { rating ->
                        findViewById<RatingBar>(R.id.agentRating).rating = rating
                        findViewById<TextView>(R.id.ratingText).text = String.format("%.1f", rating)
                    }
                }

                launch {
                    viewModel.reviews.collect { reviews ->
                        reviewAdapter.submitList(reviews)
                    }
                }

                launch {
                    viewModel.shareCode.collect { code ->
                        code?.let {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Install agent in Lavender Messenger: $it")
                            }
                            startActivity(Intent.createChooser(shareIntent, "Share agent"))
                            viewModel.clearShareCode()
                        }
                    }
                }

                launch {
                    viewModel.installResult.collect { result ->
                        result?.let {
                            if (it) {
                                Toast.makeText(this@AgentDetailActivity, "Agent installed!", Toast.LENGTH_SHORT).show()
                            }
                            viewModel.clearInstallResult()
                        }
                    }
                }

                launch {
                    viewModel.rateResult.collect { result ->
                        result?.let {
                            Toast.makeText(this@AgentDetailActivity, "Thanks for rating!", Toast.LENGTH_SHORT).show()
                            viewModel.clearRateResult()
                        }
                    }
                }

                launch {
                    viewModel.error.collect { error ->
                        error?.let {
                            Toast.makeText(this@AgentDetailActivity, it, Toast.LENGTH_SHORT).show()
                            viewModel.clearError()
                        }
                    }
                }
            }
        }
    }
}
