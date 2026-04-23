package lavender.client.android

import lavender.client.android.data.fcm.NotificationHistory

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.appcompat.widget.Toolbar
import android.view.MenuItem
import android.view.Menu
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONArray
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.ui.adapter.ChatAdapter
import com.bumptech.glide.Glide
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class ChatListActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private val grpcClient = GrpcClient
    private var username: String = ""
    private var password: String = ""
    private var colorSchemeMenuItem: MenuItem? = null
    private var selectedAvatarUri: Uri? = null
    private var currentAvatarImageView: CircleImageView? = null
    private var currentAvatarProgressBar: android.widget.ProgressBar? = null
    private var currentTheme: String? = null
    private var currentChats: List<lavender.client.android.data.models.ChatInfo> = emptyList()
    private companion object

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        val toast = Toast.makeText(this, message, duration)
        toast.setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 100)
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
                        currentAvatarImageView?.let {
                            Glide.with(this@ChatListActivity)
                                .load(avatarUrl)
                                .placeholder(R.drawable.ic_default_avatar)
                                .error(R.drawable.ic_default_avatar)
                                .into(it)
                        }
                        // Update adapter avatar cache
                        adapter.updateAvatarCache(grpcClient.getAvatarCache())
                    }
                }
            }
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedAvatarUri = uri
                uploadAvatarToServer(uri)
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

        setContentView(R.layout.activity_chat_list)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = ""
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_back_arrow)
        }

        toolbar.setNavigationOnClickListener {
            if (adapter.getSelectedChats().isNotEmpty()) {
                adapter.clearSelection()
            } else {
                // Возвращаемся на главную (выход)
                finish()
            }
        }

        // Handle system back button
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (adapter.getSelectedChats().isNotEmpty()) {
                    adapter.clearSelection()
                } else {
                    finish()
                }
            }
        })

        val profileButton = findViewById<android.widget.ImageButton>(R.id.profileButton)
        profileButton.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
                .putExtra("username", username)
                .putExtra("password", password)
            editProfileLauncher.launch(intent)
        }

        val addChatFab = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.addChatFab)
        addChatFab.setOnClickListener {
            showCreateChatDialog()
        }

        recyclerView = findViewById(R.id.chatsRecyclerView)
        adapter = ChatAdapter(
            onChatClick = { chat ->
                openChat(chat.id, chat.name)
            },
            onSelectionChanged = { count ->
                invalidateOptionsMenu()
                val toolbarTitle = findViewById<TextView>(R.id.toolbarTitle)
                if (count > 0) {
                    toolbarTitle.text = getString(R.string.selected_count, count)
                } else {
                    toolbarTitle.text = getString(R.string.chats)
                }
            },
            currentUsername = username,
            initialAvatarCache = grpcClient.getAvatarCache()
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Observe system notifications for auth failures
        lifecycleScope.launch {
            grpcClient.systemNotification.collect { notification ->
                notification?.let {
                    when (it) {
                        "auth_failed" -> {
                            runOnUiThread {
                                showToast(getString(R.string.auth_failed), Toast.LENGTH_LONG)
                                grpcClient.disconnect()
                                finish()
                            }
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
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                android.util.Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            android.util.Log.d("FCM", "FCM Token: $token")

            // Register token on server - always register to ensure token is up to date
            // Delay slightly to ensure username is loaded
            lifecycleScope.launch {
                delay(1000)
                if (username.isNotEmpty()) {
                    grpcClient.registerToken(username, token)
                    android.util.Log.d("FCM", "Token registered for user: $username")
                } else {
                    android.util.Log.w("FCM", "Username not available for token registration")
                }
            }
        }

        // Request POST_NOTIFICATIONS permission for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
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
                        // Check if chats have actually changed
                        val chatsChanged = currentChats.size != chats.size ||
                                currentChats.zip(chats).any { (old, new) ->
                                    old.id != new.id ||
                                    old.name != new.name ||
                                    old.type != new.type ||
                                    old.unreadCount != new.unreadCount
                                }

                        if (!chatsChanged) {
                            return@getChats
                        }

                        currentChats = chats

                        // Load avatars for new participants first
                        val allParticipants = mutableSetOf<String>()
                        for (chat in chats) {
                            if (chat.participants.isNotEmpty()) {
                                try {
                                    val participants = JSONArray(chat.participants)
                                    for (i in 0 until participants.length()) {
                                        allParticipants.add(participants.getString(i))
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
                                        adapter.setChats(chats)
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
                                    if (currentChats != chats) {
                                        adapter.setChats(chats)
                                    }
                                    adapter.updateAvatarCache(grpcClient.getAvatarCache())
                                }
                            }
                        }

                        // Fallback: show chats immediately if no participants
                        if (allParticipants.isEmpty()) {
                            runOnUiThread {
                                if (currentChats != chats) {
                                    adapter.setChats(chats)
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
                // Check if chats have actually changed
                val chatsChanged = currentChats.size != chats.size ||
                        currentChats.zip(chats).any { (old, new) ->
                            old.id != new.id ||
                            old.name != new.name ||
                            old.type != new.type ||
                            old.unreadCount != new.unreadCount
                        }

                if (!chatsChanged) {
                    return@getChats
                }

                currentChats = chats
                // Load avatars for all participants first
                val allParticipants = mutableSetOf<String>()
                for (chat in chats) {
                    if (chat.participants.isNotEmpty()) {
                        try {
                            val participants = JSONArray(chat.participants)
                            for (i in 0 until participants.length()) {
                                allParticipants.add(participants.getString(i))
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
                                adapter.setChats(chats)
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
                            if (currentChats != chats) {
                                adapter.setChats(chats)
                            }
                            adapter.updateAvatarCache(grpcClient.getAvatarCache())
                        }
                    }
                }

                // Fallback: show chats immediately if no participants
                if (allParticipants.isEmpty()) {
                    runOnUiThread {
                        if (currentChats != chats) {
                            adapter.setChats(chats)
                        }
                    }
                }
            }
        }
    }

    private fun loadChats() {
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
                    currentChats = chats
                    // Load avatars for all chat participants first
                    val allParticipants = mutableSetOf<String>()
                    for (chat in chats) {
                        if (chat.participants.isNotEmpty()) {
                            try {
                                val participants = JSONArray(chat.participants)
                                for (i in 0 until participants.length()) {
                                    allParticipants.add(participants.getString(i))
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
                                    // Only update if chats changed
                                    if (currentChats != chats) {
                                        adapter.setChats(chats)
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
                                if (currentChats != chats) {
                                    adapter.setChats(chats)
                                }
                                adapter.updateAvatarCache(grpcClient.getAvatarCache())
                            }
                        }
                    }

                    // Fallback: show chats immediately if no participants
                    if (allParticipants.isEmpty()) {
                        runOnUiThread {
                            if (currentChats != chats) {
                                adapter.setChats(chats)
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

    private fun uploadAvatarToServer(uri: Uri) {
        // Show progress bar
        currentAvatarProgressBar?.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Check file type
                    val mimeType = contentResolver.getType(uri)
                    val isGif = mimeType == "image/gif"

                    val bytes: ByteArray
                    val mediaType: String

                    if (isGif) {
                        // Send GIF as-is to preserve animation
                        val inputStream = contentResolver.openInputStream(uri)
                        bytes = inputStream?.readBytes() ?: byteArrayOf()
                        inputStream?.close()
                        mediaType = "image/gif"
                    } else {
                        // Resize other images
                        val resizedBytes = resizeImage(uri, 256, 256) // Max 256x256 for avatars

                        if (resizedBytes == null) {
                            runOnUiThread {
                                currentAvatarProgressBar?.visibility = View.GONE
                                showToast("Failed to resize image")
                            }
                            return@withContext
                        }

                        bytes = resizedBytes
                        mediaType = "image/jpeg"
                    }

                    if (bytes.isEmpty()) {
                        runOnUiThread {
                            currentAvatarProgressBar?.visibility = View.GONE
                            showToast("Failed to read image")
                        }
                        return@withContext
                    }

                    // Upload to HTTP server with multipart/form-data
                    val requestBody = okhttp3.MultipartBody.Builder()
                        .setType(okhttp3.MultipartBody.FORM)
                        .addFormDataPart("avatar", if (isGif) "avatar.gif" else "avatar.jpg", bytes.toRequestBody(mediaType.toMediaTypeOrNull()))
                        .build()

                    val request = okhttp3.Request.Builder()
                        .url("http://159.195.38.145:8082/upload-avatar")
                        .post(requestBody)
                        .build()

                    val client = okhttp3.OkHttpClient()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val url = extractUrlFromResponse(responseBody ?: "")

                        if (url.isNotEmpty()) {
                            // Update avatar via gRPC
                            grpcClient.updateAvatar(username, url) { success, message ->
                                runOnUiThread {
                                    currentAvatarProgressBar?.visibility = View.GONE
                                    if (success) {
                                        showToast("Avatar updated successfully")
                                        // Update avatarImageView if dialog is still open
                                        currentAvatarImageView?.let {
                                            com.bumptech.glide.Glide.with(this@ChatListActivity)
                                                .load(url)
                                                .placeholder(R.drawable.ic_default_avatar)
                                                .error(R.drawable.ic_default_avatar)
                                                .into(it)
                                        }
                                        // Update adapter avatar cache
                                        adapter.updateAvatarCache(grpcClient.getAvatarCache())
                                    } else {
                                        showToast("Failed to update avatar: $message")
                                    }
                                }
                            }
                        } else {
                            runOnUiThread {
                                showToast("Failed to extract URL from response")
                                currentAvatarProgressBar?.visibility = View.GONE
                            }
                        }
                    } else {
                        runOnUiThread {
                            showToast("Failed to upload avatar (HTTP ${response.code})")
                            currentAvatarProgressBar?.visibility = View.GONE
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showToast("Error: ${e.message}")
                    currentAvatarProgressBar?.visibility = View.GONE
                }
            }
        }
    }

    private fun extractUrlFromResponse(response: String): String {
        val regex = """"url":\s*"([^"]+)"""".toRegex()
        val match = regex.find(response)
        return match?.groupValues?.get(1) ?: ""
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (adapter.getSelectedChats().isNotEmpty()) {
                    adapter.clearSelection()
                } else {
                    // Возвращаемся на главную (выход)
                    finish()
                }
                true
            }
            R.id.action_delete -> {
                val selected = adapter.getSelectedChats()
                if (selected.isNotEmpty()) {
                    val dialogView = layoutInflater.inflate(R.layout.dialog_delete_chats, null)

                    // Set dialog background using Material Design colors
                    val typedValue = android.util.TypedValue()
                    if (isDarkTheme()) {
                        theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
                        dialogView.setBackgroundColor(typedValue.data)
                    }

                    val titleText = dialogView.findViewById<TextView>(R.id.titleText)
                    val messageText = dialogView.findViewById<TextView>(R.id.messageText)
                    val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
                    val btnDelete = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDelete)

                    titleText.text = getString(R.string.delete_chats)
                    messageText.text = getString(R.string.delete_chats_confirmation, selected.size)

                    // Set button strokes in dark theme
                    if (isDarkTheme()) {
                        val primaryValue = android.util.TypedValue()
                        theme.resolveAttribute(android.R.attr.colorPrimary, primaryValue, true)
                        val strokeColor = android.content.res.ColorStateList.valueOf(primaryValue.data)
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

                        lifecycleScope.launch {
                            var successCount = 0
                            var lastErrorMessage = ""
                            for (chat in chatsToDelete) {
                                val result: Pair<Boolean, String> = withContext(Dispatchers.IO) {
                                    val deferred = CompletableDeferred<Pair<Boolean, String>>()
                                    grpcClient.deleteChat(chat.id) { success, message ->
                                        deferred.complete(success to message)
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
            R.id.action_color_scheme -> {
                toggleColorScheme()
                true
            }
            R.id.action_notification_history -> {
                showNotificationHistory()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chat_list_menu, menu)

        val hasSelection = adapter.getSelectedChats().isNotEmpty()
        menu.findItem(R.id.action_delete)?.apply { isVisible = hasSelection }
        menu.findItem(R.id.action_language)?.apply { isVisible = !hasSelection }
        menu.findItem(R.id.action_color_scheme)?.apply { isVisible = !hasSelection }

        findViewById<View>(R.id.profileButton)?.visibility = if (hasSelection) View.GONE else View.VISIBLE

        // Save reference to color scheme menu item
        colorSchemeMenuItem = menu.findItem(R.id.action_color_scheme)
        updateColorSchemeIcon()

        // Update language indicator text and add click handler
        val languageItem = menu.findItem(R.id.action_language)
        val languageView = languageItem?.actionView
        val languageText = languageView?.findViewById<TextView>(R.id.languageText)
        val currentLang = getSavedLanguage() ?: "en"
        languageText?.text = if (currentLang == "en") "EN" else "RU"

        languageView?.setOnClickListener {
            toggleLanguage()
            // Update text after toggle
            val newLang = getSavedLanguage() ?: "en"
            languageText?.text = if (newLang == "en") "EN" else "RU"
        }

        return true
    }

    private fun showCreateChatDialog() {
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
                    showToast(getString(R.string.no_users_available))
                }
                return@launch
            }

            runOnUiThread {
                val dialogView = layoutInflater.inflate(R.layout.dialog_create_group, null)

                // Set dialog background using Material Design colors
                val typedValue = android.util.TypedValue()
                if (isDarkTheme()) {
                    theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
                    dialogView.setBackgroundColor(typedValue.data)
                }

                val groupNameInput = dialogView.findViewById<EditText>(R.id.groupNameInput)
                val usersContainer = dialogView.findViewById<android.widget.LinearLayout>(R.id.usersContainer)
                val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
                val btnCreate = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCreate)
                val groupInputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.groupInputLayout)

                // Set TextInputLayout background and button strokes in dark theme
                if (isDarkTheme()) {
                    val surfaceValue = android.util.TypedValue()
                    theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, surfaceValue, true)
                    groupInputLayout.boxBackgroundColor = surfaceValue.data

                    val primaryValue = android.util.TypedValue()
                    theme.resolveAttribute(android.R.attr.colorPrimary, primaryValue, true)
                    val strokeColor = android.content.res.ColorStateList.valueOf(primaryValue.data)
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
                    val checkBox = userView.findViewById<android.widget.CheckBox>(R.id.userCheckBox)

                    val isOnline = onlineUsers.contains(user)
                    statusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        if (isOnline) getColor(android.R.color.holo_green_dark)
                        else getColor(android.R.color.darker_gray)
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
            return
        }

        val progressView = layoutInflater.inflate(R.layout.dialog_loading, null)
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(progressView)
            .setCancelable(true)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            grpcClient.createDirectChat(username, targetUser) { chatId ->
                runOnUiThread { progressDialog.dismiss() }
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
        val progressView = layoutInflater.inflate(R.layout.dialog_loading, null)
        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(progressView)
            .setCancelable(true)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            grpcClient.createGroupChat(name, participants) { chatId ->
                runOnUiThread { progressDialog.dismiss() }
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

    private fun showProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_profile, null)

        // Set dialog background using Material Design colors
        val typedValue = android.util.TypedValue()
        if (isDarkTheme()) {
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
            dialogView.setBackgroundColor(typedValue.data)
        }

        val editTextUsername = dialogView.findViewById<EditText>(R.id.editTextUsername)
        val editTextOldPassword = dialogView.findViewById<EditText>(R.id.editTextOldPassword)
        val editTextNewPassword = dialogView.findViewById<EditText>(R.id.editTextNewPassword)
        val btnChangeUsername = dialogView.findViewById<Button>(R.id.btnChangeUsername)
        val btnChangePassword = dialogView.findViewById<Button>(R.id.btnChangePassword)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val avatarImageView = dialogView.findViewById<CircleImageView>(R.id.avatarImageView)
        val btnChangeAvatar = dialogView.findViewById<Button>(R.id.btnChangeAvatar)
        val avatarProgressBar = dialogView.findViewById<android.widget.ProgressBar>(R.id.avatarProgressBar)
        val btnCloseDialog = dialogView.findViewById<ImageButton>(R.id.btnCloseDialog)
        val btnToggleEdit = dialogView.findViewById<Button>(R.id.btnToggleEdit)
        val editFieldsContainer = dialogView.findViewById<View>(R.id.editFieldsContainer)
        val btnDeleteProfile = dialogView.findViewById<Button>(R.id.btnDeleteProfile)

        btnToggleEdit.setOnClickListener {
            if (editFieldsContainer.visibility == View.VISIBLE) {
                editFieldsContainer.visibility = View.GONE
            } else {
                editFieldsContainer.visibility = View.VISIBLE
            }
        }

        btnDeleteProfile.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.delete_profile)
                .setMessage(R.string.delete_profile_confirm)
                .setPositiveButton(R.string.delete_profile) { _, _ ->
                    grpcClient.deleteProfile(username) { success, message ->
                        runOnUiThread {
                            if (success) {
                                showToast(getString(R.string.profile_deleted))
                                grpcClient.disconnect()
                                finish()
                            } else {
                                showToast(getString(R.string.failed_to_delete_profile) + ": " + message, Toast.LENGTH_LONG)
                            }
                        }
                    }
                }
                .setNegativeButton(R.string.cancel_dialog, null)
                .show()
        }

        // Store references
        currentAvatarImageView = avatarImageView
        currentAvatarProgressBar = avatarProgressBar

        // Pre-fill current username
        editTextUsername.setText(username)

        // Load current avatar
        grpcClient.getUserAvatar(username) { avatarUrl ->
            runOnUiThread {
                if (avatarUrl.isNotEmpty()) {
                    com.bumptech.glide.Glide.with(this)
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_default_avatar)
                        .error(R.drawable.ic_default_avatar)
                        .into(avatarImageView)
                    // Update adapter avatar cache
                    adapter.updateAvatarCache(grpcClient.getAvatarCache())
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        btnChangeAvatar.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        btnChangeUsername.setOnClickListener {
            val newUsername = editTextUsername.text.toString().trim()
            if (newUsername.isNotEmpty() && newUsername != username) {
                grpcClient.updateUsername(username, newUsername) { success, message ->
                    runOnUiThread {
                        if (success) {
                            showToast(message)
                            username = newUsername
                            dialog.dismiss()
                        } else {
                            showToast(message, Toast.LENGTH_LONG)
                        }
                    }
                }
            } else {
                showToast(getString(R.string.username_empty))
            }
        }

        btnChangePassword.setOnClickListener {
            val oldPassword = editTextOldPassword.text.toString().trim()
            val newPassword = editTextNewPassword.text.toString().trim()
            if (oldPassword.isNotEmpty() && newPassword.isNotEmpty()) {
                grpcClient.updatePassword(username, oldPassword, newPassword) { success, message ->
                    runOnUiThread {
                        if (success) {
                            showToast(message)
                            password = newPassword
                            editTextOldPassword.text.clear()
                            editTextNewPassword.text.clear()
                        } else {
                            showToast(message, Toast.LENGTH_LONG)
                        }
                    }
                }
            } else {
                showToast("Please enter both old and new password")
            }
        }

        dialog.show()
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
        prefs.edit().putString("color_scheme", scheme).apply()
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
            R.drawable.ic_theme_dark
        } else {
            R.drawable.ic_theme_toggle
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

    private fun resizeImage(uri: Uri, maxWidth: Int, maxHeight: Int): ByteArray? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val options = android.graphics.BitmapFactory.Options()
            options.inJustDecodeBounds = true
            android.graphics.BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, 256, 256)
            options.inJustDecodeBounds = false

            val inputStream2 = contentResolver.openInputStream(uri) ?: return null
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream2, null, options)
            inputStream2.close()

            if (bitmap == null) {
                return null
            }

            // Resize to exact dimensions
            val width = bitmap.width
            val height = bitmap.height
            val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)

            val scaledWidth = (width * scale).toInt()
            val scaledHeight = (height * scale).toInt()

            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
            bitmap.recycle()

            // Compress to JPEG with 85% quality
            val outputStream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
            scaledBitmap.recycle()

            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, @Suppress("UNUSED_PARAMETER") reqWidth: Int, @Suppress("UNUSED_PARAMETER") reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
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
            notificationsText.text = "No notifications received yet"
        } else {
            val message = notifications.joinToString("\n\n") { notif ->
                val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(notif.timestamp))
                "[$time] ${notif.title}\n${notif.body}${if (notif.from != null) "\nFrom: ${notif.from}" else ""}"
            }
            notificationsText.text = message
        }

        // Get and display FCM token
        lifecycleScope.launch {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    fcmTokenText.text = token
                    fcmTokenText.setOnClickListener {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("FCM Token", token)
                        clipboard.setPrimaryClip(clip)
                        showToast("FCM Token copied to clipboard")
                    }
                } else {
                    fcmTokenText.text = "Failed to get FCM token"
                }
            }
        }

        copyTokenButton.setOnClickListener {
            val token = fcmTokenText.text.toString()
            if (token.isNotEmpty() && token != "Failed to get FCM token") {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("FCM Token", token)
                clipboard.setPrimaryClip(clip)
                showToast("FCM Token copied to clipboard")
            }
        }

        testNotificationButton.setOnClickListener {
            // Simulate receiving a notification
            lavender.client.android.data.fcm.NotificationHistory.add("Test Notification", "This is a test notification from the app", "local")
            notificationsText.text = lavender.client.android.data.fcm.NotificationHistory.getAll()
                .joinToString("\n\n") { notif ->
                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date(notif.timestamp))
                    "[$time] ${notif.title}\n${notif.body}${if (notif.from != null) "\nFrom: ${notif.from}" else ""}"
                }
            showToast("Test notification added to history")

            // Create notification channel if needed
            val channelId = "lavender_messaging_channel"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channelName = "Lavender Messages"
                val channelDescription = "Notifications for new messages"
                val importance = android.app.NotificationManager.IMPORTANCE_HIGH
                val channel = android.app.NotificationChannel(channelId, channelName, importance).apply {
                    description = channelDescription
                }
                val notificationManager = getSystemService(android.app.NotificationManager::class.java)
                notificationManager?.createNotificationChannel(channel)
            }

            // Also show a real system notification
            val notificationId = 9999
            val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setContentTitle("Test Notification")
                .setContentText("This is a test notification from the app")
                .setSmallIcon(R.drawable.ic_message_sent)
                .setAutoCancel(true)
                .build()

            val notificationManagerCompat = androidx.core.app.NotificationManagerCompat.from(this)
            notificationManagerCompat.notify(notificationId, notification)
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Notification History (${notifications.size})")
            .setView(dialogView)
            .setPositiveButton("Clear") { _, _ ->
                NotificationHistory.clear()
                showToast("Notification history cleared")
            }
            .setNegativeButton(android.R.string.ok, null)
            .create()

        dialog.show()
    }
}
