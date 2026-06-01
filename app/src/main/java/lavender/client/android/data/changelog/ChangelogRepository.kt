package lavender.client.android.data.changelog

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ChangelogRepository {
    private const val TAG = "ChangelogRepo"
    private const val PREFS_NAME = "ChangelogCache"
    private const val KEY_CACHED_DATA = "cached_releases_json"
    private const val KEY_CACHE_TIME = "cache_timestamp"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    suspend fun fetchReleases(context: Context, forceRefresh: Boolean = false): Result<List<ReleaseInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                // Return cached data if not forcing refresh and cache is fresh
                if (!forceRefresh) {
                    val cached = getCachedReleases(context)
                    if (cached != null) {
                        Log.d(TAG, "Returning ${cached.size} cached releases")
                        return@withContext Result.success(cached)
                    }
                }

                // Fetch from GitHub API
                Log.d(TAG, "Fetching releases from GitHub API...")
                val url = URL(ChangelogParser.getApiUrl())
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()

                    // Cache the raw JSON
                    cacheReleases(context, text)

                    val releases = ChangelogParser.parseReleases(text)
                    Log.d(TAG, "Fetched ${releases.size} releases from GitHub")
                    Result.success(releases)
                } else {
                    connection.disconnect()
                    Log.w(TAG, "GitHub API returned $responseCode")
                    // Try to return cached data even if stale
                    val cached = getCachedReleases(context, ignoreTtl = true)
                    if (cached != null) {
                        Result.success(cached)
                    } else {
                        Result.failure(Exception("HTTP $responseCode"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch releases", e)
                // Try to return cached data even if stale
                val cached = getCachedReleases(context, ignoreTtl = true)
                if (cached != null) {
                    Result.success(cached)
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    private fun cacheReleases(context: Context, json: String) {
        getPrefs(context).edit()
            .putString(KEY_CACHED_DATA, json)
            .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
            .apply()
    }

    private fun getCachedReleases(context: Context, ignoreTtl: Boolean = false): List<ReleaseInfo>? {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_CACHED_DATA, null) ?: return null
        val cacheTime = prefs.getLong(KEY_CACHE_TIME, 0)

        if (!ignoreTtl && System.currentTimeMillis() - cacheTime > CACHE_TTL_MS) {
            return null // Cache expired
        }

        return try {
            ChangelogParser.parseReleases(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cached releases", e)
            null
        }
    }

    fun clearCache(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
