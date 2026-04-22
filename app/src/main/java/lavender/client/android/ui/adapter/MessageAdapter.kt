package lavender.client.android.ui.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.models.Message
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val currentUsername: String,
    private val isGroupChat: Boolean,
    private val onMessageClick: (Message) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<Message, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    private val selectedPositions = mutableSetOf<Int>()
    private var selectionMode = false

    fun getSelectedMessages(): List<Message> {
        return selectedPositions.map { getItem(it) }
    }

    fun clearSelection() {
        val previousSelected = selectedPositions.toList()
        selectedPositions.clear()
        selectionMode = false
        previousSelected.forEach { notifyItemChanged(it) }
        onSelectionChanged(0)
    }

    fun toggleSelectionMode(enabled: Boolean) {
        if (!enabled) {
            clearSelection()
        } else {
            selectionMode = true
            notifyItemRangeChanged(0, itemCount)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val currentMessage = getItem(position)
        val previousMessage = if (position > 0) getItem(position - 1) else null

        val isOutgoing = currentMessage.user.trim().equals(currentUsername.trim(), ignoreCase = true)
        val isConsecutive = previousMessage != null && 
                           previousMessage.user.trim() == currentMessage.user.trim()
        val isSameMinute = previousMessage != null && 
                          (currentMessage.timestamp / 60000 == previousMessage.timestamp / 60000)

        holder.bind(
            message = currentMessage,
            isOutgoing = isOutgoing,
            isSelected = selectedPositions.contains(position),
            shouldHideTime = isConsecutive && isSameMinute,
            isConsecutive = isConsecutive,
            isSelectionMode = selectionMode,
            onClick = {
                val currentPosition = holder.bindingAdapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@bind
                if (selectionMode) {
                    if (selectedPositions.contains(currentPosition)) selectedPositions.remove(currentPosition)
                    else selectedPositions.add(currentPosition)
                    notifyItemChanged(currentPosition)
                    onSelectionChanged(selectedPositions.size)
                } else onMessageClick(currentMessage)
            },
            onLongClick = {
                if (!selectionMode) {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        selectionMode = true
                        selectedPositions.add(currentPosition)
                        notifyItemRangeChanged(0, itemCount)
                        onSelectionChanged(selectedPositions.size)
                    }
                }
            }
        )
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageContainer: LinearLayout = itemView.findViewById(R.id.messageContainer)
        val messageBubble: LinearLayout = itemView.findViewById(R.id.messageBubble)
        private val selectionIndicator: ImageView = itemView.findViewById(R.id.selectionIndicator)
        private val avatarImageView: de.hdodenhof.circleimageview.CircleImageView = itemView.findViewById(R.id.avatarImageView)
        private val userText: TextView = itemView.findViewById(R.id.userText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val readStatusIcon: ImageView = itemView.findViewById(R.id.readStatusIcon)
        private val replyQuoteContainer: View = itemView.findViewById(R.id.replyQuoteContainer)
        private val replyQuoteUser: TextView = itemView.findViewById(R.id.replyQuoteUser)
        private val replyQuoteText: TextView = itemView.findViewById(R.id.replyQuoteText)
        private val messageImageView: ImageView = itemView.findViewById(R.id.messageImageView)
        
        fun bind(message: Message, isOutgoing: Boolean, isSelected: Boolean, shouldHideTime: Boolean, isConsecutive: Boolean, isSelectionMode: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
            val context = itemView.context
            val isGroup = this@MessageAdapter.isGroupChat
            
            messageText.text = message.text
            userText.text = message.user

            // 1. Visibility (Telegram Style)
            val canShowSenderInfo = isGroup && !isOutgoing && !isConsecutive
            userText.visibility = if (canShowSenderInfo) View.VISIBLE else View.GONE
            
            if (canShowSenderInfo) {
                avatarImageView.visibility = View.VISIBLE
                if (message.avatarUrl.isNotEmpty()) {
                    com.bumptech.glide.Glide.with(context).load(message.avatarUrl)
                        .placeholder(R.drawable.ic_default_avatar).into(avatarImageView)
                } else avatarImageView.setImageResource(R.drawable.ic_default_avatar)
            } else {
                avatarImageView.visibility = if (isOutgoing) View.GONE else View.INVISIBLE
            }

            // 2. Alignment
            val containerParams = messageContainer.layoutParams as LinearLayout.LayoutParams
            containerParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
            containerParams.gravity = if (isOutgoing) Gravity.END else Gravity.START
            
            val topMargin = if (isConsecutive) 2.dpToPx() else 8.dpToPx()
            val sideMargin = 40.dpToPx()
            containerParams.setMargins(if (isOutgoing) sideMargin else 0, topMargin, if (isOutgoing) 0 else sideMargin, 0)
            messageContainer.layoutParams = containerParams

            // 3. Child Ordering & Background
            if (isOutgoing) {
                messageBubble.setBackgroundResource(R.drawable.bg_message_outgoing)
                if (messageContainer.getChildAt(messageContainer.childCount - 1) != avatarImageView) {
                    messageContainer.removeAllViews()
                    messageContainer.addView(selectionIndicator)
                    messageContainer.addView(messageBubble)
                    messageContainer.addView(avatarImageView)
                }
                
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                messageText.setTextColor(if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data)
                timeText.setTextColor(ContextCompat.getColor(context, R.color.tg_time_outgoing))
            } else {
                messageBubble.setBackgroundResource(R.drawable.bg_message_incoming)
                if (messageContainer.getChildAt(messageContainer.childCount - 1) != messageBubble) {
                    messageContainer.removeAllViews()
                    messageContainer.addView(selectionIndicator)
                    messageContainer.addView(avatarImageView)
                    messageContainer.addView(messageBubble)
                }
                
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                messageText.setTextColor(if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data)
                userText.setTextColor(ContextCompat.getColor(context, R.color.tg_incoming_name))
                timeText.setTextColor(ContextCompat.getColor(context, R.color.tg_time_incoming))
            }

            // 4. Status
            readStatusIcon.visibility = if (isOutgoing) View.VISIBLE else View.GONE
            if (isOutgoing) {
                val icon = if (message.isRead) R.drawable.ic_message_read else R.drawable.ic_message_sent
                val color = if (message.isRead) R.color.tg_read_check else R.color.tg_time_outgoing
                readStatusIcon.setImageResource(icon)
                readStatusIcon.setColorFilter(ContextCompat.getColor(context, color))
            }

            // 5. Content
            timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
            timeText.visibility = if (shouldHideTime) View.GONE else View.VISIBLE
            
            messageImageView.visibility = if (message.imageUrl.isNotEmpty()) View.VISIBLE else View.GONE
            if (message.imageUrl.isNotEmpty()) {
                com.bumptech.glide.Glide.with(context).load(message.imageUrl).into(messageImageView)
            }

            replyQuoteContainer.visibility = if (message.repliedToUser.isNotEmpty()) View.VISIBLE else View.GONE
            if (message.repliedToUser.isNotEmpty()) {
                replyQuoteUser.text = message.repliedToUser
                replyQuoteText.text = message.repliedToText
            }

            // 6. Interaction
            selectionIndicator.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            selectionIndicator.setImageResource(if (isSelected) R.drawable.ic_checked else R.drawable.ic_unchecked)
            messageBubble.alpha = if (isSelected) 0.6f else 1.0f
            itemView.setBackgroundColor(if (isSelected) ContextCompat.getColor(context, R.color.lavender_mist_alpha) else android.graphics.Color.TRANSPARENT)

            messageBubble.setOnClickListener { onClick() }
            messageBubble.setOnLongClickListener { onLongClick(); true }
        }

        private fun Int.dpToPx(): Int = (this * itemView.resources.displayMetrics.density).toInt()
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem == newItem
    }
}
