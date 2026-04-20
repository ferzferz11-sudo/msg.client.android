package lavender.client.android.ui.adapter

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.models.ChatInfo

class ChatAdapter(
    private val onChatClick: (ChatInfo) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private var chats = listOf<ChatInfo>()

    fun setChats(newChats: List<ChatInfo>) {
        val diffCallback = ChatDiffCallback(chats, newChats)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        chats = newChats
        diffResult.dispatchUpdatesTo(this)
    }

    private class ChatDiffCallback(
        private val oldList: List<ChatInfo>,
        private val newList: List<ChatInfo>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldChat = oldList[oldItemPosition]
            val newChat = newList[newItemPosition]
            return oldChat.name == newChat.name &&
                    oldChat.type == newChat.type &&
                    oldChat.unreadCount == newChat.unreadCount
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view, onChatClick)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chats[position])
    }

    override fun getItemCount(): Int = chats.size

    class ChatViewHolder(
        itemView: View,
        private val onChatClick: (ChatInfo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val chatName: TextView = itemView.findViewById(R.id.chatName)
        private val chatType: TextView = itemView.findViewById(R.id.chatType)
        private val unreadCount: TextView = itemView.findViewById(R.id.unreadCount)

        fun bind(chat: ChatInfo) {
            chatName.text = chat.name

            val context = itemView.context
            val config = context.resources.configuration
            val isRussian = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                config.locales[0].language == "ru"
            } else {
                @Suppress("DEPRECATION")
                config.locale.language == "ru"
            }
            chatType.text = when (chat.type) {
                "general" -> if (isRussian) "Общий чат" else "General Chat"
                "direct" -> if (isRussian) "Личное сообщение" else "Direct Message"
                else -> chat.type
            }

            if (chat.unreadCount > 0) {
                unreadCount.text = chat.unreadCount.toString()
                unreadCount.visibility = View.VISIBLE
            } else {
                unreadCount.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onChatClick(chat)
            }
        }
    }
}
