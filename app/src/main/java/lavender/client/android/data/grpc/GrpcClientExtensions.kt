@file:JvmName("GrpcClientExtensions")

package lavender.client.android.data.grpc

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import lavender.client.android.data.proto.*
import lavender.client.android.data.models.Message
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.models.AIChatInfo

// =============================================================================
// GrpcClient Extension Functions — Domain Grouping
// All proxy methods extracted from GrpcClient to reduce facade LOC.
// =============================================================================

// ======= AuthService V2 (JWT) =======

fun GrpcClient.signInV2(
    username: String, password: String,
    deviceId: String, deviceName: String,
    deviceType: String = "android", clientVersion: String = "",
    callback: (AuthResponseV2Proto?, String?) -> Unit
) = RealGrpcClient.signInV2(username, password, deviceId, deviceName, deviceType, clientVersion, callback)

fun GrpcClient.signUpV2(
    username: String, password: String, email: String,
    deviceId: String, deviceName: String,
    deviceType: String = "android", clientVersion: String = "",
    callback: (AuthResponseV2Proto?, String?) -> Unit
) = RealGrpcClient.signUpV2(username, password, email, deviceId, deviceName, deviceType, clientVersion, callback)

fun GrpcClient.refreshToken(
    refreshToken: String, callback: (RefreshTokenResponseProto?, String?) -> Unit
) = RealGrpcClient.refreshToken(refreshToken, callback)

fun GrpcClient.signOut(
    refreshToken: String = "", allDevices: Boolean = false,
    callback: (Boolean, String) -> Unit = { _, _ -> }
) = RealGrpcClient.signOut(refreshToken, allDevices, callback)

fun GrpcClient.revokeDevice(
    deviceId: String, callback: (Boolean, String) -> Unit = { _, _ -> }
) = RealGrpcClient.revokeDevice(deviceId, callback)

// ======= Chat Operations =======

fun GrpcClient.getChats(
    username: String, skipCache: Boolean = false, callback: (List<ChatInfo>) -> Unit
) = RealGrpcClient.getChats(username, skipCache, callback)

fun GrpcClient.getChatListVersion(
    username: String, callback: (Long) -> Unit
) = RealGrpcClient.getChatListVersion(username, callback)

fun GrpcClient.getAllChats(
    callback: (List<ChatInfo>) -> Unit
) = RealGrpcClient.getAllChats(callback)

fun GrpcClient.getAIChats(
    userId: String, callback: (List<AIChatInfo>) -> Unit
) = RealGrpcClient.getAIChats(userId, callback)

