package lavender.client.android.ui.chatlist

import android.content.Intent
import lavender.client.android.NewChatActivity
import lavender.client.android.R
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.session.CredentialStore

/**
 * ChatListNavigation — navigation helpers for ChatListActivity.
 * Extracted from ChatListActivity to reduce its size.
 */

internal fun navigateToChat(activity: ChatListActivity, chat: ChatInfo, username: String) {
    val displayName = chat.getDisplayName(username)
    when (chat.type) {
        "favorites" -> {
            val intent = Intent(activity, NewChatActivity::class.java).apply {
                putExtra("USERNAME", username)
                putExtra("CHAT_NAME", activity.getString(R.string.favorites))
                putExtra("ROOM_ID", "favorites_$username")
                putExtra("IS_DIRECT", false)
                putExtra("PARTICIPANTS", "[\"$username\"]")
                putExtra("CREATOR", username)
            }
            activity.startActivity(intent)
        }
        "hermes" -> {
            // v2: Use unified AI v2 chat
            val intent = Intent(activity, lavender.client.android.ui.ai.AiV2ChatActivity::class.java).apply {
                putExtra("SESSION_ID", chat.id)
                putExtra("AGENT_ID", chat.activeAgentId)
                putExtra("AGENT_NAME", displayName)
            }
            activity.startActivity(intent)
        }
        "owl" -> {
            // v2: Use unified AI v2 chat
            val intent = Intent(activity, lavender.client.android.ui.ai.AiV2ChatActivity::class.java).apply {
                putExtra("SESSION_ID", chat.id)
                putExtra("AGENT_NAME", displayName)
            }
            activity.startActivity(intent)
        }
        else -> {
            val serverAddress = CredentialStore.getServerAddress(activity) ?: ""
            val intent = Intent(activity, NewChatActivity::class.java).apply {
                putExtra("USERNAME", username)
                putExtra("SERVER_ADDRESS", serverAddress)
                putExtra("CHAT_NAME", displayName)
                putExtra("ROOM_ID", chat.id)
                putExtra("IS_DIRECT", chat.type == "direct" || chat.isSecret)
                putExtra("CHAT_TYPE", chat.type)
                putExtra("PARTICIPANTS", chat.participants)
                putExtra("AVATAR_URL", chat.avatarUrl)
                putExtra("FULL_AVATAR_URL", chat.fullAvatarUrl)
                putExtra("CREATOR", chat.creator)
                putExtra("IS_SECRET", chat.isSecret.toString())
                putExtra("IS_MUTED", chat.isMuted)
            }
            activity.startActivity(intent)
        }
    }
}
