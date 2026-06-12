package lavender.client.android.ui.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.BuildConfig
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

class RemoteAgentSettingsActivity : AppCompatActivity() {

    private lateinit var tokenListContainer: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var btnGenerateToken: MaterialButton
    private lateinit var btnStartAgent: MaterialButton
    private lateinit var btnStopAgent: MaterialButton
    private lateinit var agentStatusText: TextView

    // Hermes Gateway UI
    private lateinit var etSshHost: com.google.android.material.textfield.TextInputEditText
    private lateinit var etSshPort: com.google.android.material.textfield.TextInputEditText
    private lateinit var etSshUser: com.google.android.material.textfield.TextInputEditText
    private lateinit var etServerHost: com.google.android.material.textfield.TextInputEditText
    private lateinit var etServerPort: com.google.android.material.textfield.TextInputEditText
    private lateinit var etLocalPort: com.google.android.material.textfield.TextInputEditText
    private lateinit var tvTunnelStatus: TextView
    private lateinit var cbAutoConnect: com.google.android.material.checkbox.MaterialCheckBox
    private lateinit var btnCreateTunnel: MaterialButton
    private lateinit var btnCloseTunnel: MaterialButton

    private var userId: String = ""
    private val tokens = mutableListOf<TokenInfo>()
    private var selectedAgentId: String = ""
    private var selectedAgentName: String = ""
    private var selectedToken: String = ""
    
    // Independent coroutine scope that survives Activity recreation
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Hermes Gateway Manager
    private lateinit var gatewayManager: HermesGatewayManager

    // Keys for persisting agent selection across activity recreation
    private companion object {
        private const val PREF_AGENT_ID = "remote_agent_id"
        private const val PREF_AGENT_NAME = "remote_agent_name"
        private const val PREF_AGENT_TOKEN = "remote_agent_token"
        private const val PREF_AGENT_SCRIPT_PATH = "remote_agent_script_path"
    }

