package lavender.client.android.ui.adapter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
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
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.*
import lavender.client.android.R
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.theme.Theme
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import org.json.JSONArray
import kotlin.math.roundToInt

class ChatAdapter(
    private val scope: CoroutineScope,
    private val onChatClick: (ChatInfo) -> Unit,
    private val onEnterLobbyClick: ((ChatInfo) -> Unit)? = null,
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
    private var diffJob: Job? = null
    
    // Track if we are currently calculating a diff to avoid inconsistent notify calls
    private var isDiffing = false

    // Pre-calculated theme colors
    private var cachedPrimaryColor: Int = 0
    private var cachedTextPrimary: Int = 0
    private var cachedTextSecondary: Int = 0
    private var cachedSurfaceColor: Int = 0
    private var cachedTheme: Theme? = null
    private var colorsInitialized = false
    private var density: Float = 1f

    private fun initColors(view: View) {
        if (colorsInitialized) return
        val theme = ThemeStore.currentTheme()
        cachedTheme = theme
        cachedPrimaryColor = parseSafeColor(theme.primaryColor, Color.BLUE)
        cachedTextPrimary = parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        cachedTextSecondary = parseSafeColor(theme.onSurfaceColor, Color.LTGRAY)
        cachedSurfaceColor = parseSafeColor(theme.surfaceColor, Color.DKGRAY)
        density = view.resources.displayMetrics.density
        colorsInitialized = true
    }

    private fun parseSafeColor(colorStr: String?, defaultColor: Int): Int {
        if (colorStr.isNullOrEmpty()) return defaultColor
        return try { colorStr.toColorInt() } catch (_: Exception) { defaultColor }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateTheme() {
        colorsInitialized = false
        notifyDataSetChanged()
    }

    fun getSelectedChats(): List<ChatInfo> = selectedPositions.map { displayedChats[it] }

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

    /**
     * Robust update method to prevent Inconsistency detected crash
     */
    fun setChats(newChats: List<ChatInfo>) {
        diffJob?.cancel()
        
        // If current list is empty, perform an immediate update to ensure initial state is consistent
        if (displayedChats.isEmpty()) {
            allChats = newChats
            displayedChats = newChats
            notifyDataSetChanged()
            return
        }

        isDiffing = true
        val oldChats = displayedChats
        diffJob = scope.launch(Dispatchers.Default) {
            val filtered = if (currentFilter.isEmpty()) {
                newChats
            } else {
                newChats.filter { chat ->
                    chat.name.lowercase().contains(currentFilter) ||
                    chat.participants.lowercase().contains(currentFilter) ||
                    chat.lastMessageText.lowercase().contains(currentFilter)
                }
            }
            
            val diffResult = DiffUtil.calculateDiff(ChatDiffCallback(oldChats, filtered))
            
            if (!isActive) {
                isDiffing = false
                return@launch
            }
            
            withContext(Dispatchers.Main) {
                allChats = newChats
                displayedChats = filtered
                diffResult.dispatchUpdatesTo(this@ChatAdapter)
                isDiffing = false
            }
        }
    }

    fun filter(query: String) {
        currentFilter = query.lowercase()
        diffJob?.cancel()
        
        isDiffing = true
        val oldChats = displayedChats
        diffJob = scope.launch(Dispatchers.Default) {
            val filtered = if (currentFilter.isEmpty()) {
                allChats
            } else {
                allChats.filter { chat ->
                    chat.name.lowercase().contains(currentFilter) ||
                    chat.participants.lowercase().contains(currentFilter) ||
                    chat.lastMessageText.lowercase().contains(currentFilter)
                }
            }
            val diffResult = DiffUtil.calculateDiff(ChatDiffCallback(oldChats, filtered))
            
            if (!isActive) {
                isDiffing = false
                return@launch
            }
            
            withContext(Dispatchers.Main) {
                displayedChats = filtered
                diffResult.dispatchUpdatesTo(this@ChatAdapter)
                isDiffing = false
            }
        }
    }

    fun setOnlineUsers(users: List<String>) {
        if (onlineUsers == users) return
        onlineUsers = users
        
        // If we are currently diffing, don't perform partial updates as it might cause inconsistency
        if (isDiffing) return
        
        scope.launch(Dispatchers.Main) {
            val count = displayedChats.size
            if (count > 0 && !isDiffing) {
                try {
                    notifyItemRangeChanged(0, count, "status")
                } catch (_: Exception) {}
            }
        }
    }

    fun updateAvatarCache(newCache: Map<String, String>) {
        val snapshot = newCache.toMap()
        if (avatarCache == snapshot) return
        avatarCache = snapshot
        
        if (isDiffing) return

        scope.launch(Dispatchers.Main) {
            val count = displayedChats.size
            if (count > 0 && !isDiffing) {
                try {
                    notifyItemRangeChanged(0, count, "avatar")
                } catch (_: Exception) {}
            }
        }
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
            return (oldChat.name == newChat.name && oldChat.type == newChat.type &&
                    oldChat.unreadCount == newChat.unreadCount && oldChat.lastMessageTime == newChat.lastMessageTime &&
                    oldChat.isMuted == newChat.isMuted && oldChat.lastMessageText == newChat.lastMessageText &&
                    oldChat.lastMessageHasImage == newChat.lastMessageHasImage)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view, onChatClick)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        initColors(holder.itemView)
        val isSelected = selectedPositions.contains(position)
        val chat = displayedChats[position]
        holder.bind(chat, currentUsername, isSelected, deletingChatIds.contains(chat.id)) {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos == RecyclerView.NO_POSITION) return@bind
            if (selectedPositions.contains(currentPos)) selectedPositions.remove(currentPos)
            else selectedPositions.add(currentPos)
            notifyItemChanged(currentPos)
            onSelectionChanged(selectedPositions.size)
        }
        holder.loadParticipantAvatars(chat.participants, chat.type, currentUsername, avatarCache, onlineUsers, chat.avatarUrl)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            for (payload in payloads) {
                when (payload) {
                    "status", "avatar" -> holder.loadParticipantAvatars(displayedChats[position].participants, displayedChats[position].type, currentUsername, avatarCache, onlineUsers, displayedChats[position].avatarUrl)
                }
            }
        }
    }

    override fun getItemCount(): Int = displayedChats.size

    inner class ChatViewHolder(itemView: View, private val onChatClick: (ChatInfo) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val chatName: TextView = itemView.findViewById(R.id.chatName)
        private val chatType: TextView = itemView.findViewById(R.id.chatType)
        private val unreadCount: TextView = itemView.findViewById(R.id.unreadCount)
        private val adminIndicator: ImageView = itemView.findViewById(R.id.adminIndicator)
        private val muteIndicator: ImageView = itemView.findViewById(R.id.muteIndicator)
        private val btnEnterLobby: ImageView = itemView.findViewById(R.id.btnEnterLobby)
        private val deleteProgressBar: android.widget.ProgressBar = itemView.findViewById(R.id.deleteProgressBar)
        val participantAvatars: LinearLayout = itemView.findViewById(R.id.participantAvatars)
        private val cardView: com.google.android.material.card.MaterialCardView = itemView as com.google.android.material.card.MaterialCardView

        fun bind(chat: ChatInfo, currentUsername: String, isSelected: Boolean, isDeleting: Boolean, onLongClick: () -> Unit) {
            val context = itemView.context
            if (isSelected) {
                cardView.setCardBackgroundColor(adjustAlpha(cachedPrimaryColor, 0.3f))
                itemView.alpha = 0.8f
            } else {
                cardView.setCardBackgroundColor(cachedSurfaceColor)
                itemView.alpha = 1.0f
            }
            chatName.setTextColor(cachedTextPrimary)
            chatType.setTextColor(cachedTextSecondary)
            chatName.text = chat.getDisplayName(currentUsername)
            val lang = context.resources.configuration.locales[0].language
            
            if (chat.type == "conference" && chat.conferenceStartTime > 0) {
                val sdf = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())
                chatType.text = "📅 ${context.getString(R.string.starts_at, sdf.format(java.util.Date(chat.conferenceStartTime)))}"
                chatType.setTextColor(cachedPrimaryColor)
                chatType.setTypeface(null, android.graphics.Typeface.BOLD)
            } else if (chat.lastMessageHasImage) {
                val prefix = if ((chat.type == "group" || chat.type == "general") && chat.lastMessageUsername != "SYSTEM" && chat.lastMessageUsername.isNotEmpty()) "${chat.lastMessageUsername}: " else ""
                val photoText = if (lang == "ru") "📷 Фото" else "📷 Photo"
                chatType.text = context.getString(R.string.chat_last_message_format, prefix, photoText)
            } else if (chat.lastMessageText.isNotEmpty()) {
                val prefix = if ((chat.type == "group" || chat.type == "general") && chat.lastMessageUsername != "SYSTEM" && chat.lastMessageUsername.isNotEmpty()) "${chat.lastMessageUsername}: " else ""
                chatType.text = context.getString(R.string.chat_last_message_format, prefix, chat.lastMessageText)
            } else {
                chatType.text = if (lang == "ru") "Нет сообщений" else "No messages"
                chatType.setTextColor(cachedTextSecondary)
                chatType.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
            unreadCount.isVisible = chat.unreadCount > 0
            if (chat.unreadCount > 0) {
                unreadCount.text = chat.unreadCount.toString()
                unreadCount.backgroundTintList = ColorStateList.valueOf(cachedPrimaryColor)
                unreadCount.setTextColor(if (ThemeUtils.isLight(cachedPrimaryColor)) Color.BLACK else Color.WHITE)
            }
            adminIndicator.isVisible = false
            muteIndicator.isVisible = chat.isMuted && !isDeleting
            
            btnEnterLobby.isVisible = chat.type == "conference" && !isDeleting && selectedPositions.isEmpty()
            btnEnterLobby.setOnClickListener {
                onEnterLobbyClick?.invoke(chat)
            }

            deleteProgressBar.isVisible = isDeleting
            if (isDeleting) {
                deleteProgressBar.indeterminateTintList = ColorStateList.valueOf(cachedPrimaryColor)
                unreadCount.isVisible = false
            }
            itemView.setOnClickListener { if (!isDeleting) { if (selectedPositions.isNotEmpty()) onLongClick() else onChatClick(chat) } }
            itemView.setOnLongClickListener { onLongClick(); true }
        }

        fun loadParticipantAvatars(participantsJson: String, chatType: String, currentUsername: String, avatarCache: Map<String, String>, onlineUsers: List<String>, chatAvatarUrl: String = "") {
            try {
                val context = itemView.context
                if (chatType == "favorites") {
                    if (participantAvatars.childCount == 1 && participantAvatars.getChildAt(0).tag == "favorites") return
                    participantAvatars.removeAllViews()
                    val avatarSize = (52 * density).toInt()
                    val avatar = ShapeableImageView(context).apply {
                        tag = "favorites"
                        layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(RelativeCornerSize(0.5f)).build()
                        setImageResource(R.drawable.ic_star)
                        imageTintList = ColorStateList.valueOf(cachedPrimaryColor)
                        val p = (12 * density).toInt()
                        setPadding(p, p, p, p)
                        setBackgroundColor(adjustAlpha(cachedPrimaryColor, 0.15f))
                    }
                    participantAvatars.addView(avatar); return
                }

                if (chatType == "conference") {
                    participantAvatars.removeAllViews()
                    val avatarSize = (52 * density).toInt()
                    val container = FrameLayout(context).apply { layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize) }
                    
                    val avatar = ShapeableImageView(context).apply { 
                        layoutParams = FrameLayout.LayoutParams(avatarSize, avatarSize)
                        scaleType = if (chatAvatarUrl.isNotEmpty()) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.CENTER_INSIDE
                        shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(RelativeCornerSize(0.5f)).build()
                        if (chatAvatarUrl.isEmpty()) {
                            setImageResource(R.drawable.ic_videocam_on)
                            imageTintList = ColorStateList.valueOf(cachedPrimaryColor)
                            val p = (14 * density).toInt()
                            setPadding(p, p, p, p)
                            setBackgroundColor(adjustAlpha(cachedPrimaryColor, 0.15f))
                        }
                    }
                    
                    if (chatAvatarUrl.isNotEmpty()) {
                        Glide.with(context).load(chatAvatarUrl).placeholder(R.drawable.ic_default_avatar).into(avatar)
                    }
                    
                    container.addView(avatar)
                    
                    // Add a small live indicator if needed or just a badge
                    val badgeSize = (16 * density).toInt()
                    val badge = View(context).apply {
                        layoutParams = FrameLayout.LayoutParams(badgeSize, badgeSize).apply { 
                            gravity = android.view.Gravity.TOP or android.view.Gravity.END
                        }
                        background = ContextCompat.getDrawable(context, R.drawable.status_online_dot)
                        backgroundTintList = ColorStateList.valueOf(Color.RED)
                    }
                    container.addView(badge)
                    
                    participantAvatars.addView(container)
                    return
                }

                participantAvatars.removeAllViews()
                if (participantsJson.isEmpty() && chatAvatarUrl.isEmpty()) return
                if (chatAvatarUrl.isNotEmpty()) {
                    val avatarSize = (52 * density).toInt()
                    val avatar = ShapeableImageView(context).apply { 
                        layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(RelativeCornerSize(0.5f)).build()
                    }
                    Glide.with(context).load(chatAvatarUrl).placeholder(R.drawable.ic_default_avatar).into(avatar)
                    participantAvatars.addView(avatar); return
                }
                val participantsArray = JSONArray(participantsJson)
                val participantsList = List(participantsArray.length()) { participantsArray.getString(it) }
                if (chatType == "direct") {
                    val otherPerson = participantsList.find { it != currentUsername } ?: currentUsername
                    val isOnline = onlineUsers.contains(otherPerson)
                    val avatarSize = (52 * density).toInt()
                    val container = FrameLayout(context).apply { layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize) }
                    val avatar = ShapeableImageView(context).apply { 
                        layoutParams = FrameLayout.LayoutParams(avatarSize, avatarSize)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(RelativeCornerSize(0.5f)).build()
                    }
                    val cachedAvatarUrl = avatarCache[otherPerson]
                    if (!cachedAvatarUrl.isNullOrBlank()) {
                        Glide.with(context).load(cachedAvatarUrl).placeholder(R.drawable.ic_default_avatar).into(avatar)
                    } else {
                        ThemeUtils.applyDefaultAvatar(avatar, cachedTheme!!)
                    }
                    container.addView(avatar)
                    if (isOnline) {
                        val dotSize = (14 * density).toInt()
                        val dot = View(context).apply {
                            layoutParams = FrameLayout.LayoutParams(dotSize, dotSize).apply { gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END; setMargins(0, 0, (2 * density).toInt(), (2 * density).toInt()) }
                            background = ContextCompat.getDrawable(context, R.drawable.status_online_dot)
                        }
                        container.addView(dot)
                    }
                    participantAvatars.addView(container)
                } else {
                    val maxAvatars = 3; val avatarSize = (40 * density).toInt(); val dotSize = (10 * density).toInt()
                    for (i in 0 until minOf(participantsList.size, maxAvatars)) {
                        val uName = participantsList[i]; val isOnline = onlineUsers.contains(uName)
                        val container = FrameLayout(context).apply { layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply { if (i > 0) setMargins((-15 * density).toInt(), 0, 0, 0) } }
                        val avatar = ShapeableImageView(context).apply { 
                            layoutParams = FrameLayout.LayoutParams(avatarSize, avatarSize)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(RelativeCornerSize(0.5f)).build()
                        }
                        val cachedAvatarUrl = avatarCache[uName]
                        if (!cachedAvatarUrl.isNullOrBlank()) Glide.with(context).load(cachedAvatarUrl).placeholder(R.drawable.ic_default_avatar).into(avatar)
                        else ThemeUtils.applyDefaultAvatar(avatar, cachedTheme!!)
                        container.addView(avatar)
                        if (isOnline) {
                            val dot = View(context).apply {
                                layoutParams = FrameLayout.LayoutParams(dotSize, dotSize).apply { gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END; setMargins(0, 0, (1 * density).toInt(), (1 * density).toInt()) }
                                background = ContextCompat.getDrawable(context, R.drawable.status_online_dot)
                            }
                            container.addView(dot)
                        }
                        participantAvatars.addView(container)
                    }
                    if (participantsList.size > maxAvatars) {
                        val remainingCount = participantsList.size - maxAvatars
                        val countView = TextView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize).apply { setMargins((-15 * density).toInt(), 0, 0, 0) }
                            text = context.getString(R.string.plus_count_format, remainingCount); textSize = 11f; gravity = android.view.Gravity.CENTER; setTextColor(Color.WHITE); setBackgroundResource(R.drawable.unread_count_background)
                        }
                        participantAvatars.addView(countView)
                    }
                }
            } catch (_: Exception) {}
        }

        private fun adjustAlpha(color: Int, factor: Float): Int {
            val alpha = (Color.alpha(color) * factor).roundToInt()
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        }
    }
}
