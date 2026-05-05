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
import org.json.JSONArray
import java.util.*

class ContactsActivity : AppCompatActivity() {

    private var binding: ActivityContactsBinding? = null
    private val grpcClient = GrpcClient
    private lateinit var adapter: UserAdapter
    private var username: String = ""
    private var password: String = ""
    private var contacts = mutableListOf<String>()

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
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

        username = intent.getStringExtra("username") ?: ""
        password = intent.getStringExtra("password") ?: ""

        lavender.client.android.ui.ThemeManager.loadTheme(this, username) {
            runOnUiThread {
                binding = ActivityContactsBinding.inflate(layoutInflater)
                setContentView(binding!!.root)
                lavender.client.android.ui.ThemeManager.applyTheme(this)
                setupUI()
            }
        }
    }

    private fun setupUI() {
        binding?.let { b ->
            ViewCompat.setOnApplyWindowInsetsListener(b.toolbar) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(top = systemBars.top)
                insets
            }

            ViewCompat.setOnApplyWindowInsetsListener(b.root) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.updatePadding(bottom = systemBars.bottom)
                insets
            }

            setSupportActionBar(b.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            b.toolbar.setNavigationOnClickListener {
                if (adapter.getSelectedUsers().isNotEmpty()) {
                    adapter.clearSelection()
                } else {
                    finish()
                }
            }

            setupRecyclerView()
            loadContacts()

            b.addContactFab.setOnClickListener {
                showAddContactDialog()
            }

            b.searchEditText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s.toString().lowercase()
                    val filtered = if (query.isEmpty()) contacts else contacts.filter { it.lowercase().contains(query) }
                    adapter.setUsers(filtered)
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.contacts_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val hasSelection = adapter.getSelectedUsers().isNotEmpty()
        menu.findItem(R.id.action_create_chat).isVisible = hasSelection
        menu.findItem(R.id.action_delete_contact).isVisible = hasSelection
        menu.findItem(R.id.action_search).isVisible = !hasSelection
        
        val iconColor = lavender.client.android.ui.ThemeManager.getCurrentTheme()?.onPrimaryColor?.toColorInt() ?: android.graphics.Color.WHITE
        menu.findItem(R.id.action_create_chat).icon?.setTint(iconColor)
        menu.findItem(R.id.action_delete_contact).icon?.setTint(iconColor)
        menu.findItem(R.id.action_search).icon?.setTint(iconColor)
        
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> {
                binding?.let { b ->
                    b.searchLayout.isVisible = !b.searchLayout.isVisible
                    if (b.searchLayout.isVisible) {
                        b.searchEditText.requestFocus()
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(b.searchEditText, 0)
                    } else {
                        b.searchEditText.text?.clear()
                        adapter.setUsers(contacts)
                    }
                }
                return true
            }
            R.id.action_create_chat -> {
                createChatFromSelection()
                return true
            }
            R.id.action_delete_contact -> {
                confirmRemoveSelectedContacts()
                return true
            }
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
                if (adapter.getSelectedUsers().isNotEmpty()) {
                    adapter.toggleSelection(selectedUser)
                }
            },
            onUserLongClick = { selectedUser ->
                adapter.toggleSelection(selectedUser)
            },
            onSelectionChanged = { count ->
                invalidateOptionsMenu()
                supportActionBar?.title = if (count > 0) getString(R.string.selected_count, count) else getString(R.string.contacts)
                supportActionBar?.setHomeAsUpIndicator(if (count > 0) R.drawable.ic_close else R.drawable.ic_back_arrow)
            },
            avatarCache = grpcClient.getAvatarCache()
        )
        binding?.contactsRecyclerView?.adapter = adapter
        binding?.contactsRecyclerView?.layoutManager = LinearLayoutManager(this)

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
                binding?.emptyStateContainer?.isVisible = contacts.isEmpty()
            }
        }
    }

    private fun createChatFromSelection() {
        val selected = adapter.getSelectedUsers()
        if (selected.isEmpty()) return

        if (selected.size == 1) {
            startDirectChat(selected.first())
        } else {
            createGroupChat(getString(R.string.default_group_name), selected + username)
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
                selected.forEach { contact ->
                    grpcClient.removeContact(username, contact) { _, _ -> }
                }
                loadContacts()
                adapter.clearSelection()
            }
            .setNegativeButton(R.string.cancel_dialog, null)
            .show()
    }

    private fun showAddContactDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_contact, null)
        val searchEditText = dialogView.findViewById<TextInputEditText>(R.id.searchEditText)
        val searchInputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.searchInputLayout)
        val usersRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.usersRecyclerView)
        val createChatCheckbox = dialogView.findViewById<MaterialCheckBox>(R.id.createChatCheckbox)
        val btnAdd = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnAdd)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)

        val customTheme = lavender.client.android.ui.ThemeManager.getCurrentTheme()
        val typedValue = android.util.TypedValue()
        val bgColor = if (customTheme != null) {
            try { customTheme.primaryColor.toColorInt() } catch (_: Exception) {
                theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
                typedValue.data
            }
        } else {
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
            typedValue.data
        }

        val shapeDrawable = android.graphics.drawable.ShapeDrawable(
            android.graphics.drawable.shapes.RoundRectShape(floatArrayOf(28f, 28f, 28f, 28f, 28f, 28f, 28f, 28f), null, null)
        )
        shapeDrawable.paint.color = bgColor
        dialogView.background = shapeDrawable

        if (customTheme == null) {
            val boxColor = android.content.res.ColorStateList.valueOf(bgColor)
            searchInputLayout.setBoxStrokeColorStateList(boxColor)
            searchInputLayout.defaultHintTextColor = boxColor
        }

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

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        if (customTheme == null) {
            val btnBgColor = android.content.res.ColorStateList.valueOf(
                theme.resolveAttribute(com.google.android.material.R.attr.colorSecondaryContainer, typedValue, true).let { typedValue.data }
            )
            val btnTextColor = theme.resolveAttribute(com.google.android.material.R.attr.colorOnSecondaryContainer, typedValue, true).let { typedValue.data }
            btnCancel.backgroundTintList = btnBgColor
            btnCancel.setTextColor(btnTextColor)
            btnAdd.backgroundTintList = btnBgColor
            btnAdd.setTextColor(btnTextColor)
        }

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

    private fun createGroupChat(name: String, participants: List<String>) {
        grpcClient.createGroupChat(name, participants, username) { chatId ->
            if (chatId != null) {
                runOnUiThread {
                    val intent = Intent(this, NewChatActivity::class.java)
                        .putExtra("USERNAME", username)
                        .putExtra("PASSWORD", password)
                        .putExtra("ROOM_ID", chatId)
                        .putExtra("CHAT_NAME", name)
                        .putExtra("IS_DIRECT", false)
                        .putExtra("PARTICIPANTS", JSONArray(participants).toString())
                        .putExtra("CREATOR", username)
                    startActivity(intent)
                }
            }
        }
    }
}