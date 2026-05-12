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

        // Handle legacy messages where imageUrl might be empty but text is "Image"
        // Try to extract URL from text if it looks like a URL
        var imageUrl = proto.imageUrl
        var text = proto.text
        
        if (text == "Image" && imageUrl.isEmpty()) {
            // Text is "Image" but imageUrl is empty - this is a legacy message
            // The imageUrl should have been saved but wasn't, so we can't recover it
            // Just keep it as is
        } else if (imageUrl.isEmpty() && text.contains("http")) {
            // Try to extract URL from text if text contains a URL
            val urlPattern = """(https?://[^\s]+)""".toRegex()
            val match = urlPattern.find(text)
            if (match != null) {
                imageUrl = match.value
            }
        }

        // Handle imageUrls for gallery support
        val imageUrls = proto.imageUrls.toList()

        return Message(
            id = proto.id,
            user = proto.user,
            text = text,
            timestamp = timestamp,
            reactions = proto.reactions.map { Reaction(it.user, it.emoji) },
            repliedToMessageId = proto.repliedToMessageId,
            repliedToUser = proto.repliedToUser,
            repliedToText = proto.repliedToText,
            roomId = proto.roomId,
            isRead = proto.isRead,
            avatarUrl = proto.avatarUrl,
            imageUrl = imageUrl,
            imageUrls = imageUrls,
            edited = proto.edited,
            isSuperAdmin = proto.isSuperAdmin,
            voiceUrl = proto.voiceUrl,
            duration = proto.duration
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
}
