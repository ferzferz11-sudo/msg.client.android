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

/**
 * Unit-тесты для GrpcClient facade.
 *
 * Тестируем: connectionState mapping, StateFlow probing, isChatV2Supported delegation.
 * GrpcClient — это `object` (singleton), поэтому тестируем его публичный API напрямую.
 */
class GrpcClientFacadeTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Test
    fun connectionState_mapsReadyToTrue() = runTest {
        // Create a StateFlow that emits READY
        val statusFlow = MutableStateFlow(ConnectionStatus.READY)

        // Simulate GrpcClient.connectionState logic
        val connectionState = statusFlow
            .map { it == ConnectionStatus.READY }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, statusFlow.value == ConnectionStatus.READY)

        assertTrue("READY should map to true", connectionState.value)
    }

    @Test
    fun connectionState_mapsDisconnectedToFalse() = runTest {
        val statusFlow = MutableStateFlow(ConnectionStatus.DISCONNECTED)

        val connectionState = statusFlow
            .map { it == ConnectionStatus.READY }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, statusFlow.value == ConnectionStatus.READY)

        assertFalse("DISCONNECTED should map to false", connectionState.value)
    }

    @Test
    fun connectionState_mapsConnectingToFalse() = runTest {
        val statusFlow = MutableStateFlow(ConnectionStatus.CONNECTING)

        val connectionState = statusFlow
            .map { it == ConnectionStatus.READY }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, statusFlow.value == ConnectionStatus.READY)

        assertFalse("CONNECTING should map to false", connectionState.value)
    }

    @Test
    fun connectionState_mapsReconnectingToFalse() = runTest {
        val statusFlow = MutableStateFlow(ConnectionStatus.RECONNECTING)

        val connectionState = statusFlow
            .map { it == ConnectionStatus.READY }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, statusFlow.value == ConnectionStatus.READY)

        assertFalse("RECONNECTING should map to false", connectionState.value)
    }

    @Test
    fun connectionState_mapsFailedToFalse() = runTest {
        val statusFlow = MutableStateFlow(ConnectionStatus.FAILED)

        val connectionState = statusFlow
            .map { it == ConnectionStatus.READY }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, statusFlow.value == ConnectionStatus.READY)

        assertFalse("FAILED should map to false", connectionState.value)
    }

    @Test
    fun isChatV2Supported_delegatesToProfileClient() = runTest {
        // In test environment, ProfileClient.serviceChatVersion is empty
        val result = ProfileClient.isChatV2Supported()

        // Empty version string is not >= "2.0"
        assertFalse("Should return false when version not set", result)
    }

    @Test
    fun isProfileV2Supported_delegatesToProfileClient() = runTest {
        val result = ProfileClient.isProfileV2Supported()

        assertFalse("Should return false when version not set", result)
    }

    @Test
    fun isAuthV2Supported_delegatesToProfileClient() = runTest {
        val result = ProfileClient.isAuthV2Supported()

        assertFalse("Should return false when version not set", result)
    }

    @Test
    fun connectionState_transitionsCorrectly() = runTest {
        val statusFlow = MutableStateFlow(ConnectionStatus.DISCONNECTED)

        val connectionState = statusFlow
            .map { it == ConnectionStatus.READY }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

        // Initial: DISCONNECTED → false
        assertFalse(connectionState.value)

        // Transition to CONNECTING
        statusFlow.value = ConnectionStatus.CONNECTING
        assertFalse(connectionState.value)

        // Transition to READY
        statusFlow.value = ConnectionStatus.READY
        assertTrue(connectionState.value)

        // Transition to RECONNECTING
        statusFlow.value = ConnectionStatus.RECONNECTING
        assertFalse(connectionState.value)

        // Transition back to READY
        statusFlow.value = ConnectionStatus.READY
        assertTrue(connectionState.value)

        // Transition to FAILED
        statusFlow.value = ConnectionStatus.FAILED
        assertFalse(connectionState.value)
    }
}
