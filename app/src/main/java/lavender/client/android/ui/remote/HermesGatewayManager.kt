package lavender.client.android.ui.remote

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.JSchException
import java.net.UnknownHostException
import java.util.Properties

/**
 * HermesGatewayManager — управление SSH туннелем для подключения Remote Agent
 * к удалённому серверу через SSH шлюз.
 *
 * Использует JSch для создания SSH туннеля:
 *   ssh -L <localPort>:<serverHost>:<serverPort> <sshHost> -N -p <sshPort> -l <sshUser>
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
        private const val PREF_SSH_PASSWORD = "gateway_ssh_password"
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
     * Результат создания туннеля.
     */
    data class TunnelResult(
        val success: Boolean,
        val errorMessage: String = "",
        val errorType: TunnelErrorType = TunnelErrorType.NONE
    )

    enum class TunnelErrorType {
        NONE,
        UNKNOWN_HOST,       // хост не резолвится (DNS)
        CONNECTION_REFUSED, // соединение отклонено
        AUTH_FAILED,        // ошибка авторизации
        TIMEOUT,            // таймаут подключения
        PORT_IN_USE,        // локальный порт уже занят
        GENERIC             // другая ошибка
    }

    /**
     * Создаёт SSH туннель.
     *
     * @param sshHost     SSH хост (IP адрес или hostname, НЕ SSH alias!)
     * @param sshPort     SSH порт (по умолчанию 22)
     * @param sshUser     пользователь SSH
     * @param sshPassword пароль SSH (может быть пустым для key-based auth)
     * @param serverHost  хост сервера за SSH (например "localhost")
     * @param serverPort  порт сервера за SSH (например 50051)
     * @param localPort   локальный порт для проброса (например 50052)
     * @return TunnelResult с результатом и понятным описанием ошибки
     */
    fun createTunnel(
        sshHost: String,
        sshPort: Int = 22,
        sshUser: String = "",
        sshPassword: String = "",
        serverHost: String = "localhost",
        serverPort: Int = 50051,
        localPort: Int = 50052
    ): TunnelResult {
        // Закрываем предыдущий туннель если есть
        closeTunnel()

        Log.d(TAG, "Creating tunnel: localhost:$localPort -> $serverHost:$serverPort via $sshHost:$sshPort")

        try {
            val actualUser = sshUser.ifBlank { "root" }

            val config = Properties().apply {
                put("StrictHostKeyChecking", "no")
                put("Compression", "yes")
                put("ConnectTimeout", "15000")
                put("ServerAliveInterval", "30")
                put("ServerAliveCountMax", "3")
            }

            val sess = jsch.getSession(actualUser, sshHost, sshPort)
            sess.setConfig(config)

            // Если указан пароль — используем его
            if (sshPassword.isNotBlank()) {
                sess.setPassword(sshPassword)
            }

            sess.connect(15000)

            if (!sess.isConnected) {
                Log.e(TAG, "SSH session not connected")
                return TunnelResult(false, "SSH сессия не установлена", TunnelErrorType.CONNECTION_REFUSED)
            }

            // Пробрасываем порт: localhost:localPort -> serverHost:serverPort
            sess.setPortForwardingL(localPort, serverHost, serverPort)

            session = sess
            saveSettings(sshHost, sshPort, sshUser, serverHost, serverPort, localPort)

            Log.d(TAG, "Tunnel created successfully: localhost:$localPort -> $serverHost:$serverPort")
            return TunnelResult(true)

        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unknown host: ${sshHost}", e)
            return TunnelResult(
                false,
                "Не удалось найти хост «${sshHost}». Введите IP адрес (например: 13.140.25.249), а не SSH alias.",
                TunnelErrorType.UNKNOWN_HOST
            )
        } catch (e: JSchException) {
            Log.e(TAG, "JSch error: ${e.message}", e)
            val errMsg = e.message ?: ""
            return when {
                errMsg.contains("Auth fail", ignoreCase = true) ||
                errMsg.contains("authentication", ignoreCase = true) ||
                errMsg.contains("password", ignoreCase = true) ->
                    TunnelResult(false, "Ошибка авторизации SSH. Проверьте логин и пароль.", TunnelErrorType.AUTH_FAILED)
                errMsg.contains("Connection refused", ignoreCase = true) ->
                    TunnelResult(false, "Соединение отклонено на порту $sshPort. Проверьте SSH хост и порт.", TunnelErrorType.CONNECTION_REFUSED)
                errMsg.contains("timeout", ignoreCase = true) ->
                    TunnelResult(false, "Таймаут подключения к $sshHost:$sshPort. Проверьте хост и порт.", TunnelErrorType.TIMEOUT)
                errMsg.contains("connect", ignoreCase = true) ->
                    TunnelResult(false, "Не удалось подключиться к $sshHost:$sshPort", TunnelErrorType.CONNECTION_REFUSED)
                else ->
                    TunnelResult(false, "SSH ошибка: $errMsg", TunnelErrorType.GENERIC)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create tunnel: ${e.message}", e)
            val errMsg = e.message ?: ""
            return when {
                errMsg.contains("already in use", ignoreCase = true) ->
                    TunnelResult(false, "Локальный порт $localPort уже занят. Выберите другой порт.", TunnelErrorType.PORT_IN_USE)
                errMsg.contains("Address already in use", ignoreCase = true) ->
                    TunnelResult(false, "Локальный порт $localPort уже занят. Выберите другой порт.", TunnelErrorType.PORT_IN_USE)
                errMsg.contains("UnknownHostException", ignoreCase = true) ->
                    TunnelResult(false, "Не удалось найти хост «${sshHost}». Введите IP адрес.", TunnelErrorType.UNKNOWN_HOST)
                else ->
                    TunnelResult(false, "Ошибка: $errMsg", TunnelErrorType.GENERIC)
            }
        }
    }

    /**
     * Проверяет активен ли туннель.
     */
    fun isTunnelActive(): Boolean {
        return try {
            val active = session?.isConnected == true
            if (!active) session = null
            active
        } catch (e: Exception) {
            session = null
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
            sshPassword = prefs.getString(PREF_SSH_PASSWORD, "") ?: "",
            serverHost = prefs.getString(PREF_SERVER_HOST, "localhost") ?: "localhost",
            serverPort = prefs.getInt(PREF_SERVER_PORT, 50051),
            localPort = prefs.getInt(PREF_LOCAL_PORT, 50052),
            autoConnect = prefs.getBoolean(PREF_AUTO_CONNECT, false)
        )
    }

    /**
     * Устанавливает пароль SSH.
     */
    fun setSshPassword(password: String) {
        prefs.edit().putString(PREF_SSH_PASSWORD, password).apply()
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
    val sshPassword: String = "",
    val serverHost: String = "localhost",
    val serverPort: Int = 50051,
    val localPort: Int = 50052,
    val autoConnect: Boolean = false
)
