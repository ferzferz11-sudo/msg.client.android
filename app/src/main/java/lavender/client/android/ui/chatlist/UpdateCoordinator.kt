package lavender.client.android.ui.chatlist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.BuildConfig
import lavender.client.android.R
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.updates.UpdateManager
import lavender.client.android.data.updates.UpdateUtils
import lavender.client.android.ui.widget.StandardBottomSheet
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * UpdateCoordinator — координирует проверку обновлений, скачивание и UI.
 *
 * Вынесен из ChatListActivity для соблюдения модульной архитектуры.
 * Отвечает за:
 * - Фоновую проверку версий (silent check)
 * - Ручную проверку с диалогом
 * - Управление индикатором обновления в toolbar
 * - Диалоги прогресса и установки
 * - Проверку объявлений (announcements)
 */
class UpdateCoordinator(
    private val activity: ChatListActivity,
    private val updateManager: UpdateManager
) {
    private val context: Context get() = activity

    // Ссылки на view элементы (lazy — получаем когда нужны)
    private val llUpdateContainer: View? get() = activity.findViewById(R.id.llUpdateContainer)
    private val tvUpdateAvailable: TextView? get() = activity.findViewById(R.id.tvUpdateAvailable)
    private val tvUpdateProgress: TextView? get() = activity.findViewById(R.id.tvUpdateProgress)

    // Listener
    val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        activity.runOnUiThread { updateIndicatorVisibility() }
    }

    // ======= Silent check (called once on startup) =======

    fun checkForUpdatesSilently() {
        // Only check announcements, don't auto-download
        checkAnnouncements()
        updateManager.checkForUpdates { isAvailable, _ ->
            activity.runOnUiThread {
                updateIndicatorVisibility()
            }
        }
    }

    // ======= Manual check (called from user menu) =======

    fun checkManualUpdate() {
        val currentVersion = BuildConfig.VERSION_NAME
        updateManager.checkForUpdates { isAvailable, latestVersion ->
            activity.runOnUiThread {
                showUpdateDialog(currentVersion, latestVersion)
            }
        }
    }

    // ======= Update dialog (bottom sheet) =======

    private fun showUpdateDialog(current: String, latest: String) {
        val sheet = StandardBottomSheet(context, R.layout.bottom_sheet_update)

        val titleView = sheet.findViewById<TextView>(R.id.updateTitle)
        val messageView = sheet.findViewById<TextView>(R.id.updateMessage)
        val btnUpdate = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUpdate)
        val btnCancel = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val updateIcon = sheet.findViewById<ImageView>(R.id.updateIcon)

        val isAvailable = UpdateUtils.isUpdateAvailable(current, latest)
        if (!isAvailable) {
            titleView?.text = context.getString(R.string.ok)
            updateIcon?.setImageResource(R.drawable.ic_checked)
            btnUpdate?.text = context.getString(R.string.force_download)
        }

        messageView?.text = context.getString(R.string.version_info_format, current, latest)
        btnCancel?.setOnClickListener { sheet.dismiss() }
        btnUpdate?.setOnClickListener {
            sheet.dismiss()
            updateManager.startDownload()
            updateIndicatorVisibility()
        }

        sheet.show()
    }

    // ======= Update indicator in toolbar =======

    fun updateIndicatorVisibility() {
        val prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE)
        val isAvailable = prefs.getBoolean("update_available", false)
        val isDownloaded = prefs.getBoolean("update_downloaded", false)
        val isDownloading = prefs.getBoolean("update_downloading", false)

        // Show container if update is ready or downloading
        llUpdateContainer?.isVisible = isAvailable || isDownloading || isDownloaded

        if (isDownloading) {
            tvUpdateAvailable?.isVisible = false
            tvUpdateProgress?.isVisible = true
        } else {
            tvUpdateProgress?.isVisible = false
            tvUpdateAvailable?.isVisible = isAvailable || isDownloaded
        }

        llUpdateContainer?.setOnClickListener {
            if (isDownloaded) {
                val apkPath = prefs.getString("apk_path", null)
                if (apkPath != null) {
                    UpdateUtils.installApk(context, File(apkPath))
                }
            } else if (isDownloading) {
                showUpdateProgressDialog()
            } else if (isAvailable) {
                updateManager.startDownload()
                updateIndicatorVisibility()
            }
        }
    }

    // ======= Progress dialog (bottom sheet) =======

    private fun showUpdateProgressDialog() {
        val sheet = StandardBottomSheet(context, R.layout.bottom_sheet_update)
        val titleView = sheet.findViewById<TextView>(R.id.updateTitle)
        val messageView = sheet.findViewById<TextView>(R.id.updateMessage)
        val btnUpdate = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnUpdate)
        val btnCancel = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)

        titleView?.text = context.getString(R.string.update_in_progress)
        messageView?.text = context.getString(R.string.downloading_update)
        btnUpdate?.text = context.getString(R.string.continue_label)
        btnUpdate?.setOnClickListener { sheet.dismiss() }
        btnCancel?.text = context.getString(R.string.cancel_update)
        btnCancel?.setOnClickListener {
            sheet.dismiss()
            updateManager.cancelDownload()
            updateIndicatorVisibility()
            Toast.makeText(context, R.string.update_cancelled, Toast.LENGTH_SHORT).show()
        }
        sheet.show()
    }

    // ======= Announcements (changelog.txt from server) =======

    private fun checkAnnouncements() {
        CoroutineScope(Dispatchers.IO).launch {
            checkAnnouncementsInternal()
        }
    }

    private suspend fun checkAnnouncementsInternal() {
        try {
            val url = URL("${CredentialStore.getApkServerUrl(context)}/changelog.txt?t=${System.currentTimeMillis()}")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                var text = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                text = text.replace("\r\n", "\n").replace("\r", "\n")

                if (text.isNotEmpty()) {
                    val prefs = context.getSharedPreferences("AnnouncementPrefs", Context.MODE_PRIVATE)
                    var lastRead = prefs.getString("last_read_text", "") ?: ""
                    lastRead = lastRead.trim().replace("\r\n", "\n").replace("\r", "\n")

                    val isNew = text != lastRead
                    prefs.edit {
                        putString("current_text", text)
                        putBoolean("show_icon", isNew)
                    }

                    withContext(Dispatchers.Main) {
                        updateIndicatorVisibility()
                    }
                }
            }
            connection.disconnect()
        } catch (_: Exception) {}
    }

    // ======= Notification for downloaded update =======

    private fun showUpdateAvailableNotification(latestVersion: String) {
        val intent = Intent(context, ChatListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1005, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val whatsNewIntent = Intent(context, ChatListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_show_whats_new", true)
        }
        val whatsNewPendingIntent = PendingIntent.getActivity(
            context, 1006, whatsNewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(UpdateUtils.CHANNEL_ID, "Updates", NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, UpdateUtils.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update_available)
            .setContentTitle(context.getString(R.string.update_available))
            .setContentText(context.getString(R.string.version_available, latestVersion))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_star, context.getString(R.string.whats_new), whatsNewPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        notificationManager.notify(1007, notification)
    }
}
