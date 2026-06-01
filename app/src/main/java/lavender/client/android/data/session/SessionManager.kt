package lavender.client.android.data.session

import android.content.Context
import android.util.Log
import android.provider.Settings
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import android.os.Build

object SessionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _session = MutableStateFlow(UserSession())
    val session: StateFlow<UserSession> = _session.asStateFlow()

    private val _logoutEvent = MutableSharedFlow<Unit>(replay = 0)
    val logoutEvent: SharedFlow<Unit> = _logoutEvent.asSharedFlow()

    private var deviceUpdateJob: Job? = null

    init {
        // Sync isSuperAdmin and other global flags from GrpcClient to session
        scope.launch {
            GrpcClient.isSuperAdmin.collect { isAdmin ->
                _session.value = _session.value.copy(isSuperAdmin = isAdmin)
            }
        }

        scope.launch {
            GrpcClient.avatarCacheFlow.collect { cache ->
                val currentUsername = _session.value.username
                if (currentUsername.isNotEmpty() && cache.containsKey(currentUsername)) {
                    val avatarUrl = cache[currentUsername] ?: ""
                    val fullAvatarUrl = GrpcClient.getFullAvatarUrl(currentUsername) ?: ""
                    _session.value = _session.value.copy(
                        avatarUrl = avatarUrl,
                        fullAvatarUrl = fullAvatarUrl,
                    )
                }
            }
        }

        scope.launch {
            GrpcClient.authStatus.collect { status ->
                if (status == "FORCE_LOGOUT") {
                    _session.value = UserSession() // Clear session state
                    _logoutEvent.emit(Unit)
                }
            }
        }
    }

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) {
            model.replaceFirstChar { it.uppercase() }
        } else {
            "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
        }
    }

    fun updateDeviceInfo(context: Context) {
        val deviceId = getDeviceId(context)
        val deviceName = getDeviceName()
        _session.value = _session.value.copy(
            deviceId = deviceId,
            deviceName = deviceName
        )
    }

    fun startPeriodicDeviceUpdate(context: Context) {
        deviceUpdateJob?.cancel()
        deviceUpdateJob = scope.launch {
            while (isActive) {
                syncDeviceToServer(context)
                delay(3 * 60 * 1000) // 3 minutes
            }
        }
    }

    fun stopPeriodicDeviceUpdate() {
        deviceUpdateJob?.cancel()
        deviceUpdateJob = null
    }

    private fun syncDeviceToServer(context: Context) {
        val currentSession = _session.value
        if (currentSession.isLoggedIn) {
            Log.d("SessionManager", "Syncing device info to server: ${currentSession.deviceName}")
            GrpcClient.startChat(
                currentSession.username,
                currentSession.password,
                "", // empty join message for updates
                false,
                "",
                currentSession.deviceId,
                currentSession.deviceName,
                onMessageReceived = {}
            )
        }
    }

    fun initFromPrefs(context: Context) {
        // Migrate legacy credentials if needed
        if (CredentialStore.needsMigration(context)) {
            Log.d("SessionManager", "Migrating legacy credentials to encrypted storage")
        }

        val username = CredentialStore.getUsername(context)
        val password = CredentialStore.getPassword(context)
        val userId = CredentialStore.getUserId(context)
        val email = CredentialStore.getEmail(context)
        val serverAddress = CredentialStore.getServerAddress(context)

        updateDeviceInfo(context)

        if (username.isNotEmpty()) {
            updateSession(username = username, password = password, userId = userId, email = email)

            // Reconnect if needed
            if (serverAddress.isNotEmpty() && GrpcClient.connectionStatus.value == ConnectionStatus.DISCONNECTED) {
                val parts = serverAddress.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
                GrpcClient.connect(host, useTls = false, port = port, context = context)

                // Sync FCM token on start
                syncFcmToken(context, username)
            }
        }
    }

    fun syncFcmToken(context: Context, username: String) {
        val prefs = CredentialStore.getLegacyPrefs(context)
        val sendEnabled = prefs.getBoolean("push_send_enabled", true)
        val receiveEnabled = prefs.getBoolean("push_receive_enabled", true)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = if (receiveEnabled) task.result else "DISABLED"
                Log.d("SessionManager", "Syncing FCM token for $username: $token")
                GrpcClient.registerToken(username, token, sendEnabled)
            } else {
                Log.e("SessionManager", "Failed to get FCM token", task.exception)
            }
        }
    }

    fun updateSession(
        username: String? = null,
        password: String? = null,
        userId: String? = null,
        avatarUrl: String? = null,
        fullAvatarUrl: String? = null,
        email: String? = null
    ) {
        _session.value = _session.value.copy(
            username = username ?: _session.value.username,
            password = password ?: _session.value.password,
            userId = userId ?: _session.value.userId,
            avatarUrl = avatarUrl ?: _session.value.avatarUrl,
            fullAvatarUrl = fullAvatarUrl ?: _session.value.fullAvatarUrl,
            email = email ?: _session.value.email
        )

        // Sync to GrpcClient internal state if needed
        userId?.let { GrpcClient.setUserId(it) }
    }

    fun login(context: Context, username: String, pass: String, serverAddress: String, register: Boolean = false, email: String = "", onComplete: (String?) -> Unit) {
        Log.d("SessionManager", "Login attempt for $username at $serverAddress (register=$register)")
        val parts = serverAddress.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051

        GrpcClient.connect(host, useTls = false, port = port, context = context)

        scope.launch {
            try {
                // Wait for READY or FAILED status
                val status = withTimeoutOrNull(10000) {
                    GrpcClient.connectionStatus.filter {
                        (it == ConnectionStatus.READY) || (it == ConnectionStatus.FAILED)
                    }.first()
                }

                if (status == ConnectionStatus.READY) {
                    // Start chat with auth signal
                    val deviceId = getDeviceId(context)
                    val deviceName = getDeviceName()
                    GrpcClient.startChat(username, pass, "", register, email, deviceId, deviceName) { }

                    // Wait for auth status from server
                    val authResult = withTimeoutOrNull(5000) {
                        GrpcClient.authStatus.filter { it != null }.first()
                    }

                    Log.d("SessionManager", "Auth result received: $authResult, register=$register")

                    if (authResult == "REGISTRATION_SUCCESS" || authResult == null || authResult == "SUCCESS") {
                        // Try to fetch User ID
                        val userIdDeferred = CompletableDeferred<String?>()
                        GrpcClient.fetchUserId(username) { id, success ->
                            Log.d("SessionManager", "fetchUserId callback: id=$id, success=$success")
                            if (success) userIdDeferred.complete(id)
                            else userIdDeferred.complete(null)
                        }

                        val fetchedId = withTimeoutOrNull(3000) { userIdDeferred.await() }
                        Log.d("SessionManager", "fetchedId=$fetchedId")
                        val userId = fetchedId ?: ""

                        // Store credentials securely
                        CredentialStore.setCredentials(
                            context = context,
                            username = username,
                            password = pass,
                            userId = userId,
                            email = email,
                            serverAddress = serverAddress
                        )

                        updateSession(username = username, password = pass, userId = userId, email = email)

                        try {
                            syncFcmToken(context, username)
                        } catch (e: Exception) {
                            Log.e("SessionManager", "syncFcmToken error: ${e.message}")
                        }
                        Log.d("SessionManager", "Registration complete, calling onComplete with: ${authResult ?: "SUCCESS"}")
                        onComplete(authResult ?: "SUCCESS")
                    } else {
                        onComplete(authResult)
                    }
                } else {
                    onComplete("CONNECTION_FAILED")
                }
            } catch (e: Exception) {
                Log.e("SessionManager", "Critical login error: ${e.message}", e)
                onComplete("ERROR")
            }
        }
    }

    fun logout(context: Context) {
        Log.d("SessionManager", "Logging out, clearing all user data")
        _session.value = UserSession()

        // Clear encrypted credentials
        CredentialStore.clear(context)

        // Clear non-sensitive legacy prefs (theme, push settings, etc.)
        CredentialStore.getLegacyPrefs(context).edit {
            remove("username")
            remove("password")
            remove("user_id")
            remove("chat_list_version")
        }

        GrpcClient.disconnect()
    }
}
