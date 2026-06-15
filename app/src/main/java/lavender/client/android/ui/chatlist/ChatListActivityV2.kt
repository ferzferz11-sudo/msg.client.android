package lavender.client.android.ui.chatlist

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import lavender.client.android.NewChatActivity
import lavender.client.android.R
import lavender.client.android.ServersActivity
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.ProfileClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.widget.ServerAuthBottomSheet

/**
 * ChatListActivityV2 — Activity для v2 серверов (ChatList v2 API).
 *
 * Определяет версию сервера через fetchServerInfo() и:
 * - v2 сервер (chat >= "2.0"): показывает ChatListFragmentV2 с секциями/табами
 * - v1 сервер (chat < "2.0"): fallback на ChatListActivity (v1)
 *
 * v1 файлы (ChatListActivity.kt, ChatAdapter.kt) НЕ ИЗМЕНЯЮТСЯ.
 */
class ChatListActivityV2 : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatListActivityV2"
    }

    private lateinit var viewModel: ChatListViewModelV2
    private lateinit var chatAdapter: ChatAdapterV2
    private var swipeRefresh: SwipeRefreshLayout? = null
    private var rvChatList: RecyclerView? = null
    private var tabLayout: TabLayout? = null
    private var toolbar: MaterialToolbar? = null
    private var tvToolbarTitle: TextView? = null
    private var tvToolbarSubtitle: TextView? = null
    private var ivToolbarUserAvatar: ImageView? = null
    private var ivActionSearch: ImageView? = null
    private var ivActionSettings: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SessionManager.initFromPrefs(this)
        applyTheme()

        val serverAddress = CredentialStore.getServerAddress(this) ?: ""

        if (serverAddress.isEmpty()) {
            Log.w(TAG, "No server address — falling back to v1 ChatListActivity")
            fallbackToV1()
            return
        }

        val parts = serverAddress.split(":")
        val host = parts[0]
        val httpPort = if (parts.size > 1 && parts[1].toIntOrNull() == 50052) 8083 else 8082

        // Check server version before setting up UI
        lifecycleScope.launch {
            try {
                GrpcClient.fetchServerInfo(this@ChatListActivityV2, host, httpPort)

                if (ProfileClient.isChatV2Supported()) {
                    Log.d(TAG, "v2 server detected — using ChatListActivityV2")
                    setupV2UI()
                } else {
                    Log.d(TAG, "v1 server detected — falling back to v1 ChatListActivity")
                    fallbackToV1()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to determine server version — falling back to v1", e)
                fallbackToV1()
            }
        }
    }

    private fun setupV2UI() {
        setContentView(R.layout.activity_chat_list_v2)

        val username = SessionManager.session.value.username
        val password = SessionManager.session.value.password

        if (username.isEmpty() || password.isEmpty()) {
            showAuthChoiceDialog()
            return
        }

        ThemeUi.bind(this, username)

        // Init views
        toolbar = findViewById(R.id.toolbar)
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle)
        tvToolbarSubtitle = findViewById(R.id.tvToolbarSubtitle)
        ivToolbarUserAvatar = findViewById(R.id.ivToolbarUserAvatar)
        ivActionSearch = findViewById(R.id.ivActionSearch)
        ivActionSettings = findViewById(R.id.ivActionSettings)
        tabLayout = findViewById(R.id.tabLayout)
        swipeRefresh = findViewById(R.id.srlChatList)
        rvChatList = findViewById(R.id.rvChatList)

        // Set title
        tvToolbarTitle?.text = getString(R.string.chats)

        // Setup toolbar actions
        setupToolbarActions(username)

        // Setup tabs
        setupTabs()

        // Setup RecyclerView
        setupRecyclerView(username)

        // Setup SwipeRefresh
        setupSwipeRefresh()

        // Setup FABs
        setupFABs()

        // Connect to server
        val serverAddress = CredentialStore.getServerAddress(this) ?: return
        val parts = serverAddress.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
        GrpcClient.connect(host, false, port, this)

        // Observe connection status
        lifecycleScope.launch {
            GrpcClient.connectionStatus.collect { status ->
                val statusText = when (status) {
                    lavender.client.android.data.grpc.ConnectionStatus.CONNECTING -> getString(R.string.connecting)
                    lavender.client.android.data.grpc.ConnectionStatus.READY -> getString(R.string.connection_online)
                    lavender.client.android.data.grpc.ConnectionStatus.DISCONNECTED -> getString(R.string.connection_offline)
                    else -> ""
                }
                tvToolbarSubtitle?.text = statusText
                tvToolbarSubtitle?.isVisible = statusText.isNotEmpty()
            }
        }
    }

    private fun setupToolbarActions(username: String) {
        // Avatar click -> ProfileActivity
        ivToolbarUserAvatar?.setOnClickListener {
            val intent = Intent(this, lavender.client.android.ProfileActivity::class.java)
            startActivity(intent)
        }

        // Title click -> ServersActivity
        tvToolbarTitle?.setOnClickListener {
            val intent = Intent(this, ServersActivity::class.java)
            startActivity(intent)
        }

        // Search click -> toggle search
        ivActionSearch?.setOnClickListener {
            // TODO: SearchView expansion (Phase 5)
            Log.d(TAG, "Search clicked")
        }

        // Settings click -> ServersActivity (admin)
        ivActionSettings?.setOnClickListener {
            val intent = Intent(this, ServersActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupTabs() {
        tabLayout?.let { tabs ->
            tabs.addTab(tabs.newTab().setText(R.string.tab_all))
            tabs.addTab(tabs.newTab().setText(R.string.tab_ai))
            tabs.addTab(tabs.newTab().setText(R.string.tab_groups))

            tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    val filter = when (tab?.position) {
                        0 -> "all"
                        1 -> "ai"
                        2 -> "groups"
                        else -> "all"
                    }
                    viewModel.setTabFilter(filter)
                }
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }
    }

    private fun setupRecyclerView(username: String) {
        viewModel = ChatListViewModelV2(application)

        chatAdapter = ChatAdapterV2(
            scope = lifecycleScope,
            onChatClick = { chat -> navigateToChat(chat, username) },
            onChatLongClick = { chat, anchorView ->
                // TODO: Selection mode (Phase 2)
                Log.d(TAG, "Long click on chat: ${chat.name}")
            },
            onSelectionChanged = { count ->
                // TODO: Update toolbar selection count
            }
        )

        rvChatList?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = chatAdapter
            setHasFixedSize(false)
        }

        // Observe sections
        lifecycleScope.launch {
            viewModel.sections.collectLatest { sections ->
                chatAdapter.setSections(sections)
            }
        }

        // Observe loading
        lifecycleScope.launch {
            viewModel.isLoading.collectLatest { loading ->
                swipeRefresh?.isRefreshing = loading
            }
        }

        // Observe connection -> load chats when ready
        lifecycleScope.launch {
            viewModel.connectionStatus.collectLatest { status ->
                if (status == lavender.client.android.data.grpc.ConnectionStatus.READY) {
                    viewModel.loadChats()
                }
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh?.setOnRefreshListener {
            viewModel.refreshChats()
        }
    }

    private fun setupFABs() {
        findViewById<View>(R.id.fabAi)?.setOnClickListener {
            // TODO: Create AI chat (OwlActivity or HermesChatActivity)
            Log.d(TAG, "FAB AI clicked")
        }
        findViewById<View>(R.id.fabAddChat)?.setOnClickListener {
            val intent = Intent(this, NewChatActivity::class.java)
            startActivity(intent)
        }
    }

    private fun navigateToChat(chat: ChatInfo, username: String) {
        when (chat.type) {
            "favorites" -> {
                val intent = Intent(this, NewChatActivity::class.java).apply {
                    putExtra("USERNAME", username)
                    putExtra("CHAT_NAME", getString(R.string.favorites))
                    putExtra("ROOM_ID", "favorites_$username")
                    putExtra("IS_DIRECT", false)
                    putExtra("PARTICIPANTS", "[\"$username\"]")
                    putExtra("CREATOR", username)
                }
                startActivity(intent)
            }
            "hermes" -> {
                val intent = Intent(this, lavender.client.android.ui.hermes.HermesChatActivity::class.java).apply {
                    putExtra("CHAT_ID", chat.id)
                    putExtra("CHAT_NAME", chat.name)
                    putExtra("ACTIVE_AGENT_ID", chat.activeAgentId)
                    putExtra("AGENT_MODE", chat.agentMode)
                }
                startActivity(intent)
            }
            "owl" -> {
                val intent = Intent(this, lavender.client.android.ui.owl.OwlChatActivity::class.java).apply {
                    putExtra("CHAT_ID", chat.id)
                    putExtra("CHAT_NAME", chat.name)
                }
                startActivity(intent)
            }
            else -> {
                val serverAddress = CredentialStore.getServerAddress(this) ?: ""
                val intent = Intent(this, NewChatActivity::class.java).apply {
                    putExtra("USERNAME", username)
                    putExtra("SERVER_ADDRESS", serverAddress)
                    putExtra("CHAT_NAME", chat.name)
                    putExtra("ROOM_ID", chat.id)
                    putExtra("IS_DIRECT", chat.type == "direct")
                    putExtra("CHAT_TYPE", chat.type)
                    putExtra("PARTICIPANTS", chat.participants)
                    putExtra("AVATAR_URL", chat.avatarUrl)
                    putExtra("FULL_AVATAR_URL", chat.fullAvatarUrl)
                    putExtra("CREATOR", chat.creator)
                }
                startActivity(intent)
            }
        }
    }

    private fun fallbackToV1() {
        val intent = Intent(this, lavender.client.android.ChatListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showAuthChoiceDialog() {
        val serverAddress = CredentialStore.getServerAddress(this) ?: return
        val parts = serverAddress.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051

        ServerAuthBottomSheet(
            context = this,
            serverName = "Lava",
            serverHost = host,
            serverPort = port,
            onLogin = { },
            onRegister = { }
        ).show()
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("ThemePrefs", MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )
        ThemeApplier.apply(this, ThemeStore.currentTheme())
    }
}
