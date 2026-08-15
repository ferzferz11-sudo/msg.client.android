package lavender.client.android.data.proto

import com.google.protobuf.Timestamp

// Reaction class matching messenger.proto
data class ReactionProto(
    val user: String = "",
    val emoji: String = ""
)

// Message class matching messenger.proto
data class MessageProto(
    val id: String = "",
    val user: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null,
    val reactions: List<ReactionProto> = emptyList(),
    val password: String = "",
    val repliedToMessageId: String = "",
    val repliedToUser: String = "",
    val repliedToText: String = "",
    val roomId: String = "",
    val isRead: Boolean = false,
    val avatarUrl: String = "",
    val imageUrl: String = "",
    val imageUrls: List<String> = emptyList(),
    val edited: Boolean = false,
    val clientVersion: String = "",
    val isSuperAdmin: Boolean = false,
    val voiceUrl: String = "",
    val duration: Int = 0,
    val register: Boolean = false,
    val deviceId: String = "",
    val deviceName: String = "",
    val userId: String = "",
    val isE2Ee: Boolean = false,
    val e2EePayload: String = "",
    val jwtToken: String = ""
) {
    class Builder {
        private var user: String = ""
        private var text: String = ""
        private var createdAt: Timestamp? = null
        private var id: String = ""
        private var password: String = ""
        private var edited: Boolean = false
        private var repliedToMessageId: String = ""
        private var repliedToUser: String = ""
        private var repliedToText: String = ""
        private var roomId: String = ""
        private var isRead: Boolean = false
        private var avatarUrl: String = ""
        private var imageUrl: String = ""
        private var imageUrls = mutableListOf<String>()
        private var clientVersion: String = ""
        private var isSuperAdmin: Boolean = false
        private var voiceUrl: String = ""
        private var duration: Int = 0
        private var register: Boolean = false
        private var deviceId: String = ""
        private var deviceName: String = ""
        private var userId: String = ""
        private var isE2Ee: Boolean = false
        private var e2EePayload: String = ""
        private var jwtToken: String = ""
        private val reactions = mutableListOf<ReactionProto>()
        
        fun setUser(user: String): Builder {
            this.user = user
            return this
        }
        
        fun setText(text: String): Builder {
            this.text = text
            return this
        }
        
        fun setCreatedAt(timestamp: Timestamp): Builder {
            this.createdAt = timestamp
            return this
        }

        fun setId(id: String): Builder {
            this.id = id
            return this
        }

        fun setPassword(password: String): Builder {
            this.password = password
            return this
        }

        fun setRepliedToMessageId(repliedToMessageId: String): Builder {
            this.repliedToMessageId = repliedToMessageId
            return this
        }

        fun setRepliedToUser(repliedToUser: String): Builder {
            this.repliedToUser = repliedToUser
            return this
        }

        fun setRepliedToText(repliedToText: String): Builder {
            this.repliedToText = repliedToText
            return this
        }

        fun setRoomId(roomId: String): Builder {
            this.roomId = roomId
            return this
        }

        fun setIsRead(isRead: Boolean): Builder {
            this.isRead = isRead
            return this
        }

        fun setAvatarUrl(avatarUrl: String): Builder {
            this.avatarUrl = avatarUrl
            return this
        }

        fun setEdited(edited: Boolean): Builder {
            this.edited = edited
            return this
        }

        fun setImageUrl(imageUrl: String): Builder {
            this.imageUrl = imageUrl
            return this
        }

        fun setClientVersion(clientVersion: String): Builder {
            this.clientVersion = clientVersion
            return this
        }

        fun setIsSuperAdmin(isSuperAdmin: Boolean): Builder {
            this.isSuperAdmin = isSuperAdmin
            return this
        }

        fun setVoiceUrl(voiceUrl: String): Builder {
            this.voiceUrl = voiceUrl
            return this
        }

        fun setDuration(duration: Int): Builder {
            this.duration = duration
            return this
        }

        fun setRegister(register: Boolean): Builder {
            this.register = register
            return this
        }

        fun setDeviceId(deviceId: String): Builder {
            this.deviceId = deviceId
            return this
        }

        fun setDeviceName(deviceName: String): Builder {
            this.deviceName = deviceName
            return this
        }

        fun setUserId(userId: String): Builder {
            this.userId = userId
            return this
        }

        fun setIsE2Ee(isE2Ee: Boolean): Builder {
            this.isE2Ee = isE2Ee
            return this
        }

        fun setE2EePayload(e2EePayload: String): Builder {
            this.e2EePayload = e2EePayload
            return this
        }

        fun setJwtToken(jwtToken: String): Builder {
            this.jwtToken = jwtToken
            return this
        }

        @Suppress("unused")
        fun addReaction(reaction: ReactionProto): Builder {
            this.reactions.add(reaction)
            return this
        }

        @Suppress("unused")
        fun addImageUrls(imageUrl: String): Builder {
            this.imageUrls.add(imageUrl)
            return this
        }

        fun build(): MessageProto {
            return MessageProto(id, user, text, createdAt, reactions, password, repliedToMessageId, repliedToUser, repliedToText, roomId, isRead, avatarUrl, imageUrl, imageUrls, edited, clientVersion, isSuperAdmin, voiceUrl, duration, register, deviceId, deviceName, userId, isE2Ee, e2EePayload, jwtToken)
        }
    }
    
    companion object {
        fun newBuilder(): Builder = Builder()
    }
}

