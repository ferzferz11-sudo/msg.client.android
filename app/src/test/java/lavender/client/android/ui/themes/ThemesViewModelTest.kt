package lavender.client.android.ui.themes

import org.junit.Assert.*
import org.junit.Test

class ThemesViewModelTest {

    @Test
    fun themesUiState_defaults() {
        val state = ThemesUiState()
        assertFalse(state.isLoading)
        assertTrue(state.themes.isEmpty())
        assertEquals("dark", state.currentThemeId)
        assertEquals("dark", state.activeThemeId)
        assertFalse(state.followSystemDarkMode)
        assertNull(state.error)
        assertFalse(state.themeApplied)
        assertFalse(state.themesDeleted)
    }

    @Test
    fun themesUiState_withThemes() {
        val state = ThemesUiState(
            themes = listOf(
                lavender.client.android.data.proto.CustomThemeProto(id = "dark", name = "Dark"),
                lavender.client.android.data.proto.CustomThemeProto(id = "custom_1", name = "My Theme")
            ),
            currentThemeId = "custom_1"
        )
        assertEquals(2, state.themes.size)
        assertEquals("custom_1", state.currentThemeId)
    }

    @Test
    fun themesUiState_copy() {
        val original = ThemesUiState(currentThemeId = "dark")
        val updated = original.copy(
            currentThemeId = "light",
            themeApplied = true,
            followSystemDarkMode = true
        )
        assertEquals("light", updated.currentThemeId)
        assertTrue(updated.themeApplied)
        assertTrue(updated.followSystemDarkMode)
    }

    @Test
    fun themesUiState_errorAndSuccess() {
        val withError = ThemesUiState(error = "Failed to apply")
        assertEquals("Failed to apply", withError.error)

        val applied = ThemesUiState(themeApplied = true)
        assertTrue(applied.themeApplied)

        val deleted = ThemesUiState(themesDeleted = true)
        assertTrue(deleted.themesDeleted)
    }
}
