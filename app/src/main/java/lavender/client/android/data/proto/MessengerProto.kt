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
    val register: Boolean = false
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
            return MessageProto(id, user, text, createdAt, reactions, password, repliedToMessageId, repliedToUser, repliedToText, roomId, isRead, avatarUrl, imageUrl, imageUrls, edited, clientVersion, isSuperAdmin, voiceUrl, duration, register)
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
    val lastMessageHasImage: Boolean = false
)

// Mark Read Request/Response
data class MarkReadRequestProto(
    val roomId: String = "",
    val username: String = ""
)

data class MarkReadResponseProto(
    val success: Boolean = false
)

// Delete Chat Request/Response
data class DeleteChatRequestProto(
    val chatId: String = "",
    val requesterUsername: String = ""
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
    val user2: String = ""
)

data class CreateDirectChatResponseProto(
    val chatId: String = "",
    val success: Boolean = false
)

// Create Group Chat Request/Response
data class CreateGroupChatRequestProto(
    val name: String = "",
    val participants: List<String> = emptyList(),
    val creator: String = ""
)

data class CreateGroupChatResponseProto(
    val chatId: String = "",
    val success: Boolean = false
)

// Update Username Request/Response
data class UpdateUsernameRequestProto(
    val oldUsername: String = "",
    val newUsername: String = ""
)

data class UpdateUsernameResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

// Update Password Request/Response
data class UpdatePasswordRequestProto(
    val username: String = "",
    val oldPassword: String = "",
    val newPassword: String = ""
)

data class UpdatePasswordResponseProto(
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
    val pushEnabled: Boolean = true
)

data class TokenResponseProto(
    val success: Boolean = false
)

// Update Avatar Request/Response
data class UpdateAvatarRequestProto(
    val username: String = "",
    val avatarUrl: String = "",
    val fullAvatarUrl: String = ""
)

data class UpdateAvatarResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

// Profile updates
data class UpdateProfileRequestProto(
    val username: String = "",
    val bio: String = "",
    val status: String = ""
)

data class UpdateProfileResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class GetUserProfileRequestProto(
    val username: String = ""
)

data class GetUserProfileResponseProto(
    val username: String = "",
    val bio: String = "",
    val status: String = "",
    val avatarUrl: String = ""
)

// Participants management
data class AddParticipantRequestProto(
    val chatId: String = "",
    val username: String = ""
)

data class AddParticipantResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class RemoveParticipantRequestProto(
    val chatId: String = "",
    val username: String = ""
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
    val fullAvatarUrl: String = ""
)

data class UpdateChatAvatarResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

// Get User Avatar Request/Response
data class GetUserAvatarRequestProto(
    val username: String = ""
)

data class GetUserAvatarResponseProto(
    val avatarUrl: String = "",
    val fullAvatarUrl: String = ""
)

// Delete Profile Request/Response
data class DeleteProfileRequestProto(
    val username: String = ""
)

data class DeleteProfileResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

// Typing Signal Request/Response
data class TypingRequestProto(
    val roomId: String = "",
    val username: String = "",
    val isTyping: Boolean = false
)

data class TypingSignalProto(
    val roomId: String = "",
    val username: String = "",
    val isTyping: Boolean = false
)

// Contacts management
data class AddContactRequestProto(
    val username: String = "",
    val contactUsername: String = ""
)

data class AddContactResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class RemoveContactRequestProto(
    val username: String = "",
    val contactUsername: String = ""
)

data class RemoveContactResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class GetContactsRequestProto(
    val username: String = ""
)

data class GetContactsResponseProto(
    val contacts: List<String> = emptyList()
)

data class GetChatListVersionRequestProto(
    val username: String = ""
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
    val theme: CustomThemeProto = CustomThemeProto()
)

data class SaveThemeResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class SetCurrentThemeRequestProto(
    val username: String = "",
    val themeId: String = ""
)

data class SetCurrentThemeResponseProto(
    val success: Boolean = false
)

data class DeleteThemeRequestProto(
    val username: String = "",
    val themeId: String = ""
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
    val lastSeenAt: Timestamp? = null
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

data class RecoverPasswordRequestProto(
    val email: String = ""
)

data class RecoverPasswordResponseProto(
    val success: Boolean = false,
    val message: String = ""
)

data class GetFavoritesRequestProto(
    val userId: String = ""
)

data class GetFavoritesResponseProto(
    val messages: List<MessageProto> = emptyList()
)
