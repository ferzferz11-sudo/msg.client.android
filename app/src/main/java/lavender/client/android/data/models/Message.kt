package lavender.client.android.data.models

data class Reaction(
    val user: String,
    val emoji: String
)

data class Message(
    val id: String = "",
    val user: String,
    val text: String,
    val timestamp: Long,
    val reactions: List<Reaction> = emptyList(),
    val repliedToMessageId: String = "",
    val repliedToUser: String = "",
    val repliedToText: String = "",
    val roomId: String = "",
    val isRead: Boolean = false,
    val avatarUrl: String = "",
    val imageUrl: String = "",
    val edited: Boolean = false,
    val isSuperAdmin: Boolean = false,
    val voiceUrl: String = "",
    val duration: Int = 0
)

data class ChatInfo(
    val id: String = "",
    val name: String = "",
    val type: String = "", // 'group' or 'direct'
    val participants: String = "", // JSON array of usernames
    val createdAt: Long = 0,
    val unreadCount: Int = 0,
    val lastMessageTime: Long = 0,
    val creator: String = "",
    val lastMessageText: String = "",
    val avatarUrl: String = "",
    val lastMessageUsername: String = ""
)
