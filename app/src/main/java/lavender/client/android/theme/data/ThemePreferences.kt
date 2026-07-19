package lavender.client.android.theme.data

import android.content.Context
import lavender.client.android.theme.Theme

class ThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCurrentThemeId(): String = prefs.getString(KEY_CURRENT_THEME_ID, DEFAULT_THEME_ID) ?: DEFAULT_THEME_ID

    fun setCurrentThemeId(themeId: String) {
        prefs.edit().putString(KEY_CURRENT_THEME_ID, themeId).apply()
    }

    fun getBuiltInChatListBgOverride(themeId: String): String? =
        prefs.getString("bg_url_$themeId", null)

    fun isFollowSystemDarkMode(): Boolean = prefs.getBoolean(KEY_FOLLOW_SYSTEM_DARK_MODE, false)

    fun setFollowSystemDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FOLLOW_SYSTEM_DARK_MODE, enabled).apply()
    }

    fun saveCustomThemeCache(theme: Theme) {
        prefs.edit().apply {
            putString("cached_theme_id", theme.id)
            putString("cached_theme_name", theme.name)
            putString("cached_theme_primary", theme.primaryColor)
            putString("cached_theme_on_primary", theme.onPrimaryColor)
            putString("cached_theme_surface", theme.surfaceColor)
            putString("cached_theme_on_surface", theme.onSurfaceColor)
            putString("cached_theme_background", theme.backgroundColor)
            putString("cached_theme_text_primary", theme.textPrimaryColor)
            putString("cached_theme_text_secondary", theme.textSecondaryColor)
            putString("cached_theme_surface_container", theme.surfaceContainer)
            putString("cached_theme_bottom_panel", theme.bottomPanelColor)
            putString("cached_theme_on_bottom_panel", theme.onBottomPanelColor)
            putString("cached_theme_outgoing_bubble", theme.outgoingBubbleColor)
            putString("cached_theme_incoming_bubble", theme.incomingBubbleColor)
            putString("cached_theme_outgoing_text", theme.outgoingTextColor)
            putString("cached_theme_incoming_text", theme.incomingTextColor)
            putString("cached_theme_chat_list_bg", theme.chatListBackgroundImageUrl)
            putString("cached_theme_chat_bg", theme.chatBackgroundImageUrl)
        }.apply()
    }

    fun getCustomThemeCache(): Theme? {
        val id = prefs.getString("cached_theme_id", null) ?: return null
        return Theme(
            id = id,
            name = prefs.getString("cached_theme_name", "") ?: "",
            primaryColor = prefs.getString("cached_theme_primary", "#5F9EA0") ?: "#5F9EA0",
            onPrimaryColor = prefs.getString("cached_theme_on_primary", "#FFFFFF") ?: "#FFFFFF",
            surfaceColor = prefs.getString("cached_theme_surface", "#2D2D2D") ?: "#2D2D2D",
            onSurfaceColor = prefs.getString("cached_theme_on_surface", "#B8B8B8") ?: "#B8B8B8",
            backgroundColor = prefs.getString("cached_theme_background", "#1E1E1E") ?: "#1E1E1E",
            textPrimaryColor = prefs.getString("cached_theme_text_primary", "#E8E8E8") ?: "#E8E8E8",
            textSecondaryColor = prefs.getString("cached_theme_text_secondary", "#909090") ?: "#909090",
            surfaceContainer = prefs.getString("cached_theme_surface_container", "#252525") ?: "#252525",
            bottomPanelColor = prefs.getString("cached_theme_bottom_panel", "#2D2D2D") ?: "#2D2D2D",
            onBottomPanelColor = prefs.getString("cached_theme_on_bottom_panel", "#5F9EA0") ?: "#5F9EA0",
            outgoingBubbleColor = prefs.getString("cached_theme_outgoing_bubble", "#3D6B6C") ?: "#3D6B6C",
            incomingBubbleColor = prefs.getString("cached_theme_incoming_bubble", "#363636") ?: "#363636",
            outgoingTextColor = prefs.getString("cached_theme_outgoing_text", "#FFFFFF") ?: "#FFFFFF",
            incomingTextColor = prefs.getString("cached_theme_incoming_text", "#E8E8E8") ?: "#E8E8E8",
            chatListBackgroundImageUrl = prefs.getString("cached_theme_chat_list_bg", "") ?: "",
            chatBackgroundImageUrl = prefs.getString("cached_theme_chat_bg", "") ?: ""
        )
    }

    companion object {
        private const val PREFS_NAME = "lavender_prefs"
        private const val KEY_CURRENT_THEME_ID = "current_theme_id"
        private const val KEY_FOLLOW_SYSTEM_DARK_MODE = "follow_system_dark_mode"
        private const val DEFAULT_THEME_ID = "dark"
    }
}

