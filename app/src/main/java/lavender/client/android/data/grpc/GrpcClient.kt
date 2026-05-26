package lavender.client.android.data.grpc

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import lavender.client.android.data.models.Message // 🛠️ ВАЖНЫЙ ИМПОРТ
import lavender.client.android.data.models.ChatInfo // 🛠️ ВАЖНЫЙ ИМПОРТ
import lavender.client.android.data.proto.UserInfoProto
import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.data.proto.FCMLogEntryProto
import lavender.client.android.data.proto.GetUserProfileResponseProto
import lavender.client.android.data.proto.DeviceInfoProto
import lavender.client.android.data.proto.CallMessageProto

object GrpcClient {
    private val realGrpcClient = RealGrpcClient

    // Область видимости для конвертации потоков
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 1. Основной статус (Enum)
    val connectionStatus: StateFlow<ConnectionStatus> = realGrpcClient.connectionStatus

    // 2. Boolean статус (для совместимости), теперь с .value!
    val connectionState: StateFlow<Boolean> = realGrpcClient.connectionStatus
        .map { it == ConnectionStatus.READY }
        .stateIn(scope, SharingStarted.Eagerly, realGrpcClient.connectionStatus.value == ConnectionStatus.READY)

    // 3. Список сообщений и юзеров (они и так StateFlow в RealGrpcClient)
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

    var hasCheckedForUpdates: Boolean
        get() = realGrpcClient.hasCheckedForUpdates
        set(value) { realGrpcClient.hasCheckedForUpdates = value }

    var isAppInBackground: Boolean
        get() = realGrpcClient.isAppInBackground
        set(value) { realGrpcClient.isAppInBackground = value }

    fun connect(serverAddress: String, useTls: Boolean = false, port: Int = 50051, context: Context? = null, forceReconnect: Boolean = false) {
        realGrpcClient.connect(serverAddress, useTls, port, context, forceReconnect)
    }

    fun shouldForceReconnect(): Boolean {
        return realGrpcClient.shouldForceReconnect()
    }
    
    fun disconnect() {
        realGrpcClient.disconnect()
    }
    
    fun startChat(username: String, password: String, joinMessage: String, register: Boolean = false, email: String = "", deviceId: String = "", deviceName: String = "", onMessageReceived: (Message) -> Unit) {
        realGrpcClient.startChat(username, password, joinMessage, register, deviceId, deviceName, onMessageReceived)
    }
    
    fun sendMessage(message: Message) {
        realGrpcClient.sendMessage(message)
    }

    fun addLocalMessage(message: Message) {
        realGrpcClient.addLocalMessage(message)
    }
    
    fun deleteMessage(message: Message) {
        realGrpcClient.deleteMessage(message)
    }

    fun editMessage(messageId: String, text: String, callback: (Boolean, String) -> Unit = { _, _ -> }) {
        realGrpcClient.editMessage(messageId, text, callback)
    }

    fun updateMessage(message: Message) {
        realGrpcClient.updateMessage(message)
    }

    fun setReaction(messageId: String, username: String, emoji: String) {
        realGrpcClient.setReaction(messageId, username, emoji)
    }

    fun registerToken(user: String, token: String, pushEnabled: Boolean = true) {
        realGrpcClient.registerToken(user, token, pushEnabled)
    }

    fun clearSystemNotification() {
        realGrpcClient.clearSystemNotification()
    }

    fun loadHistory(roomId: String, onCompletion: () -> Unit = {}) {
        realGrpcClient.loadHistory(roomId, onCompletion)
    }

    fun setRoomId(roomId: String) {
        realGrpcClient.setRoomId(roomId)
    }

    fun getChats(username: String, skipCache: Boolean = false, callback: (List<ChatInfo>) -> Unit) {
        realGrpcClient.getChats(username, skipCache, callback)
    }

    fun getChatListVersion(username: String, callback: (Long) -> Unit) {
        realGrpcClient.getChatListVersion(username, callback)
    }

