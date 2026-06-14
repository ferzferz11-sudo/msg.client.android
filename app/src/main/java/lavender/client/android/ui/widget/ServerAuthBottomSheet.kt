package lavender.client.android.ui.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import lavender.client.android.R
import lavender.client.android.theme.Theme
import lavender.client.android.theme.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Server Auth Bottom Sheet — first screen when user selects a server.
 *
 * Shows: logo, server name, server address, online status (via /health), login/register buttons.
 * Used in: ChatListActivity (first login), ServersActivity (server selection).
 */
class ServerAuthBottomSheet(
    context: Context,
    private val serverName: String,
    private val serverHost: String,
    private val serverPort: Int,
    private val onLogin: () -> Unit,
    private val onRegister: () -> Unit,
    theme: Theme = ThemeStore.currentTheme()
) : StandardBottomSheet(context, R.layout.dialog_server_auth, theme) {

    private var statusIndicator: View? = null
    private var statusText: TextView? = null

    init {
        initViews()
        checkServerHealth()
    }

    private fun initViews() {
        val logo = findViewById<ImageView>(R.id.serverAuthLogo)
        logo?.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$serverHost/"))
                context.startActivity(intent)
            } catch (_: Exception) {}
        }

        findViewById<TextView>(R.id.serverAuthName)?.text = serverName
        findViewById<TextView>(R.id.serverAuthAddress)?.text = "$serverHost:$serverPort"

        statusIndicator = findViewById(R.id.serverAuthStatusIndicator)
        statusText = findViewById(R.id.serverAuthStatusText)

        // App version at bottom
        try {
            val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            findViewById<TextView>(R.id.serverAuthAppVersion)?.text = context.getString(R.string.app_version_format, versionName)
        } catch (_: Exception) {}

        findViewById<MaterialButton>(R.id.btnServerLogin)?.setOnClickListener {
            dismiss()
            onLogin()
        }

        findViewById<MaterialButton>(R.id.btnServerRegister)?.setOnClickListener {
            dismiss()
            onRegister()
        }
    }

    private fun checkServerHealth() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("http://$serverHost:8082/health")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.requestMethod = "GET"
                val responseCode = connection.responseCode
                connection.disconnect()

                val isOnline = responseCode in 200..299
                withContext(Dispatchers.Main) {
                    updateStatusIndicator(isOnline)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatusIndicator(false)
                }
            }
        }
    }

    private fun updateStatusIndicator(isOnline: Boolean) {
        val indicatorColor = if (isOnline) Color.parseColor("#4CAF50") else Color.parseColor("#9E9E9E")
        statusIndicator?.background?.setTint(indicatorColor)
        statusText?.text = if (isOnline) "Online" else "Offline"
    }
}
