package lavender.client.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.models.Reaction

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val user: String,
    val text: String,
    val timestamp: Long,
    val roomId: String,
    val repliedToMessageId: String,
    val repliedToUser: String,
    val repliedToText: String,
    val read: Boolean,
    val avatarUrl: String,
    val imageUrl: String,
    val imageUrlsJson: String = "[]", // Serialized List<String> for gallery support
    val edited: Boolean,
    val superAdmin: Boolean,
    val voiceUrl: String,
    val duration: Int,
    val userId: String = "",
    val isSent: Boolean = true,
    val reactionsJson: String // Serialized List<Reaction>
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val participants: String,
    val createdAt: Long,
    val unreadCount: Int,
    val lastMessageTime: Long,
    val creator: String,
    val lastMessageText: String,
    val avatarUrl: String,
    val fullAvatarUrl: String,
    val lastMessageUsername: String,
    val muted: Boolean,
    val lastMessageHasImage: Boolean = false,
    val isSecret: Boolean = false,
    val peerPublicKey: String = "",
    val e2eeReady: Boolean = false
)

fun Message.toEntity(): MessageEntity {
    val reactionsJson = org.json.JSONArray().apply {
        reactions.forEach { r ->
            put(org.json.JSONObject().apply {
                put("user", r.user)
                put("emoji", r.emoji)
            })
        }
    }.toString()

    val imageUrlsJson = org.json.JSONArray(imageUrls).toString()

    return MessageEntity(
        id = id,
        user = user,
        text = text,
        timestamp = timestamp,
        roomId = roomId,
        repliedToMessageId = repliedToMessageId,
        repliedToUser = repliedToUser,
        repliedToText = repliedToText,
        read = isRead,
        avatarUrl = avatarUrl,
        imageUrl = imageUrl,
        imageUrlsJson = imageUrlsJson,
        edited = edited,
        superAdmin = isSuperAdmin,
        voiceUrl = voiceUrl,
        duration = duration,
        userId = userId,
        isSent = isSent,
        reactionsJson = reactionsJson
    )
}

fun MessageEntity.toDomain(): Message {
    val reactions = mutableListOf<Reaction>()
    try {
        val arr = org.json.JSONArray(reactionsJson)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            reactions.add(Reaction(obj.getString("user"), obj.getString("emoji")))
        }
    } catch (_: Exception) {}

    val imageUrls = mutableListOf<String>()
    try {
        val arr = org.json.JSONArray(imageUrlsJson)
        for (i in 0 until arr.length()) {
            imageUrls.add(arr.getString(i))
        }
    } catch (_: Exception) {}

    return Message(
        id = id,
        user = user,
        text = text,
        timestamp = timestamp,
        reactions = reactions,
        repliedToMessageId = repliedToMessageId,
        repliedToUser = repliedToUser,
        repliedToText = repliedToText,
        roomId = roomId,
        isRead = read,
        avatarUrl = avatarUrl,
        imageUrl = imageUrl,
        imageUrls = imageUrls,
        edited = edited,
        isSuperAdmin = superAdmin,
        voiceUrl = voiceUrl,
        duration = duration,
        userId = userId,
        isSent = isSent
    )
}

fun ChatInfo.toEntity(): ChatEntity = ChatEntity(
    id, name, type, participants, createdAt, unreadCount, lastMessageTime,
    creator, lastMessageText, avatarUrl, fullAvatarUrl, lastMessageUsername, isMuted, lastMessageHasImage,
    isSecret, peerPublicKey, e2eeReady
)

fun ChatEntity.toDomain(): ChatInfo = ChatInfo(
    id, name, type, participants, createdAt, unreadCount, lastMessageTime,
    creator, lastMessageText, avatarUrl, fullAvatarUrl, lastMessageUsername, muted, lastMessageHasImage,
    isSecret, peerPublicKey, e2eeReady
)
