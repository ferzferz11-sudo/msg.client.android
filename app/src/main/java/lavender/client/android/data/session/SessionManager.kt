package lavender.client.android.data.session

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
                        fullAvatarUrl = fullAvatarUrl,
                    )
                }
            }
        }
    }

    fun initFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("username", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        val userId = prefs.getString("user_id", "") ?: ""
        val serverAddress = prefs.getString("server_address", "") ?: ""
        
        if (username.isNotEmpty()) {
            updateSession(username = username, password = password, userId = userId)
            
            // Reconnect if needed
            if (serverAddress.isNotEmpty() && GrpcClient.connectionStatus.value == ConnectionStatus.DISCONNECTED) {
                val parts = serverAddress.split(":")
                val host = parts[0]
                val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
                GrpcClient.connect(host, useTls = false, port = port, context = context)
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
        Log.d("SessionManager", "Login attempt for $username at $serverAddress")
        val parts = serverAddress.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
        
        GrpcClient.connect(host, useTls = false, port = port, context = context)
        
        scope.launch {
            try {
                // Wait for READY or FAILED status with a longer timeout
                Log.d("SessionManager", "Waiting for connection status (current: ${GrpcClient.connectionStatus.value})")
                val status = withTimeoutOrNull(15000) {
                    GrpcClient.connectionStatus.filter { 
                        Log.d("SessionManager", "Status update observed: $it")
                        (it == ConnectionStatus.READY) || (it == ConnectionStatus.FAILED)
                    }.first()
                }

                if (status == ConnectionStatus.READY) {
                    Log.d("SessionManager", "Connection established, performing auth handshake")
                    
                    // Start chat immediately to establish auth signal
                    GrpcClient.startChat(username, pass, "") {
                        // Auth successful or message received
                    }

                    // Try to fetch User ID using a Deferred
                    val userIdDeferred = CompletableDeferred<String?>()
                    GrpcClient.fetchUserId(username) { id, success ->
                        if (success) userIdDeferred.complete(id)
                        else userIdDeferred.complete(null)
                    }

                    val fetchedId = withTimeoutOrNull(3000) {
                        userIdDeferred.await()
                    }

                    if (fetchedId != null) {
                        updateSession(username = username, password = pass, userId = fetchedId)
                        Log.d("SessionManager", "UserID fetched and session updated: $fetchedId")
                    } else {
                        Log.w("SessionManager", "fetchUserId failed or timed out, using fallback session")
                        updateSession(username = username, password = pass)
                    }

                    onComplete(true)
                } else {
                    Log.e("SessionManager", "Login failed - Status: $status (null means timeout)")
                    onComplete(false)
                }
            } catch (e: Exception) {
                Log.e("SessionManager", "Critical login error: ${e.message}", e)
                onComplete(false)
            }
        }
    }

    fun logout(context: Context) {
        Log.d("SessionManager", "Logging out, clearing all user data")
        _session.value = UserSession()
        
        // Clear all relevant preferences
        context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE).edit {
            remove("username")
            remove("password")
            remove("user_id")
            remove("saved_username")
            remove("saved_password")
            remove("chat_list_version")
        }
        
        GrpcClient.disconnect()
    }
}
