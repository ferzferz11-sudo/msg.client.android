package lavender.client.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import de.hdodenhof.circleimageview.CircleImageView
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.proto.ProtoUtils
import lavender.client.android.data.proto.UserInfoProto
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeUi
import java.io.File
import java.util.Locale

class SuperAdminActivity : AppCompatActivity() {

    private val grpcClient = GrpcClient
    private lateinit var username: String
    private lateinit var usersContainer: LinearLayout
    private lateinit var progressOverlay: View
    private lateinit var searchLayout: View
    private lateinit var searchEditText: EditText
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    
    private var allUsers = listOf<UserInfoProto>()
    private var allChats = listOf<ChatInfo>()
    private var currentMode = Mode.USERS
    private val selectedUsernames = mutableSetOf<String>()
    private val selectedChatIds = mutableSetOf<String>()
    
    enum class Mode { USERS, GROUPS }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_super_admin)

        username = SessionManager.session.value.username
        ThemeUi.bind(this, username)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.super_admin)
        toolbar.setNavigationOnClickListener { finish() }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        usersContainer = findViewById(R.id.usersContainer)
        progressOverlay = findViewById(R.id.progressOverlay)
        searchLayout = findViewById(R.id.searchLayout)
        searchEditText = findViewById(R.id.searchEditText)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMode = if (tab?.position == 0) Mode.USERS else Mode.GROUPS
                clearSelection()
                updateUI(allUsers, allChats)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        applyThemeToTabs(tabLayout)

        swipeRefreshLayout.setOnRefreshListener {
            loadData()
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterCurrentList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadData()
    }

    override fun onResume() {
        super.onResume()
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        applyThemeToTabs(tabLayout)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.super_admin_menu, menu)
        
        val themeObj = ThemeStore.currentTheme()
        val iconColor = try {
            themeObj.onPrimaryColor.toColorInt()
        } catch (_: Exception) {
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
            typedValue.data
        }
        
        menu.findItem(R.id.action_show_users)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
        menu.findItem(R.id.action_show_groups)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
        menu.findItem(R.id.action_search)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
        
        // Hide tabs from menu, now using TabLayout
        menu.findItem(R.id.action_show_users)?.isVisible = false
        menu.findItem(R.id.action_show_groups)?.isVisible = false
        
        val hasSelection = selectedUsernames.isNotEmpty() || selectedChatIds.isNotEmpty()
        if (hasSelection) {
            menu.clear()
            
            if (currentMode == Mode.USERS && selectedUsernames.size == 1) {
                menu.add(0, R.id.action_change_password, 0, R.string.change_password)
                    .setIcon(R.drawable.ic_settings_account)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }

            menu.add(0, R.id.action_delete, 0, R.string.delete)
                .setIcon(R.drawable.ic_delete)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            
            menu.findItem(R.id.action_change_password)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
            menu.findItem(R.id.action_delete)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
        }
        
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (selectedUsernames.isNotEmpty() || selectedChatIds.isNotEmpty()) {
                    clearSelection()
                    updateUI(allUsers, allChats)
                } else {
                    finish()
                }
                return true
            }
            R.id.action_change_password -> {
                if (selectedUsernames.size == 1) {
                    showAdminChangePasswordDialog(selectedUsernames.first())
                }
                return true
            }
            R.id.action_delete -> {
                if (currentMode == Mode.USERS) confirmDeleteSelectedUsers()
                else confirmDeleteSelectedChats()
                return true
            }
            R.id.action_search -> {
                searchLayout.isVisible = !searchLayout.isVisible
                if (searchLayout.isVisible) searchEditText.requestFocus()
                else {
                    searchEditText.text.clear()
                    filterCurrentList("")
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadData() {
        swipeRefreshLayout.isRefreshing = true
        progressOverlay.isVisible = true

        // Add timeout to prevent infinite loading
        val loadTimeout = lifecycleScope.launch {
            delay(15000) // 15 second timeout
            if (swipeRefreshLayout.isRefreshing) {
                Log.w("SuperAdminActivity", "Load data timeout, stopping refresh")
                runOnUiThread {
                    swipeRefreshLayout.isRefreshing = false
                    progressOverlay.isVisible = false
                }
            }
        }

        grpcClient.loadAllUsers { users ->
            allUsers = users
            Log.d("SuperAdminActivity", "Loaded ${users.size} users")
            grpcClient.getAllChats { chats ->
                allChats = chats
                Log.d("SuperAdminActivity", "Loaded ${chats.size} chats")
                loadTimeout.cancel()
                runOnUiThread {
                    swipeRefreshLayout.isRefreshing = false
                    progressOverlay.isVisible = false
                    updateUI(allUsers, allChats)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI(users: List<UserInfoProto>, chats: List<ChatInfo>) {
        usersContainer.removeAllViews()
        val emptyStateText = findViewById<TextView>(R.id.emptyStateText)
        
        val theme = ThemeStore.currentTheme()
        val surfaceColor = try { theme.surfaceColor.toColorInt() } catch (_: Exception) { android.graphics.Color.DKGRAY }
        val primaryColor = try { theme.primaryColor.toColorInt() } catch (_: Exception) { android.graphics.Color.BLUE }
        val textPrimary = try { theme.textPrimaryColor.toColorInt() } catch (_: Exception) { android.graphics.Color.WHITE }
        val textSecondary = try { theme.textSecondaryColor.toColorInt() } catch (_: Exception) { android.graphics.Color.LTGRAY }
        
        emptyStateText.setTextColor(textSecondary)

        val hasSelection = selectedUsernames.isNotEmpty() || selectedChatIds.isNotEmpty()
        if (hasSelection) {
            supportActionBar?.title = getString(R.string.selected_count, if (currentMode == Mode.USERS) selectedUsernames.size else selectedChatIds.size)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)
        } else {
            supportActionBar?.title = getString(R.string.super_admin)
            supportActionBar?.setHomeAsUpIndicator(null)
        }
        invalidateOptionsMenu()

        if (currentMode == Mode.USERS) {
            emptyStateText.isVisible = users.isEmpty()
            // Sort users by last seen time (most recent first)
            val sortedUsers = users.sortedByDescending { it.lastSeenAt?.seconds ?: 0 }
            for (user in sortedUsers) {
                val userView = layoutInflater.inflate(R.layout.item_user_super_admin, usersContainer, false)
                val card = userView as MaterialCardView
                val nameText = userView.findViewById<TextView>(R.id.participantName)
                val versionText = userView.findViewById<TextView>(R.id.clientVersion)
                val timeAgoText = userView.findViewById<TextView>(R.id.timeAgoText)
                val avatarView = userView.findViewById<CircleImageView>(R.id.participantAvatar)
                val statusDot = userView.findViewById<View>(R.id.statusIndicator)
                
                val isSelected = selectedUsernames.contains(user.username)
                card.setCardBackgroundColor(if (isSelected) primaryColor else surfaceColor)
                nameText.text = user.username
                nameText.setTextColor(if (isSelected) try { theme.onPrimaryColor.toColorInt() } catch (_: Exception) { android.graphics.Color.WHITE } else textPrimary)
                
                val versionStr = if (user.lastClientVersion.isNotEmpty()) "v${user.lastClientVersion}" else ""
                versionText.text = versionStr
                versionText.setTextColor(if (isSelected) try { theme.onPrimaryColor.toColorInt() } catch (_: Exception) { android.graphics.Color.WHITE } else textSecondary)
                
                // Calculate and show time ago
                val timeAgoStr = user.lastSeenAt?.let {
                    getTimeAgo(it.seconds * 1000)
                } ?: ""
                timeAgoText.text = timeAgoStr
                timeAgoText.setTextColor(if (isSelected) try { theme.onPrimaryColor.toColorInt() } catch (_: Exception) { android.graphics.Color.WHITE } else textSecondary)
                
                val isOnline = grpcClient.users.value.contains(user.username)
                statusDot.isVisible = !isSelected
                statusDot.setBackgroundResource(if (isOnline) R.drawable.status_online_dot else R.drawable.status_offline_dot)

                if (user.avatarUrl.isNotEmpty()) {
                    Glide.with(this).load(user.avatarUrl).placeholder(R.drawable.ic_default_avatar).into(avatarView)
                    avatarView.clearColorFilter()
                } else {
                    ThemeUtils.applyDefaultAvatar(avatarView, theme)
                }

                userView.setOnClickListener {
                    if (selectedUsernames.isNotEmpty()) {
                        toggleUserSelection(user.username)
                    } else {
                        val intent = Intent(this, ProfileActivity::class.java).apply {
                            putExtra("username", user.username)
                            putExtra("is_group", false)
                        }
                        startActivity(intent)
                    }
                }

                userView.setOnLongClickListener {
                    toggleUserSelection(user.username)
                    true
                }
                usersContainer.addView(userView)
            }
        } else {
            emptyStateText.isVisible = chats.isEmpty()
            for (chat in chats) {
                val chatView = layoutInflater.inflate(R.layout.item_chat, usersContainer, false)
                val card = chatView as MaterialCardView
                val nameText = chatView.findViewById<TextView>(R.id.chatName)
                val typeText = chatView.findViewById<TextView>(R.id.chatType)
                val participantAvatars = chatView.findViewById<LinearLayout>(R.id.participantAvatars)
                
                val isSelected = selectedChatIds.contains(chat.id)
                card.setCardBackgroundColor(if (isSelected) primaryColor else surfaceColor)
                nameText.text = chat.name
                nameText.setTextColor(if (isSelected) try { theme.onPrimaryColor.toColorInt() } catch (_: Exception) { android.graphics.Color.WHITE } else textPrimary)
                
                val sdf = java.text.SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
                val creationTime = sdf.format(java.util.Date(chat.createdAt))
                
                val description = if (chat.type.equals("direct", true)) {
                    "${chat.type} - $creationTime\nID: ${chat.id}"
                } else {
                    val adminStr = if (chat.creator.isNotEmpty()) "Admin: ${chat.creator}" else ""
                    "${chat.type} - $creationTime\n$adminStr\nID: ${chat.id}"
                }
                
                typeText.text = description
                typeText.setTextColor(if (isSelected) try { theme.onPrimaryColor.toColorInt() } catch (_: Exception) { android.graphics.Color.WHITE } else textSecondary)

                // Load avatars for the group
                participantAvatars.removeAllViews()
                if (chat.avatarUrl.isNotEmpty()) {
                    val iv = CircleImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(52.dpToPx(), 52.dpToPx())
                    }
                    Glide.with(this).load(chat.avatarUrl).placeholder(R.drawable.ic_default_avatar).into(iv)
                    participantAvatars.addView(iv)
                } else {
                    ThemeUtils.applyDefaultAvatar(CircleImageView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(52.dpToPx(), 52.dpToPx())
                        participantAvatars.addView(this)
                    }, theme)
                }
                
                chatView.setOnClickListener {
                    if (selectedChatIds.isNotEmpty()) {
                        toggleChatSelection(chat.id)
                    } else {
                        val intent = Intent(this, ProfileActivity::class.java).apply {
                            putExtra("username", chat.name)
                            putExtra("is_group", !chat.type.equals("direct", true))
                            putExtra("room_id", chat.id)
                            putExtra("avatar_url", chat.avatarUrl)
                            putExtra("full_avatar_url", chat.fullAvatarUrl)
                            putExtra("creator", chat.creator)
                            putExtra("participants", chat.participants)
                        }
                        startActivity(intent)
                    }
                }

                chatView.setOnLongClickListener {
                    toggleChatSelection(chat.id)
                    true
                }
                usersContainer.addView(chatView)
            }
        }
    }

    private fun filterCurrentList(query: String) {
        val q = query.lowercase()
        if (currentMode == Mode.USERS) {
            val filtered = allUsers.filter { it.username.lowercase().contains(q) }
            updateUI(filtered, emptyList())
        } else {
            val filtered = allChats.filter { it.name.lowercase().contains(q) || it.id.lowercase().contains(q) }
            updateUI(emptyList(), filtered)
        }
    }

    private fun toggleUserSelection(username: String) {
        if (selectedUsernames.contains(username)) {
            selectedUsernames.remove(username)
        } else {
            selectedUsernames.add(username)
        }
        updateUI(allUsers, allChats)
    }

    private fun toggleChatSelection(chatId: String) {
        if (selectedChatIds.contains(chatId)) {
            selectedChatIds.remove(chatId)
        } else {
            selectedChatIds.add(chatId)
        }
        updateUI(allUsers, allChats)
    }

    private fun clearSelection() {
        selectedUsernames.clear()
        selectedChatIds.clear()
    }

    private fun applyThemeToTabs(tabLayout: TabLayout) {
        val theme = ThemeStore.currentTheme()
        try {
            val pColor = theme.primaryColor.toColorInt()
            val onPColor = theme.onPrimaryColor.toColorInt()
            
            tabLayout.setSelectedTabIndicatorColor(onPColor)
            tabLayout.setTabTextColors(onPColor.withAlpha(150), onPColor)
            tabLayout.backgroundTintList = android.content.res.ColorStateList.valueOf(pColor)
        } catch (_: Exception) {}
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return (this and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun confirmDeleteSelectedUsers() {
        val count = selectedUsernames.size
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_profile)
            .setMessage("${getString(R.string.delete_profile)}: $count ${getString(R.string.users)}?")
            .setPositiveButton(R.string.delete) { _, _ ->
                val usernames = selectedUsernames.toList()
                clearSelection()
                progressOverlay.isVisible = true
                
                var deletedCount = 0
                usernames.forEach { targetUser ->
                    grpcClient.deleteProfile(targetUser) { _, _ ->
                        runOnUiThread {
                            deletedCount++
                            if (deletedCount == usernames.size) {
                                loadData()
                            }
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun confirmDeleteSelectedChats() {
        val count = selectedChatIds.size
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_group)
            .setMessage("${getString(R.string.delete_group)}: $count ${getString(R.string.chats)}?")
            .setPositiveButton(R.string.delete) { _, _ ->
                val chatIds = selectedChatIds.toList()
                clearSelection()
                progressOverlay.isVisible = true
                
                var deletedCount = 0
                chatIds.forEach { targetId ->
                    grpcClient.deleteChat(targetId, username) { _, _ ->
                        runOnUiThread {
                            deletedCount++
                            if (deletedCount == chatIds.size) {
                                loadData()
                            }
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showAdminChangePasswordDialog(targetUser: String) {
        val theme = ThemeStore.currentTheme()
        val textColor = try { theme.textPrimaryColor.toColorInt() } catch (_: Exception) { android.graphics.Color.WHITE }
        val bgColor = try { theme.surfaceColor.toColorInt() } catch (_: Exception) { android.graphics.Color.BLACK }
        val pColor = try { theme.primaryColor.toColorInt() } catch (_: Exception) { android.graphics.Color.BLUE }

        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        dialogView.setBackgroundColor(bgColor)
        
        val titleView = dialogView.findViewById<TextView>(R.id.tvTitle)
        titleView?.setTextColor(textColor)
        
        val editNewPw = dialogView.findViewById<EditText>(R.id.editTextNewPassword)
        val editOldPw = dialogView.findViewById<EditText>(R.id.editTextOldPassword)
        
        // Find TextInputLayouts
        val editOldPwLayout = editOldPw.parent.parent as? com.google.android.material.textfield.TextInputLayout
        
        editNewPw.setTextColor(textColor)
        editNewPw.setHintTextColor(ThemeUtils.adjustAlpha(textColor, 0.6f))
        editOldPw.setTextColor(textColor)
        
        // Hide old password field since admin doesn't need it
        editOldPwLayout?.visibility = View.GONE
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.change) { _, _ ->
                val newPw = editNewPw.text.toString()
                if (newPw.isEmpty()) {
                    Toast.makeText(this, R.string.password_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                progressOverlay.isVisible = true
                grpcClient.adminUpdatePassword(targetUser, newPw, username) { success, message ->
                    runOnUiThread {
                        progressOverlay.isVisible = false
                        if (success) {
                            Toast.makeText(this, R.string.password_updated, Toast.LENGTH_SHORT).show()
                            clearSelection()
                            updateUI(allUsers, allChats)
                        } else {
                            Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(pColor)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(textColor)
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bgColor))
        }
        dialog.show()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun getTimeAgo(timestampMillis: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestampMillis

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> getString(R.string.just_now)
            minutes < 60 -> resources.getQuantityString(R.plurals.minutes_ago, minutes.toInt(), minutes.toInt())
            hours < 24 -> resources.getQuantityString(R.plurals.hours_ago, hours.toInt(), hours.toInt())
            days < 7 -> resources.getQuantityString(R.plurals.days_ago, days.toInt(), days.toInt())
            else -> {
                val date = java.util.Date(timestampMillis)
                val sdf = java.text.SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
                sdf.format(date)
            }
        }
    }
}
