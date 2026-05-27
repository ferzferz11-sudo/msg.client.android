package lavender.client.android

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.biometric.BiometricManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.session.SessionManager
import lavender.client.android.ui.adapter.DeviceAdapter
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.theme.ui.ThemeUi
import java.util.Locale

import lavender.client.android.ui.widget.StandardBottomSheet
import lavender.client.android.ui.widget.WidgetManager

class SecurityActivity : AppCompatActivity() {

    private lateinit var username: String
    private lateinit var switchBiometric: MaterialSwitch
    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var btnTerminateAll: MaterialButton

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val languageCode = prefs.getString("language", "ru") ?: "ru"
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security)

        username = SessionManager.session.value.username
        if (username.isEmpty()) {
            username = lavender.client.android.data.session.CredentialStore.getUsername(this)
        }
        
        if (username.isEmpty()) return finish()

        ThemeUi.bind(this, username)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
        toolbar.setNavigationOnClickListener { finish() }

        // Handle system bars insets
        val rootLayout = findViewById<View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.updatePadding(top = systemBars.top)
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        switchBiometric = findViewById(R.id.switchBiometric)
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        switchBiometric.isChecked = prefs.getBoolean("biometric_enabled_$username", false)

        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            switchBiometric.isEnabled = false
            if (switchBiometric.isChecked) {
                switchBiometric.isChecked = false
                prefs.edit().putBoolean("biometric_enabled_$username", false).apply()
            }
        } else {
            switchBiometric.setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("biometric_enabled_$username", isChecked).apply()
                Toast.makeText(
                    this,
                    if (isChecked) getString(R.string.biometric_login_enabled) else getString(R.string.biometric_login_disabled),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        setupDevices()
        
        // Setup theme listener to update colors that ThemeApplier doesn't handle
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                ThemeStore.theme.collect { theme ->
                    applyThemeToViews(theme)
                }
            }
        }
    }

    private fun applyThemeToViews(theme: lavender.client.android.theme.Theme) {
        val primaryColor = theme.primaryColor.toColorInt()
        val surfaceColor = theme.surfaceColor.toColorInt()
        val textPrimaryColor = theme.textPrimaryColor.toColorInt()
        val textSecondaryColor = theme.textSecondaryColor.toColorInt()

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.biometricCard)?.setCardBackgroundColor(surfaceColor)
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.devicesCard)?.setCardBackgroundColor(surfaceColor)

        // Theme biometric section elements
        findViewById<android.widget.ImageView>(R.id.biometricIcon)?.imageTintList = 
            android.content.res.ColorStateList.valueOf(primaryColor)
        findViewById<TextView>(R.id.biometricTitle)?.setTextColor(textPrimaryColor)
        findViewById<TextView>(R.id.biometricDescription)?.setTextColor(textSecondaryColor)

        findViewById<TextView>(R.id.activeSessionsTitle)?.setTextColor(primaryColor)
        btnTerminateAll = findViewById(R.id.btnTerminateAll)
        btnTerminateAll.setTextColor("#FF5252".toColorInt())

        // Update biometric switch colors
        switchBiometric.thumbTintList = android.content.res.ColorStateList.valueOf(primaryColor)
        switchBiometric.trackTintList = android.content.res.ColorStateList.valueOf(
            lavender.client.android.theme.ThemeUtils.adjustAlpha(primaryColor, 0.5f)
        )

        // Handle background image like in ChatListActivity
        findViewById<android.widget.ImageView>(R.id.securityBackground)?.let { bgView ->
            val url = theme.chatListBackgroundImageUrl
            if (url.isNotEmpty()) {
                bgView.visibility = View.VISIBLE
                com.bumptech.glide.Glide.with(this)
                    .load(url)
                    .centerCrop()
                    .into(bgView)
            } else {
                bgView.visibility = View.GONE
            }
        }
    }

    private fun setupDevices() {
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
        btnTerminateAll = findViewById(R.id.btnTerminateAll)

        val currentDeviceId = SessionManager.getDeviceId(this)
        deviceAdapter = DeviceAdapter(
            currentDeviceId,
            onItemClick = { device -> showDeviceInfoDialog(device) },
            onDeleteClick = { device -> confirmTerminateSession(device) }
        )

        devicesRecyclerView.layoutManager = LinearLayoutManager(this)
        devicesRecyclerView.adapter = deviceAdapter

        loadDevices()

        btnTerminateAll.setOnClickListener {
            confirmTerminateOtherSessions()
        }
    }

    private fun confirmTerminateOtherSessions() {
        AlertDialog.Builder(this)
            .setTitle(R.string.terminate_all_sessions)
            .setMessage(R.string.terminate_session_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                terminateOtherSessions()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun terminateOtherSessions() {
        val userId = SessionManager.session.value.userId
        val currentDeviceId = SessionManager.getDeviceId(this)
        
        GrpcClient.deleteOtherDevices(userId, currentDeviceId) { success, _ ->
            runOnUiThread {
                if (success) {
                    loadDevices()
                    Toast.makeText(this, "Другие сеансы завершены", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Ошибка при завершении сеансов", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadDevices() {
        val userId = SessionManager.session.value.userId
        if (userId.isEmpty()) {
            GrpcClient.fetchUserId(username) { id, success ->
                if (success && id != null) {
                    runOnUiThread { 
                        SessionManager.updateSession(userId = id)
                        loadDevices() 
                    }
                }
            }
            return
        }

        GrpcClient.getDevices(userId) { devices ->
            runOnUiThread {
                deviceAdapter.setDevices(devices)
                btnTerminateAll.visibility = if (devices.size > 1) View.VISIBLE else View.GONE
            }
        }
    }

    private fun confirmTerminateSession(device: lavender.client.android.data.proto.DeviceInfoProto) {
        AlertDialog.Builder(this)
            .setTitle(R.string.terminate_session)
            .setMessage(getString(R.string.terminate_session_confirm))
            .setPositiveButton(R.string.delete) { _, _ ->
                terminateSession(device.deviceId)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeviceInfoDialog(device: lavender.client.android.data.proto.DeviceInfoProto) {
        val sheet = StandardBottomSheet(this, R.layout.dialog_device_info)
        sheet.setTitle(getString(R.string.device_details))

        sheet.findViewById<TextView>(R.id.tvDeviceId)?.text = device.deviceId
        sheet.findViewById<TextView>(R.id.tvIpAddress)?.text = device.ipAddress.ifEmpty { "unknown" }
        sheet.findViewById<TextView>(R.id.tvVersion)?.text = device.clientVersion.ifEmpty { "unknown" }
        sheet.findViewById<TextView>(R.id.tvLastActive)?.text = 
            lavender.client.android.data.proto.ProtoUtils.formatLastSeen(device.lastSeenAt, this)

        val btnClose = sheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnClose)
        btnClose?.setOnClickListener { sheet.dismiss() }
        
        sheet.show()
    }

    private fun terminateSession(deviceId: String) {
        val userId = SessionManager.session.value.userId
        GrpcClient.deleteDevice(userId, deviceId) { success, _ ->
            runOnUiThread {
                if (success) {
                    loadDevices()
                } else {
                    Toast.makeText(this, "Failed to terminate session", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
