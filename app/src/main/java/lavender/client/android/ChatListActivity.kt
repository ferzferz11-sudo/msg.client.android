package lavender.client.android

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.updates.UpdateUtils
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import android.view.animation.AnimationUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import java.io.File
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.RealGrpcClient
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.db.*
import lavender.client.android.data.models.*
import lavender.client.android.data.session.*
import lavender.client.android.data.updates.*
import lavender.client.android.databinding.ActivityChatListBinding
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.adapter.ChatAdapter
import lavender.client.android.ui.adapter.UserAdapter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

import lavender.client.android.theme.Theme
import lavender.client.android.ui.widget.AIBottomSheet
import lavender.client.android.ui.widget.LoginBottomSheet
import lavender.client.android.ui.widget.RegisterBottomSheet
import lavender.client.android.ui.widget.ServerAuthBottomSheet
import lavender.client.android.ui.widget.StandardBottomSheet
import lavender.client.android.ui.widget.ActionBottomSheet
import lavender.client.android.ui.widget.SearchableListBottomSheet
import lavender.client.android.ui.widget.SheetAction
import lavender.client.android.ui.widget.WidgetManager
import lavender.client.android.data.models.AIChatInfo

class ChatListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatListBinding
    private lateinit var chatAdapter: ChatAdapter
    private val grpcClient = GrpcClient
    private lateinit var updateManager: UpdateManager
    private lateinit var username: String
    private lateinit var password: String
    private val chats = mutableListOf<ChatInfo>()
    private val pendingDeletions = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private var isChatsLoaded = false // prevent reload flicker on resume
    private var isLoadingChats = false // prevent concurrent loadChats from launcher + onResume
    private var isConnecting = false // prevent duplicate connect() calls
    private var refreshDebounceJob: Job? = null // debounce rapid refresh requests
    private var unreadNotifCount = 0 // badge count for server notifications
    private var shouldShowAiSheetOnResume = false // flag to reopen AI sheet after returning from AI activity

    private var syncJob: Job? = null
    
    private val updatePrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { updateUpdateIndicatorVisibility() }
    }
    private val announcementPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { updateUpdateIndicatorVisibility() }
    }

    private val editProfileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val newUsername = result.data?.getStringExtra("newUsername")
            if (newUsername != null && newUsername != username) {
                username = newUsername
                loadChats()
                syncJob?.cancel()
                startSync()
            }
            val intent = Intent("lavender.client.android.UPDATE_AVATAR")
            sendBroadcast(intent)
            updateToolbarAvatar()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SessionManager.initFromPrefs(this)

        applyTheme()

        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Logo tap removed — welcomeContainer deleted

        updateManager = UpdateManager(this)
        
        setupSystemNotificationObserver()

        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        
        // App update cache clearing
        val currentVersion = lavender.client.android.BuildConfig.VERSION_CODE
        val lastVersion = prefs.getInt("last_app_version", 0)
        if (lastVersion != 0 && lastVersion < currentVersion) {
            Log.d("ChatListActivity", "App updated from $lastVersion to $currentVersion, clearing cache")
            clearLocalCacheSync()
        }
        prefs.edit { putInt("last_app_version", currentVersion) }
        
        val session = SessionManager.session.value

        // Check if user is authenticated — credentials are stored encrypted
        username = session.username
        password = session.password

        // Fallback: allow explicit login via intent extras (from auth dialog)
        if (username.isEmpty()) {
            username = intent.getStringExtra("USERNAME") ?: ""
        }
        if (password.isEmpty()) {
            password = intent.getStringExtra("PASSWORD") ?: ""
        }

        val serverAddress = intent.getStringExtra("SERVER_ADDRESS")
            ?: lavender.client.android.data.session.CredentialStore.getServerAddress(this)
            ?: ""

        // Initialize basic UI components regardless of auth state
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // If not authenticated, show auth choice dialog and return early
        if (username.isEmpty() || password.isEmpty()) {
            showAuthChoiceDialog()
            return
        }

        if (SessionManager.session.value.username != username) {
            val savedUserId = prefs.getString("user_id", "") ?: ""
            SessionManager.updateSession(username = username, password = password, userId = savedUserId)
        }

        if (serverAddress.isNotEmpty()) {
            val parts = serverAddress.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
            grpcClient.connect(host, false, port, this)
        }

        ThemeUi.bind(this, username)

        Log.d("ChatListActivity", "Logged in as $username")

        // Observe download state via StateFlow
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    updateManager.isDownloadingInstance.collect { downloading ->
                        updateUpdateIndicatorVisibility()
                    }
                }
                launch {
                    updateManager.downloadProgressInstance.collect { progress ->
                        binding.tvUpdateProgress.text = getString(R.string.percent_format, progress)
                        binding.tvUpdateProgress.isVisible = progress > 0
                    }
                }
                launch {
                    updateManager.isDownloadedInstance.collect { downloaded ->
                        updateUpdateIndicatorVisibility()
                    }
                }
            }
        }
        
        checkForUpdatesSilently()
        checkAnnouncements()

        chatAdapter = ChatAdapter(
            lifecycleScope,
            onChatClick = { chat ->
                if (chat.type == "favorites") {
                    val intent = Intent(this, NewChatActivity::class.java).apply {
                        putExtra("USERNAME", username)
                        putExtra("CHAT_NAME", getString(R.string.favorites))
                        putExtra("ROOM_ID", "favorites_$username")
                        putExtra("IS_DIRECT", false)
                        putExtra("PARTICIPANTS", "[\"$username\"]")
                        putExtra("CREATOR", username)
                    }
                    startActivity(intent)
                    return@ChatAdapter
                }

                if (chat.unreadCount > 0) {
                    grpcClient.markRead(chat.id, username)
                    // Locally update for immediate visual feedback
                    val index = chats.indexOfFirst { it.id == chat.id }
                    if (index != -1) {
                        chats[index] = chats[index].copy(unreadCount = 0)
                        chatAdapter.setChats(chats.toList())
                        updateAppIconBadge(chats.sumOf { it.unreadCount })
                    }
                }

                if (chat.type == "hermes") {
                    val intent = Intent(this, lavender.client.android.ui.hermes.HermesChatActivity::class.java).apply {
                        putExtra("CHAT_ID", chat.id)
                        putExtra("CHAT_NAME", chat.name)
                        putExtra("ACTIVE_AGENT_ID", chat.activeAgentId)
                        putExtra("AGENT_MODE", chat.agentMode)
                    }
                    startActivity(intent)
                    return@ChatAdapter
                }

                val intent = Intent(this, NewChatActivity::class.java).apply {
                    putExtra("USERNAME", username)
                    putExtra("SERVER_ADDRESS", intent.getStringExtra("SERVER_ADDRESS") ?: "")
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
            },
            onEnterLobbyClick = { chat ->
                val intent = Intent(this, ConferenceLobbyActivity::class.java).apply {
                    putExtra("ROOM_ID", chat.id)
                    putExtra("CHAT_NAME", chat.name)
                    putExtra("PARTICIPANTS", chat.participants)
                    putExtra("CREATOR", chat.creator)
                }
                startActivity(intent)
            },
            onSelectionChanged = { count ->
                val hasSelection = count > 0
                binding.tvToolbarTitle.text = if (hasSelection) getString(R.string.selected_count, count) else getString(R.string.chats)
                supportActionBar?.setDisplayHomeAsUpEnabled(hasSelection || binding.searchCard.isVisible)
                supportActionBar?.setHomeAsUpIndicator(if (hasSelection || binding.searchCard.isVisible) R.drawable.ic_close else 0)
                
                binding.ivActionDelete.isVisible = hasSelection
                binding.ivActionMute.isVisible = hasSelection
                
                // Show edit icon only if ONE group chat is selected and user is creator
                val selected = chatAdapter.getSelectedChats()
                val canEdit = selected.size == 1 && (selected[0].type == "group" || selected[0].type == "general") && selected[0].creator == username
                binding.ivActionEdit.isVisible = canEdit

                binding.ivActionSearch.isVisible = !hasSelection && !binding.searchCard.isVisible
                binding.ivToolbarUserAvatar.isVisible = !hasSelection && !binding.searchCard.isVisible

                updateUpdateIndicatorVisibility()
            },
            currentUsername = username,
            initialAvatarCache = grpcClient.getAvatarCache(),
            onlineUsers = grpcClient.users.value,
            onEmptyListUpdate = {
                // Force RecyclerView re-layout when Favorites is first added to empty list
                binding.rvChatList.post {
                    binding.rvChatList.requestLayout()
                    chatAdapter.notifyDataSetChanged()
                }
            }
        )
        binding.rvChatList.apply {
            layoutManager = LinearLayoutManager(this@ChatListActivity)
            adapter = chatAdapter
            setHasFixedSize(false)
            isNestedScrollingEnabled = false
        }

        // Show Favorites immediately — don't wait for network
        // This ensures Favorites is visible even if server is unreachable
        val favoritesChat = ChatInfo(
            id = "favorites_$username",
            name = getString(R.string.favorites),
            type = "favorites",
            lastMessageText = "",
            lastMessageTime = 0L
        )
        chats.add(favoritesChat)
        chatAdapter.setChats(chats.toList())

        // Handle bottom navigation bar insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Add padding to the bottom of the RecyclerView so the last item is visible
            // We add extra padding to account for the FAB
            binding.rvChatList.updatePadding(
                bottom = systemBars.bottom + (80 * resources.displayMetrics.density).toInt()
            )
            
            // Adjust FAB margin to be above the navigation bar
            binding.fabAddChat.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + (16 * resources.displayMetrics.density).toInt()
                marginEnd = (16 * resources.displayMetrics.density).toInt()
            }

            // Adjust AI FAB margin (above addChatFab)
            binding.fabAi.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + (40 * resources.displayMetrics.density).toInt()
                marginEnd = (16 * resources.displayMetrics.density).toInt()
            }
            
            insets
        }

        binding.fabAddChat.setOnClickListener {
            showChatActionSheet()
        }

        binding.fabAi.setOnClickListener {
            lifecycleScope.launch { showAIActionSheet() }
        }

        binding.srlChatList.setOnRefreshListener {
            loadChats(skipCache = true)
        }

        binding.ivToolbarUserAvatar.setOnClickListener {
            showSettingsSheet()
        }

        binding.tvToolbarTitle.setOnClickListener {
            showSettingsSheet()
        }

        binding.ivActionSettings.setOnClickListener {
            showSettingsSheet()
        }
        
        binding.ivActionSearch.setOnClickListener {
            showSearchBar()
        }

        binding.toolbar.setNavigationOnClickListener {
            if (binding.searchCard.isVisible) {
                hideSearchBar()
            } else if (chatAdapter.getSelectedChats().isNotEmpty()) {
                chatAdapter.clearSelection()
            }
        }

        binding.ivActionDelete.setOnClickListener {
            val selected = chatAdapter.getSelectedChats()
            if (selected.isNotEmpty()) {
                confirmDeleteSelectedChats(selected)
            }
        }

        binding.ivActionMute.setOnClickListener {
            val selected = chatAdapter.getSelectedChats()
            if (selected.isNotEmpty()) {
                toggleMuteSelectedChats(selected)
            }
        }

        binding.ivActionEdit.setOnClickListener {
            val selected = chatAdapter.getSelectedChats()
            if (selected.size == 1) {
                val chat = selected[0]
                val intent = Intent(this, ProfileActivity::class.java).apply {
                    putExtra("username", chat.name)
                    putExtra("is_group", true)
                    putExtra("room_id", chat.id)
                    putExtra("avatar_url", chat.avatarUrl)
                    putExtra("full_avatar_url", chat.fullAvatarUrl)
                    putExtra("participants", chat.participants)
                    putExtra("creator", chat.creator)
                }
                startActivity(intent)
            }
        }

        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                chatAdapter.filter(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        updateToolbarAvatar()
        
        lifecycleScope.launch {
            grpcClient.connectionStatus.collect { status ->
                val isConnectingNow = status == ConnectionStatus.CONNECTING
                val isFailed = status == ConnectionStatus.FAILED
                
                // Reset isConnecting flag when status changes from CONNECTING
                if (!isConnectingNow && isConnecting) {
                    isConnecting = false
                }
                
                if (chatAdapter.getSelectedChats().isEmpty()) {
                    binding.tvToolbarTitle.text = getString(R.string.chats)
                    
                    when {
                        isConnectingNow -> {
                            binding.tvToolbarSubtitle.text = getString(R.string.connecting)
                            binding.tvToolbarSubtitle.isVisible = true
                        }
                        isFailed -> {
                            binding.tvToolbarSubtitle.text = getString(R.string.waiting_for_network)
                            binding.tvToolbarSubtitle.isVisible = true
                        }
                        else -> {
                            binding.tvToolbarSubtitle.isVisible = false
                        }
                    }
                } else {
                    binding.tvToolbarSubtitle.isVisible = false
                }

                if (status == ConnectionStatus.READY) {
                    Log.d("ChatListActivity", "READY status: username='$username', password='${if (password.isNotEmpty()) "***" else ""}', isAppInBackground=${lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground}")
                    if (username.isNotEmpty() && password.isNotEmpty() && !lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground) {
                        val session = SessionManager.session.value
                        grpcClient.startChat(username, password, "", register = false, deviceId = session.deviceId, deviceName = session.deviceName) { }
                        if (!isChatsLoaded && !isLoadingChats) {
                            Log.d("ChatListActivity", "Starting loadChats from connectionStatus collector")
                            loadChats()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            grpcClient.users.collect { onlineUsers ->
                runOnUiThread { chatAdapter.setOnlineUsers(onlineUsers) }
            }
        }

        lifecycleScope.launch {
            grpcClient.avatarCacheFlow.collect { cache ->
                runOnUiThread { 
                    chatAdapter.updateAvatarCache(cache)
                    updateToolbarAvatar()
                }
            }
        }
        
        // startSync() is called from loadChats() after successful load

        lifecycleScope.launch {
            SessionManager.logoutEvent.collect {
                runOnUiThread {
                    AppLog.warn("ChatListActivity", "Session terminated by server")
                    Toast.makeText(this@ChatListActivity, R.string.session_terminated, Toast.LENGTH_LONG).show()
                    logout()
                }
            }
        }

        intent.getStringExtra("START_DELETION_ID")?.let { performDirectDeletion(it) }
        intent.getStringExtra("DELETING_CHAT_ID")?.let { chatId ->
            chatAdapter.setChatDeleting(chatId, true)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.searchCard.isVisible) {
                    hideSearchBar()
                } else if (chatAdapter.getSelectedChats().isNotEmpty()) {
                    chatAdapter.clearSelection()
                } else {
                    moveTaskToBack(true)
                }
            }
        })

        if (intent.getBooleanExtra("extra_show_whats_new", false)) {
            showWhatsNewDialog()
        }
    }

    override fun onStart() {
        super.onStart()
        SessionManager.startPeriodicDeviceUpdate(this)
    }

    override fun onStop() {
        super.onStop()
        SessionManager.stopPeriodicDeviceUpdate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (intent.getBooleanExtra("extra_show_whats_new", false)) {
            showWhatsNewDialog()
        }

        lifecycleScope.launch {
            SessionManager.logoutEvent.collect {
                runOnUiThread {
                    Toast.makeText(this@ChatListActivity, R.string.session_terminated, Toast.LENGTH_LONG).show()
                    logout()
                }
            }
        }

        intent.getStringExtra("START_DELETION_ID")?.let { performDirectDeletion(it) }
        intent.getStringExtra("DELETING_CHAT_ID")?.let { chatId ->
            chatAdapter.setChatDeleting(chatId, true)
        }
        // Don't load chats here — wait for connectionStatus READY in the collector below.
        // Loading before connection is ready causes suspendCancellableCoroutine to hang
        // until the coroutine is cancelled (e.g. activity recreation after registration).
    }

    private fun performDirectDeletion(chatId: String) {
        pendingDeletions.add(chatId)
        chatAdapter.setChatDeleting(chatId, true)
        grpcClient.deleteChat(chatId, username) { success, _ ->
            runOnUiThread { 
                if (success) {
                    chats.removeAll { it.id == chatId }
                    chatAdapter.setChats(chats.toList())
                    updateAppIconBadge(chats.sumOf { it.unreadCount })
                    
                    lifecycleScope.launch {
                        delay(1000)
                        loadChats() 
                    }
                } else {
                    pendingDeletions.remove(chatId)
                    chatAdapter.setChatDeleting(chatId, false)
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (binding.searchCard.isVisible) {
                hideSearchBar()
            } else if (chatAdapter.getSelectedChats().isNotEmpty()) {
                chatAdapter.clearSelection()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showSearchBar() {
        binding.searchCard.isVisible = true
        binding.etSearch.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.etSearch, 0)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)
        
        binding.ivActionSearch.isVisible = false
        binding.ivToolbarUserAvatar.isVisible = false
        updateUpdateIndicatorVisibility()
    }

    private fun hideSearchBar() {
        binding.searchCard.isVisible = false
        binding.etSearch.text?.clear()
        chatAdapter.filter("")
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        
        val hasSelection = chatAdapter.getSelectedChats().isNotEmpty()
        supportActionBar?.setDisplayHomeAsUpEnabled(hasSelection)
        supportActionBar?.setHomeAsUpIndicator(if (hasSelection) R.drawable.ic_close else 0)
        binding.ivActionSearch.isVisible = !hasSelection
        binding.ivToolbarUserAvatar.isVisible = !hasSelection
        binding.tvToolbarTitle.text = if (hasSelection) getString(R.string.selected_count, chatAdapter.getSelectedChats().size) else getString(R.string.chats)
        updateUpdateIndicatorVisibility()
    }

    private fun confirmDeleteSelectedChats(selected: List<ChatInfo>) {
        val sheet = StandardBottomSheet(this, R.layout.dialog_delete_chats)
        sheet.setTitle(getString(R.string.delete_chats))

        sheet.findViewById<TextView>(R.id.messageText)?.text = 
            getString(R.string.delete_chats_confirmation, selected.size)

        sheet.findViewById<View>(R.id.btnCancel)?.setOnClickListener { sheet.dismiss() }
        sheet.findViewById<View>(R.id.btnDelete)?.setOnClickListener {
            var completedCount = 0
            val totalToDelete = selected.size
            
            selected.forEach { chat ->
                pendingDeletions.add(chat.id)
                chatAdapter.setChatDeleting(chat.id, true)
                
                grpcClient.deleteChat(chat.id, username) { success, _ ->
                    runOnUiThread {
                        completedCount++
                        if (success) {
                            chats.removeAll { it.id == chat.id }
                        } else {
                            pendingDeletions.remove(chat.id)
                            chatAdapter.setChatDeleting(chat.id, false)
                        }
                        
                        if (completedCount == totalToDelete) {
                            chatAdapter.clearSelection()
                            chatAdapter.setChats(chats.toList())
                            updateAppIconBadge(chats.sumOf { it.unreadCount })
                            
                            lifecycleScope.launch {
                                delay(1000)
                                loadChats()
                            }
                        }
                    }
                }
            }
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun toggleMuteSelectedChats(selected: List<ChatInfo>) {
        val anyUnmuted = selected.any { !it.isMuted }
        
        // Optimistic UI update: update local list immediately
        selected.forEach { selectedChat ->
            val index = chats.indexOfFirst { it.id == selectedChat.id }
            if (index != -1) {
                chats[index] = chats[index].copy(isMuted = anyUnmuted)
            }
        }
        chatAdapter.setChats(chats.toList())
        chatAdapter.clearSelection()

        // Background server update
        selected.forEach { chat ->
            grpcClient.setMutedChat(chat.id, anyUnmuted) { success ->
                if (!success) {
                    // If failed, we might want to reload to sync back with server
                    // but for now just log it
                    Log.e("ChatList", "Failed to update mute status for ${chat.id}")
                }
            }
        }

        val text = getString(if (anyUnmuted) R.string.muted_count else R.string.unmuted_count, selected.size)
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun updateToolbarAvatar() {
        val avatarCache = grpcClient.getAvatarCache()
        val myAvatarUrl = avatarCache[username]
        val currentTheme = ThemeStore.currentTheme()
        
        if (!myAvatarUrl.isNullOrEmpty()) {
            com.bumptech.glide.Glide.with(this)
                .load(myAvatarUrl)
                .placeholder(R.drawable.ic_default_avatar)
                .circleCrop()
                .into(binding.ivToolbarUserAvatar)
            binding.ivToolbarUserAvatar.clearColorFilter()
        } else {
            val avatarFile = File(filesDir, "avatars/$username.jpg")
            if (avatarFile.exists()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(avatarFile.absolutePath)
                    if (bitmap != null) {
                        binding.ivToolbarUserAvatar.setImageBitmap(bitmap)
                        binding.ivToolbarUserAvatar.clearColorFilter()
                    } else {
                        ThemeUtils.applyDefaultAvatar(binding.ivToolbarUserAvatar, currentTheme)
                    }
                } catch (e: Exception) {
                    Log.e("ChatListActivity", "Error loading avatar for toolbar", e)
                    ThemeUtils.applyDefaultAvatar(binding.ivToolbarUserAvatar, currentTheme)
                }
            } else {
                ThemeUtils.applyDefaultAvatar(binding.ivToolbarUserAvatar, currentTheme)
            }
        }
    }

    private fun applyTheme() {
        val sharedPrefs = getSharedPreferences("ThemePrefs", MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
        
        // Apply custom theme from SharedPreferences if user is not authenticated
        // This ensures the theme is preserved after logout
        if (!::username.isInitialized || username.isEmpty()) {
            val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
            val themeId = prefs.getString("current_theme_id", "dark") ?: "dark"
            val customTheme = if (themeId == "dark") {
                lavender.client.android.theme.BuiltInThemes.dark
            } else if (themeId == "light") {
                lavender.client.android.theme.BuiltInThemes.BASE_LIGHT
            } else {
                val builtIn = lavender.client.android.theme.BuiltInThemes.findById(themeId)
                if (builtIn != null) builtIn else lavender.client.android.theme.BuiltInThemes.dark
            }
            ThemeUi.bind(this, "")
        }
    }

    private fun loadChats(skipCache: Boolean = false) {
        // Only load chats if user is authenticated and chatAdapter is initialized
        if (!::chatAdapter.isInitialized) {
            Log.d("ChatListActivity", "chatAdapter not initialized, skipping loadChats")
            return
        }

        // Prevent concurrent loads
        if (isLoadingChats) {
            Log.d("ChatListActivity", "loadChats: already loading, skipping")
            return
        }

        // Don't hammer the server if we're not connected — wait for READY status
        if (grpcClient.connectionStatus.value != ConnectionStatus.READY) {
            Log.d("ChatListActivity", "loadChats: not connected (${grpcClient.connectionStatus.value}), skipping")
            return
        }

        isLoadingChats = true

        Log.d("ChatListActivity", "Loading chats for $username (skipCache: $skipCache)")

        // Show refresh indicator only for pull-to-refresh (list already has data)
        val isRefresh = chats.isNotEmpty()
        if (isRefresh) {
            binding.srlChatList.isRefreshing = true
        }

        // Add timeout to prevent infinite loading
        val loadTimeout = lifecycleScope.launch {
            delay(5000)
            if (binding.srlChatList.isRefreshing) {
                Log.w("ChatListActivity", "Load chats timeout, stopping refresh")
                runOnUiThread { binding.srlChatList.isRefreshing = false }
            }
        }

        // Clear local cache on full refresh
        if (skipCache) {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = lavender.client.android.data.db.AppDatabase.getDatabase(this@ChatListActivity)
                db.chatDao().clearAll()
            }
        }

        // Fetch everything on background thread, apply once on UI
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Fetch chats from server (with timeout to prevent hanging)
                val fetchedChats = withTimeoutOrNull(10000L) {
                    suspendCancellableCoroutine<List<ChatInfo>> { cont ->
                        grpcClient.getChats(username, skipCache = skipCache) { chats ->
                            if (cont.isActive) cont.resumeWith(Result.success(chats))
                        }
                    }
                } ?: emptyList()
                if (fetchedChats.isEmpty() && skipCache) {
                    Log.w("ChatListActivity", "getChats returned empty after timeout")
                }

                // 2. Get muted chats IDs (non-blocking fire-and-forget on IO)
                val userId = grpcClient.getUserId() ?: ""
                val mutedIds = if (userId.isNotEmpty()) {
                    suspendCancellableCoroutine<Set<String>> { cont ->
                        grpcClient.getMutedChats { ids ->
                            if (cont.isActive) cont.resumeWith(Result.success(ids.toSet()))
                        }
                    }
                } else emptySet()

                // 3. Clean up pending deletions
                val serverIds = fetchedChats.map { it.id }.toSet()
                pendingDeletions.removeAll { !serverIds.contains(it) }
                val filteredChats = fetchedChats
                    .filter { !pendingDeletions.contains(it.id) }
                    .map { it.copy(isMuted = mutedIds.contains(it.id)) }
                    .sortedByDescending { it.lastMessageTime } // sort all chats by time, OWL included

                // 4. Prepend Favorites as first item (static, never changes)
                val newChats = mutableListOf(
                    ChatInfo(
                        id = "favorites_$username",
                        name = getString(R.string.favorites),
                        type = "favorites",
                        lastMessageText = "",
                        lastMessageTime = 0L
                    )
                )
                newChats.addAll(filteredChats)

                // 5. Update local cache in background
                try {
                    val db = lavender.client.android.data.db.AppDatabase.getDatabase(this@ChatListActivity)
                    db.chatDao().syncChats(filteredChats.map { it.toEntity() })
                } catch (e: Exception) {
                    Log.e("ChatListActivity", "Failed to cache chats", e)
                }

                // 6. Apply to UI once — single update, no flicker
                withContext(Dispatchers.Main) {
                    loadTimeout.cancel()
                    binding.srlChatList.isRefreshing = false

                    chats.clear()
                    chats.addAll(newChats)
                    chatAdapter.setChats(newChats)
                    Log.d("ChatListActivity", "setChats called: ${newChats.size} chats, adapter itemCount=${chatAdapter.itemCount}")

                    // Force RecyclerView to re-layout
                    binding.rvChatList.post {
                        binding.rvChatList.requestLayout()
                        Log.d("ChatListActivity", "RecyclerView childCount=${binding.rvChatList.childCount}, itemCount=${chatAdapter.itemCount}")
                    }

                    updateAppIconBadge(chats.sumOf { it.unreadCount })
                    isChatsLoaded = true

                    Log.d("ChatListActivity", "Loaded ${chats.size} chats (muted: ${mutedIds.size})")
                    
                    // Start background sync and refresh auxiliary data after successful load
                    startSync()
                    refreshAiChats()
                    refreshUnreadCount()
                }

                // Fetch favorites data in background (non-visual)
                if (userId.isNotEmpty()) {
                    grpcClient.getFavorites(userId) { _ -> }
                }

            } catch (e: CancellationException) {
                // Activity was destroyed — don't touch UI
                Log.d("ChatListActivity", "loadChats cancelled (activity destroyed)")
            } catch (e: Exception) {
                Log.e("ChatListActivity", "Error loading chats", e)
                try {
                    withContext(Dispatchers.Main) {
                        loadTimeout.cancel()
                        binding.srlChatList.isRefreshing = false
                        // Even on error, show Favorites if list is empty
                        if (chats.isEmpty()) {
                            val favoritesChat = ChatInfo(
                                id = "favorites_$username",
                                name = getString(R.string.favorites),
                                type = "favorites",
                                lastMessageText = "",
                                lastMessageTime = 0L
                            )
                            chats.add(favoritesChat)
                            chatAdapter.setChats(chats.toList())
                        }
                    }
                } catch (_: CancellationException) {
                    // Activity destroyed during error handling
                }
            } finally {
                isLoadingChats = false
            }
        }
    }

    // Debounced refresh: wait 500ms for rapid events to batch together
    private fun refreshChatsDebounced() {
        refreshDebounceJob?.cancel()
        refreshDebounceJob = lifecycleScope.launch {
            delay(500)
            loadChats(skipCache = true)
            refreshAiChats()
            refreshUnreadCount()
        }
    }

    private fun loadChatsFromCache(fetchedChats: List<ChatInfo>) {
        runOnUiThread {
            binding.srlChatList.isRefreshing = false
            chats.clear()

            // Load from local database if available
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val db = lavender.client.android.data.db.AppDatabase.getDatabase(this@ChatListActivity)
                    val cachedChats = db.chatDao().getAllChats()

                    runOnUiThread {
                        if (cachedChats.isNotEmpty()) {
                            val sorted = cachedChats.sortedByDescending { it.lastMessageTime }
                            val chatInfos = sorted.map { dbChat ->
                                ChatInfo(
                                    id = dbChat.id,
                                    name = dbChat.name,
                                    type = dbChat.type,
                                    lastMessageText = dbChat.lastMessageText,
                                    lastMessageTime = dbChat.lastMessageTime,
                                    unreadCount = dbChat.unreadCount,
                                    participants = dbChat.participants,
                                    creator = dbChat.creator
                                )
                            }
                            // Always prepend Favorites
                            val withFavorites = mutableListOf(ChatInfo(
                                id = "favorites_$username",
                                name = getString(R.string.favorites),
                                type = "favorites",
                                lastMessageText = "",
                                lastMessageTime = 0L
                            ))
                            withFavorites.addAll(chatInfos)
                            chats.addAll(withFavorites)
                        } else {
                            // No cache — show Favorites only
                            chats.add(ChatInfo(
                                id = "favorites_$username",
                                name = getString(R.string.favorites),
                                type = "favorites",
                                lastMessageText = "",
                                lastMessageTime = 0L
                            ))
                        }

                        chatAdapter.setChats(chats.toList())
                        updateAppIconBadge(chats.sumOf { it.unreadCount })
                        Log.d("ChatListActivity", "Loaded ${chats.size} chats from cache")
                        updateUpdateIndicatorVisibility()
                    }
                } catch (e: Exception) {
                    Log.e("ChatListActivity", "Error loading from cache", e)
                    runOnUiThread {
                        // Fallback — show Favorites only
                        chats.add(ChatInfo(
                            id = "favorites_$username",
                            name = getString(R.string.favorites),
                            type = "favorites",
                            lastMessageText = "",
                            lastMessageTime = 0L
                        ))
                        chatAdapter.setChats(chats.toList())
                        updateAppIconBadge(chats.sumOf { it.unreadCount })
                        Log.d("ChatListActivity", "Loaded ${chats.size} chats (fallback)")
                        updateUpdateIndicatorVisibility()
                    }
                }
            }
        }
    }

    private fun updateAppIconBadge(count: Int) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = notificationManager.getNotificationChannel("messages")
            if (channel != null) {
                channel.setShowBadge(count > 0)
                notificationManager.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            Log.e("ChatSync", "Error updating badge", e)
        }
    }

    private fun setupSystemNotificationObserver() {
        lifecycleScope.launch {
            grpcClient.systemNotification.collect { notification ->
                if (notification != null) {
                    showSystemNotificationDialog(notification)
                }
            }
        }
    }

    private fun showSystemNotificationDialog(message: String) {
        val theme = ThemeStore.currentTheme()
        val textColor = theme.textPrimaryColor.toColorInt()
        val pColor = theme.primaryColor.toColorInt()

        val dialogView = layoutInflater.inflate(R.layout.dialog_whats_new, null)
        dialogView.findViewById<TextView>(R.id.tvTitle).apply {
            text = getString(R.string.system_notification)
            setTextColor(pColor)
        }
        dialogView.findViewById<TextView>(R.id.tvContent).apply {
            text = message
            setTextColor(textColor)
        }

        val btnClose = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnOk = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOk)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnOk.apply {
            text = getString(R.string.mark_as_read)
            backgroundTintList = ColorStateList.valueOf(pColor)
            setTextColor(theme.onPrimaryColor.toColorInt())
            setOnClickListener {
                grpcClient.clearSystemNotification()
                dialog.dismiss()
            }
        }

        btnClose.apply {
            text = getString(R.string.close)
            setTextColor(theme.textSecondaryColor.toColorInt())
            setOnClickListener { 
                grpcClient.clearSystemNotification()
                dialog.dismiss() 
            }
        }

        val shape = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
            floatArrayOf(24f, 24f, 24f, 24f, 24f, 24f, 24f, 24f), null, null
        ))
        shape.paint.color = theme.surfaceColor.toColorInt()
        dialog.window?.setBackgroundDrawable(shape)

        dialog.show()
    }

    private fun showUpdateProgressDialog() {
        ActionBottomSheet(this)
            .setTitle(getString(R.string.update_in_progress))
            .setActions(listOf(
                SheetAction(R.id.actionContinueUpdate, R.drawable.ic_play_arrow, getString(R.string.continue_label)) {
                    // Do nothing, just close the sheet
                },
                SheetAction(R.id.actionCancelUpdate, R.drawable.ic_close, getString(R.string.cancel_update)) {
                    updateManager.cancelDownload()
                    updateUpdateIndicatorVisibility()
                    Toast.makeText(this, R.string.update_cancelled, Toast.LENGTH_SHORT).show()
                }
            )).show()
    }

    private fun updateUpdateIndicatorVisibility() {
        val prefs = getSharedPreferences("UpdatePrefs", MODE_PRIVATE)
        val isAvailable = prefs.getBoolean("update_available", false)
        val isDownloaded = prefs.getBoolean("update_downloaded", false)
        val isDownloading = prefs.getBoolean("update_downloading", false)
        
        val hasSelection = if (::chatAdapter.isInitialized) chatAdapter.getSelectedChats().isNotEmpty() else false
        val isSearching = binding.searchCard.isVisible
        
        // Show container if update is ready or downloading
        binding.llUpdateContainer.isVisible = (isDownloaded || isDownloading) && !hasSelection && !isSearching
        
        if (isDownloading) {
            binding.ivUpdateAvailable.setImageResource(R.drawable.ic_update_rotating)
            val rotation = AnimationUtils.loadAnimation(this, R.anim.rotate_renew)
            binding.ivUpdateAvailable.startAnimation(rotation)
            binding.ivUpdateAvailable.contentDescription = "Downloading..."
        } else {
            binding.ivUpdateAvailable.clearAnimation()
            binding.tvUpdateProgress.isVisible = false
            if (isDownloaded) {
                binding.ivUpdateAvailable.setImageResource(R.drawable.ic_install_update)
                binding.ivUpdateAvailable.contentDescription = getString(R.string.install_update)
            } else {
                binding.ivUpdateAvailable.setImageResource(R.drawable.ic_update_available)
                binding.ivUpdateAvailable.contentDescription = getString(R.string.update_available)
            }
        }
        
        binding.llUpdateContainer.setOnClickListener {
            if (isDownloaded) {
                val apkPath = prefs.getString("apk_path", null)
                if (apkPath != null) {
                    UpdateUtils.installApk(this, File(apkPath))
                }
            } else if (isDownloading) {
                showUpdateProgressDialog()
            } else if (isAvailable) {
                updateManager.startDownload()
                updateUpdateIndicatorVisibility()
            }
        }

        Log.d("ChatListActivity", "Update visibility: avail=$isAvailable, down=$isDownloaded, downloading=$isDownloading")
    }

    private fun checkForUpdatesSilently() {
        checkAnnouncements()
        updateManager.checkForUpdates { isAvailable, latestVersion ->
            val prefs = getSharedPreferences("UpdatePrefs", MODE_PRIVATE)
            val isDownloaded = prefs.getBoolean("update_downloaded", false)
            val isDownloading = prefs.getBoolean("update_downloading", false)

            runOnUiThread {
                updateUpdateIndicatorVisibility()
                if (isAvailable) {
                    if (!isDownloaded && !isDownloading) {
                        updateManager.startDownload(isAuto = true)
                    } else if (isDownloaded) {
                        showUpdateAvailableNotification(latestVersion)
                    }
                }
            }
        }
    }

    private fun showUpdateAvailableNotification(latestVersion: String) {
        val intent = Intent(this, ChatListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1005, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to show changelog
        val whatsNewIntent = Intent(this, ChatListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_show_whats_new", true)
        }
        val whatsNewPendingIntent = PendingIntent.getActivity(
            this, 1006, whatsNewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(UpdateUtils.CHANNEL_ID, "Updates", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, UpdateUtils.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update_available)
            .setContentTitle(getString(R.string.update_available))
            .setContentText(getString(R.string.version_available, latestVersion))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_star, getString(R.string.whats_new), whatsNewPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        notificationManager.notify(1007, notification)
    }

    private fun startSync() {
        syncJob?.cancel()
        val syncServerAddress = CredentialStore.getServerAddress(this) // capture at start
        syncJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(5000) // Poll every 5 seconds

                // Skip sync if not connected
                if (grpcClient.connectionStatus.value != ConnectionStatus.READY) {
                    delay(1000)
                    continue
                }

                // Cancel if server changed
                val currentServer = CredentialStore.getServerAddress(this@ChatListActivity)
                if (currentServer != syncServerAddress) {
                    Log.d("ChatListActivity", "startSync: server changed ($syncServerAddress → $currentServer), stopping")
                    break
                }

                val currentUserId = GrpcClient.getUserId() ?: ""

                grpcClient.getChats(username, skipCache = true) { fetchedChats ->
                    // Skip empty results (server still syncing)
                    if (fetchedChats.isEmpty()) return@getChats

                    if (currentUserId.isNotEmpty()) {
                        grpcClient.getMutedChats { mutedChatIds ->
                            grpcClient.getFavorites(currentUserId) { _ ->
                                val chatsWithMute = fetchedChats
                                    .filter { !pendingDeletions.contains(it.id) }
                                    .copyWithMute(mutedChatIds)

                                val serverIds = fetchedChats.map { it.id }.toSet()
                                pendingDeletions.removeAll { !serverIds.contains(it) }

                                // Compare without Favorites offset
                                val displayChats = chatsWithMute
                                val adapterDisplay = if (chatAdapter.hasFavorites()) {
                                    // Skip Favorites at position 0 for comparison
                                    chats.drop(1)
                                } else {
                                    chats.toList()
                                }

                                val hasChanges = displayChats.size != adapterDisplay.size ||
                                        displayChats.indices.any { i ->
                                            val n = displayChats[i]
                                            val c = adapterDisplay.getOrNull(i)
                                            c == null || n.id != c.id || n.lastMessageTime != c.lastMessageTime ||
                                                    n.unreadCount != c.unreadCount || n.isMuted != c.isMuted
                                        }

                                if (hasChanges) {
                                    runOnUiThread {
                                        chats.clear()
                                        // Add Favorites only if adapter expects it
                                        if (chatAdapter.hasFavorites()) {
                                            chats.add(ChatInfo(
                                                id = "favorites_$username",
                                                name = getString(R.string.favorites),
                                                type = "favorites",
                                                lastMessageText = "",
                                                lastMessageTime = 0L
                                            ))
                                        }
                                        chats.addAll(chatsWithMute)
                                        chatAdapter.setChats(chats.toList())
                                        updateAppIconBadge(chats.sumOf { it.unreadCount })
                                    }
                                }
                            }
                        }
                    } else {
                        val filteredFetched = fetchedChats.filter { !pendingDeletions.contains(it.id) }
                        val adapterDisplay = if (chatAdapter.hasFavorites()) chats.drop(1) else chats.toList()

                        if (filteredFetched.size != adapterDisplay.size ||
                            filteredFetched.indices.any { i -> adapterDisplay.getOrNull(i)?.id != filteredFetched[i].id }) {
                            runOnUiThread {
                                chats.clear()
                                if (chatAdapter.hasFavorites()) {
                                    chats.add(ChatInfo(
                                        id = "favorites_$username",
                                        name = getString(R.string.favorites),
                                        type = "favorites",
                                        lastMessageText = "",
                                        lastMessageTime = 0L
                                    ))
                                }
                                chats.addAll(filteredFetched)
                                chatAdapter.setChats(chats.toList())
                                updateAppIconBadge(chats.sumOf { it.unreadCount })
                            }
                        }
                    }
                }
            }
        }
    }

    private fun List<ChatInfo>.copyWithMute(mutedIds: List<String>): List<ChatInfo> {
        return map { it.copy(isMuted = mutedIds.contains(it.id)) }
    }

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
        WidgetManager.clearCache()
    }

    override fun onResume() {
        try {
            super.onResume()
        } catch (_: ClassCastException) {
            // Workaround for MIUI/binder bug on some Xiaomi devices
        }
        
        getSharedPreferences("UpdatePrefs", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(updatePrefsListener)
        getSharedPreferences("AnnouncementPrefs", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(announcementPrefsListener)
        // Only refresh theme from store if user is authenticated
        // Otherwise, keep the theme from SharedPreferences (preserved after logout)
        if (username.isNotEmpty()) {
            ThemeStore.refresh(this, username) // Force theme refresh from store when returning
        }
        
        // Only update chatAdapter if it's initialized (user is authenticated)
        if (::chatAdapter.isInitialized) {
            chatAdapter.updateAvatarCache(grpcClient.getAvatarCache())
            chatAdapter.updateTheme()
            // Fetch avatar from server if not in cache (e.g. after app restart)
            val avatarCache = grpcClient.getAvatarCache()
            if (avatarCache[username].isNullOrEmpty()) {
                grpcClient.getUserAvatar(username) { _ -> }
            }
        }
        
        updateUpdateIndicatorVisibility()

        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false

        // Ensure connection is active if we have a server address
        val currentStatus = grpcClient.connectionStatus.value
        Log.d("ChatListActivity", "onResume: connectionStatus=$currentStatus")
        val savedServerAddress = lavender.client.android.data.session.CredentialStore.getServerAddress(this)

        val needsReconnect = (
                           currentStatus == ConnectionStatus.DISCONNECTED ||
                           currentStatus == ConnectionStatus.FAILED ||
                           grpcClient.shouldForceReconnect() ||
                           (savedServerAddress.isNotEmpty() && savedServerAddress != grpcClient.currentServerAddress))

        if (needsReconnect && !isConnecting) {
            if (savedServerAddress.isNotEmpty()) {
                val parts = savedServerAddress.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
                Log.d("ChatListActivity", "onResume: reconnecting to $host:$port (was: ${grpcClient.currentServerAddress})")
                isConnecting = true
                grpcClient.connect(host, false, port, this, true)
            }
        }

        // Show AI sheet on return from AI activity
        if (shouldShowAiSheetOnResume) {
            shouldShowAiSheetOnResume = false
            if (::chatAdapter.isInitialized) {
                lifecycleScope.launch { showAIActionSheet() }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        
        getSharedPreferences("UpdatePrefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(updatePrefsListener)
        getSharedPreferences("AnnouncementPrefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(announcementPrefsListener)
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = true
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            val siteUrl = lavender.client.android.data.session.CredentialStore.getApkServerUrl(this@ChatListActivity)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, "${getString(R.string.share_text)}\n${siteUrl}/download")
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app)))
    }

    private fun logout() {
        syncJob?.cancel()
        isChatsLoaded = false
        SessionManager.logout(this)

        // Synchronously clear local cache — Room DB must be wiped before
        // the new Activity instance starts (lifecycleScope is cancelled by finish())
        clearLocalCacheSync()

        // Clear Glide memory cache on main thread
        try {
            com.bumptech.glide.Glide.get(this).clearMemory()
        } catch (_: Exception) { }

        // Save current theme to SharedPreferences before logout
        val currentTheme = ThemeStore.currentTheme()
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        prefs.edit {
            putString("current_theme_id", currentTheme.id)
        }

        // Clear username and password
        username = ""
        password = ""

        // Restart ChatListActivity cleanly — new instance will show
        // auth dialog on empty screen (no stale chats visible)
        val intent = Intent(this, ChatListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun checkManualUpdate() {
        val currentVersion = BuildConfig.VERSION_NAME
        updateManager.checkForUpdates { isAvailable, latestVersion ->
            runOnUiThread {
                showUpdateDialog(currentVersion, latestVersion)
            }
        }
    }

    private fun showUpdateDialog(current: String, latest: String) {
        val sheet = StandardBottomSheet(this, R.layout.bottom_sheet_update)
        
        val titleView = sheet.findViewById<TextView>(R.id.updateTitle)
        val messageView = sheet.findViewById<TextView>(R.id.updateMessage)
        val btnUpdate = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUpdate)
        val btnCancel = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val updateIcon = sheet.findViewById<ImageView>(R.id.updateIcon)

        val isAvailable = UpdateUtils.isUpdateAvailable(current, latest)
        if (!isAvailable) {
            titleView?.text = getString(R.string.ok)
            updateIcon?.setImageResource(R.drawable.ic_checked)
            btnUpdate?.text = getString(R.string.force_download)
        }
        
        messageView?.text = getString(R.string.version_info_format, current, latest)
        
        btnCancel?.setOnClickListener { sheet.dismiss() }
        btnUpdate?.setOnClickListener {
            sheet.dismiss()
            updateManager.startDownload()
            updateUpdateIndicatorVisibility()
        }
        
        sheet.show()
    }

    private fun checkAnnouncements() {
        lifecycleScope.launch(Dispatchers.IO) {
            checkAnnouncementsInternal()
        }
    }

    private suspend fun checkAnnouncementsInternal() {
        try {
            val url = URL("${lavender.client.android.data.session.CredentialStore.getApkServerUrl(this@ChatListActivity)}/changelog.txt?t=${System.currentTimeMillis()}")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                var text = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                text = text.replace("\r\n", "\n").replace("\r", "\n")
                
                if (text.isNotEmpty()) {
                    val prefs = getSharedPreferences("AnnouncementPrefs", MODE_PRIVATE)
                    var lastRead = prefs.getString("last_read_text", "") ?: ""
                    lastRead = lastRead.trim().replace("\r\n", "\n").replace("\r", "\n")
                    
                    val isNew = text != lastRead
                    prefs.edit { 
                        putString("current_text", text)
                        putBoolean("show_icon", isNew)
                    }
                    
                    withContext(Dispatchers.Main) {
                        updateUpdateIndicatorVisibility()
                    }
                }
            }
            connection.disconnect()
        } catch (_: Exception) {}
    }

    private var isNavigatingDeeper = false

    private val settingsActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        showAdditionalSettingsSheet { showSettingsSheet() }
    }

    // Flag set when returning from ServersActivity after successful login
    // Prevents onResume from doing redundant reconnect
    private var justReturnedFromServersActivity = false

    private val serversActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // User already logged in via ServersActivity — just refresh UI
        // Do NOT auto-login here, it causes double-login bug (v1.1.3.10)
        justReturnedFromServersActivity = true

        if (it.resultCode == RESULT_OK) {
            // Update local vars from CredentialStore (updated by ServersActivity)
            username = CredentialStore.getUsername(this)
            password = CredentialStore.getPassword(this)

            // Update GrpcClient and SessionManager with new user data
            val newUserId = CredentialStore.getUserId(this)
            if (newUserId.isNotEmpty()) {
                GrpcClient.setUserId(newUserId)
            }
            SessionManager.updateSession(username = username, password = password, userId = newUserId)

            val newServer = CredentialStore.getServerAddress(this)
            if (newServer.isNotEmpty()) {
                val parts = newServer.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051

                if (newServer != grpcClient.currentServerAddress) {
                    // Server changed — clear old chats and reconnect
                    Log.d("ChatListActivity", "Server changed: ${grpcClient.currentServerAddress} → $newServer, clearing old chats")
                    chatAdapter.clearAll()

                    // Clear local cache for new server
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val db = lavender.client.android.data.db.AppDatabase.getDatabase(this@ChatListActivity)
                            db.chatDao().clearAll()
                            db.messageDao().clearAll()
                        } catch (e: Exception) {
                            Log.e("ChatListActivity", "Error clearing cache", e)
                        }
                    }

                    grpcClient.disconnect()
                    isConnecting = true
                    grpcClient.connect(host, false, port, this, forceReconnect = true)
                }

                // Wait for connection then load chats
                isChatsLoaded = false
                lifecycleScope.launch {
                    // Wait for READY status (up to 15 seconds)
                    var waited = 0
                    while (waited < 15000) {
                        delay(500)
                        waited += 500
                        if (grpcClient.connectionStatus.value == ConnectionStatus.READY) {
                            Log.d("ChatListActivity", "Connection READY after ${waited}ms, loading chats from new server")
                            loadChats(skipCache = true)
                            // startSync() is called inside loadChats() after successful load
                            return@launch
                        }
                    }
                    Log.w("ChatListActivity", "Timeout waiting for READY after server switch")
                }
            }
        }
        showAdditionalSettingsSheet { showSettingsSheet() }
    }

    private var pendingAboutOnBack: (() -> Unit)? = null

    private val changelogActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Re-open the about dialog when returning from ChangelogActivity
        val onBack = pendingAboutOnBack
        pendingAboutOnBack = null
        showAboutDialog(onBack)
    }

    private fun showWhatsNewDialog(onBack: (() -> Unit)? = null) {
        val prefs = getSharedPreferences("AnnouncementPrefs", MODE_PRIVATE)
        val announcementText = prefs.getString("current_text", "") ?: return
        
        val sheet = StandardBottomSheet(this, R.layout.dialog_whats_new)

        sheet.findViewById<TextView>(R.id.tvTitle)?.isVisible = false // Use Widget's title instead
        sheet.setTitle(getString(R.string.whats_new))

        val tvContent = sheet.findViewById<TextView>(R.id.tvContent)
        tvContent?.apply {
            val versionName = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
            text = if (versionName.isNotEmpty()) "$announcementText\n\nLava $versionName" else announcementText
        }

        val btnClose = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnMarkRead = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOk)

        btnMarkRead?.setOnClickListener {
            val cleanText = announcementText.trim().replace("\r\n", "\n").replace("\r", "\n")
            prefs.edit {
                putString("last_read_text", cleanText)
                putBoolean("show_icon", false)
            }
            updateUpdateIndicatorVisibility()
            Toast.makeText(this, R.string.mark_as_read, Toast.LENGTH_SHORT).show()
            sheet.dismiss()
        }

        btnClose?.setOnClickListener { sheet.dismiss() }

        sheet.setOnDismissListener {
            if (!isNavigatingDeeper) onBack?.invoke()
            isNavigatingDeeper = false
        }

        sheet.show()
    }

    @SuppressLint("SetTextI18n")
    private fun showAboutDialog(onBack: (() -> Unit)? = null) {
        val sheet = StandardBottomSheet(this, R.layout.dialog_about)
        val customTheme = ThemeStore.currentTheme()

        sheet.setTitle(getString(R.string.action_about))

        val clientVersionText = sheet.findViewById<TextView>(R.id.clientVersionText)
        val serverVersionText = sheet.findViewById<TextView>(R.id.serverVersionText)
        val btnClose = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClose)
        val btnWhatsNew = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnWhatsNew)
        val btnFeedback = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFeedback)
        val btnShare = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnShare)
        val btnUpdate = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUpdate)

        val versionName = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
        clientVersionText?.text = getString(R.string.version_label, versionName)
        sheet.findViewById<TextView>(R.id.aboutLogoVersion)?.text = "v$versionName"
        
        val serverVersion = GrpcClient.serverVersion.value
        if (serverVersion.isNotEmpty()) {
            serverVersionText?.text = "Server Version: $serverVersion"
        } else {
            serverVersionText?.visibility = View.GONE
        }

        // Show update button if available
        val updatePrefs = getSharedPreferences("UpdatePrefs", MODE_PRIVATE)
        if (updatePrefs.getBoolean("update_available", false)) {
            btnUpdate?.visibility = View.VISIBLE
            btnUpdate?.setOnClickListener {
                sheet.dismiss()
                updateManager.startDownload()
                updateUpdateIndicatorVisibility()
            }
        }
        
        btnClose?.setOnClickListener { sheet.dismiss() }

        btnWhatsNew?.setOnClickListener {
            isNavigatingDeeper = true
            pendingAboutOnBack = onBack
            sheet.dismiss()
            try {
                changelogActivityLauncher.launch(ChangelogActivity.createIntent(this@ChatListActivity))
            } catch (e: Exception) {
                Log.e("ChatListActivity", "Failed to open ChangelogActivity", e)
                showWhatsNewDialog { showAboutDialog(onBack) }
            }
        }

        btnFeedback?.setOnClickListener {
            isNavigatingDeeper = true
            sheet.dismiss()
            showFeedbackDialog { showAboutDialog(onBack) }
        }
        
        btnShare?.setOnClickListener {
            isNavigatingDeeper = true
            sheet.dismiss()
            shareApp()
        }
        
        sheet.setOnDismissListener {
            if (!isNavigatingDeeper) onBack?.invoke()
            isNavigatingDeeper = false
        }

        sheet.show()
    }

    private fun showFeedbackDialog(onBack: (() -> Unit)? = null) {
        val sheet = StandardBottomSheet(this, R.layout.dialog_feedback)
        val customTheme = ThemeStore.currentTheme()
        
        sheet.setTitle(getString(R.string.send_feedback))
        
        val editTextEmail = sheet.findViewById<EditText>(R.id.editTextEmail)
        val editTextMessage = sheet.findViewById<EditText>(R.id.editTextMessage)
        val btnCancel = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnSend = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSend)

        // Pre-fill email from session
        val userEmail = SessionManager.session.value.email
        if (userEmail.isNotEmpty()) {
            editTextEmail?.setText(userEmail)
        }

        btnCancel?.setOnClickListener { sheet.dismiss() }

        btnSend?.setOnClickListener {
            val fromEmail = editTextEmail?.text.toString().trim()
            val messageText = editTextMessage?.text.toString().trim()
            
            if (messageText.isEmpty()) {
                Toast.makeText(this@ChatListActivity, R.string.enter_feedback, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sheet.dismiss()
            
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:".toUri()
                putExtra(Intent.EXTRA_EMAIL, arrayOf("ferzfrez11@gmsil.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Lavender Messenger Feedback")
                putExtra(Intent.EXTRA_TEXT, "From: $fromEmail\n\n$messageText")
            }
            startActivity(Intent.createChooser(intent, "Send Feedback"))
        }

        sheet.setOnDismissListener {
            if (!isNavigatingDeeper) onBack?.invoke()
            isNavigatingDeeper = false
        }

        sheet.show()
    }

    private fun toggleLanguage() {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val currentLang = prefs.getString("language", "ru")
        val newLang = if (currentLang == "en") "ru" else "en"

        prefs.edit { putString("language", newLang) }
        setLocale(newLang)

        recreate()
    }

    private fun setLocale(languageCode: String) {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        baseContext.resources.updateConfiguration(config, baseContext.resources.displayMetrics)
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val lang = prefs.getString("language", "ru") ?: "ru"
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private fun showSettingsSheet() {
        val sheet = StandardBottomSheet(this, R.layout.bottom_sheet_user_menu)
        val customTheme = ThemeStore.currentTheme()

        val menuUsername = sheet.findViewById<TextView>(R.id.menuUsername)
        menuUsername?.text = username

        val menuUserAvatar = sheet.findViewById<ImageView>(R.id.menuUserAvatar)
        val avatarCache = grpcClient.getAvatarCache()
        val myAvatarUrl = avatarCache[username]
        
        if (menuUserAvatar != null) {
            if (!myAvatarUrl.isNullOrEmpty()) {
                com.bumptech.glide.Glide.with(this)
                    .load(myAvatarUrl)
                    .placeholder(R.drawable.ic_default_avatar)
                    .circleCrop()
                    .into(menuUserAvatar)
                menuUserAvatar.clearColorFilter()
            } else {
                val avatarFile = File(filesDir, "avatars/$username.jpg")
                if (avatarFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(avatarFile.absolutePath)
                    if (bitmap != null) {
                        menuUserAvatar.setImageBitmap(bitmap)
                        menuUserAvatar.clipToOutline = true
                        menuUserAvatar.clearColorFilter()
                    }
                } else {
                    ThemeUtils.applyDefaultAvatar(menuUserAvatar, customTheme)
                }
            }

            menuUserAvatar.setOnClickListener {
                val fullUrl = grpcClient.getFullAvatarUrl(username) ?: myAvatarUrl
                if (!fullUrl.isNullOrEmpty()) {
                    val intent = Intent(this, FullScreenImageActivity::class.java).apply {
                        putExtra("image_url", fullUrl)
                    }
                    startActivity(intent)
                } else {
                    val avatarFile = File(filesDir, "avatars/$username.jpg")
                    if (avatarFile.exists()) {
                        val intent = Intent(this, FullScreenImageActivity::class.java).apply {
                            putExtra("image_url", avatarFile.absolutePath)
                        }
                        startActivity(intent)
                    }
                }
            }
        }

        sheet.findViewById<View>(R.id.actionShareHeader)?.setOnClickListener {
            sheet.dismiss()
            shareApp()
        }
        sheet.findViewById<View>(R.id.actionEditProfile)?.setOnClickListener {
            sheet.dismiss()
            val intent = Intent(this, EditProfileActivity::class.java).apply {
                putExtra("USERNAME", username)
            }
            editProfileLauncher.launch(intent)
        }
        sheet.findViewById<View>(R.id.actionThemes)?.setOnClickListener {
            sheet.dismiss()
            val intent = Intent(this, ThemesActivity::class.java).apply { putExtra("username", username) }
            startActivity(intent)
        }
        sheet.findViewById<View>(R.id.actionContacts)?.setOnClickListener {
            sheet.dismiss()
            val intent = Intent(this, ContactsActivity::class.java).apply {
                putExtra("USERNAME", username)
            }
            startActivity(intent)
        }
        sheet.findViewById<View>(R.id.actionAdditionalSettings)?.setOnClickListener {
            isNavigatingDeeper = true
            sheet.dismiss()
            showAdditionalSettingsSheet { showSettingsSheet() }
        }
        sheet.findViewById<View>(R.id.actionToggleLanguage)?.setOnClickListener {
            sheet.dismiss()
            toggleLanguage()
        }
        sheet.findViewById<View>(R.id.actionUpdate)?.setOnClickListener {
            sheet.dismiss()
            checkManualUpdate()
        }
        sheet.show()
    }

    private fun showAdditionalSettingsSheet(onBack: (() -> Unit)? = null) {
        val sheet = StandardBottomSheet(this, R.layout.bottom_sheet_additional_settings)
        val errorColor = "#FF5252".toColorInt()

        // Show Admin Panel only for super admins
        val isSuperAdmin = SessionManager.session.value.isSuperAdmin
        sheet.findViewById<View>(R.id.actionAdmin)?.isVisible = isSuperAdmin

        // Red tint for delete and logout
        fun tintError(id: Int) {
            val view = sheet.findViewById<ViewGroup>(id) ?: return
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child is ImageView) child.imageTintList = ColorStateList.valueOf(errorColor)
                if (child is TextView) child.setTextColor(errorColor)
            }
        }
        tintError(R.id.actionDeleteProfile)
        tintError(R.id.actionLogout)

        sheet.findViewById<View>(R.id.actionSecurity)?.setOnClickListener {
            isNavigatingDeeper = true
            sheet.dismiss()
            settingsActivityLauncher.launch(Intent(this, SecurityActivity::class.java).apply {
                putExtra("username", username)
            })
        }
        sheet.findViewById<View>(R.id.actionNotifications)?.setOnClickListener {
            isNavigatingDeeper = true
            sheet.dismiss()
            settingsActivityLauncher.launch(Intent(this, NotificationActivity::class.java))
        }
        sheet.findViewById<View>(R.id.actionLogs)?.setOnClickListener {
            isNavigatingDeeper = true
            sheet.dismiss()
            settingsActivityLauncher.launch(Intent(this, lavender.client.android.ui.LogViewerActivity::class.java))
        }
        sheet.findViewById<View>(R.id.actionClearCache)?.setOnClickListener {
            sheet.dismiss()
            clearLocalCache()
        }
        sheet.findViewById<View>(R.id.actionAbout)?.setOnClickListener {
            isNavigatingDeeper = true
            sheet.dismiss()
            showAboutDialog { showAdditionalSettingsSheet(onBack) }
        }
        sheet.findViewById<View>(R.id.actionAdmin)?.setOnClickListener {
            isNavigatingDeeper = true
            sheet.dismiss()
            settingsActivityLauncher.launch(Intent(this, SuperAdminActivity::class.java))
        }
        sheet.findViewById<View>(R.id.actionServers)?.setOnClickListener {
            isNavigatingDeeper = true
            sheet.dismiss()
            serversActivityLauncher.launch(Intent(this, ServersActivity::class.java))
        }
        sheet.findViewById<View>(R.id.actionDeleteProfile)?.setOnClickListener {
            sheet.dismiss()
            confirmDeleteProfile()
        }
        sheet.findViewById<View>(R.id.actionLogout)?.setOnClickListener {
            sheet.dismiss()
            logout()
        }

        sheet.setOnDismissListener {
            if (!isNavigatingDeeper) onBack?.invoke()
            isNavigatingDeeper = false
        }

        sheet.show()
    }

    private fun confirmDeleteProfile() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_profile)
            .setMessage(R.string.delete_profile_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteProfile()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteProfile() {
        grpcClient.deleteProfile(username) { success, _ ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, R.string.profile_deleted, Toast.LENGTH_LONG).show()
                    logout()
                } else {
                    Toast.makeText(this, R.string.failed_to_delete_profile, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun clearLocalCacheSync() {
        // Synchronous cache clearing for use during user switch in onCreate
        try {
            val db = lavender.client.android.data.db.AppDatabase.getDatabase(this)
            // Run DB operations on IO thread synchronously
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                db.messageDao().clearAll()
                db.chatDao().clearAll()
            }
            Log.d("Cache", "Cleared database for new user")
        } catch (e: Exception) {
            Log.e("Cache", "Error clearing cache", e)
        }
    }

    private fun clearLocalCache() {
        lifecycleScope.launch {
            try {
                lavender.client.android.data.cache.CacheUtils.clearAllWithGlide(this@ChatListActivity)
                Toast.makeText(this@ChatListActivity, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
                loadChats()
            } catch (e: Exception) {
                Log.e("Cache", "Error clearing cache", e)
            }
        }
    }

    private fun showChatActionSheet() {
        ActionBottomSheet(this)
            .setActions(listOf(
                SheetAction(R.id.actionAddContact, R.drawable.ic_contacts, getString(R.string.add_contact)) {
                    showAddContactDialog()
                },
                SheetAction(R.id.actionCreateChat, R.drawable.ic_play_arrow, getString(R.string.start_chat)) {
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

    private suspend fun showAIActionSheet() {
        // Wait for fresh data from server before building the sheet
        refreshAiChatsAwait()

        val allChats = currentAiChats.toMutableList().sortedBy { it.createdAt }.toMutableList()

        var sheet: AIBottomSheet? = null
        sheet = AIBottomSheet(
            context = this,
            existingChats = allChats,
            onChatClick = { chat ->
                if (chat.type == "hermes") {
                    openHermesChat(chat.id, chat.name)
                } else {
                    openOwlChat(chat.id, chat.name)
                }
            },
            onDeleteChat = { chat ->
                val userId = SessionManager.session.value.userId
                val username = SessionManager.session.value.username
                if (userId.isNotEmpty()) {
                    GrpcClient.deleteChat(chat.id, userId, username) { success, _ ->
                        if (success) {
                            // Remove from local list immediately, no network refresh needed
                            currentAiChats.removeAll { it.id == chat.id }
                            // Rebuild the sheet to reflect deleted chat, keep it open
                            runOnUiThread {
                                sheet?.removeChat(chat.id)
                                sheet?.rebuildContent()
                            }
                        }
                    }
                }
            },
            onSettingsClick = { chat ->
                if (chat.type == "hermes") {
                    openHermesSettings(chat.id)
                } else {
                    openOwlSettings(chat.id)
                }
            },
            onCreateHermesChat = {
                val hermesCount = currentAiChats.count { it.type == "hermes" }
                val chatName = getString(R.string.lava_ai_n, hermesCount + 1)
                openHermesChat("", chatName)
            },
            onCreateOwlChat = {
                val owlCount = currentAiChats.count { it.type == "owl" }
                val chatName = getString(R.string.owl_agent_n, owlCount + 1)
                openOwlChat("", chatName)
            },
            onOpenNotifications = {
                shouldShowAiSheetOnResume = true
                startActivity(Intent(this, lavender.client.android.ui.notification.NotificationActivity::class.java))
            },
            onOpenRemoteAgents = {
                shouldShowAiSheetOnResume = false
                val intent = Intent(this, lavender.client.android.ui.remote.RemoteAgentActivity::class.java)
                startActivity(intent)
            },
            unreadNotifCount = unreadNotifCount
        )
        sheet.buildAndShow()
    }

    private fun openOwlSettings(chatId: String) {
        shouldShowAiSheetOnResume = true
        val intent = Intent(this, lavender.client.android.ui.owl.OwlSettingsActivity::class.java)
        intent.putExtra("chatId", chatId)
        startActivity(intent)
    }

    private fun openHermesSettings(sessionId: String) {
        shouldShowAiSheetOnResume = true
        val intent = Intent(this, lavender.client.android.ui.owl.OwlSettingsActivity::class.java)
        intent.putExtra("sessionId", sessionId)
        intent.putExtra("isHermes", true)
        startActivity(intent)
    }

    private fun showRenameDialog(chatId: String, currentName: String) {
        val editText = android.widget.EditText(this).apply {
            setText(currentName)
            setSingleLine()
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.rename_chat))
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != currentName) {
                    val userId = SessionManager.session.value.userId
                    if (userId.isNotEmpty()) {
                        GrpcClient.renameAIChat(chatId, userId, newName) { success, error ->
                            if (success) {
                                refreshAiChats()
                            }
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun openHermesChat(chatId: String, chatName: String) {
        shouldShowAiSheetOnResume = true
        val intent = Intent(this, lavender.client.android.ui.hermes.HermesChatActivity::class.java)
        intent.putExtra("CHAT_ID", chatId)
        intent.putExtra("CHAT_NAME", chatName)
        startActivity(intent)
    }

    private fun openOwlChat(chatId: String, chatName: String) {
        shouldShowAiSheetOnResume = true
        val intent = Intent(this, lavender.client.android.ui.owl.OwlChatActivity::class.java)
        intent.putExtra("CHAT_ID", chatId)
        intent.putExtra("CHAT_NAME", chatName)
        startActivity(intent)
    }

    // Data classes for existing AI chats
    data class ExistingAiChat(val id: String, val name: String, val activeAgentId: String = "", val agentMode: String = "single")

    // Unified AI chats list (OWL + Hermes)
    private val currentAiChats = mutableListOf<AIChatInfo>()

    private fun refreshAiChats() {
        currentAiChats.clear()
        val userId = SessionManager.session.value.userId
        if (userId.isNotEmpty()) {
            GrpcClient.getAIChats(userId) { aiChats ->
                currentAiChats.addAll(aiChats)
            }
        }
    }

    private suspend fun refreshAiChatsAwait(): Boolean {
        return suspendCancellableCoroutine { cont ->
            currentAiChats.clear()
            val userId = SessionManager.session.value.userId
            if (userId.isEmpty()) {
                cont.resumeWith(Result.success(false))
                return@suspendCancellableCoroutine
            }
            GrpcClient.getAIChats(userId) { aiChats ->
                currentAiChats.addAll(aiChats)
                cont.resumeWith(Result.success(true))
            }
        }
    }

    private fun refreshUnreadCount() {
        val session = SessionManager.session.value
        if (session.userId.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    unreadNotifCount = grpcClient.getUnreadCount(session.userId)
                    Log.d("ChatListActivity", "Unread notifications: $unreadNotifCount")
                } catch (e: Exception) {
                    Log.e("ChatListActivity", "Failed to get unread count", e)
                }
            }
        }
    }

    private fun showCreateConferenceDialog() {
        val sheet = SearchableListBottomSheet(this)
            .setTitle(getString(R.string.conference))
            .setActionButtonText(getString(R.string.create))
            .setExtraInputVisible(true, getString(R.string.edit_topic))
            .setLoading(true)

        val userAdapter = UserAdapter(
            lifecycleScope,
            onUserClick = { selected ->
                (sheet.recyclerView?.adapter as? UserAdapter)?.toggleSelection(selected)
            },
            onSelectionChanged = { count ->
                sheet.setActionButtonEnabled(count > 0)
                sheet.setActionButtonText(if (count > 0) "${getString(R.string.create)} ($count)" else getString(R.string.create))
            },
            avatarCache = grpcClient.getAvatarCache(),
            onlineUsers = grpcClient.users.value
        )

        sheet.setAdapter(userAdapter)

        grpcClient.getContacts(username) { list ->
            runOnUiThread { 
                sheet.setLoading(false)
                userAdapter.setUsers(list) 
            }
        }

        sheet.onSearchTextChanged { query ->
            userAdapter.filter(query)
        }

        sheet.onActionClick {
            val selected = userAdapter.getSelectedUsers()
            if (selected.isEmpty()) return@onActionClick

            val topic = sheet.extraEditText?.text.toString().trim().ifEmpty { 
                val sdf = java.text.SimpleDateFormat("dd.MM", java.util.Locale.getDefault())
                getString(R.string.new_conference_format, sdf.format(java.util.Date()))
            }
            
            val participants = selected + username
            grpcClient.createGroupChat(topic, participants, username, "conference") { chatId ->
                if (chatId != null) runOnUiThread {
                    val intent = Intent(this, NewChatActivity::class.java).apply {
                        putExtra("USERNAME", username)
                        putExtra("ROOM_ID", chatId)
                        putExtra("CHAT_NAME", topic)
                        putExtra("CHAT_TYPE", "conference")
                        putExtra("PARTICIPANTS", org.json.JSONArray(participants).toString())
                        putExtra("CREATOR", username)
                    }
                    startActivity(intent)
                    sheet.dismiss()
                    refreshChatsDebounced()
                }
            }
        }
        sheet.show()
    }

    private fun showCreateChatDialog() {
        val sheet = SearchableListBottomSheet(this)
            .setTitle(getString(R.string.start_chat))
            .setActionButtonText(getString(R.string.create))
            .setExtraInputVisible(false, getString(R.string.enter_group_name))
            .setLoading(true)

        val userAdapter = UserAdapter(
            lifecycleScope,
            onUserClick = { selected ->
                (sheet.recyclerView?.adapter as? UserAdapter)?.toggleSelection(selected)
            },
            onSelectionChanged = { count ->
                sheet.setActionButtonEnabled(count > 0)
                sheet.setActionButtonText(if (count > 1) "${getString(R.string.create)} ($count)" else getString(R.string.create))
                sheet.setExtraInputVisible(count > 1)
            },
            avatarCache = grpcClient.getAvatarCache(),
            onlineUsers = grpcClient.users.value
        )

        sheet.setAdapter(userAdapter)

        grpcClient.getContacts(username) { list ->
            runOnUiThread { 
                sheet.setLoading(false)
                userAdapter.setUsers(list) 
            }
        }

        sheet.onSearchTextChanged { query ->
            userAdapter.filter(query)
        }

        sheet.onActionClick {
            val selected = userAdapter.getSelectedUsers()
            if (selected.isEmpty()) return@onActionClick

            if (selected.size == 1) {
                val targetUser = selected.first()
                grpcClient.createDirectChat(username, targetUser) { chatId ->
                    if (chatId != null) runOnUiThread {
                        val intent = Intent(this, NewChatActivity::class.java).apply {
                            putExtra("USERNAME", username)
                            putExtra("ROOM_ID", chatId); putExtra("CHAT_NAME", targetUser)
                            putExtra("IS_DIRECT", true); putExtra("PARTICIPANTS", "[\"$username\", \"$targetUser\"]")
                        }
                        startActivity(intent); sheet.dismiss()
                        refreshChatsDebounced()
                    }
                }
            } else {
                val groupName = sheet.extraEditText?.text.toString().trim().ifEmpty { getString(R.string.default_group_name) }
                val participants = selected + username
                grpcClient.createGroupChat(groupName, participants, username) { chatId ->
                    if (chatId != null) runOnUiThread {
                        val intent = Intent(this, NewChatActivity::class.java).apply {
                            putExtra("USERNAME", username)
                            putExtra("ROOM_ID", chatId); putExtra("CHAT_NAME", groupName)
                            putExtra("IS_DIRECT", false); putExtra("PARTICIPANTS", org.json.JSONArray(participants).toString())
                            putExtra("CREATOR", username)
                        }
                        startActivity(intent); sheet.dismiss()
                        refreshChatsDebounced()
                    }
                }
            }
        }
        sheet.show()
    }

    private fun showCreateSecretChatDialog() {
        val sheet = SearchableListBottomSheet(this)
            .setTitle(getString(R.string.secret_chat))
            .setActionButtonText(getString(R.string.create))
            .setLoading(true)

        val userAdapter = UserAdapter(
            lifecycleScope,
            onUserClick = { selected ->
                val clickAdapter = sheet.recyclerView?.adapter as? UserAdapter
                if (clickAdapter != null) {
                    clickAdapter.clearSelection()
                    clickAdapter.toggleSelection(selected)
                }
            },
            onSelectionChanged = { count ->
                sheet.setActionButtonEnabled(count == 1)
            },
            avatarCache = grpcClient.getAvatarCache(),
            onlineUsers = grpcClient.users.value
        )

        sheet.setAdapter(userAdapter)

        grpcClient.getContacts(username) { list ->
            runOnUiThread {
                sheet.setLoading(false)
                userAdapter.setUsers(list)
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
            val publicKey = lavender.client.android.data.crypto.E2EEManager.getPublicKeyBase64(this@ChatListActivity)

            grpcClient.createSecretChat(targetUser, publicKey) { chatId, success, message, peerKey ->
                runOnUiThread {
                    sheet.setLoading(false)
                    if (success && chatId.isNotEmpty()) {
                        sheet.dismiss()
                        // Navigate to the new secret chat
                        val intent = Intent(this@ChatListActivity, NewChatActivity::class.java).apply {
                            putExtra("USERNAME", username)
                            putExtra("ROOM_ID", chatId)
                            putExtra("CHAT_NAME", "🔒 $targetUser")
                            putExtra("CHAT_TYPE", "secret")
                            putExtra("IS_DIRECT", true)
                            putExtra("PARTICIPANTS", "[\"$username\",\"$targetUser\"]")
                            putExtra("IS_SECRET", "true")
                        }
                        startActivity(intent)
                        refreshChatsDebounced()
                    } else {
                        Toast.makeText(this@ChatListActivity, message.ifEmpty { "Failed to create secret chat" }, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        sheet.show()
    }

    private fun showAddContactDialog() {
        val sheet = SearchableListBottomSheet(this)
            .setTitle(getString(R.string.add_contact))
            .setActionButtonText(getString(R.string.add))
            .setExtraInputVisible(false)
            .setLoading(true)
            .setCreateChatCheckboxVisible(true, getString(R.string.create_direct_chat_after))

        val currentContacts = mutableSetOf<String>()

        val userAdapter = UserAdapter(
            lifecycleScope,
            onUserClick = { selected ->
                (sheet.recyclerView?.adapter as? UserAdapter)?.toggleSelection(selected)
            },
            onSelectionChanged = { count ->
                sheet.setActionButtonEnabled(count > 0)
                sheet.setActionButtonText(if (count > 0) "${getString(R.string.add)} ($count)" else getString(R.string.add))
            },
            avatarCache = grpcClient.getAvatarCache(),
            onlineUsers = grpcClient.users.value
        )

        sheet.setAdapter(userAdapter)

        grpcClient.getContacts(username) { list ->
            currentContacts.clear()
            currentContacts.addAll(list)

            // Now that we have contacts, load/collect all users
            val usersJob = lifecycleScope.launch {
                grpcClient.allUsers.collect { users ->
                    val filteredUsers = users.filter { it.username != username && !currentContacts.contains(it.username) }.map { it.username }
                    withContext(Dispatchers.Main) {
                        sheet.setLoading(false)
                        userAdapter.setUsers(filteredUsers)
                    }
                }
            }
            sheet.setOnDismissListener { usersJob.cancel() }
            grpcClient.loadAllUsers()
        }

        sheet.onSearchTextChanged { query ->
            userAdapter.filter(query)
        }

        sheet.onActionClick {
            val selected = userAdapter.getSelectedUsers()
            if (selected.isNotEmpty()) {
                val createChat = sheet.isCreateChatChecked()
                var completed = 0
                val total = selected.size
                selected.forEach { contact ->
                    grpcClient.addContact(username, contact) { _, _ ->
                        completed++
                        if (completed == total) {
                            runOnUiThread {
                                Toast.makeText(this, R.string.contact_added, Toast.LENGTH_SHORT).show()
                                sheet.dismiss()
                                if (createChat && selected.size == 1) {
                                    // Show splash and create direct chat, then navigate
                                    try {
                                        startActivity(Intent(this, SplashLoadingActivity::class.java))
                                    } catch (_: Exception) {}
                                    val targetUser = selected.first()
                                    grpcClient.createDirectChat(username, targetUser) { chatId ->
                                        runOnUiThread {
                                            SplashLoadingActivity.finishIfShowing()
                                            if (chatId != null && chatId.isNotEmpty()) {
                                                Toast.makeText(this, getString(R.string.chat_created_with, targetUser), Toast.LENGTH_SHORT).show()
                                                val intent = Intent(this, NewChatActivity::class.java).apply {
                                                    putExtra("USERNAME", username)
                                                    putExtra("ROOM_ID", chatId)
                                                }
                                                startActivity(intent)
                                            } else {
                                                Toast.makeText(this, R.string.failed_to_create_chat, Toast.LENGTH_SHORT).show()
                                                refreshChatsDebounced()
                                            }
                                        }
                                    }
                                } else {
                                    refreshChatsDebounced()
                                }
                            }
                        }
                    }
                }
            }
        }
        sheet.show()
    }

    private fun getAuthTheme(): Theme {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val themeId = prefs.getString("current_theme_id", "dark") ?: "dark"
        return when (themeId) {
            "dark" -> lavender.client.android.theme.BuiltInThemes.dark
            "light" -> lavender.client.android.theme.BuiltInThemes.BASE_LIGHT
            else -> lavender.client.android.theme.BuiltInThemes.findById(themeId) ?: lavender.client.android.theme.BuiltInThemes.dark
        }
    }

    private fun showLoginBottomSheet() {
        val customTheme = getAuthTheme()
        var isTransitioning = false

        lateinit var loginSheet: LoginBottomSheet

        loginSheet = LoginBottomSheet(
            context = this,
            onLogin = { u: String, p: String ->
                val serverAddress = CredentialStore.getServerAddress(this).ifEmpty { "13.140.25.249:50051" }

                // Clear local cache silently on successful login
                clearAllCache()

                // Show splash overlay during login
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
                                Toast.makeText(this, R.string.connection_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            },
            onCancel = {
                isTransitioning = true
                loginSheet.dismiss()
                showAuthChoiceDialog()
            },
            theme = customTheme
        )

        loginSheet.setOnDismissListener {
            if (!isTransitioning && (username.isEmpty() || password.isEmpty())) {
                showAuthChoiceDialog()
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

    private fun showRegisterBottomSheet(prefillUser: String = "", prefillPass: String = "") {
        val customTheme = getAuthTheme()

        val serverAddress = CredentialStore.getServerAddress(this).ifEmpty { "13.140.25.249:50051" }

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
                            "REGISTRATION_SUCCESS" -> {
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
            },
            prefillUsername = prefillUser,
            prefillPassword = prefillPass,
            theme = customTheme
        )

        registerSheet.setOnDismissListener {
            if (!isTransitioning && (username.isEmpty() || password.isEmpty())) {
                showAuthChoiceDialog()
            }
        }

        registerSheet.show()
    }

    private fun showForgotPasswordBottomSheet() {
        val customTheme = getAuthTheme()
        val sheet = StandardBottomSheet(this, R.layout.bottom_sheet_forgot_password, customTheme)

        val emailInputLayout = sheet.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.emailInputLayout)
        val editTextEmail = sheet.findViewById<EditText>(R.id.editTextEmail)
        val sendProgressBar = sheet.findViewById<ProgressBar>(R.id.sendProgressBar)
        val btnSend = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSend)
        val btnCancel = sheet.findViewById<Button>(R.id.btnCancel)
        val tokenInputLayout = sheet.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tokenInputLayout)
        val newPasswordInputLayout = sheet.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.newPasswordInputLayout)
        val editTextToken = sheet.findViewById<EditText>(R.id.editTextToken)
        val editTextNewPassword = sheet.findViewById<EditText>(R.id.editTextNewPassword)

        var isTransitioning = false
        btnCancel?.setOnClickListener {
            isTransitioning = true
            sheet.dismiss()
            showAuthChoiceDialog()
        }

        sheet.setOnDismissListener {
            if (!isTransitioning && (username.isEmpty() || password.isEmpty())) {
                showAuthChoiceDialog()
            }
        }

        var isStep2 = false
        btnSend?.setOnClickListener {
            if (!isStep2) {
                val email = editTextEmail?.text.toString().trim()
                if (email.isNotEmpty()) {
                    sendProgressBar?.isVisible = true
                    btnSend.isEnabled = false
                    
                    lifecycleScope.launch(Dispatchers.IO) {
                        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                        val serverAddress = prefs.getString("server_address", "159.195.38.145:50051") ?: "159.195.38.145:50051"
                        
                        if (serverAddress.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                sendProgressBar?.isVisible = false
                                btnSend.isEnabled = true
                                Toast.makeText(this@ChatListActivity, R.string.connection_failed, Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }

                        if (grpcClient.connectionStatus.value != ConnectionStatus.READY) {
                            val parts = serverAddress.split(":")
                            val host = parts[0]
                            val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
                            grpcClient.connect(host, false, port, this@ChatListActivity)
                            
                            withTimeoutOrNull(5000) {
                                grpcClient.connectionStatus.first { it == ConnectionStatus.READY || it == ConnectionStatus.FAILED }
                            }
                        }

                        if (grpcClient.connectionStatus.value == ConnectionStatus.READY) {
                            grpcClient.requestPasswordReset(email) { success, message ->
                                runOnUiThread {
                                    sendProgressBar?.isVisible = false
                                    btnSend.isEnabled = true
                                    if (success) {
                                        isStep2 = true
                                        emailInputLayout?.isVisible = false
                                        tokenInputLayout?.isVisible = true
                                        newPasswordInputLayout?.isVisible = true
                                        btnSend.text = getString(R.string.reset_password)
                                        Toast.makeText(this@ChatListActivity, R.string.code_sent_to_email, Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(this@ChatListActivity, message.takeIf { !it.isNullOrEmpty() } ?: getString(R.string.connection_failed), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                sendProgressBar?.isVisible = false
                                btnSend.isEnabled = true
                                Toast.makeText(this@ChatListActivity, R.string.connection_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, R.string.enter_email, Toast.LENGTH_LONG).show()
                }
            } else {
                val token = editTextToken?.text.toString().trim()
                val newPw = editTextNewPassword?.text.toString().trim()
                
                if (token.isEmpty()) {
                    Toast.makeText(this, R.string.enter_code, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (newPw.isEmpty()) {
                    Toast.makeText(this, R.string.password_empty, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                sendProgressBar?.isVisible = true
                btnSend.isEnabled = false

                lifecycleScope.launch(Dispatchers.IO) {
                    grpcClient.resetPassword(token, newPw) { success, message ->
                        runOnUiThread {
                            sendProgressBar?.isVisible = false
                            btnSend.isEnabled = true
                            if (success) {
                                Toast.makeText(this@ChatListActivity, R.string.password_changed, Toast.LENGTH_LONG).show()
                                isTransitioning = true
                                sheet.dismiss()
                            } else {
                                Toast.makeText(this@ChatListActivity, message.takeIf { !it.isNullOrEmpty() } ?: getString(R.string.connection_failed), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
        sheet.show()
    }

    private fun showAuthChoiceDialog() {
        val serverName = getString(R.string.server_default_name)
        val serverHost = "13.140.25.249"
        val serverPort = 50051

        var isTransitioning = false

        lateinit var authSheet: ServerAuthBottomSheet

        authSheet = ServerAuthBottomSheet(
            context = this,
            serverName = serverName,
            serverHost = serverHost,
            serverPort = serverPort,
            onLogin = {
                isTransitioning = true
                authSheet.dismiss()
                showLoginBottomSheet()
            },
            onRegister = {
                isTransitioning = true
                authSheet.dismiss()
                showRegisterBottomSheet()
            }
        )
        authSheet.setOnDismissListener {
            if (!isTransitioning && (username.isEmpty() || password.isEmpty())) {
                showAuthChoiceDialog()
            }
        }
        authSheet.show()
    }

    /** Clear all local cache silently on successful login. */
    private fun clearAllCache() {
        lavender.client.android.data.cache.CacheUtils.clearAllSync(this)
    }
}
