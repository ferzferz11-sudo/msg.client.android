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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.changelog.ChangelogRepository
import lavender.client.android.ui.adapter.ChangelogAdapter
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeApplier
import com.google.android.material.appbar.MaterialToolbar
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ChangelogActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChangelogActivity"
        private const val CHANGELOG_URL = "http://13.140.25.249/changelog.txt"

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
    private lateinit var adapter: ChangelogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply theme directly (synchronous, no network wait)
        val theme = ThemeStore.currentTheme()
        ThemeApplier.apply(this, theme)

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

        // Show splash and start loading
        showSplashAndLoad()
    }

    private fun showSplashAndLoad() {
        splashView.visibility = View.VISIBLE
        contentView.visibility = View.GONE
        fallbackView.visibility = View.GONE

        // Get localized app name
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val lang = prefs.getString("language", "ru")
        splashAppName.text = if (lang == "en") "Lava" else "Лава"

        // Animate splash
        splashLogo.animate()
            ?.alpha(1f)
            ?.scaleX(1.1f)
            ?.scaleY(1.1f)
            ?.setDuration(500)
            ?.setInterpolator(AccelerateDecelerateInterpolator())
            ?.withEndAction {
                splashLogo.animate()
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.setDuration(300)
                    ?.withEndAction {
                        splashAppName.animate()
                            ?.alpha(1f)
                            ?.setDuration(300)
                            ?.withEndAction {
                                // Start loading data after splash animation
                                splashView.postDelayed({
                                    loadReleases()
                                }, 400)
                            }
                            ?.start()
                    }
                    ?.start()
            }
            ?.start()
    }

    private fun loadReleases() {
        showLoading()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ChangelogRepository.fetchReleases(this@ChangelogActivity, false)
                }

                result.fold(
                    onSuccess = { releases ->
                        hideLoading()
                        if (releases.isNotEmpty()) {
                            showContent()
                            adapter.setReleases(releases)
                        } else {
                            loadFallbackChangelog()
                        }
                    },
                    onFailure = { error ->
                        hideLoading()
                        Log.e(TAG, "Failed to load changelog from GitHub", error)
                        loadFallbackChangelog()
                    }
                )
            } catch (e: Exception) {
                hideLoading()
                Log.e(TAG, "Unexpected error loading changelog", e)
                loadFallbackChangelog()
            }
        }
    }

    private fun loadFallbackChangelog() {
        CoroutineScope(Dispatchers.Main).launch {
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

                if (text.isNotEmpty()) {
                    showFallback(text)
                } else {
                    showError(getString(R.string.changelog_error))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load fallback changelog", e)
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