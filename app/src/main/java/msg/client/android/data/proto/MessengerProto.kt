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
    val reactions: List<ReactionProto> = emptyList()
) {
    class Builder {
        private var user: String = ""
        private var text: String = ""
        private var createdAt: Timestamp? = null
        private var id: String = ""
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

        fun addReaction(reaction: ReactionProto): Builder {
            this.reactions.add(reaction)
            return this
        }
        
        fun build(): MessageProto {
            return MessageProto(id, user, text, createdAt, reactions)
        }
    }
    
    companion object {
        fun newBuilder(): Builder = Builder()
    }
}

// Reaction Request/Response
data class ReactionRequestProto(
    val messageId: String = "",
    val reaction: ReactionProto = ReactionProto()
)

data class ReactionResponseProto(
    val success: Boolean = false
)

// Other message types
data class ClientListRequestProto(
    val dummy: Boolean = false // Empty message
)

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
