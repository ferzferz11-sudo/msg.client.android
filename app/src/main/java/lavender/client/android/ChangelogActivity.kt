package lavender.client.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import lavender.client.android.data.changelog.ChangelogRepository
import lavender.client.android.ui.adapter.ChangelogAdapter
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.theme.ui.ThemeUi
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class ChangelogActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChangelogActivity"

        // GitHub CHANGELOG.md links (full technical changelog)
        private const val GITHUB_SERVER_CHANGELOG =
            "https://github.com/ferzferz11-sudo/msg/blob/feat/1.1.2.x/CHANGELOG.md"
        private const val GITHUB_CLIENT_CHANGELOG =
            "https://github.com/ferzferz11-sudo/msg.client.android/blob/feat/1.1.2.x/CHANGELOG.md"

        fun createIntent(context: Context): Intent {
            return Intent(context, ChangelogActivity::class.java)
        }
    }

    private lateinit var splashView: FrameLayout
    private lateinit var splashLogo: ImageView
    private lateinit var splashAppName: TextView
    private lateinit var contentView: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var rvReleases: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var fallbackView: ScrollView
    private lateinit var tvFallback: TextView
    private lateinit var tvCacheIndicator: TextView
    private lateinit var btnServerChangelog: MaterialButton
    private lateinit var btnClientChangelog: MaterialButton
    private lateinit var adapter: ChangelogAdapter

    // Track if network fetch completed (success or failure)
    private var networkCompleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply theme synchronously BEFORE setContentView to avoid white flash
        val theme = ThemeStore.currentTheme()
        ThemeApplier.apply(this, theme)

        // Also bind for future theme updates
        ThemeUi.bind(this, "")

        setContentView(R.layout.activity_changelog)

        splashView = findViewById(R.id.splashView)
        splashLogo = findViewById(R.id.splashLogo)
        splashAppName = findViewById(R.id.splashAppName)
        contentView = findViewById(R.id.contentView)
        toolbar = findViewById(R.id.toolbar)
        rvReleases = findViewById(R.id.rvReleases)
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)
        fallbackView = findViewById(R.id.fallbackView)
        tvFallback = findViewById(R.id.tvFallback)
        tvCacheIndicator = findViewById(R.id.tvCacheIndicator)
        btnServerChangelog = findViewById(R.id.btnServerChangelog)
        btnClientChangelog = findViewById(R.id.btnClientChangelog)

        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        toolbar.setTitle(R.string.changelog_title)
        toolbar.setNavigationOnClickListener { finish() }

        // Setup RecyclerView
        adapter = ChangelogAdapter { downloadUrl ->
            openUrl(downloadUrl)
        }
        rvReleases.layoutManager = LinearLayoutManager(this)
        rvReleases.adapter = adapter

        // Setup GitHub changelog buttons
        btnServerChangelog.setOnClickListener {
            openUrl(GITHUB_SERVER_CHANGELOG)
        }
        btnClientChangelog.setOnClickListener {
            openUrl(GITHUB_CLIENT_CHANGELOG)
        }

        // Try to fetch from GitHub API
        fetchFromNetwork()
    }

    /**
     * Fetch from GitHub API (releases).
     */
    private fun fetchFromNetwork() {
        lifecycleScope.launch {
            try {
                // Try GitHub API first
                val result = withContext(Dispatchers.IO) {
                    ChangelogRepository.fetchReleases(this@ChangelogActivity, false)
                }

                result.fold(
                    onSuccess = { releases ->
                        networkCompleted = true
                        if (releases.isNotEmpty()) {
                            showContent()
                            adapter.setReleases(releases)
                        }
                    },
                    onFailure = { error ->
                        Log.w(TAG, "GitHub API failed", error)
                        networkCompleted = true
                        showError(getString(R.string.changelog_error))
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error fetching from network", e)
                networkCompleted = true
                showError(getString(R.string.changelog_error))
            }
        }
    }

    private fun showContent() {
        splashView.visibility = View.GONE
        contentView.visibility = View.VISIBLE
        fallbackView.visibility = View.GONE
    }

    private fun showFallback(text: String) {
        splashView.visibility = View.GONE
        contentView.visibility = View.GONE
        fallbackView.visibility = View.VISIBLE
        tvFallback.text = text
        tvCacheIndicator.visibility = View.GONE

        // Apply theme colors programmatically for consistent dark/light appearance
        val theme = ThemeStore.currentTheme()
        val bgColor = ThemeUtils.parseSafeColor(theme.backgroundColor, android.graphics.Color.BLACK)
        val textColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor,
            if (ThemeUtils.isLight(bgColor)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        val secondaryTextColor = ThemeUtils.parseSafeColor(theme.textSecondaryColor,
            if (ThemeUtils.isLight(bgColor)) android.graphics.Color.GRAY else android.graphics.Color.LTGRAY)

        fallbackView.setBackgroundColor(bgColor)
        tvFallback.setTextColor(textColor)
        tvCacheIndicator.setTextColor(secondaryTextColor)
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        rvReleases.visibility = View.GONE
        tvError.visibility = View.GONE
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
    }

    private fun showError(message: String) {
        splashView.visibility = View.GONE
        contentView.visibility = View.VISIBLE
        fallbackView.visibility = View.GONE
        tvError.text = message
        tvError.visibility = View.VISIBLE
        rvReleases.visibility = View.GONE
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL: $url", e)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
