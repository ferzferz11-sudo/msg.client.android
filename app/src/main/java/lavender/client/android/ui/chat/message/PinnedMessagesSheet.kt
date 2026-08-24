package lavender.client.android.ui.chat.message

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.ui.adapter.PinnedMessageAdapter

class PinnedMessagesSheet(
    private val activity: androidx.appcompat.app.AppCompatActivity,
    private val grpcClient: GrpcClient
) : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: PinnedMessageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_pinned_messages, container, false)
        recyclerView = view.findViewById(R.id.recyclerView)
        tvEmpty = view.findViewById(R.id.tvEmpty)
        setupRecyclerView()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadPinnedMessages()
    }

    private fun setupRecyclerView() {
        adapter = PinnedMessageAdapter { message ->
            // Scroll to message in NewChatActivity if possible
            (activity as? lavender.client.android.NewChatActivity)?.let {
                // it.scrollToMessage(message.id) // Need to make this public or handle it
                android.util.Log.d("PinnedMessagesSheet", "Clicked pinned message: ${message.id}")
            }
            dismiss()
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
    }

    private fun loadPinnedMessages() {
        val roomId = grpcClient.currentRoomId
        if (roomId.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val pinned = grpcClient.getPinnedMessages(roomId)
                if (pinned.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    tvEmpty.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    adapter.submitList(pinned)
                }
            } catch (e: Exception) {
                tvEmpty.text = e.message
                tvEmpty.visibility = View.VISIBLE
            }
        }
    }

    fun show() {
        show(activity.supportFragmentManager, "PinnedMessagesSheet")
    }
}
