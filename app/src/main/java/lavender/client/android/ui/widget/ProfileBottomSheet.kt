package lavender.client.android.ui.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import lavender.client.android.R
import lavender.client.android.ServersActivity
import lavender.client.android.ThemesActivity
import lavender.client.android.ContactsActivity
import lavender.client.android.EditProfileActivity
import lavender.client.android.NotificationActivity
import lavender.client.android.SecurityActivity
import lavender.client.android.SuperAdminActivity
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.session.SessionManager
import lavender.client.android.data.cache.CacheUtils
import lavender.client.android.theme.ThemeStore
import lavender.client.android.ui.chatlist.ChatListActivity
import lavender.client.android.ui.LogViewerActivity

/**
 * ProfileBottomSheet — шторка профиля при тапе на аватар/тулбар.
 * Показывает: аватар, username, edit profile, настройки, серверы, logout.
 * Настройки открывают дополнительную шторку (SettingsSheet).
 */
class ProfileBottomSheet(
    context: Context,
    theme: lavender.client.android.theme.Theme = ThemeStore.currentTheme()
) : StandardBottomSheet(context, R.layout.bottom_sheet_profile, theme) {

    companion object {
        fun newInstance(): ProfileBottomSheet {
            throw IllegalStateException("Use constructor with context instead")
        }
    }

    init {
        setupContent()
    }

    private fun setupContent() {
        val context = context ?: return
        val username = CredentialStore.getUsername(context)
        val avatarUrl = GrpcClient.getAvatarCache()[username] ?: ""

        // Avatar
        val ivAvatar = findViewById<ImageView>(R.id.ivProfileAvatar)
        if (avatarUrl.isNotEmpty()) {
            Glide.with(context)
                .load(avatarUrl)
                .apply(RequestOptions.circleCropTransform().placeholder(R.drawable.ic_default_avatar))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(ivAvatar)
        }

        // Username
        findViewById<TextView>(R.id.tvProfileUsername)?.text = username

        // Edit Profile
        findViewById<View>(R.id.btnEditProfile)?.setOnClickListener {
            dismiss()
            context.startActivity(Intent(context, EditProfileActivity::class.java).apply {
                putExtra("USERNAME", username)
            })
        }

        // Settings → opens settings sheet
        findViewById<View>(R.id.btnSettings)?.setOnClickListener {
            dismiss()
            showSettingsSheet()
        }

        // Servers
        findViewById<View>(R.id.btnServers)?.setOnClickListener {
            dismiss()
            context.startActivity(Intent(context, ServersActivity::class.java))
        }

        // Logout
        findViewById<View>(R.id.btnLogout)?.setOnClickListener {
            dismiss()
            GrpcClient.disconnect()
            SessionManager.logout(context)
            val intent = Intent(context, ChatListActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }

    // ======= Settings Sheet =======

    private fun showSettingsSheet() {
        val context = context ?: return
        val username = CredentialStore.getUsername(context)
        val avatarUrl = GrpcClient.getAvatarCache()[username] ?: ""
        val sheet = StandardBottomSheet(context, R.layout.bottom_sheet_user_menu)

        // Avatar in header
        val menuUserAvatar = sheet.findViewById<ImageView>(R.id.menuUserAvatar)
        if (avatarUrl.isNotEmpty()) {
            Glide.with(context)
                .load(avatarUrl)
                .apply(RequestOptions.circleCropTransform().placeholder(R.drawable.ic_default_avatar))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(menuUserAvatar)
        }

        // Username in header
        sheet.findViewById<TextView>(R.id.menuUsername)?.text = username

        // Share
        sheet.findViewById<View>(R.id.actionShareHeader)?.setOnClickListener {
            sheet.dismiss()
            shareApp()
        }

        // Edit Profile
        sheet.findViewById<View>(R.id.actionEditProfile)?.setOnClickListener {
            sheet.dismiss()
            context.startActivity(Intent(context, EditProfileActivity::class.java).apply {
                putExtra("USERNAME", username)
            })
        }

        // Contacts
        sheet.findViewById<View>(R.id.actionContacts)?.setOnClickListener {
            sheet.dismiss()
            context.startActivity(Intent(context, ContactsActivity::class.java).apply {
                putExtra("USERNAME", username)
            })
        }

        // Themes
        sheet.findViewById<View>(R.id.actionThemes)?.setOnClickListener {
            sheet.dismiss()
            context.startActivity(Intent(context, ThemesActivity::class.java).apply {
                putExtra("username", username)
            })
        }

        // Update
        sheet.findViewById<View>(R.id.actionUpdate)?.setOnClickListener {
            sheet.dismiss()
            checkManualUpdate()
        }

        // Language toggle
        sheet.findViewById<View>(R.id.actionToggleLanguage)?.setOnClickListener {
            sheet.dismiss()
            toggleLanguage()
        }

        // Additional Settings
        sheet.findViewById<View>(R.id.actionAdditionalSettings)?.setOnClickListener {
            sheet.dismiss()
            showAdditionalSettingsSheet()
        }

        sheet.show()
    }

    private fun showAdditionalSettingsSheet() {
        val context = context ?: return
        val username = CredentialStore.getUsername(context)
        val isSuperAdmin = SessionManager.session.value.isSuperAdmin
        val sheet = StandardBottomSheet(context, R.layout.bottom_sheet_additional_settings)

        // Show Admin Panel only for super admins
        sheet.findViewById<View>(R.id.actionAdmin)?.isVisible = isSuperAdmin

        // Security
        sheet.findViewById<View>(R.id.actionSecurity)?.setOnClickListener {
            sheet.dismiss()
            context.startActivity(Intent(context, SecurityActivity::class.java).apply {
                putExtra("username", username)
            })
        }

        // Notifications
        sheet.findViewById<View>(R.id.actionNotifications)?.setOnClickListener {
            sheet.dismiss()
            context.startActivity(Intent(context, NotificationActivity::class.java))
        }

        // Logs
        sheet.findViewById<View>(R.id.actionLogs)?.setOnClickListener {
            sheet.dismiss()
            context.startActivity(Intent(context, LogViewerActivity::class.java))
        }

        // Clear Cache
        sheet.findViewById<View>(R.id.actionClearCache)?.setOnClickListener {
            sheet.dismiss()
            try {
                CacheUtils.clearAllWithGlide(context)
                Toast.makeText(context, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("ProfileBottomSheet", "Error clearing cache", e)
            }
        }

        // About
        sheet.findViewById<View>(R.id.actionAbout)?.setOnClickListener {
            sheet.dismiss()
            showAboutDialog()
        }

        // Admin
        sheet.findViewById<View>(R.id.actionAdmin)?.setOnClickListener {
            sheet.dismiss()
            context.startActivity(Intent(context, SuperAdminActivity::class.java))
        }

        // Servers
        sheet.findViewById<View>(R.id.actionServers)?.setOnClickListener {
            sheet.dismiss()
            context.startActivity(Intent(context, ServersActivity::class.java))
        }

        // Delete Profile
        sheet.findViewById<View>(R.id.actionDeleteProfile)?.setOnClickListener {
            sheet.dismiss()
            confirmDeleteProfile()
        }

        // Logout
        sheet.findViewById<View>(R.id.actionLogout)?.setOnClickListener {
            sheet.dismiss()
            GrpcClient.disconnect()
            SessionManager.logout(context)
            val intent = Intent(context, ChatListActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }

        sheet.show()
    }

    private fun confirmDeleteProfile() {
        val context = context ?: return
        val username = CredentialStore.getUsername(context)
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.delete_profile)
            .setMessage(R.string.delete_profile_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                try {
                    GrpcClient.deleteProfile(username) { success, _ ->
                        if (success) {
                            Toast.makeText(context, R.string.profile_deleted, Toast.LENGTH_LONG).show()
                            GrpcClient.disconnect()
                            SessionManager.logout(context)
                            val intent = Intent(context, ChatListActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, R.string.failed_to_delete_profile, Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ProfileBottomSheet", "deleteProfile error", e)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAboutDialog() {
        val context = context ?: return
        val sheet = StandardBottomSheet(context, R.layout.dialog_about)
        try {
            val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            sheet.findViewById<TextView>(R.id.aboutVersion)?.text = context.getString(R.string.app_version_format, versionName)
        } catch (_: Exception) {}
        // Close button
        sheet.findViewById<View>(R.id.btnClose)?.setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    private fun shareApp() {
        val context = context ?: return
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_app))
                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_app_description))
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_app)))
        } catch (e: Exception) {
            Log.e("ProfileBottomSheet", "shareApp error", e)
        }
    }

    private fun checkManualUpdate() {
        val context = context ?: return
        try {
            // TODO: implement update activity
            Toast.makeText(context, R.string.action_update, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("ProfileBottomSheet", "checkManualUpdate error", e)
        }
    }

    private fun toggleLanguage() {
        val context = context ?: return
        try {
            val prefs = context.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
            val currentLang = prefs.getString("language", "ru") ?: "ru"
            val newLang = if (currentLang == "ru") "en" else "ru"
            prefs.edit().putString("language", newLang).apply()
            // Recreate activity to apply language change
            if (context is android.app.Activity) {
                context.recreate()
            }
        } catch (e: Exception) {
            Log.e("ProfileBottomSheet", "toggleLanguage error", e)
        }
    }
}
