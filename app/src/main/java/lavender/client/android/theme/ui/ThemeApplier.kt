package lavender.client.android.theme.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import lavender.client.android.theme.ThemeUtils

object ThemeApplier {
    fun apply(activity: AppCompatActivity, theme: Theme) {
        val bgColor = parseSafeColor(theme.backgroundColor, Color.BLACK)
        val isLightMode = ThemeUtils.isLight(bgColor)

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
                
                // Set padding for status bar
                view.setPadding(0, insets.top, 0, 0)
                
                // Also adjust height if it's a fixed height from XML
                // This prevents squeezing the content (title/icons)
                val lp = view.layoutParams
                if (lp != null && lp.height > 0) {
                    // We use the view's ID as a key to store the original height 
                    // before status bar padding was added.
                    var originalHeight = view.getTag(R.id.toolbar) as? Int
                    if (originalHeight == null) {
                        // Use the standard dimension from resources as base height
                        originalHeight = activity.resources.getDimensionPixelSize(R.dimen.custom_toolbar_height)
                        view.setTag(R.id.toolbar, originalHeight)
                    }
                    
                    val targetHeight = originalHeight + insets.top
                    if (lp.height != targetHeight) {
                        lp.height = targetHeight
                        view.layoutParams = lp
                    }
                }

                windowInsets
            }
        }

        val customPrimary = parseSafeColor(theme.primaryColor, Color.BLUE)
        val customOnPrimary = parseSafeColor(theme.onPrimaryColor, Color.WHITE)
        toolbar?.apply {
            // Set elevation to 0 if toolbar is transparent to avoid shadow/surface overlay
            elevation = if (Color.alpha(customPrimary) < 255) 0f else 4f * context.resources.displayMetrics.density
            
            // To support transparency and rounded corners centrally, 
            // we create a fresh background if the color has transparency.
            if (Color.alpha(customPrimary) < 255) {
                backgroundTintList = null // Crucial: remove tint so it doesn't block transparency
                val cornerRadius = 24f * context.resources.displayMetrics.density
                val shape = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    setColor(customPrimary)
                    // Bottom corners rounded (0,0,0,0, R,R,R,R)
                    cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, cornerRadius, cornerRadius, cornerRadius, cornerRadius)
                }
                background = shape
            } else {
                // For solid colors, we can try to reuse/tint the existing background (to keep strokes/layers)
                background?.let { bg ->
                    val wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(bg.mutate())
                    androidx.core.graphics.drawable.DrawableCompat.setTint(wrapped, customPrimary)
                    background = wrapped
                } ?: setBackgroundColor(customPrimary)
            }

            setTitleTextColor(customOnPrimary)
            setNavigationIconTint(customOnPrimary)
            
            // Tint action icons
            val actions = listOf(R.id.actionSearch, R.id.actionDelete, R.id.actionMute, R.id.actionEdit, R.id.actionSettings, R.id.updateAvailableIcon, R.id.actionApply, R.id.actionCreateChat)
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
            val url = theme.chatBackgroundImageUrl
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

        // Apply text colors to inputs globally in the activity
        val inputTextColor = parseSafeColor(theme.textPrimaryColor, if (isLightMode) Color.BLACK else Color.WHITE)
        val hintTextColor = ThemeUtils.adjustAlpha(inputTextColor, 0.6f)
        
        // Find and theme common input fields
        val commonInputs = listOf(
            R.id.messageInput, R.id.searchInput, R.id.editMessageInput, 
            R.id.editTextBio, R.id.editTextUsername, R.id.editTextPassword,
            R.id.editNewUsername, R.id.editTextOldPassword, R.id.editTextNewPassword
        )
        commonInputs.forEach { id ->
            activity.findViewById<android.widget.EditText>(id)?.apply {
                setTextColor(inputTextColor)
                setHintTextColor(hintTextColor)
            }
        }

        // FABs
        listOf(R.id.addChatFab, R.id.addContactFab, R.id.addThemeFab).forEach { id ->
            (activity.findViewById<View>(id) as? com.google.android.material.floatingactionbutton.FloatingActionButton)?.apply {
                backgroundTintList = ColorStateList.valueOf(customPrimary)
                imageTintList = ColorStateList.valueOf(customOnPrimary)
            }
        }

        // Standard Primary Buttons
        listOf(R.id.btnChangeBio).forEach { id ->
            (activity.findViewById<View>(id) as? com.google.android.material.button.MaterialButton)?.apply {
                backgroundTintList = ColorStateList.valueOf(customPrimary)
                setTextColor(customOnPrimary)
                iconTint = ColorStateList.valueOf(customOnPrimary)
            }
        }

        // TextButtons and others
        listOf(R.id.btnChangeAvatar, R.id.btnChangeUsername, R.id.btnChangePassword).forEach { id ->
            (activity.findViewById<View>(id) as? com.google.android.material.button.MaterialButton)?.apply {
                setTextColor(customPrimary)
                iconTint = ColorStateList.valueOf(customPrimary)
                rippleColor = ColorStateList.valueOf(adjustAlpha(customPrimary, 0.1f))
            }
        }

        // Onboarding and Welcome
        val textPrimary = parseSafeColor(theme.textPrimaryColor, if (isLightMode) Color.BLACK else Color.WHITE)
        val textSecondary = parseSafeColor(theme.textSecondaryColor, if (isLightMode) Color.GRAY else Color.LTGRAY)
        val surfaceColor = parseSafeColor(theme.surfaceColor, bgColor)
        val onSurfaceColor = parseSafeColor(theme.onSurfaceColor, textPrimary)

        activity.findViewById<TextView>(R.id.welcomeTitle)?.setTextColor(textPrimary)
        activity.findViewById<TextView>(R.id.toolbarTitle)?.setTextColor(customOnPrimary)
        activity.findViewById<TextView>(R.id.updateProgressText)?.setTextColor(customOnPrimary)
        activity.findViewById<TextView>(R.id.welcomeDescription)?.setTextColor(textSecondary)
        
        // Settings/Edit Profile headers
        (activity.findViewById<View>(R.id.bioLabelText) as? TextView)?.setTextColor(customPrimary)
        
        listOf(R.id.onboardingProfileBubble, R.id.onboardingFabBubble, R.id.biometricCard, R.id.devicesCard, R.id.bioCard, R.id.avatarCard, R.id.settingsCard).forEach { id ->
            activity.findViewById<View>(id)?.let { view ->
                if (view is MaterialCardView) {
                    view.setCardBackgroundColor(ColorStateList.valueOf(surfaceColor))
                } else {
                    view.backgroundTintList = ColorStateList.valueOf(surfaceColor)
                }
            }
        }
        (activity.findViewById<View>(R.id.onboardingProfileText) as? TextView)?.setTextColor(onSurfaceColor)
        (activity.findViewById<View>(R.id.onboardingFabText) as? TextView)?.setTextColor(onSurfaceColor)
    }

    fun applyToDialog(dialog: com.google.android.material.bottomsheet.BottomSheetDialog, theme: Theme) {
        val window = dialog.window ?: return
        val bgColor = parseSafeColor(theme.backgroundColor, Color.BLACK)
        val isLightMode = ThemeUtils.isLight(bgColor)

        // Ensure the navigation bar matches the theme
        @Suppress("DEPRECATION")
        window.navigationBarColor = bgColor
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightNavigationBars = isLightMode
        }

        // The actual sheet background (Material 3 default has a surface color)
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            backgroundTintList = ColorStateList.valueOf(bgColor)
        }
    }

    private fun parseSafeColor(colorStr: String?, defaultColor: Int): Int {
        return ThemeUtils.parseSafeColor(colorStr, defaultColor)
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        return ThemeUtils.adjustAlpha(color, factor)
    }
}

