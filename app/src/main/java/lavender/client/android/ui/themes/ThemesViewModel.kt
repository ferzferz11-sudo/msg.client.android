package lavender.client.android.ui.themes

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.theme.BuiltInThemes
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.data.ThemeMappers

data class ThemesUiState(
    val isLoading: Boolean = false,
    val themes: List<CustomThemeProto> = emptyList(),
    val currentThemeId: String = "dark",
    val activeThemeId: String = "dark",
    val followSystemDarkMode: Boolean = false,
    val error: String? = null,
    val themeApplied: Boolean = false,
    val themesDeleted: Boolean = false
)

class ThemesViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ThemesUiState())
    val uiState: StateFlow<ThemesUiState> = _uiState.asStateFlow()

    private val grpcClient = GrpcClient

    private val prefs get() = getApplication<Application>().getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)

    fun loadThemes(username: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val queryId = grpcClient.getUserId() ?: username

        grpcClient.getThemes(queryId) { currentId, list ->
            viewModelScope.launch {
                var remoteId = currentId
                if (remoteId == "builtin_dark_graphite") remoteId = "dark"

                val localThemeId = prefs.getString("current_theme_id", null)
                val activeId = if (localThemeId == null || localThemeId == "builtin_dark_graphite") remoteId else localThemeId
                val currentIdResolved = if (localThemeId == null || localThemeId == "builtin_dark_graphite") remoteId else localThemeId

                val allThemes = buildThemeList(list)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    themes = allThemes,
                    currentThemeId = currentIdResolved,
                    activeThemeId = activeId,
                    followSystemDarkMode = ThemeStore.isFollowSystemDarkMode()
                )
            }
        }
    }

    private fun buildThemeList(customThemes: List<CustomThemeProto>): List<CustomThemeProto> {
        val all = mutableListOf<CustomThemeProto>()
        BuiltInThemes.all.map { ThemeMappers.toProto(it) }.forEach { theme ->
            val localizedName = when (theme.id) {
                "dark" -> getApplication<Application>().getString(lavender.client.android.R.string.dark_theme)
                "builtin_lavender_dark" -> getApplication<Application>().getString(lavender.client.android.R.string.theme_lavender_night)
                "builtin_dark_graphite" -> getApplication<Application>().getString(lavender.client.android.R.string.theme_dark_graphite)
                "builtin_green" -> getApplication<Application>().getString(lavender.client.android.R.string.theme_template_green)
                "builtin_blue" -> getApplication<Application>().getString(lavender.client.android.R.string.theme_template_blue)
                "builtin_graphite" -> getApplication<Application>().getString(lavender.client.android.R.string.theme_template_graphite)
                "builtin_mint" -> getApplication<Application>().getString(lavender.client.android.R.string.theme_template_mint)
                else -> theme.name
            }
            all.add(theme.copy(name = localizedName))
        }
        all.addAll(customThemes)
        return all
    }

    fun applyTheme(username: String, themeId: String) {
        val queryId = grpcClient.getUserId() ?: username
        if (queryId.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "User ID not found")
            return
        }

        grpcClient.setCurrentTheme(queryId, themeId) { success ->
            viewModelScope.launch {
                if (success) {
                    prefs.edit {
                        putString("current_theme_id", themeId)
                        commit()
                    }
                    _uiState.value = _uiState.value.copy(
                        currentThemeId = themeId,
                        themeApplied = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(error = "Failed to apply theme")
                }
            }
        }
    }

    fun deleteThemes(username: String, themeIds: List<String>) {
        val queryId = grpcClient.getUserId() ?: username
        var deletedCount = 0

        themeIds.forEach { id ->
            grpcClient.deleteTheme(queryId, id) { success ->
                if (success) {
                    deletedCount++
                    if (deletedCount == themeIds.size) {
                        viewModelScope.launch {
                            _uiState.value = _uiState.value.copy(themesDeleted = true)
                        }
                    }
                }
            }
        }
    }

    fun setFollowSystemDarkMode(context: Context, enabled: Boolean) {
        ThemeStore.setFollowSystemDarkMode(context, enabled)
        _uiState.value = _uiState.value.copy(followSystemDarkMode = enabled)
    }

    fun consumeThemeApplied() {
        _uiState.value = _uiState.value.copy(themeApplied = false)
    }

    fun consumeThemesDeleted() {
        _uiState.value = _uiState.value.copy(themesDeleted = false)
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getAvatarCache(): Map<String, String> = grpcClient.getAvatarCache()
}
