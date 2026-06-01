package lavender.client.android.ui.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import lavender.client.android.R
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

data class Reaction(
    val emoji: String,
    val count: Int = 1
)

data class OwlMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val isTyping: Boolean = false,
    val replyToText: String = "",
    val replyToUser: String = "",
    val timestamp: Long = System.currentTimeMillis(),
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

    // Theme-based bubble colors
    var outgoingBg: Int = 0xFF2A2C6D.toInt()
        set(value) { field = value; notifyItemRangeChanged(0, itemCount) }
    var incomingBg: Int = 0xFF16173A.toInt()
        set(value) { field = value; notifyItemRangeChanged(0, itemCount) }
    var outgoingText: Int = 0xFFFFFFFF.toInt()
        set(value) { field = value; notifyItemRangeChanged(0, itemCount) }
    var incomingText: Int = 0xFFFFFFFF.toInt()
        set(value) { field = value; notifyItemRangeChanged(0, itemCount) }

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

    fun updateThemeColors() {
        val theme = ThemeStore.currentTheme()
        outgoingBg = ThemeUtils.parseSafeColor(theme.outgoingBubbleColor, 0xFF2A2C6D.toInt())
        incomingBg = ThemeUtils.parseSafeColor(theme.incomingBubbleColor, 0xFF16173A.toInt())
        outgoingText = ThemeUtils.parseSafeColor(theme.outgoingTextColor, 0xFFFFFFFF.toInt())
        incomingText = ThemeUtils.parseSafeColor(theme.incomingTextColor, 0xFFFFFFFF.toInt())
    }

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
        // New layout fields (matching item_message.xml structure)
        private val messageContainer: LinearLayout = itemView.findViewById(R.id.messageContainer)
        private val avatarImageView: ShapeableImageView = itemView.findViewById(R.id.avatarImageView)
        private val messageBubble: LinearLayout = itemView.findViewById(R.id.messageBubble)
        private val replyQuoteContainer: LinearLayout = itemView.findViewById(R.id.replyQuoteContainer)
        private val replyQuoteUser: TextView = itemView.findViewById(R.id.replyQuoteUser)
        private val replyQuoteText: TextView = itemView.findViewById(R.id.replyQuoteText)
        private val userText: TextView = itemView.findViewById(R.id.userText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val editedText: TextView = itemView.findViewById(R.id.editedText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val readStatusIcon: ImageView = itemView.findViewById(R.id.readStatusIcon)
        private val reactionsText: TextView = itemView.findViewById(R.id.reactionsText)
        private val selectionIndicator: ImageView = itemView.findViewById(R.id.selectionIndicator)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)

        // Typing indicator (custom for OWL)
        private val typingContainer: LinearLayout? = itemView.findViewById(R.id.typingContainer)

        fun bind(msg: OwlMessage, isSelectionMode: Boolean, isSelected: Boolean, position: Int) {
            // Selection indicator
            selectionIndicator?.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            if (isSelectionMode) {
                selectionIndicator?.isSelected = isSelected
            }

            // Highlight bubble when selected
            if (isSelectionMode && isSelected) {
                messageBubble?.alpha = 0.6f
            } else {
                messageBubble?.alpha = 1.0f
            }

            if (msg.isTyping) {
                // Show typing indicator
                messageContainer?.visibility = View.GONE
                typingContainer?.visibility = View.VISIBLE
                return
            }

            messageContainer?.visibility = View.VISIBLE
            typingContainer?.visibility = View.GONE

            // Determine if outgoing (user) or incoming (OWL)
            val isOutgoing = msg.isUser
            val bubbleColor = if (isOutgoing) outgoingBg else incomingBg
            val textColor = if (isOutgoing) outgoingText else incomingText

            // Apply bubble color
            messageBubble?.backgroundTintList = ColorStateList.valueOf(bubbleColor)

            // Position: outgoing = right, incoming = left
            val layoutParams = messageContainer?.layoutParams as? RelativeLayout.LayoutParams
            if (isOutgoing) {
                layoutParams?.addRule(RelativeLayout.ALIGN_PARENT_END)
                layoutParams?.removeRule(RelativeLayout.ALIGN_PARENT_START)
                messageContainer?.layoutParams = layoutParams

                // Hide avatar for outgoing
                avatarImageView?.visibility = View.GONE

                // Align bubble content to end
                (messageBubble?.parent as? LinearLayout)?.gravity = android.view.Gravity.END
            } else {
                layoutParams?.addRule(RelativeLayout.ALIGN_PARENT_START)
                layoutParams?.removeRule(RelativeLayout.ALIGN_PARENT_END)
                messageContainer?.layoutParams = layoutParams

                // Show avatar for incoming (OWL)
                avatarImageView?.visibility = View.VISIBLE
                // Set OWL avatar
                avatarImageView?.setImageResource(R.drawable.ic_notification_logo)
                // Tint avatar background with theme primary
                try {
                    val theme = ThemeStore.currentTheme()
                    val primaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, 0xFF6200EE.toInt())
                    avatarImageView?.setColorFilter(primaryColor)
                } catch (_: Exception) {}

                (messageBubble?.parent as? LinearLayout)?.gravity = android.view.Gravity.START
            }

            // Reply quote
            if (msg.replyToText.isNotEmpty()) {
                replyQuoteContainer?.visibility = View.VISIBLE
                replyQuoteUser?.text = if (msg.replyToUser.isNotEmpty()) msg.replyToUser else if (isOutgoing) "Вы" else "OWL"
                replyQuoteText?.text = msg.replyToText
            } else {
                replyQuoteContainer?.visibility = View.GONE
            }

            // Username (hidden in OWL 1-on-1)
            userText?.visibility = View.GONE

            // Message text
            messageText?.text = msg.text
            messageText?.setTextColor(textColor)

            // Time
            timeText?.text = formatTime(msg.timestamp)
            timeText?.setTextColor(textColor and 0x80FFFFFF.toInt()) // semi-transparent

            // Edited indicator (hidden in OWL)
            editedText?.visibility = View.GONE

            // Read status (hidden for incoming, check for outgoing)
            if (isOutgoing) {
                readStatusIcon?.visibility = View.VISIBLE
                readStatusIcon?.setImageResource(R.drawable.ic_done_all)
                readStatusIcon?.imageTintList = ColorStateList.valueOf(textColor)
            } else {
                readStatusIcon?.visibility = View.GONE
            }

            // Reactions
            bindReactions(msg)

            // Click handlers
            messageBubble?.setOnClickListener {
                if (!msg.isTyping) {
                    if (isSelectionMode) {
                        toggleSelection(bindingAdapterPosition)
                    } else {
                        onMessageClick?.invoke(bindingAdapterPosition)
                    }
                }
            }

            messageBubble?.setOnLongClickListener {
                if (!msg.isTyping && bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    onMessageLongClick?.invoke(bindingAdapterPosition)
                }
                true
            }
        }

        private fun bindReactions(msg: OwlMessage) {
            if (msg.reactions.isEmpty()) {
                reactionsText?.visibility = View.GONE
                return
            }
            reactionsText?.visibility = View.VISIBLE
            val text = msg.reactions.joinToString(" ") { "${it.emoji} ${it.count}" }
            reactionsText?.text = text
        }

        fun updateText(msg: OwlMessage) {
            messageText?.text = msg.text
        }

        private fun formatTime(timestamp: Long): String {
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(timestamp))
        }

        private fun Int.dpToPx(context: android.content.Context): Int =
            (this * context.resources.displayMetrics.density).toInt()
    }
}
