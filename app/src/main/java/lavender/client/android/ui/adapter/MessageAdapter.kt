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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import lavender.client.android.R
import lavender.client.android.data.models.Message
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
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
            adapterPosition = position,
            onClick = {
                if (selectionMode) {
                    if (selectedPositions.contains(position)) selectedPositions.remove(position)
                    else selectedPositions.add(position)
                    notifyItemChanged(position)
                    onSelectionChanged(selectedPositions.size)
                } else onMessageClick(currentMessage)
            },
            onLongClick = {
                if (selectionMode) {
                    if (selectedPositions.contains(position)) selectedPositions.remove(position)
                    else selectedPositions.add(position)
                    notifyItemChanged(position)
                    onSelectionChanged(selectedPositions.size)
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
        private val replyQuoteBar: View = itemView.findViewById(R.id.replyQuoteBar)
        private val messageImageView: ImageView = itemView.findViewById<ImageView>(R.id.messageImageView).apply {
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    val radius = 16.dpToPx().toFloat()
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
        }
        private val galleryCountIndicator: TextView = itemView.findViewById(R.id.galleryCountIndicator)
        private val audioMessageView: lavender.client.android.ui.audio.AudioMessageView = itemView.findViewById(R.id.audioMessageView)
        
        private val reactionsText: TextView = itemView.findViewById(R.id.reactionsText)
        
        // Track pending image load requests to cancel them when ViewHolder is reused
        private var pendingImageCall: okhttp3.Call? = null
        private var currentImageUrl: String? = null
        
        fun bind(message: Message, isOutgoing: Boolean, isSelected: Boolean, shouldHideTime: Boolean, isConsecutive: Boolean, isSelectionMode: Boolean, adapterPosition: Int, onClick: () -> Unit, onLongClick: () -> Unit, onMessageLongClick: ((Message) -> Unit)? = null) {
            val context = itemView.context
            val isGroup = this@MessageAdapter.isGroupChat
            val theme = ThemeStore.currentTheme()
            
            // Cancel any pending image load from previous bind
            if (currentImageUrl != message.imageUrl) {
                pendingImageCall?.cancel()
                pendingImageCall = null
            }
            
            // Check if this is an empty/unrecoverable message:
            // - Legacy "Image" placeholder with no stored imageUrl
            // - Legacy "Voice message" placeholder with no stored voiceUrl
            // - Completely empty message (no text, no image, no voice)
            val isEmptyImageMessage = message.text == "Image" && message.imageUrl.isEmpty()
            val isEmptyVoiceMessage = message.text == "Voice message" && message.voiceUrl.isEmpty()
            val isCompletelyEmpty = message.text.isEmpty() && message.imageUrl.isEmpty() && message.voiceUrl.isEmpty()
            val isEmptyMessage = isEmptyImageMessage || isEmptyVoiceMessage || isCompletelyEmpty
            
            // Hide entire message if it's empty — use GONE + zero height to avoid blank gaps
            if (isEmptyMessage) {
                itemView.visibility = View.GONE
                itemView.layoutParams = itemView.layoutParams.also { it.height = 0 }
                return
            }
            itemView.visibility = View.VISIBLE
            itemView.layoutParams = itemView.layoutParams.also {
                if (it.height == 0) it.height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
            
            messageText.text = message.text
            userText.text = message.user

            // 1. Visibility (Telegram Style)
            val canShowSenderInfo = isGroup && !isOutgoing && !isConsecutive && !isSelectionMode
            userText.isVisible = canShowSenderInfo

            if (canShowSenderInfo) {
                avatarImageView.isVisible = true
                if (message.avatarUrl.isNotEmpty()) {
                    Glide.with(context).load(message.avatarUrl)
                        .placeholder(R.drawable.ic_default_avatar).into(avatarImageView)
                    avatarImageView.imageTintList = null
                } else {
                    ThemeUtils.applyDefaultAvatar(avatarImageView, theme, theme.incomingBubbleColor)
                }
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

            // --- 3. ПОЛУЧАЕМ ЦВЕТА ИЗ ТЕМЫ ---
            val msgColors = getMessageColorsFromTheme(theme)

            // Определяем набор цветов в зависимости от стороны
            val (sColor, pTextColor, sTextColor) = if (isOutgoing) {
                Triple(msgColors.outgoingBg, msgColors.outgoingText, msgColors.outgoingText)
            } else {
                Triple(msgColors.incomingBg, msgColors.incomingText, msgColors.incomingText)
            }

            // ПРОВЕРКА: Если цвет фона пришел как 0 (прозрачный), ставим аварийный дефолт
            val finalSurfaceColor = if (sColor != 0) sColor else {
                if (isOutgoing) "#1A1B46".toColorInt() else "#6A1B9A".toColorInt()
            }

            // 1. Устанавливаем Drawable (Важно сделать это ПЕРЕД тинтом)
            val bubbleRes = if (isOutgoing) R.drawable.bg_message_outgoing else R.drawable.bg_message_incoming
            messageBubble.setBackgroundResource(bubbleRes)

            // 2. Применяем тинт (покраску)
            messageBubble.backgroundTintList = ColorStateList.valueOf(finalSurfaceColor)

            // 3. Красим тексты
            messageText.setTextColor(pTextColor)
            messageText.setLinkTextColor(pTextColor)

            // Для времени и статуса делаем чуть прозрачнее (80% непрозрачности), чтобы не сливалось
            val secondaryColorWithAlpha = (sTextColor and 0x00FFFFFF) or (0xCC shl 24)
            timeText.setTextColor(secondaryColorWithAlpha)
            editedText.setTextColor(secondaryColorWithAlpha)

            // 4. Status (Галочки)
            readStatusIcon.isVisible = isOutgoing
            if (isOutgoing) {
                val isRead = message.isRead || chatId.startsWith("favorites_")
                val icon = when {
                    isRead -> R.drawable.ic_message_read
                    message.isSent -> R.drawable.ic_message_sent
                    else -> R.drawable.ic_message_pending
                }
                readStatusIcon.setImageResource(icon)

                val iconColor = if (isRead) {
                    ContextCompat.getColor(context, R.color.tg_read_check)
                } else if (!message.isSent) {
                    secondaryColorWithAlpha // Gray for pending
                } else {
                    secondaryColorWithAlpha
                }
                readStatusIcon.imageTintList = ColorStateList.valueOf(iconColor)
            }

            // 5. Content
            timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
            timeText.isVisible = !shouldHideTime

            if (message.voiceUrl.isNotEmpty()) {
                messageText.isVisible = false
                messageText.textSize = 12f
                messageText.alpha = 0.7f

                audioMessageView.isVisible = true
                audioMessageView.setAudioData(message.voiceUrl, message.duration)
                audioMessageView.setOnPlayClickListener { _ -> }
                audioMessageView.setOnPauseClickListener { }
                audioMessageView.setOnClickListener {
                    if (isSelectionMode) onClick()
                }
                audioMessageView.setOnLongClickListener {
                    if (isSelectionMode) onLongClick() else {
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            onLongClick()
                        }
                    }
                    true
                }
            } else {
                val isFile = message.text.startsWith("File: ")
                messageText.isVisible = message.text.isNotEmpty() && message.text != "Image" && message.text != "Voice message" || (message.imageUrl.isNotEmpty() && message.text.isEmpty() && !isFile)
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
                        if (isSelectionMode) {
                            onClick()
                        } else {
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
                    }
                    messageText.setOnLongClickListener {
                        if (isSelectionMode) onLongClick() else {
                            if (adapterPosition != RecyclerView.NO_POSITION) {
                                onLongClick()
                            }
                        }
                        true
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
                    messageText.setBackgroundColor(Color.TRANSPARENT)
                    val typedValue = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                    val color = if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
                    messageText.compoundDrawables[0]?.setTint(color)

                    messageText.setOnClickListener {
                        if (isSelectionMode) {
                            onClick()
                        } else if (fileUrl.isNotEmpty()) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, fileUrl.toUri())
                            context.startActivity(intent)
                        }
                    }
                    messageText.setOnLongClickListener {
                        if (isSelectionMode) onLongClick() else {
                            if (adapterPosition != RecyclerView.NO_POSITION) {
                                onLongClick()
                            }
                        }
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
                            val highlightColor = try {
                                val theme = ThemeStore.currentTheme()
                                ThemeUtils.adjustAlpha(theme.primaryColor.toColorInt(), 0.5f)
                            } catch (_: Exception) {
                                ContextCompat.getColor(context, R.color.lavender_mist_alpha)
                            }
                            spannable.setSpan(
                                android.text.style.BackgroundColorSpan(highlightColor),
                                start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                        messageText.text = spannable
                    } else {
                        messageText.text = message.text
                    }
                    messageText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    messageText.movementMethod = android.text.method.LinkMovementMethod.getInstance()
                    messageText.setOnClickListener { if (isSelectionMode) onClick() else onMessageClick(message) }
                    messageText.isClickable = true
                    messageText.isFocusable = true
                    messageText.setOnLongClickListener {
                        if (isSelectionMode) onLongClick() else {
                            if (adapterPosition != RecyclerView.NO_POSITION) {
                                onLongClick()
                            }
                        }
                        true
                    }
                }
            }

            // Show image if imageUrl or imageUrls is not empty (but not if it's a file)
            val isFile = message.text.startsWith("File: ")
            val hasSingleImage = message.imageUrl.isNotEmpty()
            val hasGallery = message.imageUrls.isNotEmpty()
            val shouldShowImage = (hasSingleImage || hasGallery) && message.voiceUrl.isEmpty() && !isFile
            messageImageView.isVisible = shouldShowImage
            
            if (shouldShowImage) {
                // Use first image from gallery or single image
                val displayImageUrl = if (hasGallery) {
                    message.imageUrls.first()
                } else {
                    message.imageUrl
                }
                
                // Cancel any pending image load for this ViewHolder
                if (currentImageUrl != displayImageUrl) {
                    pendingImageCall?.cancel()
                    pendingImageCall = null
                    currentImageUrl = displayImageUrl
                }
                
                val imageUrl = if (displayImageUrl.startsWith("http")) {
                    displayImageUrl.trim()
                } else {
                    "http://159.195.38.145:8082" + displayImageUrl.trim().let { if (it.startsWith("/")) it else "/$it" }
                }
                
                Glide.with(context)
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .timeout(60000)
                    .dontAnimate()
                    .centerCrop()
                    .override(Target.SIZE_ORIGINAL)
                    .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean): Boolean {
                            android.util.Log.e("MessageAdapter", "Glide failed for [$imageUrl] - Error: ${e?.message}")
                            if (message.text.isEmpty()) {
                                messageText.text = "🖼 ${context.getString(R.string.error_loading_image)}"
                                messageText.isVisible = true
                            }
                            return false
                        }
                        override fun onResourceReady(resource: android.graphics.drawable.Drawable, model: Any, target: Target<android.graphics.drawable.Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                            android.util.Log.d("MessageAdapter", "Glide success for [$imageUrl] from $dataSource")
                            if (message.text.isEmpty()) messageText.isVisible = false
                            return false
                        }
                    })
                    .into(messageImageView)
                
                // Show gallery count indicator if multiple images
                if (hasGallery && message.imageUrls.size > 1) {
                    galleryCountIndicator.isVisible = true
                    galleryCountIndicator.text = "+${message.imageUrls.size - 1}"
                    messageImageView.contentDescription = "Gallery with ${message.imageUrls.size} images"
                } else {
                    galleryCountIndicator.isVisible = false
                }
                
                messageImageView.setOnClickListener {
                    if (isSelectionMode) {
                        onClick()
                    } else {
                        val url = displayImageUrl.lowercase()
                        val isVideo = url.endsWith(".mp4") || url.endsWith(".webm") || url.endsWith(".mkv") || url.endsWith(".mov")
                        
                        if (isVideo) {
                            val intent = android.content.Intent(context, lavender.client.android.VideoPlayerActivity::class.java).apply {
                                val absoluteUrl = if (displayImageUrl.startsWith("http")) {
                                    displayImageUrl.trim()
                                } else {
                                    "http://159.195.38.145:8082" + displayImageUrl.trim().let { if (it.startsWith("/")) it else "/$it" }
                                }
                                putExtra("VIDEO_URL", absoluteUrl)
                                putExtra("IS_LOCAL", false)
                            }
                            context.startActivity(intent)
                        } else {
                            // Use gallery images if available, otherwise fall back to single images from chat
                            val allImageUrls = if (hasGallery) {
                                message.imageUrls
                            } else {
                                currentList.filter { it.imageUrl.isNotEmpty() }.map { it.imageUrl }
                            }
                            val intent = android.content.Intent(context, lavender.client.android.FullScreenImageActivity::class.java).apply {
                                putExtra("image_url", displayImageUrl)
                                putExtra("chat_id", chatId)
                                putStringArrayListExtra("image_urls", ArrayList(allImageUrls))
                                putExtra("current_index", allImageUrls.indexOf(displayImageUrl))
                            }
                            context.startActivity(intent)
                        }
                    }
                }
                messageImageView.setOnLongClickListener {
                    if (isSelectionMode) onLongClick() else {
                        if (adapterPosition != RecyclerView.NO_POSITION) {
                            onLongClick()
                        }
                    }
                    true
                }
            } else {
                messageImageView.setOnClickListener(null)
                messageImageView.setOnLongClickListener(null)
                galleryCountIndicator.isVisible = false
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

                try {
                    val onPrimary = theme.onPrimaryColor.toColorInt()
                    val onSurface = theme.onSurfaceColor.toColorInt()
                    val textPrimary = theme.textPrimaryColor.toColorInt()
                    if (isOutgoing) {
                        replyQuoteUser.setTextColor(onPrimary)
                        replyQuoteText.setTextColor(withAlpha(onPrimary, 200))
                        replyQuoteContainer.setBackgroundColor(withAlpha(onPrimary, 30))
                        replyQuoteBar.setBackgroundColor(onPrimary)
                    } else {
                        replyQuoteUser.setTextColor(onSurface)
                        replyQuoteText.setTextColor(withAlpha(textPrimary, 200))
                        replyQuoteContainer.setBackgroundColor(withAlpha(onSurface, 30))
                        replyQuoteBar.setBackgroundColor(onSurface)
                    }
                } catch (_: Exception) {}
            }

            selectionIndicator.isVisible = isSelectionMode
            selectionIndicator.setImageResource(if (isSelected) R.drawable.ic_checked else R.drawable.ic_unchecked)
            messageBubble.alpha = if (isSelected) 0.6f else 1.0f
            
            val selectionColor = try {
                val theme = ThemeStore.currentTheme()
                ThemeUtils.adjustAlpha(theme.primaryColor.toColorInt(), 0.25f)
            } catch (_: Exception) {
                ContextCompat.getColor(context, R.color.lavender_mist_alpha)
            }
            itemView.setBackgroundColor(if (isSelected) selectionColor else Color.TRANSPARENT)

            val genericOnClick = { onClick() }
            val genericOnLongClick = { onLongClick(); true }
            
            // Make entire message container clickable in selection mode
            if (isSelectionMode) {
                messageContainer.setOnClickListener { genericOnClick() }
                messageContainer.setOnLongClickListener { genericOnLongClick() }
                messageContainer.isClickable = true
                selectionIndicator.setOnClickListener { genericOnClick() }
                selectionIndicator.isClickable = true
            } else {
                messageContainer.setOnClickListener(null)
                messageContainer.setOnLongClickListener(null)
                messageContainer.isClickable = false
                selectionIndicator.setOnClickListener(null)
                selectionIndicator.isClickable = false
                messageBubble.setOnClickListener { genericOnClick() }
                messageBubble.setOnLongClickListener { genericOnLongClick() }
            }
            
            // reactionsText should also be clickable to show the dialog
            reactionsText.setOnClickListener { if (isSelectionMode) onClick() else onMessageClick(message) }
        }

        private fun withAlpha(color: Int, alpha: Int): Int {
            return (color and 0x00FFFFFF) or (alpha shl 24)
        }

        private fun Int.dpToPx(): Int = (this * itemView.resources.displayMetrics.density).toInt()
    }

    private fun getMessageColorsFromTheme(theme: lavender.client.android.theme.Theme): MessageColors {
        val defaultIncomingBg = "#16173A".toColorInt()
        val defaultOutgoingBg = "#2A2C6D".toColorInt()
        val defaultText = Color.WHITE
        return MessageColors(
            incomingBg = parseSafeColor(theme.incomingBubbleColor, defaultIncomingBg),
            incomingText = parseSafeColor(theme.incomingTextColor, defaultText),
            outgoingBg = parseSafeColor(theme.outgoingBubbleColor, defaultOutgoingBg),
            outgoingText = parseSafeColor(theme.outgoingTextColor, defaultText)
        )
    }

    private fun parseSafeColor(colorStr: String, defaultColor: Int): Int {
        return try {
            colorStr.toColorInt()
        } catch (_: Exception) {
            defaultColor
        }
    }

    data class MessageColors(
        val incomingBg: Int,
        val incomingText: Int,
        val outgoingBg: Int,
        val outgoingText: Int
    )

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean = oldItem == newItem
    }
}
