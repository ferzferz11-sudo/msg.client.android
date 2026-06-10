package lavender.client.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.changelog.ChangelogRepository
import lavender.client.android.ui.adapter.ChangelogAdapter
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.theme.ui.ThemeUi
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ChangelogActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChangelogActivity"
        private const val CHANGELOG_URL = "http://13.140.25.249/changelog.txt"
        private const val BUNDLED_ASSET = "changelog_bundled.txt"

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

    // Track if we already showed bundled content to avoid flickering
    private var bundledShown = false

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

        // Step 1: Load bundled changelog instantly from assets
        loadBundledChangelog()

        // Step 2: Try to fetch from GitHub in background
        fetchFromNetwork()
    }

    /**
     * Load bundled changelog from assets — instant, no network needed.
     * This is always shown first so the user sees content immediately.
     */
    private fun loadBundledChangelog() {
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    assets.open(BUNDLED_ASSET).bufferedReader().use { it.readText() }
                }
                if (text.isNotEmpty()) {
                    showFallback(text, isBundled = true)
                    bundledShown = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load bundled changelog", e)
                // Not critical — network fetch will handle it
            }
        }
    }

    /**
     * Fetch from GitHub API (releases) or server fallback (changelog.txt).
     * If GitHub succeeds, replaces the bundled view with the full release list.
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
                        if (releases.isNotEmpty()) {
                            // GitHub success — show full release list
                            showContent()
                            adapter.setReleases(releases)
                        }
                        // If empty, keep bundled view (already shown)
                    },
                    onFailure = { error ->
                        Log.w(TAG, "GitHub API failed, trying server fallback", error)
                        // Try server fallback
                        tryServerFallback()
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error fetching from network", e)
                tryServerFallback()
            }
        }
    }

    /**
     * Try loading changelog.txt from server as last resort.
     */
    private suspend fun tryServerFallback() {
        try {
            val text = withContext(Dispatchers.IO) {
                val url = URL(CHANGELOG_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val result = reader.use { it.readText() }
                    connection.disconnect()
                    result
                } else {
                    connection.disconnect()
                    ""
                }
            }

            if (text.isNotEmpty() && !bundledShown) {
                showFallback(text, isBundled = false)
            }
            // If bundled was already shown, keep it — server text is the same
        } catch (e: Exception) {
            Log.e(TAG, "Server fallback also failed", e)
            if (!bundledShown) {
                showError(getString(R.string.changelog_error))
            }
        }
    }

    private fun showContent() {
        splashView.visibility = View.GONE
        contentView.visibility = View.VISIBLE
        fallbackView.visibility = View.GONE
    }

    private fun showFallback(text: String, isBundled: Boolean) {
        splashView.visibility = View.GONE
        contentView.visibility = View.GONE
        fallbackView.visibility = View.VISIBLE
        tvFallback.text = text

        if (isBundled) {
            tvCacheIndicator.text = getString(R.string.changelog_loading_from_cache)
            tvCacheIndicator.visibility = View.VISIBLE
        } else {
            tvCacheIndicator.visibility = View.GONE
        }
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
