package lavender.client.android

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import lavender.client.android.data.grpc.GrpcClient
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
import java.net.URL
import java.util.*
import de.hdodenhof.circleimageview.CircleImageView

class ChatListActivity : AppCompatActivity() {

    companion object {
        private const val APK_URL = "http://159.195.38.145:8081/lavender.apk"
        private const val VERSION_CHECK_URL = "http://159.195.38.145:8081/version.txt"
    }

    private lateinit var binding: ActivityChatListBinding
    private lateinit var adapter: ChatAdapter
    private val grpcClient = GrpcClient
    private val viewModel: ChatListViewModel by viewModels()
    private var username: String = ""
    private var password: String = ""
    private var downloadJob: Job? = null
    private var currentTheme: String? = null

    private val editProfileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            loadChats()
        }
    }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        currentTheme = getSavedColorScheme() ?: "dark"
        applySavedColorScheme()
        applySavedLanguage()
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        username = intent.getStringExtra("username") ?: ""
        password = intent.getStringExtra("password") ?: ""
        val serverAddressFull = intent.getStringExtra("serverAddress") ?: "159.195.38.145"

        // Parse server address and port
        val parts = serverAddressFull.split(":")
        val serverHost = parts[0]
        val serverPort = if (parts.size > 1) parts[1].toIntOrNull() ?: 50051 else 50051

        // Connect to gRPC server if not connected
        grpcClient.connect(serverHost, false, serverPort, this)
        
        // Start chat session (auth and history loading)
        grpcClient.startChat(username, password, "") { _ -> }

        checkForUpdates()

        lavender.client.android.ui.ThemeManager.loadTheme(this, username) {
            runOnUiThread {
                lavender.client.android.ui.ThemeManager.applyTheme(this)
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updatePadding(top = systemBars.top)
            binding.chatsRecyclerView.updatePadding(bottom = systemBars.bottom)
            binding.addChatFab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = 28.dpToPx() + systemBars.bottom
                marginEnd = 16.dpToPx()
            }
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = ""
            setDisplayHomeAsUpEnabled(false)
        }

        binding.root.findViewById<CircleImageView>(R.id.toolbarUserAvatar).setOnClickListener {
            showUserMenuSheet()
        }
        binding.toolbarTitle.setOnClickListener {
            showUserMenuSheet()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.getSelectedChats().isNotEmpty()) {
                    adapter.clearSelection()
                } else {
                    moveTaskToBack(true)
                }
            }
        })

        lifecycleScope.launch {
            grpcClient.connectionState.collect { connected ->
                runOnUiThread {
                    if (!connected) {
                        binding.toolbarTitle.text = getString(R.string.connecting)
                    } else if (binding.toolbarTitle.text == getString(R.string.connecting)) {
                        binding.toolbarTitle.text = getString(R.string.chats)
                    }
                }
            }
        }

        binding.addChatFab.setOnClickListener {
            showChatActionSheet()
        }
        
        updateToolbarAvatar()

        val updatePrefs = getSharedPreferences("UpdatePrefs", MODE_PRIVATE)
        val updateAvailable = updatePrefs.getBoolean("update_available", false)
        binding.updateAvailableIcon.isVisible = updateAvailable
        binding.updateAvailableIcon.setOnClickListener {
            showUpdateConfirmationDialog(true)
        }

        adapter = ChatAdapter(
            onChatClick = { chat ->
                openChat(chat.id, chat.getDisplayName(username), chat.type == "direct", chat.participants, chat.creator, chat.avatarUrl)
            },
            onSettingsClick = { chat ->
                val isDirect = chat.type == "direct"
                val intent = Intent(this, ProfileActivity::class.java)
                    .putExtra("username", chat.getDisplayName(username))
                    .putExtra("is_group", !isDirect)
                    .putExtra("room_id", chat.id)
                    .putExtra("avatar_url", chat.avatarUrl)
                    .putExtra("participants", chat.participants)
                    .putExtra("creator", chat.creator)
                startActivity(intent)
            },
            onSelectionChanged = { _ ->
                invalidateOptionsMenu()
            },
            currentUsername = username,
            initialAvatarCache = grpcClient.getAvatarCache(),
            onlineUsers = grpcClient.users.value
        )
        binding.chatsRecyclerView.adapter = adapter
        binding.chatsRecyclerView.layoutManager = LinearLayoutManager(this)

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadChats()
        }

        lifecycleScope.launch {
            grpcClient.systemNotification.collect { notification ->
                if (notification != null) {
                    runOnUiThread {
                        AlertDialog.Builder(this@ChatListActivity)
                            .setTitle(R.string.lavender_messenger)
                            .setMessage(notification)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                    grpcClient.clearSystemNotification()
                }
            }
        }

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

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                lifecycleScope.launch {
                    delay(1000)
                    if (username.isNotEmpty()) {
                        grpcClient.registerToken(username, token)
                    }
                }
            }
        }

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
            lifecycleScope.launch {
                var attempts = 0
                while (viewModel.currentChats.isEmpty() && attempts < 10) {
                    delay(500)
                    attempts++
                }
                val chat = viewModel.currentChats.find { it.id == roomId }
                if (chat != null) {
                    openChat(chat.id, chat.getDisplayName(username), chat.type == "direct", chat.participants, chat.creator, chat.avatarUrl)
                }
            }
        }

        val deleteChatId = intent.getStringExtra("ACTION_DELETE_CHAT_ID")
        if (!deleteChatId.isNullOrEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.delete_chats)
                .setMessage(getString(R.string.delete_chats_confirmation, 1))
                .setPositiveButton(R.string.delete) { _, _ ->
                    grpcClient.deleteChat(deleteChatId) { success, _ ->
                        if (success) {
                            runOnUiThread { showToast(getString(R.string.deleted_count, 1)) }
                            loadChats()
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun loadChats() {
        if (username.isEmpty()) return
        binding.swipeRefreshLayout.isRefreshing = true
        viewModel.loadChats(username) { success, _ ->
            runOnUiThread {
                binding.swipeRefreshLayout.isRefreshing = false
                if (success) {
                    adapter.setChats(viewModel.currentChats)
                    binding.welcomeContainer.isVisible = viewModel.currentChats.isEmpty()
                    binding.chatsRecyclerView.isVisible = viewModel.currentChats.isNotEmpty()
                    checkOnboarding(viewModel.currentChats)
                    refreshAvatars()
                }
                if (!grpcClient.hasCheckedForUpdates) {
                    checkForUpdates()
                    grpcClient.hasCheckedForUpdates = true
                }
            }
            lifecycleScope.launch {
                delay(10000)
                runOnUiThread {
                    if (binding.toolbarTitle.text == getString(R.string.connecting)) {
                        binding.toolbarTitle.text = getString(R.string.chats)
                    }
                }
            }
        }
    }

    private fun checkOnboarding(chats: List<ChatInfo>) {
        val isNewUser = chats.isEmpty()
        if (isNewUser && !binding.onboardingProfileBubble.isVisible) {
            binding.onboardingProfileBubble.visibility = View.VISIBLE
            binding.onboardingProfileBubble.alpha = 0f
            binding.onboardingProfileBubble.animate().alpha(1f).setDuration(500).start()

            binding.onboardingFabBubble.visibility = View.VISIBLE
            binding.onboardingFabBubble.alpha = 0f
            binding.onboardingFabBubble.animate().alpha(1f).setDuration(500).setStartDelay(300).start()
        } else if (!isNewUser) {
            binding.onboardingProfileBubble.isVisible = false
            binding.onboardingFabBubble.isVisible = false
        }

        if (isNewUser) {
            binding.onboardingProfileBubble.setOnClickListener { it.animate().alpha(0f).setDuration(300).withEndAction { it.visibility = View.GONE }.start() }
            binding.onboardingFabBubble.setOnClickListener { it.animate().alpha(0f).setDuration(300).withEndAction { it.visibility = View.GONE }.start() }
        }
    }

    private fun refreshAvatars() {
        if (username.isEmpty()) return
        
        // Fetch own profile first
        grpcClient.getUserProfile(username) { profile ->
            if (profile != null) {
                viewModel.avatarCache = grpcClient.getAvatarCache()
                runOnUiThread { updateToolbarAvatar() }
            }
        }

        val allParticipants = viewModel.currentChats.flatMap { chat ->
            try {
                val arr = JSONArray(chat.participants)
                List(arr.length()) { arr.getString(it) }
            } catch (_: Exception) { emptyList() }
        }.distinct().filter { it != username }
        
        if (allParticipants.isEmpty()) return

        var updateCount = 0
        for (participant in allParticipants) {
            grpcClient.getUserProfile(participant) { _ ->
                updateCount++
                if (updateCount % 5 == 0 || updateCount == allParticipants.size) {
                    viewModel.avatarCache = grpcClient.getAvatarCache()
                    runOnUiThread {
                        adapter.updateAvatarCache(viewModel.avatarCache)
                    }
                }
            }
        }
    }

    private fun loadAllUsers() {
        grpcClient.loadAllUsers()
    }

    private fun startPollingChats() {
        lifecycleScope.launch {
            while (isActive) {
                if (username.isNotEmpty()) {
                    val previousChatCount = viewModel.currentChats.size
                    viewModel.loadChats(username) { success, _ ->
                        if (success) {
                            runOnUiThread {
                                adapter.setChats(viewModel.currentChats)
                                binding.welcomeContainer.isVisible = viewModel.currentChats.isEmpty()
                                binding.chatsRecyclerView.isVisible = viewModel.currentChats.isNotEmpty()
                                checkOnboarding(viewModel.currentChats)
                                if (viewModel.currentChats.size != previousChatCount) {
                                    refreshAvatars()
                                }
                            }
                        }
                    }
                }
                delay(3000)
            }
        }
    }

    private fun updateToolbarAvatar() {
        val avatarView = binding.root.findViewById<CircleImageView>(R.id.toolbarUserAvatar) ?: return
        val avatarCache = grpcClient.getAvatarCache()
        val myAvatarUrl = avatarCache[username]
        if (!myAvatarUrl.isNullOrEmpty()) {
            Glide.with(this).load(myAvatarUrl).placeholder(R.drawable.ic_default_avatar).circleCrop().into(avatarView)
        } else {
            avatarView.setImageResource(R.drawable.ic_default_avatar)
        }
    }

    private fun openChat(chatId: String, roomName: String, isDirect: Boolean = false, participants: String = "[]", creator: String = "", avatarUrl: String = "") {
        val intent = Intent(this, NewChatActivity::class.java).apply {
            putExtra("ROOM_ID", chatId)
            putExtra("CHAT_NAME", roomName)
            putExtra("IS_DIRECT", isDirect)
            putExtra("PARTICIPANTS", participants)
            putExtra("CREATOR", creator)
            putExtra("AVATAR_URL", avatarUrl)
            putExtra("USERNAME", username)
            putExtra("PASSWORD", password)
        }
        startActivity(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> {
                binding.searchLayout.isVisible = !binding.searchLayout.isVisible
                if (binding.searchLayout.isVisible) {
                    binding.searchEditText.requestFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(binding.searchEditText, 0)
                } else {
                    binding.searchEditText.text?.clear()
                    adapter.filter("")
                }
                return true
            }
            R.id.action_delete -> {
                val selected = adapter.getSelectedChats()
                if (selected.isNotEmpty()) {
                    val dialogView = layoutInflater.inflate(R.layout.dialog_delete_chats, null)
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
                    if (isDarkTheme()) {
                        val primaryValue = TypedValue()
                        theme.resolveAttribute(android.R.attr.colorPrimary, primaryValue, true)
                        val strokeColor = ColorStateList.valueOf(primaryValue.data)
                        btnCancel.strokeColor = strokeColor
                        btnCancel.strokeWidth = 2
                        btnDelete.strokeColor = strokeColor
                        btnDelete.strokeWidth = 2
                    }
                    val dialog = AlertDialog.Builder(this).setView(dialogView).create()
                    btnCancel.setOnClickListener { dialog.dismiss() }
                    btnDelete.setOnClickListener {
                        dialog.dismiss()
                        binding.toolbarTitle.text = getString(R.string.loading)
                        val chatsToDelete = selected.toList()
                        adapter.clearSelection()
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
                                if (result.first) successCount++ else lastErrorMessage = result.second
                            }
                            runOnUiThread {
                                binding.toolbarTitle.text = getString(R.string.chats)
                                if (successCount > 0) showToast(getString(R.string.deleted_count, successCount))
                                if (lastErrorMessage.isNotEmpty()) showToast(lastErrorMessage)
                                loadChats()
                            }
                        }
                    }
                    dialog.show()
                }
                return true
            }
            R.id.action_update -> {
                showToast(getString(R.string.checking_for_updates))
                checkForUpdates { isUpdateAvailable ->
                    showUpdateConfirmationDialog(isUpdateAvailable)
                }
                return true
            }
            R.id.action_about -> {
                showAboutDialog()
                return true
            }
            R.id.action_super_admin -> {
                val intent = Intent(this, SuperAdminActivity::class.java)
                startActivity(intent)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        clearMenuAnimations()
        menuInflater.inflate(R.menu.chat_list_menu, menu)
        val hasSelection = adapter.getSelectedChats().isNotEmpty()
        
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
        val onPrimary = typedValue.data

        menu.findItem(R.id.action_search)?.apply { 
            isVisible = !hasSelection
            iconTintList = ColorStateList.valueOf(onPrimary)
        }
        menu.findItem(R.id.action_delete)?.apply { 
            isVisible = hasSelection 
            iconTintList = ColorStateList.valueOf(onPrimary)
        }
        menu.findItem(R.id.action_update)?.apply { isVisible = !hasSelection }
        return true
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun isDarkTheme(): Boolean {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("color_scheme", "dark") != "light"
    }

    private fun showAboutDialog() {
        val clientVersion = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName
        } catch (_: Exception) { BuildConfig.VERSION_NAME }
        val serverVersion = grpcClient.serverVersion.value.ifEmpty { "..." }
        val latestVersion = getSharedPreferences("UpdatePrefs", MODE_PRIVATE).getString("latest_version", "") ?: ""
        val isUpdateAvailable = isUpdateAvailable(latestVersion)
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
        dialogView.findViewById<TextView>(R.id.clientVersionText).text = getString(R.string.version_label, clientVersion)
        dialogView.findViewById<TextView>(R.id.serverVersionText).text = getString(R.string.server_version_format, serverVersion)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        val btnUpdate = dialogView.findViewById<Button>(R.id.btnUpdate)
        btnUpdate.isVisible = isUpdateAvailable
        btnUpdate.setOnClickListener {
            dialog.dismiss()
            showUpdateConfirmationDialog(true)
        }
        dialogView.findViewById<Button>(R.id.btnFeedback).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:".toUri()
                putExtra(Intent.EXTRA_EMAIL, arrayOf("ferzferz11@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Lavender Messenger Feedback")
            }
            try { startActivity(intent) } catch (_: Exception) { showToast("No email app found") }
        }
        dialogView.findViewById<Button>(R.id.btnShare).setOnClickListener {
            shareApp()
        }
        dialogView.findViewById<Button>(R.id.btnClose).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_app))
        shareIntent.putExtra(Intent.EXTRA_TEXT, APK_URL)
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app)))
    }

    private fun checkForUpdates(onComplete: ((Boolean) -> Unit)? = null) {
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
                    getSharedPreferences("UpdatePrefs", MODE_PRIVATE).edit {
                        putBoolean("update_available", isAvailable)
                        putString("latest_version", latestVersion)
                    }
                    withContext(Dispatchers.Main) {
                        binding.updateAvailableIcon.isVisible = isAvailable
                        onComplete?.invoke(isAvailable)
                    }
                } else withContext(Dispatchers.Main) { onComplete?.invoke(false) }
                connection.disconnect()
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onComplete?.invoke(false) }
            }
        }
    }

    private fun isUpdateAvailable(latest: String): Boolean {
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

    private fun showUpdateConfirmationDialog(isUpdateAvailable: Boolean) {
        val currentVersion = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName
        } catch (e: Exception) { BuildConfig.VERSION_NAME }
        val latestVersion = getSharedPreferences("UpdatePrefs", MODE_PRIVATE).getString("latest_version", currentVersion) ?: currentVersion
        val builder = AlertDialog.Builder(this)
        if (isUpdateAvailable) {
            builder.setTitle(R.string.update_available)
            builder.setMessage(getString(R.string.version_current, currentVersion) + "\n" + getString(R.string.version_available, latestVersion) + "\n\n" + getString(R.string.update_confirmation_message))
            builder.setPositiveButton(R.string.update_now) { _, _ -> downloadAndInstallApk() }
            builder.setNegativeButton(R.string.cancel, null)
        } else {
            builder.setTitle(R.string.no_updates_available)
            builder.setMessage(getString(R.string.version_current, currentVersion) + "\n\n" + getString(R.string.version_latest_message))
            builder.setPositiveButton(R.string.ok, null)
            builder.setNeutralButton(R.string.force_download) { _, _ -> downloadAndInstallApk() }
        }
        builder.show()
    }

    private fun downloadAndInstallApk() {
        val downloadProgressBar = binding.root.findViewById<ProgressBar>(R.id.downloadProgressBar)
        val downloadProgressText = binding.root.findViewById<TextView>(R.id.downloadProgressText)
        val manualInstallText = binding.root.findViewById<TextView>(R.id.manualInstallText)
        val cancelDownloadButton = binding.root.findViewById<Button>(R.id.cancelDownloadButton)
        val installNowButton = binding.root.findViewById<Button>(R.id.installNowButton)
        val progressTitle = binding.root.findViewById<TextView>(R.id.progressTitle)
        progressTitle?.text = getString(R.string.download_update)
        binding.progressOverlay.isVisible = true
        downloadProgressBar.isVisible = true
        downloadProgressText.isVisible = true
        downloadProgressBar.progress = 0
        downloadProgressText.text = ""
        manualInstallText.isVisible = false
        installNowButton.isVisible = false
        cancelDownloadButton.text = getString(R.string.cancel_dialog)
        cancelDownloadButton.setOnClickListener {
            downloadJob?.cancel()
            binding.progressOverlay.isVisible = false
            clearMenuAnimations()
            invalidateOptionsMenu()
        }
        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val connection = URL(APK_URL).openConnection() as HttpURLConnection
                connection.connect()
                if (connection.responseCode != HttpURLConnection.HTTP_OK) throw Exception("Server returned error")
                val fileLength = connection.contentLength
                val input = connection.inputStream
                val file = File(getExternalFilesDir(null), "lavender_update.apk")
                val output = FileOutputStream(file)
                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                while (input.read(data).also { count = it } != -1) {
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
                        withContext(Dispatchers.Main) {
                            downloadProgressBar.progress = progress
                            downloadProgressText.text = String.format(Locale.US, "%.2f / %.2f MB", total / 1048576.0, fileLength / 1048576.0)
                        }
                    }
                    output.write(data, 0, count)
                }
                output.flush()
                output.close()
                input.close()
                withContext(Dispatchers.Main) {
                    progressTitle?.text = getString(R.string.download_complete)
                    downloadProgressBar.isVisible = false
                    downloadProgressText.isVisible = false
                    manualInstallText.isVisible = true
                    installNowButton.isVisible = true
                    cancelDownloadButton.text = getString(R.string.close)
                    installNowButton.setOnClickListener { installApk(file) }
                    cancelDownloadButton.setOnClickListener {
                        binding.progressOverlay.isVisible = false
                        clearMenuAnimations()
                        invalidateOptionsMenu()
                    }
                    installApk(file)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressOverlay.isVisible = false
                    clearMenuAnimations()
                    invalidateOptionsMenu()
                    showToast("Download error: ${e.message}")
                }
            }
        }
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun showUserMenuSheet() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_user_menu, binding.root, false)
        val menuUserAvatar = sheetView.findViewById<CircleImageView>(R.id.menuUserAvatar)
        val menuUsername = sheetView.findViewById<TextView>(R.id.menuUsername)
        val menuUserBio = sheetView.findViewById<TextView>(R.id.menuUserBio)
        menuUsername.text = username
        grpcClient.getUserProfile(username) { profile ->
            runOnUiThread {
                if (profile != null && profile.bio.isNotEmpty()) {
                    menuUserBio.isVisible = true
                    menuUserBio.text = profile.bio
                } else {
                    menuUserBio.isVisible = false
                }
            }
        }
        val avatarCache = grpcClient.getAvatarCache()
        val myAvatarUrl = avatarCache[username]
        if (!myAvatarUrl.isNullOrEmpty()) {
            Glide.with(this).load(myAvatarUrl).placeholder(R.drawable.ic_default_avatar).circleCrop().into(menuUserAvatar)
            menuUserAvatar.setOnClickListener {
                bottomSheetDialog.dismiss()
                val intent = Intent(this, FullScreenImageActivity::class.java).apply { putExtra("image_url", myAvatarUrl) }
                startActivity(intent)
            }
        }
        sheetView.findViewById<View>(R.id.actionShareHeader).setOnClickListener {
            bottomSheetDialog.dismiss()
            shareApp()
        }
        sheetView.findViewById<View>(R.id.actionEditProfile).setOnClickListener {
            bottomSheetDialog.dismiss()
            val intent = Intent(this, EditProfileActivity::class.java).apply {
                putExtra("username", username)
                putExtra("password", password)
            }
            editProfileLauncher.launch(intent)
        }
        sheetView.findViewById<View>(R.id.actionThemes).setOnClickListener {
            bottomSheetDialog.dismiss()
            val intent = Intent(this, ThemesActivity::class.java).apply { putExtra("username", username) }
            startActivity(intent)
        }
        sheetView.findViewById<View>(R.id.actionNotifications).setOnClickListener {
            bottomSheetDialog.dismiss()
            startActivity(Intent(this, NotificationActivity::class.java))
        }
        sheetView.findViewById<View>(R.id.actionContacts).setOnClickListener {
            bottomSheetDialog.dismiss()
            val intent = Intent(this, ContactsActivity::class.java).apply {
                putExtra("username", username)
                putExtra("password", password)
            }
            startActivity(intent)
        }
        sheetView.findViewById<View>(R.id.actionToggleLanguage).setOnClickListener {
            bottomSheetDialog.dismiss()
            toggleLanguage()
        }
        sheetView.findViewById<View>(R.id.actionLogout).setOnClickListener {
            bottomSheetDialog.dismiss()
            logout()
        }
        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
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
        binding.toolbarTitle.text = getString(R.string.loading)
        grpcClient.getContacts(username) { contacts ->
            runOnUiThread {
                binding.toolbarTitle.text = getString(R.string.chats)
                val dialogView = layoutInflater.inflate(R.layout.dialog_create_direct_chat, null)
                val searchEditText = dialogView.findViewById<EditText>(R.id.searchEditText)
                val usersRecyclerView = dialogView.findViewById<RecyclerView>(R.id.usersRecyclerView)
                val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
                val btnStartChat = dialogView.findViewById<MaterialButton>(R.id.btnStartChat)
                val filteredUsers = contacts.filter { it != username }.sortedWith(compareByDescending<String> { grpcClient.users.value.contains(it) }.thenBy { it })
                var selectedUser: String? = null
                val userAdapter = UserAdapter(
                    onUserClick = { user ->
                        selectedUser = user
                        btnStartChat.isEnabled = true
                    },
                    avatarCache = grpcClient.getAvatarCache(),
                    onlineUsers = grpcClient.users.value
                )
                usersRecyclerView.adapter = userAdapter
                userAdapter.setUsers(filteredUsers)
                val dialog = AlertDialog.Builder(this).setView(dialogView).create()
                searchEditText.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val query = s.toString().lowercase()
                        userAdapter.setUsers(filteredUsers.filter { it.lowercase().contains(query) })
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
                btnCancel.setOnClickListener { dialog.dismiss() }
                btnStartChat.setOnClickListener {
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
        binding.toolbarTitle.text = getString(R.string.loading)
        val resetButtons = {
            runOnUiThread {
                binding.toolbarTitle.text = getString(R.string.chats)
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
                    resetButtons()
                    showAddContactDialog()
                }
                return@getContacts
            }
            grpcClient.loadUsers()
            lifecycleScope.launch {
                delay(300)
                val onlineUsers = grpcClient.users.value
                runOnUiThread {
                    binding.progressOverlay.isVisible = false
                    resetButtons()
                    val dialogView = layoutInflater.inflate(R.layout.dialog_create_group, null)
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
                    if (isDarkTheme()) {
                        val surfaceValue = TypedValue()
                        theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, surfaceValue, true)
                        groupInputLayout.boxBackgroundColor = surfaceValue.data
                        val primaryValue = TypedValue()
                        theme.resolveAttribute(android.R.attr.colorPrimary, primaryValue, true)
                        val strokeColor = ColorStateList.valueOf(primaryValue.data)
                        btnCancel.strokeColor = strokeColor
                        btnCancel.strokeWidth = 2
                        btnCreate.strokeColor = strokeColor
                        btnCreate.strokeWidth = 2
                    }
                    val selectedUsers = mutableSetOf<String>()
                    val sortedUsers = contacts.sortedWith(compareByDescending<String> { onlineUsers.contains(it) }.thenBy { it })
                    for (user in sortedUsers) {
                        val userView = layoutInflater.inflate(R.layout.item_user_selectable, usersContainer, false)
                        val statusIndicator = userView.findViewById<View>(R.id.statusIndicator)
                        val usernameText = userView.findViewById<TextView>(R.id.usernameText)
                        val userAvatar = userView.findViewById<CircleImageView>(R.id.userAvatar)
                        val checkBox = userView.findViewById<CheckBox>(R.id.userCheckBox)
                        val isOnline = onlineUsers.contains(user)
                        statusIndicator.backgroundTintList = ColorStateList.valueOf(if (isOnline) getColor(android.R.color.holo_green_dark) else getColor(android.R.color.darker_gray))
                        usernameText.text = user
                        val avatarCache = grpcClient.getAvatarCache()
                        val cachedAvatarUrl = avatarCache[user]
                        if (!cachedAvatarUrl.isNullOrEmpty()) {
                            Glide.with(this@ChatListActivity).load(cachedAvatarUrl).placeholder(R.drawable.ic_default_avatar).circleCrop().into(userAvatar)
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
                    val dialog = AlertDialog.Builder(this@ChatListActivity).setView(dialogView).setOnDismissListener { resetButtons() }.create()
                    btnCancel.setOnClickListener { dialog.dismiss() }
                    btnCreate.setOnClickListener {
                        val groupName = groupNameInput.text.toString().trim()
                        if (selectedUsers.isEmpty()) {
                            showToast(getString(R.string.select_at_least_one_user))
                            return@setOnClickListener
                        }
                        binding.toolbarTitle.text = getString(R.string.loading)
                        if (selectedUsers.size == 1 && groupName.isEmpty()) {
                            dialog.dismiss()
                            createDirectChat(selectedUsers.first())
                        } else {
                            val finalGroupName = groupName.ifEmpty { getString(R.string.default_group_name) }
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
        binding.toolbarTitle.text = getString(R.string.loading)
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_contact, null)
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
        val userAdapter = UserAdapter(
            onUserClick = { selected -> btnAdd.isEnabled = selected != username },
            avatarCache = grpcClient.getAvatarCache(),
            onlineUsers = grpcClient.users.value
        )
        usersRecyclerView.adapter = userAdapter
        usersRecyclerView.layoutManager = LinearLayoutManager(this)
        grpcClient.loadAllUsers()
        lifecycleScope.launch {
            delay(500)
            allUsers.clear()
            allUsers.addAll(grpcClient.allUsers.value.filter { it != username })
            runOnUiThread {
                binding.toolbarTitle.text = getString(R.string.chats)
                userAdapter.setUsers(allUsers)
            }
        }
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                userAdapter.setUsers(allUsers.filter { it.lowercase().contains(query) })
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setOnDismissListener {
                binding.toolbarTitle.text = getString(R.string.chats)
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
            binding.toolbarTitle.text = getString(R.string.loading)
            grpcClient.addContact(username, selected) { success, message ->
                runOnUiThread {
                    binding.toolbarTitle.text = getString(R.string.chats)
                    if (success) {
                        showToast(getString(R.string.contact_added))
                        if (createChatCheckbox.isChecked) createDirectChat(selected)
                        dialog.dismiss()
                    } else showToast(message)
                }
            }
        }
        dialog.show()
    }

    private fun createDirectChat(targetUser: String) {
        if (targetUser == username) {
            showToast(getString(R.string.cannot_chat_with_yourself))
            return
        }
        binding.toolbarTitle.text = getString(R.string.loading)
        lifecycleScope.launch {
            grpcClient.createDirectChat(username, targetUser) { chatId ->
                runOnUiThread { binding.toolbarTitle.text = getString(R.string.chats) }
                if (chatId != null) {
                    runOnUiThread {
                        openChat(chatId, getString(R.string.private_chat_with, targetUser), true, "[\"$username\", \"$targetUser\"]", username)
                        showToast(getString(R.string.chat_created_with, targetUser))
                    }
                } else runOnUiThread { showToast(getString(R.string.failed_to_create_chat)) }
            }
        }
    }

    private fun createGroupChat(name: String, participants: List<String>) {
        binding.toolbarTitle.text = getString(R.string.loading)
        lifecycleScope.launch {
            grpcClient.createGroupChat(name, participants, username) { chatId ->
                runOnUiThread { binding.toolbarTitle.text = getString(R.string.chats) }
                if (chatId != null) {
                    runOnUiThread {
                        openChat(chatId, name, false, JSONArray(participants).toString(), username)
                        showToast(getString(R.string.group_created, name))
                    }
                } else runOnUiThread { showToast(getString(R.string.failed_to_create_chat)) }
            }
        }
    }

    private fun applySavedColorScheme() {
        val themeRes = if (getSavedColorScheme() == "light") R.style.Theme_Lavender_Light_NoActionBar else R.style.Theme_Lavender_Dark_NoActionBar
        setTheme(themeRes)
    }

    private fun applySavedLanguage() {
        val lang = getSavedLanguage() ?: "en"
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun getSavedColorScheme(): String? = getSharedPreferences("ChatPrefs", MODE_PRIVATE).getString("color_scheme", null)
    private fun getSavedLanguage(): String? = getSharedPreferences("ChatPrefs", MODE_PRIVATE).getString("language", null)
    private fun saveLanguage(languageCode: String) { getSharedPreferences("ChatPrefs", MODE_PRIVATE).edit { putString("language", languageCode) } }

    private fun toggleLanguage() {
        val next = if (getSavedLanguage() == "en") "ru" else "en"
        saveLanguage(next)
        setLocale(next)
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

    private fun logout() {
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

    private fun clearMenuAnimations() {
        // Optional: clear any pending menu animations if they exist
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
