package lavender.client.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import lavender.client.android.R
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

    fun clearTheme() {
        currentCustomTheme = null
    }

    fun applyTheme(activity: AppCompatActivity) {
        val theme = currentCustomTheme
        val root = activity.findViewById<View>(android.R.id.content)

        // Ensure Edge-to-Edge by making system bars transparent
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = Color.TRANSPARENT
        
        // Fix for Android 15+ and better Edge-to-Edge consistency
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        if (theme == null) {
            // Support standard themes (light/dark)
            val typedValue = android.util.TypedValue()
            activity.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
            val onPrimary = if (typedValue.resourceId != 0) ContextCompat.getColor(activity, typedValue.resourceId) else typedValue.data
            
            // Force tinting for toolbar children (handles custom titles and icons in standard themes)
            findAndTintToolbars(root, onPrimary)
            return
        }
        
        applyThemeToView(root, theme)
        
        // Handle bottom panel specifically if it exists
        activity.findViewById<View>(R.id.bottomPanel)?.let { 
            applyThemeToBottomPanel(it, theme)
        }

        // Automatic System UI bars adjustment based on brightness of Primary Color
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val controller = activity.window.insetsController
            if (controller != null) {
                try {
                    val primaryColor = Color.parseColor(theme.primaryColor)
                    val isLight = isColorLight(primaryColor)
                    
                    if (isLight) {
                        // Light background -> Dark icons on bars
                        controller.setSystemBarsAppearance(
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        )
                        controller.setSystemBarsAppearance(
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        )
                    } else {
                        // Dark background -> Light icons on bars
                        controller.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                        controller.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
                    }
                } catch (_: Exception) {}
            }
        }
        
        // Handle Dialog styling for the activity (using reflections or specific theme overrides)
        // Since we can't easily change the system AlertDialog theme at runtime without a style resource,
        // we can at least ensure activities apply the background.

        // Activity-specific background
        try {
            val bgColor = Color.parseColor(theme.backgroundColor)
            activity.window.decorView.setBackgroundColor(bgColor)
            root.setBackgroundColor(bgColor)
            
            // Set background for SwipeRefreshLayout if it exists
            activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshLayout)?.setBackgroundColor(bgColor)
            
            // Fix: ensure the content view itself is transparent to see root background
            (root as? ViewGroup)?.getChildAt(0)?.setBackgroundColor(Color.TRANSPARENT)
            
            // Handle background images
            val bgImageView = activity.findViewById<android.widget.ImageView>(
                if (activity.javaClass.simpleName == "ChatListActivity") R.id.chatListBgImage
                else R.id.chatBackground
            )
            
            val imageUrl = if (activity.javaClass.simpleName == "ChatListActivity") 
                theme.chatListBackgroundImageUrl 
            else 
                theme.backgroundImageUrl

            if (bgImageView != null) {
                if (imageUrl.isNotEmpty()) {
                    bgImageView.visibility = View.VISIBLE
                    com.bumptech.glide.Glide.with(activity).load(imageUrl).centerCrop().into(bgImageView)
                    root.setBackgroundColor(Color.TRANSPARENT)
                    activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshLayout)?.setBackgroundColor(Color.TRANSPARENT)
                } else {
                    bgImageView.visibility = View.GONE
                }
            }
        } catch (_: Exception) {}
    }

    private fun findAndTintToolbars(view: View, color: Int) {
        if (view is com.google.android.material.appbar.MaterialToolbar) {
            view.setTitleTextColor(color)
            view.setSubtitleTextColor(color)
            view.setNavigationIconTint(color)
            
            val overflowIcon = view.overflowIcon
            if (overflowIcon != null) {
                val tintedIcon = androidx.core.graphics.drawable.DrawableCompat.wrap(overflowIcon).mutate()
                androidx.core.graphics.drawable.DrawableCompat.setTint(tintedIcon, color)
                view.overflowIcon = tintedIcon
            }

            for (i in 0 until view.menu.size()) {
                view.menu.getItem(i).icon?.setTint(color)
            }

            for (i in 0 until view.childCount) {
                applyColorToToolbarChild(view.getChildAt(i), color)
            }
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findAndTintToolbars(view.getChildAt(i), color)
            }
        }
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
                    view.backgroundTintList = ColorStateList.valueOf(primaryColor)
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

                    // Deep search for all text and icons in toolbar
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i)
                        applyColorToToolbarChild(child, onPrimaryColor)
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
                    if (view.id == R.id.bottomPanel) {
                        try {
                            val bpColor = Color.parseColor(theme.bottomPanelColor)
                            view.setCardBackgroundColor(bpColor)
                        } catch (_: Exception) {
                            view.setCardBackgroundColor(surfaceColor)
                        }
                    } else {
                        view.setCardBackgroundColor(surfaceColor)
                    }
                }
                is com.google.android.material.floatingactionbutton.FloatingActionButton -> {
                    view.backgroundTintList = ColorStateList.valueOf(primaryColor)
                    view.imageTintList = ColorStateList.valueOf(onPrimaryColor)
                }
            }
            
            // Handle background for generic containers
            if (view.tag == "themed_background") {
                view.setBackgroundColor(backgroundColor)
            }

            // CRITICAL: Skip recursion for MaterialToolbar children to avoid overwriting tinted items
            if (view is com.google.android.material.appbar.MaterialToolbar) {
                return
            }

            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    applyThemeToView(view.getChildAt(i), theme)
                }
            }
        } catch (_: Exception) {}
    }

    private fun applyColorToToolbarChild(view: View, color: Int) {
        // Skip tinting for avatars - they should show the real photo
        val idName = try { view.resources.getResourceEntryName(view.id) } catch (_: Exception) { "" }
        if (idName.contains("avatar", ignoreCase = true)) {
            return
        }

        when (view) {
            is TextView -> view.setTextColor(color)
            is android.widget.ImageView -> {
                view.imageTintList = ColorStateList.valueOf(color)
                view.setColorFilter(color)
            }
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    applyColorToToolbarChild(view.getChildAt(i), color)
                }
            }
        }
    }

    fun applyThemeToBottomPanel(view: View, theme: CustomThemeProto) {
        try {
            val bpColor = Color.parseColor(theme.bottomPanelColor)
            val onBpColor = Color.parseColor(theme.onBottomPanelColor)
            val textPrimary = Color.parseColor(theme.textPrimaryColor)

            if (view is MaterialCardView) {
                view.setCardBackgroundColor(bpColor)
            }
            
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    when (child) {
                        is android.widget.ImageButton -> {
                            // Don't tint the send button if it's the primary color
                            if (child.id == R.id.sendButton) {
                                child.imageTintList = ColorStateList.valueOf(Color.parseColor(theme.primaryColor))
                            } else {
                                child.imageTintList = ColorStateList.valueOf(onBpColor)
                            }
                        }
                        is android.widget.EditText -> {
                            child.setTextColor(textPrimary)
                            child.setHintTextColor(onBpColor.withAlpha(128))
                        }
                        is ViewGroup -> applyThemeToBottomPanel(child, theme)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun isColorLight(color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness < 0.5
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
            backgroundImageUrl = obj.optString("backgroundImageUrl", ""),
            chatListBackgroundImageUrl = obj.optString("chatListBackgroundImageUrl", ""),
            bottomPanelColor = obj.optString("bottomPanelColor", ""),
            onBottomPanelColor = obj.optString("onBottomPanelColor", "")
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
            put("chatListBackgroundImageUrl", theme.chatListBackgroundImageUrl)
            put("bottomPanelColor", theme.bottomPanelColor)
            put("onBottomPanelColor", theme.onBottomPanelColor)
        }.toString()
    }
}
