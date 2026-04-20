package msg.client.android.data.proto

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
    val roomId: String = ""
) {
    class Builder {
        private var user: String = ""
        private var text: String = ""
        private var createdAt: Timestamp? = null
        private var id: String = ""
        private var password: String = ""
        private var repliedToMessageId: String = ""
        private var repliedToUser: String = ""
        private var repliedToText: String = ""
        private var roomId: String = ""
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

        @Suppress("unused")
        fun addReaction(reaction: ReactionProto): Builder {
            this.reactions.add(reaction)
            return this
        }

        fun build(): MessageProto {
            return MessageProto(id, user, text, createdAt, reactions, password, repliedToMessageId, repliedToUser, repliedToText, roomId)
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
    val createdAt: Timestamp? = null
)

// Get Chats Request/Response
data class GetChatsRequestProto(
    val username: String = ""
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
    val messages: List<MessageProto> = emptyList()
)

data class DeleteMessagesResponseProto(
    val success: Boolean = false
)

data class TokenRequestProto(
    val user: String = "",
    val token: String = ""
)

data class TokenResponseProto(
    val success: Boolean = false
)
