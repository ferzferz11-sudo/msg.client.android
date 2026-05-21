package lavender.client.android

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.session.SessionManager
import lavender.client.android.data.updates.DownloadUpdateWorker
import lavender.client.android.databinding.ActivityChatListBinding
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.adapter.ChatAdapter
import lavender.client.android.ui.adapter.UserAdapter
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class ChatListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatListBinding
    private lateinit var chatAdapter: ChatAdapter
    private val grpcClient = GrpcClient
    private lateinit var username: String
    private lateinit var password: String
    private val chats = mutableListOf<ChatInfo>()
    private val pendingDeletions = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private var syncJob: Job? = null

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

        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        
        // Check if user is authenticated
        val savedUsername = prefs.getString("saved_username", "")
        val savedPassword = prefs.getString("saved_password", "")
        
        username = intent.getStringExtra("USERNAME") ?: savedUsername ?: ""
        password = intent.getStringExtra("PASSWORD") ?: savedPassword ?: ""
        val serverAddress = intent.getStringExtra("SERVER_ADDRESS") ?: prefs.getString("server_address", "159.195.38.145:50051") ?: "159.195.38.145:50051"

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
            // Check if this is first time for this username
            val firstLoginKey = "first_login_$username"
            val isFirstLogin = !prefs.contains(firstLoginKey)
            if (isFirstLogin) {
                // Mark as registered with timestamp
                prefs.edit { putLong(firstLoginKey, System.currentTimeMillis()) }
                // Show registration success message
                Toast.makeText(this, getString(R.string.registration_success), Toast.LENGTH_LONG).show()
            }
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

        // Show onboarding tips for new users (within 24 hours of registration)
        setupOnboardingTips()

        chatAdapter = ChatAdapter(
            onChatClick = { chat ->
                if (chat.type == "favorites") {
                    val intent = Intent(this, NewChatActivity::class.java).apply {
                        putExtra("USERNAME", username)
                        putExtra("PASSWORD", password)
                        putExtra("CHAT_NAME", getString(R.string.favorites))
                        putExtra("ROOM_ID", "favorites_$username")
                        putExtra("IS_DIRECT", false) // Treat as a special room
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
                    putExtra("PASSWORD", password)
                    putExtra("SERVER_ADDRESS", intent.getStringExtra("SERVER_ADDRESS") ?: "")
                    putExtra("CHAT_NAME", chat.name)
                    putExtra("ROOM_ID", chat.id)
                    putExtra("IS_DIRECT", chat.type == "direct")
                    putExtra("PARTICIPANTS", chat.participants)
                    putExtra("AVATAR_URL", chat.avatarUrl)
                    putExtra("FULL_AVATAR_URL", chat.fullAvatarUrl)
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

        binding.updateAvailableIcon.setOnClickListener {
            checkManualUpdate()
        }

        binding.actionWhatsNew.setOnClickListener {
            showWhatsNewDialog()
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
                if (isConnecting) {
                    binding.toolbarTitle.text = getString(R.string.connecting)
                } else if (chatAdapter.getSelectedChats().isEmpty()) {
                    binding.toolbarTitle.text = getString(R.string.chats)
                }

                if (status == ConnectionStatus.READY) {
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        val session = SessionManager.session.value
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
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_chats)
            .setMessage(getString(R.string.delete_chats_confirmation, selected.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                var completedCount = 0
                val totalToDelete = selected.size
                
                selected.forEach { chat ->
                    // Show immediate feedback
                    pendingDeletions.add(chat.id)
                    chatAdapter.setChatDeleting(chat.id, true)
                    
                    grpcClient.deleteChat(chat.id, username) { success, _ ->
                        runOnUiThread {
                            completedCount++
                            if (success) {
                                // Optimistically remove from local list
                                chats.removeAll { it.id == chat.id }
                            } else {
                                pendingDeletions.remove(chat.id)
                                chatAdapter.setChatDeleting(chat.id, false)
                            }
                            
                            if (completedCount == totalToDelete) {
                                chatAdapter.clearSelection()
                                chatAdapter.setChats(chats.toList())
                                updateAppIconBadge(chats.sumOf { it.unreadCount })
                                
                                // Delayed full refresh to ensure server is in sync
                                lifecycleScope.launch {
                                    delay(1000)
                                    loadChats()
                                }
                            }
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel_dialog, null)
            .show()
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
            delay(15000) // 15 second timeout
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
            val userId = grpcClient.getUserId() ?: ""
            if (userId.isNotEmpty()) {
                grpcClient.getMutedChats { mutedChatIds ->
                    grpcClient.getFavorites(userId) { _ ->
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
                                .map { chat ->
                                    chat.copy(isMuted = mutedChatIds.contains(chat.id))
                                }

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

                            // Clear searching filter or force refresh to ensure Favorites stay
                            updateUpdateIndicatorVisibility()
                            checkForUpdatesSilently()

                            // Pre-fetch avatars for all participants
                            fetchedChats.forEach { chat ->
                                try {
                                    val arr = org.json.JSONArray(chat.participants)
                                    for (i in 0 until arr.length()) {
                                        val p = arr.getString(i)
                                        if (!grpcClient.getAvatarCache().containsKey(p)) {
                                            grpcClient.getUserAvatar(p) { }
                                        }
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
            } else {
                // No userId - likely offline, load from cache
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

    private fun updateUpdateIndicatorVisibility() {
        val prefs = getSharedPreferences("UpdatePrefs", MODE_PRIVATE)
        val isAvailable = prefs.getBoolean("update_available", false)
        val hasSelection = if (::chatAdapter.isInitialized) chatAdapter.getSelectedChats().isNotEmpty() else false
        val isSearching = binding.searchCard.isVisible
        
        binding.updateAvailableIcon.isVisible = isAvailable && !hasSelection && !isSearching
        
        // Also update WhatsNew icon visibility
        val showAnnouncement = getSharedPreferences("AnnouncementPrefs", MODE_PRIVATE).getBoolean("show_icon", false)
        binding.actionWhatsNew.isVisible = showAnnouncement && !hasSelection && !isSearching
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
        lifecycleScope.launch(Dispatchers.IO) {
            checkAnnouncementsInternal()
            try {
                val url = URL("http://159.195.38.145:8081/version.txt")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val latestVersion = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                    val isAvailable = isUpdateAvailable(BuildConfig.VERSION_NAME, latestVersion)
                    
                    getSharedPreferences("UpdatePrefs", MODE_PRIVATE).edit {
                        putBoolean("update_available", isAvailable)
                        putString("latest_version", latestVersion)
                    }
                    
                    withContext(Dispatchers.Main) {
                        updateUpdateIndicatorVisibility()
                    }
                }
                connection.disconnect()
            } catch (_: Exception) {}
        }
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
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        // Only refresh theme from store if user is authenticated
        // Otherwise, keep the theme from SharedPreferences (preserved after logout)
        if (username.isNotEmpty()) {
            ThemeStore.refresh(this, username) // Force theme refresh from store when returning
        }
        
        // Only update chatAdapter if it's initialized (user is authenticated)
        if (::chatAdapter.isInitialized) {
            chatAdapter.updateAvatarCache(grpcClient.getAvatarCache())
            chatAdapter.notifyDataSetChanged() // Force redraw all visible items with new theme
        }
        
        updateUpdateIndicatorVisibility()

        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false

        // Ensure connection is active if we have a server address
        val needsReconnect = grpcClient.connectionStatus.value != ConnectionStatus.READY ||
                           grpcClient.shouldForceReconnect()

        if (needsReconnect) {
            val serverAddress = intent.getStringExtra("SERVER_ADDRESS")
                ?: getSharedPreferences("lavender_prefs", MODE_PRIVATE).getString("server_address", "")

            if (!serverAddress.isNullOrEmpty()) {
                val parts = serverAddress.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
                val forceReconnect = grpcClient.shouldForceReconnect()
                grpcClient.connect(host, false, port, this, forceReconnect)
            }
        }

        loadChats()
    }

    override fun onPause() {
        super.onPause()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = true
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Lavender Messenger")
            putExtra(Intent.EXTRA_TEXT, "http://159.195.38.145:8081/lavender.apk")
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
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("http://159.195.38.145:8081/version.txt")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val latestVersion = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(currentVersion, latestVersion)
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatListActivity, "Failed to check updates: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showUpdateDialog(current: String, latest: String) {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val customTheme = ThemeStore.currentTheme()
        ThemeApplier.applyToDialog(bottomSheet, customTheme)
        val dialogView = layoutInflater.inflate(R.layout.bottom_sheet_update, binding.root, false)
        
        val titleView = dialogView.findViewById<TextView>(R.id.updateTitle)
        val messageView = dialogView.findViewById<TextView>(R.id.updateMessage)
        val btnUpdate = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUpdate)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val dragHandle = dialogView.findViewById<View>(R.id.dragHandle)
        val updateIcon = dialogView.findViewById<ImageView>(R.id.updateIcon)

        try {
            val bgColor = customTheme.backgroundColor.toColorInt()
            val txtColor = customTheme.textPrimaryColor.toColorInt()
            val secTxtColor = customTheme.textSecondaryColor.toColorInt()
            val primColor = customTheme.primaryColor.toColorInt()
            
            dialogView.setBackgroundColor(bgColor)
            titleView.setTextColor(txtColor)
            messageView.setTextColor(secTxtColor)
            dragHandle.backgroundTintList = ColorStateList.valueOf(primColor)
            updateIcon.imageTintList = ColorStateList.valueOf(primColor)
            btnUpdate.backgroundTintList = ColorStateList.valueOf(primColor)
            btnUpdate.setTextColor(customTheme.onPrimaryColor.toColorInt())
            btnCancel.setTextColor(secTxtColor)
        } catch (_: Exception) {}

        val isAvailable = isUpdateAvailable(current, latest)
        if (!isAvailable) {
            titleView.text = getString(R.string.ok)
            updateIcon.setImageResource(R.drawable.ic_checked) // Use checkmark if already up to date
            btnUpdate.text = getString(R.string.force_download)
        }
        
        messageView.text = getString(R.string.version_info_format, current, latest)
        
        btnCancel.setOnClickListener { bottomSheet.dismiss() }
        btnUpdate.setOnClickListener {
            bottomSheet.dismiss()
            startBackgroundDownload()
        }
        
        bottomSheet.setContentView(dialogView)
        bottomSheet.show()
    }

    private suspend fun checkAnnouncementsInternal() {
        try {
            val url = URL("http://159.195.38.145:8081/changelog.txt")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val text = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                if (text.isNotEmpty()) {
                    val prefs = getSharedPreferences("AnnouncementPrefs", MODE_PRIVATE)
                    val lastRead = prefs.getString("last_read_text", "")
                    
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

    private fun showWhatsNewDialog() {
        val prefs = getSharedPreferences("AnnouncementPrefs", MODE_PRIVATE)
        val text = prefs.getString("current_text", "") ?: return
        
        val theme = ThemeStore.currentTheme()
        val pColor = theme.primaryColor.toColorInt()
        val textColor = theme.textPrimaryColor.toColorInt()

        val dialogView = layoutInflater.inflate(R.layout.dialog_whats_new, null)
        dialogView.findViewById<TextView>(R.id.tvContent).apply {
            this.text = text
            setTextColor(textColor)
        }
        dialogView.findViewById<TextView>(R.id.tvTitle).setTextColor(pColor)

        val btnClose = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnMarkRead = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnOk)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnMarkRead.apply {
            backgroundTintList = ColorStateList.valueOf(pColor)
            setTextColor(theme.onPrimaryColor.toColorInt())
            setOnClickListener {
                prefs.edit {
                    putString("last_read_text", text)
                    putBoolean("show_icon", false)
                }
                updateUpdateIndicatorVisibility()
                dialog.dismiss()
            }
        }

        btnClose.apply {
            setTextColor(theme.textSecondaryColor.toColorInt())
            setOnClickListener { dialog.dismiss() }
        }

        // Custom rounded background for dialog
        val shape = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
            floatArrayOf(24f, 24f, 24f, 24f, 24f, 24f, 24f, 24f), null, null
        ))
        shape.paint.color = theme.surfaceColor.toColorInt()
        dialog.window?.setBackgroundDrawable(shape)

        dialog.show()
    }

    private fun isUpdateAvailable(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
            val c = currentParts.getOrNull(i) ?: 0
            val l = latestParts.getOrNull(i) ?: 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun startBackgroundDownload() {
        val workRequest = OneTimeWorkRequestBuilder<DownloadUpdateWorker>()
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "update_download",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
        Toast.makeText(this, "Загрузка обновления началась в фоне", Toast.LENGTH_LONG).show()
    }

    private fun installApk(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    @SuppressLint("SetTextI18n")
    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, binding.root, false)
        val customTheme = ThemeStore.currentTheme()
        
        val clientVersionText = dialogView.findViewById<TextView>(R.id.clientVersionText)
        val serverVersionText = dialogView.findViewById<TextView>(R.id.serverVersionText)
        val btnClose = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClose)
        val btnFeedback = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFeedback)
        val btnShare = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnShare)

        try {
            val bgColor = customTheme.backgroundColor.toColorInt()
            val txtColor = customTheme.textPrimaryColor.toColorInt()
            dialogView.setBackgroundColor(bgColor)
            clientVersionText.setTextColor(txtColor)
            serverVersionText.setTextColor(txtColor)
        } catch (_: Exception) {}

        val versionName = packageManager.getPackageInfo(packageName, 0).versionName
        clientVersionText.text = "Client Version: $versionName"
        
        val serverVersion = GrpcClient.serverVersion.value
        if (serverVersion.isNotEmpty()) {
            serverVersionText.text = "Server Version: $serverVersion"
        } else {
            serverVersionText.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        btnClose.setOnClickListener { dialog.dismiss() }
        
        btnFeedback.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:".toUri()
                putExtra(Intent.EXTRA_EMAIL, arrayOf("support@lavender.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Lavender Messenger Feedback")
            }
            startActivity(Intent.createChooser(intent, "Send Feedback"))
        }
        
        btnShare.setOnClickListener {
            dialog.dismiss()
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Check out Lavender Messenger!")
            }
            startActivity(Intent.createChooser(shareIntent, "Share App"))
        }
        
        dialog.show()
    }

    private fun toggleLanguage() {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val currentLang = prefs.getString("language", "en")
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
        val lang = prefs.getString("language", "en") ?: "en"
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private fun showSettingsSheet() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val customTheme = ThemeStore.currentTheme()
        ThemeApplier.applyToDialog(bottomSheetDialog, customTheme)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_user_menu, binding.root, false)
        val actionIds = listOf(
            R.id.actionShareHeader, R.id.actionEditProfile, R.id.actionThemes,
            R.id.actionContacts, R.id.actionAdditionalSettings,
            R.id.actionToggleLanguage, R.id.actionUpdate
        )

        try {
            val bgColor = customTheme.backgroundColor.toColorInt()
            val txtColor = customTheme.textPrimaryColor.toColorInt()
            val primColor = customTheme.primaryColor.toColorInt()

            sheetView.setBackgroundColor(bgColor)
            sheetView.findViewById<View>(R.id.dragHandle)?.backgroundTintList = ColorStateList.valueOf(primColor)

            val menuUsername = sheetView.findViewById<TextView>(R.id.menuUsername)
            menuUsername.text = username
            menuUsername.setTextColor(txtColor)

            val menuUserAvatar = sheetView.findViewById<ImageView>(R.id.menuUserAvatar)
            val avatarCache = grpcClient.getAvatarCache()
            val myAvatarUrl = avatarCache[username]
            
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

            fun applyThemeToMenu(view: View, isShare: Boolean) {
                if (view is TextView) {
                    if (isShare) view.setTextColor(primColor)
                    else view.setTextColor(txtColor)
                } else if (view is ImageView) {
                    view.imageTintList = ColorStateList.valueOf(primColor)
                } else if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        applyThemeToMenu(view.getChildAt(i), isShare)
                    }
                }
            }

            actionIds.forEach { id ->
                sheetView.findViewById<View>(id)?.let { view ->
                    applyThemeToMenu(view, id == R.id.actionShareHeader)
                }
            }
        } catch (_: Exception) {
            Log.e("Theme", "Error tinting settings sheet")
        }

        sheetView.findViewById<View>(R.id.actionShareHeader).setOnClickListener {
            bottomSheetDialog.dismiss()
            shareApp()
        }
        sheetView.findViewById<View>(R.id.actionEditProfile).setOnClickListener {
            bottomSheetDialog.dismiss()
            val intent = Intent(this, EditProfileActivity::class.java).apply {
                putExtra("USERNAME", username)
                putExtra("PASSWORD", password)
            }
            editProfileLauncher.launch(intent)
        }
        sheetView.findViewById<View>(R.id.actionThemes).setOnClickListener {
            bottomSheetDialog.dismiss()
            val intent = Intent(this, ThemesActivity::class.java).apply { putExtra("username", username) }
            startActivity(intent)
        }
        sheetView.findViewById<View>(R.id.actionContacts).setOnClickListener {
            bottomSheetDialog.dismiss()
            val intent = Intent(this, ContactsActivity::class.java).apply {
                putExtra("USERNAME", username)
                putExtra("PASSWORD", password)
            }
            startActivity(intent)
        }
        sheetView.findViewById<View>(R.id.actionAdditionalSettings).setOnClickListener {
            bottomSheetDialog.dismiss()
            showAdditionalSettingsSheet()
        }
        sheetView.findViewById<View>(R.id.actionToggleLanguage).setOnClickListener {
            bottomSheetDialog.dismiss()
            toggleLanguage()
        }
        sheetView.findViewById<View>(R.id.actionUpdate).setOnClickListener {
            bottomSheetDialog.dismiss()
            checkManualUpdate()
        }
        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }

    private fun showAdditionalSettingsSheet() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val customTheme = ThemeStore.currentTheme()
        ThemeApplier.applyToDialog(bottomSheetDialog, customTheme)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_additional_settings, binding.root, false)
        val actionIds = listOf(
            R.id.actionSecurity, R.id.actionNotifications, R.id.actionClearCache, R.id.actionAbout, R.id.actionAdmin, R.id.actionServers, R.id.actionDeleteProfile, R.id.actionLogout
        )

        try {
            val bgColor = customTheme.backgroundColor.toColorInt()
            val txtColor = customTheme.textPrimaryColor.toColorInt()
            val primColor = customTheme.primaryColor.toColorInt()
            val errorColor = "#FF5252".toColorInt()

            sheetView.setBackgroundColor(bgColor)
            sheetView.findViewById<View>(R.id.dragHandle)?.backgroundTintList = ColorStateList.valueOf(primColor)

            sheetView.findViewById<TextView>(R.id.settingsTitle)?.setTextColor(txtColor)

            // Show Admin Panel and Servers only for super admins
            val isSuperAdmin = SessionManager.session.value.isSuperAdmin
            sheetView.findViewById<View>(R.id.actionAdmin).isVisible = isSuperAdmin
            sheetView.findViewById<View>(R.id.actionServers).isVisible = isSuperAdmin

            fun applyThemeToMenu(view: View, isLogout: Boolean, isDelete: Boolean) {
                if (view is TextView) {
                    if (isDelete || isLogout) view.setTextColor(errorColor)
                    else view.setTextColor(txtColor)
                } else if (view is ImageView) {
                    if (isDelete || isLogout) view.imageTintList = ColorStateList.valueOf(errorColor)
                    else view.imageTintList = ColorStateList.valueOf(primColor)
                } else if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        applyThemeToMenu(view.getChildAt(i), isLogout, isDelete)
                    }
                }
            }

            actionIds.forEach { id ->
                sheetView.findViewById<View>(id)?.let { view ->
                    applyThemeToMenu(view, id == R.id.actionLogout, id == R.id.actionDeleteProfile)
                }
            }

        } catch (_: Exception) {
            Log.e("Theme", "Error tinting additional settings sheet")
        }

        sheetView.findViewById<View>(R.id.actionSecurity).setOnClickListener {
            bottomSheetDialog.dismiss()
            startActivity(Intent(this, SecurityActivity::class.java).apply {
                putExtra("username", username)
            })
        }
        sheetView.findViewById<View>(R.id.actionNotifications).setOnClickListener {
            bottomSheetDialog.dismiss()
            startActivity(Intent(this, NotificationActivity::class.java))
        }
        sheetView.findViewById<View>(R.id.actionClearCache).setOnClickListener {
            bottomSheetDialog.dismiss()
            clearLocalCache()
        }
        sheetView.findViewById<View>(R.id.actionAbout).setOnClickListener {
            bottomSheetDialog.dismiss()
            showAboutDialog()
        }
        sheetView.findViewById<View>(R.id.actionAdmin).setOnClickListener {
            bottomSheetDialog.dismiss()
            startActivity(Intent(this, SuperAdminActivity::class.java))
        }
        sheetView.findViewById<View>(R.id.actionServers).setOnClickListener {
            bottomSheetDialog.dismiss()
            val currentServer = getSharedPreferences("lavender_prefs", MODE_PRIVATE).getString("server_address", "Unknown")
            AlertDialog.Builder(this)
                .setTitle(R.string.servers)
                .setMessage("${getString(R.string.under_development)}\n\nConnected to: $currentServer")
                .setPositiveButton("OK", null)
                .show()
        }
        sheetView.findViewById<View>(R.id.actionDeleteProfile).setOnClickListener {
            bottomSheetDialog.dismiss()
            confirmDeleteProfile()
        }
        sheetView.findViewById<View>(R.id.actionLogout).setOnClickListener {
            bottomSheetDialog.dismiss()
            logout()
        }

        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
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
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val customTheme = ThemeStore.currentTheme()
        ThemeApplier.applyToDialog(bottomSheetDialog, customTheme)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_chat_actions, binding.root, false)
        val actionIds = listOf(R.id.actionCreateChat, R.id.actionAddContact)
        try {
            val bgColor = customTheme.backgroundColor.toColorInt()
            val txtColor = customTheme.textPrimaryColor.toColorInt()
            val primColor = customTheme.primaryColor.toColorInt()
            sheetView.setBackgroundColor(bgColor)
            sheetView.findViewById<View>(R.id.dragHandle)?.backgroundTintList = ColorStateList.valueOf(primColor)
            fun applyThemeToMenu(view: View, isShare: Boolean, isLogout: Boolean) {
                if (view is TextView) {
                    if (isShare) view.setTextColor(primColor)
                    else if (!isLogout) view.setTextColor(txtColor)
                } else if (view is ImageView) {
                    if (!isLogout) view.imageTintList = ColorStateList.valueOf(primColor)
                } else if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        applyThemeToMenu(view.getChildAt(i), isShare, isLogout)
                    }
                }
            }

            actionIds.forEach { id ->
                sheetView.findViewById<View>(id)?.let { view ->
                    applyThemeToMenu(view, id == R.id.actionShareHeader, id == R.id.actionLogout)
                }
            }
        } catch (_: Exception) {
            Log.e("Theme", "Error tinting chat action sheet")
        }

        sheetView.findViewById<View>(R.id.actionCreateChat).setOnClickListener {
            bottomSheetDialog.dismiss()
            showCreateChatDialog()
        }

        sheetView.findViewById<View>(R.id.actionAddContact).setOnClickListener {
            bottomSheetDialog.dismiss()
            showAddContactDialog()
        }
        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }

    private fun showCreateChatDialog() {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val customTheme = ThemeStore.currentTheme()
        ThemeApplier.applyToDialog(bottomSheet, customTheme)
        @Suppress("DEPRECATION")
        bottomSheet.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val view = layoutInflater.inflate(R.layout.bottom_sheet_create_chat, binding.root, false)
        
        val groupNameLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.groupNameLayout)
        val groupNameEditText = view.findViewById<TextInputEditText>(R.id.groupNameEditText)
        val searchEditText = view.findViewById<TextInputEditText>(R.id.searchEditText)
        val searchInputLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.searchInputLayout)
        val usersRecyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.usersRecyclerView)
        val btnCreate = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCreate)
        try {
            val bgColor = customTheme.backgroundColor.toColorInt()
            val primColor = customTheme.primaryColor.toColorInt()
            val txtColor = customTheme.textPrimaryColor.toColorInt()
            
            view.setBackgroundColor(bgColor)
            view.findViewById<View>(R.id.dragHandle)?.backgroundTintList = ColorStateList.valueOf(primColor)
            
            val boxColor = ColorStateList.valueOf(primColor)
            searchInputLayout.setBoxStrokeColorStateList(boxColor)
            searchInputLayout.defaultHintTextColor = boxColor
            searchInputLayout.setStartIconTintList(boxColor)
            groupNameLayout.setBoxStrokeColorStateList(boxColor)
            groupNameLayout.defaultHintTextColor = boxColor
            
            searchEditText.setTextColor(txtColor)
            groupNameEditText.setTextColor(txtColor)
            view.findViewById<TextView>(R.id.dialogTitle)?.setTextColor(primColor)
        } catch (_: Exception) {}

        val allContacts = mutableListOf<String>()
        val filteredContacts = mutableListOf<String>()
        
        val userAdapter = UserAdapter(
            onUserClick = { selected ->
                val adapter = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.usersRecyclerView).adapter as? UserAdapter
                adapter?.toggleSelection(selected)
            },
            onSelectionChanged = { count ->
                btnCreate.isEnabled = count > 0
                btnCreate.text = if (count > 1) "${getString(R.string.create)} ($count)" else getString(R.string.create)
                groupNameLayout.isVisible = count > 1
            },
            avatarCache = grpcClient.getAvatarCache(),
            onlineUsers = grpcClient.users.value
        )

        usersRecyclerView.adapter = userAdapter
        usersRecyclerView.layoutManager = LinearLayoutManager(this)

        grpcClient.getContacts(username) { list ->
            allContacts.clear()
            allContacts.addAll(list)
            filteredContacts.clear()
            filteredContacts.addAll(allContacts)
            runOnUiThread { userAdapter.setUsers(filteredContacts) }
        }

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                filteredContacts.clear()
                filteredContacts.addAll(allContacts.filter { it.lowercase().contains(query) })
                userAdapter.setUsers(filteredContacts)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        btnCreate.setOnClickListener {
            val selected = userAdapter.getSelectedUsers()
            if (selected.isEmpty()) return@setOnClickListener

            if (selected.size == 1) {
                val targetUser = selected.first()
                grpcClient.createDirectChat(username, targetUser) { chatId ->
                    if (chatId != null) runOnUiThread {
                        val intent = Intent(this, NewChatActivity::class.java).apply {
                            putExtra("USERNAME", username); putExtra("PASSWORD", password)
                            putExtra("ROOM_ID", chatId); putExtra("CHAT_NAME", targetUser)
                            putExtra("IS_DIRECT", true); putExtra("PARTICIPANTS", "[\"$username\", \"$targetUser\"]")
                        }
                        startActivity(intent); bottomSheet.dismiss()
                        // Refresh chat list to include the new chat
                        loadChats(skipCache = true)
                    }
                }
            } else {
                val groupName = groupNameEditText.text.toString().trim().ifEmpty { getString(R.string.default_group_name) }
                val participants = selected + username
                grpcClient.createGroupChat(groupName, participants, username) { chatId ->
                    if (chatId != null) runOnUiThread {
                        val intent = Intent(this, NewChatActivity::class.java).apply {
                            putExtra("USERNAME", username); putExtra("PASSWORD", password)
                            putExtra("ROOM_ID", chatId); putExtra("CHAT_NAME", groupName)
                            putExtra("IS_DIRECT", false); putExtra("PARTICIPANTS", org.json.JSONArray(participants).toString())
                            putExtra("CREATOR", username)
                        }
                        startActivity(intent); bottomSheet.dismiss()
                        // Refresh chat list to include the new chat
                        loadChats(skipCache = true)
                    }
                }
            }
        }

        bottomSheet.setContentView(view)
        bottomSheet.show()
    }

    private fun showAddContactDialog() {
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val customTheme = ThemeStore.currentTheme()
        ThemeApplier.applyToDialog(bottomSheet, customTheme)
        @Suppress("DEPRECATION")
        bottomSheet.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val view = layoutInflater.inflate(R.layout.bottom_sheet_add_contacts, binding.root, false)
        
        val searchEditText = view.findViewById<TextInputEditText>(R.id.searchEditText)
        val searchInputLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.searchInputLayout)
        val usersRecyclerView = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.usersRecyclerView)
        val btnAdd = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAdd)
        try {
            val bgColor = customTheme.backgroundColor.toColorInt()
            val primColor = customTheme.primaryColor.toColorInt()
            val txtColor = customTheme.textPrimaryColor.toColorInt()
            
            view.setBackgroundColor(bgColor)
            view.findViewById<View>(R.id.dragHandle)?.backgroundTintList = ColorStateList.valueOf(primColor)
            
            val boxColor = ColorStateList.valueOf(primColor)
            searchInputLayout.setBoxStrokeColorStateList(boxColor)
            searchInputLayout.defaultHintTextColor = boxColor
            searchInputLayout.setStartIconTintList(boxColor)
            searchEditText.setTextColor(txtColor)
            view.findViewById<TextView>(R.id.dialogTitle)?.setTextColor(primColor)
        } catch (_: Exception) {}

        val allUsers = mutableListOf<String>()
        val filteredUsers = mutableListOf<String>()
        val currentContacts = mutableSetOf<String>()
        
        val userAdapter = UserAdapter(
            onUserClick = { selected ->
                val adapter = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.usersRecyclerView).adapter as? UserAdapter
                adapter?.toggleSelection(selected)
            },
            onSelectionChanged = { count ->
                btnAdd.isEnabled = count > 0
                btnAdd.text = if (count > 0) "${getString(R.string.add)} ($count)" else getString(R.string.add)
            },
            avatarCache = grpcClient.getAvatarCache(),
            onlineUsers = grpcClient.users.value
        )

        usersRecyclerView.adapter = userAdapter
        usersRecyclerView.layoutManager = LinearLayoutManager(this)

        grpcClient.getContacts(username) { list ->
            currentContacts.addAll(list)
            grpcClient.loadAllUsers()
        }

        lifecycleScope.launch {
            grpcClient.allUsers.collect { users ->
                if (users.isNotEmpty()) {
                    allUsers.clear()
                    allUsers.addAll(users.filter { it.username != username && !currentContacts.contains(it.username) }.map { it.username })
                    filteredUsers.clear()
                    filteredUsers.addAll(allUsers)
                    runOnUiThread { userAdapter.setUsers(filteredUsers) }
                }
            }
        }

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                filteredUsers.clear()
                filteredUsers.addAll(allUsers.filter { it.lowercase().contains(query) })
                userAdapter.setUsers(filteredUsers)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        btnAdd.setOnClickListener {
            val selected = userAdapter.getSelectedUsers()
            if (selected.isNotEmpty()) {
                var completed = 0
                selected.forEach { contact ->
                    grpcClient.addContact(username, contact) { _, _ ->
                        completed++
                        if (completed == selected.size) {
                            runOnUiThread {
                                Toast.makeText(this, R.string.contact_added, Toast.LENGTH_SHORT).show()
                                bottomSheet.dismiss()
                            }
                        }
                    }
                }
            }
        }

        bottomSheet.setContentView(view)
        bottomSheet.show()
    }

    private fun showLoginBottomSheet() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        // Load theme from SharedPreferences instead of ThemeStore to preserve theme when logged out
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
        ThemeApplier.applyToDialog(bottomSheetDialog, customTheme)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_login, binding.root, false)

        val usernameInputLayout = sheetView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.usernameInputLayout)
        val passwordInputLayout = sheetView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.passwordInputLayout)
        val editText = sheetView.findViewById<EditText>(R.id.editTextUsername)
        val editTextPassword = sheetView.findViewById<EditText>(R.id.editTextPassword)
        val serverAddressSpinner = sheetView.findViewById<Spinner>(R.id.serverAddressSpinner)
        val serverStatusIndicator = sheetView.findViewById<View>(R.id.serverStatusIndicator)
        val serverStatusText = sheetView.findViewById<TextView>(R.id.serverStatusText)
        val serverAddressLabel = sheetView.findViewById<TextView>(R.id.serverAddressLabel)
        val serverStatusLayout = sheetView.findViewById<LinearLayout>(R.id.serverStatusLayout)
        val joinProgressBar = sheetView.findViewById<ProgressBar>(R.id.joinProgressBar)
        val btnCancel = sheetView.findViewById<Button>(R.id.btnCancel)
        val btnJoin = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnJoin)
        val forgotPasswordButton = sheetView.findViewById<TextView>(R.id.forgotPasswordButton)

        // Apply custom theme colors to all views
        try {
            val bgColor = customTheme.backgroundColor.toColorInt()
            val surfaceColor = customTheme.surfaceColor.toColorInt()
            val primaryColor = customTheme.primaryColor.toColorInt()
            val onSurfaceColor = customTheme.onSurfaceColor.toColorInt()
            val onPrimaryColor = customTheme.onPrimaryColor.toColorInt()
            
            // Set background color
            sheetView.setBackgroundColor(bgColor)
            
            // Set TextInputLayout background
            usernameInputLayout.boxBackgroundColor = surfaceColor
            passwordInputLayout.boxBackgroundColor = surfaceColor
            
            // Set text colors
            editText.setTextColor(onSurfaceColor)
            editText.setHintTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(onSurfaceColor, 128))
            editTextPassword.setTextColor(onSurfaceColor)
            editTextPassword.setHintTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(onSurfaceColor, 128))
            
            // Set button colors
            btnJoin.setBackgroundColor(primaryColor)
            btnJoin.setTextColor(onPrimaryColor)
            btnCancel.setTextColor(onSurfaceColor)
            forgotPasswordButton.setTextColor(primaryColor)
            
            // Set spinner text color
            serverStatusText?.setTextColor(onSurfaceColor)
            
            // Set title color
            sheetView.findViewById<TextView>(R.id.titleText)?.setTextColor(onSurfaceColor)
            
            // Set server status indicator to green (online)
            serverStatusIndicator?.setBackgroundColor("#4CAF50".toColorInt())
        } catch (_: Exception) {
            // Fallback to default theme handling
            if (isDarkTheme()) {
                val surfaceValue = android.util.TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, surfaceValue, true)
                usernameInputLayout.boxBackgroundColor = surfaceValue.data
                passwordInputLayout.boxBackgroundColor = surfaceValue.data

                val primaryValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.colorPrimary, primaryValue, true)
                btnJoin.strokeColor = ColorStateList.valueOf(primaryValue.data)
                btnJoin.strokeWidth = 2
            }
        }

        // Setup server address spinner
        val serverList = listOf("159.195.38.145:50051")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, serverList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        serverAddressSpinner?.adapter = adapter
        serverAddressSpinner?.setSelection(0)

        if (serverList.size <= 1) {
            serverAddressSpinner?.visibility = View.GONE
            serverStatusLayout?.visibility = View.GONE
            serverAddressLabel?.visibility = View.GONE
        }

        bottomSheetDialog.setContentView(sheetView)

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
            showAuthChoiceDialog()
        }

        // Handle dismiss without login
        bottomSheetDialog.setOnDismissListener {
            if (username.isEmpty() || password.isEmpty()) {
                //showFavoritesFromCache()
            }
        }

        btnJoin.setOnClickListener {
            val username = editText.text.toString().trim()
            val password = editTextPassword.text.toString().trim()
            val serverAddress = serverAddressSpinner?.selectedItem?.toString() ?: "159.195.38.145:50051"
            if (username.isNotEmpty() && password.isNotEmpty()) {
                // Show loading state
                btnJoin.text = ""
                btnJoin.isEnabled = false
                joinProgressBar.isVisible = true

                SessionManager.login(this, username, password, serverAddress, register = false, email = "") { result ->
                    runOnUiThread {
                        when (result) {
                            "SUCCESS" -> {
                                // Save credentials
                                val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                                prefs.edit {
                                    putString("saved_username", username)
                                    putString("saved_password", password)
                                    putString("server_address", serverAddress)
                                }

                                val userId = SessionManager.session.value.userId
                                if (userId.isNotEmpty()) {
                                    prefs.edit { putString("user_id", userId) }
                                }

                                bottomSheetDialog.dismiss()
                                recreate() // Reload activity with authenticated user
                            }
                            "USER_NOT_FOUND" -> {
                                joinProgressBar.isVisible = false
                                btnJoin.text = getString(R.string.join)
                                btnJoin.isEnabled = true
                                
                                AlertDialog.Builder(this)
                                    .setTitle(R.string.user_not_found)
                                    .setMessage(getString(R.string.register_confirm, username))
                                    .setPositiveButton(R.string.yes) { _, _ ->
                                        bottomSheetDialog.dismiss()
                                        showRegisterBottomSheet()
                                    }
                                    .setNegativeButton(R.string.no) { _, _ ->
                                        bottomSheetDialog.dismiss()
                                    }
                                    .show()
                            }
                            "AUTH_FAILED" -> {
                                joinProgressBar.isVisible = false
                                btnJoin.text = getString(R.string.join)
                                btnJoin.isEnabled = true
                                Toast.makeText(this, R.string.auth_failed, Toast.LENGTH_LONG).show()
                            }
                            else -> {
                                joinProgressBar.isVisible = false
                                btnJoin.text = getString(R.string.join)
                                btnJoin.isEnabled = true
                                Toast.makeText(this, R.string.connection_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } else if (username.isEmpty()) {
                Toast.makeText(this, R.string.username_empty, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, R.string.password_empty, Toast.LENGTH_LONG).show()
            }
        }

        forgotPasswordButton.setOnClickListener {
            bottomSheetDialog.dismiss()
            showForgotPasswordBottomSheet()
        }

        bottomSheetDialog.show()
    }

    private fun showRegisterBottomSheet() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        // Load theme from SharedPreferences instead of ThemeStore to preserve theme when logged out
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
        ThemeApplier.applyToDialog(bottomSheetDialog, customTheme)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_register, binding.root, false)

        val usernameInputLayout = sheetView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.usernameInputLayout)
        val passwordInputLayout = sheetView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.passwordInputLayout)
        val confirmPasswordInputLayout = sheetView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.confirmPasswordInputLayout)
        val emailInputLayout = sheetView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.emailInputLayout)
        val editText = sheetView.findViewById<EditText>(R.id.editTextUsername)
        val editTextPassword = sheetView.findViewById<EditText>(R.id.editTextPassword)
        val editTextConfirmPassword = sheetView.findViewById<EditText>(R.id.editTextConfirmPassword)
        val editTextEmail = sheetView.findViewById<EditText>(R.id.editTextEmail)
        val serverAddressSpinner = sheetView.findViewById<Spinner>(R.id.serverAddressSpinner)
        val serverStatusIndicator = sheetView.findViewById<View>(R.id.serverStatusIndicator)
        val serverStatusText = sheetView.findViewById<TextView>(R.id.serverStatusText)
        val serverAddressLabel = sheetView.findViewById<TextView>(R.id.serverAddressLabel)
        val serverStatusLayout = sheetView.findViewById<LinearLayout>(R.id.serverStatusLayout)
        val registerProgressBar = sheetView.findViewById<ProgressBar>(R.id.registerProgressBar)
        val btnCancel = sheetView.findViewById<Button>(R.id.btnCancel)
        val btnRegister = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRegister)

        // Apply custom theme colors to all views
        try {
            val bgColor = customTheme.backgroundColor.toColorInt()
            val surfaceColor = customTheme.surfaceColor.toColorInt()
            val primaryColor = customTheme.primaryColor.toColorInt()
            val onSurfaceColor = customTheme.onSurfaceColor.toColorInt()
            val onPrimaryColor = customTheme.onPrimaryColor.toColorInt()
            
            // Set background color
            sheetView.setBackgroundColor(bgColor)
            
            // Set TextInputLayout background
            usernameInputLayout.boxBackgroundColor = surfaceColor
            passwordInputLayout.boxBackgroundColor = surfaceColor
            confirmPasswordInputLayout.boxBackgroundColor = surfaceColor
            emailInputLayout.boxBackgroundColor = surfaceColor
            
            // Set text colors
            editText.setTextColor(onSurfaceColor)
            editText.setHintTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(onSurfaceColor, 128))
            editTextPassword.setTextColor(onSurfaceColor)
            editTextPassword.setHintTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(onSurfaceColor, 128))
            editTextConfirmPassword.setTextColor(onSurfaceColor)
            editTextConfirmPassword.setHintTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(onSurfaceColor, 128))
            editTextEmail.setTextColor(onSurfaceColor)
            editTextEmail.setHintTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(onSurfaceColor, 128))
            
            // Set button colors
            btnRegister.setBackgroundColor(primaryColor)
            btnRegister.setTextColor(onPrimaryColor)
            btnCancel.setTextColor(onSurfaceColor)
            
            // Set spinner text color
            serverStatusText?.setTextColor(onSurfaceColor)
            
            // Set title color
            sheetView.findViewById<TextView>(R.id.titleText)?.setTextColor(onSurfaceColor)
            
            // Set server status indicator to green (online)
            serverStatusIndicator?.setBackgroundColor("#4CAF50".toColorInt())
        } catch (_: Exception) {
            // Fallback to default theme handling
            if (isDarkTheme()) {
                val surfaceValue = android.util.TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, surfaceValue, true)
                usernameInputLayout.boxBackgroundColor = surfaceValue.data
                passwordInputLayout.boxBackgroundColor = surfaceValue.data
                confirmPasswordInputLayout.boxBackgroundColor = surfaceValue.data
                emailInputLayout.boxBackgroundColor = surfaceValue.data

                val primaryValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.colorPrimary, primaryValue, true)
                btnRegister.strokeColor = ColorStateList.valueOf(primaryValue.data)
                btnRegister.strokeWidth = 2
            }
        }

        // Setup server address spinner
        val serverList = listOf("159.195.38.145:50051")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, serverList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        serverAddressSpinner?.adapter = adapter
        serverAddressSpinner?.setSelection(0)

        if (serverList.size <= 1) {
            serverAddressSpinner?.visibility = View.GONE
            serverStatusLayout?.visibility = View.GONE
            serverAddressLabel?.visibility = View.GONE
        }

        bottomSheetDialog.setContentView(sheetView)

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
            showAuthChoiceDialog()
        }

        // Handle dismiss without registration
        bottomSheetDialog.setOnDismissListener {
            if (username.isEmpty() || password.isEmpty()) {
                //showFavoritesFromCache()
            }
        }

        btnRegister.setOnClickListener {
            val username = editText.text.toString().trim()
            val password = editTextPassword.text.toString().trim()
            val confirmPassword = editTextConfirmPassword.text.toString().trim()
            val email = editTextEmail.text.toString().trim()
            val serverAddress = serverAddressSpinner?.selectedItem?.toString() ?: "159.195.38.145:50051"

            if (username.isEmpty()) {
                Toast.makeText(this, R.string.username_empty, Toast.LENGTH_LONG).show()
            } else if (password.isEmpty()) {
                Toast.makeText(this, R.string.password_empty, Toast.LENGTH_LONG).show()
            } else if (password != confirmPassword) {
                Toast.makeText(this, R.string.passwords_do_not_match, Toast.LENGTH_LONG).show()
            } else {
                // Show loading state
                btnRegister.text = ""
                btnRegister.isEnabled = false
                registerProgressBar.isVisible = true

                SessionManager.login(this, username, password, serverAddress, register = true, email = email) { result ->
                    runOnUiThread {
                        when (result) {
                            "REGISTRATION_SUCCESS" -> {
                                // Save credentials
                                val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                                prefs.edit {
                                    putString("saved_username", username)
                                    putString("saved_password", password)
                                    putString("server_address", serverAddress)
                                }

                                val userId = SessionManager.session.value.userId
                                if (userId.isNotEmpty()) {
                                    prefs.edit { putString("user_id", userId) }
                                }

                                // Clear local cache for new user
                                clearLocalCacheSync()
                                // Set onboarding flag
                                prefs.edit {
                                    putBoolean("onboarding_completed_$username", false)
                                    putLong("first_login_$username", System.currentTimeMillis())
                                }

                                bottomSheetDialog.dismiss()
                                recreate() // Reload activity with authenticated user
                            }
                            "USER_ALREADY_EXISTS" -> {
                                registerProgressBar.isVisible = false
                                btnRegister.text = getString(R.string.register)
                                btnRegister.isEnabled = true
                                Toast.makeText(this, R.string.user_already_exists, Toast.LENGTH_LONG).show()
                            }
                            "EMAIL_ALREADY_IN_USE" -> {
                                registerProgressBar.isVisible = false
                                btnRegister.text = getString(R.string.register)
                                btnRegister.isEnabled = true
                                Toast.makeText(this, R.string.email_already_in_use, Toast.LENGTH_LONG).show()
                            }
                            "AUTH_FAILED" -> {
                                registerProgressBar.isVisible = false
                                btnRegister.text = getString(R.string.register)
                                btnRegister.isEnabled = true
                                Toast.makeText(this, R.string.auth_failed, Toast.LENGTH_LONG).show()
                            }
                            else -> {
                                registerProgressBar.isVisible = false
                                btnRegister.text = getString(R.string.register)
                                btnRegister.isEnabled = true
                                Toast.makeText(this, R.string.connection_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        bottomSheetDialog.show()
    }

    private fun isDarkTheme(): Boolean {
        return when (AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                currentNightMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun showForgotPasswordBottomSheet() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
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
        ThemeApplier.applyToDialog(bottomSheetDialog, customTheme)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_forgot_password, binding.root, false)

        val emailInputLayout = sheetView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.emailInputLayout)
        val editTextEmail = sheetView.findViewById<EditText>(R.id.editTextEmail)
        val sendProgressBar = sheetView.findViewById<ProgressBar>(R.id.sendProgressBar)
        val btnSend = sheetView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSend)
        val btnCancel = sheetView.findViewById<Button>(R.id.btnCancel)
        val tokenInputLayout = sheetView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tokenInputLayout)
        val newPasswordInputLayout = sheetView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.newPasswordInputLayout)
        val editTextToken = sheetView.findViewById<EditText>(R.id.editTextToken)
        val editTextNewPassword = sheetView.findViewById<EditText>(R.id.editTextNewPassword)

        // Apply custom theme colors to all views
        try {
            val bgColor = customTheme.backgroundColor.toColorInt()
            val surfaceColor = customTheme.surfaceColor.toColorInt()
            val primaryColor = customTheme.primaryColor.toColorInt()
            val onSurfaceColor = customTheme.onSurfaceColor.toColorInt()
            val onPrimaryColor = customTheme.onPrimaryColor.toColorInt()
            
            // Set background color
            sheetView.setBackgroundColor(bgColor)
            
            // Set TextInputLayout background
            emailInputLayout.boxBackgroundColor = surfaceColor
            tokenInputLayout.boxBackgroundColor = surfaceColor
            newPasswordInputLayout.boxBackgroundColor = surfaceColor
            
            // Set text colors
            editTextEmail.setTextColor(onSurfaceColor)
            editTextEmail.setHintTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(onSurfaceColor, 128))
            editTextToken.setTextColor(onSurfaceColor)
            editTextToken.setHintTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(onSurfaceColor, 128))
            editTextNewPassword.setTextColor(onSurfaceColor)
            editTextNewPassword.setHintTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(onSurfaceColor, 128))
            
            // Set button colors
            btnSend.setBackgroundColor(primaryColor)
            btnSend.setTextColor(onPrimaryColor)
            btnCancel.setTextColor(onSurfaceColor)
            
            // Set title color
            sheetView.findViewById<TextView>(R.id.titleText)?.setTextColor(onSurfaceColor)
            sheetView.findViewById<TextView>(R.id.descriptionText)?.setTextColor(androidx.core.graphics.ColorUtils.setAlphaComponent(onSurfaceColor, 178))
            
        } catch (_: Exception) {
            // Fallback to default theme handling
            if (isDarkTheme()) {
                val surfaceValue = android.util.TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, surfaceValue, true)
                emailInputLayout.boxBackgroundColor = surfaceValue.data
                
                val primaryValue = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.colorPrimary, primaryValue, true)
                btnSend.strokeColor = ColorStateList.valueOf(primaryValue.data)
                btnSend.strokeWidth = 2
            }
        }

        bottomSheetDialog.setContentView(sheetView)

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
            showAuthChoiceDialog()
        }

        var isStep2 = false
        btnSend.setOnClickListener {
            if (!isStep2) {
                val email = editTextEmail.text.toString().trim()
                if (email.isNotEmpty()) {
                    sendProgressBar.isVisible = true
                    btnSend.isEnabled = false
                    
                    lifecycleScope.launch(Dispatchers.IO) {
                        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                        val serverAddress = prefs.getString("server_address", "159.195.38.145:50051") ?: "159.195.38.145:50051"
                        
                        if (serverAddress.isEmpty()) {
                            withContext(Dispatchers.Main) {
                                sendProgressBar.isVisible = false
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
                            
                            // Wait up to 5 seconds for connection to be READY
                            withTimeoutOrNull(5000) {
                                grpcClient.connectionStatus.first { it == ConnectionStatus.READY || it == ConnectionStatus.FAILED }
                            }
                        }

                        if (grpcClient.connectionStatus.value == ConnectionStatus.READY) {
                            grpcClient.requestPasswordReset(email) { success, message ->
                                runOnUiThread {
                                    sendProgressBar.isVisible = false
                                    btnSend.isEnabled = true
                                    if (success) {
                                        isStep2 = true
                                        emailInputLayout.isVisible = false
                                        tokenInputLayout.isVisible = true
                                        newPasswordInputLayout.isVisible = true
                                        btnSend.text = "Сбросить пароль"
                                        Toast.makeText(this@ChatListActivity, "Код отправлен на ваш email", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(this@ChatListActivity, message.takeIf { !it.isNullOrEmpty() } ?: getString(R.string.connection_failed), Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                sendProgressBar.isVisible = false
                                btnSend.isEnabled = true
                                Toast.makeText(this@ChatListActivity, R.string.connection_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, R.string.enter_email, Toast.LENGTH_LONG).show()
                }
            } else {
                val token = editTextToken.text.toString().trim()
                val newPw = editTextNewPassword.text.toString().trim()
                
                if (token.isEmpty()) {
                    Toast.makeText(this, "Введите код", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (newPw.isEmpty()) {
                    Toast.makeText(this, R.string.password_empty, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                sendProgressBar.isVisible = true
                btnSend.isEnabled = false

                lifecycleScope.launch(Dispatchers.IO) {
                    grpcClient.resetPassword(token, newPw) { success, message ->
                        runOnUiThread {
                            sendProgressBar.isVisible = false
                            btnSend.isEnabled = true
                            if (success) {
                                Toast.makeText(this@ChatListActivity, "Пароль успешно изменен", Toast.LENGTH_LONG).show()
                                bottomSheetDialog.dismiss()
                            } else {
                                Toast.makeText(this@ChatListActivity, message.takeIf { !it.isNullOrEmpty() } ?: getString(R.string.connection_failed), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        bottomSheetDialog.show()
    }

    private fun showAuthChoiceDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_auth_choice, binding.root, false)
        // Load theme from SharedPreferences instead of ThemeStore to preserve theme when logged out
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
        
        val btnLogin = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLogin)
        val btnRegister = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRegister)

        try {
            val bgColor = customTheme.backgroundColor.toColorInt()
            dialogView.setBackgroundColor(bgColor)
        } catch (_: Exception) {}

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)
        
        btnLogin.setOnClickListener {
            dialog.dismiss()
            showLoginBottomSheet()
        }
        
        btnRegister.setOnClickListener {
            dialog.dismiss()
            showRegisterBottomSheet()
        }
        
        dialog.show()
    }
}
