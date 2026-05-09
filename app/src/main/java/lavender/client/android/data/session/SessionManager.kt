package lavender.client.android.data.session

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient

object SessionManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _session = MutableStateFlow(UserSession())
    val session: StateFlow<UserSession> = _session.asStateFlow()

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
                        fullAvatarUrl = fullAvatarUrl
                    )
                }
            }
        }
    }

    fun updateSession(
        username: String? = null,
        password: String? = null,
        userId: String? = null,
        avatarUrl: String? = null,
        fullAvatarUrl: String? = null
    ) {
        _session.value = _session.value.copy(
            username = username ?: _session.value.username,
            password = password ?: _session.value.password,
            userId = userId ?: _session.value.userId,
            avatarUrl = avatarUrl ?: _session.value.avatarUrl,
            fullAvatarUrl = fullAvatarUrl ?: _session.value.fullAvatarUrl
        )
        
        // Sync to GrpcClient internal state if needed
        userId?.let { GrpcClient.setUserId(it) }
    }

    fun login(context: Context, username: String, pass: String, serverAddress: String, onComplete: (Boolean) -> Unit) {
        val parts = serverAddress.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
        
        GrpcClient.connect(host, false, port, context)
        
        scope.launch {
            // Wait for connection
            GrpcClient.connectionStatus.collect { status ->
                if (status == ConnectionStatus.READY) {
                    // Try to fetch User ID first
                    GrpcClient.fetchUserId(username) { id, success ->
                        val finalId = if (success) id else ""
                        updateSession(username = username, password = pass, userId = finalId)
                        
                        // Perform the actual startChat (auth signal)
                        GrpcClient.startChat(username, pass, "") {
                            // Auth successful
                        }
                        onComplete(true)
                    }
                    return@collect
                } else if (status == ConnectionStatus.FAILED) {
                    onComplete(false)
                    return@collect
                }
            }
        }
    }

    fun logout() {
        _session.value = UserSession()
        GrpcClient.disconnect()
    }

    fun initFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("username", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        val userId = prefs.getString("user_id", "") ?: ""
        if (username.isNotEmpty()) {
            updateSession(username = username, password = password, userId = userId)
        }
    }

    fun refreshProfile() {
        val currentUsername = _session.value.username
        if (currentUsername.isNotEmpty()) {
            GrpcClient.getUserAvatar(currentUsername) { /* Cache updated in GrpcClient */ }
            // Fetch ID if missing
            if (_session.value.userId.isEmpty()) {
                GrpcClient.fetchUserId(currentUsername) { id, success ->
                    if (success && id != null) {
                        updateSession(userId = id)
                    }
                }
            }
        }
    }
}
