package lavender.client.android.data.proto

import com.google.protobuf.Timestamp

// Simplified protobuf message for chat
data class ChatMessageProto(
    val user: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null
) {
    fun toByteArray(): ByteArray {
        // Simplified serialization - in real implementation this would be protobuf
        val data = "$user|$text|${createdAt?.seconds ?: 0}"
        return data.toByteArray()
    }
    
    companion object {
        fun fromByteArray(data: ByteArray): ChatMessageProto {
            val str = String(data)
            val parts = str.split("|")
            return ChatMessageProto(
                user = parts.getOrNull(0) ?: "",
                text = parts.getOrNull(1) ?: "",
                createdAt = parts.getOrNull(2)?.toLongOrNull()?.let { 
                    Timestamp.newBuilder().setSeconds(it).build() 
                }
            )
        }
        
        fun newBuilder(): Builder = Builder()
    }
    
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
        
        fun build(): ChatMessageProto {
            return ChatMessageProto(user, text, createdAt)
        }
    }
}
