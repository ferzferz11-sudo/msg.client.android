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
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

class RemoteAgentSettingsActivity : AppCompatActivity() {

    private lateinit var tokenListContainer: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var btnGenerateToken: MaterialButton

    private var userId: String = ""
    private val tokens = mutableListOf<TokenInfo>()

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

        // Toolbar
        toolbar.setBackgroundColor(surfaceColor)
        toolbar.setTitleTextColor(txtColor)
        toolbar.setNavigationIconTint(txtColor)
        toolbar.setNavigationOnClickListener { finish() }

        // Style the generate button
        btnGenerateToken.setBackgroundColor(ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE))
        btnGenerateToken.setTextColor(ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE))

        // Generate token button
        btnGenerateToken.setOnClickListener {
            showTokenDialog()
        }

        // Load tokens
        loadTokens()
    }

    private fun loadTokens() {
        lifecycleScope.launch {
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
        lifecycleScope.launch {
            try {
                android.util.Log.d("RemoteAgentSettings", "generateToken: agentId=$agentId name=$agentName caps=$capabilities ttl=$ttlHours userId=$userId")
                val response = GrpcClient.generateAgentToken(
                    agentId = agentId,
                    agentName = agentName,
                    capabilities = capabilities,
                    ttlHours = ttlHours,
                    adminUserId = userId
                )
                android.util.Log.d("RemoteAgentSettings", "generateToken response: success=${response.success} token=${response.token.take(20)} error=${response.error}")
                if (response.success) {
                    showTokenResultDialog(response.token)
                    loadTokens()
                } else {
                    Toast.makeText(this@RemoteAgentSettingsActivity, "Ошибка: ${response.error}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("RemoteAgentSettings", "generateToken error", e)
                Toast.makeText(this@RemoteAgentSettingsActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showTokenResultDialog(token: String) {
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

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(
            this,
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog
        )
            .setTitle("Токен сгенерирован")
            .setView(container)
            .setPositiveButton("Копировать") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Agent Token", token))
                Toast.makeText(this, "Токен скопирован", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Закрыть", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(primColor)
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
            } catch (e: Exception) {
                Toast.makeText(this@RemoteAgentSettingsActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
