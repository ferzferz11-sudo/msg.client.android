package lavender.client.android.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import lavender.client.android.R
import lavender.client.android.data.models.ChatInfo
import org.json.JSONArray

class ChatAdapter(
    private val onChatClick: (ChatInfo) -> Unit,
    private val onSelectionChanged: (Int) -> Unit = {},
    private val currentUsername: String = "",
    initialAvatarCache: Map<String, String> = emptyMap(),
    private var onlineUsers: List<String> = emptyList()
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private var chats = listOf<ChatInfo>()
    var avatarCache: Map<String, String> = initialAvatarCache
    private val selectedPositions = mutableSetOf<Int>()

    fun setOnlineUsers(users: List<String>) {
        onlineUsers = users
        notifyDataSetChanged()
    }

    fun getSelectedChats(): List<ChatInfo> {
        return selectedPositions.map { chats[it] }
    }

    fun clearSelection() {
        val previousSelected = selectedPositions.toSet()
        selectedPositions.clear()
        previousSelected.forEach { notifyItemChanged(it) }
        onSelectionChanged(0)
    }

    fun getChats(): List<ChatInfo> {
        return chats
    }

    fun setChats(newChats: List<ChatInfo>) {
        val diffResult = DiffUtil.calculateDiff(ChatDiffCallback(chats, newChats))
        chats = newChats
        diffResult.dispatchUpdatesTo(this)
    }

    fun updateAvatarCache(newCache: Map<String, String>) {
        avatarCache = newCache
        chats.indices.forEach { notifyItemChanged(it) }
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
                    oldChat.unreadCount == newChat.unreadCount &&
                    oldChat.createdAt == newChat.createdAt
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view, onChatClick)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val isSelected = selectedPositions.contains(position)
        holder.bind(chats[position], currentUsername, avatarCache, isSelected, onlineUsers) {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos == RecyclerView.NO_POSITION) return@bind

            if (selectedPositions.contains(currentPos)) {
                selectedPositions.remove(currentPos)
            } else {
                selectedPositions.add(currentPos)
            }
            notifyItemChanged(currentPos)
            onSelectionChanged(selectedPositions.size)
        }
    }

    override fun getItemCount(): Int = chats.size

    class ChatViewHolder(
        itemView: View,
        private val onChatClick: (ChatInfo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val chatName: TextView = itemView.findViewById(R.id.chatName)
        private val chatType: TextView = itemView.findViewById(R.id.chatType)
        private val unreadCount: TextView = itemView.findViewById(R.id.unreadCount)
        private val participantAvatars: LinearLayout = itemView.findViewById(R.id.participantAvatars)
        private val cardView: com.google.android.material.card.MaterialCardView = itemView as com.google.android.material.card.MaterialCardView

        fun bind(chat: ChatInfo, currentUsername: String, avatarCache: Map<String, String>, isSelected: Boolean, onlineUsers: List<String>, onLongClick: () -> Unit) {
            chatName.text = chat.name

            val context = itemView.context

            if (isSelected) {
                cardView.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.lavender_mist_alpha))
                itemView.alpha = 0.7f
            } else {
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
                val color = if (typedValue.resourceId != 0) {
                    androidx.core.content.ContextCompat.getColor(context, typedValue.resourceId)
                } else {
                    typedValue.data
                }
                cardView.setCardBackgroundColor(color)
                itemView.alpha = 1.0f
            }
            val isRussian = context.resources.configuration.locales[0].language == "ru"
            
            if (chat.type == "direct") {
                val participantsArray = JSONArray(chat.participants)
                val otherPerson = (0 until participantsArray.length())
                    .map { participantsArray.getString(it) }
                    .find { it != currentUsername } ?: chat.name
                chatName.text = otherPerson
                chatType.text = if (isRussian) "Личное сообщение" else "Direct Message"
            } else {
                chatName.text = chat.name
                chatType.text = when (chat.type) {
                    "general" -> if (isRussian) "Общий чат" else "General Chat"
                    "group" -> if (isRussian) "Группа" else "Group"
                    else -> chat.type
                }
            }

            unreadCount.isVisible = chat.unreadCount > 0
            if (chat.unreadCount > 0) {
                unreadCount.text = chat.unreadCount.toString()
            }

            // Load participant avatars
            loadParticipantAvatars(chat.participants, chat.type, currentUsername, avatarCache, onlineUsers)

            itemView.setOnClickListener {
                onChatClick(chat)
            }
            itemView.setOnLongClickListener {
                onLongClick()
                true
            }
        }

        private fun loadParticipantAvatars(participantsJson: String, chatType: String, currentUsername: String, avatarCache: Map<String, String>, onlineUsers: List<String>) {
            participantAvatars.removeAllViews()

            if (participantsJson.isEmpty()) return

            try {
                val participantsArray = JSONArray(participantsJson)
                val participantsList = mutableListOf<String>()
                for (i in 0 until participantsArray.length()) {
                    participantsList.add(participantsArray.getString(i))
                }

                val context = itemView.context
                
                if (chatType == "direct") {
                    // Larger avatar of the OTHER person
                    val otherPerson = participantsList.find { it != currentUsername } ?: currentUsername
                    val isOnline = onlineUsers.contains(otherPerson)
                    
                    val avatarSize = 52.dpToPx()
                    
                    val container = FrameLayout(context).apply {
                        layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
                    }

                    val avatar = ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(avatarSize, avatarSize)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }

                    val cachedAvatarUrl = avatarCache[otherPerson]
                    if (!cachedAvatarUrl.isNullOrBlank()) {
                        Glide.with(context)
                            .load(cachedAvatarUrl)
                            .placeholder(R.drawable.ic_default_avatar)
                            .error(R.drawable.ic_default_avatar)
                            .circleCrop()
                            .into(avatar)
                    } else {
                        avatar.setImageResource(R.drawable.ic_default_avatar)
                    }
                    container.addView(avatar)

                    // Online status dot
                    if (isOnline) {
                        val dotSize = 14.dpToPx()
                        val dot = View(context).apply {
                            layoutParams = FrameLayout.LayoutParams(dotSize, dotSize).apply {
                                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                                setMargins(0, 0, 2.dpToPx(), 2.dpToPx())
                            }
                            background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.status_online_dot)
                        }
                        container.addView(dot)
                    }
                    
                    participantAvatars.addView(container)
                } else {
                    // Group chat: small overlapping avatars on the left
                    val maxAvatars = 3
                    val avatarSize = 40.dpToPx()
                    val dotSize = 10.dpToPx()

                    for (i in 0 until minOf(participantsList.size, maxAvatars)) {
                        val username = participantsList[i]
                        val isOnline = onlineUsers.contains(username)

                        val container = FrameLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                                if (i > 0) setMargins(-15.dpToPx(), 0, 0, 0)
                            }
                        }

                        val avatar = ImageView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(avatarSize, avatarSize)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }

                        val cachedAvatarUrl = avatarCache[username]
                        if (!cachedAvatarUrl.isNullOrBlank()) {
                            Glide.with(context)
                                .load(cachedAvatarUrl)
                                .placeholder(R.drawable.ic_default_avatar)
                                .error(R.drawable.ic_default_avatar)
                                .circleCrop()
                                .into(avatar)
                        } else {
                            avatar.setImageResource(R.drawable.ic_default_avatar)
                        }
                        container.addView(avatar)

                        // Online status dot for group participants
                        if (isOnline) {
                            val dot = View(context).apply {
                                layoutParams = FrameLayout.LayoutParams(dotSize, dotSize).apply {
                                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                                    setMargins(0, 0, 1.dpToPx(), 1.dpToPx())
                                }
                                background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.status_online_dot)
                            }
                            container.addView(dot)
                        }

                        participantAvatars.addView(container)
                    }

                    if (participantsList.size > maxAvatars) {
                        val remainingCount = participantsList.size - maxAvatars
                        val countView = TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                                setMargins(-15.dpToPx(), 0, 0, 0)
                            }
                            text = "+$remainingCount"
                            textSize = 11f
                            gravity = android.view.Gravity.CENTER
                            setTextColor(android.graphics.Color.WHITE)
                            setBackgroundResource(R.drawable.unread_count_background)
                        }
                        participantAvatars.addView(countView)
                    }
                }
            } catch (_: Exception) {}
        }

        private fun Int.dpToPx(): Int = (this * itemView.resources.displayMetrics.density).toInt()
    }
}
