package lavender.client.android.ui.adapter

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import lavender.client.android.R
import lavender.client.android.data.models.ChatInfo
import org.json.JSONArray

class ChatAdapter(
    private val onChatClick: (ChatInfo) -> Unit,
    private val currentUsername: String = "",
    initialAvatarCache: Map<String, String> = emptyMap()
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private var chats = listOf<ChatInfo>()
    var avatarCache: Map<String, String> = initialAvatarCache

    fun setChats(newChats: List<ChatInfo>) {
        val diffCallback = ChatDiffCallback(chats, newChats)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        chats = newChats
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateAvatarCache(newCache: Map<String, String>) {
        avatarCache = newCache
        notifyDataSetChanged()
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
        return ChatViewHolder(view, onChatClick, currentUsername, avatarCache)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(chats[position], currentUsername, avatarCache)
    }

    override fun getItemCount(): Int = chats.size

    class ChatViewHolder(
        itemView: View,
        private val onChatClick: (ChatInfo) -> Unit,
        private val currentUsername: String = "",
        private val avatarCache: Map<String, String> = emptyMap()
    ) : RecyclerView.ViewHolder(itemView) {

        private val chatName: TextView = itemView.findViewById(R.id.chatName)
        private val chatType: TextView = itemView.findViewById(R.id.chatType)
        private val unreadCount: TextView = itemView.findViewById(R.id.unreadCount)
        private val participantAvatars: LinearLayout = itemView.findViewById(R.id.participantAvatars)

        fun bind(chat: ChatInfo, currentUsername: String, avatarCache: Map<String, String>) {
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

            // Load participant avatars
            loadParticipantAvatars(chat.participants, currentUsername, avatarCache)

            itemView.setOnClickListener {
                onChatClick(chat)
            }
        }

        private fun loadParticipantAvatars(participantsJson: String, currentUsername: String, avatarCache: Map<String, String>) {
            participantAvatars.removeAllViews()

            if (participantsJson.isEmpty()) return

            try {
                val participants = JSONArray(participantsJson)
                val maxAvatars = 3 // Show max 3 avatars
                val avatarSize = 96 // Avatar size in dp

                // Show participant avatars for both direct and group chats
                for (i in 0 until minOf(participants.length(), maxAvatars)) {
                    val username = participants.getString(i)

                    // Create avatar ImageView
                    val avatar = ImageView(itemView.context).apply {
                        layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                            if (i > 0) {
                                setMargins(-8, 0, 0, 0) // Overlap avatars
                            }
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }

                    // Check if avatar is in cache
                    val cachedAvatarUrl = avatarCache[username]

                    // Load avatar with placeholder
                    if (!cachedAvatarUrl.isNullOrBlank()) {
                        Glide.with(itemView.context)
                            .load(cachedAvatarUrl)
                            .placeholder(R.drawable.ic_default_avatar)
                            .error(R.drawable.ic_default_avatar)
                            .circleCrop()
                            .into(avatar)
                    } else {
                        // Show default avatar if not in cache
                        avatar.setImageResource(R.drawable.ic_default_avatar)
                    }

                    participantAvatars.addView(avatar)
                }

                // If there are more participants, show count indicator
                if (participants.length() > maxAvatars) {
                    val remainingCount = participants.length() - maxAvatars
                    val countView = TextView(itemView.context).apply {
                        layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                            setMargins(-8, 0, 0, 0)
                        }
                        text = "+$remainingCount"
                        textSize = 10f
                        gravity = android.view.Gravity.CENTER
                        setTextColor(android.graphics.Color.WHITE)
                        setBackgroundResource(R.drawable.unread_count_background)
                    }
                    participantAvatars.addView(countView)
                }
            } catch (e: Exception) {
                // If JSON parsing fails, just hide avatars
            }
        }
    }
}
