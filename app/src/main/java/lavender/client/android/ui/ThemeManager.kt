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

    val builtInThemes = listOf(
        CustomThemeProto(
            id = "builtin_green",
            name = "Зеленый лес",
            primaryColor = "#2E7D32",
            backgroundColor = "#F8FAF5",
            surfaceColor = "#EEF7E2",
            surfaceContainer = "#E1EDD1", // Более темный зеленый для меню
            textPrimaryColor = "#144218",
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#33691E",
            bottomPanelColor = "#FFFFFF",
            onBottomPanelColor = "#2E7D32"
        ),
        CustomThemeProto(
            id = "builtin_blue",
            name = "Современный синий",
            primaryColor = "#007AFF",
            backgroundColor = "#E3F2FD",
            surfaceColor = "#FFFFFF",
            surfaceContainer = "#D1E9FF", // Голубой контейнер для меню
            textPrimaryColor = "#1C1C1E",
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#3A3A3C",
            bottomPanelColor = "#E3F2FD",
            onBottomPanelColor = "#1565C0"
        ),
        CustomThemeProto(
            id = "builtin_purple",
            name = "Королевский пурпур",
            primaryColor = "#6A1B9A",
            backgroundColor = "#FBF8FF",
            surfaceColor = "#F0E2F5",
            surfaceContainer = "#E8D0F0", // Пурпурный контейнер
            textPrimaryColor = "#2D0C54",
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#4A148C",
            bottomPanelColor = "#FFFFFF",
            onBottomPanelColor = "#6A1B9A"
        ),
        CustomThemeProto(
            id = "builtin_sunset",
            name = "Закатный оранжевый",
            primaryColor = "#D84315",
            backgroundColor = "#FFF3E0",
            surfaceColor = "#FFE0B2",
            surfaceContainer = "#FFD180", // Оранжевый контейнер
            textPrimaryColor = "#BF360C",
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#E65100",
            bottomPanelColor = "#FFF3E0",
            onBottomPanelColor = "#D84315"
        ),
        CustomThemeProto(
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
        ),
        CustomThemeProto(
            id = "builtin_rose",
            name = "Северная роза",
            primaryColor = "#B08990",
            backgroundColor = "#EAECEF",
            surfaceColor = "#F9F6F7",
            surfaceContainer = "#D8DCE3", // Холодный серый контейнер
            textPrimaryColor = "#443C3D",
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#5E5455",
            bottomPanelColor = "#EAECEF",
            onBottomPanelColor = "#B08990"
        ),
        CustomThemeProto(
            id = "builtin_mint",
            name = "Кибер Мята",
            primaryColor = "#00BFA5",
            backgroundColor = "#F1FBF9",
            surfaceColor = "#FFFFFF",
            surfaceContainer = "#D7F2ED", // Мятный контейнер
            textPrimaryColor = "#002B26",
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#004D40",
            bottomPanelColor = "#FFFFFF",
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
        val theme = currentCustomTheme
        val typedValue = TypedValue()
        val res = context.resources
        val pkg = context.packageName

        return if (theme != null) {
            // --- 1. КАСТОМНЫЕ И ВСТРОЕННЫЕ ТЕМЫ ---
            MessageColors(
                incomingBg = parseSafeColor(theme.primaryColor, Color.BLUE),
                incomingText = parseSafeColor(theme.onPrimaryColor, Color.WHITE),
                outgoingBg = parseSafeColor(theme.surfaceColor, Color.LTGRAY),
                outgoingText = parseSafeColor(theme.textPrimaryColor, Color.BLACK)
            )
        } else {
            // --- 2. СТАНДАРТНАЯ ТЕМНАЯ ТЕМА (Твой концепт) ---

            // Входящие (Акцентные - Deep Purple)
            val attrPrimary = res.getIdentifier("colorPrimary", "attr", pkg)
            val attrOnPrimary = res.getIdentifier("colorOnPrimary", "attr", pkg)

            val incBg = if (attrPrimary != 0 && context.theme.resolveAttribute(attrPrimary, typedValue, true)) {
                typedValue.data
            } else {
                Color.parseColor("#6A1B9A") // Наш хардкод-фиолетовый
            }

            val incText = if (attrOnPrimary != 0 && context.theme.resolveAttribute(attrOnPrimary, typedValue, true)) {
                typedValue.data
            } else {
                Color.WHITE
            }

            // Исходящие (Фоновые - Темно-синие)
            // ВАЖНО: Мы будем искать именно colorSurfaceContainer,
            // который ты настроил как #1A1B46 в themes.xml
            val attrSurface = res.getIdentifier("colorSurfaceContainer", "attr", pkg)
            val attrOnSurface = res.getIdentifier("colorOnSurface", "attr", pkg)

            val outBg = if (attrSurface != 0 && context.theme.resolveAttribute(attrSurface, typedValue, true)) {
                // Если атрибут найден, берем его. Если он вернул 0, берем наш синий.
                if (typedValue.data != 0) typedValue.data else Color.parseColor("#1A1B46")
            } else {
                Color.parseColor("#1A1B46") // Твой темно-синий
            }

            val outText = if (attrOnSurface != 0 && context.theme.resolveAttribute(attrOnSurface, typedValue, true)) {
                typedValue.data
            } else {
                Color.parseColor("#E6E6FA") // Лавандовый текст
            }

            MessageColors(incBg, incText, outBg, outText)
        }
    }

    fun loadTheme(context: Context, username: String, onComplete: () -> Unit = {}) {
        val prefs = context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        val themeId = prefs.getString("current_theme_id", "dark") ?: "dark"

        Log.d("ThemeManager", "READING PREFS: current_theme_id is '$themeId'")

        // 1. Если тема системная (dark/light), просто сбрасываем кастом и выходим
        if (themeId == "light" || themeId == "dark") {
            currentCustomTheme = null
            onComplete()
            return
        }

        // 2. Проверка встроенных (Built-in) тем
        val builtIn = builtInThemes.find { it.id == themeId }
        if (builtIn != null) {
            // 🛠️ ПРОВЕРЯЕМ ЛОКАЛЬНЫЙ ФОН ПЕРЕД ПРИМЕНЕНИЕМ
            val localBg = prefs.getString("bg_url_$themeId", null)

            currentCustomTheme = if (!localBg.isNullOrEmpty()) {
                // Если есть локальный фон — создаем копию темы с этой картинкой
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
                currentCustomTheme = parseThemeFromJson(cachedTheme)
                onComplete()
                return
            } catch (_: Exception) {}
        }

        // 4. Только если это кастомная тема пользователя, идем на сервер
        GrpcClient.getThemes(username) { _, themes ->
            val theme = themes.find { it.id == themeId }
            if (theme != null) {
                currentCustomTheme = theme
                prefs.edit { putString("custom_theme_json_$themeId", serializeThemeToJson(theme)) }

                // (context as? android.app.Activity)?.runOnUiThread { onComplete() }
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
            // remove("current_theme_id")
        }
        currentCustomTheme = null
    }

    fun applyTheme(activity: AppCompatActivity) {
        val theme = currentCustomTheme
        val root = activity.findViewById<View>(android.R.id.content) ?: return
        val toolbar = activity.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)

        // 1. Прозрачные панели
        activity.window.apply {
            @Suppress("DEPRECATION")
            statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            navigationBarColor = Color.TRANSPARENT
            WindowCompat.setDecorFitsSystemWindows(this, false)
        }

        // 2. ЦВЕТА
        val defaultBgColor = "#04052E".toColorInt()
        val defaultContainerColor = "#1A1B46".toColorInt()

        val bgColor = if (theme != null) {
            try { theme.backgroundColor.toColorInt() } catch (_: Exception) { defaultBgColor }
        } else {
            defaultBgColor
        }

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
            val surfaceColor = if (theme != null) {
                try { theme.surfaceColor.toColorInt() } catch (_: Exception) { defaultContainerColor }
            } else {
                defaultContainerColor
            }
            card.setCardBackgroundColor(ColorStateList.valueOf(surfaceColor))

            val textColor = if (theme != null) {
                try { theme.textPrimaryColor.toColorInt() } catch (_: Exception) { Color.WHITE }
            } else {
                Color.WHITE
            }

            card.findViewById<EditText>(R.id.searchEditText)?.apply {
                setTextColor(textColor)
                setHintTextColor(adjustAlpha(textColor, 0.6f))
            }

            card.findViewById<ImageView>(R.id.searchIcon)?.imageTintList = ColorStateList.valueOf(textColor)
        }

        // 5. ЛОГИКА ДЛЯ ДЕФОЛТНОЙ ТЕМЫ
        if (theme == null) {
            Log.d("ThemeManager", "✅ Applying system theme")

            val typedValue = TypedValue()
            // Используем полный путь к ресурсу Material, чтобы избежать Unresolved reference
            activity.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
            val systemPrimaryColor = typedValue.data

            val onPrimaryValue = TypedValue()
            activity.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, onPrimaryValue, true)
            val onPrimaryColor = onPrimaryValue.data

            toolbar?.apply {
                backgroundTintList = ColorStateList.valueOf(systemPrimaryColor)
                setTitleTextColor(onPrimaryColor)
                setNavigationIconTint(onPrimaryColor)
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                clipToOutline = true
                popupTheme = androidx.appcompat.R.style.ThemeOverlay_AppCompat_Dark
            }

            activity.findViewById<ImageView>(R.id.actionDelete)?.imageTintList = ColorStateList.valueOf(onPrimaryColor)
            return
        }

        // 6. ЛОГИКА ДЛЯ КАСТОМНОЙ ТЕМЫ
        Log.d("ThemeManager", "SUCCESS: Applying custom theme '${theme.name}'")

        val customPrimary = try { theme.primaryColor.toColorInt() } catch (_: Exception) { defaultBgColor }
        val customOnPrimary = try { theme.onPrimaryColor.toColorInt() } catch (_: Exception) { Color.WHITE }

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

        // Кнопка удаления для кастомной темы
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
            val context = view.context

            // Используем наш безопасный парсер (который мы добавили в ThemeManager)
            val bpColor = parseSafeColor(theme.bottomPanelColor, Color.WHITE)
            val onBpColor = parseSafeColor(theme.onBottomPanelColor, Color.BLACK)
            val primaryColor = parseSafeColor(theme.primaryColor, Color.BLUE)
            val textPrimary = parseSafeColor(theme.textPrimaryColor, Color.BLACK)

            // 1. Покраска самой подложки (Card или Layout)
            if (view is MaterialCardView) {
                view.setCardBackgroundColor(bpColor)
                view.strokeColor = adjustAlpha(onBpColor, 0.1f)
            } else if (view.id == R.id.bottomPanelContent) {
                view.setBackgroundColor(bpColor)
            }

            // 2. Рекурсивный обход детей
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    when (child) {
                        is ImageButton -> {
                            // Кнопка отправки — главный акцент, остальные — вторичные
                            val tint = if (child.id == R.id.sendButton) primaryColor else onBpColor
                            child.imageTintList = ColorStateList.valueOf(tint)
                        }
                        is EditText -> {
                            child.setTextColor(textPrimary)
                            child.setHintTextColor(adjustAlpha(onBpColor, 0.5f))

                            // Красим курсор (API 29+)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                child.textCursorDrawable?.setTint(primaryColor)
                            }

                            // Добавляем цвет выделения текста для стиля
                            child.highlightColor = adjustAlpha(primaryColor, 0.3f)
                        }
                        is ProgressBar -> {
                            child.indeterminateTintList = ColorStateList.valueOf(primaryColor)
                        }
                        is TextView -> {
                            // Исключаем EditText, так как он уже обработан выше (EditText наследуется от TextView)
                            if (child !is EditText) {
                                child.setTextColor(onBpColor)
                            }
                        }
                        // Если внутри еще один контейнер — ныряем глубже
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
            // 1. БЕЗОПАСНЫЙ ПАРСИНГ (чтобы не упасть на пустых строках)
            val primary = parseSafeColor(theme.primaryColor, Color.BLUE)
            val onPrimary = parseSafeColor(theme.onPrimaryColor, Color.WHITE)
            val surface = parseSafeColor(theme.surfaceColor, Color.WHITE)
            val onSurface = parseSafeColor(theme.onSurfaceColor, Color.GRAY)
            val textPrimary = parseSafeColor(theme.textPrimaryColor, Color.BLACK)

            // 2. ПОКРАСКА КОНКРЕТНЫХ КОМПОНЕНТОВ
            when (view) {
                // Тулбар
                is MaterialToolbar -> {
                    view.backgroundTintList = ColorStateList.valueOf(primary)
                    view.setTitleTextColor(onPrimary)
                    view.setSubtitleTextColor(onPrimary)
                    view.setNavigationIconTint(onPrimary)
                    view.overflowIcon?.setTint(onPrimary)
                    // Красим иконки меню
                    for (i in 0 until view.menu.size()) {
                        view.menu.getItem(i).icon?.setTint(onPrimary)
                    }
                    // Красим кастомные вьюхи внутри тулбара (например, заголовок по центру)
                    for (i in 0 until view.childCount) {
                        applyColorToToolbarChild(view.getChildAt(i), onPrimary)
                    }
                }

                // Кнопки
                is MaterialButton -> {
                    // Если это TextButton (без фона), красим только текст и иконку
                    if (view.backgroundTintList == null || view.stateListAnimator == null) {
                        view.setTextColor(primary)
                        view.iconTint = ColorStateList.valueOf(primary)
                    } else {
                        view.backgroundTintList = ColorStateList.valueOf(primary)
                        view.setTextColor(onPrimary)
                        view.iconTint = ColorStateList.valueOf(onPrimary)
                    }
                }

                // FAB (Плавающая кнопка)
                is FloatingActionButton -> {
                    view.backgroundTintList = ColorStateList.valueOf(primary)
                    view.imageTintList = ColorStateList.valueOf(onPrimary)
                }

                // Текстовые поля
                is EditText -> {
                    view.setTextColor(textPrimary)
                    view.setHintTextColor(adjustAlpha(onSurface, 0.5f))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        view.textCursorDrawable?.setTint(primary)
                    }
                }

                // Обычный текст
                is TextView -> {
                    // Не трогаем заголовки тулбара (они красятся отдельно)
                    if (view.id != R.id.toolbarTitle && view.id != R.id.toolbarSubtitle) {
                        // Если это вторичный текст (например, время или статус), можно использовать onSurface
                        // Но по умолчанию используем основной текст
                        view.setTextColor(textPrimary)
                    }
                }

                // Карточки
                is MaterialCardView -> {
                    // Не перекрашиваем нижнюю панель здесь (для неё отдельный метод)
                    if (view.id != R.id.bottomPanel) {
                        view.setCardBackgroundColor(surface)
                        view.strokeColor = adjustAlpha(onSurface, 0.2f)
                    }
                }

                // Индикаторы загрузки
                is ProgressBar -> {
                    view.indeterminateTintList = ColorStateList.valueOf(primary)
                }

                // Чекбоксы и Свитчи
                is MaterialCheckBox -> {
                    view.buttonTintList = ColorStateList.valueOf(primary)
                    view.setTextColor(textPrimary)
                }
            }

            // 3. РЕКУРСИЯ (Идем вглубь контейнеров)
            if (view is ViewGroup && view !is MaterialToolbar) {
                for (i in 0 until view.childCount) {
                    applyThemeToView(view.getChildAt(i), theme)
                }
            }
        } catch (e: Exception) {
            Log.e("ThemeManager", "Error in applyThemeToView", e)
        }
    }

    private fun findAndTintToolbars(view: View, color: Int) {
        if (view is MaterialToolbar) {
            // 1. Красим стандартные элементы
            view.setTitleTextColor(color)
            view.setSubtitleTextColor(color)
            view.setNavigationIconTint(color)
            view.overflowIcon?.setTint(color)

            // 2. Красим иконки меню
            for (i in 0 until view.menu.size()) {
                view.menu.getItem(i).icon?.setTint(color)
            }

            // 3. Красим кастомные View внутри тулбара (например, R.id.toolbarTitle)
            for (i in 0 until view.childCount) {
                applyColorToToolbarChild(view.getChildAt(i), color)
            }
        } else if (view is ViewGroup) {
            // Рекурсивно ищем тулбар в иерархии
            for (i in 0 until view.childCount) {
                findAndTintToolbars(view.getChildAt(i), color)
            }
        }
    }

    private fun applyColorToToolbarChild(view: View, color: Int) {
        when (view) {
            is TextView -> {
                view.setTextColor(color)
            }
            is ImageView -> {
                // Используем TintList — это более гибко для Material-компонентов
                view.imageTintList = ColorStateList.valueOf(color)
            }
            is ViewGroup -> {
                // Если внутри тулбара есть контейнер (например, заголовок + подзаголовок в LinearLayout)
                for (i in 0 until view.childCount) {
                    applyColorToToolbarChild(view.getChildAt(i), color)
                }
            }
        }
    }

    // Безопасно парсит HEX-строку в Color Int. Если строка пустая или формат неверный — возвращает дефолтный цвет.
    private fun parseSafeColor(colorStr: String?, defaultColor: Int): Int {
        if (colorStr.isNullOrEmpty()) return defaultColor
        return try {
            colorStr.toColorInt()
        } catch (_: Exception) {
            Log.e("ThemeManager", "Invalid color string: $colorStr")
            defaultColor
        }
    }

    // Меняет прозрачность (alpha) для существующего цвета. Factor от 0.0 до 1.0
     fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).roundToInt()
        val red   = Color.red(color)
        val green = Color.green(color)
        val blue  = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    // Расширение для определения яркости цвета.
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

        // 1. Сохраняем ссылку локально
        prefs.edit {
            putString("bg_url_$themeId", imageUrl)
        }

        // 2. Обновляем текущую тему в памяти, если она активна
        val current = currentCustomTheme
        if (current != null && current.id == themeId) {
            currentCustomTheme = current.copy(backgroundImageUrl = imageUrl)
        }

        Log.d("ThemeManager", "Background override saved for $themeId: $imageUrl")
    }

    // toDo: make interface
    fun clearBackgroundOverride(context: Context, username: String, themeId: String) {
        val prefs = context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            remove("bg_url_$themeId")
        }
        // Теперь используем реальный username для перезагрузки
        loadTheme(context, username)
    }
}
