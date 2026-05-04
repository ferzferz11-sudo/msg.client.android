package lavender.client.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.floatingactionbutton.FloatingActionButton
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import org.json.JSONObject
import kotlin.math.roundToInt

object ThemeManager {
    private var currentCustomTheme: CustomThemeProto? = null

    // Базовая светлая тема (Графит и золото)
    private val baseLightTheme = CustomThemeProto(
        id = "builtin_graphite",
        name = "Графит и золото",
        primaryColor = "#85754E",
        backgroundColor = "#F5F5F5",
        surfaceColor = "#FFFFFF",
        surfaceContainer = "#E0E0E0", // Серый бетон для меню
        textPrimaryColor = "#212121",
        onPrimaryColor = "#FFFFFF",
        onSurfaceColor = "#424242",
        bottomPanelColor = "#FFFFFF",
        onBottomPanelColor = "#85754E"
    )

    // Базовая темная тема (не будет доступна для выбора)
    private val baseDarkTheme = CustomThemeProto(
        id = "dark",
        name = "Системная темная",
        primaryColor = "#1A1B46",
        backgroundColor = "#04052E",
        surfaceColor = "#1A1B46",
        surfaceContainer = "#1A1B46",
        textPrimaryColor = "#FFFFFF",
        onPrimaryColor = "#FFFFFF",
        onSurfaceColor = "#E0E0E0",
        bottomPanelColor = "#1A1B46",
        onBottomPanelColor = "#FFFFFF"
    )

    val builtInThemes = listOf(
        baseLightTheme.copy(
            id = "builtin_green",
            name = "Зеленый лес",
            primaryColor = "#2E7D32",
            backgroundColor = "#F8FAF5",
            surfaceColor = "#EEF7E2",
            surfaceContainer = "#E1EDD1",
            textPrimaryColor = "#144218",
            onSurfaceColor = "#33691E",
            onBottomPanelColor = "#2E7D32"
        ),
        baseLightTheme.copy(
            id = "builtin_blue",
            name = "Современный синий",
            primaryColor = "#007AFF",
            backgroundColor = "#E3F2FD",
            surfaceColor = "#FFFFFF",
            surfaceContainer = "#D1E9FF",
            textPrimaryColor = "#1C1C1E",
            onSurfaceColor = "#3A3A3C",
            bottomPanelColor = "#E3F2FD",
            onBottomPanelColor = "#1565C0"
        ),
        baseLightTheme.copy(
            id = "builtin_purple",
            name = "Королевский пурпур",
            primaryColor = "#6A1B9A",
            backgroundColor = "#FBF8FF",
            surfaceColor = "#F0E2F5",
            surfaceContainer = "#E8D0F0",
            textPrimaryColor = "#2D0C54",
            onSurfaceColor = "#4A148C",
            onBottomPanelColor = "#6A1B9A"
        ),
        baseLightTheme.copy(
            id = "builtin_sunset",
            name = "Закатный оранжевый",
            primaryColor = "#D84315",
            backgroundColor = "#FFF3E0",
            surfaceColor = "#FFE0B2",
            surfaceContainer = "#FFD180",
            textPrimaryColor = "#BF360C",
            onSurfaceColor = "#E65100",
            bottomPanelColor = "#FFF3E0",
            onBottomPanelColor = "#D84315"
        ),
        baseLightTheme, // Графит и золото, теперь он же и базовый
        baseLightTheme.copy(
            id = "builtin_rose",
            name = "Северная роза",
            primaryColor = "#B08990",
            backgroundColor = "#EAECEF",
            surfaceColor = "#F9F6F7",
            surfaceContainer = "#D8DCE3",
            textPrimaryColor = "#443C3D",
            onSurfaceColor = "#5E5455",
            bottomPanelColor = "#EAECEF",
            onBottomPanelColor = "#B08990"
        ),
        baseLightTheme.copy(
            id = "builtin_mint",
            name = "Кибер Мята",
            primaryColor = "#00BFA5",
            backgroundColor = "#F1FBF9",
            surfaceColor = "#FFFFFF",
            surfaceContainer = "#D7F2ED",
            textPrimaryColor = "#002B26",
            onSurfaceColor = "#004D40",
            onBottomPanelColor = "#00BFA5"
        )
    )

    // Добавь это внутрь ThemeManager
    data class MessageColors(
        val incomingBg: Int,
        val incomingText: Int,
        val outgoingBg: Int,
        val outgoingText: Int
    )

    fun getMessageColors(context: Context): MessageColors {
        val theme = currentCustomTheme ?: baseDarkTheme // Используем темную как фоллбэк
        return MessageColors(
            incomingBg = parseSafeColor(theme.primaryColor, Color.BLUE),
            incomingText = parseSafeColor(theme.onPrimaryColor, Color.WHITE),
            outgoingBg = parseSafeColor(theme.surfaceColor, Color.LTGRAY),
            outgoingText = parseSafeColor(theme.textPrimaryColor, Color.BLACK)
        )
    }

