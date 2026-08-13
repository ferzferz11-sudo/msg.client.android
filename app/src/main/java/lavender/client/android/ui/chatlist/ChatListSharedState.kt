package lavender.client.android.ui.chatlist

/**
 * Shared state between NewChatActivity and ChatListActivity/ChatListViewModel.
 * Used to propagate mute changes made inside a chat back to the chat list.
 */
object ChatListSharedState {
    /**
     * Pending mute update: Pair(roomId, isMuted).
     * Set by NewChatActivity when mute is toggled.
     * Consumed by ChatListViewModel.loadChats() to update allChats before displaying.
     */
    @Volatile
    var pendingMuteUpdate: Pair<String, Boolean>? = null
}
