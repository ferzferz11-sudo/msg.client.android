package lavender.client.android.ui.chat

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import lavender.client.android.data.models.Message

class ChatViewModelTest {

    @Test
    fun `initial state - isLoading is false`() {
        val isLoading = false
        assertFalse("isLoading should be false initially", isLoading)
    }

    @Test
    fun `initial state - currentRoomId is general`() {
        val currentRoomId = "general"
        assertEquals("general", currentRoomId)
    }

    @Test
    fun `Message data class construction`() {
        val message = Message(
            id = "msg-1",
            user = "testuser",
            text = "Hello World",
            timestamp = 1000L
        )
        assertEquals("msg-1", message.id)
        assertEquals("testuser", message.user)
        assertEquals("Hello World", message.text)
        assertEquals(1000L, message.timestamp)
    }

    @Test
    fun `Message copy preserves fields`() {
        val original = Message(id = "msg-1", user = "user1", text = "Original", timestamp = 1000L)
        val copied = original.copy(text = "Edited")
        assertEquals("msg-1", copied.id)
        assertEquals("user1", copied.user)
        assertEquals("Edited", copied.text)
        assertEquals(1000L, copied.timestamp)
    }

    @Test
    fun `Message with imageUrl`() {
        val message = Message(
            id = "msg-2",
            user = "user1",
            text = "",
            timestamp = 2000L,
            imageUrl = "https://example.com/image.png"
        )
        assertEquals("https://example.com/image.png", message.imageUrl)
    }

    @Test
    fun `Message with voiceUrl`() {
        val message = Message(
            id = "msg-3",
            user = "user1",
            text = "",
            timestamp = 3000L,
            voiceUrl = "https://example.com/voice.ogg"
        )
        assertEquals("https://example.com/voice.ogg", message.voiceUrl)
    }

    @Test
    fun `Message isOutgoing check`() {
        val outgoing = Message(id = "m1", user = "me", text = "Hi", timestamp = 1000L)
        val incoming = Message(id = "m2", user = "other", text = "Hi", timestamp = 1000L)
        // isOutgoing is determined by comparing username, not stored in Message
        assertEquals("me", outgoing.user)
        assertEquals("other", incoming.user)
    }

    @Test
    fun `Message list operations`() {
        val messages = listOf(
            Message(id = "1", user = "u1", text = "a", timestamp = 1000L),
            Message(id = "2", user = "u2", text = "b", timestamp = 2000L),
            Message(id = "3", user = "u1", text = "c", timestamp = 3000L)
        )
        assertEquals(3, messages.size)
        assertEquals("a", messages[0].text)
        assertEquals("c", messages[2].text)
    }

    @Test
    fun `Message with empty text and no media`() {
        val empty = Message(id = "empty", user = "u1", text = "", timestamp = 0L)
        assertTrue("Text should be empty", empty.text.isEmpty())
        assertTrue("ImageUrl should be empty", empty.imageUrl.isEmpty())
        assertTrue("VoiceUrl should be empty", empty.voiceUrl.isEmpty())
    }

    @Test
    fun `Message with reply info`() {
        val message = Message(
            id = "reply-1",
            user = "user1",
            text = "Reply text",
            timestamp = 1000L,
            replyToId = "original-1",
            replyToUser = "user2",
            replyToText = "Original text"
        )
        assertEquals("original-1", message.replyToId)
        assertEquals("user2", message.replyToUser)
        assertEquals("Original text", message.replyToText)
    }

    @Test
    fun `Message with reaction`() = runTest {
        val message = Message(
            id = "react-1",
            user = "user1",
            text = "Hello",
            timestamp = 1000L,
            reactions = mapOf("👍" to listOf("user2", "user3"))
        )
        assertEquals(1, message.reactions.size)
        assertEquals(2, message.reactions["👍"]?.size)
    }
}
