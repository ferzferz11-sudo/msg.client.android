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
    val roomId: String = ""
)

data class ChatInfo(
    val id: String = "",
    val name: String = "",
    val type: String = "", // 'general' or 'direct'
    val participants: String = "", // JSON array of usernames
    val createdAt: Long = 0
)
