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
    val imageUrls: List<String> = emptyList(), // For gallery support
    val edited: Boolean = false,
    val isSuperAdmin: Boolean = false,
    val voiceUrl: String = "",
    val duration: Int = 0,
    val userId: String = "",
    val isSent: Boolean = true, // Messages from server are always sent
    val isE2EE: Boolean = false, // E2EE-encrypted message
    val e2eePayload: String = "" // Base64-encoded encrypted data
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
    val fullAvatarUrl: String = "",
    val lastMessageUsername: String = "",
    val isMuted: Boolean = false,
    val lastMessageHasImage: Boolean = false,
    val allowMembersToAdd: Boolean = false,
    val conferenceStartTime: Long = 0,
    val isSecret: Boolean = false,      // E2EE secret chat
    val peerPublicKey: String = "",     // Base64 peer public key
    val e2eeReady: Boolean = false      // Both keys exchanged
) {
    fun getDisplayName(currentUsername: String): String {
        if (type != "direct") return name
        return try {
            val arr = org.json.JSONArray(participants)
            var other = ""
            for (i in 0 until arr.length()) {
                val p = arr.getString(i)
                if (p != currentUsername) {
                    other = p
                    break
                }
            }
            if (other.isEmpty()) name else other
        } catch (e: Exception) {
            name
        }
    }
}
