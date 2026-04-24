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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.fcm.NotificationHistory
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.databinding.ActivityChatListBinding
import lavender.client.android.ui.adapter.ChatAdapter
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
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
    private var username: String = ""
    private var password: String = ""
    private var downloadJob: Job? = null
    private var colorSchemeMenuItem: MenuItem? = null
    private var currentTheme: String? = null
    private var currentChats: List<ChatInfo> = emptyList()

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        val toast = Toast.makeText(this, message, duration)
        toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 100)
        toast.show()
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

        username = intent.getStringExtra("username") ?: ""
        password = intent.getStringExtra("password") ?: ""

        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                // Возвращаемся на главную страницу приложения
                val intent = Intent(this@ChatListActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                intent.putExtra("extra_skip_autologin", true)
                startActivity(intent)
                finish()
            }
        }

        // Handle system back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.getSelectedChats().isNotEmpty()) {
                    adapter.clearSelection()
                } else {
                    // Возвращаемся на главную страницу приложения
                    val intent = Intent(this@ChatListActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    intent.putExtra("extra_skip_autologin", true)
                    startActivity(intent)
                    finish()
                }
            }
        })

        binding.addChatFab.setOnClickListener {
            showCreateChatDialog()
        }

        // Check for update availability from SharedPreferences
        val updatePrefs = getSharedPreferences("UpdatePrefs", MODE_PRIVATE)
        val updateAvailable = updatePrefs.getBoolean("update_available", false)
        val updateIcon = binding.root.findViewById<ImageView>(R.id.updateAvailableIcon)
        updateIcon.isVisible = updateAvailable

        // Handle update icon click - navigate to MainActivity for update
        updateIcon.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        }

        adapter = ChatAdapter(
            onChatClick = { chat ->
                openChat(chat.id, chat.name)
            },
            onSelectionChanged = { count ->
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
            initialAvatarCache = grpcClient.getAvatarCache()
        )
        binding.chatsRecyclerView.adapter = adapter
        binding.chatsRecyclerView.layoutManager = LinearLayoutManager(this)

        // Check for updates automatically only once per session
        if (!grpcClient.hasCheckedForUpdates) {
            checkForUpdates()
            grpcClient.hasCheckedForUpdates = true
        }

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

        loadChats()
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
                grpcClient.getChats(username) { chats ->
                    if (chats.isNotEmpty()) {
                        val sortedChats = chats.sortedByDescending { it.lastMessageTime }
                        // Check if chats have actually changed
                        val chatsChanged = currentChats.size != sortedChats.size ||
                                currentChats.zip(sortedChats).any { (old, new) ->
                                    old.id != new.id ||
                                    old.name != new.name ||
                                    old.type != new.type ||
                                    old.unreadCount != new.unreadCount ||
                                    old.lastMessageTime != new.lastMessageTime
                                }

                        if (!chatsChanged) {
                            return@getChats
                        }

                        currentChats = sortedChats

                        // Load avatars for new participants first
                        val allParticipants = mutableSetOf<String>()
                        for (chat in sortedChats) {
                            if (chat.participants.isNotEmpty()) {
                                try {
                                    val participants = JSONArray(chat.participants)
                                    for (i in 0 until participants.length()) {
                                        allParticipants.add(participants.optString(i))
                                    }
                                } catch (_: Exception) {
                                    // JSON parsing failed
                                }
                            }
                        }

                        // Load avatars for all participants
                        val loadedCount = AtomicInteger(0)
                        val totalParticipants = allParticipants.size

                        for (participant in allParticipants) {
                            grpcClient.getUserAvatar(participant) { avatarUrl ->
                                if (avatarUrl.isEmpty()) {
                                    grpcClient.updateAvatarCache(participant, "")
                                }
                                val loaded = loadedCount.incrementAndGet()
                                if (loaded == totalParticipants) {
                                    runOnUiThread {
                                        adapter.setChats(sortedChats)
                                        adapter.updateAvatarCache(grpcClient.getAvatarCache())
                                    }
                                }
                            }
                        }

                        // Fallback: show chats after 2 seconds if not all avatars loaded
                        lifecycleScope.launch {
                            delay(2000)
                            runOnUiThread {
                                if (loadedCount.get() < totalParticipants) {
                                    if (currentChats != sortedChats) {
                                        adapter.setChats(sortedChats)
                                    }
                                    adapter.updateAvatarCache(grpcClient.getAvatarCache())
                                }
                            }
                        }

                        // Fallback: show chats immediately if no participants
                        if (allParticipants.isEmpty()) {
                            runOnUiThread {
                                if (currentChats != sortedChats) {
                                    adapter.setChats(sortedChats)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Check if theme has changed in another activity
        val savedTheme = getSavedColorScheme() ?: "dark"
        if (savedTheme != currentTheme) {
            recreate()
            return
        }

        // Refresh chats immediately when returning from chat
        grpcClient.getChats(username) { chats ->
            if (chats.isNotEmpty()) {
                val sortedChats = chats.sortedByDescending { it.lastMessageTime }
                // Check if chats have actually changed
                val chatsChanged = currentChats.size != sortedChats.size ||
                        currentChats.zip(sortedChats).any { (old, new) ->
                            old.id != new.id ||
                            old.name != new.name ||
                            old.type != new.type ||
                            old.unreadCount != new.unreadCount ||
                            old.createdAt != new.createdAt
                        }

                if (!chatsChanged) {
                    return@getChats
                }

                currentChats = sortedChats
                // Load avatars for all participants first
                val allParticipants = mutableSetOf<String>()
                for (chat in sortedChats) {
                    if (chat.participants.isNotEmpty()) {
                        try {
                            val participants = JSONArray(chat.participants)
                            for (i in 0 until participants.length()) {
                                allParticipants.add(participants.optString(i))
                            }
                        } catch (_: Exception) {
                            // JSON parsing failed
                        }
                    }
                }

                // Load avatars for all participants
                val loadedCount = AtomicInteger(0)
                val totalParticipants = allParticipants.size

                for (participant in allParticipants) {
                    grpcClient.getUserAvatar(participant) { avatarUrl ->
                        if (avatarUrl.isEmpty()) {
                            grpcClient.updateAvatarCache(participant, "")
                        }
                        val loaded = loadedCount.incrementAndGet()
                        if (loaded == totalParticipants) {
                            runOnUiThread {
                                adapter.setChats(sortedChats)
                                adapter.updateAvatarCache(grpcClient.getAvatarCache())
                            }
                        }
                    }
                }

                // Fallback: show chats after 2 seconds if not all avatars loaded
                lifecycleScope.launch {
                    delay(2000)
                    runOnUiThread {
                        if (loadedCount.get() < totalParticipants) {
                            if (currentChats != sortedChats) {
                                adapter.setChats(sortedChats)
                            }
                            adapter.updateAvatarCache(grpcClient.getAvatarCache())
                        }
                    }
                }

                // Fallback: show chats immediately if no participants
                if (allParticipants.isEmpty()) {
                    runOnUiThread {
                        if (currentChats != sortedChats) {
                            adapter.setChats(sortedChats)
                        }
                    }
                }
            }
        }
    }

    private fun loadChats() {
        binding.root.findViewById<TextView>(R.id.progressTitle)?.text = getString(R.string.loading)
        binding.progressOverlay.isVisible = true
        lifecycleScope.launch {
            val serverAddress = getString(R.string.server_address)
            val (host, port) = serverAddress.split(":")
            grpcClient.connect(host, false, port.toInt(), applicationContext)

            // Send auth message first
            grpcClient.startChat(username, password, "") { _ -> }

            // Wait a bit for auth to complete
            delay(500)

            // Check auth result
            try {
                coroutineScope {
                    val notification = grpcClient.systemNotification.take(1).first()
                    when (notification) {
                        "auth_failed" -> {
                            runOnUiThread {
                                binding.progressOverlay.isVisible = false
                                showToast(getString(R.string.auth_failed), Toast.LENGTH_LONG)
                                grpcClient.disconnect()
                                finish()
                            }
                            return@coroutineScope
                        }
                        "registration_success" -> {
                            // Auth successful, continue loading chats
                        }
                    }
                    grpcClient.clearSystemNotification()
                }
            } catch (_: Exception) {
                // No auth notification received, might be already authenticated
            }

            grpcClient.getChats(username) { chats ->
                if (chats.isEmpty()) {
                    runOnUiThread {
                        // Если нет чатов, открываем general чат
                        openChat("general", getString(R.string.general_chat))
                    }
                } else {
                    val sortedChats = chats.sortedByDescending { it.lastMessageTime }
                    currentChats = sortedChats
                    // Load avatars for all chat participants first
                        val allParticipants = mutableSetOf<String>()
                        for (chat in sortedChats) {
                            if (chat.participants.isNotEmpty()) {
                                try {
                                    val participants = JSONArray(chat.participants)
                                    for (i in 0 until participants.length()) {
                                        allParticipants.add(participants.optString(i))
                                    }
                                } catch (_: Exception) {
                                    // JSON parsing failed
                                }
                            }
                        }

                    // Load avatars for all participants
                    val loadedCount = AtomicInteger(0)
                    val totalParticipants = allParticipants.size

                    for (participant in allParticipants) {
                        grpcClient.getUserAvatar(participant) { avatarUrl ->
                            // Save in cache (including empty strings to avoid retrying)
                            grpcClient.updateAvatarCache(participant, avatarUrl)
                            val loaded = loadedCount.incrementAndGet()
                            // Show chats after all avatars are loaded
                            if (loaded == totalParticipants) {
                                runOnUiThread {
                                    binding.progressOverlay.isVisible = false
                                    // Show/hide welcome message
                                    binding.welcomeContainer.isVisible = chats.isEmpty()
                                    binding.chatsRecyclerView.isVisible = chats.isNotEmpty()

                                    // Only update if chats changed
                                    if (currentChats != sortedChats) {
                                        adapter.setChats(sortedChats)
                                    }
                                    adapter.updateAvatarCache(grpcClient.getAvatarCache())
                                }
                            }
                        }
                    }

                    // Fallback: show chats after 5 seconds if not all avatars loaded
                    lifecycleScope.launch {
                        delay(5000)
                        runOnUiThread {
                            if (loadedCount.get() < totalParticipants) {
                                binding.progressOverlay.isVisible = false
                                // Show/hide welcome message
                                binding.welcomeContainer.isVisible = sortedChats.isEmpty()
                                binding.chatsRecyclerView.isVisible = sortedChats.isNotEmpty()

                                if (currentChats != sortedChats) {
                                    adapter.setChats(sortedChats)
                                }
                                adapter.updateAvatarCache(grpcClient.getAvatarCache())
                            }
                        }
                    }

                    // Fallback: show chats immediately if no participants
                    if (allParticipants.isEmpty()) {
                        runOnUiThread {
                            binding.progressOverlay.isVisible = false
                            // Show/hide welcome message
                            binding.welcomeContainer.isVisible = sortedChats.isEmpty()
                            binding.chatsRecyclerView.isVisible = sortedChats.isNotEmpty()

                            if (currentChats != sortedChats) {
                                adapter.setChats(sortedChats)
                            }
                        }
                    }
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

    private fun openChat(chatId: String, roomName: String? = null) {
        lifecycleScope.launch {
            val chat = adapter.getChats().find { it.id == chatId }
            val intent = Intent(this@ChatListActivity, NewChatActivity::class.java)
                .putExtra("USERNAME", username)
                .putExtra("PASSWORD", password)
                .putExtra("ROOM_ID", chatId)
                .putExtra("CHAT_NAME", roomName ?: chat?.name ?: "Chat")
                .putExtra("IS_DIRECT", chat?.type == "direct")
            
            if (chat != null && chat.participants.isNotEmpty()) {
                intent.putExtra("PARTICIPANTS", chat.participants)
            }
            
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        grpcClient.disconnect()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            androidR.id.home -> {
                if (adapter.getSelectedChats().isNotEmpty()) {
                    adapter.clearSelection()
                } else {
                    // Возвращаемся на главную страницу приложения
                    val intent = Intent(this@ChatListActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                    intent.putExtra("extra_skip_autologin", true)
                    startActivity(intent)
                    finish()
                }
                true
            }
            R.id.action_add_chat -> {
                showCreateChatDialog()
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
            R.id.action_color_scheme -> {
                toggleColorScheme()
                true
            }
            R.id.action_notification_history -> {
                showNotificationHistory()
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
            R.id.action_toggle_language -> {
                toggleLanguage()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chat_list_menu, menu)

        val hasSelection = adapter.getSelectedChats().isNotEmpty()
        menu.findItem(R.id.action_add_chat)?.apply { isVisible = !hasSelection }
        menu.findItem(R.id.action_delete)?.apply { isVisible = hasSelection }
        menu.findItem(R.id.action_toggle_language)?.apply { isVisible = !hasSelection }
        menu.findItem(R.id.action_color_scheme)?.apply { isVisible = !hasSelection }
        menu.findItem(R.id.action_profile)?.apply { isVisible = !hasSelection }
        menu.findItem(R.id.action_notification_history)?.apply { isVisible = !hasSelection }
        menu.findItem(R.id.action_update)?.apply { isVisible = !hasSelection }
        menu.findItem(R.id.action_logout)?.apply { isVisible = !hasSelection }

        // Save reference to color scheme menu item
        colorSchemeMenuItem = menu.findItem(R.id.action_color_scheme)
        updateColorSchemeIcon()

        // Force overflow icon and its tint
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
        val color = typedValue.data
        
        binding.toolbar.overflowIcon = ContextCompat.getDrawable(this, R.drawable.ic_overflow_settings)?.apply {
            setTint(color)
        }

        return true
    }

    private fun logout() {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit {
            remove("username")
            remove("password")
        }

        // Show exit toast
        showToast(getString(R.string.logged_out))

        // Clear activity stack and go back to MainActivity
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
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
                    installApk(file)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressOverlay.isVisible = false
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

    private fun showCreateChatDialog() {
        binding.root.findViewById<TextView>(R.id.progressTitle)?.text = getString(R.string.loading)
        binding.progressOverlay.isVisible = true
        // Load all users and online users
        grpcClient.loadAllUsers()
        grpcClient.loadUsers()

        lifecycleScope.launch {
            // Wait a bit for users to load
            delay(500)

            val allUsers = grpcClient.allUsers.value.filter { it != username }
            val onlineUsers = grpcClient.users.value

            if (allUsers.isEmpty()) {
                runOnUiThread {
                    binding.progressOverlay.isVisible = false
                    showToast(getString(R.string.no_users_available))
                }
                return@launch
            }

            runOnUiThread {
                binding.progressOverlay.isVisible = false
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

                // Sort users: online first, then offline
                val sortedUsers = allUsers.sortedWith(compareByDescending<String> { onlineUsers.contains(it) }.thenBy { it })

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
                    
                    binding.progressOverlay.isVisible = true

                    if (selectedUsers.size == 1 && groupName.isEmpty()) {
                        // Create direct chat
                        dialog.dismiss()
                        createDirectChat(selectedUsers.first())
                    } else {
                        // Create group chat
                        val finalGroupName = if (groupName.isEmpty()) {
                            (selectedUsers + username).joinToString(", ")
                        } else {
                            groupName
                        }
                        dialog.dismiss()
                        createGroupChat(finalGroupName, (selectedUsers + username).toList())
                    }
                }

                dialog.show()
            }
        }
    }

    private fun createDirectChat(targetUser: String) {
        if (targetUser == username) {
            showToast(getString(R.string.cannot_chat_with_yourself))
            binding.progressOverlay.isVisible = false
            return
        }

        binding.root.findViewById<TextView>(R.id.progressTitle)?.text = getString(R.string.loading)
        lifecycleScope.launch {
            grpcClient.createDirectChat(username, targetUser) { chatId ->
                runOnUiThread { binding.progressOverlay.isVisible = false }
                if (chatId != null) {
                    lifecycleScope.launch {
                        delay(500)
                        runOnUiThread {
                            openChat(chatId, getString(R.string.private_chat_with, targetUser))
                            showToast(getString(R.string.chat_created_with, targetUser))
                        }
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
        lifecycleScope.launch {
            grpcClient.createGroupChat(name, participants) { chatId ->
                runOnUiThread { binding.progressOverlay.isVisible = false }
                if (chatId != null) {
                    lifecycleScope.launch {
                        delay(500)
                        runOnUiThread {
                            openChat(chatId, name)
                            showToast(getString(R.string.group_created, name))
                        }
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
            "light" -> R.style.Theme_MsgClientAndroid
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

    private fun saveColorScheme(scheme: String) {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit { putString("color_scheme", scheme) }
    }

    private fun toggleColorScheme() {
        val schemes = listOf("light", "dark")
        val currentScheme = getSavedColorScheme() ?: "dark"
        val currentIndex = schemes.indexOf(currentScheme)
        val nextIndex = (currentIndex + 1) % schemes.size
        val newScheme = schemes[nextIndex]

        saveColorScheme(newScheme)
        recreate()
    }

    private fun updateColorSchemeIcon() {
        val currentScheme = getSavedColorScheme() ?: "dark"
        val iconRes = if (currentScheme == "dark") {
            R.drawable.ic_light_mode
        } else {
            R.drawable.ic_theme_dark
        }
        colorSchemeMenuItem?.setIcon(iconRes)
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
                        val updateIcon = binding.root.findViewById<ImageView>(R.id.updateAvailableIcon)
                        updateIcon?.isVisible = isAvailable
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

    private fun showNotificationHistory() {
        val notifications = NotificationHistory.getAll()

        // Create custom view for dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_notification_history, null)
        val notificationsText = dialogView.findViewById<TextView>(R.id.notificationsText)
        val fcmTokenText = dialogView.findViewById<TextView>(R.id.fcmTokenText)
        val copyTokenButton = dialogView.findViewById<Button>(R.id.copyTokenButton)
        val testNotificationButton = dialogView.findViewById<Button>(R.id.testNotificationButton)

        // Display notifications
        if (notifications.isEmpty()) {
            notificationsText.text = getString(R.string.no_notifications)
        } else {
            val message = notifications.joinToString("\n\n") { notif ->
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(Date(notif.timestamp))
                "[$time] ${notif.title}\n${notif.body}${if (notif.from != null) "\nFrom: ${notif.from}" else ""}"
            }
            notificationsText.text = message
        }

        // Get and display FCM token
        lifecycleScope.launch {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    fcmTokenText.text = token
                    fcmTokenText.setOnClickListener {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("FCM Token", token)
                        clipboard.setPrimaryClip(clip)
                        showToast(getString(R.string.token_copied))
                    }
                } else {
                    fcmTokenText.text = getString(R.string.failed_to_get_token)
                }
            }
        }

        copyTokenButton.setOnClickListener {
            val token = fcmTokenText.text.toString()
            if (token.isNotEmpty() && token != getString(R.string.failed_to_get_token)) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("FCM Token", token)
                clipboard.setPrimaryClip(clip)
                showToast(getString(R.string.token_copied))
            }
        }

        testNotificationButton.setOnClickListener {
            // Simulate receiving a notification
            NotificationHistory.add("Test Notification", "This is a test notification from the app", "local")
            notificationsText.text = NotificationHistory.getAll()
                .joinToString("\n\n") { notif ->
                    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        .format(Date(notif.timestamp))
                    "[$time] ${notif.title}\n${notif.body}${if (notif.from != null) "\nFrom: ${notif.from}" else ""}"
                }
            showToast(getString(R.string.test_notification_added))

            // Create notification channel if needed
            val channelId = "lavender_messaging_channel"
            val channelName = "Lavender Messages"
            val channelDescription = "Notifications for new messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)

            // Also show a real system notification
            val notificationId = 9999
            val notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Test Notification")
                .setContentText("This is a test notification from the app")
                .setSmallIcon(R.drawable.ic_message_sent)
                .setAutoCancel(true)
                .build()

            val notificationManagerCompat = NotificationManagerCompat.from(this)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManagerCompat.notify(notificationId, notification)
            } else {
                showToast("Notification permission not granted")
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.notifications))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.clear)) { _, _ ->
                NotificationHistory.clear()
                showToast(getString(R.string.history_cleared))
            }
            .setNegativeButton(androidR.string.ok, null)
            .create()

        dialog.show()
    }
}
