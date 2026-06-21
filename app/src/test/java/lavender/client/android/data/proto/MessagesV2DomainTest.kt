package lavender.client.android.data.proto

import lavender.client.android.data.grpc.GrpcMessageV2Client
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.Reaction
import org.junit.Assert.*
import org.junit.Test

class MessagesV2DomainTest {

    private fun resolveUsername(senderId: String): String = when (senderId) {
        "uuid-1" -> "alice"
        "uuid-2" -> "bob"
        "uuid-admin" -> "admin"
        else -> ""
    }

    // ======= ProtoUtils.createMessageV2Proto =======

    @Test
    fun createMessageV2Proto_textMessage() {
        val msg = Message(
            id = "msg-001", user = "alice", text = "Hello!",
            timestamp = 1700000000000L, roomId = "room-1", userId = "uuid-1"
        )
        val proto = ProtoUtils.createMessageV2Proto(msg)
        assertEquals("msg-001", proto.id)
        assertEquals("room-1", proto.roomId)
        assertEquals("uuid-1", proto.senderId)
        assertEquals("Hello!", proto.text)
        assertNull(proto.media)
        assertNull(proto.reply)
        assertEquals(1700000000L, proto.createdAt?.seconds)
    }

    @Test
    fun createMessageV2Proto_imageMessage() {
        val msg = Message(
            id = "m1", user = "alice", text = "", timestamp = 1000L,
            roomId = "r1", userId = "uuid-1",
            imageUrl = "https://example.com/img.png",
            imageUrls = listOf("https://example.com/img.png", "https://example.com/img2.png")
        )
        val proto = ProtoUtils.createMessageV2Proto(msg)
        assertNotNull(proto.media)
        assertEquals("image", proto.media?.type)
        assertEquals("https://example.com/img.png", proto.media?.url)
        assertEquals(2, proto.media?.urls?.size)
    }

    @Test
    fun createMessageV2Proto_voiceMessage() {
        val msg = Message(
            id = "m1", user = "alice", text = "", timestamp = 1000L,
            roomId = "r1", userId = "uuid-1",
            voiceUrl = "https://example.com/voice.ogg", duration = 30
        )
        val proto = ProtoUtils.createMessageV2Proto(msg)
        assertNotNull(proto.media)
        assertEquals("voice", proto.media?.type)
        assertEquals(30, proto.media?.duration)
    }

    @Test
    fun createMessageV2Proto_replyMessage() {
        val msg = Message(
            id = "m1", user = "alice", text = "replying", timestamp = 1000L,
            roomId = "r1", userId = "uuid-1",
            repliedToMessageId = "orig-123", repliedToText = "original"
        )
        val proto = ProtoUtils.createMessageV2Proto(msg)
        assertNotNull(proto.reply)
        assertEquals("orig-123", proto.reply?.messageId)
        assertEquals("original", proto.reply?.preview)
        assertEquals("replying", proto.text)
    }

    @Test
    fun createMessageV2Proto_reactions() {
        val msg = Message(
            id = "m1", user = "alice", text = "hi", timestamp = 1000L,
            roomId = "r1", userId = "uuid-1",
            reactions = listOf(Reaction("uuid-1", "👍"), Reaction("uuid-2", "🔥"))
        )
        val proto = ProtoUtils.createMessageV2Proto(msg)
        assertTrue(proto.reactions.isNotEmpty())
        val json = String(proto.reactions)
        assertTrue(json.contains("uuid-1"))
        assertTrue(json.contains("👍"))
        assertTrue(json.contains("uuid-2"))
        assertTrue(json.contains("🔥"))
    }

    @Test
    fun createMessageV2Proto_e2ee() {
        val msg = Message(
            id = "m1", user = "alice", text = "", timestamp = 1000L,
            roomId = "r1", userId = "uuid-1",
            isE2EE = true, e2eePayload = "encrypted-data"
        )
        val proto = ProtoUtils.createMessageV2Proto(msg)
        assertTrue(proto.isE2EE)
        assertEquals("encrypted-data", proto.e2eePayload)
    }

    @Test
    fun createMessageV2Proto_editedAndRead() {
        val msg = Message(
            id = "m1", user = "alice", text = "edited", timestamp = 1000L,
            roomId = "r1", userId = "uuid-1", edited = true, isRead = true
        )
        val proto = ProtoUtils.createMessageV2Proto(msg)
        assertTrue(proto.edited)
        assertTrue(proto.isRead)
    }

    // ======= ProtoUtils.createMessageFromV2Proto =======

