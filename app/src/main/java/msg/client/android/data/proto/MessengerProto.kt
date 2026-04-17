package msg.client.android.data.proto

import com.google.protobuf.Timestamp

// Message class matching messenger.proto
data class MessageProto(
    val user: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null
) {
    class Builder {
        private var user: String = ""
        private var text: String = ""
        private var createdAt: Timestamp? = null
        
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
        
        fun build(): MessageProto {
            return MessageProto(user, text, createdAt)
        }
    }
    
    companion object {
        fun newBuilder(): Builder = Builder()
    }
}

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
