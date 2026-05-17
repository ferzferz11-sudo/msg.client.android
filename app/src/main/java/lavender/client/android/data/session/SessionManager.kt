package lavender.client.android.data.session

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.messaging.FirebaseMessaging
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

                // Sync FCM token on start
                syncFcmToken(context, username)
            }
        }
    }

    fun syncFcmToken(context: Context, username: String) {
        val prefs = context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
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
                    GrpcClient.startChat(username, pass, "", register, email) { }

                    // Wait for auth status from server
                    val authResult = withTimeoutOrNull(5000) {
                        GrpcClient.authStatus.filter { it != null }.first()
                    }

                    Log.d("SessionManager", "Auth result received: $authResult")

                    if (authResult == "REGISTRATION_SUCCESS" || authResult == null || authResult == "SUCCESS") {
                        // Note: null might happen if server doesn't explicitly send SUCCESS for existing users yet, 
                        // but we can assume success if no error was received within timeout and connection is READY.
                        // Actually, for existing users server currently doesn't send "SUCCESS" system message, it just starts sending data.
                        
                        // Try to fetch User ID
                        val userIdDeferred = CompletableDeferred<String?>()
                        GrpcClient.fetchUserId(username) { id, success ->
                            if (success) userIdDeferred.complete(id)
                            else userIdDeferred.complete(null)
                        }

                        val fetchedId = withTimeoutOrNull(3000) { userIdDeferred.await() }
                        if (fetchedId != null) {
                            updateSession(username = username, password = pass, userId = fetchedId)
                        } else {
                            updateSession(username = username, password = pass)
                        }

                        syncFcmToken(context, username)
                        // This is the bug fix - if it was REGISTRATION_SUCCESS, we must pass it back!
                        onComplete(authResult ?: "SUCCESS")
                    } else {
                        // Return the actual error signal: AUTH_FAILED or USER_NOT_FOUND
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