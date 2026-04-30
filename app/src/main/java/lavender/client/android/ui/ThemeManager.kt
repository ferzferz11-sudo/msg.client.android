package lavender.client.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import org.json.JSONObject

object ThemeManager {
    private var currentCustomTheme: CustomThemeProto? = null

    val builtInThemes = listOf(
        CustomThemeProto(
            id = "builtin_green",
            name = "Зеленый лес",
            primaryColor = "#2E7D32",
            backgroundColor = "#F1F8E9",
            surfaceColor = "#DCEDC8",
            textPrimaryColor = "#1B5E20",
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#33691E",
            bottomPanelColor = "#E8F5E9",
            onBottomPanelColor = "#2E7D32",
            isDark = false
        ),
        CustomThemeProto(
            id = "builtin_blue",
            name = "Современный синий",
            primaryColor = "#007AFF", // #1565C0
            backgroundColor = "#E3F2FD", // #F8FAFF
            surfaceColor = "#FFFFFF", // #BBDEFB
            textPrimaryColor = "#1C1C1E", // #0D47A1
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#3A3A3C", //Второстепенные элементы 01579B
            bottomPanelColor = "#E3F2FD", //нижняя панель
            onBottomPanelColor = "#1565C0",
            isDark = false
        ),
        CustomThemeProto(
            id = "builtin_purple",
            name = "Королевский пурпур",
            primaryColor = "#6A1B9A",
            backgroundColor = "#F3E5F5",
            surfaceColor = "#E1BEE7",
            textPrimaryColor = "#4A148C",
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#4A148C",
            bottomPanelColor = "#F3E5F5",
            onBottomPanelColor = "#6A1B9A",
            isDark = false
        ),
        CustomThemeProto(
            id = "builtin_sunset",
            name = "Закатный оранжевый",
            primaryColor = "#D84315",
            backgroundColor = "#FFF3E0",
            surfaceColor = "#FFE0B2",
            textPrimaryColor = "#BF360C",
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#E65100",
            bottomPanelColor = "#FFF3E0",
            onBottomPanelColor = "#D84315",
            isDark = false
        )
    )

    fun loadTheme(context: Context, username: String, onComplete: () -> Unit = {}) {
        val prefs = context.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
        var themeId = prefs.getString("current_theme_id", "dark") ?: "dark"
        
        // Migrate users from light theme to dark theme
        if (themeId == "light") {
            themeId = "dark"
            prefs.edit { putString("current_theme_id", "dark") }
        }
        
        if (themeId == "dark") {
            currentCustomTheme = null
            onComplete()
            return
        }

        // Check built-in themes first
        val builtIn = builtInThemes.find { it.id == themeId }
        if (builtIn != null) {
            currentCustomTheme = builtIn
            onComplete()
            return
        }

        val cachedTheme = prefs.getString("custom_theme_json_$themeId", null)
        if (cachedTheme != null) {
            currentCustomTheme = parseThemeFromJson(cachedTheme)
            onComplete()
        }

        GrpcClient.getThemes(username) { _, themes ->
            val theme = themes.find { it.id == themeId }
            if (theme != null) {
                currentCustomTheme = theme
                prefs.edit { putString("custom_theme_json_$themeId", serializeThemeToJson(theme)) }
                onComplete()
            }
        }
    }

    fun clearTheme() {
        currentCustomTheme = null
    }

    fun clearAllCaches(context: Context) {
        val prefs = context.getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
        val allKeys = prefs.all.keys
        prefs.edit {
            allKeys.filter { it.startsWith("custom_theme_json_") }.forEach { remove(it) }
        }
        currentCustomTheme = null
    }

    fun applyTheme(activity: AppCompatActivity) {
        val theme = currentCustomTheme
        val root = activity.findViewById<View>(android.R.id.content)

        @Suppress("DEPRECATION")
        activity.window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = Color.TRANSPARENT
        
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.window, false)

        if (theme == null) {
            val typedValue = android.util.TypedValue()
            activity.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
            val onPrimary = if (typedValue.resourceId != 0) ContextCompat.getColor(activity, typedValue.resourceId) else typedValue.data
            findAndTintToolbars(root, onPrimary)
            return
        }
        
        applyThemeToView(root, theme)
        
        activity.findViewById<View>(R.id.bottomPanel)?.let { 
            applyThemeToBottomPanel(it, theme)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val controller = activity.window.insetsController
            if (controller != null) {
                try {
                    val primaryColor = theme.primaryColor.toColorInt()
                    val isLight = isColorLight(primaryColor)
                    
                    if (isLight) {
                        controller.setSystemBarsAppearance(
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        )
                        controller.setSystemBarsAppearance(
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                            android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        )
                    } else {
                        controller.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                        controller.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
                    }
                } catch (_: Exception) {}
            }
        }
        
        try {
            val bgColor = theme.backgroundColor.toColorInt()
            activity.window.decorView.setBackgroundColor(bgColor)
            root.setBackgroundColor(bgColor)
            
            activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.swipeRefreshLayout)?.setBackgroundColor(bgColor)
            
            (root as? ViewGroup)?.getChildAt(0)?.setBackgroundColor(Color.TRANSPARENT)
            
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
            val primaryColor = theme.primaryColor.toColorInt()
            val onPrimaryColor = theme.onPrimaryColor.toColorInt()
            val backgroundColor = theme.backgroundColor.toColorInt()
            val textPrimaryColor = theme.textPrimaryColor.toColorInt()
            val surfaceColor = theme.surfaceColor.toColorInt()
            val onSurfaceColor = theme.onSurfaceColor.toColorInt()

            when (view) {
                is com.google.android.material.appbar.MaterialToolbar -> {
                    view.backgroundTintList = ColorStateList.valueOf(primaryColor)
                    view.setTitleTextColor(onPrimaryColor)
                    view.setSubtitleTextColor(onPrimaryColor)
                    view.setNavigationIconTint(onPrimaryColor)
                    
                    val overflowIcon = view.overflowIcon
                    if (overflowIcon != null) {
                        val tintedIcon = androidx.core.graphics.drawable.DrawableCompat.wrap(overflowIcon).mutate()
                        androidx.core.graphics.drawable.DrawableCompat.setTint(tintedIcon, onPrimaryColor)
                        view.overflowIcon = tintedIcon
                    }

                    for (i in 0 until view.menu.size()) {
                        val item = view.menu.getItem(i)
                        item.icon?.setTint(onPrimaryColor)
                    }

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
                            val bpColor = theme.bottomPanelColor.toColorInt()
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
            
            if (view.tag == "themed_background") {
                view.setBackgroundColor(backgroundColor)
            }

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
            val bpColor = theme.bottomPanelColor.toColorInt()
            val onBpColor = theme.onBottomPanelColor.toColorInt()
            val textPrimary = theme.textPrimaryColor.toColorInt()

            if (view is MaterialCardView) {
                view.setCardBackgroundColor(bpColor)
            }
            
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    when (child) {
                        is android.widget.ImageButton -> {
                            if (child.id == R.id.sendButton) {
                                child.imageTintList = ColorStateList.valueOf(theme.primaryColor.toColorInt())
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
