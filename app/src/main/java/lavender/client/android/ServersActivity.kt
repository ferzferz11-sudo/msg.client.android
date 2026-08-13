package lavender.client.android

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.session.CredentialStore.ServerEntry
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.ui.widget.StandardBottomSheet
import lavender.client.android.ui.widget.LoginBottomSheet
import lavender.client.android.ui.widget.RegisterBottomSheet
import lavender.client.android.ui.widget.ServerAuthBottomSheet
import java.util.UUID

/**
 * ServersActivity — управление списком серверов.
 *
 * Все сервары (включая dev) доступны всем пользователям.
 * По умолчанию предустановлены Lava Germany (prod) и Lava Germany dev.
 * При входе через сервер — prefill последнего логина + splash перед навигацией.
 */
class ServersActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var fabAddServer: FloatingActionButton
    private lateinit var adapter: ServerAdapter
    private val servers = mutableListOf<ServerEntry>()

    // Currently selected server address (host:port) from CredentialStore
    private var currentServerAddress: String = ""

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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_servers)

        // Remember current server for highlighting
        currentServerAddress = CredentialStore.getServerAddress(this)

        // Toolbar — custom title inside toolbar (no baseline title)
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        // Status bar insets
        val systemBars = WindowInsetsCompat.Type.systemBars()
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
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
            onDelete = { server -> confirmDeleteServer(server) },
            onSelect = { server -> selectServer(server) }
        )
        adapter.currentServerAddress = currentServerAddress
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Swipe to delete
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos in servers.indices) {
                    adapter.notifyItemChanged(pos)
                    confirmDeleteServer(servers[pos])
                }
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)

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
        val bgColor = theme.backgroundColor.toColorInt()
        val textPrimary = theme.textPrimaryColor.toColorInt()
        val textSecondary = theme.textSecondaryColor.toColorInt()

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setBackgroundColor(primary)
        toolbar.setTitleTextColor(onPrimary)
        toolbar.navigationIcon?.setTint(onPrimary)
        findViewById<TextView>(R.id.toolbarTitle).setTextColor(onPrimary)

        findViewById<View>(R.id.rootLayout).setBackgroundColor(bgColor)
        findViewById<View>(R.id.serversHint).let { (it as TextView).setTextColor(textSecondary) }
        findViewById<View>(R.id.emptyView).let { (it as TextView).setTextColor(textSecondary) }

        fabAddServer.backgroundTintList = ColorStateList.valueOf(primary)
        fabAddServer.imageTintList = ColorStateList.valueOf(onPrimary)

        adapter.updateColors(primary, surface, textPrimary, textSecondary)
    }

    override fun onResume() {
        super.onResume()
        currentServerAddress = CredentialStore.getServerAddress(this)
        adapter.currentServerAddress = currentServerAddress
        loadServers()
    }

    private fun loadServers() {
        servers.clear()
        val list = CredentialStore.getServerList(this)
        if (list.isEmpty()) {
            // First run — seed with default servers (all servers available to all users)
            val defaultServers = listOf(
                CredentialStore.ServerEntry(
                    id = "default-server-1",
                    name = "Lava Germany",
                    host = "13.140.25.249",
                    port = 50051,
                    isDefault = true
                ),
                CredentialStore.ServerEntry(
                    id = "default-server-2",
                    name = "Lava Germany dev",
                    host = "13.140.25.249",
                    port = 50052,
                    isDefault = false
                )
            )
            CredentialStore.saveServerList(this, defaultServers)
            servers.addAll(defaultServers)
        } else {
            servers.addAll(list)
        }
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

    private fun confirmDeleteServer(server: ServerEntry) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_server_title))
            .setMessage(getString(R.string.delete_server_confirm, server.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ -> doDeleteServer(server) }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun doDeleteServer(server: ServerEntry) {
        val wasDefault = server.isDefault
        CredentialStore.removeServer(this, server.id)

        if (wasDefault) {
            val remaining = CredentialStore.getServerList(this)
            if (remaining.isNotEmpty()) {
                val updated = remaining.mapIndexed { idx, s -> s.copy(isDefault = idx == 0) }
                CredentialStore.saveServerList(this, updated)
            }
        }

        loadServers()
        Toast.makeText(this, getString(R.string.server_deleted, server.name), Toast.LENGTH_SHORT).show()
    }

    private fun selectServer(server: ServerEntry) {
        // Show server auth bottom sheet with logo, health check, login/register
        val authSheet = ServerAuthBottomSheet(
            context = this,
            serverName = server.name,
            serverHost = server.host,
            serverPort = server.port,
            onLogin = { showServerLoginSheet(server) },
            onRegister = { showServerRegisterSheet(server) }
        )
        authSheet.show()
    }

    private fun showServerLoginSheet(server: ServerEntry) {
        val serverAddress = "${server.host}:${server.port}"
        val lastUsername = CredentialStore.getLastUsername(this)

        lateinit var loginSheet: LoginBottomSheet

        loginSheet = LoginBottomSheet(
            context = this,
            onLogin = { u: String, p: String ->
                SessionManager.login(this, u, p, serverAddress, register = false, email = "") { result: String? ->
                    lifecycleScope.launch {
                        when (result) {
                            "SUCCESS" -> {
                                CredentialStore.setServerAddress(this@ServersActivity, serverAddress)
                                CredentialStore.setCredentials(this@ServersActivity, u, p, serverAddress)
                                CredentialStore.setLastUsername(this@ServersActivity, u)
                                val userId = SessionManager.session.value.userId
                                if (userId.isNotEmpty()) {
                                    CredentialStore.setUserId(this@ServersActivity, userId)
                                }
                                loginSheet.dismiss()
                                showSplashAndFinish()
                            }
                            "USER_NOT_FOUND" -> {
                                loginSheet.setLoading(false)
                                Toast.makeText(this@ServersActivity, R.string.user_not_found, Toast.LENGTH_LONG).show()
                            }
                            "AUTH_FAILED" -> {
                                loginSheet.setLoading(false)
                                Toast.makeText(this@ServersActivity, R.string.wrong_password, Toast.LENGTH_LONG).show()
                            }
                            "SERVER_ERROR" -> {
                                loginSheet.setLoading(false)
                                Toast.makeText(this@ServersActivity, R.string.server_error, Toast.LENGTH_LONG).show()
                            }
                            "CONNECTION_FAILED" -> {
                                loginSheet.setLoading(false)
                                Toast.makeText(this@ServersActivity, R.string.connection_failed, Toast.LENGTH_LONG).show()
                            }
                            else -> {
                                loginSheet.setLoading(false)
                                Toast.makeText(this@ServersActivity, result ?: getString(R.string.unknown_error), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            },
            onCancel = {
                loginSheet.dismiss()
                selectServer(server)
            }
        )

        // Prefill last username
        if (lastUsername.isNotEmpty()) {
            loginSheet.prefillUsername(lastUsername)
        }

        loginSheet.show()
    }

    private fun showServerRegisterSheet(server: ServerEntry, prefillUser: String = "", prefillPass: String = "") {
        val serverAddress = "${server.host}:${server.port}"

        lateinit var registerSheet: RegisterBottomSheet

        registerSheet = RegisterBottomSheet(
            context = this,
            onRegister = { u: String, p: String, email: String ->
                SessionManager.login(this, u, p, serverAddress, register = true, email = email) { result: String? ->
                    lifecycleScope.launch {
                        when (result) {
                            "SUCCESS", "REGISTRATION_SUCCESS" -> {
                                CredentialStore.setServerAddress(this@ServersActivity, serverAddress)
                                CredentialStore.setCredentials(this@ServersActivity, u, p, serverAddress)
                                CredentialStore.setLastUsername(this@ServersActivity, u)
                                val userId = SessionManager.session.value.userId
                                if (userId.isNotEmpty()) {
                                    CredentialStore.setUserId(this@ServersActivity, userId)
                                }
                                registerSheet.dismiss()
                                showSplashAndFinish()
                            }
                            else -> {
                                registerSheet.setLoading(false)
                                Toast.makeText(this@ServersActivity, result ?: getString(R.string.unknown_error), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            },
            onCancel = {
                registerSheet.dismiss()
                selectServer(server)
            },
            prefillUsername = prefillUser,
            prefillPassword = prefillPass
        )

        registerSheet.setTitle(getString(R.string.register_to_server, server.name))
        registerSheet.show()
    }

    private fun showSplashAndFinish() {
        // Clear local cache silently on successful login
        clearAllCache()
        // Show splash screen before navigating to chat list (same as normal login flow)
        val intent = Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    /** Clear all local cache silently on successful login. */
    private fun clearAllCache() {
        lavender.client.android.data.cache.CacheUtils.clearAllSync(this)
    }

    private fun showAddServerDialog() {
        val theme = ThemeStore.currentTheme()
        val sheet = StandardBottomSheet(this, R.layout.dialog_add_server, theme)
        sheet.setTitle(getString(R.string.add_server))

        val nameInput = sheet.findViewById<TextInputEditText>(R.id.editServerName)
        val hostInput = sheet.findViewById<TextInputEditText>(R.id.editServerHost)
        val portInput = sheet.findViewById<TextInputEditText>(R.id.editServerPort)
        val addBtn = sheet.findViewById<MaterialButton>(R.id.btnAddServer)

        addBtn?.setOnClickListener {
            val name = nameInput?.text.toString().trim()
            val host = hostInput?.text.toString().trim()
            val portStr = portInput?.text.toString().trim()

            if (name.isEmpty()) {
                nameInput?.error = getString(R.string.error_required)
                return@setOnClickListener
            }
            if (host.isEmpty()) {
                hostInput?.error = getString(R.string.error_required)
                return@setOnClickListener
            }
            val port = portStr.toIntOrNull()
            if (port == null || port < 1 || port > 65535) {
                portInput?.error = getString(R.string.error_invalid_port)
                return@setOnClickListener
            }

            val server = ServerEntry(
                id = UUID.randomUUID().toString(),
                name = name,
                host = host,
                port = port,
                isDefault = servers.isEmpty()
            )
            CredentialStore.addServer(this, server)
            loadServers()
            sheet.dismiss()
            Toast.makeText(this, getString(R.string.server_added, name), Toast.LENGTH_SHORT).show()
        }

        sheet.show()
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
        var currentServerAddress: String = ""

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
            val address = "${server.host}:${server.port}"
            holder.name.text = server.name
            holder.address.text = address
            holder.defaultBadge.visibility = if (server.isDefault) View.VISIBLE else View.GONE

            val isSelected = address == currentServerAddress
            if (isSelected) {
                holder.card.strokeWidth = 2
                holder.card.strokeColor = primaryColor
            } else {
                holder.card.strokeWidth = 0
            }

            holder.card.setCardBackgroundColor(surfaceColor)
            holder.name.setTextColor(textPrimaryColor)
            holder.address.setTextColor(textSecondaryColor)

            holder.card.setOnClickListener { onSelect(server) }
            if (server.isDefault || server.isProtected) {
                holder.deleteBtn.visibility = View.GONE
            } else {
                holder.deleteBtn.visibility = View.VISIBLE
                holder.deleteBtn.setOnClickListener { onDelete(server) }
            }
            holder.card.setOnLongClickListener {
                if (!server.isDefault) onSetDefault(server)
                true
            }
        }

        override fun getItemCount() = servers.size
    }
}
