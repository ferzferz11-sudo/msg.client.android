package lavender.client.android.theme.data

import android.content.Context

class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCurrentThemeId(): String = prefs.getString(KEY_CURRENT_THEME_ID, DEFAULT_THEME_ID) ?: DEFAULT_THEME_ID

    fun getBuiltInChatListBgOverride(themeId: String): String? =
        prefs.getString("bg_url_$themeId", null)

    companion object {
        private const val PREFS_NAME = "lavender_prefs"
        private const val KEY_CURRENT_THEME_ID = "current_theme_id"
        private const val DEFAULT_THEME_ID = "dark"
    }
}

