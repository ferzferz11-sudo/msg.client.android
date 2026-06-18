package lavender.client.android.data.cache

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import lavender.client.android.data.db.AppDatabase

/**
 * Utility for clearing local app cache.
 * Used on login (silent) and from settings (with toast).
 */
object CacheUtils {

    /** Clear all local cache silently (for login flow). */
    fun clearAllSync(context: Context) {
        try {
            val db = AppDatabase.getDatabase(context)
            runBlocking(Dispatchers.IO) {
                db.messageDao().clearAll()
                db.chatDao().clearAll()
            }
            Log.d("Cache", "Silently cleared all local cache")
        } catch (e: Exception) {
            Log.e("Cache", "Error clearing cache", e)
        }
    }

    /** Clear all local cache with Glide (for settings flow). */
    suspend fun clearAllWithGlide(context: Context) {
        try {
            val db = AppDatabase.getDatabase(context)
            withContext(Dispatchers.IO) {
                db.messageDao().clearAll()
                db.chatDao().clearAll()
            }
            // Clear Glide caches on main thread
            com.bumptech.glide.Glide.get(context).clearMemory()
            withContext(Dispatchers.IO) {
                com.bumptech.glide.Glide.get(context).clearDiskCache()
            }
            Log.d("Cache", "Cleared all local cache with Glide")
        } catch (e: Exception) {
            Log.e("Cache", "Error clearing cache", e)
        }
    }
}
