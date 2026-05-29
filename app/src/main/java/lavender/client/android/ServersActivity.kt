package lavender.client.android

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.session.CredentialStore.ServerEntry
import lavender.client.android.theme.ThemeStore
import java.util.UUID

class ServersActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var fabAddServer: FloatingActionButton
    private lateinit var adapter: ServerAdapter
    private val servers = mutableListOf<ServerEntry>()

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru"
        val locale = java.util.Locale.forLanguageTag(languageCode)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_servers)

        // Toolbar
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.title = getString(R.string.servers)

        // Status bar
        val systemBars = androidx.core.view.WindowInsetsCompat.Type.systemBars()
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val top = insets.getInsets(systemBars).top
            v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
            insets
        }

        // Hint
        val hintView = findViewById<TextView>(R.id.serversHint)
        hintView.text = getString(R.string.servers_hint)

        // RecyclerView
        recyclerView = findViewById(R.id.serversRecyclerView)
        emptyView = findViewById(R.id.emptyView)
        emptyView.text = getString(R.string.servers_empty)

        adapter = ServerAdapter(
            context = this,
            servers = servers,
            onSetDefault = { server -> setDefaultServer(server) },
            onDelete = { server -> deleteServer(server) },
            onSelect = { server -> selectServer(server) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // FAB
        fabAddServer = findViewById(R.id.fabAddServer)
        fabAddServer.setOnClickListener { showAddServerDialog() }

        // Theme
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                ThemeStore.theme.collect { theme -> applyTheme(theme) }
            }
        }
    }

    private fun applyTheme(theme: lavender.client.android.theme.Theme) {
        val primary = theme.primaryColor.toColorInt()
        val onPrimary = theme.onPrimaryColor.toColorInt()
        val surface = theme.surfaceColor.toColorInt()
        val textPrimary = theme.textPrimaryColor.toColorInt()
        val textSecondary = theme.textSecondaryColor.toColorInt()

        // Toolbar
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setBackgroundColor(primary)
        toolbar.setTitleTextColor(onPrimary)
        toolbar.navigationIcon?.setTint(onPrimary)
        toolbar.setTitleTextColor(onPrimary)

        // Background
        val bg = findViewById<View>(R.id.serversBackground)
        bg.setBackgroundColor(theme.backgroundColor.toColorInt())

        // Hint
        findViewById<TextView>(R.id.serversHint).setTextColor(textSecondary)

        // Empty
        findViewById<TextView>(R.id.emptyView).setTextColor(textSecondary)

        // FAB
        fabAddServer.backgroundTintList = ColorStateList.valueOf(onPrimary)
        fabAddServer.imageTintList = ColorStateList.valueOf(primary)

        // Update adapter colors
        adapter.updateColors(primary, surface, textPrimary, textSecondary)
    }

    override fun onResume() {
        super.onResume()
        loadServers()
    }

    private fun loadServers() {
        servers.clear()
        servers.addAll(CredentialStore.getServerList(this))
        adapter.notifyDataSetChanged()
        emptyView.visibility = if (servers.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (servers.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setDefaultServer(server: ServerEntry) {
        val updated = servers.map { it.copy(isDefault = it.id == server.id) }
        CredentialStore.saveServerList(this, updated)
        loadServers()
        Toast.makeText(this, getString(R.string.server_set_default, server.name), Toast.LENGTH_SHORT).show()
    }

    private fun deleteServer(server: ServerEntry) {
        CredentialStore.removeServer(this, server.id)
        loadServers()
    }

    private fun selectServer(server: ServerEntry) {
        CredentialStore.setServerAddress(this, "${server.host}:${server.port}")
        Toast.makeText(this, getString(R.string.server_selected, server.name), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showAddServerDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_server, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.editServerName)
        val hostInput = dialogView.findViewById<TextInputEditText>(R.id.editServerHost)
        val portInput = dialogView.findViewById<TextInputEditText>(R.id.editServerPort)
        val addBtn = dialogView.findViewById<MaterialButton>(R.id.btnAddServer)

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(dialogView)

        addBtn.setOnClickListener {
            val name = nameInput.text.toString().trim()
            val host = hostInput.text.toString().trim()
            val portStr = portInput.text.toString().trim()

            if (name.isEmpty()) {
                nameInput.error = getString(R.string.error_required)
                return@setOnClickListener
            }
            if (host.isEmpty()) {
                hostInput.error = getString(R.string.error_required)
                return@setOnClickListener
            }
            val port = portStr.toIntOrNull() ?: 50051

            val server = ServerEntry(
                id = UUID.randomUUID().toString(),
                name = name,
                host = host,
                port = port,
                isDefault = servers.isEmpty()
            )
            CredentialStore.addServer(this, server)
            loadServers()
            dialog.dismiss()
            Toast.makeText(this, getString(R.string.server_added, name), Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private class ServerAdapter(
        private val context: Context,
        private val servers: List<ServerEntry>,
        private val onSetDefault: (ServerEntry) -> Unit,
        private val onDelete: (ServerEntry) -> Unit,
        private val onSelect: (ServerEntry) -> Unit
    ) : RecyclerView.Adapter<ServerAdapter.VH>() {

        private var primaryColor: Int = Color.GRAY
        private var surfaceColor: Int = Color.DKGRAY
        private var textPrimaryColor: Int = Color.WHITE
        private var textSecondaryColor: Int = Color.LTGRAY

        fun updateColors(primary: Int, surface: Int, textPrimary: Int, textSecondary: Int) {
            primaryColor = primary
            surfaceColor = surface
            textPrimaryColor = textPrimary
            textSecondaryColor = textSecondary
            notifyDataSetChanged()
        }

        class VH(view: MaterialCardView) : RecyclerView.ViewHolder(view) {
            val card: MaterialCardView = view
            val name: TextView = view.findViewById(R.id.serverName)
            val address: TextView = view.findViewById(R.id.serverAddress)
            val defaultBadge: TextView = view.findViewById(R.id.defaultBadge)
            val deleteBtn: ImageButton = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_server, parent, false) as MaterialCardView
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val server = servers[position]
            holder.name.text = server.name
            holder.address.text = "${server.host}:${server.port}"
            holder.defaultBadge.visibility = if (server.isDefault) View.VISIBLE else View.GONE

            // Colors
            holder.card.setCardBackgroundColor(surfaceColor)
            holder.name.setTextColor(textPrimaryColor)
            holder.address.setTextColor(textSecondaryColor)

            holder.card.setOnClickListener { onSelect(server) }
            holder.deleteBtn.setOnClickListener {
                onDelete(server)
            }
            holder.card.setOnLongClickListener {
                if (!server.isDefault) onSetDefault(server)
                true
            }
        }

        override fun getItemCount() = servers.size
    }
}
