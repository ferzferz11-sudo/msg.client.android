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
import lavender.client.android.data.db.AppDatabase
import lavender.client.android.data.db.toDomain
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.adapter.MessageAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class FavoritesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private lateinit var emptyText: TextView
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
        // setDecorFitsSystemWindows(true) — default, needed for adjustResize
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_favorites)

        username = intent.getStringExtra("USERNAME") ?: ""
        ThemeUi.bind(this, username)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.favoritesRecyclerView)
        emptyText = findViewById(R.id.emptyText)

        adapter = MessageAdapter(
            currentUsername = username,
            isGroupChat = true, // To show sender names
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

        loadFavorites()
    }

    private fun loadFavorites() {
        val userId = GrpcClient.getUserId() ?: ""
        if (userId.isEmpty()) {
            emptyText.isVisible = true
            emptyText.text = "Error: User ID not loaded"
            return
        }

        val favoritesRoomId = "favorites_$username"

        // Step 1: Show cached data immediately
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(this@FavoritesActivity)
                val cached = db.messageDao().getFavorites(favoritesRoomId).map { it.toDomain() }
                if (cached.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        adapter.submitList(cached)
                    }
                }
            } catch (_: Exception) {}
        }

        // Step 2: Refresh from server (async, non-blocking)
        GrpcClient.getFavorites(userId) { messages ->
            runOnUiThread {
                adapter.submitList(messages)
                emptyText.isVisible = messages.isEmpty()
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
        val userId = GrpcClient.getUserId() ?: ""
        GrpcClient.removeFavorite(userId, message.id) { success ->
            if (success) {
                runOnUiThread { loadFavorites() }
            }
        }
    }
}
