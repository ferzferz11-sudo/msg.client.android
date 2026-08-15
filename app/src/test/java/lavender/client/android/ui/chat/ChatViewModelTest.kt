package lavender.client.android.ui.chat

import org.junit.Assert.*
import org.junit.Test

class ChatViewModelTest {

    @Test
    fun chatMetadata_defaults() {
        val meta = ChatViewModel.ChatMetadata(
            chatName = "", isDirect = false, chatType = "",
            participantsJson = "", creator = "",
            avatarUrl = "", fullAvatarUrl = ""
        )
        assertEquals("", meta.chatName)
        assertFalse(meta.isDirect)
        assertEquals("", meta.chatType)
        assertEquals("", meta.participantsJson)
        assertEquals("", meta.creator)
        assertEquals("", meta.avatarUrl)
        assertEquals("", meta.fullAvatarUrl)
    }

    @Test
    fun chatMetadata_withValues() {
        val meta = ChatViewModel.ChatMetadata(
            chatName = "Dev Team", isDirect = false, chatType = "group",
            participantsJson = "[\"ferz\",\"alice\"]", creator = "ferz",
            avatarUrl = "https://example.com/thumb.jpg", fullAvatarUrl = "https://example.com/full.jpg"
        )
        assertEquals("Dev Team", meta.chatName)
        assertFalse(meta.isDirect)
        assertEquals("group", meta.chatType)
        assertEquals("[\"ferz\",\"alice\"]", meta.participantsJson)
        assertEquals("ferz", meta.creator)
        assertEquals("https://example.com/thumb.jpg", meta.avatarUrl)
        assertEquals("https://example.com/full.jpg", meta.fullAvatarUrl)
    }

    @Test
    fun chatMetadata_copy() {
        val original = ChatViewModel.ChatMetadata(
            chatName = "Old Name", isDirect = true, chatType = "direct",
            participantsJson = "[]", creator = "ferz",
            avatarUrl = "", fullAvatarUrl = ""
        )
        val updated = original.copy(chatName = "New Name", chatType = "group")
        assertEquals("New Name", updated.chatName)
        assertEquals("group", updated.chatType)
        assertTrue(updated.isDirect)
        assertEquals("ferz", updated.creator)
    }

    @Test
    fun chatMetadata_directChat() {
        val meta = ChatViewModel.ChatMetadata(
            chatName = "alice", isDirect = true, chatType = "direct",
            participantsJson = "[\"ferz\",\"alice\"]", creator = "",
            avatarUrl = "", fullAvatarUrl = ""
        )
        assertTrue(meta.isDirect)
        assertEquals("direct", meta.chatType)
    }

    @Test
    fun chatMetadata_secretChat() {
        val meta = ChatViewModel.ChatMetadata(
            chatName = "🔒 alice", isDirect = true, chatType = "direct",
            participantsJson = "[\"ferz\",\"alice\"]", creator = "",
            avatarUrl = "", fullAvatarUrl = ""
        )
        assertTrue(meta.isDirect)
        assertEquals("direct", meta.chatType)
        assertTrue(meta.chatName.startsWith("🔒"))
    }

    @Test
    fun chatMetadata_groupChat() {
        val meta = ChatViewModel.ChatMetadata(
            chatName = "Dev Team", isDirect = false, chatType = "group",
            participantsJson = "[\"ferz\",\"alice\",\"bob\"]", creator = "ferz",
            avatarUrl = "https://example.com/avatar.jpg", fullAvatarUrl = "https://example.com/full.jpg"
        )
        assertFalse(meta.isDirect)
        assertEquals("group", meta.chatType)
        assertEquals(3, meta.participantsJson.split(",").size)
    }

    @Test
    fun chatMetadata_conferenceChat() {
        val meta = ChatViewModel.ChatMetadata(
            chatName = "Conference Call", isDirect = false, chatType = "conference",
            participantsJson = "[\"ferz\",\"alice\"]", creator = "ferz",
            avatarUrl = "", fullAvatarUrl = ""
        )
        assertFalse(meta.isDirect)
        assertEquals("conference", meta.chatType)
    }

    @Test
    fun chatMetadata_emptyParticipants() {
        val meta = ChatViewModel.ChatMetadata(
            chatName = "Chat", isDirect = false, chatType = "group",
            participantsJson = "[]", creator = "",
            avatarUrl = "", fullAvatarUrl = ""
        )
        assertEquals("[]", meta.participantsJson)
    }

