package msg.client.android

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.EditText
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import msg.client.android.R
import msg.client.android.data.grpc.GrpcClient
import msg.client.android.data.models.ChatInfo
import msg.client.android.ui.adapter.ChatAdapter
import java.util.Locale

class ChatListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private val grpcClient = GrpcClient()
    private var username: String = ""
    private var password: String = ""
    private var colorSchemeMenuItem: MenuItem? = null

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
        adapter = ChatAdapter { chat ->
            openChat(chat.id)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Observe system notifications for auth failures
        lifecycleScope.launch {
            grpcClient.systemNotification.collect { notification ->
                notification?.let {
                    when (it) {
                        "auth_failed" -> {
                            runOnUiThread {
                                Toast.makeText(this@ChatListActivity, getString(R.string.auth_failed), Toast.LENGTH_LONG).show()
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
                                Toast.makeText(this@ChatListActivity, getString(R.string.auth_failed), Toast.LENGTH_LONG).show()
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
                runOnUiThread {
                    if (chats.isEmpty()) {
                        // Если нет чатов, открываем general чат
                        openChat("general")
                    } else {
                        // Если есть чаты, показываем список
                        adapter.setChats(chats)
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
                    Toast.makeText(this@ChatListActivity, getString(R.string.no_users_available), Toast.LENGTH_SHORT).show()
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

                    val isOnline = onlineUsers.contains(user)
                    statusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(
                        if (isOnline) getColor(android.R.color.holo_green_dark)
                        else getColor(android.R.color.darker_gray)
                    )

                    usernameText.text = user
                    container.addView(userView)
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
            Toast.makeText(this, getString(R.string.cannot_chat_with_yourself), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            grpcClient.createDirectChat(username, targetUser) { chatId ->
                if (chatId != null) {
                    runOnUiThread {
                        openChat(chatId)
                        Toast.makeText(this@ChatListActivity, getString(R.string.chat_created_with, targetUser), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@ChatListActivity, getString(R.string.failed_to_create_chat), Toast.LENGTH_SHORT).show()
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

        // Pre-fill current username
        editTextUsername.setText(username)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnChangeUsername.setOnClickListener {
            val newUsername = editTextUsername.text.toString().trim()
            if (newUsername.isNotEmpty() && newUsername != username) {
                grpcClient.updateUsername(username, newUsername) { success, message ->
                    runOnUiThread {
                        if (success) {
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                            username = newUsername
                            dialog.dismiss()
                        } else {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, getString(R.string.username_empty), Toast.LENGTH_SHORT).show()
            }
        }

        btnChangePassword.setOnClickListener {
            val oldPassword = editTextOldPassword.text.toString().trim()
            val newPassword = editTextNewPassword.text.toString().trim()
            if (oldPassword.isNotEmpty() && newPassword.isNotEmpty()) {
                grpcClient.updatePassword(username, oldPassword, newPassword) { success, message ->
                    runOnUiThread {
                        if (success) {
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                            password = newPassword
                            editTextOldPassword.text.clear()
                            editTextNewPassword.text.clear()
                        } else {
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Please enter both old and new password", Toast.LENGTH_SHORT).show()
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
}