fun GrpcClient.renameAIChat(
    chatId: String, userId: String, newName: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.renameAIChat(chatId, userId, newName, callback)

fun GrpcClient.createDirectChat(
    user1: String, user2: String, callback: (String?) -> Unit
) = RealGrpcClient.createDirectChat(user1, user2, callback)

fun GrpcClient.createGroupChat(
    name: String, participants: List<String>, creator: String,
    type: String = "group", callback: (String?) -> Unit
) = RealGrpcClient.createGroupChat(name, participants, creator, type, callback)

fun GrpcClient.deleteChat(
    chatId: String, requesterUsername: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.deleteChat(chatId, requesterUsername, callback)

fun GrpcClient.deleteChat(
    chatId: String, userId: String, username: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.deleteChatWithUserId(chatId, userId, username, callback)

fun GrpcClient.updateChatName(
    chatId: String, newName: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.updateChatName(chatId, newName, callback)

fun GrpcClient.removeParticipant(
    chatId: String, username: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.removeParticipant(chatId, username, callback)

fun GrpcClient.addParticipant(
    chatId: String, username: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.addParticipant(chatId, username, callback)

fun GrpcClient.addParticipants(
    chatId: String, usernames: List<String>, callback: (Boolean, String) -> Unit
) = RealGrpcClient.addParticipants(chatId, usernames, callback)

fun GrpcClient.updateChatSettings(
    chatId: String, allowAdd: Boolean, callback: (Boolean, String) -> Unit
) = RealGrpcClient.updateChatSettings(chatId, allowAdd, callback)

fun GrpcClient.updateChatAvatar(
    chatId: String, avatarUrl: String, username: String,
    fullAvatarUrl: String = "", callback: (Boolean, String) -> Unit
) = RealGrpcClient.updateChatAvatar(chatId, avatarUrl, username, fullAvatarUrl, callback)

// ======= ChatList V2 (suspend) =======

suspend fun GrpcClient.pinChat(context: Context, chatId: String): Boolean {
    if (!ProfileClient.isChatV2Supported()) return false
    return RealGrpcClient.pinChat(chatId)
}

suspend fun GrpcClient.unpinChat(context: Context, chatId: String): Boolean {
    if (!ProfileClient.isChatV2Supported()) return false
    return RealGrpcClient.unpinChat(chatId)
}

suspend fun GrpcClient.searchChats(context: Context, query: String, limit: Int = 20, offset: Int = 0): List<ChatInfo> {
    if (!ProfileClient.isChatV2Supported()) return emptyList()
    return RealGrpcClient.searchChats(query, limit, offset)
}

suspend fun GrpcClient.archiveChat(context: Context, chatId: String): Boolean {
    if (!ProfileClient.isChatV2Supported()) return false
    return RealGrpcClient.archiveChat(chatId)
}

suspend fun GrpcClient.unarchiveChat(context: Context, chatId: String): Boolean {
    if (!ProfileClient.isChatV2Supported()) return false
    return RealGrpcClient.unarchiveChat(chatId)
}

// ======= Pin Message =======

suspend fun GrpcClient.pinMessage(context: Context, chatId: String, messageId: String): Boolean {
    if (!ProfileClient.isChatV2Supported()) return false
    return RealGrpcClient.pinMessage(chatId, messageId)
}

suspend fun GrpcClient.unpinMessage(context: Context, chatId: String, messageId: String): Boolean {
    if (!ProfileClient.isChatV2Supported()) return false
    return RealGrpcClient.unpinMessage(chatId, messageId)
}

suspend fun GrpcClient.getPinnedMessages(context: Context, chatId: String): List<Message> {
    if (!ProfileClient.isChatV2Supported()) return emptyList()
    return RealGrpcClient.getPinnedMessages(chatId)
}

// ======= Message Operations =======

fun GrpcClient.sendMessage(message: Message) =
    RealGrpcClient.sendMessage(message)

fun GrpcClient.addLocalMessage(message: Message) =
    RealGrpcClient.addLocalMessage(message)

fun GrpcClient.deleteMessage(message: Message) =
    RealGrpcClient.deleteMessage(message)

fun GrpcClient.editMessage(
    messageId: String, text: String, callback: (Boolean, String) -> Unit = { _, _ -> }
) = RealGrpcClient.editMessage(messageId, text, callback)

fun GrpcClient.updateMessage(message: Message) =
    RealGrpcClient.updateMessage(message)

fun GrpcClient.setReaction(messageId: String, username: String, emoji: String) =
    RealGrpcClient.setReaction(messageId, username, emoji)

fun GrpcClient.markRead(
    roomId: String, username: String, onCompletion: (() -> Unit)? = null
) = RealGrpcClient.markRead(roomId, username, onCompletion)

fun GrpcClient.sendTypingSignal(username: String, isTyping: Boolean) =
    RealGrpcClient.sendTypingSignal(username, isTyping)

fun GrpcClient.registerToken(user: String, token: String, pushEnabled: Boolean = true) =
    RealGrpcClient.registerToken(user, token, pushEnabled)

fun GrpcClient.clearMessages() =
    RealGrpcClient.clearMessages()

// ======= Profile Operations =======

fun GrpcClient.updateUsername(
    oldUsername: String, newUsername: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.updateUsername(oldUsername, newUsername, callback)

fun GrpcClient.updatePassword(
    username: String, oldPassword: String, newPassword: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.updatePassword(username, oldPassword, newPassword, callback)

fun GrpcClient.adminUpdatePassword(
    targetUsername: String, newPassword: String, adminUsername: String,
    callback: (Boolean, String) -> Unit
) = RealGrpcClient.adminUpdatePassword(targetUsername, newPassword, adminUsername, callback)

fun GrpcClient.updateAvatar(
    username: String, avatarUrl: String, fullAvatarUrl: String = "",
    callback: (Boolean, String) -> Unit
) = RealGrpcClient.updateAvatar(username, avatarUrl, fullAvatarUrl, callback)

fun GrpcClient.getUserAvatar(
    username: String, userId: String = "", callback: (String) -> Unit
) = RealGrpcClient.getUserAvatar(username, userId, callback)

fun GrpcClient.getUserProfile(
    userId: String, callback: (GetUserProfileResponseProto?) -> Unit
) = RealGrpcClient.getUserProfile(userId, callback)

fun GrpcClient.updateProfile(
    username: String, bio: String, status: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.updateProfile(username, bio, status, callback)

fun GrpcClient.deleteProfile(
    username: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.deleteProfile(username, callback)

fun GrpcClient.requestPasswordReset(
    email: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.requestPasswordReset(email, callback)

fun GrpcClient.resetPassword(
    token: String, newPw: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.resetPassword(token, newPw, callback)

fun GrpcClient.getDevices(
    userId: String, callback: (List<DeviceInfoProto>) -> Unit
) = RealGrpcClient.getDevices(userId, callback)

fun GrpcClient.deleteDevice(
    userId: String, deviceId: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.deleteDevice(userId, deviceId, callback)

fun GrpcClient.deleteOtherDevices(
    userId: String, currentDeviceId: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.deleteOtherDevices(userId, currentDeviceId, callback)

// ======= Contacts =======

fun GrpcClient.addContact(
    username: String, contactUsername: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.addContact(username, contactUsername, callback)

fun GrpcClient.removeContact(
    username: String, contactUsername: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.removeContact(username, contactUsername, callback)

fun GrpcClient.getContacts(
    username: String, callback: (List<String>) -> Unit
) = RealGrpcClient.getContacts(username, callback)

// ======= ProfileService V2 (suspend) =======

suspend fun GrpcClient.fetchServerInfo(
    context: Context, serverAddress: String, port: Int = 8083, grpcPort: Int = 50051
) = ProfileClient.fetchServerInfo(context, serverAddress, port, grpcPort)

suspend fun GrpcClient.getProfileV2(context: Context): GetProfileResponseProto? =
    ProfileClient.getProfile(context)

suspend fun GrpcClient.updateProfileV2(
    context: Context, username: String = "", bio: String = "",
    status: String = "", locale: String = ""
): Boolean = ProfileClient.updateProfile(context, username, bio, status, locale)

suspend fun GrpcClient.updateAvatarV2(
    context: Context, avatarUrl: String, fullAvatarUrl: String = ""
): Boolean = ProfileClient.updateAvatar(context, avatarUrl, fullAvatarUrl)

suspend fun GrpcClient.getUserSettingsV2(context: Context): GetUserSettingsResponseProto? =
    ProfileClient.getUserSettings(context)

suspend fun GrpcClient.updateUserSettingsV2(
    context: Context, locale: String = "", themeId: String = "", pushEnabled: Boolean? = null
): Boolean = ProfileClient.updateUserSettings(context, locale, themeId, pushEnabled)

// ======= Theme Operations =======

fun GrpcClient.getThemes(
    username: String, callback: (String, List<CustomThemeProto>) -> Unit
) = RealGrpcClient.getThemes(username, callback)

fun GrpcClient.saveTheme(
    username: String, theme: CustomThemeProto, callback: (Boolean, String) -> Unit
) = RealGrpcClient.saveTheme(username, theme, callback)

fun GrpcClient.setCurrentTheme(
    username: String, themeId: String, callback: (Boolean) -> Unit
) = RealGrpcClient.setCurrentTheme(username, themeId, callback)

fun GrpcClient.deleteTheme(
    username: String, themeId: String, callback: (Boolean) -> Unit
) = RealGrpcClient.deleteTheme(username, themeId, callback)

// ======= Draft Operations =======

fun GrpcClient.saveDraft(
    roomId: String, draftText: String,
    repliedToMessageId: String = "", repliedToUser: String = "",
    repliedToText: String = "", callback: (Boolean, String) -> Unit = { _, _ -> }
) = RealGrpcClient.saveDraft(roomId, draftText, repliedToMessageId, repliedToUser, repliedToText, callback)

fun GrpcClient.getDraft(
    roomId: String,
    callback: (draftText: String, repliedToMessageId: String, repliedToUser: String, repliedToText: String, hasDraft: Boolean) -> Unit
) = RealGrpcClient.getDraft(roomId, callback)

fun GrpcClient.deleteDraft(
    roomId: String, callback: (Boolean) -> Unit = {}
) = RealGrpcClient.deleteDraft(roomId, callback)

// ======= Muted Chats =======

fun GrpcClient.getMutedChats(callback: (List<String>) -> Unit) =
    RealGrpcClient.getMutedChats(callback)

fun GrpcClient.setMutedChat(
    roomId: String, muted: Boolean, callback: (Boolean) -> Unit = {}
) = RealGrpcClient.setMutedChat(roomId, muted, callback)

// ======= Favorites =======

fun GrpcClient.addFavorite(
    userId: String, messageId: String, callback: (Boolean, String) -> Unit
) = RealGrpcClient.addFavorite(userId, messageId, callback)

fun GrpcClient.removeFavorite(
    userId: String, messageId: String, callback: (Boolean) -> Unit
) = RealGrpcClient.removeFavorite(userId, messageId, callback)

fun GrpcClient.getFavorites(
    userId: String, callback: (List<Message>) -> Unit
) = RealGrpcClient.getFavorites(userId, callback)

// ======= Call Operations =======

fun GrpcClient.startCallSession() =
    RealGrpcClient.startCallSession()

fun GrpcClient.sendCallSignal(signal: CallMessageProto) =
    RealGrpcClient.sendCallSignal(signal)

// ======= FCM Logs =======

fun GrpcClient.getFCMLogs(callback: (List<FCMLogEntryProto>) -> Unit) =
    RealGrpcClient.getFCMLogs(callback)

// ======= Server Discovery =======

fun GrpcClient.getServers(context: Context, cb: (List<ServerInfoProto>) -> Unit) =
    RealGrpcClient.fetchServersList(context, cb)

// ======= Avatar Cache =======

fun GrpcClient.getAvatarCache(): Map<String, String> =
    RealGrpcClient.getAvatarCache()

fun GrpcClient.getFullAvatarCache(): Map<String, String> =
    RealGrpcClient.getFullAvatarCache()

fun GrpcClient.getFullAvatarUrl(username: String): String? =
    RealGrpcClient.getFullAvatarUrl(username)

fun GrpcClient.updateAvatarCache(username: String, avatarUrl: String, fullAvatarUrl: String = "") =
    RealGrpcClient.updateAvatarCache(username, avatarUrl, fullAvatarUrl)

// ======= User Identity =======

fun GrpcClient.getCurrentUsername(): String? = RealGrpcClient.getCurrentUsername()

fun GrpcClient.setUserId(userId: String) = RealGrpcClient.setUserId(userId)

fun GrpcClient.getUserId(): String? = RealGrpcClient.getUserId()

fun GrpcClient.fetchUserId(username: String, callback: (String?, Boolean) -> Unit) =
    RealGrpcClient.fetchUserId(username, callback)

// ======= Secret Chat =======

fun GrpcClient.createSecretChat(
    targetUsername: String, publicKey: String,
    callback: (String, Boolean, String, String) -> Unit
) {
    val clientVersion = lavender.client.android.BuildConfig.VERSION_NAME
    RealGrpcClient.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
        val (chatId, success, message) = lavender.client.android.data.grpc.createSecretChat(
            targetUsername = targetUsername, targetUserId = "",
            publicKey = publicKey, clientVersion = clientVersion
        )
        callback(chatId, success, message, "")
    }
}

fun GrpcClient.exchangeSecretKey(
    chatId: String, publicKey: String, callback: (Boolean, String, Boolean) -> Unit
) {
    RealGrpcClient.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
        val (success, peerKey, peerHasKey) = lavender.client.android.data.grpc.exchangeSecretKey(chatId, publicKey)
        callback(success, peerKey, peerHasKey)
    }
}

fun GrpcClient.getSecretChatKey(
    chatId: String, callback: (String, Boolean) -> Unit
) {
    RealGrpcClient.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
        val (peerKey, peerHasKey) = lavender.client.android.data.grpc.getSecretChatKey(chatId)
        callback(peerKey, peerHasKey)
    }
}

fun GrpcClient.sendE2EEMessage(chatId: String, encryptedPayload: String) {
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

// ======= Hermes Multi-Agent Orchestrator =======

fun GrpcClient.chatWithOrchestrator(
    userId: String, sessionId: String, message: String,
    agentId: String = "", mode: String = "",
    scope: CoroutineScope,
    onResponse: (token: String, finished: Boolean, error: String?, agentId: String, agentName: String) -> Unit
) = lavender.client.android.data.grpc.chatWithOrchestrator(
    userId, sessionId, message, agentId, mode, scope, onResponse
)

val GrpcClient.hermesResponses: kotlinx.coroutines.flow.SharedFlow<OrchestratorResponseProto>
    get() = lavender.client.android.data.grpc.hermesResponses

val GrpcClient.hermesTyping: kotlinx.coroutines.flow.SharedFlow<Boolean>
    get() = lavender.client.android.data.grpc.hermesTyping

val GrpcClient.owlResponses: kotlinx.coroutines.flow.SharedFlow<OwlResponseProto>
    get() = lavender.client.android.data.grpc.owlResponses

val GrpcClient.owlTyping: kotlinx.coroutines.flow.SharedFlow<Boolean>
    get() = lavender.client.android.data.grpc.owlTyping

val GrpcClient.serverNotifications: kotlinx.coroutines.flow.SharedFlow<ServerNotificationProto>
    get() = lavender.client.android.data.grpc.serverNotifications

fun GrpcClient.subscribeNotifications(userId: String, types: List<String> = emptyList()) =
    lavender.client.android.data.grpc.subscribeNotifications(userId, types, RealGrpcClient.scope)

suspend fun GrpcClient.getNotificationHistory(
    userId: String, limit: Int = 50
): List<ServerNotificationProto> = lavender.client.android.data.grpc.getNotificationHistory(userId, limit)

suspend fun GrpcClient.markNotificationsRead(
    userId: String, notificationIds: List<String>
): Boolean = lavender.client.android.data.grpc.markNotificationsRead(userId, notificationIds)

suspend fun GrpcClient.getUnreadCount(userId: String): Int =
    lavender.client.android.data.grpc.getUnreadCount(userId)

// ======= Hermes Unary Methods =======

suspend fun GrpcClient.listAgents(userId: String = ""): List<AgentInfoProto> =
    lavender.client.android.data.grpc.listAgents(userId)

suspend fun GrpcClient.listAgentPresets(): List<AgentPresetInfoProto> =
    lavender.client.android.data.grpc.listAgentPresets()

suspend fun GrpcClient.createAgent(
    userId: String, presetId: String, customName: String = "",
    customPrompt: String = "", model: String = "", maxTokens: Int = 0
): CreateAgentResponseProto =
    lavender.client.android.data.grpc.createAgent(userId, presetId, customName, customPrompt, model, maxTokens)

suspend fun GrpcClient.updateAgent(
    agentId: String, userId: String, name: String = "",
    systemPrompt: String = "", model: String = "", maxTokens: Int = 0
): Boolean = lavender.client.android.data.grpc.updateAgent(agentId, userId, name, systemPrompt, model, maxTokens)

suspend fun GrpcClient.deleteAgent(agentId: String, userId: String): Boolean =
    lavender.client.android.data.grpc.deleteAgent(agentId, userId)

suspend fun GrpcClient.listUserAgents(userId: String): List<AgentInfoProto> =
    lavender.client.android.data.grpc.listUserAgents(userId)

suspend fun GrpcClient.createHermesSession(
    userId: String, agentId: String = "", mode: String = ""
): CreateHermesSessionResponseProto =
    lavender.client.android.data.grpc.createHermesSession(userId, agentId, mode)

suspend fun GrpcClient.deleteHermesSession(sessionId: String, userId: String): Boolean =
    lavender.client.android.data.grpc.deleteHermesSession(sessionId, userId)

suspend fun GrpcClient.getOrchestratorHistory(
    sessionId: String, limit: Int = 50
): List<OrchestratorHistoryMessageProto> =
    lavender.client.android.data.grpc.getOrchestratorHistory(sessionId, limit)

// ======= AI Chat (unified for OWL + Hermes) =======

fun GrpcClient.chatWithAI(
    userId: String, sessionId: String, message: String, agentId: String = "",
    scope: CoroutineScope, onResponse: (token: String, finished: Boolean, error: String) -> Unit
) = lavender.client.android.data.grpc.chatWithAI(userId, sessionId, message, agentId, scope, onResponse)

val GrpcClient.aiChatResponses: kotlinx.coroutines.flow.SharedFlow<AIChatResponseProto>
    get() = lavender.client.android.data.grpc.aiChatResponses

val GrpcClient.aiChatTyping: kotlinx.coroutines.flow.SharedFlow<Boolean>
    get() = lavender.client.android.data.grpc.aiChatTyping

suspend fun GrpcClient.getAIChatHistory(
    sessionId: String, userId: String, limit: Int = 50
): List<AIChatMessageProto> =
    lavender.client.android.data.grpc.getAIChatHistory(sessionId, userId, limit)

suspend fun GrpcClient.getAIChatSettings(
    sessionId: String, userId: String
): AIChatSettingsProto =
    lavender.client.android.data.grpc.getAIChatSettings(sessionId, userId)

suspend fun GrpcClient.updateAIChatSettings(
    sessionId: String, userId: String, apiKey: String = "", model: String = ""
): UpdateAIChatSettingsResponseProto =
    lavender.client.android.data.grpc.updateAIChatSettings(sessionId, userId, apiKey, model)

// ======= Remote Agent =======

suspend fun GrpcClient.listRemoteAgents(filterStatus: String = ""): List<RemoteAgentInfoProto> =
    lavender.client.android.data.grpc.listRemoteAgents(filterStatus)

suspend fun GrpcClient.getRemoteAgentStatus(agentId: String): GetRemoteAgentStatusResponseProto =
    lavender.client.android.data.grpc.getRemoteAgentStatus(agentId)

suspend fun GrpcClient.deployAgentTask(
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

fun GrpcClient.deployAgentTaskStream(
    agentId: String, taskType: String,
    params: Map<String, String> = emptyMap(), workingDir: String = "",
    timeoutSec: Int = 60, tunnelMode: Int = 0,
    tunnelHost: String = "", tunnelPort: Int = 22,
    tunnelUser: String = "", tunnelPassword: String = "",
    tunnelServerHost: String = "localhost", tunnelServerPort: Int = 50051,
    tunnelLocalPort: Int = 50052
): kotlinx.coroutines.flow.Flow<DeployAgentTaskStreamResponseProto> =
    lavender.client.android.data.grpc.deployAgentTaskStream(
        agentId, taskType, params, workingDir, timeoutSec,
        tunnelMode, tunnelHost, tunnelPort, tunnelUser, tunnelPassword,
        tunnelServerHost, tunnelServerPort, tunnelLocalPort
    )

// ======= Agent Token Management =======

suspend fun GrpcClient.generateAgentToken(
    agentId: String, agentName: String, capabilities: List<String>,
    ttlHours: Int, adminUserId: String
): GenerateAgentTokenResponseProto {
    android.util.Log.d("GrpcClient", "generateAgentToken CALLED: agentId=$agentId name=$agentName")
    return lavender.client.android.data.grpc.generateAgentToken(
        agentId, agentName, capabilities, ttlHours, adminUserId
    )
}

suspend fun GrpcClient.revokeAgentToken(
    agentId: String, adminUserId: String
): RevokeAgentTokenResponseProto =
    lavender.client.android.data.grpc.revokeAgentToken(agentId, adminUserId)

suspend fun GrpcClient.listAgentTokens(
    adminUserId: String
): ListAgentTokensResponseProto =
    lavender.client.android.data.grpc.listAgentTokens(adminUserId)

// ======= Agent Process Management =======

suspend fun GrpcClient.startAgentOnServer(
    agentId: String, agentName: String, token: String,
    serverAddress: String = "",
    capabilities: List<String> = listOf("shell", "git", "build", "file", "docker", "ai"),
    adminUserId: String = ""
): StartAgentResponseProto =
    lavender.client.android.data.grpc.startAgentOnServer(
        agentId, agentName, token, serverAddress, capabilities, adminUserId
    )

suspend fun GrpcClient.stopAgentOnServer(
    agentId: String, adminUserId: String = ""
): StopAgentResponseProto =
    lavender.client.android.data.grpc.stopAgentOnServer(agentId, adminUserId)

suspend fun GrpcClient.getAgentProcessStatus(
    agentId: String, adminUserId: String = ""
): GetAgentProcessStatusResponseProto =
    lavender.client.android.data.grpc.getAgentProcessStatus(agentId, adminUserId)
