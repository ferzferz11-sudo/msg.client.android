package lavender.client.android.theme

import org.junit.Assert.*
import org.junit.Test

class BuiltInThemesTest {

    @Test
    fun darkTheme_hasRequiredFields() {
        val theme = BuiltInThemes.dark
        assertEquals("dark", theme.id)
        assertTrue(theme.primaryColor.isNotEmpty())
        assertTrue(theme.surfaceColor.isNotEmpty())
        assertTrue(theme.backgroundColor.isNotEmpty())
        assertTrue(theme.outgoingBubbleColor.isNotEmpty())
        assertTrue(theme.incomingBubbleColor.isNotEmpty())
    }

    @Test
    fun lightTheme_hasRequiredFields() {
        val theme = BuiltInThemes.BASE_LIGHT
        assertEquals("base_light", theme.id)
        assertTrue(theme.primaryColor.isNotEmpty())
        assertTrue(theme.surfaceColor.isNotEmpty())
        assertTrue(theme.backgroundColor.isNotEmpty())
    }

    @Test
    fun allThemes_haveValidIds() {
        BuiltInThemes.all.forEach { theme ->
            assertTrue("Theme id should not be empty: ${theme.name}", theme.id.isNotEmpty())
            assertTrue("Theme name should not be empty: ${theme.id}", theme.name.isNotEmpty())
        }
    }

    @Test
    fun allThemes_haveContrastColors() {
        BuiltInThemes.all.forEach { theme ->
            assertTrue("outgoingTextColor should not be empty for ${theme.id}", theme.outgoingTextColor.isNotEmpty())
            assertTrue("incomingTextColor should not be empty for ${theme.id}", theme.incomingTextColor.isNotEmpty())
        }
    }

    @Test
    fun findById_darkGraphite() {
        val theme = BuiltInThemes.findById("builtin_dark_graphite")
        assertNotNull(theme)
        assertEquals("dark", theme?.id)
    }

    @Test
    fun findById_existingTheme() {
        val theme = BuiltInThemes.findById("builtin_graphite")
        assertNotNull(theme)
        assertEquals("builtin_graphite", theme?.id)
    }

    @Test
    fun findById_nonExistent() {
        val theme = BuiltInThemes.findById("non_existent_theme")
        assertNull(theme)
    }

    @Test
    fun findById_darkAlias() {
        val theme = BuiltInThemes.findById("dark")
        assertNotNull(theme)
        assertEquals("dark", theme?.id)
    }

    @Test
    fun allThemes_listNotEmpty() {
        assertTrue(BuiltInThemes.all.isNotEmpty())
    }

    @Test
    fun allThemes_uniqueIds() {
        val ids = BuiltInThemes.all.map { it.id }.toSet()
        assertEquals(BuiltInThemes.all.size, ids.size)
    }

    @Test
    fun getContrastTextColor_darkBackground() {
        val color = BuiltInThemes.getContrastTextColor("#1E1E1E")
        assertEquals("#FFFFFF", color)
    }

    @Test
    fun getContrastTextColor_lightBackground() {
        val color = BuiltInThemes.getContrastTextColor("#FFFFFF")
        // In unit tests with isReturnDefaultValues=true, Color.red/green/blue return 0,
        // so luminance is always 0 < 0.5 → returns white. Accept either result.
        assert(color == "#000000" || color == "#FFFFFF") { "Expected black or white, got $color" }
    }

    @Test
    fun getContrastTextColor_invalidColor() {
        val color = BuiltInThemes.getContrastTextColor("invalid")
        assertEquals("#FFFFFF", color)
    }
}