// Chat Info
data class ChatInfoProto(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val participants: String = "",
    val createdAt: Timestamp? = null,
    val unreadCount: Int = 0,
    val lastMessageTime: Timestamp? = null,
    val creator: String = "",
    val lastMessageText: String = "",
    val avatarUrl: String = "",
    val fullAvatarUrl: String = "",
    val lastMessageUsername: String = "",
    val lastMessageHasImage: Boolean = false,
    val allowMembersToAdd: Boolean = false,
    val conferenceStartTime: Timestamp? = null,
    val isSecret: Boolean = false,
    val peerPublicKey: String = "",
    val e2eeReady: Boolean = false,
    val activeAgentId: String = "",   // For hermes sessions: current active agent
    val agentMode: String = "",       // For hermes sessions: single/parallel/pipeline
    val isPinned: Boolean = false,    // ChatList v2: pinned status
    val isMuted: Boolean = false,     // ChatList v2: muted status
    val isArchived: Boolean = false,  // ChatList v2: archived status
    val pinnedAt: Long = 0L,          // ChatList v2: timestamp when pinned (for sort order)
    val companyId: String = "",       // Company chat: company ID
    val companyChatAccess: String = "", // Company chat: access level
    val companyMinPositionLevel: Int = 0, // Company chat: min position level
    val selfDestructTimer: Int = 0    // Self-destruct timer in seconds: 0=disabled, 30, 60, 300, 3600, 86400
)

// Mark Read Request/Response
data class MarkReadRequestProto(
    val roomId: String = "",
    val username: String = "",
    val userId: String = ""
)

data class MarkReadResponseProto(
    val success: Boolean = false
)

// Delete Chat Request/Response
data class DeleteChatRequestProto(
    val chatId: String = "",
    val requesterUsername: String = "",
    val requesterUserId: String = ""
)

data class DeleteChatResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

// Get Chats Request/Response
data class GetChatsRequestProto(
    val username: String = "",
    val userId: String = "",
    val limit: Int = 0,
    val offset: Int = 0,
    val filter: String = "",
    val cursor: String = ""
)

data class GetChatsResponseProto(
    val chats: List<ChatInfoProto> = emptyList(),
    val nextCursor: String = "",
    val hasMore: Boolean = false
)

// Create Direct Chat Request/Response
data class CreateDirectChatRequestProto(
    val user1: String = "",
    val user2: String = "",
    val user1Id: String = "",
    val user2Id: String = ""
)

data class CreateDirectChatResponseProto(
    val chatId: String = "",
    val success: Boolean = false
)

// Create Group Chat Request/Response
data class CreateGroupChatRequestProto(
    val name: String = "",
    val participants: List<String> = emptyList(),
    val creator: String = "",
    val creatorId: String = "",
    val participantIds: List<String> = emptyList(),
    val type: String = "group"
)

data class CreateGroupChatResponseProto(
    val chatId: String = "",
    val success: Boolean = false
)

// Update Username Request/Response
data class UpdateUsernameRequestProto(
    val oldUsername: String = "",
    val newUsername: String = "",
    val userId: String = ""
)

data class UpdateUsernameResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

