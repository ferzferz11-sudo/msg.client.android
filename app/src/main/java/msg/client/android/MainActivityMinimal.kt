package msg.client.android

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import java.util.Locale
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import msg.client.android.data.grpc.ServerConnectivityTest

class MainActivityMinimal : AppCompatActivity() {
    
    private lateinit var joinChatButton: Button
    
    private val serverList = listOf(
        "192.168.1.135:50051",
        "10.0.2.2:50051",
        "localhost:50051"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedColorScheme()
        applySavedLanguage()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_simple)
        updateLanguageButtonText()
        updateColorSchemeButtonText()
        
        // Show join chat button
        joinChatButton = findViewById(R.id.joinChatButton)
        joinChatButton.setOnClickListener {
            showUsernameDialog()
        }
        
        // Add logout button
        val logoutButton: Button = findViewById(R.id.logoutButton)
        logoutButton.setOnClickListener {
            logout()
        }
        
        // Add language toggle button
        val languageButton: Button = findViewById(R.id.languageButton)
        languageButton.setOnClickListener {
            toggleLanguage()
        }
        
        // Add color scheme toggle button
        val colorSchemeButton: Button = findViewById(R.id.colorSchemeButton)
        colorSchemeButton.setOnClickListener {
            toggleColorScheme()
        }
    }
    
    private fun showUsernameDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_join_chat, null)
        val titleText = dialogView.findViewById<TextView>(R.id.titleText)
        val editText = dialogView.findViewById<EditText>(R.id.editTextUsername)
        val serverAddressSpinner = dialogView.findViewById<Spinner>(R.id.serverAddressSpinner)
        val serverStatusIndicator = dialogView.findViewById<View>(R.id.serverStatusIndicator)
        val serverStatusText = dialogView.findViewById<TextView>(R.id.serverStatusText)
        val refreshServerButton = dialogView.findViewById<TextView>(R.id.refreshServerButton)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val btnJoin = dialogView.findViewById<Button>(R.id.btnJoin)
        
        // Set localized text
        titleText.text = getString(R.string.welcome)
        editText.hint = getString(R.string.enter_username)
        btnCancel.text = getString(R.string.cancel_dialog)
        btnJoin.text = getString(R.string.join)
        
        // Pre-fill with saved username
        val savedUsername = getSavedUsername()
        if (savedUsername != null) {
            editText.setText(savedUsername)
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
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnJoin.setOnClickListener {
            val username = editText.text.toString().trim()
            val serverAddress = serverAddressSpinner.selectedItem.toString()
            if (username.isNotEmpty()) {
                saveUsername(username)
                saveServerAddress(serverAddress)
                android.util.Log.d("MainActivity", "Connecting to server: $serverAddress")
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("USERNAME", username)
                intent.putExtra("SERVER_ADDRESS", serverAddress)
                startActivity(intent)
                dialog.dismiss()
            } else {
                Toast.makeText(this, getString(R.string.username_empty), Toast.LENGTH_LONG).show()
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
        prefs.edit().putString("username", username).apply()
    }
    
    private fun getSavedServerAddress(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("server_address", null)
    }
    
    private fun saveServerAddress(address: String) {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit().putString("server_address", address).apply()
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
        if (savedScheme != null) {
            val theme = when (savedScheme) {
                "dark" -> R.style.Theme_MsgClientAndroid_Dark
                else -> R.style.Theme_MsgClientAndroid
            }
            setTheme(theme)
        }
    }
    
    private fun getSavedLanguage(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("language", null)
    }
    
    private fun saveLanguage(languageCode: String) {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit().putString("language", languageCode).apply()
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
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val resources: Resources = resources
        val config: Configuration = resources.configuration
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale)
            createConfigurationContext(config)
        } else {
            config.locale = locale
            resources.updateConfiguration(config, resources.displayMetrics)
        }
        
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
            languageButton.text = "${getString(R.string.language)}: $languageName"
        }
    }
    
    private fun updateColorSchemeButtonText() {
        val colorSchemeButton: Button? = findViewById(R.id.colorSchemeButton)
        if (colorSchemeButton != null) {
            val currentScheme = getSavedColorScheme() ?: "light"
            val schemeName = if (currentScheme == "dark") {
                getString(R.string.dark)
            } else {
                getString(R.string.light)
            }
            colorSchemeButton.text = "${getString(R.string.toggle_color_scheme)}: $schemeName"
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
        serverStatusIndicator.setBackgroundColor(getColor(android.R.color.darker_gray))
        btnJoin.isEnabled = false
        
        // Subscribe to test results
        lifecycleScope.launch {
            connectivityTest.testResult.collect { result ->
                when {
                    result?.contains("SUCCESS") == true -> {
                        serverStatusText.text = getString(R.string.server_available)
                        serverStatusIndicator.setBackgroundColor(getColor(android.R.color.holo_green_dark))
                        btnJoin.isEnabled = true
                    }
                    result?.contains("FAILED") == true || result?.contains("PARTIAL") == true -> {
                        serverStatusText.text = getString(R.string.server_unavailable)
                        serverStatusIndicator.setBackgroundColor(getColor(android.R.color.holo_red_dark))
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
}
