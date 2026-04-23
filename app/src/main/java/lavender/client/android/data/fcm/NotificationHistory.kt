package lavender.client.android.data.fcm

data class NotificationEntry(
    val title: String,
    val body: String,
    val timestamp: Long,
    val from: String?
)

object NotificationHistory {
    private val notifications = mutableListOf<NotificationEntry>()
    private const val MAX_HISTORY = 20

    fun add(title: String, body: String, from: String? = null) {
        notifications.add(0, NotificationEntry(title, body, System.currentTimeMillis(), from))
        if (notifications.size > MAX_HISTORY) {
            notifications.removeAt(notifications.size - 1)
        }
    }

    fun getAll(): List<NotificationEntry> = notifications.toList()

    fun clear() {
        notifications.clear()
    }
}
