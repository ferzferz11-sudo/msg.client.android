package msg.client.android.data.models

data class Reaction(
    val user: String,
    val emoji: String
)

data class Message(
    val id: String = "",
    val user: String,
    val text: String,
    val timestamp: Long,
    val reactions: List<Reaction> = emptyList()
)
