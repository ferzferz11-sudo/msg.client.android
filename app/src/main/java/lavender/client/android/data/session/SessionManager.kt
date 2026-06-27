package lavender.client.android.data.session

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import lavender.client.android.data.auth.AuthManager
import lavender.client.android.data.grpc.ChatKeepAliveService
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.proto.AuthResponseV2Proto
import lavender.client.android.data.proto.RefreshTokenResponseProto

object SessionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _session = MutableStateFlow(UserSession())
    val session: StateFlow<UserSession> = _session.asStateFlow()

    private val _logoutEvent = MutableSharedFlow<Unit>(replay = 0)
    val logoutEvent: SharedFlow<Unit> = _logoutEvent.asSharedFlow()

    private var deviceUpdateJob: Job? = null

    init {
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
                    stopTokenRefresh()
                    _session.value = UserSession()
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
        _session.value = _session.value.copy(deviceId = deviceId, deviceName = deviceName)
    }

    fun startPeriodicDeviceUpdate(context: Context) {
        deviceUpdateJob?.cancel()
        deviceUpdateJob = scope.launch {
            while (isActive) {
                syncDeviceToServer(context)
                delay(3 * 60 * 1000)
            }
        }
    }

    fun stopPeriodicDeviceUpdate() {
        deviceUpdateJob?.cancel()
        deviceUpdateJob = null
    }

    private var tokenRefreshJob: Job? = null

    fun startTokenRefresh(context: Context) {
        tokenRefreshJob?.cancel()
        tokenRefreshJob = scope.launch {
            while (isActive) {
                try {
                    if (AuthManager.isJwtAuthenticated(context) && AuthManager.needsRefresh(context)) {
                        performTokenRefresh(context)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("SessionManager", "Token refresh check error: ${e.message}")
                }
                delay(60_000)
            }
        }
    }

    fun stopTokenRefresh() {
        tokenRefreshJob?.cancel()
        tokenRefreshJob = null
    }

    /**
     * Synchronous token refresh — called before chat stream to ensure fresh JWT.
     * Blocks up to 5 seconds. Safe to call from non-suspend context.
     */
    fun ensureFreshToken(context: Context) {
        if (!AuthManager.isTokenExpiredOrExpiring(context)) return
        val refreshToken = AuthManager.getRefreshToken(context) ?: return
        Log.d("SessionManager", "Token expired, refreshing synchronously...")

        val latch = java.util.concurrent.CountDownLatch(1)
        var refreshed = false

        GrpcClient.refreshToken(refreshToken) { response, error ->
            if (response != null && response.accessToken.isNotEmpty()) {
                val currentUsername = AuthManager.getUsername(context)
                val currentUserId = AuthManager.getUserId(context)
                val currentDeviceId = AuthManager.getDeviceId(context)

                AuthManager.storeTokens(
                    context = context,
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    accessExpiresAt = response.accessExpiresAt,
                    refreshExpiresAt = response.refreshExpiresAt,
                    userId = currentUserId,
                    username = currentUsername,
                    deviceId = currentDeviceId
                )
                _session.value = _session.value.copy(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken
                )
                refreshed = true
            } else {
                Log.w("SessionManager", "Sync token refresh failed: $error")
            }
            latch.countDown()
        }

        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        if (!refreshed) Log.w("SessionManager", "Sync token refresh timed out")
    }

    /**
     * Force token refresh — ignores expiration check.
     * Used by pull-to-refresh to guarantee fresh JWT.
     */
    fun forceTokenRefresh(context: Context) {
        val refreshToken = AuthManager.getRefreshToken(context) ?: return
        Log.d("SessionManager", "Force refreshing token...")

        val latch = java.util.concurrent.CountDownLatch(1)
        var refreshed = false

        GrpcClient.refreshToken(refreshToken) { response, error ->
            if (response != null && response.accessToken.isNotEmpty()) {
                val currentUsername = AuthManager.getUsername(context)
                val currentUserId = AuthManager.getUserId(context)
                val currentDeviceId = AuthManager.getDeviceId(context)

                AuthManager.storeTokens(
                    context = context,
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    accessExpiresAt = response.accessExpiresAt,
                    refreshExpiresAt = response.refreshExpiresAt,
                    userId = currentUserId,
                    username = currentUsername,
                    deviceId = currentDeviceId
                )
                _session.value = _session.value.copy(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken
                )
                refreshed = true
                Log.d("SessionManager", "Force token refresh succeeded")
            } else {
                Log.w("SessionManager", "Force token refresh failed: $error")
            }
            latch.countDown()
        }

        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        if (!refreshed) Log.w("SessionManager", "Force token refresh timed out")
    }

    private suspend fun performTokenRefresh(context: Context) {
        val refreshToken = AuthManager.getRefreshToken(context) ?: return

        val result = suspendCancellableCoroutine<RefreshTokenResponseProto?> { cont ->
            GrpcClient.refreshToken(refreshToken) { response, error ->
                if (cont.isActive) {
                    if (response != null && response.accessToken.isNotEmpty()) {
                        cont.resumeWith(Result.success(response))
                    } else {
                        Log.w("SessionManager", "Token refresh failed: $error")
                        cont.resumeWith(Result.success(null))
                    }
                }
            }
        }

        if (result != null && result.accessToken.isNotEmpty()) {
            val currentUsername = AuthManager.getUsername(context)
            val currentUserId = AuthManager.getUserId(context)
            val currentDeviceId = AuthManager.getDeviceId(context)

            AuthManager.storeTokens(
                context = context,
                accessToken = result.accessToken,
                refreshToken = result.refreshToken,
                accessExpiresAt = result.accessExpiresAt,
                refreshExpiresAt = result.refreshExpiresAt,
                userId = currentUserId,
                username = currentUsername,
                deviceId = currentDeviceId
            )

            _session.value = _session.value.copy(
                accessToken = result.accessToken,
                refreshToken = result.refreshToken
            )
        } else if (AuthManager.isRefreshTokenExpired(context)) {
            Log.w("SessionManager", "Refresh token expired — attempting re-login with saved password")
            val username = AuthManager.getUsername(context)
            val password = CredentialStore.getPassword(context)
            val serverAddress = CredentialStore.getServerAddress(context)
            if (username.isNotEmpty() && password.isNotEmpty() && serverAddress.isNotEmpty()) {
                loginV2(context, username, password, serverAddress, false, "", {})
            } else {
                Log.w("SessionManager", "No saved credentials for re-login")
                _session.value = UserSession()
                _logoutEvent.emit(Unit)
            }
        }
    }

    private fun syncDeviceToServer(context: Context) {
        val currentSession = _session.value
        if (currentSession.isLoggedIn) {
            GrpcClient.startChatV2("") { /* ignore */ }
        }
    }

    fun initFromPrefs(context: Context) {
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
            if (AuthManager.isJwtAuthenticated(context)) {
                val jwtServer = CredentialStore.getJwtServerAddress(context)
                if (jwtServer.isNotEmpty() && jwtServer != serverAddress) {
                    Log.w("SessionManager", "Server changed (was=$jwtServer, now=$serverAddress), clearing stale JWT tokens")
                    AuthManager.clearTokens(context)
                    updateSession(username = username, password = password, userId = userId, email = email)
                } else {
                    val jwtUserId = AuthManager.getUserId(context)
                    val jwtUsername = AuthManager.getUsername(context)
                    val deviceId = AuthManager.getDeviceId(context)

                    if (AuthManager.isTokenExpiredOrExpiring(context) && password.isNotEmpty()) {
                        Log.d("SessionManager", "JWT expired on startup — will re-login with saved password after connection")
                        updateSession(username = username, password = password, userId = userId, email = email)
                } else {
                    Log.d("SessionManager", "Restoring JWT session for $username (server=$jwtServer)")
                    updateSession(
                        username = jwtUsername.ifEmpty { username },
                        password = password,
                        userId = jwtUserId.ifEmpty { userId },
                        email = email
                    )
                    _session.value = _session.value.copy(
                        accessToken = AuthManager.getAccessToken(context) ?: "",
                        refreshToken = AuthManager.getRefreshToken(context) ?: "",
                        authMethod = "v2_jwt",
                        deviceId = deviceId
                    )
                    startTokenRefresh(context)
                }
                }
            } else {
                updateSession(username = username, password = password, userId = userId, email = email)
            }

            if (serverAddress.isNotEmpty() && GrpcClient.connectionStatus.value == ConnectionStatus.DISCONNECTED) {
                val parts = serverAddress.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
                GrpcClient.connect(host, useTls = false, port = port, context = context)

                ChatKeepAliveService.start(context)

                if (AuthManager.isTokenExpiredOrExpiring(context) && password.isNotEmpty()) {
                    waitForConnectionAndReLogin(context, username, password, serverAddress)
                }

                syncFcmToken(context, username)
            }
        }
    }

    private fun waitForConnectionAndReLogin(context: Context, username: String, password: String, serverAddress: String) {
        scope.launch {
            val status = withTimeoutOrNull(10000) {
                GrpcClient.connectionStatus.filter {
                    it == ConnectionStatus.READY || it == ConnectionStatus.FAILED
                }.first()
            }
            if (status != ConnectionStatus.READY) {
                Log.w("SessionManager", "Re-login: connection failed")
                return@launch
            }

            Log.d("SessionManager", "Re-login: connection ready, refreshing tokens for $username")
            val refreshToken = AuthManager.getRefreshToken(context)
            if (!refreshToken.isNullOrEmpty()) {
                val latch = java.util.concurrent.CountDownLatch(1)
                GrpcClient.refreshToken(refreshToken) { response, error ->
                    if (response != null && response.accessToken.isNotEmpty()) {
                        val deviceId = AuthManager.getDeviceId(context)
                        AuthManager.storeTokens(
                            context = context,
                            accessToken = response.accessToken,
                            refreshToken = response.refreshToken,
                            accessExpiresAt = response.accessExpiresAt,
                            refreshExpiresAt = response.refreshExpiresAt,
                            userId = AuthManager.getUserId(context),
                            username = AuthManager.getUsername(context),
                            deviceId = deviceId
                        )
                        _session.value = _session.value.copy(
                            accessToken = response.accessToken,
                            refreshToken = response.refreshToken
                        )
                        startTokenRefresh(context)
                        Log.d("SessionManager", "Re-login: tokens refreshed successfully")
                    } else {
                        Log.w("SessionManager", "Re-login: refresh failed ($error), re-authenticating with password")
                        val parts = serverAddress.split(":")
                        val host = parts[0]
                        val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
                        GrpcClient.disconnect()
                        GrpcClient.connect(host, false, port, context, forceReconnect = true)
                        loginV2(context, username, password, serverAddress, false, "", {})
                    }
                    latch.countDown()
                }
                latch.await(8, java.util.concurrent.TimeUnit.SECONDS)
            } else {
                Log.w("SessionManager", "Re-login: no refresh token, re-authenticating with password")
                loginV2(context, username, password, serverAddress, false, "", {})
            }
        }
    }

    fun syncFcmToken(context: Context, username: String) {
        val prefs = CredentialStore.getLegacyPrefs(context)
        val sendEnabled = prefs.getBoolean("push_send_enabled", true)
        val receiveEnabled = prefs.getBoolean("push_receive_enabled", true)

        @Suppress("DEPRECATION") // No non-deprecated alternative for FCM token retrieval
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = if (receiveEnabled) task.result else "DISABLED"
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

        userId?.let { GrpcClient.setUserId(it) }
    }

    fun login(context: Context, username: String, pass: String, serverAddress: String, register: Boolean = false, email: String = "", onComplete: (String?) -> Unit) {
        AuthManager.clearTokens(context)
        loginV2(context, username, pass, serverAddress, register, email, onComplete)
    }

    private fun loginV2(
        context: Context,
        username: String,
        pass: String,
        serverAddress: String,
        register: Boolean,
        email: String,
        onComplete: (String?) -> Unit
    ) {
        Log.d("SessionManager", "LoginV2 attempt for $username at $serverAddress (register=$register)")
        val parts = serverAddress.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051

        GrpcClient.disconnect()
        GrpcClient.connect(host, useTls = false, port = port, context = context, forceReconnect = true)

        scope.launch {
            try {
                val status = withTimeoutOrNull(10000) {
                    GrpcClient.connectionStatus.filter {
                        (it == ConnectionStatus.READY) || (it == ConnectionStatus.FAILED)
                    }.first()
                }

                if (status != ConnectionStatus.READY) {
                    Log.w("SessionManager", "V2: connection failed")
                    onComplete("CONNECTION_FAILED")
                    return@launch
                }

                val deviceId = getDeviceId(context)
                val deviceName = getDeviceName()
                val clientVersion = lavender.client.android.BuildConfig.VERSION_NAME

                val v2Callback = if (register) {
                    { cb: (AuthResponseV2Proto?, String?) -> Unit ->
                        GrpcClient.signUpV2(username, pass, email, deviceId, deviceName, "android", clientVersion, cb)
                    }
                } else {
                    { cb: (AuthResponseV2Proto?, String?) -> Unit ->
                        GrpcClient.signInV2(username, pass, deviceId, deviceName, "android", clientVersion, cb)
                    }
                }

                val authResult = withTimeoutOrNull(10000) {
                    suspendCancellableCoroutine<AuthResponseV2Proto?> { cont ->
                        v2Callback { response, error ->
                            if (cont.isActive) {
                                if (response != null && response.success) {
                                    cont.resumeWith(Result.success(response))
                                } else {
                                    cont.resumeWith(Result.success(null))
                                }
                            }
                        }
                    }
                }

                if (authResult != null && authResult.success) {
                    Log.d("SessionManager", "V2 auth success for ${authResult.username}")

                    AuthManager.storeTokens(
                        context = context,
                        accessToken = authResult.accessToken,
                        refreshToken = authResult.refreshToken,
                        accessExpiresAt = authResult.accessExpiresAt,
                        refreshExpiresAt = authResult.refreshExpiresAt,
                        userId = authResult.userId,
                        username = authResult.username,
                        deviceId = deviceId
                    )

                    CredentialStore.setCredentials(
                        context = context,
                        username = username,
                        password = pass,
                        userId = authResult.userId,
                        email = authResult.email,
                        serverAddress = serverAddress
                    )

                    updateSession(
                        username = authResult.username,
                        password = pass,
                        userId = authResult.userId,
                        email = authResult.email
                    )
                    _session.value = _session.value.copy(
                        accessToken = authResult.accessToken,
                        refreshToken = authResult.refreshToken,
                        authMethod = "v2_jwt"
                    )

                    CredentialStore.setJwtServerAddress(context, serverAddress)

                    try { syncFcmToken(context, username) } catch (e: Exception) { }

                    GrpcClient.getUserAvatar(username) { _ -> }

                    startTokenRefresh(context)

                    ChatKeepAliveService.start(context)

                    onComplete("SUCCESS")
                } else {
                    Log.w("SessionManager", "AUTH_FAILED")
                    onComplete("AUTH_FAILED")
                }
            } catch (e: Exception) {
                Log.e("SessionManager", "V2 login error: ${e.message}", e)
                onComplete("ERROR")
            }
        }
    }

    fun logout(context: Context) {
        Log.d("SessionManager", "Logging out, clearing credentials, resetting to prod server")
        val currentUsername = _session.value.username
        _session.value = UserSession(username = currentUsername)

        stopTokenRefresh()
        ChatKeepAliveService.stop(context)
        GrpcClient.clearLastChatRequestPrefs()
        AuthManager.clearTokens(context)
        CredentialStore.clear(context)
        CredentialStore.setServerAddress(context, lavender.client.android.data.ServerConfig.PROD_SERVER_ADDRESS)

        CredentialStore.getLegacyPrefs(context).edit {
            remove("password")
            remove("user_id")
            remove("chat_list_version")
            putString("last_username", currentUsername)
        }

        context.getSharedPreferences("lavender_prefs", android.content.Context.MODE_PRIVATE)
            .edit().remove("is_super_admin").remove("admin_user_id").apply()

        GrpcClient.disconnect()
    }
}
