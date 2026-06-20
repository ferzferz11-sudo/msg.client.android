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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

class RemoteAgentSettingsActivity : AppCompatActivity(),
    RemoteAgentManager.RemoteAgentStateListener {

    // Toolbar
    private lateinit var toolbar: MaterialToolbar

    // Tabs
    private lateinit var tabLayout: TabLayout
    private lateinit var tabGateway: View
    private lateinit var tabToken: View

    // Gateway tab views
    private lateinit var gatewayForm: LinearLayout
    private lateinit var gatewayConnectedStatus: LinearLayout
    private lateinit var etSshHost: com.google.android.material.textfield.TextInputEditText
    private lateinit var etSshPort: com.google.android.material.textfield.TextInputEditText
    private lateinit var etSshUser: com.google.android.material.textfield.TextInputEditText
    private lateinit var etSshPassword: com.google.android.material.textfield.TextInputEditText
    private lateinit var etServerHost: com.google.android.material.textfield.TextInputEditText
    private lateinit var etServerPort: com.google.android.material.textfield.TextInputEditText
    private lateinit var etLocalPort: com.google.android.material.textfield.TextInputEditText
    private lateinit var cbAutoConnect: MaterialCheckBox
    private lateinit var btnCreateTunnel: MaterialButton
    private lateinit var btnCloseTunnel: MaterialButton
    private lateinit var btnDisconnectGateway: MaterialButton
    private lateinit var tvGatewayStatus: TextView
    private lateinit var tvTunnelAddress: TextView

    // Token tab views
    private lateinit var tokenListContainer: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var btnGenerateToken: MaterialButton
    private lateinit var btnStartAgent: MaterialButton
    private lateinit var btnStopAgent: MaterialButton
    private lateinit var agentStatusText: TextView

    private var userId: String = ""
    private val tokens = mutableListOf<TokenInfo>()
    private var selectedAgentId: String = ""
    private var selectedAgentName: String = ""
    private var selectedToken: String = ""
    private var serviceBound = false
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var gatewayManager: HermesGatewayManager

    companion object {
        private const val PREF_AGENT_ID = "remote_agent_id"
        private const val PREF_AGENT_NAME = "remote_agent_name"
        private const val PREF_AGENT_TOKEN = "remote_agent_token"
        private const val PREF_AGENT_SCRIPT_PATH = "remote_agent_script_path"
        private val DEFAULT_AGENT_SCRIPT_PATH = "/root/msg.remote.agent/hermes_remote_agent.py"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_agent_settings)

        userId = SessionManager.session.value.userId
        RemoteAgentManager.init(applicationContext)
        gatewayManager = HermesGatewayManager(this)

        initViews()
        setupTabs()
        setupGatewayTab()
        setupTokenTab()
        applyTheme()

        restoreGatewaySettings()
        restoreSelectedAgent()
        loadTokens()
        checkAgentStatus()

        updateTunnelStatusUI()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        tabLayout = findViewById(R.id.tabLayout)
        tabGateway = findViewById(R.id.tabGateway)
        tabToken = findViewById(R.id.tabToken)

        // Gateway
        gatewayForm = findViewById(R.id.gatewayForm)
        gatewayConnectedStatus = findViewById(R.id.gatewayConnectedStatus)
        etSshHost = findViewById(R.id.etSshHost)
        etSshPort = findViewById(R.id.etSshPort)
        etSshUser = findViewById(R.id.etSshUser)
        etSshPassword = findViewById(R.id.etSshPassword)
        etServerHost = findViewById(R.id.etServerHost)
        etServerPort = findViewById(R.id.etServerPort)
        etLocalPort = findViewById(R.id.etLocalPort)
        cbAutoConnect = findViewById(R.id.cbAutoConnect)
        btnCreateTunnel = findViewById(R.id.btnCreateTunnel)
        btnCloseTunnel = findViewById(R.id.btnCloseTunnel)
        btnDisconnectGateway = findViewById(R.id.btnDisconnectGateway)
        tvGatewayStatus = findViewById(R.id.tvGatewayStatus)
        tvTunnelAddress = findViewById(R.id.tvTunnelAddress)

        // Token
        tokenListContainer = findViewById(R.id.tokenListContainer)
        emptyText = findViewById(R.id.emptyText)
        btnGenerateToken = findViewById(R.id.btnGenerateToken)
        btnStartAgent = findViewById(R.id.btnStartAgent)
        btnStopAgent = findViewById(R.id.btnStopAgent)
        agentStatusText = findViewById(R.id.agentStatusText)
    }

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.tab_gateway)))
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.tab_token)))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                when (tab.position) {
                    0 -> { tabGateway.visibility = View.VISIBLE; tabToken.visibility = View.GONE }
                    1 -> { tabGateway.visibility = View.GONE; tabToken.visibility = View.VISIBLE }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupGatewayTab() {
        toolbar.setNavigationOnClickListener { finish() }

        btnCreateTunnel.setOnClickListener { createTunnel() }
        btnCloseTunnel.setOnClickListener { closeTunnel() }
        btnDisconnectGateway.setOnClickListener { closeTunnel() }

        cbAutoConnect.setOnCheckedChangeListener { _, isChecked ->
            gatewayManager.setAutoConnect(isChecked)
        }
    }

    private fun setupTokenTab() {
        btnGenerateToken.setOnClickListener { showTokenDialog() }
        btnStartAgent.setOnClickListener { startAgentOnServer() }
        btnStopAgent.setOnClickListener { stopAgentOnServer() }
    }

    private fun applyTheme() {
        val theme = ThemeStore.currentTheme()
        val bgColor = ThemeUtils.parseSafeColor(theme.backgroundColor, Color.BLACK)
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val surfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
        val hintColor = ThemeUtils.adjustAlpha(txtColor, 0.6f)

        window.decorView.setBackgroundColor(bgColor)
        toolbar.setBackgroundColor(surfaceColor)
        toolbar.setTitleTextColor(txtColor)
        toolbar.setNavigationIconTint(txtColor)

        // Theme input fields
        val inputFields = listOf(etSshHost, etSshPort, etSshUser, etSshPassword, etServerHost, etServerPort, etLocalPort)
        inputFields.forEach { field ->
            field.setTextColor(txtColor)
            field.setHintTextColor(hintColor)
        }

        // Theme labels
        tvGatewayStatus?.setTextColor(txtColor)
        tvTunnelAddress?.setTextColor(txtColor)

        btnGenerateToken.setBackgroundColor(primColor)
        btnGenerateToken.setTextColor(ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE))
        btnStartAgent.setBackgroundColor(primColor)
        btnStartAgent.setTextColor(ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE))
        btnStopAgent.setBackgroundColor(Color.parseColor("#F44336"))
        btnStopAgent.setTextColor(Color.WHITE)

        agentStatusText.setTextColor(txtColor)
    }

    private fun createTunnel() {
        val sshHost = etSshHost.text.toString().trim()
        val sshPort = etSshPort.text.toString().toIntOrNull() ?: 22
        val sshUser = etSshUser.text.toString().trim()
        val sshPassword = etSshPassword.text.toString()
        val serverHost = etServerHost.text.toString().trim().ifEmpty { "localhost" }
        val serverPort = etServerPort.text.toString().toIntOrNull() ?: 50051
        val localPort = etLocalPort.text.toString().toIntOrNull() ?: 50052

        if (sshHost.isEmpty()) {
            Toast.makeText(this, R.string.ssh_host_empty, Toast.LENGTH_SHORT).show()
            return
        }

        btnCreateTunnel.isEnabled = false
        btnCreateTunnel.text = getString(R.string.connecting)

        RemoteAgentManager.createTunnel(
            sshHost = sshHost,
            sshPort = sshPort,
            sshUser = sshUser,
            sshPassword = sshPassword,
            serverHost = serverHost,
            serverPort = serverPort,
            localPort = localPort
        ) { success, error, type ->
            runOnUiThread {
                btnCreateTunnel.isEnabled = true
                btnCreateTunnel.text = getString(R.string.connect)
                if (success) {
                    Toast.makeText(this, R.string.gateway_connected, Toast.LENGTH_SHORT).show()
                    updateTunnelStatusUI()
                } else {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun closeTunnel() {
        RemoteAgentManager.closeTunnel()
        updateTunnelStatusUI()
        Toast.makeText(this, R.string.gateway_disconnected, Toast.LENGTH_SHORT).show()
    }

    private fun updateTunnelStatusUI() {
        val isConnected = RemoteAgentManager.isTunnelActive()
        if (isConnected) {
            gatewayForm.visibility = View.GONE
            gatewayConnectedStatus.visibility = View.VISIBLE
            val settings = gatewayManager.loadSettings()
            tvGatewayStatus.text = getString(R.string.gateway_format, settings.sshHost, settings.sshPort)
            tvTunnelAddress.text = getString(R.string.tunnel_format, RemoteAgentManager.getTunnelAddress())
            btnCloseTunnel.isEnabled = true

            // Persist connected agent
            selectedAgentId = "gateway_agent"
            selectedAgentName = getString(R.string.agent_via_gateway, settings.sshHost)
            saveSelectedAgent()
        } else {
            gatewayForm.visibility = View.VISIBLE
            gatewayConnectedStatus.visibility = View.GONE
            btnCloseTunnel.isEnabled = false
        }
    }

    private fun restoreGatewaySettings() {
        val settings = gatewayManager.loadSettings()
        etSshHost.setText(settings.sshHost)
        etSshPort.setText(settings.sshPort.toString())
        etSshUser.setText(settings.sshUser)
        etServerHost.setText(settings.serverHost)
        etServerPort.setText(settings.serverPort.toString())
        etLocalPort.setText(settings.localPort.toString())
        cbAutoConnect.isChecked = gatewayManager.isAutoConnect()
    }

    private fun restoreSelectedAgent() {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        selectedAgentId = prefs.getString(PREF_AGENT_ID, "") ?: ""
        selectedAgentName = prefs.getString(PREF_AGENT_NAME, "") ?: ""
        selectedToken = prefs.getString(PREF_AGENT_TOKEN, "") ?: ""
    }

    private fun saveSelectedAgent() {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_AGENT_ID, selectedAgentId)
            .putString(PREF_AGENT_NAME, selectedAgentName)
            .putString(PREF_AGENT_TOKEN, selectedToken)
            .apply()
    }

    // ===== Token tab =====

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
            } catch (e: Exception) {
                // Token list load failed — not critical, log silently
                android.util.Log.e("RemoteAgentSettings", "Failed to load tokens: ${e.message}")
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

                val agentName = view.findViewById<TextView>(R.id.tvTokenAgentName)
                val status = view.findViewById<TextView>(R.id.tvTokenStatus)
                val hash = view.findViewById<TextView>(R.id.tvTokenHash)
                val caps = view.findViewById<TextView>(R.id.tvTokenCapabilities)
                val expires = view.findViewById<TextView>(R.id.tvTokenExpires)
                val revokeBtn = view.findViewById<MaterialButton>(R.id.btnRevoke)

                agentName.text = token.agentName
                agentName.setTextColor(txtColor)
                status.text = getString(R.string.active)
                status.setTextColor(Color.parseColor("#4CAF50"))
                status.setBackgroundColor(Color.parseColor("#1A4CAF50"))
                status.setPadding(12, 4, 12, 4)
                hash.text = getString(R.string.token_hash_format, token.tokenHash.take(16))
                hash.setTextColor(txtSecondary)
                caps.text = getString(R.string.capabilities_format, token.capabilities.joinToString(", "))
                caps.setTextColor(txtSecondary)
                expires.text = if (token.expiresAt.isNotEmpty()) getString(R.string.expires_format, token.expiresAt) else getString(R.string.no_expiry)
                expires.setTextColor(txtSecondary)

                val copyTokenBtn = view.findViewById<MaterialButton>(R.id.btnCopyToken)
                val copyCmdBtn = view.findViewById<MaterialButton>(R.id.btnCopyCmd)

                revokeBtn.setTextColor(Color.parseColor("#F44336"))
                revokeBtn.setOnClickListener { confirmRevoke(token) }

                copyTokenBtn.setTextColor(primColor)
                copyTokenBtn.setOnClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Agent Token", token.fullToken))
                    Toast.makeText(this, getString(R.string.token_copied), Toast.LENGTH_SHORT).show()
                }

                copyCmdBtn.setTextColor(primColor)
                copyCmdBtn.setOnClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Agent Command", token.command))
                    Toast.makeText(this, getString(R.string.command_copied), Toast.LENGTH_SHORT).show()
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
                val resp = GrpcClient.generateAgentToken(
                    agentId = agentId, agentName = agentName,
                    capabilities = capabilities, ttlHours = ttlHours, adminUserId = userId
                )
                if (resp.success) {
                    selectedAgentId = agentId
                    selectedAgentName = agentName
                    selectedToken = resp.token
                    saveSelectedAgent()
                    selectedToken = resp.token

                    val expiresAt = if (resp.expiresAt > 0) {
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                            .format(java.util.Date(resp.expiresAt * 1000))
                    } else ""

                    val agentCmd = "python3 $DEFAULT_AGENT_SCRIPT_PATH --server <server:port> --token ${resp.token}"

                    tokens.add(TokenInfo(
                        id = 0, agentId = agentId, agentName = agentName,
                        tokenHash = resp.token.take(16), capabilities = capabilities,
                        createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date()),
                        expiresAt = expiresAt, revoked = false, createdBy = userId,
                        fullToken = resp.token, command = agentCmd
                    ))
                    renderTokens()
                    showTokenResultDialog(resp.token, agentId, agentName)
                } else {
                    Toast.makeText(this@RemoteAgentSettingsActivity, getString(R.string.error_colon, resp.error), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RemoteAgentSettingsActivity, getString(R.string.error_colon, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showTokenResultDialog(token: String, agentId: String, agentName: String) {
        val theme = ThemeStore.currentTheme()
        val bgColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
            setBackgroundColor(bgColor)
            id = View.generateViewId()
        }

        val label = TextView(this).apply {
            text = getString(R.string.token_generated_hint)
            setTextColor(txtColor); textSize = 14f
            id = View.generateViewId()
        }
        val tokenView = TextView(this).apply {
            text = token; setTextColor(txtColor); textSize = 13f
            setPadding(0, 16, 0, 16); setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            id = View.generateViewId()
        }
        container.addView(label)
        container.addView(tokenView)

        val agentCmd = "python3 $DEFAULT_AGENT_SCRIPT_PATH --server <server:port> --token $token"
        val cmdLabel = TextView(this).apply {
            text = getString(R.string.agent_command_hint); setTextColor(txtColor); textSize = 14f; setPadding(0, 16, 0, 0)
            id = View.generateViewId()
        }
        val cmdView = TextView(this).apply {
            text = agentCmd; setTextColor(txtColor); textSize = 11f
            setPadding(0, 8, 0, 8); setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundColor(ThemeUtils.parseSafeColor(theme.backgroundColor, Color.BLACK))
            id = View.generateViewId()
        }
        container.addView(cmdLabel)
        container.addView(cmdView)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.token_generated_title))
            .setView(container)
            .setPositiveButton(getString(R.string.copy_token), null)
            .setNeutralButton(getString(R.string.copy_command), null)
            .setNegativeButton(getString(R.string.close), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Agent Token", token))
                Toast.makeText(this, R.string.token_copied, Toast.LENGTH_SHORT).show()
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Agent Command", agentCmd))
                Toast.makeText(this, R.string.command_copied, Toast.LENGTH_SHORT).show()
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { dialog.dismiss() }
        }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(primColor)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(primColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(txtColor)
    }

    private fun confirmRevoke(token: TokenInfo) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.revoke_token_title))
            .setMessage(getString(R.string.revoke_token_message, token.agentName))
            .setPositiveButton(getString(R.string.revoke)) { _, _ ->
                activityScope.launch {
                    try {
                        val resp = GrpcClient.revokeAgentToken(token.agentId, userId)
                        if (resp.success) { loadTokens() }
                    } catch (e: Exception) {
                        Toast.makeText(this@RemoteAgentSettingsActivity, getString(R.string.error_colon, e.message), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun startAgentOnServer() {
        if (selectedToken.isEmpty()) {
            Toast.makeText(this, getString(R.string.generate_token_first), Toast.LENGTH_SHORT).show()
            return
        }
        activityScope.launch {
            try {
                val resp = GrpcClient.startAgentOnServer(
                    agentId = selectedAgentId, agentName = selectedAgentName,
                    token = selectedToken, serverAddress = "", adminUserId = userId
                )
                if (resp.success) {
                    Toast.makeText(this@RemoteAgentSettingsActivity, getString(R.string.agent_started, resp.pid), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@RemoteAgentSettingsActivity, getString(R.string.error_colon, resp.error), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RemoteAgentSettingsActivity, getString(R.string.error_colon, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun stopAgentOnServer() {
        if (selectedAgentId.isEmpty()) {
            Toast.makeText(this, getString(R.string.agent_not_selected), Toast.LENGTH_SHORT).show()
            return
        }
        activityScope.launch {
            try {
                val resp = GrpcClient.stopAgentOnServer(selectedAgentId, userId)
                if (resp.success) {
                    Toast.makeText(this@RemoteAgentSettingsActivity, getString(R.string.agent_stopped), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@RemoteAgentSettingsActivity, getString(R.string.error_colon, resp.error), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@RemoteAgentSettingsActivity, getString(R.string.error_colon, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkAgentStatus() {
        activityScope.launch {
            try {
                if (selectedAgentId.isNotEmpty()) {
                    val status = GrpcClient.getRemoteAgentStatus(selectedAgentId)
                    runOnUiThread {
                        val txtColor = ThemeUtils.parseSafeColor(ThemeStore.currentTheme().textPrimaryColor, Color.WHITE)
                        agentStatusText.setTextColor(txtColor)
                        if (status.status == "connected") {
                            agentStatusText.text = getString(R.string.status_connected)
                        } else {
                            agentStatusText.text = getString(R.string.status_disconnected, status.status)
                        }
                    }
                } else {
                    runOnUiThread {
                        agentStatusText.text = getString(R.string.status_not_running)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    agentStatusText.text = getString(R.string.status_check_error)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        RemoteAgentManager.bind(this)
        serviceBound = true
        updateTunnelStatusUI()
        checkAgentStatus()
    }

    override fun onPause() {
        super.onPause()
        if (serviceBound) {
            RemoteAgentManager.unbind(this)
            serviceBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    override fun onStateChanged(state: RemoteAgentManager.AgentConnectionState) {
        runOnUiThread {
            updateTunnelStatusUI()
        }
    }
}
