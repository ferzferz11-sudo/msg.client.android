package lavender.client.android.theme

import android.content.Context
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

    fun currentTheme(): Theme = _theme.value

    fun refresh(context: Context, username: String): Job {
        val running = refreshJob
        if (running?.isActive == true) return running
        return scope.launch {
            val t = repo.loadCurrentTheme(context, username)
            _theme.value = t
        }.also { refreshJob = it }
    }
}

