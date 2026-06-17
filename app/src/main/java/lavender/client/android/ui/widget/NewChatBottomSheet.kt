package lavender.client.android.ui.widget

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import lavender.client.android.NewChatActivity
import lavender.client.android.R
import lavender.client.android.ui.chatlist.ChatListActivity

/**
 * NewChatBottomSheet — шторка создания нового чата при тапе на FAB +.
 * Пункты: Add Contact, Start Chat, Group, Secret Chat.
 * AI чаты — отдельно через FAB AI (робот).
 */
class NewChatBottomSheet : BottomSheetDialogFragment() {

    companion object {
        fun newInstance() = NewChatBottomSheet()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_new_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        // Add Contact
        view.findViewById<View>(R.id.btnAddContact)?.setOnClickListener {
            dismiss()
            (activity as? ChatListActivity)?.showAddContactDialog()
        }

        // Start Chat (New Private Chat)
        view.findViewById<View>(R.id.btnNewChat)?.setOnClickListener {
            dismiss()
            context.startActivity(Intent(context, NewChatActivity::class.java))
        }

        // New Group
        view.findViewById<View>(R.id.btnNewGroup)?.setOnClickListener {
            dismiss()
            val intent = Intent(context, NewChatActivity::class.java).apply {
                putExtra("CREATE_GROUP", true)
            }
            context.startActivity(intent)
        }

        // Secret Chat
        view.findViewById<View>(R.id.btnSecretChat)?.setOnClickListener {
            dismiss()
            val intent = Intent(context, NewChatActivity::class.java).apply {
                putExtra("SECRET_CHAT", true)
            }
            context.startActivity(intent)
        }
    }
}
