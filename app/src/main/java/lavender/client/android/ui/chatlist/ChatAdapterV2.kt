package lavender.client.android.ui.chatlist

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.R
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.models.Message
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

/**
 * ChatAdapterV2 — адаптер с поддержкой секций (Pinned/Favorites/All Chats).
 *
 * ViewType:
 * - SECTION_HEADER — заголовок секции
 * - CHAT_ITEM — обычный чат
 * - FAVORITES — избранное
 *
 * Использует DiffUtil для эффективных обновлений.
 */
class ChatAdapterV2(
    private val scope: CoroutineScope,
    private val onChatClick: (ChatInfo) -> Unit,
    private val onChatLongClick: (ChatInfo, View) -> Unit,
    private val onSelectionChanged: (Int) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SECTION_HEADER = 0
        private const val TYPE_CHAT_ITEM = 1
        private const val TYPE_FAVORITES = 2
    }

    private var sections: List<SectionItem> = emptyList()
    private var flatItems: List<FlatItem> = emptyList()
    private var currentFilter: String = ""

    // Theme colors
    private var cachedPrimaryColor: Int = 0
    private var cachedTextPrimary: Int = 0
    private var cachedTextSecondary: Int = 0
    private var cachedSurfaceColor: Int = 0
    private var colorsInitialized = false

    private fun initColors(view: View) {
        if (colorsInitialized) return
        val theme = ThemeStore.currentTheme()
        cachedPrimaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
        cachedTextPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        cachedTextSecondary = ThemeUtils.parseSafeColor(theme.onSurfaceColor, Color.LTGRAY)
        cachedSurfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
        colorsInitialized = true
    }

    fun setSections(newSections: List<SectionItem>) {
        sections = newSections
        rebuildFlatList()
        notifyDataSetChanged()
    }

    private fun rebuildFlatList() {
        val result = mutableListOf<FlatItem>()
        for (section in sections) {
            // Section header
            result.add(FlatItem.SectionHeader(section.section, section.chats.size))
            // Chats in section
            for (chat in section.chats) {
                if (chat.type == "favorites") {
                    result.add(FlatItem.FavoritesItem(chat))
                } else {
                    result.add(FlatItem.ChatItem(chat))
                }
            }
        }
        flatItems = result
    }

    override fun getItemViewType(position: Int): Int {
        return when (flatItems.getOrNull(position)) {
            is FlatItem.SectionHeader -> TYPE_SECTION_HEADER
            is FlatItem.ChatItem -> TYPE_CHAT_ITEM
            is FlatItem.FavoritesItem -> TYPE_FAVORITES
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
            TYPE_FAVORITES -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat, parent, false)
                FavoritesViewHolder(view, onChatClick, onChatLongClick)
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
            is FlatItem.ChatItem -> (holder as ChatViewHolder).bind(item.chat)
            is FlatItem.FavoritesItem -> (holder as FavoritesViewHolder).bind(item.chat)
            null -> {}
        }
    }

    override fun getItemCount(): Int = flatItems.size

    fun filter(query: String) {
        currentFilter = query.lowercase()
        // Rebuild sections with filter applied
        val filteredSections = sections.map { section ->
            val filteredChats = if (currentFilter.isEmpty()) {
                section.chats
            } else {
                section.chats.filter { chat ->
                    chat.name.lowercase().contains(currentFilter) ||
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
                Section.FAVORITES -> itemView.context.getString(R.string.section_favorites)
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
        private val ivMuteIndicator: android.widget.ImageView = itemView.findViewById(R.id.ivMuteIndicator)
        private val cardView: com.google.android.material.card.MaterialCardView =
            itemView as com.google.android.material.card.MaterialCardView

        fun bind(chat: ChatInfo) {
            initColors(itemView)
            tvChatName.text = chat.name
            tvChatName.setTextColor(cachedTextPrimary)

            if (chat.lastMessageText.isNotEmpty()) {
                tvChatType.text = chat.lastMessageText
                tvChatType.setTextColor(cachedTextSecondary)
            } else {
                tvChatType.text = itemView.context.getString(R.string.no_messages)
                tvChatType.setTextColor(cachedTextSecondary)
            }

            tvUnreadCount.isVisible = chat.unreadCount > 0
            if (chat.unreadCount > 0) {
                tvUnreadCount.text = chat.unreadCount.toString()
            }

            ivMuteIndicator.isVisible = chat.isMuted

            cardView.setCardBackgroundColor(cachedSurfaceColor)

            itemView.setOnClickListener { onChatClick(chat) }
            itemView.setOnLongClickListener { view -> onChatLongClick(chat, view); true }
        }

        private fun initColors(view: View) {
            if (colorsInitialized) return
            val theme = ThemeStore.currentTheme()
            cachedPrimaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
            cachedTextPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
            cachedTextSecondary = ThemeUtils.parseSafeColor(theme.onSurfaceColor, Color.LTGRAY)
            cachedSurfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
            colorsInitialized = true
        }

        private var cachedPrimaryColor: Int = 0
        private var cachedTextPrimary: Int = 0
        private var cachedTextSecondary: Int = 0
        private var cachedSurfaceColor: Int = 0
        private var colorsInitialized = false
    }

    class FavoritesViewHolder(
        itemView: View,
        private val onChatClick: (ChatInfo) -> Unit,
        private val onChatLongClick: (ChatInfo, View) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvChatName: TextView = itemView.findViewById(R.id.tvChatName)
        private val tvChatType: TextView = itemView.findViewById(R.id.tvChatType)
        private val cardView: com.google.android.material.card.MaterialCardView =
            itemView as com.google.android.material.card.MaterialCardView

        fun bind(chat: ChatInfo) {
            initColors(itemView)
            tvChatName.text = itemView.context.getString(R.string.favorites)
            tvChatName.setTextColor(cachedTextPrimary)
            tvChatType.text = itemView.context.getString(R.string.favorites_description)
            tvChatType.setTextColor(cachedTextSecondary)
            cardView.setCardBackgroundColor(cachedSurfaceColor)
            itemView.setOnClickListener { onChatClick(chat) }
        }

        private fun initColors(view: View) {
            if (colorsInitialized) return
            val theme = ThemeStore.currentTheme()
            cachedPrimaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
            cachedTextPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
            cachedTextSecondary = ThemeUtils.parseSafeColor(theme.onSurfaceColor, Color.LTGRAY)
            cachedSurfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
            colorsInitialized = true
        }

        private var cachedPrimaryColor: Int = 0
        private var cachedTextPrimary: Int = 0
        private var cachedTextSecondary: Int = 0
        private var cachedSurfaceColor: Int = 0
        private var colorsInitialized = false
    }
}

// ======= Flat list items =======

sealed class FlatItem {
    data class SectionHeader(val section: Section, val count: Int) : FlatItem()
    data class ChatItem(val chat: ChatInfo) : FlatItem()
    data class FavoritesItem(val chat: ChatInfo) : FlatItem()
}
