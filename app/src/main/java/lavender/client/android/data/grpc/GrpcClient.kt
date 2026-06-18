package lavender.client.android.data.grpc

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import lavender.client.android.data.grpc.GrpcClientExtensions.*
import lavender.client.android.data.models.Message
import lavender.client.android.data.proto.CallMessageProto
import lavender.client.android.data.proto.ServerInfoProto

/**
 * GrpcClient — unified facade for gRPC operations.
 *
 * Owns: StateFlow declarations, connection scope, core connection lifecycle.
 * Domain methods: delegated to GrpcClientExtensions (grouped by domain).
 *
 * Extensions import brings all domain methods into scope:
 *   import lavender.client.android.data.grpc.GrpcClientExtensions.*
 */
object GrpcClient {
    private val realGrpcClient = RealGrpcClient

    // ====== Coroutine scope for flow conversions ======
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ====== Connection State ======
    val connectionStatus: StateFlow<ConnectionStatus> = realGrpcClient.connectionStatus

    val connectionState: StateFlow<Boolean> = realGrpcClient.connectionStatus
        .map { it == ConnectionStatus.READY }
        .stateIn(scope, SharingStarted.Eagerly, realGrpcClient.connectionStatus.value == ConnectionStatus.READY)

    // ====== Data Flows ======
    val messages: StateFlow<List<Message>> = realGrpcClient.messages
    val users: StateFlow<List<String>> = realGrpcClient.users
    val allUsers: StateFlow<List<UserInfoProto>> = realGrpcClient.allUsers
    val error: StateFlow<String?> = realGrpcClient.error
    val systemNotification: StateFlow<String?> = realGrpcClient.systemNotification
    val isSuperAdmin: StateFlow<Boolean> = realGrpcClient.isSuperAdmin
    val serverVersion: StateFlow<String> = realGrpcClient.serverVersion
    val authStatus: StateFlow<String?> = realGrpcClient.authStatus
    val typingUsers: StateFlow<Map<String, Set<String>>> = realGrpcClient.typingUsers
    val chatDeletedEvent: StateFlow<String?> = realGrpcClient.chatDeletedEvent
    val callSignals: SharedFlow<CallMessageProto> = realGrpcClient.callSignals
    val newMessageEvent: SharedFlow<Pair<String, String>> = realGrpcClient.newMessageEvent
    val readReceiptEvent: SharedFlow<Pair<String, String>> = realGrpcClient.readReceiptEvent
    val avatarCacheFlow = realGrpcClient.avatarCacheFlow

    // ====== Mutable State ======
    var currentRoomId: String
        get() = realGrpcClient.currentRoomId
        set(value) { realGrpcClient.currentRoomId = value }

    var hasCheckedForUpdates: Boolean
        get() = realGrpcClient.hasCheckedForUpdates
        set(value) { realGrpcClient.hasCheckedForUpdates = value }

    var isAppInBackground: Boolean
        get() = realGrpcClient.isAppInBackground
        set(value) { realGrpcClient.isAppInBackground = value }

    val currentServerAddress: String?
        get() = realGrpcClient.currentServerAddress

    // ====== V2 Service Detection ======
    val isChatV2Supported: Boolean
        get() = ProfileClient.isChatV2Supported()

    val chatServiceVersion: String
        get() = ProfileClient.serviceChatVersion

    val isProfileV2Supported: Boolean
        get() = ProfileClient.isProfileV2Supported()

    val profileServiceVersion: String
        get() = ProfileClient.serviceProfileVersion

    // ====== Core Connection Lifecycle (kept in facade) ======
    fun connect(serverAddress: String, useTls: Boolean = false, port: Int = 50051, context: Context? = null, forceReconnect: Boolean = false) {
        realGrpcClient.connect(serverAddress, useTls, port, context, forceReconnect)
    }

    fun shouldForceReconnect(): Boolean = realGrpcClient.shouldForceReconnect()

    fun disconnect() = realGrpcClient.disconnect()

    fun startChat(username: String, password: String, joinMessage: String, register: Boolean = false, email: String = "", deviceId: String = "", deviceName: String = "", onMessageReceived: (Message) -> Unit) {
        realGrpcClient.startChat(username, password, joinMessage, register, deviceId, deviceName, onMessageReceived)
    }

    fun clearSystemNotification() = realGrpcClient.clearSystemNotification()

    fun loadHistory(roomId: String, onCompletion: () -> Unit = {}) {
        realGrpcClient.loadHistory(roomId, onCompletion)
    }

    fun setRoomId(roomId: String) = realGrpcClient.setRoomId(roomId)

    fun loadUsers() = realGrpcClient.loadUsers()

    fun loadAllUsers(callback: ((List<UserInfoProto>) -> Unit)? = null) {
        realGrpcClient.loadAllUsers(callback ?: {})
    }
}
