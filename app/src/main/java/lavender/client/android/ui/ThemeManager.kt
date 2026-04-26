package lavender.client.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import org.json.JSONObject

object ThemeManager {
    private var currentCustomTheme: CustomThemeProto? = null

    fun loadTheme(context: Context, username: String, onComplete: () -> Unit = {}) {
        val prefs = context.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
        val themeId = prefs.getString("current_theme_id", "dark") ?: "dark"
        
        if (themeId == "light" || themeId == "dark") {
            currentCustomTheme = null
            onComplete()
            return
        }

        // Try to load from cache first
        val cachedTheme = prefs.getString("custom_theme_json_$themeId", null)
        if (cachedTheme != null) {
            currentCustomTheme = parseThemeFromJson(cachedTheme)
            onComplete()
        }

        // Always refresh from server
        GrpcClient.getThemes(username) { _, themes ->
            val theme = themes.find { it.id == themeId }
            if (theme != null) {
                currentCustomTheme = theme
                prefs.edit().putString("custom_theme_json_$themeId", serializeThemeToJson(theme)).apply()
                onComplete()
            }
        }
    }

    fun applyTheme(activity: AppCompatActivity) {
        val theme = currentCustomTheme ?: return
        
        val root = activity.findViewById<View>(android.R.id.content)
        applyThemeToView(root, theme)

        // System UI bars adjustment based on isDark flag
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val controller = activity.window.insetsController
            if (controller != null) {
                if (theme.isDark) {
                    // Dark theme -> Light icons on bars
                    controller.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                    controller.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
                } else {
                    // Light theme -> Dark icons on bars
                    controller.setSystemBarsAppearance(android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                    controller.setSystemBarsAppearance(android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS, android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
                }
            }
        }
        
        // Handle Dialog styling for the activity (using reflections or specific theme overrides)
        // Since we can't easily change the system AlertDialog theme at runtime without a style resource,
        // we can at least ensure activities apply the background.

        // Activity-specific background
        try {
            val bgColor = Color.parseColor(theme.backgroundColor)
            activity.window.decorView.setBackgroundColor(bgColor)
            
            // Apply background color to the root content view if no image is present
            if (theme.backgroundImageUrl.isEmpty()) {
                root.setBackgroundColor(bgColor)
            }
        } catch (_: Exception) {}
    }

    fun applyThemeToView(view: View, theme: CustomThemeProto) {
        try {
            val primaryColor = Color.parseColor(theme.primaryColor)
            val onPrimaryColor = Color.parseColor(theme.onPrimaryColor)
            val backgroundColor = Color.parseColor(theme.backgroundColor)
            val textPrimaryColor = Color.parseColor(theme.textPrimaryColor)
            val surfaceColor = Color.parseColor(theme.surfaceColor)
            val onSurfaceColor = Color.parseColor(theme.onSurfaceColor)

            when (view) {
                is com.google.android.material.appbar.MaterialToolbar -> {
                    view.setBackgroundColor(primaryColor)
                    view.setTitleTextColor(onPrimaryColor)
                    view.setSubtitleTextColor(onPrimaryColor)
                    view.setNavigationIconTint(onPrimaryColor)
                    
                    // Force overflow icon tinting
                    val overflowIcon = view.overflowIcon
                    if (overflowIcon != null) {
                        val tintedIcon = androidx.core.graphics.drawable.DrawableCompat.wrap(overflowIcon).mutate()
                        androidx.core.graphics.drawable.DrawableCompat.setTint(tintedIcon, onPrimaryColor)
                        view.overflowIcon = tintedIcon
                    }

                    // Tint all menu items
                    for (i in 0 until view.menu.size()) {
                        val item = view.menu.getItem(i)
                        item.icon?.setTint(onPrimaryColor)
                    }
                }
                is MaterialButton -> {
                    if (view.id != android.R.id.home) {
                        view.backgroundTintList = ColorStateList.valueOf(primaryColor)
                        view.setTextColor(onPrimaryColor)
                        view.iconTint = ColorStateList.valueOf(onPrimaryColor)
                    }
                }
                is android.widget.EditText -> {
                    view.setTextColor(textPrimaryColor)
                    view.setHintTextColor(onSurfaceColor.withAlpha(150))
                }
                is TextView -> {
                    view.setTextColor(textPrimaryColor)
                    view.setHintTextColor(onSurfaceColor.withAlpha(150))
                }
                is MaterialCardView -> {
                    view.setCardBackgroundColor(surfaceColor)
                }
            }
            
            // Handle background for generic containers
            if (view.tag == "themed_background") {
                view.setBackgroundColor(backgroundColor)
            }

            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    applyThemeToView(view.getChildAt(i), theme)
                }
            }
        } catch (_: Exception) {}
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return (this and 0x00FFFFFF) or (alpha shl 24)
    }

    fun getCurrentTheme(): CustomThemeProto? = currentCustomTheme

    private fun parseThemeFromJson(json: String): CustomThemeProto {
        val obj = JSONObject(json)
        return CustomThemeProto(
            id = obj.getString("id"),
            name = obj.getString("name"),
            primaryColor = obj.getString("primaryColor"),
            onPrimaryColor = obj.getString("onPrimaryColor"),
            surfaceColor = obj.getString("surfaceColor"),
            onSurfaceColor = obj.getString("onSurfaceColor"),
            backgroundColor = obj.getString("backgroundColor"),
            textPrimaryColor = obj.getString("textPrimaryColor"),
            textSecondaryColor = obj.getString("textSecondaryColor"),
            isDark = obj.getBoolean("isDark"),
            backgroundImageUrl = obj.optString("backgroundImageUrl", "")
        )
    }

    private fun serializeThemeToJson(theme: CustomThemeProto): String {
        return JSONObject().apply {
            put("id", theme.id)
            put("name", theme.name)
            put("primaryColor", theme.primaryColor)
            put("onPrimaryColor", theme.onPrimaryColor)
            put("surfaceColor", theme.surfaceColor)
            put("onSurfaceColor", theme.onSurfaceColor)
            put("backgroundColor", theme.backgroundColor)
            put("textPrimaryColor", theme.textPrimaryColor)
            put("textSecondaryColor", theme.textSecondaryColor)
            put("isDark", theme.isDark)
            put("backgroundImageUrl", theme.backgroundImageUrl)
        }.toString()
    }
}
