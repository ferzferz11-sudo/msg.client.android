package lavender.client.android.data.updates

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
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
                    return@withContext Result.failure()
                }

                val body = response.body ?: return@withContext Result.failure()
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
                            setForeground(createForegroundInfo(progress))
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                Log.d(TAG, "Download complete: ${file.absolutePath}")
                
                UpdateUtils.showUpdateReadyNotification(applicationContext, file)
                
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                Result.failure()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UpdateUtils.CHANNEL_ID,
                "Updates",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val title = "Downloading Lavender Update"
        val progressText = "$progress%"

        val notification = NotificationCompat.Builder(applicationContext, UpdateUtils.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(progressText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