    @Test
    fun createMessageFromV2Proto_textMessage() {
        val ts = com.google.protobuf.Timestamp.newBuilder()
            .setSeconds(1700000000).setNanos(500000000).build()
        val proto = MessageV2Proto(
            id = "m1", roomId = "r1", senderId = "uuid-1",
            text = "Hello!", createdAt = ts, isRead = true, edited = true
        )
        val msg = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertEquals("m1", msg.id)
        assertEquals("r1", msg.roomId)
        assertEquals("alice", msg.user)
        assertEquals("uuid-1", msg.userId)
        assertEquals("Hello!", msg.text)
        assertEquals(1700000000500L, msg.timestamp)
        assertTrue(msg.isRead)
        assertTrue(msg.edited)
    }

    @Test
    fun createMessageFromV2Proto_unknownSender() {
        val proto = MessageV2Proto(
            id = "m1", roomId = "r1", senderId = "unknown-uuid",
            text = "Hi", createdAt = com.google.protobuf.Timestamp.getDefaultInstance()
        )
        val msg = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertEquals("", msg.user)
        assertEquals("unknown-uuid", msg.userId)
    }

    @Test
    fun createMessageFromV2Proto_imageMessage() {
        val media = MessageMediaProto(type = "image", url = "img.png", urls = listOf("img.png", "img2.png"))
        val proto = MessageV2Proto(id = "m1", roomId = "r1", senderId = "uuid-1", media = media)
        val msg = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertEquals("img.png", msg.imageUrl)
        assertEquals(2, msg.imageUrls.size)
        assertEquals("", msg.text)
    }

    @Test
    fun createMessageFromV2Proto_voiceMessage() {
        val media = MessageMediaProto(type = "voice", url = "voice.ogg", duration = 25)
        val proto = MessageV2Proto(id = "m1", roomId = "r1", senderId = "uuid-1", media = media)
        val msg = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertEquals("voice.ogg", msg.voiceUrl)
        assertEquals(25, msg.duration)
    }

    @Test
    fun createMessageFromV2Proto_replyMessage() {
        val reply = MessageReplyProto(messageId = "orig-1", preview = "quoted")
        val proto = MessageV2Proto(id = "m1", roomId = "r1", senderId = "uuid-1", text = "reply", reply = reply)
        val msg = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertEquals("orig-1", msg.repliedToMessageId)
        assertEquals("quoted", msg.repliedToText)
    }

    @Test
    fun createMessageFromV2Proto_reactions() {
        val reactions = """{"uuid-1":"👍","uuid-2":"🔥"}""".toByteArray()
        val proto = MessageV2Proto(id = "m1", roomId = "r1", senderId = "uuid-1", reactions = reactions)
        val msg = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertEquals(2, msg.reactions.size)
        assertTrue(msg.reactions.any { it.user == "uuid-1" && it.emoji == "👍" })
        assertTrue(msg.reactions.any { it.user == "uuid-2" && it.emoji == "🔥" })
    }

    @Test
    fun createMessageFromV2Proto_emptyReactions() {
        val proto = MessageV2Proto(id = "m1", reactions = byteArrayOf())
        val msg = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertTrue(msg.reactions.isEmpty())
    }

    @Test
    fun createMessageFromV2Proto_invalidReactionsJson() {
        val proto = MessageV2Proto(id = "m1", reactions = "not-json".toByteArray())
        val msg = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertTrue(msg.reactions.isEmpty())
    }

    @Test
    fun createMessageFromV2Proto_e2ee() {
        val proto = MessageV2Proto(
            id = "m1", roomId = "r1", senderId = "uuid-1",
            text = "", isE2EE = true, e2eePayload = "encrypted"
        )
        val msg = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertTrue(msg.isE2EE)
        assertEquals("encrypted", msg.e2eePayload)
    }

    @Test
    fun createMessageFromV2Proto_nullTimestamp() {
        val proto = MessageV2Proto(id = "m1", senderId = "uuid-1", text = "hi")
        val msg = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        // Falls back to System.currentTimeMillis(), so just check it's > 0
        assertTrue(msg.timestamp > 0)
    }

    // ======= Round-trip: domain → proto → domain =======

    @Test
    fun roundTrip_textMessage() {
        val original = Message(
            id = "m1", user = "alice", text = "Hello!",
            timestamp = 1700000000000L, roomId = "r1", userId = "uuid-1",
            isRead = true, edited = true
        )
        val proto = ProtoUtils.createMessageV2Proto(original)
        val restored = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertEquals(original.id, restored.id)
        assertEquals(original.user, restored.user)
        assertEquals(original.text, restored.text)
        assertEquals(original.roomId, restored.roomId)
        assertEquals(original.userId, restored.userId)
        assertEquals(original.isRead, restored.isRead)
        assertEquals(original.edited, restored.edited)
    }

