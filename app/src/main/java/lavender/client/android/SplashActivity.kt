package lavender.client.android

import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log
import lavender.client.android.ui.chatlist.ChatListActivity
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lavender.client.android.data.session.SessionManager

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                prefs.edit {
                    putString("last_crash", "${throwable.javaClass.simpleName}: ${throwable.message}\n${throwable.stackTraceToString().take(2000)}")
                    putLong("last_crash_time", System.currentTimeMillis())
                }
            } catch (_: Exception) {}
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(thread, throwable)
        }

        // Initialize language to Russian on first launch
        if (!prefs.contains("language")) {
            prefs.edit { putString("language", "ru") }
        }

        SessionManager.initFromPrefs(this)
        lavender.client.android.network.HttpClient.init(this)
        lavender.client.android.theme.ThemeStore.init(this)
        lavender.client.android.data.calls.CallManager.init(this)

        // Sync language from server if logged in
        val session = SessionManager.session.value
        if (session.isLoggedIn) {
            lifecycleScope.launch {
                try {
                    val settings = lavender.client.android.data.grpc.GrpcClient.getUserSettingsV2(this@SplashActivity)
                    val serverLocale = settings?.locale
                    if (!serverLocale.isNullOrEmpty() && serverLocale != prefs.getString("language", "ru")) {
                        prefs.edit { putString("language", serverLocale) }
                    }
                } catch (e: Exception) {
                    // Ignore - use local setting
                }
            }
        }

        val isLoggedIn = session.isLoggedIn

        val skipAutoLogin = intent.getBooleanExtra("extra_skip_autologin", false)
        val shouldProceed = isLoggedIn && !skipAutoLogin

        // Проверяем, пришел ли ID комнаты или звонок из уведомления
        val roomIdFromPush = intent.getStringExtra("ROOM_ID") ?: intent.getStringExtra("room_id")
        val callIdFromPush = intent.getStringExtra("CALL_ID") ?: intent.getStringExtra("call_id")

        animateAndNavigate(shouldProceed, roomIdFromPush, callIdFromPush, session, prefs)

        lifecycleScope.launch {
            delay(5000)
            if (!isFinishing && !isDestroyed) {
                Log.w("SplashActivity", "Splash timeout — force navigating")
                navigateToTarget(shouldProceed, roomIdFromPush, callIdFromPush, session, prefs)
            }
        }
    }

    private fun animateAndNavigate(
        shouldProceed: Boolean,
        roomIdFromPush: String?,
        callIdFromPush: String?,
        session: lavender.client.android.data.session.UserSession,
        prefs: android.content.SharedPreferences
    ) {
        // Create a simple splash view programmatically
        val splashView = android.widget.FrameLayout(this).apply {
            setBackgroundColor(resources.getColor(R.color.splash_background, null))
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Logo image from drawable (same as auth sheet)
        val logoImage = ImageView(this).apply {
            setImageResource(R.drawable.ic_notification_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            layoutParams = android.widget.FrameLayout.LayoutParams(
                (68 * resources.displayMetrics.density).toInt(),
                (68 * resources.displayMetrics.density).toInt(),
                android.view.Gravity.CENTER
            ).apply {
                bottomMargin = (20 * resources.displayMetrics.density).toInt()
            }
        }

        // App name — localized
        val appNameText = TextView(this).apply {
            text = getString(R.string.lavender_messenger)
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.lavender_mist, null))
            gravity = android.view.Gravity.CENTER
            alpha = 0f
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            ).apply {
                topMargin = (90 * resources.displayMetrics.density).toInt()
            }
        }

        // Version text
        val versionText = TextView(this).apply {
            text = BuildConfig.VERSION_NAME
            textSize = 12f
            setTextColor(resources.getColor(R.color.lavender_mist, null))
            alpha = 0.5f
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            ).apply {
                topMargin = (56 * resources.displayMetrics.density).toInt()
            }
        }

        splashView.addView(logoImage)
        splashView.addView(versionText)
        splashView.addView(appNameText)
        setContentView(splashView)

        // Animate logo fade-in + scale
        logoImage.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                // Then scale back + show app name
                logoImage.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .withEndAction {
                        appNameText.animate()
                            .alpha(1f)
                            .setDuration(300)
                            .withEndAction {
                                // Wait a bit, then navigate
                                lifecycleScope.launch {
                                    delay(400)
                                    if (!isFinishing && !isDestroyed) {
                                        navigateToTarget(shouldProceed, roomIdFromPush, callIdFromPush, session, prefs)
                                    }
                                }
                            }
                            .start()
                    }
                    .start()
            }
            .start()
    }

    private fun navigateToTarget(
        shouldProceed: Boolean,
        roomIdFromPush: String?,
        callIdFromPush: String?,
        session: lavender.client.android.data.session.UserSession,
        prefs: android.content.SharedPreferences
    ) {
        val serverAddress = prefs.getString("server_address", "") ?: ""
        val host = serverAddress.split(":").getOrNull(0) ?: ""

        val targetIntent = if (shouldProceed) {
            when {
                callIdFromPush != null -> {
                    Intent(this, CallActivity::class.java).apply {
                        putExtra("CALL_ID", callIdFromPush)
                        putExtra("RECEIVER_ID", intent.getStringExtra("SENDER_ID") ?: intent.getStringExtra("sender_id"))
                        putExtra("IS_INCOMING", true)
                        putExtra("from_notification", true)
                    }
                }
                roomIdFromPush != null -> {
                    val isConference = intent.getStringExtra("IS_CONFERENCE")?.toBoolean() ?: false
                    if (isConference) {
                        Intent(this, ConferenceLobbyActivity::class.java).apply {
                            putExtra("ROOM_ID", roomIdFromPush)
                            putExtra("from_notification", true)
                        }
                    } else {
                        Intent(this, NewChatActivity::class.java).apply {
                            putExtra("USERNAME", session.username)
                            putExtra("SERVER_ADDRESS", serverAddress)
                            putExtra("ROOM_ID", roomIdFromPush)
                            putExtra("from_notification", true)
                        }
                    }
                }
                else -> {
                    navigateToChatList(host)
                    return
                }
            }
        } else {
            Intent(this, ChatListActivity::class.java)
        }

        intent.extras?.let { targetIntent.putExtras(it) }

        startActivity(targetIntent)
        finish()
    }

    private fun navigateToChatList(host: String) {
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val username = SessionManager.session.value.username
        val biometricEnabled = prefs.getBoolean("biometric_enabled_$username", false)

        if (biometricEnabled) {
            val biometricManager = BiometricManager.from(this)
            val canAuthenticate = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )

            if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                showBiometricPrompt()
                return
            }
        }

        startActivity(Intent(this, ChatListActivity::class.java))
        finish()
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)

        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    startActivity(Intent(this@SplashActivity, ChatListActivity::class.java))
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        finish()
                    } else {
                        startActivity(Intent(this@SplashActivity, ChatListActivity::class.java))
                        finish()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_login_title))
            .setSubtitle(getString(R.string.biometric_login_subtitle))
            .setDescription(getString(R.string.biometric_login_description))
            .setNegativeButtonText(getString(R.string.biometric_login_negative_button))
            .build()

        biometricPrompt.authenticate(promptInfo)

        lifecycleScope.launch {
            delay(15000)
            if (!isFinishing && !isDestroyed) {
                Log.w("SplashActivity", "Biometric timeout — navigating anyway")
                startActivity(Intent(this@SplashActivity, ChatListActivity::class.java))
                finish()
            }
        }
    }

    /** Clear all local cache silently on successful login. */
    private fun clearAllCache() {
        lavender.client.android.data.cache.CacheUtils.clearAllSync(this)
    }
}
