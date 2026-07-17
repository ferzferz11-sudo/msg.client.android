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
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import lavender.client.android.R
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.ui.chatlist.SectionItem
import lavender.client.android.ui.chatlist.Section
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

/**
 * ChatAdapter — адаптер с поддержкой секций (Pinned/Favorites/All Chats) и режима выбора.
 *
 * ViewType:
 * - SECTION_HEADER — заголовок секции
 * - CHAT_ITEM — обычный чат
 * - FAVORITES — избранное
 *
 * Selection Mode:
 * - При включении показывает CheckBox на каждом элементе
 * - Множественный выбор через тап (toggle)
 * - Визуальная подсветка выбранных элементов
 *
 * DiffUtil:
 * - setSections() использует DiffUtil для анимированных обновлений
 * - Секции идентифицируются по Section enum
 * - Чаты идентифицируются по chat.id
 */
class ChatAdapter(
    private val scope: CoroutineScope,
    private val currentUsername: String,
    private val onChatClick: (ChatInfo) -> Unit,
    private val onChatLongClick: (ChatInfo, View) -> Unit,
    private val onSelectionChanged: (Int) -> Unit = {},
    private var onlineUsers: List<String> = emptyList(),
    private var allUsers: List<lavender.client.android.data.proto.UserInfoProto> = emptyList()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SECTION_HEADER = 0
        private const val TYPE_CHAT_ITEM = 1
    }

    private var sections: List<SectionItem> = emptyList()
    private var flatItems: List<FlatItem> = emptyList()
    private var currentFilter: String = ""

    // Selection state
    private var selectionMode = false
    private val selectedIds = mutableSetOf<String>()

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
        // Selection highlight: primary color with alpha
        cachedSelectedColor = Color.argb(48, Color.red(cachedPrimaryColor), Color.green(cachedPrimaryColor), Color.blue(cachedPrimaryColor))
        // Unread highlight: primary color with subtle alpha
        cachedUnreadColor = Color.argb(40, Color.red(cachedPrimaryColor), Color.green(cachedPrimaryColor), Color.blue(cachedPrimaryColor))
        colorsInitialized = true
    }

    fun updateTheme() {
        colorsInitialized = false
        notifyItemRangeChanged(0, itemCount)
    }

    // ======= Public API =======

    /**
     * Update sections with DiffUtil for animated changes.
     * Calculates minimal diff and dispatches insert/remove/move/update operations.
     */
    fun setSections(newSections: List<SectionItem>) {
        sections = newSections
        val newFlat = buildFlatList(newSections)
        val diff = DiffUtil.calculateDiff(ChatListDiffCallback(flatItems, newFlat))
        flatItems = newFlat
        diff.dispatchUpdatesTo(this)
    }

    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    fun getSelectedChats(): List<ChatInfo> {
        return flatItems.mapNotNull { item ->
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
        notifyDataSetChanged()
    }

    fun toggleSelection(chatId: String) {
        if (selectedIds.contains(chatId)) {
            selectedIds.remove(chatId)
        } else {
            selectedIds.add(chatId)
        }
        onSelectionChanged(selectedIds.size)
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedIds.clear()
        selectionMode = false
        onSelectionChanged(0)
        notifyDataSetChanged()
    }

    fun isSelectionMode(): Boolean = selectionMode

    // ======= Internal =======

    private fun buildFlatList(sections: List<SectionItem>): List<FlatItem> {
        val result = mutableListOf<FlatItem>()
        for (section in sections) {
            for (chat in section.chats) {
                result.add(FlatItem.ChatItem(chat))
            }
        }
        return result
    }

    // ======= DiffUtil =======

    class ChatListDiffCallback(
        private val oldList: List<FlatItem>,
        private val newList: List<FlatItem>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
            val old = oldList[oldPos]
            val new = newList[newPos]
            return when {
                old is FlatItem.SectionHeader && new is FlatItem.SectionHeader ->
                    old.section == new.section
                old is FlatItem.ChatItem && new is FlatItem.ChatItem ->
                    old.chat.id == new.chat.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
            return oldList[oldPos] == newList[newPos]
        }
    }

    // ======= RecyclerView.Adapter =======

    override fun getItemViewType(position: Int): Int {
        return when (flatItems.getOrNull(position)) {
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
        when (val item = flatItems.getOrNull(position)) {
            is FlatItem.SectionHeader -> (holder as SectionHeaderViewHolder).bind(item)
            is FlatItem.ChatItem -> (holder as ChatViewHolder).bind(
                item.chat, cachedTextPrimary, cachedTextSecondary, cachedSurfaceColor, cachedSelectedColor, cachedUnreadColor, cachedPrimaryColor, selectionMode, selectedIds.contains(item.chat.id), currentUsername, onlineUsers.toSet(), allUsers
            )
            null -> {}
        }
    }

    override fun getItemCount(): Int = flatItems.size

    fun updateOnlineUsers(users: List<String>) {
        onlineUsers = users
        notifyDataSetChanged()
    }

    fun updateAllUsers(users: List<lavender.client.android.data.proto.UserInfoProto>) {
        allUsers = users
        notifyDataSetChanged()
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
        private val tvConferenceBadge: TextView = itemView.findViewById(R.id.tvConferenceBadge)
        private val btnEnterLobby: ImageView = itemView.findViewById(R.id.btnEnterLobby)
        private val cardView: com.google.android.material.card.MaterialCardView =
            itemView as com.google.android.material.card.MaterialCardView

        fun bind(chat: ChatInfo, textPrimary: Int, textSecondary: Int, surfaceColor: Int, selectedColor: Int, unreadColor: Int, primaryColor: Int, selectionMode: Boolean, isSelected: Boolean, currentUsername: String, onlineUsers: Set<String>, allUsers: List<lavender.client.android.data.proto.UserInfoProto>) {
            val hasUnread = chat.unreadCount > 0
            if (hasUnread) android.util.Log.d("ChatAdapter", "BIND UNREAD: ${chat.name} unreadCount=${chat.unreadCount}")
            tvChatName.text = chat.getDisplayName(currentUsername)
            tvChatName.setTextColor(if (hasUnread) primaryColor else textPrimary)
            tvChatName.setTypeface(null, if (hasUnread) Typeface.BOLD else Typeface.NORMAL)

            // Company badge
            tvCompanyBadge.isVisible = chat.companyId.isNotEmpty()

            // Conference badge
            tvConferenceBadge.isVisible = chat.type == "conference"

            if (chat.isSecret) {
                tvChatType.text = itemView.context.getString(R.string.e2ee_verified)
                tvChatType.setTextColor(if (hasUnread) textPrimary else textSecondary)
                tvChatType.setTypeface(null, if (hasUnread) Typeface.BOLD else Typeface.NORMAL)
            } else if (chat.type == "conference") {
                tvChatType.text = itemView.context.getString(R.string.conference)
                tvChatType.setTextColor(if (hasUnread) textPrimary else textSecondary)
                tvChatType.setTypeface(null, if (hasUnread) Typeface.BOLD else Typeface.NORMAL)
            } else if (chat.lastMessageText.isNotEmpty()) {
                tvChatType.text = translateMediaPreview(chat.lastMessageText)
                tvChatType.setTextColor(if (hasUnread) textPrimary else textSecondary)
                tvChatType.setTypeface(null, if (hasUnread) Typeface.NORMAL else Typeface.NORMAL)
            } else {
                tvChatType.text = itemView.context.getString(R.string.no_messages)
                tvChatType.setTextColor(textSecondary)
            }

            // Unread badge — styled by theme
            if (chat.unreadCount > 0 && !selectionMode) {
                tvUnreadCount.isVisible = true
                tvUnreadCount.text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString()
                // Badge background uses primary color
                tvUnreadCount.backgroundTintList = android.content.res.ColorStateList.valueOf(primaryColor)
                // Text color: white for dark primary, black for light primary
                tvUnreadCount.setTextColor(if (ThemeUtils.isLight(primaryColor)) Color.BLACK else Color.WHITE)
            } else {
                tvUnreadCount.isVisible = false
            }

            // Conference lobby button — visible for conference chats, hidden in selection mode
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

            // Selection mode — themed checkbox
            cbChatSelect.isVisible = selectionMode
            cbChatSelect.isChecked = isSelected
            cbChatSelect.buttonTintList = android.content.res.ColorStateList.valueOf(primaryColor)

            // Background: highlight if selected, unread tint if it has unread messages
            val bgColor = when {
                isSelected -> selectedColor
                chat.unreadCount > 0 -> unreadColor
                else -> surfaceColor
            }
            cbChatSelect.backgroundTintList = android.content.res.ColorStateList.valueOf(bgColor)
            cardView.setCardBackgroundColor(bgColor)

            // Online status + last seen — direct chats only
            if (chat.type == "direct" && !chat.isSecret && !chat.id.startsWith("favorites_")) {
                val otherUser = getOtherParticipant(chat, currentUsername)
                if (otherUser.isNotEmpty()) {
                    val isOnline = onlineUsers.contains(otherUser)
                    statusIndicator.isVisible = true
                    statusIndicator.setBackgroundResource(if (isOnline) R.drawable.status_online_dot else R.drawable.status_offline_dot)

                    if (!isOnline) {
                        val userInfo = allUsers.firstOrNull { it.username == otherUser }
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

        private fun getOtherParticipant(chat: ChatInfo, currentUsername: String): String {
            return try {
                val arr = org.json.JSONArray(chat.participants)
                for (i in 0 until arr.length()) {
                    val p = arr.getString(i)
                    if (p != currentUsername) return p
                }
                ""
            } catch (e: Exception) {
                ""
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
                else -> {
                    val format = java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.getDefault())
                    format.format(java.util.Date(timestampMillis))
                }
            }
        }
    }
}

// ======= Flat list items =======

sealed class FlatItem {
    data class SectionHeader(val section: Section, val count: Int) : FlatItem()
    data class ChatItem(val chat: ChatInfo) : FlatItem()
}
