package lavender.client.android.data.grpc

import android.content.Context
import android.util.Log
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import lavender.client.android.data.proto.*
import java.util.concurrent.TimeUnit

/**
 * Manages gRPC channel lifecycle: connect, disconnect, reconnect, keepalive.
 *
 * Owns:
 * - channel (ManagedChannel)
 * - currentServerAddress / currentServerPort
 * - reconnect scheduling with exponential backoff
 *
 * Does NOT own: authStatus, connectionStatus — those are in RealGrpcClient.
 */
class GrpcConnectionManager(
    private val scope: CoroutineScope,
    private val connectionStatus: MutableStateFlow<ConnectionStatus>,
    private val onFetchServerInfo: (String, Int, Context) -> Unit,
    private val onAutoResumeChat: () -> Unit
) {
    companion object {
        private const val TAG = "GrpcConnectionManager"
    }

    var channel: ManagedChannel? = null
        private set

    var currentServerAddress: String? = null
        private set
    var currentServerPort: Int = 50051
        private set

    private var reconnectJob: Job? = null
    private var reconnectDelayMs = 5000L
    private var appContext: Context? = null

    /** Guard: skip reconnect on auth failures. Set by GrpcAuthClient. */
    @Volatile
    var isAuthFailure: Boolean = false

    fun isConnectedTo(host: String, port: Int): Boolean {
        val ch = channel
        return ch != null && !ch.isShutdown && !ch.isTerminated
                && currentServerAddress == host && currentServerPort == port
                && connectionStatus.value == ConnectionStatus.READY
    }

    fun connect(
        serverAddress: String,
        useTls: Boolean = false,
        port: Int = 50051,
        context: Context? = null,
        forceReconnect: Boolean = false
    ) {
        Log.d(TAG, "connect() called: addr=$serverAddress:$port force=$forceReconnect status=${connectionStatus.value}")
        if (forceReconnect) {
            Log.d(TAG, "forceReconnect requested — stack: ${Thread.currentThread().stackTrace.take(8).joinToString(" -> ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }}")
        }

        val channelDead = channel?.isShutdown == true || channel?.isTerminated == true
        val addressMatch = currentServerAddress == serverAddress
        val channelAlive = channel != null && !channelDead && connectionStatus.value == ConnectionStatus.READY

        if (addressMatch && channelAlive && !forceReconnect) {
            Log.d(TAG, "Connection already READY, keeping active streams")
            return
        }

        // CRITICAL: Do not reset channel if a call is in progress
        if (lavender.client.android.data.calls.CallManager.currentCall.value != null) {
            Log.w(TAG, "Call in progress, preventing channel reset")
            return
        }

        appContext = context?.applicationContext
        currentServerAddress = serverAddress
        currentServerPort = port

        Log.d(TAG, "Connecting to $serverAddress:$port (TLS: $useTls)")
        val wasConnected = connectionStatus.value == ConnectionStatus.READY ||
                connectionStatus.value == ConnectionStatus.RECONNECTING
        connectionStatus.value = if (wasConnected) ConnectionStatus.RECONNECTING else ConnectionStatus.CONNECTING

        try {
            val builder = OkHttpChannelBuilder.forAddress(serverAddress, port)
            if (useTls) {
                builder.useTransportSecurity()
            } else {
                builder.usePlaintext()
            }

            builder.keepAliveTime(30, TimeUnit.SECONDS)
            builder.keepAliveTimeout(10, TimeUnit.SECONDS)
            builder.keepAliveWithoutCalls(true)
            builder.maxInboundMessageSize(64 * 1024 * 1024)
            builder.idleTimeout(25, TimeUnit.MINUTES)

            val appCtx = context?.applicationContext
            if (appCtx != null) {
                builder.intercept(BearerTokenInterceptor(appCtx))
            }

            channel?.shutdownNow()
            val newChannel = builder.build()
            channel = newChannel

            connectionStatus.value = ConnectionStatus.READY
            resetReconnectBackoff()
            Log.d(TAG, "Channel built — READY (optimistic): $serverAddress")
            RealGrpcClient.clearServerShuttingDown()

            if (context != null) {
                val httpPort = if (port == 50052) 8083 else 8082
                scope.launch {
                    try {
                        onFetchServerInfo(serverAddress, httpPort, context)
                    } catch (e: Exception) {
                        Log.w(TAG, "fetchServerInfo failed: ${e.message}")
                    }
                }
            }

            onAutoResumeChat()
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            connectionStatus.value = ConnectionStatus.RECONNECTING
            scheduleReconnect(serverAddress, useTls, port, context)
        }
    }

    fun reconnect() {
        val addr = currentServerAddress
        if (!addr.isNullOrEmpty()) {
            Log.d(TAG, "reconnect() called, reconnecting to $addr:$currentServerPort")
            connect(addr, false, currentServerPort, appContext, true)
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        resetReconnectBackoff()
        channel?.shutdown()
        channel = null
        connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    private fun scheduleReconnect(serverAddress: String, useTls: Boolean, port: Int, context: Context?) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = reconnectDelayMs.coerceAtMost(30000L)
            Log.d(TAG, "Scheduling reconnect in ${delayMs}ms...")
            delay(delayMs)
            reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(60000L)
            Log.d(TAG, "Retrying connection to $serverAddress...")
            connect(serverAddress, useTls, port, context)
        }
    }

    fun resetReconnectBackoff() {
        reconnectDelayMs = 5000L
    }
}
