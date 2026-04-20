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

        return MessageProto.newBuilder()
            .setUser(message.user)
            .setText(message.text)
            .setCreatedAt(timestamp)
            .setId(message.id)
            .setRepliedToMessageId(message.repliedToMessageId)
            .setRepliedToUser(message.repliedToUser)
            .setRepliedToText(message.repliedToText)
            .setRoomId(message.roomId)
            .setIsRead(message.isRead)
            .build()
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
            isRead = proto.isRead
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
}
