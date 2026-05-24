package lavender.client.android.data.updates

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.content.edit
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lavender.client.android.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

import androidx.work.workDataOf

class DownloadUpdateWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val TAG = "DownloadUpdateWorker"
        private const val NOTIFICATION_ID = 1002
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background download...")
        
        createNotificationChannel()
        setForeground(createForegroundInfo(0))

        val file = File(applicationContext.getExternalFilesDir(null), "lavender_update.apk")
        
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(UpdateUtils.UPDATE_URL)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    applicationContext.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE).edit {
                        putBoolean("update_downloading", false)
                    }
                    return@withContext Result.failure()
                }

                val body = response.body ?: run {
                    applicationContext.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE).edit {
                        putBoolean("update_downloading", false)
                    }
                    return@withContext Result.failure()
                }
                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(file)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloadedBytes: Long = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    
                    if (totalBytes > 0) {
                        val progress = (downloadedBytes * 100 / totalBytes).toInt()
                        // Update progress every 5% to avoid notification spam
                        if (progress % 5 == 0) {
                            setProgress(workDataOf("progress" to progress))
                            setForeground(createForegroundInfo(progress))
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                Log.d(TAG, "Download complete: ${file.absolutePath}")
                
                applicationContext.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE).edit {
                    putBoolean("update_downloaded", true)
                    putBoolean("update_downloading", false)
                    putString("apk_path", file.absolutePath)
                }

                UpdateUtils.showUpdateReadyNotification(applicationContext, file)
                
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                applicationContext.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE).edit {
                    putBoolean("update_downloading", false)
                }
                Result.failure()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UpdateUtils.CHANNEL_ID,
                "Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val title = applicationContext.getString(R.string.downloading_update)
        val progressText = "$progress%"

        // Intent to show changelog
        val whatsNewIntent = Intent(applicationContext, lavender.client.android.ChatListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_show_whats_new", true)
        }
        val whatsNewPendingIntent = PendingIntent.getActivity(
            applicationContext, 1003, whatsNewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, UpdateUtils.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_update_rotating)
            .setContentTitle(title)
            .setContentText(progressText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setShowWhen(false)
            .setProgress(100, progress, false)
            .addAction(R.drawable.ic_star, applicationContext.getString(R.string.whats_new), whatsNewPendingIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
