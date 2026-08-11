package lavender.client.android.ui.chatlist

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.R
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.session.SessionManager
import lavender.client.android.data.updates.UpdateManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.adapter.ChatAdapter
import lavender.client.android.ui.widget.AIBottomSheet
import kotlin.time.Duration.Companion.seconds

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
    internal var ivFavorites: ImageView? = null

    // ActionMode
    internal var isSelectionMode = false
    internal var searchView: androidx.appcompat.widget.SearchView? = null
    internal var searchDebounceJob: Job? = null

    // AI Bottom Sheet
    internal var aiBottomSheet: AIBottomSheet? = null
    internal var shouldReopenAIBottomSheet = false

    // Update
    internal var updateCoordinator: UpdateCoordinator? = null

    // Sheet navigation: re-open parent sheet after returning from activity
    internal var isNavigatingDeeper = false

    // Mark-as-read broadcast receiver (from notification action)
    private var markReadReceiver: BroadcastReceiver? = null
    internal val settingsActivityLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        if (isNavigatingDeeper) {
            isNavigatingDeeper = false
            showAdditionalSettingsSheet(this)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted && !shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.notifications)
                .setMessage(R.string.notification_permission_denied)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                    }
                    startActivity(intent)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
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
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

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
        registerMarkReadReceiver()
    }

    private fun registerMarkReadReceiver() {
        markReadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val roomId = intent.getStringExtra("room_id") ?: return
                Log.d(TAG, "MarkRead broadcast received: room=$roomId")
                viewModel.markAsRead(roomId)
            }
        }
        val filter = android.content.IntentFilter(lavender.client.android.data.fcm.NotificationMarkReadReceiver.ACTION_CHAT_MARKED_READ)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(markReadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(markReadReceiver, filter)
        }
    }

    override fun onDestroy() {
        markReadReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        super.onDestroy()
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

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Init views
        toolbar = findViewById(R.id.toolbar)
        tvToolbarTitle = findViewById(R.id.tvToolbarTitle)
        tvToolbarSubtitle = findViewById(R.id.tvToolbarSubtitle)
        ivToolbarUserAvatar = findViewById(R.id.ivToolbarUserAvatar)
        llToolbarTitleContainer = findViewById(R.id.llToolbarTitleContainer)
        ivFavorites = findViewById(R.id.ivFavorites)
        tabLayout = findViewById(R.id.tabLayout)
        swipeRefresh = findViewById(R.id.srlChatList)
        rvChatList = findViewById(R.id.rvChatList)

        // Favorites button
        ivFavorites?.setOnClickListener {
            val favoritesChat = ChatInfo(
                id = "favorites_$username",
                name = getString(R.string.favorites),
                type = "favorites",
                lastMessageText = "",
                lastMessageTime = 0L
            )
            navigateToChat(favoritesChat, username)
        }
        ivFavorites?.visibility = android.view.View.VISIBLE

        // Set title
        tvToolbarTitle?.text = getString(R.string.chats)

        // Toolbar styling: solid background with bottom rounded corners
        // Applied after ThemeApplier to layer on top of theme colors
        toolbar?.let { tb ->
            val bg = tb.background?.mutate()
            if (bg != null) {
                val color = when (bg) {
                    is android.graphics.drawable.GradientDrawable -> bg.color?.defaultColor ?: 0
                    else -> 0
                }
                if (color != 0) {
                    val shape = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        setColor(color)
                    }
                    tb.background = shape
                }
            }
            tb.elevation = 6f
        }

        // Make AppBarLayout transparent so toolbar transparency shows through
        findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)?.setBackgroundColor(Color.TRANSPARENT)

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
            var wasReady = GrpcClient.connectionStatus.value == ConnectionStatus.READY
            GrpcClient.connectionStatus.collect { status ->
                val chatsReady = viewModel.chatsLoaded.value
                val statusText = when (status) {
                    ConnectionStatus.CONNECTING -> getString(R.string.connecting)
                    ConnectionStatus.READY -> if (chatsReady) getString(R.string.connection_online) else getString(R.string.connecting)
                    ConnectionStatus.DISCONNECTED -> {
                        if (GrpcClient.serverShuttingDown.value) getString(R.string.server_restarting)
                        else if (CredentialStore.getServerAddress(this@ChatListActivity).isNotEmpty()) getString(R.string.connecting)
                        else getString(R.string.connection_offline)
                    }
                    ConnectionStatus.RECONNECTING -> if (GrpcClient.serverShuttingDown.value) getString(R.string.server_restarting) else getString(R.string.connecting)
                    ConnectionStatus.FAILED -> if (GrpcClient.serverShuttingDown.value) getString(R.string.server_restarting) else getString(R.string.connection_offline)
                }
                tvToolbarSubtitle?.text = statusText
                tvToolbarSubtitle?.isVisible = statusText.isNotEmpty()

                // Load chats when connection becomes READY (handles wake from doze)
                if (status == ConnectionStatus.READY && !wasReady) {
                    wasReady = true
                    if (::viewModel.isInitialized) {
                        viewModel.loadChats(silent = true)
                        GrpcClient.loadUsers()
                    }
                }
                if (status != ConnectionStatus.READY) wasReady = false
            }
        }

        // Update subtitle when chats finish loading (switch from "Connecting..." to "Online")
        lifecycleScope.launch {
            viewModel.chatsLoaded.collect { loaded ->
                if (loaded && GrpcClient.connectionStatus.value == ConnectionStatus.READY) {
                    tvToolbarSubtitle?.text = getString(R.string.connection_online)
                }
            }
        }

        // Periodic user refresh every 60s to keep lastSeenAt current
        lifecycleScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60.seconds)
                if (GrpcClient.connectionStatus.value == ConnectionStatus.READY) {
                    GrpcClient.loadUsers()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false

        ThemeStore.init(this)
        ThemeApplier.apply(this, ThemeStore.currentTheme())
        if (::chatAdapter.isInitialized) chatAdapter.updateTheme()
        // Register update prefs listener
        updateCoordinator?.let { coord ->
            getSharedPreferences("UpdatePrefs", MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(coord.prefsListener)
            coord.updateIndicatorVisibility()
        }

        // Switch ChatV2 stream to empty room — so server sends push notifications
        // and messages are not auto-marked as read when user is on chat list
        GrpcClient.startChatV2("") { /* ignore */ }

        // Token refresh on resume — handles long idle (doze, overnight)
        // Coroutine-based refresh to avoid blocking Main thread
        if (lavender.client.android.data.auth.AuthManager.isTokenExpiredOrExpiring(this)) {
            lifecycleScope.launch {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    SessionManager.ensureFreshToken(this@ChatListActivity)
                }
            }
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

        // Always refresh chat list on resume to pick up unread count changes
        if (::viewModel.isInitialized && GrpcClient.connectionStatus.value == ConnectionStatus.READY) {
            viewModel.loadChats(silent = true)
        }
        // Pre-load users for add contact/create chat sheets
        if (GrpcClient.connectionStatus.value == ConnectionStatus.READY) {
            GrpcClient.loadUsers()
        }

        // Re-open AI bottom sheet after returning from agent management
        if (shouldReopenAIBottomSheet) {
            shouldReopenAIBottomSheet = false
            showAIBottomSheet()
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
        viewModel = androidx.lifecycle.ViewModelProvider(this)[ChatListViewModel::class.java]

        // Observe force logout event (auth error with empty chat list)
        lifecycleScope.launch {
            viewModel.forceLogoutEvent.collect { error ->
                Log.w("ChatListActivity", "Force logout triggered: $error")
                Toast.makeText(this@ChatListActivity, R.string.session_expired, Toast.LENGTH_LONG).show()
                lavender.client.android.data.session.SessionManager.logout(this@ChatListActivity)
                finish()
                startActivity(Intent(this@ChatListActivity, lavender.client.android.SplashActivity::class.java))
            }
        }

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

        setupSwipeActions()

        // Observe sections
        lifecycleScope.launch {
            viewModel.sections.collectLatest { sections ->
                val layoutManager = rvChatList?.layoutManager as? LinearLayoutManager
                val firstVisible = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0
                val wasNearTop = firstVisible <= 1
                val previousItemCount = chatAdapter.itemCount
                chatAdapter.setSections(sections)
                if (wasNearTop) {
                    rvChatList?.scrollToPosition(0)
                } else if (chatAdapter.itemCount > 0) {
                    val targetPos = minOf(firstVisible, chatAdapter.itemCount - 1)
                    rvChatList?.scrollToPosition(targetPos)
                }
            }
        }

        // Loading state — no preloader needed (SwipeRefreshLayout disabled)

        // Observe online users for chat list status dots
        lifecycleScope.launch {
            GrpcClient.users.collectLatest { users ->
                chatAdapter.updateOnlineUsers(users)
            }
        }

        // Observe all users for last seen time
        lifecycleScope.launch {
            GrpcClient.allUsers.collectLatest { users ->
                chatAdapter.updateAllUsers(users)
            }
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh?.isEnabled = false
        swipeRefresh?.setOnRefreshListener {
            viewModel.refreshChats()
            if (GrpcClient.connectionStatus.value == ConnectionStatus.READY) {
                GrpcClient.loadUsers()
            }
        }

        rvChatList?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private var canTriggerUpdate = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                    canTriggerUpdate = layoutManager?.findFirstCompletelyVisibleItemPosition() == 0
                }
                if (newState == RecyclerView.SCROLL_STATE_IDLE && canTriggerUpdate) {
                    canTriggerUpdate = false
                    val dy = recyclerView.computeVerticalScrollOffset()
                    if (dy <= 0) {
                        updateCoordinator?.checkForUpdatesSilently()
                    }
                }
            }
        })
    }

    private fun setupSwipeActions() {
        val swipeCallback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun getSwipeDirs(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val position = viewHolder.bindingAdapterPosition
                val items = chatAdapter.currentList()
                if (position == RecyclerView.NO_POSITION || position >= items.size) return 0
                val item = items[position]
                if (item !is lavender.client.android.ui.adapter.FlatItem.ChatItem) return 0
                return super.getSwipeDirs(rv, viewHolder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val items = chatAdapter.currentList()
                if (position == RecyclerView.NO_POSITION || position >= items.size) return
                val item = items[position]
                if (item !is lavender.client.android.ui.adapter.FlatItem.ChatItem) return
                val chat = item.chat

                if (direction == androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
                    lifecycleScope.launch {
                        if (chat.isPinned) {
                            viewModel.unpinChat(chat.id)
                        } else {
                            viewModel.pinChat(chat.id)
                        }
                        chatAdapter.notifyItemChanged(position)
                    }
                    return
                }

                val options = mutableListOf<CharSequence>()
                val actions = mutableListOf<() -> Unit>()

                options.add(getString(R.string.archive))
                actions.add {
                    viewModel.archiveChat(chat.id)
                    Toast.makeText(this@ChatListActivity, getString(R.string.archived), Toast.LENGTH_SHORT).show()
                }

                options.add(if (chat.isMuted) getString(R.string.unmute) else getString(R.string.mute))
                actions.add {
                    viewModel.toggleMute(chat.id, !chat.isMuted)
                    val msg = if (chat.isMuted) getString(R.string.unmuted) else getString(R.string.muted)
                    Toast.makeText(this@ChatListActivity, msg, Toast.LENGTH_SHORT).show()
                }

                options.add(getString(R.string.delete))
                actions.add {
                    androidx.appcompat.app.AlertDialog.Builder(this@ChatListActivity)
                        .setTitle(R.string.delete_chat)
                        .setPositiveButton(R.string.delete) { _, _ ->
                            viewModel.deleteChat(chat.id) { error ->
                                lifecycleScope.launch {
                                    if (error != null) {
                                        Toast.makeText(this@ChatListActivity, getString(R.string.failed), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }

                androidx.appcompat.app.AlertDialog.Builder(this@ChatListActivity)
                    .setItems(options.toTypedArray()) { _, which ->
                        actions[which]()
                    }
                    .setOnCancelListener {
                        if (position != RecyclerView.NO_POSITION) {
                            chatAdapter.notifyItemChanged(position)
                        }
                    }
                    .show()
            }

            override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val itemView = viewHolder.itemView

                if (dX < 0) {
                    val deleteColor = ThemeUtils.parseSafeColor(ThemeStore.currentTheme().primaryColor, android.graphics.Color.RED)
                    val bg = android.graphics.drawable.ColorDrawable(deleteColor)
                    bg.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                    bg.draw(c)

                    val trashIcon = androidx.core.content.ContextCompat.getDrawable(this@ChatListActivity, android.R.drawable.ic_menu_delete)
                    trashIcon?.mutate()?.setTint(android.graphics.Color.WHITE)
                    val iconMargin = (itemView.height - (trashIcon?.intrinsicHeight ?: 0)) / 2
                    val iconTop = itemView.top + iconMargin
                    val iconBottom = iconTop + (trashIcon?.intrinsicHeight ?: 0)
                    val iconLeft = itemView.right - iconMargin - (trashIcon?.intrinsicWidth ?: 0)
                    val iconRight = itemView.right - iconMargin
                    trashIcon?.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    trashIcon?.draw(c)
                } else if (dX > 0) {
                    val pinColor = android.graphics.Color.parseColor("#4CAF50")
                    val bg = android.graphics.drawable.ColorDrawable(pinColor)
                    bg.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                    bg.draw(c)

                    val pinIcon = androidx.core.content.ContextCompat.getDrawable(this@ChatListActivity, R.drawable.ic_pin)
                    pinIcon?.mutate()?.setTint(android.graphics.Color.WHITE)
                    val iconMargin = (itemView.height - (pinIcon?.intrinsicHeight ?: 0)) / 2
                    val iconTop = itemView.top + iconMargin
                    val iconBottom = iconTop + (pinIcon?.intrinsicHeight ?: 0)
                    val iconLeft = itemView.left + iconMargin
                    val iconRight = iconLeft + (pinIcon?.intrinsicWidth ?: 0)
                    pinIcon?.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    pinIcon?.draw(c)
                }

                if (dX == 0f) {
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                }
            }
        }
        androidx.recyclerview.widget.ItemTouchHelper(swipeCallback).attachToRecyclerView(rvChatList)
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
        ThemeApplier.apply(this, ThemeStore.currentTheme())
    }
}
