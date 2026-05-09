package lavender.client.android.theme

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

    /**
     * Применяет дефолтный аватар в зависимости от темы.
     * Для светлых тем использует белый аватар, окрашенный в основной цвет.
     */
    fun applyDefaultAvatar(imageView: ImageView, theme: Theme) {
        val bgColor = parseSafeColor(theme.backgroundColor, Color.BLACK)
        val isLight = isLight(bgColor)
        val primaryColor = parseSafeColor(theme.primaryColor, Color.BLUE)

        if (isLight) {
            imageView.setImageResource(R.drawable.ic_default_avatar_white)
            // Use setColorFilter instead of imageTintList for CircleImageView compatibility
            imageView.setColorFilter(primaryColor)
        } else {
            imageView.setImageResource(R.drawable.ic_default_avatar)
            imageView.clearColorFilter()
        }
    }
}
