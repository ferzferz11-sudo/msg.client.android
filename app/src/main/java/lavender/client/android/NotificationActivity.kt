package lavender.client.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.theme.ui.ThemeUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationActivity : AppCompatActivity() {

    private val grpcClient = GrpcClient
    private lateinit var previewTitle: TextView
    private lateinit var previewBody: TextView
    private lateinit var previewTime: TextView

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru" // Default to Russian for first launch
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)
        val username = getSharedPreferences("lavender_prefs", MODE_PRIVATE).getString("current_username", "") ?: ""
        ThemeUi.bind(this, username)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
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

        // DND Bypass
        val switchBypassDnd = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchBypassDnd)
        switchBypassDnd.isChecked = prefs.getBoolean("push_bypass_dnd", false)

        switchBypassDnd.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean("push_bypass_dnd", isChecked) }
            if (isChecked) {
                requestDndBypassPermission()
            }
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
        val theme = lavender.client.android.theme.ThemeStore.currentTheme()
        try {
            val color = theme.onPrimaryColor.toColorInt()
            for (i in 0 until menu.size()) {
                menu.getItem(i).icon?.setTint(color)
            }
        } catch (_: Exception) {}
        
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
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
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
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
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

    private fun requestDndBypassPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) {
                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivity(intent)
            }
        }
    }
}
