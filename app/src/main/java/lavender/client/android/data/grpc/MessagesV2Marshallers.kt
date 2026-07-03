package lavender.client.android.data.grpc

import com.google.protobuf.WireFormat
import lavender.client.android.data.proto.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

// ======= Messages V2 Marshallers =======
// Marshallers for ChatV2 bidirectional stream + unary RPCs.

class MessageMediaProtoMarshaller {
    companion object {
        fun serialize(value: MessageMediaProto, cos: com.google.protobuf.CodedOutputStream, fieldNumber: Int) {
            val baos = ByteArrayOutputStream()
            val inner = com.google.protobuf.CodedOutputStream.newInstance(baos)
            if (value.type.isNotEmpty()) inner.writeString(1, value.type)
            if (value.url.isNotEmpty()) inner.writeString(2, value.url)
            for (u in value.urls) inner.writeString(3, u)
            if (value.duration != 0) inner.writeInt32(4, value.duration)
            inner.flush()
            val bytes = baos.toByteArray()
            cos.writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(bytes.size)
            cos.writeRawBytes(bytes)
        }

        fun parse(cis: com.google.protobuf.CodedInputStream): MessageMediaProto {
            var type = ""; var url = ""; val urls = mutableListOf<String>(); var duration = 0
            val inner = com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(cis.readUInt32()))
            while (!inner.isAtEnd) {
                val tag = inner.readTag(); if (tag == 0) break
                when (WireFormat.getTagFieldNumber(tag)) {
                    1 -> type = inner.readString(); 2 -> url = inner.readString()
                    3 -> urls.add(inner.readString()); 4 -> duration = inner.readInt32()
                    else -> inner.skipField(tag)
                }
            }
            return MessageMediaProto(type, url, urls, duration)
        }
    }
}

class MessageReplyProtoMarshaller {
    companion object {
        fun serialize(value: MessageReplyProto, cos: com.google.protobuf.CodedOutputStream, fieldNumber: Int) {
            val baos = ByteArrayOutputStream()
            val inner = com.google.protobuf.CodedOutputStream.newInstance(baos)
            if (value.messageId.isNotEmpty()) inner.writeString(1, value.messageId)
            if (value.preview.isNotEmpty()) inner.writeString(2, value.preview)
            inner.flush()
            val bytes = baos.toByteArray()
            cos.writeTag(fieldNumber, WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(bytes.size)
            cos.writeRawBytes(bytes)
        }

        fun parse(cis: com.google.protobuf.CodedInputStream): MessageReplyProto {
            var messageId = ""; var preview = ""
            val inner = com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(cis.readUInt32()))
            while (!inner.isAtEnd) {
                val tag = inner.readTag(); if (tag == 0) break
                when (WireFormat.getTagFieldNumber(tag)) {
                    1 -> messageId = inner.readString(); 2 -> preview = inner.readString()
                    else -> inner.skipField(tag)
                }
            }
            return MessageReplyProto(messageId, preview)
        }
    }
}

