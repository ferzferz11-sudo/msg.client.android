package lavender.client.android.ui.chatlist

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import lavender.client.android.NewChatActivity
import lavender.client.android.R
import lavender.client.android.SplashLoadingActivity
import lavender.client.android.ServersActivity
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.ProfileClient
import lavender.client.android.data.models.AIChatInfo
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.widget.AIBottomSheet
import lavender.client.android.ui.widget.LoginBottomSheet
import lavender.client.android.ui.widget.RegisterBottomSheet
import lavender.client.android.ui.widget.ServerAuthBottomSheet
import lavender.client.android.ui.widget.ProfileBottomSheet
import lavender.client.android.ui.widget.NewChatBottomSheet
import lavender.client.android.ui.adapter.ChatAdapter

/**
 * ChatListActivity — единый Activity для списка чатов.
 *
 * Работает на v1 и v2 серверах:
 * - v2: полный функционал (Pin, Archive, Search, Tabs)
 * - v1: базовый функционал (список чатов, Favorites, AI)
 *
 * Никакого fallback на отдельный Activity — всё в одном месте.
 */
class ChatListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatListActivity"
        private const val SEARCH_DEBOUNCE_MS = 300L
    }

    private lateinit var viewModel: ChatListViewModel
    private lateinit var chatAdapter: ChatAdapter
    private var swipeRefresh: SwipeRefreshLayout? = null
    private var rvChatList: RecyclerView? = null
    private var tabLayout: TabLayout? = null
    private var toolbar: MaterialToolbar? = null
    private var tvToolbarTitle: TextView? = null
    private var tvToolbarSubtitle: TextView? = null
    private var ivToolbarUserAvatar: ImageView? = null
    private var ivActionSettings: ImageView? = null

    // ActionMode
    private var actionMode: ActionMode? = null
    private var searchView: androidx.appcompat.widget.SearchView? = null
    private var searchDebounceJob: Job? = null

    // AI Bottom Sheet
    private var aiBottomSheet: AIBottomSheet? = null
    private val aiChats = mutableListOf<AIChatInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SessionManager.initFromPrefs(this)
        applyTheme()

        val serverAddress = CredentialStore.getServerAddress(this) ?: ""

        if (serverAddress.isEmpty()) {
            Log.w(TAG, "No server address — showing auth dialog")
            showAuthChoiceDialog()
            return
        }

        // Always setup UI — works on both v1 and v2 servers
        setupUI()
    }

    private fun setupUI() {
        setContentView(R.layout.activity_chat_list)

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
        ivActionSettings = findViewById(R.id.ivActionSettings)
        tabLayout = findViewById(R.id.tabLayout)
        swipeRefresh = findViewById(R.id.srlChatList)
        rvChatList = findViewById(R.id.rvChatList)

        // Set title
        tvToolbarTitle?.text = getString(R.string.chats)

        // Setup toolbar actions
        setupToolbarActions(username)

        // Setup search menu in toolbar
        setupSearchMenu()

        // Setup tabs
        setupTabs()

        // Setup RecyclerView
        setupRecyclerView(username)

        // Setup SwipeRefresh
        setupSwipeRefresh()

        // Setup FABs
        setupFABs()

        // Register back press handler for selection mode
        setupBackPressHandler()

        // Connect to server
        val serverAddress = CredentialStore.getServerAddress(this) ?: return
        val parts = serverAddress.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
        GrpcClient.connect(host, false, port, this)

        // Fetch user avatar for toolbar
        val avatarUsername = username
        GrpcClient.getUserAvatar(avatarUsername) { _ ->
            Log.d(TAG, "Avatar fetched for $avatarUsername")
        }
        // Observe avatar cache to update toolbar avatar
        lifecycleScope.launch {
            GrpcClient.avatarCacheFlow.collectLatest { cache ->
                val url = cache[avatarUsername]
                if (!url.isNullOrEmpty() && ivToolbarUserAvatar != null) {
                    Glide.with(this@ChatListActivity)
                        .load(url)
                        .apply(RequestOptions.circleCropTransform()
                            .placeholder(R.drawable.ic_default_avatar)
                            .error(R.drawable.ic_default_avatar))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(ivToolbarUserAvatar!!)
                }
            }
        }

        // Observe connection status
        lifecycleScope.launch {
            GrpcClient.connectionStatus.collect { status ->
                val statusText = when (status) {
                    lavender.client.android.data.grpc.ConnectionStatus.CONNECTING -> getString(R.string.connecting)
                    lavender.client.android.data.grpc.ConnectionStatus.READY -> getString(R.string.connection_online)
                    lavender.client.android.data.grpc.ConnectionStatus.DISCONNECTED -> getString(R.string.connection_offline)
                    lavender.client.android.data.grpc.ConnectionStatus.RECONNECTING -> getString(R.string.connecting)
                    lavender.client.android.data.grpc.ConnectionStatus.FAILED -> getString(R.string.connection_offline)
                }
                tvToolbarSubtitle?.text = statusText
                tvToolbarSubtitle?.isVisible = statusText.isNotEmpty()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Safety net: if chats list is empty but we're connected, reload.
        // This handles the case where loadChats() was called before READY
        // (e.g. race condition during server switch).
        if (::viewModel.isInitialized && viewModel.getChats().isEmpty()
            && GrpcClient.connectionStatus.value == ConnectionStatus.READY
        ) {
            Log.d(TAG, "onResume: chats empty but READY — reloading")
            viewModel.loadChats()
        }
    }

    private fun setupToolbarActions(username: String) {
        // Avatar click -> ProfileBottomSheet
        ivToolbarUserAvatar?.setOnClickListener {
            ProfileBottomSheet.newInstance().show(supportFragmentManager, "profile")
        }

        // Title click -> ServersActivity
        tvToolbarTitle?.setOnClickListener {
            val intent = Intent(this, ServersActivity::class.java)
            startActivity(intent)
        }

        // Settings click -> ProfileBottomSheet
        ivActionSettings?.setOnClickListener {
            ProfileBottomSheet.newInstance().show(supportFragmentManager, "profile")
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
        viewModel = ChatListViewModel(application)

        chatAdapter = ChatAdapter(
            scope = lifecycleScope,
            onChatClick = { chat ->
                if (chatAdapter.isSelectionMode()) {
                    // In selection mode: tap toggles selection
                    chatAdapter.toggleSelection(chat.id)
                    updateActionModeTitle()
                    if (chatAdapter.getSelectedIds().isEmpty()) {
                        actionMode?.finish()
                    }
                } else {
                    // Normal mode: navigate to chat
                    navigateToChat(chat, username)
                }
            },
            onChatLongClick = { chat, anchorView ->
                if (!chatAdapter.isSelectionMode()) {
                    // Start selection mode on long press
                    chatAdapter.setSelectionMode(true)
                    chatAdapter.toggleSelection(chat.id)
                    startSupportActionMode(actionModeCallback)
                    updateActionModeTitle()
                }
            },
            onSelectionChanged = { count ->
                updateActionModeTitle()
                if (count == 0 && actionMode != null) {
                    actionMode?.finish()
                }
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

        // NOTE: loadChats() is called from ViewModel's init when it observes READY status.
        // Do NOT duplicate the call here — double invocation causes race condition.
    }

    private fun setupSwipeRefresh() {
        swipeRefresh?.setOnRefreshListener {
            viewModel.refreshChats()
        }
    }

    private fun setupFABs() {
        findViewById<View>(R.id.fabAi)?.setOnClickListener {
            showAIBottomSheet()
        }
        findViewById<View>(R.id.fabAddChat)?.setOnClickListener {
            NewChatBottomSheet.newInstance().show(supportFragmentManager, "new_chat")
        }
    }

    // ======= AI Bottom Sheet =======

    private fun showAIBottomSheet() {
        // Filter AI chats from the full list (types: "hermes", "owl")
        aiChats.clear()
        aiChats.addAll(viewModel.getChats().filter {
            it.type == "hermes" || it.type == "owl"
        }.map { chat ->
            AIChatInfo(
                id = chat.id,
                name = chat.name,
                type = chat.type
            )
        })

        aiBottomSheet = AIBottomSheet(
            context = this,
            existingChats = aiChats,
            onChatClick = { aiChat ->
                if (aiChat.type == "hermes") {
                    openHermesChat(aiChat.id, aiChat.name)
                } else {
                    openOwlChat(aiChat.id, aiChat.name)
                }
            },
            onDeleteChat = { aiChat ->
                val userId = SessionManager.session.value.userId
                val username = SessionManager.session.value.username
                if (userId.isNotEmpty()) {
                    GrpcClient.deleteChat(aiChat.id, userId, username) { success, _ ->
                        if (success) {
                            viewModel.loadChats()
                        }
                    }
                }
            },
            onSettingsClick = { aiChat ->
                if (aiChat.type == "hermes") {
                    openHermesSettings(aiChat.id)
                } else {
                    openOwlSettings(aiChat.id)
                }
            },
            onCreateHermesChat = {
                val hermesCount = aiChats.count { it.type == "hermes" }
                val chatName = getString(R.string.lava_ai_n, hermesCount + 1)
                openHermesChat("", chatName)
            },
            onCreateOwlChat = {
                val owlCount = aiChats.count { it.type == "owl" }
                val chatName = getString(R.string.owl_agent_n, owlCount + 1)
                openOwlChat("", chatName)
            },
            onOpenNotifications = {
                startActivity(Intent(this, lavender.client.android.ui.notification.NotificationActivity::class.java))
            },
            onOpenRemoteAgents = {
                startActivity(Intent(this, lavender.client.android.ui.remote.RemoteAgentActivity::class.java))
            },
            unreadNotifCount = 0
        )
        aiBottomSheet?.buildAndShow()
    }

    private fun openHermesChat(chatId: String, chatName: String) {
        val intent = Intent(this, lavender.client.android.ui.hermes.HermesChatActivity::class.java).apply {
            putExtra("CHAT_ID", chatId)
            putExtra("CHAT_NAME", chatName)
        }
        startActivity(intent)
    }

    private fun openOwlChat(chatId: String, chatName: String) {
        val intent = Intent(this, lavender.client.android.ui.owl.OwlChatActivity::class.java).apply {
            putExtra("CHAT_ID", chatId)
            putExtra("CHAT_NAME", chatName)
        }
        startActivity(intent)
    }

    private fun openHermesSettings(chatId: String) {
        val intent = Intent(this, lavender.client.android.ui.owl.OwlSettingsActivity::class.java).apply {
            putExtra("sessionId", chatId)
            putExtra("isHermes", true)
        }
        startActivity(intent)
    }

    private fun openOwlSettings(chatId: String) {
        val intent = Intent(this, lavender.client.android.ui.owl.OwlSettingsActivity::class.java).apply {
            putExtra("CHAT_ID", chatId)
        }
        startActivity(intent)
    }

    // ======= ActionMode (Selection Mode) =======

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            actionMode = mode
            mode.menuInflater.inflate(R.menu.chat_list_action_mode, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val selectedChats = chatAdapter.getSelectedChats()
            if (selectedChats.isEmpty()) return false

            return when (item.itemId) {
                R.id.action_pin -> {
                    pinSelectedChats(selectedChats)
                    true
                }
                R.id.action_mute -> {
                    muteSelectedChats(selectedChats)
                    true
                }
                R.id.action_archive -> {
                    archiveSelectedChats(selectedChats)
                    true
                }
                R.id.action_delete -> {
                    deleteSelectedChats(selectedChats)
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            chatAdapter.clearSelection()
        }
    }

    private fun updateActionModeTitle() {
        val count = chatAdapter.getSelectedIds().size
        actionMode?.title = getString(R.string.selected_count, count)
    }

    private fun pinSelectedChats(chats: List<ChatInfo>) {
        lifecycleScope.launch {
            var pinned = 0
            var unpinned = 0
            for (chat in chats) {
                if (chat.isPinned) {
                    if (GrpcClient.unpinChat(this@ChatListActivity, chat.id)) unpinned++
                } else {
                    if (GrpcClient.pinChat(this@ChatListActivity, chat.id)) pinned++
                }
            }
            if (pinned > 0 || unpinned > 0) {
                viewModel.loadChats()
            }
            actionMode?.finish()
        }
    }

    private fun muteSelectedChats(chats: List<ChatInfo>) {
        lifecycleScope.launch {
            for (chat in chats) {
                viewModel.toggleMute(chat.id, !chat.isMuted)
            }
            actionMode?.finish()
        }
    }

    private fun archiveSelectedChats(chats: List<ChatInfo>) {
        lifecycleScope.launch {
            var archived = 0
            var unarchived = 0
            for (chat in chats) {
                if (chat.isArchived) {
                    if (GrpcClient.unarchiveChat(this@ChatListActivity, chat.id)) unarchived++
                } else {
                    if (GrpcClient.archiveChat(this@ChatListActivity, chat.id)) archived++
                }
            }
            if (archived > 0 || unarchived > 0) {
                viewModel.loadChats()
            }
            actionMode?.finish()
        }
    }

    private fun deleteSelectedChats(chats: List<ChatInfo>) {
        lifecycleScope.launch {
            var deleted = 0
            for (chat in chats) {
                viewModel.deleteChat(chat.id)
                deleted++
            }
            if (deleted > 0) {
                viewModel.loadChats()
            }
            actionMode?.finish()
        }
    }

    // ======= Search =======

    private fun setupSearchMenu() {
        toolbar?.inflateMenu(R.menu.chat_list_search)
        toolbar?.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.action_search) {
                // SearchView is handled by its own listener in onMenuItemClick
                true
            } else {
                false
            }
        }

        // Find SearchView from menu and set listener
        val searchItem = toolbar?.menu?.findItem(R.id.action_search)
        searchView = searchItem?.actionView as? androidx.appcompat.widget.SearchView
        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                return true
            }
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.loadChats()
                return true
            }
        })

        searchView?.apply {
            queryHint = getString(R.string.search_chats)
            setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = true

                override fun onQueryTextChange(newText: String?): Boolean {
                    searchDebounceJob?.cancel()
                    searchDebounceJob = lifecycleScope.launch {
                        delay(SEARCH_DEBOUNCE_MS)
                        val query = newText ?: ""
                        if (query.isEmpty()) {
                            viewModel.loadChats()
                        } else {
                            viewModel.searchChats(query)
                        }
                    }
                    return true
                }
            })
        }
    }

    // ======= Navigation =======

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

    private fun showAuthChoiceDialog() {
        var serverAddress = CredentialStore.getServerAddress(this)
        var host: String
        var port: Int
        var serverName: String

        if (serverAddress.isEmpty()) {
            // No saved server — try to get default from server list
            val defaultServer = CredentialStore.getDefaultServer(this)
            if (defaultServer != null) {
                serverAddress = "${defaultServer.host}:${defaultServer.port}"
                host = defaultServer.host
                port = defaultServer.port
                serverName = defaultServer.name
                // Save as current server so next time it's available
                CredentialStore.setServerAddress(this, serverAddress)
            } else {
                Log.w(TAG, "No server address and no default server — cannot show auth dialog")
                return
            }
        } else {
            val parts = serverAddress.split(":")
            host = parts[0]
            port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
            serverName = if (port == 50052) "Lava Germany dev" else "Lava Germany"
        }

        var isTransitioning = false
        lateinit var authSheet: ServerAuthBottomSheet

        authSheet = ServerAuthBottomSheet(
            context = this,
            serverName = serverName,
            serverHost = host,
            serverPort = port,
            onLogin = {
                isTransitioning = true
                authSheet.dismiss()
                showLoginBottomSheet(serverAddress)
            },
            onRegister = {
                isTransitioning = true
                authSheet.dismiss()
                showRegisterBottomSheet(serverAddress)
            }
        )
        authSheet.setOnDismissListener {
            // If user dismissed without logging in, re-show auth dialog
            if (!isTransitioning) {
                val uname = SessionManager.session.value.username
                val pwd = SessionManager.session.value.password
                if (uname.isEmpty() || pwd.isEmpty()) {
                    showAuthChoiceDialog()
                }
            }
        }
        authSheet.show()
    }

    private fun showLoginBottomSheet(serverAddress: String) {
        var isTransitioning = false

        lateinit var loginSheet: LoginBottomSheet

        loginSheet = LoginBottomSheet(
            context = this,
            onLogin = { u: String, p: String ->
                try {
                    startActivity(Intent(this, SplashLoadingActivity::class.java))
                } catch (_: Exception) {}

                SessionManager.login(this, u, p, serverAddress, register = false, email = "") { result ->
                    runOnUiThread {
                        SplashLoadingActivity.finishIfShowing()
                        when (result) {
                            "SUCCESS" -> {
                                CredentialStore.setCredentials(
                                    context = this@ChatListActivity,
                                    username = u,
                                    password = p,
                                    serverAddress = serverAddress
                                )
                                val userId = SessionManager.session.value.userId
                                if (userId.isNotEmpty()) {
                                    CredentialStore.setUserId(this@ChatListActivity, userId)
                                }
                                isTransitioning = true
                                loginSheet.dismiss()
                                recreate()
                            }
                            "USER_NOT_FOUND" -> {
                                loginSheet.setLoading(false)
                                Toast.makeText(this@ChatListActivity, R.string.user_not_found, Toast.LENGTH_LONG).show()
                            }
                            "AUTH_FAILED" -> {
                                loginSheet.setLoading(false)
                                Toast.makeText(this@ChatListActivity, R.string.wrong_password, Toast.LENGTH_LONG).show()
                            }
                            else -> {
                                loginSheet.setLoading(false)
                                Toast.makeText(this@ChatListActivity, R.string.connection_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            },
            onCancel = {
                isTransitioning = true
                loginSheet.dismiss()
                showAuthChoiceDialog()
            }
        )

        loginSheet.setOnDismissListener {
            if (!isTransitioning) {
                val uname = SessionManager.session.value.username
                val pwd = SessionManager.session.value.password
                if (uname.isEmpty() || pwd.isEmpty()) {
                    showAuthChoiceDialog()
                }
            }
        }

        // Pre-fill username from last login
        val lastUsername = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
            .getString("last_username", "") ?: ""
        if (lastUsername.isNotEmpty()) {
            loginSheet.prefillUsername(lastUsername)
        }

        loginSheet.show()
    }

    private fun showRegisterBottomSheet(serverAddress: String) {
        var isTransitioning = false

        lateinit var registerSheet: RegisterBottomSheet

        registerSheet = RegisterBottomSheet(
            context = this,
            onRegister = { u: String, p: String, email: String ->
                try {
                    startActivity(Intent(this, SplashLoadingActivity::class.java))
                } catch (_: Exception) {}

                SessionManager.login(this, u, p, serverAddress, register = true, email = email) { result ->
                    runOnUiThread {
                        SplashLoadingActivity.finishIfShowing()
                        when (result) {
                            "SUCCESS", "REGISTRATION_SUCCESS" -> {
                                CredentialStore.setCredentials(
                                    context = this@ChatListActivity,
                                    username = u,
                                    password = p,
                                    email = email,
                                    serverAddress = serverAddress
                                )
                                val userId = SessionManager.session.value.userId
                                if (userId.isNotEmpty()) {
                                    CredentialStore.setUserId(this@ChatListActivity, userId)
                                }
                                Toast.makeText(this@ChatListActivity, R.string.registration_success, Toast.LENGTH_LONG).show()
                                isTransitioning = true
                                registerSheet.dismiss()
                                recreate()
                            }
                            "USER_ALREADY_EXISTS" -> {
                                registerSheet.setLoading(false)
                                Toast.makeText(this, R.string.user_already_exists, Toast.LENGTH_LONG).show()
                            }
                            "EMAIL_ALREADY_IN_USE" -> {
                                registerSheet.setLoading(false)
                                Toast.makeText(this, R.string.email_already_in_use, Toast.LENGTH_LONG).show()
                            }
                            "AUTH_FAILED" -> {
                                registerSheet.setLoading(false)
                                Toast.makeText(this, R.string.auth_failed, Toast.LENGTH_LONG).show()
                            }
                            else -> {
                                registerSheet.setLoading(false)
                                Toast.makeText(this, R.string.connection_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            },
            onCancel = {
                isTransitioning = true
                registerSheet.dismiss()
                showAuthChoiceDialog()
            }
        )

        registerSheet.setOnDismissListener {
            if (!isTransitioning) {
                val uname = SessionManager.session.value.username
                val pwd = SessionManager.session.value.password
                if (uname.isEmpty() || pwd.isEmpty()) {
                    showAuthChoiceDialog()
                }
            }
        }

        registerSheet.show()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::chatAdapter.isInitialized && chatAdapter.isSelectionMode()) {
                    actionMode?.finish()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
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
