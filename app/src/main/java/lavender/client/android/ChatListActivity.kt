package lavender.client.android

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.databinding.ActivityChatListBinding
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.adapter.ChatAdapter
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatListBinding
    private lateinit var chatAdapter: ChatAdapter
    private val grpcClient = GrpcClient
    private lateinit var username: String
    private lateinit var password: String
    private val chats = mutableListOf<ChatInfo>()

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

        applyTheme()

        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        username = intent.getStringExtra("USERNAME") ?: ""
        password = intent.getStringExtra("PASSWORD") ?: ""
        val serverAddress = intent.getStringExtra("SERVER_ADDRESS") ?: ""

        if (serverAddress.isNotEmpty()) {
            val parts = serverAddress.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
            grpcClient.connect(host, false, port, this)
        }

        ThemeUi.bind(this, username)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        Log.d("ChatListActivity", "Logged in as $username")

        chatAdapter = ChatAdapter(
            onChatClick = { chat ->
                if (chat.unreadCount > 0) {
                    grpcClient.markRead(chat.id, username)
                }

                val intent = Intent(this, NewChatActivity::class.java).apply {
                    putExtra("USERNAME", username)
                    putExtra("PASSWORD", password)
                    putExtra("CHAT_NAME", chat.name)
                    putExtra("ROOM_ID", chat.id)
                    putExtra("IS_DIRECT", chat.type == "direct")
                    putExtra("PARTICIPANTS", chat.participants)
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
                binding.actionSearch.isVisible = !hasSelection && !binding.searchCard.isVisible
                binding.toolbarUserAvatar.isVisible = !hasSelection && !binding.searchCard.isVisible
            },
            currentUsername = username,
            initialAvatarCache = grpcClient.getAvatarCache(),
            onlineUsers = grpcClient.users.value
        )
        binding.chatsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatListActivity)
            adapter = chatAdapter
        }

        binding.addChatFab.setOnClickListener {
            showChatActionSheet()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadChats()
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
                if (status == lavender.client.android.data.grpc.ConnectionStatus.READY) {
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        grpcClient.startChat(username, password, "") { /* onMessageReceived */ }
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
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.searchEditText, 0)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)
        
        binding.actionSearch.isVisible = false
        binding.toolbarUserAvatar.isVisible = false
    }

    private fun hideSearchBar() {
        binding.searchCard.isVisible = false
        binding.searchEditText.text?.clear()
        chatAdapter.filter("")
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
        
        val hasSelection = chatAdapter.getSelectedChats().isNotEmpty()
        supportActionBar?.setDisplayHomeAsUpEnabled(hasSelection)
        supportActionBar?.setHomeAsUpIndicator(if (hasSelection) R.drawable.ic_close else 0)
        binding.actionSearch.isVisible = !hasSelection
        binding.toolbarUserAvatar.isVisible = !hasSelection
        binding.toolbarTitle.text = if (hasSelection) getString(R.string.selected_count, chatAdapter.getSelectedChats().size) else getString(R.string.chats)
    }

    private fun confirmDeleteSelectedChats(selected: List<ChatInfo>) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_chats)
            .setMessage(getString(R.string.delete_chats_confirmation, selected.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                selected.forEach { chat ->
                    grpcClient.deleteChat(chat.id) { _, _ -> }
                }
                chatAdapter.clearSelection()
                loadChats()
            }
            .setNegativeButton(R.string.cancel_dialog, null)
            .show()
    }

    private fun toggleMuteSelectedChats(selected: List<ChatInfo>) {
        val anyUnmuted = selected.any { !it.isMuted }
        selected.forEach { chat ->
            grpcClient.setMutedChat(chat.id, anyUnmuted)
        }
        chatAdapter.clearSelection()
        loadChats()
        Toast.makeText(this, if (anyUnmuted) R.string.muted_count else R.string.unmuted_count, Toast.LENGTH_SHORT).show()
    }

    private fun updateToolbarAvatar() {
        val avatarCache = grpcClient.getAvatarCache()
        val myAvatarUrl = avatarCache[username]
        if (!myAvatarUrl.isNullOrEmpty()) {
            com.bumptech.glide.Glide.with(this)
                .load(myAvatarUrl)
                .placeholder(R.drawable.ic_default_avatar)
                .circleCrop()
                .into(binding.toolbarUserAvatar)
        } else {
            val avatarFile = File(filesDir, "avatars/$username.jpg")
            if (avatarFile.exists()) {
                try {
                    val bitmap = BitmapFactory.decodeFile(avatarFile.absolutePath)
                    if (bitmap != null) {
                        binding.toolbarUserAvatar.setImageBitmap(bitmap)
                    } else {
                        binding.toolbarUserAvatar.setImageResource(R.drawable.ic_default_avatar_white)
                    }
                } catch (e: Exception) {
                    Log.e("ChatListActivity", "Error loading avatar for toolbar", e)
                    binding.toolbarUserAvatar.setImageResource(R.drawable.ic_default_avatar_white)
                }
            } else {
                binding.toolbarUserAvatar.setImageResource(R.drawable.ic_default_avatar_white)
            }
        }
    }

    private fun applyTheme() {
        val sharedPrefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun loadChats() {
        Log.d("ChatListActivity", "Loading chats for $username")

        binding.swipeRefreshLayout.isRefreshing = true

        grpcClient.getChats(username) { fetchedChats ->
            runOnUiThread {
                binding.swipeRefreshLayout.isRefreshing = false
                chats.clear()
                chats.addAll(fetchedChats)

                chatAdapter.setChats(chats)

                val totalUnread = chats.sumOf { it.unreadCount }
                updateAppIconBadge(totalUnread)

                Log.d("ChatListActivity", "Loaded ${chats.size} chats")

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
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun updateAppIconBadge(count: Int) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = notificationManager.getNotificationChannel("messages")
                if (channel != null) {
                    channel.setShowBadge(count > 0)
                    notificationManager.createNotificationChannel(channel)
                }
            }
        } catch (e: Exception) {
            Log.e("ChatList", "Error updating badge", e)
        }
    }

    private fun startSync() {
        syncJob?.cancel()
        syncJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(5000) // Poll every 5 seconds

                grpcClient.getChats(username) { fetchedChats ->
                    if (fetchedChats.size != chats.size || fetchedChats.any { fc -> chats.none { it.id == fc.id && it.lastMessageTime == fc.lastMessageTime && it.unreadCount == fc.unreadCount } }) {
                        runOnUiThread {
                            chats.clear()
                            chats.addAll(fetchedChats)
                            chatAdapter.setChats(chats)
                            updateAppIconBadge(chats.sumOf { it.unreadCount })
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

    override fun onResume() {
        super.onResume()
        chatAdapter.updateAvatarCache(grpcClient.getAvatarCache())
        if (grpcClient.connectionStatus.value == lavender.client.android.data.grpc.ConnectionStatus.READY) {
            loadChats()
        }
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Lavender Messenger")
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_app))
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app)))
    }

    private fun logout() {
        val sharedPrefs = getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit {
            remove("saved_username")
            remove("saved_password")
        }

        syncJob?.cancel()
        grpcClient.disconnect()

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showAboutDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_chats, null)
        val customTheme = ThemeStore.currentTheme()
        val titleText = dialogView.findViewById<TextView>(R.id.titleText)
        val messageText = dialogView.findViewById<TextView>(R.id.messageText)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnAction = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDelete)

        try {
            val bgColor = customTheme.backgroundColor.toColorInt()
            val txtColor = customTheme.textPrimaryColor.toColorInt()
            dialogView.setBackgroundColor(bgColor)
            titleText.setTextColor(txtColor)
            messageText.setTextColor(txtColor)
        } catch (_: Exception) {}

        titleText.text = getString(R.string.about)
        messageText.text = getString(R.string.app_description)
        btnAction.visibility = View.GONE
        btnCancel.text = getString(R.string.ok)

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun toggleLanguage() {
        val prefs = getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        val currentLang = prefs.getString("language", "en")
        val newLang = if (currentLang == "en") "ru" else "en"

        prefs.edit().putString("language", newLang).apply()
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
        val prefs = newBase.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "en") ?: "en"
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private fun showSettingsSheet() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_user_menu, binding.root, false)
        val customTheme = ThemeStore.currentTheme()
        val actionIds = listOf(
            R.id.actionShareHeader, R.id.actionEditProfile, R.id.actionThemes,
            R.id.actionNotifications, R.id.actionContacts, R.id.actionToggleLanguage,
            R.id.actionAbout, R.id.actionUpdate, R.id.actionLogout
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
            val avatarFile = File(filesDir, "avatars/$username.jpg")
            if (avatarFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(avatarFile.absolutePath)
                if (bitmap != null) {
                    menuUserAvatar.setImageBitmap(bitmap)
                    menuUserAvatar.clipToOutline = true
                }
            } else {
                menuUserAvatar.setImageResource(R.drawable.ic_default_avatar_white)
                menuUserAvatar.setColorFilter(primColor)
            }

            actionIds.forEach { id ->
                sheetView.findViewById<LinearLayout>(id)?.let { layout ->
                    for (i in 0 until layout.childCount) {
                        val child = layout.getChildAt(i)
                        if (child is TextView) child.setTextColor(txtColor)
                        if (child is ImageView) child.imageTintList = ColorStateList.valueOf(primColor)
                    }
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
        sheetView.findViewById<View>(R.id.actionNotifications).setOnClickListener {
            bottomSheetDialog.dismiss()
            startActivity(Intent(this, NotificationActivity::class.java))
        }
        sheetView.findViewById<View>(R.id.actionContacts).setOnClickListener {
            bottomSheetDialog.dismiss()
            val intent = Intent(this, ContactsActivity::class.java).apply {
                putExtra("USERNAME", username)
                putExtra("PASSWORD", password)
            }
            startActivity(intent)
        }
        sheetView.findViewById<View>(R.id.actionToggleLanguage).setOnClickListener {
            bottomSheetDialog.dismiss()
            toggleLanguage()
        }
        sheetView.findViewById<View>(R.id.actionUpdate).setOnClickListener {
            bottomSheetDialog.dismiss()
            Toast.makeText(this, getString(R.string.checking_for_updates), Toast.LENGTH_SHORT).show()
        }
        sheetView.findViewById<View>(R.id.actionAbout).setOnClickListener {
            bottomSheetDialog.dismiss()
            showAboutDialog()
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
        val customTheme = ThemeStore.currentTheme()
        val actionIds = listOf(R.id.actionAddContact)
        try {
            val bgColor = customTheme.backgroundColor.toColorInt()
            val txtColor = customTheme.textPrimaryColor.toColorInt()
            val primColor = customTheme.primaryColor.toColorInt()
            sheetView.setBackgroundColor(bgColor)
            sheetView.findViewById<View>(R.id.dragHandle)?.backgroundTintList = ColorStateList.valueOf(primColor)
            actionIds.forEach { id ->
                sheetView.findViewById<LinearLayout>(id)?.let { layout ->
                    for (i in 0 until layout.childCount) {
                        val child = layout.getChildAt(i)
                        if (child is TextView) child.setTextColor(txtColor)
                        if (child is ImageView) child.imageTintList = ColorStateList.valueOf(primColor)
                    }
                }
            }
        } catch (_: Exception) {
            Log.e("Theme", "Error tinting chat action sheet")
        }

        sheetView.findViewById<View>(R.id.actionAddContact).setOnClickListener {
            bottomSheetDialog.dismiss()
            val intent = Intent(this, ContactsActivity::class.java).apply {
                putExtra("USERNAME", username)
                putExtra("PASSWORD", password)
            }
            startActivity(intent)
        }
        bottomSheetDialog.setContentView(sheetView)
        bottomSheetDialog.show()
    }
}
