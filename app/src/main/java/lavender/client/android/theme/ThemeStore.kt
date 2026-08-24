package lavender.client.android.theme

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.toColorInt
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
     * Quickly load theme from local cache to avoid flickering on startup.
     */
    fun init(context: Context) {
        val prefs = lavender.client.android.theme.data.ThemePreferences(context)
        val themeId = prefs.getCurrentThemeId()
        followSystemDarkMode = prefs.isFollowSystemDarkMode()

        val targetTheme = if (followSystemDarkMode) {
            resolveSystemTheme(context)
        } else {
            val builtIn = BuiltInThemes.findById(themeId)
            if (builtIn != null) {
                builtIn
            } else {
                val cached = prefs.getCustomThemeCache()
                if (cached != null && cached.id == themeId) {
                    cached
                } else {
                    BuiltInThemes.dark
                }
            }
        }
        
        _theme.value = targetTheme
        syncNightMode()
    }

    /**
     * Atomically select a theme, disabling "follow system" mode and updating preferences.
     */
    fun selectTheme(context: Context, themeId: String) {
        followSystemDarkMode = false
        val prefs = lavender.client.android.theme.data.ThemePreferences(context)
        prefs.setFollowSystemDarkMode(false)
        prefs.setCurrentThemeId(themeId)

        val builtIn = BuiltInThemes.findById(themeId)
        val newTheme = if (builtIn != null) {
            builtIn
        } else {
            val cached = prefs.getCustomThemeCache()
            if (cached != null && cached.id == themeId) cached else BuiltInThemes.dark
        }
        
        _theme.value = newTheme
        syncNightMode()
    }

    fun refresh(context: Context, username: String, force: Boolean = false): Job {
        val running = refreshJob
        if (!force && running?.isActive == true) return running
        running?.cancel()

        return scope.launch {
            val t = repo.loadCurrentTheme(context, username)
            if (followSystemDarkMode) {
                _theme.value = resolveSystemTheme(context)
            } else {
                _theme.value = t
            }
        }.also { refreshJob = it }
    }

    fun setFollowSystemDarkMode(context: Context, enabled: Boolean) {
        followSystemDarkMode = enabled
        val prefs = lavender.client.android.theme.data.ThemePreferences(context)
        prefs.setFollowSystemDarkMode(enabled)
        
        if (enabled) {
            _theme.value = resolveSystemTheme(context)
        } else {
            // Keep current _theme.value but sync prefs
        }
        syncNightMode()
    }

    fun isFollowSystemDarkMode(): Boolean = followSystemDarkMode

    fun onConfigurationChanged(context: Context) {
        if (followSystemDarkMode) {
            _theme.value = resolveSystemTheme(context)
        }
    }

    private fun syncNightMode() {
        val mode = if (followSystemDarkMode) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        } else {
            val isDark = isThemeDark(_theme.value)
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun isThemeDark(theme: Theme): Boolean {
        return try {
            val bgColor = theme.backgroundColor.toColorInt()
            val luminance = (0.299 * android.graphics.Color.red(bgColor) +
                    0.587 * android.graphics.Color.green(bgColor) +
                    0.114 * android.graphics.Color.blue(bgColor)) / 255
            luminance < 0.5
        } catch (_: Exception) {
            true
        }
    }

    private fun resolveSystemTheme(context: Context): Theme {
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_NO) {
            BuiltInThemes.BASE_LIGHT
        } else {
            BuiltInThemes.dark
        }
    }
}
