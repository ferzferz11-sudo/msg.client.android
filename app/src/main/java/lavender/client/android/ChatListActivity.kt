package lavender.client.android

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.appcompat.widget.Toolbar
import android.view.MenuItem
import android.view.Menu
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.json.JSONArray
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.ui.adapter.ChatAdapter
import com.bumptech.glide.Glide
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class ChatListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private val grpcClient = GrpcClient()
    private var username: String = ""
    private var password: String = ""
    private var colorSchemeMenuItem: MenuItem? = null
    private var selectedAvatarUri: Uri? = null
    private var currentAvatarImageView: CircleImageView? = null
    private var currentAvatarProgressBar: android.widget.ProgressBar? = null
    private companion object {
        private const val PICK_IMAGE_REQUEST = 1001
    }

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        val toast = Toast.makeText(this, message, duration)
        toast.setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 100)
        toast.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
            // Возвращаемся на главную (выход)
            finish()
        }

        val usersButton = findViewById<android.widget.ImageButton>(R.id.usersButton)
        usersButton.setOnClickListener {
            showUsersDialog()
        }

        val profileButton = findViewById<android.widget.ImageButton>(R.id.profileButton)
        profileButton.setOnClickListener {
            showProfileDialog()
        }

        recyclerView = findViewById(R.id.chatsRecyclerView)
        adapter = ChatAdapter(
            onChatClick = { chat ->
                openChat(chat.id)
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
        startPollingChats()
    }

    private fun startPollingChats() {
        lifecycleScope.launch {
            while (isActive) {
                delay(3000) // Poll every 3 seconds for faster updates
                grpcClient.getChats(username) { chats ->
                    if (chats.isNotEmpty()) {
                        // Load avatars for new participants first
                        val allParticipants = mutableSetOf<String>()
                        for (chat in chats) {
                            if (chat.participants.isNotEmpty()) {
                                try {
                                    val participants = JSONArray(chat.participants)
                                    for (i in 0 until participants.length()) {
                                        allParticipants.add(participants.getString(i))
                                    }
                                } catch (e: Exception) {
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
                            kotlinx.coroutines.delay(2000)
                            runOnUiThread {
                                if (loadedCount.get() < totalParticipants) {
                                    adapter.setChats(chats)
                                    adapter.updateAvatarCache(grpcClient.getAvatarCache())
                                }
                            }
                        }

                        // Fallback: show chats immediately if no participants
                        if (allParticipants.isEmpty()) {
                            runOnUiThread {
                                adapter.setChats(chats)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh chats immediately when returning from chat
        grpcClient.getChats(username) { chats ->
            if (chats.isNotEmpty()) {
                // Load avatars for all participants first
                val allParticipants = mutableSetOf<String>()
                for (chat in chats) {
                    if (chat.participants.isNotEmpty()) {
                        try {
                            val participants = JSONArray(chat.participants)
                            for (i in 0 until participants.length()) {
                                allParticipants.add(participants.getString(i))
                            }
                        } catch (e: Exception) {
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
                    kotlinx.coroutines.delay(2000)
                    runOnUiThread {
                        if (loadedCount.get() < totalParticipants) {
                            adapter.setChats(chats)
                            adapter.updateAvatarCache(grpcClient.getAvatarCache())
                        }
                    }
                }

                // Fallback: show chats immediately if no participants
                if (allParticipants.isEmpty()) {
                    runOnUiThread {
                        adapter.setChats(chats)
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
            kotlinx.coroutines.delay(500)

            // Check auth result
            try {
                kotlinx.coroutines.coroutineScope {
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
            } catch (e: Exception) {
                // No auth notification received, might be already authenticated
            }

            grpcClient.getChats(username) { chats ->
                if (chats.isEmpty()) {
                    runOnUiThread {
                        // Если нет чатов, открываем general чат
                        openChat("general")
                    }
                } else {
                    // Load avatars for all chat participants first
                    val allParticipants = mutableSetOf<String>()
                    for (chat in chats) {
                        if (chat.participants.isNotEmpty()) {
                            try {
                                val participants = JSONArray(chat.participants)
                                for (i in 0 until participants.length()) {
                                    allParticipants.add(participants.getString(i))
                                }
                            } catch (e: Exception) {
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
                                    adapter.setChats(chats)
                                    adapter.updateAvatarCache(grpcClient.getAvatarCache())
                                }
                            }
                        }
                    }

                    // Fallback: show chats after 5 seconds if not all avatars loaded
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(5000)
                        runOnUiThread {
                            if (loadedCount.get() < totalParticipants) {
                                adapter.setChats(chats)
                                adapter.updateAvatarCache(grpcClient.getAvatarCache())
                            }
                        }
                    }

                    // Fallback: show chats immediately if no participants
                    if (allParticipants.isEmpty()) {
                        runOnUiThread {
                            adapter.setChats(chats)
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

            // Load avatars for all users (for users dialog)
            grpcClient.loadAllUsers()
            lifecycleScope.launch {
                kotlinx.coroutines.delay(2000) // Wait for users to load
                var allUsers = grpcClient.allUsers.value

                // If users are still not loaded, wait more
                var attempts = 0
                while (allUsers.isEmpty() && attempts < 5) {
                    kotlinx.coroutines.delay(500)
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
    }

    private fun openChat(chatId: String) {
        lifecycleScope.launch {
            val intent = Intent(this@ChatListActivity, ChatActivity::class.java)
                .putExtra("username", username)
                .putExtra("password", password)
                .putExtra("roomId", chatId)
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        grpcClient.disconnect()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedAvatarUri = data.data
            // Upload avatar to server
            uploadAvatarToServer(selectedAvatarUri!!)
        }
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
                // Возвращаемся на главную (выход)
                finish()
                true
            }
            R.id.action_color_scheme -> {
                toggleColorScheme()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chat_list_menu, menu)

        // Save reference to color scheme menu item
        colorSchemeMenuItem = menu.findItem(R.id.action_color_scheme)
        updateColorSchemeIcon()

        // Update language indicator text and add click handler
        val languageItem = menu.findItem(R.id.action_language)
        val languageView = languageItem.actionView
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

    private fun showUsersDialog() {
        // Load all users and online users
        grpcClient.loadAllUsers()
        grpcClient.loadUsers()

        lifecycleScope.launch {
            // Wait a bit for users to load
            kotlinx.coroutines.delay(500)

            val allUsers = grpcClient.allUsers.value
            val onlineUsers = grpcClient.users.value

            if (allUsers.isEmpty()) {
                runOnUiThread {
                    showToast(getString(R.string.no_users_available))
                }
                return@launch
            }

            runOnUiThread {
                val container = android.widget.LinearLayout(this@ChatListActivity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(16, 16, 16, 16)
                }

                // Sort users: online first, then offline
                val sortedUsers = allUsers.sortedWith(compareByDescending<String> { onlineUsers.contains(it) }.thenBy { it })

                for (user in sortedUsers) {
                    val userView = layoutInflater.inflate(R.layout.item_user, null)
                    val statusIndicator = userView.findViewById<View>(R.id.statusIndicator)
                    val usernameText = userView.findViewById<TextView>(R.id.usernameText)
                    val userAvatar = userView.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.userAvatar)

                    val isOnline = onlineUsers.contains(user)
                    statusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        if (isOnline) getColor(android.R.color.holo_green_dark)
                        else getColor(android.R.color.darker_gray)
                    )

                    usernameText.text = user
                    container.addView(userView)

                    // Check cache first
                    val avatarCache = grpcClient.getAvatarCache()
                    val cachedAvatarUrl = avatarCache[user]

                    when {
                        !cachedAvatarUrl.isNullOrEmpty() -> {
                            // Load from cache
                            Glide.with(this@ChatListActivity)
                                .load(cachedAvatarUrl)
                                .placeholder(R.drawable.ic_default_avatar)
                                .error(R.drawable.ic_default_avatar)
                                .circleCrop()
                                .into(userAvatar)
                        }
                        cachedAvatarUrl == "" -> {
                            // Cache has empty string (already tried loading), set default avatar
                            userAvatar.setImageResource(R.drawable.ic_default_avatar)
                        }
                        else -> {
                            // Load from server
                            grpcClient.getUserAvatar(user) { avatarUrl ->
                                runOnUiThread {
                                    if (avatarUrl.isNotEmpty()) {
                                        Glide.with(this@ChatListActivity)
                                            .load(avatarUrl)
                                            .placeholder(R.drawable.ic_default_avatar)
                                            .error(R.drawable.ic_default_avatar)
                                            .circleCrop()
                                            .into(userAvatar)
                                        adapter.updateAvatarCache(grpcClient.getAvatarCache())
                                    } else {
                                        // Set default avatar if server returns empty
                                        userAvatar.setImageResource(R.drawable.ic_default_avatar)
                                        // Save empty string in cache to avoid retrying
                                        grpcClient.updateAvatarCache(user, "")
                                        adapter.updateAvatarCache(grpcClient.getAvatarCache())
                                    }
                                }
                            }
                        }
                    }
                }

                val dialog = android.app.AlertDialog.Builder(this@ChatListActivity)
                    .setTitle(getString(R.string.select_user))
                    .setView(container)
                    .setPositiveButton(android.R.string.cancel, null)
                    .show()

                // Set click listeners after dialog is created
                for (i in 0 until container.childCount) {
                    val userView = container.getChildAt(i)
                    val usernameText = userView.findViewById<TextView>(R.id.usernameText)
                    val user = usernameText.text.toString()

                    userView.setOnClickListener {
                        if (user != username) {
                            createDirectChat(user)
                        }
                        dialog.dismiss()
                    }
                }
            }
        }
    }

    private fun createDirectChat(targetUser: String) {
        if (targetUser == username) {
            showToast(getString(R.string.cannot_chat_with_yourself))
            return
        }

        lifecycleScope.launch {
            grpcClient.createDirectChat(username, targetUser) { chatId ->
                if (chatId != null) {
                    runOnUiThread {
                        openChat(chatId)
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

    private fun showProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_profile, null)

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
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
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
        val scheme = getSavedColorScheme() ?: "light"
        when (scheme) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
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
        val currentScheme = getSavedColorScheme() ?: "light"
        val currentIndex = schemes.indexOf(currentScheme)
        val nextIndex = (currentIndex + 1) % schemes.size
        val newScheme = schemes[nextIndex]

        saveColorScheme(newScheme)
        recreate()
    }

    private fun updateColorSchemeIcon() {
        val currentScheme = getSavedColorScheme() ?: "light"
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
            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
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

    private fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
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
}
