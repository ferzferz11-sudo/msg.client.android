package lavender.client.android.ui.chatlist

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import lavender.client.android.ChatListActivity
import lavender.client.android.R
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.ProfileClient
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.session.SessionManager
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.theme.ui.ThemeUi

/**
 * ChatListActivityV2 — новый Activity для v2 серверов (ChatList v2 API).
 *
 * Определяет версию сервера через fetchServerInfo() и:
 * - v2 сервер (chat >= "2.0"): показывает ChatListFragmentV2 с секциями/табами
 * - v1 сервер (chat < "2.0"): fallback на ChatListActivity (v1)
 *
 * v1 файлы (ChatListActivity.kt, ChatAdapter.kt) НЕ ИЗМЕНЯЮТСЯ.
 */
class ChatListActivityV2 : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatListActivityV2"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SessionManager.initFromPrefs(this)
        applyTheme()

        val serverAddress = CredentialStore.getServerAddress(this) ?: ""

        if (serverAddress.isEmpty()) {
            Log.w(TAG, "No server address — falling back to v1 ChatListActivity")
            fallbackToV1()
            return
        }

        val parts = serverAddress.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051

        // Check server version before setting up UI
        lifecycleScope.launch {
            try {
                GrpcClient.fetchServerInfo(this@ChatListActivityV2, host, if (port == 50051) 8082 else 8083)

                if (ProfileClient.isChatV2Supported()) {
                    Log.d(TAG, "v2 server detected — using ChatListFragmentV2")
                    setupV2UI()
                } else {
                    Log.d(TAG, "v1 server detected — falling back to v1 ChatListActivity")
                    fallbackToV1()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to determine server version — falling back to v1", e)
                fallbackToV1()
            }
        }
    }

    private fun setupV2UI() {
        setContentView(R.layout.activity_chat_list_v2)

        val username = SessionManager.session.value.username
        val password = SessionManager.session.value.password

        if (username.isEmpty() || password.isEmpty()) {
            showAuthChoiceDialog()
            return
        }

        ThemeUi.bind(this, username)

        // Connect to server
        val serverAddress = CredentialStore.getServerAddress(this) ?: return
        val parts = serverAddress.split(":")
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 50051
        GrpcClient.connect(host, false, port, this)

        // Observe connection status
        lifecycleScope.launch {
            GrpcClient.connectionStatus.collect { status ->
                if (status == lavender.client.android.data.grpc.ConnectionStatus.READY) {
                    Log.d(TAG, "Connected — loading chats")
                }
            }
        }
    }

    private fun fallbackToV1() {
        val intent = Intent(this, ChatListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showAuthChoiceDialog() {
        // Reuse v1 auth widgets — they work for both v1 and v2
        lavender.client.android.ui.widget.ServerAuthBottomSheet(
            context = this,
            onLoginClick = { },
            onRegisterClick = { }
        ).show()
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("ThemePrefs", MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )
        ThemeApplier.apply(this)
    }
}
