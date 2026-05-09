package lavender.client.android.theme

object BuiltInThemes {
    private const val LAVENDER_PRIMARY = "#967BB6"
    private const val GOLD_PRIMARY = "#85754E"

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
        chatListBackgroundImageUrl = "",
        chatBackgroundImageUrl = "",
    )

    // Псевдоним для системной темной темы
    val dark = BASE_DARK

    /**
     * Список всех встроенных тем
     */
    val all: List<Theme> = listOf(
        // Стандартная темная
        BASE_DARK,

        // Светлые темы на базе шаблона
        BASE_LIGHT.copy(
            id = "builtin_graphite",
            name = "Графит и золото",
            outgoingBubbleColor = GOLD_PRIMARY,
            incomingBubbleColor = "#E0E0E0",
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
        ),
    )

    fun findById(id: String): Theme? = all.find { it.id == id }
}
