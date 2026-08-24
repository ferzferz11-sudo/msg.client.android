# Fix theme flicker when "Follow System Theme" is enabled

## Problem
When "Follow System Theme" is enabled, navigating between activities (e.g., from Chat List to a Chat) causes a brief flicker where a light theme (specifically "Base Light" or "Graphite & Gold") is applied before the correct dark theme is restored.

## Cause
`ThemeStore.refresh` is called in `ThemeUi.bind` (which is used by all activities). `refresh` asynchronously loads the theme specified by `current_theme_id` in preferences from the server or local repository and sets it to `_theme.value`. If "Follow System" is ON, the `current_theme_id` might still point to a light theme from a previous selection, causing `_theme.value` to briefly change to that light theme until a configuration change or another update restores the system-appropriate theme.

## User Review Required
> [!NOTE]
> This change ensures that when "Follow System Theme" is enabled, the active theme is always strictly determined by the system's dark/light mode, ignoring any manually selected themes that might be fetched from the server during background synchronization.

## Proposed Changes

### Theme Component

#### [MODIFY] [ThemeStore.kt](file:///Users/paveld/LavenderMessenger-Android/app/src/main/java/lavender/client/android/theme/ThemeStore.kt)
Modify the `refresh` method to respect the `followSystemDarkMode` flag. When enabled, it should ensure `_theme.value` remains consistent with the system-resolved theme even after fetching a theme from the repository.

```kotlin
    fun refresh(context: Context, username: String, force: Boolean = false): Job {
        val running = refreshJob
        if (!force && running?.isActive == true) return running
        running?.cancel()

        return scope.launch {
            val t = repo.loadCurrentTheme(context, username)
            if (followSystemDarkMode) {
                // Ensure we stick to the system theme if following system dark mode
                _theme.value = resolveSystemTheme(context)
            } else {
                _theme.value = t
            }
        }.also { refreshJob = it }
    }
```

## Verification Plan

### Manual Verification
1. Open the app and go to Settings (Themes).
2. Enable "Follow system theme".
3. Set Android system theme to DARK.
4. Go back to the Chat List.
5. Click on any chat to enter it.
6. **Verify**: There is no flicker to a light theme during the transition.
7. Change Android system theme to LIGHT.
8. **Verify**: The app immediately switches to light theme.
9. Enter a chat again and verify no flicker to dark theme.
