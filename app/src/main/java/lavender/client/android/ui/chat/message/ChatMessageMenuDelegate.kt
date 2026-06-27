package lavender.client.android.ui.chat.message

import android.content.ClipData
import android.content.ClipboardManager
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.appcompat.app.AppCompatActivity
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.ui.widget.StandardBottomSheet

/**
 * Message context menu: reactions, reply, copy, edit, delete.
 */
class ChatMessageMenuDelegate(
    private val activity: AppCompatActivity,
    private val grpcClient: GrpcClient
) {
    private var username: String = ""

    fun configure(username: String) {
        this.username = username
    }

    fun showReactionsDialog(m: Message, onReply: (Message) -> Unit) {
        val sheet = StandardBottomSheet(activity, R.layout.dialog_reactions)
        val container = sheet.findViewById<LinearLayout>(R.id.reactionsContainer)

        listOf("👍", "💯", "🔥", "✅", "❤️", "😂", "😮", "😢", "🙏").forEach { e ->
            val tv = TextView(activity).apply {
                text = e
                textSize = 30f
                setPadding(16, 8, 16, 8)
                val v2 = TypedValue()
                activity.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, v2, true)
                setBackgroundResource(v2.resourceId)
                setOnClickListener {
                    grpcClient.setReactionV2(m.id, username, e)
                    sheet.dismiss()
                }
            }
            container?.addView(tv)
        }

        sheet.findViewById<View>(R.id.menuReply)?.setOnClickListener {
            sheet.dismiss()
            onReply(m)
        }

        sheet.findViewById<View>(R.id.menuCopy)?.setOnClickListener {
            sheet.dismiss()
            (activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("message", m.text))
            Toast.makeText(activity, activity.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
        }

        val edit = sheet.findViewById<View>(R.id.menuEdit)
        if (m.user == username) {
            edit?.isVisible = true
            edit?.setOnClickListener {
                sheet.dismiss()
                showEditMessageDialog(m)
            }
        } else {
            edit?.isVisible = false
        }

        sheet.findViewById<View>(R.id.menuDelete)?.setOnClickListener {
            sheet.dismiss()
            grpcClient.deleteMessageV2(listOf(m.id))
        }

        sheet.show()
    }

    private fun showEditMessageDialog(m: Message) {
        val sheet = StandardBottomSheet(activity, R.layout.dialog_edit_message)
        sheet.setTitle(activity.getString(R.string.edit_message))
        val edit = sheet.findViewById<EditText>(R.id.editMessageInput)
        val cancel = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val save = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)

        edit?.setText(m.text)
        edit?.setSelection(m.text.length)
        edit?.requestFocus()

        cancel?.setOnClickListener { sheet.dismiss() }
        save?.setOnClickListener {
            val nt = edit?.text.toString().trim()
            if (nt.isNotEmpty() && nt != m.text) {
                grpcClient.editMessageV2(m.id, nt) { s, msg ->
                    if (!s) activity.runOnUiThread {
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            sheet.dismiss()
        }
        sheet.show()
    }
}
