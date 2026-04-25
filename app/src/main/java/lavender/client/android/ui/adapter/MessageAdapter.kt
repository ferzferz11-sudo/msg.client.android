package lavender.client.android.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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
    private val adminUsername: String = "",
    private val onMessageClick: (Message) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<Message, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    private val selectedPositions = mutableSetOf<Int>()
    private var selectionMode = false
    private var searchHighlight: String? = null

    fun setSearchHighlight(query: String?) {
        searchHighlight = query
        notifyItemRangeChanged(0, itemCount)
    }

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
        selectionMode = enabled
        if (!enabled) {
            selectedPositions.clear()
        }
        notifyItemRangeChanged(0, itemCount)
        if (!enabled) onSelectionChanged(0)
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
                        val isAdmin = currentUsername == adminUsername
                        if (isAdmin || isOutgoing) {
                            selectionMode = true
                            selectedPositions.add(currentPosition)
                            notifyItemRangeChanged(0, itemCount)
                            onSelectionChanged(selectedPositions.size)
                        }
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
        private val editedText: TextView = itemView.findViewById(R.id.editedText)
        private val readStatusIcon: ImageView = itemView.findViewById(R.id.readStatusIcon)
        private val replyQuoteContainer: View = itemView.findViewById(R.id.replyQuoteContainer)
        private val replyQuoteUser: TextView = itemView.findViewById(R.id.replyQuoteUser)
        private val replyQuoteText: TextView = itemView.findViewById(R.id.replyQuoteText)
        private val messageImageView: ImageView = itemView.findViewById(R.id.messageImageView)
        
        private val reactionsText: TextView = itemView.findViewById(R.id.reactionsText)
        
        fun bind(message: Message, isOutgoing: Boolean, isSelected: Boolean, shouldHideTime: Boolean, isConsecutive: Boolean, isSelectionMode: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
            val context = itemView.context
            val isGroup = this@MessageAdapter.isGroupChat
            
            messageText.text = message.text
            userText.text = message.user

            // 1. Visibility (Telegram Style)
            val canShowSenderInfo = isGroup && !isOutgoing && !isConsecutive
            userText.isVisible = canShowSenderInfo
            
            if (canShowSenderInfo) {
                avatarImageView.isVisible = true
                if (message.avatarUrl.isNotEmpty()) {
                    com.bumptech.glide.Glide.with(context).load(message.avatarUrl)
                        .placeholder(R.drawable.ic_default_avatar).into(avatarImageView)
                } else avatarImageView.setImageResource(R.drawable.ic_default_avatar)
            } else {
                avatarImageView.visibility = if (isOutgoing) View.GONE else View.INVISIBLE
            }

            // 2. Alignment
            val lp = messageContainer.layoutParams
            if (lp is RelativeLayout.LayoutParams) {
                lp.removeRule(RelativeLayout.ALIGN_PARENT_START)
                lp.removeRule(RelativeLayout.ALIGN_PARENT_END)
                lp.removeRule(RelativeLayout.END_OF)

                if (isOutgoing) {
                    lp.addRule(RelativeLayout.ALIGN_PARENT_END)
                } else {
                    lp.addRule(RelativeLayout.ALIGN_PARENT_START)
                    if (isSelectionMode) {
                        lp.addRule(RelativeLayout.END_OF, R.id.selectionIndicator)
                    }
                }
                
                val topMargin = if (isConsecutive) 2.dpToPx() else 8.dpToPx()
                val sideMargin = 40.dpToPx()
                lp.setMargins(if (isOutgoing) sideMargin else 0, topMargin, if (isOutgoing) 0 else sideMargin, 0)
                messageContainer.layoutParams = lp
            }

            // 3. Child Ordering & Background
            if (isOutgoing) {
                messageBubble.setBackgroundResource(R.drawable.bg_message_outgoing)
                messageBubble.gravity = android.view.Gravity.END
                messageText.gravity = android.view.Gravity.START // Text still starts from left but inside the bubble
                avatarImageView.visibility = View.GONE
                
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                messageText.setTextColor(if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data)
                timeText.setTextColor(ContextCompat.getColor(context, R.color.tg_time_outgoing))
            } else {
                messageBubble.setBackgroundResource(R.drawable.bg_message_incoming)
                messageBubble.gravity = android.view.Gravity.START
                messageText.gravity = android.view.Gravity.START
                if (canShowSenderInfo) {
                    avatarImageView.visibility = View.VISIBLE
                } else {
                    avatarImageView.visibility = View.INVISIBLE
                }
                
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                messageText.setTextColor(if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data)
                userText.setTextColor(ContextCompat.getColor(context, R.color.tg_incoming_name))
                timeText.setTextColor(ContextCompat.getColor(context, R.color.tg_time_incoming))
            }

            // 4. Status
            readStatusIcon.isVisible = isOutgoing
            if (isOutgoing) {
                val icon = if (message.isRead) R.drawable.ic_message_read else R.drawable.ic_message_sent
                val color = if (message.isRead) R.color.tg_read_check else R.color.tg_time_outgoing
                readStatusIcon.setImageResource(icon)
                readStatusIcon.setColorFilter(ContextCompat.getColor(context, color))
            }

            // 5. Content
            timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
            timeText.isVisible = !shouldHideTime

            val isLocation = message.text.startsWith("geo:")

            // Show edited label based on edited field
            editedText.text = context.getString(R.string.edited_label)
            editedText.isVisible = message.edited

            if (isLocation) {
                messageText.text = context.getString(R.string.location)
                messageText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_location, 0, 0, 0)
                messageText.compoundDrawablePadding = 8.dpToPx()
                
                // Theme-aware icon tint
                val iconColorAttr = if (isOutgoing) {
                    val typedValue = android.util.TypedValue()
                    if (context.theme.resolveAttribute(R.attr.isLightTheme, typedValue, true) && typedValue.data != 0) {
                        android.R.attr.textColorPrimary // Use dark text on light bubble in light theme
                    } else {
                        android.R.attr.textColorPrimary // Usually white on dark bubble in dark theme
                    }
                } else {
                    android.R.attr.textColorPrimary
                }
                
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(iconColorAttr, typedValue, true)
                val color = if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
                messageText.compoundDrawables[0]?.setTint(color)
            } else if (message.text.startsWith("File: ")) {
                val lines = message.text.split("\n")
                val fileName = if (lines.size > 1) lines[0].removePrefix("File: ") else message.text.removePrefix("File: ")
                val fileUrl = if (lines.size > 1) lines[1] else ""

                val fileIcon = when {
                    fileName.lowercase().endsWith(".pdf") -> R.drawable.ic_file_pdf
                    fileName.lowercase().endsWith(".zip") || fileName.lowercase().endsWith(".rar") || fileName.lowercase().endsWith(".7z") -> R.drawable.ic_file_archive
                    else -> R.drawable.ic_file
                }

                messageText.text = fileName
                messageText.setCompoundDrawablesWithIntrinsicBounds(fileIcon, 0, 0, 0)
                messageText.compoundDrawablePadding = 8.dpToPx()

                // Theme-aware icon tint for files
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                val color = if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
                messageText.compoundDrawables[0]?.setTint(color)

                // Make text clickable to download file
                messageText.setOnClickListener {
                    if (fileUrl.isNotEmpty()) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(fileUrl))
                        context.startActivity(intent)
                    }
                }
            } else {
                val text = message.text
                val highlight = searchHighlight
                if (!highlight.isNullOrEmpty() && text.contains(highlight, ignoreCase = true)) {
                    val spannable = android.text.SpannableString(message.text)
                    val start = text.lowercase().indexOf(highlight.lowercase())
                    if (start != -1) {
                        val end = start + highlight.length
                        spannable.setSpan(
                            android.text.style.BackgroundColorSpan(ContextCompat.getColor(context, R.color.lavender_mist_alpha)),
                            start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    messageText.text = spannable
                } else {
                    messageText.text = message.text
                }
                messageText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                messageText.setOnClickListener(null)
            }
            
            messageImageView.isVisible = message.imageUrl.isNotEmpty()
            if (message.imageUrl.isNotEmpty()) {
                com.bumptech.glide.Glide.with(context)
                    .load(message.imageUrl)
                    .transform(com.bumptech.glide.load.resource.bitmap.CenterCrop(), com.bumptech.glide.load.resource.bitmap.RoundedCorners(12.dpToPx()))
                    .into(messageImageView)
            }

            // 5.1 Reactions
            reactionsText.isVisible = message.reactions.isNotEmpty()
            if (message.reactions.isNotEmpty()) {
                val groupedReactions = message.reactions.groupBy { it.emoji }
                val reactionSummary = groupedReactions.entries.joinToString(" ") { 
                    "${it.key}${if (it.value.size > 1) " ${it.value.size}" else ""}" 
                }
                reactionsText.text = reactionSummary
            }

            replyQuoteContainer.isVisible = message.repliedToUser.isNotEmpty()
            if (message.repliedToUser.isNotEmpty()) {
                replyQuoteUser.text = message.repliedToUser
                replyQuoteText.text = message.repliedToText
            }

            // 6. Interaction
            selectionIndicator.isVisible = isSelectionMode
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
