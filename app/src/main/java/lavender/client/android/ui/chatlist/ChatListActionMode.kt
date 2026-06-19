package lavender.client.android.ui.chatlist

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.grpc.*

/**
 * ActionMode (Selection Mode) for ChatListActivity.
 * Extracted to reduce ChatListActivity size.
 */
internal fun createActionModeCallback(activity: ChatListActivity): ActionMode.Callback {
    return object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            activity.actionMode = mode
            mode.menuInflater.inflate(R.menu.chat_list_action_mode, menu)
            val typedValue = android.util.TypedValue()
            activity.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
            val iconColor = typedValue.data
            menu.findItem(R.id.action_pin)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
            menu.findItem(R.id.action_mute)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
            menu.findItem(R.id.action_archive)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
            menu.findItem(R.id.action_delete)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            val selectedChats = activity.chatAdapter.getSelectedChats()
            if (selectedChats.isEmpty()) return false

            return when (item.itemId) {
                R.id.action_pin -> {
                    pinSelectedChats(activity, selectedChats)
                    true
                }
                R.id.action_mute -> {
                    muteSelectedChats(activity, selectedChats)
                    true
                }
                R.id.action_archive -> {
                    archiveSelectedChats(activity, selectedChats)
                    true
                }
                R.id.action_delete -> {
                    deleteSelectedChats(activity, selectedChats)
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            activity.actionMode = null
            activity.chatAdapter.clearSelection()
        }
    }
}

internal fun updateActionModeTitle(activity: ChatListActivity) {
    val count = activity.chatAdapter.getSelectedIds().size
    activity.actionMode?.title = activity.getString(R.string.selected_count, count)
}

internal fun pinSelectedChats(activity: ChatListActivity, chats: List<ChatInfo>) {
    activity.lifecycleScope.launch {
        var pinned = 0
        var unpinned = 0
        for (chat in chats) {
            if (chat.isPinned) {
                if (GrpcClient.unpinChat(activity, chat.id)) unpinned++
            } else {
                if (GrpcClient.pinChat(activity, chat.id)) pinned++
            }
        }
        if (pinned > 0 || unpinned > 0) {
            activity.viewModel.loadChats()
        }
        activity.actionMode?.finish()
    }
}

internal fun muteSelectedChats(activity: ChatListActivity, chats: List<ChatInfo>) {
    activity.lifecycleScope.launch {
        for (chat in chats) {
            activity.viewModel.toggleMute(chat.id, !chat.isMuted)
        }
        activity.actionMode?.finish()
    }
}

internal fun archiveSelectedChats(activity: ChatListActivity, chats: List<ChatInfo>) {
    activity.lifecycleScope.launch {
        var archived = 0
        var unarchived = 0
        for (chat in chats) {
            if (chat.isArchived) {
                if (GrpcClient.unarchiveChat(activity, chat.id)) unarchived++
            } else {
                if (GrpcClient.archiveChat(activity, chat.id)) archived++
            }
        }
        if (archived > 0 || unarchived > 0) {
            activity.viewModel.loadChats()
        }
        activity.actionMode?.finish()
    }
}

internal fun deleteSelectedChats(activity: ChatListActivity, chats: List<ChatInfo>) {
    activity.lifecycleScope.launch {
        var deleted = 0
        for (chat in chats) {
            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                activity.viewModel.deleteChat(chat.id) {
                    if (cont.isActive) cont.resumeWith(Result.success(Unit))
                }
            }
            deleted++
        }
        if (deleted > 0) {
            activity.viewModel.loadChats()
        }
        activity.actionMode?.finish()
    }
}
