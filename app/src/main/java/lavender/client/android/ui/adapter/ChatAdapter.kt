package lavender.client.android.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import lavender.client.android.R
import lavender.client.android.data.models.ChatInfo
import org.json.JSONArray

class ChatAdapter(
    private val onChatClick: (ChatInfo) -> Unit,
    private val onSettingsClick: ((ChatInfo) -> Unit)? = null,
    private val onSelectionChanged: (Int) -> Unit = {},
    private val currentUsername: String = "",
    initialAvatarCache: Map<String, String> = emptyMap(),
    private var onlineUsers: List<String> = emptyList(),
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private var allChats = listOf<ChatInfo>()
    private var displayedChats = listOf<ChatInfo>()
    var avatarCache: Map<String, String> = initialAvatarCache
    private val selectedPositions = mutableSetOf<Int>()
    private var currentFilter: String = ""

    fun getSelectedChats(): List<ChatInfo> {
        return selectedPositions.map { displayedChats[it] }
    }

    fun clearSelection() {
        val previousSelected = selectedPositions.toSet()
        selectedPositions.clear()
        previousSelected.forEach { notifyItemChanged(it) }
        onSelectionChanged(0)
    }

    fun getChats(): List<ChatInfo> {
        return displayedChats
    }

    fun setChats(newChats: List<ChatInfo>) {
        allChats = newChats
        applyFilter()
    }

    fun filter(query: String) {
        currentFilter = query.lowercase()
        applyFilter()
    }

    private fun applyFilter() {
        val filtered = if (currentFilter.isEmpty()) {
            allChats
        } else {
            allChats.filter { chat ->
                chat.name.lowercase().contains(currentFilter) ||
                chat.participants.lowercase().contains(currentFilter) ||
                chat.lastMessageText.lowercase().contains(currentFilter)
            }
        }
        
        val diffResult = DiffUtil.calculateDiff(ChatDiffCallback(displayedChats, filtered))
        displayedChats = filtered
        diffResult.dispatchUpdatesTo(this)
    }

    fun setOnlineUsers(users: List<String>) {
        if (onlineUsers == users) return
        onlineUsers = users
        notifyDataSetChanged()
    }

    fun updateAvatarCache(newCache: Map<String, String>) {
        if (avatarCache == newCache) return
        avatarCache = newCache
        notifyDataSetChanged()
    }

    private class ChatDiffCallback(
        private val oldList: List<ChatInfo>,
        private val newList: List<ChatInfo>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = oldList[oldItemPosition].id == newList[newItemPosition].id
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldChat = oldList[oldItemPosition]
            val newChat = newList[newItemPosition]
            return (oldChat.name == newChat.name && oldChat.type == newChat.type &&
                    oldChat.unreadCount == newChat.unreadCount && oldChat.lastMessageTime == newChat.lastMessageTime)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view, onChatClick)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val isSelected = selectedPositions.contains(position)
        holder.bind(displayedChats[position], currentUsername, avatarCache, isSelected, onlineUsers) {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos == RecyclerView.NO_POSITION) return@bind
            if (selectedPositions.contains(currentPos)) selectedPositions.remove(currentPos) else selectedPositions.add(currentPos)
            notifyItemChanged(currentPos); onSelectionChanged(selectedPositions.size)
        }
    }

    override fun getItemCount(): Int = displayedChats.size

    inner class ChatViewHolder(itemView: View, private val onChatClick: (ChatInfo) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val chatName: TextView = itemView.findViewById(R.id.chatName)
        private val chatType: TextView = itemView.findViewById(R.id.chatType)
        private val unreadCount: TextView = itemView.findViewById(R.id.unreadCount)
        private val adminIndicator: ImageView = itemView.findViewById(R.id.adminIndicator)
        private val participantAvatars: LinearLayout = itemView.findViewById(R.id.participantAvatars)
        private val cardView: com.google.android.material.card.MaterialCardView = itemView as com.google.android.material.card.MaterialCardView

        fun bind(chat: ChatInfo, currentUsername: String, avatarCache: Map<String, String>, isSelected: Boolean, onlineUsers: List<String>, onLongClick: () -> Unit) {
            chatName.text = chat.name
            val context = itemView.context
            val theme = lavender.client.android.ui.ThemeManager.getCurrentTheme()
            if (isSelected) {
                cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.lavender_mist_alpha))
                itemView.alpha = 0.7f
            } else {
                if (theme != null) {
                    try { cardView.setCardBackgroundColor(theme.surfaceColor.toColorInt()) } catch (_: Exception) { applyDefaultCardBackground(context) }
                } else applyDefaultCardBackground(context)
                itemView.alpha = 1.0f
            }
            if (theme != null) {
                try { chatName.setTextColor(theme.textPrimaryColor.toColorInt()); chatType.setTextColor(theme.textSecondaryColor.toColorInt()) } catch (_: Exception) {}
            } else {
                val typedPrimary = android.util.TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedPrimary, true)
                chatName.setTextColor(if (typedPrimary.resourceId != 0) ContextCompat.getColor(context, typedPrimary.resourceId) else typedPrimary.data)
                val typedSecondary = android.util.TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedSecondary, true)
                chatType.setTextColor(if (typedSecondary.resourceId != 0) ContextCompat.getColor(context, typedSecondary.resourceId) else typedSecondary.data)
            }
            if (chat.type == "direct") {
                val participantsArray = JSONArray(chat.participants)
                val otherPerson = (0 until participantsArray.length()).asSequence()
                    .map { participantsArray.getString(it) }
                    .find { it != currentUsername } ?: chat.name
                chatName.text = otherPerson
            } else chatName.text = chat.name
            if (chat.lastMessageText.isNotEmpty()) {
                val prefix = if (chat.type == "group" || chat.type == "general") { if (chat.lastMessageUsername.isNotEmpty()) "${chat.lastMessageUsername}: " else "" } else ""
                chatType.text = context.getString(R.string.chat_last_message_format, prefix, chat.lastMessageText)
                chatType.maxLines = 1; chatType.ellipsize = android.text.TextUtils.TruncateAt.END
            } else chatType.text = if (context.resources.configuration.locales[0].language == "ru") "Нет сообщений" else "No messages"
            unreadCount.isVisible = chat.unreadCount > 0
            if (chat.unreadCount > 0) unreadCount.text = chat.unreadCount.toString()
            val isMeAdmin = chat.creator.trim().equals(currentUsername.trim(), ignoreCase = true)
            adminIndicator.isVisible = !chat.type.equals("direct", ignoreCase = true) && isMeAdmin
            if (isMeAdmin) adminIndicator.setOnClickListener { onSettingsClick?.invoke(chat) } else adminIndicator.setOnClickListener(null)
            loadParticipantAvatars(chat.participants, chat.type, currentUsername, avatarCache, onlineUsers, chat.avatarUrl)
            itemView.setOnClickListener {
                if (selectedPositions.isNotEmpty()) {
                    if ((chat.type == "group" || chat.type == "general") && !chat.creator.trim().equals(currentUsername.trim(), ignoreCase = true)) {
                        val msg = if (context.resources.configuration.locales[0].language == "ru") "Вы не являетесь администратором этой группы" else "You are not the admin of this group"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show(); return@setOnClickListener
                    }
                    onLongClick()
                } else onChatClick(chat)
            }
            itemView.setOnLongClickListener {
                if ((chat.type == "group" || chat.type == "general") && !chat.creator.trim().equals(currentUsername.trim(), ignoreCase = true)) {
                    val msg = if (context.resources.configuration.locales[0].language == "ru") "Вы не являетесь администратором этой группы" else "You are not the admin of this group"
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show(); return@setOnLongClickListener true
                }
                onLongClick(); true
            }
        }

        private fun loadParticipantAvatars(participantsJson: String, chatType: String, currentUsername: String, avatarCache: Map<String, String>, onlineUsers: List<String>, chatAvatarUrl: String = "") {
            participantAvatars.removeAllViews()
            if (participantsJson.isEmpty()) return
            try {
                val context = itemView.context
                if (chatAvatarUrl.isNotEmpty()) {
                    val avatarSize = 52.dpToPx(); val avatar = ImageView(context).apply { layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize); scaleType = ImageView.ScaleType.CENTER_CROP }
                    Glide.with(context).load(chatAvatarUrl).placeholder(R.drawable.ic_default_avatar).error(R.drawable.ic_default_avatar).circleCrop().into(avatar)
                    participantAvatars.addView(avatar); return
                }
                val participantsArray = JSONArray(participantsJson); val participantsList = mutableListOf<String>()
                for (i in 0 until participantsArray.length()) participantsList.add(participantsArray.getString(i))
                if (chatType == "direct") {
                    val otherPerson = participantsList.find { it != currentUsername } ?: currentUsername
                    val isOnline = onlineUsers.contains(otherPerson); val avatarSize = 52.dpToPx()
                    val container = FrameLayout(context).apply { layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize) }
                    val avatar = ImageView(context).apply { layoutParams = FrameLayout.LayoutParams(avatarSize, avatarSize); scaleType = ImageView.ScaleType.CENTER_CROP }
                    val cachedAvatarUrl = avatarCache[otherPerson]
                    if (!cachedAvatarUrl.isNullOrBlank()) Glide.with(context).load(cachedAvatarUrl).placeholder(R.drawable.ic_default_avatar).error(R.drawable.ic_default_avatar).circleCrop().into(avatar)
                    else avatar.setImageResource(R.drawable.ic_default_avatar)
                    container.addView(avatar)
                    if (isOnline) {
                        val dotSize = 14.dpToPx()
                        val dot = View(context).apply {
                            layoutParams = FrameLayout.LayoutParams(dotSize, dotSize).apply {
                                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                                setMargins(0, 0, 2.dpToPx(), 2.dpToPx())
                            }
                            background = ContextCompat.getDrawable(context, R.drawable.status_online_dot)
                        }
                        container.addView(dot)
                    }
                    participantAvatars.addView(container)
                } else {
                    val maxAvatars = 3
                    val avatarSize = 40.dpToPx()
                    val dotSize = 10.dpToPx()
                    for (i in 0 until minOf(participantsList.size, maxAvatars)) {
                        val uName = participantsList[i]
                        val isOnline = onlineUsers.contains(uName)
                        val container = FrameLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply {
                                if (i > 0) setMargins((-15).dpToPx(), 0, 0, 0)
                            }
                        }
                        val avatar = ImageView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(avatarSize, avatarSize)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                        val cachedAvatarUrl = avatarCache[uName]
                        if (!cachedAvatarUrl.isNullOrBlank()) Glide.with(context).load(cachedAvatarUrl).placeholder(R.drawable.ic_default_avatar).error(R.drawable.ic_default_avatar).circleCrop().into(avatar)
                        else avatar.setImageResource(R.drawable.ic_default_avatar)
                        container.addView(avatar)
                        if (isOnline) {
                            val dot = View(context).apply {
                                layoutParams = FrameLayout.LayoutParams(dotSize, dotSize).apply {
                                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                                    setMargins(0, 0, 1.dpToPx(), 1.dpToPx())
                                }
                                background = ContextCompat.getDrawable(context, R.drawable.status_online_dot)
                            }
                            container.addView(dot)
                        }
                        participantAvatars.addView(container)
                    }
                    if (participantsList.size > maxAvatars) {
                        val remainingCount = participantsList.size - maxAvatars
                        val countView = TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply { setMargins((-15).dpToPx(), 0, 0, 0) }
                            text = context.getString(R.string.plus_count_format, remainingCount)
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

        private fun applyDefaultCardBackground(context: android.content.Context) {
            val typedValue = android.util.TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
            val color = if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
            cardView.setCardBackgroundColor(color)
        }

        private fun Int.dpToPx(): Int = (this * itemView.resources.displayMetrics.density).toInt()
    }
}
