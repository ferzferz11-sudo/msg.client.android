package lavender.client.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.ServerConnectivityTest
import lavender.client.android.data.fcm.NotificationHistory
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.databinding.ActivityChatListBinding
import lavender.client.android.ui.adapter.ChatAdapter
import lavender.client.android.ui.adapter.UserAdapter
import lavender.client.android.ui.viewmodel.ChatListViewModel
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import de.hdodenhof.circleimageview.CircleImageView
import android.R as androidR

class ChatListActivity : AppCompatActivity() {

    companion object {
        private const val APK_URL = "http://159.195.38.145:8081/lavender.apk"
        private const val VERSION_CHECK_URL = "http://159.195.38.145:8081/version.txt"
    }

    private lateinit var binding: ActivityChatListBinding

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    private lateinit var adapter: ChatAdapter
    private val grpcClient = GrpcClient
    private val viewModel: ChatListViewModel by viewModels()
    private var username: String = ""
    private var password: String = ""
    private var downloadJob: Job? = null
    private var currentTheme: String? = null

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        val toast = Toast.makeText(this, message, duration)
        toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 100)
        toast.show()
    }

    private fun clearMenuAnimations() {
        val menu = binding.toolbar.menu
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            findViewById<View>(item.itemId)?.clearAnimation()
        }
    }

    private fun isDarkTheme(): Boolean {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("color_scheme", "dark") != "light"
    }

    private val editProfileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Refresh avatar when profile is updated
            grpcClient.getUserAvatar(username) { avatarUrl ->
                runOnUiThread {
                    if (avatarUrl.isNotEmpty()) {
                        Log.d("ChatList", "Updating avatar for $username to $avatarUrl")
                        // Update cache first
                        grpcClient.updateAvatarCache(username, avatarUrl)
                        
                        // Update adapter avatar cache
                        adapter.updateAvatarCache(grpcClient.getAvatarCache())
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        currentTheme = getSavedColorScheme() ?: "dark"
        applySavedColorScheme()
        applySavedLanguage()
        super.onCreate(savedInstanceState)
        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        username = intent.getStringExtra("username") ?: ""
        password = intent.getStringExtra("password") ?: ""

        // Load and apply custom theme if needed
        lavender.client.android.ui.ThemeManager.loadTheme(this, username) {
            runOnUiThread {
                lavender.client.android.ui.ThemeManager.applyTheme(this)
            }
        }

        // Handle window insets to avoid overlapping with status bar (edge-to-edge mode)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = ""
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.exit_to_app_24)
        }

        binding.toolbar.setNavigationOnClickListener {
            if (adapter.getSelectedChats().isNotEmpty()) {
                adapter.clearSelection()
            } else {
                // Minimize app to background instead of returning to login
                moveTaskToBack(true)
            }
        }

        // Handle system back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.getSelectedChats().isNotEmpty()) {
                    adapter.clearSelection()
                } else {
                    // Minimize app to background instead of returning to login
                    moveTaskToBack(true)
                }
            }
        })

        binding.addChatFab.setOnClickListener {
            showChatActionSheet()
        }

        // Check for update availability from SharedPreferences
        val updatePrefs = getSharedPreferences("UpdatePrefs", MODE_PRIVATE)
        val updateAvailable = updatePrefs.getBoolean("update_available", false)
        binding.updateAvailableIcon.isVisible = updateAvailable

        // Handle update icon click - directly start download process
        binding.updateAvailableIcon.setOnClickListener {
            downloadAndInstallApk()
        }

        adapter = ChatAdapter(
            onChatClick = { chat ->
                openChat(chat.id, chat.name, creator = chat.creator, participants = chat.participants)
            },
            onSettingsClick = { chat ->
                val intent = Intent(this, ProfileActivity::class.java)
                    .putExtra("username", chat.name)
                    .putExtra("is_group", !chat.type.equals("direct", true))
                    .putExtra("room_id", chat.id)
                    .putExtra("participants", chat.participants)
                    .putExtra("creator", chat.creator)
                startActivity(intent)
            },
            onSelectionChanged = { count ->
                clearMenuAnimations()
                invalidateOptionsMenu()
                if (count > 0) {
                    supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)
                    binding.toolbarTitle.text = getString(R.string.selected_count, count)
                } else {
                    supportActionBar?.setHomeAsUpIndicator(R.drawable.exit_to_app_24)
                    binding.toolbarTitle.text = getString(R.string.chats)
                }
            },
            currentUsername = username,
            initialAvatarCache = viewModel.avatarCache.ifEmpty { grpcClient.getAvatarCache() }
        )
        binding.chatsRecyclerView.adapter = adapter
        binding.chatsRecyclerView.layoutManager = LinearLayoutManager(this)

        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshChats(true)
        }

        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Observe system notifications for auth failures
        lifecycleScope.launch {
            grpcClient.systemNotification.collect { notification ->
                notification?.let {
                    if (it == "auth_failed") {
                        runOnUiThread {
                            showToast(getString(R.string.auth_failed), Toast.LENGTH_LONG)
                            grpcClient.disconnect()
                            finish()
                        }
                    }
                    // Clear notification after showing
                    grpcClient.clearSystemNotification()
                }
            }
        }

        // Observe online users
        lifecycleScope.launch {
            grpcClient.users.collect { onlineUsers ->
                runOnUiThread {
                    adapter.setOnlineUsers(onlineUsers)
                }
            }
        }

        if (viewModel.isInitialLoadComplete) {
            binding.swipeRefreshLayout.isRefreshing = false
            adapter.setChats(viewModel.currentChats)
            binding.welcomeContainer.isVisible = viewModel.currentChats.isEmpty()
            binding.chatsRecyclerView.isVisible = viewModel.currentChats.isNotEmpty()
        } else {
            loadChats()
        }

        loadAllUsers()
        startPollingChats()

        // Get and log FCM token for testing
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d("FCM", "FCM Token: $token")

            // Register token on server - always register to ensure token is up to date
            // Delay slightly to ensure username is loaded
            lifecycleScope.launch {
                delay(1000)
                if (username.isNotEmpty()) {
                    grpcClient.registerToken(username, token)
                    Log.d("FCM", "Token registered for user: $username")
                } else {
                    Log.w("FCM", "Username not available for token registration")
                }
            }
        }

        // Request POST_NOTIFICATIONS permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        handleIncomingActions(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingActions(intent)
    }

    private fun handleIncomingActions(intent: Intent) {
        val fromNotification = intent.getBooleanExtra("from_notification", false)
        val roomId = intent.getStringExtra("room_id")
        
        if (fromNotification && !roomId.isNullOrEmpty()) {
            android.util.Log.d("ChatList", "Coming from notification for room: $roomId")
            // Wait for chats to load if needed, then open the chat
            lifecycleScope.launch {
                var attempts = 0
                while (viewModel.currentChats.isEmpty() && attempts < 10) {
                    delay(500)
                    attempts++
                }
                
                val chat = viewModel.currentChats.find { it.id == roomId }
                if (chat != null) {
                    openChat(chat.id, chat.name, chat.type == "direct", chat.participants, chat.creator)
                }
            }
        }

        val deleteChatId = intent.getStringExtra("ACTION_DELETE_CHAT_ID")
        val deleteChatName = intent.getStringExtra("ACTION_DELETE_CHAT_NAME")
        if (!deleteChatId.isNullOrEmpty()) {
            performSingleChatDeletion(deleteChatId, deleteChatName ?: "")
        }
    }

    private fun performSingleChatDeletion(chatId: String, chatName: String) {
        binding.root.findViewById<TextView>(R.id.progressTitle)?.text = getString(R.string.delete)
        binding.progressOverlay.isVisible = true
        
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val deferred = kotlinx.coroutines.CompletableDeferred<Pair<Boolean, String>>()
                grpcClient.deleteChat(chatId) { success, message ->
                    deferred.complete(Pair(success, message))
                }
                deferred.await()
            }
            
            runOnUiThread {
                binding.progressOverlay.isVisible = false
                if (result.first) {
                    showToast(if (chatName.isNotEmpty()) getString(R.string.deleted_count, 1) + ": $chatName" else getString(R.string.deleted_count, 1))
                    loadChats()
                } else {
                    showToast(result.second)
                }
            }
        }
    }

    private fun loadAllUsers() {
        grpcClient.loadAllUsers()
        lifecycleScope.launch {
            delay(2000) // Wait for users to load
            var allUsers = grpcClient.allUsers.value

            // If users are still not loaded, wait more
            var attempts = 0
            while (allUsers.isEmpty() && attempts < 5) {
                delay(500)
                allUsers = grpcClient.allUsers.value
                attempts++
            }

            for (user in allUsers) {
                grpcClient.getUserAvatar(user) { avatarUrl ->
                    runOnUiThread {
                        // Save in cache (including empty strings to avoid retrying)
                        if (avatarUrl.isEmpty()) {
                            grpcClient.updateAvatarCache(user, "")
                        }
                        adapter.updateAvatarCache(grpcClient.getAvatarCache())
                    }
                }
            }
        }
    }

    private fun startPollingChats() {
        lifecycleScope.launch {
            while (isActive) {
                delay(3000) // Poll every 3 seconds for faster updates
                refreshChats(false)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false
        
        // Reset FAB and Menu state
        binding.addChatFab.isEnabled = true
        binding.addChatFab.setImageResource(android.R.drawable.ic_input_add)
        binding.addChatFab.clearAnimation()
        clearMenuAnimations()
        invalidateOptionsMenu()

        // Check if theme has changed in another activity
        val savedTheme = getSavedColorScheme() ?: "dark"
        if (savedTheme != currentTheme) {
            recreate()
            return
        }

        // Always try to refresh version and list when returning to this screen
        refreshChats(false)
        grpcClient.loadUsers()
    }

    private fun refreshChats(isManual: Boolean = false) {
        if (isManual) {
            binding.swipeRefreshLayout.isRefreshing = true
            
            // Safety timeout to clear refreshing state
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (binding.swipeRefreshLayout.isRefreshing) {
                    binding.swipeRefreshLayout.isRefreshing = false
                    Log.w("ChatList", "Refresh timed out")
                }
            }, 5000)
        }
        
        // If disconnected, try to reconnect first
        if (!grpcClient.connectionState.value) {
            Log.d("ChatList", "Disconnected, attempting to reconnect...")
            loadChats()
            return
        }

        grpcClient.getChatListVersion(username) { version ->
            if (isManual || version > viewModel.lastChatListVersion) {
                Log.d("ChatList", "Refreshing chats (Manual: $isManual, version: $version)")
                    grpcClient.getChats(username) { chats ->
                        val sortedChats = chats.sortedWith(compareByDescending<ChatInfo> { it.lastMessageTime }.thenByDescending { it.createdAt })
                        viewModel.currentChats = sortedChats
                        viewModel.lastChatListVersion = version
                    
                    runOnUiThread {
                        binding.swipeRefreshLayout.isRefreshing = false
                        adapter.setChats(sortedChats)
                        binding.chatsRecyclerView.post {
                            binding.chatsRecyclerView.scrollToPosition(0)
                        }
                        binding.welcomeContainer.isVisible = sortedChats.isEmpty()
                        binding.chatsRecyclerView.isVisible = sortedChats.isNotEmpty()
                    }

                    // Load avatars in background with batching
                    val allParticipants = mutableSetOf<String>()
                    for (chat in sortedChats) {
                        if (chat.participants.isNotEmpty()) {
                            try {
                                val participants = JSONArray(chat.participants)
                                for (i in 0 until participants.length()) {
                                    allParticipants.add(participants.optString(i))
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    lifecycleScope.launch {
                        var updateCount = 0
                        for (participant in allParticipants) {
                            grpcClient.getUserAvatar(participant) { avatarUrl ->
                                grpcClient.updateAvatarCache(participant, avatarUrl)
                                updateCount++
                                if (updateCount % 10 == 0 || updateCount == allParticipants.size) {
                                    viewModel.avatarCache = grpcClient.getAvatarCache()
                                    runOnUiThread {
                                        adapter.updateAvatarCache(viewModel.avatarCache)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                runOnUiThread {
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun loadChats() {
        binding.root.findViewById<TextView>(R.id.progressTitle)?.text = getString(R.string.loading)
        binding.progressOverlay.isVisible = true
        lifecycleScope.launch {
            val serverAddress = intent.getStringExtra("serverAddress") ?: getString(R.string.server_address)
            val parts = serverAddress.split(":")
            val host = parts[0]
            val port = if (parts.size > 1) parts[1].toInt() else 50051
            
            Log.d("ChatList", "Loading chats from server: $host:$port")
            
            // First, test server connectivity
            var serverAvailable = false
            
            withContext(Dispatchers.IO) {
                val socket = Socket()
                try {
                    socket.connect(java.net.InetSocketAddress(host, port), 10000)
                    serverAvailable = true
                    socket.close()
                    Log.d("ChatList", "TCP connection test successful")
                } catch (e: Exception) {
                    serverAvailable = false
                    Log.e("ChatList", "TCP connection test failed: ${e.message}")
                }
            }
            
            if (!serverAvailable) {
                runOnUiThread {
                    binding.progressOverlay.isVisible = false
                    showToast(getString(R.string.server_unavailable))
                }
                return@launch
            }

            grpcClient.connect(host, false, port, applicationContext)

            // Send auth message first
            grpcClient.startChat(username, password, "") { _ -> }

            // Wait for auth result with timeout
            var authComplete = false
            val startTime = System.currentTimeMillis()
            while (!authComplete && System.currentTimeMillis() - startTime < 5000) {
                val notification = grpcClient.systemNotification.value
                if (notification == "auth_failed") {
                    runOnUiThread {
                        binding.progressOverlay.isVisible = false
                        showToast(getString(R.string.auth_failed), Toast.LENGTH_LONG)
                        grpcClient.disconnect()
                        finish()
                    }
                    return@launch
                } else if (notification == "registration_success") {
                    authComplete = true
                    grpcClient.clearSystemNotification()
                }
                delay(200)
            }

            grpcClient.getChats(username) { chats ->
                grpcClient.getChatListVersion(username) { version ->
                    viewModel.lastChatListVersion = version
                    runOnUiThread {
                        binding.swipeRefreshLayout.isRefreshing = false
                        binding.progressOverlay.isVisible = false
                        // Show/hide welcome message
                        binding.welcomeContainer.isVisible = chats.isEmpty()
                        binding.chatsRecyclerView.isVisible = chats.isNotEmpty()

                        val sortedChats = chats.sortedWith(compareByDescending<ChatInfo> { it.lastMessageTime }.thenByDescending { it.createdAt })
                        viewModel.currentChats = sortedChats
                        viewModel.isInitialLoadComplete = true
                        adapter.setChats(sortedChats)
                        
                        // Ensure we are at the top after initial load
                        binding.chatsRecyclerView.post {
                            binding.chatsRecyclerView.scrollToPosition(0)
                        }
                        
                        // Load avatars in background with batching to avoid excessive UI updates
                        val allParticipants = mutableSetOf<String>()
                        for (chat in sortedChats) {
                            if (chat.participants.isNotEmpty()) {
                                try {
                                    val participants = JSONArray(chat.participants)
                                    for (i in 0 until participants.length()) {
                                        allParticipants.add(participants.optString(i))
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        lifecycleScope.launch {
                            var updateCount = 0
                            for (participant in allParticipants) {
                                grpcClient.getUserAvatar(participant) { avatarUrl ->
                                    grpcClient.updateAvatarCache(participant, avatarUrl)
                                    updateCount++
                                    
                                    // Update UI every 10 avatars or at the end to keep it smooth
                                    if (updateCount % 10 == 0 || updateCount == allParticipants.size) {
                                        viewModel.avatarCache = grpcClient.getAvatarCache()
                                        runOnUiThread {
                                            adapter.updateAvatarCache(viewModel.avatarCache)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Check for updates automatically only once per session
                // Done here after initial chat list load
                if (!grpcClient.hasCheckedForUpdates) {
                    checkForUpdates()
                    grpcClient.hasCheckedForUpdates = true
                }
            }

            // Fallback: hide progress after 10 seconds total if still loading
            delay(10000)
            runOnUiThread {
                if (binding.progressOverlay.isVisible) {
                    binding.progressOverlay.isVisible = false
                    showToast(getString(R.string.connection_failed))
                }
            }

            // Load current user avatar for chat list
            grpcClient.getUserAvatar(username) { avatarUrl ->
                runOnUiThread {
                    if (avatarUrl.isNotEmpty()) {
                        adapter.updateAvatarCache(grpcClient.getAvatarCache())
                    }
                }
            }
        }
    }

    private fun openChat(chatId: String, roomName: String? = null, isDirect: Boolean? = null, participants: String? = null, creator: String? = null) {
        lifecycleScope.launch {
            val chat = adapter.getChats().find { it.id == chatId }
            val intent = Intent(this@ChatListActivity, NewChatActivity::class.java)
                .putExtra("USERNAME", username)
                .putExtra("PASSWORD", password)
                .putExtra("ROOM_ID", chatId)
                .putExtra("CHAT_NAME", roomName ?: chat?.name ?: "Chat")
                .putExtra("IS_DIRECT", isDirect ?: (chat?.type == "direct"))
            
            val finalParticipants = participants ?: chat?.participants
            if (!finalParticipants.isNullOrEmpty()) {
                intent.putExtra("PARTICIPANTS", finalParticipants)
            }

            val finalCreator = creator ?: chat?.creator ?: ""
            if (finalCreator.isNotEmpty()) {
                intent.putExtra("CREATOR", finalCreator)
            }
            
            startActivity(intent)
        }
    }

    override fun onPause() {
        super.onPause()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = true
    }

    override fun onDestroy() {
        super.onDestroy()
        // Keep connection alive for other activities
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != androidR.id.home && 
            item.itemId != R.id.action_search && 
            item.itemId != R.id.action_profile &&
            item.itemId != R.id.action_about) {
            
            item.isEnabled = false
            item.setIcon(R.drawable.ic_loading_renew)
            
            // To animate a MenuItem, we need its View.
            // In MaterialToolbar, we can try to find it by ID.
            val menuView = findViewById<View>(item.itemId)
            if (menuView != null) {
                val rotate = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.rotate_renew)
                menuView.startAnimation(rotate)
            }
        }

        return when (item.itemId) {
            androidR.id.home -> {
                if (adapter.getSelectedChats().isNotEmpty()) {
                    adapter.clearSelection()
                } else {
                    // Minimize app to background instead of returning to login
                    moveTaskToBack(true)
                }
                true
            }
            R.id.action_search -> {
                binding.searchLayout.isVisible = !binding.searchLayout.isVisible
                if (binding.searchLayout.isVisible) {
                    binding.searchEditText.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(binding.searchEditText, 0)
                } else {
                    binding.searchEditText.text?.clear()
                    adapter.filter("")
                }
                true
            }
            R.id.action_delete -> {
                val selected = adapter.getSelectedChats()
                if (selected.isNotEmpty()) {
                    val dialogView = layoutInflater.inflate(R.layout.dialog_delete_chats, null)

                    // Set dialog background using Material Design colors
                    val typedValue = TypedValue()
                    if (isDarkTheme()) {
                        theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
                        dialogView.setBackgroundColor(typedValue.data)
                    }

                    val titleText = dialogView.findViewById<TextView>(R.id.titleText)
                    val messageText = dialogView.findViewById<TextView>(R.id.messageText)
                    val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
                    val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btnDelete)

                    titleText.text = getString(R.string.delete_chats)
                    messageText.text = getString(R.string.delete_chats_confirmation, selected.size)

                    // Set button strokes in dark theme
                    if (isDarkTheme()) {
                        val primaryValue = TypedValue()
                        theme.resolveAttribute(androidR.attr.colorPrimary, primaryValue, true)
                        val strokeColor = ColorStateList.valueOf(primaryValue.data)
                        btnCancel.strokeColor = strokeColor
                        btnCancel.strokeWidth = 2
                        btnDelete.strokeColor = strokeColor
                        btnDelete.strokeWidth = 2
                    }

                    val dialog = AlertDialog.Builder(this)
                        .setView(dialogView)
                        .setOnDismissListener { 
                            clearMenuAnimations()
                            invalidateOptionsMenu() 
                        }
                        .create()

                    btnCancel.setOnClickListener {
                        dialog.dismiss()
                    }

                    btnDelete.setOnClickListener {
                        dialog.dismiss()
                        val chatsToDelete = selected.toList()
                        adapter.clearSelection()
                        binding.root.findViewById<TextView>(R.id.progressTitle)?.text = getString(R.string.loading)
                        binding.progressOverlay.isVisible = true

                        lifecycleScope.launch {
                            var successCount = 0
                            var lastErrorMessage = ""
                            for (chat in chatsToDelete) {
                                val result: Pair<Boolean, String> = withContext(Dispatchers.IO) {
                                    val deferred = CompletableDeferred<Pair<Boolean, String>>()
                                    grpcClient.deleteChat(chat.id) { success, message ->
                                        deferred.complete(Pair(success, message))
                                    }
                                    deferred.await()
                                }
                                if (result.first) {
                                    successCount++
                                } else {
                                    lastErrorMessage = result.second
                                }
                            }

                            runOnUiThread {
                                binding.progressOverlay.isVisible = false
                                if (successCount > 0) {
                                    showToast(getString(R.string.deleted_count, successCount))
                                    // Refresh the chat list
                                    loadChats()
                                } else {
                                    val errorMsg = if (lastErrorMessage.isNotEmpty()) lastErrorMessage else getString(R.string.failed_to_delete_chats)
                                    showToast(errorMsg)
                                }
                            }
                        }
                    }

                    dialog.show()
                }
                true
            }
            R.id.action_profile_view -> {
                val intent = Intent(this, ProfileActivity::class.java).apply {
                    putExtra("username", username)
                    putExtra("is_group", false)
                }
                startActivity(intent)
                true
            }
            R.id.action_profile_edit -> {
                val intent = Intent(this, EditProfileActivity::class.java).apply {
                    putExtra("username", username)
                    putExtra("password", password)
                }
                editProfileLauncher.launch(intent)
                true
            }
            R.id.action_contacts -> {
                val intent = Intent(this, ContactsActivity::class.java).apply {
                    putExtra("username", username)
                    putExtra("password", password)
                }
                startActivity(intent)
                true
            }
            R.id.action_themes -> {
                val intent = Intent(this, ThemesActivity::class.java).apply {
                    putExtra("username", username)
                }
                startActivity(intent)
                true
            }
            R.id.action_notification_history -> {
                val intent = Intent(this, NotificationActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_update -> {
                downloadAndInstallApk()
                true
            }
            R.id.action_logout -> {
                logout()
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            R.id.action_super_admin -> {
                val intent = Intent(this, SuperAdminActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_toggle_language -> {
                toggleLanguage()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        clearMenuAnimations()
        menuInflater.inflate(R.menu.chat_list_menu, menu)

        val hasSelection = adapter.getSelectedChats().isNotEmpty()
        menu.findItem(R.id.action_search)?.apply { isVisible = !hasSelection }
        menu.findItem(R.id.action_contacts)?.apply { isVisible = !hasSelection }
        menu.findItem(R.id.action_delete)?.apply { isVisible = hasSelection }
        menu.findItem(R.id.action_themes)?.apply { isVisible = !hasSelection }
        menu.findItem(R.id.action_toggle_language)?.apply { isVisible = !hasSelection }
        menu.findItem(R.id.action_profile)?.apply { isVisible = !hasSelection }
        menu.findItem(R.id.action_notification_history)?.apply { isVisible = !hasSelection }
        menu.findItem(R.id.action_update)?.apply { isVisible = !hasSelection }
        menu.findItem(R.id.action_logout)?.apply { isVisible = !hasSelection }
        menu.findItem(R.id.action_about)?.apply { isVisible = !hasSelection }
        
        lifecycleScope.launch {
            grpcClient.isSuperAdmin.collect { isAdmin ->
                runOnUiThread {
                    menu.findItem(R.id.action_super_admin)?.isVisible = !hasSelection && isAdmin
                }
            }
        }

        // Final force application via ThemeManager logic
        lavender.client.android.ui.ThemeManager.getCurrentTheme()?.let { 
            lavender.client.android.ui.ThemeManager.applyThemeToView(binding.toolbar, it)
        }

        return true
    }

    private fun logout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.logout_confirm_title)
            .setMessage(R.string.logout_confirm_message)
            .setPositiveButton(R.string.logout_yes) { _, _ ->
                val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
                prefs.edit {
                    remove("username")
                    remove("password")
                }
                showToast(getString(R.string.logged_out))
                val intent = Intent(this, SplashActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                intent.putExtra("extra_skip_autologin", true)
                startActivity(intent)
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadAndInstallApk() {
        val downloadProgressBar = binding.root.findViewById<ProgressBar>(R.id.downloadProgressBar)
        val downloadProgressText = binding.root.findViewById<TextView>(R.id.downloadProgressText)
        val cancelDownloadButton = binding.root.findViewById<Button>(R.id.cancelDownloadButton)
        val progressOverlay = binding.root.findViewById<FrameLayout>(R.id.progressOverlay)
        val progressTitle = binding.root.findViewById<TextView>(R.id.progressTitle)

        progressTitle?.text = getString(R.string.download_update)
        progressOverlay.isVisible = true
        downloadProgressBar.progress = 0
        downloadProgressText.text = ""

        // Cancel button listener
        cancelDownloadButton.setOnClickListener {
            downloadJob?.cancel()
            progressOverlay.isVisible = false
            clearMenuAnimations()
            invalidateOptionsMenu()
            showToast("Download cancelled")
        }

        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val connection = URL(APK_URL).openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Server returned HTTP ${connection.responseCode}")
                }

                val fileLength = connection.contentLength
                val input = connection.inputStream
                val file = File(getExternalFilesDir(null), "lavender_update.apk")
                val output = FileOutputStream(file)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    // Check if job was cancelled
                    if (!isActive) {
                        output.close()
                        input.close()
                        connection.disconnect()
                        file.delete()
                        return@launch
                    }

                    total += count.toLong()
                    if (fileLength > 0) {
                        val progress = (total * 100 / fileLength).toInt()
                        val downloadedMb = total / (1024.0 * 1024.0)
                        val totalMb = fileLength / (1024.0 * 1024.0)
                        withContext(Dispatchers.Main) {
                            downloadProgressBar.progress = progress
                            downloadProgressText.text = String.format(Locale.US, "%.2f / %.2f MB", downloadedMb, totalMb)
                        }
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                withContext(Dispatchers.Main) {
                    progressOverlay.isVisible = false
                    clearMenuAnimations()
                    invalidateOptionsMenu()
                    installApk(file)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.isVisible = false
                    clearMenuAnimations()
                    invalidateOptionsMenu()
                    showToast("Download error: ${e.message}", Toast.LENGTH_LONG)
                }
            }
        }
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun showChatActionSheet() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_chat_actions, binding.root, false)
        
        sheetView.findViewById<View>(R.id.actionStartChat).setOnClickListener {
            bottomSheetDialog.dismiss()
            showCreateDirectChatDialog()
        }
        
        sheetView.findViewById<View>(R.id.actionAddContact).setOnClickListener {
            bottomSheetDialog.dismiss()
            showAddContactDialog()
        }
        
        sheetView.findViewById<View>(R.id.actionAddGroup).setOnClickListener {
            bottomSheetDialog.dismiss()
            showCreateChatDialog()
        }
        
        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }

    private fun showCreateDirectChatDialog() {
        binding.progressOverlay.isVisible = true
        
        // Fetch contacts first to only show them in the direct chat dialog
        grpcClient.getContacts(username) { contacts ->
            runOnUiThread {
                binding.progressOverlay.isVisible = false
                val dialogView = layoutInflater.inflate(R.layout.dialog_create_direct_chat, null)
                
                val searchEditText = dialogView.findViewById<EditText>(R.id.searchEditText)
                val usersRecyclerView = dialogView.findViewById<RecyclerView>(R.id.usersRecyclerView)
                val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
                val btnStartChat = dialogView.findViewById<MaterialButton>(R.id.btnStartChat)
                
                // Show ONLY contacts, sorted by online status
                val filteredUsers = contacts.filter { it != username }.sortedWith(
                    compareByDescending<String> { grpcClient.users.value.contains(it) }.thenBy { it }
                )
                
                var selectedUser: String? = null
                
                val userAdapter = lavender.client.android.ui.adapter.UserAdapter(
                    onUserClick = { user ->
                        selectedUser = user
                        btnStartChat.isEnabled = true
                    },
                    avatarCache = grpcClient.getAvatarCache(),
                    onlineUsers = grpcClient.users.value
                )
                
                usersRecyclerView.adapter = userAdapter
                userAdapter.setUsers(filteredUsers)
                
                val dialog = AlertDialog.Builder(this)
                    .setView(dialogView)
                    .create()
                
                searchEditText.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val query = s.toString().lowercase()
                        val searchFiltered = filteredUsers.filter { it.lowercase().contains(query) }
                        userAdapter.setUsers(searchFiltered)
                    }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })
                
                btnCancel.setOnClickListener { dialog.dismiss() }
                
                btnStartChat.setOnClickListener {
                    Log.d("ChatList", "Direct chat start clicked for user: $selectedUser")
                    selectedUser?.let { targetUser ->
                        dialog.dismiss()
                        createDirectChat(targetUser)
                    }
                }
                
                dialog.show()
            }
        }
    }

    private fun showCreateChatDialog() {
        binding.progressOverlay.isVisible = true
        // Ensure buttons are restored if dialog fails to show or after it finishes
        val resetButtons = {
            runOnUiThread {
                binding.addChatFab.isEnabled = true
                binding.addChatFab.setImageResource(android.R.drawable.ic_input_add)
                binding.addChatFab.clearAnimation()
                clearMenuAnimations()
                invalidateOptionsMenu()
            }
        }

        grpcClient.getContacts(username) { contacts ->
            if (contacts.isEmpty()) {
                runOnUiThread {
                    binding.progressOverlay.isVisible = false
                    resetButtons()
                    showAddContactDialog()
                }
                return@getContacts
            }

            // Load all users and online users to show status in filtered contact list
            grpcClient.loadUsers()

            lifecycleScope.launch {
                // Wait a bit for online users to load
                delay(300)
                val onlineUsers = grpcClient.users.value

                runOnUiThread {
                    binding.progressOverlay.isVisible = false
                    resetButtons()
                    val dialogView = layoutInflater.inflate(R.layout.dialog_create_group, null)

                    // Set dialog background using Material Design colors
                    val typedValue = TypedValue()
                    if (isDarkTheme()) {
                        theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
                        dialogView.setBackgroundColor(typedValue.data)
                    }

                    val groupNameInput = dialogView.findViewById<EditText>(R.id.groupNameInput)
                    val usersContainer = dialogView.findViewById<LinearLayout>(R.id.usersContainer)
                    val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
                    val btnCreate = dialogView.findViewById<MaterialButton>(R.id.btnCreate)
                    val groupInputLayout = dialogView.findViewById<TextInputLayout>(R.id.groupInputLayout)

                    // Set TextInputLayout background and button strokes in dark theme
                    if (isDarkTheme()) {
                        val surfaceValue = TypedValue()
                        theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, surfaceValue, true)
                        groupInputLayout.boxBackgroundColor = surfaceValue.data

                        val primaryValue = TypedValue()
                        theme.resolveAttribute(androidR.attr.colorPrimary, primaryValue, true)
                        val strokeColor = ColorStateList.valueOf(primaryValue.data)
                        btnCancel.strokeColor = strokeColor
                        btnCancel.strokeWidth = 2
                        btnCreate.strokeColor = strokeColor
                        btnCreate.strokeWidth = 2
                    }
                    
                    val selectedUsers = mutableSetOf<String>()

                    // Sort filtered users (contacts only): online first, then offline
                    val sortedUsers = contacts.sortedWith(compareByDescending<String> { onlineUsers.contains(it) }.thenBy { it })

                    for (user in sortedUsers) {
                        val userView = layoutInflater.inflate(R.layout.item_user_selectable, usersContainer, false)
                        val statusIndicator = userView.findViewById<View>(R.id.statusIndicator)
                        val usernameText = userView.findViewById<TextView>(R.id.usernameText)
                        val userAvatar = userView.findViewById<CircleImageView>(R.id.userAvatar)
                        val checkBox = userView.findViewById<CheckBox>(R.id.userCheckBox)

                        val isOnline = onlineUsers.contains(user)
                        statusIndicator.backgroundTintList = ColorStateList.valueOf(
                            if (isOnline) getColor(androidR.color.holo_green_dark)
                            else getColor(androidR.color.darker_gray)
                        )

                        usernameText.text = user
                        
                        // Set avatar
                        val avatarCache = grpcClient.getAvatarCache()
                        val cachedAvatarUrl = avatarCache[user]
                        if (!cachedAvatarUrl.isNullOrEmpty()) {
                            Glide.with(this@ChatListActivity)
                                .load(cachedAvatarUrl)
                                .placeholder(R.drawable.ic_default_avatar)
                                .circleCrop()
                                .into(userAvatar)
                        } else {
                            userAvatar.setImageResource(R.drawable.ic_default_avatar)
                        }

                        userView.setOnClickListener {
                            checkBox.isChecked = !checkBox.isChecked
                            if (checkBox.isChecked) selectedUsers.add(user) else selectedUsers.remove(user)
                        }
                        checkBox.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) selectedUsers.add(user) else selectedUsers.remove(user)
                        }

                        usersContainer.addView(userView)
                    }

                    val dialog = AlertDialog.Builder(this@ChatListActivity)
                        .setView(dialogView)
                        .setOnDismissListener {
                            resetButtons()
                        }
                        .create()

                    // Set up button listeners
                    btnCancel.setOnClickListener {
                        dialog.dismiss()
                    }

                    btnCreate.setOnClickListener {
                        val groupName = groupNameInput.text.toString().trim()
                        if (selectedUsers.isEmpty()) {
                            showToast(getString(R.string.select_at_least_one_user))
                            return@setOnClickListener
                        }
                        
                        Log.d("ChatList", "Group dialog create clicked. Name: $groupName, Users: $selectedUsers")
                        binding.progressOverlay.isVisible = true

                        if (selectedUsers.size == 1 && groupName.isEmpty()) {
                            // Create direct chat
                            Log.d("ChatList", "Single user and no name - redirecting to createDirectChat")
                            dialog.dismiss()
                            createDirectChat(selectedUsers.first())
                        } else {
                            // Create group chat
                            val finalGroupName = groupName.ifEmpty { getString(R.string.default_group_name) }
                            Log.d("ChatList", "Creating group: $finalGroupName")
                            dialog.dismiss()
                            createGroupChat(finalGroupName, (selectedUsers + username).toList())
                        }
                    }

                    dialog.show()
                }
            }
        }
    }

    private fun showAddContactDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_contact, null)
        
        // Set dialog background using Material Design colors
        val typedValue = TypedValue()
        if (isDarkTheme()) {
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
            dialogView.setBackgroundColor(typedValue.data)
        }

        val searchEditText = dialogView.findViewById<EditText>(R.id.searchEditText)
        val usersRecyclerView = dialogView.findViewById<RecyclerView>(R.id.usersRecyclerView)
        val createChatCheckbox = dialogView.findViewById<CheckBox>(R.id.createChatCheckbox)
        val btnAdd = dialogView.findViewById<MaterialButton>(R.id.btnAdd)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)

        val allUsers = mutableListOf<String>()
        val filteredUsers = mutableListOf<String>()
        val userAdapter = UserAdapter(
            onUserClick = { selected ->
                btnAdd.isEnabled = selected != username
            },
            avatarCache = grpcClient.getAvatarCache()
        )

        usersRecyclerView.adapter = userAdapter
        usersRecyclerView.layoutManager = LinearLayoutManager(this)

        grpcClient.loadAllUsers()
        lifecycleScope.launch {
            delay(500)
            allUsers.clear()
            allUsers.addAll(grpcClient.allUsers.value.filter { it != username })
            filteredUsers.clear()
            filteredUsers.addAll(allUsers)
            runOnUiThread { userAdapter.setUsers(filteredUsers) }
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

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setOnDismissListener {
                binding.addChatFab.isEnabled = true
                binding.addChatFab.setImageResource(android.R.drawable.ic_input_add)
                binding.addChatFab.clearAnimation()
                clearMenuAnimations()
                invalidateOptionsMenu()
            }
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnAdd.setOnClickListener {
            val selected = userAdapter.getSelectedUser() ?: return@setOnClickListener
            grpcClient.addContact(username, selected) { success, message ->
                runOnUiThread {
                    if (success) {
                        showToast(getString(R.string.contact_added))
                        if (createChatCheckbox.isChecked) {
                            createDirectChat(selected)
                        }
                        dialog.dismiss()
                    } else {
                        showToast(message)
                    }
                }
            }
        }

        dialog.show()
    }

    private fun createDirectChat(targetUser: String) {
        if (targetUser == username) {
            showToast(getString(R.string.cannot_chat_with_yourself))
            binding.progressOverlay.isVisible = false
            return
        }

        binding.root.findViewById<TextView>(R.id.progressTitle)?.text = getString(R.string.loading)
        binding.progressOverlay.isVisible = true
        lifecycleScope.launch {
            Log.d("ChatList", "Creating direct chat with $targetUser")
            grpcClient.createDirectChat(username, targetUser) { chatId ->
                Log.d("ChatList", "Create direct chat result: $chatId")
                runOnUiThread { binding.progressOverlay.isVisible = false }
                if (chatId != null) {
                    runOnUiThread {
                        openChat(
                            chatId = chatId,
                            roomName = getString(R.string.private_chat_with, targetUser),
                            isDirect = true,
                            participants = "[\"$username\", \"$targetUser\"]",
                            creator = username
                        )
                        showToast(getString(R.string.chat_created_with, targetUser))
                    }
                } else {
                    runOnUiThread {
                        showToast(getString(R.string.failed_to_create_chat))
                    }
                }
            }
        }
    }

    private fun createGroupChat(name: String, participants: List<String>) {
        binding.root.findViewById<TextView>(R.id.progressTitle)?.text = getString(R.string.loading)
        binding.progressOverlay.isVisible = true
        lifecycleScope.launch {
            Log.d("ChatList", "Creating group chat $name with ${participants.size} participants")
            grpcClient.createGroupChat(name, participants, username) { chatId ->
                Log.d("ChatList", "Create group chat result: $chatId")
                runOnUiThread { binding.progressOverlay.isVisible = false }
                if (chatId != null) {
                    runOnUiThread {
                        openChat(
                            chatId = chatId,
                            roomName = name,
                            isDirect = false,
                            participants = JSONArray(participants).toString(),
                            creator = username
                        )
                        showToast(getString(R.string.group_created, name))
                    }
                } else {
                    runOnUiThread {
                        showToast(getString(R.string.failed_to_create_chat))
                    }
                }
            }
        }
    }

    private fun applySavedColorScheme() {
        val theme = when (getSavedColorScheme()) {
            "light" -> R.style.Theme_Lavender_Light_NoActionBar
            else -> R.style.Theme_Lavender_Dark_NoActionBar
        }
        setTheme(theme)
    }

    private fun applySavedLanguage() {
        val language = getSavedLanguage() ?: "en"
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun getSavedColorScheme(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("color_scheme", null)
    }

    private fun getSavedLanguage(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("language", null)
    }

    private fun saveLanguage(languageCode: String) {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit {
            putString("language", languageCode)
        }
    }

    private fun toggleLanguage() {
        val currentLanguage = getSavedLanguage() ?: "en"
        val newLanguage = if (currentLanguage == "en") "ru" else "en"
        saveLanguage(newLanguage)
        setLocale(newLanguage)
        recreate()
    }

    private fun setLocale(languageCode: String) {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun checkForUpdates() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(VERSION_CHECK_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val latestVersion = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                    val isAvailable = isUpdateAvailable(latestVersion)

                    // Save update availability to SharedPreferences
                    getSharedPreferences("UpdatePrefs", MODE_PRIVATE).edit {
                        putBoolean("update_available", isAvailable)
                        putString("latest_version", latestVersion)
                    }

                    withContext(Dispatchers.Main) {
                        binding.updateAvailableIcon.isVisible = isAvailable
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("ChatListActivity", "Version check failed: ${e.message}")
            }
        }
    }

    private fun isUpdateAvailable(latest: String): Boolean {
        // Compare versions in format MAJOR.MINOR.PATCH.BUILD (e.g., 1.0.1.22)
        val currentVersion = BuildConfig.VERSION_NAME
        val currentParts = currentVersion.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }

        if (currentParts.isEmpty() || latestParts.isEmpty()) return false

        for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
            val currentPart = currentParts.getOrNull(i) ?: 0
            val latestPart = latestParts.getOrNull(i) ?: 0

            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }

        return false
    }


    private fun showAboutDialog() {
        val clientVersion = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName
        } catch (e: Exception) {
            "1.0.2.0"
        }

        val serverVersion = grpcClient.serverVersion.value.ifEmpty { "..." }
        val latestVersion = getSharedPreferences("UpdatePrefs", MODE_PRIVATE).getString("latest_version", "") ?: ""
        val isUpdateAvailable = isUpdateAvailable(latestVersion)

        val developerEmail = "ferzferz11@gmail.com"

        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
        val clientVersionText = dialogView.findViewById<TextView>(R.id.clientVersionText)
        val serverVersionText = dialogView.findViewById<TextView>(R.id.serverVersionText)
        val btnUpdate = dialogView.findViewById<Button>(R.id.btnUpdate)
        val btnFeedback = dialogView.findViewById<Button>(R.id.btnFeedback)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)

        clientVersionText.text = getString(R.string.version_label, clientVersion)
        serverVersionText.text = getString(R.string.server_version_format, serverVersion)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnUpdate.isVisible = isUpdateAvailable
        btnUpdate.setOnClickListener {
            dialog.dismiss()
            downloadAndInstallApk()
        }

        btnFeedback.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(developerEmail))
                putExtra(Intent.EXTRA_SUBJECT, "Lavender Messenger Feedback")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                showToast("No email app found")
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
