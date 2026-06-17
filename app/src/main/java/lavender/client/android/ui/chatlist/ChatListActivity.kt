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
import androidx.appcompat.app.AlertDialog
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import lavender.client.android.NewChatActivity
import lavender.client.android.R
import lavender.client.android.SplashLoadingActivity
import lavender.client.android.ServersActivity
import lavender.client.android.ThemesActivity
import lavender.client.android.ContactsActivity
import lavender.client.android.EditProfileActivity
import lavender.client.android.NotificationActivity
import lavender.client.android.SecurityActivity
import lavender.client.android.SuperAdminActivity
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.ProfileClient
import lavender.client.android.data.models.AIChatInfo
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.session.SessionManager
import lavender.client.android.data.cache.CacheUtils
import lavender.client.android.data.updates.UpdateManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.widget.AIBottomSheet
import lavender.client.android.ui.widget.ActionBottomSheet
import lavender.client.android.ui.widget.SearchableListBottomSheet
import lavender.client.android.ui.widget.LoginBottomSheet
import lavender.client.android.ui.widget.RegisterBottomSheet
import lavender.client.android.ui.widget.ServerAuthBottomSheet
import lavender.client.android.ui.widget.NewChatBottomSheet
import lavender.client.android.ui.widget.StandardBottomSheet
import lavender.client.android.ui.widget.SheetAction
import lavender.client.android.ui.adapter.ChatAdapter
import lavender.client.android.ui.adapter.UserAdapter
import lavender.client.android.ui.LogViewerActivity
import lavender.client.android.data.crypto.E2EEManager
import lavender.client.android.data.proto.UserInfoProto
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    internal lateinit var viewModel: ChatListViewModel
    internal lateinit var chatAdapter: ChatAdapter
    internal var swipeRefresh: SwipeRefreshLayout? = null
    internal var rvChatList: RecyclerView? = null
    internal var tabLayout: TabLayout? = null
    internal var toolbar: MaterialToolbar? = null
    internal var tvToolbarTitle: TextView? = null
    internal var tvToolbarSubtitle: TextView? = null
    internal var ivToolbarUserAvatar: ImageView? = null

    // ActionMode
    internal var actionMode: ActionMode? = null
    internal var searchView: androidx.appcompat.widget.SearchView? = null
    internal var searchDebounceJob: Job? = null

    // AI Bottom Sheet
    internal var aiBottomSheet: AIBottomSheet? = null
    internal val aiChats = mutableListOf<AIChatInfo>()

    // Update
    internal var updateCoordinator: UpdateCoordinator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SessionManager.initFromPrefs(this)
        applyTheme()

        val serverAddress = CredentialStore.getServerAddress(this) ?: ""

        if (serverAddress.isEmpty()) {
            Log.w(TAG, "No server address — showing auth dialog")
            setContentView(R.layout.activity_chat_list)
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
        // Register update prefs listener
        updateCoordinator?.let { coord ->
            getSharedPreferences("UpdatePrefs", MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(coord.prefsListener)
            coord.updateIndicatorVisibility()
        }
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

    override fun onPause() {
        super.onPause()
        updateCoordinator?.let { coord ->
            getSharedPreferences("UpdatePrefs", MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(coord.prefsListener)
        }
    }

    private fun setupToolbarActions(username: String) = lavender.client.android.ui.chatlist.setupToolbarActions(this, username)
    private fun showSettingsSheet() = lavender.client.android.ui.chatlist.showSettingsSheet(this)
    private fun showAdditionalSettingsSheet() = lavender.client.android.ui.chatlist.showAdditionalSettingsSheet(this)
    private fun confirmDeleteProfile() = lavender.client.android.ui.chatlist.confirmDeleteProfile(this)
    private fun showAboutDialog() = lavender.client.android.ui.chatlist.showAboutDialog(this)
    private fun shareApp() = lavender.client.android.ui.chatlist.shareApp(this)
    private fun toggleLanguage() = lavender.client.android.ui.chatlist.toggleLanguage(this)

    private fun setupTabs() = setupTabs(this)
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
            showChatActionSheet()
        }
    }

    // ======= FAB [+] Action Sheet =======

    private fun showChatActionSheet() {
        ActionBottomSheet(this)
            .setActions(listOf(
                SheetAction(R.id.actionAddContact, R.drawable.ic_contacts, getString(R.string.add_contact)) {
                    showAddContactDialog()
                },
                SheetAction(R.id.actionCreateChat, R.drawable.ic_add, getString(R.string.start_chat)) {
                    showCreateChatDialog()
                },
                SheetAction(R.id.actionCreateSecretChat, R.drawable.ic_lock, getString(R.string.secret_chat)) {
                    showCreateSecretChatDialog()
                },
                SheetAction(R.id.actionCreateConference, R.drawable.ic_videocam_on, getString(R.string.conference_in_development)) {
                    showCreateConferenceDialog()
                }
            )).show()
    }

    // ======= Add Contact Dialog =======

    private fun showAddContactDialog() {
        val username = SessionManager.session.value.username
        val sheet = SearchableListBottomSheet(this)
            .setTitle(getString(R.string.add_contact))
            .setActionButtonText(getString(R.string.add))
            .setExtraInputVisible(false)
            .setLoading(true)
            .setCreateChatCheckboxVisible(true, getString(R.string.create_direct_chat_after))

        val currentContacts = mutableSetOf<String>()

        val userAdapter = UserAdapter(
            scope = lifecycleScope,
            onUserClick = { selected ->
                (sheet.recyclerView?.adapter as? UserAdapter)?.toggleSelection(selected)
            },
            onSelectionChanged = { count ->
                sheet.setActionButtonEnabled(count > 0)
                sheet.setActionButtonText(if (count > 0) "${getString(R.string.add)} ($count)" else getString(R.string.add))
            },
            avatarCache = GrpcClient.getAvatarCache(),
            onlineUsers = GrpcClient.users.value
        )

        sheet.setAdapter(userAdapter)

        // Load contacts first, then filter all users
        GrpcClient.getContacts(username) { contacts ->
            currentContacts.clear()
            currentContacts.addAll(contacts)

            lifecycleScope.launch {
                GrpcClient.allUsers.collect { allUsersList ->
                    val filtered = allUsersList
                        .map { it.username }
                        .filter { it != username && !currentContacts.contains(it) }
                    runOnUiThread {
                        sheet.setLoading(false)
                        userAdapter.setUsers(filtered)
                    }
                }
            }
        }

        sheet.onSearchTextChanged { query ->
            userAdapter.filter(query)
        }

        sheet.onActionClick {
            val selected = userAdapter.getSelectedUsers()
            if (selected.isEmpty()) return@onActionClick

            var added = 0
            val total = selected.size
            for (contact in selected) {
                GrpcClient.addContact(username, contact) { success, _ ->
                    if (success) added++
                    if (added == total || (added + (total - selected.indexOf(contact) - 1)) == total) {
                        runOnUiThread {
                            sheet.dismiss()
                            // Optionally create direct chat with first added contact
                            if (sheet.isCreateChatChecked() && selected.isNotEmpty()) {
                                val firstContact = selected.first()
                                GrpcClient.createDirectChat(username, firstContact) { chatId ->
                                    if (chatId != null) {
                                        runOnUiThread {
                                            val intent = Intent(this, NewChatActivity::class.java).apply {
                                                putExtra("USERNAME", username)
                                                putExtra("ROOM_ID", chatId)
                                                putExtra("CHAT_NAME", firstContact)
                                                putExtra("IS_DIRECT", true)
                                                putExtra("PARTICIPANTS", JSONArray(listOf(username, firstContact)).toString())
                                            }
                                            startActivity(intent)
                                        }
                                    }
                                }
                            }
                            Toast.makeText(this, getString(R.string.contacts_added, added), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        sheet.show()
    }

    // ======= Create Chat Dialog =======

    private fun showCreateChatDialog() {
        val username = SessionManager.session.value.username
        val sheet = SearchableListBottomSheet(this)
            .setTitle(getString(R.string.start_chat))
            .setActionButtonText(getString(R.string.create))
            .setExtraInputVisible(false, getString(R.string.enter_group_name))
            .setLoading(true)

        val userAdapter = UserAdapter(
            scope = lifecycleScope,
            onUserClick = { selected ->
                (sheet.recyclerView?.adapter as? UserAdapter)?.toggleSelection(selected)
            },
            onSelectionChanged = { count ->
                sheet.setActionButtonEnabled(count > 0)
                sheet.setActionButtonText(if (count > 1) "${getString(R.string.create)} ($count)" else getString(R.string.create))
                sheet.setExtraInputVisible(count > 1, getString(R.string.enter_group_name))
            },
            avatarCache = GrpcClient.getAvatarCache(),
            onlineUsers = GrpcClient.users.value
        )

        sheet.setAdapter(userAdapter)

        GrpcClient.getContacts(username) { contacts ->
            runOnUiThread {
                sheet.setLoading(false)
                userAdapter.setUsers(contacts)
            }
        }

        sheet.onSearchTextChanged { query ->
            userAdapter.filter(query)
        }

        sheet.onActionClick {
            val selected = userAdapter.getSelectedUsers()
            if (selected.isEmpty()) return@onActionClick

            if (selected.size == 1) {
                // Direct chat
                val targetUser = selected.first()
                GrpcClient.createDirectChat(username, targetUser) { chatId ->
                    if (chatId != null) {
                        runOnUiThread {
                            sheet.dismiss()
                            val intent = Intent(this, NewChatActivity::class.java).apply {
                                putExtra("USERNAME", username)
                                putExtra("ROOM_ID", chatId)
                                putExtra("CHAT_NAME", targetUser)
                                putExtra("IS_DIRECT", true)
                                putExtra("PARTICIPANTS", JSONArray(listOf(username, targetUser)).toString())
                            }
                            startActivity(intent)
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, R.string.failed_to_create_chat, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                // Group chat
                val groupName = sheet.extraEditText?.text?.toString()?.trim()?.ifEmpty {
                    getString(R.string.default_group_name)
                } ?: getString(R.string.default_group_name)
                val participants = selected + username
                GrpcClient.createGroupChat(groupName, participants, username) { chatId ->
                    if (chatId != null) {
                        runOnUiThread {
                            sheet.dismiss()
                            val intent = Intent(this, NewChatActivity::class.java).apply {
                                putExtra("USERNAME", username)
                                putExtra("ROOM_ID", chatId)
                                putExtra("CHAT_NAME", groupName)
                                putExtra("IS_DIRECT", false)
                                putExtra("PARTICIPANTS", JSONArray(participants).toString())
                                putExtra("CREATOR", username)
                            }
                            startActivity(intent)
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, R.string.failed_to_create_chat, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        sheet.show()
    }

    // ======= Create Secret Chat Dialog =======

    private fun showCreateSecretChatDialog() {
        val username = SessionManager.session.value.username
        val sheet = SearchableListBottomSheet(this)
            .setTitle(getString(R.string.secret_chat))
            .setActionButtonText(getString(R.string.create))
            .setLoading(true)

        val userAdapter = UserAdapter(
            scope = lifecycleScope,
            onUserClick = { selected ->
                val clickAdapter = sheet.recyclerView?.adapter as? UserAdapter
                clickAdapter?.let {
                    it.clearSelection()
                    it.toggleSelection(selected)
                }
            },
            onSelectionChanged = { count ->
                sheet.setActionButtonEnabled(count == 1)
            },
            avatarCache = GrpcClient.getAvatarCache(),
            onlineUsers = GrpcClient.users.value
        )

        sheet.setAdapter(userAdapter)

        GrpcClient.getContacts(username) { contacts ->
            runOnUiThread {
                sheet.setLoading(false)
                userAdapter.setUsers(contacts)
            }
        }

        sheet.onSearchTextChanged { query ->
            userAdapter.filter(query)
        }

        sheet.onActionClick {
            val selected = userAdapter.getSelectedUsers()
            if (selected.isEmpty()) return@onActionClick
            val targetUser = selected.first()

            sheet.setLoading(true)

            // Generate E2EE key pair and create secret chat
            val publicKey = E2EEManager.getPublicKeyBase64(this@ChatListActivity)

            GrpcClient.createSecretChat(targetUser, publicKey) { chatId, success, message, _ ->
                runOnUiThread {
                    sheet.setLoading(false)
                    if (success && chatId.isNotEmpty()) {
                        sheet.dismiss()
                        val intent = Intent(this@ChatListActivity, NewChatActivity::class.java).apply {
                            putExtra("USERNAME", username)
                            putExtra("ROOM_ID", chatId)
                            putExtra("CHAT_NAME", "🔒 $targetUser")
                            putExtra("CHAT_TYPE", "secret")
                            putExtra("IS_DIRECT", true)
                            putExtra("PARTICIPANTS", JSONArray(listOf(username, targetUser)).toString())
                            putExtra("IS_SECRET", "true")
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@ChatListActivity, message.ifEmpty { "Failed to create secret chat" }, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        sheet.show()
    }

    // ======= Create Conference Dialog =======

    private fun showCreateConferenceDialog() {
        val username = SessionManager.session.value.username
        val sheet = SearchableListBottomSheet(this)
            .setTitle(getString(R.string.conference))
            .setActionButtonText(getString(R.string.create))
            .setExtraInputVisible(true, getString(R.string.edit_topic))
            .setLoading(true)

        val userAdapter = UserAdapter(
            scope = lifecycleScope,
            onUserClick = { selected ->
                (sheet.recyclerView?.adapter as? UserAdapter)?.toggleSelection(selected)
            },
            onSelectionChanged = { count ->
                sheet.setActionButtonEnabled(count > 0)
                sheet.setActionButtonText(if (count > 0) "${getString(R.string.create)} ($count)" else getString(R.string.create))
            },
            avatarCache = GrpcClient.getAvatarCache(),
            onlineUsers = GrpcClient.users.value
        )

        sheet.setAdapter(userAdapter)

        GrpcClient.getContacts(username) { contacts ->
            runOnUiThread {
                sheet.setLoading(false)
                userAdapter.setUsers(contacts)
            }
        }

        sheet.onSearchTextChanged { query ->
            userAdapter.filter(query)
        }

        sheet.onActionClick {
            val selected = userAdapter.getSelectedUsers()
            if (selected.isEmpty()) return@onActionClick

            val topic = sheet.extraEditText?.text?.toString()?.trim()?.ifEmpty {
                val sdf = SimpleDateFormat("dd.MM", Locale.getDefault())
                getString(R.string.new_conference_format, sdf.format(Date()))
            } ?: getString(R.string.new_conference_format, SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date()))

            val participants = selected + username
            GrpcClient.createGroupChat(topic, participants, username, "conference") { chatId ->
                if (chatId != null) {
                    runOnUiThread {
                        sheet.dismiss()
                        val intent = Intent(this, NewChatActivity::class.java).apply {
                            putExtra("USERNAME", username)
                            putExtra("ROOM_ID", chatId)
                            putExtra("CHAT_NAME", topic)
                            putExtra("CHAT_TYPE", "conference")
                            putExtra("PARTICIPANTS", JSONArray(participants).toString())
                            putExtra("CREATOR", username)
                        }
                        startActivity(intent)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this, R.string.failed_to_create_chat, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        sheet.show()
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

    private val actionModeCallback = createActionModeCallback(this)

    private fun updateActionModeTitle() = updateActionModeTitle(this)

    private fun pinSelectedChats(chats: List<ChatInfo>) = pinSelectedChats(this, chats)
    private fun muteSelectedChats(chats: List<ChatInfo>) = muteSelectedChats(this, chats)
    private fun archiveSelectedChats(chats: List<ChatInfo>) = archiveSelectedChats(this, chats)
    private fun deleteSelectedChats(chats: List<ChatInfo>) = deleteSelectedChats(this, chats)

    // ======= Search =======

    private fun setupSearchMenu() = setupSearchMenu(this)

    // ======= Navigation =======

    internal fun navigateToChat(chat: ChatInfo, username: String) {
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
            // No saved server — always default to prod
            serverAddress = "13.140.25.249:50051"
            host = "13.140.25.249"
            port = 50051
            serverName = "Lava Germany"
            CredentialStore.setServerAddress(this, serverAddress)
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
        ThemeStore.init(this)
        val prefs = getSharedPreferences("ThemePrefs", MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )
        ThemeApplier.apply(this, ThemeStore.currentTheme())
    }

    // Public method for NewChatBottomSheet to open contacts
    fun showAddContactDialogPublic() {
        startActivity(Intent(this, ContactsActivity::class.java).apply {
            putExtra("USERNAME", SessionManager.session.value.username)
        })
    }
}