class MessageV2ProtoMarshaller : io.grpc.MethodDescriptor.Marshaller<MessageV2Proto> {
    override fun stream(value: MessageV2Proto): java.io.InputStream {
        val baos = ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.id.isNotEmpty()) cos.writeString(1, value.id)
        if (value.roomId.isNotEmpty()) cos.writeString(2, value.roomId)
        if (value.senderId.isNotEmpty()) cos.writeString(3, value.senderId)
        when {
            value.text.isNotEmpty() -> cos.writeString(10, value.text)
            value.media != null -> MessageMediaProtoMarshaller.serialize(value.media, cos, 11)
            value.reply != null -> MessageReplyProtoMarshaller.serialize(value.reply, cos, 12)
        }
        if (value.edited) cos.writeBool(20, value.edited)
        if (value.isRead) cos.writeBool(21, value.isRead)
        value.createdAt?.let {
            cos.writeTag(22, WireFormat.WIRETYPE_LENGTH_DELIMITED)
            val b = it.toByteArray()
            cos.writeUInt32NoTag(b.size)
            cos.writeRawBytes(b)
        }
        if (value.reactions.isNotEmpty()) {
            cos.writeTag(23, WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(value.reactions.size)
            cos.writeRawBytes(value.reactions)
        }
        if (value.isE2EE) cos.writeBool(30, value.isE2EE)
        if (value.e2eePayload.isNotEmpty()) cos.writeString(31, value.e2eePayload)
        if (value.mentions.isNotEmpty()) {
            cos.writeTag(40, WireFormat.WIRETYPE_LENGTH_DELIMITED)
            val baosInner = ByteArrayOutputStream()
            val cosInner = com.google.protobuf.CodedOutputStream.newInstance(baosInner)
            for (m in value.mentions) cosInner.writeString(1, m)
            cosInner.flush()
            val bytes = baosInner.toByteArray()
            cos.writeUInt32NoTag(bytes.size)
            cos.writeRawBytes(bytes)
        }
        cos.flush()
        return ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): MessageV2Proto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var id = ""; var roomId = ""; var senderId = ""
        var text = ""; var media: MessageMediaProto? = null; var reply: MessageReplyProto? = null
        var edited = false; var isRead = false; var createdAt: com.google.protobuf.Timestamp? = null
        var reactions = byteArrayOf(); var isE2EE = false; var e2eePayload = ""
        val mentions = mutableListOf<String>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (WireFormat.getTagFieldNumber(tag)) {
                1 -> id = cis.readString()
                2 -> roomId = cis.readString()
                3 -> senderId = cis.readString()
                10 -> text = cis.readString()
                11 -> media = MessageMediaProtoMarshaller.parse(cis)
                12 -> reply = MessageReplyProtoMarshaller.parse(cis)
                20 -> edited = cis.readBool()
                21 -> isRead = cis.readBool()
                22 -> { val len = cis.readUInt32(); createdAt = com.google.protobuf.Timestamp.parseFrom(cis.readRawBytes(len)) }
                23 -> { val len = cis.readUInt32(); reactions = cis.readRawBytes(len) }
                30 -> isE2EE = cis.readBool()
                31 -> e2eePayload = cis.readString()
                40 -> {
                    val len = cis.readUInt32()
                    val inner = com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))
                    while (!inner.isAtEnd) {
                        val innerTag = inner.readTag(); if (innerTag == 0) break
                        when (WireFormat.getTagFieldNumber(innerTag)) {
                            1 -> mentions.add(inner.readString())
                            else -> inner.skipField(innerTag)
                        }
                    }
                }
                else -> cis.skipField(tag)
            }
        }
        return MessageV2Proto(id, roomId, senderId, text, media, reply, edited, isRead, createdAt, reactions, isE2EE, e2eePayload, mentions)
    }
}

class ChatV2MessageMarshaller : io.grpc.MethodDescriptor.Marshaller<ChatV2MessageProto> {
    override fun stream(value: ChatV2MessageProto): java.io.InputStream {
        val baos = ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (value.jwtToken.isNotEmpty()) cos.writeString(1, value.jwtToken)
        if (value.roomId.isNotEmpty()) cos.writeString(2, value.roomId)
        if (value.clientVersion.isNotEmpty()) cos.writeString(3, value.clientVersion)
        if (value.message != null) {
            val msgBytes = MessageV2ProtoMarshaller().stream(value.message).readBytes()
            cos.writeTag(10, WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(msgBytes.size)
            cos.writeRawBytes(msgBytes)
        }
        if (value.typing != null) {
            val baos2 = ByteArrayOutputStream()
            val cos2 = com.google.protobuf.CodedOutputStream.newInstance(baos2)
            cos2.writeBool(1, value.typing.isTyping)
            cos2.flush()
            val bytes = baos2.toByteArray()
            cos.writeTag(11, WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(bytes.size)
            cos.writeRawBytes(bytes)
        }
        if (value.system != null) {
            val baos2 = ByteArrayOutputStream()
            val cos2 = com.google.protobuf.CodedOutputStream.newInstance(baos2)
            if (value.system.type.isNotEmpty()) cos2.writeString(1, value.system.type)
            if (value.system.message.isNotEmpty()) cos2.writeString(2, value.system.message)
            cos2.flush()
            val bytes = baos2.toByteArray()
            cos.writeTag(12, WireFormat.WIRETYPE_LENGTH_DELIMITED)
            cos.writeUInt32NoTag(bytes.size)
            cos.writeRawBytes(bytes)
        }
        cos.flush()
        return ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(stream: java.io.InputStream): ChatV2MessageProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(stream)
        var jwtToken = ""; var roomId = ""; var clientVersion = ""
        var message: MessageV2Proto? = null; var typing: ChatV2TypingProto? = null; var system: ChatV2SystemProto? = null
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (WireFormat.getTagFieldNumber(tag)) {
                1 -> jwtToken = cis.readString()
                2 -> roomId = cis.readString()
                3 -> clientVersion = cis.readString()
                10 -> { val len = cis.readUInt32(); message = MessageV2ProtoMarshaller().parse(ByteArrayInputStream(cis.readRawBytes(len))) }
                11 -> {
                    val len = cis.readUInt32()
                    val inner = com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))
                    var isTyping = false
                    while (!inner.isAtEnd) { val t = inner.readTag(); if (t == 0) break; when (WireFormat.getTagFieldNumber(t)) { 1 -> isTyping = inner.readBool(); else -> inner.skipField(t) } }
                    typing = ChatV2TypingProto(isTyping)
                }
                12 -> {
                    val len = cis.readUInt32()
                    val inner = com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))
                    var type = ""; var message2 = ""
                    while (!inner.isAtEnd) { val t = inner.readTag(); if (t == 0) break; when (WireFormat.getTagFieldNumber(t)) { 1 -> type = inner.readString(); 2 -> message2 = inner.readString(); else -> inner.skipField(t) } }
                    system = ChatV2SystemProto(type, message2)
                }
                else -> cis.skipField(tag)
            }
        }
        return ChatV2MessageProto(jwtToken, roomId, clientVersion, message, typing, system)
    }
}

