package lavender.client.android.ui.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.AppLog
import lavender.client.android.data.grpc.GrpcClientExtensions.*

/**
 * RemoteAgentService — foreground service для фонового подключения Remote Agent.
 *
 * Управляет SSH туннелем (через HermesGatewayManager) и gRPC подключением.
 * START_STICKY — перезапускается при убийстве системой.
 *
 * Жизненный цикл:
 * 1. startService() → onCreate() → startForeground()
 * 2. bindService() из Activity получение IBinder для вызова методов
 * 3. stopSelf() / stopService() при явном отключении
 */
class RemoteAgentService : Service() {

    companion object {
        private const val TAG = "RemoteAgentService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "remote_agent_channel"

        // Actions for restart intent
        const val ACTION_STOP = "lavender.client.android.ACTION_STOP"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val binder = LocalBinder()

    private lateinit var gatewayManager: HermesGatewayManager

    // Текущее состояние подключения
    @Volatile
    private var isTunnelActive = false

    @Volatile
    private var isGrpcConnected = false

    @Volatile
    private var currentTunnelAddress: String = ""

    @Volatile
    private var isStartedAsForeground = false

    // Callback для получения результатов задач (устанавливается из Activity)
    var taskCallback: ((success: Boolean, output: String, error: String) -> Unit)? = null

    /**
     * Binder для привязки Activity к сервису.
     */
    inner class LocalBinder : Binder() {
        fun getService(): RemoteAgentService = this@RemoteAgentService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")

        gatewayManager = HermesGatewayManager(applicationContext)
        createNotificationChannel()

        // Восстанавливаем состояние туннеля из предыдущего запуска
        if (gatewayManager.isTunnelActive()) {
            isTunnelActive = true
            currentTunnelAddress = gatewayManager.getLocalAddress()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            else -> {
                // Обычный запуск — поднимаем foreground notification
                if (!isStartedAsForeground) {
                    val notification = buildNotification(getString(R.string.notif_initializing), isConnected = false)
                    startForeground(NOTIFICATION_ID, notification)
                    isStartedAsForeground = true
                }
            }
        }

        // START_STICKY — перезапускается системой после убийства
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service onBind")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Service onUnbind")
        // Сервис продолжает работать после unbind (bound + started)
        return true // Позволяет onRebind
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        Log.d(TAG, "Service onRebind")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        closeTunnel()
        serviceScope.cancel()
    }

    // ===== Публичные методы для вызова из Activity =====

    /**
     * Проверяет, есть ли активное подключение (туннель или gRPC).
     */
    fun isConnected(): Boolean {
        return gatewayManager.isTunnelActive() || isGrpcConnected
    }

    /**
     * Возвращает текстовый статус подключения.
     */
    fun getStatusText(): String {
        return when {
            gatewayManager.isTunnelActive() -> {
                val addr = gatewayManager.getLocalAddress()
                getString(R.string.notif_connected_via_gateway, addr)
            }
            isGrpcConnected -> getString(R.string.notif_connected)
            else -> getString(R.string.notif_disconnected)
        }
    }

