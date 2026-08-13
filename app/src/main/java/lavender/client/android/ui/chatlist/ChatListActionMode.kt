package lavender.client.android.ui.chatlist

import android.util.Log
import android.view.MenuItem
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.grpc.*

/**
 * Selection mode for ChatListActivity — toolbar-native (no Android ActionMode bar).
 */

internal fun enterSelectionMode(activity: ChatListActivity) {
    activity.isSelectionMode = true
    activity.ivToolbarUserAvatar?.parent?.let { (it as? android.view.View)?.isVisible = false }
    activity.llToolbarTitleContainer?.isVisible = true
    activity.ivFavorites?.isVisible = false
    activity.tvToolbarSubtitle?.isVisible = false
    activity.toolbar?.menu?.clear()
    activity.toolbar?.inflateMenu(R.menu.chat_list_action_mode)
    val typedValue = android.util.TypedValue()
    activity.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
    val iconColor = typedValue.data
    activity.toolbar?.menu?.findItem(R.id.action_mute)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
    activity.toolbar?.menu?.findItem(R.id.action_delete)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
    activity.toolbar?.setOnMenuItemClickListener { item ->
        onMenuItemClicked(activity, item)
    }
    activity.toolbar?.setNavigationIcon(R.drawable.ic_back_arrow)
    activity.toolbar?.navigationIcon?.setTint(iconColor)
    activity.toolbar?.setNavigationOnClickListener { exitSelectionMode(activity) }
    activity.tvToolbarTitle?.setTextColor(iconColor)
    updateActionModeTitle(activity)
    updateActionModeIcons(activity)
}

internal fun exitSelectionMode(activity: ChatListActivity) {
    activity.isSelectionMode = false
    try { activity.chatAdapter.clearSelection() } catch (_: Exception) {}
    // Restore avatar container (FrameLayout wrapper)
    activity.ivToolbarUserAvatar?.parent?.let { (it as? android.view.View)?.isVisible = true }
    activity.llToolbarTitleContainer?.isVisible = true
    activity.ivFavorites?.isVisible = true
    activity.tvToolbarTitle?.text = activity.getString(R.string.chats)
    // Restore title color from theme
    val typedValue = android.util.TypedValue()
    activity.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
    activity.tvToolbarTitle?.setTextColor(typedValue.data)
    activity.toolbar?.menu?.clear()
    activity.toolbar?.setNavigationIcon(null)
    activity.toolbar?.setNavigationOnClickListener(null)
    activity.toolbar?.setOnMenuItemClickListener(null)
    setupSearchMenu(activity)
}

internal fun updateActionModeTitle(activity: ChatListActivity) {
    val count = try { activity.chatAdapter.getSelectedIds().size } catch (_: Exception) { return }
    activity.tvToolbarTitle?.text = activity.getString(R.string.selected_count, count)
    updateActionModeIcons(activity)
}

private fun updateActionModeIcons(activity: ChatListActivity) {
    val selectedChats = try { activity.chatAdapter.getSelectedChats() } catch (_: Exception) { return }
    if (selectedChats.isEmpty()) return
    val allMuted = selectedChats.all { it.isMuted }
    activity.toolbar?.menu?.findItem(R.id.action_mute)?.let { item ->
        item.setTitle(if (allMuted) R.string.action_unmute else R.string.action_mute)
    }
}

private fun onMenuItemClicked(activity: ChatListActivity, item: MenuItem): Boolean {
    val selectedChats = activity.chatAdapter.getSelectedChats()
    if (selectedChats.isEmpty()) return false

    return when (item.itemId) {
        R.id.action_mute -> {
            muteSelectedChats(activity, selectedChats)
            true
        }
        R.id.action_delete -> {
            deleteSelectedChats(activity, selectedChats)
            true
        }
        else -> false
    }
}

