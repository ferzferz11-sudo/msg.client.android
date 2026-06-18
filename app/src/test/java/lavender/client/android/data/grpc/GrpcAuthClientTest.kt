package lavender.client.android.data.grpc

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import io.mockk.*
import lavender.client.android.data.proto.*
import java.util.concurrent.atomic.AtomicReference

class GrpcAuthClientTest {

    private lateinit var channel: ManagedChannel
    private lateinit var connectionStatus: MutableStateFlow<ConnectionStatus>
    private lateinit var authStatus: MutableStateFlow<String?>
    private var authFailureFlag = false
    private lateinit var client: GrpcAuthClient

    @Before
    fun setup() {
        channel = mockk()
        connectionStatus = MutableStateFlow(ConnectionStatus.READY)
        authStatus = MutableStateFlow(null)
        authFailureFlag = false
        client = GrpcAuthClient(
            getChannel = { channel },
            connectionStatus = connectionStatus,
            authStatus = authStatus,
            setAuthFailure = { authFailureFlag = it }
        )
    }

    @Test
    fun signInV2_success_returnsToken() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(AuthResponseV2Proto(success = true, accessToken = "test-access-token", refreshToken = "test-refresh-token"))
        }

        val resultRef = AtomicReference<AuthResponseV2Proto?>()
        val errorRef = AtomicReference<String?>()

        client.signInV2("testuser", "testpass", "device-123", "Test Device") { res, err ->
            resultRef.set(res); errorRef.set(err)
        }

        assertNotNull("Result should not be null", resultRef.get())
        assertTrue("Success should be true", resultRef.get()!!.success)
        assertEquals("Access token", "test-access-token", resultRef.get()!!.accessToken)
        assertEquals("Refresh token", "test-refresh-token", resultRef.get()!!.refreshToken)
        assertNull("Error should be null", errorRef.get())
    }

    @Test
    fun signInV2_wrongPassword_returnsError() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(AuthResponseV2Proto(success = false, message = "Invalid password"))
        }

        val resultRef = AtomicReference<AuthResponseV2Proto?>()
        val errorRef = AtomicReference<String?>()

        client.signInV2("testuser", "wrongpass", "device-123", "Test Device") { res, err ->
            resultRef.set(res); errorRef.set(err)
        }

        assertNull("Result should be null on failure", resultRef.get())
        assertEquals("Error message", "Invalid password", errorRef.get())
    }

    @Test
    fun signInV2_nullChannel_returnsNotConnected() = runTest {
        val nullClient = GrpcAuthClient(
            getChannel = { null }, connectionStatus = connectionStatus,
            authStatus = authStatus, setAuthFailure = { authFailureFlag = it }
        )
        val resultRef = AtomicReference<AuthResponseV2Proto?>()
        val errorRef = AtomicReference<String?>()

        nullClient.signInV2("testuser", "testpass", "device-123", "Test Device") { res, err ->
            resultRef.set(res); errorRef.set(err)
        }

        assertNull("Result should be null", resultRef.get())
        assertEquals("Error should be 'Not connected'", "Not connected", errorRef.get())
    }

    @Test
    fun signInV2_serverError_returnsError() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onClose(Status.INTERNAL.withDescription("Server error"), Metadata())
        }

        val resultRef = AtomicReference<AuthResponseV2Proto?>()
        val errorRef = AtomicReference<String?>()

        client.signInV2("testuser", "testpass", "device-123", "Test Device") { res, err ->
            resultRef.set(res); errorRef.set(err)
        }

        assertNull("Result should be null on server error", resultRef.get())
        assertNotNull("Error should not be null", errorRef.get())
        assertTrue("Error should contain 'Server error'", errorRef.get()!!.contains("Server error"))
    }

    @Test
    fun signInV2_emptyUsername_sendsRequest() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(AuthResponseV2Proto(success = false, message = "Username is required"))
        }

        val resultRef = AtomicReference<AuthResponseV2Proto?>()
        val errorRef = AtomicReference<String?>()

        client.signInV2("", "testpass", "device-123", "Test Device") { res, err ->
            resultRef.set(res); errorRef.set(err)
        }

        assertNull("Result should be null", resultRef.get())
        assertEquals("Error message", "Username is required", errorRef.get())
    }

    @Test
    fun signUpV2_success_returnsToken() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(AuthResponseV2Proto(success = true, accessToken = "new-access-token", refreshToken = "new-refresh-token"))
        }

        val resultRef = AtomicReference<AuthResponseV2Proto?>()
        val errorRef = AtomicReference<String?>()

        client.signUpV2("newuser", "newpass", "new@example.com", "device-456", "Test Device") { res, err ->
            resultRef.set(res); errorRef.set(err)
        }

        assertNotNull("Result should not be null", resultRef.get())
        assertTrue("Success should be true", resultRef.get()!!.success)
        assertEquals("Access token", "new-access-token", resultRef.get()!!.accessToken)
        assertNull("Error should be null", errorRef.get())
    }

    @Test
    fun signUpV2_duplicateUsername_returnsError() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(AuthResponseV2Proto(success = false, message = "Username already exists"))
        }

        val resultRef = AtomicReference<AuthResponseV2Proto?>()
        val errorRef = AtomicReference<String?>()

        client.signUpV2("existinguser", "pass", "user@example.com", "device-789", "Test Device") { res, err ->
            resultRef.set(res); errorRef.set(err)
        }

        assertNull("Result should be null on duplicate", resultRef.get())
        assertEquals("Error message", "Username already exists", errorRef.get())
    }

    @Test
    fun refreshToken_success_returnsNewTokens() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(RefreshTokenResponseProto(accessToken = "refreshed-access", refreshToken = "refreshed-refresh"))
        }

        val resultRef = AtomicReference<RefreshTokenResponseProto?>()
        val errorRef = AtomicReference<String?>()

        client.refreshToken("old-refresh-token") { res, err ->
            resultRef.set(res); errorRef.set(err)
        }

        assertNotNull("Result should not be null", resultRef.get())
        assertEquals("Access token", "refreshed-access", resultRef.get()!!.accessToken)
        assertEquals("Refresh token", "refreshed-refresh", resultRef.get()!!.refreshToken)
        assertNull("Error should be null", errorRef.get())
    }

    @Test
    fun signOut_success_returnsTrue() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(SimpleAuthResponseProto(success = true, message = "Signed out"))
        }

        val resultRef = AtomicReference<Boolean?>()
        val errorRef = AtomicReference<String?>()

        client.signOut("some-token") { s, err ->
            resultRef.set(s); errorRef.set(err)
        }

        assertTrue("Sign out should succeed", resultRef.get())
        assertNull("Error should be null", errorRef.get())
    }

    @Test
    fun revokeDevice_success_returnsTrue() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>()
        every { channel.newCall<Any, Any>(any(), any()) } returns mockCall
        every { mockCall.start(any(), any()) } answers {
            @Suppress("UNCHECKED_CAST")
            firstArg<ClientCall.Listener<Any>>()
                .onMessage(SimpleAuthResponseProto(success = true, message = "Device revoked"))
        }

        val resultRef = AtomicReference<Boolean?>()
        val errorRef = AtomicReference<String?>()

        client.revokeDevice("device-to-revoke") { s, err ->
            resultRef.set(s); errorRef.set(err)
        }

        assertTrue("Revoke should succeed", resultRef.get())
        assertNull("Error should be null", errorRef.get())
    }
}
