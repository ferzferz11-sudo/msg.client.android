package lavender.client.android.ui.adapter
import android.util.Log

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
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
import android.graphics.Typeface
import lavender.client.android.data.calls.CallMessageHelper
import lavender.client.android.data.models.Message
import lavender.client.android.data.session.CredentialStore
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
    private val onRetrySendMessage: ((Message) -> Unit)? = null,
) : ListAdapter<Message, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    private val selectedPositions = mutableSetOf<Int>()
    private var selectionMode = false
    private var searchHighlight: String? = null
    private var pinnedMessageIds = mutableSetOf<String>()
    private val dayFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    }

    fun setSearchHighlight(query: String?) {
        val oldHighlight = searchHighlight
        searchHighlight = query
        if (oldHighlight != query) {
            for (i in 0 until itemCount) {
                val msg = try { getItem(i) } catch (_: Exception) { continue }
                if ((oldHighlight == null || msg.text.contains(oldHighlight, true)) ||
                    (query != null && msg.text.contains(query, true))) {
                    notifyItemChanged(i)
                }
            }
        }
    }
    fun updatePinnedMessages(ids: Set<String>) {
        val oldPinned = pinnedMessageIds.toSet()
        pinnedMessageIds = ids.toMutableSet()
        for (i in 0 until itemCount) {
            val msg = try { getItem(i) } catch (_: Exception) { continue }
            if (msg.id in oldPinned || msg.id in pinnedMessageIds) notifyItemChanged(i)
        }
    }
    fun getSelectedMessages(): List<Message> = selectedPositions.map { getItem(it) }

    @Suppress("UNUSED")
    fun clearSelection() {
        val prev = selectedPositions.toList(); selectedPositions.clear(); selectionMode = false
        prev.forEach { notifyItemChanged(it) }; onSelectionChanged(0)
    }

    fun toggleSelectionMode(enabled: Boolean) {
        selectionMode = enabled; if (!enabled) selectedPositions.clear()
        notifyItemRangeChanged(0, itemCount); if (!enabled) onSelectionChanged(0)
    }

    fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) selectedPositions.remove(position) else selectedPositions.add(position)
        notifyItemChanged(position); onSelectionChanged(selectedPositions.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return MessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false))
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = getItem(position)
        val prev = if (position > 0) getItem(position - 1) else null
        val isOutgoing = msg.user.trim().equals(currentUsername.trim(), ignoreCase = true)
        val isConsecutive = prev != null && prev.user.trim() == msg.user.trim()
        val now = System.currentTimeMillis()
        val curTs = if (msg.timestamp > now) now else msg.timestamp
        val prevTs = prev?.let { if (it.timestamp > now) now else it.timestamp } ?: 0L
        val isSameMinute = prev != null && (curTs / 60000 == prevTs / 60000)
        val showDateSeparator = if (prev == null) true else {
            val fmt = dayFormat.get()!!
            val curDay = fmt.format(Date(curTs))
            val prevDay = fmt.format(Date(prevTs))
            curDay != prevDay
        }
        holder.bind(msg, isOutgoing, selectedPositions.contains(position), isConsecutive && isSameMinute,
            isConsecutive, selectionMode, position, showDateSeparator,
            onClick = { pos -> if (selectionMode) { if (selectedPositions.contains(pos)) selectedPositions.remove(pos) else selectedPositions.add(pos); notifyItemChanged(pos); onSelectionChanged(selectedPositions.size) } else onMessageClick(getItem(pos)) },
            onLongClick = { pos -> if (selectionMode) { if (selectedPositions.contains(pos)) selectedPositions.remove(pos) else selectedPositions.add(pos); notifyItemChanged(pos); onSelectionChanged(selectedPositions.size) } else onMessageLongClick?.invoke(getItem(pos)) },
            onMessageLongClick = onMessageLongClick)
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageContainer: LinearLayout = itemView.findViewById(R.id.messageContainer)
        val messageBubble: LinearLayout = itemView.findViewById(R.id.messageBubble)
        private val dateText: TextView = itemView.findViewById(R.id.tvDateSeparator)
        private val selectionIndicator: ImageView = itemView.findViewById(R.id.ivSelectionIndicator)
        private val avatarImageView: com.google.android.material.imageview.ShapeableImageView = itemView.findViewById(R.id.ivAvatar)
        private val userText: TextView = itemView.findViewById(R.id.tvUserName)
        val messageText: TextView = itemView.findViewById(R.id.tvMessageText)
        val timeText: TextView = itemView.findViewById(R.id.tvMessageTime)
        private val editedText: TextView = itemView.findViewById(R.id.tvEditedLabel)
        private val readStatusIcon: ImageView = itemView.findViewById(R.id.ivReadStatus)
        private val replyQuoteContainer: View = itemView.findViewById(R.id.llReplyQuote)
        private val replyQuoteUser: TextView = itemView.findViewById(R.id.tvReplyUser)
        private val replyQuoteText: TextView = itemView.findViewById(R.id.tvReplyText)
        private val replyQuoteBar: View = itemView.findViewById(R.id.vReplyBar)
        val messageImageView: ImageView = itemView.findViewById<ImageView>(R.id.ivMessageImage).apply {
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) { outline.setRoundRect(0, 0, view.width, view.height, 16f * itemView.resources.displayMetrics.density) }
            }
        }
        val galleryThumbnailsRecyclerView: RecyclerView = itemView.findViewById(R.id.rvGalleryThumbnails)
        val audioMessageView: lavender.client.android.ui.audio.AudioMessageView = itemView.findViewById(R.id.audioMessageView)
        val reactionsText: TextView = itemView.findViewById(R.id.tvReactions)
        private val btnDownloadFile: ImageButton = itemView.findViewById(R.id.btnDownloadFile)
        val lottieStickerView: com.airbnb.lottie.LottieAnimationView = itemView.findViewById(R.id.lottieStickerView)
        val stickerImageView: ImageView = itemView.findViewById(R.id.ivStickerImage)

        fun bind(message: Message, isOutgoing: Boolean, isSelected: Boolean, shouldHideTime: Boolean,
                 isConsecutive: Boolean, isSelectionMode: Boolean, adapterPosition: Int, showDateSeparator: Boolean,
                 onClick: (Int) -> Unit, onLongClick: (Int) -> Unit, onMessageLongClick: ((Message) -> Unit)?) {
            try {
            val ctx = itemView.context
            val isGroup = this@MessageAdapter.isGroupChat
            val theme = try { ThemeStore.currentTheme() } catch (_: Exception) { lavender.client.android.theme.BuiltInThemes.dark }
            val isCompletelyEmpty = message.text.isEmpty() && message.imageUrl.isEmpty() && message.voiceUrl.isEmpty() && message.imageUrls.isEmpty()
            btnDownloadFile.isVisible = false
            if (isCompletelyEmpty) { itemView.visibility = View.GONE; itemView.layoutParams = itemView.layoutParams.also { it.height = 0 }; return }
            itemView.visibility = View.VISIBLE; itemView.layoutParams = itemView.layoutParams.also { if (it.height == 0) it.height = ViewGroup.LayoutParams.WRAP_CONTENT }

            dateText.isVisible = showDateSeparator; if (showDateSeparator) dateText.text = getFormattedDate(message.timestamp)
            messageText.text = message.text; userText.text = message.user; messageText.movementMethod = null
            val canShowSenderInfo = isGroup && !isOutgoing && !isConsecutive && !isSelectionMode
            userText.isVisible = canShowSenderInfo
            if (canShowSenderInfo) {
                avatarImageView.isVisible = true
                if (message.avatarUrl.isNotEmpty()) {
                    try { Glide.with(ctx).load(message.avatarUrl).placeholder(R.drawable.ic_default_avatar).into(avatarImageView) } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }
                    avatarImageView.imageTintList = null
                } else {
                    try { ThemeUtils.applyDefaultAvatar(avatarImageView, theme, theme.incomingBubbleColor) } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }
                }
            }
            else { avatarImageView.visibility = if (isOutgoing) View.GONE else View.INVISIBLE }

            bindAlignment(message, isOutgoing, isConsecutive, isSelectionMode, isGroup)
            val msgColors = getMessageColorsFromTheme(theme)
            val (surfaceColor, pTextColor) = if (isOutgoing) Pair(msgColors.outgoingBg, msgColors.outgoingText) else Pair(msgColors.incomingBg, msgColors.incomingText)
            val finalSurface = if (surfaceColor != 0) surfaceColor else { if (isOutgoing) "#3D6B6C".toColorInt() else "#363636".toColorInt() }
            val secColor = (pTextColor and 0x00FFFFFF) or (0xCC shl 24)

            val isCallMessage = CallMessageHelper.isCallOrConference(message.text)
            val isSystem = message.user == "SYSTEM" && !isCallMessage

            bindBubbleStyle(isOutgoing, isSystem, finalSurface, pTextColor, secColor, canShowSenderInfo, theme)
            if (isCallMessage) bindCallMessage(message, isOutgoing, pTextColor, ctx) else { messageText.text = message.text; messageText.textSize = 16f; messageText.setTypeface(null, Typeface.NORMAL); messageText.setTextColor(pTextColor) }
            timeText.setTextColor(secColor); timeText.isVisible = !shouldHideTime; editedText.setTextColor(secColor)
            if (isOutgoing) bindReadStatus(message, secColor, ctx) else { readStatusIcon.isVisible = false }

            val safeTs = if (message.timestamp > System.currentTimeMillis()) System.currentTimeMillis() else message.timestamp
            timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(safeTs))
            timeText.isVisible = !shouldHideTime

            if (message.voiceUrl.isNotEmpty()) bindAudioContent(message, isOutgoing, isSelectionMode, theme, onClick, onLongClick, adapterPosition)
            else { audioMessageView.isVisible = false; bindTextContent(message, isOutgoing, isSelectionMode, pTextColor, theme, ctx, onClick, onLongClick, adapterPosition) }

            bindStickerContent(message, isOutgoing, isSelectionMode, onClick, onLongClick, adapterPosition, ctx)
            bindImageContent(message, isOutgoing, isSelectionMode, onClick, onLongClick, adapterPosition, ctx)
            bindReactions(message, theme, isOutgoing, onClick)
            bindReplyQuote(message, isOutgoing, theme)
            bindSelectionIndicator(isSelected, isSelectionMode, theme, ctx)
            bindContainerClicks(isSelectionMode, onClick, onLongClick, adapterPosition)
            bindPinnedBadge(message, theme, ctx)
            reactionsText.setOnClickListener { if (isSelectionMode) onClick(bindingAdapterPosition) else onMessageClick(message) }
            } catch (e: Exception) {
                android.util.Log.e("MessageAdapter", "bind crashed for msg ${message.id}: ${e.message}", e)
                try {
                    messageText.text = message.text.ifEmpty { "…" }
                    messageText.setTextColor(Color.WHITE)
                    messageText.isVisible = true
                    avatarImageView.isVisible = false
                    timeText.isVisible = false
                    audioMessageView.isVisible = false
                    messageImageView.isVisible = false
                    galleryThumbnailsRecyclerView.isVisible = false
                } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }
            }
        }

        private fun bindAlignment(message: Message, isOutgoing: Boolean, isConsecutive: Boolean, isSelectionMode: Boolean, isGroup: Boolean) {
            val lp = messageContainer.layoutParams
            val isCallMessage = CallMessageHelper.isCallOrConference(message.text)
            val isSystem = message.user == "SYSTEM" && !isCallMessage
            if (lp is RelativeLayout.LayoutParams) {
                lp.removeRule(RelativeLayout.ALIGN_PARENT_START); lp.removeRule(RelativeLayout.ALIGN_PARENT_END); lp.removeRule(RelativeLayout.ALIGN_PARENT_LEFT); lp.removeRule(RelativeLayout.ALIGN_PARENT_RIGHT); lp.removeRule(RelativeLayout.END_OF); lp.removeRule(RelativeLayout.RIGHT_OF); lp.removeRule(RelativeLayout.CENTER_HORIZONTAL)
                if (isSystem) { lp.addRule(RelativeLayout.CENTER_HORIZONTAL); lp.marginStart = 40.dpToPx(); lp.marginEnd = 40.dpToPx() }
                else if (isOutgoing) { lp.addRule(RelativeLayout.ALIGN_PARENT_END); lp.marginStart = 40.dpToPx(); lp.marginEnd = 0 }
                else { lp.addRule(RelativeLayout.ALIGN_PARENT_START); if (isSelectionMode) lp.addRule(RelativeLayout.END_OF, R.id.ivSelectionIndicator); lp.marginStart = 0; lp.marginEnd = 40.dpToPx() }
                lp.topMargin = if (isConsecutive && !isSystem) 2.dpToPx() else 8.dpToPx(); messageContainer.layoutParams = lp
            }
        }

        private fun bindBubbleStyle(isOutgoing: Boolean, isSystem: Boolean, surfaceColor: Int, textColor: Int, secColor: Int, canShowSenderInfo: Boolean, theme: lavender.client.android.theme.Theme) {
            if (isSystem) { messageBubble.setBackgroundResource(R.drawable.bg_date_separator); messageBubble.backgroundTintList = ColorStateList.valueOf(ThemeUtils.adjustAlpha(Color.GRAY, 0.4f)); messageText.setTextColor(Color.WHITE); messageText.textSize = 13f; timeText.isVisible = false; readStatusIcon.isVisible = false; avatarImageView.isVisible = false; userText.isVisible = false; return }
            val bubbleRes = if (isOutgoing) R.drawable.bg_message_outgoing else R.drawable.bg_message_incoming
            messageBubble.setBackgroundResource(bubbleRes); messageBubble.backgroundTintList = ColorStateList.valueOf(surfaceColor)
            messageText.setTextColor(textColor); messageText.setLinkTextColor(textColor)
            if (canShowSenderInfo) userText.setTextColor(ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE))
            timeText.setTextColor(secColor); editedText.setTextColor(secColor)
        }

        private fun bindCallMessage(message: Message, isOutgoing: Boolean, textColor: Int, ctx: android.content.Context) {
            val raw = message.text; val isMissed = CallMessageHelper.isCallMissed(raw)
            val isCompleted = CallMessageHelper.isCallEnded(raw)
            val icon = when { isMissed -> if (isOutgoing) "🚫" else "📞↙️"; isCompleted -> if (isOutgoing) "📞↗️" else "📞↙️"; else -> if (isOutgoing) "📞↗️" else "📹" }
            val statusText = when { isMissed -> if (isOutgoing) ctx.getString(R.string.call_not_accepted) else ctx.getString(R.string.call_missed)
                isCompleted -> { val dur = raw.substringAfter("(").substringBefore(")"); if (isOutgoing) ctx.getString(R.string.call_outgoing_with_duration, dur) else ctx.getString(R.string.call_incoming_with_duration, dur) }
                else -> if (isOutgoing) ctx.getString(R.string.call_outgoing_video) else ctx.getString(R.string.call_incoming_video) }
            messageText.text = "$icon $statusText"; messageText.textSize = 15f; messageText.setTypeface(null, Typeface.BOLD)
            messageText.setTextColor(if (isMissed && !isOutgoing) "#FF5252".toColorInt() else textColor)
        }

        private fun bindReadStatus(message: Message, secColor: Int, ctx: android.content.Context) {
            readStatusIcon.isVisible = true; val isRead = message.isRead || chatId.startsWith("favorites_")
            val isTimedOut = !message.isSent && (System.currentTimeMillis() - message.timestamp > 60 * 1000)
            val icon = when { isTimedOut -> R.drawable.ic_loading_renew; isRead -> R.drawable.ic_message_read; message.isSent -> R.drawable.ic_message_sent; else -> R.drawable.ic_message_pending }
            readStatusIcon.setImageResource(icon)
            val iconColor = when { isTimedOut -> Color.RED; isRead -> ContextCompat.getColor(ctx, R.color.tg_read_check); else -> secColor }
            readStatusIcon.imageTintList = ColorStateList.valueOf(iconColor)
            if (isTimedOut) { readStatusIcon.setOnClickListener { onRetrySendMessage?.invoke(message) }; readStatusIcon.isClickable = true }
            else { readStatusIcon.setOnClickListener(null); readStatusIcon.isClickable = false }
        }

        private fun bindAudioContent(message: Message, isOutgoing: Boolean, isSelectionMode: Boolean, theme: lavender.client.android.theme.Theme, onClick: (Int) -> Unit, onLongClick: (Int) -> Unit, pos: Int) {
            messageText.isVisible = false; audioMessageView.isVisible = true
            audioMessageView.setAudioData(message.voiceUrl, message.duration); audioMessageView.applyTheme(theme, isOutgoing)
            audioMessageView.setOnClickListener { if (isSelectionMode) onClick(pos) }
            audioMessageView.setOnLongClickListener { if (isSelectionMode) onLongClick(pos) else { if (pos != RecyclerView.NO_POSITION) onLongClick(pos) }; true }
        }

        private fun bindTextContent(message: Message, isOutgoing: Boolean, isSelectionMode: Boolean, textColor: Int, theme: lavender.client.android.theme.Theme, ctx: android.content.Context, onClick: (Int) -> Unit, onLongClick: (Int) -> Unit, pos: Int) {
            val isFile = message.text.startsWith("File: ")
            val isLocation = message.text.startsWith("geo:")
            val isSticker = message.stickerUrl.isNotEmpty()
            messageText.textSize = 16f; messageText.alpha = 1.0f
            editedText.text = ctx.getString(R.string.edited_label); editedText.isVisible = message.edited
            messageText.isVisible = !isSticker && (message.text.isNotEmpty() && message.text != "Image" && message.text != "Voice message" || (message.imageUrl.isNotEmpty() && message.text.isEmpty() && !isFile))

            if (isLocation) bindLocationContent(message, ctx, textColor, isSelectionMode, onClick, onLongClick, pos)
            else if (isFile) bindFileContent(message, ctx, textColor, isSelectionMode, onClick, onLongClick, pos)
            else bindPlainContent(message, theme, ctx, isSelectionMode, onClick, onLongClick, pos)
        }

        private fun bindLocationContent(message: Message, ctx: android.content.Context, textColor: Int, isSelectionMode: Boolean, onClick: (Int) -> Unit, onLongClick: (Int) -> Unit, pos: Int) {
            messageText.text = ctx.getString(R.string.location); messageText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_location, 0, 0, 0); messageText.compoundDrawablePadding = 8.dpToPx()
            val tv = android.util.TypedValue(); ctx.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
            val color = if (tv.resourceId != 0) ContextCompat.getColor(ctx, tv.resourceId) else tv.data; messageText.compoundDrawables[0]?.setTint(color)
            messageText.setOnClickListener { if (isSelectionMode) onClick(pos) else { val coords = message.text.removePrefix("geo:").split(","); if (coords.size == 2) { val lat = coords[0].toDoubleOrNull() ?: 0.0; val lng = coords[1].toDoubleOrNull() ?: 0.0; ctx.startActivity(android.content.Intent(ctx, lavender.client.android.MapPickerActivity::class.java).apply { putExtra("view_mode", true); putExtra("lat", lat); putExtra("lng", lng) }) } } }
            messageText.setOnLongClickListener { if (isSelectionMode) onLongClick(pos) else { if (pos != RecyclerView.NO_POSITION) onLongClick(pos) }; true }
        }

        private fun bindFileContent(message: Message, ctx: android.content.Context, textColor: Int, isSelectionMode: Boolean, onClick: (Int) -> Unit, onLongClick: (Int) -> Unit, pos: Int) {
            val lines = message.text.split("\n"); val fileName = if (lines.size > 1) lines[0].removePrefix("File: ") else message.text.removePrefix("File: "); val fileUrl = if (lines.size > 1) lines[1] else ""
            val fileIcon = when { fileName.lowercase().endsWith(".pdf") -> R.drawable.ic_file_pdf; fileName.lowercase().endsWith(".zip") || fileName.lowercase().endsWith(".rar") || fileName.lowercase().endsWith(".7z") -> R.drawable.ic_file_archive; else -> R.drawable.ic_file }
            messageText.text = fileName; messageText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0); messageText.setBackgroundColor(Color.TRANSPARENT)
            btnDownloadFile.isVisible = !isSelectionMode; btnDownloadFile.setImageResource(fileIcon); btnDownloadFile.imageTintList = ColorStateList.valueOf(textColor)
            btnDownloadFile.setOnClickListener { val cp = bindingAdapterPosition; if (cp == RecyclerView.NO_POSITION) return@setOnClickListener; if (isSelectionMode) onClick(cp) else if (fileUrl.isNotEmpty()) ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, fileUrl.toUri())) }
            messageText.setOnClickListener { val cp = bindingAdapterPosition; if (cp == RecyclerView.NO_POSITION) return@setOnClickListener; if (isSelectionMode) onClick(cp) else if (fileUrl.isNotEmpty()) ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, fileUrl.toUri())) }
            messageText.setOnLongClickListener { if (isSelectionMode) onLongClick(pos) else { if (pos != RecyclerView.NO_POSITION) onLongClick(pos) }; true }
        }

        private fun bindPlainContent(message: Message, theme: lavender.client.android.theme.Theme, ctx: android.content.Context, isSelectionMode: Boolean, onClick: (Int) -> Unit, onLongClick: (Int) -> Unit, pos: Int) {
            val text = message.text; val highlight = searchHighlight
            if (!highlight.isNullOrEmpty() && text.contains(highlight, ignoreCase = true)) {
                val spannable = android.text.SpannableString(text)
                val hlColor = try { ThemeUtils.adjustAlpha(theme.primaryColor.toColorInt(), 0.5f) } catch (_: Exception) { ContextCompat.getColor(ctx, R.color.lavender_mist_alpha) }
                val lowerText = text.lowercase(); val lowerHighlight = highlight.lowercase(); var idx = 0
                while (idx <= lowerText.length - lowerHighlight.length) {
                    val start = lowerText.indexOf(lowerHighlight, idx)
                    if (start == -1) break
                    spannable.setSpan(android.text.style.BackgroundColorSpan(hlColor), start, start + highlight.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    idx = start + highlight.length
                }
                applyMentionSpans(spannable, message, ctx)
                messageText.text = spannable
            } else {
                val spannable = android.text.SpannableString(text)
                applyMentionSpans(spannable, message, ctx)
                messageText.text = spannable
            }
            messageText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            messageText.movementMethod = if (isSelectionMode) null else android.text.method.LinkMovementMethod.getInstance()
            messageText.setOnClickListener { if (isSelectionMode) onClick(pos) else onMessageClick(message) }
            messageText.setOnTouchListener { v, event -> if (event.action == android.view.MotionEvent.ACTION_DOWN) v.tag = System.currentTimeMillis() else if (event.action == android.view.MotionEvent.ACTION_UP) { if (System.currentTimeMillis() - (v.tag as? Long ?: 0L) > android.view.ViewConfiguration.getLongPressTimeout()) return@setOnTouchListener true }; false }
            messageText.isClickable = true; messageText.isLongClickable = true; messageText.isFocusable = true
            messageText.setOnLongClickListener { if (isSelectionMode) onLongClick(pos) else { if (pos != RecyclerView.NO_POSITION) onLongClick(pos) }; true }
        }

        private fun applyMentionSpans(spannable: android.text.SpannableString, message: Message, ctx: android.content.Context) {
            val mentionedUsers = message.mentions.toSet()
            if (mentionedUsers.isEmpty()) return
            val mentionColor = try { ThemeUtils.adjustAlpha(lavender.client.android.theme.ThemeStore.currentTheme().primaryColor.toColorInt(), 0.85f) } catch (_: Exception) { ContextCompat.getColor(ctx, R.color.lavender_mist_alpha) }
            val regex = Regex("@(\\w+)")
            for (match in regex.findAll(spannable)) {
                val username = match.groupValues[1]
                if (username in mentionedUsers || mentionedUsers.isEmpty()) {
                    val start = match.range.first; val end = match.range.last + 1
                    spannable.setSpan(android.text.style.ForegroundColorSpan(mentionColor), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(android.text.style.StyleSpan(Typeface.BOLD), start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    spannable.setSpan(object : android.text.style.ClickableSpan() {
                        override fun onClick(widget: android.view.View) {
                            // Mention click — no action for now, just visual feedback
                        }
                    }, start, end, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        private fun bindStickerContent(message: Message, isOutgoing: Boolean, isSelectionMode: Boolean, onClick: (Int) -> Unit, onLongClick: (Int) -> Unit, pos: Int, ctx: android.content.Context) {
            val isSticker = message.stickerUrl.isNotEmpty()
            lottieStickerView.isVisible = false
            stickerImageView.isVisible = false
            if (!isSticker) { lottieStickerView.setOnClickListener(null); lottieStickerView.setOnLongClickListener(null); stickerImageView.setOnClickListener(null); stickerImageView.setOnLongClickListener(null); return }

            val stickerUrl = message.stickerUrl
            val isLottie = stickerUrl.endsWith(".json", ignoreCase = true)
            if (isLottie) {
                lottieStickerView.isVisible = true
                lottieStickerView.setAnimation(stickerUrl)
                lottieStickerView.repeatCount = 0
                lottieStickerView.playAnimation()
                lottieStickerView.setOnClickListener { if (isSelectionMode) onClick(pos) else onMessageClick(message) }
                lottieStickerView.setOnLongClickListener { if (isSelectionMode) onLongClick(pos) else { onLongClick(pos) }; true }
            } else {
                stickerImageView.isVisible = true
                Glide.with(ctx).load(stickerUrl).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_placeholder).centerCrop().into(stickerImageView)
                stickerImageView.setOnClickListener { if (isSelectionMode) onClick(pos) else onMessageClick(message) }
                stickerImageView.setOnLongClickListener { if (isSelectionMode) onLongClick(pos) else { onLongClick(pos) }; true }
            }
        }

        private fun bindImageContent(message: Message, isOutgoing: Boolean, isSelectionMode: Boolean, onClick: (Int) -> Unit, onLongClick: (Int) -> Unit, pos: Int, ctx: android.content.Context) {
            val isFile = message.text.startsWith("File: "); val hasSingle = message.imageUrl.isNotEmpty(); val hasGallery = message.imageUrls.isNotEmpty()
            val isMultiImage = hasGallery && message.imageUrls.size > 1
            val shouldShow = (hasSingle || hasGallery) && message.voiceUrl.isEmpty() && !isFile
            messageImageView.isVisible = shouldShow && !isMultiImage
            galleryThumbnailsRecyclerView.isVisible = shouldShow && isMultiImage
            if (!shouldShow) { messageImageView.setOnClickListener(null); messageImageView.setOnLongClickListener(null); return }
            if (isMultiImage) {
                val urls = message.imageUrls.map { url ->
                    if (url.startsWith("http")) url.trim() else "${CredentialStore.getHttpServerUrl(ctx)}" + url.trim().let { if (it.startsWith("/")) it else "/$it" }
                }
                val adapter = ThumbnailGridAdapter(urls) { clickIndex ->
                    if (isSelectionMode) onClick(pos) else {
                        ctx.startActivity(android.content.Intent(ctx, lavender.client.android.FullScreenImageActivity::class.java).apply {
                            putStringArrayListExtra("image_urls", ArrayList(urls))
                            putExtra("current_index", clickIndex)
                        })
                    }
                }
                galleryThumbnailsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(ctx, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
                galleryThumbnailsRecyclerView.adapter = adapter
            } else {
                val displayUrl = if (hasGallery) message.imageUrls.first() else message.imageUrl
                val imageUrl = if (displayUrl.startsWith("http")) displayUrl.trim() else "${CredentialStore.getHttpServerUrl(ctx)}" + displayUrl.trim().let { if (it.startsWith("/")) it else "/$it" }
                Glide.with(ctx).load(imageUrl).diskCacheStrategy(DiskCacheStrategy.ALL).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_placeholder).timeout(60000).dontAnimate().centerCrop().override(Target.SIZE_ORIGINAL)
                    .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean): Boolean { if (message.text.isEmpty()) { messageText.text = "🖼 ${ctx.getString(R.string.error_loading_image)}"; messageText.isVisible = true }; return false }
                        override fun onResourceReady(resource: android.graphics.drawable.Drawable, model: Any, target: Target<android.graphics.drawable.Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean { if (message.text.isEmpty()) messageText.isVisible = false; return false }
                    }).into(messageImageView)
                messageImageView.setOnClickListener { if (isSelectionMode) onClick(pos) else { val url = displayUrl.lowercase(); val isVideo = url.endsWith(".mp4") || url.endsWith(".webm") || url.endsWith(".mkv") || url.endsWith(".mov"); if (isVideo) { val absUrl = if (displayUrl.startsWith("http")) displayUrl.trim() else "${CredentialStore.getHttpServerUrl(ctx)}" + displayUrl.trim().let { if (it.startsWith("/")) it else "/$it" }; ctx.startActivity(android.content.Intent(ctx, lavender.client.android.VideoPlayerActivity::class.java).apply { putExtra("VIDEO_URL", absUrl); putExtra("IS_LOCAL", false) }) } else { val allUrls = if (hasGallery) message.imageUrls else currentList.filter { it.imageUrl.isNotEmpty() }.map { it.imageUrl }; ctx.startActivity(android.content.Intent(ctx, lavender.client.android.FullScreenImageActivity::class.java).apply { putExtra("image_url", displayUrl); putExtra("chat_id", chatId); putStringArrayListExtra("image_urls", ArrayList(allUrls)); putExtra("current_index", allUrls.indexOf(displayUrl)) }) } } }
                messageImageView.setOnLongClickListener { if (isSelectionMode) onLongClick(pos) else { onLongClick(pos) }; true }
            }
        }

        private fun bindReactions(message: Message, theme: lavender.client.android.theme.Theme, isOutgoing: Boolean, onClick: (Int) -> Unit) {
            reactionsText.isVisible = message.reactions.isNotEmpty()
            if (message.reactions.isNotEmpty()) { val grouped = message.reactions.groupBy { it.emoji }; reactionsText.text = grouped.entries.joinToString(" ") { "${it.key}${if (it.value.size > 1) " ${it.value.size}" else ""}" } }
        }

        private fun bindReplyQuote(message: Message, isOutgoing: Boolean, theme: lavender.client.android.theme.Theme) {
            replyQuoteContainer.isVisible = message.repliedToUser.isNotEmpty()
            if (message.repliedToUser.isNotEmpty()) { replyQuoteUser.text = message.repliedToUser; replyQuoteText.text = message.repliedToText
                try { val onPrim = theme.onPrimaryColor.toColorInt(); val onSurf = theme.onSurfaceColor.toColorInt(); val txtPrim = theme.textPrimaryColor.toColorInt()
                    if (isOutgoing) { replyQuoteUser.setTextColor(onPrim); replyQuoteText.setTextColor(withAlpha(onPrim, 200)); replyQuoteContainer.setBackgroundColor(withAlpha(onPrim, 30)); replyQuoteBar.setBackgroundColor(onPrim) }
                    else { replyQuoteUser.setTextColor(onSurf); replyQuoteText.setTextColor(withAlpha(txtPrim, 200)); replyQuoteContainer.setBackgroundColor(withAlpha(onSurf, 30)); replyQuoteBar.setBackgroundColor(onSurf) } } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) } }
        }

        private fun bindSelectionIndicator(isSelected: Boolean, isSelectionMode: Boolean, theme: lavender.client.android.theme.Theme, ctx: android.content.Context) {
            selectionIndicator.isVisible = isSelectionMode; selectionIndicator.setImageResource(if (isSelected) R.drawable.ic_checked else R.drawable.ic_unchecked)
            try { val pColor = theme.primaryColor.toColorInt(); val sColor = theme.textSecondaryColor.toColorInt(); selectionIndicator.imageTintList = ColorStateList.valueOf(if (isSelected) pColor else sColor) } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }
            messageBubble.alpha = if (isSelected) 0.6f else 1.0f
            val selColor = try { ThemeUtils.adjustAlpha(theme.primaryColor.toColorInt(), 0.25f) } catch (_: Exception) { ContextCompat.getColor(ctx, R.color.lavender_mist_alpha) }
            itemView.setBackgroundColor(if (isSelected) selColor else Color.TRANSPARENT)
        }

        private fun bindContainerClicks(isSelectionMode: Boolean, onClick: (Int) -> Unit, onLongClick: (Int) -> Unit, pos: Int) {
            if (isSelectionMode) { messageContainer.setOnClickListener { onClick(pos) }; messageContainer.setOnLongClickListener { onLongClick(pos); true }; messageContainer.isClickable = true; selectionIndicator.setOnClickListener { onClick(pos) }; selectionIndicator.isClickable = true }
            else { messageContainer.setOnClickListener(null); messageContainer.setOnLongClickListener(null); messageContainer.isClickable = false; selectionIndicator.setOnClickListener(null); selectionIndicator.isClickable = false
                messageBubble.setOnClickListener { val cp = bindingAdapterPosition; if (cp != RecyclerView.NO_POSITION) onClick(cp) }; messageBubble.setOnLongClickListener { val cp = bindingAdapterPosition; if (cp != RecyclerView.NO_POSITION) onLongClick(cp); true } }
        }

        private fun bindPinnedBadge(message: Message, theme: lavender.client.android.theme.Theme, ctx: android.content.Context) {
            val llPinned: LinearLayout? = itemView.findViewById(R.id.llPinnedBadge); val ivPinned: ImageView? = itemView.findViewById(R.id.ivPinnedIcon); val tvPinned: TextView? = itemView.findViewById(R.id.tvPinnedText)
            if (llPinned != null && ivPinned != null && tvPinned != null) { val isPinned = pinnedMessageIds.contains(message.id); llPinned.isVisible = isPinned; if (isPinned) { tvPinned.text = message.text.ifEmpty { ctx.getString(R.string.pinned_message) }; try { llPinned.backgroundTintList = ColorStateList.valueOf(ThemeUtils.parseSafeColor(theme.surfaceColor, Color.LTGRAY)) } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) } } }
        }

        private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
        private fun getFormattedDate(timestamp: Long): String { val now = System.currentTimeMillis(); val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault()); val cur = sdf.format(Date(timestamp)); val today = sdf.format(Date(now)); val yesterday = sdf.format(Date(now - 86400000)); return when (cur) { today -> itemView.context.getString(R.string.today); yesterday -> itemView.context.getString(R.string.yesterday); else -> SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(timestamp)) } }
        private fun Int.dpToPx(): Int = (this * itemView.resources.displayMetrics.density).toInt()
    }

    private fun getMessageColorsFromTheme(theme: lavender.client.android.theme.Theme): MessageColors {
        return MessageColors(parseSafeColor(theme.incomingBubbleColor, "#16173A".toColorInt()), parseSafeColor(theme.incomingTextColor, Color.WHITE), parseSafeColor(theme.outgoingBubbleColor, "#2A2C6D".toColorInt()), parseSafeColor(theme.outgoingTextColor, Color.WHITE))
    }
    private fun parseSafeColor(colorStr: String, defaultColor: Int): Int = try { colorStr.toColorInt() } catch (_: Exception) { defaultColor }
    data class MessageColors(val incomingBg: Int, val incomingText: Int, val outgoingBg: Int, val outgoingText: Int)
    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() { override fun areItemsTheSame(a: Message, b: Message): Boolean = a.id == b.id; override fun areContentsTheSame(a: Message, b: Message): Boolean = a == b }

    private class ThumbnailGridAdapter(
        private val urls: List<String>,
        private val onThumbnailClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ThumbnailGridAdapter.ThumbnailViewHolder>() {

        inner class ThumbnailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val image: ImageView = itemView.findViewById(R.id.thumbnailImage)
            val border: View = itemView.findViewById(R.id.selectedBorder)
            init {
                border.visibility = View.GONE
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_thumbnail, parent, false)
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                parent.resources.getDimensionPixelSize(R.dimen.chat_gallery_thumb_size).coerceAtLeast(64)
            )
            return ThumbnailViewHolder(view)
        }

        override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
            val url = urls[position]
            Glide.with(holder.itemView.context)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(holder.image)
            holder.itemView.setOnClickListener { onThumbnailClick(position) }
        }

        override fun getItemCount() = urls.size.coerceAtMost(4)
    }
}
