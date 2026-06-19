package lavender.client.android.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
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
    private val onSelectionChanged: (Int) -> Unit = {}
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
        colorsInitialized = true
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
                item.chat, cachedTextPrimary, cachedTextSecondary, cachedSurfaceColor, cachedSelectedColor, cachedPrimaryColor, selectionMode, selectedIds.contains(item.chat.id), currentUsername
            )
            null -> {}
        }
    }

    override fun getItemCount(): Int = flatItems.size

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
        private val cbChatSelect: CheckBox = itemView.findViewById(R.id.cbChatSelect)
        private val cardView: com.google.android.material.card.MaterialCardView =
            itemView as com.google.android.material.card.MaterialCardView

        fun bind(chat: ChatInfo, textPrimary: Int, textSecondary: Int, surfaceColor: Int, selectedColor: Int, primaryColor: Int, selectionMode: Boolean, isSelected: Boolean, currentUsername: String) {
            tvChatName.text = chat.getDisplayName(currentUsername)
            tvChatName.setTextColor(textPrimary)

            if (chat.isSecret) {
                tvChatType.text = itemView.context.getString(R.string.e2ee_verified)
                tvChatType.setTextColor(textSecondary)
            } else if (chat.lastMessageText.isNotEmpty()) {
                tvChatType.text = chat.lastMessageText
                tvChatType.setTextColor(textSecondary)
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
                tvUnreadCount.setTextColor(if (ThemeUtils.isLight(primaryColor)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            } else {
                tvUnreadCount.isVisible = false
            }

            ivMuteIndicator.isVisible = chat.isMuted && !selectionMode

            // Selection mode
            cbChatSelect.isVisible = selectionMode
            cbChatSelect.isChecked = isSelected

            // Background: highlight if selected
            cardView.setCardBackgroundColor(if (isSelected) selectedColor else surfaceColor)

            // Click listeners
            if (selectionMode) {
                itemView.setOnClickListener { onChatClick(chat) }
                itemView.setOnLongClickListener(null)
            } else {
                itemView.setOnClickListener { onChatClick(chat) }
                itemView.setOnLongClickListener { view -> onChatLongClick(chat, view); true }
            }
        }
    }
}

// ======= Flat list items =======

sealed class FlatItem {
    data class SectionHeader(val section: Section, val count: Int) : FlatItem()
    data class ChatItem(val chat: ChatInfo) : FlatItem()
}
