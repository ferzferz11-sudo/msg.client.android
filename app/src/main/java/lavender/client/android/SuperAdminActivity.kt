package lavender.client.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.proto.AdminUserInfoProto
import lavender.client.android.data.proto.UserInfoProto
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.LogViewerActivity
import lavender.client.android.ui.adapter.SuperAdminAdapter
import lavender.client.android.ui.widget.StandardBottomSheet
import java.util.Locale

class SuperAdminActivity : AppCompatActivity() {

    private val grpcClient = GrpcClient
    private lateinit var username: String
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SuperAdminAdapter
    private lateinit var searchLayout: View
    private lateinit var searchEditText: EditText
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    
    private var allUsers = listOf<UserInfoProto>()
    private var adminUsers = listOf<AdminUserInfoProto>()
    private var allChats = listOf<ChatInfo>()
    private var currentMode = Mode.USERS
    private var currentCursor = ""
    private var hasMore = true
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
        toolbar.navigationIcon?.setTint(getColorOnPrimary())
        toolbar.setNavigationOnClickListener { finish() }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        recyclerView = findViewById(R.id.recyclerView)
        searchLayout = findViewById(R.id.searchLayout)
        searchEditText = findViewById(R.id.searchEditText)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        setupRecyclerView()

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMode = if (tab?.position == 0) Mode.USERS else Mode.GROUPS
                clearSelection()
                if (currentMode == Mode.USERS) {
                    updateAdminUI(adminUsers, allChats)
                } else {
                    updateUI(allUsers, allChats)
                }
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

