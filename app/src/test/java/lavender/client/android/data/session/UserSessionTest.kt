package lavender.client.android.data.session

import org.junit.Assert.*
import org.junit.Test

class UserSessionTest {

    @Test
    fun defaultState_isNotLoggedIn() {
        val session = UserSession()
        assertFalse(session.isLoggedIn)
        assertEquals("", session.userId)
        assertEquals("", session.username)
        assertEquals("", session.accessToken)
        assertEquals("", session.authMethod)
        assertFalse(session.isSuperAdmin)
    }

    @Test
    fun isLoggedIn_trueWhenUsernameSet() {
        val session = UserSession(username = "testuser")
        assertTrue(session.isLoggedIn)
    }

    @Test
    fun isLoggedIn_falseWhenUsernameEmpty() {
        val session = UserSession(userId = "123")
        assertFalse(session.isLoggedIn)
    }

    @Test
    fun isJwtAuth_trueWhenV2TokenPresent() {
        val session = UserSession(
            username = "testuser",
            accessToken = "jwt-token",
            authMethod = "v2_jwt"
        )
        assertTrue(session.isJwtAuth)
    }

    @Test
    fun isJwtAuth_falseWhenV1Legacy() {
        val session = UserSession(
            username = "testuser",
            authMethod = "v1_legacy"
        )
        assertFalse(session.isJwtAuth)
    }

    @Test
    fun isJwtAuth_falseWhenTokenEmpty() {
        val session = UserSession(
            username = "testuser",
            accessToken = "",
            authMethod = "v2_jwt"
        )
        assertFalse(session.isJwtAuth)
    }

    @Test
    fun isJwtAuth_falseWhenAuthMethodEmpty() {
        val session = UserSession(
            username = "testuser",
            accessToken = "jwt-token",
            authMethod = ""
        )
        assertFalse(session.isJwtAuth)
    }

    @Test
    fun copy_preservesAllFields() {
        val original = UserSession(
            userId = "user-123",
            username = "testuser",
            password = "secret",
            avatarUrl = "https://example.com/avatar.jpg",
            fullAvatarUrl = "https://example.com/full-avatar.jpg",
            isSuperAdmin = true,
            deviceId = "device-456",
            deviceName = "Pixel 7",
            email = "test@example.com",
            accessToken = "access-jwt",
            refreshToken = "refresh-jwt",
            authMethod = "v2_jwt"
        )
        val copied = original.copy()
        assertEquals(original, copied)
        assertEquals(original.userId, copied.userId)
        assertEquals(original.username, copied.username)
        assertEquals(original.isSuperAdmin, copied.isSuperAdmin)
        assertEquals(original.isJwtAuth, copied.isJwtAuth)
        assertTrue(copied.isLoggedIn)
    }

    @Test
    fun copy_canOverrideIndividualFields() {
        val original = UserSession(
            username = "testuser",
            accessToken = "old-token",
            authMethod = "v2_jwt"
        )
        val updated = original.copy(accessToken = "new-token", userId = "new-id")
        assertEquals("new-token", updated.accessToken)
        assertEquals("new-id", updated.userId)
        assertEquals("testuser", updated.username)
        assertEquals("v2_jwt", updated.authMethod)
        assertTrue(updated.isJwtAuth)
    }

    @Test
    fun equality_bothDefault() {
        assertEquals(UserSession(), UserSession())
    }

    @Test
    fun equality_differentUsernamesAreNotEqual() {
        val a = UserSession(username = "alice")
        val b = UserSession(username = "bob")
        assertNotEquals(a, b)
    }

    @Test
    fun equality_sameFieldsAreEqual() {
        val a = UserSession(username = "alice", userId = "1", accessToken = "tok", authMethod = "v2_jwt")
        val b = UserSession(username = "alice", userId = "1", accessToken = "tok", authMethod = "v2_jwt")
        assertEquals(a, b)
    }

    @Test
    fun hashCode_sameFieldsSameHash() {
        val a = UserSession(username = "alice", userId = "1")
        val b = UserSession(username = "alice", userId = "1")
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun toString_containsUsername() {
        val session = UserSession(username = "alice")
        assertTrue(session.toString().contains("alice"))
    }

    @Test
    fun isSuperAdmin_falseByDefault() {
        val session = UserSession(username = "user")
        assertFalse(session.isSuperAdmin)
    }

    @Test
    fun deviceId_emptyByDefault() {
        val session = UserSession()
        assertEquals("", session.deviceId)
        assertEquals("", session.deviceName)
    }

    @Test
    fun allFields_emptyMeansNotLoggedIn() {
        val session = UserSession(
            userId = "",
            username = "",
            password = "",
            avatarUrl = "",
            fullAvatarUrl = "",
            deviceId = "",
            deviceName = "",
            email = "",
            accessToken = "",
            refreshToken = "",
            authMethod = ""
        )
        assertFalse(session.isLoggedIn)
        assertFalse(session.isJwtAuth)
    }
}
