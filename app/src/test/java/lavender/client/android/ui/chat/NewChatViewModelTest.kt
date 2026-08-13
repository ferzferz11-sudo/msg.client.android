package lavender.client.android.ui.chat

import org.junit.Assert.*
import org.junit.Test

class NewChatViewModelTest {

    @Test
    fun chatIntentData_defaults() {
        val data = ChatIntentData()
        assertEquals("", data.roomId)
        assertEquals("", data.username)
        assertEquals("", data.password)
        assertEquals("", data.chatName)
        assertFalse(data.isDirect)
        assertEquals("group", data.chatType)
        assertEquals("[]", data.participantsJson)
        assertEquals("", data.creator)
        assertEquals("", data.chatAvatarUrl)
        assertEquals("", data.chatFullAvatarUrl)
        assertFalse(data.isSecret)
        assertEquals("", data.serverAddress)
    }

    @Test
    fun chatIntentData_directChat() {
        val data = ChatIntentData(
            roomId = "room-123",
            username = "alice",
            chatName = "Bob",
            isDirect = true,
            chatType = "direct",
            participantsJson = """["alice", "bob"]"""
        )
        assertEquals("room-123", data.roomId)
        assertTrue(data.isDirect)
        assertEquals("direct", data.chatType)
    }

    @Test
    fun chatIntentData_secretChat() {
        val data = ChatIntentData(
            roomId = "secret-456",
            isSecret = true,
            chatType = "secret"
        )
        assertTrue(data.isSecret)
        assertEquals("secret", data.chatType)
    }

    @Test
    fun chatIntentData_copy() {
        val original = ChatIntentData(roomId = "r1", username = "alice")
        val updated = original.copy(chatName = "Updated Chat", isDirect = true)
        assertEquals("r1", updated.roomId)
        assertEquals("alice", updated.username)
        assertEquals("Updated Chat", updated.chatName)
        assertTrue(updated.isDirect)
    }

    @Test
    fun chatMetadataState_defaults() {
        val meta = ChatMetadataState()
        assertEquals("", meta.chatName)
        assertFalse(meta.isDirect)
        assertEquals("group", meta.chatType)
        assertEquals("[]", meta.participantsJson)
        assertEquals("", meta.creator)
        assertEquals("", meta.avatarUrl)
        assertEquals("", meta.fullAvatarUrl)
    }

    @Test
    fun chatMetadataState_withValues() {
        val meta = ChatMetadataState(
            chatName = "Team Chat",
            isDirect = false,
            chatType = "group",
            participantsJson = """["alice", "bob", "charlie"]""",
            creator = "alice",
            avatarUrl = "https://example.com/avatar.jpg",
            fullAvatarUrl = "https://example.com/full.jpg"
        )
        assertEquals("Team Chat", meta.chatName)
        assertEquals(3, meta.participantsJson.split(",").size)
        assertEquals("alice", meta.creator)
    }

    @Test
    fun chatMetadataState_copy() {
        val original = ChatMetadataState(chatName = "Old Name")
        val updated = original.copy(chatName = "New Name", isDirect = true)
        assertEquals("New Name", updated.chatName)
        assertTrue(updated.isDirect)
    }
}
