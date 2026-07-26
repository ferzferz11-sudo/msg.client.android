package lavender.client.android.ui.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.core.graphics.toColorInt
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.tabs.TabLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

class RemoteAgentSettingsActivity : AppCompatActivity(),
    RemoteAgentManager.RemoteAgentStateListener {

    private lateinit var viewModel: RemoteAgentSettingsViewModel

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

    private var serviceBound = false
    private lateinit var gatewayManager: HermesGatewayManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_agent_settings)

        viewModel = ViewModelProvider(this)[RemoteAgentSettingsViewModel::class.java]

        RemoteAgentManager.init(applicationContext)
        gatewayManager = HermesGatewayManager(this)

        initViews()
        setupTabs()
        setupGatewayTab()
        setupTokenTab()
        applyTheme()

        restoreGatewaySettings()
        observeViewModel()

        viewModel.loadTokens()
        viewModel.checkAgentStatus()

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
        btnStartAgent.setOnClickListener { viewModel.startAgentOnServer() }
        btnStopAgent.setOnClickListener { viewModel.stopAgentOnServer() }
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
        tvGatewayStatus.setTextColor(txtColor)
        tvTunnelAddress.setTextColor(txtColor)

        btnGenerateToken.setBackgroundColor(primColor)
        btnGenerateToken.setTextColor(ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE))
        btnStartAgent.setBackgroundColor(primColor)
        btnStartAgent.setTextColor(ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE))
        btnStopAgent.setBackgroundColor("#F44336".toColorInt())
        btnStopAgent.setTextColor(Color.WHITE)

        agentStatusText.setTextColor(txtColor)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                renderTokens(state.tokens)
                updateAgentStatus(state.agentStatus)
                updateTunnelStatusUI()

                state.successMessage?.let { message ->
                    Toast.makeText(this@RemoteAgentSettingsActivity, message, Toast.LENGTH_SHORT).show()
                    viewModel.clearSuccess()
                }

                state.error?.let { error ->
                    Toast.makeText(this@RemoteAgentSettingsActivity, getString(R.string.error_colon, error), Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }
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
            lifecycleScope.launch {
                btnCreateTunnel.isEnabled = true
                btnCreateTunnel.text = getString(R.string.connect)
                if (success) {
                    Toast.makeText(this@RemoteAgentSettingsActivity, R.string.gateway_connected, Toast.LENGTH_SHORT).show()
                    viewModel.setTunnelActive(true, RemoteAgentManager.getTunnelAddress())
                    gatewayManager.saveSettings(sshHost, sshPort, sshUser, serverHost, serverPort, localPort)
                } else {
                    Toast.makeText(this@RemoteAgentSettingsActivity, error, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun closeTunnel() {
        RemoteAgentManager.closeTunnel()
        viewModel.setTunnelActive(false)
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

    private fun updateAgentStatus(status: String) {
        val theme = ThemeStore.currentTheme()
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        agentStatusText.setTextColor(txtColor)

        when (status) {
            "connected" -> agentStatusText.text = getString(R.string.status_connected)
            "not_running" -> agentStatusText.text = getString(R.string.status_not_running)
            "error" -> agentStatusText.text = getString(R.string.status_check_error)
            else -> agentStatusText.text = getString(R.string.status_disconnected, status)
        }
    }

    // ===== Token tab =====

    private fun renderTokens(tokens: List<TokenInfo>) {
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
                status.setTextColor("#4CAF50".toColorInt())
                status.setBackgroundColor("#1A4CAF50".toColorInt())
                status.setPadding(12, 4, 12, 4)
                hash.text = getString(R.string.token_hash_format, token.tokenHash.take(16))
                hash.setTextColor(txtSecondary)
                caps.text = getString(R.string.capabilities_format, token.capabilities.joinToString(", "))
                caps.setTextColor(txtSecondary)
                expires.text = if (token.expiresAt.isNotEmpty()) getString(R.string.expires_format, token.expiresAt) else getString(R.string.no_expiry)
                expires.setTextColor(txtSecondary)

                val copyTokenBtn = view.findViewById<MaterialButton>(R.id.btnCopyToken)
                val copyCmdBtn = view.findViewById<MaterialButton>(R.id.btnCopyCmd)

                revokeBtn.setTextColor("#F44336".toColorInt())
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
                viewModel.generateToken(agentName, capabilities, ttlHours)
            }
        )
        dialog.show()
    }

    private fun confirmRevoke(token: TokenInfo) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.revoke_token_title))
            .setMessage(getString(R.string.revoke_token_message, token.agentName))
            .setPositiveButton(getString(R.string.revoke)) { _, _ ->
                viewModel.revokeToken(token)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        RemoteAgentManager.bind(this)
        serviceBound = true
        updateTunnelStatusUI()
        viewModel.checkAgentStatus()
    }

    override fun onPause() {
        super.onPause()
        if (serviceBound) {
            RemoteAgentManager.unbind(this)
            serviceBound = false
        }
    }

    override fun onStateChanged(state: RemoteAgentManager.AgentConnectionState) {
        lifecycleScope.launch {
            updateTunnelStatusUI()
        }
    }
}
