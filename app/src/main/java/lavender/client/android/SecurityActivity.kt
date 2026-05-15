package lavender.client.android

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.biometric.BiometricManager
import com.google.android.material.materialswitch.MaterialSwitch
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ui.ThemeApplier
import lavender.client.android.theme.ui.ThemeUi
import java.util.Locale

class SecurityActivity : AppCompatActivity() {

    private lateinit var username: String
    private lateinit var switchBiometric: MaterialSwitch

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

        username = intent.getStringExtra("username") ?: return finish()

        ThemeUi.bind(this, username)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.security)
        }
        toolbar.setNavigationOnClickListener { finish() }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }

        switchBiometric = findViewById(R.id.switchBiometric)
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        switchBiometric.isChecked = prefs.getBoolean("biometric_enabled_$username", false)

        // Apply theme colors to switch
        val theme = ThemeStore.currentTheme()
        val primaryColor = theme.primaryColor.toColorInt()
        switchBiometric.thumbTintList = android.content.res.ColorStateList.valueOf(primaryColor)
        switchBiometric.trackTintList = android.content.res.ColorStateList.valueOf(
            lavender.client.android.theme.ThemeUtils.adjustAlpha(primaryColor, 0.5f)
        )

        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            switchBiometric.isEnabled = false

            if (switchBiometric.isChecked) {
                switchBiometric.isChecked = false
                prefs.edit().putBoolean("biometric_enabled_$username", false).apply()
            }

            val reason = when (canAuthenticate) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> getString(R.string.biometric_login_error_no_hardware)
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> getString(R.string.biometric_login_error_unknown)
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> getString(R.string.biometric_login_error_no_fingerprints)
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Требуется обновление безопасности"
                BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> getString(R.string.biometric_login_error_sdk_not_supported)
                BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> getString(R.string.biometric_login_error_unknown)
                else -> getString(R.string.biometric_login_error_unknown)
            }
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
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
    }
}
