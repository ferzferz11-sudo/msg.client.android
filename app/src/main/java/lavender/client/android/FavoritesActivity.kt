package lavender.client.android

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.adapter.MessageAdapter
import java.util.Locale

class FavoritesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private lateinit var emptyText: TextView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private var username: String = ""

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        username = SessionManager.session.value.username
        ThemeUi.bind(this, username)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.favorites)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.favoritesRecyclerView)
        emptyText = findViewById(R.id.emptyText)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        adapter = MessageAdapter(
            currentUsername = username,
            isGroupChat = true,
            onMessageClick = { message ->
                showFavoritesActionDialog(message)
            },
            onSelectionChanged = {},
            onMessageLongClick = { message ->
                showFavoritesActionDialog(message)
            },
            chatId = "favorites_$username"
        )

        recyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = false }
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener {
            loadFavorites()
        }

        loadFavorites()
    }

    private fun loadFavorites() {
        val userId = SessionManager.session.value.userId
        if (userId.isEmpty()) {
            emptyText.isVisible = true
            emptyText.text = getString(R.string.user_not_found)
            swipeRefresh.isRefreshing = false
            return
        }

        val favoritesRoomId = "favorites_$username"

        // Load from server
        GrpcClient.getFavorites(userId) { messages ->
            runOnUiThread {
                swipeRefresh.isRefreshing = false
                adapter.submitList(messages)
                emptyText.isVisible = messages.isEmpty()
                if (messages.isEmpty()) {
                    emptyText.text = getString(R.string.no_favorites_yet)
                }
            }
        }
    }

    private fun showFavoritesActionDialog(message: Message) {
        val options = arrayOf(getString(R.string.remove), getString(R.string.copy))
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> removeFromFavorites(message)
                    1 -> {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("message", message.text)
                        clipboard.setPrimaryClip(clip)
                    }
                }
            }
            .show()
    }

    private fun removeFromFavorites(message: Message) {
        val userId = SessionManager.session.value.userId
        if (userId.isEmpty()) return
        GrpcClient.removeFavorite(userId, message.id) { success ->
            if (success) {
                runOnUiThread { loadFavorites() }
            }
        }
    }
}
