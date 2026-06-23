package lavender.client.android.data.grpc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import lavender.client.android.R
import lavender.client.android.data.session.CredentialStore

/**
 * ChatKeepAliveService — foreground service для поддержания gRPC chat stream в фоне.
 *
 * Предотвращает убийство процесса системой при нехватке памяти.
 * Автоматически переподключает chat stream при обрыве соединения.
 *
 * Жизненный цикл:
 * 1. startService() при login / app startup с активной сессией
 * 2. startForeground() с persistent notification
 * 3. Мониторинг connectionStatus + auto-reconnect
 * 4. stopSelf() при logout
 */
class ChatKeepAliveService : Service() {

    companion object {
        private const val TAG = "ChatKeepAlive"
        private const val NOTIFICATION_ID = 3001
        private const val CHANNEL_ID = "chat_keepalive_channel"
        private const val ACTION_STOP = "lavender.client.android.ACTION_STOP_CHAT"

        fun start(context: Context) {
            val intent = Intent(context, ChatKeepAliveService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ChatKeepAliveService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun isRunning(): Boolean = _isRunning
        private var _isRunning = false
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var statusJob: Job? = null
    private var reconnectJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (!_isRunning) {
                    val notification = buildNotification(getString(R.string.chat_keepalive_connecting))
                    startForeground(NOTIFICATION_ID, notification)
                    _isRunning = true
                }
                startMonitoring()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        statusJob?.cancel()
        reconnectJob?.cancel()
        serviceScope.cancel()
        _isRunning = false
    }

    private fun startMonitoring() {
        statusJob?.cancel()
        statusJob = serviceScope.launch {
            GrpcClient.connectionStatus
                .collect { status ->
                    Log.d(TAG, "Connection status: $status")
                    when (status) {
                        ConnectionStatus.READY -> {
                            updateNotification(getString(R.string.chat_keepalive_connected))
                            reconnectJob?.cancel()
                        }
                        ConnectionStatus.DISCONNECTED -> {
                            updateNotification(getString(R.string.chat_keepalive_reconnecting))
                            scheduleReconnect()
                        }
                        ConnectionStatus.FAILED -> {
                            updateNotification(getString(R.string.chat_keepalive_reconnecting))
                            scheduleReconnect()
                        }
                        ConnectionStatus.RECONNECTING -> {
                            updateNotification(getString(R.string.chat_keepalive_reconnecting))
                        }
                        ConnectionStatus.CONNECTING -> {
                            updateNotification(getString(R.string.chat_keepalive_connecting))
                        }
                    }
                }
        }
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(5000)
            val ctx = applicationContext
            val serverAddress = CredentialStore.getServerAddress(ctx)
            if (!serverAddress.isNullOrEmpty()) {
                val parts = serverAddress.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
                Log.d(TAG, "Auto-reconnecting to $host:$port")
                GrpcClient.connect(host, useTls = false, port = port, context = ctx, forceReconnect = true)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.chat_keepalive_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.chat_keepalive_channel_desc)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lavender Messenger")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(statusText: String) {
        try {
            val notification = buildNotification(statusText)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }
}
