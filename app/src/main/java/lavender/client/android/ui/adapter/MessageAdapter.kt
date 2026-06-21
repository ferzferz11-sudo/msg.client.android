package lavender.client.android.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.models.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(
    private val currentUsername: String,
    var isGroupChat: Boolean,
    var adminUsername: String = "",
    private val onMessageClick: (Message) -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
    private val onMessageLongClick: ((Message) -> Unit)? = null,
    private val chatId: String = "",
    private val onRetrySendMessage: ((Message) -> Unit)? = null,
) : ListAdapter<Message, MessageViewHolder>(MessageDiffCallback()) {

    private val selectedPositions = mutableSetOf<Int>()
    private var selectionMode = false
    private var searchHighlight: String? = null
    private var pinnedMessageIds = mutableSetOf<String>()

    fun setSearchHighlight(query: String?) {
        searchHighlight = query
        notifyItemRangeChanged(0, itemCount)
    }

    fun updatePinnedMessages(ids: Set<String>) {
        pinnedMessageIds = ids.toMutableSet()
        notifyItemRangeChanged(0, itemCount)
    }

    fun getSelectedMessages(): List<Message> {
        return selectedPositions.map { getItem(it) }
    }

    @Suppress("UNUSED")
    fun clearSelection() {
        val previousSelected = selectedPositions.toList()
        selectedPositions.clear()
        selectionMode = false
        previousSelected.forEach { notifyItemChanged(it) }
        onSelectionChanged(0)
    }

    fun toggleSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) {
            selectedPositions.clear()
        }
        notifyItemRangeChanged(0, itemCount)
        if (!enabled) onSelectionChanged(0)
    }

    fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
        } else {
            selectedPositions.add(position)
        }
        notifyItemChanged(position)
        onSelectionChanged(selectedPositions.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(
            itemView = view,
            currentUsername = currentUsername,
            isGroupChat = isGroupChat,
            onRetrySendMessage = onRetrySendMessage
        )
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val currentMessage = getItem(position)
        val previousMessage = if (position > 0) getItem(position - 1) else null

        val isOutgoing = currentMessage.user.trim().equals(currentUsername.trim(), ignoreCase = true)
        val isConsecutive = (previousMessage != null &&
                previousMessage.user.trim() == currentMessage.user.trim())

        val now = System.currentTimeMillis()
        val currentTs = if (currentMessage.timestamp > now) now else currentMessage.timestamp
        val previousTs = previousMessage?.let { if (it.timestamp > now) now else it.timestamp } ?: 0L

        val isSameMinute = previousMessage != null && (currentTs / 60000 == previousTs / 60000)

        val showDateSeparator = if (previousMessage == null) {
            true
        } else {
            val currentDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(currentTs))
            val previousDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(previousTs))
            currentDay != previousDay
        }

        holder.bind(
            message = currentMessage,
            isOutgoing = isOutgoing,
            isSelected = selectedPositions.contains(position),
            shouldHideTime = isConsecutive && isSameMinute,
            isConsecutive = isConsecutive,
            isSelectionMode = selectionMode,
            adapterPosition = position,
            showDateSeparator = showDateSeparator,
            onClick = {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos == RecyclerView.NO_POSITION) return@bind
                if (selectionMode) {
                    if (selectedPositions.contains(currentPos)) selectedPositions.remove(currentPos)
                    else selectedPositions.add(currentPos)
                    notifyItemChanged(currentPos)
                    onSelectionChanged(selectedPositions.size)
                } else {
                    onMessageClick(getItem(currentPos))
                }
            },
            onLongClick = {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos == RecyclerView.NO_POSITION) return@bind
                if (selectionMode) {
                    if (selectedPositions.contains(currentPos)) selectedPositions.remove(currentPos)
                    else selectedPositions.add(currentPos)
                    notifyItemChanged(currentPos)
                    onSelectionChanged(selectedPositions.size)
                } else {
                    onMessageLongClick?.invoke(getItem(currentPos))
                }
            },
            onMessageLongClick = onMessageLongClick,
            pinnedMessageIds = pinnedMessageIds,
            searchHighlight = searchHighlight,
            currentList = currentList,
            chatId = chatId
        )
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem == newItem
    }
}
