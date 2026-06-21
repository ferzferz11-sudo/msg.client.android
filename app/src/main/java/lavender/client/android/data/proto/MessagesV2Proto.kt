package lavender.client.android.data.proto

import com.google.protobuf.Timestamp

data class MessageMediaProto(
    val type: String = "",
    val url: String = "",
    val urls: List<String> = emptyList(),
    val duration: Int = 0
)

data class MessageReplyProto(
    val messageId: String = "",
    val preview: String = ""
)

data class MessageV2Proto(
    val id: String = "",
    val roomId: String = "",
    val senderId: String = "",
    val text: String = "",
    val media: MessageMediaProto? = null,
    val reply: MessageReplyProto? = null,
    val edited: Boolean = false,
    val isRead: Boolean = false,
    val createdAt: Timestamp? = null,
    val reactions: ByteArray = byteArrayOf(),
    val isE2EE: Boolean = false,
    val e2eePayload: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageV2Proto) return false
        return id == other.id && roomId == other.roomId && senderId == other.senderId &&
            text == other.text && media == other.media && reply == other.reply &&
            edited == other.edited && isRead == other.isRead &&
            createdAt?.seconds == other.createdAt?.seconds && createdAt?.nanos == other.createdAt?.nanos &&
            isE2EE == other.isE2EE
    }
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + roomId.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + (createdAt?.seconds?.hashCode() ?: 0)
        return result
    }
}

data class ChatV2TypingProto(
    val isTyping: Boolean = false
)

data class ChatV2SystemProto(
    val type: String = "",
    val message: String = ""
)

data class ChatV2MessageProto(
    val jwtToken: String = "",
    val roomId: String = "",
    val message: MessageV2Proto? = null,
    val typing: ChatV2TypingProto? = null,
    val system: ChatV2SystemProto? = null
)

data class GetHistoryV2RequestProto(
    val roomId: String = "",
    val limit: Int = 50,
    val cursor: String = ""
)

data class GetHistoryV2ResponseProto(
    val messages: List<MessageV2Proto> = emptyList(),
    val nextCursor: String = "",
    val hasMore: Boolean = false
)

data class SendMessageV2RequestProto(
    val roomId: String = "",
    val text: String = "",
    val media: MessageMediaProto? = null,
    val replyToId: String = "",
    val isE2EE: Boolean = false,
    val e2eePayload: String = ""
)

data class SendMessageV2ResponseProto(
    val message: MessageV2Proto? = null,
    val success: Boolean = false,
    val error: String = ""
)

data class EditMessageV2RequestProto(
    val messageId: String = "",
    val text: String = ""
)

data class EditMessageV2ResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class DeleteMessageV2RequestProto(
    val messageIds: List<String> = emptyList(),
    val requesterUserId: String = ""
)

data class DeleteMessageV2ResponseProto(
    val success: Boolean = false
)

data class SetReactionV2RequestProto(
    val messageId: String = "",
    val emoji: String = ""
)

data class SetReactionV2ResponseProto(
    val success: Boolean = false,
    val reactions: ByteArray = byteArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SetReactionV2ResponseProto) return false
        return success == other.success
    }
    override fun hashCode(): Int = success.hashCode()
}
