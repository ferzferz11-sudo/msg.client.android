package lavender.client.android.data.sticker

import android.content.Context
import android.util.LruCache
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lavender.client.android.network.HttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

object StickerCacheManager {
    private const val MAX_MEMORY_CACHE_SIZE = 30
    private const val MAX_DISK_CACHE_SIZE = 50L * 1024 * 1024
    private const val CACHE_DIR_NAME = "sticker_cache"

    private val memoryCache = object : LruCache<String, LottieComposition>(MAX_MEMORY_CACHE_SIZE) {
        override fun sizeOf(key: String, value: LottieComposition): Int = 1
    }

    private var cacheDir: File? = null

    fun init(context: Context) {
        cacheDir = File(context.cacheDir, CACHE_DIR_NAME).also { it.mkdirs() }
    }

    private fun diskCacheFile(url: String): File? {
        val dir = cacheDir ?: return null
        val hash = MessageDigest.getInstance("MD5").digest(url.toByteArray())
        val hex = hash.joinToString("") { "%02x".format(it) }
        return File(dir, "$hex.json")
    }

    suspend fun getComposition(url: String): LottieComposition? {
        memoryCache.get(url)?.let { return it }

        val diskFile = diskCacheFile(url)
        if (diskFile != null && diskFile.exists()) {
            val comp = withContext(Dispatchers.IO) {
                LottieCompositionFactory.fromJsonStringSync(diskFile.readText(), null).value
            }
            if (comp != null) {
                memoryCache.put(url, comp)
                return comp
            }
        }

        val json = downloadLottieJson(url) ?: return null
        val comp = withContext(Dispatchers.IO) {
            LottieCompositionFactory.fromJsonStringSync(json, null).value
        }
        if (comp != null) {
            memoryCache.put(url, comp)
        }
        return comp
    }

    private suspend fun downloadLottieJson(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            val response = HttpClient.client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            response.close()

            val diskFile = diskCacheFile(url)
            if (diskFile != null) {
                diskFile.writeText(body)
                cleanupOldCache()
            }
            body
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanupOldCache() {
        val dir = cacheDir ?: return
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var totalSize = files.sumOf { it.length() }
        for (file in files) {
            if (totalSize <= MAX_DISK_CACHE_SIZE) break
            totalSize -= file.length()
            file.delete()
        }
    }

    fun clearCache() {
        memoryCache.evictAll()
        cacheDir?.listFiles()?.forEach { it.delete() }
    }
}
