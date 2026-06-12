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

    private var userId: String = ""
    private val tokens = mutableListOf<TokenInfo>()
    private var selectedAgentId: String = ""
    private var selectedAgentName: String = ""
    private var selectedToken: String = ""

    // Keys for persisting agent selection across activity recreation
    private companion object {
        private const val PREF_AGENT_ID = "remote_agent_id"
        private const val PREF_AGENT_NAME = "remote_agent_name"
        private const val PREF_AGENT_TOKEN = "remote_agent_token"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_remote_agent_settings)

        userId = SessionManager.session.value.userId

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

        // Load tokens
        loadTokens()

        // Restore previously selected agent from prefs
        restoreSelectedAgent()

        // Check agent status
        checkAgentStatus()
    }

    private fun loadTokens() {
        lifecycleScope.launch {
            try {
                if (BuildConfig.DEBUG) {
                android.util.Log.d("RemoteAgentSettings", "loadTokens: userId=$userId")
                }
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
                if (BuildConfig.DEBUG) {
                android.util.Log.d("RemoteAgentSettings", "loadTokens cancelled")
                }
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

                revokeBtn.setTextColor(Color.parseColor("#F44336"))
                revokeBtn.setOnClickListener {
                    confirmRevoke(token)
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
        if (BuildConfig.DEBUG) {
            android.util.Log.d("RemoteAgentSettings", "generateToken CALLED: agentId=$agentId name=$agentName userId=$userId")
        }
        lifecycleScope.launch {
            try {
                if (BuildConfig.DEBUG) {
                android.util.Log.d("RemoteAgentSettings", "generateToken: agentId=$agentId name=$agentName caps=$capabilities ttl=$ttlHours userId=$userId")
                }
                val response = GrpcClient.generateAgentToken(
                    agentId = agentId,
                    agentName = agentName,
                    capabilities = capabilities,
                    ttlHours = ttlHours,
                    adminUserId = userId
                )
                if (BuildConfig.DEBUG) {
                android.util.Log.d("RemoteAgentSettings", "generateToken response: success=${response.success} token=${response.token.take(20)} error=${response.error}")
                }
                if (response.success) {
                    selectedAgentId = agentId
                    selectedAgentName = agentName
                    selectedToken = response.token
                    if (BuildConfig.DEBUG) {
                        android.util.Log.d("RemoteAgentSettings", "Token saved: agentId=$agentId token=${response.token.take(20)}... selectedToken=${selectedToken.take(20)}...")
                    }
                    saveSelectedAgent()
                    showTokenResultDialog(response.token, agentId, agentName)
                    loadTokens()
                } else {
                    if (BuildConfig.DEBUG) {
                        android.util.Log.e("RemoteAgentSettings", "Token generation failed: ${response.error}")
                    }
                    Toast.makeText(this@RemoteAgentSettingsActivity, "Ошибка: ${response.error}", Toast.LENGTH_LONG).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (BuildConfig.DEBUG) {
                android.util.Log.d("RemoteAgentSettings", "generateToken cancelled")
                }
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                android.util.Log.e("RemoteAgentSettings", "generateToken error", e)
                }
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

        // Build agent command
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val serverAddr = prefs.getString("server_address", "") ?: ""
        val serverPort = prefs.getString("server_port", "50051") ?: "50051"
        val fullServer = if (serverAddr.isNotEmpty()) "$serverAddr:$serverPort" else "<server:port>"
        val agentCmd = "python3 hermes_remote_agent.py --server $fullServer --token $token"

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
            .setPositiveButton("Копировать токен") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Agent Token", token))
                Toast.makeText(this, "Токен скопирован", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Копировать команду") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Agent Command", agentCmd))
                Toast.makeText(this, "Команда скопирована", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Закрыть", null)
            .create()

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
        lifecycleScope.launch {
            try {
                val response = GrpcClient.revokeAgentToken(agentId, userId)
                if (response.success) {
                    Toast.makeText(this@RemoteAgentSettingsActivity, "Токен отозван", Toast.LENGTH_SHORT).show()
                    loadTokens()
                } else {
                    Toast.makeText(this@RemoteAgentSettingsActivity, "Ошибка: ${response.error}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (BuildConfig.DEBUG) {
                android.util.Log.d("RemoteAgentSettings", "revokeToken cancelled")
                }
                throw e
            } catch (e: Exception) {
                Toast.makeText(this@RemoteAgentSettingsActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ===== Agent Process Management (server-side) =====

    private fun startAgentOnServer() {
        if (BuildConfig.DEBUG) {
            android.util.Log.d("RemoteAgentSettings", "startAgentOnServer: selectedToken=${selectedToken.take(20)}... selectedAgentId=$selectedAgentId")
        }
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
                    agentStatusText.text = "Статус: запущен (PID: ${response.pid})"
                } else {
                    Toast.makeText(this@RemoteAgentSettingsActivity,
                        "Ошибка: ${response.error}", Toast.LENGTH_LONG).show()
                    agentStatusText.text = "Статус: ошибка — ${response.error}"
                }
            } catch (e: Exception) {
                Toast.makeText(this@RemoteAgentSettingsActivity,
                    "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                agentStatusText.text = "Статус: ошибка — ${e.message}"
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
                    agentStatusText.text = "Статус: остановлен"
                } else {
                    Toast.makeText(this@RemoteAgentSettingsActivity,
                        "Ошибка: ${response.error}", Toast.LENGTH_LONG).show()
                    agentStatusText.text = "Статус: ошибка — ${response.error}"
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
                agentStatusText.text = if (response.running) {
                    "Статус: запущен (PID: ${response.pid}, с ${response.startedAt})"
                } else {
                    "Статус: не запущен"
                }
            } catch (_: Exception) {}
        }
    }

    private fun saveSelectedAgent() {
        getSharedPreferences("lavender_prefs", MODE_PRIVATE).edit()
            .putString(PREF_AGENT_ID, selectedAgentId)
            .putString(PREF_AGENT_NAME, selectedAgentName)
            .putString(PREF_AGENT_TOKEN, selectedToken)
            .apply()
    }

    private fun restoreSelectedAgent() {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        selectedAgentId = prefs.getString(PREF_AGENT_ID, "") ?: ""
        selectedAgentName = prefs.getString(PREF_AGENT_NAME, "") ?: ""
        selectedToken = prefs.getString(PREF_AGENT_TOKEN, "") ?: ""
        if (selectedAgentId.isNotEmpty()) {
            agentStatusText.text = "Статус: выбран агент $selectedAgentName"
        }
    }
}
