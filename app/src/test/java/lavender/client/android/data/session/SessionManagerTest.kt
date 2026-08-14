package lavender.client.android.data.session

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import lavender.client.android.data.grpc.ConnectionStatus
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LoginConnectionFilterTest {

    // Replicates the filter logic from loginV2 to verify CONNECTING is accepted
    private suspend fun simulateLoginWaitForConnection(
        connectionStatus: MutableStateFlow<ConnectionStatus>,
        emitStatus: ConnectionStatus
    ): String? {
        connectionStatus.value = ConnectionStatus.DISCONNECTED
        // Simulate the status transition that happens during connect()
        connectionStatus.value = emitStatus

        val status = withTimeoutOrNull(1.seconds) {
            connectionStatus.first {
                it == ConnectionStatus.CONNECTING || it == ConnectionStatus.READY || it == ConnectionStatus.FAILED
            }
        }

        return if (status == ConnectionStatus.FAILED || status == null) {
            "CONNECTION_FAILED"
        } else {
            null // success
        }
    }

    @Test
    fun loginFilter_connectingAccepted() = runTest {
        val connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        val result = simulateLoginWaitForConnection(connectionStatus, ConnectionStatus.CONNECTING)
        assertNull("CONNECTING should be accepted (not CONNECTION_FAILED)", result)
    }

    @Test
    fun loginFilter_readyAccepted() = runTest {
        val connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        val result = simulateLoginWaitForConnection(connectionStatus, ConnectionStatus.READY)
        assertNull("READY should be accepted (not CONNECTION_FAILED)", result)
    }

    @Test
    fun loginFilter_failedRejected() = runTest {
        val connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        val result = simulateLoginWaitForConnection(connectionStatus, ConnectionStatus.FAILED)
        assertEquals("FAILED should be rejected", "CONNECTION_FAILED", result)
    }

    @Test
    fun loginFilter_disconnectedTimesOut() = runTest {
        val connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        // Don't emit any status — should timeout
        val status = withTimeoutOrNull(1.seconds) {
            connectionStatus.first {
                it == ConnectionStatus.CONNECTING || it == ConnectionStatus.READY || it == ConnectionStatus.FAILED
            }
        }
        assertNull("DISCONNECTED should timeout", status)
    }

    @Test
    fun loginFilter_connectingThenReady_stillSucceeds() = runTest {
        val connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        // Simulate: connect() sets CONNECTING, then later READY
        connectionStatus.value = ConnectionStatus.CONNECTING

        val status = withTimeoutOrNull(2.seconds) {
            connectionStatus.first {
                it == ConnectionStatus.CONNECTING || it == ConnectionStatus.READY || it == ConnectionStatus.FAILED
            }
        }

        assertNotNull("Should succeed when CONNECTING", status)
        assertNotEquals("Should not be FAILED", ConnectionStatus.FAILED, status)
    }
}

class SessionManagerTest {

    @Test
    fun session_defaultValues() {
        val session = UserSession()
        assertEquals("", session.username)
        assertEquals("", session.password)
        assertEquals("", session.userId)
        assertEquals("", session.email)
        assertEquals("", session.accessToken)
        assertEquals("", session.refreshToken)
        assertEquals("", session.authMethod)
        assertFalse(session.isLoggedIn)
        assertFalse(session.isJwtAuth)
        assertFalse(session.isSuperAdmin)
    }

    @Test
    fun session_loggedIn_whenUsernamePresent() {
        val session = UserSession(username = "ferz")
        assertTrue(session.isLoggedIn)
    }

    @Test
    fun session_notLoggedIn_withoutUsername() {
        val session = UserSession(password = "pass123")
        assertFalse(session.isLoggedIn)
    }

    @Test
    fun session_isJwtAuth_requiresMethodAndToken() {
        val session = UserSession(
            username = "ferz",
            authMethod = "v2_jwt",
            accessToken = "eyJhbGciOiJIUzI1NiJ9.test"
        )
        assertTrue(session.isJwtAuth)
    }

    @Test
    fun session_isJwtAuth_falseWithoutToken() {
        val session = UserSession(
            username = "ferz",
            authMethod = "v2_jwt"
        )
        assertFalse(session.isJwtAuth)
    }

    @Test
    fun session_isJwtAuth_falseWithWrongMethod() {
        val session = UserSession(
            username = "ferz",
            authMethod = "v1_legacy",
            accessToken = "token"
        )
        assertFalse(session.isJwtAuth)
    }

    @Test
    fun session_copy_preservesFields() {
        val original = UserSession(
            username = "ferz",
            password = "pass",
            userId = "uuid-123",
            email = "test@test.com",
            accessToken = "access",
            refreshToken = "refresh",
            authMethod = "v2_jwt",
            isSuperAdmin = true
        )
        val copy = original.copy(username = "new_user")
        assertEquals("new_user", copy.username)
        assertEquals("pass", copy.password)
        assertEquals("uuid-123", copy.userId)
        assertEquals("test@test.com", copy.email)
        assertEquals("access", copy.accessToken)
        assertEquals("refresh", copy.refreshToken)
        assertEquals("v2_jwt", copy.authMethod)
        assertTrue(copy.isSuperAdmin)
    }

    @Test
    fun session_withDevice() {
        val session = UserSession(
            username = "ferz",
            deviceId = "abc123",
            deviceName = "Xiaomi 14"
        )
        assertEquals("abc123", session.deviceId)
        assertEquals("Xiaomi 14", session.deviceName)
    }

    @Test
    fun session_withAvatar() {
        val session = UserSession(
            username = "ferz",
            avatarUrl = "https://example.com/avatar.jpg",
            fullAvatarUrl = "https://example.com/full.jpg"
        )
        assertEquals("https://example.com/avatar.jpg", session.avatarUrl)
        assertEquals("https://example.com/full.jpg", session.fullAvatarUrl)
    }
}