    @Test
    fun roundTrip_imageMessage() {
        val original = Message(
            id = "m1", user = "alice", text = "", timestamp = 1000L,
            roomId = "r1", userId = "uuid-1",
            imageUrl = "img.png", imageUrls = listOf("img.png", "img2.png")
        )
        val proto = ProtoUtils.createMessageV2Proto(original)
        val restored = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertEquals(original.imageUrl, restored.imageUrl)
        assertEquals(original.imageUrls.size, restored.imageUrls.size)
    }

    @Test
    fun roundTrip_voiceMessage() {
        val original = Message(
            id = "m1", user = "alice", text = "", timestamp = 1000L,
            roomId = "r1", userId = "uuid-1",
            voiceUrl = "voice.ogg", duration = 30
        )
        val proto = ProtoUtils.createMessageV2Proto(original)
        val restored = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertEquals(original.voiceUrl, restored.voiceUrl)
        assertEquals(original.duration, restored.duration)
    }

    @Test
    fun roundTrip_replyMessage() {
        val original = Message(
            id = "m1", user = "alice", text = "reply", timestamp = 1000L,
            roomId = "r1", userId = "uuid-1",
            repliedToMessageId = "orig-1", repliedToText = "original"
        )
        val proto = ProtoUtils.createMessageV2Proto(original)
        val restored = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertEquals(original.repliedToMessageId, restored.repliedToMessageId)
        assertEquals(original.repliedToText, restored.repliedToText)
    }

    @Test
    fun roundTrip_reactions() {
        val original = Message(
            id = "m1", user = "alice", text = "hi", timestamp = 1000L,
            roomId = "r1", userId = "uuid-1",
            reactions = listOf(Reaction("uuid-1", "👍"), Reaction("uuid-2", "🔥"))
        )
        val proto = ProtoUtils.createMessageV2Proto(original)
        val restored = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertEquals(2, restored.reactions.size)
        assertTrue(restored.reactions.any { it.user == "uuid-1" && it.emoji == "👍" })
        assertTrue(restored.reactions.any { it.user == "uuid-2" && it.emoji == "🔥" })
    }

    @Test
    fun roundTrip_e2ee() {
        val original = Message(
            id = "m1", user = "alice", text = "", timestamp = 1000L,
            roomId = "r1", userId = "uuid-1",
            isE2EE = true, e2eePayload = "encrypted"
        )
        val proto = ProtoUtils.createMessageV2Proto(original)
        val restored = ProtoUtils.createMessageFromV2Proto(proto, ::resolveUsername)
        assertTrue(restored.isE2EE)
        assertEquals("encrypted", restored.e2eePayload)
    }

    // ======= GrpcMessageV2Client conversion =======

    @Test
    fun messageV2Client_messageV2ToDomain_text() {
        val client = createTestV2Client()
        val ts = com.google.protobuf.Timestamp.newBuilder().setSeconds(1700000000).build()
        val proto = MessageV2Proto(
            id = "m1", roomId = "r1", senderId = "uuid-2",
            text = "From bob", createdAt = ts, isRead = true
        )
        val msg = client.messageV2ToDomain(proto)
        assertEquals("m1", msg.id)
        assertEquals("bob", msg.user)
        assertEquals("uuid-2", msg.userId)
        assertEquals("From bob", msg.text)
        assertEquals("r1", msg.roomId)
        assertTrue(msg.isRead)
    }

    @Test
    fun messageV2Client_messageV2ToDomain_image() {
        val client = createTestV2Client()
        val media = MessageMediaProto(type = "image", url = "photo.jpg", urls = listOf("photo.jpg"))
        val proto = MessageV2Proto(id = "m1", roomId = "r1", senderId = "uuid-1", media = media)
        val msg = client.messageV2ToDomain(proto)
        assertEquals("photo.jpg", msg.imageUrl)
        assertEquals(1, msg.imageUrls.size)
    }

    @Test
    fun messageV2Client_messageV2ToDomain_voice() {
        val client = createTestV2Client()
        val media = MessageMediaProto(type = "voice", url = "audio.ogg", duration = 45)
        val proto = MessageV2Proto(id = "m1", roomId = "r1", senderId = "uuid-1", media = media)
        val msg = client.messageV2ToDomain(proto)
        assertEquals("audio.ogg", msg.voiceUrl)
        assertEquals(45, msg.duration)
    }

    @Test
    fun messageV2Client_messageV2ToDomain_reply() {
        val client = createTestV2Client()
        val reply = MessageReplyProto(messageId = "orig-1", preview = "quoted text")
        val proto = MessageV2Proto(id = "m1", roomId = "r1", senderId = "uuid-1", text = "replying", reply = reply)
        val msg = client.messageV2ToDomain(proto)
        assertEquals("orig-1", msg.repliedToMessageId)
        assertEquals("quoted text", msg.repliedToText)
        assertEquals("replying", msg.text)
    }