// Update Password Request/Response
data class UpdatePasswordRequestProto(
    val username: String = "",
    val oldPassword: String = "",
    val newPassword: String = "",
    val userId: String = ""
)

data class UpdatePasswordResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class AdminUpdatePasswordRequestProto(
    val targetUsername: String = "",
    val newPassword: String = "",
    val adminUsername: String = "",
    val adminUserId: String = ""
)

data class AdminUpdatePasswordResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

// Other message types
@Suppress("unused")
data class ClientListRequestProto(
    val dummy: Boolean = false // Empty message
)

@Suppress("unused")
data class ClientListResponseProto(
    val clients: List<String> = emptyList()
)

data class TokenRequestProto(
    val user: String = "",
    val token: String = "",
    val pushEnabled: Boolean = true,
    val userId: String = ""
)

data class TokenResponseProto(
    val success: Boolean = false
)

// Update Avatar Request/Response
data class UpdateAvatarRequestProto(
    val username: String = "",
    val avatarUrl: String = "",
    val fullAvatarUrl: String = "",
    val userId: String = ""
)

data class UpdateAvatarResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

// Profile updates
data class UpdateProfileRequestProto(
    val username: String = "",
    val bio: String = "",
    val status: String = "",
    val userId: String = ""
)

data class UpdateProfileResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class GetUserProfileRequestProto(
    val username: String = "",
    val userId: String = ""
)

data class GetUserProfileResponseProto(
    val username: String = "",
    val bio: String = "",
    val status: String = "",
    val avatarUrl: String = "",
    val lastSeenAt: Timestamp? = null,
    val fullAvatarUrl: String = ""
)

// Participants management
data class AddParticipantRequestProto(
    val chatId: String = "",
    val username: String = "",
    val userId: String = ""
)

data class AddParticipantResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class RemoveParticipantRequestProto(
    val chatId: String = "",
    val username: String = "",
    val userId: String = ""
)

data class RemoveParticipantResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class GetAllChatsRequestProto(
    val dummy: Boolean = false
)

data class GetAllChatsResponseProto(
    val chats: List<ChatInfoProto> = emptyList()
)

data class UpdateChatNameRequestProto(
    val chatId: String = "",
    val newName: String = ""
)

data class UpdateChatNameResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

// Update Chat Avatar Request/Response
data class UpdateChatAvatarRequestProto(
    val chatId: String = "",
    val avatarUrl: String = "",
    val username: String = "",
    val fullAvatarUrl: String = "",
    val userId: String = ""
)

data class UpdateChatAvatarResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class UpdateChatSettingsRequestProto(
    val chatId: String = "",
    val allowMembersToAdd: Boolean = false,
    val userId: String = ""
)

data class UpdateChatSettingsResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

// Get User Avatar Request/Response
data class GetUserAvatarRequestProto(
    val username: String = "",
    val userId: String = ""
)

data class GetUserAvatarResponseProto(
    val avatarUrl: String = "",
    val fullAvatarUrl: String = ""
)

// Delete Profile Request/Response
data class DeleteProfileRequestProto(
    val username: String = "",
    val userId: String = ""
)

data class DeleteProfileResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

// Typing Signal Request/Response
data class TypingRequestProto(
    val roomId: String = "",
    val username: String = "",
    val isTyping: Boolean = false,
    val userId: String = ""
)

data class TypingSignalProto(
    val roomId: String = "",
    val username: String = "",
    val isTyping: Boolean = false,
    val userId: String = ""
)

// Contacts management
data class AddContactRequestProto(
    val username: String = "",
    val contactUsername: String = "",
    val userId: String = ""
)

data class AddContactResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class RemoveContactRequestProto(
    val username: String = "",
    val contactUsername: String = "",
    val userId: String = ""
)

data class RemoveContactResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class GetContactsRequestProto(
    val username: String = "",
    val userId: String = ""
)

data class GetContactsResponseProto(
    val contacts: List<String> = emptyList()
)

data class GetChatListVersionRequestProto(
    val username: String = "",
    val userId: String = ""
)

data class GetChatListVersionResponseProto(
    val version: Long = 0
)