    // Default agent script path (can be overridden in settings)
    private val DEFAULT_AGENT_SCRIPT_PATH = "/root/msg.remote.agent/hermes_remote_agent.py"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_remote_agent_settings)

        userId = SessionManager.session.value.userId
        gatewayManager = HermesGatewayManager(this)

        val theme = ThemeStore.currentTheme()
        val bgColor = ThemeUtils.parseSafeColor(theme.backgroundColor, Color.BLACK)
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val surfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)

        // Apply background
        window.decorView.setBackgroundColor(bgColor)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        tokenListContainer = findViewById(R.id.tokenListContainer)
        emptyText = findViewById(R.id.emptyText)
        btnGenerateToken = findViewById(R.id.btnGenerateToken)
        btnStartAgent = findViewById(R.id.btnStartAgent)
        btnStopAgent = findViewById(R.id.btnStopAgent)
        agentStatusText = findViewById(R.id.agentStatusText)

        // Hermes Gateway UI
        etSshHost = findViewById(R.id.etSshHost)
        etSshPort = findViewById(R.id.etSshPort)
        etSshUser = findViewById(R.id.etSshUser)
        etServerHost = findViewById(R.id.etServerHost)
        etServerPort = findViewById(R.id.etServerPort)
        etLocalPort = findViewById(R.id.etLocalPort)
        tvTunnelStatus = findViewById(R.id.tvTunnelStatus)
        cbAutoConnect = findViewById(R.id.cbAutoConnect)
        btnCreateTunnel = findViewById(R.id.btnCreateTunnel)
        btnCloseTunnel = findViewById(R.id.btnCloseTunnel)

        // Set initial status text color
        agentStatusText.setTextColor(ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE))

        // Toolbar
        toolbar.setBackgroundColor(surfaceColor)
        toolbar.setTitleTextColor(txtColor)
        toolbar.setNavigationIconTint(txtColor)
        toolbar.setNavigationOnClickListener { finish() }

        // Style the generate button
        btnGenerateToken.setBackgroundColor(ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE))
        btnGenerateToken.setTextColor(ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE))

        // Style agent control buttons
        btnStartAgent.setBackgroundColor(ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE))
        btnStartAgent.setTextColor(ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE))
        btnStopAgent.setBackgroundColor(Color.parseColor("#F44336"))
        btnStopAgent.setTextColor(Color.WHITE)

        // Style gateway buttons
        btnCreateTunnel.setBackgroundColor(ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE))
        btnCreateTunnel.setTextColor(ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE))
        btnCloseTunnel.setBackgroundColor(Color.parseColor("#F44336"))
        btnCloseTunnel.setTextColor(Color.WHITE)

        // Restore gateway settings
        restoreGatewaySettings()

        // Generate token button
        btnGenerateToken.setOnClickListener {
            showTokenDialog()
        }

        // Start agent button
        btnStartAgent.setOnClickListener {
            startAgentOnServer()
        }

        // Stop agent button
        btnStopAgent.setOnClickListener {
            stopAgentOnServer()
        }

        // Create tunnel button
        btnCreateTunnel.setOnClickListener {
            createTunnel()
        }

        // Close tunnel button
        btnCloseTunnel.setOnClickListener {
            closeTunnel()
        }

        // Auto-connect checkbox
        cbAutoConnect.setOnCheckedChangeListener { _, isChecked ->
            gatewayManager.setAutoConnect(isChecked)
        }

        // Restore previously selected agent from prefs FIRST
        restoreSelectedAgent()

        // Load tokens
        loadTokens()

        // Check agent status AFTER restore
        checkAgentStatus()

        // Update tunnel status UI
        updateTunnelStatusUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    override fun onResume() {
        super.onResume()
        updateTunnelStatusUI()
    }

    private fun loadTokens() {
        activityScope.launch {
            try {
                val response = GrpcClient.listAgentTokens(userId)
                tokens.clear()
                if (response.success) {
                    tokens.addAll(response.tokens.map { proto ->
                        TokenInfo(
                            id = proto.id,
                            agentId = proto.agentId,
                            agentName = proto.agentName,
                            tokenHash = proto.tokenHash,
                            capabilities = proto.capabilities,
                            createdAt = proto.createdAt,
                            expiresAt = proto.expiresAt,
                            revoked = proto.revoked,
                            createdBy = proto.createdBy
                        )
                    })
                }
                renderTokens()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@RemoteAgentSettingsActivity, "Ошибка загрузки токенов: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderTokens() {
        val theme = ThemeStore.currentTheme()
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val txtSecondary = ThemeUtils.parseSafeColor(theme.textSecondaryColor, Color.GRAY)
        val surfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)

        tokenListContainer.removeAllViews()

        val activeTokens = tokens.filter { !it.revoked }

        if (activeTokens.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            emptyText.setTextColor(txtSecondary)
            tokenListContainer.visibility = View.GONE
        } else {
            emptyText.visibility = View.GONE
            tokenListContainer.visibility = View.VISIBLE

            val inflater = LayoutInflater.from(this)
            activeTokens.forEach { token ->
                val view = inflater.inflate(R.layout.item_agent_token, tokenListContainer, false)

                val agentName = view.findViewById<TextView>(R.id.tokenAgentName)
                val status = view.findViewById<TextView>(R.id.tokenStatus)
                val hash = view.findViewById<TextView>(R.id.tokenHash)
                val caps = view.findViewById<TextView>(R.id.tokenCapabilities)
                val expires = view.findViewById<TextView>(R.id.tokenExpires)
                val revokeBtn = view.findViewById<MaterialButton>(R.id.btnRevoke)

                agentName.text = token.agentName
                agentName.setTextColor(txtColor)

                status.text = "Активен"
                status.setTextColor(Color.parseColor("#4CAF50"))
                status.setBackgroundColor(Color.parseColor("#1A4CAF50"))
                status.setPadding(12, 4, 12, 4)

                hash.text = "Хэш: ${token.tokenHash.take(16)}..."
                hash.setTextColor(txtSecondary)

                caps.text = "Возможности: ${token.capabilities.joinToString(", ")}"
                caps.setTextColor(txtSecondary)

                expires.text = if (token.expiresAt.isNotEmpty()) "Истёк: ${token.expiresAt}" else "Бессрочный"
                expires.setTextColor(txtSecondary)

                val copyTokenBtn = view.findViewById<MaterialButton>(R.id.btnCopyToken)
                val copyCmdBtn = view.findViewById<MaterialButton>(R.id.btnCopyCmd)

                revokeBtn.setTextColor(Color.parseColor("#F44336"))
                revokeBtn.setOnClickListener {
                    confirmRevoke(token)
                }

                copyTokenBtn.setTextColor(primColor)
                copyTokenBtn.setOnClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Agent Token", token.fullToken))
                    Toast.makeText(this@RemoteAgentSettingsActivity, "Токен скопирован", Toast.LENGTH_SHORT).show()
                }

                copyCmdBtn.setTextColor(primColor)
                copyCmdBtn.setOnClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Agent Command", token.command))
                    Toast.makeText(this@RemoteAgentSettingsActivity, "Команда скопирована", Toast.LENGTH_SHORT).show()
                }

                val card = view as com.google.android.material.card.MaterialCardView
                card.setCardBackgroundColor(surfaceColor)

                tokenListContainer.addView(view)
            }
        }
    }

    private fun showTokenDialog() {
        val dialog = TokenDialog(
            context = this,
            theme = ThemeStore.currentTheme(),
            onGenerate = { agentName, capabilities, ttlHours ->
                val agentId = "agent_${System.currentTimeMillis()}"
                generateToken(agentId, agentName, capabilities, ttlHours)
            }
        )
        dialog.show()
    }

    private fun generateToken(agentId: String, agentName: String, capabilities: List<String>, ttlHours: Int) {
        activityScope.launch {
            try {
                val response = GrpcClient.generateAgentToken(
                    agentId = agentId,
                    agentName = agentName,
                    capabilities = capabilities,
                    ttlHours = ttlHours,
                    adminUserId = userId
                )
                if (response.success) {
                    selectedAgentId = agentId
                    selectedAgentName = agentName
                    selectedToken = response.token
                    saveSelectedAgent()
                    
                    // Add token to local list immediately
                    val expiresAt = if (response.expiresAt > 0) {
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(response.expiresAt * 1000))
                    } else ""
                    // Build server address for agent command — use tunnel if active
                    val prefs2 = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                    val agentScript = prefs2.getString(PREF_AGENT_SCRIPT_PATH, DEFAULT_AGENT_SCRIPT_PATH) ?: DEFAULT_AGENT_SCRIPT_PATH
                    val fullServer = if (gatewayManager.isTunnelActive()) {
                        gatewayManager.getLocalAddress()
                    } else {
                        val serverAddr = prefs2.getString("server_address", "") ?: ""
                        val serverPort = prefs2.getString("server_port", "50051") ?: "50051"
                        if (serverAddr.isNotEmpty()) "$serverAddr:$serverPort" else "<server:port>"
                    }
                    val agentCmd = "python3 $agentScript --server $fullServer --token ${response.token}"
                    tokens.add(TokenInfo(
                        id = 0,
                        agentId = agentId,
                        agentName = agentName,
                        tokenHash = response.token.take(16),
                        capabilities = capabilities,
                        createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date()),
                        expiresAt = expiresAt,
                        revoked = false,
                        createdBy = userId,
                        fullToken = response.token,
                        command = agentCmd
                    ))
                    renderTokens()
                    
                    showTokenResultDialog(response.token, agentId, agentName)
                } else {
                    Toast.makeText(this@RemoteAgentSettingsActivity, "Ошибка: ${response.error}", Toast.LENGTH_LONG).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@RemoteAgentSettingsActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showTokenResultDialog(token: String, agentId: String = "", agentName: String = "") {
        val theme = ThemeStore.currentTheme()
        val bgColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
            setBackgroundColor(bgColor)
        }

        val label = TextView(this).apply {
            text = "Токен агента (скопируйте — он показывается только один раз):"
            setTextColor(txtColor)
            textSize = 14f
        }

        val tokenView = TextView(this).apply {
            text = token
            setTextColor(txtColor)
            textSize = 13f
            setPadding(0, 16, 0, 16)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        container.addView(label)
        container.addView(tokenView)

        // Build agent command — use tunnel address if active
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val agentScript = prefs.getString(PREF_AGENT_SCRIPT_PATH, DEFAULT_AGENT_SCRIPT_PATH) ?: DEFAULT_AGENT_SCRIPT_PATH
        val fullServer = if (gatewayManager.isTunnelActive()) {
            gatewayManager.getLocalAddress()
        } else {
            val serverAddr = prefs.getString("server_address", "") ?: ""
            val serverPort = prefs.getString("server_port", "50051") ?: "50051"
            if (serverAddr.isNotEmpty()) "$serverAddr:$serverPort" else "<server:port>"
        }
        val agentCmd = "python3 $agentScript --server $fullServer --token $token"

        val cmdLabel = TextView(this).apply {
            text = "Команда для запуска агента:"
            setTextColor(txtColor)
            textSize = 14f
            setPadding(0, 16, 0, 0)
        }

        val cmdView = TextView(this).apply {
            text = agentCmd
            setTextColor(txtColor)
            textSize = 11f
            setPadding(0, 8, 0, 8)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundColor(ThemeUtils.parseSafeColor(theme.backgroundColor, Color.BLACK))
        }

        container.addView(cmdLabel)
        container.addView(cmdView)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle("Токен сгенерирован")
            .setView(container)
            .setPositiveButton("Копировать токен", null)
            .setNeutralButton("Копировать команду", null)
            .setNegativeButton("Закрыть", null)
            .create()

        dialog.setOnShowListener {
            // Override button clicks to prevent auto-dismiss
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Agent Token", token))
                Toast.makeText(this, "Токен скопирован", Toast.LENGTH_SHORT).show()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Agent Command", agentCmd))
                Toast.makeText(this, "Команда скопирована", Toast.LENGTH_SHORT).show()
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                dialog.dismiss()
            }
        }

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(primColor)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(primColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(txtColor)
        val titleId = resources.getIdentifier("alertTitle", "id", "android")
        dialog.findViewById<TextView>(titleId)?.setTextColor(txtColor)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bgColor))
    }

    private fun confirmRevoke(token: TokenInfo) {
        val theme = ThemeStore.currentTheme()
        val bgColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle("Отозвать токен?")
            .setMessage("Токен для \"${token.agentName}\" будет отозван. Агент потеряет доступ.")
            .setPositiveButton("Отозвать") { _, _ ->
                revokeToken(token.agentId)
            }
            .setNegativeButton("Отмена", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#F44336"))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(txtColor)
        val titleId = resources.getIdentifier("alertTitle", "id", "android")
        dialog.findViewById<TextView>(titleId)?.setTextColor(txtColor)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bgColor))
    }

    private fun revokeToken(agentId: String) {
        activityScope.launch {
            try {
                val response = GrpcClient.revokeAgentToken(agentId, userId)
                if (response.success) {
                    Toast.makeText(this@RemoteAgentSettingsActivity, "Токен отозван", Toast.LENGTH_SHORT).show()
                    loadTokens()
                } else {
                    Toast.makeText(this@RemoteAgentSettingsActivity, "Ошибка: ${response.error}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@RemoteAgentSettingsActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ===== Agent Process Management (server-side) =====

    private fun startAgentOnServer() {
        if (selectedToken.isEmpty()) {
            Toast.makeText(this, "Сначала сгенерируйте токен", Toast.LENGTH_LONG).show()
            return
        }
        if (selectedAgentId.isEmpty()) {
            selectedAgentId = "agent_${System.currentTimeMillis()}"
        }
        if (selectedAgentName.isEmpty()) {
            selectedAgentName = selectedAgentId
        }

        btnStartAgent.isEnabled = false
        agentStatusText.text = "Статус: запуск..."

        val serverAddress = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
            .getString("server_address", "") ?: ""

        lifecycleScope.launch {
            try {
                val response = GrpcClient.startAgentOnServer(
                    agentId = selectedAgentId,
                    agentName = selectedAgentName,
                    token = selectedToken,
                    serverAddress = serverAddress + ":50052",
                    adminUserId = userId
                )
                if (response.success) {
                    Toast.makeText(this@RemoteAgentSettingsActivity,
                        "Агент запущен (PID: ${response.pid})", Toast.LENGTH_LONG).show()
                    updateAgentStatusText("подключён", true)
                } else {
                    Toast.makeText(this@RemoteAgentSettingsActivity,
                        "Ошибка: ${response.error}", Toast.LENGTH_LONG).show()
                    updateAgentStatusText("ошибка — ${response.error}", false)
                }
            } catch (e: Exception) {
                Toast.makeText(this@RemoteAgentSettingsActivity,
                    "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                updateAgentStatusText("ошибка — ${e.message}", false)
            }
            btnStartAgent.isEnabled = true
        }
    }

    private fun stopAgentOnServer() {
        if (selectedAgentId.isEmpty()) {
            Toast.makeText(this, "Нет запущенного агента", Toast.LENGTH_SHORT).show()
            return
        }

        btnStopAgent.isEnabled = false
        agentStatusText.text = "Статус: остановка..."

        lifecycleScope.launch {
            try {
                val response = GrpcClient.stopAgentOnServer(
                    agentId = selectedAgentId,
                    adminUserId = userId
                )
                if (response.success) {
                    Toast.makeText(this@RemoteAgentSettingsActivity,
                        "Агент остановлен", Toast.LENGTH_SHORT).show()
                    updateAgentStatusText("остановлен", false)
                } else {
                    // Translate server error to Russian
                    val errorMsg = when {
                        response.error.contains("not found", ignoreCase = true) -> "Агент не найден"
                        response.error.contains("already stopped", ignoreCase = true) -> "Агент уже остановлен"
                        else -> response.error
                    }
                    Toast.makeText(this@RemoteAgentSettingsActivity,
                        "Ошибка: $errorMsg", Toast.LENGTH_LONG).show()
                    updateAgentStatusText("ошибка — $errorMsg", false)
                }
            } catch (e: Exception) {
                Toast.makeText(this@RemoteAgentSettingsActivity,
                    "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
            btnStopAgent.isEnabled = true
        }
    }

    private fun checkAgentStatus() {
        if (selectedAgentId.isEmpty()) return

        lifecycleScope.launch {
            try {
                val response = GrpcClient.getAgentProcessStatus(selectedAgentId, userId)
                if (response.running) {
                    updateAgentStatusText("подключён", true)
                } else {
                    updateAgentStatusText("не запущен", false)
                }
            } catch (_: Exception) {
                updateAgentStatusText("не запущен", false)
            }
        }
    }

    private fun updateAgentStatusText(status: String, connected: Boolean) {
        val theme = ThemeStore.currentTheme()
        val txtColor = if (connected) {
            Color.parseColor("#4CAF50") // green
        } else {
            ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        }
        agentStatusText.text = "Статус: $status"
        agentStatusText.setTextColor(txtColor)
    }

    private fun saveSelectedAgent() {
        getSharedPreferences("lavender_prefs", MODE_PRIVATE).edit()
            .putString(PREF_AGENT_ID, selectedAgentId)
            .putString(PREF_AGENT_NAME, selectedAgentName)
            .putString(PREF_AGENT_TOKEN, selectedToken)
            .putString(PREF_AGENT_SCRIPT_PATH, DEFAULT_AGENT_SCRIPT_PATH)
            .apply()
    }

    private fun restoreSelectedAgent() {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        selectedAgentId = prefs.getString(PREF_AGENT_ID, "") ?: ""
        selectedAgentName = prefs.getString(PREF_AGENT_NAME, "") ?: ""
        selectedToken = prefs.getString(PREF_AGENT_TOKEN, "") ?: ""
        if (selectedAgentId.isNotEmpty()) {
            // Check actual process status
            lifecycleScope.launch {
                try {
                    val response = GrpcClient.getAgentProcessStatus(selectedAgentId, userId)
                    if (response.running) {
                        updateAgentStatusText("подключён", true)
                    } else {
                        updateAgentStatusText("не запущен", false)
                    }
                } catch (_: Exception) {
                    updateAgentStatusText("не запущен", false)
                }
            }
        }
    }

    // ===== Hermes Gateway (SSH Tunnel) =====

    private fun restoreGatewaySettings() {
        val settings = gatewayManager.loadSettings()
        etSshHost.setText(settings.sshHost)
        etSshPort.setText(settings.sshPort.toString())
        etSshUser.setText(settings.sshUser)
        etServerHost.setText(settings.serverHost)
        etServerPort.setText(settings.serverPort.toString())
        etLocalPort.setText(settings.localPort.toString())
        cbAutoConnect.isChecked = settings.autoConnect
        updateTunnelStatusUI()
    }

    private fun updateTunnelStatusUI() {
        val theme = ThemeStore.currentTheme()
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val greenColor = Color.parseColor("#4CAF50")

        if (gatewayManager.isTunnelActive()) {
            val localAddr = gatewayManager.getLocalAddress()
            tvTunnelStatus.text = "Туннель: АКТИВЕН → $localAddr"
            tvTunnelStatus.setTextColor(greenColor)
            btnCreateTunnel.isEnabled = false
            btnCloseTunnel.isEnabled = true
        } else {
            tvTunnelStatus.text = "Туннель: не создан"
            tvTunnelStatus.setTextColor(txtColor)
            btnCreateTunnel.isEnabled = true
            btnCloseTunnel.isEnabled = false
        }
    }

    private fun createTunnel() {
        val sshHost = etSshHost.text.toString().trim()
        val sshPort = etSshPort.text.toString().trim().toIntOrNull() ?: 22
        val sshUser = etSshUser.text.toString().trim()
        val serverHost = etServerHost.text.toString().trim().ifEmpty { "localhost" }
        val serverPort = etServerPort.text.toString().trim().toIntOrNull() ?: 50051
        val localPort = etLocalPort.text.toString().trim().toIntOrNull() ?: 50052

        if (sshHost.isEmpty()) {
            Toast.makeText(this, "Укажите SSH хост", Toast.LENGTH_SHORT).show()
            return
        }

        tvTunnelStatus.text = "Туннель: подключение..."
        tvTunnelStatus.setTextColor(Color.parseColor("#FFA000")) // amber
        btnCreateTunnel.isEnabled = false

        // Run SSH tunnel creation in background
        activityScope.launch(Dispatchers.IO) {
            try {
                val ok = gatewayManager.createTunnel(
                    sshHost = sshHost,
                    sshPort = sshPort,
                    sshUser = sshUser,
                    serverHost = serverHost,
                    serverPort = serverPort,
                    localPort = localPort
                )
                // Update UI on main thread
                activityScope.launch(Dispatchers.Main) {
                    if (ok) {
                        Toast.makeText(this@RemoteAgentSettingsActivity,
                            "Туннель создан: localhost:$localPort → $serverHost:$serverPort",
                            Toast.LENGTH_LONG).show()
                        // If server address is empty, pre-fill it with local tunnel
                        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
                        val curAddr = prefs.getString("server_address", "")
                        if (curAddr.isNullOrEmpty()) {
                            prefs.edit().putString("server_address", "localhost")
                                .putString("server_port", localPort.toString()).apply()
                        }
                    } else {
                        Toast.makeText(this@RemoteAgentSettingsActivity,
                            "Ошибка создания туннеля", Toast.LENGTH_LONG).show()
                    }
                    updateTunnelStatusUI()
                }
            } catch (e: Exception) {
                activityScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@RemoteAgentSettingsActivity,
                        "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    updateTunnelStatusUI()
                }
            }
        }
    }

    private fun closeTunnel() {
        gatewayManager.closeTunnel()
        Toast.makeText(this, "Туннель закрыт", Toast.LENGTH_SHORT).show()
        updateTunnelStatusUI()
    }
}
