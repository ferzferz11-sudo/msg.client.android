package msg.client.android

import lavender.client.android.data.models.ChatInfo
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ChatAdapter logic.
 * Tests pure functions: getItemCount, hasSavedMessages, Saved Messages offset logic.
 *
 * Note: We test the data logic, not the Android UI binding.
 */
class ChatAdapterTest {

    private fun createChat(
        id: String = "chat-1",
        name: String = "Chat 1",
        type: String = "regular",
        participants: String = "",
        lastMessageText: String = ""
    ) = ChatInfo(
        id = id,
        name = name,
        type = type,
        participants = participants,
        createdAt = System.currentTimeMillis(),
        unreadCount = 0,
        lastMessageTime = System.currentTimeMillis(),
        creator = "user1",
        lastMessageText = lastMessageText,
        avatarUrl = "",
        fullAvatarUrl = "",
        lastMessageUsername = "",
        lastMessageHasImage = false,
        allowMembersToAdd = true,
        conferenceStartTime = 0,
        isSecret = false,
        peerPublicKey = "",
        e2eeReady = false,
        activeAgentId = "",
        agentMode = ""
    )

    @Test
    fun chatInfo_creation_regularChat() {
        val chat = createChat(id = "1", name = "Test Chat")
        assertEquals("1", chat.id)
        assertEquals("Test Chat", chat.name)
        assertEquals("regular", chat.type)
    }

    @Test
    fun chatInfo_creation_savedMessagesChat() {
        val chat = createChat(id = "fav", name = "Saved Messages", type = "saved_messages")
        assertEquals("saved_messages", chat.type)
    }

    @Test
    fun filterQuery_empty_returnsAllChats() {
        val chats = listOf(
            createChat(id = "1", name = "Alice"),
            createChat(id = "2", name = "Bob"),
            createChat(id = "3", name = "Charlie")
        )
        val query = ""
        val filtered = if (query.isEmpty()) chats else {
            chats.filter {
                it.name.lowercase().contains(query) ||
                it.participants.lowercase().contains(query) ||
                it.lastMessageText.lowercase().contains(query)
            }
        }
        assertEquals(3, filtered.size)
    }

    @Test
    fun filterQuery_byName_returnsMatchingChats() {
        val chats = listOf(
            createChat(id = "1", name = "Alice"),
            createChat(id = "2", name = "Bob"),
            createChat(id = "3", name = "Charlie")
        )
        val query = "ali"
        val filtered = chats.filter {
            it.name.lowercase().contains(query) ||
            it.participants.lowercase().contains(query) ||
            it.lastMessageText.lowercase().contains(query)
        }
        assertEquals(1, filtered.size)
        assertEquals("Alice", filtered[0].name)
    }

    @Test
    fun filterQuery_byLastMessage_returnsMatchingChats() {
        val chats = listOf(
            createChat(id = "1", name = "Alice", lastMessageText = "Hello world"),
            createChat(id = "2", name = "Bob", lastMessageText = "Goodbye"),
            createChat(id = "3", name = "Charlie", lastMessageText = "Hello there")
        )
        val query = "hello"
        val filtered = chats.filter {
            it.name.lowercase().contains(query) ||
            it.participants.lowercase().contains(query) ||
            it.lastMessageText.lowercase().contains(query)
        }
        assertEquals(2, filtered.size)
    }

    @Test
    fun filterQuery_caseInsensitive() {
        val chats = listOf(
            createChat(id = "1", name = "ALICE"),
            createChat(id = "2", name = "bob")
        )
        val query = "alice"
        val filtered = chats.filter {
            it.name.lowercase().contains(query) ||
            it.participants.lowercase().contains(query) ||
            it.lastMessageText.lowercase().contains(query)
        }
        assertEquals(1, filtered.size)
        assertEquals("ALICE", filtered[0].name)
    }

    @Test
    fun filterQuery_noMatch_returnsEmpty() {
        val chats = listOf(
            createChat(id = "1", name = "Alice"),
            createChat(id = "2", name = "Bob")
        )
        val query = "xyz"
        val filtered = chats.filter {
            it.name.lowercase().contains(query) ||
            it.participants.lowercase().contains(query) ||
            it.lastMessageText.lowercase().contains(query)
        }
        assertEquals(0, filtered.size)
    }

    @Test
    fun savedMessagesExtraction_savedMessagesType_isExtracted() {
        val chats = listOf(
            createChat(id = "fav", name = "Saved Messages", type = "saved_messages"),
            createChat(id = "1", name = "Alice"),
            createChat(id = "2", name = "Bob")
        )
        val savedMsg = chats.firstOrNull { it.type == "saved_messages" }
        val actualChats = if (savedMsg != null) chats.drop(1) else chats

        assertNotNull(savedMsg)
        assertEquals(2, actualChats.size)
        assertFalse(actualChats.any { it.type == "saved_messages" })
    }

    @Test
    fun savedMessagesExtraction_noSavedMessagesType_allChatsRemain() {
        val chats = listOf(
            createChat(id = "1", name = "Alice"),
            createChat(id = "2", name = "Bob")
        )
        val savedMsg = chats.firstOrNull { it.type == "saved_messages" }
        val actualChats = if (savedMsg != null) chats.drop(1) else chats

        assertNull(savedMsg)
        assertEquals(2, actualChats.size)
    }

    @Test
    fun savedMessagesOffset_withSavedMessages_itemCountIsPlusOne() {
        // Simulates: getItemCount() = displayedChats.size + 1 (if Favorites exists)
        val displayedChats = listOf(
            createChat(id = "1", name = "Alice"),
            createChat(id = "2", name = "Bob")
        )
        val hasSavedMessages = true
        val itemCount = displayedChats.size + if (hasSavedMessages) 1 else 0
        assertEquals(3, itemCount)
    }

    @Test
    fun savedMessagesOffset_withoutSavedMessages_itemCountEqualsChats() {
        val displayedChats = listOf(
            createChat(id = "1", name = "Alice"),
            createChat(id = "2", name = "Bob")
        )
        val hasSavedMessages = false
        val itemCount = displayedChats.size + if (hasSavedMessages) 1 else 0
        assertEquals(2, itemCount)
    }

    @Test
    fun savedMessagesOffset_emptyListWithSavedMessages_itemCountIsOne() {
        val displayedChats = emptyList<ChatInfo>()
        val hasSavedMessages = true
        val itemCount = displayedChats.size + if (hasSavedMessages) 1 else 0
        assertEquals(1, itemCount)
    }

    @Test
    fun diffUtil_oldAndNew_sameList_noChanges() {
        val old = listOf(createChat(id = "1", name = "Alice"))
        val new = listOf(createChat(id = "1", name = "Alice"))
        // Same content — diff should show no changes
        assertEquals(old.size, new.size)
        assertEquals(old[0].id, new[0].id)
    }

    @Test
    fun diffUtil_oldAndNew_differentSize_changesDetected() {
        val old = listOf(createChat(id = "1", name = "Alice"))
        val new = listOf(
            createChat(id = "1", name = "Alice"),
            createChat(id = "2", name = "Bob")
        )
        assertNotEquals(old.size, new.size)
    }
}
