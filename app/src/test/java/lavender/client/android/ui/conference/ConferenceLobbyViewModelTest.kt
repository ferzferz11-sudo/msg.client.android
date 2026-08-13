package lavender.client.android.ui.conference

import org.junit.Assert.*
import org.junit.Test

class ConferenceLobbyViewModelTest {

    @Test
    fun conferenceLobbyUiState_defaults() {
        val state = ConferenceLobbyUiState()
        assertFalse(state.isLoading)
        assertEquals("", state.roomId)
        assertEquals("", state.topic)
        assertTrue(state.startTime > 0)
        assertFalse(state.isCreator)
        assertEquals("", state.conferenceCreatorId)
        assertFalse(state.isTopicManual)
        assertTrue(state.participants.isEmpty())
        assertTrue(state.invited.isEmpty())
        assertEquals(0, state.participantCount)
        assertTrue(state.avatarCache.isEmpty())
        assertTrue(state.isMicEnabled)
        assertTrue(state.isCameraEnabled)
        assertNull(state.error)
        assertNull(state.successMessage)
    }

    @Test
    fun conferenceLobbyUiState_withValues() {
        val state = ConferenceLobbyUiState(
            isLoading = true,
            roomId = "room-123",
            topic = "Team Meeting",
            isCreator = true,
            conferenceCreatorId = "user-1",
            isTopicManual = true,
            participants = listOf("alice", "bob"),
            invited = listOf("charlie"),
            participantCount = 2,
            avatarCache = mapOf("alice" to "https://example.com/alice.jpg"),
            isMicEnabled = false,
            isCameraEnabled = false
        )
        assertTrue(state.isLoading)
        assertEquals("room-123", state.roomId)
        assertEquals("Team Meeting", state.topic)
        assertTrue(state.isCreator)
        assertEquals("user-1", state.conferenceCreatorId)
        assertTrue(state.isTopicManual)
        assertEquals(2, state.participants.size)
        assertEquals(1, state.invited.size)
        assertEquals(2, state.participantCount)
        assertEquals(1, state.avatarCache.size)
        assertFalse(state.isMicEnabled)
        assertFalse(state.isCameraEnabled)
    }

    @Test
    fun conferenceLobbyUiState_copy() {
        val original = ConferenceLobbyUiState(roomId = "room-1")
        val updated = original.copy(
            topic = "New Topic",
            isMicEnabled = false,
            error = "Connection failed"
        )
        assertEquals("room-1", updated.roomId) // unchanged
        assertEquals("New Topic", updated.topic)
        assertFalse(updated.isMicEnabled)
        assertEquals("Connection failed", updated.error)
    }

    @Test
    fun conferenceLobbyUiState_participants() {
        val state = ConferenceLobbyUiState(
            participants = listOf("alice", "bob", "charlie"),
            participantCount = 3
        )
        assertEquals(3, state.participants.size)
        assertEquals(3, state.participantCount)
        assertTrue(state.participants.contains("alice"))
        assertTrue(state.participants.contains("bob"))
        assertTrue(state.participants.contains("charlie"))
    }

    @Test
    fun conferenceLobbyUiState_audioVideo() {
        val state = ConferenceLobbyUiState(
            isMicEnabled = true,
            isCameraEnabled = false
        )
        assertTrue(state.isMicEnabled)
        assertFalse(state.isCameraEnabled)

        val toggled = state.copy(isMicEnabled = !state.isMicEnabled)
        assertFalse(toggled.isMicEnabled)
        assertFalse(toggled.isCameraEnabled) // unchanged
    }
}
