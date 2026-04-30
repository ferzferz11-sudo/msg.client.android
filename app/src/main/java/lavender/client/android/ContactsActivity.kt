package lavender.client.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.databinding.ActivityContactsBinding
import lavender.client.android.ui.adapter.UserAdapter
import androidx.core.graphics.toColorInt
import java.util.*

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private val grpcClient = GrpcClient
    private lateinit var adapter: UserAdapter
    private var username: String = ""
    private var password: String = ""
    private var contacts = mutableListOf<String>()

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
        applySavedColorScheme()
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        username = intent.getStringExtra("username") ?: ""
        password = intent.getStringExtra("password") ?: ""

        lavender.client.android.ui.ThemeManager.loadTheme(this, username) {
            runOnUiThread {
                lavender.client.android.ui.ThemeManager.applyTheme(this)
            }
        }

        // Handle window insets for edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupRecyclerView()
        loadContacts()

        binding.addContactFab.setOnClickListener {
            showAddContactDialog()
        }

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                val filtered = if (query.isEmpty()) contacts else contacts.filter { it.lowercase().contains(query) }
                adapter.setUsers(filtered)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val searchItem = menu.add(0, 1, 0, R.string.search)
            .setIcon(R.drawable.ic_search_custom)
            .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        
        // Get colors from custom theme or Material Design attributes
        val customTheme = lavender.client.android.ui.ThemeManager.getCurrentTheme()
        val iconColor = if (customTheme != null) {
            try {
                customTheme.onPrimaryColor.toColorInt()
            } catch (_: Exception) {
                val typedValue = android.util.TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
                typedValue.data
            }
        } else {
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
            typedValue.data
        }
        
        // Tint the search icon
        searchItem.icon?.setTint(iconColor)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 1) {
            binding.searchLayout.isVisible = !binding.searchLayout.isVisible
            if (binding.searchLayout.isVisible) {
                binding.searchEditText.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.searchEditText, 0)
            } else {
                binding.searchEditText.text?.clear()
                adapter.setUsers(contacts)
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = false
        loadContacts()
    }

    override fun onPause() {
        super.onPause()
        lavender.client.android.data.grpc.RealGrpcClient.isAppInBackground = true
    }

    private fun setupRecyclerView() {
        adapter = UserAdapter(
            onUserClick = { selectedUser ->
                showContactOptions(selectedUser)
            },
            avatarCache = grpcClient.getAvatarCache()
        )
        binding.contactsRecyclerView.adapter = adapter
        binding.contactsRecyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            grpcClient.users.collect { onlineUsers ->
                runOnUiThread { adapter.setOnlineUsers(onlineUsers) }
            }
        }
    }

    private fun loadContacts() {
        grpcClient.getContacts(username) { list ->
            contacts = list.toMutableList()
            runOnUiThread {
                adapter.setUsers(contacts)
                binding.emptyStateContainer.isVisible = contacts.isEmpty()
            }
        }
    }

    private fun showContactOptions(contactUsername: String) {
        AlertDialog.Builder(this)
            .setTitle(contactUsername)
            .setItems(arrayOf(getString(R.string.create_private_chat), getString(R.string.remove_contact))) { _, which ->
                when (which) {
                    0 -> startDirectChat(contactUsername)
                    1 -> confirmRemoveContact(contactUsername)
                }
            }
            .show()
    }

    private fun confirmRemoveContact(contactUsername: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.remove_contact)
            .setMessage(getString(R.string.remove_contact_confirm, contactUsername))
            .setPositiveButton(R.string.delete) { _, _ ->
                grpcClient.removeContact(username, contactUsername) { success, message ->
                    runOnUiThread {
                        if (success) {
                            contacts.remove(contactUsername)
                            adapter.setUsers(contacts)
                            binding.emptyStateContainer.isVisible = contacts.isEmpty()
                        } else {
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel_dialog, null)
            .show()
    }

    private fun showAddContactDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_contact, null)
        val searchEditText = dialogView.findViewById<TextInputEditText>(R.id.searchEditText)
        val usersRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.usersRecyclerView)
        val createChatCheckbox = dialogView.findViewById<MaterialCheckBox>(R.id.createChatCheckbox)
        val btnAdd = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAdd)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)

        val allUsers = mutableListOf<String>()
        val filteredUsers = mutableListOf<String>()
        val userAdapter = UserAdapter(
            onUserClick = { selected ->
                btnAdd.isEnabled = selected != username && !contacts.contains(selected)
            },
            avatarCache = grpcClient.getAvatarCache()
        )

        lifecycleScope.launch {
            grpcClient.users.collect { onlineUsers ->
                runOnUiThread { userAdapter.setOnlineUsers(onlineUsers) }
            }
        }

        usersRecyclerView.adapter = userAdapter
        usersRecyclerView.layoutManager = LinearLayoutManager(this)

        grpcClient.loadAllUsers()
        lifecycleScope.launch {
            // Give a bit of time for users to load
            kotlinx.coroutines.delay(500)
            allUsers.clear()
            allUsers.addAll(grpcClient.allUsers.value.filter { it != username && !contacts.contains(it) })
            filteredUsers.clear()
            filteredUsers.addAll(allUsers)
            runOnUiThread { userAdapter.setUsers(filteredUsers) }
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase()
                filteredUsers.clear()
                filteredUsers.addAll(allUsers.filter { it.lowercase().contains(query) && !contacts.contains(it) })
                userAdapter.setUsers(filteredUsers)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnAdd.setOnClickListener {
            val selected = userAdapter.getSelectedUser() ?: return@setOnClickListener
            grpcClient.addContact(username, selected) { success, message ->
                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, R.string.contact_added, Toast.LENGTH_SHORT).show()
                        loadContacts()
                        if (createChatCheckbox.isChecked) {
                            startDirectChat(selected)
                        }
                        dialog.dismiss()
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun startDirectChat(targetUser: String) {
        grpcClient.createDirectChat(username, targetUser) { chatId ->
            if (chatId != null) {
                runOnUiThread {
                    val intent = Intent(this, NewChatActivity::class.java)
                        .putExtra("USERNAME", username)
                        .putExtra("PASSWORD", password)
                        .putExtra("ROOM_ID", chatId)
                        .putExtra("CHAT_NAME", getString(R.string.private_chat_with, targetUser))
                        .putExtra("IS_DIRECT", true)
                        .putExtra("PARTICIPANTS", "[\"$username\", \"$targetUser\"]")
                    startActivity(intent)
                }
            }
        }
    }

    private fun applySavedColorScheme() {
        setTheme(R.style.Theme_Lavender_Dark_NoActionBar)
    }

    private fun getSavedColorScheme(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("color_scheme", null)
    }
}
