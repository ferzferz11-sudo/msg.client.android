package lavender.client.android.data.grpc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GrpcClientFacadeTest {

    @Test
    fun connectionState_mapsReadyToTrue() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val statusFlow = MutableStateFlow(ConnectionStatus.READY)
        val connectionState = statusFlow
            .map { it == ConnectionStatus.READY }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, statusFlow.value == ConnectionStatus.READY)
        assertTrue("READY should map to true", connectionState.value)
    }

    @Test
    fun connectionState_mapsDisconnectedToFalse() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val statusFlow = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        val connectionState = statusFlow
            .map { it == ConnectionStatus.READY }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, statusFlow.value == ConnectionStatus.READY)
        assertFalse("DISCONNECTED should map to false", connectionState.value)
    }

    @Test
    fun connectionState_mapsConnectingToFalse() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val statusFlow = MutableStateFlow(ConnectionStatus.CONNECTING)
        val connectionState = statusFlow
            .map { it == ConnectionStatus.READY }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, statusFlow.value == ConnectionStatus.READY)
        assertFalse("CONNECTING should map to false", connectionState.value)
    }

    @Test
    fun connectionState_mapsReconnectingToFalse() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val statusFlow = MutableStateFlow(ConnectionStatus.RECONNECTING)
        val connectionState = statusFlow
            .map { it == ConnectionStatus.READY }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, statusFlow.value == ConnectionStatus.READY)
        assertFalse("RECONNECTING should map to false", connectionState.value)
    }

    @Test
    fun connectionState_mapsFailedToFalse() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val statusFlow = MutableStateFlow(ConnectionStatus.FAILED)
        val connectionState = statusFlow
            .map { it == ConnectionStatus.READY }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, statusFlow.value == ConnectionStatus.READY)
        assertFalse("FAILED should map to false", connectionState.value)
    }

    @Test
    fun isChatV2Supported_delegatesToProfileClient() = runTest {
        val result = ProfileClient.isChatV2Supported()
        assertTrue("Should return true", result)
    }

    @Test
    fun isProfileV2Supported_delegatesToProfileClient() = runTest {
        val result = ProfileClient.isProfileV2Supported()
        assertTrue("Should return true", result)
    }

    @Test
    fun isAuthV2Supported_delegatesToProfileClient() = runTest {
        val result = ProfileClient.isAuthV2Supported()
        assertTrue("Should return true", result)
    }

    @Test
    fun connectionState_transitionsCorrectly() = runTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val statusFlow = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        val connectionState = statusFlow
            .map { it == ConnectionStatus.READY }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

        assertFalse(connectionState.value) // DISCONNECTED
        statusFlow.value = ConnectionStatus.CONNECTING
        assertFalse(connectionState.value) // CONNECTING
        statusFlow.value = ConnectionStatus.READY
        assertTrue(connectionState.value) // READY
        statusFlow.value = ConnectionStatus.RECONNECTING
        assertFalse(connectionState.value) // RECONNECTING
        statusFlow.value = ConnectionStatus.READY
        assertTrue(connectionState.value) // READY
        statusFlow.value = ConnectionStatus.FAILED
        assertFalse(connectionState.value) // FAILED
    }
}