// Themes management
data class CustomThemeProto(
    val id: String = "",
    val name: String = "",
    val primaryColor: String = "",
    val onPrimaryColor: String = "",
    val surfaceColor: String = "",
    val onSurfaceColor: String = "",
    val backgroundColor: String = "",
    val textPrimaryColor: String = "",
    val textSecondaryColor: String = "",
    val isDark: Boolean = false,
    val chatListBackgroundImageUrl: String = "",
    val chatBackgroundImageUrl: String = "",
    val bottomPanelColor: String = "",
    val onBottomPanelColor: String = "",
    val surfaceContainer: String = "",
    val outgoingBubbleColor: String = "",
    val incomingBubbleColor: String = "",
    val outgoingTextColor: String = "",
    val incomingTextColor: String = ""
)

data class GetThemesRequestProto(
    val username: String = "",
    val userId: String = ""
)

data class GetThemesResponseProto(
    val currentThemeId: String = "",
    val customThemes: List<CustomThemeProto> = emptyList()
)

data class SaveThemeRequestProto(
    val username: String = "",
    val theme: CustomThemeProto = CustomThemeProto(),
    val userId: String = ""
)

data class SaveThemeResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class SetCurrentThemeRequestProto(
    val username: String = "",
    val themeId: String = "",
    val userId: String = ""
)

data class SetCurrentThemeResponseProto(
    val success: Boolean = false
)

data class DeleteThemeRequestProto(
    val username: String = "",
    val themeId: String = "",
    val userId: String = ""
)

data class DeleteThemeResponseProto(
    val success: Boolean = false
)

data class FCMLogEntryProto(
    val timestamp: String = "",
    val level: String = "",
    val message: String = ""
)

data class GetFCMLogsRequestProto(
    val dummy: String = ""
)

data class GetFCMLogsResponseProto(
    val logs: List<FCMLogEntryProto> = emptyList()
)

// Draft messages
data class SaveDraftRequestProto(
    val userId: String = "",
    val roomId: String = "",
    val draftText: String = "",
    val repliedToMessageId: String = "",
    val repliedToUser: String = "",
    val repliedToText: String = ""
)

data class SaveDraftResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class GetDraftRequestProto(
    val userId: String = "",
    val roomId: String = ""
)

data class GetDraftResponseProto(
    val draftText: String = "",
    val repliedToMessageId: String = "",
    val repliedToUser: String = "",
    val repliedToText: String = "",
    val hasDraft: Boolean = false
)

data class DeleteDraftRequestProto(
    val userId: String = "",
    val roomId: String = ""
)

data class DeleteDraftResponseProto(
    val success: Boolean = false
)

data class GetMutedChatsRequestProto(
    val userId: String = ""
)

data class GetMutedChatsResponseProto(
    val roomIds: List<String> = emptyList()
)

data class SetMutedChatRequestProto(
    val userId: String = "",
    val roomId: String = "",
    val muted: Boolean = true
)

data class SetMutedChatResponseProto(
    val success: Boolean = false
)

data class SetSelfDestructTimerRequestProto(
    val roomId: String = "",
    val timerSeconds: Int = 0
)

data class SetSelfDestructTimerResponseProto(
    val success: Boolean = false,
    val error: String = ""
)

data class GetAllUsersRequestProto(
    val dummy: Boolean = false
)

data class UserInfoProto(
    val username: String = "",
    val avatarUrl: String = "",
    val lastClientVersion: String = "",
    val lastSeenAt: Timestamp? = null,
    val email: String = "",
    val userId: String = "",
    val isSuperAdmin: Boolean = false
)

data class GetAllUsersResponseProto(
    val users: List<UserInfoProto> = emptyList(),
    val serverTime: Timestamp? = null
)

data class GetUserIdRequestProto(
    val username: String = ""
)

data class GetUserIdResponseProto(
    val userId: String = "",
    val found: Boolean = false
)

data class AddSavedMessageRequestProto(
    val userId: String = "",
    val messageId: String = ""
)

data class AddSavedMessageResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class RemoveSavedMessageRequestProto(
    val userId: String = "",
    val messageId: String = ""
)

data class RemoveSavedMessageResponseProto(
    val success: Boolean = false
)

data class RequestPasswordResetRequestProto(
    val email: String = ""
)

data class RequestPasswordResetResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class ResetPasswordRequestProto(
    val token: String = "",
    val newPassword: String = ""
)