class GetHistoryV2RequestMarshaller : io.grpc.MethodDescriptor.Marshaller<GetHistoryV2RequestProto> {
    override fun stream(v: GetHistoryV2RequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.roomId.isNotEmpty()) cos.writeString(1, v.roomId)
        if (v.limit != 0) cos.writeInt32(2, v.limit)
        if (v.cursor.isNotEmpty()) cos.writeString(3, v.cursor)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): GetHistoryV2RequestProto = GetHistoryV2RequestProto()
}

class GetHistoryV2ResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<GetHistoryV2ResponseProto> {
    override fun stream(v: GetHistoryV2ResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): GetHistoryV2ResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val msgs = mutableListOf<MessageV2Proto>()
        var nextCursor = ""; var hasMore = false
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); msgs.add(MessageV2ProtoMarshaller().parse(ByteArrayInputStream(cis.readRawBytes(len)))) }
                2 -> nextCursor = cis.readString()
                3 -> hasMore = cis.readBool()
                else -> cis.skipField(tag)
            }
        }
        return GetHistoryV2ResponseProto(msgs, nextCursor, hasMore)
    }
}

class SendMessageV2RequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SendMessageV2RequestProto> {
    override fun stream(v: SendMessageV2RequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.roomId.isNotEmpty()) cos.writeString(1, v.roomId)
        when {
            v.text.isNotEmpty() -> cos.writeString(2, v.text)
            v.media != null -> MessageMediaProtoMarshaller.serialize(v.media, cos, 3)
        }
        if (v.replyToId.isNotEmpty()) cos.writeString(4, v.replyToId)
        if (v.isE2EE) cos.writeBool(5, v.isE2EE)
        if (v.e2eePayload.isNotEmpty()) cos.writeString(6, v.e2eePayload)
        if (v.mentions.isNotEmpty()) {
            cos.writeTag(7, WireFormat.WIRETYPE_LENGTH_DELIMITED)
            val baosInner = ByteArrayOutputStream()
            val cosInner = com.google.protobuf.CodedOutputStream.newInstance(baosInner)
            for (m in v.mentions) cosInner.writeString(1, m)
            cosInner.flush()
            val bytes = baosInner.toByteArray()
            cos.writeUInt32NoTag(bytes.size)
            cos.writeRawBytes(bytes)
        }
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SendMessageV2RequestProto = SendMessageV2RequestProto()
}

class SendMessageV2ResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SendMessageV2ResponseProto> {
    override fun stream(v: SendMessageV2ResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SendMessageV2ResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var message: MessageV2Proto? = null; var success = false; var error = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (WireFormat.getTagFieldNumber(tag)) {
                1 -> { val len = cis.readUInt32(); message = MessageV2ProtoMarshaller().parse(ByteArrayInputStream(cis.readRawBytes(len))) }
                2 -> success = cis.readBool()
                3 -> error = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return SendMessageV2ResponseProto(message, success, error)
    }
}

class EditMessageV2RequestMarshaller : io.grpc.MethodDescriptor.Marshaller<EditMessageV2RequestProto> {
    override fun stream(v: EditMessageV2RequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.messageId.isNotEmpty()) cos.writeString(1, v.messageId)
        if (v.text.isNotEmpty()) cos.writeString(2, v.text)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): EditMessageV2RequestProto = EditMessageV2RequestProto()
}

