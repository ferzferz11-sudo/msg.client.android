package lavender.client.android.ui.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.util.Properties

/**
 * HermesGatewayManager — управление SSH туннелем для подключения Remote Agent
 * к удалённому серверу через SSH шлюз.
 *
 * Использует JSch для создания SSH туннеля:
 *   ssh -L <localPort>:<serverHost>:<serverPort> <sshHost> -N
 *
 * После создания туннеля агент подключается к localhost:<localPort>
 * и трафик пробрасывается через SSH на удалённый сервер.
 */
class HermesGatewayManager(private val context: Context) {

    companion object {
        private const val TAG = "HermesGatewayManager"
        private const val PREFS_NAME = "hermes_gateway_prefs"
        private const val PREF_SSH_HOST = "gateway_ssh_host"
        private const val PREF_SSH_PORT = "gateway_ssh_port"
        private const val PREF_SSH_USER = "gateway_ssh_user"
        private const val PREF_SERVER_HOST = "gateway_server_host"
        private const val PREF_SERVER_PORT = "gateway_server_port"
        private const val PREF_LOCAL_PORT = "gateway_local_port"
        private const val PREF_AUTO_CONNECT = "gateway_auto_connect"
    }

    private var session: Session? = null
    private val jsch = JSch()

    val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Создаёт SSH туннель.
     *
     * @param sshHost     SSH хост (например "lava" — alias из ~/.ssh/config)
     * @param sshPort     SSH порт (по умолчанию 22)
     * @param sshUser     пользователь SSH
     * @param serverHost  хост сервера за SSH (например "localhost")
     * @param serverPort  порт сервера за SSH (например 50051)
     * @param localPort   локальный порт для проброса (например 50052)
     * @return true если туннель успешно создан
     */
    fun createTunnel(
        sshHost: String,
        sshPort: Int = 22,
        sshUser: String = "",
        serverHost: String = "localhost",
        serverPort: Int = 50051,
        localPort: Int = 50052
    ): Boolean {
        // Закрываем предыдущий туннель если есть
        closeTunnel()

        return try {
            Log.d(TAG, "Creating tunnel: localhost:$localPort -> $serverHost:$serverPort via $sshHost:$sshPort")

            val config = Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("Compression", "yes")
                put("ConnectTimeout", "10000")
                put("ServerAliveInterval", "30")
                put("ServerAliveCountMax", "3")
            }

            val sess = jsch.getSession(sshUser, sshHost, sshPort)
            sess.setConfig(config)
            sess.connect(15000) // 15s timeout

            if (!sess.isConnected) {
                Log.e(TAG, "SSH session not connected")
                return false
            }

            // Пробрасываем порт: localhost:localPort -> serverHost:serverPort
            sess.setPortForwardingL(localPort, serverHost, serverPort)

            session = sess

            // Сохраняем настройки
            saveSettings(sshHost, sshPort, sshUser, serverHost, serverPort, localPort)

            Log.d(TAG, "Tunnel created successfully: localhost:$localPort -> $serverHost:$serverPort")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create tunnel: ${e.message}", e)
            false
        }
    }

    /**
     * Проверяет активен ли туннель.
     */
    fun isTunnelActive(): Boolean {
        return try {
            session?.isConnected == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Закрывает SSH туннель.
     */
    fun closeTunnel() {
        try {
            session?.disconnect()
            Log.d(TAG, "Tunnel closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing tunnel: ${e.message}")
        }
        session = null
    }

    /**
     * Возвращает локальный адрес для подключения агента.
     * Если туннель активен — localhost:<localPort>
     * Если нет — пустая строка.
     */
    fun getLocalAddress(): String {
        return if (isTunnelActive()) {
            val port = prefs.getInt(PREF_LOCAL_PORT, 50052)
            "localhost:$port"
        } else {
            ""
        }
    }

    /**
     * Сохраняет настройки туннеля в SharedPreferences.
     */
    fun saveSettings(
        sshHost: String,
        sshPort: Int,
        sshUser: String,
        serverHost: String,
        serverPort: Int,
        localPort: Int
    ) {
        prefs.edit()
            .putString(PREF_SSH_HOST, sshHost)
            .putInt(PREF_SSH_PORT, sshPort)
            .putString(PREF_SSH_USER, sshUser)
            .putString(PREF_SERVER_HOST, serverHost)
            .putInt(PREF_SERVER_PORT, serverPort)
            .putInt(PREF_LOCAL_PORT, localPort)
            .apply()
    }

    /**
     * Загружает сохранённые настройки.
     */
    fun loadSettings(): GatewaySettings {
        return GatewaySettings(
            sshHost = prefs.getString(PREF_SSH_HOST, "") ?: "",
            sshPort = prefs.getInt(PREF_SSH_PORT, 22),
            sshUser = prefs.getString(PREF_SSH_USER, "") ?: "",
            serverHost = prefs.getString(PREF_SERVER_HOST, "localhost") ?: "localhost",
            serverPort = prefs.getInt(PREF_SERVER_PORT, 50051),
            localPort = prefs.getInt(PREF_LOCAL_PORT, 50052),
            autoConnect = prefs.getBoolean(PREF_AUTO_CONNECT, false)
        )
    }

    /**
     * Устанавливает флаг автоподключения.
     */
    fun setAutoConnect(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_AUTO_CONNECT, enabled).apply()
    }

    /**
     * Проверяет включено ли автоподключение.
     */
    fun isAutoConnect(): Boolean {
        return prefs.getBoolean(PREF_AUTO_CONNECT, false)
    }
}

/**
 * Настройки Hermes Gateway.
 */
data class GatewaySettings(
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUser: String = "",
    val serverHost: String = "localhost",
    val serverPort: Int = 50051,
    val localPort: Int = 50052,
    val autoConnect: Boolean = false
)