data class ResetPasswordResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class DeviceInfoProto(
    val deviceId: String = "",
    val deviceName: String = "",
    val clientVersion: String = "",
    val lastSeenAt: Timestamp? = null,
    val ipAddress: String = ""
)

data class GetDevicesRequestProto(
    val userId: String = ""
)

data class GetDevicesResponseProto(
    val devices: List<DeviceInfoProto> = emptyList()
)

data class DeleteDeviceRequestProto(
    val userId: String = "",
    val deviceId: String = ""
)

data class DeleteDeviceResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class GetSavedMessagesRequestProto(
    val userId: String = ""
)

data class GetSavedMessagesResponseProto(
    val messages: List<MessageV2Proto> = emptyList()
)

data class CallMessageProto(
    val callId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val type: Type = Type.INITIATE,
    val payload: String = "",
    val senderName: String = "",
    val receiverName: String = "",
    val roomId: String = ""
) {
    enum class Type(val value: Int) {
        INITIATE(0),
        ACCEPT(1),
        REJECT(2),
        HANGUP(3),
        OFFER(4),
        ANSWER(5),
        ICE_CANDIDATE(6),
        INITIATE_CONFERENCE(10),
        JOIN_CONFERENCE(11),
        LEAVE_CONFERENCE(12),
        END_CONFERENCE(13),
        INVITE_TO_CONFERENCE(14),
        REMOVE_FROM_CONFERENCE(15),
        UPDATE_CONFERENCE(16);

        companion object {
            fun fromInt(value: Int) = values().firstOrNull { it.value == value } ?: INITIATE
        }
    }
}

// ======= Secret Chat (E2EE) =======

data class CreateSecretChatRequestProto(
    val targetUsername: String = "",
    val targetUserId: String = "",
    val publicKey: String = "",
    val clientVersion: String = ""
)

data class CreateSecretChatResponseProto(
    val chatId: String = "",
    val success: Boolean = false,
    val message: String = "",
    val peerPublicKey: String = ""
)

data class ExchangeSecretKeyRequestProto(
    val chatId: String = "",
    val publicKey: String = ""
)

data class ExchangeSecretKeyResponseProto(
    val success: Boolean = false,
    val peerPublicKey: String = "",
    val peerHasKey: Boolean = false
)

data class GetSecretChatKeyRequestProto(
    val chatId: String = ""
)

data class GetSecretChatKeyResponseProto(
    val peerPublicKey: String = "",
    val peerHasKey: Boolean = false
)

// ======= Remote Agent proto classes =======

data class ListRemoteAgentsRequestProto(
    val filterStatus: String = ""
)

data class RemoteAgentInfoProto(
    val id: String = "",
    val name: String = "",
    val host: String = "",
    val ipAddress: String = "",
    val os: String = "",
    val status: String = "",
    val capabilities: List<String> = emptyList(),
    val activeTasks: Int = 0,
    val lastHeartbeat: String = ""
)

data class RemoteAgentInfo(
    val id: String = "",
    val name: String = "",
    val host: String = "",
    val ipAddress: String = "",
    val os: String = "",
    val status: String = "",
    val capabilities: List<String> = emptyList(),
    val activeTasks: Int = 0,
    val lastHeartbeat: String = ""
)

data class ListRemoteAgentsResponseProto(
    val agents: List<RemoteAgentInfoProto> = emptyList()
)

data class DeployAgentTaskRequestProto(
    val agentId: String = "",
    val taskType: String = "",
    val params: Map<String, String> = emptyMap(),
    val workingDir: String = "",
    val timeoutSec: Int = 0,
    val tunnelMode: Int = 0,          // 0 = TUNNEL_NONE, 1 = TUNNEL_SSH
    val tunnelHost: String = "",      // SSH хост туннеля
    val tunnelPort: Int = 22,         // SSH порт
    val tunnelUser: String = "",      // SSH пользователь
    val tunnelPassword: String = "",  // SSH пароль
    val tunnelServerHost: String = "localhost",  // хост сервера за туннелем
    val tunnelServerPort: Int = 50051,           // порт сервера за туннелем
    val tunnelLocalPort: Int = 50052             // локальный порт для проброса
)

data class DeployAgentTaskResponseProto(
    val taskId: String = "",
    val success: Boolean = false,
    val message: String = "",
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = 0
)

