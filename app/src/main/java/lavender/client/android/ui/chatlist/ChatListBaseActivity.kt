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
import lavender.client.android.ui.widget.LoginBottomSheet
import lavender.client.android.ui.widget.RegisterBottomSheet
import lavender.client.android.ui.widget.ServerAuthBottomSheet

/**
 * Base activity for chat list screens (v1 and v2).
 *
 * Provides common functionality:
 * - Theme application
 * - Server connection
 * - Auth dialogs
 * - Navigation to chats (favorites, hermes, owl, regular)
 * - Settings navigation
 * - Back press handling
 *
 * v1 (ChatListActivity): extends this + adds its own toolbar, adapter, chat loading
 * v2 (ChatListActivityV2): extends this + adds ViewModel, sections, selection mode
 */
abstract class ChatListBaseActivity : AppCompatActivity() {

    protected var currentUsername: String = ""
    protected var currentPassword: String = ""

    // ======== Lifecycle ========

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

    // ======== Theme ========

    protected fun applyTheme(username: String) {
        try {
            lavender.client.android.theme.ui.ThemeApplier.apply(this, username)
        } catch (e: Exception) {
            // Theme application should not crash the activity
        }
    }

    // ======== Server Connection ========

    protected fun connectToServer(): Boolean {
        val serverAddress = CredentialStore.getServerAddress(this) ?: return false
        val parts = serverAddress.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
        if (GrpcClient.isConnectedTo(host, port)) return true
        GrpcClient.connect(host, false, port, this)
        return true
    }

    // ======== Auth Dialogs ========

    protected fun showAuthChoiceDialog() {
        val serverAddress = CredentialStore.getServerAddress(this)
        if (serverAddress.isNullOrEmpty()) { finish(); return }
        val parts = serverAddress.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
        ServerAuthBottomSheet(host, port) { action ->
            when (action) {
                ServerAuthBottomSheet.Action.LOGIN -> showLoginBottomSheet()
                ServerAuthBottomSheet.Action.REGISTER -> showRegisterBottomSheet()
            }
        }.show(supportFragmentManager, "auth_choice")
    }

    protected fun showLoginBottomSheet() {
        LoginBottomSheet(currentUsername) { username, password ->
            currentUsername = username
            currentPassword = password
            performLogin(username, password)
        }.show(supportFragmentManager, "login")
    }

    protected fun showRegisterBottomSheet() {
        RegisterBottomSheet { username, password, _ ->
            currentUsername = username
            currentPassword = password
            performRegister(username, password)
        }.show(supportFragmentManager, "register")
    }

    protected open fun performLogin(username: String, password: String) {}
    protected open fun performRegister(username: String, password: String) {}

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

    // ======== Back Press ========

    protected fun handleOnBackPressed(): Boolean = false
}
