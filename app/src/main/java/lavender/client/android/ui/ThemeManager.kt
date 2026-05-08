package lavender.client.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.theme.BuiltInThemes
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.data.ThemeMappers
import kotlin.math.roundToInt

/**
 * Legacy compatibility object for theme access.
 * New code should use ThemeStore directly with Theme class.
 */
object ThemeManager {

    // Delegate to new BuiltInThemes location
    val builtInThemes: List<CustomThemeProto>
        get() = BuiltInThemes.all.map { ThemeMappers.toProto(it) }

    data class MessageColors(
        val incomingBg: Int,
        val incomingText: Int,
        val outgoingBg: Int,
        val outgoingText: Int
    )

    fun getMessageColors(context: Context): MessageColors {
        val theme = ThemeStore.currentTheme()
        return MessageColors(
            incomingBg = parseSafeColor(theme.incomingBubbleColor, Color.BLUE),
            incomingText = parseSafeColor(theme.primaryColor, Color.WHITE),
            outgoingBg = parseSafeColor(theme.outgoingBubbleColor, Color.LTGRAY),
            outgoingText = parseSafeColor(theme.textPrimaryColor, Color.BLACK)
        )
    }

    fun getCurrentTheme(): CustomThemeProto? {
        return ThemeMappers.toProto(ThemeStore.currentTheme())
    }

    fun applyThemeToView(view: View, theme: CustomThemeProto) {
        val textPrimary = parseSafeColor(theme.textPrimaryColor, Color.BLACK)
        val onSurface = parseSafeColor(theme.onSurfaceColor, Color.GRAY)

        when (view) {
            is MaterialButton -> {
                view.setTextColor(parseSafeColor(theme.primaryColor, Color.BLUE))
            }
            is CheckBox -> {
                view.buttonTintList = ColorStateList.valueOf(parseSafeColor(theme.primaryColor, Color.BLUE))
            }
            is TextView -> {
                view.setTextColor(textPrimary)
            }
            is MaterialCardView -> {
                view.setCardBackgroundColor(ColorStateList.valueOf(parseSafeColor(theme.surfaceColor, Color.WHITE)))
                view.strokeColor = adjustAlpha(onSurface, 0.2f)
            }
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    applyThemeToView(view.getChildAt(i), theme)
                }
            }
        }
    }

    fun parseSafeColor(colorStr: String?, defaultColor: Int): Int {
        if (colorStr.isNullOrEmpty()) return defaultColor
        return try {
            colorStr.toColorInt()
        } catch (_: Exception) {
            Log.e("ThemeManager", "Invalid color string: $colorStr")
            defaultColor
        }
    }

    fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).roundToInt()
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    fun findBuiltInThemeById(id: String): CustomThemeProto? {
        return BuiltInThemes.findById(id)?.let { ThemeMappers.toProto(it) }
            ?: if (id == "dark") ThemeMappers.toProto(BuiltInThemes.dark) else null
    }
}