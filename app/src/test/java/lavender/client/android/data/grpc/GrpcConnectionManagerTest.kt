package lavender.client.android.data.grpc

import io.grpc.ManagedChannel
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GrpcConnectionManagerTest {

    private lateinit var connectionStatus: MutableStateFlow<ConnectionStatus>
    private lateinit var manager: GrpcConnectionManager
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setup() {
        connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        manager = GrpcConnectionManager(
            scope = scope,
            connectionStatus = connectionStatus,
            onFetchServerInfo = { _, _, _ -> },
            onAutoResumeChat = { }
        )
    }

    @Test
    fun connect_validAddress_attemptsConnection() = runTest {
        manager.connect("127.0.0.1", false, 0)
        kotlinx.coroutines.delay(500)
        val status = connectionStatus.value
        assertTrue("Should attempt connection",
            status == ConnectionStatus.CONNECTING ||
            status == ConnectionStatus.RECONNECTING ||
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
        kotlinx.coroutines.delay(200)
        manager.disconnect()
        assertEquals(ConnectionStatus.DISCONNECTED, connectionStatus.value)
        assertNull(manager.channel)
    }

    @Test
    fun reconnect_callsConnectWithForce() = runTest {
        manager.connect("127.0.0.1", false, 0)
        kotlinx.coroutines.delay(200)
        manager.reconnect()
        val status = connectionStatus.value
        assertTrue("Should attempt reconnection",
            status == ConnectionStatus.RECONNECTING ||
            status == ConnectionStatus.CONNECTING)
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