    @Test
    fun messageV2Client_messageV2ToDomain_reactions() {
        val client = createTestV2Client()
        val reactions = """{"uuid-1":"👍","uuid-2":"❤️"}""".toByteArray()
        val proto = MessageV2Proto(id = "m1", roomId = "r1", senderId = "uuid-1", reactions = reactions)
        val msg = client.messageV2ToDomain(proto)
        assertEquals(2, msg.reactions.size)
        assertTrue(msg.reactions.any { r -> r.user == "uuid-1" && r.emoji == "👍" })
        assertTrue(msg.reactions.any { r -> r.user == "uuid-2" && r.emoji == "❤️" })
    }

    @Test
    fun messageV2Client_messageV2ToDomain_e2ee() {
        val client = createTestV2Client()
        val proto = MessageV2Proto(
            id = "m1", roomId = "r1", senderId = "uuid-1",
            isE2EE = true, e2eePayload = "encrypted-data"
        )
        val msg = client.messageV2ToDomain(proto)
        assertTrue(msg.isE2EE)
        assertEquals("encrypted-data", msg.e2eePayload)
    }

    @Test
    fun messageV2Client_messageV2ToDomain_unknownUser() {
        val client = createTestV2Client()
        val proto = MessageV2Proto(id = "m1", senderId = "unknown-uuid", text = "hi")
        val msg = client.messageV2ToDomain(proto)
        assertEquals("", msg.user)
        assertEquals("unknown-uuid", msg.userId)
    }

    @Test
    fun messageV2Client_domainToSendRequest_text() {
        val client = createTestV2Client()
        val msg = Message(
            id = "m1", user = "alice", text = "Hello!",
            timestamp = 1000L, roomId = "r1", userId = "uuid-1"
        )
        val req = client.domainToSendRequest(msg)
        assertEquals("r1", req.roomId)
        assertEquals("Hello!", req.text)
        assertNull(req.media)
    }

    @Test
    fun messageV2Client_domainToSendRequest_image() {
        val client = createTestV2Client()
        val msg = Message(
            id = "m1", user = "alice", text = "", timestamp = 1000L,
            roomId = "r1", userId = "uuid-1",
            imageUrl = "img.png", imageUrls = listOf("img.png")
        )
        val req = client.domainToSendRequest(msg)
        assertNotNull(req.media)
        assertEquals("image", req.media?.type)
        assertEquals("img.png", req.media?.url)
    }

    @Test
    fun messageV2Client_domainToSendRequest_voice() {
        val client = createTestV2Client()
        val msg = Message(
            id = "m1", user = "alice", text = "", timestamp = 1000L,
            roomId = "r1", userId = "uuid-1",
            voiceUrl = "voice.ogg", duration = 20
        )
        val req = client.domainToSendRequest(msg)
        assertNotNull(req.media)
        assertEquals("voice", req.media?.type)
        assertEquals(20, req.media?.duration)
    }

    @Test
    fun messageV2Client_domainToSendRequest_reply() {
        val client = createTestV2Client()
        val msg = Message(
            id = "m1", user = "alice", text = "reply", timestamp = 1000L,
            roomId = "r1", userId = "uuid-1",
            repliedToMessageId = "orig-1", repliedToText = "quoted"
        )
        val req = client.domainToSendRequest(msg)
        assertEquals("orig-1", req.replyToId)
    }

    @Test
    fun messageV2Client_domainToSendRequest_e2ee() {
        val client = createTestV2Client()
        val msg = Message(
            id = "m1", user = "alice", text = "", timestamp = 1000L,
            roomId = "r1", userId = "uuid-1",
            isE2EE = true, e2eePayload = "encrypted"
        )
        val req = client.domainToSendRequest(msg)
        assertTrue(req.isE2EE)
        assertEquals("encrypted", req.e2eePayload)
    }

    private fun createTestV2Client(): GrpcMessageV2Client {
        val users = listOf(
            UserInfoProto(username = "alice", userId = "uuid-1"),
            UserInfoProto(username = "bob", userId = "uuid-2"),
            UserInfoProto(username = "admin", userId = "uuid-admin")
        )
        return GrpcMessageV2Client(
            getChannel = { null },
            getUserId = { "uuid-1" },
            getUsername = { "alice" },
            messages = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
            allUsers = { users },
            deletedMessageHashes = mutableSetOf(),
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            appContext = { null },
            onReadReceipt = null
        )
    }
}
