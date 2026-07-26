package lavender.client.android.ui.widget
import android.util.Log

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import lavender.client.android.R
import lavender.client.android.data.updates.UpdateManager
import lavender.client.android.data.updates.UpdateUtils
import lavender.client.android.theme.Theme
import lavender.client.android.theme.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Server Auth Bottom Sheet — first screen when user selects a server.
 *
 * Shows: logo, server name, server address, online status (via /health), login/register buttons.
 * Also checks for app updates and offers download/install.
 */
class ServerAuthBottomSheet(
    context: Context,
    private val serverName: String,
    private val serverHost: String,
    private val serverPort: Int,
    private val httpPort: Int = if (serverPort == 50052) 8083 else 8082,
    private val onLogin: () -> Unit,
    private val onRegister: () -> Unit,
    theme: Theme = ThemeStore.currentTheme()
) : StandardBottomSheet(context, R.layout.dialog_server_auth, theme) {

    private var statusIndicator: View? = null
    private val updateManager = UpdateManager(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var healthCheckJob: Job? = null

    init {
        initViews()
        checkServerHealth()
        checkForUpdate()
        setOnDismissListener {
            healthCheckJob?.cancel()
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }
    }

    private fun initViews() {
        val logo = findViewById<ImageView>(R.id.serverAuthLogo)
        logo?.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$serverHost/"))
                context.startActivity(intent)
            } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }
        }

        findViewById<TextView>(R.id.serverAuthName)?.text = serverName
        findViewById<TextView>(R.id.serverAuthAddress)?.text = "$serverHost:$serverPort"

        statusIndicator = findViewById(R.id.serverAuthStatusIndicator)

        // App version at bottom
        try {
            val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            findViewById<TextView>(R.id.serverAuthAppVersion)?.text = context.getString(R.string.app_version_format, versionName)
        } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }

        findViewById<MaterialButton>(R.id.btnServerLogin)?.setOnClickListener {
            dismiss()
            onLogin()
        }

        findViewById<MaterialButton>(R.id.btnServerRegister)?.setOnClickListener {
            dismiss()
            onRegister()
        }

        // Update button
        val btnUpdate = findViewById<MaterialButton>(R.id.btnUpdateApp)
        btnUpdate?.setOnClickListener {
            val prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE)
            val isDownloaded = prefs.getBoolean("update_downloaded", false)

            if (isDownloaded) {
                val apkPath = prefs.getString("apk_path", null)
                if (apkPath != null) {
                    UpdateUtils.installApk(context, File(apkPath))
                }
            } else {
                btnUpdate.text = context.getString(R.string.downloading)
                btnUpdate.isEnabled = false
                updateManager.startDownload()
            }
        }

        // Observe download state
        scope.launch {
            updateManager.isDownloadingInstance.collect { downloading ->
                if (!downloading) {
                    val prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE)
                    val isDownloaded = prefs.getBoolean("update_downloaded", false)
                    if (isDownloaded) {
                        btnUpdate?.text = context.getString(R.string.install_update)
                        btnUpdate?.isEnabled = true
                    }
                }
            }
        }
    }

    private fun checkForUpdate() {
        val btnUpdate = findViewById<MaterialButton>(R.id.btnUpdateApp) ?: return

        // Always check for updates first — this clears stale APK if version mismatch
        updateManager.checkForUpdates { isAvailable, _ ->
            val prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE)
            val isDownloaded = prefs.getBoolean("update_downloaded", false)

            btnUpdate.post {
                if (isDownloaded) {
                    val apkPath = prefs.getString("apk_path", null)
                    if (apkPath != null && File(apkPath).exists()) {
                        btnUpdate.text = context.getString(R.string.install_update)
                        btnUpdate.visibility = View.VISIBLE
                    }
                } else if (isAvailable) {
                    btnUpdate.text = context.getString(R.string.update_download_prompt)
                    btnUpdate.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun checkServerHealth() {
        // For dev server (50052), skip HTTP health check — it may be behind NAT/firewall
        // gRPC port is what matters, and we'll know if it works when we try to connect
        if (serverPort == 50052) {
            updateStatusIndicator(true)
            return
        }
        healthCheckJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = URL("http://$serverHost:$httpPort/health")
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
        val indicatorColor = if (isOnline) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
        statusIndicator?.background?.setTint(indicatorColor)
    }
}
