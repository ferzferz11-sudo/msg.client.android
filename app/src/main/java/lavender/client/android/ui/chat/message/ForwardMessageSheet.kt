package lavender.client.android.ui.chat.message

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import de.hdodenhof.circleimageview.CircleImageView
import lavender.client.android.R
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.models.Message
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import org.json.JSONArray

/**
 * Improved Forward Message Bottom Sheet with:
 * - Search bar for filtering chats
 * - Multi-chat selection
 * - Confirmation before sending
 * - Message preview
 */
class ForwardMessageSheet(
    private val activity: android.app.Activity,
    private val messages: List<Message>,
    private val currentUsername: String,
    private val avatarCache: Map<String, String>,
    private val onForward: (List<ChatInfo>, List<Message>) -> Unit
) {
    private var dialog: BottomSheetDialog? = null
    private var adapter: ForwardChatListAdapter? = null
    private val selectedChats = mutableSetOf<String>()

    fun show() {
        dialog = BottomSheetDialog(activity)

        val view = LayoutInflater.from(activity).inflate(R.layout.sheet_forward_message, null, false)
        dialog?.setContentView(view)

        // Theme
        val theme = ThemeStore.currentTheme()
        val bgColor = ThemeUtils.parseSafeColor(theme.backgroundColor, Color.BLACK)
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val primaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)

        view.setBackgroundColor(bgColor)

        // Title with message count
        val tvTitle = view.findViewById<TextView>(R.id.tvForwardTitle)
        tvTitle.text = activity.getString(R.string.forward_to)
        tvTitle.setTextColor(txtColor)

        val tvMessageCount = view.findViewById<TextView>(R.id.tvMessageCount)
        tvMessageCount.text = activity.resources.getQuantityString(
            R.plurals.forward_message_count, messages.size, messages.size
        )
        tvMessageCount.setTextColor(ThemeUtils.adjustAlpha(txtColor, 0.7f))

        // Message preview
        val previewContainer = view.findViewById<LinearLayout>(R.id.messagePreviewContainer)
        previewContainer.removeAllViews()
        val previewMessages = messages.take(3)
        previewMessages.forEach { msg ->
            val previewView = LayoutInflater.from(activity).inflate(R.layout.item_forward_preview, previewContainer, false)
            val tvPreviewText = previewView.findViewById<TextView>(R.id.tvPreviewText)
            val tvPreviewUser = previewView.findViewById<TextView>(R.id.tvPreviewUser)
            val ivMedia = previewView.findViewById<ImageView>(R.id.ivPreviewMedia)

            val displayText = when {
                msg.text.isNotEmpty() -> msg.text.take(100)
                msg.stickerUrl.isNotEmpty() -> activity.getString(R.string.sticker_image)
                msg.imageUrl.isNotEmpty() -> activity.getString(R.string.photo)
                msg.voiceUrl.isNotEmpty() -> activity.getString(R.string.voice_message)
                msg.isForwarded && msg.forwardedFrom.isNotEmpty() -> activity.getString(R.string.forwarded_from, msg.forwardedFrom)
                else -> ""
            }
            tvPreviewText.text = displayText
            tvPreviewText.setTextColor(txtColor)
            tvPreviewUser.text = msg.user
            tvPreviewUser.setTextColor(ThemeUtils.adjustAlpha(txtColor, 0.6f))

            // Show thumbnail for image/sticker
            val thumbUrl = when {
                msg.imageUrl.isNotEmpty() -> msg.imageUrl
                msg.stickerThumbnailUrl.isNotEmpty() -> msg.stickerThumbnailUrl
                msg.stickerUrl.isNotEmpty() -> msg.stickerUrl
                else -> null
            }
            if (thumbUrl != null) {
                ivMedia?.visibility = View.VISIBLE
                com.bumptech.glide.Glide.with(activity.applicationContext)
                    .load(thumbUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(ivMedia!!)
            }

            previewContainer.addView(previewView)
        }
        if (messages.size > 3) {
            val moreView = TextView(activity).apply {
                text = activity.getString(R.string.forward_more_messages, messages.size - 3)
                setTextColor(ThemeUtils.adjustAlpha(txtColor, 0.6f))
                setPadding(16, 8, 16, 8)
            }
            previewContainer.addView(moreView)
        }

        // Search
        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        searchInput.setTextColor(txtColor)
        searchInput.setHintTextColor(ThemeUtils.adjustAlpha(txtColor, 0.5f))
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter?.filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Chat list
        val recyclerView = view.findViewById<RecyclerView>(R.id.chatsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(activity)

        adapter = ForwardChatListAdapter(
            currentUsername = currentUsername,
            avatarCache = avatarCache,
            selectedIds = selectedChats,
            onSelectionChanged = { updateSendButton(view) }
        )
        recyclerView.adapter = adapter

        // Send button
        val btnSend = view.findViewById<MaterialButton>(R.id.btnForward)
        btnSend.setBackgroundColor(primaryColor)
        btnSend.setTextColor(ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE))
        btnSend.isEnabled = false
        btnSend.setOnClickListener {
            val selected = adapter?.getSelectedChats() ?: emptyList()
            if (selected.isEmpty()) return@setOnClickListener
            onForward(selected, messages)
            dialog?.dismiss()
        }

        // Cancel button
        val btnCancel = view.findViewById<ImageView>(R.id.btnCancel)
        btnCancel.setOnClickListener { dialog?.dismiss() }

        dialog?.show()
    }

    private fun updateSendButton(view: View) {
        val btnSend = view.findViewById<MaterialButton>(R.id.btnForward)
        val count = selectedChats.size
        btnSend.isEnabled = count > 0
        btnSend.text = if (count > 0) {
            activity.getString(R.string.forward_send_count, count)
        } else {
            activity.getString(R.string.forward_send)
        }
    }

    @Suppress("Unused")
    fun loadChats(chats: List<ChatInfo>) {
        val filtered = chats.filter { it.id != activity.getString(R.string.favorites) }
        adapter?.submitList(filtered)
    }

    /**
     * ListAdapter for chat selection with multi-select support
     */
    inner class ForwardChatListAdapter(
        private val currentUsername: String,
        private val avatarCache: Map<String, String>,
        private val selectedIds: MutableSet<String>,
        private val onSelectionChanged: () -> Unit
    ) : RecyclerView.Adapter<ForwardChatListAdapter.ViewHolder>() {

        private var allChats = listOf<ChatInfo>()
        private var filteredChats = listOf<ChatInfo>()

        @Suppress("NotifyDataSetChanged")
        fun submitList(chats: List<ChatInfo>) {
            allChats = chats
            filteredChats = chats
            notifyDataSetChanged()
        }

        @Suppress("NotifyDataSetChanged")
        fun filter(query: String) {
            val q = query.lowercase()
            filteredChats = if (q.isEmpty()) {
                allChats
            } else {
                allChats.filter { chat ->
                    chat.getDisplayName(currentUsername).lowercase().contains(q) ||
                    chat.lastMessageText.lowercase().contains(q)
                }
            }
            notifyDataSetChanged()
        }

        fun getSelectedChats(): List<ChatInfo> {
            return allChats.filter { selectedIds.contains(it.id) }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_forward_chat, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(filteredChats[position])
        }

        override fun getItemCount(): Int = filteredChats.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val chatName: TextView = view.findViewById(R.id.tvChatName)
            private val chatType: TextView = view.findViewById(R.id.tvChatType)
            private val avatarContainer: LinearLayout = view.findViewById(R.id.avatarContainer)
            private val checkIndicator: ImageView = view.findViewById(R.id.checkIndicator)
            private val cardView: MaterialCardView = view as MaterialCardView

            fun bind(chat: ChatInfo) {
                val theme = ThemeStore.currentTheme()
                val isSelected = selectedIds.contains(chat.id)

                chatName.text = chat.getDisplayName(currentUsername)
                chatName.setTextColor(theme.textPrimaryColor.toColorInt())

                chatType.text = when (chat.type) {
                    "direct" -> itemView.context.getString(R.string.direct_chat_type)
                    "group" -> itemView.context.getString(R.string.group_chat_type)
                    else -> chat.type.replaceFirstChar { it.uppercase() }
                }
                chatType.setTextColor(ThemeUtils.adjustAlpha(theme.textPrimaryColor.toColorInt(), 0.6f))

                // Selection state
                if (isSelected) {
                    cardView.setCardBackgroundColor(ThemeUtils.adjustAlpha(theme.primaryColor.toColorInt(), 0.2f))
                    checkIndicator.visibility = View.VISIBLE
                    checkIndicator.imageTintList = android.content.res.ColorStateList.valueOf(theme.primaryColor.toColorInt())
                } else {
                    cardView.setCardBackgroundColor(theme.surfaceColor.toColorInt())
                    checkIndicator.visibility = View.GONE
                }

                // Load avatar
                avatarContainer.removeAllViews()
                loadAvatar(chat)

                itemView.setOnClickListener {
                    if (isSelected) {
                        selectedIds.remove(chat.id)
                    } else {
                        selectedIds.add(chat.id)
                    }
                    notifyItemChanged(bindingAdapterPosition)
                    onSelectionChanged()
                }
            }

            private fun loadAvatar(chat: ChatInfo) {
                val context = itemView.context

                if (chat.type == "favorites") {
                    val avatar = ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx())
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                        setImageResource(R.drawable.ic_star)
                        val theme = ThemeStore.currentTheme()
                        imageTintList = android.content.res.ColorStateList.valueOf(theme.primaryColor.toColorInt())
                        val p = 8.dpToPx()
                        setPadding(p, p, p, p)
                        background = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(ThemeUtils.adjustAlpha(theme.primaryColor.toColorInt(), 0.15f))
                        }
                    }
                    avatarContainer.addView(avatar)
                    return
                }

                if (chat.avatarUrl.isNotEmpty()) {
                    val iv = CircleImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx())
                    }
                    Glide.with(context).load(chat.avatarUrl).placeholder(R.drawable.ic_default_avatar).into(iv)
                    avatarContainer.addView(iv)
                } else {
                    try {
                        val arr = JSONArray(chat.participants)
                        for (i in 0 until arr.length().coerceAtMost(if (chat.type == "direct") 1 else 3)) {
                            val u = arr.getString(i)
                            if (chat.type == "direct" && u == currentUsername && arr.length() > 1) continue

                            val iv = CircleImageView(context).apply {
                                val size = 48.dpToPx()
                                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                                    if (i > 0) marginStart = (-12).dpToPx()
                                }
                                borderWidth = 2.dpToPx()
                                borderColor = ThemeStore.currentTheme().surfaceColor.toColorInt()
                            }

                            val url = avatarCache[u]
                            if (!url.isNullOrEmpty()) {
                                Glide.with(context).load(url).placeholder(R.drawable.ic_default_avatar).into(iv)
                            } else {
                                ThemeUtils.applyDefaultAvatar(iv, ThemeStore.currentTheme())
                            }
                            avatarContainer.addView(iv)
                        }
                    } catch (_: Exception) {
                        val iv = CircleImageView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx())
                        }
                        ThemeUtils.applyDefaultAvatar(iv, ThemeStore.currentTheme())
                        avatarContainer.addView(iv)
                    }
                }
            }

            private fun Int.dpToPx(): Int = (this * itemView.resources.displayMetrics.density).toInt()
        }
    }
}