    fun getThemes(username: String, callback: (String, List<CustomThemeProto>) -> Unit) {
        realGrpcClient.getThemes(username, callback)
    }

    fun saveTheme(username: String, theme: CustomThemeProto, callback: (Boolean, String) -> Unit) {
        realGrpcClient.saveTheme(username, theme, callback)
    }

    fun setCurrentTheme(username: String, themeId: String, callback: (Boolean) -> Unit) {
        realGrpcClient.setCurrentTheme(username, themeId, callback)
    }

    fun deleteTheme(username: String, themeId: String, callback: (Boolean) -> Unit) {
        realGrpcClient.deleteTheme(username, themeId, callback)
    }

    fun updateChatName(chatId: String, newName: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.updateChatName(chatId, newName, callback)
    }

    fun removeParticipant(chatId: String, username: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.removeParticipant(chatId, username, callback)
    }

    fun createDirectChat(user1: String, user2: String, callback: (String?) -> Unit) {
        realGrpcClient.createDirectChat(user1, user2, callback)
    }

    fun createGroupChat(name: String, participants: List<String>, creator: String, callback: (String?) -> Unit) {
        realGrpcClient.createGroupChat(name, participants, creator, callback)
    }

    fun loadUsers() {
        realGrpcClient.loadUsers()
    }

    fun loadAllUsers(callback: ((List<UserInfoProto>) -> Unit)? = null) {
        realGrpcClient.loadAllUsers(callback ?: {})
    }

    fun getAllChats(callback: (List<ChatInfo>) -> Unit) {
        realGrpcClient.getAllChats(callback)
    }

    fun updateUsername(oldUsername: String, newUsername: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.updateUsername(oldUsername, newUsername, callback)
    }

    fun updatePassword(username: String, oldPassword: String, newPassword: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.updatePassword(username, oldPassword, newPassword, callback)
    }

    fun adminUpdatePassword(targetUsername: String, newPassword: String, adminUsername: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.adminUpdatePassword(targetUsername, newPassword, adminUsername, callback)
    }

    fun markRead(roomId: String, username: String, onCompletion: (() -> Unit)? = null) {
        realGrpcClient.markRead(roomId, username, onCompletion)
    }

    fun sendTypingSignal(username: String, isTyping: Boolean) {
        realGrpcClient.sendTypingSignal(username, isTyping)
    }

    fun startCallSession() {
        realGrpcClient.startCallSession()
    }

    fun sendCallSignal(signal: CallMessageProto) {
        realGrpcClient.sendCallSignal(signal)
    }

    fun updateAvatar(username: String, avatarUrl: String, fullAvatarUrl: String = "", callback: (Boolean, String) -> Unit) {
        realGrpcClient.updateAvatar(username, avatarUrl, fullAvatarUrl, callback)
    }

    fun updateChatAvatar(chatId: String, avatarUrl: String, username: String, fullAvatarUrl: String = "", callback: (Boolean, String) -> Unit) {
        realGrpcClient.updateChatAvatar(chatId, avatarUrl, username, fullAvatarUrl, callback)
    }

    fun updateChatSettings(chatId: String, allowAdd: Boolean, callback: (Boolean, String) -> Unit) {
        realGrpcClient.updateChatSettings(chatId, allowAdd, callback)
    }

    fun getUserAvatar(username: String, userId: String = "", callback: (String) -> Unit) {
        realGrpcClient.getUserAvatar(username, userId, callback)
    }

    fun addParticipant(chatId: String, username: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.addParticipant(chatId, username, callback)
    }

    fun addParticipants(chatId: String, usernames: List<String>, callback: (Boolean, String) -> Unit) {
        realGrpcClient.addParticipants(chatId, usernames, callback)
    }

    fun deleteChat(chatId: String, requesterUsername: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.deleteChat(chatId, requesterUsername, callback)
    }

    fun requestPasswordReset(email: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.requestPasswordReset(email, callback)
    }

