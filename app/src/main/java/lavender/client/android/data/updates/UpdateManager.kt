package lavender.client.android.data.updates

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import lavender.client.android.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages app updates, including checking and background downloading.
 */
class UpdateManager(private val context: Context) {
    private val TAG = "UpdateManager"
    private val prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE)

    fun checkForUpdates(onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val url = URL(UpdateUtils.getVersionUrl(context))
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val latestVersion = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                    val currentVersion = BuildConfig.VERSION_NAME
                    val isAvailable = UpdateUtils.isUpdateAvailable(currentVersion, latestVersion)
                    
                    prefs.edit {
                        putBoolean("update_available", isAvailable)
                        putString("latest_version", latestVersion)
                        if (!isAvailable) {
                            putBoolean("update_downloaded", false)
                            remove("apk_path")
                            WorkManager.getInstance(context).cancelUniqueWork("update_download")
                        }
                    }
                    onResult(isAvailable, latestVersion)
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
            }
        }.start()
    }

    fun startDownload(isAuto: Boolean = false) {
        prefs.edit { putBoolean("update_downloading", true) }
        
        val constraintsBuilder = androidx.work.Constraints.Builder()
        if (isAuto) {
            constraintsBuilder.setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
            constraintsBuilder.setRequiresBatteryNotLow(true)
        } else {
            constraintsBuilder.setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
        }

        val workRequest = OneTimeWorkRequestBuilder<DownloadUpdateWorker>()
            .setConstraints(constraintsBuilder.build())
            .build()
            
        WorkManager.getInstance(context).enqueueUniqueWork(
            "update_download",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
    }

    fun cancelDownload() {
        WorkManager.getInstance(context).cancelUniqueWork("update_download")
        prefs.edit { 
            putBoolean("update_downloading", false) 
        }
    }
}