class EditMessageV2ResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<EditMessageV2ResponseProto> {
    override fun stream(v: EditMessageV2ResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): EditMessageV2ResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var msg = ""
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; when (WireFormat.getTagFieldNumber(tag)) { 1 -> ok = cis.readBool(); 2 -> msg = cis.readString(); else -> cis.skipField(tag) } }
        return EditMessageV2ResponseProto(ok, msg)
    }
}

class DeleteMessageV2RequestMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteMessageV2RequestProto> {
    override fun stream(v: DeleteMessageV2RequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        for (id in v.messageIds) cos.writeString(1, id)
        if (v.requesterUserId.isNotEmpty()) cos.writeString(2, v.requesterUserId)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): DeleteMessageV2RequestProto = DeleteMessageV2RequestProto()
}

class DeleteMessageV2ResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<DeleteMessageV2ResponseProto> {
    override fun stream(v: DeleteMessageV2ResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): DeleteMessageV2ResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false
        while (!cis.isAtEnd) { val tag = cis.readTag(); if (tag == 0) break; if (WireFormat.getTagFieldNumber(tag) == 1) ok = cis.readBool() else cis.skipField(tag) }
        return DeleteMessageV2ResponseProto(ok)
    }
}

class SetReactionV2RequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SetReactionV2RequestProto> {
    override fun stream(v: SetReactionV2RequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.messageId.isNotEmpty()) cos.writeString(1, v.messageId)
        if (v.emoji.isNotEmpty()) cos.writeString(2, v.emoji)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SetReactionV2RequestProto = SetReactionV2RequestProto()
}

class SetReactionV2ResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SetReactionV2ResponseProto> {
    override fun stream(v: SetReactionV2ResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SetReactionV2ResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s); var ok = false; var reactions = byteArrayOf()
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (WireFormat.getTagFieldNumber(tag)) {
                1 -> ok = cis.readBool()
                2 -> { val len = cis.readUInt32(); reactions = cis.readRawBytes(len) }
                else -> cis.skipField(tag)
            }
        }
        return SetReactionV2ResponseProto(ok, reactions)
    }
}

class SearchMessagesRequestMarshaller : io.grpc.MethodDescriptor.Marshaller<SearchMessagesRequestProto> {
    override fun stream(v: SearchMessagesRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream(); val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.roomId.isNotEmpty()) cos.writeString(1, v.roomId)
        if (v.query.isNotEmpty()) cos.writeString(2, v.query)
        if (v.limit != 0) cos.writeInt32(3, v.limit)
        cos.flush(); return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): SearchMessagesRequestProto = SearchMessagesRequestProto()
}

class SearchMessagesResponseMarshaller : io.grpc.MethodDescriptor.Marshaller<SearchMessagesResponseProto> {
    override fun stream(v: SearchMessagesResponseProto): java.io.InputStream = ByteArrayInputStream(byteArrayOf())
    override fun parse(s: java.io.InputStream): SearchMessagesResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        val results = mutableListOf<SearchResultProto>()
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (WireFormat.getTagFieldNumber(tag)) {
                1 -> {
                    val len = cis.readUInt32()
                    val inner = com.google.protobuf.CodedInputStream.newInstance(cis.readRawBytes(len))
                    var messageId = ""; var roomId = ""; var username = ""; var preview = ""; var createdAt = ""
                    while (!inner.isAtEnd) { val t = inner.readTag(); if (t == 0) break; when (WireFormat.getTagFieldNumber(t)) {
                        1 -> messageId = inner.readString(); 2 -> roomId = inner.readString()
                        3 -> username = inner.readString(); 4 -> preview = inner.readString()
                        5 -> createdAt = inner.readString(); else -> inner.skipField(t)
                    } }
                    results.add(SearchResultProto(messageId, roomId, username, preview, createdAt))
                }
                else -> cis.skipField(tag)
            }
        }
        return SearchMessagesResponseProto(results)
    }
}
