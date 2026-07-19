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
        getPrefs().edit().remove(KEY_RECENT).apply()
    }

    fun toggleFavorite(sticker: Sticker): Boolean {
        val favorites = getFavoriteStickers().toMutableList()
        val isFavorite = favorites.any { it.id == sticker.id }
        if (isFavorite) {
            favorites.removeAll { it.id == sticker.id }
        } else {
            favorites.add(sticker)
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
        getPrefs().edit().putString(key, jsonArray.toString()).apply()
    }

    private fun loadStickerList(key: String): List<Sticker> {
        val json = getPrefs().getString(key, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).mapNotNull { i ->
                val obj = jsonArray.getJSONObject(i)
                Sticker(
                    id = obj.getString("id"),
                    packId = obj.optString("packId", ""),
                    lottieUrl = obj.getString("lottieUrl"),
                    thumbnailUrl = obj.optString("thumbnailUrl", ""),
                    emoji = obj.optString("emoji", ""),
                    width = obj.optInt("width", 512),
                    height = obj.optInt("height", 512)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
