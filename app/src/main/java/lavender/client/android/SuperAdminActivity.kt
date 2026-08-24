package lavender.client.android

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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import lavender.client.android.data.proto.AdminUserInfoProto
import lavender.client.android.data.proto.UserInfoProto
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.LogViewerActivity
import lavender.client.android.ui.admin.Mode
import lavender.client.android.ui.admin.SuperAdminViewModel
import lavender.client.android.ui.adapter.SuperAdminAdapter
import lavender.client.android.ui.widget.StandardBottomSheet
import java.util.Locale

class SuperAdminActivity : BaseActivity() {

    private lateinit var viewModel: SuperAdminViewModel
    private lateinit var username: String
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SuperAdminAdapter
    private lateinit var searchLayout: View
    private lateinit var searchEditText: EditText
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_super_admin)

        viewModel = ViewModelProvider(this)[SuperAdminViewModel::class.java]

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
                val mode = if (tab?.position == 0) Mode.USERS else Mode.GROUPS
                viewModel.setMode(mode)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        applyThemeToTabs(tabLayout)

        swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadData()
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.filterCurrentList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        observeViewModel()
        viewModel.loadData()
    }

    private fun setupRecyclerView() {
        adapter = SuperAdminAdapter(
            onUserClick = { user ->
                val adminUser = user as? AdminUserInfoProto ?: return@SuperAdminAdapter
                if (viewModel.uiState.value.selectedUsernames.isNotEmpty()) {
                    viewModel.toggleUserSelection(adminUser.username)
                } else {
                    viewModel.loadUserSessions(adminUser)
                }
            },
            onUserLongClick = { user ->
                val username = when (user) {
                    is UserInfoProto -> user.username
                    is AdminUserInfoProto -> user.username
                    else -> return@SuperAdminAdapter
                }
                viewModel.toggleUserSelection(username)
            },
            onChatClick = { chat ->
                if (viewModel.uiState.value.selectedChatIds.isNotEmpty()) {
                    viewModel.toggleChatSelection(chat.id)
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
                viewModel.toggleChatSelection(chat.id)
            },
            onlineUsers = lavender.client.android.data.grpc.GrpcClient.users.value.toSet()
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0 && viewModel.uiState.value.currentMode == Mode.USERS) {
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    val totalItems = layoutManager.itemCount
                    if (lastVisible >= totalItems - 5 && viewModel.uiState.value.hasMore && viewModel.uiState.value.currentCursor.isNotEmpty()) {
                        viewModel.loadMoreUsers()
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

        menu.findItem(R.id.action_show_users)?.isVisible = false
        menu.findItem(R.id.action_show_groups)?.isVisible = false

        val hasSelection = viewModel.uiState.value.selectedUsernames.isNotEmpty() || viewModel.uiState.value.selectedChatIds.isNotEmpty()
        if (hasSelection) {
            menu.clear()

            if (viewModel.uiState.value.currentMode == Mode.USERS && viewModel.uiState.value.selectedUsernames.size == 1) {
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
                if (viewModel.uiState.value.selectedUsernames.isNotEmpty() || viewModel.uiState.value.selectedChatIds.isNotEmpty()) {
                    viewModel.clearSelection()
                } else {
                    finish()
                }
                return true
            }
            R.id.action_change_password -> {
                if (viewModel.uiState.value.selectedUsernames.size == 1) {
                    showAdminChangePasswordDialog(viewModel.uiState.value.selectedUsernames.first())
                }
                return true
            }
            R.id.action_delete -> {
                if (viewModel.uiState.value.currentMode == Mode.USERS) confirmDeleteSelectedUsers()
                else confirmDeleteSelectedChats()
                return true
            }
            R.id.action_search -> {
                searchLayout.isVisible = !searchLayout.isVisible
                if (searchLayout.isVisible) searchEditText.requestFocus()
                else {
                    searchEditText.text.clear()
                    viewModel.filterCurrentList("")
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

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                swipeRefreshLayout.isRefreshing = state.isLoading

                val emptyStateText = findViewById<TextView>(R.id.emptyStateText)
                val theme = ThemeStore.currentTheme()
                val textSecondary = try { theme.textSecondaryColor.toColorInt() } catch (_: Exception) { android.graphics.Color.LTGRAY }
                emptyStateText.setTextColor(textSecondary)

                val hasSelection = state.selectedUsernames.isNotEmpty() || state.selectedChatIds.isNotEmpty()
                if (hasSelection) {
                    supportActionBar?.title = getString(R.string.selected_count, if (state.currentMode == Mode.USERS) state.selectedUsernames.size else state.selectedChatIds.size)
                    supportActionBar?.setDisplayHomeAsUpEnabled(true)
                    supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close)
                    findViewById<MaterialToolbar>(R.id.toolbar).navigationIcon?.setTint(getColorOnPrimary())
                } else {
                    supportActionBar?.title = getString(R.string.super_admin)
                    supportActionBar?.setHomeAsUpIndicator(null)
                }
                invalidateOptionsMenu()

                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                    val sortedUsers = state.adminUsers.sortedByDescending { it.lastMessageTime?.seconds ?: 0 }
                    val sortedChats = state.allChats
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (state.currentMode == Mode.USERS) {
                            emptyStateText.isVisible = state.adminUsers.isEmpty()
                            adapter.setAdminItems(sortedUsers)
                        } else {
                            emptyStateText.isVisible = state.allChats.isEmpty()
                            adapter.setItems(sortedChats)
                        }
                    }
                }

                state.successMessage?.let { message ->
                    Toast.makeText(this@SuperAdminActivity, message, Toast.LENGTH_SHORT).show()
                    viewModel.clearSuccess()
                }

                state.error?.let { error ->
                    Toast.makeText(this@SuperAdminActivity, getString(R.string.error_colon, error), Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }
    }

    private fun getColorOnPrimary(): Int {
        val theme = ThemeStore.currentTheme()
        return lavender.client.android.theme.ThemeUtils.parseSafeColor(theme.onPrimaryColor, android.graphics.Color.WHITE)
    }

    private fun applyThemeToTabs(tabLayout: TabLayout) {
        val theme = ThemeStore.currentTheme()
        try {
            val pColor = theme.primaryColor.toColorInt()

            tabLayout.setSelectedTabIndicatorColor(pColor)
            tabLayout.setTabTextColors(pColor.withAlpha(150), pColor)
            tabLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            tabLayout.backgroundTintList = null
        } catch (e: Exception) { Log.w(TAG, "Caught: " + e.message) }
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return (this and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun confirmDeleteSelectedUsers() {
        val count = viewModel.uiState.value.selectedUsernames.size
        val sheet = StandardBottomSheet(this, R.layout.dialog_delete_chats)
        sheet.setTitle(getString(R.string.delete_profile))

        sheet.findViewById<TextView>(R.id.tvMessageText)?.text =
            getString(R.string.delete_confirmation_users, getString(R.string.delete_profile), count, getString(R.string.users))

        sheet.findViewById<View>(R.id.btnCancel)?.setOnClickListener { sheet.dismiss() }
        sheet.findViewById<View>(R.id.btnDelete)?.setOnClickListener {
            viewModel.deleteSelectedUsers()
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun confirmDeleteSelectedChats() {
        val count = viewModel.uiState.value.selectedChatIds.size
        val sheet = StandardBottomSheet(this, R.layout.dialog_delete_chats)
        sheet.setTitle(getString(R.string.delete_group))

        sheet.findViewById<TextView>(R.id.tvMessageText)?.text =
            getString(R.string.delete_confirmation_chats, getString(R.string.delete_group), count, getString(R.string.chats))

        sheet.findViewById<View>(R.id.btnCancel)?.setOnClickListener { sheet.dismiss() }
        sheet.findViewById<View>(R.id.btnDelete)?.setOnClickListener {
            viewModel.deleteSelectedChats()
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

            viewModel.changePassword(targetUser, newPw)
            sheet.dismiss()
        }
        sheet.show()
    }

    companion object {
        private const val TAG = "SuperAdminActivity"
    }
}
