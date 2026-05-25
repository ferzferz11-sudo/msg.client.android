package lavender.client.android

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import lavender.client.android.data.session.SessionManager

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        
        // Initialize language to Russian on first launch
        if (!prefs.contains("language")) {
            prefs.edit { putString("language", "ru") }
        }

        SessionManager.initFromPrefs(this)
        lavender.client.android.theme.ThemeStore.init(this)
        lavender.client.android.data.calls.CallManager.init(this)

        val savedUsername = prefs.getString("username", null)
        val savedPassword = prefs.getString("password", null)
        val savedServerAddress = prefs.getString("server_address", null)

        val skipAutoLogin = intent.getBooleanExtra("extra_skip_autologin", false)
        val isLoggedIn = !skipAutoLogin && savedUsername != null && savedPassword != null

        // Проверяем, пришел ли ID комнаты или звонок из уведомления
        val roomIdFromPush = intent.getStringExtra("ROOM_ID") ?: intent.getStringExtra("room_id")
        val callIdFromPush = intent.getStringExtra("CALL_ID") ?: intent.getStringExtra("call_id")
        
        Log.d("SplashActivity", "roomIdFromPush: $roomIdFromPush, callIdFromPush: $callIdFromPush")

        val targetIntent = if (isLoggedIn) {
            when {
                callIdFromPush != null -> {
                    Log.d("SplashActivity", "Directing to CallActivity")
                    Intent(this, CallActivity::class.java).apply {
                        putExtra("CALL_ID", callIdFromPush)
                        putExtra("RECEIVER_ID", intent.getStringExtra("SENDER_ID") ?: intent.getStringExtra("sender_id"))
                        putExtra("IS_INCOMING", true)
                        putExtra("from_notification", true)
                    }
                }
                roomIdFromPush != null -> {
                    // Check if it's a conference signal or just a message
                    val isConference = intent.getStringExtra("IS_CONFERENCE")?.toBoolean() ?: false
                    if (isConference) {
                         Log.d("SplashActivity", "Directing to ConferenceLobbyActivity")
                         Intent(this, ConferenceLobbyActivity::class.java).apply {
                            putExtra("ROOM_ID", roomIdFromPush)
                            putExtra("from_notification", true)
                         }
                    } else {
                         Log.d("SplashActivity", "Directing to NewChatActivity")
                         Intent(this, NewChatActivity::class.java).apply {
                            putExtra("USERNAME", savedUsername)
                            putExtra("PASSWORD", savedPassword)
                            putExtra("SERVER_ADDRESS", savedServerAddress)
                            putExtra("ROOM_ID", roomIdFromPush)
                            putExtra("from_notification", true)
                         }
                    }
                }
                else -> {
                    Log.d("SplashActivity", "Directing to ChatListActivity")
                    Intent(this, ChatListActivity::class.java).apply {
                        putExtra("USERNAME", savedUsername)
                        putExtra("PASSWORD", savedPassword)
                        putExtra("SERVER_ADDRESS", savedServerAddress)
                    }
                }
            }
        } else {
            Log.d("SplashActivity", "Not logged in, directing to ChatListActivity")
            Intent(this, ChatListActivity::class.java)
        }

        // Пробрасываем все остальные флаги и данные (flags, extras)
        intent.extras?.let { targetIntent.putExtras(it) }

        startActivity(targetIntent)
        finish()
    }
}
