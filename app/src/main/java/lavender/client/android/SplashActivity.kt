package lavender.client.android

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
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

        val session = SessionManager.session.value
        val isLoggedIn = session.isLoggedIn

        val skipAutoLogin = intent.getBooleanExtra("extra_skip_autologin", false)
        val shouldProceed = isLoggedIn && !skipAutoLogin

        // Проверяем, пришел ли ID комнаты или звонок из уведомления
        val roomIdFromPush = intent.getStringExtra("ROOM_ID") ?: intent.getStringExtra("room_id")
        val callIdFromPush = intent.getStringExtra("CALL_ID") ?: intent.getStringExtra("call_id")

        Log.d("SplashActivity", "roomIdFromPush: $roomIdFromPush, callIdFromPush: $callIdFromPush")

        // Show splash animation, then navigate
        animateAndNavigate(shouldProceed, roomIdFromPush, callIdFromPush, session, prefs)
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
                                splashView.postDelayed({
                                    navigateToTarget(shouldProceed, roomIdFromPush, callIdFromPush, session, prefs)
                                }, 400)
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
                    Log.d("SplashActivity", "Directing to CallActivity")
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
                        Log.d("SplashActivity", "Directing to ConferenceLobbyActivity")
                        Intent(this, ConferenceLobbyActivity::class.java).apply {
                            putExtra("ROOM_ID", roomIdFromPush)
                            putExtra("from_notification", true)
                        }
                    } else {
                        Log.d("SplashActivity", "NewChatActivity")
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
            Log.d("SplashActivity", "Not logged in, directing to ChatListActivity")
            Intent(this, ChatListActivity::class.java)
        }

        intent.extras?.let { targetIntent.putExtras(it) }

        startActivity(targetIntent)
        finish()
    }

    private fun navigateToChatList(host: String) {
        if (host.isNotEmpty()) {
            Log.d("SplashActivity", "Directing to ChatListActivityV2 (server: $host)")
            Intent(this, lavender.client.android.ui.chatlist.ChatListActivityV2::class.java)
        } else {
            Log.d("SplashActivity", "Directing to ChatListActivity (no server)")
            Intent(this, ChatListActivity::class.java)
        }.let {
            startActivity(it)
            finish()
        }
    }
