package lavender.client.android.ui.chat

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.session.SessionManager

data class ChatIntentData(
    val roomId: String = "",
    val username: String = "",
    val password: String = "",
    val chatName: String = "",
    val isDirect: Boolean = false,
    val chatType: String = "group",
    val participantsJson: String = "[]",
    val creator: String = "",
    val chatAvatarUrl: String = "",
    val chatFullAvatarUrl: String = "",
    val isSecret: Boolean = false,
    val serverAddress: String = ""
)

data class ChatMetadataState(
    val chatName: String = "",
    val isDirect: Boolean = false,
    val chatType: String = "group",
    val participantsJson: String = "[]",
    val creator: String = "",
    val avatarUrl: String = "",
    val fullAvatarUrl: String = ""
)

class NewChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _intentData = MutableStateFlow(ChatIntentData())
    val intentData: StateFlow<ChatIntentData> = _intentData.asStateFlow()

    private val _metadata = MutableStateFlow(ChatMetadataState())
    val metadata: StateFlow<ChatMetadataState> = _metadata.asStateFlow()

    private val grpcClient = GrpcClient

    val connectionStatus: StateFlow<ConnectionStatus> = grpcClient.connectionStatus

    fun parseIntent(intent: Intent, savedState: Bundle?) {
        val roomId = intent.getStringExtra("ROOM_ID")
            ?: intent.getStringExtra("roomId")
            ?: (savedState?.getString("roomId") ?: "general")

        var username = intent.getStringExtra("USERNAME").orEmpty()
        var password = intent.getStringExtra("PASSWORD").orEmpty()

        if (username.isEmpty() || password.isEmpty()) {
            val session = SessionManager.session.value
            if (session.isLoggedIn) {
                username = session.username
                password = session.password
            } else {
                val prefs = getApplication<Application>().getSharedPreferences("lavender_prefs", android.content.Context.MODE_PRIVATE)
                username = prefs.getString("username", "") ?: ""
                password = prefs.getString("password", "") ?: ""
            }
        }

        val chatName = intent.getStringExtra("CHAT_NAME").orEmpty()
        val isDirect = intent.getBooleanExtra("IS_DIRECT", false)
        val chatType = intent.getStringExtra("CHAT_TYPE") ?: (if (isDirect) "direct" else "group")
        val participantsJson = intent.getStringExtra("PARTICIPANTS") ?: "[]"
        val creator = intent.getStringExtra("CREATOR").orEmpty()
        val chatAvatarUrl = intent.getStringExtra("AVATAR_URL").orEmpty()
        val chatFullAvatarUrl = intent.getStringExtra("FULL_AVATAR_URL").orEmpty()
        val isSecret = intent.getStringExtra("IS_SECRET") == "true"
        val serverAddress = intent.getStringExtra("SERVER_ADDRESS")
            ?: getApplication<Application>().getSharedPreferences("lavender_prefs", android.content.Context.MODE_PRIVATE)
                .getString("server_address", "")

        _intentData.value = ChatIntentData(
            roomId = roomId,
            username = username,
            password = password,
            chatName = chatName,
            isDirect = isDirect,
            chatType = if (isSecret) "secret" else chatType,
            participantsJson = participantsJson,
            creator = creator,
            chatAvatarUrl = chatAvatarUrl,
            chatFullAvatarUrl = chatFullAvatarUrl,
            isSecret = isSecret,
            serverAddress = serverAddress ?: ""
        )
    }

    fun ensureConnection() {
        val data = _intentData.value
        if (grpcClient.connectionStatus.value != ConnectionStatus.READY && data.serverAddress.isNotEmpty()) {
            val parts = data.serverAddress.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
            grpcClient.connect(host, false, port, getApplication())
        }
    }

    fun setRoomId(roomId: String) {
        grpcClient.setRoomId(roomId)
    }

    fun updateMetadata(meta: ChatMetadataState) {
        _metadata.value = meta
    }

    fun shouldForceReconnect(): Boolean = grpcClient.shouldForceReconnect()

    fun forceReconnect() {
        val data = _intentData.value
        if (data.serverAddress.isNotEmpty()) {
            val parts = data.serverAddress.split(":")
            val host = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
            grpcClient.connect(host, false, port, getApplication(), true)
        }
    }

    fun dismissNotifications(roomId: String) {
        lavender.client.android.data.fcm.LavenderMessagingService.dismissNotificationsForRoom(getApplication(), roomId)
    }
}
