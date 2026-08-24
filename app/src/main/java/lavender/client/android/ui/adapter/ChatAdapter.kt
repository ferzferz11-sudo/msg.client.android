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
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

/**
 * ChatAdapter — ListAdapter with DiffUtil for animated updates.
 *
 * Performance optimizations:
 * - Stable IDs for efficient ViewHolder reuse
 * - Cached display names (JSON parsed once, not per-bind)
 * - Cached message previews (system message check + strip + translation done once)
 * - updateOnlineUsers: only notifies items whose status actually changed
 * - setSelectionMode/clearSelection: uses partial notify instead of notifyDataSetChanged
 * - Fast mode: skips all heavy work (Glide, borders, text processing)
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
        const val TYPE_SECTION_HEADER = 0
        const val TYPE_CHAT_ITEM = 1

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

        /** Pre-compute display name once (avoids JSON parse on every bind/sort). */
        private fun computeDisplayName(chat: ChatInfo, currentUsername: String, otherCache: MutableMap<String, String>): String {
            if (chat.isSecret) {
                val other = getOrComputeOtherParticipant(chat, currentUsername, otherCache)
                return if (other.isNotEmpty()) "\uD83D\uDD12 $other" else chat.name.replace(currentUsername, "").trim().trim(',')
            }
            if (chat.type != "direct") return chat.name
            val other = getOrComputeOtherParticipant(chat, currentUsername, otherCache)
            return other.ifEmpty { chat.name }
        }

        /** Pre-compute message preview once (avoids string ops on every bind). */
        private fun computeMessagePreview(chat: ChatInfo, context: android.content.Context): String? {
            if (chat.isSecret) return null
            val text = chat.lastMessageText
            if (text.isEmpty()) return null
            if (isSystemMessagePreviewRaw(text)) return null
            
            val stripped = stripForwardPrefixRaw(text)
            return translateMediaPreviewRaw(stripped, context)
        }

        private fun translateMediaPreviewRaw(text: String, context: android.content.Context): String {
            return when (text) {
                "Image" -> context.getString(R.string.chat_preview_image)
                "Voice message" -> context.getString(R.string.chat_preview_voice)
                else -> text
            }
        }

        private fun isSystemMessagePreviewRaw(text: String): Boolean {
            return text.startsWith("\uD83D\uDD25") || // 🔥
                   text.startsWith("\uD83D\uDCF9") || // 📹
                   text.startsWith("\uD83D\uDCDE")    // 📞
        }

        private fun stripForwardPrefixRaw(text: String): String {
            val prefix = "\u200B\u2709"
            if (!text.startsWith(prefix)) return text
            val endIdx = text.indexOf('\u200B', prefix.length)
            if (endIdx <= prefix.length) return text
            val after = text.substring(endIdx + 1)
            return if (after.startsWith("\n")) after.substring(1) else after
        }
    }

    private var sections: List<SectionItem> = emptyList()
    private var currentFilter: String = ""

    // Selection state
    private var selectionMode = false
    private val selectedIds = mutableSetOf<String>()

    // Fast mode — disables avatars and heavy graphics
    var fastModeEnabled: Boolean = false

    // Performance caches
    private var onlineUsersSet: Set<String> = onlineUsersList.toSet()
    private var previousOnlineUsersSet: Set<String> = onlineUsersList.toSet()
    private var allUsersMap: Map<String, lavender.client.android.data.proto.UserInfoProto> =
        allUsersList.associateBy { it.username }
    private var avatarUrlCache: Map<String, String> = allUsersList.associateBy({ it.username }, { it.avatarUrl })
    private var otherParticipantCache: MutableMap<String, String> = mutableMapOf()

    // Pre-computed caches (populated on setSections)
    private var displayNameCache: MutableMap<String, String> = mutableMapOf()
    private var messagePreviewCache: MutableMap<String, String?> = mutableMapOf()

    // Theme colors — single cache for entire adapter
    private var cachedPrimaryColor: Int = 0
    private var cachedTextPrimary: Int = 0
    private var cachedTextSecondary: Int = 0
    private var cachedSurfaceColor: Int = 0
    private var cachedSelectedColor: Int = 0
    private var cachedUnreadColor: Int = 0
    private var cachedIsLightTheme: Boolean = false
    private var colorsInitialized = false

    // Enable stable IDs for efficient ViewHolder reuse
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return when (val item = getItem(position)) {
            is FlatItem.ChatItem -> item.chat.id.hashCode().toLong()
            is FlatItem.SectionHeader -> (0x7FFFFFF0 - item.section.ordinal).toLong()
            null -> RecyclerView.NO_ID
        }
    }

    private fun initColors() {
        if (colorsInitialized) return
        val theme = ThemeStore.currentTheme()
        cachedPrimaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
        cachedTextPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        cachedTextSecondary = ThemeUtils.parseSafeColor(theme.onSurfaceColor, Color.LTGRAY)
        cachedSurfaceColor = ThemeUtils.parseSafeColor(theme.incomingBubbleColor, Color.DKGRAY)
        cachedSelectedColor = Color.argb(48, Color.red(cachedPrimaryColor), Color.green(cachedPrimaryColor), Color.blue(cachedPrimaryColor))
        cachedUnreadColor = Color.argb(40, Color.red(cachedPrimaryColor), Color.green(cachedPrimaryColor), Color.blue(cachedPrimaryColor))
        cachedIsLightTheme = ThemeUtils.isLight(ThemeUtils.parseSafeColor(theme.backgroundColor, Color.BLACK))
        colorsInitialized = true
    }

    fun updateTheme() {
        colorsInitialized = false
        notifyDataSetChanged()
    }

    // ======= Public API =======

    fun setSections(newSections: List<SectionItem>, context: android.content.Context) {
        sections = newSections
        // Pre-compute display names and message previews for all chats
        for (section in newSections) {
            for (chat in section.chats) {
                val nameKey = chat.id
                if (!displayNameCache.containsKey(nameKey)) {
                    displayNameCache[nameKey] = computeDisplayName(chat, currentUsername, otherParticipantCache)
                }
                val previewKey = chat.id + "_" + chat.lastMessageTime
                if (!messagePreviewCache.containsKey(previewKey)) {
                    messagePreviewCache[previewKey] = computeMessagePreview(chat, context)
                }
            }
        }
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
        // Use partial update instead of notifyDataSetChanged — only rebind visible ChatItems
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
        val previousSelection = selectedIds.toSet()
        selectedIds.clear()
        selectionMode = false
        onSelectionChanged(0)
        // Only notify items that were actually selected
        val items = currentList
        for (i in items.indices) {
            val item = items[i]
            if (item is FlatItem.ChatItem && previousSelection.contains(item.chat.id)) {
                notifyItemChanged(i)
            }
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

    /** Notify only ChatItem positions (skip SectionHeaders). */
    private fun notifyChatItemsChanged() {
        val items = currentList
        for (i in items.indices) {
            if (items[i] is FlatItem.ChatItem) {
                notifyItemChanged(i)
            }
        }
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
        initColors()
        when (val item = getItem(position)) {
            is FlatItem.SectionHeader -> (holder as SectionHeaderViewHolder).bind(item)
            is FlatItem.ChatItem -> {
                val chat = item.chat
                val displayName = displayNameCache[chat.id] ?: computeDisplayName(chat, currentUsername, otherParticipantCache)
                val previewKey = chat.id + "_" + chat.lastMessageTime
                val messagePreview = messagePreviewCache[previewKey] ?: computeMessagePreview(chat, holder.itemView.context)
                (holder as ChatViewHolder).bind(
                    chat, displayName, messagePreview,
                    cachedTextPrimary, cachedTextSecondary, cachedSurfaceColor,
                    cachedSelectedColor, cachedUnreadColor, cachedPrimaryColor,
                    cachedIsLightTheme, selectionMode, selectedIds.contains(chat.id),
                    currentUsername, onlineUsersSet, allUsersMap, avatarUrlCache,
                    otherParticipantCache, fastModeEnabled
                )
            }
            null -> {}
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        (holder as? ChatViewHolder)?.clearAvatar()
    }

    fun updateOnlineUsers(users: List<String>) {
        val newSet = users.toSet()
        // Only notify items whose online status actually changed
        val added = newSet - previousOnlineUsersSet
        val removed = previousOnlineUsersSet - newSet
        if (added.isEmpty() && removed.isEmpty()) return

        onlineUsersSet = newSet
        previousOnlineUsersSet = newSet

        val currentItems = currentList
        for (i in currentItems.indices) {
            val item = currentItems[i]
            if (item is FlatItem.ChatItem && item.chat.type == "direct" && !item.chat.isSecret && !item.chat.id.startsWith("saved_messages_")) {
                val otherUser = getOrComputeOtherParticipant(item.chat, currentUsername, otherParticipantCache)
                if (otherUser in added || otherUser in removed) {
                    notifyItemChanged(i)
                }
            }
        }
    }

    fun updateAllUsers(users: List<lavender.client.android.data.proto.UserInfoProto>) {
        val oldAvatarCache = avatarUrlCache
        allUsersMap = users.associateBy { it.username }
        avatarUrlCache = users.associate { it.username to it.avatarUrl }
        // Invalidate display name cache for direct chats (name might change)
        displayNameCache.clear()
        messagePreviewCache.clear()
        val currentItems = currentList
        val changedPositions = mutableListOf<Int>()
        for (i in currentItems.indices) {
            val item = currentItems[i]
            if (item is FlatItem.ChatItem && item.chat.type == "direct" && !item.chat.isSecret && !item.chat.id.startsWith("saved_messages_")) {
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
                    val displayName = displayNameCache[chat.id] ?: chat.getDisplayName(currentUsername)
                    displayName.lowercase().contains(currentFilter) ||
                    chat.lastMessageText.lowercase().contains(currentFilter)
                }
            }
            section.copy(chats = filteredChats)
        }
        val rv = (scope as? androidx.fragment.app.FragmentActivity)?.findViewById<RecyclerView>(R.id.rvChatList)
        setSections(filteredSections, rv?.context ?: return)
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
            tvSectionCount.text = itemView.context.getString(R.string.plus_count_format, item.count)
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

        private val dayFormat = SimpleDateFormat("dd.MM.yy", Locale.getDefault())

        fun bind(
            chat: ChatInfo,
            displayName: String,
            messagePreview: String?,
            textPrimary: Int, textSecondary: Int, surfaceColor: Int,
            selectedColor: Int, unreadColor: Int, primaryColor: Int,
            isLightTheme: Boolean, selectionMode: Boolean, isSelected: Boolean,
            currentUsername: String, onlineUsers: Set<String>,
            allUsersMap: Map<String, lavender.client.android.data.proto.UserInfoProto>,
            avatarCache: Map<String, String>,
            otherParticipantCache: MutableMap<String, String>,
            fastMode: Boolean
        ) {
            val hasUnread = chat.unreadCount > 0
            
            // Performance: Only update text if it actually changed
            if (tvChatName.text != displayName) {
                tvChatName.text = displayName
            }
            
            // Performance: Only update typeface if unread status changed
            val targetStyle = if (hasUnread) Typeface.BOLD else Typeface.NORMAL
            if (tvChatName.typeface?.style != targetStyle) {
                tvChatName.setTypeface(null, targetStyle)
            }
            tvChatName.setTextColor(if (hasUnread) primaryColor else textPrimary)

            // Avatar — skip in fast mode for performance
            if (!fastMode) {
                ivChatAvatar.visibility = View.VISIBLE
                val avatarUrl = if (chat.type == "direct" || chat.isSecret) {
                    val otherUser = getOrComputeOtherParticipant(chat, currentUsername, otherParticipantCache)
                    avatarCache[otherUser] ?: ""
                } else chat.avatarUrl
                try {
                    val currentTag = ivChatAvatar.tag as? String
                    if (avatarUrl.isNotEmpty() && avatarUrl != currentTag) {
                        ivChatAvatar.tag = avatarUrl
                        val sizePx = (48 * itemView.resources.displayMetrics.density).toInt()
                        ivChatAvatar.clearColorFilter()
                        com.bumptech.glide.Glide.with(itemView.context).load(avatarUrl)
                            .placeholder(R.drawable.ic_default_avatar).error(R.drawable.ic_default_avatar)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                            .override(sizePx, sizePx).circleCrop().into(ivChatAvatar)
                    } else if (avatarUrl.isEmpty()) {
                        if (currentTag != null) {
                            ivChatAvatar.tag = null
                            com.bumptech.glide.Glide.with(itemView.context).clear(ivChatAvatar)
                        }
                        try {
                            val currentTheme = ThemeStore.currentTheme()
                            ThemeUtils.applyDefaultAvatar(ivChatAvatar, currentTheme)
                        } catch (_: Exception) { ivChatAvatar.setImageResource(R.drawable.ic_default_avatar) }
                    }
                } catch (_: Exception) { ivChatAvatar.setImageResource(R.drawable.ic_default_avatar) }

                // Avatar border — Primary color outline on light themes only
                if (isLightTheme) {
                    val borderPx = (1.5f * itemView.resources.displayMetrics.density).toInt()
                    ivChatAvatar.borderWidth = borderPx
                    ivChatAvatar.borderColor = primaryColor
                } else {
                    ivChatAvatar.borderWidth = 0
                }
            } else {
                // Fast mode: completely hide avatar
                ivChatAvatar.visibility = View.GONE
                ivChatAvatar.tag = null
            }

            // Company badge
            tvCompanyBadge.isVisible = chat.companyId.isNotEmpty()

            // Message preview — use pre-computed value
            if (chat.isSecret) {
                tvChatType.text = itemView.context.getString(R.string.e2ee_verified)
                tvChatType.setTextColor(if (hasUnread) textPrimary else textSecondary)
                tvChatType.setTypeface(null, if (hasUnread) Typeface.BOLD else Typeface.NORMAL)
            } else if (chat.type == "conference") {
                tvChatType.text = itemView.context.getString(R.string.conference)
                tvChatType.setTextColor(if (hasUnread) textPrimary else textSecondary)
                tvChatType.setTypeface(null, if (hasUnread) Typeface.BOLD else Typeface.NORMAL)
            } else if (messagePreview != null) {
                if (tvChatType.text != messagePreview) {
                    tvChatType.text = messagePreview
                }
                tvChatType.setTextColor(if (hasUnread) textPrimary else textSecondary)
                tvChatType.setTypeface(null, Typeface.NORMAL)
            } else {
                val noMsgs = itemView.context.getString(R.string.no_messages)
                if (tvChatType.text != noMsgs) {
                    tvChatType.text = noMsgs
                }
                tvChatType.setTextColor(textSecondary)
            }

            // Unread badge
            if (chat.unreadCount > 0 && !selectionMode) {
                tvUnreadCount.isVisible = true
                val unreadText = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString()
                if (tvUnreadCount.text != unreadText) {
                    tvUnreadCount.text = unreadText
                }
                tvUnreadCount.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
                tvUnreadCount.setTextColor(if (ThemeUtils.isLight(primaryColor)) Color.BLACK else Color.WHITE)
            } else {
                tvUnreadCount.isVisible = false
            }

            // Conference lobby button
            val showLobby = chat.type == "conference" && !selectionMode
            if (btnEnterLobby.isVisible != showLobby) {
                btnEnterLobby.isVisible = showLobby
                if (showLobby) {
                    btnEnterLobby.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
                }
            }
            if (showLobby) {
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
            }

            val showMute = chat.isMuted && !selectionMode
            if (ivMuteIndicator.isVisible != showMute) {
                ivMuteIndicator.isVisible = showMute
            }

            // Selection mode
            if (cbChatSelect.isVisible != selectionMode) {
                cbChatSelect.isVisible = selectionMode
            }
            if (selectionMode) {
                cbChatSelect.isChecked = isSelected
                cbChatSelect.buttonTintList = android.content.res.ColorStateList.valueOf(primaryColor)
            }

            // Background
            val bgColor = when {
                isSelected -> selectedColor
                chat.unreadCount > 0 -> unreadColor
                else -> surfaceColor
            }
            if (cardView.cardBackgroundColor.defaultColor != bgColor) {
                cardView.setCardBackgroundColor(bgColor)
            }

            // Online status + last seen
            if (chat.type == "direct" && !chat.isSecret && !chat.id.startsWith("saved_messages_") && !fastMode) {
                val otherUser = getOrComputeOtherParticipant(chat, currentUsername, otherParticipantCache)
                if (otherUser.isNotEmpty()) {
                    val isOnline = onlineUsers.contains(otherUser)
                    statusIndicator.isVisible = true
                    val targetRes = if (isOnline) R.drawable.status_online_dot else R.drawable.status_offline_dot
                    if (statusIndicator.tag != targetRes) {
                        statusIndicator.setBackgroundResource(targetRes)
                        statusIndicator.tag = targetRes
                    }

                    if (!isOnline) {
                        val userInfo = allUsersMap[otherUser]
                        val lastSeenStr = userInfo?.lastSeenAt?.let { getTimeAgo(it.seconds * 1000, itemView.context) }
                        if (lastSeenStr != null) {
                            tvLastSeen.isVisible = true
                            if (tvLastSeen.text != lastSeenStr) {
                                tvLastSeen.text = lastSeenStr
                            }
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
                else -> synchronized(dayFormat) { dayFormat.format(Date(timestampMillis)) }
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