    /**
     * Создаёт SSH туннель.
     */
    fun createTunnel(
        sshHost: String,
        sshPort: Int = 22,
        sshUser: String,
        sshPassword: String,
        serverHost: String = "localhost",
        serverPort: Int = 50051,
        localPort: Int = 50052,
        callback: (success: Boolean, errorMessage: String, errorType: HermesGatewayManager.TunnelErrorType) -> Unit
    ) {
        if (gatewayManager.isTunnelActive()) {
            callback(true, "", HermesGatewayManager.TunnelErrorType.NONE)
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            try {
                val result = gatewayManager.createTunnel(
                    sshHost = sshHost,
                    sshPort = sshPort,
                    sshUser = sshUser,
                    sshPassword = sshPassword,
                    serverHost = serverHost,
                    serverPort = serverPort,
                    localPort = localPort
                )
                withContext(Dispatchers.Main) {
                    if (result.success) {
                        isTunnelActive = true
                        currentTunnelAddress = gatewayManager.getLocalAddress()
                        updateNotification(
                            getString(R.string.notif_connected_via_gateway, currentTunnelAddress),
                            isConnected = true
                        )
                        Log.d(TAG, "Tunnel created: $currentTunnelAddress")
                    }
                    callback(result.success, result.errorMessage, result.errorType)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tunnel creation error", e)
                AppLog.error("RemoteAgentService.createTunnel", "SSH tunnel error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    callback(false, getString(R.string.notif_error, e.message), HermesGatewayManager.TunnelErrorType.GENERIC)
                }
            }
        }
    }

    /**
     * Закрывает SSH туннель.
     */
    fun closeTunnel() {
        gatewayManager.closeTunnel()
        isTunnelActive = false
        currentTunnelAddress = ""
        updateNotification(getString(R.string.notif_disconnected), isConnected = false)
        Log.d(TAG, "Tunnel closed")
    }

    /**
     * Проверяет активен ли туннель.
     */
    fun isTunnelActiveLocal(): Boolean {
        val active = gatewayManager.isTunnelActive()
        isTunnelActive = active
        return active
    }

    /**
     * Возвращает локальный адрес туннеля (для подключения агента).
     */
    fun getTunnelAddress(): String {
        return if (gatewayManager.isTunnelActive()) {
            gatewayManager.getLocalAddress()
        } else ""
    }

    /**
     * Отправляет задачу агенту.
     */
    fun sendTask(
        agentId: String,
        taskType: String,
        command: String,
        userId: String,
        callback: (success: Boolean, output: String, error: String) -> Unit
    ) {
        val tunnelActive = gatewayManager.isTunnelActive()
        val settings = gatewayManager.loadSettings()

        serviceScope.launch {
            try {
                val thisTunnelAddress = gatewayManager.getLocalAddress()
                val response = GrpcClient.deployAgentTask(
                    agentId = agentId,
                    taskType = taskType,
                    params = mapOf("command" to command),
                    tunnelMode = if (tunnelActive) 1 else 0,
                    tunnelHost = settings.sshHost,
                    tunnelPort = settings.sshPort,
                    tunnelUser = settings.sshUser,
                    tunnelServerHost = settings.serverHost,
                    tunnelServerPort = settings.serverPort,
                    tunnelLocalPort = settings.localPort
                )
                if (response.success) {
                    val output = if (response.stdout.isNotEmpty()) response.stdout else "(no output)"
                    callback(true, output, "")
                } else {
                    val errText = if (response.stderr.isNotEmpty()) response.stderr else response.message
                    callback(false, "", errText)
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendTask error", e)
                AppLog.error("RemoteAgentService.sendTask", "Task execution error: ${e.message}", e)
                callback(false, "", getString(R.string.notif_error, e.message))
            }
        }
    }

    // ===== Notification =====

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Remote Agent",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_background_connection)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String, isConnected: Boolean): Notification {
        val iconRes = if (isConnected) R.drawable.ic_agents else R.drawable.ic_notification_small
        val tintColor = if (isConnected) 0xFF4CAF50.toInt() else 0xFFF44336.toInt()

        // Intent для открытия RemoteAgentActivity
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, RemoteAgentActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent для остановки сервиса
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RemoteAgentService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Remote Agent")
            .setContentText(statusText)
            .setSmallIcon(iconRes)
            .setColor(tintColor)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.notif_disconnect), stopIntent)
            .setOngoing(isConnected)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(statusText: String, isConnected: Boolean) {
        try {
            val notification = buildNotification(statusText, isConnected)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundService() {
        try {
            closeTunnel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            isStartedAsForeground = false
            stopSelf()
            Log.d(TAG, "Service stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service", e)
        }
    }
}
