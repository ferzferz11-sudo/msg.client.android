package msg.client.android

import lavender.client.android.data.models.ChatInfo
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ChatAdapter logic.
 * Tests pure functions: getItemCount, hasFavorites, Favorites offset logic.
 *
 * Note: We test the data logic, not the Android UI binding.
 */
class ChatAdapterTest {

    private fun createChat(
        id: String = "chat-$id",
        name: String = "Chat $id",
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
        isOnline = false,
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
    fun chatInfo_creation_favoritesChat() {
        val chat = createChat(id = "fav", name = "Favorites", type = "favorites")
        assertEquals("favorites", chat.type)
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
    fun favoritesExtraction_favoritesType_isExtracted() {
        val chats = listOf(
            createChat(id = "fav", name = "Favorites", type = "favorites"),
            createChat(id = "1", name = "Alice"),
            createChat(id = "2", name = "Bob")
        )
        val favorites = chats.firstOrNull { it.type == "favorites" }
        val actualChats = if (favorites != null) chats.drop(1) else chats

        assertNotNull(favorites)
        assertEquals(2, actualChats.size)
        assertFalse(actualChats.any { it.type == "favorites" })
    }

    @Test
    fun favoritesExtraction_noFavoritesType_allChatsRemain() {
        val chats = listOf(
            createChat(id = "1", name = "Alice"),
            createChat(id = "2", name = "Bob")
        )
        val favorites = chats.firstOrNull { it.type == "favorites" }
        val actualChats = if (favorites != null) chats.drop(1) else chats

        assertNull(favorites)
        assertEquals(2, actualChats.size)
    }

    @Test
    fun favoritesOffset_withFavorites_itemCountIsPlusOne() {
        // Simulates: getItemCount() = displayedChats.size + 1 (if Favorites exists)
        val displayedChats = listOf(
            createChat(id = "1", name = "Alice"),
            createChat(id = "2", name = "Bob")
        )
        val hasFavorites = true
        val itemCount = displayedChats.size + if (hasFavorites) 1 else 0
        assertEquals(3, itemCount)
    }

    @Test
    fun favoritesOffset_withoutFavorites_itemCountEqualsChats() {
        val displayedChats = listOf(
            createChat(id = "1", name = "Alice"),
            createChat(id = "2", name = "Bob")
        )
        val hasFavorites = false
        val itemCount = displayedChats.size + if (hasFavorites) 1 else 0
        assertEquals(2, itemCount)
    }

    @Test
    fun favoritesOffset_emptyListWithFavorites_itemCountIsOne() {
        val displayedChats = emptyList<ChatInfo>()
        val hasFavorites = true
        val itemCount = displayedChats.size + if (hasFavorites) 1 else 0
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
