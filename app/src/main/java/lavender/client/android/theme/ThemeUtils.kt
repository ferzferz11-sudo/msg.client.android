package lavender.client.android.theme

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
        val primaryColor = parseSafeColor(theme.primaryColor, Color.BLUE)
        val surfaceContainer = parseSafeColor(theme.surfaceContainer, Color.LTGRAY)
        val bgColor = parseSafeColor(theme.backgroundColor, Color.BLACK)
        val isLight = isLight(bgColor)

        val avatarBgColor = if (!bubbleColor.isNullOrEmpty()) {
            parseSafeColor(bubbleColor, surfaceContainer)
        } else surfaceContainer

        // If it's a ShapeableImageView, we use its native properties
        if (imageView is com.google.android.material.imageview.ShapeableImageView) {
            imageView.strokeWidth = 0f // Remove stroke to avoid double contour/aliasing
            imageView.background = ColorDrawable(avatarBgColor)
            imageView.setImageResource(if (isLight) R.drawable.ic_default_avatar_white else R.drawable.ic_default_avatar)
            imageView.setColorFilter(primaryColor)
            return
        }

        // Fallback for regular ImageViews (or CircleImageView)
        imageView.setImageResource(if (isLight) R.drawable.ic_default_avatar_white else R.drawable.ic_default_avatar)
        imageView.setColorFilter(primaryColor)

        // Reuse or create background
        val currentBg = imageView.background
        if (currentBg is android.graphics.drawable.GradientDrawable) {
            currentBg.setColor(avatarBgColor)
            currentBg.setStroke(0, Color.TRANSPARENT) // Remove stroke here too
        } else {
            val bg = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(avatarBgColor)
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
