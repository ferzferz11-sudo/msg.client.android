package lavender.client.android.ui.widget

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import lavender.client.android.NewChatActivity
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.AIChatInfo
import lavender.client.android.ui.chatlist.ChatListActivity
import lavender.client.android.ui.hermes.HermesChatActivity
import lavender.client.android.ui.owl.OwlChatActivity

/**
 * NewChatBottomSheet — шторка создания нового чата при тапе на FAB +.
 * Варианты: Новый чат, Группа, Hermes AI, OWL AI.
 */
class NewChatBottomSheet : BottomSheetDialogFragment() {

    companion object {
        fun newInstance() = NewChatBottomSheet()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_new_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view)

        val context = requireContext()

        // New private chat
        view.findViewById<View>(R.id.btnNewChat)?.setOnClickListener {
            dismiss()
            context.startActivity(Intent(context, NewChatActivity::class.java))
        }

        // New group
        view.findViewById<View>(R.id.btnNewGroup)?.setOnClickListener {
            dismiss()
            val intent = Intent(context, NewChatActivity::class.java).apply {
                putExtra("CREATE_GROUP", true)
            }
            context.startActivity(intent)
        }

        // Hermes AI
        view.findViewById<View>(R.id.btnHermesAI)?.setOnClickListener {
            dismiss()
            val intent = Intent(context, HermesChatActivity::class.java).apply {
                putExtra("chatId", "") // Empty = create new
            }
            context.startActivity(intent)
        }

        // OWL AI
        view.findViewById<View>(R.id.btnOwlAI)?.setOnClickListener {
            dismiss()
            val intent = Intent(context, OwlChatActivity::class.java).apply {
                putExtra("chatId", "") // Empty = create new
            }
            context.startActivity(intent)
        }
    }
}
