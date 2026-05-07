package lavender.client.android.theme.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import lavender.client.android.theme.ThemeStore

object ThemeUi {
    fun bind(activity: AppCompatActivity, username: String) {
        ThemeStore.refresh(activity, username)
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                ThemeStore.theme.collect { theme ->
                    ThemeApplier.apply(activity, theme)
                }
            }
        }
    }
}

