package lavender.client.android

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ActivityInfo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.data.grpc.ServerConnectivityTest
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    private fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        val toast = Toast.makeText(this, message, duration)
        toast.setGravity(android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL, 0, 100)
        toast.show()
    }

    private fun isDarkTheme(): Boolean {
        return true
    }

    companion object {
        private const val APK_URL = "http://159.195.38.145:8081/lavender.apk"
        private const val VERSION_CHECK_URL = "http://159.195.38.145:8081/version.txt"
    }

    private lateinit var joinChatButton: Button
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var downloadProgressText: TextView
    private lateinit var updateAvailableIndicator: ImageView
    private lateinit var languageButton: Button
    private lateinit var logoutButton: Button
    private lateinit var copyLinkButton: ImageButton
    private lateinit var shareLinkButton: ImageButton
    private lateinit var downloadUpdateButton: Button
    private var currentLanguage: String? = null
    private var updateCheckJob: Job? = null
    private var connectivityJob: Job? = null

    private val serverList = listOf(
        "159.195.38.145:50051",
        "192.168.1.135:50051"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedColorScheme()
        applySavedLanguage()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Для портретного режима
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        // Handle window insets to avoid overlapping with status bar
        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }

        currentLanguage = getSavedLanguage()
        updateLanguageButtonText()

        updateAvailableIndicator = findViewById(R.id.updateAvailableIndicator)
        downloadProgressBar = findViewById(R.id.downloadProgressBar)
        downloadProgressText = findViewById(R.id.downloadProgressText)
        joinChatButton = findViewById(R.id.joinChatButton)
        languageButton = findViewById(R.id.languageButton)
        logoutButton = findViewById(R.id.logoutButton)
        copyLinkButton = findViewById(R.id.copyLinkButton)
        shareLinkButton = findViewById(R.id.shareLinkButton)
        downloadUpdateButton = findViewById(R.id.downloadUpdateButton)

        setupJoinChatButton()
        setupLogoutButton()
        setupLanguageButton()
        setupDownloadUpdateButton()

        // Auto-navigate if credentials are saved
        val savedUsername = getSavedUsername()
        val savedPassword = getSavedPassword()
        val savedServerAddress = getSavedServerAddress()

        // Check if coming from notification
        val fromNotification = intent.getBooleanExtra("from_notification", false)
        val skipAutoLogin = intent.getBooleanExtra("extra_skip_autologin", false)
        val notificationRoomId = intent.getStringExtra("room_id")

        if (!skipAutoLogin && savedUsername != null && savedPassword != null && savedServerAddress != null) {
            // Credentials exist - navigate to appropriate screen
            if (fromNotification && !notificationRoomId.isNullOrEmpty()) {
                // Open the specific chat from notification
                navigateToChat(savedUsername, savedPassword, savedServerAddress, notificationRoomId)
            } else {
                // Navigate to chat list
                navigateToChatList(savedUsername, savedPassword, savedServerAddress)
            }
            // Still check for updates in background
            checkForUpdates()
        } else {
            // No credentials or explicit skip
            checkForUpdates()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // Handle notification click when app is already running
        val fromNotification = intent.getBooleanExtra("from_notification", false)
        val notificationRoomId = intent.getStringExtra("room_id") ?: "general"
        val savedUsername = getSavedUsername()
        val savedPassword = getSavedPassword()
        val savedServerAddress = getSavedServerAddress()

        if (fromNotification && savedUsername != null && savedPassword != null && savedServerAddress != null) {
            navigateToChat(savedUsername, savedPassword, savedServerAddress, notificationRoomId)
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if language changed and recreate activity if needed
        val savedLanguage = getSavedLanguage()
        if (savedLanguage != currentLanguage) {
            currentLanguage = savedLanguage
            applySavedLanguage()
            
            // Safer way to recreate activity on some Android 14 devices (Xiaomi/MIUI)
            // to avoid ClassCastException in ClientTransaction.
            lifecycleScope.launch {
                delay(10)
                recreate()
            }
        } else {
            updateLanguageButtonText()
        }

        // Only start periodic update check if not navigating away immediately
        // Don't start if coming from notification (will navigate to chat)
        val fromNotification = intent.getBooleanExtra("from_notification", false)
        if (!fromNotification && !lavender.client.android.data.grpc.GrpcClient.hasCheckedForUpdates) {
            startPeriodicUpdateCheck()
            lavender.client.android.data.grpc.GrpcClient.hasCheckedForUpdates = true
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop periodic update check
        stopPeriodicUpdateCheck()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Activity will handle configuration changes without recreation
        // Download will continue in the background
    }

    // Show join chat button
    private fun setupJoinChatButton() {
        joinChatButton = findViewById(R.id.joinChatButton)
        joinChatButton.setOnClickListener {
            showUsernameDialog()
        }
    }

    // Add logout button
    private fun setupLogoutButton() {
        logoutButton.setOnClickListener {
            logout()
        }
    }

    // Add language toggle button
    private fun setupLanguageButton() {
        languageButton.setOnClickListener {
            toggleLanguage()
        }
    }

    // Add download update button
    private fun setupDownloadUpdateButton() {
        downloadUpdateButton.setOnClickListener {
            try {
                downloadAndInstallApk()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Update button click error: ${e.message}", e)
                showToast("Error starting download: ${e.message}", Toast.LENGTH_LONG)
            }
        }

        // Update version text from BuildConfig
        val appVersionText: TextView = findViewById(R.id.appVersionText)
        appVersionText.text = BuildConfig.VERSION_NAME

        copyLinkButton.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Lavender APK URL", APK_URL)
            clipboard.setPrimaryClip(clip)
            showToast(getString(R.string.copied_to_clipboard))
        }

        shareLinkButton.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_app))
            shareIntent.putExtra(Intent.EXTRA_TEXT, APK_URL)
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app)))
        }
    }

    private fun downloadAndInstallApk() {
        if (!::downloadProgressBar.isInitialized || !::downloadProgressText.isInitialized) {
            android.util.Log.e("MainActivity", "Views not initialized")
            showToast("Error: Views not initialized", Toast.LENGTH_LONG)
            return
        }

        downloadProgressBar.isVisible = true
        downloadProgressText.isVisible = true
        downloadProgressBar.progress = 0
        setButtonsEnabled(false)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val connection = URL(APK_URL).openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Server returned HTTP ${connection.responseCode}")
                }

                val fileLength = connection.contentLength
                val input = connection.inputStream
                val file = File(getExternalFilesDir(null), "lavender_update.apk")
                val output = FileOutputStream(file)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int
                while (input.read(data).also { count = it } != -1) {
                    total += count.toLong()
                    if (fileLength > 0) {
                        val progress = (total * 100 / fileLength).toInt()
                        val downloadedMb = total / (1024.0 * 1024.0)
                        val totalMb = fileLength / (1024.0 * 1024.0)
                        withContext(Dispatchers.Main) {
                            downloadProgressBar.progress = progress
                            downloadProgressText.text = String.format(Locale.US, "%.2f / %.2f MB", downloadedMb, totalMb)
                        }
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                withContext(Dispatchers.Main) {
                    downloadProgressBar.isVisible = false
                    downloadProgressText.isVisible = false
                    setButtonsEnabled(true)
                    installApk(file)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloadProgressBar.isVisible = false
                    downloadProgressText.isVisible = false
                    setButtonsEnabled(true)
                    showToast("Download error: ${e.message}", Toast.LENGTH_LONG)
                }
            }
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        if (::joinChatButton.isInitialized) joinChatButton.isEnabled = enabled
        if (::languageButton.isInitialized) languageButton.isEnabled = enabled
        if (::logoutButton.isInitialized) logoutButton.isEnabled = enabled
        if (::copyLinkButton.isInitialized) copyLinkButton.isEnabled = enabled
        if (::shareLinkButton.isInitialized) shareLinkButton.isEnabled = enabled
        if (::downloadUpdateButton.isInitialized) downloadUpdateButton.isEnabled = enabled
    }

    private fun installApk(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "$packageName.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
    
    private fun showUsernameDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_join_chat, null)

        // Set dialog background using Material Design colors
        //val typedValue = android.util.TypedValue()
        //if (isDarkTheme()) {
        //    theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
        //    dialogView.setBackgroundColor(typedValue.data)
        //}

        val titleText = dialogView.findViewById<TextView>(R.id.titleText)
        val usernameInputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.usernameInputLayout)
        val passwordInputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.passwordInputLayout)
        val editText = dialogView.findViewById<EditText>(R.id.editTextUsername)
        val editTextPassword = dialogView.findViewById<EditText>(R.id.editTextPassword)
        val serverAddressSpinner = dialogView.findViewById<Spinner>(R.id.serverAddressSpinner)
        val serverStatusIndicator = dialogView.findViewById<View>(R.id.serverStatusIndicator)
        val serverStatusText = dialogView.findViewById<TextView>(R.id.serverStatusText)
        val refreshServerButton = dialogView.findViewById<TextView>(R.id.refreshServerButton)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnJoin = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnJoin)

        // Set TextInputLayout background and button strokes in dark theme
        if (isDarkTheme()) {
            val surfaceValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, surfaceValue, true)
            usernameInputLayout.boxBackgroundColor = surfaceValue.data
            passwordInputLayout.boxBackgroundColor = surfaceValue.data

            // Add stroke to btnJoin using primary color
            val primaryValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.colorPrimary, primaryValue, true)
            btnJoin.strokeColor = android.content.res.ColorStateList.valueOf(primaryValue.data)
            btnJoin.strokeWidth = 2
        }
        
        // Set localized text
        titleText.text = getString(R.string.welcome)
        editText.hint = getString(R.string.enter_username)
        editTextPassword.hint = getString(R.string.enter_password)
        btnCancel.text = getString(R.string.cancel_dialog)
        btnJoin.text = getString(R.string.join)
        
        // Pre-fill with saved username and password
        val savedUsername = getSavedUsername()
        if (savedUsername != null) {
            editText.setText(savedUsername)
        }
        val savedPassword = getSavedPassword()
        if (savedPassword != null) {
            editTextPassword.setText(savedPassword)
        }
        
        // Setup server address spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, serverList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        serverAddressSpinner.adapter = adapter
        
        // Select saved server address
        val savedServerAddress = getSavedServerAddress()
        val savedIndex = serverList.indexOf(savedServerAddress)
        if (savedIndex >= 0) {
            serverAddressSpinner.setSelection(savedIndex)
        }
        
        // Initialize connectivity test
        val connectivityTest = ServerConnectivityTest()
        
        // Check server availability
        checkServerAvailabilityInDialog(serverAddressSpinner, serverStatusIndicator, serverStatusText, btnJoin, connectivityTest)
        
        // Recheck server when selection changes
        serverAddressSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                checkServerAvailabilityInDialog(serverAddressSpinner, serverStatusIndicator, serverStatusText, btnJoin, connectivityTest)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        
        // Refresh button
        refreshServerButton.setOnClickListener {
            checkServerAvailabilityInDialog(serverAddressSpinner, serverStatusIndicator, serverStatusText, btnJoin, connectivityTest)
        }
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnJoin.setOnClickListener {
            val username = editText.text.toString().trim()
            val password = editTextPassword.text.toString().trim()
            val serverAddress = serverAddressSpinner.selectedItem.toString()
            if (username.isNotEmpty() && password.isNotEmpty()) {
                saveUsername(username)
                savePassword(password)
                saveServerAddress(serverAddress)
                android.util.Log.d("MainActivity", "Connecting to server: $serverAddress")
                val intent = Intent(this, ChatListActivity::class.java)
                intent.putExtra("username", username)
                intent.putExtra("password", password)
                intent.putExtra("serverAddress", serverAddress)
                startActivity(intent)
                dialog.dismiss()
            } else if (username.isEmpty()) {
                showToast(getString(R.string.username_empty), Toast.LENGTH_LONG)
            } else {
                showToast(getString(R.string.password_empty), Toast.LENGTH_LONG)
            }
        }
        
        dialog.show()
        
        // Make dialog wider
        val window = dialog.window
        window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    private fun getSavedUsername(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("username", null)
    }
    
    private fun saveUsername(username: String) {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit {
            putString("username", username)
        }
    }
    
    private fun getSavedPassword(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("password", null)
    }
    
    private fun savePassword(password: String) {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit {
            putString("password", password)
        }
    }
    
    private fun getSavedServerAddress(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("server_address", null)
    }
    
    private fun saveServerAddress(address: String) {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit {
            putString("server_address", address)
        }
    }
    
    private fun logout() {
        finishAffinity()
        exitProcess(0)   // (Опционально) Завершает процесс Java VM
        /*val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit {
            remove("username")
            remove("password")
        }
        showToast(getString(R.string.logged_out))

        // Re-open via Splash to ensure clean state
        val intent = Intent(this, SplashActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra("extra_skip_autologin", true)
        startActivity(intent)
        finish()*/
    }

    private fun navigateToChatList(username: String, password: String, serverAddress: String) {
        android.util.Log.d("MainActivity", "Auto-navigating to chat list")
        val intent = Intent(this, ChatListActivity::class.java)
        intent.putExtra("username", username)
        intent.putExtra("password", password)
        intent.putExtra("serverAddress", serverAddress)
        startActivity(intent)
        finish()
    }

    private fun navigateToChat(username: String, password: String, serverAddress: String, roomId: String) {
        android.util.Log.d("MainActivity", "Auto-navigating to chat room: $roomId")
        // Need to get chat info first
        lavender.client.android.data.grpc.GrpcClient.connect(serverAddress, false, 50051, this)
        
        // Start chat session (auth)
        lavender.client.android.data.grpc.GrpcClient.startChat(username, password, "") { _ -> }

        // Use lifecycleScope to wait for auth and then get chats
        lifecycleScope.launch {
            delay(500) // Wait for auth to complete
            
            lavender.client.android.data.grpc.GrpcClient.getChats(username) { chats ->
                runOnUiThread {
                    val chat = chats.find { it.id == roomId }
                    if (chat != null) {
                        val intent = Intent(this@MainActivity, NewChatActivity::class.java)
                        intent.putExtra("USERNAME", username)
                        intent.putExtra("PASSWORD", password)
                        intent.putExtra("ROOM_ID", roomId)
                        intent.putExtra("CHAT_NAME", chat.name)
                        intent.putExtra("IS_DIRECT", chat.type == "direct")
                        intent.putExtra("PARTICIPANTS", chat.participants)
                        intent.putExtra("CREATOR", chat.creator)
                        startActivity(intent)
                        finish()
                    } else {
                        // Chat not found, navigate to chat list
                        navigateToChatList(username, password, serverAddress)
                    }
                }
            }
        }
    }
    
    private fun applySavedLanguage() {
        val savedLanguage = getSavedLanguage()
        if (savedLanguage != null) {
            setLocale(savedLanguage)
        }
    }
    
    private fun applySavedColorScheme() {
        setTheme(R.style.Theme_Lavender_Dark_NoActionBar)
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
        
        // Recreate activity to apply language change
        recreate()
    }
    
    private fun setLocale(languageCode: String) {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        
        val resources: Resources = resources
        val config: Configuration = resources.configuration
        
        config.setLocale(locale)
        // Для современной поддержки смены языка "на лету" без deprecated методов
        // обычно используется attachBaseContext, но здесь мы просто подавим варнинг 
        // или используем более современный API, если доступен.
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }
    
    private fun updateLanguageButtonText() {
        val languageButton: Button? = findViewById(R.id.languageButton)
        if (languageButton != null) {
            val currentLanguage = getSavedLanguage() ?: "en"
            val languageName = if (currentLanguage == "en") {
                getString(R.string.english)
            } else {
                getString(R.string.russian)
            }
            languageButton.text = getString(R.string.language_format, languageName)
        }
    }
    
    private fun checkServerAvailabilityInDialog(
        serverAddressSpinner: Spinner,
        serverStatusIndicator: View,
        serverStatusText: TextView,
        btnJoin: Button,
        connectivityTest: ServerConnectivityTest
    ) {
        val serverAddress = serverAddressSpinner.selectedItem.toString()
        val parts = serverAddress.split(":")
        val host = if (parts.isNotEmpty()) parts[0] else "localhost"
        val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 50051 else 50051
        
        serverStatusText.text = getString(R.string.checking_server)
        serverStatusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(android.R.color.darker_gray))
        btnJoin.isEnabled = false
        
        connectivityJob?.cancel()
        connectivityJob = lifecycleScope.launch {
            connectivityTest.testResult.collect { result ->
                if (result == null) return@collect
                
                when {
                    result.contains("SUCCESS") -> {
                        serverStatusText.text = getString(R.string.server_available)
                        serverStatusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(android.R.color.holo_green_dark))
                        btnJoin.isEnabled = true
                    }
                    result.contains("FAILED") -> {
                        serverStatusText.text = getString(R.string.server_unavailable)
                        serverStatusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(android.R.color.holo_red_dark))
                        btnJoin.isEnabled = false
                    }
                    else -> {
                        serverStatusText.text = result
                    }
                }
            }
        }
        
        connectivityTest.testServerReachability(host, port)
    }

    private fun checkForUpdates() {
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

                    // Save update availability to SharedPreferences
                    getSharedPreferences("UpdatePrefs", MODE_PRIVATE).edit {
                        putBoolean("update_available", isAvailable)
                        putString("latest_version", latestVersion)
                    }

                    withContext<Unit>(Dispatchers.Main) {
                        updateAvailableIndicator.isVisible = isAvailable
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                // Silent fail - don't show indicator if check fails
                android.util.Log.e("MainActivity", "Version check failed: ${e.message}")
            }
        }
    }

    private fun isUpdateAvailable(latest: String): Boolean {
        // Compare versions in format MAJOR.MINOR.PATCH.BUILD (e.g., 1.0.1.22)
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

    private fun startPeriodicUpdateCheck() {
        stopPeriodicUpdateCheck() // Cancel any existing job

        updateCheckJob = lifecycleScope.launch {
            while (isActive) {
                checkForUpdates()
                delay(30000) // Check every 30 seconds
            }
        }
    }

    private fun stopPeriodicUpdateCheck() {
        updateCheckJob?.cancel()
        updateCheckJob = null
    }
}
