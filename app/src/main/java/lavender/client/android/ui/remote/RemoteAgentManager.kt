package lavender.client.android.ui.remote

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log

/**
 * RemoteAgentManager — singleton для привязки UI к RemoteAgentService.
 *
 * Использование:
 * 1. В Application.onCreate() или первой Activity: RemoteAgentManager.init(context)
 * 2. В Activity.onCreate(): RemoteAgentManager.bind() + serviceConnection
 * 3. В Activity.onDestroy(): RemoteAgentManager.unbind()
 * 4. Для вызова методов: RemoteAgentManager.getService()?.метод()
 *
 * Сервис запускается через startService() + bindService().
 * При unbind сервис продолжает работать (foreground).
 * Для остановки: stopService() или ACTION_STOP через notification.
 */
object RemoteAgentManager {

    private const val TAG = "RemoteAgentManager"

    private var service: RemoteAgentService? = null
    private var isBound = false
    private var appContext: Context? = null

    // Список подписчиков на изменение состояния
    private val stateListeners = mutableListOf<RemoteAgentStateListener>()

    /**
     * Состояние подключения Remote Agent.
     */
    data class AgentConnectionState(
        val isConnected: Boolean = false,
        val isTunnelActive: Boolean = false,
        val tunnelAddress: String = "",
        val statusText: String = "Отключено"
    )

    /**
     * Callback для получения результатов задач.
     */
    interface TaskCallback {
        fun onTaskResult(success: Boolean, output: String, error: String)
    }

    /**
     * Listener для изменения состояния подключения.
     */
    interface RemoteAgentStateListener {
        fun onStateChanged(state: AgentConnectionState)
    }

    /**
     * Инициализация (вызвать один раз при старте приложения).
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        Log.d(TAG, "init")
    }

    /**
     * Запускает сервис (foreground) и привязывается к нему.
     */
    fun bind(listener: RemoteAgentStateListener? = null) {
        val ctx = appContext ?: return

        listener?.let { addStateListener(it) }

        // Запускаем сервис (startService чтобы он жил после unbind)
        val intent = Intent(ctx, RemoteAgentService::class.java)
        ctx.startService(intent)

        // Привязываемся
        if (!isBound) {
            try {
                val bound = ctx.bindService(
                    intent,
                    serviceConnection,
                    Context.BIND_AUTO_CREATE
                )
                Log.d(TAG, "bindService result: $bound")
            } catch (e: Exception) {
                Log.e(TAG, "bindService error", e)
            }
        }
    }

    /**
     * Отвязывается от сервиса (сервис продолжает работать).
     */
    fun unbind(listener: RemoteAgentStateListener? = null) {
        val ctx = appContext ?: return

        listener?.let { removeStateListener(it) }

        if (isBound) {
            try {
                ctx.unbindService(serviceConnection)
                isBound = false
                service = null
                Log.d(TAG, "unbindService")
            } catch (e: Exception) {
                Log.e(TAG, "unbindService error", e)
            }
        }
    }

    /**
     * Останавливает сервис полностью.
     */
    fun stopService() {
        val ctx = appContext ?: return
        try {
            val intent = Intent(ctx, RemoteAgentService::class.java)
            intent.action = RemoteAgentService.ACTION_STOP
            ctx.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "stopService error", e)
        }
    }

    /**
     * Возвращает ссылку на сервис (null если не привязан).
     */
    fun getService(): RemoteAgentService? = service

    /**
     * Проверяет, привязаны ли к сервису.
     */
    fun isServiceBound(): Boolean = isBound && service != null

    /**
     * Проверяет, подключен ли агент.
     */
    fun isConnected(): Boolean {
        return service?.isConnected() == true
    }

    /**
     * Возвращает текстовый статус.
     */
    fun getStatusText(): String {
        return service?.getStatusText() ?: "Отключено"
    }

    /**
     * Создаёт SSH туннель через сервис.
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
        val svc = service
        if (svc == null) {
            callback(false, "Сервис не запущен. Попробуйте позже.", HermesGatewayManager.TunnelErrorType.GENERIC)
            return
        }
        svc.createTunnel(
            sshHost, sshPort, sshUser, sshPassword,
            serverHost, serverPort, localPort
        ) { success, error, type ->
            notifyStateChanged()
            callback(success, error, type)
        }
    }

    /**
     * Закрывает туннель через сервис.
     */
    fun closeTunnel() {
        service?.closeTunnel()
        notifyStateChanged()
    }

    /**
     * Проверяет активен ли туннель.
     */
    fun isTunnelActive(): Boolean {
        return service?.isTunnelActiveLocal() == true
    }

    /**
     * Возвращает адрес туннеля.
     */
    fun getTunnelAddress(): String {
        return service?.getTunnelAddress() ?: ""
    }

    /**
     * Отправляет задачу агенту через сервис.
     */
    fun sendTask(
        agentId: String,
        taskType: String,
        command: String,
        userId: String,
        callback: TaskCallback
    ) {
        val svc = service
        if (svc == null) {
            callback.onTaskResult(false, "", "Сервис не запущен")
            return
        }
        svc.sendTask(agentId, taskType, command, userId) { success, output, error ->
            callback.onTaskResult(success, output, error)
        }
    }

    /**
     * Устанавливает callback для результатов задач в сервисе.
     */
    fun setTaskCallback(callback: (success: Boolean, output: String, error: String) -> Unit) {
        service?.taskCallback = callback
    }

    // ===== State listeners =====

    fun addStateListener(listener: RemoteAgentStateListener) {
        if (!stateListeners.contains(listener)) {
            stateListeners.add(listener)
        }
    }

    fun removeStateListener(listener: RemoteAgentStateListener) {
        stateListeners.remove(listener)
    }

    private fun notifyStateChanged() {
        val state = AgentConnectionState(
            isConnected = isConnected(),
            isTunnelActive = isTunnelActive(),
            tunnelAddress = getTunnelAddress(),
            statusText = getStatusText()
        )
        stateListeners.forEach { it.onStateChanged(state) }
    }

    // ===== ServiceConnection =====

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "onServiceConnected")
            val localBinder = binder as? RemoteAgentService.LocalBinder
            service = localBinder?.getService()
            isBound = true
            notifyStateChanged()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected")
            service = null
            isBound = false
            notifyStateChanged()
        }
    }
}
