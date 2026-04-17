package msg.client.android.data.models

data class Message(
    val user: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
