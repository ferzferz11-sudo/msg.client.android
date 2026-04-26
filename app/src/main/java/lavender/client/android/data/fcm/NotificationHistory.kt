package lavender.client.android.data.fcm

data class NotificationEntry(
    val title: String,
    val body: String,
    val timestamp: Long,
    val from: String?,
    val isOutgoing: Boolean = false
)

object NotificationHistory {
    private val notifications = mutableListOf<NotificationEntry>()
    private const val MAX_HISTORY = 100

    fun add(title: String, body: String, from: String?, isOutgoing: Boolean = false) {
        notifications.add(0, NotificationEntry(title, body, System.currentTimeMillis(), from, isOutgoing))
        if (notifications.size > MAX_HISTORY) {
            notifications.removeAt(notifications.size - 1)
        }
    }

    fun getAll(): List<NotificationEntry> = notifications.toList()
    
    fun getIncoming(): List<NotificationEntry> = notifications.filter { !it.isOutgoing }
    fun getOutgoing(): List<NotificationEntry> = notifications.filter { it.isOutgoing }

    fun clear() {
        notifications.clear()
    }
}
