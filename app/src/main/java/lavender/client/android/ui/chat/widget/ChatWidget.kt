package lavender.client.android.ui.chat.widget

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import de.hdodenhof.circleimageview.CircleImageView
import lavender.client.android.R
import lavender.client.android.ui.widget.StandardBottomSheet
import lavender.client.android.databinding.WidgetChatBinding

/**
 * ChatWidget — переиспользуемый UI компонент чата.
 *
 * Используется в:
 * - NewChatActivity (групповой чат)
 * - HermesChatActivity (агенты как участники группового чата)
 *
 * Предоставляет:
 * - Toolbar с аватаром/иконкой, названием, статусом
 * - RecyclerView с ChatMessageAdapter
 * - Input panel с emoji, input, send
 * - Reply preview
 * - Mention popup (@ agent selection)
 */
class ChatWidget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: WidgetChatBinding =
        WidgetChatBinding.inflate(LayoutInflater.from(context), this, true)

    val messagesRecyclerView: RecyclerView get() = binding.messagesRecyclerView
    val messageInput: EditText get() = binding.messageInput
    val sendButton: ImageButton get() = binding.sendButton
    val emojiButton: ImageButton get() = binding.emojiButton
    val attachButton: ImageButton get() = binding.attachButton
    val audioButton: ImageButton get() = binding.audioButton
    val toolbarTitle: TextView get() = binding.toolbarTitle
    val toolbarSubtitle: TextView get() = binding.toolbarSubtitle
    val toolbarAvatar: CircleImageView get() = binding.toolbarAvatar
    val toolbarAgentIcon: TextView get() = binding.toolbarAgentIcon
    val groupHeader: LinearLayout get() = binding.groupHeader
    val groupParticipantsContainer: LinearLayout get() = binding.groupParticipantsContainer
    val replyPreview: View get() = binding.replyPreview
    val replyUser: TextView get() = binding.replyUser
    val replyText: TextView get() = binding.replyText
    val cancelReply: ImageButton get() = binding.cancelReply
    val bottomPanel: View get() = binding.bottomPanel

    // Mention UI
    val mentionContainer: MaterialCardView get() = binding.mentionContainer
    val mentionList: RecyclerView get() = binding.mentionList

    private var adapter: ChatMessageAdapter? = null
    private var mentionAdapter: MentionAdapter? = null
    private var onSendMessageListener: ((String) -> Unit)? = null
    private var onEmojiClickListener: (() -> Unit)? = null
    private var onCancelReplyListener: (() -> Unit)? = null
    private var onMentionSelectedListener: ((MentionItem) -> Unit)? = null

    init {
        orientation = VERTICAL
        setupRecyclerView()
        setupMentionList()
        setupInput()
    }

    private fun setupRecyclerView() {
        messagesRecyclerView.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
        }
    }

    private fun setupMentionList() {
        mentionList.layoutManager = LinearLayoutManager(context)
        mentionAdapter = MentionAdapter { item ->
            onMentionSelectedListener?.invoke(item)
        }
        mentionList.adapter = mentionAdapter
    }

    private fun setupInput() {
        sendButton.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (text.isNotEmpty()) {
                onSendMessageListener?.invoke(text)
                messageInput.setText("")
            }
        }

        emojiButton.setOnClickListener {
            onEmojiClickListener?.invoke()
        }

        cancelReply.setOnClickListener {
            hideReplyPreview()
            onCancelReplyListener?.invoke()
        }
    }

    // ===== Public API =====

    fun setAdapter(adapter: ChatMessageAdapter) {
        this.adapter = adapter
        messagesRecyclerView.adapter = adapter
    }

    fun getAdapter(): ChatMessageAdapter? = adapter

    fun setOnSendMessageListener(listener: (String) -> Unit) {
        onSendMessageListener = listener
    }

    fun setOnEmojiClickListener(listener: () -> Unit) {
        onEmojiClickListener = listener
    }

    fun setOnCancelReplyListener(listener: () -> Unit) {
        onCancelReplyListener = listener
    }

    fun setOnMentionSelectedListener(listener: (MentionItem) -> Unit) {
        onMentionSelectedListener = listener
    }

    fun setToolbarTitle(title: String) {
        toolbarTitle.text = title
    }

    fun setToolbarSubtitle(subtitle: String, visible: Boolean = true) {
        toolbarSubtitle.text = subtitle
        toolbarSubtitle.visibility = if (visible) View.VISIBLE else View.GONE
        groupHeader.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setToolbarAvatar(visible: Boolean) {
        toolbarAvatar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setToolbarAgentIcon(emoji: String, visible: Boolean = true) {
        toolbarAgentIcon.text = emoji
        toolbarAgentIcon.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun showReplyPreview(user: String, text: String) {
        replyPreview.visibility = View.VISIBLE
        replyUser.text = user
        replyText.text = text
    }

    fun hideReplyPreview() {
        replyPreview.visibility = View.GONE
    }

    fun scrollToBottom() {
        adapter?.let {
            if (it.itemCount > 0) {
                messagesRecyclerView.scrollToPosition(it.itemCount - 1)
            }
        }
    }

    fun addParticipantChip(view: View) {
        groupParticipantsContainer.addView(view)
        groupParticipantsContainer.visibility = View.VISIBLE
        groupHeader.visibility = View.VISIBLE
    }

    fun clearParticipantChips() {
        groupParticipantsContainer.removeAllViews()
    }

    // ===== Mention API =====

    fun showMentionList(items: List<MentionItem>, filter: String = "") {
        mentionAdapter?.setItems(items, filter)
        mentionContainer.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
    }

    fun hideMentionList() {
        mentionContainer.visibility = View.GONE
    }

    fun isMentionListVisible(): Boolean = mentionContainer.visibility == View.VISIBLE

    // ===== Emoji Picker =====

    private val emojiList = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
        "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
        "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤔",
        "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴",
        "🤢", "🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👿", "👹", "👺", "🤡", "💩", "👻", "💀", "☠️", "👽",
        "👾", "🤖", "🎃", "😺", "😸", "😹", "😻", "😼", "😽", "🙀", "😿", "😾", "👋", "🤚", "🖐", "✋", "🖖", "👌", "🤏", "✌️",
        "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲",
        "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦵", "🦿", "🦶",
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟",
        "🔥", "⭐", "🌟", "💫", "💥", "💢", "💦", "💨", "💣", "💬", "💭", "💤"
    )

    fun showEmojiPicker() {
        val ctx = context
        val sheet = StandardBottomSheet(ctx, R.layout.dialog_emoji_picker)
        val emojiGrid = sheet.findViewById<GridLayout>(R.id.emojiGrid) ?: return
        val size = (48 * ctx.resources.displayMetrics.density).toInt()

        for (emoji in emojiList) {
            val tv = TextView(ctx).apply {
                text = emoji
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(size, size)
                val tv2 = TypedValue()
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv2, true)
                setBackgroundResource(tv2.resourceId)
                setOnClickListener {
                    val input = messageInput
                    val cp = input.selectionStart
                    val ct = input.text.toString()
                    input.setText(ct.substring(0, cp) + emoji + ct.substring(cp))
                    input.setSelection(cp + emoji.length)
                    sheet.dismiss()
                }
            }
            emojiGrid.addView(tv)
        }
        sheet.show()
    }
}
