package lavender.client.android.data.grpc

import com.google.protobuf.Timestamp
import lavender.client.android.data.proto.ChatV2MessageProto
import lavender.client.android.data.proto.ChatV2SystemProto
import lavender.client.android.data.proto.ChatV2TypingProto
import lavender.client.android.data.proto.DeleteMessageV2RequestProto
import lavender.client.android.data.proto.DeleteMessageV2ResponseProto
import lavender.client.android.data.proto.EditMessageV2RequestProto
import lavender.client.android.data.proto.EditMessageV2ResponseProto
import lavender.client.android.data.proto.GetHistoryV2RequestProto
import lavender.client.android.data.proto.GetHistoryV2ResponseProto
import lavender.client.android.data.proto.MessageMediaProto
import lavender.client.android.data.proto.MessageReplyProto
import lavender.client.android.data.proto.MessageV2Proto
import lavender.client.android.data.proto.SendMessageV2RequestProto
import lavender.client.android.data.proto.SendMessageV2ResponseProto
import lavender.client.android.data.proto.ClearRoomHistoryRequestProto
import lavender.client.android.data.proto.ClearRoomHistoryResponseProto
import lavender.client.android.data.proto.SetReactionV2RequestProto
import lavender.client.android.data.proto.SetReactionV2ResponseProto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesV2MarshallersTest {

    // ======= MessageV2Proto =======

    @Test
    fun messageV2Proto_defaults() {
        val msg = MessageV2Proto()
        assertEquals("", msg.id)
        assertEquals("", msg.roomId)
        assertEquals("", msg.senderId)
        assertEquals("", msg.text)
        assertNull(msg.media)
        assertNull(msg.reply)
        assertFalse(msg.edited)
        assertFalse(msg.isRead)
        assertNull(msg.createdAt)
        assertTrue(msg.reactions.isEmpty())
        assertFalse(msg.isE2EE)
        assertEquals("", msg.e2eePayload)
    }

    @Test
    fun messageV2Proto_textMessage() {
        val ts = Timestamp.newBuilder().setSeconds(1700000000).setNanos(500000000).build()
        val msg = MessageV2Proto(
            id = "msg-001",
            roomId = "room-1",
            senderId = "uuid-123",
            text = "Hello world",
            edited = false,
            isRead = true,
            createdAt = ts,
            isE2EE = false
        )
        assertEquals("msg-001", msg.id)
        assertEquals("room-1", msg.roomId)
        assertEquals("uuid-123", msg.senderId)
        assertEquals("Hello world", msg.text)
        assertNull(msg.media)
        assertNull(msg.reply)
        assertFalse(msg.edited)
        assertTrue(msg.isRead)
        assertEquals(1700000000L, msg.createdAt?.seconds)
        assertEquals(500000000, msg.createdAt?.nanos)
    }

    @Test
    fun messageV2Proto_mediaImage() {
        val media = MessageMediaProto(type = "image", url = "https://example.com/img.png", urls = listOf("https://example.com/img.png"))
        val msg = MessageV2Proto(id = "m1", media = media)
        assertNotNull(msg.media)
        assertEquals("image", msg.media?.type)
        assertEquals("https://example.com/img.png", msg.media?.url)
        assertEquals(1, msg.media?.urls?.size)
        assertTrue(msg.text.isEmpty())
    }

    @Test
    fun messageV2Proto_mediaVoice() {
        val media = MessageMediaProto(type = "voice", url = "https://example.com/voice.ogg", duration = 15)
        val msg = MessageV2Proto(id = "m2", media = media)
        assertEquals("voice", msg.media?.type)
        assertEquals(15, msg.media?.duration)
    }

    @Test
    fun messageV2Proto_reply() {
        val reply = MessageReplyProto(messageId = "orig-123", preview = "Original text")
        val msg = MessageV2Proto(id = "m3", reply = reply)
        assertNotNull(msg.reply)
        assertEquals("orig-123", msg.reply?.messageId)
        assertEquals("Original text", msg.reply?.preview)
    }

    @Test
    fun messageV2Proto_e2ee() {
        val msg = MessageV2Proto(
            id = "m4", text = "", isE2EE = true, e2eePayload = "base64encrypted"
        )
        assertTrue(msg.isE2EE)
        assertEquals("base64encrypted", msg.e2eePayload)
    }

    @Test
    fun messageV2Proto_reactions() {
        val reactions = """{"uuid-1":"👍","uuid-2":"🔥"}""".toByteArray()
        val msg = MessageV2Proto(id = "m5", reactions = reactions)
        assertTrue(msg.reactions.isNotEmpty())
        val json = String(msg.reactions)
        assertTrue(json.contains("uuid-1"))
        assertTrue(json.contains("👍"))
    }

    @Test
    fun messageV2Proto_equals() {
        val ts = Timestamp.newBuilder().setSeconds(100).build()
        val a = MessageV2Proto(id = "m1", roomId = "r1", senderId = "s1", text = "hi", createdAt = ts)
        val b = MessageV2Proto(id = "m1", roomId = "r1", senderId = "s1", text = "hi", createdAt = ts)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun messageV2Proto_notEquals_differentId() {
        val a = MessageV2Proto(id = "m1", text = "hi")
        val b = MessageV2Proto(id = "m2", text = "hi")
        assertNotEquals(a, b)
    }

    // ======= MessageMediaProto =======

    @Test
    fun messageMediaProto_defaults() {
        val media = MessageMediaProto()
        assertEquals("", media.type)
        assertEquals("", media.url)
        assertTrue(media.urls.isEmpty())
        assertEquals(0, media.duration)
    }

    @Test
    fun messageMediaProto_image() {
        val media = MessageMediaProto(type = "image", url = "url1", urls = listOf("url1", "url2"))
        assertEquals("image", media.type)
        assertEquals(2, media.urls.size)
    }

    // ======= MessageReplyProto =======

    @Test
    fun messageReplyProto_defaults() {
        val reply = MessageReplyProto()
        assertEquals("", reply.messageId)
        assertEquals("", reply.preview)
    }

    @Test
    fun messageReplyProto_withValues() {
        val reply = MessageReplyProto(messageId = "id-1", preview = "text preview")
        assertEquals("id-1", reply.messageId)
        assertEquals("text preview", reply.preview)
    }

    // ======= ChatV2MessageProto =======

    @Test
    fun chatV2MessageProto_defaults() {
        val msg = ChatV2MessageProto()
        assertEquals("", msg.jwtToken)
        assertEquals("", msg.roomId)
        assertNull(msg.message)
        assertNull(msg.typing)
        assertNull(msg.system)
    }

    @Test
    fun chatV2MessageProto_authMessage() {
        val msg = ChatV2MessageProto(jwtToken = "jwt-abc", roomId = "room-1")
        assertEquals("jwt-abc", msg.jwtToken)
        assertEquals("room-1", msg.roomId)
    }

    @Test
    fun chatV2MessageProto_withMessage() {
        val inner = MessageV2Proto(id = "m1", text = "hi")
        val msg = ChatV2MessageProto(message = inner)
        assertNotNull(msg.message)
        assertEquals("m1", msg.message?.id)
    }

    @Test
    fun chatV2MessageProto_typing() {
        val msg = ChatV2MessageProto(typing = ChatV2TypingProto(isTyping = true))
        assertNotNull(msg.typing)
        assertTrue(msg.typing?.isTyping == true)
    }

    @Test
    fun chatV2MessageProto_system() {
        val msg = ChatV2MessageProto(system = ChatV2SystemProto(type = "DELETE_MESSAGE", message = "msg-123"))
        assertNotNull(msg.system)
        assertEquals("DELETE_MESSAGE", msg.system?.type)
        assertEquals("msg-123", msg.system?.message)
    }

    // ======= ChatV2TypingProto =======

    @Test
    fun chatV2TypingProto_defaults() {
        assertFalse(ChatV2TypingProto().isTyping)
    }

    @Test
    fun chatV2TypingProto_typing() {
        assertTrue(ChatV2TypingProto(isTyping = true).isTyping)
    }

    // ======= ChatV2SystemProto =======

    @Test
    fun chatV2SystemProto_defaults() {
        val sys = ChatV2SystemProto()
        assertEquals("", sys.type)
        assertEquals("", sys.message)
    }

    @Test
    fun chatV2SystemProto_deleteMessage() {
        val sys = ChatV2SystemProto(type = "DELETE_MESSAGE", message = "msg-id-456")
        assertEquals("DELETE_MESSAGE", sys.type)
        assertEquals("msg-id-456", sys.message)
    }

    // ======= GetHistoryV2Request/Response =======

    @Test
    fun getHistoryV2Request_defaults() {
        val req = GetHistoryV2RequestProto()
        assertEquals("", req.roomId)
        assertEquals(50, req.limit)
        assertEquals("", req.cursor)
    }

    @Test
    fun getHistoryV2Request_withCursor() {
        val req = GetHistoryV2RequestProto(roomId = "r1", limit = 25, cursor = "cursor-abc")
        assertEquals("r1", req.roomId)
        assertEquals(25, req.limit)
        assertEquals("cursor-abc", req.cursor)
    }

    @Test
    fun getHistoryV2Response_defaults() {
        val res = GetHistoryV2ResponseProto()
        assertTrue(res.messages.isEmpty())
        assertEquals("", res.nextCursor)
        assertFalse(res.hasMore)
    }

    @Test
    fun getHistoryV2Response_withMessages() {
        val msg = MessageV2Proto(id = "m1", text = "hello")
        val res = GetHistoryV2ResponseProto(messages = listOf(msg), nextCursor = "cursor-1", hasMore = true)
        assertEquals(1, res.messages.size)
        assertEquals("cursor-1", res.nextCursor)
        assertTrue(res.hasMore)
    }

    // ======= SendMessageV2Request/Response =======

    @Test
    fun sendMessageV2Request_text() {
        val req = SendMessageV2RequestProto(roomId = "r1", text = "Hello!")
        assertEquals("r1", req.roomId)
        assertEquals("Hello!", req.text)
        assertNull(req.media)
    }

    @Test
    fun sendMessageV2Request_media() {
        val media = MessageMediaProto(type = "image", url = "img.png")
        val req = SendMessageV2RequestProto(roomId = "r1", media = media)
        assertNotNull(req.media)
        assertEquals("image", req.media?.type)
        assertTrue(req.text.isEmpty())
    }

    @Test
    fun sendMessageV2Request_reply() {
        val req = SendMessageV2RequestProto(roomId = "r1", text = "replying", replyToId = "orig-1")
        assertEquals("orig-1", req.replyToId)
    }

    @Test
    fun sendMessageV2Request_e2ee() {
        val req = SendMessageV2RequestProto(roomId = "r1", isE2EE = true, e2eePayload = "encrypted")
        assertTrue(req.isE2EE)
        assertEquals("encrypted", req.e2eePayload)
    }

    @Test
    fun sendMessageV2Response_defaults() {
        val res = SendMessageV2ResponseProto()
        assertNull(res.message)
        assertFalse(res.success)
        assertEquals("", res.error)
    }

    @Test
    fun sendMessageV2Response_success() {
        val msg = MessageV2Proto(id = "m1", text = "sent")
        val res = SendMessageV2ResponseProto(message = msg, success = true)
        assertTrue(res.success)
        assertEquals("m1", res.message?.id)
    }

    @Test
    fun sendMessageV2Response_error() {
        val res = SendMessageV2ResponseProto(success = false, error = "rate limit")
        assertFalse(res.success)
        assertEquals("rate limit", res.error)
    }

    // ======= EditMessageV2 =======

    @Test
    fun editMessageV2Request() {
        val req = EditMessageV2RequestProto(messageId = "m1", text = "edited text")
        assertEquals("m1", req.messageId)
        assertEquals("edited text", req.text)
    }

    @Test
    fun editMessageV2Response() {
        val res = EditMessageV2ResponseProto(success = true, message = "ok")
        assertTrue(res.success)
        assertEquals("ok", res.message)
    }

    // ======= DeleteMessageV2 =======

    @Test
    fun deleteMessageV2Request() {
        val req = DeleteMessageV2RequestProto(messageIds = listOf("m1", "m2"), requesterUserId = "u1")
        assertEquals(2, req.messageIds.size)
        assertEquals("u1", req.requesterUserId)
    }

    @Test
    fun deleteMessageV2Response() {
        assertTrue(DeleteMessageV2ResponseProto(success = true).success)
        assertFalse(DeleteMessageV2ResponseProto(success = false).success)
    }

    // ======= SetReactionV2 =======

    @Test
    fun setReactionV2Request() {
        val req = SetReactionV2RequestProto(messageId = "m1", emoji = "👍")
        assertEquals("m1", req.messageId)
        assertEquals("👍", req.emoji)
    }

    @Test
    fun setReactionV2Response_defaults() {
        val res = SetReactionV2ResponseProto()
        assertFalse(res.success)
        assertTrue(res.reactions.isEmpty())
    }

    @Test
    fun setReactionV2Response_withReactions() {
        val reactions = """{"uuid-1":"👍"}""".toByteArray()
        val res = SetReactionV2ResponseProto(success = true, reactions = reactions)
        assertTrue(res.success)
        assertTrue(res.reactions.isNotEmpty())
    }

    @Test
    fun setReactionV2Response_equals() {
        val a = SetReactionV2ResponseProto(success = true)
        val b = SetReactionV2ResponseProto(success = true)
        assertEquals(a, b)
    }

    @Test
    fun setReactionV2Response_notEquals() {
        val a = SetReactionV2ResponseProto(success = true)
        val b = SetReactionV2ResponseProto(success = false)
        assertNotEquals(a, b)
    }

    // ======= Marshallers: serialization round-trip =======

    @Test
    fun chatV2MessageMarshaller_roundTrip_auth() {
        val original = ChatV2MessageProto(jwtToken = "jwt-123", roomId = "room-abc")
        val marshaller = ChatV2MessageMarshaller()
        val bytes = marshaller.stream(original).readBytes()
        assertTrue(bytes.isNotEmpty())
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(bytes))
        assertEquals("jwt-123", parsed.jwtToken)
        assertEquals("room-abc", parsed.roomId)
        assertNull(parsed.message)
    }

    @Test
    fun chatV2MessageMarshaller_roundTrip_message() {
        val ts = Timestamp.newBuilder().setSeconds(1700000000).setNanos(0).build()
        val inner = MessageV2Proto(
            id = "m1", roomId = "r1", senderId = "uuid-1",
            text = "Hello!", createdAt = ts, edited = true, isRead = true
        )
        val original = ChatV2MessageProto(message = inner)
        val marshaller = ChatV2MessageMarshaller()
        val bytes = marshaller.stream(original).readBytes()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(bytes))
        assertNotNull(parsed.message)
        assertEquals("m1", parsed.message?.id)
        assertEquals("r1", parsed.message?.roomId)
        assertEquals("uuid-1", parsed.message?.senderId)
        assertEquals("Hello!", parsed.message?.text)
        assertTrue(parsed.message?.edited == true)
        assertTrue(parsed.message?.isRead == true)
    }

    @Test
    fun chatV2MessageMarshaller_roundTrip_typing() {
        val original = ChatV2MessageProto(typing = ChatV2TypingProto(isTyping = true))
        val marshaller = ChatV2MessageMarshaller()
        val bytes = marshaller.stream(original).readBytes()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(bytes))
        assertNotNull(parsed.typing)
        assertTrue(parsed.typing?.isTyping == true)
    }

    @Test
    fun chatV2MessageMarshaller_roundTrip_system() {
        val original = ChatV2MessageProto(system = ChatV2SystemProto(type = "DELETE_MESSAGE", message = "m-99"))
        val marshaller = ChatV2MessageMarshaller()
        val bytes = marshaller.stream(original).readBytes()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(bytes))
        assertNotNull(parsed.system)
        assertEquals("DELETE_MESSAGE", parsed.system?.type)
        assertEquals("m-99", parsed.system?.message)
    }

    @Test
    fun messageV2ProtoMarshaller_roundTrip_text() {
        val ts = Timestamp.newBuilder().setSeconds(1700000000).setNanos(123000000).build()
        val original = MessageV2Proto(
            id = "m1", roomId = "r1", senderId = "uuid-1",
            text = "Hello", createdAt = ts, isRead = true, edited = true
        )
        val marshaller = MessageV2ProtoMarshaller()
        val bytes = marshaller.stream(original).readBytes()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(bytes))
        assertEquals("m1", parsed.id)
        assertEquals("r1", parsed.roomId)
        assertEquals("uuid-1", parsed.senderId)
        assertEquals("Hello", parsed.text)
        assertEquals(1700000000L, parsed.createdAt?.seconds)
        assertEquals(123000000, parsed.createdAt?.nanos)
        assertTrue(parsed.isRead)
        assertTrue(parsed.edited)
    }

    @Test
    fun messageV2ProtoMarshaller_roundTrip_media() {
        val media = MessageMediaProto(type = "image", url = "img.png", urls = listOf("img.png", "img2.png"), duration = 0)
        val original = MessageV2Proto(id = "m1", roomId = "r1", senderId = "s1", media = media)
        val marshaller = MessageV2ProtoMarshaller()
        val bytes = marshaller.stream(original).readBytes()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(bytes))
        assertNotNull(parsed.media)
        assertEquals("image", parsed.media?.type)
        assertEquals("img.png", parsed.media?.url)
        assertEquals(2, parsed.media?.urls?.size)
        assertTrue(parsed.text.isEmpty())
    }

    @Test
    fun messageV2ProtoMarshaller_roundTrip_reply() {
        val reply = MessageReplyProto(messageId = "orig-1", preview = "quoted text")
        val original = MessageV2Proto(id = "m1", roomId = "r1", senderId = "s1", reply = reply)
        val marshaller = MessageV2ProtoMarshaller()
        val bytes = marshaller.stream(original).readBytes()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(bytes))
        assertNotNull(parsed.reply)
        assertEquals("orig-1", parsed.reply?.messageId)
        assertEquals("quoted text", parsed.reply?.preview)
    }

    @Test
    fun messageV2ProtoMarshaller_roundTrip_reactions() {
        val reactions = """{"uuid-1":"👍","uuid-2":"🔥"}""".toByteArray()
        val original = MessageV2Proto(id = "m1", reactions = reactions)
        val marshaller = MessageV2ProtoMarshaller()
        val bytes = marshaller.stream(original).readBytes()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(bytes))
        assertTrue(parsed.reactions.isNotEmpty())
        val json = String(parsed.reactions)
        assertTrue(json.contains("uuid-1"))
        assertTrue(json.contains("👍"))
        assertTrue(json.contains("uuid-2"))
        assertTrue(json.contains("🔥"))
    }

    @Test
    fun messageV2ProtoMarshaller_roundTrip_e2ee() {
        val original = MessageV2Proto(id = "m1", isE2EE = true, e2eePayload = "base64data")
        val marshaller = MessageV2ProtoMarshaller()
        val bytes = marshaller.stream(original).readBytes()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(bytes))
        assertTrue(parsed.isE2EE)
        assertEquals("base64data", parsed.e2eePayload)
    }

    @Test
    fun messageV2ProtoMarshaller_emptyBytes() {
        val marshaller = MessageV2ProtoMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertEquals("", parsed.id)
        assertEquals("", parsed.text)
        assertFalse(parsed.isRead)
    }

    // ======= GetHistoryV2 marshallers =======

    @Test
    fun getHistoryV2RequestMarshaller_serializes() {
        val req = GetHistoryV2RequestProto(roomId = "room-1", limit = 25, cursor = "cursor-abc")
        val marshaller = GetHistoryV2RequestMarshaller()
        val bytes = marshaller.stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun getHistoryV2RequestMarshaller_empty() {
        val req = GetHistoryV2RequestProto(limit = 0)
        val marshaller = GetHistoryV2RequestMarshaller()
        val bytes = marshaller.stream(req).readBytes()
        assertEquals(0, bytes.size)
    }

    @Test
    fun getHistoryV2ResponseMarshaller_emptyBytes() {
        val marshaller = GetHistoryV2ResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertTrue(parsed.messages.isEmpty())
        assertEquals("", parsed.nextCursor)
        assertFalse(parsed.hasMore)
    }

    @Test
    fun getHistoryV2ResponseMarshaller_withMessages() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        // Write a MessageV2 at field 1
        val msgBaos = java.io.ByteArrayOutputStream()
        val msgCos = com.google.protobuf.CodedOutputStream.newInstance(msgBaos)
        msgCos.writeString(1, "m1")
        msgCos.writeString(2, "r1")
        msgCos.writeString(3, "uuid-1")
        msgCos.writeString(10, "Hello")
        msgCos.flush()
        val msgBytes = msgBaos.toByteArray()
        cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        cos.writeUInt32NoTag(msgBytes.size)
        cos.writeRawBytes(msgBytes)
        cos.writeString(2, "cursor-1")
        cos.writeBool(3, true)
        cos.flush()
        val parsed = GetHistoryV2ResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals(1, parsed.messages.size)
        assertEquals("m1", parsed.messages[0].id)
        assertEquals("Hello", parsed.messages[0].text)
        assertEquals("cursor-1", parsed.nextCursor)
        assertTrue(parsed.hasMore)
    }

    // ======= SendMessageV2 marshallers =======

    @Test
    fun sendMessageV2RequestMarshaller_serializes() {
        val req = SendMessageV2RequestProto(roomId = "r1", text = "Hi!")
        val marshaller = SendMessageV2RequestMarshaller()
        val bytes = marshaller.stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun sendMessageV2ResponseMarshaller_empty() {
        val marshaller = SendMessageV2ResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertNull(parsed.message)
        assertFalse(parsed.success)
        assertEquals("", parsed.error)
    }

    @Test
    fun sendMessageV2ResponseMarshaller_withMessage() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        // Write nested MessageV2 at field 1
        val msgBaos = java.io.ByteArrayOutputStream()
        val msgCos = com.google.protobuf.CodedOutputStream.newInstance(msgBaos)
        msgCos.writeString(1, "m1")
        msgCos.writeString(10, "sent text")
        msgCos.flush()
        val msgBytes = msgBaos.toByteArray()
        cos.writeTag(1, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        cos.writeUInt32NoTag(msgBytes.size)
        cos.writeRawBytes(msgBytes)
        cos.writeBool(2, true)
        cos.flush()
        val parsed = SendMessageV2ResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertTrue(parsed.success)
        assertNotNull(parsed.message)
        assertEquals("m1", parsed.message?.id)
        assertEquals("sent text", parsed.message?.text)
    }

    // ======= EditMessageV2 marshallers =======

    @Test
    fun editMessageV2RequestMarshaller_serializes() {
        val req = EditMessageV2RequestProto(messageId = "m1", text = "edited")
        val marshaller = EditMessageV2RequestMarshaller()
        val bytes = marshaller.stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun editMessageV2ResponseMarshaller_parses() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeBool(1, true)
        cos.writeString(2, "ok")
        cos.flush()
        val parsed = EditMessageV2ResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertTrue(parsed.success)
        assertEquals("ok", parsed.message)
    }

    // ======= DeleteMessageV2 marshallers =======

    @Test
    fun deleteMessageV2RequestMarshaller_serializes() {
        val req = DeleteMessageV2RequestProto(messageIds = listOf("m1", "m2"), requesterUserId = "u1")
        val marshaller = DeleteMessageV2RequestMarshaller()
        val bytes = marshaller.stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun deleteMessageV2ResponseMarshaller_parses() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeBool(1, true)
        cos.flush()
        val parsed = DeleteMessageV2ResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertTrue(parsed.success)
    }

    // ======= SetReactionV2 marshallers =======

    @Test
    fun setReactionV2RequestMarshaller_serializes() {
        val req = SetReactionV2RequestProto(messageId = "m1", emoji = "👍")
        val marshaller = SetReactionV2RequestMarshaller()
        val bytes = marshaller.stream(req).readBytes()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun setReactionV2ResponseMarshaller_empty() {
        val marshaller = SetReactionV2ResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse(parsed.success)
        assertTrue(parsed.reactions.isEmpty())
    }

    @Test
    fun setReactionV2ResponseMarshaller_withReactions() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeBool(1, true)
        val reactionsBytes = """{"uuid-1":"👍"}""".toByteArray()
        cos.writeTag(2, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        cos.writeUInt32NoTag(reactionsBytes.size)
        cos.writeRawBytes(reactionsBytes)
        cos.flush()
        val parsed = SetReactionV2ResponseMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertTrue(parsed.success)
        assertTrue(parsed.reactions.isNotEmpty())
    }

    // ======= Edge cases =======

    @Test
    fun messageV2ProtoMarshaller_skipUnknownFields() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(1, "m1")
        cos.writeString(10, "text")
        cos.writeString(99, "unknown-field-value") // field 99 — should be skipped
        cos.flush()
        val parsed = MessageV2ProtoMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals("m1", parsed.id)
        assertEquals("text", parsed.text)
    }

    @Test
    fun chatV2MessageMarshaller_skipUnknownFields() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(1, "jwt")
        cos.writeString(99, "unknown")
        cos.flush()
        val parsed = ChatV2MessageMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals("jwt", parsed.jwtToken)
    }

    @Test
    fun messageV2ProtoMarshaller_multipleContentFields_lastWins() {
        val baos = java.io.ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        cos.writeString(1, "m1")
        cos.writeString(10, "text-content")
        // Also write media at field 11 — in protobuf, both are written, but the last one of a oneof wins
        val mediaBaos = java.io.ByteArrayOutputStream()
        val mediaCos = com.google.protobuf.CodedOutputStream.newInstance(mediaBaos)
        mediaCos.writeString(1, "voice")
        mediaCos.writeString(2, "voice.ogg")
        mediaCos.flush()
        val mediaBytes = mediaBaos.toByteArray()
        cos.writeTag(11, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
        cos.writeUInt32NoTag(mediaBytes.size)
        cos.writeRawBytes(mediaBytes)
        cos.flush()
        val parsed = MessageV2ProtoMarshaller().parse(java.io.ByteArrayInputStream(baos.toByteArray()))
        assertEquals("m1", parsed.id)
        // Both text and media parsed (protobuf doesn't enforce oneof at wire level)
        assertEquals("text-content", parsed.text)
        assertNotNull(parsed.media)
        assertEquals("voice", parsed.media?.type)
    }

    // ======= when-bug fix tests: text+reply and text+media must serialize independently =======

    @Test
    fun messageV2ProtoMarshaller_roundTrip_textAndReply() {
        val reply = MessageReplyProto(messageId = "orig-1", preview = "quoted text", senderId = "uuid-sender")
        val original = MessageV2Proto(
            id = "m1", roomId = "r1", senderId = "uuid-1",
            text = "My reply", reply = reply
        )
        val marshaller = MessageV2ProtoMarshaller()
        val bytes = marshaller.stream(original).readBytes()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(bytes))
        assertEquals("m1", parsed.id)
        assertEquals("My reply", parsed.text)
        assertNotNull(parsed.reply)
        assertEquals("orig-1", parsed.reply?.messageId)
        assertEquals("quoted text", parsed.reply?.preview)
        assertEquals("uuid-sender", parsed.reply?.senderId)
    }

    @Test
    fun messageV2ProtoMarshaller_roundTrip_textAndMedia() {
        val media = MessageMediaProto(type = "image", url = "photo.jpg", urls = listOf("thumb.jpg"))
        val original = MessageV2Proto(
            id = "m2", roomId = "r1", senderId = "uuid-1",
            text = "Check this out", media = media
        )
        val marshaller = MessageV2ProtoMarshaller()
        val bytes = marshaller.stream(original).readBytes()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(bytes))
        assertEquals("m2", parsed.id)
        assertEquals("Check this out", parsed.text)
        assertNotNull(parsed.media)
        assertEquals("image", parsed.media?.type)
        assertEquals("photo.jpg", parsed.media?.url)
        assertEquals(1, parsed.media?.urls?.size)
    }

    @Test
    fun messageV2ProtoMarshaller_roundTrip_allContentFields() {
        val media = MessageMediaProto(type = "voice", url = "voice.ogg", duration = 10)
        val reply = MessageReplyProto(messageId = "orig-2", preview = "previous message")
        val original = MessageV2Proto(
            id = "m3", roomId = "r2", senderId = "uuid-2",
            text = "Reply with media", media = media, reply = reply,
            edited = true, isRead = true, isE2EE = false
        )
        val marshaller = MessageV2ProtoMarshaller()
        val bytes = marshaller.stream(original).readBytes()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(bytes))
        assertEquals("m3", parsed.id)
        assertEquals("Reply with media", parsed.text)
        assertNotNull(parsed.media)
        assertEquals("voice", parsed.media?.type)
        assertEquals(10, parsed.media?.duration)
        assertNotNull(parsed.reply)
        assertEquals("orig-2", parsed.reply?.messageId)
        assertEquals("previous message", parsed.reply?.preview)
        assertTrue(parsed.edited)
        assertTrue(parsed.isRead)
    }

    @Test
    fun messageV2ProtoMarshaller_roundTrip_replyOnly() {
        val reply = MessageReplyProto(messageId = "orig-3", preview = "just a reply")
        val original = MessageV2Proto(
            id = "m4", roomId = "r1", senderId = "uuid-1",
            reply = reply
        )
        val marshaller = MessageV2ProtoMarshaller()
        val bytes = marshaller.stream(original).readBytes()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(bytes))
        assertEquals("m4", parsed.id)
        assertTrue(parsed.text.isEmpty())
        assertNull(parsed.media)
        assertNotNull(parsed.reply)
        assertEquals("orig-3", parsed.reply?.messageId)
    }

    @Test
    fun sendMessageV2RequestMarshaller_roundTrip_textAndMedia() {
        val media = MessageMediaProto(type = "image", url = "photo.jpg")
        val original = SendMessageV2RequestProto(
            roomId = "r1", text = "Caption", media = media
        )
        val marshaller = SendMessageV2RequestMarshaller()
        val bytes = marshaller.stream(original).readBytes()
        // Parse manually since request marshaller parse() returns default
        val cis = com.google.protobuf.CodedInputStream.newInstance(java.io.ByteArrayInputStream(bytes))
        var parsedRoomId = ""; var parsedText = ""; var parsedMedia: MessageMediaProto? = null
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> parsedRoomId = cis.readString()
                2 -> parsedText = cis.readString()
                3 -> parsedMedia = MessageMediaProtoMarshaller.parse(cis)
                else -> cis.skipField(tag)
            }
        }
        assertEquals("r1", parsedRoomId)
        assertEquals("Caption", parsedText)
        assertNotNull(parsedMedia)
        assertEquals("image", parsedMedia?.type)
        assertEquals("photo.jpg", parsedMedia?.url)
    }

    // ======= ClearRoomHistory =======

    @Test
    fun clearRoomHistoryRequestProto_defaults() {
        val proto = ClearRoomHistoryRequestProto()
        assertEquals("", proto.roomId)
        assertEquals("", proto.requesterUsername)
    }

    @Test
    fun clearRoomHistoryRequestProto_withValues() {
        val proto = ClearRoomHistoryRequestProto(roomId = "room-42", requesterUsername = "alice")
        assertEquals("room-42", proto.roomId)
        assertEquals("alice", proto.requesterUsername)
    }

    @Test
    fun clearRoomHistoryResponseProto_defaults() {
        val proto = ClearRoomHistoryResponseProto()
        assertFalse(proto.success)
    }

    @Test
    fun clearRoomHistoryResponseProto_success() {
        val proto = ClearRoomHistoryResponseProto(success = true)
        assertTrue(proto.success)
    }

    @Test
    fun clearRoomHistoryRequestMarshaller_stream_writesFields() {
        val marshaller = ClearRoomHistoryRequestMarshaller()
        val proto = ClearRoomHistoryRequestProto(roomId = "room-1", requesterUsername = "bob")
        val bytes = marshaller.stream(proto).readBytes()
        assertTrue("Should have data", bytes.isNotEmpty())
    }

    @Test
    fun clearRoomHistoryRequestMarshaller_stream_emptyFields() {
        val marshaller = ClearRoomHistoryRequestMarshaller()
        val proto = ClearRoomHistoryRequestProto()
        val bytes = marshaller.stream(proto).readBytes()
        assertEquals("Empty proto should produce empty bytes", 0, bytes.size)
    }

    @Test
    fun clearRoomHistoryResponseMarshaller_parse_true() {
        val marshaller = ClearRoomHistoryResponseMarshaller()
        // Manually encode: field 1 (bool) = true → tag=8, value=1
        val data = byteArrayOf(8, 1)
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(data))
        assertTrue(parsed.success)
    }

    @Test
    fun clearRoomHistoryResponseMarshaller_parse_false() {
        val marshaller = ClearRoomHistoryResponseMarshaller()
        // field 1 (bool) = false → tag=8, value=0
        val data = byteArrayOf(8, 0)
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(data))
        assertFalse(parsed.success)
    }

    @Test
    fun clearRoomHistoryResponseMarshaller_parse_empty() {
        val marshaller = ClearRoomHistoryResponseMarshaller()
        val parsed = marshaller.parse(java.io.ByteArrayInputStream(byteArrayOf()))
        assertFalse("Empty response should default to false", parsed.success)
    }

    @Test
    fun clearRoomHistoryRequestMarshaller_roundTrip() {
        val marshaller = ClearRoomHistoryRequestMarshaller()
        val original = ClearRoomHistoryRequestProto(roomId = "r1", requesterUsername = "user1")
        val bytes = marshaller.stream(original).readBytes()
        // Parse manually since request marshaller parse() returns default
        val cis = com.google.protobuf.CodedInputStream.newInstance(java.io.ByteArrayInputStream(bytes))
        var parsedRoomId = ""; var parsedUsername = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag(); if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> parsedRoomId = cis.readString()
                2 -> parsedUsername = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        assertEquals("r1", parsedRoomId)
        assertEquals("user1", parsedUsername)
    }
}
