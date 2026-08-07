package lavender.client.android.data.grpc

import android.content.Context
import android.util.Log
import io.grpc.ManagedChannel
import io.grpc.okhttp.OkHttpChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
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

        // Channel configuration
        private const val KEEP_ALIVE_TIME_SECONDS = 30L
        private const val KEEP_ALIVE_TIMEOUT_SECONDS = 10L
        private const val MAX_INBOUND_MESSAGE_SIZE = 64 * 1024 * 1024 // 64 MB
        private const val IDLE_TIMEOUT_MINUTES = 25L

        // HTTP port mapping
        private const val DEV_GRPC_PORT = 50052
        private const val DEV_HTTP_PORT = 8083
        private const val PROD_HTTP_PORT = 8082
    }

    @Volatile var channel: ManagedChannel? = null
        private set

    @Volatile var currentServerAddress: String? = null
        private set
    @Volatile var currentServerPort: Int = 50051
        private set

    @Volatile private var appContext: Context? = null

    val reconnectStrategy = GrpcReconnectStrategy(scope)

    var isAuthFailure: Boolean
        get() = reconnectStrategy.isAuthFailure
        set(value) { reconnectStrategy.isAuthFailure = value }

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

        if (!shouldConnect(serverAddress, forceReconnect)) {
            return
        }

        if (isCallInProgress()) {
            Log.w(TAG, "Call in progress, preventing channel reset")
            return
        }

        updateServerAddress(serverAddress, port, context)
        updateConnectionStatus(isReconnecting = connectionStatus.value == ConnectionStatus.READY ||
                connectionStatus.value == ConnectionStatus.RECONNECTING)

        val newChannel = buildChannel(serverAddress, useTls, port, context)
        if (newChannel != null) {
            activateChannel(newChannel, serverAddress, port, context)
        } else {
            scheduleReconnect(serverAddress, useTls, port)
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
        reconnectStrategy.cancel()
        reconnectStrategy.resetBackoff()
        channel?.shutdown()
        channel = null
        connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    fun resetReconnectBackoff() {
        reconnectStrategy.resetBackoff()
    }

    // ====== Private helpers ======

    private fun shouldConnect(serverAddress: String, forceReconnect: Boolean): Boolean {
        val channelDead = channel?.isShutdown == true || channel?.isTerminated == true
        val addressMatch = currentServerAddress == serverAddress
        val channelAlive = channel != null && !channelDead && connectionStatus.value == ConnectionStatus.READY

        if (addressMatch && channelAlive && !forceReconnect) {
            Log.d(TAG, "Connection already READY, keeping active streams")
            return false
        }
        return true
    }

    private fun isCallInProgress(): Boolean {
        return lavender.client.android.data.calls.CallManager.currentCall.value != null
    }

    private fun updateServerAddress(serverAddress: String, port: Int, context: Context?) {
        appContext = context?.applicationContext
        currentServerAddress = serverAddress
        currentServerPort = port
    }

    private fun updateConnectionStatus(isReconnecting: Boolean) {
        Log.d(TAG, "Connecting to $currentServerAddress:$currentServerPort")
        connectionStatus.value = if (isReconnecting) ConnectionStatus.RECONNECTING else ConnectionStatus.CONNECTING
    }

    private fun buildChannel(
        serverAddress: String,
        useTls: Boolean,
        port: Int,
        context: Context?
    ): ManagedChannel? {
        return try {
            val builder = OkHttpChannelBuilder.forAddress(serverAddress, port)

            if (useTls) {
                builder.useTransportSecurity()
            } else {
                builder.usePlaintext()
            }

            builder.keepAliveTime(KEEP_ALIVE_TIME_SECONDS, TimeUnit.SECONDS)
            builder.keepAliveTimeout(KEEP_ALIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            builder.keepAliveWithoutCalls(true)
            builder.maxInboundMessageSize(MAX_INBOUND_MESSAGE_SIZE)
            builder.idleTimeout(IDLE_TIMEOUT_MINUTES, TimeUnit.MINUTES)

            val appCtx = context?.applicationContext
            if (appCtx != null) {
                builder.intercept(BearerTokenInterceptor(appCtx))
            }

            channel?.shutdown()
            val newChannel = builder.build()
            Log.d(TAG, "Channel built: $serverAddress:$port")
            newChannel
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build channel", e)
            null
        }
    }

    private fun activateChannel(
        newChannel: ManagedChannel,
        serverAddress: String,
        port: Int,
        context: Context?
    ) {
        channel = newChannel
        connectionStatus.value = ConnectionStatus.READY
        reconnectStrategy.resetBackoff()
        Log.d(TAG, "Channel activated — READY (optimistic): $serverAddress")
        RealGrpcClient.clearServerShuttingDown()

        fetchServerInfoIfNeeded(serverAddress, port, context)
        onAutoResumeChat()
    }

    private fun fetchServerInfoIfNeeded(serverAddress: String, port: Int, context: Context?) {
        if (context != null) {
            val httpPort = if (port == DEV_GRPC_PORT) DEV_HTTP_PORT else PROD_HTTP_PORT
            scope.launch {
                try {
                    onFetchServerInfo(serverAddress, httpPort, context)
                } catch (e: Exception) {
                    Log.w(TAG, "fetchServerInfo failed: ${e.message}")
                }
            }
        }
    }

    private fun scheduleReconnect(serverAddress: String, useTls: Boolean, port: Int) {
        connectionStatus.value = ConnectionStatus.RECONNECTING
        reconnectStrategy.schedule {
            val appCtx = appContext
            connect(serverAddress, useTls, port, appCtx)
        }
    }
}
