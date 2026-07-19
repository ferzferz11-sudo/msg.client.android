package lavender.client.android.data.grpc

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Handles reconnect scheduling with exponential backoff.
 *
 * Owns:
 * - reconnectDelayMs (backoff state)
 * - reconnectJob (scheduled reconnect)
 * - isAuthFailure guard (skips reconnect on auth failures)
 *
 * Does NOT own: channel, connectionStatus, server address — those stay in GrpcConnectionManager.
 */
class GrpcReconnectStrategy(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "GrpcReconnectStrategy"
    }

    private var reconnectJob: Job? = null
    @Volatile private var reconnectDelayMs = 5000L

    /** Guard: skip reconnect on auth failures. Set by GrpcAuthClient. */
    @Volatile
    var isAuthFailure: Boolean = false

    fun schedule(reconnectAction: suspend () -> Unit) {
        if (isAuthFailure) {
            Log.w(TAG, "Skipping reconnect: auth failure")
            return
        }
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = reconnectDelayMs.coerceAtMost(30000L)
            Log.d(TAG, "Scheduling reconnect in ${delayMs}ms...")
            delay(delayMs)
            reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(60000L)
            Log.d(TAG, "Retrying connection...")
            reconnectAction()
        }
    }

    fun resetBackoff() {
        reconnectDelayMs = 5000L
    }

    fun cancel() {
        reconnectJob?.cancel()
        reconnectJob = null
    }
}