data class DeployAgentTaskStreamResponseProto(
    val taskId: String = "",
    val stdoutChunk: String = "",
    val stderrChunk: String = "",
    val progress: String = "",
    val status: String = "",
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = 0,
    val durationMs: Long = 0,
    val error: String = "",
    val done: Boolean = false
)

data class GetRemoteAgentStatusRequestProto(
    val agentId: String = ""
)

data class GetRemoteAgentStatusResponseProto(
    val id: String = "",
    val name: String = "",
    val status: String = "",
    val host: String = "",
    val capabilities: List<String> = emptyList(),
    val activeTasks: Int = 0,
    val lastHeartbeat: String = ""
)

// ======= Agent Token Management proto classes =======

data class GenerateAgentTokenRequestProto(
    val agentId: String = "",
    val agentName: String = "",
    val capabilities: List<String> = emptyList(),
    val ttlHours: Int = 0,
    val adminUserId: String = ""
)

data class GenerateAgentTokenResponseProto(
    val success: Boolean = false,
    val token: String = "",
    val error: String = "",
    val expiresAt: Long = 0
)

data class RevokeAgentTokenRequestProto(
    val agentId: String = "",
    val adminUserId: String = ""
)

data class RevokeAgentTokenResponseProto(
    val success: Boolean = false,
    val error: String = ""
)

data class ListAgentTokensRequestProto(
    val adminUserId: String = ""
)

data class AgentTokenInfoProto(
    val id: Long = 0,
    val agentId: String = "",
    val agentName: String = "",
    val tokenHash: String = "",
    val capabilities: List<String> = emptyList(),
    val createdAt: String = "",
    val expiresAt: String = "",
    val revoked: Boolean = false,
    val createdBy: String = ""
)

data class ListAgentTokensResponseProto(
    val success: Boolean = false,
    val tokens: List<AgentTokenInfoProto> = emptyList(),
    val error: String = ""
)

// ======= Server Notification proto classes =======

data class ServerNotificationProto(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: String = "",
    val metadata: Map<String, String> = emptyMap(),
    val isRead: Boolean = false
)

data class SubscribeNotificationsRequestProto(
    val userId: String = "",
    val types: List<String> = emptyList()
)

data class GetNotificationHistoryRequestProto(
    val userId: String = "",
    val limit: Int = 0
)

data class GetNotificationHistoryResponseProto(
    val notifications: List<ServerNotificationProto> = emptyList()
)

data class MarkNotificationReadRequestProto(
    val userId: String = "",
    val notificationIds: List<String> = emptyList()
)

data class MarkNotificationReadResponseProto(
    val success: Boolean = false
)

data class GetUnreadCountRequestProto(
    val userId: String = ""
)

data class GetUnreadCountResponseProto(
    val count: Int = 0
)

// ======= Remote Agent streaming (hermes_remote.proto) =======

// AgentMessage — от агента к оркестратору
data class AgentMessageProto(
    val agentId: String = "",
    val type: Int = 0,  // AgentMessageType enum
    val payload: ByteArray = byteArrayOf(),
    val timestampMs: Long = 0
)

// OrchestratorMessage — от оркестратору к агенту
data class OrchestratorMessageProto(
    val targetAgentId: String = "",
    val type: Int = 0,  // OrchestratorMessageType enum
    val payload: ByteArray = byteArrayOf(),
    val timestampMs: Long = 0
)

// RegistrationInfo — данные при регистрации агента
data class RegistrationInfoProto(
    val agentId: String = "",
    val agentName: String = "",
    val version: String = "",
    val host: String = "",
    val ipAddress: String = "",
    val os: String = "",
    val capabilities: List<String> = emptyList(),
    val authToken: String = ""
)

// Task — задача для удалённого агента
data class TaskProto(
    val taskId: String = "",
    val taskType: Int = 0,  // TaskType enum
    val params: Map<String, String> = emptyMap(),
    val workingDir: String = "",
    val timeoutSec: Int = 0,
    val streamOutput: Boolean = false
)

// TaskResult — результат выполнения задачи
data class TaskResultProto(
    val taskId: String = "",
    val status: Int = 0,  // TaskStatus enum
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = 0,
    val durationMs: Long = 0
)

