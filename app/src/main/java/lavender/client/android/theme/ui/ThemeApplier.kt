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
import com.google.android.material.appbar.AppBarLayout
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

        val customPrimary = parseSafeColor(theme.primaryColor, Color.BLUE)
        val customOnPrimary = parseSafeColor(theme.onPrimaryColor, Color.WHITE)
        val textPrimary = parseSafeColor(theme.textPrimaryColor, if (isLightMode) Color.BLACK else Color.WHITE)
        val textSecondary = parseSafeColor(theme.textSecondaryColor, if (isLightMode) Color.GRAY else Color.LTGRAY)
        val surfaceColor = parseSafeColor(theme.surfaceColor, bgColor)
        val onSurfaceColor = parseSafeColor(theme.onSurfaceColor, textPrimary)

        try {
            WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                isAppearanceLightStatusBars = isLightMode
                isAppearanceLightNavigationBars = isLightMode
            }
        } catch (e: Exception) {
            android.util.Log.e("ThemeApplier", "WindowInsetsController failed: ${e.message}")
        }

        try {
            val root = activity.findViewById<View>(android.R.id.content)
            activity.window.decorView.setBackgroundColor(bgColor)
            root?.setBackgroundColor(bgColor)
        } catch (e: Exception) {
            android.util.Log.e("ThemeApplier", "Background color failed: ${e.message}")
        }

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

        toolbar?.apply {
            try {
                elevation = if (Color.alpha(customPrimary) < 255) 0f else 4f * context.resources.displayMetrics.density
                
                if (Color.alpha(customPrimary) < 255) {
                    backgroundTintList = null
                    val cornerRadius = 24f * context.resources.displayMetrics.density
                    val shape = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        setColor(customPrimary)
                        cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, cornerRadius, cornerRadius, cornerRadius, cornerRadius)
                    }
                    background = shape
                } else {
                    background?.let { bg ->
                        val wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(bg.mutate())
                        androidx.core.graphics.drawable.DrawableCompat.setTint(wrapped, customPrimary)
                        background = wrapped
                    } ?: setBackgroundColor(customPrimary)
                }

                setTitleTextColor(customOnPrimary)
                setNavigationIconTint(customOnPrimary)
                overflowIcon?.let {
                    val wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(it.mutate())
                    androidx.core.graphics.drawable.DrawableCompat.setTint(wrapped, customOnPrimary)
                    overflowIcon = wrapped
                }
                
                val toolbarActions = listOf(R.id.actionSearch, R.id.actionDelete, R.id.actionEdit, R.id.actionApply, R.id.actionCreateChat, R.id.btnLobby)
                toolbarActions.forEach { id ->
                    findViewById<ImageView>(id)?.let { iv ->
                        iv.imageTintList = ColorStateList.valueOf(customOnPrimary)
                        if (id == R.id.btnLobby) {
                            iv.backgroundTintList = ColorStateList.valueOf(customPrimary)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ThemeApplier", "Toolbar theme failed: ${e.message}")
            }
        }

        try {
            activity.findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)?.let { appBar ->
                appBar.setBackgroundColor(customPrimary)
            }

            activity.findViewById<ImageView>(R.id.ivToolbarUserAvatar)?.let { avatar ->
                if (activity is lavender.client.android.ui.chatlist.ChatListActivity) {
                    if (activity.isShowingDefaultAvatar) {
                        lavender.client.android.theme.ThemeUtils.applyDefaultAvatar(avatar, theme)
                    }
                } else {
                    avatar.imageTintList = ColorStateList.valueOf(customOnPrimary)
                }
            }

            activity.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab)?.apply {
                backgroundTintList = ColorStateList.valueOf(customPrimary)
                imageTintList = ColorStateList.valueOf(customOnPrimary)
            }

            val otherIcons = listOf(
                R.id.selectChatListBackground, R.id.removeChatListBackground,
                R.id.selectChatBackground, R.id.removeChatBackground
            )
            otherIcons.forEach { id ->
                activity.findViewById<View>(id)?.let { v ->
                    if (v is ImageView) v.imageTintList = ColorStateList.valueOf(customPrimary)
                }
            }
            activity.findViewById<TabLayout>(R.id.tabLayout)?.apply {
                setBackgroundColor(surfaceColor)
                setTabTextColors(adjustAlpha(textPrimary, 0.6f), textPrimary)
                setSelectedTabIndicatorColor(customPrimary)
            }
        } catch (e: Exception) {
            android.util.Log.e("ThemeApplier", "Widget theming failed: ${e.message}")
        }

        try {
            val root2 = activity.findViewById<View>(android.R.id.content)
            activity.findViewById<ImageView>(R.id.chatBackground)?.let { bgImageView ->
                val url = theme.chatBackgroundImageUrl
                if (url.isNotEmpty()) {
                    bgImageView.visibility = View.VISIBLE
                    Glide.with(activity)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerCrop()
                        .into(bgImageView)
                    root2?.setBackgroundColor(Color.TRANSPARENT)
                } else {
                    bgImageView.visibility = View.GONE
                }
            }

            activity.findViewById<ImageView>(R.id.chatListBackground)?.let { chatListBgView ->
                val url = theme.chatListBackgroundImageUrl
                if (url.isNotEmpty()) {
                    chatListBgView.visibility = View.VISIBLE
                    Glide.with(activity)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .centerCrop()
                        .into(chatListBgView)
                    root2?.setBackgroundColor(Color.TRANSPARENT)
                } else {
                    chatListBgView.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ThemeApplier", "Background image theming failed: ${e.message}")
        }

        try {
            activity.findViewById<MaterialCardView>(R.id.bottomPanel)?.let { panel ->
                val panelColor = parseSafeColor(theme.bottomPanelColor, bgColor)
                panel.setCardBackgroundColor(ColorStateList.valueOf(panelColor))

                val onPanelColor = parseSafeColor(theme.onBottomPanelColor, customPrimary)
                panel.findViewById<ImageButton>(R.id.emojiButton)?.imageTintList = ColorStateList.valueOf(onPanelColor)
                panel.findViewById<ImageButton>(R.id.attachButton)?.imageTintList = ColorStateList.valueOf(onPanelColor)
                panel.findViewById<ImageButton>(R.id.audioButton)?.imageTintList = ColorStateList.valueOf(onPanelColor)
                panel.findViewById<ImageButton>(R.id.sendButton)?.imageTintList = ColorStateList.valueOf(onPanelColor)
            }

            activity.findViewById<MaterialCardView>(R.id.replyPreview)?.let { preview ->
                preview.setCardBackgroundColor(ColorStateList.valueOf(surfaceColor))
                preview.findViewById<View>(R.id.replyBar)?.setBackgroundColor(customPrimary)
                preview.findViewById<TextView>(R.id.replyUser)?.setTextColor(customPrimary)
                preview.findViewById<TextView>(R.id.replyText)?.setTextColor(onSurfaceColor)
                preview.findViewById<ImageButton>(R.id.cancelReply)?.imageTintList = ColorStateList.valueOf(adjustAlpha(onSurfaceColor, 0.6f))
            }

            activity.findViewById<MaterialCardView>(R.id.mentionContainer)?.setCardBackgroundColor(ColorStateList.valueOf(surfaceColor))
            activity.findViewById<View>(R.id.imagePreviewScroll)?.setBackgroundColor(surfaceColor)
        } catch (e: Exception) {
            android.util.Log.e("ThemeApplier", "Chat panel theming failed: ${e.message}")
        }

        try {
            val inputTextColor = parseSafeColor(theme.textPrimaryColor, if (isLightMode) Color.BLACK else Color.WHITE)
            val hintTextColor = ThemeUtils.adjustAlpha(inputTextColor, 0.6f)
            
            val commonInputs = listOf(
                R.id.messageInput, R.id.searchInput, R.id.editMessageInput,
                R.id.editTextBio, R.id.editTextUsername, R.id.editTextPassword,
                R.id.editNewUsername, R.id.editTextOldPassword, R.id.editTextNewPassword,
                R.id.etSshHost, R.id.etSshPort, R.id.etSshUser, R.id.etSshPassword,
                R.id.etServerHost, R.id.etServerPort, R.id.etLocalPort,
                R.id.agentNameInput, R.id.agentDescriptionInput, R.id.providerTypeInput,
                R.id.modelInput, R.id.systemPromptInput, R.id.agentApiKeyInput, R.id.maxTokensInput
            )
            commonInputs.forEach { id ->
                activity.findViewById<android.widget.EditText>(id)?.apply {
                    setTextColor(inputTextColor)
                    setHintTextColor(hintTextColor)
                    textCursorDrawable = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        setSize((2 * resources.displayMetrics.density).toInt(), 0)
                        setColor(customPrimary)
                    }
                }
            }

            listOf(R.id.fabAi, R.id.fabAddChat, R.id.addContactFab, R.id.addThemeFab, R.id.fabAddMember).forEach { id ->
                (activity.findViewById<View>(id) as? com.google.android.material.floatingactionbutton.FloatingActionButton)?.apply {
                    backgroundTintList = ColorStateList.valueOf(customPrimary)
                    imageTintList = ColorStateList.valueOf(customOnPrimary)
                }
            }

            listOf(R.id.btnChangeBio, R.id.saveButton).forEach { id ->
                (activity.findViewById<View>(id) as? com.google.android.material.button.MaterialButton)?.apply {
                    backgroundTintList = ColorStateList.valueOf(customPrimary)
                    setTextColor(customOnPrimary)
                    iconTint = ColorStateList.valueOf(customOnPrimary)
                }
            }

            listOf(R.id.agentNameInput, R.id.agentDescriptionInput, R.id.providerTypeInput,
                R.id.modelInput, R.id.systemPromptInput, R.id.agentApiKeyInput, R.id.maxTokensInput).forEach { id ->
                (activity.findViewById<View>(id)?.parent as? com.google.android.material.textfield.TextInputLayout)?.apply {
                    setHintTextColor(ColorStateList.valueOf(hintTextColor))
                    setBoxStrokeColorStateList(ColorStateList.valueOf(customPrimary))
                }
            }
            listOf(R.id.toolsEnabledSwitch, R.id.ragEnabledSwitch).forEach { id ->
                (activity.findViewById<View>(id) as? com.google.android.material.switchmaterial.SwitchMaterial)?.apply {
                    setTextColor(inputTextColor)
                }
            }

            listOf(R.id.btnChangeAvatar, R.id.btnChangePassword).forEach { id ->
                (activity.findViewById<View>(id) as? com.google.android.material.button.MaterialButton)?.apply {
                    setTextColor(customPrimary)
                    iconTint = ColorStateList.valueOf(customPrimary)
                    rippleColor = ColorStateList.valueOf(adjustAlpha(customPrimary, 0.1f))
                }
            }

            (activity.findViewById<View>(R.id.btnCompanyAction) as? ImageButton)?.imageTintList = ColorStateList.valueOf(customPrimary)

            listOf(R.id.bioLabelText, R.id.tvCompanyLabel, R.id.usernameLabel).forEach { id ->
                (activity.findViewById<View>(id) as? TextView)?.setTextColor(customPrimary)
            }

            (activity.findViewById<View>(R.id.tvCompanyName) as? TextView)?.setTextColor(textPrimary)

            listOf(R.id.biometricCard, R.id.devicesCard, R.id.bioCard, R.id.avatarCard, R.id.settingsCard, R.id.companyCard, R.id.usernameCard).forEach { id ->
                activity.findViewById<View>(id)?.let { view ->
                    if (view is MaterialCardView) {
                        view.setCardBackgroundColor(ColorStateList.valueOf(surfaceColor))
                    } else {
                        view.backgroundTintList = ColorStateList.valueOf(surfaceColor)
                    }
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("ThemeApplier", "Form/input theming failed: ${e.message}")
        }
    }

    fun applyToDialog(dialog: com.google.android.material.bottomsheet.BottomSheetDialog, theme: Theme) {
        val window = dialog.window ?: return
        val bgColor = parseSafeColor(theme.backgroundColor, Color.BLACK)
        val isLightMode = ThemeUtils.isLight(bgColor)

        lavender.client.android.data.CompatUtils.setNavigationBarColor(window, bgColor)
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
