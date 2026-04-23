package msg.client.android

import com.google.protobuf.Timestamp
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.Reaction
import lavender.client.android.data.proto.MessageProto
import lavender.client.android.data.proto.ProtoUtils
import lavender.client.android.data.proto.ReactionProto
import org.junit.Test

import org.junit.Assert.*

/**
 * Unit tests for ProtoUtils
 * Tests conversion between Message model and MessageProto
 */
class ProtoUtilsTest {

    @Test
    fun createMessageProto_convertsAllFields() {
        val message = Message(
            id = "test-id-123",
            user = "testuser",
            text = "Hello, World!",
            timestamp = 1713926400000L, // 2024-04-24 12:00:00 UTC
            reactions = listOf(Reaction("user1", "👍"), Reaction("user2", "❤️")),
            repliedToMessageId = "reply-id-456",
            repliedToUser = "replyuser",
            repliedToText = "Original message",
            roomId = "general",
            isRead = true,
            avatarUrl = "https://example.com/avatar.jpg",
            imageUrl = "https://example.com/image.jpg",
            edited = true
        )

        val proto = ProtoUtils.createMessageProto(message)

        assertEquals("test-id-123", proto.id)
        assertEquals("testuser", proto.user)
        assertEquals("Hello, World!", proto.text)
        assertNotNull(proto.createdAt)
        assertEquals(1713926400L, proto.createdAt!!.seconds)
        // Just check nanos is in valid range
        assertTrue(proto.createdAt!!.nanos >= 0)
        assertTrue(proto.createdAt!!.nanos < 1000000000)
        assertEquals(2, proto.reactions.size)
        assertEquals("reply-id-456", proto.repliedToMessageId)
        assertEquals("replyuser", proto.repliedToUser)
        assertEquals("Original message", proto.repliedToText)
        assertEquals("general", proto.roomId)
        assertEquals(true, proto.isRead)
        assertEquals("https://example.com/avatar.jpg", proto.avatarUrl)
        assertEquals("https://example.com/image.jpg", proto.imageUrl)
        assertEquals(true, proto.edited)
    }

    @Test
    fun createMessageFromProto_convertsAllFields() {
        val timestamp = Timestamp.newBuilder()
            .setSeconds(1713926400L)
            .setNanos(0)
            .build()

        val reaction1 = ReactionProto(user = "user1", emoji = "👍")
        val reaction2 = ReactionProto(user = "user2", emoji = "❤️")

        val proto = MessageProto(
            id = "test-id-123",
            user = "testuser",
            text = "Hello, World!",
            createdAt = timestamp,
            reactions = listOf(reaction1, reaction2),
            repliedToMessageId = "reply-id-456",
            repliedToUser = "replyuser",
            repliedToText = "Original message",
            roomId = "general",
            isRead = true,
            avatarUrl = "https://example.com/avatar.jpg",
            imageUrl = "https://example.com/image.jpg",
            edited = true
        )

        val message = ProtoUtils.createMessageFromProto(proto)

        assertEquals("test-id-123", message.id)
        assertEquals("testuser", message.user)
        assertEquals("Hello, World!", message.text)
        assertEquals(1713926400000L, message.timestamp)
        assertEquals(2, message.reactions.size)
        assertEquals("reply-id-456", message.repliedToMessageId)
        assertEquals("replyuser", message.repliedToUser)
        assertEquals("Original message", message.repliedToText)
        assertEquals("general", message.roomId)
        assertEquals(true, message.isRead)
        assertEquals("https://example.com/avatar.jpg", message.avatarUrl)
        assertEquals("https://example.com/image.jpg", message.imageUrl)
        assertEquals(true, message.edited)
    }

    @Test
    fun createMessageFromProto_handlesNullTimestamp() {
        val proto = MessageProto(
            id = "test-id",
            user = "testuser",
            text = "Test message",
            createdAt = null
        )

        val message = ProtoUtils.createMessageFromProto(proto)

        // Should use current timestamp when proto.createdAt is null
        assertNotNull(message.timestamp)
        assertTrue(message.timestamp > 0)
    }

    @Test
    fun messageToProtoRoundTrip_preservesData() {
        val originalMessage = Message(
            id = "roundtrip-id",
            user = "roundtrip-user",
            text = "Round trip test",
            timestamp = 1713926400000L,
            reactions = listOf(Reaction("u1", "😀")),
            repliedToMessageId = "reply-id",
            repliedToUser = "reply-user",
            repliedToText = "Reply text",
            roomId = "test-room",
            isRead = false,
            avatarUrl = "avatar.jpg",
            imageUrl = "image.jpg",
            edited = false
        )

        val proto = ProtoUtils.createMessageProto(originalMessage)
        val convertedBack = ProtoUtils.createMessageFromProto(proto)

        assertEquals(originalMessage.id, convertedBack.id)
        assertEquals(originalMessage.user, convertedBack.user)
        assertEquals(originalMessage.text, convertedBack.text)
        // Timestamp should be preserved within millisecond precision
        val timeDiff = Math.abs(originalMessage.timestamp - convertedBack.timestamp)
        assertTrue("Timestamp difference: $timeDiff", timeDiff < 1000)
        assertEquals(originalMessage.reactions.size, convertedBack.reactions.size)
        if (originalMessage.reactions.isNotEmpty()) {
            assertEquals(originalMessage.reactions[0].user, convertedBack.reactions[0].user)
            assertEquals(originalMessage.reactions[0].emoji, convertedBack.reactions[0].emoji)
        }
        assertEquals(originalMessage.repliedToMessageId, convertedBack.repliedToMessageId)
        assertEquals(originalMessage.repliedToUser, convertedBack.repliedToUser)
        assertEquals(originalMessage.repliedToText, convertedBack.repliedToText)
        assertEquals(originalMessage.roomId, convertedBack.roomId)
        assertEquals(originalMessage.isRead, convertedBack.isRead)
        assertEquals(originalMessage.avatarUrl, convertedBack.avatarUrl)
        assertEquals(originalMessage.imageUrl, convertedBack.imageUrl)
        assertEquals(originalMessage.edited, convertedBack.edited)
    }

    @Test
    fun getCurrentTimestamp_returnsValidTimestamp() {
        val timestamp = ProtoUtils.getCurrentTimestamp()

        assertNotNull(timestamp)
        assertTrue(timestamp.seconds > 0)
        assertTrue(timestamp.nanos >= 0)
        assertTrue(timestamp.nanos < 1000000000)
    }

    @Test
    fun createMessageProto_handlesEmptyReactions() {
        val message = Message(
            id = "test-id",
            user = "testuser",
            text = "Test",
            timestamp = 1713926400000L,
            reactions = emptyList(),
            repliedToMessageId = "",
            repliedToUser = "",
            repliedToText = "",
            roomId = "general",
            isRead = false,
            avatarUrl = "",
            imageUrl = "",
            edited = false
        )

        val proto = ProtoUtils.createMessageProto(message)

        assertEquals(0, proto.reactions.size)
    }

    @Test
    fun createMessageFromProto_handlesEmptyReactions() {
        val timestamp = Timestamp.newBuilder()
            .setSeconds(1713926400L)
            .setNanos(0)
            .build()

        val proto = MessageProto(
            id = "test-id",
            user = "testuser",
            text = "Test",
            createdAt = timestamp,
            reactions = emptyList()
        )

        val message = ProtoUtils.createMessageFromProto(proto)

        assertEquals(0, message.reactions.size)
    }
}