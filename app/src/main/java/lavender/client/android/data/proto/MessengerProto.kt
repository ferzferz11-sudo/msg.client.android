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
    val e2EePayload: String = ""
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
            return MessageProto(id, user, text, createdAt, reactions, password, repliedToMessageId, repliedToUser, repliedToText, roomId, isRead, avatarUrl, imageUrl, imageUrls, edited, clientVersion, isSuperAdmin, voiceUrl, duration, register, deviceId, deviceName, userId, isE2Ee, e2EePayload)
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
    val agentMode: String = ""        // For hermes sessions: single/parallel/pipeline
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
    val userId: String = ""
)

data class GetChatsResponseProto(
    val chats: List<ChatInfoProto> = emptyList()
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

// Reaction Request/Response
data class ReactionRequestProto(
    val messageId: String = "",
    val reaction: ReactionProto = ReactionProto()
)

data class ReactionResponseProto(
    val success: Boolean = false
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

data class GetHistoryRequestProto(
    val limit: Int = 50,
    val room: String = ""
)

data class GetHistoryResponseProto(
    val messages: List<MessageProto> = emptyList()
)

data class DeleteMessagesRequestProto(
    val messages: List<MessageProto> = emptyList(),
    val requesterUsername: String = ""
)

data class DeleteMessagesResponseProto(
    val success: Boolean = false
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

data class EditMessageRequestProto(
    val messageId: String = "",
    val text: String = ""
)

data class EditMessageResponseProto(
    val success: Boolean = false,
    val message: String = ""
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
    val isTyping: Boolean = false
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

data class GetAllUsersRequestProto(
    val dummy: Boolean = false
)

data class UserInfoProto(
    val username: String = "",
    val avatarUrl: String = "",
    val lastClientVersion: String = "",
    val lastSeenAt: Timestamp? = null,
    val email: String = ""
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

data class AddFavoriteRequestProto(
    val userId: String = "",
    val messageId: String = ""
)

data class AddFavoriteResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class RemoveFavoriteRequestProto(
    val userId: String = "",
    val messageId: String = ""
)

data class RemoveFavoriteResponseProto(
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

data class GetFavoritesRequestProto(
    val userId: String = ""
)

data class GetFavoritesResponseProto(
    val messages: List<MessageProto> = emptyList()
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

// ======= Hermes Multi-Agent Orchestrator =======

data class OrchestratorRequestProto(
    val userId: String = "",
    val sessionId: String = "",
    val message: String = "",
    val agentId: String = "",
    val mode: String = ""
)

data class OrchestratorResponseProto(
    val token: String = "",
    val finished: Boolean = false,
    val error: String = "",
    val agentId: String = "",
    val agentName: String = ""
)

data class GetOrchestratorHistoryRequestProto(
    val sessionId: String = "",
    val limit: Int = 50
)

data class OrchestratorHistoryMessageProto(
    var role: String = "",
    var content: String = "",
    var agentId: String = "",
    var agentName: String = "",
    var createdAt: String = ""
)

data class GetOrchestratorHistoryResponseProto(
    val messagesList: List<OrchestratorHistoryMessageProto> = emptyList()
)

data class ListAgentsRequestProto(
    val userId: String = ""
)

data class AgentInfoProto(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val role: String = "",
    val isPreset: Boolean = false,
    val icon: String = ""
)

data class ListAgentsResponseProto(
    val agents: List<AgentInfoProto> = emptyList()
)

data class ListAgentPresetsRequestProto(
    val dummy: Boolean = false  // empty message needs at least one field
)

data class AgentPresetInfoProto(
    val id: String = "",
    val name: String = "",
    val role: String = "",
    val description: String = "",
    val icon: String = "",
    val maxTokens: Int = 0
)

data class ListAgentPresetsResponseProto(
    val presets: List<AgentPresetInfoProto> = emptyList()
)

data class CreateAgentRequestProto(
    val userId: String = "",
    val presetId: String = "",
    val customName: String = "",
    val customPrompt: String = "",
    val model: String = "",
    val maxTokens: Int = 0
)

data class CreateAgentResponseProto(
    val agentId: String = "",
    val success: Boolean = false,
    val message: String = "",
    val agent: AgentInfoProto? = null
)

data class UpdateAgentRequestProto(
    val agentId: String = "",
    val userId: String = "",
    val name: String = "",
    val systemPrompt: String = "",
    val model: String = "",
    val maxTokens: Int = 0
)

data class UpdateAgentResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class DeleteAgentRequestProto(
    val agentId: String = "",
    val userId: String = ""
)

data class DeleteAgentResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class ListUserAgentsRequestProto(
    val userId: String = ""
)

data class ListUserAgentsResponseProto(
    val agents: List<AgentInfoProto> = emptyList()
)

data class CreateHermesSessionRequestProto(
    val userId: String = "",
    val agentId: String = "",
    val mode: String = ""
)

data class CreateHermesSessionResponseProto(
    val sessionId: String = "",
    val success: Boolean = false,
    val message: String = "",
    val name: String = ""
)

data class DeleteHermesSessionRequestProto(
    val sessionId: String = "",
    val userId: String = ""
)

data class DeleteHermesSessionResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

// ======= Remote Agent proto classes (FUTURE) =======

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

data class ListRemoteAgentsResponseProto(
    val agents: List<RemoteAgentInfoProto> = emptyList()
)

data class DeployAgentTaskRequestProto(
    val agentId: String = "",
    val taskType: String = "",
    val params: Map<String, String> = emptyMap(),
    val workingDir: String = "",
    val timeoutSec: Int = 0
)

data class DeployAgentTaskResponseProto(
    val taskId: String = "",
    val success: Boolean = false,
    val message: String = ""
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

// ======= Bot Command proto classes =======

data class BotCommandRequestProto(
    val userId: String = "",
    val username: String = "",
    val chatId: String = "",
    val command: String = "",
    val args: List<String> = emptyList()
)

data class BotCommandResponseProto(
    val success: Boolean = false,
    val responseText: String = "",
    val isError: Boolean = false,
    val errorMessage: String = ""
)

data class BotCommandInfoProto(
    val command: String = "",
    val description: String = "",
    val usage: String = "",
    val category: String = ""
)

data class GetBotCommandsRequestProto(
    val userId: String = ""
)

data class GetBotCommandsResponseProto(
    val commands: List<BotCommandInfoProto> = emptyList()
)

data class OWLStatusRequestProto(
    val userId: String = ""
)

data class OWLStatusResponseProto(
    val available: Boolean = false,
    val model: String = "",
    val queueLength: Int = 0,
    val status: String = ""
)

// ======= OWL streaming proto =======

data class OwlRequestProto(
    val userId: String = "",
    val message: String = "",
    val sessionId: String = ""
)

data class OwlResponseProto(
    val text: String = "",
    val finished: Boolean = false,
    val error: String = ""
)

// ======= Server Notification proto classes =======

data class ServerNotificationProto(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val message: String = "",
    val timestamp: String = "",
    val metadata: Map<String, String> = emptyMap()
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

// ======= OWL Settings =======

data class UpdateOwlSettingsRequestProto(
    val chatId: String = "",
    val userId: String = "",
    val apiKey: String = "",
    val model: String = ""
)

data class UpdateOwlSettingsResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class GetOwlSettingsRequestProto(
    val chatId: String = "",
    val userId: String = ""
)

data class GetOwlSettingsResponseProto(
    val apiKey: String = "",
    val model: String = ""
)

// ======= OWL Chat creation =======

data class CreateOwlChatRequestProto(
    val userId: String = "",
    val name: String = ""
)

data class CreateOwlChatResponseProto(
    val chatId: String = "",
    val name: String = "",
    val success: Boolean = false,
    val message: String = ""
)
