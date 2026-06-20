package lavender.client.android.data.grpc

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.proto.*

/**
 * GrpcClient — unified facade for gRPC operations.
 * All domain methods delegate directly to RealGrpcClient.
 * Target: ~400 LOC (down from 780).
 */
object GrpcClient {
    private val realGrpcClient = RealGrpcClient
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
    val adminUserId: kotlinx.coroutines.flow.StateFlow<String?> = realGrpcClient.adminUserId
    val serverVersion: StateFlow<String> = realGrpcClient.serverVersion
    val authStatus: StateFlow<String?> = realGrpcClient.authStatus
    val typingUsers: StateFlow<Map<String, Set<String>>> = realGrpcClient.typingUsers
    val chatDeletedEvent: StateFlow<String?> = realGrpcClient.chatDeletedEvent
    val serverShuttingDown: StateFlow<Boolean> = realGrpcClient.serverShuttingDown
    val callSignals: SharedFlow<CallMessageProto> = realGrpcClient.callSignals
    val newMessageEvent: SharedFlow<Message> = realGrpcClient.newMessageEvent
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

    // ====== Core Connection Lifecycle ======
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

    // ======= AuthService V2 (JWT) =======

    fun signInV2(
        username: String, password: String,
        deviceId: String, deviceName: String,
        deviceType: String = "android", clientVersion: String = "",
        callback: (AuthResponseV2Proto?, String?) -> Unit
    ) = realGrpcClient.signInV2(username, password, deviceId, deviceName, deviceType, clientVersion, callback)

    fun signUpV2(
        username: String, password: String, email: String,
        deviceId: String, deviceName: String,
        deviceType: String = "android", clientVersion: String = "",
        callback: (AuthResponseV2Proto?, String?) -> Unit
    ) = realGrpcClient.signUpV2(username, password, email, deviceId, deviceName, deviceType, clientVersion, callback)

    fun refreshToken(
        refreshToken: String, callback: (RefreshTokenResponseProto?, String?) -> Unit
    ) = realGrpcClient.refreshToken(refreshToken, callback)

    fun signOut(
        refreshToken: String = "", allDevices: Boolean = false,
        callback: (Boolean, String) -> Unit = { _, _ -> }
    ) = realGrpcClient.signOut(refreshToken, allDevices, callback)

    fun revokeDevice(
        deviceId: String, callback: (Boolean, String) -> Unit = { _, _ -> }
    ) = realGrpcClient.revokeDevice(deviceId, callback)

    // ======= Chat Operations =======

    fun getChats(
        username: String, skipCache: Boolean = false, limit: Int = 100, cursor: String = "", callback: (ChatListPage) -> Unit
    ) = realGrpcClient.getChats(username, skipCache, limit, cursor, callback)

    fun getChatListVersion(username: String, callback: (Long) -> Unit) =
        realGrpcClient.getChatListVersion(username, callback)

    fun getAllChats(callback: (List<ChatInfo>) -> Unit) =
        realGrpcClient.getAllChats(callback)

    fun createDirectChat(user1: String, user2: String, callback: (String?) -> Unit) =
        realGrpcClient.createDirectChat(user1, user2, callback)

    fun createGroupChat(
        name: String, participants: List<String>, creator: String,
        type: String = "group", callback: (String?) -> Unit
    ) = realGrpcClient.createGroupChat(name, participants, creator, type, callback)

