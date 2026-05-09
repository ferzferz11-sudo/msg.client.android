package lavender.client.android.theme.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import lavender.client.android.R
import lavender.client.android.theme.Theme
import kotlin.math.roundToInt

object ThemeApplier {
    fun apply(activity: AppCompatActivity, theme: Theme) {
        val bgColor = parseSafeColor(theme.backgroundColor, Color.BLACK)
        val isLightMode = bgColor.isLight()

        activity.enableEdgeToEdge()
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = isLightMode
            isAppearanceLightNavigationBars = isLightMode
        }

        val root = activity.findViewById<View>(android.R.id.content)
        activity.window.decorView.setBackgroundColor(bgColor)
        root?.setBackgroundColor(bgColor)

        val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar?.let { tb ->
            ViewCompat.setOnApplyWindowInsetsListener(tb) { view, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(0, insets.top, 0, 0)
                windowInsets
            }
        }

        val customPrimary = parseSafeColor(theme.primaryColor, Color.BLUE)
        val customOnPrimary = parseSafeColor(theme.onPrimaryColor, Color.WHITE)
        toolbar?.apply {
            backgroundTintList = ColorStateList.valueOf(customPrimary)
            setTitleTextColor(customOnPrimary)
            setNavigationIconTint(customOnPrimary)
            
            // Tint action icons
            val actions = listOf(R.id.actionSearch, R.id.actionDelete, R.id.actionMute, R.id.actionEdit, R.id.actionSettings, R.id.updateAvailableIcon)
            actions.forEach { id ->
                findViewById<ImageView>(id)?.imageTintList = ColorStateList.valueOf(customOnPrimary)
            }
        }

        activity.findViewById<TabLayout>(R.id.tabLayout)?.apply {
            val surfaceColor = parseSafeColor(theme.surfaceColor, bgColor)
            val onSurfaceColor = parseSafeColor(theme.onSurfaceColor, customOnPrimary)
            setBackgroundColor(surfaceColor)
            setTabTextColors(adjustAlpha(onSurfaceColor, 0.75f), customPrimary)
            setSelectedTabIndicatorColor(customPrimary)
        }

        // Chat background image
        activity.findViewById<ImageView>(R.id.chatBackground)?.let { bgImageView ->
            val url = theme.chatListBackgroundImageUrl
            if (url.isNotEmpty()) {
                bgImageView.visibility = View.VISIBLE
                Glide.with(activity)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(bgImageView)
                root?.setBackgroundColor(Color.TRANSPARENT)
            } else {
                bgImageView.visibility = View.GONE
            }
        }

        // Chat list background image
        activity.findViewById<ImageView>(R.id.chatListBackground)?.let { chatListBgView ->
            val url = theme.chatListBackgroundImageUrl
            if (url.isNotEmpty()) {
                chatListBgView.visibility = View.VISIBLE
                Glide.with(activity)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(chatListBgView)
                root?.setBackgroundColor(Color.TRANSPARENT)
            } else {
                chatListBgView.visibility = View.GONE
            }
        }

        // Bottom panel (chat)
        activity.findViewById<MaterialCardView>(R.id.bottomPanel)?.let { panel ->
            val panelColor = parseSafeColor(theme.bottomPanelColor, bgColor)
            panel.setCardBackgroundColor(ColorStateList.valueOf(panelColor))

            val onPanelColor = parseSafeColor(theme.onBottomPanelColor, customPrimary)
            panel.findViewById<ImageButton>(R.id.emojiButton)?.imageTintList = ColorStateList.valueOf(onPanelColor)
            panel.findViewById<ImageButton>(R.id.attachButton)?.imageTintList = ColorStateList.valueOf(onPanelColor)
            panel.findViewById<ImageButton>(R.id.audioButton)?.imageTintList = ColorStateList.valueOf(onPanelColor)
            panel.findViewById<ImageButton>(R.id.sendButton)?.imageTintList = ColorStateList.valueOf(onPanelColor)
        }

        // FABs
        listOf(R.id.addChatFab, R.id.addContactFab, R.id.addThemeFab).forEach { fabId ->
            activity.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(fabId)?.apply {
                backgroundTintList = ColorStateList.valueOf(customPrimary)
                imageTintList = ColorStateList.valueOf(customOnPrimary)
            }
        }
    }

    private fun parseSafeColor(colorStr: String?, defaultColor: Int): Int {
        if (colorStr.isNullOrEmpty()) return defaultColor
        return try {
            colorStr.toColorInt()
        } catch (_: Exception) {
            defaultColor
        }
    }

    fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).roundToInt()
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    private fun Int.isLight(): Boolean {
        val darkness = 1 - (0.299 * Color.red(this) +
            0.587 * Color.green(this) +
            0.114 * Color.blue(this)) / 255
        return darkness < 0.5
    }
}

