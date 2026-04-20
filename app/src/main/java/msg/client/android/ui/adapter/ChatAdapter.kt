package msg.client.android.ui.adapter

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import msg.client.android.R
import msg.client.android.data.models.ChatInfo

class ChatAdapter(
    private val onChatClick: (ChatInfo) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private var chats = listOf<ChatInfo>()

    fun setChats(newChats: List<ChatInfo>) {
        chats = newChats
        notifyDataSetChanged()
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

            itemView.setOnClickListener {
                onChatClick(chat)
            }
        }
    }
}
