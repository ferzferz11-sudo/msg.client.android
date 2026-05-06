package lavender.client.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.messaging.FirebaseMessaging
import de.hdodenhof.circleimageview.CircleImageView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.RealGrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.databinding.ActivityChatListBinding
import lavender.client.android.ui.ThemeManager
import lavender.client.android.ui.adapter.ChatAdapter
import lavender.client.android.ui.adapter.UserAdapter
import lavender.client.android.ui.viewmodel.ChatListViewModel
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

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
    private var mutedChats = mutableSetOf<String>() // Set of muted room IDs

    private val editProfileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            loadChats()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru" // Default to Russian for first launch
        val locale = Locale.forLanguageTag(languageCode)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedLanguage()
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)
        binding = ActivityChatListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        username = intent.getStringExtra("USERNAME") ?: ""
        password = intent.getStringExtra("PASSWORD") ?: ""
        val serverAddressFull = intent.getStringExtra("SERVER_ADDRESS") ?: "159.195.38.145"

        val parts = serverAddressFull.split(":")
        val serverHost = parts[0]
        val serverPort = if (parts.size > 1) parts[1].toIntOrNull() ?: 50051 else 50051

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.actionDelete.setOnClickListener { showDeleteChatsDialog() }
        binding.actionMute.setOnClickListener { showMuteChatsDialog() }
        binding.actionSearch.setOnClickListener { showSearchBar() }

        loadMutedChats()

        adapter = ChatAdapter(
            onChatClick = { chat ->
                openChat(chat.id, chat.getDisplayName(username), chat.type == "direct", chat.participants, chat.creator, chat.avatarUrl, chat.fullAvatarUrl)
            },
            onSelectionChanged = { selectedCount ->
                val hasSelection = selectedCount > 0
                val selectedChats = adapter.getSelectedChats()
                val canDelete = selectedChats.all { chat -> chat.type == "direct" || chat.creator == username }

                // Show settings gear only for exactly 1 selected group chat where user is admin
                val canSettings = selectedCount == 1 && selectedChats.firstOrNull()?.let { chat ->
                    chat.type != "direct" && chat.creator.trim().equals(username.trim(), ignoreCase = true)
                } == true

                binding.actionDelete.isVisible = hasSelection && canDelete
                binding.actionMute.isVisible = hasSelection
                binding.actionSearch.isVisible = !hasSelection
                binding.actionSettings.isVisible = canSettings

                // Store selected chat for settings click
                if (canSettings) {
                    binding.actionSettings.setOnClickListener {
                        val chat = selectedChats.first()
                        val intent = Intent(this, ProfileActivity::class.java)
                            .putExtra("username", chat.getDisplayName(username))
                            .putExtra("is_group", true)
                            .putExtra("room_id", chat.id)
                            .putExtra("avatar_url", chat.avatarUrl)
                            .putExtra("full_avatar_url", chat.fullAvatarUrl)
                            .putExtra("participants", chat.participants)
                            .putExtra("creator", chat.creator)
                        startActivity(intent)
                    }
                }

                binding.toolbarTitle.text = if (hasSelection) getString(R.string.selected_count, selectedCount) else getString(R.string.chats)
                binding.toolbarUserAvatar.isVisible = !hasSelection

                supportActionBar?.setDisplayHomeAsUpEnabled(hasSelection)
                if (hasSelection) {
                    supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)
                }
            },
            currentUsername = username,
            initialAvatarCache = grpcClient.getAvatarCache(),
            onlineUsers = grpcClient.users.value
        )

        binding.chatsRecyclerView.adapter = adapter
        binding.chatsRecyclerView.layoutManager = LinearLayoutManager(this)

        grpcClient.connect(serverHost, false, serverPort, this)
        grpcClient.startChat(username, password, "") { _ -> }

        ThemeManager.loadTheme(this, username) {
            runOnUiThread {
                ThemeManager.applyTheme(this)
                val theme = ThemeManager.getCurrentTheme()
                if (theme != null) {
                    binding.swipeRefreshLayout.setColorSchemeColors(theme.primaryColor.toColorInt())
                } else {
                    val nightColor = "#04052E".toColorInt()
                    binding.root.setBackgroundColor(nightColor)
                    binding.chatsRecyclerView.setBackgroundColor(nightColor)
                    binding.swipeRefreshLayout.setBackgroundColor(nightColor)
                }
                adapter.notifyDataSetChanged()
                updateToolbarAvatar()
            }
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = ""
            setDisplayHomeAsUpEnabled(false)
        }
        
        binding.toolbar.setNavigationOnClickListener {
            if (binding.searchCard.isVisible) {
                hideSearchBar()
            } else if (adapter.getSelectedChats().isNotEmpty()) {
                adapter.clearSelection()
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

        binding.toolbarTitle.setOnClickListener { showUserMenuSheet() }
        binding.root.findViewById<CircleImageView>(R.id.toolbarUserAvatar).setOnClickListener { showUserMenuSheet() }
        binding.addChatFab.setOnClickListener { showChatActionSheet() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    RealGrpcClient.connectionStatus.collect { status ->
                        binding.toolbarTitle.text = when (status) {
                            ConnectionStatus.READY        -> getString(R.string.chats)
                            ConnectionStatus.CONNECTING   -> getString(R.string.connecting)
                            ConnectionStatus.FAILED       -> "Waiting for network..."
                            ConnectionStatus.DISCONNECTED -> "Offline"
                        }
                        binding.toolbarTitle.alpha = if (status == ConnectionStatus.READY) 1.0f else 0.6f
                    }
                }
                launch {
                    grpcClient.users.collect { users ->
                        adapter.setOnlineUsers(users)
                    }
                }
                launch {
                    grpcClient.avatarCacheFlow.collect { cache ->
                        adapter.updateAvatarCache(cache)
                    }
                }
            }
        }

        binding.swipeRefreshLayout.setOnRefreshListener { loadChats() }

        if (viewModel.isInitialLoadComplete) {
            binding.swipeRefreshLayout.isRefreshing = false
            adapter.setChats(viewModel.currentChats)
            binding.welcomeContainer.isVisible = viewModel.currentChats.isEmpty()
            binding.chatsRecyclerView.isVisible = viewModel.currentChats.isNotEmpty()
        } else {
            loadChats()
        }

        checkForUpdates()
        updateToolbarAvatar()
        loadAllUsers()
        startPollingChats()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.searchCard.isVisible) hideSearchBar()
                else if (adapter.getSelectedChats().isNotEmpty()) adapter.clearSelection()
                else moveTaskToBack(true)
            }
        })

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful && username.isNotEmpty()) {
                val token = task.result
                lifecycleScope.launch {
                    delay(1000)
                    val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                    val sendEnabled = prefs.getBoolean("push_send_enabled", true)
                    val receiveEnabled = prefs.getBoolean("push_receive_enabled", true)
                    grpcClient.registerToken(username, if (receiveEnabled) token else "DISABLED", sendEnabled)
                }
            }
        }

        handleIncomingActions(intent)
    }

    override fun onStart() {
        super.onStart()
        if (grpcClient.connectionStatus.value != ConnectionStatus.READY) {
            val serverAddressFull = intent.getStringExtra("SERVER_ADDRESS") ?: "159.195.38.145"
            val parts = serverAddressFull.split(":")
            val serverHost = parts[0]
            val serverPort = if (parts.size > 1) parts[1].toIntOrNull() ?: 50051 else 50051
            grpcClient.connect(serverHost, false, serverPort, this)
        }
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
                    openChat(chat.id, chat.getDisplayName(username), chat.type == "direct", chat.participants, chat.creator, chat.avatarUrl, chat.fullAvatarUrl)
                }
            }
        }

        val deleteChatId = intent.getStringExtra("ACTION_DELETE_CHAT_ID")
        if (!deleteChatId.isNullOrEmpty()) {
            val dialogView = layoutInflater.inflate(R.layout.dialog_delete_chats, null)
            val titleText = dialogView.findViewById<TextView>(R.id.titleText)
            val messageText = dialogView.findViewById<TextView>(R.id.messageText)
            val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
            val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btnDelete)
            titleText.text = getString(R.string.delete_chats)
            messageText.text = getString(R.string.delete_chats_confirmation, 1)
            
            val customTheme = ThemeManager.getCurrentTheme()
            if (customTheme != null) {
                try {
                    val onPrimaryContainerColor = customTheme.textPrimaryColor.toColorInt()
                    titleText.setTextColor(onPrimaryContainerColor)
                    messageText.setTextColor(onPrimaryContainerColor)
                    btnCancel.setTextColor(onPrimaryContainerColor)
                    val shapeDrawable = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
                        floatArrayOf(18f, 18f, 18f, 18f, 18f, 18f, 18f, 18f), null, null
                    ))
                    shapeDrawable.paint.color = customTheme.surfaceColor.toColorInt()
                    dialogView.background = shapeDrawable
                } catch (_: Exception) {}
            } else {
                val typedValue = TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
                val bgColor = ContextCompat.getColor(this, typedValue.resourceId)
                theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true)
                val textColor = ContextCompat.getColor(this, typedValue.resourceId)
                titleText.setTextColor(textColor)
                messageText.setTextColor(textColor)
                btnCancel.setTextColor(textColor)
                val shapeDrawable = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
                    floatArrayOf(18f, 18f, 18f, 18f, 18f, 18f, 18f, 18f), null, null
                ))
                shapeDrawable.paint.color = bgColor
                dialogView.background = shapeDrawable
            }
            
            val dialog = AlertDialog.Builder(this).setView(dialogView).create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            btnCancel.setOnClickListener { dialog.dismiss() }
            btnDelete.setOnClickListener {
                dialog.dismiss()
                grpcClient.deleteChat(deleteChatId) { success, _ ->
                    if (success) {
                        runOnUiThread { showToast(getString(R.string.deleted_count, 1)) }
                        loadChats()
                    }
                }
            }
            dialog.show()
        }
    }

    private fun loadChats() {
        if (username.isEmpty()) return
        binding.swipeRefreshLayout.isRefreshing = true
        loadMutedChats()
        viewModel.loadChats(username) { success, _ ->
            runOnUiThread {
                binding.swipeRefreshLayout.isRefreshing = false
                if (success) {
                    val updatedChats = viewModel.currentChats.map {
                        it.copy(isMuted = mutedChats.contains(it.id))
                    }
                    adapter.setChats(updatedChats)
                    binding.welcomeContainer.isVisible = updatedChats.isEmpty()
                    binding.chatsRecyclerView.isVisible = updatedChats.isNotEmpty()
                    checkOnboarding(updatedChats)
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
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        var firstLaunch = prefs.getLong("first_launch_time", 0L)
        if (firstLaunch == 0L) {
            firstLaunch = System.currentTimeMillis()
            prefs.edit { putLong("first_launch_time", firstLaunch) }
        }
        val isWithinTwoDays = System.currentTimeMillis() - firstLaunch < 2 * 24 * 60 * 60 * 1000L

        val isNewUser = chats.isEmpty() && isWithinTwoDays
        val typedValue = TypedValue()
        val customTheme = ThemeManager.getCurrentTheme()
        val bubbleBgColor = if (customTheme != null) {
            try {
                customTheme.primaryColor.toColorInt()
            } catch (_: Exception) {
                getColorFromAttr(com.google.android.material.R.attr.colorSecondaryContainer)
            }
        } else {
            getColorFromAttr(com.google.android.material.R.attr.colorSecondaryContainer)
        }
        val textColor = getColorFromAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
        binding.onboardingProfileBubble.backgroundTintList = ColorStateList.valueOf(bubbleBgColor)
        binding.onboardingFabBubble.backgroundTintList = ColorStateList.valueOf(bubbleBgColor)
        binding.onboardingProfileText.setTextColor(textColor)
        binding.onboardingFabText.setTextColor(textColor)
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
        grpcClient.getUserAvatar(username) { url ->
            if (url.isNotEmpty()) {
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
            grpcClient.getUserAvatar(participant) { _ ->
                updateCount++
                if (updateCount % 5 == 0 || updateCount == allParticipants.size) {
                    viewModel.avatarCache = grpcClient.getAvatarCache()
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
                delay(3000)
                if (username.isNotEmpty()) {
                    val previousChatCount = viewModel.currentChats.size
                    viewModel.loadChats(username) { success, _ ->
                        if (success) {
                            runOnUiThread {
                                val updatedChats = viewModel.currentChats.map {
                                    it.copy(isMuted = mutedChats.contains(it.id))
                                }
                                adapter.setChats(updatedChats)
                                binding.welcomeContainer.isVisible = updatedChats.isEmpty()
                                binding.chatsRecyclerView.isVisible = updatedChats.isNotEmpty()
                                checkOnboarding(updatedChats)
                                if (updatedChats.size != previousChatCount) {
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
            avatarView.setImageResource(R.drawable.ic_default_avatar_white)
            avatarView.clearColorFilter()
        }
    }

    private fun openChat(chatId: String, roomName: String, isDirect: Boolean = false, participants: String = "[]", creator: String = "", avatarUrl: String = "", fullAvatarUrl: String = "") {
        val intent = Intent(this, NewChatActivity::class.java).apply {
            putExtra("ROOM_ID", chatId)
            putExtra("CHAT_NAME", roomName)
            putExtra("IS_DIRECT", isDirect)
            putExtra("PARTICIPANTS", participants)
            putExtra("CREATOR", creator)
            putExtra("AVATAR_URL", avatarUrl)
            putExtra("FULL_AVATAR_URL", fullAvatarUrl)
            putExtra("USERNAME", username)
            putExtra("PASSWORD", password)
            val serverAddressFull = this@ChatListActivity.intent.getStringExtra("SERVER_ADDRESS")
            putExtra("SERVER_ADDRESS", serverAddressFull)
        }
        startActivity(intent)
    }

    private fun showDeleteChatsDialog() {
        val selected = adapter.getSelectedChats()
        if (selected.isEmpty()) return
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_chats, null)
        val titleText = dialogView.findViewById<TextView>(R.id.titleText)
        val messageText = dialogView.findViewById<TextView>(R.id.messageText)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btnDelete)
        titleText.text = getString(R.string.delete_chats)
        messageText.text = getString(R.string.delete_chats_confirmation, selected.size)
        val customTheme = ThemeManager.getCurrentTheme()
        if (customTheme != null) {
            try {
                val textColor = customTheme.textPrimaryColor.toColorInt()
                titleText.setTextColor(textColor)
                messageText.setTextColor(textColor)
                btnCancel.setTextColor(textColor)
                val shape = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
                    floatArrayOf(18f, 18f, 18f, 18f, 18f, 18f, 18f, 18f), null, null
                ))
                shape.paint.color = customTheme.surfaceColor.toColorInt()
                dialogView.background = shape
            } catch (_: Exception) {}
        }
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnDelete.setOnClickListener {
            dialog.dismiss()
            binding.toolbarTitle.text = getString(R.string.loading)
            val chatsToDelete = selected.toList()
            adapter.clearSelection()
            lifecycleScope.launch {
                var successCount = 0
                for (chat in chatsToDelete) {
                    val success = withContext(Dispatchers.IO) {
                        val deferred = CompletableDeferred<Boolean>()
                        grpcClient.deleteChat(chat.id) { s, _ -> deferred.complete(s) }
                        deferred.await()
                    }
                    if (success) successCount++
                }
                runOnUiThread {
                    binding.toolbarTitle.text = getString(R.string.chats)
                    if (successCount > 0) showToast(getString(R.string.deleted_count, successCount))
                    loadChats()
                }
            }
        }
        dialog.show()
    }

    private fun loadMutedChats() {
        // Ensure we have userId set before loading muted chats
        ensureUserIdSet {
            grpcClient.getMutedChats { roomIds ->
                runOnUiThread {
                    mutedChats.clear()
                    mutedChats.addAll(roomIds)
                    if (viewModel.currentChats.isNotEmpty()) {
                        val updatedChats = viewModel.currentChats.map {
                            it.copy(isMuted = mutedChats.contains(it.id))
                        }
                        adapter.setChats(updatedChats)
                    }
                }
            }
        }
    }

    private fun ensureUserIdSet(onReady: () -> Unit) {
        val savedUserId = getSavedUserId()
        if (savedUserId != null) {
            grpcClient.setUserId(savedUserId)
            onReady()
        } else if (username.isNotEmpty()) {
            // Fetch userId from server
            grpcClient.fetchUserId(username) { userId, found ->
                if (found && userId != null) {
                    saveUserId(userId)
                    grpcClient.setUserId(userId)
                }
                onReady()
            }
        } else {
            onReady()
        }
    }

    private fun getSavedUserId(): String? {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        return prefs.getString("user_id", null)
    }

    private fun saveUserId(userId: String) {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        prefs.edit { putString("user_id", userId) }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun showMuteChatsDialog() {
        val selected = adapter.getSelectedChats()
        if (selected.isEmpty()) return
        val allMuted = selected.all { mutedChats.contains(it.id) }
        val titleRes = if (allMuted) R.string.unmute_chats else R.string.mute_chats
        val messageRes = if (allMuted) R.string.unmute_chats_confirmation else R.string.mute_chats_confirmation
        val iconRes = if (allMuted) R.drawable.ic_volume_on else R.drawable.ic_volume_off
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_chats, null)
        val titleText = dialogView.findViewById<TextView>(R.id.titleText)
        val messageText = dialogView.findViewById<TextView>(R.id.messageText)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnAction = dialogView.findViewById<MaterialButton>(R.id.btnDelete)
        titleText.text = getString(titleRes)
        messageText.text = getString(messageRes, selected.size)
        btnAction.text = getString(if (allMuted) R.string.unmute else R.string.mute)
        btnAction.icon = ContextCompat.getDrawable(this, iconRes)
        val customTheme = ThemeManager.getCurrentTheme()
        if (customTheme != null) {
            try {
                val textColor = customTheme.textPrimaryColor.toColorInt()
                titleText.setTextColor(textColor)
                messageText.setTextColor(textColor)
                btnCancel.setTextColor(textColor)
                val shape = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
                    floatArrayOf(18f, 18f, 18f, 18f, 18f, 18f, 18f, 18f), null, null
                ))
                shape.paint.color = customTheme.surfaceColor.toColorInt()
                dialogView.background = shape
            } catch (_: Exception) {}
        }
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnAction.setOnClickListener {
            dialog.dismiss()
            val chatsToToggle = selected.toList()
            adapter.clearSelection()
            lifecycleScope.launch {
                var successCount = 0
                for (chat in chatsToToggle) {
                    val success = withContext(Dispatchers.IO) {
                        val deferred = CompletableDeferred<Boolean>()
                        grpcClient.setMutedChat(chat.id, !allMuted) { deferred.complete(it) }
                        deferred.await()
                    }
                    if (success) {
                        successCount++
                        if (!allMuted) mutedChats.add(chat.id) else mutedChats.remove(chat.id)
                    }
                }
                runOnUiThread {
                    binding.toolbarTitle.text = getString(R.string.chats)
                    if (successCount > 0) {
                        val toastRes = if (allMuted) R.string.unmuted_count else R.string.muted_count
                        showToast(getString(toastRes, successCount))
                    }
                    val updatedChats = adapter.getChats().map {
                        it.copy(isMuted = mutedChats.contains(it.id))
                    }
                    adapter.setChats(updatedChats)
                }
            }
        }
        dialog.show()
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResume() {
        super.onResume()
        ThemeManager.loadTheme(this, username) {
            runOnUiThread {
                ThemeManager.applyTheme(this)
                binding.toolbar.dismissPopupMenus()
                if (::adapter.isInitialized) {
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showAboutDialog() {
        val clientVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) { BuildConfig.VERSION_NAME }
        val serverVersion = grpcClient.serverVersion.value.ifEmpty { "..." }
        val latestVersion = getSharedPreferences("UpdatePrefs", MODE_PRIVATE).getString("latest_version", "") ?: ""
        val isUpdateAvailable = isUpdateAvailable(latestVersion)
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
        val clientVersionText = dialogView.findViewById<TextView>(R.id.clientVersionText)
        val serverVersionText = dialogView.findViewById<TextView>(R.id.serverVersionText)
        val btnUpdate = dialogView.findViewById<Button>(R.id.btnUpdate)
        val btnFeedback = dialogView.findViewById<Button>(R.id.btnFeedback)
        val btnShare = dialogView.findViewById<Button>(R.id.btnShare)
        val btnClose = dialogView.findViewById<Button>(R.id.btnClose)
        clientVersionText.text = getString(R.string.version_label, clientVersion)
        serverVersionText.text = getString(R.string.server_version_format, serverVersion)
        val customTheme = ThemeManager.getCurrentTheme()
        if (customTheme != null) {
            try {
                val onPrimaryContainerColor = customTheme.textPrimaryColor.toColorInt()
                val surfaceColor = customTheme.surfaceColor.toColorInt()
                val primaryColor = customTheme.primaryColor.toColorInt()
                clientVersionText.setTextColor(onPrimaryContainerColor)
                serverVersionText.setTextColor(onPrimaryContainerColor)
                listOf(btnUpdate, btnFeedback, btnShare, btnClose).forEach { btn ->
                    if (btn is MaterialButton) {
                        btn.setTextColor(primaryColor)
                        btn.iconTint = ColorStateList.valueOf(primaryColor)
                        btn.rippleColor = ColorStateList.valueOf(ThemeManager.adjustAlpha(primaryColor, 0.1f))
                    } else {
                        btn.setTextColor(primaryColor)
                    }
                }
                val shapeDrawable = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
                    floatArrayOf(50f, 50f, 50f, 50f, 50f, 50f, 50f, 50f), null, null
                ))
                shapeDrawable.paint.color = surfaceColor
                dialogView.background = shapeDrawable
            } catch (e: Exception) {
                Log.e("ThemeManager", "Error tinting About dialog", e)
            }
        } else {
            val typedValue = TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
            val bgColor = if (typedValue.resourceId != 0) ContextCompat.getColor(this, typedValue.resourceId) else typedValue.data
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true)
            val textColor = if (typedValue.resourceId != 0) ContextCompat.getColor(this, typedValue.resourceId) else typedValue.data
            clientVersionText.setTextColor(textColor)
            serverVersionText.setTextColor(textColor)
            val shapeDrawable = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
                floatArrayOf(50f, 50f, 50f, 50f, 50f, 50f, 50f, 50f), null, null
            ))
            shapeDrawable.paint.color = bgColor
            dialogView.background = shapeDrawable
        }
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnUpdate.isVisible = isUpdateAvailable
        btnUpdate.setOnClickListener {
            dialog.dismiss()
            showUpdateConfirmationDialog(true)
        }
        btnFeedback.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:".toUri()
                putExtra(Intent.EXTRA_EMAIL, arrayOf("ferzferz11@gmail.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Lavender Messenger Feedback")
            }
            try { startActivity(intent) } catch (_: Exception) { showToast("No email app found") }
        }
        btnShare.setOnClickListener { shareApp() }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_app))
            putExtra(Intent.EXTRA_TEXT, APK_URL)
        }
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
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) { BuildConfig.VERSION_NAME }
        val latestVersion = getSharedPreferences("UpdatePrefs", MODE_PRIVATE).getString("latest_version", currentVersion) ?: currentVersion
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_chats, null)
        val titleText = dialogView.findViewById<TextView>(R.id.titleText)
        val messageText = dialogView.findViewById<TextView>(R.id.messageText)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btnDelete)
        if (isUpdateAvailable) {
            titleText.text = getString(R.string.update_available)
            messageText.text = getString(R.string.version_current, currentVersion) + "\n" + getString(R.string.version_available, latestVersion) + "\n\n" + getString(R.string.update_confirmation_message)
            btnDelete.text = getString(R.string.update_now)
            btnCancel.text = getString(R.string.cancel)
        } else {
            titleText.text = getString(R.string.no_updates_available)
            messageText.text = getString(R.string.version_current, currentVersion) + "\n\n" + getString(R.string.version_latest_message)
            btnDelete.text = getString(R.string.force_download)
            btnCancel.text = getString(R.string.ok)
        }
        val customTheme = ThemeManager.getCurrentTheme()
        if (customTheme != null) {
            try {
                val onPrimaryContainerColor = customTheme.textPrimaryColor.toColorInt()
                titleText.setTextColor(onPrimaryContainerColor)
                messageText.setTextColor(onPrimaryContainerColor)
                btnCancel.setTextColor(onPrimaryContainerColor)
                btnDelete.setTextColor(onPrimaryContainerColor)
                val shapeDrawable = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
                    floatArrayOf(18f, 18f, 18f, 18f, 18f, 18f, 18f, 18f), null, null
                ))
                shapeDrawable.paint.color = customTheme.surfaceColor.toColorInt()
                dialogView.background = shapeDrawable
            } catch (_: Exception) {}
        } else {
            val typedValue = TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
            val bgColor = ContextCompat.getColor(this, typedValue.resourceId)
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true)
            val textColor = ContextCompat.getColor(this, typedValue.resourceId)
            titleText.setTextColor(textColor)
            messageText.setTextColor(textColor)
            btnCancel.setTextColor(textColor)
            btnDelete.setTextColor(textColor)
            val shapeDrawable = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.RoundRectShape(
                floatArrayOf(18f, 18f, 18f, 18f, 18f, 18f, 18f, 18f), null, null
            ))
            shapeDrawable.paint.color = bgColor
            dialogView.background = shapeDrawable
        }
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnDelete.setOnClickListener {
            dialog.dismiss()
            downloadAndInstallApk()
        }
        dialog.show()
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

        sheetView.findViewById<View>(R.id.actionAdmin)?.isVisible = grpcClient.isSuperAdmin.value

        val customTheme = ThemeManager.getCurrentTheme()
        if (customTheme != null) {
            val actionIds = listOf(R.id.actionEditProfile, R.id.actionThemes, R.id.actionNotifications, R.id.actionContacts, R.id.actionToggleLanguage, R.id.actionLogout, R.id.actionUpdate, R.id.actionAbout, R.id.actionAdmin)
            try {
                val bgColor = customTheme.backgroundColor.toColorInt()
                val txtColor = customTheme.textPrimaryColor.toColorInt()
                val primColor = customTheme.primaryColor.toColorInt()
                val secColor = customTheme.onSurfaceColor.toColorInt()
                sheetView.setBackgroundColor(bgColor)
                menuUsername.setTextColor(txtColor)
                menuUserBio.setTextColor(secColor)
                sheetView.findViewById<View>(R.id.dragHandle)?.backgroundTintList = ColorStateList.valueOf(primColor)
                actionIds.forEach { id ->
                    sheetView.findViewById<LinearLayout>(id)?.let { layout ->
                        for (i in 0 until layout.childCount) {
                            val child = layout.getChildAt(i)
                            if (child is TextView) {
                                if (id != R.id.actionLogout) child.setTextColor(txtColor)
                            }
                            if (child is ImageView) {
                                if (id != R.id.actionLogout) child.imageTintList = ColorStateList.valueOf(primColor)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                Log.e("ThemeManager", "Error tinting bottom sheet items")
            }
        } else {
            try {
                val actionIds = listOf(R.id.actionEditProfile, R.id.actionThemes, R.id.actionNotifications, R.id.actionContacts, R.id.actionToggleLanguage, R.id.actionLogout, R.id.actionUpdate, R.id.actionAbout, R.id.actionAdmin)
                val typedValue = TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
                val bgColor = typedValue.data
                sheetView.setBackgroundColor(bgColor)
                theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                val primaryColor = typedValue.data
                sheetView.findViewById<View>(R.id.dragHandle)?.backgroundTintList = ColorStateList.valueOf(primaryColor)
                actionIds.forEach { id ->
                    sheetView.findViewById<LinearLayout>(id)?.let { layout ->
                        for (i in 0 until layout.childCount) {
                            val child = layout.getChildAt(i)
                            if (child is ImageView) {
                                if (id != R.id.actionLogout) child.imageTintList = ColorStateList.valueOf(primaryColor)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
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
        val fullAvatarCache = grpcClient.getFullAvatarCache()
        val myAvatarUrl = avatarCache[username]
        val myFullAvatarUrl = fullAvatarCache[username]
        if (!myAvatarUrl.isNullOrEmpty()) {
            Glide.with(this).load(myAvatarUrl).placeholder(R.drawable.ic_default_avatar).circleCrop().into(menuUserAvatar)
            menuUserAvatar.setOnClickListener {
                bottomSheetDialog.dismiss()
                val intent = Intent(this, FullScreenImageActivity::class.java).apply {
                    putExtra("image_url", myFullAvatarUrl ?: myAvatarUrl)
                }
                startActivity(intent)
            }
        } else {
            menuUserAvatar.setImageResource(R.drawable.ic_default_avatar_white)
            menuUserAvatar.clearColorFilter()
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
        sheetView.findViewById<View>(R.id.actionAbout).setOnClickListener {
            bottomSheetDialog.dismiss()
            showAboutDialog()
        }
        sheetView.findViewById<View>(R.id.actionAdmin)?.setOnClickListener {
            bottomSheetDialog.dismiss()
            startActivity(Intent(this, SuperAdminActivity::class.java))
        }
        sheetView.findViewById<View>(R.id.actionUpdate).setOnClickListener {
            bottomSheetDialog.dismiss()
            checkForUpdates { available ->
                showUpdateConfirmationDialog(available)
            }
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
        val customTheme = ThemeManager.getCurrentTheme()
        val actionIds = listOf(R.id.actionStartChat, R.id.actionAddContact, R.id.actionAddGroup)
        if (customTheme != null) {
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
                Log.e("ThemeManager", "Error tinting chat action sheet")
            }
        } else {
            try {
                val typedValue = TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
                val bgColor = typedValue.data
                sheetView.setBackgroundColor(bgColor)
                theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                val primaryColor = typedValue.data
                sheetView.findViewById<View>(R.id.dragHandle)?.backgroundTintList = ColorStateList.valueOf(primaryColor)
                actionIds.forEach { id ->
                    sheetView.findViewById<LinearLayout>(id)?.let { layout ->
                        for (i in 0 until layout.childCount) {
                            val child = layout.getChildAt(i)
                            if (child is ImageView) child.imageTintList = ColorStateList.valueOf(primaryColor)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
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
                val customTheme = ThemeManager.getCurrentTheme()
                val bgColor = if (customTheme != null) {
                    try { customTheme.primaryColor.toColorInt() } catch (_: Exception) { getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainer) }
                } else {
                    getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainer)
                }
                val shapeDrawable = android.graphics.drawable.ShapeDrawable(
                    android.graphics.drawable.shapes.RoundRectShape(floatArrayOf(28f, 28f, 28f, 28f, 28f, 28f, 28f, 28f), null, null)
                )
                shapeDrawable.paint.color = bgColor
                dialogView.background = shapeDrawable
                val searchEditText = dialogView.findViewById<EditText>(R.id.searchEditText)
                val searchInputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.searchInputLayout)
                val usersRecyclerView = dialogView.findViewById<RecyclerView>(R.id.usersRecyclerView)
                val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
                val btnStartChat = dialogView.findViewById<MaterialButton>(R.id.btnStartChat)
                if (customTheme == null) {
                    val btnBgColor = ColorStateList.valueOf(getColorFromAttr(com.google.android.material.R.attr.colorSecondaryContainer))
                    val btnTextColor = getColorFromAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
                    btnCancel.backgroundTintList = btnBgColor
                    btnCancel.setTextColor(btnTextColor)
                    btnStartChat.backgroundTintList = btnBgColor
                    btnStartChat.setTextColor(btnTextColor)
                    val boxColor = ColorStateList.valueOf(bgColor)
                    searchInputLayout.setBoxStrokeColorStateList(boxColor)
                    searchInputLayout.defaultHintTextColor = boxColor
                }
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
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
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
                    val customTheme = ThemeManager.getCurrentTheme()
                    val bgColor = if (customTheme != null) {
                        try { customTheme.primaryColor.toColorInt() } catch (_: Exception) { getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainer) }
                    } else {
                        getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainer)
                    }
                    val shapeDrawable = android.graphics.drawable.ShapeDrawable(
                        android.graphics.drawable.shapes.RoundRectShape(floatArrayOf(28f, 28f, 28f, 28f, 28f, 28f, 28f, 28f), null, null)
                    )
                    shapeDrawable.paint.color = bgColor
                    dialogView.background = shapeDrawable
                    val groupNameInput = dialogView.findViewById<EditText>(R.id.groupNameInput)
                    val usersContainer = dialogView.findViewById<LinearLayout>(R.id.usersContainer)
                    val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
                    val btnCreate = dialogView.findViewById<MaterialButton>(R.id.btnCreate)
                    val groupInputLayout = dialogView.findViewById<TextInputLayout>(R.id.groupInputLayout)
                    if (customTheme == null) {
                        val btnBgColor = ColorStateList.valueOf(getColorFromAttr(com.google.android.material.R.attr.colorSecondaryContainer))
                        val btnTextColor = getColorFromAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
                        btnCancel.backgroundTintList = btnBgColor
                        btnCancel.setTextColor(btnTextColor)
                        btnCreate.backgroundTintList = btnBgColor
                        btnCreate.setTextColor(btnTextColor)
                        val boxColor = ColorStateList.valueOf(bgColor)
                        groupInputLayout.setBoxStrokeColorStateList(boxColor)
                        groupInputLayout.defaultHintTextColor = boxColor
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
                        if (!cachedAvatarUrl.isNullOrBlank()) {
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
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
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
        val customTheme = ThemeManager.getCurrentTheme()
        val bgColor = if (customTheme != null) {
            try { customTheme.primaryColor.toColorInt() } catch (_: Exception) { getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainer) }
        } else {
            getColorFromAttr(com.google.android.material.R.attr.colorSurfaceContainer)
        }
        val shapeDrawable = android.graphics.drawable.ShapeDrawable(
            android.graphics.drawable.shapes.RoundRectShape(floatArrayOf(28f, 28f, 28f, 28f, 28f, 28f, 28f, 28f), null, null)
        )
        shapeDrawable.paint.color = bgColor
        dialogView.background = shapeDrawable
        val searchEditText = dialogView.findViewById<EditText>(R.id.searchEditText)
        val searchInputLayout = dialogView.findViewById<TextInputLayout>(R.id.searchInputLayout)
        val usersRecyclerView = dialogView.findViewById<RecyclerView>(R.id.usersRecyclerView)
        val createChatCheckbox = dialogView.findViewById<CheckBox>(R.id.createChatCheckbox)
        val btnAdd = dialogView.findViewById<MaterialButton>(R.id.btnAdd)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        if (customTheme == null) {
            val btnBgColor = ColorStateList.valueOf(getColorFromAttr(com.google.android.material.R.attr.colorSecondaryContainer))
            val btnTextColor = getColorFromAttr(com.google.android.material.R.attr.colorOnSecondaryContainer)
            btnCancel.backgroundTintList = btnBgColor
            btnCancel.setTextColor(btnTextColor)
            btnAdd.backgroundTintList = btnBgColor
            btnAdd.setTextColor(btnTextColor)
            val boxColor = ColorStateList.valueOf(bgColor)
            searchInputLayout.setBoxStrokeColorStateList(boxColor)
            searchInputLayout.defaultHintTextColor = boxColor
        }
        val allUsers = mutableListOf<String>()
        val userContacts = mutableListOf<String>()
        val userAdapter = UserAdapter(
            onUserClick = { selected -> btnAdd.isEnabled = selected != username && !userContacts.contains(selected) },
            avatarCache = grpcClient.getAvatarCache(),
            onlineUsers = grpcClient.users.value
        )
        usersRecyclerView.adapter = userAdapter
        usersRecyclerView.layoutManager = LinearLayoutManager(this)
        grpcClient.loadAllUsers()
        grpcClient.getContacts(username) { contacts ->
            userContacts.clear()
            userContacts.addAll(contacts)
        }
        lifecycleScope.launch {
            delay(500)
            allUsers.clear()
            allUsers.addAll(grpcClient.allUsers.value.filter { it != username && !userContacts.contains(it) })
            runOnUiThread {
                binding.toolbarTitle.text = getString(R.string.chats)
                userAdapter.setUsers(allUsers)
            }
        }
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                userAdapter.setUsers(allUsers.filter { it.lowercase().contains(query) && !userContacts.contains(it) })
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
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
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

    private fun applySavedLanguage() {
        val lang = getSavedLanguage() ?: "ru"
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
    }

    private fun getSavedLanguage(): String? = getSharedPreferences("lavender_prefs", MODE_PRIVATE).getString("language", null)
    private fun saveLanguage(languageCode: String) { getSharedPreferences("lavender_prefs", MODE_PRIVATE).edit { putString("language", languageCode) } }

    private fun toggleLanguage() {
        val next = if (getSavedLanguage() == "en") "ru" else "en"
        saveLanguage(next)
        recreate()
    }

    private fun logout() {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        prefs.edit {
            remove("username")
            remove("password")
            remove("chat_list_version")
        }
        grpcClient.disconnect()
        showToast(getString(R.string.logged_out))
        val intent = Intent(this, SplashActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra("extra_skip_autologin", true)
        startActivity(intent)
        finish()
    }

    private fun clearMenuAnimations() {}

    private fun showSearchBar() {
        binding.searchCard.isVisible = true
        binding.searchEditText.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.searchEditText, 0)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_close)
        }
        binding.actionSearch.isVisible = false
        binding.actionDelete.isVisible = false
        binding.actionMute.isVisible = false
    }

    private fun hideSearchBar() {
        binding.searchCard.isVisible = false
        binding.searchEditText.text.clear()
        adapter.filter("")
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)
        val hasSelection = adapter.getSelectedChats().isNotEmpty()
        supportActionBar?.setDisplayHomeAsUpEnabled(hasSelection)
        binding.actionSearch.isVisible = !hasSelection
        binding.actionDelete.isVisible = hasSelection
        binding.actionMute.isVisible = hasSelection
        if (hasSelection) {
            supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun getColorFromAttr(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
}
