package lavender.client.android.ui.profile

import org.junit.Assert.*
import org.junit.Test

class EditProfileViewModelTest {

    @Test
    fun profileUiState_defaults() {
        val state = ProfileUiState()
        assertFalse(state.isLoading)
        assertNull(state.profile)
        assertEquals("", state.avatarUrl)
        assertEquals("", state.fullAvatarUrl)
        assertEquals("", state.companyId)
        assertEquals("", state.companyName)
        assertEquals("", state.companyPosition)
        assertEquals("", state.companyLogoUrl)
        assertFalse(state.hasMultipleCompanies)
        assertEquals(0, state.companyCount)
        assertNull(state.error)
        assertNull(state.successMessage)
    }

    @Test
    fun profileUiState_withValues() {
        val state = ProfileUiState(
            isLoading = true,
            avatarUrl = "https://example.com/avatar.jpg",
            companyId = "comp-123",
            companyName = "Test Company",
            companyPosition = "Manager",
            hasMultipleCompanies = true,
            companyCount = 3
        )
        assertTrue(state.isLoading)
        assertEquals("https://example.com/avatar.jpg", state.avatarUrl)
        assertEquals("comp-123", state.companyId)
        assertEquals("Test Company", state.companyName)
        assertEquals("Manager", state.companyPosition)
        assertTrue(state.hasMultipleCompanies)
        assertEquals(3, state.companyCount)
    }

    @Test
    fun profileUiState_copy() {
        val original = ProfileUiState(companyName = "Original")
        val updated = original.copy(
            companyName = "Updated",
            error = "Some error",
            isLoading = true
        )
        assertEquals("Updated", updated.companyName)
        assertEquals("Some error", updated.error)
        assertTrue(updated.isLoading)
        assertEquals("", updated.companyId) // unchanged
    }

    @Test
    fun avatarUploadState_defaults() {
        val state = AvatarUploadState()
        assertFalse(state.isUploading)
        assertEquals(0f, state.progress, 0.001f)
        assertNull(state.error)
    }

    @Test
    fun avatarUploadState_uploading() {
        val state = AvatarUploadState(isUploading = true, progress = 0.5f)
        assertTrue(state.isUploading)
        assertEquals(0.5f, state.progress, 0.001f)
    }

    @Test
    fun avatarUploadState_error() {
        val state = AvatarUploadState(error = "Upload failed", isUploading = false)
        assertEquals("Upload failed", state.error)
        assertFalse(state.isUploading)
    }

    @Test
    fun avatarUploadState_copy() {
        val original = AvatarUploadState()
        val uploading = original.copy(isUploading = true, progress = 0.75f)
        assertTrue(uploading.isUploading)
        assertEquals(0.75f, uploading.progress, 0.001f)
        assertNull(uploading.error)
    }
}
