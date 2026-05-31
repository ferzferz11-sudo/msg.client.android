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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
import lavender.client.android.ui.widget.StandardBottomSheet
import lavender.client.android.ui.widget.ActionBottomSheet
import lavender.client.android.ui.widget.SearchableListBottomSheet
import lavender.client.android.ui.widget.SheetAction
import lavender.client.android.ui.widget.WidgetManager

class ChatListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatListBinding
    private lateinit var chatAdapter: ChatAdapter
    private val grpcClient = GrpcClient
    private lateinit var updateManager: UpdateManager
    private lateinit var username: String
    private lateinit var password: String
    private val chats = mutableListOf<ChatInfo>()
    private val pendingDeletions = java.util.Collections.synchronizedSet(mutableSetOf<String>())

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

        // Logo tap → open website
        binding.logoImage?.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://13.140.25.249/"))
                startActivity(intent)
            } catch (_: Exception) { }
        }

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

        val previousUsername = prefs.getString("last_logged_username", "")
        val isNewUser = previousUsername != username

        if (isNewUser) {
            // Clear cache for new user to prevent showing previous user's chats
            clearLocalCacheSync()
            // Save current username as last logged
            prefs.edit { putString("last_logged_username", username) }
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
                        binding.updateProgressText.text = getString(R.string.percent_format, progress)
                        binding.updateProgressText.isVisible = progress > 0
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

        // Show onboarding tips for new users (within 24 hours of registration)
        setupOnboardingTips()

        chatAdapter = ChatAdapter(
            lifecycleScope,
            onChatClick = { chat ->
                // OWL AI virtual chat
                if (chat.id.startsWith("owl-")) {
                    val intent = Intent(this, OwlActivity::class.java)
                    intent.putExtra("CHAT_ID", chat.id)
                    startActivity(intent)
                    return@ChatAdapter
                }

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
                binding.toolbarTitle.text = if (hasSelection) getString(R.string.selected_count, count) else getString(R.string.chats)
                supportActionBar?.setDisplayHomeAsUpEnabled(hasSelection || binding.searchCard.isVisible)
                supportActionBar?.setHomeAsUpIndicator(if (hasSelection || binding.searchCard.isVisible) R.drawable.ic_close else 0)
                
                binding.actionDelete.isVisible = hasSelection
                binding.actionMute.isVisible = hasSelection
                
                // Show edit icon only if ONE group chat is selected and user is creator
                val selected = chatAdapter.getSelectedChats()
                val canEdit = selected.size == 1 && (selected[0].type == "group" || selected[0].type == "general") && selected[0].creator == username
                binding.actionEdit.isVisible = canEdit

                binding.actionSearch.isVisible = !hasSelection && !binding.searchCard.isVisible
                binding.toolbarUserAvatar.isVisible = !hasSelection && !binding.searchCard.isVisible

                updateUpdateIndicatorVisibility()
            },
            currentUsername = username,
            initialAvatarCache = grpcClient.getAvatarCache(),
            onlineUsers = grpcClient.users.value
        )
        binding.chatsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatListActivity)
            adapter = chatAdapter
        }

        // Handle bottom navigation bar insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Add padding to the bottom of the RecyclerView so the last item is visible
            // We add extra padding to account for the FAB
            binding.chatsRecyclerView.updatePadding(
                bottom = systemBars.bottom + (80 * resources.displayMetrics.density).toInt()
            )
            
            // Adjust FAB margin to be above the navigation bar
            binding.addChatFab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = systemBars.bottom + (16 * resources.displayMetrics.density).toInt()
                marginEnd = (16 * resources.displayMetrics.density).toInt()
            }
            
            insets
        }

        binding.addChatFab.setOnClickListener {
            showChatActionSheet()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadChats(skipCache = true)
        }

        binding.toolbarUserAvatar.setOnClickListener {
            showSettingsSheet()
        }

        binding.toolbarTitle.setOnClickListener {
            showSettingsSheet()
        }

        binding.actionSettings.setOnClickListener {
            showSettingsSheet()
        }
        
        binding.actionSearch.setOnClickListener {
            showSearchBar()
        }

        binding.toolbar.setNavigationOnClickListener {
            if (binding.searchCard.isVisible) {
                hideSearchBar()
            } else if (chatAdapter.getSelectedChats().isNotEmpty()) {
                chatAdapter.clearSelection()
            }
        }

        binding.actionDelete.setOnClickListener {
            val selected = chatAdapter.getSelectedChats()
            if (selected.isNotEmpty()) {
                confirmDeleteSelectedChats(selected)
            }
        }

        binding.actionMute.setOnClickListener {
            val selected = chatAdapter.getSelectedChats()
            if (selected.isNotEmpty()) {
                toggleMuteSelectedChats(selected)
            }
        }

        binding.actionEdit.setOnClickListener {
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

        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                chatAdapter.filter(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        updateToolbarAvatar()
        
        lifecycleScope.launch {
            grpcClient.connectionStatus.collect { status ->
                val isConnecting = status == ConnectionStatus.CONNECTING
                val isFailed = status == ConnectionStatus.FAILED
                
                if (chatAdapter.getSelectedChats().isEmpty()) {
                    binding.toolbarTitle.text = getString(R.string.chats)
                    
                    when {
                        isConnecting -> {
                            binding.toolbarSubtitle.text = getString(R.string.connecting)
                            binding.toolbarSubtitle.isVisible = true
                        }
                        isFailed -> {
                            binding.toolbarSubtitle.text = getString(R.string.waiting_for_network)
                            binding.toolbarSubtitle.isVisible = true
                        }
                        else -> {
                            binding.toolbarSubtitle.isVisible = false
                        }
                    }
                } else {
                    binding.toolbarSubtitle.isVisible = false
                }

                if (status == ConnectionStatus.READY) {
                    if (username.isNotEmpty() && password.isNotEmpty() && !lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground) {
                        val session = SessionManager.session.value
                        // Only start chat background stream if we are actually on this screen
                        // and no other room is active
                        grpcClient.startChat(username, password, "", deviceId = session.deviceId, deviceName = session.deviceName) { /* onMessageReceived */ }
                        loadChats()
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
        
        startSync()

        lifecycleScope.launch {
            SessionManager.logoutEvent.collect {
                runOnUiThread {
                    Toast.makeText(this@ChatListActivity, "Сессия завершена", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this@ChatListActivity, "Сессия завершена", Toast.LENGTH_LONG).show()
                    logout()
                }
            }
        }

        intent.getStringExtra("START_DELETION_ID")?.let { performDirectDeletion(it) }
        intent.getStringExtra("DELETING_CHAT_ID")?.let { chatId ->
            chatAdapter.setChatDeleting(chatId, true)
        }
        loadChats()
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
        binding.searchEditText.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.searchEditText, 0)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)
        
        binding.actionSearch.isVisible = false
        binding.toolbarUserAvatar.isVisible = false
        updateUpdateIndicatorVisibility()
    }

    private fun hideSearchBar() {
        binding.searchCard.isVisible = false
        binding.searchEditText.text?.clear()
        chatAdapter.filter("")
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
        
        val hasSelection = chatAdapter.getSelectedChats().isNotEmpty()
        supportActionBar?.setDisplayHomeAsUpEnabled(hasSelection)
        supportActionBar?.setHomeAsUpIndicator(if (hasSelection) R.drawable.ic_close else 0)
        binding.actionSearch.isVisible = !hasSelection
        binding.toolbarUserAvatar.isVisible = !hasSelection
        binding.toolbarTitle.text = if (hasSelection) getString(R.string.selected_count, chatAdapter.getSelectedChats().size) else getString(R.string.chats)
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
                .into(binding.toolbarUserAvatar)
            binding.toolbarUserAvatar.clearColorFilter()
        } else {
            val avatarFile = File(filesDir, "avatars/$username.jpg")
            if (avatarFile.exists()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(avatarFile.absolutePath)
                    if (bitmap != null) {
                        binding.toolbarUserAvatar.setImageBitmap(bitmap)
                        binding.toolbarUserAvatar.clearColorFilter()
                    } else {
                        ThemeUtils.applyDefaultAvatar(binding.toolbarUserAvatar, currentTheme)
                    }
                } catch (e: Exception) {
                    Log.e("ChatListActivity", "Error loading avatar for toolbar", e)
                    ThemeUtils.applyDefaultAvatar(binding.toolbarUserAvatar, currentTheme)
                }
            } else {
                ThemeUtils.applyDefaultAvatar(binding.toolbarUserAvatar, currentTheme)
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

        Log.d("ChatListActivity", "Loading chats for $username (skipCache: $skipCache)")

        binding.swipeRefreshLayout.isRefreshing = true

        // Add timeout to prevent infinite loading
        val loadTimeout = lifecycleScope.launch {
            delay(10000) // 10 second timeout
            if (binding.swipeRefreshLayout.isRefreshing) {
                Log.w("ChatListActivity", "Load chats timeout, stopping refresh")
                runOnUiThread {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }

        // Clear local cache on full refresh
        if (skipCache) {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = lavender.client.android.data.db.AppDatabase.getDatabase(this@ChatListActivity)
                db.chatDao().clearAll()
            }
        }

        // 1. Immediately ensure Favorites is in the list to avoid flickering
        if (chats.none { it.id == "favorites" }) {
            chats.add(0, ChatInfo(
                id = "favorites",
                name = getString(R.string.favorites),
                type = "favorites",
                lastMessageText = getString(R.string.favorites_description),
                lastMessageTime = 0L
            ))
            chatAdapter.setChats(chats.toList())
        }

        grpcClient.getChats(username, skipCache = skipCache) { fetchedChats ->
            loadTimeout.cancel()
            runOnUiThread {
                binding.swipeRefreshLayout.isRefreshing = false
                chats.clear()

                // Always add Favorites at the top
                chats.add(
                    ChatInfo(
                        id = "favorites",
                        name = getString(R.string.favorites),
                        type = "favorites",
                        lastMessageText = getString(R.string.favorites_description),
                        lastMessageTime = 0L
                    )
                )

                val chatsWithMute = fetchedChats
                    .filter { !pendingDeletions.contains(it.id) }

                // Clean up pendingDeletions that are no longer on server
                val serverIds = fetchedChats.map { it.id }.toSet()
                pendingDeletions.removeAll { !serverIds.contains(it) }

                chats.addAll(chatsWithMute)
                chatAdapter.setChats(chats.toList())

                // If user has real chats (more than just favorites), mark onboarding as completed
                if (fetchedChats.isNotEmpty()) {
                    val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                    if (!prefs.getBoolean("onboarding_completed_$username", false)) {
                        prefs.edit { putBoolean("onboarding_completed_$username", true) }
                        hideOnboardingTips()
                    }
                }

                val totalUnread = chats.sumOf { it.unreadCount }
                updateAppIconBadge(totalUnread)

                Log.d("ChatListActivity", "Loaded ${chats.size} chats")
            }

            // Load muted chats and favorites in background (non-blocking)
            val userId = grpcClient.getUserId() ?: ""
            if (userId.isNotEmpty()) {
                grpcClient.getMutedChats { mutedChatIds ->
                    runOnUiThread {
                        val updatedChats = chats.map { chat ->
                            chat.copy(isMuted = mutedChatIds.contains(chat.id))
                        }
                        chats.clear()
                        chats.addAll(updatedChats)
                        chatAdapter.setChats(chats.toList())
                    }
                }
                grpcClient.getFavorites(userId) { _ -> }
            }

            if (fetchedChats.isEmpty()) {
                loadChatsFromCache(fetchedChats)
            }
        }
    }

    private fun loadChatsFromCache(fetchedChats: List<ChatInfo>) {
        runOnUiThread {
            binding.swipeRefreshLayout.isRefreshing = false
            chats.clear()
            
            // Always add Favorites at the top
            chats.add(
                ChatInfo(
                    id = "favorites",
                    name = getString(R.string.favorites),
                    type = "favorites",
                    lastMessageText = getString(R.string.favorites_description),
                    lastMessageTime = 0L
                )
            )

            // Load from local database if available
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val db = lavender.client.android.data.db.AppDatabase.getDatabase(this@ChatListActivity)
                    val cachedChats = db.chatDao().getAllChats()
                    
                    runOnUiThread {
                        if (cachedChats.isNotEmpty()) {
                            chats.addAll(cachedChats.map { dbChat ->
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
                            })
                        } else {
                            // If no cache, use fetchedChats if available
                            chats.addAll(fetchedChats)
                        }
                        
                        chatAdapter.setChats(chats.toList())
                        updateAppIconBadge(chats.sumOf { it.unreadCount })
                        Log.d("ChatListActivity", "Loaded ${chats.size} chats from cache")
                        updateUpdateIndicatorVisibility()
                    }
                } catch (e: Exception) {
                    Log.e("ChatListActivity", "Error loading from cache", e)
                    runOnUiThread {
                        // Fallback to fetchedChats if cache fails
                        chats.addAll(fetchedChats)
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
            text = "Системное уведомление"
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
        binding.updateContainer.isVisible = (isDownloaded || isDownloading) && !hasSelection && !isSearching
        
        if (isDownloading) {
            binding.updateAvailableIcon.setImageResource(R.drawable.ic_update_rotating)
            val rotation = AnimationUtils.loadAnimation(this, R.anim.rotate_renew)
            binding.updateAvailableIcon.startAnimation(rotation)
            binding.updateAvailableIcon.contentDescription = "Downloading..."
        } else {
            binding.updateAvailableIcon.clearAnimation()
            binding.updateProgressText.isVisible = false
            if (isDownloaded) {
                binding.updateAvailableIcon.setImageResource(R.drawable.ic_install_update)
                binding.updateAvailableIcon.contentDescription = getString(R.string.install_update)
            } else {
                binding.updateAvailableIcon.setImageResource(R.drawable.ic_update_available)
                binding.updateAvailableIcon.contentDescription = getString(R.string.update_available)
            }
        }
        
        binding.updateContainer.setOnClickListener {
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

    private fun setupOnboardingTips() {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val firstLoginKey = "first_login_$username"
        val registrationTime = prefs.getLong(firstLoginKey, 0)
        val completed = prefs.getBoolean("onboarding_completed_$username", false)

        // Only show tips within 24 hours of registration and if not completed
        if (!completed && registrationTime > 0) {
            val hoursSinceRegistration = (System.currentTimeMillis() - registrationTime) / (1000 * 60 * 60)
            if (hoursSinceRegistration < 24) {
                showOnboardingTips()
            } else {
                hideOnboardingTips()
            }
        } else {
            hideOnboardingTips()
        }
    }

    private fun showOnboardingTips() {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val profileHintShown = prefs.getBoolean("onboarding_profile_shown_$username", false)
        val fabHintShown = prefs.getBoolean("onboarding_fab_shown_$username", false)

        val versionName = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
        binding.welcomeVersionText.text = "v$versionName"

        // Show welcome container for new users
        binding.welcomeContainer.isVisible = true
        binding.chatsRecyclerView.isVisible = false

        // Dismiss welcome when user clicks anywhere
        binding.welcomeContainer.setOnClickListener {
            binding.welcomeContainer.isVisible = false
            binding.chatsRecyclerView.isVisible = true

            // Show profile hint after welcome dismissed
            if (!profileHintShown) {
                binding.onboardingProfileBubble.isVisible = true
                prefs.edit { putBoolean("onboarding_profile_shown_$username", true) }

                // Dismiss profile hint on click
                binding.onboardingProfileBubble.setOnClickListener {
                    binding.onboardingProfileBubble.isVisible = false

                    // Show FAB hint after profile hint dismissed
                    if (!fabHintShown) {
                        binding.onboardingFabBubble.isVisible = true
                        prefs.edit { putBoolean("onboarding_fab_shown_$username", true) }

                        // Dismiss FAB hint on click
                        binding.onboardingFabBubble.setOnClickListener {
                            binding.onboardingFabBubble.isVisible = false
                            prefs.edit { putBoolean("onboarding_completed_$username", true) }
                        }
                    } else {
                        prefs.edit { putBoolean("onboarding_completed_$username", true) }
                    }
                }
            } else if (!fabHintShown) {
                binding.onboardingFabBubble.isVisible = true
                prefs.edit { putBoolean("onboarding_fab_shown_$username", true) }

                binding.onboardingFabBubble.setOnClickListener {
                    binding.onboardingFabBubble.isVisible = false
                    prefs.edit { putBoolean("onboarding_completed_$username", true) }
                }
            } else {
                prefs.edit { putBoolean("onboarding_completed_$username", true) }
            }
        }
    }

    private fun hideOnboardingTips() {
        binding.welcomeContainer.isVisible = false
        binding.onboardingProfileBubble.isVisible = false
        binding.onboardingFabBubble.isVisible = false
        binding.chatsRecyclerView.isVisible = true
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
        syncJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(5000) // Poll every 5 seconds

                val currentUserId = GrpcClient.getUserId() ?: ""
                
                grpcClient.getChats(username, skipCache = true) { fetchedChats ->
                    if (currentUserId.isNotEmpty()) {
                        grpcClient.getMutedChats { mutedChatIds ->
                            grpcClient.getFavorites(currentUserId) { _ ->
                                val newFullList = mutableListOf<ChatInfo>()
                                
                                // Always add Favorites
                                newFullList.add(
                                    ChatInfo(
                                        id = "favorites",
                                        name = getString(R.string.favorites),
                                        type = "favorites",
                                        lastMessageText = getString(R.string.favorites_description),
                                        lastMessageTime = 0L
                                    )
                                )

                                val chatsWithMute = fetchedChats
                                    .filter { !pendingDeletions.contains(it.id) }
                                    .map { chat ->
                                        chat.copy(isMuted = mutedChatIds.contains(chat.id))
                                    }
                                
                                // Clean up pendingDeletions
                                val serverIds = fetchedChats.map { it.id }.toSet()
                                pendingDeletions.removeAll { !serverIds.contains(it) }
                                
                                newFullList.addAll(chatsWithMute)

                                // Check for actual changes including Favorites and Mute status
                                val hasChanges = newFullList.size != chats.size ||
                                        newFullList.indices.any { i ->
                                            val n = newFullList[i]
                                            val c = chats.getOrNull(i)
                                            c == null || n.id != c.id || n.lastMessageTime != c.lastMessageTime ||
                                                    n.unreadCount != c.unreadCount || n.isMuted != c.isMuted
                                        }

                                if (hasChanges) {
                                    runOnUiThread {
                                        chats.clear()
                                        chats.addAll(newFullList)
                                        chatAdapter.setChats(chats.toList())
                                        updateAppIconBadge(chats.sumOf { it.unreadCount })
                                    }
                                }
                            }
                        }
                    } else {
                        // Fallback if no userId
                        val newFullList = mutableListOf<ChatInfo>()
                        newFullList.add(
                            ChatInfo(
                                id = "favorites",
                                name = getString(R.string.favorites),
                                type = "favorites",
                                lastMessageText = getString(R.string.favorites_description),
                                        lastMessageTime = 0L
                            )
                        )
                        
                        val filteredFetched = fetchedChats.filter { !pendingDeletions.contains(it.id) }
                        newFullList.addAll(filteredFetched)

                        if (newFullList.size != chats.size || newFullList.indices.any { i -> chats.getOrNull(i)?.id != newFullList[i].id }) {
                            runOnUiThread {
                                chats.clear()
                                chats.addAll(newFullList)
                                chatAdapter.setChats(chats.toList())
                                updateAppIconBadge(chats.sumOf { it.unreadCount })
                            }
                        }
                    }
                }
            }
        }
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
        }
        
        updateUpdateIndicatorVisibility()

        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false

        // Ensure connection is active if we have a server address
        val currentStatus = grpcClient.connectionStatus.value
        Log.d("ChatListActivity", "onResume: connectionStatus=$currentStatus")
        val needsReconnect = currentStatus == ConnectionStatus.DISCONNECTED ||
                           currentStatus == ConnectionStatus.FAILED ||
                           grpcClient.shouldForceReconnect()

        if (needsReconnect) {
            val serverAddress = intent.getStringExtra("SERVER_ADDRESS")
                ?: getSharedPreferences("lavender_prefs", MODE_PRIVATE).getString("server_address", "")

            if (!serverAddress.isNullOrEmpty()) {
                val parts = serverAddress.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
                Log.d("ChatListActivity", "onResume: reconnecting to $host:$port")
                grpcClient.connect(host, false, port, this, true)
            }
        }

        // Reload chats when returning from another activity.
        // The connection may have dropped while we were away (e.g. OWL activity
        // held the gRPC channel and keepalive failed). The Flow collector in
        // onCreate will call loadChats() when status becomes READY, but only
        // if the channel actually reconnects. As a safety net, if after 3 seconds
        // the status is still not READY, force a reconnect.
        lifecycleScope.launch {
            var waited = 0
            while (waited < 3000) {
                delay(500)
                waited += 500
                if (grpcClient.connectionStatus.value == ConnectionStatus.READY) {
                    loadChats()
                    return@launch
                }
            }
            // Still not ready after 3s — force reconnect
            if (grpcClient.connectionStatus.value != ConnectionStatus.READY) {
                Log.d("ChatListActivity", "onResume: connection still not READY after 3s, forcing reconnect")
                val serverAddress = intent.getStringExtra("SERVER_ADDRESS")
                    ?: getSharedPreferences("lavender_prefs", MODE_PRIVATE).getString("server_address", "")
                if (!serverAddress.isNullOrEmpty()) {
                    val parts = serverAddress.split(":")
                    grpcClient.connect(parts[0], false, parts.getOrNull(1)?.toIntOrNull() ?: 50051, this@ChatListActivity, true)
                }
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
        SessionManager.logout(this)

        // Save current theme to SharedPreferences before logout
        val currentTheme = ThemeStore.currentTheme()
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        prefs.edit {
            putString("current_theme_id", currentTheme.id)
        }

        // Clear username and password
        username = ""
        password = ""

        // Show auth choice dialog
        showAuthChoiceDialog()
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

    private val serversActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // When returning from ServersActivity, check if the server changed
        val newServer = CredentialStore.getServerAddress(this)
        if (newServer.isNotEmpty() && newServer != grpcClient.currentServerAddress) {
            val parts = newServer.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
            // Force reconnect with saved credentials
            if (::username.isInitialized && ::password.isInitialized) {
                // Store the new server address
                CredentialStore.setServerAddress(this, newServer)
                // Reconnect
                grpcClient.connect(host, false, port, this, forceReconnect = true)
                // Trigger auto-login with new server
                SessionManager.login(this, username, password, newServer, register = false) { _ ->
                    runOnUiThread { loadChats(); startSync() }
                }
            }
        }
        showAdditionalSettingsSheet { showSettingsSheet() }
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
            sheet.dismiss()
            checkAnnouncements()
            showWhatsNewDialog { showAboutDialog(onBack) }
        }

        btnFeedback?.setOnClickListener {
            isNavigatingDeeper = true
            sheet.dismiss()
            showFeedbackDialog { showAboutDialog(onBack) }
        }
        
        btnShare?.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Check out Lavender Messenger!")
            }
            startActivity(Intent.createChooser(shareIntent, "Share App"))
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
        // Servers are available for all users
        val isSuperAdmin = SessionManager.session.value.isSuperAdmin
        sheet.findViewById<View>(R.id.actionAdmin)?.isVisible = isSuperAdmin
        sheet.findViewById<View>(R.id.actionServers)?.isVisible = true

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
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Clear Room database using DAOs
                val db = lavender.client.android.data.db.AppDatabase.getDatabase(this@ChatListActivity)
                db.messageDao().clearAll()
                db.chatDao().clearAll()

                // 2. Clear Glide disk cache
                com.bumptech.glide.Glide.get(this@ChatListActivity).clearDiskCache()
                
                withContext(Dispatchers.Main) {
                    // 3. Clear Glide memory cache
                    com.bumptech.glide.Glide.get(this@ChatListActivity).clearMemory()
                    
                    Toast.makeText(this@ChatListActivity, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
                    
                    // Reload chats to refresh from server
                    loadChats()
                }
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
                SheetAction(R.id.actionCreateConference, R.drawable.ic_videocam_on, getString(R.string.conference)) {
                    showCreateConferenceDialog()
                },
                SheetAction(R.id.actionOwlChat, R.drawable.ic_notification_logo, "Чат с AI") {
                    createNewOwlChat()
                }
            )).show()
    }

    private fun createNewOwlChat() {
        val uid = username
        if (uid.isEmpty()) {
            Toast.makeText(this, "Необходимо войти в аккаунт", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                var chatId = GrpcClient.createOwlChat(uid, "AI Chat")
                if (chatId.isEmpty()) {
                    // Channel was dead — reconnect and retry once
                    Toast.makeText(this@ChatListActivity, "Подключение...", Toast.LENGTH_SHORT).show()
                    val parts = (intent.getStringExtra("SERVER_ADDRESS")
                        ?: getSharedPreferences("lavender_prefs", MODE_PRIVATE).getString("server_address", "")).let {
                        it?.split(":")
                    }
                    if (parts != null && parts.isNotEmpty()) {
                        val host = parts[0]
                        val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
                        grpcClient.connect(host, false, port, this@ChatListActivity, true)
                        // Wait up to 5s for READY
                        var waited = 0
                        while (grpcClient.connectionStatus.value != ConnectionStatus.READY && waited < 5000) {
                            delay(500)
                            waited += 500
                        }
                    }
                    chatId = GrpcClient.createOwlChat(uid, "AI Chat")
                }
                runOnUiThread {
                    if (chatId.isNotEmpty()) {
                        // Add OWL chat to local list and cache so it appears immediately
                        val owlChat = ChatInfo(
                            id = chatId,
                            name = "🤖 Чат с AI",
                            type = "owl",
                            participants = "[\"$uid\"]",
                            creator = uid,
                            avatarUrl = "",
                            fullAvatarUrl = "",
                            unreadCount = 0
                        )
                        chats.add(0, owlChat)
                        chatAdapter.setChats(chats.toList())

                        // Persist to local cache immediately
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val db = lavender.client.android.data.db.AppDatabase.getDatabase(this@ChatListActivity)
                                db.chatDao().insertChats(listOf(owlChat.toEntity()))
                            } catch (e: Exception) {
                                android.util.Log.e("ChatListActivity", "Failed to cache OWL chat", e)
                            }
                        }

                        val intent = Intent(this@ChatListActivity, OwlActivity::class.java)
                        intent.putExtra("CHAT_ID", chatId)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@ChatListActivity, "Ошибка создания чата", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@ChatListActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    loadChats(skipCache = true)
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
                        loadChats(skipCache = true)
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
                        loadChats(skipCache = true)
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
                        loadChats(skipCache = true)
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
                var completed = 0
                selected.forEach { contact ->
                    grpcClient.addContact(username, contact) { _, _ ->
                        completed++
                        if (completed == selected.size) {
                            runOnUiThread {
                                Toast.makeText(this, R.string.contact_added, Toast.LENGTH_SHORT).show()
                                sheet.dismiss()
                                loadChats(skipCache = true)
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
        val sheet = StandardBottomSheet(this, R.layout.bottom_sheet_login, customTheme)

        val usernameInputLayout = sheet.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.usernameInputLayout)
        val passwordInputLayout = sheet.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.passwordInputLayout)
        val editText = sheet.findViewById<EditText>(R.id.editTextUsername)
        val editTextPassword = sheet.findViewById<EditText>(R.id.editTextPassword)
        val serverAddressSpinner = sheet.findViewById<Spinner>(R.id.serverAddressSpinner)
        val serverStatusLayout = sheet.findViewById<LinearLayout>(R.id.serverStatusLayout)
        val serverAddressLabel = sheet.findViewById<TextView>(R.id.serverAddressLabel)
        val joinProgressBar = sheet.findViewById<ProgressBar>(R.id.joinProgressBar)
        val btnCancel = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnJoin = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnJoin)
        val forgotPasswordButton = sheet.findViewById<TextView>(R.id.forgotPasswordButton)

        // Setup server address spinner — fetch from gRPC (public, no auth)
        val serverList = mutableListOf<String>()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, serverList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        serverAddressSpinner?.adapter = adapter
        serverAddressSpinner?.visibility = View.GONE
        serverStatusLayout?.visibility = View.GONE
        serverAddressLabel?.visibility = View.GONE

        lavender.client.android.data.grpc.GrpcClient.getServers(this) { servers ->
            runOnUiThread {
                serverList.clear()
                if (servers.isNotEmpty()) {
                    servers.forEach { serverList.add("${it.name} [${it.address}]") }
                    serverAddressSpinner?.adapter = adapter
                    serverAddressSpinner?.setSelection(0)
                    if (servers.size > 1) {
                        serverAddressSpinner?.visibility = View.VISIBLE
                        serverStatusLayout?.visibility = View.VISIBLE
                        serverAddressLabel?.visibility = View.VISIBLE
                    }
                    // Green indicator — we got servers
                    sheet.findViewById<View>(R.id.serverStatusIndicator)?.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
                }
                adapter.notifyDataSetChanged()
            }
        }

        btnCancel?.setOnClickListener {
            sheet.dismiss()
            showAuthChoiceDialog()
        }

        sheet.setOnDismissListener {
            if (username.isEmpty() || password.isEmpty()) {
                showAuthChoiceDialog()
            }
        }

        btnJoin?.setOnClickListener {
            val u = editText?.text.toString().trim()
            val p = editTextPassword?.text.toString().trim()
            val serverAddressRaw = serverAddressSpinner?.selectedItem?.toString() ?: ""
            val serverAddress = if (serverAddressRaw.contains("[") && serverAddressRaw.contains("]")) {
                serverAddressRaw.substringAfter("[").substringBefore("]")
            } else {
                serverAddressRaw
            }
            if (u.isNotEmpty() && p.isNotEmpty()) {
                btnJoin?.text = ""
                btnJoin?.isEnabled = false
                joinProgressBar?.isVisible = true

                SessionManager.login(this, u, p, serverAddress, register = false, email = "") { result ->
                    runOnUiThread {
                        when (result) {
                            "SUCCESS" -> {
                                // Store credentials securely via CredentialStore
                                lavender.client.android.data.session.CredentialStore.setCredentials(
                                    context = this@ChatListActivity,
                                    username = u,
                                    password = p,
                                    serverAddress = serverAddress
                                )
                                val userId = SessionManager.session.value.userId
                                if (userId.isNotEmpty()) {
                                    lavender.client.android.data.session.CredentialStore.setUserId(this@ChatListActivity, userId)
                                }
                                clearLocalCacheSync()
                                Toast.makeText(this@ChatListActivity, R.string.login_success, Toast.LENGTH_LONG).show()
                                sheet.dismiss()
                                recreate()
                            }
                            "USER_NOT_FOUND" -> {
                                joinProgressBar?.isVisible = false
                                btnJoin?.text = getString(R.string.join)
                                btnJoin?.isEnabled = true
                                
                                AlertDialog.Builder(this)
                                    .setTitle(R.string.user_not_found)
                                    .setMessage(getString(R.string.register_confirm, u))
                                    .setPositiveButton(R.string.yes) { _, _ ->
                                        sheet.dismiss()
                                        showRegisterBottomSheet()
                                    }
                                    .setNegativeButton(R.string.no) { _, _ ->
                                        sheet.dismiss()
                                    }
                                    .show()
                            }
                            "AUTH_FAILED" -> {
                                joinProgressBar?.isVisible = false
                                btnJoin.text = getString(R.string.join)
                                btnJoin.isEnabled = true
                                Toast.makeText(this, R.string.auth_failed, Toast.LENGTH_LONG).show()
                            }
                            else -> {
                                joinProgressBar?.isVisible = false
                                btnJoin.text = getString(R.string.join)
                                btnJoin.isEnabled = true
                                Toast.makeText(this, R.string.connection_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } else if (u.isEmpty()) {
                Toast.makeText(this, R.string.username_empty, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, R.string.password_empty, Toast.LENGTH_LONG).show()
            }
        }

        forgotPasswordButton?.setOnClickListener {
            sheet.dismiss()
            showForgotPasswordBottomSheet()
        }

        sheet.show()
    }

    private fun showRegisterBottomSheet() {
        val customTheme = getAuthTheme()
        val sheet = StandardBottomSheet(this, R.layout.bottom_sheet_register, customTheme)

        val usernameInputLayout = sheet.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.usernameInputLayout)
        val passwordInputLayout = sheet.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.passwordInputLayout)
        val confirmPasswordInputLayout = sheet.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.confirmPasswordInputLayout)
        val emailInputLayout = sheet.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.emailInputLayout)
        val editText = sheet.findViewById<EditText>(R.id.editTextUsername)
        val editTextPassword = sheet.findViewById<EditText>(R.id.editTextPassword)
        val editTextConfirmPassword = sheet.findViewById<EditText>(R.id.editTextConfirmPassword)
        val editTextEmail = sheet.findViewById<EditText>(R.id.editTextEmail)
        val serverAddressSpinner = sheet.findViewById<Spinner>(R.id.serverAddressSpinner)
        val serverStatusLayout = sheet.findViewById<LinearLayout>(R.id.serverStatusLayout)
        val serverAddressLabel = sheet.findViewById<TextView>(R.id.serverAddressLabel)
        val registerProgressBar = sheet.findViewById<ProgressBar>(R.id.registerProgressBar)
        val btnCancel = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnRegister = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRegister)

        // Setup server address spinner — fetch from gRPC (public, no auth)
        val serverList = mutableListOf<String>()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, serverList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        serverAddressSpinner?.adapter = adapter
        serverAddressSpinner?.visibility = View.GONE
        serverStatusLayout?.visibility = View.GONE
        serverAddressLabel?.visibility = View.GONE

        lavender.client.android.data.grpc.GrpcClient.getServers(this) { servers ->
            runOnUiThread {
                serverList.clear()
                if (servers.isNotEmpty()) {
                    servers.forEach { serverList.add("${it.name} [${it.address}]") }
                    serverAddressSpinner?.adapter = adapter
                    serverAddressSpinner?.setSelection(0)
                    if (servers.size > 1) {
                        serverAddressSpinner?.visibility = View.VISIBLE
                        serverStatusLayout?.visibility = View.VISIBLE
                        serverAddressLabel?.visibility = View.VISIBLE
                    }
                    // Green indicator — we got servers
                    sheet.findViewById<View>(R.id.serverStatusIndicator)?.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
                }
                adapter.notifyDataSetChanged()
            }
        }

        btnCancel?.setOnClickListener {
            sheet.dismiss()
            showAuthChoiceDialog()
        }

        sheet.setOnDismissListener {
            if (username.isEmpty() || password.isEmpty()) {
                showAuthChoiceDialog()
            }
        }

        btnRegister?.setOnClickListener {
            val u = editText?.text.toString().trim()
            val p = editTextPassword?.text.toString().trim()
            val confirmPassword = editTextConfirmPassword?.text.toString().trim()
            val email = editTextEmail?.text.toString().trim()
            val serverAddressRaw = serverAddressSpinner?.selectedItem?.toString() ?: ""
            val serverAddress = if (serverAddressRaw.contains("[") && serverAddressRaw.contains("]")) {
                serverAddressRaw.substringAfter("[").substringBefore("]")
            } else {
                serverAddressRaw
            }

            if (u.isEmpty()) {
                Toast.makeText(this, R.string.username_empty, Toast.LENGTH_LONG).show()
            } else if (p.isEmpty()) {
                Toast.makeText(this, R.string.password_empty, Toast.LENGTH_LONG).show()
            } else if (p != confirmPassword) {
                Toast.makeText(this, R.string.passwords_do_not_match, Toast.LENGTH_LONG).show()
            } else {
                btnRegister.text = ""
                btnRegister.isEnabled = false
                registerProgressBar?.isVisible = true

                SessionManager.login(this, u, p, serverAddress, register = true, email = email) { result ->
                    runOnUiThread {
                        when (result) {
                            "REGISTRATION_SUCCESS" -> {
                                // Store credentials securely via CredentialStore
                                lavender.client.android.data.session.CredentialStore.setCredentials(
                                    context = this@ChatListActivity,
                                    username = u,
                                    password = p,
                                    email = email,
                                    serverAddress = serverAddress
                                )
                                val userId = SessionManager.session.value.userId
                                if (userId.isNotEmpty()) {
                                    lavender.client.android.data.session.CredentialStore.setUserId(this@ChatListActivity, userId)
                                }
                                clearLocalCacheSync()
                                Toast.makeText(this@ChatListActivity, R.string.registration_success, Toast.LENGTH_LONG).show()
                                val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                                prefs.edit { putBoolean("onboarding_completed_$u", false); putLong("first_login_$u", System.currentTimeMillis()) }
                                sheet.dismiss(); recreate()
                            }
                            "USER_ALREADY_EXISTS" -> {
                                registerProgressBar?.isVisible = false
                                btnRegister.text = getString(R.string.register); btnRegister.isEnabled = true
                                Toast.makeText(this, R.string.user_already_exists, Toast.LENGTH_LONG).show()
                            }
                            "EMAIL_ALREADY_IN_USE" -> {
                                registerProgressBar?.isVisible = false
                                btnRegister.text = getString(R.string.register); btnRegister.isEnabled = true
                                Toast.makeText(this, R.string.email_already_in_use, Toast.LENGTH_LONG).show()
                            }
                            "AUTH_FAILED" -> {
                                registerProgressBar?.isVisible = false
                                btnRegister.text = getString(R.string.register); btnRegister.isEnabled = true
                                Toast.makeText(this, R.string.auth_failed, Toast.LENGTH_LONG).show()
                            }
                            else -> {
                                registerProgressBar?.isVisible = false
                                btnRegister.text = getString(R.string.register); btnRegister.isEnabled = true
                                Toast.makeText(this, R.string.connection_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
        sheet.show()
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

        btnCancel?.setOnClickListener {
            sheet.dismiss()
            showAuthChoiceDialog()
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
                                        btnSend.text = "Сбросить пароль"
                                        Toast.makeText(this@ChatListActivity, "Код отправлен на ваш email", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this, "Введите код", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(this@ChatListActivity, "Пароль успешно изменен", Toast.LENGTH_LONG).show()
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
        val customTheme = getAuthTheme()
        val sheet = StandardBottomSheet(this, R.layout.dialog_auth_choice, customTheme)
        
        val btnLogin = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLogin)
        val btnRegister = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRegister)
        val versionText = sheet.findViewById<TextView>(R.id.authVersionText)

        val versionName = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
        versionText?.text = "v$versionName"

        val logoImage = sheet.findViewById<ImageView>(R.id.authLogoImage)
        logoImage?.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://13.140.25.249/"))
                startActivity(intent)
            } catch (_: Exception) {}
        }

        btnLogin?.setOnClickListener {
            sheet.dismiss()
            showLoginBottomSheet()
        }

        btnRegister?.setOnClickListener {
            sheet.dismiss()
            showRegisterBottomSheet()
        }

        sheet.setCancelable(false)
        sheet.show()
    }
}