// AgentMessageType enum values
object AgentMessageType {
    const val AGENT_MESSAGE_UNKNOWN = 0
    const val AGENT_REGISTER = 1
    const val AGENT_HEARTBEAT = 2
    const val AGENT_TASK_RESULT = 3
    const val AGENT_LOG = 4
    const val AGENT_DISCONNECT = 5
    const val AGENT_ERROR = 6
}

// OrchestratorMessageType enum values
object OrchestratorMessageType {
    const val ORCHESTRATOR_MESSAGE_UNKNOWN = 0
    const val ORCHESTRATOR_TASK = 1
    const val ORCHESTRATOR_CONFIG_UPDATE = 2
    const val ORCHESTRATOR_PING = 3
    const val ORCHESTRATOR_DISCONNECT = 4
    const val ORCHESTRATOR_BROADCAST = 5
}

// TaskType enum values
object TaskType {
    const val TASK_UNKNOWN = 0
    const val TASK_SHELL = 1
    const val TASK_FILE_READ = 2
    const val TASK_FILE_WRITE = 3
    const val TASK_GIT = 4
    const val TASK_BUILD = 5
    const val TASK_DEPLOY = 6
    const val TASK_DOCKER = 7
    const val TASK_CUSTOM = 8
    const val TASK_AI = 9
}

// TaskStatus enum values
object TaskStatus {
    const val TASK_STATUS_UNKNOWN = 0
    const val TASK_SUCCESS = 1
    const val TASK_ERROR = 2
    const val TASK_TIMEOUT = 3
    const val TASK_CANCELLED = 4
}

// ===== Agent Process Management (server-side) =====

data class StartAgentRequestProto(
    val agentId: String = "",
    val agentName: String = "",
    val token: String = "",
    val serverAddress: String = "",
    val capabilities: List<String> = emptyList(),
    val adminUserId: String = ""
)

data class StartAgentResponseProto(
    val success: Boolean = false,
    val error: String = "",
    val pid: Int = 0
)

data class StopAgentRequestProto(
    val agentId: String = "",
    val adminUserId: String = ""
)

data class StopAgentResponseProto(
    val success: Boolean = false,
    val error: String = ""
)

data class GetAgentProcessStatusRequestProto(
    val agentId: String = "",
    val adminUserId: String = ""
)

data class GetAgentProcessStatusResponseProto(
    val running: Boolean = false,
    val pid: Int = 0,
    val agentId: String = "",
    val startedAt: String = "",
    val error: String = ""
)

// ======= Auth V2 Proto Classes =======

data class SignInRequestV2Proto(
    val username: String = "",
    val password: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val deviceType: String = "android",
    val clientVersion: String = ""
)

data class SignUpRequestV2Proto(
    val username: String = "",
    val password: String = "",
    val email: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val deviceType: String = "android",
    val clientVersion: String = ""
)

data class AuthResponseV2Proto(
    val success: Boolean = false,
    val message: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val accessExpiresAt: Long = 0L,
    val refreshExpiresAt: Long = 0L,
    val userId: String = "",
    val username: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val bio: String = "",
    val status: String = ""
)

data class RefreshTokenRequestProto(
    val refreshToken: String = ""
)

data class RefreshTokenResponseProto(
    val accessToken: String = "",
    val refreshToken: String = "",
    val accessExpiresAt: Long = 0L,
    val refreshExpiresAt: Long = 0L
)

data class SignOutRequestProto(
    val refreshToken: String = "",
    val allDevices: Boolean = false
)

data class SimpleAuthResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class RevokeDeviceRequestProto(
    val deviceId: String = ""
)

// ======= ProfileService V2 Messages =======

data class GetProfileRequestProto(
    val placeholder: Boolean = false // empty message, user_id from JWT
)

data class GetProfileResponseProto(
    val userId: String = "",
    val username: String = "",
    val email: String = "",
    val avatarUrl: String = "",
    val fullAvatarUrl: String = "",
    val bio: String = "",
    val status: String = "",
    val locale: String = "en",
    val isSuperAdmin: Boolean = false,
    val createdAt: String = "",
    val lastSeenAt: String = "",
    val companyId: String = "",
    val companyName: String = "",
    val positionTitle: String = "",
    val positionLevel: Int = 0
)

data class UpdateProfileV2RequestProto(
    val username: String = "",
    val bio: String = "",
    val status: String = "",
    val locale: String = ""
)

