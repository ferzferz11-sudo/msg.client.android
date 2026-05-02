package lavender.client.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
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
import kotlin.math.roundToInt

object ThemeManager {
    private var currentCustomTheme: CustomThemeProto? = null

    val builtInThemes = listOf(
        CustomThemeProto(
            id = "builtin_green",
            name = "Зеленый лес",
            primaryColor = "#2E7D32",
            backgroundColor = "#F8FAF5", // F1F8E9
            surfaceColor = "#EEF7E2", // DCEDC8
            textPrimaryColor = "#144218", // 1B5E20
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#33691E",
            bottomPanelColor = "#FFFFFF", // E8F5E9
            onBottomPanelColor = "#2E7D32"
        ),
        CustomThemeProto(
            id = "builtin_blue",
            name = "Современный синий",
            primaryColor = "#007AFF", // #1565C0
            backgroundColor = "#E3F2FD", // #F8FAFF
            surfaceColor = "#FFFFFF", // #BBDEFB
            textPrimaryColor = "#1C1C1E", // #0D47A1
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#3A3A3C", // Второстепенные элементы 01579B
            bottomPanelColor = "#E3F2FD", // нижняя панель
            onBottomPanelColor = "#1565C0"
        ),
        CustomThemeProto(
            id = "builtin_purple",
            name = "Королевский пурпур",
            primaryColor = "#6A1B9A",
            backgroundColor = "#FBF8FF", // F3E5F5
            surfaceColor = "#F0E2F5", // E1BEE7
            textPrimaryColor = "#2D0C54", // 4A148C
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#4A148C",
            bottomPanelColor = "#FFFFFF", // F3E5F5
            onBottomPanelColor = "#6A1B9A"
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
            onBottomPanelColor = "#D84315"
        ),
        // 1. ПРЕМИАЛЬНАЯ: Сдержанный стиль, замена синему
        CustomThemeProto(
            id = "builtin_graphite",
            name = "Графит и золото",
            primaryColor = "#85754E",      // Приглушенное золото
            backgroundColor = "#F5F5F5",   // Светло-серый бетон
            surfaceColor = "#FFFFFF",      // Чисто белый (бабл)
            textPrimaryColor = "#212121",  // Почти черный текст
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#424242",
            bottomPanelColor = "#FFFFFF",
            onBottomPanelColor = "#85754E"
        ),
        // 2. УЮТНАЯ: Мягкая палитра, отличная от мессенджеров конкурентов
        CustomThemeProto(
            id = "builtin_rose",
            name = "Северная роза",
            primaryColor = "#B08990",      // Пыльная роза
            backgroundColor = "#EAECEF",   // Холодный серо-голубой фон
            surfaceColor = "#F9F6F7",      // Едва розоватый бабл
            textPrimaryColor = "#443C3D",  // Темный графит
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#5E5455",
            bottomPanelColor = "#EAECEF",
            onBottomPanelColor = "#B08990"
        ),

        // 3. ЭНЕРГИЧНАЯ: Чистая, технологичная и свежая
        CustomThemeProto(
            id = "builtin_mint",
            name = "Кибер Мята",
            primaryColor = "#00BFA5",      // Яркая мята
            backgroundColor = "#F1FBF9",   // Мятный тинт на фоне
            surfaceColor = "#FFFFFF",      // Чисто белый бабл
            textPrimaryColor = "#002B26",  // Глубокий темно-зеленый текст
            onPrimaryColor = "#FFFFFF",
            onSurfaceColor = "#004D40",
            bottomPanelColor = "#FFFFFF",
            onBottomPanelColor = "#00BFA5"
        )
    )

