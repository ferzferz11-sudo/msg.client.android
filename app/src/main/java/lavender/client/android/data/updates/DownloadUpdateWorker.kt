package lavender.client.android.data.updates

import android.content.Context
import androidx.core.content.edit
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

import androidx.work.workDataOf

class DownloadUpdateWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    companion object {
        private const val TAG = "DownloadUpdateWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background download...")
        
        applicationContext.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE).edit {
            putBoolean("update_downloading", true)
        }

        val file = File(applicationContext.getExternalFilesDir(null), "lavender_update.apk")
        
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(UpdateUtils.getUpdateUrl(applicationContext))
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
                        // Update progress every 5% to avoid excessive updates
                        if (progress % 5 == 0) {
                            setProgress(workDataOf("progress" to progress))
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
}