internal fun pinSelectedChats(activity: ChatListActivity, chats: List<ChatInfo>) {
    Log.d("ChatListActionMode", "pinSelectedChats: ${chats.size} chats, ids=${chats.map { it.id }}")
    activity.lifecycleScope.launch {
        var pinned = 0
        var unpinned = 0
        for (chat in chats) {
            try {
                if (chat.isPinned) {
                    val result = GrpcClient.unpinChat(chat.id)
                    Log.d("ChatListActionMode", "unpinChat(${chat.id}) = $result")
                    if (result) unpinned++
                } else {
                    val result = GrpcClient.pinChat(chat.id)
                    Log.d("ChatListActionMode", "pinChat(${chat.id}) = $result")
                    if (result) pinned++
                }
            } catch (e: Exception) {
                Log.e("ChatListActionMode", "pin/unpin failed for ${chat.id}", e)
            }
        }
        Log.d("ChatListActionMode", "pinSelectedChats done: pinned=$pinned, unpinned=$unpinned")
        if (pinned > 0 || unpinned > 0) {
            activity.viewModel.loadChats()
        }
        exitSelectionMode(activity)
    }
}

internal fun muteSelectedChats(activity: ChatListActivity, chats: List<ChatInfo>) {
    activity.lifecycleScope.launch {
        for (chat in chats) {
            activity.viewModel.toggleMute(chat.id, !chat.isMuted)
        }
        exitSelectionMode(activity)
    }
}

internal fun archiveSelectedChats(activity: ChatListActivity, chats: List<ChatInfo>) {
    activity.lifecycleScope.launch {
        var archived = 0
        var unarchived = 0
        for (chat in chats) {
            if (chat.isArchived) {
                if (GrpcClient.unarchiveChat(chat.id)) unarchived++
            } else {
                if (GrpcClient.archiveChat(chat.id)) archived++
            }
        }
        if (archived > 0 || unarchived > 0) {
            activity.viewModel.loadChats()
        }
        exitSelectionMode(activity)
    }
}

internal fun deleteSelectedChats(activity: ChatListActivity, chats: List<ChatInfo>) {
    val groupChats = chats.filter { it.type == "group" || it.type == "conference" }
    val nonGroupChats = chats.filter { it.type != "group" && it.type != "conference" }

    if (groupChats.isNotEmpty()) {
        val username = lavender.client.android.data.session.SessionManager.session.value.username
        val creator = groupChats.first().creator
        val isCreator = creator == username

        if (isCreator || nonGroupChats.isEmpty()) {
            // Creator or all group chats — show confirmation with creator info
            val message = if (groupChats.size == 1) {
                val chatName = groupChats.first().name
                activity.getString(R.string.delete_group) + ": \"$chatName\"?\n" +
                    activity.getString(R.string.group_creator) + ": $creator"
            } else {
                activity.getString(R.string.delete_group) + ": ${groupChats.size} " +
                    activity.getString(R.string.chats) + "?"
            }

            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle(R.string.delete_group)
                .setMessage(message)
                .setPositiveButton(R.string.delete) { _, _ ->
                    doDeleteChats(activity, chats)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            // Non-creator — show info about who can delete
            val chatName = groupChats.first().name
            val infoMessage = activity.getString(R.string.cannot_delete_group) + "\n" +
                activity.getString(R.string.group_creator) + ": $creator"

            androidx.appcompat.app.AlertDialog.Builder(activity)
                .setTitle(chatName)
                .setMessage(infoMessage)
                .setPositiveButton(R.string.ok, null)
                .show()
            // Still delete non-group chats if any
            if (nonGroupChats.isNotEmpty()) {
                doDeleteChats(activity, nonGroupChats)
            }
        }
    } else {
        doDeleteChats(activity, chats)
    }
}

private fun doDeleteChats(activity: ChatListActivity, chats: List<ChatInfo>) {
    activity.lifecycleScope.launch {
        var deleted = 0
        for (chat in chats) {
            val errorMessage = kotlinx.coroutines.suspendCancellableCoroutine<String?> { cont ->
                activity.viewModel.deleteChat(chat.id) { errorMsg ->
                    if (cont.isActive) cont.resumeWith(Result.success(errorMsg))
                }
            }
            if (errorMessage != null) {
                android.widget.Toast.makeText(activity, errorMessage, android.widget.Toast.LENGTH_LONG).show()
            } else {
                deleted++
            }
        }
        if (deleted > 0) {
            activity.viewModel.loadChats()
        }
        exitSelectionMode(activity)
    }
}
