package lavender.client.android.theme.data

import android.content.Context
import lavender.client.android.theme.BuiltInThemes
import lavender.client.android.theme.Theme

class ThemeRepository(
    private val remote: ThemeRemoteDataSource = ThemeRemoteDataSource(),
) {
    suspend fun loadCurrentTheme(context: Context, username: String): Theme {
        val prefs = ThemePreferences(context)
        val themeId = prefs.getCurrentThemeId()

        if (themeId == "dark") return BuiltInThemes.dark
        if (themeId == "light") return BuiltInThemes.baseLight

        val builtIn = BuiltInThemes.findById(themeId)
        if (builtIn != null) {
            val override = prefs.getBuiltInChatListBgOverride(themeId)
            return if (!override.isNullOrEmpty()) builtIn.copy(chatListBackgroundImageUrl = override) else builtIn
        }

        val queryId = lavender.client.android.data.grpc.GrpcClient.getUserId() ?: username
        val themes = remote.getThemes(queryId)
        val found = themes.find { it.id == themeId } ?: return BuiltInThemes.dark
        return ThemeMappers.fromProto(found)
    }
}

