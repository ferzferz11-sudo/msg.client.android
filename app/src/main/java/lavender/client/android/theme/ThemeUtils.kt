package lavender.client.android.theme

import android.app.Activity
import android.graphics.Color
import android.util.Log
import android.widget.ImageView
import androidx.core.graphics.toColorInt
import lavender.client.android.R
import kotlin.math.roundToInt

object ThemeUtils {

    fun parseSafeColor(colorStr: String?, defaultColor: Int): Int {
        if (colorStr.isNullOrEmpty()) return defaultColor
        return try {
            colorStr.toColorInt()
        } catch (_: Exception) {
            Log.e("ThemeUtils", "Invalid color string: $colorStr")
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

    fun isLight(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) +
                0.587 * Color.green(color) +
                0.114 * Color.blue(color)) / 255
        return darkness < 0.5
    }

    fun applyDefaultAvatar(imageView: ImageView, theme: Theme, bubbleColor: String? = null) {
        val bgColor = parseSafeColor(theme.backgroundColor, Color.BLACK)
        val isLight = isLight(bgColor)
        val primaryColor = parseSafeColor(theme.primaryColor, Color.BLUE)
        val surfaceContainer = parseSafeColor(theme.surfaceContainer, Color.LTGRAY)
        // Use bubble color if provided (for chat avatars), otherwise use surfaceContainer
        val avatarBgColor = if (!bubbleColor.isNullOrEmpty()) {
            parseSafeColor(bubbleColor, surfaceContainer)
        } else surfaceContainer

        if (isLight) {
            imageView.setImageResource(R.drawable.ic_default_avatar_white)
            // Use setColorFilter instead of imageTintList for CircleImageView compatibility
            imageView.setColorFilter(primaryColor)

            // Add a circular background with primary color border
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(avatarBgColor)
                setStroke(3, primaryColor)
            }
            imageView.background = bg
        } else {
            imageView.setImageResource(R.drawable.ic_default_avatar)
            // Apply primary color tint for dark themes too
            imageView.setColorFilter(primaryColor)
            // For dark themes, also add background using incoming bubble color
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(avatarBgColor)
                setStroke(2, primaryColor)
            }
            imageView.background = bg
        }
    }

    fun applyThemeToActivity(activity: Activity, theme: Theme) {
        try {
            val bgColor = parseSafeColor(theme.backgroundColor, Color.BLACK)
            activity.window.decorView.setBackgroundColor(bgColor)
        } catch (_: Exception) {
            Log.e("ThemeUtils", "Error applying theme to activity")
        }
    }
}
