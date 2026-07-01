package lavender.client.android.ui.chat.message

import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.theme.ThemeStore
import lavender.client.android.ui.adapter.MessageAdapter
import lavender.client.android.ui.widget.StandardBottomSheet

/**
 * Selection mode: toolbar with copy/reply/pin/delete/forward actions.
 */
class ChatSelectionDelegate(
    private val activity: AppCompatActivity,
    private val grpcClient: GrpcClient
) {
    lateinit var selectionToolbar: LinearLayout
    lateinit var selectionCountText: TextView
    lateinit var toolbarContent: View
    lateinit var copyMessages: ImageButton
    lateinit var replyMessage: ImageButton
    lateinit var deleteMessages: ImageButton
    lateinit var forwardMessages: ImageButton

    private var selectionMode = false
    private var pinnedMessageIds = mutableSetOf<String>()
    private var adapter: MessageAdapter? = null
    private var roomId: String = ""
    private var username: String = ""

    var onSelectionModeChanged: ((Boolean) -> Unit)? = null
    var getToolbarDelegate: (() -> ChatToolbarDelegate)? = null
    var onReplySelected: ((Message) -> Unit)? = null

    fun initViews() {
        selectionToolbar = activity.findViewById(R.id.selectionToolbar)
        selectionCountText = activity.findViewById(R.id.selectionCountText)
        toolbarContent = activity.findViewById(R.id.toolbarContent)
        copyMessages = activity.findViewById(R.id.copyMessages)
        replyMessage = activity.findViewById(R.id.replyMessage)
        deleteMessages = activity.findViewById(R.id.deleteMessages)
        forwardMessages = activity.findViewById(R.id.forwardMessages)
    }

    fun configure(roomId: String, username: String) {
        this.roomId = roomId
        this.username = username
    }

    fun setAdapter(adapter: MessageAdapter) {
        this.adapter = adapter
    }

    fun setPinnedMessageIds(ids: Set<String>) {
        pinnedMessageIds = ids.toMutableSet()
    }

    fun setupListeners() {
        copyMessages.setOnClickListener { copySelectedMessages() }
        replyMessage.setOnClickListener { replyToSelectedMessage() }
        deleteMessages.setOnClickListener { deleteSelectedMessages() }
        forwardMessages.setOnClickListener { forwardSelectedMessages() }
    }

    fun enterSelectionMode(m: Message) {
        val p = adapter?.currentList?.indexOf(m) ?: return
        if (p != -1) {
            adapter?.toggleSelectionMode(true)
            adapter?.toggleSelection(p)
            showSelectionToolbar(adapter?.getSelectedMessages()?.size ?: 0)
        }
    }

    fun showSelectionToolbar(count: Int) {
        selectionMode = true
        onSelectionModeChanged?.invoke(true)
        toolbarContent.isVisible = false
        selectionToolbar.isVisible = true
        selectionCountText.text = count.toString()
        getToolbarDelegate?.invoke()?.setNavigationIcon(R.drawable.ic_close)
        replyMessage.isVisible = count == 1
        forwardMessages.isVisible = count > 0
        try {
            selectionToolbar.setBackgroundColor(ThemeStore.currentTheme().primaryColor.toColorInt())
        } catch (_: Exception) {}
    }

    fun hideSelectionToolbar() {
        if (!selectionMode) return
        selectionMode = false
        adapter?.toggleSelectionMode(false)
        onSelectionModeChanged?.invoke(false)
        selectionToolbar.isVisible = false
        toolbarContent.isVisible = true
        getToolbarDelegate?.invoke()?.setNavigationIcon(R.drawable.ic_back_arrow)
    }

    fun isInSelectionMode() = selectionMode

    private fun copySelectedMessages() {
        val sm = adapter?.getSelectedMessages() ?: return
        val tc = sm.joinToString("\n\n") { "${it.user}: ${it.text}" }
        (activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("messages", tc))
        Toast.makeText(activity, activity.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        hideSelectionToolbar()
    }

    private fun replyToSelectedMessage() {
        val sm = adapter?.getSelectedMessages() ?: return
        if (sm.size == 1) {
            onReplySelected?.invoke(sm[0])
            hideSelectionToolbar()
        }
    }

    private fun deleteSelectedMessages() {
        val sm = adapter?.getSelectedMessages() ?: return
        val sheet = StandardBottomSheet(activity, R.layout.dialog_delete_messages)
        sheet.setTitle(activity.getString(R.string.delete_messages_title))
        sheet.findViewById<TextView>(R.id.messageText)?.text =
            activity.getString(R.string.delete_messages_confirm, sm.size)
        sheet.findViewById<View>(R.id.btnCancel)?.setOnClickListener { sheet.dismiss() }
        sheet.findViewById<View>(R.id.btnDelete)?.setOnClickListener {
            val ids = sm.map { it.id }
            grpcClient.deleteMessageV2(ids)
            hideSelectionToolbar()
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun forwardSelectedMessages() {
        val sm = adapter?.getSelectedMessages() ?: return
        if (sm.isEmpty()) { hideSelectionToolbar(); return }
        grpcClient.getChats(username) { page ->
            activity.runOnUiThread {
                val oc = page.chats.toMutableList()
                if (!roomId.startsWith("favorites_")) {
                    oc.add(0, lavender.client.android.data.models.ChatInfo(
                        id = "favorites_$username", name = activity.getString(R.string.favorites), type = "favorites"
                    ))
                }
                val f = oc.filter { it.id != roomId }
                if (f.isEmpty()) {
                    Toast.makeText(activity, activity.getString(R.string.no_other_chats), Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val sheet = lavender.client.android.ui.widget.ListBottomSheet(activity)
                    .setTitle(activity.getString(R.string.forward_to))
                val forwardAdapter = lavender.client.android.ui.adapter.ForwardChatAdapter(
                    chats = f, currentUsername = username, avatarCache = grpcClient.getAvatarCache(),
                    onChatSelected = { target ->
                        sheet.dismiss()
                        sm.forEach { m ->
                            grpcClient.sendMessageV2(Message(
                                user = username, text = m.text, timestamp = System.currentTimeMillis(),
                                roomId = target.id, imageUrl = m.imageUrl, voiceUrl = m.voiceUrl,
                                duration = m.duration, userId = grpcClient.getUserId() ?: ""
                            ))
                        }
                        Toast.makeText(activity, activity.getString(R.string.messages_forwarded), Toast.LENGTH_SHORT).show()
                        hideSelectionToolbar()
                    }
                )
                sheet.setAdapter(forwardAdapter)
                sheet.show()
            }
        }
    }
}
