package lavender.client.android.theme

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.theme.data.ThemeRepository

object ThemeStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repo = ThemeRepository()

    private val _theme = MutableStateFlow(BuiltInThemes.dark)
    val theme: StateFlow<Theme> = _theme.asStateFlow()

    @Volatile private var refreshJob: Job? = null
    @Volatile private var followSystemDarkMode = false

    fun currentTheme(): Theme = _theme.value

    /**
     * Quickly load theme from local cache to avoid flickering on startup
     */
    fun init(context: Context) {
        val prefs = lavender.client.android.theme.data.ThemePreferences(context)
        val themeId = prefs.getCurrentThemeId()
        followSystemDarkMode = prefs.isFollowSystemDarkMode()

        if (followSystemDarkMode) {
            _theme.value = resolveSystemTheme(context)
            return
        }

        // Instant load for built-in themes
        if (themeId == "dark" || themeId == "builtin_dark_graphite") {
            _theme.value = BuiltInThemes.dark
            return
        }
        if (themeId == "light") {
            _theme.value = BuiltInThemes.BASE_LIGHT
            return
        }

        val builtIn = BuiltInThemes.findById(themeId)
        if (builtIn != null) {
            _theme.value = builtIn
            return
        }

        // Load custom theme from cache
        val cached = prefs.getCustomThemeCache()
        if (cached != null && cached.id == themeId) {
            _theme.value = cached
        }
    }

    fun refresh(context: Context, username: String, force: Boolean = false): Job {
        val running = refreshJob
        if (!force && running?.isActive == true) return running
        running?.cancel()

        return scope.launch {
            val t = repo.loadCurrentTheme(context, username)
            _theme.value = t
        }.also { refreshJob = it }
    }

    fun setFollowSystemDarkMode(context: Context, enabled: Boolean) {
        followSystemDarkMode = enabled
        val prefs = lavender.client.android.theme.data.ThemePreferences(context)
        prefs.setFollowSystemDarkMode(enabled)
        if (enabled) {
            _theme.value = resolveSystemTheme(context)
        } else {
            init(context)
        }
    }

    fun isFollowSystemDarkMode(): Boolean = followSystemDarkMode

    fun onConfigurationChanged(context: Context) {
        if (followSystemDarkMode) {
            _theme.value = resolveSystemTheme(context)
        }
    }

    private fun resolveSystemTheme(context: Context): Theme {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return when (nightModeFlags) {
            Configuration.UI_MODE_NIGHT_YES -> BuiltInThemes.dark
            else -> BuiltInThemes.BASE_LIGHT
        }
    }
}
