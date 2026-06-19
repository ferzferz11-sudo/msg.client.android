package lavender.client.android.ui.chatlist

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
    // Hide normal toolbar content
    activity.ivToolbarUserAvatar?.isVisible = false
    activity.llToolbarTitleContainer?.isVisible = false
    // Inflate selection menu into toolbar
    activity.toolbar?.menu?.clear()
    activity.toolbar?.inflateMenu(R.menu.chat_list_action_mode)
    val typedValue = android.util.TypedValue()
    activity.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
    val iconColor = typedValue.data
    activity.toolbar?.menu?.findItem(R.id.action_pin)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
    activity.toolbar?.menu?.findItem(R.id.action_mute)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
    activity.toolbar?.menu?.findItem(R.id.action_archive)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
    activity.toolbar?.menu?.findItem(R.id.action_delete)?.iconTintList = android.content.res.ColorStateList.valueOf(iconColor)
    activity.toolbar?.setOnMenuItemClickListener { item ->
        onMenuItemClicked(activity, item)
    }
    // Back arrow
    activity.toolbar?.setNavigationIcon(R.drawable.ic_back_arrow)
    activity.toolbar?.navigationIcon?.let {
        val wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(it)
        val theme = lavender.client.android.theme.ThemeStore.currentTheme()
        val navColor = try { theme.onPrimaryColor.toInt() } catch (_: Exception) { iconColor }
        androidx.core.graphics.drawable.DrawableCompat.setTint(wrapped, navColor)
        activity.toolbar?.navigationIcon = wrapped
    }
    activity.toolbar?.setNavigationOnClickListener { exitSelectionMode(activity) }
    // Update title to selection count
    updateActionModeTitle(activity)
}

internal fun exitSelectionMode(activity: ChatListActivity) {
    activity.isSelectionMode = false
    activity.chatAdapter.clearSelection()
    // Restore toolbar
    activity.ivToolbarUserAvatar?.isVisible = true
    activity.llToolbarTitleContainer?.isVisible = true
    activity.toolbar?.menu?.clear()
    activity.toolbar?.setNavigationIcon(null)
    activity.toolbar?.setNavigationOnClickListener(null)
    activity.toolbar?.setOnMenuItemClickListener(null)
    setupSearchMenu(activity)
    activity.tvToolbarTitle?.text = activity.getString(R.string.chats)
}

internal fun updateActionModeTitle(activity: ChatListActivity) {
    val count = activity.chatAdapter.getSelectedIds().size
    activity.tvToolbarTitle?.text = activity.getString(R.string.selected_count, count)
}

private fun onMenuItemClicked(activity: ChatListActivity, item: MenuItem): Boolean {
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
                if (GrpcClient.unarchiveChat(activity, chat.id)) unarchived++
            } else {
                if (GrpcClient.archiveChat(activity, chat.id)) archived++
            }
        }
        if (archived > 0 || unarchived > 0) {
            activity.viewModel.loadChats()
        }
        exitSelectionMode(activity)
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
        exitSelectionMode(activity)
    }
}
