package msg.client.android

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ChatActivitySafe : AppCompatActivity() {
    
    private lateinit var logTextView: TextView
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageAdapter: MessageAdapter
    private var isAdvancedMode = false
    private var currentLanguage = "English"
    private lateinit var grpcClient: msg.client.android.data.grpc.GrpcClient
    private var serverAddress = "192.168.1.135" // Default Go server IP
    
    // Advanced mode buttons
    private lateinit var testButton: Button
    private lateinit var networkInfoButton: Button
    private lateinit var pingButton: Button
    private lateinit var editServerButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request permissions at runtime
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.ACCESS_WIFI_STATE
            ), 1)
        }
        
        try {
            // Create simple layout programmatically to avoid XML issues
            val mainLayout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
            }
            
            // Load saved language
        currentLanguage = getSavedLanguage()
        
                    
            // Log display area (hidden by default, only visible in expert mode)
            val logText = TextView(this).apply {
                text = getString(R.string.connection_logs) + "\n"
                textSize = 12f
                setPadding(8, 8, 8, 8)
                setBackgroundColor(android.graphics.Color.parseColor("#F0F0F0"))
                setTextColor(android.graphics.Color.BLACK)
                setTypeface(android.graphics.Typeface.MONOSPACE)
                visibility = android.view.View.GONE  // Hidden by default
            }
            mainLayout.addView(logText)
            
            // Username display with persistence and change option
            val username = intent.getStringExtra("USERNAME") ?: getSavedUsername() ?: "User"
            saveUsername(username)
            val usernameText = TextView(this).apply {
                text = "$username " + getString(R.string.connected_status)
                textSize = 16f
                setPadding(0, 0, 0, 16)
                setTextColor(android.graphics.Color.BLUE)
                isClickable = true
                setOnClickListener {
                    showChangeUsernameDialog()
                }
            }
            mainLayout.addView(usernameText)
            
            // Test connection button (hidden by default)
            testButton = Button(this).apply {
                text = getString(R.string.test_connection)
                setOnClickListener {
                    testConnection()
                }
                visibility = android.view.View.GONE
            }
            mainLayout.addView(testButton)
            
                        
            // Network info button (hidden by default)
            networkInfoButton = Button(this).apply {
                text = getString(R.string.show_network_info)
                setOnClickListener {
                    showNetworkInfo()
                }
                visibility = android.view.View.GONE
            }
            mainLayout.addView(networkInfoButton)
            
                        
            // Ping laptop button (hidden by default)
            pingButton = Button(this).apply {
                text = getString(R.string.ping_laptop)
                setOnClickListener {
                    pingLaptop()
                }
                visibility = android.view.View.GONE
            }
            mainLayout.addView(pingButton)
            
            // Edit server button (hidden by default)
            editServerButton = Button(this).apply {
                text = "Edit Server Address"
                setOnClickListener {
                    showEditServerDialog()
                }
                visibility = android.view.View.GONE
            }
            mainLayout.addView(editServerButton)
            
            // Enable ActionBar and menu
            supportActionBar?.title = "Lavanda"
            // Disable back button to prevent returning to main screen
            
            // Messages RecyclerView
            messagesRecyclerView = RecyclerView(this).apply {
                layoutManager = LinearLayoutManager(this@ChatActivitySafe)
                setPadding(0, 0, 0, 16)
            }
            messageAdapter = MessageAdapter(mutableListOf())
            messagesRecyclerView.adapter = messageAdapter
            mainLayout.addView(messagesRecyclerView)
            
            // Message input
            val messageInput = EditText(this).apply {
                hint = getString(R.string.type_message_here)
                setPadding(0, 16, 0, 16)
            }
            mainLayout.addView(messageInput)
            
            // Send button
            val sendButton = Button(this).apply {
                text = getString(R.string.send_message)
                setOnClickListener {
                    val text = messageInput.text.toString()
                    if (text.isNotEmpty()) {
                        sendMessage(text)
                        messageInput.text.clear()
                    }
                }
            }
            mainLayout.addView(sendButton)
            
            setContentView(mainLayout)
            
            // Store logText reference for logging
            logTextView = logText
            
            addLog(getString(R.string.lavanda_loaded))
            
            // Initialize gRPC client
            grpcClient = msg.client.android.data.grpc.GrpcClient()
            
            // Connect to server
            addLog("Connecting to server: $serverAddress:50051")
            grpcClient.connect(serverAddress)
            
            // Start chat with username
            val chatUsername = getSavedUsername() ?: "User"
            grpcClient.startChat(chatUsername) { message ->
                runOnUiThread {
                    addLog("Received: ${message.user}: ${message.text}")
                    messageAdapter.addMessage(message)
                    // Scroll to bottom
                    messagesRecyclerView.scrollToPosition(messageAdapter.itemCount - 1)
                }
            }
            
            Toast.makeText(this, getString(R.string.lavanda_ready), Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            
            // Fallback to basic text view
            val errorText = TextView(this).apply {
                text = "Error loading chat: ${e.message}"
                textSize = 16f
                setPadding(16, 16, 16, 16)
            }
            setContentView(errorText)
        }
    }
    
    private fun addLog(message: String) {
        try {
            if (::logTextView.isInitialized) {
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                val logEntry = "[$timestamp] $message\n"
                logTextView.append(logEntry)
            }
        } catch (e: Exception) {
            // Ignore logging errors to prevent crashes
        }
    }
    
    private fun testConnection() {
        addLog("Testing connection to 192.168.1.135:50051...")
        Toast.makeText(this, "Testing connection...", Toast.LENGTH_SHORT).show()
        
        Thread {
            try {
                addLog("Attempting TCP connection...")
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress("192.168.1.135", 50051), 5000)
                
                runOnUiThread {
                    addLog("SUCCESS: Server reachable at 192.168.1.135:50051!")
                    Toast.makeText(this, "SUCCESS: Server connected!", Toast.LENGTH_LONG).show()
                }
                socket.close()
                
            } catch (e: Exception) {
                runOnUiThread {
                    addLog("FAILED: ${e.message}")
                    Toast.makeText(this, "Connection failed", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
    
    private fun sendMessage(text: String) {
        val username = getSavedUsername() ?: "User"
        val message = msg.client.android.data.models.Message(
            user = username,
            text = text,
            timestamp = System.currentTimeMillis()
        )
        
        // Add to UI immediately
        messageAdapter.addMessage(message)
        messagesRecyclerView.scrollToPosition(messageAdapter.itemCount - 1)
        
        // Send via gRPC client
        grpcClient.sendMessage(message)
        
        addLog("Sent: $text")
        Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
    }
    
    private fun getSavedUsername(): String? {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        return prefs.getString("username", null)
    }
    
    private fun saveUsername(username: String) {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit().putString("username", username).apply()
    }
    
    private fun testMultipleIPs() {
        addLog("Testing multiple IP addresses...")
        Toast.makeText(this, "Testing all IPs...", Toast.LENGTH_SHORT).show()
        
        val testIPs = listOf(
            "192.168.1.135",  // Your IP
            "10.0.2.2",      // Android emulator localhost
            "192.168.0.1",   // Common router
            "192.168.1.1"    // Common router
        )
        
        // Use single thread to avoid crashes
        Thread {
            for (ip in testIPs) {
                try {
                    runOnUiThread {
                        addLog("Testing $ip:50051...")
                    }
                    
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress(ip, 50051), 2000)
                    
                    runOnUiThread {
                        addLog("SUCCESS: Connected to $ip:50051!")
                        Toast.makeText(this, "SUCCESS: $ip connected!", Toast.LENGTH_LONG).show()
                    }
                    socket.close()
                    
                    // If successful, no need to test other IPs
                    break
                    
                } catch (e: Exception) {
                    runOnUiThread {
                        addLog("FAILED: $ip:50051 - ${e.message}")
                    }
                }
                
                // Small delay between tests
                Thread.sleep(500)
            }
            
            runOnUiThread {
                addLog("IP testing completed")
            }
        }.start()
    }
    
    private fun testEmulatorConnection() {
        addLog("Testing emulator localhost (10.0.2.2:50051)...")
        Toast.makeText(this, "Testing emulator...", Toast.LENGTH_SHORT).show()
        
        Thread {
            try {
                addLog("Attempting connection to emulator...")
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress("10.0.2.2", 50051), 3000)
                
                runOnUiThread {
                    addLog("SUCCESS: Connected to emulator localhost!")
                    Toast.makeText(this, "SUCCESS: Emulator connected!", Toast.LENGTH_LONG).show()
                }
                socket.close()
                
            } catch (e: Exception) {
                runOnUiThread {
                    addLog("FAILED: Emulator connection - ${e.message}")
                    Toast.makeText(this, "Emulator failed", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
    
    private fun showNetworkInfo() {
        addLog("=== Network Information ===")
        
        try {
            // Get WiFi network info
            val wifiManager = applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipAddress = wifiInfo.ipAddress
            
            // Convert IP to readable format
            val ipString = String.format(
                "%d.%d.%d.%d",
                (ipAddress and 0xff),
                (ipAddress shr 8 and 0xff),
                (ipAddress shr 16 and 0xff),
                (ipAddress shr 24 and 0xff)
            )
            
            addLog("Phone IP: $ipString")
            addLog("SSID: ${wifiInfo.ssid}")
            addLog("Network ID: ${wifiInfo.networkId}")
            
        } catch (e: Exception) {
            addLog("Error getting WiFi info: ${e.message}")
        }
        
        // Test if we can reach the laptop IP
        Thread {
            try {
                addLog("Testing connectivity to laptop...")
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress("192.168.1.135", 50051), 3000)
                
                runOnUiThread {
                    addLog("SUCCESS: Can reach laptop at 192.168.1.135:50051")
                }
                socket.close()
                
            } catch (e: Exception) {
                runOnUiThread {
                    addLog("FAILED: Cannot reach laptop - ${e.message}")
                }
            }
        }.start()
        
        addLog("=== End Network Info ===")
    }
    
    private fun pingLaptop() {
        addLog("=== Ping Test ===")
        addLog("Pinging 192.168.1.135...")
        
        Thread {
            try {
                // Try to reach laptop on port 80 (HTTP) first
                addLog("Testing HTTP (port 80)...")
                val httpSocket = java.net.Socket()
                httpSocket.connect(java.net.InetSocketAddress("192.168.1.135", 80), 2000)
                addLog("SUCCESS: HTTP reachable")
                httpSocket.close()
                
                // Try to reach gRPC port
                addLog("Testing gRPC (port 50051)...")
                val grpcSocket = java.net.Socket()
                grpcSocket.connect(java.net.InetSocketAddress("192.168.1.135", 50051), 2000)
                addLog("SUCCESS: gRPC reachable")
                grpcSocket.close()
                
                runOnUiThread {
                    addLog("Laptop is fully reachable!")
                    Toast.makeText(this, "Laptop reachable!", Toast.LENGTH_LONG).show()
                }
                
            } catch (e: java.net.SocketTimeoutException) {
                runOnUiThread {
                    addLog("TIMEOUT: Laptop not responding")
                    Toast.makeText(this, "Ping timeout", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    addLog("FAILED: Cannot ping laptop - ${e.message}")
                    Toast.makeText(this, "Ping failed", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
        
        addLog("=== End Ping Test ===")
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun toggleExpertMode() {
        isAdvancedMode = !isAdvancedMode
        
        // Toggle advanced buttons visibility using saved references
        testButton.visibility = if (isAdvancedMode) android.view.View.VISIBLE else android.view.View.GONE
        networkInfoButton.visibility = if (isAdvancedMode) android.view.View.VISIBLE else android.view.View.GONE
        pingButton.visibility = if (isAdvancedMode) android.view.View.VISIBLE else android.view.View.GONE
        editServerButton.visibility = if (isAdvancedMode) android.view.View.VISIBLE else android.view.View.GONE
        
        // Toggle connection logs visibility
        logTextView.visibility = if (isAdvancedMode) android.view.View.VISIBLE else android.view.View.GONE
        
        val message = if (isAdvancedMode) getString(R.string.expert_mode_enabled) else getString(R.string.expert_mode_disabled)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun showSettingsDialog() {
        val options = arrayOf(
            getString(R.string.language),
            getString(R.string.color_scheme),
            getString(R.string.expert_mode),
            getString(R.string.cancel)
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.settings_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showLanguageDialog()
                    1 -> showColorSchemeDialog()
                    2 -> toggleExpertMode()
                }
            }
            .show()
    }
    
    private fun getLocalizedText(language: String, key: String): String {
        return when (language) {
            "Russian" -> {
                when (key) {
                    "enter_username" -> "введите свое имя"
                    "test_connection" -> "Test Connection"
                    "show_network_info" -> "Show Network Info"
                    "ping_laptop" -> "Ping Laptop (192.168.1.135)"
                    "send_message" -> "Send Message"
                    "lavanda_ready" -> "Lavanda Messenger ready!"
                    "expert_mode_enabled" -> "Expert mode enabled"
                    "expert_mode_disabled" -> "Expert mode disabled"
                    "language_selected" -> "Language: %s"
                    "color_scheme_selected" -> "Color Scheme: %s"
                    "testing_connection" -> "Testing connection..."
                    "connection_success" -> "SUCCESS: Server connected!"
                    "connection_failed" -> "Connection failed"
                    "ping_success" -> "SUCCESS: Laptop reachable!"
                    "ping_failed" -> "Ping failed"
                    "ping_timeout" -> "Ping timeout"
                    "light" -> "Light"
                    "dark" -> "Dark"
                    "blue" -> "Blue"
                    "join" -> "Join"
                    "cancel_dialog" -> "Cancel"
                    "connected_status" -> "connected"
                    "enter_username" -> "Enter your name"
                    "welcome" -> "Enter your name"
                    "connected_status" -> "Enter your name"
                    "cancel_dialog" -> "Cancel"
                    "connected_status" -> "connected"
                    "username_empty" -> "Username cannot be empty"
                    "logged_out" -> "Logged out successfully"
                    "exiting_app" -> "Exiting application"
                    "change_username" -> "Change Username"
                    "enter_new_username" -> "Enter new username"
                    "change" -> "Change"
                    else -> key
                }
            }
            "Chinese" -> {
                when (key) {
                    "welcome" -> "Enter your name"
                    "enter_username" -> "Enter your name"
                    "welcome" -> "Enter your name"
                    "connected_status" -> "Enter your name"
                    "cancel_dialog" -> "Cancel"
                    "connected_status" -> "connected"
                    "test_connection" -> "Test Connection"
                    "show_network_info" -> "Show Network Info"
                    "ping_laptop" -> "Ping Laptop (192.168.1.135)"
                    "send_message" -> "Send Message"
                    "lavanda_ready" -> "Lavanda Messenger ready!"
                    "expert_mode_enabled" -> "Expert mode enabled"
                    "expert_mode_disabled" -> "Expert mode disabled"
                    "language_selected" -> "Language: %s"
                    "color_scheme_selected" -> "Color Scheme: %s"
                    "testing_connection" -> "Testing connection..."
                    "connection_success" -> "SUCCESS: Server connected!"
                    "connection_failed" -> "Connection failed"
                    "ping_success" -> "SUCCESS: Laptop reachable!"
                    "ping_failed" -> "Ping failed"
                    "ping_timeout" -> "Ping timeout"
                    "light" -> "Light"
                    "dark" -> "Dark"
                    "blue" -> "Blue"
                    "join" -> "Join"
                    "cancel_dialog" -> "Cancel"
                    "connected_status" -> "connected"
                    "enter_username" -> "Enter your name"
                    "welcome" -> "Enter your name"
                    "connected_status" -> "Enter your name"
                    "cancel_dialog" -> "Cancel"
                    "connected_status" -> "connected"
                    "username_empty" -> "Username cannot be empty"
                    "logged_out" -> "Logged out successfully"
                    "exiting_app" -> "Exiting application"
                    "change_username" -> "Change Username"
                    "enter_new_username" -> "Enter new username"
                    "change" -> "Change"
                    else -> key
                }
            }
            else -> key // English (default)
        }
    }
    
    private fun showLanguageDialog() {
        val languages = arrayOf("Russian", "English", "Chinese")
        val currentLanguage = getSavedLanguage()
        val currentIndex = languages.indexOf(currentLanguage)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Language")
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                saveLanguage(languages[which])
                dialog.dismiss()
                Toast.makeText(this, "Language: ${languages[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun showColorSchemeDialog() {
        val schemes = arrayOf("Light", "Dark", "Blue")
        val currentScheme = getSavedColorScheme()
        val currentIndex = schemes.indexOf(currentScheme)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Color Scheme")
            .setSingleChoiceItems(schemes, currentIndex) { dialog, which ->
                saveColorScheme(schemes[which])
                applyColorScheme(schemes[which])
                dialog.dismiss()
                Toast.makeText(this, "Color Scheme: ${schemes[which]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun getSavedLanguage(): String {
        val prefs = getSharedPreferences("Settings", MODE_PRIVATE)
        return prefs.getString("language", "English") ?: "English"
    }
    
    private fun saveLanguage(language: String) {
        val prefs = getSharedPreferences("Settings", MODE_PRIVATE)
        prefs.edit().putString("language", language).apply()
    }
    
    private fun getSavedColorScheme(): String {
        val prefs = getSharedPreferences("Settings", MODE_PRIVATE)
        return prefs.getString("color_scheme", "Light") ?: "Light"
    }
    
    private fun saveColorScheme(scheme: String) {
        val prefs = getSharedPreferences("Settings", MODE_PRIVATE)
        prefs.edit().putString("color_scheme", scheme).apply()
    }
    
    private fun applyColorScheme(scheme: String) {
        when (scheme) {
            "Light" -> {
                // Light theme (default)
                logTextView.setBackgroundColor(android.graphics.Color.parseColor("#F0F0F0"))
                logTextView.setTextColor(android.graphics.Color.BLACK)
            }
            "Dark" -> {
                // Dark theme
                logTextView.setBackgroundColor(android.graphics.Color.parseColor("#2C2C2C"))
                logTextView.setTextColor(android.graphics.Color.WHITE)
            }
            "Blue" -> {
                // Blue theme
                logTextView.setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
                logTextView.setTextColor(android.graphics.Color.parseColor("#1565C0"))
            }
        }
    }
    
    private fun showChangeUsernameDialog() {
        val editText = EditText(this)
        editText.hint = getString(R.string.enter_new_username)
        editText.setText(getSavedUsername())
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.change_username))
            .setView(editText)
            .setPositiveButton(getString(R.string.change)) { dialog, _ ->
                val newUsername = editText.text.toString().trim()
                if (newUsername.isNotEmpty()) {
                    saveUsername(newUsername)
                    // Restart activity with new username
                    val intent = Intent(this, ChatActivitySafe::class.java)
                    intent.putExtra("USERNAME", newUsername)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, getString(R.string.username_empty), Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(getString(android.R.string.cancel), null)
            .show()
    }
    
    private fun showEditServerDialog() {
        val editText = EditText(this)
        editText.hint = "Server address (e.g., 10.0.2.2 or 192.168.1.135)"
        editText.setText(serverAddress)
        editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Edit Server Address")
            .setView(editText)
            .setPositiveButton("Connect") { dialog, _ ->
                val newAddress = editText.text.toString().trim()
                if (newAddress.isNotEmpty()) {
                    // Disconnect current connection
                    if (::grpcClient.isInitialized) {
                        grpcClient.disconnect()
                    }
                    
                    // Update server address
                    serverAddress = newAddress
                    addLog("Changing server to: $serverAddress:50051")
                    
                    // Reconnect with new address
                    grpcClient = msg.client.android.data.grpc.GrpcClient()
                    grpcClient.connect(serverAddress)
                    
                    // Restart chat
                    val username = getSavedUsername() ?: "User"
                    grpcClient.startChat(username) { message ->
                        runOnUiThread {
                            addLog("Received: ${message.user}: ${message.text}")
                            messageAdapter.addMessage(message)
                            messagesRecyclerView.scrollToPosition(messageAdapter.itemCount - 1)
                        }
                    }
                    
                    Toast.makeText(this, "Connected to $serverAddress:50051", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun logout() {
        // Disconnect gRPC client
        if (::grpcClient.isInitialized) {
            grpcClient.disconnect()
        }
        
        // Clear saved username
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        prefs.edit().remove("username").apply()
        
        Toast.makeText(this, getString(R.string.logged_out), Toast.LENGTH_SHORT).show()
        
        // Go back to main activity
        val intent = Intent(this, MainActivityMinimal::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
