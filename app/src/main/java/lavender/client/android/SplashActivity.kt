package lavender.client.android

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import lavender.client.android.data.session.SessionManager

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SessionManager.initFromPrefs(this)

        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val savedUsername = prefs.getString("username", null)
        val savedPassword = prefs.getString("password", null)
        val savedServerAddress = prefs.getString("server_address", null)

        val skipAutoLogin = intent.getBooleanExtra("extra_skip_autologin", false)
        val isLoggedIn = !skipAutoLogin && savedUsername != null && savedPassword != null

        // Проверяем, пришел ли ID комнаты из уведомления
        val roomIdFromPush = intent.getStringExtra("ROOM_ID") ?: intent.getStringExtra("room_id")

        val targetIntent = if (isLoggedIn) {
            val roomIdFromPush = intent.getStringExtra("ROOM_ID") ?: intent.getStringExtra("room_id")

            if (roomIdFromPush != null) {
                // А. Если нажали на пуш — летим сразу в чат
                Intent(this, NewChatActivity::class.java).apply {
                    putExtra("USERNAME", savedUsername)
                    putExtra("PASSWORD", savedPassword)
                    putExtra("SERVER_ADDRESS", savedServerAddress)
                    putExtra("ROOM_ID", roomIdFromPush)
                    putExtra("from_notification", true)
                }
            } else {
                // Б. Если просто открыли приложение — идем в СПИСОК ЧАТОВ
                Intent(this, ChatListActivity::class.java).apply {
                    putExtra("USERNAME", savedUsername)
                    putExtra("PASSWORD", savedPassword)
                    putExtra("SERVER_ADDRESS", savedServerAddress)
                }
            }
        } else {
            // Если не залогинены — на экран входа
            Intent(this, MainActivity::class.java).apply {
                putExtra("extra_skip_autologin", skipAutoLogin)
            }
        }

        // Пробрасываем все остальные флаги и данные (flags, extras)
        intent.extras?.let { targetIntent.putExtras(it) }

        startActivity(targetIntent)
        finish()
    }
}
