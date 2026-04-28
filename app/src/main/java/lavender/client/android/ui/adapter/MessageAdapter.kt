package lavender.client.android.ui.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
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
    var isGroupChat: Boolean,
    var adminUsername: String = "",
    private val onMessageClick: (Message) -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
    private val onMessageLongClick: ((Message) -> Unit)? = null,
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val currentMessage = getItem(position)
        val previousMessage = if (position > 0) getItem(position - 1) else null

        val isOutgoing = currentMessage.user.trim().equals(currentUsername.trim(), ignoreCase = true)
        val isConsecutive = (previousMessage != null &&
                previousMessage.user.trim() == currentMessage.user.trim())
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
                if (selectionMode) {
                    val currentPosition = holder.bindingAdapterPosition
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        if (selectedPositions.contains(currentPosition)) selectedPositions.remove(currentPosition)
                        else selectedPositions.add(currentPosition)
                        notifyItemChanged(currentPosition)
                        onSelectionChanged(selectedPositions.size)
                    }
                } else {
                    onMessageLongClick?.invoke(currentMessage)
                }
            },
            onMessageLongClick = onMessageLongClick
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
        private val audioMessageView: lavender.client.android.ui.audio.AudioMessageView = itemView.findViewById(R.id.audioMessageView)
        
        private val reactionsText: TextView = itemView.findViewById(R.id.reactionsText)
        
        fun bind(message: Message, isOutgoing: Boolean, isSelected: Boolean, shouldHideTime: Boolean, isConsecutive: Boolean, isSelectionMode: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onMessageLongClick: ((Message) -> Unit)? = null) {
            val context = itemView.context
            val isGroup = this@MessageAdapter.isGroupChat
            val theme = lavender.client.android.ui.ThemeManager.getCurrentTheme()
            
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
                lp.removeRule(RelativeLayout.ALIGN_PARENT_LEFT)
                lp.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                lp.removeRule(RelativeLayout.END_OF)
                lp.removeRule(RelativeLayout.RIGHT_OF)

                if (isOutgoing) {
                    lp.addRule(RelativeLayout.ALIGN_PARENT_END)
                    lp.marginStart = 40.dpToPx()
                    lp.marginEnd = 0
                } else {
                    lp.addRule(RelativeLayout.ALIGN_PARENT_START)
                    if (isSelectionMode) {
                        lp.addRule(RelativeLayout.END_OF, R.id.selectionIndicator)
                    }
                    lp.marginStart = 0
                    lp.marginEnd = 40.dpToPx()
                }
                
                lp.topMargin = if (isConsecutive) 2.dpToPx() else 8.dpToPx()
                messageContainer.layoutParams = lp
            }

            // 3. Child Ordering & Background
            val surfaceColor: Int
            val textPrimaryColor: Int
            val textSecondaryColor: Int

            if (theme != null) {
                val surface = try { theme.surfaceColor.toColorInt() } catch (_: Exception) { Color.LTGRAY }
                val primary = try { theme.primaryColor.toColorInt() } catch (_: Exception) { Color.BLUE }
                val textPrimary = try { theme.textPrimaryColor.toColorInt() } catch (_: Exception) { Color.BLACK }
                val textSecondary = try { theme.textSecondaryColor.toColorInt() } catch (_: Exception) { Color.DKGRAY }
                val onPrimary = try { theme.onPrimaryColor.toColorInt() } catch (_: Exception) { Color.WHITE }

                if (isOutgoing) {
                    surfaceColor = primary
                    textPrimaryColor = onPrimary
                    textSecondaryColor = onPrimary
                } else {
                    surfaceColor = surface
                    textPrimaryColor = textPrimary
                    textSecondaryColor = textSecondary
                }
            } else {
                val typedValue = android.util.TypedValue()
                
                if (isOutgoing) {
                    context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                    surfaceColor = if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
                    context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
                    textPrimaryColor = if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
                    textSecondaryColor = textPrimaryColor
                } else {
                    context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
                    surfaceColor = if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
                    context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                    textPrimaryColor = if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
                    context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                    textSecondaryColor = if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
                }
            }

            // Apply Background & Gravity
            if (isOutgoing) {
                messageBubble.setBackgroundResource(R.drawable.bg_message_outgoing)
                messageBubble.gravity = android.view.Gravity.END
            } else {
                messageBubble.setBackgroundResource(R.drawable.bg_message_incoming)
                messageBubble.gravity = android.view.Gravity.START
                userText.setTextColor(textSecondaryColor)
            }
            
            messageBubble.backgroundTintList = ColorStateList.valueOf(surfaceColor)
            messageText.setTextColor(textPrimaryColor)
            timeText.setTextColor(textSecondaryColor)
            editedText.setTextColor(textSecondaryColor)

            // 4. Status
            readStatusIcon.isVisible = isOutgoing
            if (isOutgoing) {
                val icon = if (message.isRead) R.drawable.ic_message_read else R.drawable.ic_message_sent
                readStatusIcon.setImageResource(icon)
                
                val iconColor = if (message.isRead) {
                    ContextCompat.getColor(context, R.color.tg_read_check)
                } else {
                    textSecondaryColor
                }
                readStatusIcon.imageTintList = ColorStateList.valueOf(iconColor)
            }

            // 5. Content
            timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
            timeText.isVisible = !shouldHideTime

            if (message.voiceUrl.isNotEmpty()) {
                messageText.isVisible = true
                messageText.text = context.getString(R.string.voice_message)
                messageText.textSize = 12f
                messageText.alpha = 0.7f
                
                audioMessageView.isVisible = true
                audioMessageView.setAudioData(message.voiceUrl, message.duration)
                audioMessageView.setOnPlayClickListener { _ -> }
                audioMessageView.setOnPauseClickListener { }
            } else {
                messageText.isVisible = message.text.isNotEmpty() || message.imageUrl.isNotEmpty()
                messageText.textSize = 16f
                messageText.alpha = 1.0f
                
                audioMessageView.isVisible = false
                
                val isLocation = message.text.startsWith("geo:")
                editedText.text = context.getString(R.string.edited_label)
                editedText.isVisible = message.edited

                if (isLocation) {
                    messageText.text = context.getString(R.string.location)
                    messageText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_location, 0, 0, 0)
                    messageText.compoundDrawablePadding = 8.dpToPx()
                    
                    val iconColorAttr = android.R.attr.textColorPrimary
                    val typedValue = android.util.TypedValue()
                    context.theme.resolveAttribute(iconColorAttr, typedValue, true)
                    val color = if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
                    messageText.compoundDrawables[0]?.setTint(color)

                    messageText.setOnClickListener {
                        val coords = message.text.removePrefix("geo:").split(",")
                        if (coords.size == 2) {
                            val lat = coords[0].toDoubleOrNull() ?: 0.0
                            val lng = coords[1].toDoubleOrNull() ?: 0.0
                            val intent = android.content.Intent(context, lavender.client.android.MapPickerActivity::class.java).apply {
                                putExtra("view_mode", true)
                                putExtra("lat", lat)
                                putExtra("lng", lng)
                            }
                            context.startActivity(intent)
                        }
                    }
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
                    val typedValue = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                    val color = if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
                    messageText.compoundDrawables[0]?.setTint(color)

                    messageText.setOnClickListener {
                        if (fileUrl.isNotEmpty()) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, fileUrl.toUri())
                            context.startActivity(intent)
                        }
                    }
                    messageText.setOnLongClickListener {
                        if (isSelectionMode) onLongClick() else onMessageLongClick?.invoke(message)
                        true
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
                    messageText.isClickable = false
                    messageText.isFocusable = false
                }
            }
            
            messageImageView.isVisible = message.imageUrl.isNotEmpty() && message.voiceUrl.isEmpty()
            if (message.imageUrl.isNotEmpty() && message.voiceUrl.isEmpty()) {
                com.bumptech.glide.Glide.with(context)
                    .load(message.imageUrl)
                    .transform(com.bumptech.glide.load.resource.bitmap.CenterCrop(), com.bumptech.glide.load.resource.bitmap.RoundedCorners(12.dpToPx()))
                    .into(messageImageView)
                messageImageView.setOnClickListener { 
                    if (isSelectionMode) onClick() else onMessageClick(message) 
                }
                messageImageView.setOnLongClickListener {
                    if (isSelectionMode) onLongClick() else onMessageLongClick?.invoke(message)
                    true
                }
            } else {
                messageImageView.setOnClickListener(null)
                messageImageView.setOnLongClickListener(null)
            }

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

                if (theme != null) {
                    try {
                        val onPrimary = theme.onPrimaryColor.toColorInt()
                        val onSurface = theme.onSurfaceColor.toColorInt()
                        val textPrimary = theme.textPrimaryColor.toColorInt()
                        if (isOutgoing) {
                            replyQuoteUser.setTextColor(onPrimary)
                            replyQuoteText.setTextColor(withAlpha(onPrimary, 200))
                            replyQuoteContainer.setBackgroundColor(withAlpha(onPrimary, 30))
                        } else {
                            replyQuoteUser.setTextColor(onSurface)
                            replyQuoteText.setTextColor(withAlpha(textPrimary, 200))
                            replyQuoteContainer.setBackgroundColor(withAlpha(onSurface, 30))
                        }
                    } catch (_: Exception) {}
                } else {
                    val onPrimary = android.util.TypedValue()
                    context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, onPrimary, true)
                    val onPrimaryColor = if (onPrimary.resourceId != 0) ContextCompat.getColor(context, onPrimary.resourceId) else onPrimary.data

                    val textPrimary = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.textColorPrimary, textPrimary, true)
                    val textPrimaryColor = if (textPrimary.resourceId != 0) ContextCompat.getColor(context, textPrimary.resourceId) else textPrimary.data

                    if (isOutgoing) {
                        replyQuoteUser.setTextColor(onPrimaryColor)
                        replyQuoteText.setTextColor(onPrimaryColor)
                        replyQuoteContainer.setBackgroundColor(withAlpha(onPrimaryColor, 30))
                    } else {
                        replyQuoteUser.setTextColor(ContextCompat.getColor(context, R.color.tg_blue))
                        replyQuoteText.setTextColor(textPrimaryColor)
                        replyQuoteContainer.setBackgroundColor(withAlpha(textPrimaryColor, 20))
                    }
                }
            }

            selectionIndicator.isVisible = isSelectionMode
            selectionIndicator.setImageResource(if (isSelected) R.drawable.ic_checked else R.drawable.ic_unchecked)
            messageBubble.alpha = if (isSelected) 0.6f else 1.0f
            itemView.setBackgroundColor(if (isSelected) ContextCompat.getColor(context, R.color.lavender_mist_alpha) else Color.TRANSPARENT)

            val genericOnClick = { onClick() }
            val genericOnLongClick = { onLongClick(); true }
            messageBubble.setOnClickListener { genericOnClick() }
            messageBubble.setOnLongClickListener { genericOnLongClick() }
            
            // reactionsText should also be clickable to show the dialog
            reactionsText.setOnClickListener { genericOnLongClick() }
        }

        private fun withAlpha(color: Int, alpha: Int): Int {
            return (color and 0x00FFFFFF) or (alpha shl 24)
        }

        private fun Int.dpToPx(): Int = (this * itemView.resources.displayMetrics.density).toInt()
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem == newItem
    }
}
