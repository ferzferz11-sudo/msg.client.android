package lavender.client.android.ui.chatlist

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.session.SessionManager
import lavender.client.android.data.updates.UpdateManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.widget.AIBottomSheet
import lavender.client.android.ui.adapter.ChatAdapter

/**
 * ChatListActivity — единый Activity для списка чатов.
 *
 * Работает на v1 и v2 серверах:
 * - v2: полный функционал (Pin, Archive, Search, Tabs)
 * - v1: базовый функционал (список чатов, Favorites, AI)
 *
 * Никакого fallback на отдельный Activity — всё в одном месте.
 *
 * Модули (вынесены в отдельные файлы того же пакета):
 * - ChatListToolbar.kt — toolbar + settings sheets
 * - ChatListTabs.kt — tabs
 * - ChatListActionMode.kt — selection mode
 * - ChatListSearch.kt — search
 * - ChatListFABs.kt — FABs + action sheets + AI bottom sheet
 * - ChatListNavigation.kt — navigateToChat
 * - ChatListAuth.kt — auth dialogs (ServerAuth, Login, Register)
 */
class ChatListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatListActivity"
        internal const val SEARCH_DEBOUNCE_MS = 300L
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val lang = prefs.getString("language", "ru") ?: "ru"
        val locale = java.util.Locale.forLanguageTag(lang)
        java.util.Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    internal lateinit var viewModel: ChatListViewModel
    internal lateinit var chatAdapter: ChatAdapter
    internal var swipeRefresh: SwipeRefreshLayout? = null
    internal var rvChatList: RecyclerView? = null
    internal var tabLayout: TabLayout? = null
    internal var toolbar: MaterialToolbar? = null
    internal var tvToolbarTitle: TextView? = null
    internal var tvToolbarSubtitle: TextView? = null
    internal var ivToolbarUserAvatar: ImageView? = null
    internal var llToolbarTitleContainer: android.widget.LinearLayout? = null

    // ActionMode
    internal var isSelectionMode = false
    internal var searchView: androidx.appcompat.widget.SearchView? = null
    internal var searchDebounceJob: Job? = null

    // AI Bottom Sheet
    internal var aiBottomSheet: AIBottomSheet? = null

    // Update
    internal var updateCoordinator: UpdateCoordinator? = null

    // Sheet navigation: re-open parent sheet after returning from activity
    internal var isNavigatingDeeper = false
    internal val settingsActivityLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        if (isNavigatingDeeper) {
            isNavigatingDeeper = false
            showAdditionalSettingsSheet(this)
        }
    }
    internal val editProfileLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        if (isNavigatingDeeper) {
            isNavigatingDeeper = false
            showSettingsSheet(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SessionManager.initFromPrefs(this)
        applyTheme()

        val serverAddress = CredentialStore.getServerAddress(this) ?: ""

        if (serverAddress.isEmpty()) {
            Log.w(TAG, "No server address — showing auth dialog")
            setContentView(R.layout.activity_chat_list)
            showAuthChoiceDialog(this)
            return
        }

        setupUI()
    }

    private fun setupUI() {
        setContentView(R.layout.activity_chat_list)

        val username = SessionManager.session.value.username
        val password = SessionManager.session.value.password

        if (username.isEmpty() || password.isEmpty()) {
            showAuthChoiceDialog(this)
            return
        }

        ThemeUi.bind(this, username)

        // Init views
        toolbar = findViewById(R.id.toolbar)
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle)
        tvToolbarSubtitle = findViewById(R.id.tvToolbarSubtitle)
        ivToolbarUserAvatar = findViewById(R.id.ivToolbarUserAvatar)
        llToolbarTitleContainer = findViewById(R.id.llToolbarTitleContainer)
        tabLayout = findViewById(R.id.tabLayout)
        swipeRefresh = findViewById(R.id.srlChatList)
        rvChatList = findViewById(R.id.rvChatList)

        // Set title
        tvToolbarTitle?.text = getString(R.string.chats)

        // Toolbar styling: 30% transparency + bottom shadow
        // Applied after ThemeApplier to layer on top of theme colors
        toolbar?.let { tb ->
            val bg = tb.background?.mutate()
            if (bg != null) {
                val color = when (bg) {
                    is android.graphics.drawable.GradientDrawable -> bg.color?.defaultColor ?: 0
                    else -> 0
                }
                val alpha = (0.8f * 255).toInt() // 80% opacity = 30% transparent
                if (color != 0) {
                    val shape = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        setColor(android.graphics.Color.argb(alpha, android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color)))
                    }
                    tb.background = shape
                } else {
                    bg.alpha = alpha
                    tb.background = bg
                }
            }
            tb.elevation = 6f
        }

        // Make AppBarLayout transparent so toolbar transparency shows through
        findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)?.let { appBar ->
            appBar.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        // Setup toolbar actions
        setupToolbarActions(this, username)

        // Setup search menu in toolbar
        setupSearchMenu(this)

        // Setup tabs
        setupTabs(this)

        // Setup RecyclerView
        setupRecyclerView(username)

        // Setup SwipeRefresh
        setupSwipeRefresh()

        // Setup FABs
        setupFABs(this)

        // Register back press handler for selection mode
        setupBackPressHandler()

        // Initialize UpdateCoordinator and observe download state
        val updateManager = UpdateManager(this)
        updateCoordinator = UpdateCoordinator(this, updateManager)

        lifecycleScope.launch {
            updateManager.isDownloadingInstance.collect { downloading ->
                updateCoordinator?.updateIndicatorVisibility()
            }
        }
        lifecycleScope.launch {
            updateManager.downloadProgressInstance.collect { progress ->
                val tvProgress = findViewById<TextView>(R.id.tvUpdateProgress)
                tvProgress?.text = getString(R.string.percent_format, progress)
                tvProgress?.isVisible = progress > 0
            }
        }
        lifecycleScope.launch {
            updateManager.isDownloadedInstance.collect { downloaded ->
                updateCoordinator?.updateIndicatorVisibility()
            }
        }
        // Silent update check on startup
        updateCoordinator?.checkForUpdatesSilently()

        // Note: GrpcClient.connect() is already called from SessionManager.initFromPrefs()
        // when serverAddress is set. No need to connect again here.

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
                    ConnectionStatus.CONNECTING -> getString(R.string.connecting)
                    ConnectionStatus.READY -> getString(R.string.connection_online)
                    ConnectionStatus.DISCONNECTED -> {
                        if (CredentialStore.getServerAddress(this@ChatListActivity).isNotEmpty()) getString(R.string.connecting)
                        else getString(R.string.connection_offline)
                    }
                    ConnectionStatus.RECONNECTING -> if (GrpcClient.serverShuttingDown.value) getString(R.string.server_restarting) else getString(R.string.connecting)
                    ConnectionStatus.FAILED -> getString(R.string.connection_offline)
                }
                tvToolbarSubtitle?.text = statusText
                tvToolbarSubtitle?.isVisible = statusText.isNotEmpty()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false
        // Register update prefs listener
        updateCoordinator?.let { coord ->
            getSharedPreferences("UpdatePrefs", MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(coord.prefsListener)
            coord.updateIndicatorVisibility()
        }

        // Channel health check on resume from background
        val currentStatus = GrpcClient.connectionStatus.value
        if (currentStatus != ConnectionStatus.READY) {
            val serverAddr = CredentialStore.getServerAddress(this) ?: ""
            if (serverAddr.isNotEmpty()) {
                val parts = serverAddr.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
                Log.d(TAG, "onResume: status=$currentStatus, forcing reconnect to $host:$port")
                GrpcClient.connect(host, useTls = false, port = port, context = this, forceReconnect = true)
            }
        }

        // Safety net: if chats list is empty but we're connected, reload.
        if (::viewModel.isInitialized && viewModel.getChats().isEmpty()
            && GrpcClient.connectionStatus.value == ConnectionStatus.READY
        ) {
            Log.d(TAG, "onResume: chats empty but READY — reloading")
            viewModel.loadChats()
        }
        // Pre-load users for add contact/create chat sheets
        if (GrpcClient.connectionStatus.value == ConnectionStatus.READY) {
            GrpcClient.loadUsers()
        }
    }

    override fun onPause() {
        super.onPause()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = true
        updateCoordinator?.let { coord ->
            getSharedPreferences("UpdatePrefs", MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(coord.prefsListener)
        }
    }

    // ======= Proxy methods to modules =======

    // Toolbar
    private fun showSettingsSheet() = showSettingsSheet(this)
    private fun showAdditionalSettingsSheet() = showAdditionalSettingsSheet(this)
    private fun confirmDeleteProfile() = confirmDeleteProfile(this)
    private fun showAboutDialog() = showAboutDialog(this)
    private fun shareApp() = shareApp(this)
    private fun toggleLanguage() = toggleLanguage(this)

    // Tabs
    private fun setupTabs() = setupTabs(this)

    // FABs
    private fun setupFABs() = setupFABs(this)
    private fun showChatActionSheet() = showChatActionSheet(this)
    internal fun showAddContactDialog() = showAddContactDialog(this)
    private fun showCreateChatDialog() = showCreateChatDialog(this)
    private fun showCreateSecretChatDialog() = showCreateSecretChatDialog(this)
    private fun showCreateConferenceDialog() = showCreateConferenceDialog(this)
    private fun showAIBottomSheet() = showAIBottomSheet(this)

    // Navigation
    internal fun navigateToChat(chat: ChatInfo, username: String) = navigateToChat(this, chat, username)

    // Auth
    private fun showAuthChoiceDialog() = showAuthChoiceDialog(this)
    private fun showLoginBottomSheet(serverAddress: String) = showLoginBottomSheet(this, serverAddress)
    private fun showRegisterBottomSheet(serverAddress: String) = showRegisterBottomSheet(this, serverAddress)

    // ActionMode

    private fun setupRecyclerView(username: String) {
        viewModel = ChatListViewModel(application)

        chatAdapter = ChatAdapter(
            scope = lifecycleScope,
            currentUsername = username,
            onChatClick = { chat ->
                if (chatAdapter.isSelectionMode()) {
                    chatAdapter.toggleSelection(chat.id)
                    updateActionModeTitle(this@ChatListActivity)
                    if (chatAdapter.getSelectedIds().isEmpty()) {
                        exitSelectionMode(this@ChatListActivity)
                    }
                } else {
                    if (chat.unreadCount > 0) viewModel.markAsRead(chat.id)
                    navigateToChat(chat, username)
                }
            },
            onChatLongClick = { chat, anchorView ->
                if (!chatAdapter.isSelectionMode()) {
                    chatAdapter.setSelectionMode(true)
                    chatAdapter.toggleSelection(chat.id)
                    enterSelectionMode(this@ChatListActivity)
                    updateActionModeTitle(this@ChatListActivity)
                }
            },
            onSelectionChanged = { count ->
                updateActionModeTitle(this@ChatListActivity)
                if (count == 0 && isSelectionMode) {
                    exitSelectionMode(this@ChatListActivity)
                }
            }
        )

        rvChatList?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = chatAdapter
            setHasFixedSize(false)
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0) {
                        val layoutManager = rv.layoutManager as LinearLayoutManager
                        val lastVisible = layoutManager.findLastVisibleItemPosition()
                        val totalItems = layoutManager.itemCount
                        if (lastVisible >= totalItems - 5) {
                            viewModel.loadMoreChats()
                        }
                    }
                }
            })
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
    }

    private fun setupSwipeRefresh() {
        swipeRefresh?.setOnRefreshListener {
            viewModel.refreshChats()
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::chatAdapter.isInitialized && chatAdapter.isSelectionMode()) {
                    exitSelectionMode(this@ChatListActivity)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun applyTheme() {
        ThemeStore.init(this)
        val prefs = getSharedPreferences("ThemePrefs", MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )
        ThemeApplier.apply(this, ThemeStore.currentTheme())
    }
}