    private fun setupRecyclerView() {
        adapter = SuperAdminAdapter(
            onUserClick = { user ->
                val adminUser = user as? AdminUserInfoProto ?: return@SuperAdminAdapter
                if (selectedUsernames.isNotEmpty()) {
                    toggleUserSelection(adminUser.username)
                } else {
                    adapter.toggleSessions(adminUser)
                    if (adapter.isExpanded(adminUser.username)) {
                        loadUserSessions(adminUser)
                    }
                }
            },
            onUserLongClick = { user ->
                val username = when (user) {
                    is UserInfoProto -> user.username
                    is AdminUserInfoProto -> user.username
                    else -> return@SuperAdminAdapter
                }
                toggleUserSelection(username)
            },
            onChatClick = { chat ->
                if (selectedChatIds.isNotEmpty()) {
                    toggleChatSelection(chat.id)
                } else {
                    val intent = Intent(this, ProfileActivity::class.java).apply {
                        putExtra("username", chat.getDisplayName(username))
                        putExtra("is_group", !chat.type.equals("direct", true))
                        putExtra("room_id", chat.id)
                        putExtra("avatar_url", chat.avatarUrl)
                        putExtra("full_avatar_url", chat.fullAvatarUrl)
                        putExtra("creator", chat.creator)
                        putExtra("participants", chat.participants)
                        putExtra("chat_name", chat.name)
                    }
                    startActivity(intent)
                }
            },
            onChatLongClick = { chat ->
                toggleChatSelection(chat.id)
            },
            onlineUsers = grpcClient.users.value.toSet()
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0 && currentMode == Mode.USERS) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    val totalItems = layoutManager.itemCount
                    if (lastVisible >= totalItems - 5 && hasMore && currentCursor.isNotEmpty()) {
                        loadMoreUsers()
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        applyThemeToTabs(tabLayout)
        if (::adapter.isInitialized) {
            adapter.updateTheme()
        }
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
        
        menu.findItem(R.id.action_show_users)?.iconTintList = ColorStateList.valueOf(iconColor)
        menu.findItem(R.id.action_show_groups)?.iconTintList = ColorStateList.valueOf(iconColor)
        menu.findItem(R.id.action_search)?.iconTintList = ColorStateList.valueOf(iconColor)
        menu.findItem(R.id.action_logs)?.iconTintList = ColorStateList.valueOf(iconColor)
        
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
            
            menu.findItem(R.id.action_change_password)?.iconTintList = ColorStateList.valueOf(iconColor)
            menu.findItem(R.id.action_delete)?.iconTintList = ColorStateList.valueOf(iconColor)
        }
        
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                if (selectedUsernames.isNotEmpty() || selectedChatIds.isNotEmpty()) {
                    clearSelection()
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
            R.id.action_logs -> {
                startActivity(Intent(this, LogViewerActivity::class.java))
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadData() {
        swipeRefreshLayout.isRefreshing = true
        currentCursor = ""
        hasMore = true
        adapter.clearExpanded()

        val loadTimeout = lifecycleScope.launch {
            delay(15000)
            if (swipeRefreshLayout.isRefreshing) {
                Log.w("SuperAdminActivity", "Load data timeout, stopping refresh")
                runOnUiThread {
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }

        grpcClient.getAdminUserList("", "", 50, "last_message") { response ->
            adminUsers = response.users
            currentCursor = response.nextCursor
            hasMore = response.hasMore
            Log.d("SuperAdminActivity", "Loaded ${response.users.size} admin users, hasMore=$hasMore")
            runOnUiThread {
                swipeRefreshLayout.isRefreshing = false
                updateAdminUI(adminUsers, allChats)
            }
            grpcClient.getAllChats { chats ->
                allChats = chats
                Log.d("SuperAdminActivity", "Loaded ${chats.size} chats")
                runOnUiThread {
                    updateAdminUI(adminUsers, allChats)
                }
            }
            loadTimeout.cancel()
        }
    }

    private fun loadUserSessions(user: AdminUserInfoProto) {
        grpcClient.getAdminUserSessions(user.userId) { response ->
            Log.d("SuperAdminActivity", "Loaded ${response.sessions.size} sessions for ${user.username}")
            runOnUiThread {
                adapter.setSessions(user.username, response.sessions)
            }
        }
    }

    private fun getColorOnPrimary(): Int {
        val theme = ThemeStore.currentTheme()
        return lavender.client.android.theme.ThemeUtils.parseSafeColor(theme.onPrimaryColor, android.graphics.Color.WHITE)
    }

    @SuppressLint("SetTextI18n")
    private fun updateUI(users: List<UserInfoProto>, chats: List<ChatInfo>) {
        val emptyStateText = findViewById<TextView>(R.id.emptyStateText)
        
        val theme = ThemeStore.currentTheme()
        val textSecondary = try { theme.textSecondaryColor.toColorInt() } catch (_: Exception) { android.graphics.Color.LTGRAY }
        
        emptyStateText.setTextColor(textSecondary)

        val hasSelection = selectedUsernames.isNotEmpty() || selectedChatIds.isNotEmpty()
        if (hasSelection) {
            supportActionBar?.title = getString(R.string.selected_count, if (currentMode == Mode.USERS) selectedUsernames.size else selectedChatIds.size)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)
            findViewById<MaterialToolbar>(R.id.toolbar).navigationIcon?.setTint(getColorOnPrimary())
        } else {
            supportActionBar?.title = getString(R.string.super_admin)
            supportActionBar?.setHomeAsUpIndicator(null)
        }
        invalidateOptionsMenu()

        lifecycleScope.launch(Dispatchers.Default) {
            val sorted = if (currentMode == Mode.USERS) {
                users.sortedByDescending { it.lastSeenAt?.seconds ?: 0 }
            } else {
                chats
            }
            withContext(Dispatchers.Main) {
                if (currentMode == Mode.USERS) {
                    emptyStateText.isVisible = users.isEmpty()
                    adapter.setItems(sorted)
                } else {
                    emptyStateText.isVisible = chats.isEmpty()
                    adapter.setItems(sorted)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateAdminUI(users: List<AdminUserInfoProto>, chats: List<ChatInfo>) {
        val emptyStateText = findViewById<TextView>(R.id.emptyStateText)
        
        val theme = ThemeStore.currentTheme()
        val textSecondary = try { theme.textSecondaryColor.toColorInt() } catch (_: Exception) { android.graphics.Color.LTGRAY }
        
        emptyStateText.setTextColor(textSecondary)

        val hasSelection = selectedUsernames.isNotEmpty() || selectedChatIds.isNotEmpty()
        if (hasSelection) {
            supportActionBar?.title = getString(R.string.selected_count, if (currentMode == Mode.USERS) selectedUsernames.size else selectedChatIds.size)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)
            findViewById<MaterialToolbar>(R.id.toolbar).navigationIcon?.setTint(getColorOnPrimary())
        } else {
            supportActionBar?.title = getString(R.string.super_admin)
            supportActionBar?.setHomeAsUpIndicator(null)
        }
        invalidateOptionsMenu()

        lifecycleScope.launch(Dispatchers.Default) {
            val sortedUsers = users.sortedByDescending { it.lastMessageTime?.seconds ?: 0 }
            val sortedChats = chats
            withContext(Dispatchers.Main) {
                if (currentMode == Mode.USERS) {
                    emptyStateText.isVisible = users.isEmpty()
                    adapter.setAdminItems(sortedUsers)
                } else {
                    emptyStateText.isVisible = chats.isEmpty()
                    adapter.setItems(sortedChats)
                }
            }
        }
    }

    private fun loadMoreUsers() {
        if (!hasMore || currentCursor.isEmpty()) return
        grpcClient.getAdminUserList("", currentCursor, 50, "last_message") { response ->
            adminUsers = adminUsers + response.users
            currentCursor = response.nextCursor
            hasMore = response.hasMore
            Log.d("SuperAdminActivity", "Loaded ${response.users.size} more admin users, total=${adminUsers.size}")
            runOnUiThread {
                updateAdminUI(adminUsers, allChats)
            }
        }
    }

    private fun filterCurrentList(query: String) {
        val q = query.lowercase()
        if (currentMode == Mode.USERS) {
            val filtered = adminUsers.filter { it.username.lowercase().contains(q) }
            updateAdminUI(filtered, emptyList())
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
        adapter.toggleSelection(username)
        if (currentMode == Mode.USERS) {
            updateAdminUI(adminUsers, allChats)
        } else {
            updateUI(allUsers, allChats)
        }
    }

    private fun toggleChatSelection(chatId: String) {
        if (selectedChatIds.contains(chatId)) {
            selectedChatIds.remove(chatId)
        } else {
            selectedChatIds.add(chatId)
        }
        adapter.toggleSelection(chatId)
        if (currentMode == Mode.USERS) {
            updateAdminUI(adminUsers, allChats)
        } else {
            updateUI(allUsers, allChats)
        }
    }

    private fun clearSelection() {
        selectedUsernames.clear()
        selectedChatIds.clear()
        adapter.clearSelection()
        if (currentMode == Mode.USERS) {
            updateAdminUI(adminUsers, allChats)
        } else {
            updateUI(allUsers, allChats)
        }
    }

    private fun applyThemeToTabs(tabLayout: TabLayout) {
        val theme = ThemeStore.currentTheme()
        try {
            val pColor = theme.primaryColor.toColorInt()
            
            tabLayout.setSelectedTabIndicatorColor(pColor)
            tabLayout.setTabTextColors(pColor.withAlpha(150), pColor)
            tabLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            tabLayout.backgroundTintList = null
        } catch (_: Exception) {}
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return (this and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun confirmDeleteSelectedUsers() {
        val count = selectedUsernames.size
        val sheet = StandardBottomSheet(this, R.layout.dialog_delete_chats)
        sheet.setTitle(getString(R.string.delete_profile))
        
        sheet.findViewById<TextView>(R.id.tvMessageText)?.text = 
            "${getString(R.string.delete_profile)}: $count ${getString(R.string.users)}?"

        sheet.findViewById<View>(R.id.btnCancel)?.setOnClickListener { sheet.dismiss() }
        sheet.findViewById<View>(R.id.btnDelete)?.setOnClickListener {
            val usernames = selectedUsernames.toList()
            clearSelection()
            
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
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun confirmDeleteSelectedChats() {
        val count = selectedChatIds.size
        val sheet = StandardBottomSheet(this, R.layout.dialog_delete_chats)
        sheet.setTitle(getString(R.string.delete_group))

        sheet.findViewById<TextView>(R.id.tvMessageText)?.text = 
            "${getString(R.string.delete_group)}: $count ${getString(R.string.chats)}?"

        sheet.findViewById<View>(R.id.btnCancel)?.setOnClickListener { sheet.dismiss() }
        sheet.findViewById<View>(R.id.btnDelete)?.setOnClickListener {
            val chatIds = selectedChatIds.toList()
            clearSelection()
            
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
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun showAdminChangePasswordDialog(targetUser: String) {
        val sheet = StandardBottomSheet(this, R.layout.dialog_change_password)
        sheet.setTitle(getString(R.string.change_password))
        
        val editNewPw = sheet.findViewById<EditText>(R.id.editTextNewPassword)
        
        sheet.findViewById<View>(R.id.editTextOldPassword)?.parent?.parent?.let {
            if (it is View) it.visibility = View.GONE
        }
        
        sheet.findViewById<View>(R.id.btnCancel)?.setOnClickListener { sheet.dismiss() }
        sheet.findViewById<View>(R.id.btnSave)?.setOnClickListener {
            val newPw = editNewPw?.text.toString()
            if (newPw.isEmpty()) {
                Toast.makeText(this, R.string.password_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            grpcClient.adminUpdatePassword(targetUser, newPw, username) { success, message ->
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, R.string.password_updated, Toast.LENGTH_SHORT).show()
                        clearSelection()
                        if (currentMode == Mode.USERS) {
                            updateAdminUI(adminUsers, allChats)
                        } else {
                            updateUI(allUsers, allChats)
                        }
                        sheet.dismiss()
                    } else {
                        Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        sheet.show()
    }
}
