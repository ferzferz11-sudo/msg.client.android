package lavender.client.android.ui.admin

import org.junit.Assert.*
import org.junit.Test

class SuperAdminViewModelTest {

    @Test
    fun superAdminUiState_defaults() {
        val state = SuperAdminUiState()
        assertFalse(state.isLoading)
        assertTrue(state.adminUsers.isEmpty())
        assertTrue(state.allChats.isEmpty())
        assertEquals(Mode.USERS, state.currentMode)
        assertEquals("", state.currentCursor)
        assertTrue(state.hasMore)
        assertTrue(state.selectedUsernames.isEmpty())
        assertTrue(state.selectedChatIds.isEmpty())
        assertTrue(state.expandedUserSessions.isEmpty())
        assertTrue(state.expandedUsers.isEmpty())
        assertNull(state.error)
        assertNull(state.successMessage)
    }

    @Test
    fun superAdminUiState_withValues() {
        val state = SuperAdminUiState(
            isLoading = true,
            currentMode = Mode.GROUPS,
            currentCursor = "next-page-cursor",
            hasMore = false,
            selectedUsernames = setOf("alice", "bob"),
            selectedChatIds = setOf("chat-1"),
            expandedUsers = setOf("alice")
        )
        assertTrue(state.isLoading)
        assertEquals(Mode.GROUPS, state.currentMode)
        assertEquals("next-page-cursor", state.currentCursor)
        assertFalse(state.hasMore)
        assertEquals(2, state.selectedUsernames.size)
        assertTrue(state.selectedUsernames.contains("alice"))
        assertEquals(1, state.selectedChatIds.size)
        assertEquals(1, state.expandedUsers.size)
    }

    @Test
    fun superAdminUiState_copy() {
        val original = SuperAdminUiState(currentMode = Mode.USERS)
        val updated = original.copy(
            isLoading = true,
            currentMode = Mode.GROUPS,
            error = "Loading failed"
        )
        assertTrue(updated.isLoading)
        assertEquals(Mode.GROUPS, updated.currentMode)
        assertEquals("Loading failed", updated.error)
        assertEquals("", updated.currentCursor) // unchanged
    }

    @Test
    fun mode_values() {
        assertEquals(2, Mode.values().size)
        assertNotNull(Mode.USERS)
        assertNotNull(Mode.GROUPS)
    }

    @Test
    fun superAdminUiState_selectionToggle() {
        val state = SuperAdminUiState()
        val withSelection = state.copy(selectedUsernames = state.selectedUsernames + "alice")
        assertEquals(1, withSelection.selectedUsernames.size)
        assertTrue(withSelection.selectedUsernames.contains("alice"))

        val withoutSelection = withSelection.copy(
            selectedUsernames = withSelection.selectedUsernames - "alice"
        )
        assertTrue(withoutSelection.selectedUsernames.isEmpty())
    }

    @Test
    fun superAdminUiState_expandedSessions() {
        val sessions = listOf<lavender.client.android.data.proto.AdminUserSessionProto>()
        val state = SuperAdminUiState(
            expandedUserSessions = mapOf("alice" to sessions),
            expandedUsers = setOf("alice")
        )
        assertTrue(state.expandedUserSessions.containsKey("alice"))
        assertTrue(state.expandedUsers.contains("alice"))
    }
}
