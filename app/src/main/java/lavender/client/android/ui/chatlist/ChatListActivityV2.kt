package lavender.client.android.ui.chatlist

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.view.ActionMode
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import lavender.client.android.NewChatActivity
import lavender.client.android.R
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
import lavender.client.android.ui.widget.ServerAuthBottomSheet

/**
 * ChatListActivityV2 — Activity для v2 серверов (ChatList v2 API).
 *
 * Features:
 * - Selection Mode: long press = start ActionMode, tap = toggle selection
 * - Search: SearchView in toolbar with 300ms debounce
 * - Tab filter: All / AI / Groups
 * - v1 fallback: auto-redirect to ChatListActivity if server doesn't support v2
 *
 * Extends ChatListBaseActivity for common functionality (theme, auth, navigation).
 */
class ChatListActivityV2 : ChatListBaseActivity() {

    companion object {
        private const val TAG = "ChatListActivityV2"
        private const val SEARCH_DEBOUNCE_MS = 300L
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
    private var ivActionSettings: ImageView? = null
    private var aiBottomSheet: AIBottomSheet? = null
    private var actionMode: ActionMode? = null
    private var searchJob: Job? = null
    private var searchMenuItem: MenuItem? = null

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.chat_list_action_mode, menu)
            chatAdapter.setSelectionMode(true)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val selectedIds = chatAdapter.getSelectedIds()
            when (item.itemId) {
                R.id.action_pin -> {
                    selectedIds.forEach { viewModel.pinChat(it) }
                    mode.finish()
                    return true
                }
                R.id.action_mute -> {
                    selectedIds.forEach { viewModel.toggleMute(it, true) }
                    mode.finish()
                    return true
                }
                R.id.action_archive -> {
                    selectedIds.forEach { viewModel.archiveChat(it) }
                    mode.finish()
                    return true
                }
                R.id.action_delete -> {
                    mode.finish()
                    return true
                }
            }
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            chatAdapter.setSelectionMode(false)
            actionMode = null
        }
    }

    // ======== Lifecycle ========

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeUi.bind(this, currentUsername)

        if (currentUsername.isEmpty() || currentPassword.isEmpty()) {
            showAuthChoiceDialog()
            return
        }

        setupV2UI()
    }

    override fun onResume() {
        super.onResume()
        // Safety net: if chats list is empty but we're connected, reload.
        if (::viewModel.isInitialized && viewModel.getChats().isEmpty()
            && GrpcClient.connectionStatus.value == ConnectionStatus.READY
        ) {
            Log.d(TAG, "onResume: chats empty but READY — reloading")
            viewModel.loadChats()
        }
    }

    // ======= Setup ========

    private fun setupV2UI() {
        setContentView(R.layout.activity_chat_list_v2)

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
        setupToolbarActions(currentUsername)

        // Setup search menu in toolbar
        setupSearchMenu()

        // Setup tabs
        setupTabs()

        // Setup RecyclerView
        setupRecyclerView(currentUsername)

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

        // Observe connection status
        lifecycleScope.launch {
            GrpcClient.connectionStatus.collect { status ->
                val statusText = when (status) {
                    ConnectionStatus.CONNECTING -> getString(R.string.connecting)
                    ConnectionStatus.READY -> getString(R.string.connection_online)
                    ConnectionStatus.DISCONNECTED -> getString(R.string.connection_offline)
                    else -> ""
                }
                tvToolbarSubtitle?.text = statusText
                tvToolbarSubtitle?.isVisible = statusText.isNotEmpty()
            }
        }
    }

    private fun setupToolbarActions(username: String) {
        ivToolbarUserAvatar?.setOnClickListener {
            val intent = Intent(this, lavender.client.android.ProfileActivity::class.java)
            startActivity(intent)
        }
        tvToolbarTitle?.setOnClickListener {
            val intent = Intent(this, ServersActivity::class.java)
            startActivity(intent)
        }
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
            onChatClick = { chat ->
                if (chatAdapter.isSelectionMode()) {
                    chatAdapter.toggleSelection(chat.id)
                    updateActionModeTitle()
                    if (chatAdapter.getSelectedIds().isEmpty()) {
                        actionMode?.finish()
                    }
                } else {
                    if (chat.unreadCount > 0) {
                        viewModel.markAsRead(chat.id)
                    }
                    navigateToChat(chat, username)
                }
            },
            onChatLongClick = { chat, anchorView ->
                if (!chatAdapter.isSelectionMode()) {
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

        // Observe new messages for non-active chats — update unread badges in real-time
        lifecycleScope.launch {
            GrpcClient.newMessageEvent.collect { (roomId, _) ->
                if (roomId.isNotEmpty() && roomId != GrpcClient.currentRoomId) {
                    viewModel.incrementUnreadCount(roomId)
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
        findViewById<View>(R.id.fabAddChat)?.setOnClickListener {
            val intent = Intent(this, NewChatActivity::class.java)
            intent.putExtra("USERNAME", currentUsername)
            startActivity(intent)
        }
        findViewById<View>(R.id.fabAi)?.setOnClickListener {
            showAIBottomSheet()
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
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

    private fun setupSearchMenu() {
        toolbar?.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_search -> {
                    searchMenuItem = menuItem
                    true
                }
                else -> false
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chat_list_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                // Search handled via SearchView in toolbar
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun onMenuItemActionExpand(item: MenuItem): Boolean = true

    fun onMenuItemActionCollapse(item: MenuItem): Boolean {
        searchJob?.cancel()
        viewModel.searchChats("")
        return true
    }

    fun onQueryTextChange(newText: String): Boolean {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            viewModel.searchChats(newText)
        }
        return true
    }

    fun onQueryTextSubmit(query: String): Boolean {
        searchJob?.cancel()
        viewModel.searchChats(query)
        return true
    }

    // ======== UI Actions ========

    private fun showAIBottomSheet() {
        val chats = if (::viewModel.isInitialized) viewModel.getChats() else emptyList()
        val aiChats = chats.filter { it.type == "owl" || it.type == "hermes" }
            .map { AIChatInfo(id = it.id, name = it.name, type = it.type) }

        aiBottomSheet = AIBottomSheet(
            context = this,
            existingChats = aiChats.toMutableList(),
            onCreateOwlChat = {
                navigateToOwl(ChatInfo(id = "", name = "OWL", type = "owl"))
            },
            onCreateHermesChat = {
                navigateToHermes(ChatInfo(id = "", name = "Hermes", type = "hermes"))
            },
            onChatClick = { chat -> navigateToChat(chat, currentUsername) },
            onSettingsClick = { chat ->
                if (chat.type == "hermes") openHermesSettings() else openOwlSettings()
            }
        )
        aiBottomSheet?.buildAndShow()
    }

    override fun navigateToRegularChat(chat: ChatInfo, username: String) {
        val serverAddress = CredentialStore.getServerAddress(this) ?: ""
        startActivity(Intent(this, NewChatActivity::class.java).apply {
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
        })
    }

    // ======== Selection Mode ========

    private fun updateActionModeTitle() {
        val count = chatAdapter.getSelectedIds().size
        actionMode?.title = getString(R.string.selected_count, count)
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

    private fun fallbackToV1() {
        startActivity(Intent(this, lavender.client.android.ChatListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
