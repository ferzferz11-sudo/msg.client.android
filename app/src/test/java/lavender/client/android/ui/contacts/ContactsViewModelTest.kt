package lavender.client.android.ui.contacts

import org.junit.Assert.*
import org.junit.Test

class ContactsViewModelTest {

    @Test
    fun contactsUiState_defaults() {
        val state = ContactsUiState()
        assertFalse(state.isLoading)
        assertTrue(state.contacts.isEmpty())
        assertTrue(state.allUsers.isEmpty())
        assertNull(state.error)
        assertNull(state.chatCreated)
    }

    @Test
    fun contactsUiState_withContacts() {
        val state = ContactsUiState(
            contacts = listOf("alice", "bob", "charlie"),
            isLoading = false
        )
        assertEquals(3, state.contacts.size)
        assertEquals("alice", state.contacts[0])
        assertFalse(state.isLoading)
    }

    @Test
    fun contactsUiState_withError() {
        val state = ContactsUiState(error = "Network error")
        assertEquals("Network error", state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun contactsUiState_copy() {
        val original = ContactsUiState(contacts = listOf("alice"))
        val updated = original.copy(isLoading = true, error = "Loading failed")
        assertTrue(updated.isLoading)
        assertEquals("Loading failed", updated.error)
        assertEquals(listOf("alice"), updated.contacts)
    }

    @Test
    fun chatCreatedEvent_defaults() {
        val event = ChatCreatedEvent(
            chatId = "chat-123",
            chatName = "Test Chat",
            isDirect = true,
            participants = """["alice", "bob"]"""
        )
        assertEquals("chat-123", event.chatId)
        assertEquals("Test Chat", event.chatName)
        assertTrue(event.isDirect)
        assertEquals("alice, bob", event.participants.removePrefix("[").removeSuffix("]").replace("\"", ""))
        assertEquals("", event.creator)
    }

    @Test
    fun chatCreatedEvent_groupChat() {
        val event = ChatCreatedEvent(
            chatId = "group-456",
            chatName = "Team Chat",
            isDirect = false,
            participants = """["alice", "bob", "charlie"]""",
            creator = "alice"
        )
        assertFalse(event.isDirect)
        assertEquals("alice", event.creator)
    }

    @Test
    fun chatCreatedEvent_copy() {
        val original = ChatCreatedEvent(chatId = "c1", chatName = "Chat", isDirect = true, participants = "[]")
        val updated = original.copy(chatName = "Renamed Chat", creator = "admin")
        assertEquals("c1", updated.chatId)
        assertEquals("Renamed Chat", updated.chatName)
        assertEquals("admin", updated.creator)
    }
}
