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
import lavender.client.android.theme.Theme
import lavender.client.android.theme.ThemeStore
import org.json.JSONArray
import kotlin.math.roundToInt

class ChatAdapter(
    private val onChatClick: (ChatInfo) -> Unit,
    private val onSelectionChanged: (Int) -> Unit = {},
    private val currentUsername: String = "",
    initialAvatarCache: Map<String, String> = emptyMap(),
    private var onlineUsers: List<String> = emptyList(),
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private var allChats = listOf<ChatInfo>()
    private var displayedChats = listOf<ChatInfo>()
    var avatarCache: Map<String, String> = initialAvatarCache
    private val selectedPositions = mutableSetOf<Int>()
    private val deletingChatIds = mutableSetOf<String>()
    private var currentFilter: String = ""

    fun getSelectedChats(): List<ChatInfo> {
        return selectedPositions.map { displayedChats[it] }
    }

    fun setChatDeleting(chatId: String, deleting: Boolean) {
        if (deleting) deletingChatIds.add(chatId) else deletingChatIds.remove(chatId)
        val index = displayedChats.indexOfFirst { it.id == chatId }
        if (index != -1) notifyItemChanged(index)
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
        val theme = ThemeStore.currentTheme()
        val isSelected = selectedPositions.contains(position)
        val chat = displayedChats[position]

        holder.bind(
            chat,
            currentUsername,
            isSelected,
            deletingChatIds.contains(chat.id),
            theme
        ) {
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

        // Lazy load avatars
        holder.loadParticipantAvatars(chat.participants, chat.type, currentUsername, avatarCache, onlineUsers, chat.avatarUrl)
    }

    override fun getItemCount(): Int = displayedChats.size

    inner class ChatViewHolder(itemView: View, private val onChatClick: (ChatInfo) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val chatName: TextView = itemView.findViewById(R.id.chatName)
        private val chatType: TextView = itemView.findViewById(R.id.chatType)
        private val unreadCount: TextView = itemView.findViewById(R.id.unreadCount)
        private val adminIndicator: ImageView = itemView.findViewById(R.id.adminIndicator)
        private val muteIndicator: ImageView = itemView.findViewById(R.id.muteIndicator)
        private val deleteProgressBar: android.widget.ProgressBar = itemView.findViewById(R.id.deleteProgressBar)
        val participantAvatars: LinearLayout = itemView.findViewById(R.id.participantAvatars)
        private val cardView: com.google.android.material.card.MaterialCardView = itemView as com.google.android.material.card.MaterialCardView

        fun bind(
            chat: ChatInfo,
            currentUsername: String,
            isSelected: Boolean,
            isDeleting: Boolean,
            theme: Theme,
            onLongClick: () -> Unit
        ) {
            val context = itemView.context

            val defaultCardBg = "#1A1B46".toColorInt()
            val defaultText = android.graphics.Color.WHITE
            val defaultSecondary = "#E6E6FA".toColorInt()
            val defaultPrimary = "#B19CD9".toColorInt()

            val primaryColor = parseSafeColor(theme.primaryColor, defaultPrimary)
            val textPrimary = parseSafeColor(theme.textPrimaryColor, defaultText)
            val textSecondary = parseSafeColor(theme.onSurfaceColor, defaultSecondary)
            val surfaceColor = parseSafeColor(theme.surfaceColor, defaultCardBg)

            if (isSelected) {
                cardView.setCardBackgroundColor(adjustAlpha(primaryColor, 0.3f))
                itemView.alpha = 0.8f
            } else {
                cardView.setCardBackgroundColor(surfaceColor)
                itemView.alpha = 1.0f
            }

            chatName.setTextColor(textPrimary)
            chatType.setTextColor(textSecondary)
            chatName.text = chat.getDisplayName(currentUsername)

            if (chat.lastMessageText.isNotEmpty()) {
                val prefix = if (chat.type == "group" || chat.type == "general") {
                    if (chat.lastMessageUsername.isNotEmpty()) "${chat.lastMessageUsername}: " else ""
                } else ""
                chatType.text = context.getString(R.string.chat_last_message_format, prefix, chat.lastMessageText)
                chatType.maxLines = 1
                chatType.ellipsize = android.text.TextUtils.TruncateAt.END
            } else {
                chatType.text = if (context.resources.configuration.locales[0].language == "ru") "Нет сообщений" else "No messages"
            }

            unreadCount.isVisible = chat.unreadCount > 0
            if (chat.unreadCount > 0) {
                unreadCount.text = chat.unreadCount.toString()
                unreadCount.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
                val textColorForBadge = if (primaryColor.isLight()) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                unreadCount.setTextColor(textColorForBadge)
            }

            // Admin settings moved to toolbar in ChatListActivity
            adminIndicator.isVisible = false
            adminIndicator.setOnClickListener(null)

            muteIndicator.isVisible = chat.isMuted && !isDeleting

            deleteProgressBar.isVisible = isDeleting
            if (isDeleting) {
                deleteProgressBar.indeterminateTintList = android.content.res.ColorStateList.valueOf(primaryColor)
                unreadCount.isVisible = false
            }

            itemView.setOnClickListener {
                if (isDeleting) return@setOnClickListener
                if (selectedPositions.isNotEmpty()) {
                    onLongClick()
                } else {
                    onChatClick(chat)
                }
            }

            itemView.setOnLongClickListener {
                onLongClick()
                true
            }
        }

        fun loadParticipantAvatars(participantsJson: String, chatType: String, currentUsername: String, avatarCache: Map<String, String>, onlineUsers: List<String>, chatAvatarUrl: String = "") {
            participantAvatars.removeAllViews()
            if (participantsJson.isEmpty()) return
            try {
                val context = itemView.context
                if (chatAvatarUrl.isNotEmpty()) {
                    val avatarSize = 52.dpToPx(); val avatar = ImageView(context).apply { layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize); scaleType = ImageView.ScaleType.CENTER_CROP }
                    Glide.with(context).load(chatAvatarUrl).thumbnail(Glide.with(context).load(chatAvatarUrl).sizeMultiplier(0.1f)).placeholder(R.drawable.ic_default_avatar).error(R.drawable.ic_default_avatar).circleCrop().into(avatar)
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
                    if (!cachedAvatarUrl.isNullOrBlank()) Glide.with(context).load(cachedAvatarUrl).thumbnail(Glide.with(context).load(cachedAvatarUrl).sizeMultiplier(0.1f)).placeholder(R.drawable.ic_default_avatar).error(R.drawable.ic_default_avatar).circleCrop().into(avatar)
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
                        if (!cachedAvatarUrl.isNullOrBlank()) Glide.with(context).load(cachedAvatarUrl).thumbnail(Glide.with(context).load(cachedAvatarUrl).sizeMultiplier(0.1f)).placeholder(R.drawable.ic_default_avatar).error(R.drawable.ic_default_avatar).circleCrop().into(avatar)
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
                    
                    val onlineCount = participantsList.count { onlineUsers.contains(it) }
                    val totalCount = participantsList.size
                    if (totalCount > 0) {
                        val onlineIndicator = TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                                setMargins(8.dpToPx(), 0, 0, 0)
                            }
                            text = context.getString(R.string.online_count_format, onlineCount, totalCount)
                            textSize = 12f
                            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                        }
                        participantAvatars.addView(onlineIndicator)
                    }
                }
            } catch (_: Exception) {}
        }

        private fun parseSafeColor(colorStr: String?, defaultColor: Int): Int {
            if (colorStr.isNullOrEmpty()) return defaultColor
            return try {
                colorStr.toColorInt()
            } catch (_: Exception) {
                defaultColor
            }
        }

        private fun Int.isLight(): Boolean {
            val darkness = 1 - (0.299 * android.graphics.Color.red(this) +
                    0.587 * android.graphics.Color.green(this) +
                    0.114 * android.graphics.Color.blue(this)) / 255
            return darkness < 0.5
        }

        private fun Int.dpToPx(): Int = (this * itemView.resources.displayMetrics.density).toInt()

        private fun adjustAlpha(color: Int, factor: Float): Int {
            val alpha = (android.graphics.Color.alpha(color) * factor).roundToInt()
            val red = android.graphics.Color.red(color)
            val green = android.graphics.Color.green(color)
            val blue = android.graphics.Color.blue(color)
            return android.graphics.Color.argb(alpha, red, green, blue)
        }
    }
}
