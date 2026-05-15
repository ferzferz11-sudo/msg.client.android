package lavender.client.android.theme

import androidx.core.graphics.toColorInt

object BuiltInThemes {
    private const val LAVENDER_PRIMARY = "#967BB6"
    private const val GOLD_PRIMARY = "#85754E"

    /**
     * Calculate a contrasting text color for a given background color
     */
    fun getContrastTextColor(backgroundColor: String): String {
        return try {
            val color = backgroundColor.toColorInt()
            val luminance = (0.299 * android.graphics.Color.red(color) +
                           0.587 * android.graphics.Color.green(color) +
                           0.114 * android.graphics.Color.blue(color)) / 255
            // Return white for dark backgrounds, black for light backgrounds
            if (luminance < 0.5) "#FFFFFF" else "#000000"
        } catch (e: Exception) {
            "#FFFFFF"
        }
    }

    /**
     * Базовый шаблон для всех светлых тем
     */
    val BASE_LIGHT: Theme = Theme(
        id = "base_light",
        name = "Base Light",
        primaryColor = GOLD_PRIMARY,
        onPrimaryColor = "#FFFFFF",
        surfaceColor = "#FFFFFF",
        onSurfaceColor = "#424242",
        backgroundColor = "#F5F5F5",
        textPrimaryColor = "#212121",
        textSecondaryColor = "#757575",
        surfaceContainer = "#EEEEEE",
        bottomPanelColor = "#FFFFFF",
        onBottomPanelColor = GOLD_PRIMARY,
        outgoingBubbleColor = GOLD_PRIMARY,
        incomingBubbleColor = "#E0E0E0",
        outgoingTextColor = getContrastTextColor(GOLD_PRIMARY),
        incomingTextColor = getContrastTextColor("#E0E0E0"),
        chatListBackgroundImageUrl = "",
        chatBackgroundImageUrl = "",
    )

    /**
     * Базовый шаблон для всех темных тем (Дефолтная тема приложения)
     */
    val BASE_DARK: Theme = Theme(
        id = "dark",
        name = "Lavender Dark",
        primaryColor = LAVENDER_PRIMARY,
        onPrimaryColor = "#FFFFFF",
        surfaceColor = "#1A1B46",
        onSurfaceColor = "#E0E0E0",
        backgroundColor = "#04052E",
        textPrimaryColor = "#FFFFFF",
        textSecondaryColor = "#B0B0CC",
        surfaceContainer = "#121330",
        bottomPanelColor = "#1A1B46",
        onBottomPanelColor = LAVENDER_PRIMARY,
        outgoingBubbleColor = "#2A2C6D",
        incomingBubbleColor = "#16173A",
        outgoingTextColor = getContrastTextColor("#2A2C6D"),
        incomingTextColor = getContrastTextColor("#16173A"),
        chatListBackgroundImageUrl = "",
        chatBackgroundImageUrl = "",
    )

    // Псевдоним для системной темной темы
    val dark = BASE_DARK

    /**
     * Альтернативная темная тема - Графитовый ночной
     * Темная тема с графитовыми оттенками и приглушенным бирюзовым акцентом
     */
    val DARK_GRAPHITE: Theme = Theme(
        id = "builtin_dark_graphite",
        name = "Графитовый ночной",
        primaryColor = "#5F9EA0", // Cadet Blue - приглушенный акцент
        onPrimaryColor = "#1A1A1A",
        surfaceColor = "#2D2D2D",
        onSurfaceColor = "#B8B8B8",
        backgroundColor = "#1E1E1E",
        textPrimaryColor = "#E8E8E8",
        textSecondaryColor = "#909090",
        surfaceContainer = "#252525",
        bottomPanelColor = "#2D2D2D",
        onBottomPanelColor = "#5F9EA0",
        outgoingBubbleColor = "#3D6B6C",
        incomingBubbleColor = "#363636",
        outgoingTextColor = getContrastTextColor("#3D6B6C"),
        incomingTextColor = getContrastTextColor("#363636"),
        chatListBackgroundImageUrl = "",
        chatBackgroundImageUrl = "",
    )

    /**
     * Список всех встроенных тем
     */
    val all: List<Theme> = listOf(
        // Стандартная темная
        BASE_DARK,

        // Альтернативная темная тема
        DARK_GRAPHITE,

        // Светлые темы на базе шаблона
        BASE_LIGHT.copy(
            id = "builtin_graphite",
            name = "Графит и золото",
            outgoingBubbleColor = GOLD_PRIMARY,
            incomingBubbleColor = "#E0E0E0",
            outgoingTextColor = getContrastTextColor(GOLD_PRIMARY),
            incomingTextColor = getContrastTextColor("#E0E0E0"),
        ),
        BASE_LIGHT.copy(
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
            outgoingBubbleColor = "#007AFF",
            incomingBubbleColor = "#D1E9FF",
            outgoingTextColor = getContrastTextColor("#007AFF"),
            incomingTextColor = getContrastTextColor("#D1E9FF"),
        ),
        BASE_LIGHT.copy(
            id = "builtin_green",
            name = "Зеленый лес",
            primaryColor = "#2E7D32",
            backgroundColor = "#F8FAF5",
            surfaceColor = "#EEF7E2",
            surfaceContainer = "#E1EDD1",
            textPrimaryColor = "#144218",
            onSurfaceColor = "#33691E",
            onBottomPanelColor = "#2E7D32",
            outgoingBubbleColor = "#2E7D32",
            incomingBubbleColor = "#E1EDD1",
            outgoingTextColor = getContrastTextColor("#2E7D32"),
            incomingTextColor = getContrastTextColor("#E1EDD1"),
        ),
        BASE_LIGHT.copy(
            id = "builtin_mint",
            name = "Кибер Мята",
            primaryColor = "#00BFA5",
            backgroundColor = "#F1FBF9",
            surfaceColor = "#FFFFFF",
            surfaceContainer = "#D7F2ED",
            textPrimaryColor = "#002B26",
            onSurfaceColor = "#004D40",
            onBottomPanelColor = "#00BFA5",
            outgoingBubbleColor = "#00BFA5",
            incomingBubbleColor = "#D7F2ED",
            outgoingTextColor = getContrastTextColor("#00BFA5"),
            incomingTextColor = getContrastTextColor("#D7F2ED"),
        ),
    )

    fun findById(id: String): Theme? = all.find { it.id == id }
}
