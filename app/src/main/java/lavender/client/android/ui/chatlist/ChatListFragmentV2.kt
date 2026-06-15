package lavender.client.android.ui.chatlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.databinding.FragmentChatListV2Binding

/**
 * ChatListFragmentV2 — фрагмент с секциями чатов (Pinned/Favorites/All) и табами.
 *
 * Использует ChatAdapterV2 для отображения чатов с секциями.
 * Поддерживает swipe-to-refresh, поиск, контекстное меню.
 */
class ChatListFragmentV2 : Fragment() {

    private var _binding: FragmentChatListV2Binding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ChatListViewModelV2
    private lateinit var chatAdapter: ChatAdapterV2

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatListV2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ChatListViewModelV2::class.java]

        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapterV2(
            scope = viewLifecycleOwner.lifecycleScope,
            onChatClick = { chat ->
                viewModel.onChatClick(chat)
            },
            onChatLongClick = { chat, anchorView ->
                showChatContextMenu(chat, anchorView)
            },
            onSelectionChanged = { count ->
                // Update toolbar if needed
            }
        )

        binding.rvChatList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = chatAdapter
            setHasFixedSize(false)
        }
    }

    private fun setupSwipeRefresh() {
        binding.srlChatList.setOnRefreshListener {
            viewModel.refreshChats()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.sections.collectLatest { sections ->
                chatAdapter.setSections(sections)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { loading ->
                binding.srlChatList.isRefreshing = loading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.connectionStatus.collectLatest { status ->
                // Update subtitle if needed
            }
        }
    }

    private fun showChatContextMenu(chat: ChatInfo, anchorView: View) {
        val popup = android.widget.PopupMenu(requireContext(), anchorView)
        popup.menuInflater.inflate(R.menu.chat_list_context_menu_v2, popup.menu)

        // Update menu item titles based on current state
        popup.menu.findItem(R.id.action_pin)?.title = if (chat.isPinned) {
            getString(R.string.action_unpin)
        } else {
            getString(R.string.action_pin)
        }
        popup.menu.findItem(R.id.action_mute)?.title = if (chat.isMuted) {
            getString(R.string.action_unmute)
        } else {
            getString(R.string.action_mute)
        }

        // Hide pin for favorites
        popup.menu.findItem(R.id.action_pin)?.isVisible = chat.type != "favorites"

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_pin -> {
                    if (chat.isPinned) viewModel.unpinChat(chat.id)
                    else viewModel.pinChat(chat.id)
                    true
                }
                R.id.action_mute -> {
                    viewModel.toggleMute(chat.id, !chat.isMuted)
                    true
                }
                R.id.action_delete -> {
                    viewModel.deleteChat(chat.id)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