    @Test
    fun chatMetadata_equals() {
        val meta1 = ChatViewModel.ChatMetadata(
            chatName = "Dev Team", isDirect = false, chatType = "group",
            participantsJson = "[]", creator = "ferz",
            avatarUrl = "", fullAvatarUrl = ""
        )
        val meta2 = ChatViewModel.ChatMetadata(
            chatName = "Dev Team", isDirect = false, chatType = "group",
            participantsJson = "[]", creator = "ferz",
            avatarUrl = "", fullAvatarUrl = ""
        )
        assertEquals(meta1, meta2)
    }

    @Test
    fun chatMetadata_notEquals() {
        val meta1 = ChatViewModel.ChatMetadata(
            chatName = "Dev Team", isDirect = false, chatType = "group",
            participantsJson = "[]", creator = "ferz",
            avatarUrl = "", fullAvatarUrl = ""
        )
        val meta2 = ChatViewModel.ChatMetadata(
            chatName = "Other Team", isDirect = false, chatType = "group",
            participantsJson = "[]", creator = "ferz",
            avatarUrl = "", fullAvatarUrl = ""
        )
        assertNotEquals(meta1, meta2)
    }

    @Test
    fun chatMetadata_hashCode() {
        val meta1 = ChatViewModel.ChatMetadata(
            chatName = "Dev Team", isDirect = false, chatType = "group",
            participantsJson = "[]", creator = "ferz",
            avatarUrl = "", fullAvatarUrl = ""
        )
        val meta2 = ChatViewModel.ChatMetadata(
            chatName = "Dev Team", isDirect = false, chatType = "group",
            participantsJson = "[]", creator = "ferz",
            avatarUrl = "", fullAvatarUrl = ""
        )
        assertEquals(meta1.hashCode(), meta2.hashCode())
    }

    @Test
    fun chatMetadata_toString() {
        val meta = ChatViewModel.ChatMetadata(
            chatName = "Dev Team", isDirect = false, chatType = "group",
            participantsJson = "[]", creator = "ferz",
            avatarUrl = "", fullAvatarUrl = ""
        )
        val str = meta.toString()
        assertTrue(str.contains("Dev Team"))
        assertTrue(str.contains("group"))
        assertTrue(str.contains("ferz"))
    }

    @Test
    fun chatMetadata_copy_preservesOtherFields() {
        val original = ChatViewModel.ChatMetadata(
            chatName = "Old Name", isDirect = true, chatType = "direct",
            participantsJson = "[\"ferz\",\"alice\"]", creator = "ferz",
            avatarUrl = "https://example.com/thumb.jpg", fullAvatarUrl = "https://example.com/full.jpg"
        )
        val updated = original.copy(chatName = "New Name")
        assertEquals("New Name", updated.chatName)
        assertTrue(updated.isDirect)
        assertEquals("direct", updated.chatType)
        assertEquals("[\"ferz\",\"alice\"]", updated.participantsJson)
        assertEquals("ferz", updated.creator)
        assertEquals("https://example.com/thumb.jpg", updated.avatarUrl)
        assertEquals("https://example.com/full.jpg", updated.fullAvatarUrl)
    }

    @Test
    fun chatMetadata_copy_updatesMultipleFields() {
        val original = ChatViewModel.ChatMetadata(
            chatName = "Old Name", isDirect = true, chatType = "direct",
            participantsJson = "[]", creator = "ferz",
            avatarUrl = "", fullAvatarUrl = ""
        )
        val updated = original.copy(
            chatName = "New Name",
            isDirect = false,
            chatType = "group",
            participantsJson = "[\"ferz\",\"alice\"]"
        )
        assertEquals("New Name", updated.chatName)
        assertFalse(updated.isDirect)
        assertEquals("group", updated.chatType)
        assertEquals("[\"ferz\",\"alice\"]", updated.participantsJson)
    }

    @Test
    fun chatMetadata_savedMessagesRoom() {
        val meta = ChatViewModel.ChatMetadata(
            chatName = "Saved Messages", isDirect = false, chatType = "saved_messages",
            participantsJson = "[]", creator = "",
            avatarUrl = "", fullAvatarUrl = ""
        )
        assertEquals("saved_messages", meta.chatType)
        assertFalse(meta.isDirect)
    }

    @Test
    fun chatMetadata_generalRoom() {
        val meta = ChatViewModel.ChatMetadata(
            chatName = "General", isDirect = false, chatType = "general",
            participantsJson = "[]", creator = "",
            avatarUrl = "", fullAvatarUrl = ""
        )
        assertEquals("general", meta.chatType)
        assertFalse(meta.isDirect)
    }
}
