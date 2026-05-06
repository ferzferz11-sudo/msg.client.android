package lavender.client.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.CustomThemeProto
import org.json.JSONObject
import kotlin.math.roundToInt

object ThemeManager {
    private const val LAVENDER_MIST = "#967BB6"

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
        onBottomPanelColor = "#85754E",
        outgoingBubbleColor = "#85754E", // Золотой акцент
        incomingBubbleColor = "#E0E0E0"  // Нейтральный серый
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
        onBottomPanelColor = LAVENDER_MIST,
        outgoingBubbleColor = "#2A2C6D", // Насыщенный синий
        incomingBubbleColor = "#16173A"  // Глубокий синий
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
            onBottomPanelColor = "#2E7D32",
            outgoingBubbleColor = "#2E7D32", // Темно-зеленый
            incomingBubbleColor = "#E1EDD1"  // Светлая хвоя
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
            onBottomPanelColor = "#1565C0",
            outgoingBubbleColor = "#007AFF", // Ярко-синий (iOS style)
            incomingBubbleColor = "#D1E9FF"  // Небесный
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
            onBottomPanelColor = "#6A1B9A",
            outgoingBubbleColor = "#6A1B9A", // Насыщенный пурпур
            incomingBubbleColor = "#E8D0F0"  // Нежная сирень
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
            onBottomPanelColor = "#D84315",
            outgoingBubbleColor = "#D84315", // Огненный
            incomingBubbleColor = "#FFD180"  // Теплый песок
        ),
        baseLightTheme.copy(
            outgoingBubbleColor = "#85754E", // Золото (из Графита)
            incomingBubbleColor = "#E0E0E0"  // Бетон
        ),
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
            onBottomPanelColor = "#B08990",
            outgoingBubbleColor = "#B08990", // Пыльная роза
            incomingBubbleColor = "#D8DCE3"  // Дымчатый
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
            onBottomPanelColor = "#00BFA5",
            outgoingBubbleColor = "#00BFA5", // Мята
            incomingBubbleColor = "#D7F2ED"  // Свежий лед
        )
    )

    data class MessageColors(
        val incomingBg: Int,
        val incomingText: Int,
        val outgoingBg: Int,
        val outgoingText: Int
    )

    fun getMessageColors(context: Context): MessageColors {
        val theme = currentCustomTheme ?: baseDarkTheme
        return MessageColors(
            incomingBg = parseSafeColor(theme.incomingBubbleColor, Color.BLUE),
            incomingText = parseSafeColor(theme.primaryColor, Color.WHITE),
            outgoingBg = parseSafeColor(theme.outgoingBubbleColor, Color.LTGRAY),
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

        // 3. Пробуем КЭШ для скорости
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
                    onBottomPanelColor = custom.onBottomPanelColor,
                    outgoingBubbleColor = custom.outgoingBubbleColor,
                    incomingBubbleColor = custom.incomingBubbleColor
                )
                onComplete()
                return
            } catch (_: Exception) {}
        }

        // 3. Только если это кастомная тема пользователя, идем на сервер
        // Используем id пользователя, если он доступен
        val queryId = GrpcClient.getUserId() ?: username
        GrpcClient.getThemes(queryId) { _, themes ->
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
                    onBottomPanelColor = theme.onBottomPanelColor,
                    outgoingBubbleColor = theme.outgoingBubbleColor,
                    incomingBubbleColor = theme.incomingBubbleColor
                )
                prefs.edit { putString("custom_theme_json_$themeId", serializeThemeToJson(theme)) }
            }

            Handler(Looper.getMainLooper()).post {
                onComplete()
            }
        }
    }

    fun getCurrentTheme(): CustomThemeProto? = currentCustomTheme

    fun clearAllCaches(context: Context) {
        val prefs = context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            val allKeys = prefs.all.keys
            allKeys.filter { it.startsWith("custom_theme_json_") }.forEach { remove(it) }
        }
        currentCustomTheme = null
    }

    fun applyTheme(activity: AppCompatActivity) {
        val theme = currentCustomTheme ?: baseDarkTheme
        val bgColor = parseSafeColor(theme.backgroundColor, Color.BLACK)
        val isLightMode = bgColor.isLight()

        // 1. Включаем Edge-to-Edge (бары становятся прозрачными, убираются warnings)
        activity.enableEdgeToEdge()

        // 2. Настраиваем цвет иконок системных баров (темные для светлых тем, белые для темных)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = isLightMode
            isAppearanceLightNavigationBars = isLightMode
        }

        // 3. Базовая установка фона
        val root = activity.findViewById<View>(android.R.id.content)
        activity.window.decorView.setBackgroundColor(bgColor)
        root?.setBackgroundColor(bgColor)

        // 4. Корректировка Toolbar (Padding для компенсации статус-бара)
        val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar?.let { tb ->
            ViewCompat.setOnApplyWindowInsetsListener(tb) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(0, insets.top, 0, 0)
                windowInsets
            }
        }

        // 5. Темы оформления поисковой карточки
        activity.findViewById<MaterialCardView>(R.id.searchCard)?.let { card ->
            val surfaceColor = parseSafeColor(theme.surfaceColor, Color.DKGRAY)
            card.setCardBackgroundColor(ColorStateList.valueOf(surfaceColor))

            val textColor = parseSafeColor(theme.textPrimaryColor, Color.WHITE)
            card.findViewById<EditText>(R.id.searchEditText)?.apply {
                setTextColor(textColor)
                setHintTextColor(adjustAlpha(textColor, 0.6f))
            }
            card.findViewById<ImageView>(R.id.searchIcon)?.imageTintList = ColorStateList.valueOf(textColor)
        }

        Log.d("ThemeManager", "SUCCESS: Applying theme '${theme.name}'")

        // 6. Акцентные цвета для Toolbar
        val customPrimary = parseSafeColor(theme.primaryColor, Color.BLUE)
        val customOnPrimary = parseSafeColor(theme.onPrimaryColor, Color.WHITE)

        toolbar?.apply {
            backgroundTintList = ColorStateList.valueOf(customPrimary)
            setTitleTextColor(customOnPrimary)
            setNavigationIconTint(customOnPrimary)
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }

        // 7. Иконки действий и дополнительные панели
        activity.findViewById<ImageView>(R.id.actionDelete)?.imageTintList = ColorStateList.valueOf(customOnPrimary)

        // 8. Фоновое изображение чата
        val bgImageView = activity.findViewById<ImageView>(R.id.chatBackground)
        if (bgImageView != null) {
            if (!theme.backgroundImageUrl.isNullOrEmpty()) {
                bgImageView.visibility = View.VISIBLE
                Glide.with(activity)
                    .load(theme.backgroundImageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(bgImageView)
                // Если есть картинка, делаем root прозрачным, чтобы видеть её
                root.setBackgroundColor(Color.TRANSPARENT)
            } else {
                bgImageView.visibility = View.GONE
            }
        }

        // 9. Нижняя панель чата
        activity.findViewById<MaterialCardView>(R.id.bottomPanel)?.let { panel ->
            val panelColor = parseSafeColor(theme.bottomPanelColor, bgColor)
            panel.setCardBackgroundColor(ColorStateList.valueOf(panelColor))

            val onPanelColor = parseSafeColor(theme.onBottomPanelColor, customPrimary)
            
            // Иконки
            panel.findViewById<ImageButton>(R.id.emojiButton)?.imageTintList = ColorStateList.valueOf(onPanelColor)
            panel.findViewById<ImageButton>(R.id.attachButton)?.imageTintList = ColorStateList.valueOf(onPanelColor)
            panel.findViewById<ImageButton>(R.id.audioButton)?.imageTintList = ColorStateList.valueOf(onPanelColor)
            panel.findViewById<ImageButton>(R.id.sendButton)?.imageTintList = ColorStateList.valueOf(onPanelColor)
            
            // Текст ввода
            panel.findViewById<EditText>(R.id.messageInput)?.apply {
                val textColor = parseSafeColor(theme.textPrimaryColor, Color.BLACK)
                setTextColor(textColor)
                setHintTextColor(adjustAlpha(textColor, 0.5f))
            }
        }
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
            textSecondaryColor = obj.optString("textSecondaryColor", ""),
            backgroundImageUrl = obj.optString("backgroundImageUrl", ""),
            chatListBackgroundImageUrl = obj.optString("chatListBackgroundImageUrl", ""),
            bottomPanelColor = obj.optString("bottomPanelColor", ""),
            onBottomPanelColor = obj.optString("onBottomPanelColor", ""),
            surfaceContainer = obj.optString("surfaceContainer", ""),
            outgoingBubbleColor = obj.optString("outgoingBubbleColor", ""),
            incomingBubbleColor = obj.optString("incomingBubbleColor", ""),
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
            put("outgoingBubbleColor", theme.outgoingBubbleColor)
            put("incomingBubbleColor", theme.incomingBubbleColor)
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