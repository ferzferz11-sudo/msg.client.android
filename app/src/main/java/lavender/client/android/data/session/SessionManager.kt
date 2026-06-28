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
import lavender.client.android.data.proto.AuthResponseV2Proto
import lavender.client.android.data.proto.RefreshTokenResponseProto
import lavender.client.android.data.auth.AuthManager
import android.os.Build
import lavender.client.android.data.grpc.*

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
                    stopTokenRefresh()
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

    /**
     * Starts periodic JWT token refresh check.
     * Runs every 60 seconds. If the access token is about to expire
     * (within 5 minutes), silently refreshes it using the refresh token.
     *
     * Only active when user is authenticated via JWT (v2).
     * For legacy v1 auth this is a no-op.
     */
    private var tokenRefreshJob: Job? = null

    fun startTokenRefresh(context: Context) {
        tokenRefreshJob?.cancel()
        tokenRefreshJob = scope.launch {
            while (isActive) {
                try {
                    if (AuthManager.isJwtAuthenticated(context) && AuthManager.needsRefresh(context)) {
                        Log.d("SessionManager", "Proactive token refresh triggered")
                        performTokenRefresh(context)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("SessionManager", "Token refresh check error: ${e.message}")
                }
                delay(60_000) // check every 60 seconds
            }
        }
    }

    fun stopTokenRefresh() {
        tokenRefreshJob?.cancel()
        tokenRefreshJob = null
    }

    /**
     * Performs a synchronous token refresh using the stored refresh token.
     * Called from the periodic refresh job.
     */
    private suspend fun performTokenRefresh(context: Context) {
        val refreshToken = AuthManager.getRefreshToken(context) ?: return
        Log.d("SessionManager", "Refreshing JWT token...")

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
            // Store new token pair
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

            // Update session state
            _session.value = _session.value.copy(
                accessToken = result.accessToken,
                refreshToken = result.refreshToken
            )

            Log.d("SessionManager", "Token refreshed successfully, new access_expires=${result.accessExpiresAt}")
        }
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
            // Check if we have JWT tokens
            if (AuthManager.isJwtAuthenticated(context)) {
                // Validate that the JWT tokens were issued for this server
                val jwtServer = CredentialStore.getJwtServerAddress(context)
                if (jwtServer.isNotEmpty() && jwtServer != serverAddress) {
                    // Server has changed — clear stale JWT tokens
                    Log.w("SessionManager", "Server changed (was=$jwtServer, now=$serverAddress), clearing stale JWT tokens")
                    AuthManager.clearTokens(context)
                    updateSession(username = username, password = password, userId = userId, email = email)
                    _session.value = _session.value.copy(authMethod = AuthManager.getAuthMethod(context))
                } else {
                    Log.d("SessionManager", "Restoring JWT session for $username (server=$jwtServer)")
                    val jwtUserId = AuthManager.getUserId(context)
                    val jwtUsername = AuthManager.getUsername(context)
                    val deviceId = AuthManager.getDeviceId(context)

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
                }
            } else {
                updateSession(username = username, password = password, userId = userId, email = email)
                _session.value = _session.value.copy(authMethod = AuthManager.getAuthMethod(context))
            }

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
        // Clear any stale JWT tokens before starting a new login.
        // This prevents token conflicts when switching between servers (prod/dev)
        // or when the previous session's tokens are expired.
        AuthManager.clearTokens(context)

        // Try AuthService v2 (JWT) first
        loginV2(context, username, pass, serverAddress, register, email, onComplete)
    }

    /**
     * AuthService v2 login — uses JWT tokens.
     * Falls back to v1 (legacy Chat stream auth) if v2 is not available.
     */
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

        // Force reconnect for clean auth
        GrpcClient.disconnect()
        GrpcClient.connect(host, useTls = false, port = port, context = context, forceReconnect = true)

        scope.launch {
            try {
                // Wait for READY or FAILED status
                val status = withTimeoutOrNull(10000) {
                    GrpcClient.connectionStatus.filter {
                        (it == ConnectionStatus.READY) || (it == ConnectionStatus.FAILED)
                    }.first()
                }

                if (status != ConnectionStatus.READY) {
                    // Connection failed — fallback to v1
                    Log.w("SessionManager", "V2: connection failed, falling back to v1")
                    @Suppress("DEPRECATION")
                    loginV1(context, username, pass, serverAddress, register, email, onComplete)
                    return@launch
                }

                val deviceId = getDeviceId(context)
                val deviceName = getDeviceName()
                val clientVersion = lavender.client.android.BuildConfig.VERSION_NAME

                // Call SignInV2 or SignUpV2
                val v2Callback = if (register) {
                    { cb: (AuthResponseV2Proto?, String?) -> Unit ->
                        GrpcClient.signUpV2(username, pass, email, deviceId, deviceName, "android", clientVersion, cb)
                    }
                } else {
                    { cb: (AuthResponseV2Proto?, String?) -> Unit ->
                        GrpcClient.signInV2(username, pass, deviceId, deviceName, "android", clientVersion, cb)
                    }
                }

                // Wait for auth response
                val authResult = withTimeoutOrNull(10000) {
                    suspendCancellableCoroutine<AuthResponseV2Proto?> { cont ->
                        v2Callback { response, error ->
                            if (cont.isActive) {
                                if (response != null && response.success) {
                                    cont.resumeWith(Result.success(response))
                                } else {
                                    cont.resumeWith(Result.success(null)) // v2 failed, will fallback
                                }
                            }
                        }
                    }
                }

                if (authResult != null && authResult.success) {
                    // V2 auth successful — store JWT tokens
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

                    // Also store credentials for backward compat
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

                    // Track which server issued these JWT tokens
                    CredentialStore.setJwtServerAddress(context, serverAddress)

                    try { syncFcmToken(context, username) } catch (e: Exception) { }

                    GrpcClient.getUserAvatar(username) { _ -> }

                    // Start proactive token refresh for JWT sessions
                    startTokenRefresh(context)

                    onComplete("SUCCESS")
                } else {
                    // V2 auth failed — fallback to v1
                    val errorMsg = "V2 not available"
                    Log.w("SessionManager", "$errorMsg, falling back to v1")
                    @Suppress("DEPRECATION")
                    loginV1(context, username, pass, serverAddress, register, email, onComplete)
                }
            } catch (e: Exception) {
                Log.e("SessionManager", "V2 login error: ${e.message}, falling back to v1")
                @Suppress("DEPRECATION")
                loginV1(context, username, pass, serverAddress, register, email, onComplete)
            }
        }
    }

    /**
     * AuthService v1 login — legacy Chat stream auth (deprecated but functional).
     */
    @Deprecated("Use loginV2 instead")
    private fun loginV1(context: Context, username: String, pass: String, serverAddress: String, register: Boolean = false, email: String = "", onComplete: (String?) -> Unit) {
        Log.d("SessionManager", "LoginV1 (legacy) for $username at $serverAddress (register=$register)")
        val parts = serverAddress.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051

        // Mark as legacy auth
        AuthManager.setLegacyAuth(context)

        // Force reconnect
        GrpcClient.disconnect()
        GrpcClient.connect(host, useTls = false, port = port, context = context, forceReconnect = true)

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

                    Log.d("SessionManager", "V1 auth result: $authResult, register=$register")

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
                        _session.value = _session.value.copy(authMethod = "v1_legacy")

                        try {
                            syncFcmToken(context, username)
                        } catch (e: Exception) {
                            Log.e("SessionManager", "syncFcmToken error: ${e.message}")
                        }

                        // Fetch user avatar
                        GrpcClient.getUserAvatar(username) { avatarUrl ->
                            Log.d("SessionManager", "getUserAvatar callback: url=$avatarUrl")
                        }

                        Log.d("SessionManager", "V1 login complete, calling onComplete with: ${authResult ?: "SUCCESS"}")
                        onComplete(authResult ?: "SUCCESS")
                    } else {
                        onComplete(authResult)
                    }
                } else {
                    onComplete("CONNECTION_FAILED")
                }
            } catch (e: Exception) {
                Log.e("SessionManager", "V1 login error: ${e.message}", e)
                onComplete("ERROR")
            }
        }
    }

    fun logout(context: Context) {
        Log.d("SessionManager", "Logging out, clearing credentials, resetting to prod server")
        val currentUsername = _session.value.username
        _session.value = UserSession(username = currentUsername)

        // Stop token refresh
        stopTokenRefresh()

        // Clear JWT tokens
        AuthManager.clearTokens(context)

        // Clear encrypted credentials (password, tokens, etc.)
        CredentialStore.clear(context)

        // Always reset to prod server on logout
        CredentialStore.setServerAddress(context, ServerConfig.PROD.grpcAddress)

        // Clear non-sensitive legacy prefs but keep username for pre-fill
        CredentialStore.getLegacyPrefs(context).edit {
            remove("password")
            remove("user_id")
            remove("chat_list_version")
            // Save username for pre-fill on next login
            putString("last_username", currentUsername)
        }

        GrpcClient.disconnect()
    }
}
