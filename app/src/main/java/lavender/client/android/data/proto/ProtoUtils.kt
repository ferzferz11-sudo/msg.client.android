package lavender.client.android.data.proto

import com.google.protobuf.Timestamp
import lavender.client.android.R
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

        // E2EE fields
        if (message.isE2EE) {
            builder.setIsE2Ee(true)
            builder.setE2EePayload(message.e2eePayload)
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
            userId = proto.userId,
            isE2EE = proto.isE2Ee,
            e2eePayload = proto.e2EePayload
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

    // ======= Messages V2 =======
    fun createMessageV2Proto(message: Message): MessageV2Proto {
        val timestamp = Timestamp.newBuilder()
            .setSeconds(message.timestamp / 1000)
            .setNanos(((message.timestamp % 1000) * 1000000).toInt())
            .build()

        val media = when {
            message.stickerUrl.isNotEmpty() -> MessageMediaProto(type = "sticker", url = message.stickerUrl, urls = listOf(message.stickerThumbnailUrl).filter { it.isNotEmpty() })
            message.voiceUrl.isNotEmpty() -> MessageMediaProto(type = "voice", url = message.voiceUrl, duration = message.duration)
            message.imageUrl.isNotEmpty() -> MessageMediaProto(
                type = "image",
                url = message.imageUrl,
                urls = message.imageUrls.ifEmpty { listOf(message.imageUrl).filter { it.isNotEmpty() } }
            )
            else -> null
        }

        val reply = if (message.repliedToMessageId.isNotEmpty()) {
            MessageReplyProto(messageId = message.repliedToMessageId, preview = message.repliedToText)
        } else null

        val reactionsBytes = org.json.JSONObject().let { obj ->
            message.reactions.forEach { obj.put(it.user, it.emoji) }
            obj.toString().toByteArray()
        }

        return MessageV2Proto(
            id = message.id,
            roomId = message.roomId,
            senderId = message.userId,
            text = message.text,
            media = media,
            reply = reply,
            edited = message.edited,
            isRead = message.isRead,
            createdAt = timestamp,
            reactions = reactionsBytes,
            isE2EE = message.isE2EE,
            e2eePayload = message.e2eePayload
        )
    }

    fun createMessageFromV2Proto(proto: MessageV2Proto, resolveUsername: (String) -> String): Message {
        val timestamp = proto.createdAt?.let {
            it.seconds * 1000 + (it.nanos / 1000000)
        } ?: System.currentTimeMillis()

        val username = resolveUsername(proto.senderId)

        var imageUrl = ""; var imageUrls = emptyList<String>()
        var voiceUrl = ""; var duration = 0
        var repliedToMessageId = ""; var repliedToText = ""
        var stickerUrl = ""; var stickerThumbnailUrl = ""

        when {
            proto.media != null -> {
                when (proto.media.type) {
                    "image" -> {
                        imageUrl = proto.media.url
                        imageUrls = proto.media.urls.ifEmpty { listOf(proto.media.url).filter { it.isNotEmpty() } }
                    }
                    "voice" -> {
                        voiceUrl = proto.media.url
                        duration = proto.media.duration
                    }
                    "sticker" -> {
                        stickerUrl = proto.media.url
                        stickerThumbnailUrl = proto.media.urls.firstOrNull() ?: ""
                    }
                }
            }
            proto.reply != null -> {
                repliedToMessageId = proto.reply.messageId
                repliedToText = proto.reply.preview
            }
        }

        val reactions = if (proto.reactions.isNotEmpty()) {
            try {
                val obj = org.json.JSONObject(String(proto.reactions))
                val result = mutableListOf<Reaction>()
                for (key in obj.keys()) {
                    val emoji = obj.getString(key)
                    if (emoji.isNotEmpty()) result.add(Reaction(user = key, emoji = emoji))
                }
                result
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        return Message(
            id = proto.id,
            user = username,
            text = proto.text,
            timestamp = timestamp,
            reactions = reactions,
            repliedToMessageId = repliedToMessageId,
            repliedToText = repliedToText,
            roomId = proto.roomId,
            isRead = proto.isRead,
            imageUrl = imageUrl,
            imageUrls = imageUrls,
            edited = proto.edited,
            voiceUrl = voiceUrl,
            duration = duration,
            userId = proto.senderId,
            isE2EE = proto.isE2EE,
            e2eePayload = proto.e2eePayload,
            stickerUrl = stickerUrl,
            stickerThumbnailUrl = stickerThumbnailUrl
        )
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
            diffMinutes < 1 -> context.getString(R.string.was_online_just_now)
            diffMinutes < 60 -> context.getString(R.string.was_online_minutes, diffMinutes.toInt())
            diffHours < 24 -> context.getString(R.string.was_online_hours, diffHours.toInt())
            diffDays < 7 -> context.getString(R.string.was_online_days, diffDays.toInt())
            else -> {
                val date = java.util.Date(lastSeenMillis)
                val format = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.forLanguageTag("ru"))
                context.getString(R.string.was_online_date, format.format(date))
            }
        }
    }
}
