package lavender.client.android.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import lavender.client.android.data.proto.CustomThemeProto
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
        primaryColor = "#967BB6", // LAVENDER_MIST для текста входящих (был #1A1B46)
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

    fun getCurrentTheme(): CustomThemeProto? = currentCustomTheme

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

    fun findBuiltInThemeById(id: String): CustomThemeProto? {
        return if (id == "dark") baseDarkTheme else builtInThemes.find { it.id == id }
    }
}