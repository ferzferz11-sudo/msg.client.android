package lavender.client.android.ui.chatlist

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.session.SessionManager
import lavender.client.android.ui.hermes.HermesChatActivity
import lavender.client.android.ui.owl.OwlChatActivity

/**
 * Base activity for chat list screens (v1 and v2).
 *
 * Provides common functionality:
 * - Navigation to chats (favorites, hermes, owl, regular via subclass override)
 * - Settings navigation
 *
 * v1 (ChatListActivity): extends this + adds its own toolbar, adapter, chat loading
 * v2 (ChatListActivityV2): extends this + adds ViewModel, sections, selection mode
 */
abstract class ChatListBaseActivity : AppCompatActivity() {

    protected var currentUsername: String = ""
    protected var currentPassword: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentUsername = SessionManager.session.value.username
        currentPassword = SessionManager.session.value.password
    }

    override fun onResume() {
        super.onResume()
        currentUsername = SessionManager.session.value.username
        currentPassword = SessionManager.session.value.password
    }

    // ======== Navigation ========

    protected fun navigateToChat(chat: ChatInfo, username: String) {
        when (chat.type) {
            "favorites" -> navigateToFavorites(chat, username)
            "hermes" -> navigateToHermes(chat)
            "owl" -> navigateToOwl(chat)
            else -> navigateToRegularChat(chat, username)
        }
    }

    protected open fun navigateToRegularChat(chat: ChatInfo, username: String) {}

    protected fun navigateToFavorites(chat: ChatInfo, username: String) {
        startActivity(Intent(this, lavender.client.android.NewChatActivity::class.java).apply {
            putExtra("USERNAME", username)
            putExtra("CHAT_NAME", getString(R.string.favorites))
            putExtra("ROOM_ID", "favorites_$username")
            putExtra("IS_DIRECT", false)
            putExtra("PARTICIPANTS", "[\"$username\"]")
            putExtra("CREATOR", username)
        })
    }

    protected fun navigateToHermes(chat: ChatInfo) {
        startActivity(Intent(this, HermesChatActivity::class.java).apply {
            putExtra("CHAT_ID", chat.id)
            putExtra("CHAT_NAME", chat.name)
            putExtra("ACTIVE_AGENT_ID", chat.activeAgentId)
            putExtra("AGENT_MODE", chat.agentMode)
        })
    }

    protected fun navigateToOwl(chat: ChatInfo) {
        startActivity(Intent(this, OwlChatActivity::class.java).apply {
            putExtra("CHAT_ID", chat.id)
            putExtra("CHAT_NAME", chat.name)
        })
    }

    protected fun openHermesSettings() {
        startActivity(Intent(this, lavender.client.android.ui.owl.OwlSettingsActivity::class.java).apply {
            putExtra("isHermes", true)
        })
    }

    protected fun openOwlSettings() {
        startActivity(Intent(this, lavender.client.android.ui.owl.OwlSettingsActivity::class.java).apply {
            putExtra("isHermes", false)
        })
    }

    protected fun openHermesChat(chatId: String, chatName: String) {
        startActivity(Intent(this, HermesChatActivity::class.java).apply {
            putExtra("CHAT_ID", chatId)
            putExtra("CHAT_NAME", chatName)
        })
    }

    protected fun openOwlChat(chatId: String, chatName: String) {
        startActivity(Intent(this, OwlChatActivity::class.java).apply {
            putExtra("CHAT_ID", chatId)
            putExtra("CHAT_NAME", chatName)
        })
    }
}
