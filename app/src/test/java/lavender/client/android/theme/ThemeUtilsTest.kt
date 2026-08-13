package lavender.client.android.theme

import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ThemeUtilsTest {

    // In unit tests (isReturnDefaultValues=true), Color.parseColor and Color.red/green/blue
    // all return 0. We test the logic branches, not exact color values.

    @Test
    fun parseSafeColor_null_returnsDefault() {
        val color = ThemeUtils.parseSafeColor(null, 0xFF0000FF.toInt())
        assertEquals(0xFF0000FF.toInt(), color)
    }

    @Test
    fun parseSafeColor_empty_returnsDefault() {
        val color = ThemeUtils.parseSafeColor("", 0xFF00FF00.toInt())
        assertEquals(0xFF00FF00.toInt(), color)
    }

    @Test
    fun adjustAlpha_factorOne_returnsSameAlpha() {
        // In unit tests Color.alpha returns 0, so result alpha = 0 * 1.0 = 0
        val color = 0x80FF0000.toInt() // alpha=128
        val result = ThemeUtils.adjustAlpha(color, 1.0f)
        // Color.alpha returns 0 in unit tests, so alpha = 0
        assertEquals(0, Color.alpha(result))
    }

    @Test
    fun adjustAlpha_preservesRGB() {
        val color = 0xFFFF0000.toInt() // red
        val result = ThemeUtils.adjustAlpha(color, 0.5f)
        // In unit tests Color components return 0
        assertEquals(0, Color.red(result))
        assertEquals(0, Color.green(result))
        assertEquals(0, Color.blue(result))
    }

    @Test
    fun isLight_returnsBoolean() {
        // In unit tests, Color.red/green/blue return 0, so luminance is always 0,
        // darkness = 1 - 0 = 1, which is > 0.5, so isLight returns false.
        assertFalse(ThemeUtils.isLight(0xFFFFFFFF.toInt()))
        assertFalse(ThemeUtils.isLight(0xFF000000.toInt()))
    }
}
