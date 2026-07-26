package lavender.client.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.RealGrpcClient
import lavender.client.android.databinding.ActivityContactsBinding
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeUi
import lavender.client.android.ui.contacts.ContactsViewModel
import lavender.client.android.ui.widget.SearchableListBottomSheet
import lavender.client.android.ui.adapter.UserAdapter
import java.util.Locale

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private lateinit var viewModel: ContactsViewModel
    private lateinit var adapter: UserAdapter
    private var username: String = ""
    private var password: String = ""

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

        viewModel = ViewModelProvider(this)[ContactsViewModel::class.java]

        username = intent.getStringExtra("USERNAME") ?: ""
        password = intent.getStringExtra("PASSWORD") ?: ""

        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeUi.bind(this, username)
        setupUI()
        setupObservers()
    }

    private fun setupUI() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.contactsRecyclerView.updatePadding(bottom = systemBars.bottom + (80 * resources.displayMetrics.density).toInt())
            insets
        }

        binding.toolbar.setNavigationIcon(R.drawable.ic_back_arrow)
        binding.toolbar.navigationIcon?.setTint(getColorOnPrimary())

        binding.toolbar.setNavigationOnClickListener {
            if (binding.searchCard.isVisible) {
                hideSearchBar()
            } else if (adapter.getSelectedUsers().isNotEmpty()) {
                adapter.clearSelection()
            } else {
                finish()
            }
        }

        setupRecyclerView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.searchCard.isVisible -> hideSearchBar()
                    adapter.getSelectedUsers().isNotEmpty() -> adapter.clearSelection()
                    else -> finish()
                }
            }
        })

        binding.addContactFab.setOnClickListener { showAddContactDialog() }
        binding.actionSearch.setOnClickListener { showSearchBar() }
        binding.actionCreateChat.setOnClickListener { createChatFromSelection() }
        binding.actionDelete.setOnClickListener { confirmRemoveSelectedContacts() }

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.emptyStateContainer.isVisible = state.contacts.isEmpty() && !state.isLoading
                    state.chatCreated?.let { event ->
                        viewModel.consumeChatCreatedEvent()
                        navigateToChat(event)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.onlineUsers.collect { onlineUsers ->
                    adapter.setOnlineUsers(onlineUsers)
                }
            }
        }
    }

    private fun getColorOnPrimary(): Int {
        val theme = ThemeStore.currentTheme()
        return ThemeUtils.parseSafeColor(theme.onPrimaryColor, android.graphics.Color.WHITE)
    }

    private fun setBackIcon(isClose: Boolean) {
        val iconRes = if (isClose) R.drawable.ic_close else R.drawable.ic_back_arrow
        binding.toolbar.navigationIcon = androidx.core.content.ContextCompat.getDrawable(this, iconRes)?.apply {
            setTint(getColorOnPrimary())
        }
    }

    private fun showSearchBar() {
        binding.searchCard.isVisible = true
        binding.searchEditText.requestFocus()
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.searchEditText, 0)
        setBackIcon(true)
        binding.actionSearch.isVisible = false
    }

    private fun hideSearchBar() {
        binding.searchCard.isVisible = false
        binding.searchEditText.text?.clear()
        adapter.filter("")
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)

        val hasSelection = adapter.getSelectedUsers().isNotEmpty()
        setBackIcon(hasSelection)
        binding.actionSearch.isVisible = !hasSelection
    }

    override fun onResume() {
        super.onResume()
        RealGrpcClient.isAppInBackground = false
        viewModel.loadContacts(username)
    }

    override fun onPause() {
        super.onPause()
        RealGrpcClient.isAppInBackground = true
    }

    private fun setupRecyclerView() {
        adapter = UserAdapter(
            lifecycleScope,
            onUserClick = { openProfile(it) },
            onUserLongClick = { adapter.toggleSelection(it) },
            onSelectionChanged = { count ->
                val hasSelection = count > 0
                binding.toolbarTitle.text = if (hasSelection) getString(R.string.selected_count, count) else getString(R.string.contacts)
                setBackIcon(hasSelection || binding.searchCard.isVisible)
                binding.actionCreateChat.isVisible = hasSelection
                binding.actionDelete.isVisible = hasSelection
                binding.actionSearch.isVisible = !hasSelection && !binding.searchCard.isVisible
            },
            avatarCache = viewModel.getAvatarCache(),
            onlineUsers = viewModel.onlineUsers.value
        )
        binding.contactsRecyclerView.adapter = adapter
        binding.contactsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun openProfile(targetUser: String) {
        startActivity(Intent(this, ProfileActivity::class.java).apply {
            putExtra("username", targetUser)
            putExtra("is_group", false)
        })
    }

    private fun createChatFromSelection() {
        val selected = adapter.getSelectedUsers()
        if (selected.isEmpty()) return
        if (selected.size == 1) {
            viewModel.createDirectChat(username, selected.first())
        } else {
            viewModel.createGroupChat(getString(R.string.default_group_name), selected + username, username)
        }
        adapter.clearSelection()
    }

    private fun confirmRemoveSelectedContacts() {
        val selected = adapter.getSelectedUsers()
        if (selected.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle(R.string.remove_contact)
            .setMessage(getString(R.string.remove_multiple_contacts_confirm, selected.size))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.removeContacts(username, selected) {
                    lifecycleScope.launch { viewModel.loadContacts(username) }
                }
                adapter.clearSelection()
            }
            .setNegativeButton(R.string.cancel_dialog, null)
            .show()
    }

    private fun showAddContactDialog() {
        val sheet = SearchableListBottomSheet(this)
            .setTitle(getString(R.string.add_contact))
            .setActionButtonText(getString(R.string.add))
            .setExtraInputVisible(false)
            .setLoading(true)
            .setCreateChatCheckboxVisible(true, getString(R.string.create_chat_after))

        val userAdapter = UserAdapter(
            lifecycleScope,
            onUserClick = { selected ->
                (sheet.recyclerView?.adapter as? UserAdapter)?.toggleSelection(selected)
            },
            onSelectionChanged = { count ->
                sheet.setActionButtonEnabled(count > 0)
                sheet.setActionButtonText(if (count > 0) "${getString(R.string.add)} ($count)" else getString(R.string.add))
            },
            avatarCache = viewModel.getAvatarCache(),
            onlineUsers = viewModel.onlineUsers.value
        )

        sheet.setAdapter(userAdapter)
        viewModel.loadAllUsers()

        val contacts = viewModel.uiState.value.contacts
        val usersJob = lifecycleScope.launch {
            viewModel.observeAllUsers { allUsers ->
                val filtered = allUsers
                    .filter { it.username != username && !contacts.contains(it.username) }
                    .map { it.username }
                sheet.setLoading(false)
                userAdapter.setUsers(filtered)
            }
        }

        sheet.setOnDismissListener { usersJob.cancel() }
        sheet.onSearchTextChanged { query -> userAdapter.filter(query) }

        sheet.onActionClick {
            val selected = userAdapter.getSelectedUsers()
            if (selected.isNotEmpty()) {
                val createChat = sheet.isCreateChatChecked()
                var completed = 0
                val total = selected.size
                selected.forEach { contact ->
                    viewModel.addContact(username, contact) { success ->
                        completed++
                        if (completed == total) {
                            lifecycleScope.launch {
                                Toast.makeText(this@ContactsActivity, getString(R.string.contacts_added, selected.size), Toast.LENGTH_SHORT).show()
                                sheet.dismiss()
                                if (createChat) {
                                    if (selected.size == 1) {
                                        viewModel.createDirectChat(username, selected.first())
                                    } else {
                                        viewModel.createGroupChat(getString(R.string.default_group_name), selected + username, username)
                                    }
                                } else {
                                    viewModel.loadContacts(username)
                                }
                            }
                        }
                    }
                }
            }
        }
        sheet.show()
    }

    private fun navigateToChat(event: lavender.client.android.ui.contacts.ChatCreatedEvent) {
        val intent = Intent(this, NewChatActivity::class.java).apply {
            putExtra("USERNAME", username)
            putExtra("ROOM_ID", event.chatId)
            putExtra("CHAT_NAME", event.chatName)
            putExtra("IS_DIRECT", event.isDirect)
            putExtra("PARTICIPANTS", event.participants)
            if (event.creator.isNotEmpty()) putExtra("CREATOR", event.creator)
        }
        startActivity(intent)
    }
}