    fun deleteChat(
        chatId: String, requesterUsername: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.deleteChat(chatId, requesterUsername, callback)

    fun deleteChat(
        chatId: String, userId: String, username: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.deleteChatWithUserId(chatId, userId, username, callback)

    fun updateChatName(
        chatId: String, newName: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.updateChatName(chatId, newName, callback)

    fun removeParticipant(
        chatId: String, username: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.removeParticipant(chatId, username, callback)

    fun addParticipant(
        chatId: String, username: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.addParticipant(chatId, username, callback)

    fun addParticipants(
        chatId: String, usernames: List<String>, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.addParticipants(chatId, usernames, callback)

    fun updateChatSettings(
        chatId: String, allowAdd: Boolean, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.updateChatSettings(chatId, allowAdd, callback)

    fun updateChatAvatar(
        chatId: String, avatarUrl: String, username: String,
        fullAvatarUrl: String = "", callback: (Boolean, String) -> Unit
    ) = realGrpcClient.updateChatAvatar(chatId, avatarUrl, username, fullAvatarUrl, callback)

    // ======= ChatList V2 (suspend) =======

    suspend fun pinChat(context: Context, chatId: String): Boolean {
        if (!ProfileClient.isChatV2Supported()) return false
        return realGrpcClient.pinChat(chatId)
    }

    suspend fun unpinChat(context: Context, chatId: String): Boolean {
        if (!ProfileClient.isChatV2Supported()) return false
        return realGrpcClient.unpinChat(chatId)
    }

    suspend fun searchChats(context: Context, query: String, limit: Int = 20, offset: Int = 0): List<ChatInfo> {
        if (!ProfileClient.isChatV2Supported()) return emptyList()
        return realGrpcClient.searchChats(query, limit, offset)
    }

    suspend fun archiveChat(context: Context, chatId: String): Boolean {
        if (!ProfileClient.isChatV2Supported()) return false
        return realGrpcClient.archiveChat(chatId)
    }

    suspend fun unarchiveChat(context: Context, chatId: String): Boolean {
        if (!ProfileClient.isChatV2Supported()) return false
        return realGrpcClient.unarchiveChat(chatId)
    }

    // ======= Pin Message =======

    suspend fun pinMessage(context: Context, chatId: String, messageId: String): Boolean {
        if (!ProfileClient.isChatV2Supported()) return false
        return realGrpcClient.pinMessage(chatId, messageId)
    }

    suspend fun unpinMessage(context: Context, chatId: String, messageId: String): Boolean {
        if (!ProfileClient.isChatV2Supported()) return false
        return realGrpcClient.unpinMessage(chatId, messageId)
    }

    suspend fun getPinnedMessages(context: Context, chatId: String): List<Message> {
        if (!ProfileClient.isChatV2Supported()) return emptyList()
        return realGrpcClient.getPinnedMessages(chatId)
    }

    // ======= Message Operations =======

    fun sendMessage(message: Message) =
        realGrpcClient.sendMessage(message)

    fun addLocalMessage(message: Message) =
        realGrpcClient.addLocalMessage(message)

    fun deleteMessage(message: Message) =
        realGrpcClient.deleteMessage(message)

    fun editMessage(
        messageId: String, text: String, callback: (Boolean, String) -> Unit = { _, _ -> }
    ) = realGrpcClient.editMessage(messageId, text, callback)

    fun updateMessage(message: Message) =
        realGrpcClient.updateMessage(message)

    fun setReaction(messageId: String, username: String, emoji: String) =
        realGrpcClient.setReaction(messageId, username, emoji)

    fun markRead(
        roomId: String, username: String, onCompletion: (() -> Unit)? = null
    ) = realGrpcClient.markRead(roomId, username, onCompletion)

    fun sendTypingSignal(username: String, isTyping: Boolean) =
        realGrpcClient.sendTypingSignal(username, isTyping)

    fun registerToken(user: String, token: String, pushEnabled: Boolean = true) =
        realGrpcClient.registerToken(user, token, pushEnabled)

    fun clearMessages() = realGrpcClient.clearMessages()

    // ======= Profile Operations =======

    fun updateUsername(
        oldUsername: String, newUsername: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.updateUsername(oldUsername, newUsername, callback)

    fun updatePassword(
        username: String, oldPassword: String, newPassword: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.updatePassword(username, oldPassword, newPassword, callback)

    fun adminUpdatePassword(
        targetUsername: String, newPassword: String, adminUsername: String,
        callback: (Boolean, String) -> Unit
    ) = realGrpcClient.adminUpdatePassword(targetUsername, newPassword, adminUsername, callback)

    fun updateAvatar(
        username: String, avatarUrl: String, fullAvatarUrl: String = "",
        callback: (Boolean, String) -> Unit
    ) = realGrpcClient.updateAvatar(username, avatarUrl, fullAvatarUrl, callback)

    fun getUserAvatar(
        username: String, userId: String = "", callback: (String) -> Unit
    ) = realGrpcClient.getUserAvatar(username, userId, callback)

    fun getUserProfile(
        userId: String, callback: (GetUserProfileResponseProto?) -> Unit
    ) = realGrpcClient.getUserProfile(userId, callback)

    fun updateProfile(
        username: String, bio: String, status: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.updateProfile(username, bio, status, callback)

    fun deleteProfile(
        username: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.deleteProfile(username, callback)

    fun requestPasswordReset(
        email: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.requestPasswordReset(email, callback)

    fun resetPassword(
        token: String, newPw: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.resetPassword(token, newPw, callback)

    fun getDevices(
        userId: String, callback: (List<DeviceInfoProto>) -> Unit
    ) = realGrpcClient.getDevices(userId, callback)

    fun deleteDevice(
        userId: String, deviceId: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.deleteDevice(userId, deviceId, callback)

    fun deleteOtherDevices(
        userId: String, currentDeviceId: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.deleteOtherDevices(userId, currentDeviceId, callback)

    // ======= Contacts =======

    fun addContact(
        username: String, contactUsername: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.addContact(username, contactUsername, callback)

    fun removeContact(
        username: String, contactUsername: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.removeContact(username, contactUsername, callback)

    fun getContacts(
        username: String, callback: (List<String>) -> Unit
    ) = realGrpcClient.getContacts(username, callback)

    // ======= ProfileService V2 (suspend) =======

    suspend fun fetchServerInfo(
        context: Context, serverAddress: String, port: Int = 8083
    ) = ProfileClient.fetchServerInfo(context, serverAddress, port)

    suspend fun getProfileV2(context: Context): GetProfileResponseProto? =
        ProfileClient.getProfile(context)

    suspend fun updateProfileV2(
        context: Context, username: String = "", bio: String = "",
        status: String = "", locale: String = ""
    ): Boolean = ProfileClient.updateProfile(context, username, bio, status, locale)

    suspend fun updateAvatarV2(
        context: Context, avatarUrl: String, fullAvatarUrl: String = ""
    ): Boolean = ProfileClient.updateAvatar(context, avatarUrl, fullAvatarUrl)

    suspend fun deleteProfileV2(
        context: Context, password: String
    ): Boolean = ProfileClient.deleteProfile(context, password)

    suspend fun getUserSettingsV2(context: Context): GetUserSettingsResponseProto? =
        ProfileClient.getUserSettings(context)

    suspend fun updateUserSettingsV2(
        context: Context, locale: String = "", themeId: String = "", pushEnabled: Boolean? = null
    ): Boolean = ProfileClient.updateUserSettings(context, locale, themeId, pushEnabled)

    // ======= Theme Operations =======

    fun getThemes(
        username: String, callback: (String, List<CustomThemeProto>) -> Unit
    ) = realGrpcClient.getThemes(username, callback)

    fun saveTheme(
        username: String, theme: CustomThemeProto, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.saveTheme(username, theme, callback)

    fun setCurrentTheme(
        username: String, themeId: String, callback: (Boolean) -> Unit
    ) = realGrpcClient.setCurrentTheme(username, themeId, callback)

    fun deleteTheme(
        username: String, themeId: String, callback: (Boolean) -> Unit
    ) = realGrpcClient.deleteTheme(username, themeId, callback)

    // ======= Draft Operations =======

    fun saveDraft(
        roomId: String, draftText: String,
        repliedToMessageId: String = "", repliedToUser: String = "",
        repliedToText: String = "", callback: (Boolean, String) -> Unit = { _, _ -> }
    ) = realGrpcClient.saveDraft(roomId, draftText, repliedToMessageId, repliedToUser, repliedToText, callback)

    fun getDraft(
        roomId: String,
        callback: (draftText: String, repliedToMessageId: String, repliedToUser: String, repliedToText: String, hasDraft: Boolean) -> Unit
    ) = realGrpcClient.getDraft(roomId, callback)

    fun deleteDraft(
        roomId: String, callback: (Boolean) -> Unit = {}
    ) = realGrpcClient.deleteDraft(roomId, callback)

    // ======= Muted Chats =======

    fun getMutedChats(callback: (List<String>) -> Unit) =
        realGrpcClient.getMutedChats(callback)

    fun setMutedChat(
        roomId: String, muted: Boolean, callback: (Boolean) -> Unit = {}
    ) = realGrpcClient.setMutedChat(roomId, muted, callback)

    // ======= Favorites =======

    fun addFavorite(
        userId: String, messageId: String, callback: (Boolean, String) -> Unit
    ) = realGrpcClient.addFavorite(userId, messageId, callback)

    fun removeFavorite(
        userId: String, messageId: String, callback: (Boolean) -> Unit
    ) = realGrpcClient.removeFavorite(userId, messageId, callback)

    fun getFavorites(
        userId: String, callback: (List<Message>) -> Unit
    ) = realGrpcClient.getFavorites(userId, callback)

    // ======= Call Operations =======

    fun startCallSession() = realGrpcClient.startCallSession()

    fun sendCallSignal(signal: CallMessageProto) =
        realGrpcClient.sendCallSignal(signal)

    // ======= FCM Logs =======

    fun getFCMLogs(callback: (List<FCMLogEntryProto>) -> Unit) =
        realGrpcClient.getFCMLogs(callback)

    // ======= Server Discovery =======

    fun getServers(context: Context, cb: (List<ServerInfoProto>) -> Unit) =
        realGrpcClient.fetchServersList(context, cb)

    // ======= Avatar Cache =======

    fun getAvatarCache(): Map<String, String> =
        realGrpcClient.getAvatarCache()

    fun getFullAvatarCache(): Map<String, String> =
        realGrpcClient.getFullAvatarCache()

    fun getFullAvatarUrl(username: String): String? =
        realGrpcClient.getFullAvatarUrl(username)

    fun updateAvatarCache(username: String, avatarUrl: String, fullAvatarUrl: String = "") =
        realGrpcClient.updateAvatarCache(username, avatarUrl, fullAvatarUrl)

    // ======= User Identity =======

    fun getCurrentUsername(): String? = realGrpcClient.getCurrentUsername()

    fun setUserId(userId: String) = realGrpcClient.setUserId(userId)

    fun getUserId(): String? = realGrpcClient.getUserId()

    fun fetchUserId(username: String, callback: (String?, Boolean) -> Unit) =
        realGrpcClient.fetchUserId(username, callback)

    // ======= Secret Chat =======

    fun createSecretChat(
        targetUsername: String, publicKey: String,
        callback: (String, Boolean, String, String) -> Unit
    ) {
        val clientVersion = lavender.client.android.BuildConfig.VERSION_NAME
        realGrpcClient.scope.launch(Dispatchers.Main) {
            val (chatId, success, message) = lavender.client.android.data.grpc.createSecretChat(
                targetUsername = targetUsername, targetUserId = "",
                publicKey = publicKey, clientVersion = clientVersion
            )
            callback(chatId, success, message, "")
        }
    }

    fun exchangeSecretKey(
        chatId: String, publicKey: String, callback: (Boolean, String, Boolean) -> Unit
    ) {
        realGrpcClient.scope.launch(Dispatchers.Main) {
            val (success, peerKey, peerHasKey) = lavender.client.android.data.grpc.exchangeSecretKey(chatId, publicKey)
            callback(success, peerKey, peerHasKey)
        }
    }

    fun getSecretChatKey(
        chatId: String, callback: (String, Boolean) -> Unit
    ) {
        realGrpcClient.scope.launch(Dispatchers.Main) {
            val (peerKey, peerHasKey) = lavender.client.android.data.grpc.getSecretChatKey(chatId)
            callback(peerKey, peerHasKey)
        }
    }

    fun sendE2EEMessage(chatId: String, encryptedPayload: String) {
        val msg = Message(
            id = java.util.UUID.randomUUID().toString(),
            user = getCurrentUsername() ?: "",
            text = "",
            timestamp = System.currentTimeMillis(),
            roomId = chatId,
            userId = getUserId() ?: "",
            isE2EE = true,
            e2eePayload = encryptedPayload
        )
        addLocalMessage(msg)
        sendMessage(msg)
    }

    // ======= Notifications =======

    val serverNotifications: SharedFlow<ServerNotificationProto>
        get() = lavender.client.android.data.grpc.serverNotifications

    fun subscribeNotifications(userId: String, types: List<String> = emptyList()) =
        lavender.client.android.data.grpc.subscribeNotifications(userId, types, realGrpcClient.scope)

    suspend fun getNotificationHistory(
        userId: String, limit: Int = 50
    ): List<ServerNotificationProto> = lavender.client.android.data.grpc.getNotificationHistory(userId, limit)

    suspend fun markNotificationsRead(
        userId: String, notificationIds: List<String>
    ): Boolean = lavender.client.android.data.grpc.markNotificationsRead(userId, notificationIds)

    suspend fun getUnreadCount(userId: String): Int =
        lavender.client.android.data.grpc.getUnreadCount(userId)

    // ======= Remote Agent =======

    suspend fun listRemoteAgents(filterStatus: String = ""): List<RemoteAgentInfoProto> =
        lavender.client.android.data.grpc.listRemoteAgents(filterStatus)

    suspend fun getRemoteAgentStatus(agentId: String): GetRemoteAgentStatusResponseProto =
        lavender.client.android.data.grpc.getRemoteAgentStatus(agentId)

    suspend fun deployAgentTask(
        agentId: String, taskType: String,
        params: Map<String, String> = emptyMap(), workingDir: String = "",
        timeoutSec: Int = 60, tunnelMode: Int = 0,
        tunnelHost: String = "", tunnelPort: Int = 22,
        tunnelUser: String = "", tunnelPassword: String = "",
        tunnelServerHost: String = "localhost", tunnelServerPort: Int = 50051,
        tunnelLocalPort: Int = 50052
    ): DeployAgentTaskResponseProto =
        lavender.client.android.data.grpc.deployAgentTask(
            agentId, taskType, params, workingDir, timeoutSec,
            tunnelMode, tunnelHost, tunnelPort, tunnelUser, tunnelPassword,
            tunnelServerHost, tunnelServerPort, tunnelLocalPort
        )

    fun deployAgentTaskStream(
        agentId: String, taskType: String,
        params: Map<String, String> = emptyMap(), workingDir: String = "",
        timeoutSec: Int = 60, tunnelMode: Int = 0,
        tunnelHost: String = "", tunnelPort: Int = 22,
        tunnelUser: String = "", tunnelPassword: String = "",
        tunnelServerHost: String = "localhost", tunnelServerPort: Int = 50051,
        tunnelLocalPort: Int = 50052
    ): Flow<DeployAgentTaskStreamResponseProto> =
        lavender.client.android.data.grpc.deployAgentTaskStream(
            agentId, taskType, params, workingDir, timeoutSec,
            tunnelMode, tunnelHost, tunnelPort, tunnelUser, tunnelPassword,
            tunnelServerHost, tunnelServerPort, tunnelLocalPort
        )

    // ======= Agent Token Management =======

    suspend fun generateAgentToken(
        agentId: String, agentName: String, capabilities: List<String>,
        ttlHours: Int, adminUserId: String
    ): GenerateAgentTokenResponseProto {
        android.util.Log.d("GrpcClient", "generateAgentToken CALLED: agentId=$agentId name=$agentName")
        return lavender.client.android.data.grpc.generateAgentToken(
            agentId, agentName, capabilities, ttlHours, adminUserId
        )
    }

    suspend fun revokeAgentToken(
        agentId: String, adminUserId: String
    ): RevokeAgentTokenResponseProto =
        lavender.client.android.data.grpc.revokeAgentToken(agentId, adminUserId)

    suspend fun listAgentTokens(
        adminUserId: String
    ): ListAgentTokensResponseProto =
        lavender.client.android.data.grpc.listAgentTokens(adminUserId)

    // ======= Agent Process Management =======

    suspend fun startAgentOnServer(
        agentId: String, agentName: String, token: String,
        serverAddress: String = "",
        capabilities: List<String> = listOf("shell", "git", "build", "file", "docker", "ai"),
        adminUserId: String = ""
    ): StartAgentResponseProto =
        lavender.client.android.data.grpc.startAgentOnServer(
            agentId, agentName, token, serverAddress, capabilities, adminUserId
        )

    suspend fun stopAgentOnServer(
        agentId: String, adminUserId: String = ""
    ): StopAgentResponseProto =
        lavender.client.android.data.grpc.stopAgentOnServer(agentId, adminUserId)

    suspend fun getAgentProcessStatus(
        agentId: String, adminUserId: String = ""
    ): GetAgentProcessStatusResponseProto =
        lavender.client.android.data.grpc.getAgentProcessStatus(agentId, adminUserId)

    // ======= AI Services v2 =======

    val aiV2Client: GrpcAIv2Client
        get() = realGrpcClient.aiV2Client

    // ======= AI v2 Marketplace =======

    suspend fun rateAIAgent(agentId: String, rating: Int, review: String) =
        realGrpcClient.aiV2Client.rateAgent(agentId, rating, review)

    suspend fun getAIAgentReviews(agentId: String, limit: Int = 20) =
        realGrpcClient.aiV2Client.getAgentReviews(agentId, limit)

    suspend fun listMarketplaceAgents(query: String = "", limit: Int = 20, offset: Int = 0) =
        realGrpcClient.aiV2Client.listMarketplaceAgents(query, limit, offset)

    suspend fun getAIAgentStats(agentId: String) =
        realGrpcClient.aiV2Client.getAgentStats(agentId)

    suspend fun shareAIAgent(agentId: String) =
        realGrpcClient.aiV2Client.shareAgent(agentId)

    suspend fun installAIAgent(shareCode: String, newName: String = "") =
        realGrpcClient.aiV2Client.installAgent(shareCode, newName)

    suspend fun getAIUsageStats() =
        realGrpcClient.aiV2Client.getUsageStats()

    // ======= AI Chat Settings =======

    suspend fun getAIChatSettings(sessionId: String) =
        realGrpcClient.aiV2Client.getChatSettings(sessionId)

    suspend fun updateAIChatSettings(sessionId: String, apiKey: String = "", model: String = "") =
        realGrpcClient.aiV2Client.updateChatSettings(sessionId, apiKey, model)
}
