package lavender.client.android.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R

data class Reaction(
    val emoji: String,
    val count: Int = 1
)

data class OwlMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val isTyping: Boolean = false,
    val reactions: MutableList<Reaction> = mutableListOf()
)

class OwlMessageAdapter(
    private val onMessageClick: ((Int) -> Unit)? = null,
    private val onMessageLongClick: ((Int) -> Unit)? = null,
    private val onSelectionChanged: ((Int) -> Unit)? = null,
    private val onReactionClick: ((Int, String) -> Unit)? = null
) : RecyclerView.Adapter<OwlMessageAdapter.ViewHolder>() {

    private val messages = mutableListOf<OwlMessage>()
    private val selectedPositions = mutableSetOf<Int>()
    private var selectionMode = false
    private val quickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

    fun addMessage(msg: OwlMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(text: String) {
        if (messages.isNotEmpty()) {
            val last = messages[messages.size - 1]
            messages[messages.size - 1] = last.copy(text = text, isTyping = false)
            notifyItemChanged(messages.size - 1)
        }
    }

    fun showTyping() {
        hideTyping()
        messages.add(OwlMessage(text = "", isUser = false, isTyping = true))
        notifyItemInserted(messages.size - 1)
    }

    fun hideTyping() {
        val idx = messages.indexOfFirst { it.isTyping }
        if (idx >= 0) {
            messages.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    fun clear() {
        messages.clear()
        selectedPositions.clear()
        selectionMode = false
        notifyDataSetChanged()
    }

    fun isLastMessageUser(): Boolean {
        return messages.isNotEmpty() && messages.last().isUser
    }

    fun updateLastAssistantMessage(text: String) {
        val idx = messages.indexOfLast { !it.isUser && !it.isTyping && it.text.isNotEmpty() }
        if (idx >= 0) {
            messages[idx] = messages[idx].copy(text = text)
            notifyItemChanged(idx, PAYLOAD_TEXT)
        } else {
            addMessage(OwlMessage(text = text, isUser = false))
        }
    }

    fun removeLastMessage() {
        if (messages.isNotEmpty()) {
            messages.removeAt(messages.size - 1)
            notifyItemRemoved(messages.size)
        }
    }

    fun removeMessages(indices: List<Int>) {
        val toRemove = indices.sortedDescending()
        for (i in toRemove) {
            if (i < messages.size) {
                messages.removeAt(i)
                notifyItemRemoved(i)
            }
        }
        selectedPositions.clear()
        selectionMode = false
    }

    fun getMessageAt(position: Int): OwlMessage? {
        return if (position in messages.indices) messages[position] else null
    }

    fun getAllMessages(): List<OwlMessage> = messages.toList()

    fun getSelectedPositions(): Set<Int> = selectedPositions.toSet()

    fun toggleSelectionMode(enabled: Boolean) {
        val wasEnabled = selectionMode
        selectionMode = enabled
        if (!enabled) {
            selectedPositions.clear()
        }
        if (wasEnabled != enabled) {
            notifyItemRangeChanged(0, itemCount)
        }
        onSelectionChanged?.invoke(selectedPositions.size)
    }

    fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
        } else {
            selectedPositions.add(position)
        }
        notifyItemChanged(position)
        onSelectionChanged?.invoke(selectedPositions.size)
    }

    fun clearSelection() {
        val previousSelected = selectedPositions.toList()
        selectedPositions.clear()
        selectionMode = false
        previousSelected.forEach { notifyItemChanged(it) }
        onSelectionChanged?.invoke(0)
    }

    fun exitSelectionMode() {
        selectedPositions.clear()
        selectionMode = false
        notifyItemRangeChanged(0, itemCount)
        onSelectionChanged?.invoke(0)
    }

    fun addReaction(position: Int, emoji: String) {
        if (position !in messages.indices) return
        val msg = messages[position]
        val existing = msg.reactions.find { it.emoji == emoji }
        if (existing != null) {
            msg.reactions.remove(existing)
            msg.reactions.add(existing.copy(count = existing.count + 1))
        } else {
            msg.reactions.add(Reaction(emoji))
        }
        notifyItemChanged(position)
    }

    fun getQuickReactions(): List<String> = quickReactions

    companion object {
        const val PAYLOAD_TEXT = "text_update"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_owl_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]
        holder.bind(msg, selectionMode, selectedPositions.contains(position), position)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_TEXT)) {
            holder.updateText(messages[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userContainer: LinearLayout = itemView.findViewById(R.id.userMessageContainer)
        private val userText: TextView = itemView.findViewById(R.id.userMessageText)
        private val userReactionsContainer: LinearLayout = itemView.findViewById(R.id.userReactionsContainer)
        private val owlContainer: LinearLayout = itemView.findViewById(R.id.owlMessageContainer)
        private val owlText: TextView = itemView.findViewById(R.id.owlMessageText)
        private val owlReactionsContainer: LinearLayout = itemView.findViewById(R.id.owlReactionsContainer)
        private val typingContainer: LinearLayout = itemView.findViewById(R.id.typingContainer)
        private val selectionIndicator: ImageView = itemView.findViewById(R.id.selectionIndicator)

        fun bind(msg: OwlMessage, isSelectionMode: Boolean, isSelected: Boolean, position: Int) {
            // Selection indicator
            selectionIndicator?.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            if (isSelectionMode) {
                selectionIndicator?.isSelected = isSelected
            }

            // Highlight background when selected
            if (isSelectionMode && isSelected) {
                itemView.setBackgroundColor(0x33000000.toInt())
            } else {
                itemView.setBackgroundColor(0x00000000)
            }

            if (msg.isTyping) {
                userContainer.visibility = View.GONE
                owlContainer.visibility = View.GONE
                typingContainer.visibility = View.VISIBLE
                userReactionsContainer?.visibility = View.GONE
                owlReactionsContainer?.visibility = View.GONE
            } else if (msg.isUser) {
                userContainer.visibility = View.VISIBLE
                owlContainer.visibility = View.GONE
                typingContainer.visibility = View.GONE
                userText.text = msg.text
                bindReactions(msg, userReactionsContainer, position, true)
            } else {
                userContainer.visibility = View.GONE
                owlContainer.visibility = View.VISIBLE
                typingContainer.visibility = View.GONE
                owlText.text = msg.text
                bindReactions(msg, owlReactionsContainer, position, false)
            }

            // Click handlers
            val clickTarget = if (msg.isUser) userContainer else owlContainer

            clickTarget.setOnClickListener {
                if (!msg.isTyping) {
                    if (isSelectionMode) {
                        toggleSelection(bindingAdapterPosition)
                    } else {
                        onMessageClick?.invoke(bindingAdapterPosition)
                    }
                }
            }

            clickTarget.setOnLongClickListener {
                if (!msg.isTyping && bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onMessageLongClick?.invoke(bindingAdapterPosition)
                }
                true
            }
        }

        private fun bindReactions(msg: OwlMessage, container: LinearLayout?, position: Int, isUser: Boolean) {
            if (container == null) return
            if (msg.reactions.isEmpty()) {
                container.visibility = View.GONE
                return
            }
            container.visibility = View.VISIBLE
            container.removeAllViews()
            for (reaction in msg.reactions) {
                val tv = TextView(container.context).apply {
                    text = "${reaction.emoji} ${reaction.count}"
                    textSize = 12f
                    setPadding(8, 2, 8, 2)
                    background = container.context.getDrawable(R.drawable.bg_reactions)
                    setOnClickListener {
                        if (!selectionMode) {
                            onReactionClick?.invoke(position, reaction.emoji)
                        }
                    }
                }
                container.addView(tv, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 4.dpToPx(container.context)
                })
            }
        }

        fun updateText(msg: OwlMessage) {
            if (!msg.isUser && !msg.isTyping) {
                owlText.text = msg.text
            } else if (msg.isUser) {
                userText.text = msg.text
            }
        }

        private fun Int.dpToPx(context: android.content.Context): Int =
            (this * context.resources.displayMetrics.density).toInt()
    }
}
