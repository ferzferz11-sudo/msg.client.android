package lavender.client.android.data.grpc

import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GrpcConnectionManagerTest {

    private lateinit var connectionStatus: MutableStateFlow<ConnectionStatus>
    private lateinit var manager: GrpcConnectionManager
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockkObject(lavender.client.android.data.calls.CallManager)
        every { lavender.client.android.data.calls.CallManager.currentCall } returns MutableStateFlow(null)
        connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        manager = GrpcConnectionManager(
            scope = scope,
            connectionStatus = connectionStatus,
            onFetchServerInfo = { _, _, _ -> },
            onAutoResumeChat = { }
        )
    }

    @After
    fun tearDown() {
        unmockkObject(lavender.client.android.data.calls.CallManager)
        Dispatchers.resetMain()
    }

    @Test
    fun connect_validAddress_attemptsConnection() = runTest {
        manager.connect("127.0.0.1", false, 0)
        val status = connectionStatus.value
        assertTrue("Should attempt connection",
            status == ConnectionStatus.CONNECTING ||
            status == ConnectionStatus.RECONNECTING ||
            status == ConnectionStatus.READY ||
            status == ConnectionStatus.FAILED)
    }

    @Test
    fun connect_alreadyConnected_skipsReconnect() = runTest {
        connectionStatus.value = ConnectionStatus.READY
        manager.connect("127.0.0.1", false, 50051)
        assertEquals(ConnectionStatus.READY, connectionStatus.value)
    }

    @Test
    fun disconnect_setsDisconnected() = runTest {
        manager.connect("127.0.0.1", false, 0)
        manager.disconnect()
        assertEquals(ConnectionStatus.DISCONNECTED, connectionStatus.value)
        assertNull(manager.channel)
    }

    @Test
    fun reconnect_callsConnectWithForce() = runTest {
        manager.connect("127.0.0.1", false, 0)
        manager.reconnect()
        val status = connectionStatus.value
        assertTrue("Should attempt reconnection",
            status == ConnectionStatus.RECONNECTING ||
            status == ConnectionStatus.CONNECTING ||
            status == ConnectionStatus.READY)
    }

    @Test
    fun isConnectedTo_notConnected_returnsFalse() = runTest {
        assertFalse(manager.isConnectedTo("192.168.1.1", 50051))
    }

    @Test
    fun resetReconnectBackoff_noCrash() = runTest {
        manager.connect("127.0.0.1", false, 0)
        kotlinx.coroutines.delay(200)
        manager.resetReconnectBackoff()
        assertTrue(true)
    }

    @Test
    fun isAuthFailure_defaultIsFalse() {
        assertFalse(manager.isAuthFailure)
    }
}
