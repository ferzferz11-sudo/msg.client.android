package lavender.client.android.data.ai

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

object FavoriteAgentsManager {
    private const val PREFS_NAME = "favorite_agents"
    private const val KEY_FAVORITES = "favorite_ids"

    private var prefs: SharedPreferences? = null

    private fun ensureInit(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getFavorites(context: Context): Set<String> {
        ensureInit(context)
        val json = prefs?.getString(KEY_FAVORITES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun isFavorite(context: Context, agentId: String): Boolean = agentId in getFavorites(context)

    fun toggleFavorite(context: Context, agentId: String): Boolean {
        ensureInit(context)
        val current = getFavorites(context).toMutableSet()
        val isNowFavorite = if (current.contains(agentId)) {
            current.remove(agentId)
            false
        } else {
            current.add(agentId)
            true
        }
        saveFavorites(current)
        return isNowFavorite
    }

    fun addFavorite(context: Context, agentId: String) {
        ensureInit(context)
        val current = getFavorites(context).toMutableSet()
        current.add(agentId)
        saveFavorites(current)
    }

    fun removeFavorite(context: Context, agentId: String) {
        ensureInit(context)
        val current = getFavorites(context).toMutableSet()
        current.remove(agentId)
        saveFavorites(current)
    }

    private fun saveFavorites(ids: Set<String>) {
        val arr = JSONArray(ids.toList())
        prefs?.edit()?.putString(KEY_FAVORITES, arr.toString())?.apply()
    }
}