    fun loadTheme(context: Context, username: String, onComplete: () -> Unit = {}) {
        val prefs = context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        val themeId = prefs.getString("current_theme_id", "dark") ?: "dark"

        Log.d("ThemeManager", "READING PREFS: current_theme_id is '$themeId'")

        // 1. Обработка системных тем
        if (themeId == "light") {
            currentCustomTheme = baseLightTheme
            onComplete()
            return
        }
        if (themeId == "dark") {
            currentCustomTheme = baseDarkTheme
            onComplete()
            return
        }

        // 2. Проверка встроенных (Built-in) тем
        val builtIn = builtInThemes.find { it.id == themeId }
        if (builtIn != null) {
            val localBg = prefs.getString("bg_url_$themeId", null)
            currentCustomTheme = if (!localBg.isNullOrEmpty()) {
                builtIn.copy(backgroundImageUrl = localBg)
            } else {
                builtIn
            }
            onComplete()
            return
        }

        // 3. Если не встроенная, пробуем КЭШ для скорости
        val cachedTheme = prefs.getString("custom_theme_json_$themeId", null)
        if (cachedTheme != null) {
            try {
                // Применяем кастомную тему поверх базовой светлой
                val custom = parseThemeFromJson(cachedTheme)
                currentCustomTheme = baseLightTheme.copy(
                    id = custom.id,
                    name = custom.name,
                    primaryColor = custom.primaryColor,
                    onPrimaryColor = custom.onPrimaryColor,
                    surfaceColor = custom.surfaceColor,
                    onSurfaceColor = custom.onSurfaceColor,
                    backgroundColor = custom.backgroundColor,
                    textPrimaryColor = custom.textPrimaryColor,
                    backgroundImageUrl = custom.backgroundImageUrl,
                    bottomPanelColor = custom.bottomPanelColor,
                    onBottomPanelColor = custom.onBottomPanelColor
                )
                onComplete()
                return
            } catch (_: Exception) {}
        }

        // 4. Только если это кастомная тема пользователя, идем на сервер
        GrpcClient.getThemes(username) { _, themes ->
            val theme = themes.find { it.id == themeId }
            if (theme != null) {
                // Применяем скачанную тему поверх базовой светлой
                currentCustomTheme = baseLightTheme.copy(
                    id = theme.id,
                    name = theme.name,
                    primaryColor = theme.primaryColor,
                    onPrimaryColor = theme.onPrimaryColor,
                    surfaceColor = theme.surfaceColor,
                    onSurfaceColor = theme.onSurfaceColor,
                    backgroundColor = theme.backgroundColor,
                    textPrimaryColor = theme.textPrimaryColor,
                    backgroundImageUrl = theme.backgroundImageUrl,
                    bottomPanelColor = theme.bottomPanelColor,
                    onBottomPanelColor = theme.onBottomPanelColor
                )
                prefs.edit { putString("custom_theme_json_$themeId", serializeThemeToJson(theme)) }
            }

            Handler(Looper.getMainLooper()).post {
                onComplete()
            }
        }
    }

    // Внутри object ThemeManager
    fun getCurrentTheme(): CustomThemeProto? = currentCustomTheme

