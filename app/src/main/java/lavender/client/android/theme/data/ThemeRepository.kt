package lavender.client.android.theme.data

import android.content.Context
import lavender.client.android.theme.BuiltInThemes
import lavender.client.android.theme.Theme

class ThemeRepository(
    private val remote: ThemeRemoteDataSource = ThemeRemoteDataSource(),
) {
    suspend fun loadCurrentTheme(context: Context, username: String): Theme {
        val prefs = ThemePreferences(context)
        var themeId = prefs.getCurrentThemeId()

        // Migrate old graphite ID to default dark
        if (themeId == "builtin_dark_graphite") {
            themeId = "dark"
            prefs.setCurrentThemeId("dark")
        }

        if (themeId == "dark") return BuiltInThemes.dark
        if (themeId == "light") return BuiltInThemes.BASE_LIGHT

        val builtIn = BuiltInThemes.findById(themeId)
        if (builtIn != null) {
            val override = prefs.getBuiltInChatListBgOverride(themeId)
            return if (!override.isNullOrEmpty()) builtIn.copy(chatListBackgroundImageUrl = override) else builtIn
        }

        // Try to load from local cache first for instant startup
        val cached = prefs.getCustomThemeCache()
        
        try {
            val queryId = lavender.client.android.data.grpc.GrpcClient.getUserId() ?: username
            if (queryId.isNotEmpty()) {
                val themes = remote.getThemes(queryId)
                if (themes.isNotEmpty()) {
                    val found = themes.find { it.id == themeId }
                    if (found != null) {
                        val theme = ThemeMappers.fromProto(found)
                        prefs.saveCustomThemeCache(theme) // Update cache
                        return theme
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ThemeRepo", "Failed to fetch theme from remote: ${e.message}")
        }

        // Fallback to cache if remote failed or theme not found on server
        if (cached != null && cached.id == themeId) {
            return cached
        }

        return BuiltInThemes.dark
    }
}

