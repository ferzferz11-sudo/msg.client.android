package lavender.client.android.data.proto

import com.google.protobuf.Timestamp
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.Reaction

object ProtoUtils {
    fun createMessageProto(message: Message): MessageProto {
        val timestamp = Timestamp.newBuilder()
            .setSeconds(message.timestamp / 1000)
            .setNanos(((message.timestamp % 1000) * 1000000).toInt())
            .build()

        val builder = MessageProto.newBuilder()
            .setUser(message.user)
            .setText(message.text)
            .setCreatedAt(timestamp)
            .setId(message.id)
            .setRepliedToMessageId(message.repliedToMessageId)
            .setRepliedToUser(message.repliedToUser)
            .setRepliedToText(message.repliedToText)
            .setRoomId(message.roomId)
            .setIsRead(message.isRead)
            .setAvatarUrl(message.avatarUrl)
            .setImageUrl(message.imageUrl)
            .setEdited(message.edited)
            .setClientVersion(lavender.client.android.BuildConfig.VERSION_NAME)
            .setIsSuperAdmin(message.isSuperAdmin)
            .setVoiceUrl(message.voiceUrl)
            .setDuration(message.duration)
            .setUserId(message.userId)

        // Add reactions
        message.reactions.forEach { reaction ->
            builder.addReaction(ReactionProto(user = reaction.user, emoji = reaction.emoji))
        }

        // Add image URLs for gallery support
        message.imageUrls.forEach { imageUrl ->
            builder.addImageUrls(imageUrl)
        }

        return builder.build()
    }

    fun createMessageFromProto(proto: MessageProto): Message {
        val timestamp = proto.createdAt?.let {
            it.seconds * 1000 + (it.nanos / 1000000)
        } ?: System.currentTimeMillis()

        return Message(
            id = proto.id,
            user = proto.user,
            text = proto.text,
            timestamp = timestamp,
            reactions = proto.reactions.map { Reaction(it.user, it.emoji) },
            repliedToMessageId = proto.repliedToMessageId,
            repliedToUser = proto.repliedToUser,
            repliedToText = proto.repliedToText,
            roomId = proto.roomId,
            isRead = proto.isRead,
            avatarUrl = proto.avatarUrl,
            imageUrl = proto.imageUrl,
            imageUrls = proto.imageUrls.toList(),
            edited = proto.edited,
            isSuperAdmin = proto.isSuperAdmin,
            voiceUrl = proto.voiceUrl,
            duration = proto.duration,
            userId = proto.userId
        )
    }
    
    fun getCurrentTimestamp(): Timestamp {
        val currentTime = System.currentTimeMillis()
        val seconds = currentTime / 1000
        val nanos = ((currentTime % 1000) * 1000000).toInt()

        return Timestamp.newBuilder()
            .setSeconds(seconds)
            .setNanos(nanos)
            .build()
    }

    fun timestampToProto(timestamp: Timestamp): com.google.protobuf.Timestamp {
        return com.google.protobuf.Timestamp.newBuilder()
            .setSeconds(timestamp.seconds)
            .setNanos(timestamp.nanos)
            .build()
    }

    fun parseTimestampFromProto(stream: java.io.InputStream): Timestamp {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var seconds = 0L
        var nanos = 0
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> seconds = cis.readInt64()
                2 -> nanos = cis.readInt32()
                else -> cis.skipField(tag)
            }
        }
        return Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build()
    }

    fun formatLastSeen(timestamp: Timestamp?, context: android.content.Context): String {
        if (timestamp == null) return ""
        
        val lastSeenMillis = timestamp.seconds * 1000 + timestamp.nanos / 1000000
        val now = System.currentTimeMillis()
        val diffMillis = now - lastSeenMillis
        val diffMinutes = diffMillis / (1000 * 60)
        val diffHours = diffMillis / (1000 * 60 * 60)
        val diffDays = diffMillis / (1000 * 60 * 60 * 24)
        
        return when {
            diffMinutes < 1 -> "был(а) в сети только что"
            diffMinutes < 60 -> "был(а) в сети $diffMinutes мин назад"
            diffHours < 24 -> "был(а) в сети $diffHours ч назад"
            diffDays < 7 -> "был(а) в сети $diffDays дн назад"
            else -> {
                val date = java.util.Date(lastSeenMillis)
                val format = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.forLanguageTag("ru"))
                "был(а) в сети ${format.format(date)}"
            }
        }
    }
}
