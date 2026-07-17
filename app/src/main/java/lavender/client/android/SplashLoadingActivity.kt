package lavender.client.android

import android.annotation.SuppressLint
import android.os.Bundle
import android.graphics.Typeface
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import lavender.client.android.data.session.SessionManager

/**
 * Lightweight splash overlay for showing during loading operations (login, register, etc.).
 * Shows the logo + app name animation, then stays visible until finish() is called.
 * No auto-navigation — purely visual feedback.
 */
@SuppressLint("CustomSplashScreen")
class SplashLoadingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentInstance = this

        SessionManager.initFromPrefs(this)
        lavender.client.android.theme.ThemeStore.init(this)

        val splashView = FrameLayout(this).apply {
            setBackgroundColor(resources.getColor(R.color.splash_background, null))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val logoImage = ImageView(this).apply {
            setImageResource(R.drawable.ic_notification_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(
                (68 * resources.displayMetrics.density).toInt(),
                (68 * resources.displayMetrics.density).toInt(),
                android.view.Gravity.CENTER
            ).apply {
                bottomMargin = (20 * resources.displayMetrics.density).toInt()
            }
        }

        val appNameText = TextView(this).apply {
            text = getString(R.string.lavender_messenger)
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setTextColor(resources.getColor(R.color.lavender_mist, null))
            gravity = android.view.Gravity.CENTER
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
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
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            ).apply {
                topMargin = (56 * resources.displayMetrics.density).toInt()
            }
        }

        splashView.addView(logoImage)
        splashView.addView(appNameText)
        splashView.addView(versionText)
        setContentView(splashView)

        // Animate logo fade-in + scale
        logoImage.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                logoImage.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .withEndAction {
                        appNameText.animate()
                            .alpha(1f)
                            .setDuration(300)
                            .start()
                    }
                    .start()
            }
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentInstance == this) currentInstance = null
    }

    companion object {
        private var currentInstance: SplashLoadingActivity? = null

        fun finishIfShowing() {
            currentInstance?.finish()
            currentInstance = null
        }
    }
}
