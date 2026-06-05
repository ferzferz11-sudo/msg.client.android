package lavender.client.android.ui.chat.widget

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
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
    val commandButton: ImageButton get() = binding.commandButton
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

    // Search bar
    val searchBar: LinearLayout get() = binding.searchBar
    val searchInput: EditText get() = binding.searchInput
    val searchResultsCount: TextView get() = binding.searchResultsCount
    val searchPrev: ImageButton get() = binding.searchPrev
    val searchNext: ImageButton get() = binding.searchNext

    // Selection toolbar
    val selectionToolbar: LinearLayout get() = binding.selectionToolbar
    val selectionCountText: TextView get() = binding.selectionCountText
    val selectionClose: ImageButton get() = binding.selectionClose
    val selectionReply: ImageButton get() = binding.selectionReply
    val selectionCopy: ImageButton get() = binding.selectionCopy
    val selectionForward: ImageButton get() = binding.selectionForward
    val selectionDelete: ImageButton get() = binding.selectionDelete
    val selectionStar: ImageButton get() = binding.selectionStar

    // Image preview
    val imagePreviewScroll: HorizontalScrollView get() = binding.imagePreviewScroll
    val imagePreviewContainer: LinearLayout get() = binding.imagePreviewContainer

    // Upload progress
    val uploadProgressContainer: LinearLayout get() = binding.uploadProgressContainer
    val uploadProgressBar: ProgressBar get() = binding.uploadProgressBar
    val uploadProgressText: TextView get() = binding.uploadProgressText

    // Mention UI
    val mentionContainer: MaterialCardView get() = binding.mentionContainer
    val mentionList: RecyclerView get() = binding.mentionList

    // Listeners
    private var adapter: ChatMessageAdapter? = null
    private var mentionAdapter: MentionAdapter? = null
    private var onSendMessageListener: ((String) -> Unit)? = null
    private var onCancelReplyListener: (() -> Unit)? = null
    private var onMentionSelectedListener: ((MentionItem) -> Unit)? = null
    private var onAttachClickListener: (() -> Unit)? = null
    private var onAudioClickListener: (() -> Unit)? = null
    private var onSearchQueryListener: ((String) -> Unit)? = null

    // State
    private var searchResults: List<Int> = emptyList()
    private var currentSearchIndex = -1

    fun setOnAttachClickListener(listener: () -> Unit) { onAttachClickListener = listener }
    fun setOnAudioClickListener(listener: () -> Unit) { onAudioClickListener = listener }
    fun setOnSearchQueryListener(listener: (String) -> Unit) { onSearchQueryListener = listener }

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

        attachButton.setOnClickListener {
            onAttachClickListener?.invoke()
        }

        audioButton.setOnClickListener {
            onAudioClickListener?.invoke()
        }

        cancelReply.setOnClickListener {
            hideReplyPreview()
            onCancelReplyListener?.invoke()
        }

        // Search
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                onSearchQueryListener?.invoke(searchInput.text.toString())
                true
            } else false
        }

        searchPrev.setOnClickListener {
            if (searchResults.isNotEmpty()) {
                currentSearchIndex = if (currentSearchIndex <= 0) searchResults.size - 1 else currentSearchIndex - 1
                searchResultsCount.text = "${currentSearchIndex + 1}/${searchResults.size}"
                adapter?.highlightPosition(searchResults[currentSearchIndex])
            }
        }

        searchNext.setOnClickListener {
            if (searchResults.isNotEmpty()) {
                currentSearchIndex = if (currentSearchIndex >= searchResults.size - 1) 0 else currentSearchIndex + 1
                searchResultsCount.text = "${currentSearchIndex + 1}/${searchResults.size}"
                adapter?.highlightPosition(searchResults[currentSearchIndex])
            }
        }

        selectionClose.setOnClickListener {
            selectionToolbar.visibility = View.GONE
        }
    }

    // ===== Public API =====

    fun showSearchBar() {
        searchBar.visibility = View.VISIBLE
        searchInput.requestFocus()
    }

    fun hideSearchBar() {
        searchBar.visibility = View.GONE
        searchInput.text.clear()
        searchResults = emptyList()
        currentSearchIndex = -1
        searchResultsCount.text = ""
        adapter?.highlightPosition(-1)
    }

    fun showSelectionToolbar(count: Int) {
        selectionToolbar.visibility = View.VISIBLE
        selectionCountText.text = "$count"
    }

    fun hideSelectionToolbar() {
        selectionToolbar.visibility = View.GONE
    }

    fun updateSearchResults(results: List<Int>) {
        searchResults = results
        currentSearchIndex = if (results.isNotEmpty()) 0 else -1
        searchResultsCount.text = if (results.isNotEmpty()) "1/${results.size}" else ""
        if (results.isNotEmpty()) {
            adapter?.highlightPosition(results[0])
        }
    }

    fun showUploadProgress(text: String) {
        uploadProgressContainer.visibility = View.VISIBLE
        uploadProgressText.text = text
    }

    fun hideUploadProgress() {
        uploadProgressContainer.visibility = View.GONE
    }

    fun setUploadProgress(progress: Int) {
        uploadProgressBar.progress = progress
    }

    fun addImagePreview(view: View) {
        imagePreviewContainer.addView(view)
        imagePreviewScroll.visibility = View.VISIBLE
    }

    fun clearImagePreviews() {
        imagePreviewContainer.removeAllViews()
        imagePreviewScroll.visibility = View.GONE
    }

    fun setAdapter(adapter: ChatMessageAdapter) {
        this.adapter = adapter
        messagesRecyclerView.adapter = adapter
    }

    fun getAdapter(): ChatMessageAdapter? = adapter

    fun setOnSendMessageListener(listener: (String) -> Unit) {
        onSendMessageListener = listener
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
}