    fun loadTheme(context: Context, username: String, onComplete: () -> Unit = {}) {
        val prefs = context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        val themeId = prefs.getString("current_theme_id", "dark") ?: "dark"

        android.util.Log.d("ThemeManager", "READING PREFS: current_theme_id is '$themeId'")

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

            android.os.Handler(android.os.Looper.getMainLooper()).post {
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
        val root = activity.findViewById<View>(android.R.id.content)

        // 1. Настройка Edge-to-Edge (прозрачные панели)
        activity.window.apply {
            @Suppress("DEPRECATION")
            statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            navigationBarColor = Color.TRANSPARENT
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(this, false)
        }

        // 2. ОПРЕДЕЛЯЕМ ЦВЕТ ФОНА (для вычисления яркости)
        val defaultBgColor = "#04052E".toColorInt()
        val bgColor = if (theme != null) {
            try { theme.backgroundColor.toColorInt() } catch (_: Exception) { defaultBgColor }
        } else {
            defaultBgColor
        }

        // 3. АВТОМАТИЧЕСКАЯ НАСТРОЙКА ИКОНОК СТАТУС-БАРА
        // Если фон светлый — иконки станут темными, и наоборот.
        val isLightMode = bgColor.isLight()
        val window = activity.window
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = isLightMode
            isAppearanceLightNavigationBars = isLightMode
        }

        // 4. ПРИМЕНЯЕМ ФОН К ОКНУ
        activity.window.decorView.setBackgroundColor(bgColor)
        root.setBackgroundColor(bgColor)

        android.util.Log.d("ThemeManager", "Applying theme '${theme?.name}' to ${activity.localClassName}")

        // 5. ЛОГИКА ДЛЯ ДЕФОЛТНОЙ ТЕМЫ (Системная или еще не загружена)
        if (theme === null) {
            val prefs = activity.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
            val themeId = prefs.getString("current_theme_id", "dark")

            if (themeId != "light" && themeId != "dark") {
                // Вот это реальная проблема загрузки
                android.util.Log.w("ThemeManager", "⏳ Theme $themeId is CUSTOM but still NULL. Waiting for network/cache...")
            } else {
                // Это штатная работа системы
                android.util.Log.d("ThemeManager", "✅ Using system theme: $themeId")
            }

            val typedValue = android.util.TypedValue()
            activity.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
            val color = if (typedValue.resourceId != 0) ContextCompat.getColor(activity, typedValue.resourceId) else typedValue.data
            findAndTintToolbars(root, color)
            return
        }

        android.util.Log.d("ThemeManager", "SUCCESS: Applying theme '${theme.name}' to ${activity.localClassName}")

        // 6. ПРИМЕНЯЕМ КАСТОМНУЮ ТЕМУ К ВЬЮХАМ
        applyThemeToView(root, theme)

        // Нижняя панель (если есть)
        activity.findViewById<View>(R.id.bottomPanel)?.let {
            applyThemeToBottomPanel(it, theme)
        }

        // Фоновое изображение чата
        val bgImageView = activity.findViewById<android.widget.ImageView>(R.id.chatBackground)
        if (bgImageView != null) {
            if (theme.backgroundImageUrl.isNotEmpty()) {
                bgImageView.visibility = View.VISIBLE
                com.bumptech.glide.Glide.with(activity)
                    .load(theme.backgroundImageUrl)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(bgImageView)
                // Если есть картинка, делаем фон контейнера прозрачным, чтобы видеть её
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
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
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
            android.util.Log.e("ThemeManager", "Error applying theme to bottom panel", e)
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
                is com.google.android.material.appbar.MaterialToolbar -> {
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
                is com.google.android.material.floatingactionbutton.FloatingActionButton -> {
                    view.backgroundTintList = ColorStateList.valueOf(primary)
                    view.imageTintList = ColorStateList.valueOf(onPrimary)
                }

                // Текстовые поля
                is EditText -> {
                    view.setTextColor(textPrimary)
                    view.setHintTextColor(adjustAlpha(onSurface, 0.5f))
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
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
                is com.google.android.material.checkbox.MaterialCheckBox -> {
                    view.buttonTintList = ColorStateList.valueOf(primary)
                    view.setTextColor(textPrimary)
                }
            }

            // 3. РЕКУРСИЯ (Идем вглубь контейнеров)
            if (view is ViewGroup && view !is com.google.android.material.appbar.MaterialToolbar) {
                for (i in 0 until view.childCount) {
                    applyThemeToView(view.getChildAt(i), theme)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ThemeManager", "Error in applyThemeToView", e)
        }
    }

    private fun findAndTintToolbars(view: View, color: Int) {
        if (view is com.google.android.material.appbar.MaterialToolbar) {
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
            is android.widget.ImageView -> {
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
            android.util.Log.e("ThemeManager", "Invalid color string: $colorStr")
            defaultColor
        }
    }

    // Меняет прозрачность (alpha) для существующего цвета. Factor от 0.0 до 1.0
    private fun adjustAlpha(color: Int, factor: Float): Int {
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
            put("backgroundImageUrl", theme.backgroundImageUrl)
            put("bottomPanelColor", theme.bottomPanelColor)
            put("onBottomPanelColor", theme.onBottomPanelColor)
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

        android.util.Log.d("ThemeManager", "Background override saved for $themeId: $imageUrl")
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
