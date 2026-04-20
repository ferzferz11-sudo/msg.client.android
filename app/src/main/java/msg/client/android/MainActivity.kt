package msg.client.android

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
import android.content.Context
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import msg.client.android.data.grpc.ServerConnectivityTest
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    companion object {
        private const val APK_URL = "http://159.195.38.145:8081/lavender.apk"
        private const val VERSION_CHECK_URL = "http://159.195.38.145:8081/version.txt"
    }

    private lateinit var joinChatButton: Button
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var updateAvailableIndicator: ImageView
    private var currentLanguage: String? = null
    private var updateCheckJob: Job? = null

    private val serverList = listOf(
        "159.195.38.145:50051",
        "192.168.1.135:50051"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedColorScheme()
        applySavedLanguage()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        currentLanguage = getSavedLanguage()
        updateLanguageButtonText()
        updateColorSchemeButtonText()

        updateAvailableIndicator = findViewById(R.id.updateAvailableIndicator)

        setupJoinChatButton()
        setupLogoutButton()
        setupLanguageButton()
        setupColorSchemeButton()
        setupDownloadUpdateButton()

        checkForUpdates()
    }

    override fun onResume() {
        super.onResume()
        // Check if language changed and recreate activity if needed
        val savedLanguage = getSavedLanguage()
        if (savedLanguage != currentLanguage) {
            currentLanguage = savedLanguage
            applySavedLanguage()
            recreate()
        } else {
            updateLanguageButtonText()
            updateColorSchemeButtonText()
        }

        // Start periodic update check
        startPeriodicUpdateCheck()
    }

    override fun onPause() {
        super.onPause()
        // Stop periodic update check
        stopPeriodicUpdateCheck()
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
        val logoutButton: Button = findViewById(R.id.logoutButton)
        logoutButton.setOnClickListener {
            logout()
        }
    }

    // Add language toggle button
    private fun setupLanguageButton() {
        val languageButton: Button = findViewById(R.id.languageButton)
        languageButton.setOnClickListener {
            toggleLanguage()
        }
    }

    // Add color scheme toggle button
    private fun setupColorSchemeButton() {
        val colorSchemeButton: Button = findViewById(R.id.colorSchemeButton)
        colorSchemeButton.setOnClickListener {
            toggleColorScheme()
        }
    }

    // Add download update button
    private fun setupDownloadUpdateButton() {
        val downloadUpdateButton: TextView = findViewById(R.id.downloadUpdateButton)
        downloadProgressBar = findViewById(R.id.downloadProgressBar)
        downloadUpdateButton.setOnClickListener {
            downloadAndInstallApk()
        }

        // Update version text from BuildConfig
        val appVersionText: TextView = findViewById(R.id.appVersionText)
        appVersionText.text = BuildConfig.VERSION_NAME

        findViewById<ImageButton>(R.id.copyLinkButton).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Lavender APK URL", APK_URL)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.shareLinkButton).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_app))
            shareIntent.putExtra(Intent.EXTRA_TEXT, APK_URL)
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app)))
        }
    }

    private fun downloadAndInstallApk() {
        downloadProgressBar.visibility = View.VISIBLE
        downloadProgressBar.progress = 0
        
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
                        withContext(Dispatchers.Main) {
                            downloadProgressBar.progress = progress
                        }
                    }
                    output.write(data, 0, count)
                }
                
                output.flush()
                output.close()
                input.close()
                
                withContext(Dispatchers.Main) {
                    downloadProgressBar.visibility = View.GONE
                    installApk(file)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloadProgressBar.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Download error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
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
        val titleText = dialogView.findViewById<TextView>(R.id.titleText)
        val editText = dialogView.findViewById<EditText>(R.id.editTextUsername)
        val editTextPassword = dialogView.findViewById<EditText>(R.id.editTextPassword)
        val serverAddressSpinner = dialogView.findViewById<Spinner>(R.id.serverAddressSpinner)
        val serverStatusIndicator = dialogView.findViewById<View>(R.id.serverStatusIndicator)
        val serverStatusText = dialogView.findViewById<TextView>(R.id.serverStatusText)
        val refreshServerButton = dialogView.findViewById<TextView>(R.id.refreshServerButton)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnJoin = dialogView.findViewById<Button>(R.id.btnJoin)
        
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
                Toast.makeText(this, getString(R.string.username_empty), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, getString(R.string.password_empty), Toast.LENGTH_LONG).show()
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
        Toast.makeText(this, getString(R.string.exiting_app), Toast.LENGTH_SHORT).show()
        
        // Exit the application completely
        finishAffinity()
        System.exit(0)
    }
    
    private fun applySavedLanguage() {
        val savedLanguage = getSavedLanguage()
        if (savedLanguage != null) {
            setLocale(savedLanguage)
        }
    }
    
    private fun applySavedColorScheme() {
        val savedScheme = getSavedColorScheme()
        val theme = if (savedScheme != null) {
            when (savedScheme) {
                "dark" -> R.style.Theme_MsgClientAndroid_Dark
                else -> R.style.Theme_MsgClientAndroid
            }
        } else {
            // If no saved scheme (first launch), use dark theme
            R.style.Theme_MsgClientAndroid_Dark
        }
        setTheme(theme)
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
    
    private fun getSavedColorScheme(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("color_scheme", null)
    }
    
    private fun saveColorScheme(scheme: String) {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit {
            putString("color_scheme", scheme)
        }
    }
    
    private fun toggleColorScheme() {
        val schemes = listOf("light", "dark")
        val currentScheme = getSavedColorScheme() ?: "dark"
        val currentIndex = schemes.indexOf(currentScheme)
        val nextIndex = (currentIndex + 1) % schemes.size
        val newScheme = schemes[nextIndex]
        
        saveColorScheme(newScheme)
        updateColorSchemeButtonText()
        recreate()
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
    
    private fun updateColorSchemeButtonText() {
        val colorSchemeButton: Button? = findViewById(R.id.colorSchemeButton)
        if (colorSchemeButton != null) {
            val currentScheme = getSavedColorScheme() ?: "dark"
            val schemeName = if (currentScheme == "dark") {
                getString(R.string.dark)
            } else {
                getString(R.string.light)
            }
            colorSchemeButton.text = getString(R.string.color_scheme_format, schemeName)
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
        
        // Parse server address (format: host:port)
        val parts = serverAddress.split(":")
        val host = if (parts.size >= 1) parts[0] else "localhost"
        val port = if (parts.size >= 2) parts[1].toIntOrNull() ?: 50051 else 50051
        
        // Set checking state
        serverStatusText.text = getString(R.string.checking_server)
        serverStatusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(android.R.color.darker_gray))
        btnJoin.isEnabled = false
        
        // Subscribe to test results
        lifecycleScope.launch {
            connectivityTest.testResult.collect { result ->
                when {
                    result?.contains("SUCCESS") == true -> {
                        serverStatusText.text = getString(R.string.server_available)
                        serverStatusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(android.R.color.holo_green_dark))
                        btnJoin.isEnabled = true
                    }
                    result?.contains("FAILED") == true || result?.contains("PARTIAL") == true -> {
                        serverStatusText.text = getString(R.string.server_unavailable)
                        serverStatusIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(android.R.color.holo_red_dark))
                        btnJoin.isEnabled = false
                    }
                    else -> {
                        // Still testing
                        serverStatusText.text = result ?: getString(R.string.checking_server)
                    }
                }
            }
        }
        
        // Start the test
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
                    val currentVersion = BuildConfig.VERSION_NAME

                    withContext<Unit>(Dispatchers.Main) {
                        if (isUpdateAvailable(currentVersion, latestVersion)) {
                            updateAvailableIndicator.visibility = View.VISIBLE
                        } else {
                            updateAvailableIndicator.visibility = View.GONE
                        }
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                // Silent fail - don't show indicator if check fails
                android.util.Log.e("MainActivity", "Version check failed: ${e.message}")
            }
        }
    }

    private fun isUpdateAvailable(current: String, latest: String): Boolean {
        // Compare versions in format MAJOR.MINOR.PATCH.BUILD (e.g., 1.0.1.22)
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
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
