package lavender.client.android.ui.adapter

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.checkbox.MaterialCheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import lavender.client.android.R
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.ui.chatlist.SectionItem
import lavender.client.android.ui.chatlist.Section
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

/**
 * ChatAdapter — ListAdapter with DiffUtil for animated updates.
 *
 * ViewType:
 * - SECTION_HEADER — section header
 * - CHAT_ITEM — chat item
 *
 * Selection Mode:
 * - CheckBox on each item when enabled
 * - Multiple selection via tap (toggle)
 * - Visual highlight for selected items
 */
interface ChatListAdapter {
    fun getItemAtPosition(position: Int): FlatItem?
}

class ChatAdapter(
    @Suppress("UNUSED_PARAMETER") private val scope: CoroutineScope,
    private val currentUsername: String,
    private val onChatClick: (ChatInfo) -> Unit,
    private val onChatLongClick: (ChatInfo, View) -> Unit,
    private val onSelectionChanged: (Int) -> Unit = {},
    onlineUsersList: List<String> = emptyList(),
    allUsersList: List<lavender.client.android.data.proto.UserInfoProto> = emptyList()
) : ListAdapter<FlatItem, RecyclerView.ViewHolder>(FlatItemDiffCallback()), ChatListAdapter {

    companion object {
        private const val TYPE_SECTION_HEADER = 0
        private const val TYPE_CHAT_ITEM = 1

        fun getOrComputeOtherParticipant(chat: ChatInfo, currentUsername: String, cache: MutableMap<String, String>): String {
            cache[chat.id]?.let { return it }
            val result = try {
                val arr = org.json.JSONArray(chat.participants)
                var other = ""
                for (i in 0 until arr.length()) {
                    val p = arr.getString(i)
                    if (p != currentUsername) { other = p; break }
                }
                other
            } catch (_: Exception) { "" }
            cache[chat.id] = result
            return result
        }
    }

    private var sections: List<SectionItem> = emptyList()
    private var currentFilter: String = ""

    // Selection state
    private var selectionMode = false
    private val selectedIds = mutableSetOf<String>()

    // Performance caches
    private var onlineUsersSet: Set<String> = onlineUsersList.toSet()
    private var allUsersMap: Map<String, lavender.client.android.data.proto.UserInfoProto> =
        allUsersList.associateBy { it.username }
    private var avatarUrlCache: Map<String, String> = allUsersList.associate { it.username to it.avatarUrl }
    private var otherParticipantCache: MutableMap<String, String> = mutableMapOf()

    // Theme colors — single cache for entire adapter
    private var cachedPrimaryColor: Int = 0
    private var cachedTextPrimary: Int = 0
    private var cachedTextSecondary: Int = 0
    private var cachedSurfaceColor: Int = 0
    private var cachedSelectedColor: Int = 0
    private var cachedUnreadColor: Int = 0
    private var colorsInitialized = false

    private fun initColors(view: View) {
        if (colorsInitialized) return
        val theme = ThemeStore.currentTheme()
        cachedPrimaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
        cachedTextPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        cachedTextSecondary = ThemeUtils.parseSafeColor(theme.onSurfaceColor, Color.LTGRAY)
        cachedSurfaceColor = ThemeUtils.parseSafeColor(theme.incomingBubbleColor, Color.DKGRAY)
        cachedSelectedColor = Color.argb(48, Color.red(cachedPrimaryColor), Color.green(cachedPrimaryColor), Color.blue(cachedPrimaryColor))
        cachedUnreadColor = Color.argb(40, Color.red(cachedPrimaryColor), Color.green(cachedPrimaryColor), Color.blue(cachedPrimaryColor))
        colorsInitialized = true
    }

    fun updateTheme() {
        colorsInitialized = false
        notifyDataSetChanged()
    }

    // ======= Public API =======

    /**
     * Update sections with DiffUtil for animated changes.
     */
    fun setSections(newSections: List<SectionItem>) {
        sections = newSections
        val newFlat = buildFlatList(newSections)
        submitList(newFlat)
    }

    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    fun getSelectedChats(): List<ChatInfo> {
        return currentList.mapNotNull { item ->
            when (item) {
                is FlatItem.ChatItem -> if (selectedIds.contains(item.chat.id)) item.chat else null
                else -> null
            }
        }
    }

    fun setSelectionMode(enabled: Boolean) {
        if (selectionMode == enabled) return
        selectionMode = enabled
        if (!enabled) {
            selectedIds.clear()
        }
        notifyChatItemsChanged()
    }

    fun toggleSelection(chatId: String) {
        if (selectedIds.contains(chatId)) {
            selectedIds.remove(chatId)
        } else {
            selectedIds.add(chatId)
        }
        onSelectionChanged(selectedIds.size)
        val pos = currentList.indexOfFirst { it is FlatItem.ChatItem && it.chat.id == chatId }
        if (pos >= 0) notifyItemChanged(pos)
    }

    fun clearSelection() {
        val previousSelected = selectedIds.toSet()
        selectedIds.clear()
        selectionMode = false
        onSelectionChanged(0)
        currentList.forEachIndexed { i, item ->
            if (item is FlatItem.ChatItem && previousSelected.contains(item.chat.id)) {
                notifyItemChanged(i)
            }
        }
    }

    private fun notifyChatItemsChanged() {
        currentList.forEachIndexed { i, item ->
            if (item is FlatItem.ChatItem) notifyItemChanged(i)
        }
    }

    fun isSelectionMode(): Boolean = selectionMode

    fun currentList(): List<FlatItem> = currentList

    // ======= Internal =======

    override fun getItemAtPosition(position: Int): FlatItem? {
        return getItem(position)
    }

    private fun buildFlatList(sections: List<SectionItem>): List<FlatItem> {
        val result = mutableListOf<FlatItem>()
        for (section in sections) {
            for (chat in section.chats) {
                result.add(FlatItem.ChatItem(chat))
            }
        }
        return result
    }

    // ======= ListAdapter overrides =======

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is FlatItem.SectionHeader -> TYPE_SECTION_HEADER
            is FlatItem.ChatItem -> TYPE_CHAT_ITEM
            null -> TYPE_CHAT_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_SECTION_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_section_header, parent, false)
                SectionHeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat, parent, false)
                ChatViewHolder(view, onChatClick, onChatLongClick)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        initColors(holder.itemView)
        when (val item = getItem(position)) {
            is FlatItem.SectionHeader -> (holder as SectionHeaderViewHolder).bind(item)
            is FlatItem.ChatItem -> (holder as ChatViewHolder).bind(
                item.chat, cachedTextPrimary, cachedTextSecondary, cachedSurfaceColor, cachedSelectedColor, cachedUnreadColor, cachedPrimaryColor, selectionMode, selectedIds.contains(item.chat.id), currentUsername, onlineUsersSet, allUsersMap, avatarUrlCache, otherParticipantCache
            )
            null -> {}
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ChatViewHolder) {
            holder.clearAvatar()
        }
    }

    fun updateOnlineUsers(users: List<String>) {
        onlineUsersSet = users.toSet()
        val currentItems = currentList
        val changedPositions = mutableListOf<Int>()
        for (i in currentItems.indices) {
            val item = currentItems[i]
            if (item is FlatItem.ChatItem && item.chat.type == "direct" && !item.chat.isSecret && !item.chat.id.startsWith("favorites_")) {
                changedPositions.add(i)
            }
        }
        for (pos in changedPositions) {
            notifyItemChanged(pos)
        }
    }

    fun updateAllUsers(users: List<lavender.client.android.data.proto.UserInfoProto>) {
        val oldAvatarCache = avatarUrlCache
        allUsersMap = users.associateBy { it.username }
        avatarUrlCache = users.associate { it.username to it.avatarUrl }
        val currentItems = currentList
        val changedPositions = mutableListOf<Int>()
        for (i in currentItems.indices) {
            val item = currentItems[i]
            if (item is FlatItem.ChatItem && item.chat.type == "direct" && !item.chat.isSecret && !item.chat.id.startsWith("favorites_")) {
                val otherUser = getOrComputeOtherParticipant(item.chat, currentUsername, otherParticipantCache)
                val oldUrl = oldAvatarCache[otherUser] ?: ""
                val newUrl = avatarUrlCache[otherUser] ?: ""
                if (oldUrl != newUrl) changedPositions.add(i)
            }
        }
        for (pos in changedPositions) {
            notifyItemChanged(pos)
        }
    }

    fun filter(query: String) {
        currentFilter = query.lowercase()
        val filteredSections = sections.map { section ->
            val filteredChats = if (currentFilter.isEmpty()) {
                section.chats
            } else {
                section.chats.filter { chat ->
                    chat.getDisplayName(currentUsername).lowercase().contains(currentFilter) ||
                    chat.lastMessageText.lowercase().contains(currentFilter)
                }
            }
            section.copy(chats = filteredChats)
        }
        setSections(filteredSections)
    }

    // ======= DiffUtil callback =======

    class FlatItemDiffCallback : DiffUtil.ItemCallback<FlatItem>() {
        override fun areItemsTheSame(oldItem: FlatItem, newItem: FlatItem): Boolean {
            return when {
                oldItem is FlatItem.SectionHeader && newItem is FlatItem.SectionHeader ->
                    oldItem.section == newItem.section
                oldItem is FlatItem.ChatItem && newItem is FlatItem.ChatItem ->
                    oldItem.chat.id == newItem.chat.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: FlatItem, newItem: FlatItem): Boolean {
            return oldItem == newItem
        }
    }

    // ======= ViewHolders =======

    class SectionHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSectionName: TextView = itemView.findViewById(R.id.tvSectionName)
        private val tvSectionCount: TextView = itemView.findViewById(R.id.tvSectionCount)

        fun bind(item: FlatItem.SectionHeader) {
            tvSectionName.text = when (item.section) {
                Section.PINNED -> itemView.context.getString(R.string.section_pinned)
                Section.ALL_CHATS -> itemView.context.getString(R.string.section_all_chats)
                Section.ARCHIVED -> itemView.context.getString(R.string.section_archived)
            }
            tvSectionCount.text = "(${item.count})"
        }
    }

    class ChatViewHolder(
        itemView: View,
        private val onChatClick: (ChatInfo) -> Unit,
        private val onChatLongClick: (ChatInfo, View) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvChatName: TextView = itemView.findViewById(R.id.tvChatName)
        private val tvChatType: TextView = itemView.findViewById(R.id.tvChatType)
        private val tvUnreadCount: TextView = itemView.findViewById(R.id.tvUnreadCount)
        private val ivMuteIndicator: ImageView = itemView.findViewById(R.id.ivMuteIndicator)
        private val cbChatSelect: MaterialCheckBox = itemView.findViewById(R.id.cbChatSelect)
        private val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)
        private val tvLastSeen: TextView = itemView.findViewById(R.id.tvLastSeen)
        private val tvCompanyBadge: TextView = itemView.findViewById(R.id.tvCompanyBadge)
        private val btnEnterLobby: ImageView = itemView.findViewById(R.id.btnEnterLobby)
        private val ivChatAvatar: de.hdodenhof.circleimageview.CircleImageView = itemView.findViewById(R.id.ivChatAvatar)
        private val cardView: com.google.android.material.card.MaterialCardView =
            itemView as com.google.android.material.card.MaterialCardView

        fun bind(chat: ChatInfo, textPrimary: Int, textSecondary: Int, surfaceColor: Int, selectedColor: Int, unreadColor: Int, primaryColor: Int, selectionMode: Boolean, isSelected: Boolean, currentUsername: String, onlineUsers: Set<String>, allUsersMap: Map<String, lavender.client.android.data.proto.UserInfoProto>, avatarCache: Map<String, String>, otherParticipantCache: MutableMap<String, String>) {
            val hasUnread = chat.unreadCount > 0
            tvChatName.text = chat.getDisplayName(currentUsername)
            tvChatName.setTextColor(if (hasUnread) primaryColor else textPrimary)
            tvChatName.setTypeface(null, if (hasUnread) Typeface.BOLD else Typeface.NORMAL)

            // Avatar
            val avatarUrl = if (chat.type == "direct" || chat.isSecret) {
                val otherUser = getOrComputeOtherParticipant(chat, currentUsername, otherParticipantCache)
                avatarCache[otherUser] ?: ""
            } else chat.avatarUrl
            try {
                val currentTag = ivChatAvatar.tag as? String
                if (avatarUrl.isNotEmpty() && avatarUrl != currentTag) {
                    ivChatAvatar.tag = avatarUrl
                    val sizePx = (48 * itemView.resources.displayMetrics.density).toInt()
                    com.bumptech.glide.Glide.with(itemView.context).load(avatarUrl)
                        .placeholder(R.drawable.ic_default_avatar).error(R.drawable.ic_default_avatar)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .override(sizePx, sizePx).circleCrop().into(ivChatAvatar)
                } else if (avatarUrl.isEmpty() && currentTag != null) {
                    ivChatAvatar.tag = null
                    com.bumptech.glide.Glide.with(itemView.context).clear(ivChatAvatar)
                    ivChatAvatar.setImageResource(R.drawable.ic_default_avatar)
                }
            } catch (_: Exception) { ivChatAvatar.setImageResource(R.drawable.ic_default_avatar) }

            // Company badge
            tvCompanyBadge.isVisible = chat.companyId.isNotEmpty()

            if (chat.isSecret) {
                tvChatType.text = itemView.context.getString(R.string.e2ee_verified)
                tvChatType.setTextColor(if (hasUnread) textPrimary else textSecondary)
                tvChatType.setTypeface(null, if (hasUnread) Typeface.BOLD else Typeface.NORMAL)
            } else if (chat.type == "conference") {
                tvChatType.text = itemView.context.getString(R.string.conference)
                tvChatType.setTextColor(if (hasUnread) textPrimary else textSecondary)
                tvChatType.setTypeface(null, if (hasUnread) Typeface.BOLD else Typeface.NORMAL)
            } else if (chat.lastMessageText.isNotEmpty()) {
                tvChatType.text = translateMediaPreview(stripForwardPrefix(chat.lastMessageText))
                tvChatType.setTextColor(if (hasUnread) textPrimary else textSecondary)
                tvChatType.setTypeface(null, Typeface.NORMAL)
            } else {
                tvChatType.text = itemView.context.getString(R.string.no_messages)
                tvChatType.setTextColor(textSecondary)
            }

            // Unread badge
            if (chat.unreadCount > 0 && !selectionMode) {
                tvUnreadCount.isVisible = true
                tvUnreadCount.text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString()
                tvUnreadCount.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
                tvUnreadCount.setTextColor(if (ThemeUtils.isLight(primaryColor)) Color.BLACK else Color.WHITE)
            } else {
                tvUnreadCount.isVisible = false
            }

            // Conference lobby button
            btnEnterLobby.isVisible = chat.type == "conference" && !selectionMode
            btnEnterLobby.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            btnEnterLobby.setOnClickListener { v ->
                val ctx = v.context
                val intent = android.content.Intent(ctx, lavender.client.android.ConferenceLobbyActivity::class.java).apply {
                    putExtra("ROOM_ID", chat.id)
                    putExtra("CHAT_NAME", chat.name)
                    putExtra("PARTICIPANTS", chat.participants)
                    putExtra("CREATOR", chat.creator)
                }
                ctx.startActivity(intent)
            }

            ivMuteIndicator.isVisible = chat.isMuted && !selectionMode

            // Selection mode
            cbChatSelect.isVisible = selectionMode
            cbChatSelect.isChecked = isSelected
            cbChatSelect.buttonTintList = android.content.res.ColorStateList.valueOf(primaryColor)

            // Background
            val bgColor = when {
                isSelected -> selectedColor
                chat.unreadCount > 0 -> unreadColor
                else -> surfaceColor
            }
            cbChatSelect.backgroundTintList = android.content.res.ColorStateList.valueOf(bgColor)
            cardView.setCardBackgroundColor(bgColor)

            // Online status + last seen
            if (chat.type == "direct" && !chat.isSecret && !chat.id.startsWith("favorites_")) {
                val otherUser = getOrComputeOtherParticipant(chat, currentUsername, otherParticipantCache)
                if (otherUser.isNotEmpty()) {
                    val isOnline = onlineUsers.contains(otherUser)
                    statusIndicator.isVisible = true
                    statusIndicator.setBackgroundResource(if (isOnline) R.drawable.status_online_dot else R.drawable.status_offline_dot)

                    if (!isOnline) {
                        val userInfo = allUsersMap[otherUser]
                        val lastSeenStr = userInfo?.lastSeenAt?.let { getTimeAgo(it.seconds * 1000, itemView.context) }
                        if (lastSeenStr != null) {
                            tvLastSeen.isVisible = true
                            tvLastSeen.text = lastSeenStr
                            tvLastSeen.setTextColor(textSecondary)
                        } else {
                            tvLastSeen.isVisible = false
                        }
                    } else {
                        tvLastSeen.isVisible = false
                    }
                } else {
                    statusIndicator.isVisible = false
                    tvLastSeen.isVisible = false
                }
            } else {
                statusIndicator.isVisible = false
                tvLastSeen.isVisible = false
            }

            // Click listeners
            if (selectionMode) {
                itemView.setOnClickListener { onChatClick(chat) }
                itemView.setOnLongClickListener(null)
            } else {
                itemView.setOnClickListener { onChatClick(chat) }
                itemView.setOnLongClickListener { view -> onChatLongClick(chat, view); true }
            }
        }

        private fun translateMediaPreview(text: String): String {
            val ctx = itemView.context
            return when (text) {
                "Image" -> ctx.getString(R.string.chat_preview_image)
                "Voice message" -> ctx.getString(R.string.chat_preview_voice)
                else -> text
            }
        }

        private fun stripForwardPrefix(text: String): String {
            val prefix = "\u200B\u2709"
            if (!text.startsWith(prefix)) return text
            val endIdx = text.indexOf('\u200B', prefix.length)
            if (endIdx <= prefix.length) return text
            val after = text.substring(endIdx + 1)
            return if (after.startsWith("\n")) after.substring(1) else after
        }

        private fun getTimeAgo(timestampMillis: Long, context: android.content.Context): String {
            val now = System.currentTimeMillis()
            val diff = now - timestampMillis
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            return when {
                seconds < 60 -> context.getString(R.string.just_now)
                minutes < 60 -> context.resources.getQuantityString(R.plurals.minutes_ago, minutes.toInt(), minutes.toInt())
                hours < 24 -> context.resources.getQuantityString(R.plurals.hours_ago, hours.toInt(), hours.toInt())
                days < 7 -> context.resources.getQuantityString(R.plurals.days_ago, days.toInt(), days.toInt())
                else -> {
                    val format = java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.getDefault())
                    format.format(java.util.Date(timestampMillis))
                }
            }
        }

        fun clearAvatar() {
            ivChatAvatar.tag = null
            com.bumptech.glide.Glide.with(itemView.context).clear(ivChatAvatar)
        }
    }
}

// ======= Flat list items =======

sealed class FlatItem {
    data class SectionHeader(val section: Section, val count: Int) : FlatItem()
    data class ChatItem(val chat: ChatInfo) : FlatItem()
}