    fun resetPassword(token: String, newPw: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.resetPassword(token, newPw, callback)
    }

    fun getDevices(userId: String, callback: (List<DeviceInfoProto>) -> Unit) {
        realGrpcClient.getDevices(userId, callback)
    }

    fun deleteDevice(userId: String, deviceId: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.deleteDevice(userId, deviceId, callback)
    }

    fun deleteOtherDevices(userId: String, currentDeviceId: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.deleteOtherDevices(userId, currentDeviceId, callback)
    }

    fun deleteProfile(username: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.deleteProfile(username, callback)
    }

    fun getUserProfile(userId: String, callback: (GetUserProfileResponseProto?) -> Unit) {
        realGrpcClient.getUserProfile(userId, callback)
    }

    fun updateProfile(username: String, bio: String, status: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.updateProfile(username, bio, status, callback)
    }

    fun addContact(username: String, contactUsername: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.addContact(username, contactUsername, callback)
    }

    fun removeContact(username: String, contactUsername: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.removeContact(username, contactUsername, callback)
    }

    fun getContacts(username: String, callback: (List<String>) -> Unit) {
        realGrpcClient.getContacts(username, callback)
    }

    fun getFCMLogs(callback: (List<FCMLogEntryProto>) -> Unit) {
        realGrpcClient.getFCMLogs(callback)
    }

    fun saveDraft(roomId: String, draftText: String, repliedToMessageId: String = "", repliedToUser: String = "", repliedToText: String = "", callback: (Boolean, String) -> Unit = { _, _ -> }) {
        realGrpcClient.saveDraft(roomId, draftText, repliedToMessageId, repliedToUser, repliedToText, callback)
    }

    fun getDraft(roomId: String, callback: (draftText: String, repliedToMessageId: String, repliedToUser: String, repliedToText: String, hasDraft: Boolean) -> Unit) {
        realGrpcClient.getDraft(roomId, callback)
    }

    fun deleteDraft(roomId: String, callback: (Boolean) -> Unit = {}) {
        realGrpcClient.deleteDraft(roomId, callback)
    }

    fun getMutedChats(callback: (List<String>) -> Unit) {
        realGrpcClient.getMutedChats(callback)
    }

    fun setMutedChat(roomId: String, muted: Boolean, callback: (Boolean) -> Unit = {}) {
        realGrpcClient.setMutedChat(roomId, muted, callback)
    }

    fun getAvatarCache(): Map<String, String> {
        return realGrpcClient.getAvatarCache()
    }

    fun getFullAvatarCache(): Map<String, String> {
        return realGrpcClient.getFullAvatarCache()
    }

    fun getFullAvatarUrl(username: String): String? {
        return realGrpcClient.getFullAvatarUrl(username)
    }

    val avatarCacheFlow = realGrpcClient.avatarCacheFlow

    fun updateAvatarCache(username: String, avatarUrl: String, fullAvatarUrl: String = "") {
        realGrpcClient.updateAvatarCache(username, avatarUrl, fullAvatarUrl)
    }

    fun getCurrentUsername(): String? = realGrpcClient.getCurrentUsername()

    fun setUserId(userId: String) {
        realGrpcClient.setUserId(userId)
    }

    fun getUserId(): String? = realGrpcClient.getUserId()

    fun fetchUserId(username: String, callback: (String?, Boolean) -> Unit) {
        realGrpcClient.fetchUserId(username, callback)
    }

    fun addFavorite(userId: String, messageId: String, callback: (Boolean, String) -> Unit) {
        realGrpcClient.addFavorite(userId, messageId, callback)
    }

    fun removeFavorite(userId: String, messageId: String, callback: (Boolean) -> Unit) {
        realGrpcClient.removeFavorite(userId, messageId, callback)
    }

    fun getFavorites(userId: String, callback: (List<Message>) -> Unit) {
        realGrpcClient.getFavorites(userId, callback)
    }

    fun clearMessages() {
        realGrpcClient.clearMessages()
    }
}