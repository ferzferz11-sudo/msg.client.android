package lavender.client.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import lavender.client.android.data.changelog.ChangelogRepository
import lavender.client.android.data.changelog.ReleaseInfo
import lavender.client.android.ui.adapter.ChangelogAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar

class ChangelogActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChangelogActivity"

        fun createIntent(context: Context): Intent {
            return Intent(context, ChangelogActivity::class.java)
        }
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var rvReleases: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var adapter: ChangelogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_changelog)

        toolbar = findViewById(R.id.toolbar)
        rvReleases = findViewById(R.id.rvReleases)
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)

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

        // Load releases
        loadReleases()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadReleases(forceRefresh: Boolean = false) {
        showLoading()

        lifecycleScope.launch {
            try {
                val result = ChangelogRepository.fetchReleases(this@ChangelogActivity, forceRefresh)

                result.fold(
                    onSuccess = { releases ->
                        hideLoading()
                        if (releases.isNotEmpty()) {
                            adapter.setReleases(releases)
                        } else {
                            showError(getString(R.string.changelog_empty))
                        }
                    },
                    onFailure = { error ->
                        hideLoading()
                        Log.e(TAG, "Failed to load changelog from GitHub", error)
                        showError(getString(R.string.changelog_error))
                        // Offer to open on GitHub as fallback
                        Snackbar.make(rvReleases, R.string.changelog_open_github, Snackbar.LENGTH_LONG)
                            .setAction(R.string.open) {
                                openUrl("https://github.com/ferzferz11-sudo/msg.client.android/releases")
                            }
                            .show()
                    }
                )
            } catch (e: Exception) {
                hideLoading()
                Log.e(TAG, "Unexpected error loading changelog", e)
                showError(getString(R.string.changelog_error))
            }
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
}
