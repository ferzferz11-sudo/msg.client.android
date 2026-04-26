package lavender.client.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Determine where to go
        val prefs = getSharedPreferences("LavenderPrefs", Context.MODE_PRIVATE)
        val savedUsername = prefs.getString("username", null)
        val savedPassword = prefs.getString("password", null)
        val savedServerAddress = prefs.getString("server_address", null)

        val skipAutoLogin = intent.getBooleanExtra("extra_skip_autologin", false)

        val targetIntent = if (!skipAutoLogin && savedUsername != null && savedPassword != null && savedServerAddress != null) {
            // Already logged in - go to ChatListActivity
            Intent(this, ChatListActivity::class.java).apply {
                putExtra("username", savedUsername)
                putExtra("password", savedPassword)
                putExtra("serverAddress", savedServerAddress)
            }
        } else {
            // Not logged in - go to MainActivity
            Intent(this, MainActivity::class.java).apply {
                // Ensure we skip auto-login if explicitly requested (e.g. after logout)
                putExtra("extra_skip_autologin", skipAutoLogin)
            }
        }

        // Forward extras from original intent (e.g. from notification)
        intent.extras?.let { targetIntent.putExtras(it) }
        
        startActivity(targetIntent)
        finish()
    }
}
