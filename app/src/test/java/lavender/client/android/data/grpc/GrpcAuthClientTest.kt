package lavender.client.android.data.grpc

import io.grpc.CallOptions
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import lavender.client.android.data.proto.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import io.mockk.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Unit-тесты для GrpcAuthClient.
 *
 * Тестируем: signInV2, signUpV2, refreshToken, signOut, revokeDevice.
 * Мокаем: ManagedChannel, ClientCall через MockK.
 */
class GrpcAuthClientTest {

    private lateinit var channel: ManagedChannel
    private lateinit var connectionStatus: MutableStateFlow<ConnectionStatus>
    private lateinit var authStatus: MutableStateFlow<String?>
    private var authFailureFlag = false
    private lateinit var client: GrpcAuthClient

    @Before
    fun setup() {
        channel = mockk(relaxed = true)
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

    // ====== signInV2 ======

    @Test
    fun signInV2_success_returnsToken() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every {
            channel.newCall<Any, Any>(any<MethodDescriptor<Any, Any>>(), any<CallOptions>())
        } returns mockCall

        every {
            mockCall.start(any<ClientCall.Listener<Any>>(), any<Metadata>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val response = AuthResponseV2Proto(
                success = true,
                accessToken = "test-access-token",
                refreshToken = "test-refresh-token"
            )
            listener.onMessage(response)
            listener.onClose(Status.OK, Metadata())
        }

        val resultRef = AtomicReference<AuthResponseV2Proto?>()
        val errorRef = AtomicReference<String?>()

        client.signInV2(
            username = "testuser",
            password = "testpass",
            deviceId = "device-123",
            deviceName = "Test Device",
            callback = { res, err ->
                resultRef.set(res)
                errorRef.set(err)
            }
        )

        val result = resultRef.get()
        val error = errorRef.get()
        assertNotNull("Result should not be null", result)
        assertTrue("Success should be true", result!!.success)
        assertEquals("Access token", "test-access-token", result!!.accessToken)
        assertEquals("Refresh token", "test-refresh-token", result!!.refreshToken)
        assertNull("Error should be null", error)
    }

    @Test
    fun signInV2_wrongPassword_returnsError() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every {
            channel.newCall<Any, Any>(any<MethodDescriptor<Any, Any>>(), any<CallOptions>())
        } returns mockCall

        every {
            mockCall.start(any<ClientCall.Listener<Any>>(), any<Metadata>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val response = AuthResponseV2Proto(
                success = false,
                message = "Invalid password"
            )
            listener.onMessage(response)
            listener.onClose(Status.OK, Metadata())
        }

        val resultRef = AtomicReference<AuthResponseV2Proto?>()
        val errorRef = AtomicReference<String?>()

        client.signInV2(
            username = "testuser",
            password = "wrongpass",
            deviceId = "device-123",
            deviceName = "Test Device",
            callback = { res, err ->
                resultRef.set(res)
                errorRef.set(err)
            }
        )

        val result = resultRef.get()
        val error = errorRef.get()
        assertNull("Result should be null on failure", result)
        assertNotNull("Error should not be null", error)
        assertEquals("Error message", "Invalid password", error)
    }

    @Test
    fun signInV2_nullChannel_returnsNotConnected() = runTest {
        val clientWithNullChannel = GrpcAuthClient(
            getChannel = { null },
            connectionStatus = connectionStatus,
            authStatus = authStatus,
            setAuthFailure = { authFailureFlag = it }
        )

        val resultRef = AtomicReference<AuthResponseV2Proto?>()
        val errorRef = AtomicReference<String?>()

        clientWithNullChannel.signInV2(
            username = "testuser",
            password = "testpass",
            deviceId = "device-123",
            deviceName = "Test Device",
            callback = { res, err ->
                resultRef.set(res)
                errorRef.set(err)
            }
        )

        val result = resultRef.get()
        val error = errorRef.get()
        assertNull("Result should be null when channel is null", result)
        assertEquals("Error should be 'Not connected'", "Not connected", error)
    }

    @Test
    fun signInV2_serverError_returnsError() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every {
            channel.newCall<Any, Any>(any<MethodDescriptor<Any, Any>>(), any<CallOptions>())
        } returns mockCall

        every {
            mockCall.start(any<ClientCall.Listener<Any>>(), any<Metadata>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            listener.onClose(Status.INTERNAL.withDescription("Server error"), Metadata())
        }

        val resultRef = AtomicReference<AuthResponseV2Proto?>()
        val errorRef = AtomicReference<String?>()

        client.signInV2(
            username = "testuser",
            password = "testpass",
            deviceId = "device-123",
            deviceName = "Test Device",
            callback = { res, err ->
                resultRef.set(res)
                errorRef.set(err)
            }
        )

        val result = resultRef.get()
        val error = errorRef.get()
        assertNull("Result should be null on server error", result)
        assertNotNull("Error should not be null", error)
        assertTrue("Error should contain 'Server error'", error!!.contains("Server error"))
    }

    @Test
    fun signInV2_emptyUsername_sendsRequest() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every {
            channel.newCall<Any, Any>(any<MethodDescriptor<Any, Any>>(), any<CallOptions>())
        } returns mockCall

        every {
            mockCall.start(any<ClientCall.Listener<Any>>(), any<Metadata>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val response = AuthResponseV2Proto(
                success = false,
                message = "Username is required"
            )
            listener.onMessage(response)
            listener.onClose(Status.OK, Metadata())
        }

        val resultRef = AtomicReference<AuthResponseV2Proto?>()
        val errorRef = AtomicReference<String?>()

        client.signInV2(
            username = "",
            password = "testpass",
            deviceId = "device-123",
            deviceName = "Test Device",
            callback = { res, err ->
                resultRef.set(res)
                errorRef.set(err)
            }
        )

        val result = resultRef.get()
        val error = errorRef.get()
        assertNull("Result should be null", result)
        assertEquals("Error message", "Username is required", error)
    }

    // ====== signUpV2 ======

    @Test
    fun signUpV2_success_returnsToken() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every {
            channel.newCall<Any, Any>(any<MethodDescriptor<Any, Any>>(), any<CallOptions>())
        } returns mockCall

        every {
            mockCall.start(any<ClientCall.Listener<Any>>(), any<Metadata>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val response = AuthResponseV2Proto(
                success = true,
                accessToken = "new-access-token",
                refreshToken = "new-refresh-token"
            )
            listener.onMessage(response)
            listener.onClose(Status.OK, Metadata())
        }

        val resultRef = AtomicReference<AuthResponseV2Proto?>()
        val errorRef = AtomicReference<String?>()

        client.signUpV2(
            username = "newuser",
            password = "newpass",
            email = "new@example.com",
            deviceId = "device-456",
            deviceName = "Test Device",
            callback = { res, err ->
                resultRef.set(res)
                errorRef.set(err)
            }
        )

        val result = resultRef.get()
        val error = errorRef.get()
        assertNotNull("Result should not be null", result)
        assertTrue("Success should be true", result!!.success)
        assertEquals("Access token", "new-access-token", result!!.accessToken)
        assertNull("Error should be null", error)
    }

    @Test
    fun signUpV2_duplicateUsername_returnsError() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every {
            channel.newCall<Any, Any>(any<MethodDescriptor<Any, Any>>(), any<CallOptions>())
        } returns mockCall

        every {
            mockCall.start(any<ClientCall.Listener<Any>>(), any<Metadata>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val response = AuthResponseV2Proto(
                success = false,
                message = "Username already exists"
            )
            listener.onMessage(response)
            listener.onClose(Status.OK, Metadata())
        }

        val resultRef = AtomicReference<AuthResponseV2Proto?>()
        val errorRef = AtomicReference<String?>()

        client.signUpV2(
            username = "existinguser",
            password = "pass",
            email = "user@example.com",
            deviceId = "device-789",
            deviceName = "Test Device",
            callback = { res, err ->
                resultRef.set(res)
                errorRef.set(err)
            }
        )

        val result = resultRef.get()
        val error = errorRef.get()
        assertNull("Result should be null on duplicate", result)
        assertEquals("Error message", "Username already exists", error)
    }

    // ====== refreshToken ======

    @Test
    fun refreshToken_success_returnsNewTokens() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every {
            channel.newCall<Any, Any>(any<MethodDescriptor<Any, Any>>(), any<CallOptions>())
        } returns mockCall

        every {
            mockCall.start(any<ClientCall.Listener<Any>>(), any<Metadata>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val response = RefreshTokenResponseProto(
                accessToken = "refreshed-access",
                refreshToken = "refreshed-refresh"
            )
            listener.onMessage(response)
            listener.onClose(Status.OK, Metadata())
        }

        val resultRef = AtomicReference<RefreshTokenResponseProto?>()
        val errorRef = AtomicReference<String?>()

        client.refreshToken(
            refreshToken = "old-refresh-token",
            callback = { res, err ->
                resultRef.set(res)
                errorRef.set(err)
            }
        )

        val result = resultRef.get()
        val error = errorRef.get()
        assertNotNull("Result should not be null", result)
        assertEquals("Access token", "refreshed-access", result!!.accessToken)
        assertEquals("Refresh token", "refreshed-refresh", result!!.refreshToken)
        assertNull("Error should be null", error)
    }

    // ====== signOut ======

    @Test
    fun signOut_success_returnsTrue() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every {
            channel.newCall<Any, Any>(any<MethodDescriptor<Any, Any>>(), any<CallOptions>())
        } returns mockCall

        every {
            mockCall.start(any<ClientCall.Listener<Any>>(), any<Metadata>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val response = SimpleAuthResponseProto(
                success = true,
                message = "Signed out"
            )
            listener.onMessage(response)
            listener.onClose(Status.OK, Metadata())
        }

        val successRef = AtomicReference<Boolean>()
        val errorRef = AtomicReference<String?>()

        client.signOut(
            refreshToken = "some-token",
            callback = { s, err ->
                successRef.set(s)
                errorRef.set(err)
            }
        )

        val success = successRef.get()
        val error = errorRef.get()
        assertTrue("Sign out should succeed", success)
        assertNull("Error should be null", error)
    }

    // ====== revokeDevice ======

    @Test
    fun revokeDevice_success_returnsTrue() = runTest {
        val mockCall = mockk<ClientCall<Any, Any>>(relaxed = true)
        every {
            channel.newCall<Any, Any>(any<MethodDescriptor<Any, Any>>(), any<CallOptions>())
        } returns mockCall

        every {
            mockCall.start(any<ClientCall.Listener<Any>>(), any<Metadata>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val listener = firstArg<ClientCall.Listener<Any>>()
            val response = SimpleAuthResponseProto(
                success = true,
                message = "Device revoked"
            )
            listener.onMessage(response)
            listener.onClose(Status.OK, Metadata())
        }

        val successRef = AtomicReference<Boolean>()
        val errorRef = AtomicReference<String?>()

        client.revokeDevice(
            deviceId = "device-to-revoke",
            callback = { s, err ->
                successRef.set(s)
                errorRef.set(err)
            }
        )

        val success = successRef.get()
        val error = errorRef.get()
        assertTrue("Revoke should succeed", success)
        assertNull("Error should be null", error)
    }
}
