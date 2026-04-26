package lavender.client.android

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import lavender.client.android.data.fcm.NotificationEntry
import lavender.client.android.data.fcm.NotificationHistory
import lavender.client.android.data.grpc.GrpcClient
import java.text.SimpleDateFormat
import java.util.*

class NotificationActivity : AppCompatActivity() {

    private val grpcClient = GrpcClient
    private lateinit var previewTitle: TextView
    private lateinit var previewBody: TextView
    private lateinit var previewTime: TextView

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "en") ?: "en"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedColorScheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        // Apply theme colors to toolbar
        lavender.client.android.ui.ThemeManager.applyTheme(this)

        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        val switchReceive = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchReceivePush)
        val switchSend = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchSendPush)
        
        switchReceive.isChecked = prefs.getBoolean("push_receive_enabled", true)
        switchSend.isChecked = prefs.getBoolean("push_send_enabled", true)

        switchReceive.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("push_receive_enabled", isChecked) }
            updateTokenOnServer()
        }

        switchSend.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("push_send_enabled", isChecked) }
            updateTokenOnServer()
        }

        // Preview views
        val previewLayout = findViewById<View>(R.id.notificationPreview)
        previewTitle = previewLayout.findViewById(R.id.notifTitle)
        previewBody = previewLayout.findViewById(R.id.notifBody)
        previewTime = previewLayout.findViewById(R.id.notifTime)
        previewTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        // Notification Style
        val radioGroupStyle = findViewById<android.widget.RadioGroup>(R.id.radioGroupStyle)
        val currentStyle = prefs.getString("notification_style", "standard") ?: "standard"
        
        when (currentStyle) {
            "standard" -> radioGroupStyle.check(R.id.styleStandard)
            "messaging" -> radioGroupStyle.check(R.id.styleMessaging)
            "big_text" -> radioGroupStyle.check(R.id.styleBigText)
        }

        updatePreview(currentStyle)

        radioGroupStyle.setOnCheckedChangeListener { _, checkedId ->
            val style = when (checkedId) {
                R.id.styleStandard -> "standard"
                R.id.styleMessaging -> "messaging"
                R.id.styleBigText -> "big_text"
                else -> "standard"
            }
            prefs.edit { putString("notification_style", style) }
            updatePreview(style)
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menu.add(0, 1, 0, R.string.notification_history).apply {
            setIcon(R.drawable.ic_notification_history)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        
        lifecycleScope.launch {
            grpcClient.isSuperAdmin.collect { isAdmin ->
                if (isAdmin) {
                    runOnUiThread {
                        if (menu.findItem(2) == null) {
                            menu.add(0, 2, 1, "FCM Logs").apply {
                                setIcon(R.drawable.ic_settings)
                                setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
                                // Standard tint will be applied below
                            }
                        }
                    }
                }
            }
        }
        
        // Force tint if current custom theme is applied
        lavender.client.android.ui.ThemeManager.getCurrentTheme()?.let { theme ->
            val color = android.graphics.Color.parseColor(theme.onPrimaryColor)
            for (i in 0 until menu.size()) {
                menu.getItem(i).icon?.setTint(color)
            }
        }
        
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            1 -> startActivity(android.content.Intent(this, NotificationLogActivity::class.java))
            2 -> startActivity(android.content.Intent(this, FCMLogsActivity::class.java))
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updatePreview(style: String) {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        val username = prefs.getString("username", "") ?: ""
        
        when (style) {
            "messaging" -> {
                previewTitle.text = username.ifEmpty { getString(R.string.notif_preview_sender) }
                previewBody.text = getString(R.string.notif_preview_msg_messaging)
            }
            "big_text" -> {
                previewTitle.text = getString(R.string.notif_preview_title_bigtext)
                previewBody.text = getString(R.string.notif_preview_msg_bigtext)
            }
            else -> {
                previewTitle.text = getString(R.string.notif_preview_title_standard)
                previewBody.text = getString(R.string.notif_preview_msg_standard)
            }
        }
    }

    private fun updateTokenOnServer() {
        val prefs = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
        val username = prefs.getString("username", "") ?: ""
        val sendEnabled = prefs.getBoolean("push_send_enabled", true)
        val receiveEnabled = prefs.getBoolean("push_receive_enabled", true)
        
        if (username.isNotEmpty()) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = if (receiveEnabled) task.result else "DISABLED"
                    grpcClient.registerToken(username, token, sendEnabled)
                }
            }
        }
    }

    private fun applySavedColorScheme() {
        val theme = when (getSharedPreferences("ChatPrefs", MODE_PRIVATE).getString("color_scheme", "dark")) {
            "light" -> R.style.Theme_Lavender_Light_NoActionBar
            else -> R.style.Theme_Lavender_Dark_NoActionBar
        }
        setTheme(theme)
    }
}