    // Temporary unused
    fun clearAllCaches(context: Context) {
        val prefs = context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            val allKeys = prefs.all.keys
            allKeys.filter { it.startsWith("custom_theme_json_") }.forEach { remove(it) }
        }
        currentCustomTheme = null
    }

    fun applyTheme(activity: AppCompatActivity) {
        val theme = currentCustomTheme ?: baseDarkTheme // Фоллбэк на темную тему
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        val toolbar = activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)

        // 1. Прозрачные панели
        activity.window.apply {
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }

        // 2. ЦВЕТА
        val bgColor = parseSafeColor(theme.backgroundColor, Color.BLACK)
        val isLightMode = bgColor.isLight()
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = isLightMode
            isAppearanceLightNavigationBars = isLightMode
        }

        // 3. ФОН
        activity.window.decorView.setBackgroundColor(bgColor)
        root.setBackgroundColor(bgColor)

        // 4. ТЕМИЗАЦИЯ КАРТОЧКИ ПОИСКА
        activity.findViewById<com.google.android.material.card.MaterialCardView>(R.id.searchCard)?.let { card ->
            val surfaceColor = parseSafeColor(theme.surfaceColor, Color.DKGRAY)
            card.setCardBackgroundColor(ColorStateList.valueOf(surfaceColor))

            val textColor = parseSafeColor(theme.textPrimaryColor, Color.WHITE)
            card.findViewById<EditText>(R.id.searchEditText)?.apply {
                setTextColor(textColor)
                setHintTextColor(adjustAlpha(textColor, 0.6f))
            }
            card.findViewById<ImageView>(R.id.searchIcon)?.imageTintList = ColorStateList.valueOf(textColor)
        }

        // 5. ЛОГИКА ДЛЯ КАСТОМНОЙ ТЕМЫ (теперь все темы кастомные)
        Log.d("ThemeManager", "SUCCESS: Applying theme '${theme.name}'")

        val customPrimary = parseSafeColor(theme.primaryColor, Color.BLUE)
        val customOnPrimary = parseSafeColor(theme.onPrimaryColor, Color.WHITE)

        toolbar?.apply {
            backgroundTintList = ColorStateList.valueOf(customPrimary)
            setTitleTextColor(customOnPrimary)
            setNavigationIconTint(customOnPrimary)
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            popupTheme = if (isLightMode)
                androidx.appcompat.R.style.ThemeOverlay_AppCompat_Light
            else
                androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dark
        }

        activity.findViewById<ImageView>(R.id.actionDelete)?.imageTintList = ColorStateList.valueOf(customOnPrimary)
        applyThemeToView(root, theme)
        activity.findViewById<View>(R.id.bottomPanel)?.let {
            applyThemeToBottomPanel(it, theme)
        }

        // Фоновое изображение
        val bgImageView = activity.findViewById<ImageView>(R.id.chatBackground)
        if (bgImageView != null) {
            if (!theme.backgroundImageUrl.isNullOrEmpty()) {
                bgImageView.visibility = View.VISIBLE
                Glide.with(activity)
                    .load(theme.backgroundImageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(bgImageView)
                root.setBackgroundColor(Color.TRANSPARENT)
            } else {
                bgImageView.visibility = View.GONE
            }
        }
    }

    private fun applyThemeToBottomPanel(view: View, theme: CustomThemeProto) {
        try {
            val bpColor = parseSafeColor(theme.bottomPanelColor, Color.WHITE)
            val onBpColor = parseSafeColor(theme.onBottomPanelColor, Color.BLACK)
            val primaryColor = parseSafeColor(theme.primaryColor, Color.BLUE)
            val textPrimary = parseSafeColor(theme.textPrimaryColor, Color.BLACK)

            if (view is MaterialCardView) {
                view.setCardBackgroundColor(bpColor)
                view.strokeColor = adjustAlpha(onBpColor, 0.1f)
            } else if (view.id == R.id.bottomPanelContent) {
                view.setBackgroundColor(bpColor)
            }

            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    when (child) {
                        is ImageButton -> {
                            child.imageTintList = ColorStateList.valueOf(primaryColor)
                        }
                        is EditText -> {
                            child.setTextColor(textPrimary)
                            child.setHintTextColor(adjustAlpha(onBpColor, 0.5f))
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                child.textCursorDrawable?.setTint(primaryColor)
                            }
                            child.highlightColor = adjustAlpha(primaryColor, 0.3f)
                        }
                        is ProgressBar -> {
                            child.indeterminateTintList = ColorStateList.valueOf(primaryColor)
                        }
                        is TextView -> {
                            if (child !is EditText) {
                                child.setTextColor(onBpColor)
                            }
                        }
                        is ViewGroup -> applyThemeToBottomPanel(child, theme)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ThemeManager", "Error applying theme to bottom panel", e)
        }
    }

    fun applyThemeToView(view: View, theme: CustomThemeProto) {
        try {
            val primary = parseSafeColor(theme.primaryColor, Color.BLUE)
            val onPrimary = parseSafeColor(theme.onPrimaryColor, Color.WHITE)
            val surface = parseSafeColor(theme.surfaceColor, Color.WHITE)
            val onSurface = parseSafeColor(theme.onSurfaceColor, Color.GRAY)
            val textPrimary = parseSafeColor(theme.textPrimaryColor, Color.BLACK)

            when (view) {
                is MaterialToolbar -> {
                    view.backgroundTintList = ColorStateList.valueOf(primary)
                    view.setTitleTextColor(onPrimary)
                    view.setSubtitleTextColor(onPrimary)
                    view.setNavigationIconTint(onPrimary)
                    view.overflowIcon?.setTint(onPrimary)
                    for (i in 0 until view.menu.size()) {
                        view.menu.getItem(i).icon?.setTint(onPrimary)
                    }
                    for (i in 0 until view.childCount) {
                        applyColorToToolbarChild(view.getChildAt(i), onPrimary)
                    }
                }
                is MaterialButton -> {
                    val isTextButton = view.backgroundTintList == null
                            || view.backgroundTintList?.defaultColor == Color.TRANSPARENT
                    if (isTextButton) {
                        view.setTextColor(primary)
                        view.iconTint = ColorStateList.valueOf(primary)
                        view.rippleColor = ColorStateList.valueOf(adjustAlpha(primary, 0.12f))
                    } else {
                        view.backgroundTintList = ColorStateList.valueOf(surface)
                        view.setTextColor(textPrimary)
                        view.iconTint = ColorStateList.valueOf(textPrimary)
                        view.rippleColor = ColorStateList.valueOf(adjustAlpha(primary, 0.24f))
                    }
                }
                is FloatingActionButton -> {
                    view.backgroundTintList = ColorStateList.valueOf(primary)
                    view.imageTintList = ColorStateList.valueOf(onPrimary)
                }
                is EditText -> {
                    view.setTextColor(textPrimary)
                    view.setHintTextColor(adjustAlpha(onSurface, 0.5f))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        view.textCursorDrawable?.setTint(primary)
                    }
                }
                is TextView -> {
                    if (view.id != R.id.toolbarTitle && view.id != R.id.toolbarSubtitle) {
                        view.setTextColor(textPrimary)
                    }
                }
                is MaterialCardView -> {
                    if (view.id != R.id.bottomPanel) {
                        view.setCardBackgroundColor(surface)
                        view.strokeColor = adjustAlpha(onSurface, 0.2f)
                    }
                }
                is ProgressBar -> {
                    view.indeterminateTintList = ColorStateList.valueOf(primary)
                }
                is MaterialCheckBox -> {
                    view.buttonTintList = ColorStateList.valueOf(primary)
                    view.setTextColor(textPrimary)
                }
            }

            if (view is ViewGroup && view !is MaterialToolbar) {
                for (i in 0 until view.childCount) {
                    applyThemeToView(view.getChildAt(i), theme)
                }
            }
        } catch (e: Exception) {
            Log.e("ThemeManager", "Error in applyThemeToView", e)
        }
    }

    private fun applyColorToToolbarChild(view: View, color: Int) {
        when (view) {
            is TextView -> view.setTextColor(color)
            is ImageView -> view.imageTintList = ColorStateList.valueOf(color)
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    applyColorToToolbarChild(view.getChildAt(i), color)
                }
            }
        }
    }

    private fun parseSafeColor(colorStr: String?, defaultColor: Int): Int {
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
        val red   = Color.red(color)
        val green = Color.green(color)
        val blue  = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    private fun Int.isLight(): Boolean {
        val darkness = 1 - (0.299 * Color.red(this) +
                0.587 * Color.green(this) +
                0.114 * Color.blue(this)) / 255
        return darkness < 0.5
    }

    private fun parseThemeFromJson(json: String): CustomThemeProto {
        val obj = JSONObject(json)
        return CustomThemeProto(
            id = obj.optString("id", ""),
            name = obj.optString("name", "Unknown"),
            primaryColor = obj.optString("primaryColor", ""),
            onPrimaryColor = obj.optString("onPrimaryColor", ""),
            surfaceColor = obj.optString("surfaceColor", ""),
            onSurfaceColor = obj.optString("onSurfaceColor", ""),
            backgroundColor = obj.optString("backgroundColor", ""),
            textPrimaryColor = obj.optString("textPrimaryColor", ""),
            backgroundImageUrl = obj.optString("backgroundImageUrl", ""),
            bottomPanelColor = obj.optString("bottomPanelColor", ""),
            onBottomPanelColor = obj.optString("onBottomPanelColor", ""),
            textSecondaryColor = obj.optString("textSecondaryColor", ""),
            chatListBackgroundImageUrl = obj.optString("chatListBackgroundImageUrl", ""),
            surfaceContainer = obj.optString("surfaceContainer", ""),
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
            put("backgroundImageUrl", theme.backgroundImageUrl)
            put("bottomPanelColor", theme.bottomPanelColor)
            put("onBottomPanelColor", theme.onBottomPanelColor)
            put("textSecondaryColor", theme.textSecondaryColor)
            put("chatListBackgroundImageUrl", theme.chatListBackgroundImageUrl)
            put("surfaceContainer", theme.surfaceContainer)
        }.toString()
    }

    fun saveBackgroundOverride(context: Context, themeId: String, imageUrl: String) {
        val prefs = context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putString("bg_url_$themeId", imageUrl)
        }
        val current = currentCustomTheme
        if (current != null && current.id == themeId) {
            currentCustomTheme = current.copy(backgroundImageUrl = imageUrl)
        }
        Log.d("ThemeManager", "Background override saved for $themeId: $imageUrl")
    }

    fun clearBackgroundOverride(context: Context, username: String, themeId: String) {
        val prefs = context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            remove("bg_url_$themeId")
        }
        loadTheme(context, username)
    }
}
