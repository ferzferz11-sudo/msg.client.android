package lavender.client.android.theme.data

import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.theme.BuiltInThemes
import org.junit.Assert.*
import org.junit.Test

class ThemeMappersTest {

    @Test
    fun fromProto_null_returnsDarkTheme() {
        val theme = ThemeMappers.fromProto(null)
        assertEquals("dark", theme.id)
    }

    @Test
    fun fromProto_validProto() {
        val proto = CustomThemeProto(
            id = "test_theme",
            name = "Test Theme",
            primaryColor = "#FF0000",
            onPrimaryColor = "#FFFFFF",
            surfaceColor = "#333333",
            onSurfaceColor = "#EEEEEE",
            backgroundColor = "#111111",
            textPrimaryColor = "#FFFFFF",
            textSecondaryColor = "#999999",
            surfaceContainer = "#222222",
            bottomPanelColor = "#444444",
            onBottomPanelColor = "#FF0000",
            outgoingBubbleColor = "#00FF00",
            incomingBubbleColor = "#0000FF",
            outgoingTextColor = "#FFFFFF",
            incomingTextColor = "#000000",
            chatListBackgroundImageUrl = "https://example.com/bg.jpg",
            chatBackgroundImageUrl = "https://example.com/chat.jpg"
        )
        val theme = ThemeMappers.fromProto(proto)
        assertEquals("test_theme", theme.id)
        assertEquals("Test Theme", theme.name)
        assertEquals("#FF0000", theme.primaryColor)
        assertEquals("https://example.com/bg.jpg", theme.chatListBackgroundImageUrl)
    }

    @Test
    fun fromProto_emptyId_usesUnknown() {
        val proto = CustomThemeProto(id = "")
        val theme = ThemeMappers.fromProto(proto)
        assertEquals("unknown", theme.id)
    }

    @Test
    fun fromProto_emptyName_usesCustomTheme() {
        val proto = CustomThemeProto(id = "test", name = "")
        val theme = ThemeMappers.fromProto(proto)
        assertEquals("Custom Theme", theme.name)
    }

    @Test
    fun fromProto_invalidColor_usesFallback() {
        val proto = CustomThemeProto(
            id = "test",
            primaryColor = "not_a_color"
        )
        val theme = ThemeMappers.fromProto(proto)
        assertEquals("#5F9EA0", theme.primaryColor)
    }

    @Test
    fun fromProto_emptyColor_usesFallback() {
        val proto = CustomThemeProto(
            id = "test",
            primaryColor = ""
        )
        val theme = ThemeMappers.fromProto(proto)
        assertEquals("#5F9EA0", theme.primaryColor)
    }

    @Test
    fun toProto_roundTrip() {
        val original = BuiltInThemes.dark
        val proto = ThemeMappers.toProto(original)
        val restored = ThemeMappers.fromProto(proto)
        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.primaryColor, restored.primaryColor)
        assertEquals(original.surfaceColor, restored.surfaceColor)
        assertEquals(original.backgroundColor, restored.backgroundColor)
    }

    @Test
    fun toProto_preservesAllFields() {
        val theme = BuiltInThemes.all.first()
        val proto = ThemeMappers.toProto(theme)
        assertEquals(theme.id, proto.id)
        assertEquals(theme.name, proto.name)
        assertEquals(theme.primaryColor, proto.primaryColor)
        assertEquals(theme.onPrimaryColor, proto.onPrimaryColor)
        assertEquals(theme.surfaceColor, proto.surfaceColor)
        assertEquals(theme.onSurfaceColor, proto.onSurfaceColor)
        assertEquals(theme.backgroundColor, proto.backgroundColor)
        assertEquals(theme.outgoingBubbleColor, proto.outgoingBubbleColor)
        assertEquals(theme.incomingBubbleColor, proto.incomingBubbleColor)
    }
}