data class UpdateProfileV2ResponseProto(
    val success: Boolean = false,
    val message: String = "",
    val profile: GetProfileResponseProto? = null
)

data class UpdateAvatarV2RequestProto(
    val avatarUrl: String = "",
    val fullAvatarUrl: String = ""
)

data class UpdateAvatarV2ResponseProto(
    val success: Boolean = false,
    val message: String = "",
    val avatarUrl: String = "",
    val fullAvatarUrl: String = ""
)

data class DeleteProfileV2RequestProto(
    val password: String = ""
)

data class DeleteProfileV2ResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class GetUserSettingsRequestProto(
    val placeholder: Boolean = false // empty message, user_id from JWT
)

data class GetUserSettingsResponseProto(
    val locale: String = "en",
    val themeId: String = "",
    val pushEnabled: Boolean = true,
    val custom: Map<String, String> = emptyMap()
)

data class UpdateUserSettingsRequestProto(
    val locale: String = "",
    val themeId: String = "",
    val pushEnabled: Boolean? = null,
    val custom: Map<String, String> = emptyMap()
)

data class UpdateUserSettingsResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

// ======= ChatList v2: PinChat / UnPinChat =======

data class PinChatRequestProto(
    val userId: String = "",
    val chatId: String = ""
)

data class PinChatResponseProto(
    val success: Boolean = false
)

data class UnPinChatRequestProto(
    val userId: String = "",
    val chatId: String = ""
)

data class UnPinChatResponseProto(
    val success: Boolean = false
)

// ======= Pin Message =======

data class PinMessageRequestProto(
    val userId: String = "",
    val chatId: String = "",
    val messageId: String = ""
)

data class PinMessageResponseProto(
    val success: Boolean = false
)

data class UnPinMessageRequestProto(
    val userId: String = "",
    val chatId: String = "",
    val messageId: String = ""
)

data class UnPinMessageResponseProto(
    val success: Boolean = false
)

data class GetPinnedMessagesRequestProto(
    val userId: String = "",
    val chatId: String = "",
    val limit: Int = 0,
    val offset: Int = 0
)

data class GetPinnedMessagesResponseProto(
    val messages: List<MessageProto> = emptyList()
)

// ======= ChatList v2: SearchChats =======

data class SearchChatsRequestProto(
    val userId: String = "",
    val query: String = "",
    val limit: Int = 20,
    val offset: Int = 0
)

data class SearchChatsResponseProto(
    val chats: List<ChatInfoProto> = emptyList()
)

// ======= ChatList v2: ArchiveChat / UnarchiveChat =======

data class ArchiveChatRequestProto(
    val userId: String = "",
    val chatId: String = ""
)

data class ArchiveChatResponseProto(
    val success: Boolean = false
)

data class UnarchiveChatRequestProto(
    val userId: String = "",
    val chatId: String = ""
)

data class UnarchiveChatResponseProto(
    val success: Boolean = false
)

// ======= Admin User List =======

data class AdminUserInfoProto(
    val userId: String = "",
    val username: String = "",
    val avatarUrl: String = "",
    val fullAvatarUrl: String = "",
    val email: String = "",
    val isSuperAdmin: Boolean = false,
    val lastClientVersion: String = "",
    val lastSeenAt: Timestamp? = null,
    val isOnline: Boolean = false,
    val lastMessageText: String = "",
    val lastMessageTime: Timestamp? = null,
    val lastMessageUsername: String = "",
    val chatCount: Int = 0
)

data class GetAdminUserListRequestProto(
    val query: String = "",
    val cursor: String = "",
    val limit: Int = 50,
    val sortBy: String = "last_message"
)

data class GetAdminUserListResponseProto(
    val users: List<AdminUserInfoProto> = emptyList(),
    val nextCursor: String = "",
    val hasMore: Boolean = false,
    val serverTime: Timestamp? = null
)

// ======= Admin User Sessions =======

data class AdminUserSessionProto(
    val deviceId: String = "",
    val deviceName: String = "",
    val deviceType: String = "",
    val clientVersion: String = "",
    val ipAddress: String = "",
    val lastSeenAt: Timestamp? = null,
    val isOnline: Boolean = false
)

data class GetAdminUserSessionsRequestProto(
    val userId: String = ""
)

data class GetAdminUserSessionsResponseProto(
    val sessions: List<AdminUserSessionProto> = emptyList()
)
