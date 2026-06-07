package lavender.client.android.ui.chat.widget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.hdodenhof.circleimageview.CircleImageView
import lavender.client.android.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * Unified chat message adapter.
 * Supports: user messages, agent/participant messages, typing indicators, date separators.
 * Used by both NewChatActivity (group chat) and HermesChatActivity (agents as participants).
 */
class ChatMessageAdapter(
    private val currentUserId: String = "",
    private val showAvatars: Boolean = true,
    private val showNames: Boolean = true
) : ListAdapter<ChatMessageItem, RecyclerView.ViewHolder>(ChatMessageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_AGENT = 1
        private const val VIEW_TYPE_TYPING = 2
        private const val VIEW_TYPE_DATE = 3
    }

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("d MMMM", Locale.forLanguageTag("ru"))

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return when {
            item.isDateSeparator -> VIEW_TYPE_DATE
            item.isTyping -> VIEW_TYPE_TYPING
            item.isCurrentUser -> VIEW_TYPE_USER
            else -> VIEW_TYPE_AGENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DATE -> {
                val view = inflater.inflate(R.layout.item_chat_message, parent, false)
                DateSeparatorHolder(view)
            }
            VIEW_TYPE_TYPING -> {
                val view = inflater.inflate(R.layout.item_chat_message, parent, false)
                TypingHolder(view)
            }
            VIEW_TYPE_USER -> {
                val view = inflater.inflate(R.layout.item_chat_message, parent, false)
                UserMessageHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_chat_message, parent, false)
                AgentMessageHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is DateSeparatorHolder -> holder.bind(item)
            is TypingHolder -> holder.bind(item)
            is UserMessageHolder -> holder.bind(item)
            is AgentMessageHolder -> holder.bind(item)
        }
    }

    // ===== Holders =====

    inner class DateSeparatorHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.dateText)

        fun bind(item: ChatMessageItem) {
            dateText.text = dateFormat.format(Date(item.timestamp))
            dateText.isVisible = true
            // Hide all other containers
            itemView.findViewById<LinearLayout>(R.id.userMessageContainer)?.isVisible = false
            itemView.findViewById<LinearLayout>(R.id.agentMessageContainer)?.isVisible = false
            itemView.findViewById<LinearLayout>(R.id.typingContainer)?.isVisible = false
        }
    }

    inner class TypingHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val typingContainer: LinearLayout = itemView.findViewById(R.id.typingContainer)
        private val typingText: TextView = itemView.findViewById(R.id.typingText)

        fun bind(item: ChatMessageItem) {
            typingContainer.isVisible = true
            itemView.findViewById<LinearLayout>(R.id.userMessageContainer)?.isVisible = false
            itemView.findViewById<LinearLayout>(R.id.agentMessageContainer)?.isVisible = false
            itemView.findViewById<TextView>(R.id.dateText)?.isVisible = false

            val name = item.senderName.ifEmpty { "Агент" }
            typingText.text = "$name печатает..."
        }
    }

    inner class UserMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout = itemView.findViewById(R.id.userMessageContainer)
        private val messageText: TextView = itemView.findViewById(R.id.userMessageText)
        private val messageTime: TextView = itemView.findViewById(R.id.userMessageTime)
        private val readStatus: ImageView = itemView.findViewById(R.id.userReadStatus)
        private val replyContainer: LinearLayout = itemView.findViewById(R.id.userReplyContainer)
        private val replyUser: TextView = itemView.findViewById(R.id.userReplyUser)
        private val replyText: TextView = itemView.findViewById(R.id.userReplyText)

        fun bind(item: ChatMessageItem) {
            container.isVisible = true
            itemView.findViewById<LinearLayout>(R.id.agentMessageContainer)?.isVisible = false
            itemView.findViewById<LinearLayout>(R.id.typingContainer)?.isVisible = false
            itemView.findViewById<TextView>(R.id.dateText)?.isVisible = false

            messageText.text = item.content
            messageTime.text = timeFormat.format(Date(item.timestamp))

            // Reply
            if (item.replyToUser.isNotEmpty()) {
                replyContainer.isVisible = true
                replyUser.text = item.replyToUser
                replyText.text = item.replyToText
            } else {
                replyContainer.isVisible = false
            }

            // Read status
            readStatus.isVisible = item.isRead
        }
    }

    inner class AgentMessageHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val container: LinearLayout = itemView.findViewById(R.id.agentMessageContainer)
        private val avatar: CircleImageView = itemView.findViewById(R.id.agentAvatar)
        private val emoji: TextView = itemView.findViewById(R.id.agentEmoji)
        private val name: TextView = itemView.findViewById(R.id.agentName)
        private val messageText: TextView = itemView.findViewById(R.id.agentMessageText)
        private val messageTime: TextView = itemView.findViewById(R.id.agentMessageTime)
        private val replyContainer: LinearLayout = itemView.findViewById(R.id.agentReplyContainer)
        private val replyUser: TextView = itemView.findViewById(R.id.agentReplyUser)
        private val replyText: TextView = itemView.findViewById(R.id.agentReplyText)

        fun bind(item: ChatMessageItem) {
            container.isVisible = true
            itemView.findViewById<LinearLayout>(R.id.userMessageContainer)?.isVisible = false
            itemView.findViewById<LinearLayout>(R.id.typingContainer)?.isVisible = false
            itemView.findViewById<TextView>(R.id.dateText)?.isVisible = false

            messageText.text = item.content
            messageTime.text = timeFormat.format(Date(item.timestamp))

            // Sender name
            if (showNames && item.senderName.isNotEmpty()) {
                name.isVisible = true
                name.text = item.senderName
            } else {
                name.isVisible = false
            }

            // Avatar or emoji
            if (item.senderAvatarUrl.isNotEmpty()) {
                avatar.isVisible = showAvatars
                emoji.isVisible = false
                // TODO: Load avatar with Glide
            } else if (item.senderEmoji.isNotEmpty()) {
                avatar.isVisible = false
                emoji.isVisible = true
                emoji.text = item.senderEmoji
            } else {
                avatar.isVisible = false
                emoji.isVisible = true
                emoji.text = "🤖"
            }

            // Reply
            if (item.replyToUser.isNotEmpty()) {
                replyContainer.isVisible = true
                replyUser.text = item.replyToUser
                replyText.text = item.replyToText
            } else {
                replyContainer.isVisible = false
            }
        }
    }

    // ===== Search highlight =====

    private var highlightPosition: Int = -1

    fun highlightPosition(position: Int) {
        val old = highlightPosition
        highlightPosition = position
        if (old >= 0) notifyItemChanged(old)
        if (position >= 0) notifyItemChanged(position)
    }
}

/**
 * Unified chat message data class.
 * Used for both regular chat messages and Hermes agent messages.
 */
data class ChatMessageItem(
    val id: String = UUID.randomUUID().toString(),
    val content: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderEmoji: String = "",       // For agents: emoji icon
    val senderAvatarUrl: String = "",   // For users: avatar URL
    val timestamp: Long = System.currentTimeMillis(),
    val isCurrentUser: Boolean = false,
    val isRead: Boolean = false,
    val isTyping: Boolean = false,
    val isDateSeparator: Boolean = false,
    val replyToUser: String = "",
    val replyToText: String = "",
    val replyToMessageId: String = ""
)

class ChatMessageDiffCallback : DiffUtil.ItemCallback<ChatMessageItem>() {
    override fun areItemsTheSame(oldItem: ChatMessageItem, newItem: ChatMessageItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ChatMessageItem, newItem: ChatMessageItem): Boolean {
        return oldItem == newItem
    }
}
