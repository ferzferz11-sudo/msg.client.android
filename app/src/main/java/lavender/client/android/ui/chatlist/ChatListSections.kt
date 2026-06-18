package lavender.client.android.ui.chatlist

import lavender.client.android.data.models.ChatInfo

/**
 * Section — тип секции в списке чатов.
 */
enum class Section {
    PINNED,      // Закреплённые чаты
    ALL_CHATS,   // Все чаты
    ARCHIVED     // Архивированные (скрыты по умолчанию, доступны через меню)
}

/**
 * SectionItem — секция с чатами.
 */
data class SectionItem(
    val section: Section,
    val chats: List<ChatInfo>
)
