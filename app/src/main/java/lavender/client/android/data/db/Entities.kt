package lavender.client.android.data.db
import android.util.Log

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import lavender.client.android.data.ai.AiProviderType
import lavender.client.android.data.ai.MarketplaceAgent
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.models.Reaction

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val user: String,
    val text: String,
    val timestamp: Long,
    @ColumnInfo(index = true) val roomId: String,
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
    @ColumnInfo(index = true) val isSent: Boolean = true,
    val reactionsJson: String // Serialized List<Reaction>
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(index = true) val type: String,
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
    val allowMembersToAdd: Boolean = false,
    val isSecret: Boolean = false,
    val peerPublicKey: String = "",
    val e2eeReady: Boolean = false,
    val activeAgentId: String = "",
    val agentMode: String = "",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val pinnedAt: Long = 0L,
    val companyId: String = "",
    val companyChatAccess: String = "",
    val companyMinPositionLevel: Int = 0
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
    } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }

    val imageUrls = mutableListOf<String>()
    try {
        val arr = org.json.JSONArray(imageUrlsJson)
        for (i in 0 until arr.length()) {
            imageUrls.add(arr.getString(i))
        }
    } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }

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
    allowMembersToAdd, isSecret, peerPublicKey, e2eeReady, activeAgentId, agentMode,
    isPinned, isArchived, pinnedAt, companyId, companyChatAccess, companyMinPositionLevel
)

fun ChatEntity.toDomain(): ChatInfo = ChatInfo(
    id, name, type, participants, createdAt, unreadCount, lastMessageTime,
    creator, lastMessageText, avatarUrl, fullAvatarUrl, lastMessageUsername, muted, lastMessageHasImage,
    allowMembersToAdd, conferenceStartTime = 0L,
    isSecret, peerPublicKey, e2eeReady, activeAgentId, agentMode,
    isPinned, isArchived, pinnedAt, companyId, companyChatAccess, companyMinPositionLevel
)

@Entity(tableName = "marketplace_agents")
data class MarketplaceAgentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val providerType: String,
    val model: String,
    val toolsEnabled: Boolean,
    val ragEnabled: Boolean,
    val isPreset: Boolean,
    val isPublic: Boolean,
    val avgRating: Float,
    val installCount: Int,
    val cachedAt: Long = System.currentTimeMillis()
)

fun MarketplaceAgent.toEntity(): MarketplaceAgentEntity = MarketplaceAgentEntity(
    id = id,
    name = name,
    description = description,
    providerType = providerType.value,
    model = model,
    toolsEnabled = toolsEnabled,
    ragEnabled = ragEnabled,
    isPreset = isPreset,
    isPublic = isPublic,
    avgRating = avgRating,
    installCount = installCount
)

fun MarketplaceAgentEntity.toDomain(): MarketplaceAgent = MarketplaceAgent(
    id = id,
    name = name,
    description = description,
    providerType = AiProviderType.fromString(providerType),
    model = model,
    toolsEnabled = toolsEnabled,
    ragEnabled = ragEnabled,
    isPreset = isPreset,
    isPublic = isPublic,
    avgRating = avgRating,
    installCount = installCount
)
