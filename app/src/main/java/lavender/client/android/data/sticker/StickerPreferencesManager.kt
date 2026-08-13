package lavender.client.android.data.sticker

import android.content.Context
import android.content.SharedPreferences
import lavender.client.android.data.models.Sticker
import org.json.JSONArray

object StickerPreferencesManager {

    private const val PREFS_NAME = "sticker_prefs"
    private const val KEY_RECENT = "recent_stickers"
    private const val KEY_FAVORITES = "favorite_stickers"
    private const val MAX_RECENT = 20
    private const val MAX_FAVORITES = 100
    private const val SCHEMA_VERSION = 1

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getPrefs(): SharedPreferences {
        if (prefs == null) throw IllegalStateException("StickerPreferencesManager.init() must be called first")
        return prefs!!
    }

    fun addRecent(sticker: Sticker) {
        val recent = getRecentStickers().toMutableList()
        recent.removeAll { it.id == sticker.id }
        recent.add(0, sticker)
        if (recent.size > MAX_RECENT) {
            val trimmed = recent.take(MAX_RECENT)
            saveStickerList(KEY_RECENT, trimmed)
        } else {
            saveStickerList(KEY_RECENT, recent)
        }
    }

    fun getRecentStickers(): List<Sticker> {
        return loadStickerList(KEY_RECENT)
    }

    fun clearRecent() {
        try { getPrefs().edit().remove(KEY_RECENT).apply() } catch (_: Exception) {}
    }

    fun toggleFavorite(sticker: Sticker): Boolean {
        val favorites = getFavoriteStickers().toMutableList()
        val isFavorite = favorites.any { it.id == sticker.id }
        if (isFavorite) {
            favorites.removeAll { it.id == sticker.id }
        } else {
            if (favorites.size >= MAX_FAVORITES) {
                favorites.removeAt(favorites.lastIndex)
            }
            favorites.add(0, sticker)
        }
        saveStickerList(KEY_FAVORITES, favorites)
        return !isFavorite
    }

    fun isFavorite(stickerId: String): Boolean {
        return getFavoriteStickers().any { it.id == stickerId }
    }

    fun getFavoriteStickers(): List<Sticker> {
        return loadStickerList(KEY_FAVORITES)
    }

    fun removeFavorite(stickerId: String) {
        val favorites = getFavoriteStickers().toMutableList()
        favorites.removeAll { it.id == stickerId }
        saveStickerList(KEY_FAVORITES, favorites)
    }

    private fun saveStickerList(key: String, stickers: List<Sticker>) {
        try {
            val jsonArray = JSONArray()
            stickers.forEach { sticker ->
                val obj = org.json.JSONObject().apply {
                    put("id", sticker.id)
                    put("packId", sticker.packId)
                    put("lottieUrl", sticker.lottieUrl)
                    put("thumbnailUrl", sticker.thumbnailUrl)
                    put("emoji", sticker.emoji)
                    put("width", sticker.width)
                    put("height", sticker.height)
                }
                jsonArray.put(obj)
            }
            val wrapper = org.json.JSONObject().apply {
                put("version", SCHEMA_VERSION)
                put("data", jsonArray)
            }
            getPrefs().edit().putString(key, wrapper.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("StickerPrefs", "saveStickerList failed", e)
        }
    }

    private fun loadStickerList(key: String): List<Sticker> {
        return try {
            val json = getPrefs().getString(key, null) ?: return emptyList()
            val jsonArray = try {
                val wrapper = org.json.JSONObject(json)
                wrapper.getJSONArray("data")
            } catch (_: org.json.JSONException) {
                JSONArray(json)
            }
            deserializeStickers(jsonArray)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun deserializeStickers(jsonArray: JSONArray): List<Sticker> {
        return (0 until jsonArray.length()).mapNotNull { i ->
            try {
                val obj = jsonArray.getJSONObject(i)
                Sticker(
                    id = obj.optString("id", ""),
                    packId = obj.optString("packId", ""),
                    lottieUrl = obj.optString("animationUrl", obj.optString("lottieUrl", "")),
                    thumbnailUrl = obj.optString("thumbnailUrl", ""),
                    emoji = obj.optString("emoji", ""),
                    width = obj.optInt("width", 512),
                    height = obj.optInt("height", 512)
                ).takeIf { it.id.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
        }
    }
}
