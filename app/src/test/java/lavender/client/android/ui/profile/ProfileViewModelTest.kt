package lavender.client.android.ui.profile

import org.junit.Assert.*
import org.junit.Test

class ProfileViewModelTest {

    @Test
    fun profileData_defaults() {
        val data = ProfileViewModel.ProfileData()
        assertEquals("", data.username)
        assertEquals("", data.avatarUrl)
        assertEquals("", data.fullAvatarUrl)
        assertEquals("", data.bio)
        assertEquals("", data.status)
        assertFalse(data.isOnline)
        assertNull(data.lastSeenAt)
    }

    @Test
    fun profileData_withValues() {
        val data = ProfileViewModel.ProfileData(
            username = "ferz",
            avatarUrl = "https://example.com/avatar.jpg",
            fullAvatarUrl = "https://example.com/full.jpg",
            bio = "Developer",
            status = "online",
            isOnline = true
        )
        assertEquals("ferz", data.username)
        assertEquals("https://example.com/avatar.jpg", data.avatarUrl)
        assertEquals("https://example.com/full.jpg", data.fullAvatarUrl)
        assertEquals("Developer", data.bio)
        assertEquals("online", data.status)
        assertTrue(data.isOnline)
    }

    @Test
    fun profileData_copy() {
        val original = ProfileViewModel.ProfileData(username = "ferz", bio = "Old bio")
        val updated = original.copy(bio = "New bio", status = "busy")
        assertEquals("ferz", updated.username)
        assertEquals("New bio", updated.bio)
        assertEquals("busy", updated.status)
    }

    @Test
    fun groupData_defaults() {
        val data = ProfileViewModel.GroupData()
        assertEquals("", data.name)
        assertEquals("", data.avatarUrl)
        assertEquals("", data.fullAvatarUrl)
        assertEquals("", data.creator)
        assertTrue(data.participants.isEmpty())
        assertFalse(data.allowMembersToAdd)
    }

    @Test
    fun groupData_withParticipants() {
        val data = ProfileViewModel.GroupData(
            name = "Dev Team",
            participants = listOf("ferz", "alice", "bob"),
            allowMembersToAdd = true,
            creator = "ferz"
        )
        assertEquals("Dev Team", data.name)
        assertEquals(3, data.participants.size)
        assertTrue(data.participants.contains("ferz"))
        assertTrue(data.allowMembersToAdd)
        assertEquals("ferz", data.creator)
    }

    @Test
    fun groupData_copy_updatesName() {
        val original = ProfileViewModel.GroupData(name = "Old Name")
        val updated = original.copy(name = "New Name")
        assertEquals("New Name", updated.name)
    }

    @Test
    fun avatarUploadResult_defaults() {
        val result = ProfileViewModel.AvatarUploadResult()
        assertEquals("", result.thumbUrl)
        assertEquals("", result.fullUrl)
        assertEquals("", result.error)
    }

    @Test
    fun avatarUploadResult_success() {
        val result = ProfileViewModel.AvatarUploadResult(
            thumbUrl = "https://example.com/thumb.jpg",
            fullUrl = "https://example.com/full.jpg"
        )
        assertEquals("https://example.com/thumb.jpg", result.thumbUrl)
        assertEquals("https://example.com/full.jpg", result.fullUrl)
        assertEquals("", result.error)
    }

    @Test
    fun avatarUploadResult_error() {
        val result = ProfileViewModel.AvatarUploadResult(error = "Upload failed: 500")
        assertEquals("Upload failed: 500", result.error)
        assertEquals("", result.thumbUrl)
    }

    @Test
    fun contactFiltering_excludesExistingParticipants() {
        val allContacts = listOf("alice", "bob", "charlie", "dave")
        val currentParticipants = listOf("alice", "charlie")
        val available = allContacts.filter { it !in currentParticipants }
        assertEquals(2, available.size)
        assertTrue(available.contains("bob"))
        assertTrue(available.contains("dave"))
        assertFalse(available.contains("alice"))
        assertFalse(available.contains("charlie"))
    }

    @Test
    fun contactFiltering_emptyParticipants() {
        val allContacts = listOf("alice", "bob")
        val currentParticipants = emptyList<String>()
        val available = allContacts.filter { it !in currentParticipants }
        assertEquals(2, available.size)
    }

    @Test
    fun contactFiltering_noContacts() {
        val allContacts = emptyList<String>()
        val currentParticipants = listOf("alice")
        val available = allContacts.filter { it !in currentParticipants }
        assertTrue(available.isEmpty())
    }
}
