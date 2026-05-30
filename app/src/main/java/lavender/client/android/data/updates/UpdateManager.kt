package lavender.client.android.data.updates

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import lavender.client.android.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages app updates: checking, background downloading with progress, and cancellation.
 * Uses coroutines instead of WorkManager for reliable foreground downloading.
 */
class UpdateManager(private val context: Context) {
    private val TAG = "UpdateManager"
    private val prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var downloadJob: Job? = null

        @Volatile
        private var downloadCancelled = false

        private val _downloadProgress = MutableStateFlow(0)
        val downloadProgress: StateFlow<Int> = _downloadProgress

        private val _isDownloading = MutableStateFlow(false)
        val isDownloading: StateFlow<Boolean> = _isDownloading

        private val _isDownloaded = MutableStateFlow(false)
        val isDownloaded: StateFlow<Boolean> = _isDownloaded
    }

    // Instance-level accessors for proper type resolution in collect {}
    val downloadProgressInstance: StateFlow<Int> get() = downloadProgress
    val isDownloadingInstance: StateFlow<Boolean> get() = isDownloading
    val isDownloadedInstance: StateFlow<Boolean> get() = isDownloaded

    init {
        // Restore state from prefs on init
        _isDownloading.value = prefs.getBoolean("update_downloading", false)
        _isDownloaded.value = prefs.getBoolean("update_downloaded", false)
    }

    // --- Version check ---

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
                        }
                    }
                    onResult(isAvailable, latestVersion)
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                onResult(false, "")
            }
        }.start()
    }

    // --- Download ---

    fun startDownload(isAuto: Boolean = false) {
        if (_isDownloading.value) {
            Log.d(TAG, "Download already in progress, ignoring")
            return
        }

        downloadJob?.cancel()
        downloadCancelled = false
        _isDownloading.value = true
        _isDownloaded.value = false
        _downloadProgress.value = 0
        prefs.edit {
            putBoolean("update_downloading", true)
            putBoolean("update_downloaded", false)
        }

        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Starting download, isAuto=$isAuto")
            val file = File(context.getExternalFilesDir(null), "lavender_update.apk")

            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url(UpdateUtils.getUpdateUrl(context))
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed: HTTP ${response.code}")
                    finishDownload(false)
                    return@launch
                }

                val body = response.body ?: run {
                    Log.e(TAG, "Download failed: empty body")
                    finishDownload(false)
                    return@launch
                }

                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(file)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var downloadedBytes: Long = 0
                var lastProgress = 0

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    if (downloadCancelled) {
                        Log.d(TAG, "Download cancelled by user")
                        outputStream.close()
                        inputStream.close()
                        file.delete()
                        finishDownload(false)
                        return@launch
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    if (totalBytes > 0) {
                        val progress = (downloadedBytes * 100 / totalBytes).toInt()
                        if (progress > lastProgress) {
                            lastProgress = progress
                            _downloadProgress.value = progress
                        }
                    }
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()

                Log.d(TAG, "Download complete: ${file.absolutePath}")

                prefs.edit {
                    putBoolean("update_downloaded", true)
                    putString("apk_path", file.absolutePath)
                }
                _isDownloaded.value = true
                _downloadProgress.value = 100

                // Show install notification on main thread
                withContext(Dispatchers.Main) {
                    UpdateUtils.showUpdateReadyNotification(context, file)
                }

                finishDownload(true)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                file.delete()
                finishDownload(false)
            }
        }
    }

    fun cancelDownload() {
        Log.d(TAG, "Cancelling download")
        downloadCancelled = true
        downloadJob?.cancel()
        finishDownload(false)
    }

    private fun finishDownload(success: Boolean) {
        _isDownloading.value = false
        prefs.edit {
            putBoolean("update_downloading", false)
        }
        if (!success) {
            _downloadProgress.value = 0
        }
        downloadJob = null
    }
}
