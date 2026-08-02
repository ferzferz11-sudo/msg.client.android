package lavender.client.android.ui.chatlist
import android.util.Log

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.EditProfileActivity
import lavender.client.android.ContactsActivity
import lavender.client.android.R
import lavender.client.android.ServersActivity
import lavender.client.android.ChangelogActivity
import lavender.client.android.ThemesActivity
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.session.SessionManager
import lavender.client.android.data.cache.CacheUtils
import lavender.client.android.ui.widget.StandardBottomSheet

/**
 * Toolbar setup and settings sheet logic for ChatListActivity.
 * Extracted to reduce ChatListActivity from 1470 to ~800 lines.
 */

private const val TAG = "ChatListToolbar"

internal fun setupToolbarActions(activity: ChatListActivity, @Suppress("UNUSED_PARAMETER") username: String) {
    activity.ivToolbarUserAvatar?.setOnClickListener {
        showSettingsSheet(activity)
    }
    activity.tvToolbarTitle?.setOnClickListener {
        showSettingsSheet(activity)
    }
}

internal fun showSettingsSheet(activity: ChatListActivity) {
    showSettingsSheet(activity, null)
}

internal fun showSettingsSheet(activity: ChatListActivity, onBack: (() -> Unit)?) {
    val username = SessionManager.session.value.username
    val avatarUrl = GrpcClient.getAvatarCache()[username] ?: ""
    val sheet = StandardBottomSheet(activity, R.layout.bottom_sheet_user_menu)

    val menuUserAvatar = sheet.findViewById<ImageView>(R.id.menuUserAvatar)
    if (avatarUrl.isNotEmpty() && menuUserAvatar != null) {
        Glide.with(activity)
            .load(avatarUrl)
            .apply(RequestOptions.circleCropTransform().placeholder(R.drawable.ic_default_avatar))
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(menuUserAvatar)
    }

    sheet.findViewById<TextView>(R.id.menuUsername)?.text = username

    // Fetch admin status if not yet known (fallback for old version upgrades)
    if (!SessionManager.session.value.isSuperAdmin) {
        activity.lifecycleScope.launch {
            try {
                val profile = lavender.client.android.data.grpc.ProfileClient.getProfile(activity)
                if (profile != null && profile.isSuperAdmin) {
                    withContext(Dispatchers.Main) {
                        lavender.client.android.data.grpc.GrpcClient.setSuperAdmin(true)
                        activity.getSharedPreferences("lavender_prefs", android.content.Context.MODE_PRIVATE)
                            .edit { putBoolean("is_super_admin", true) }
                        // Update admin panel visibility if sheet is still showing
                        sheet.findViewById<View>(R.id.actionAdmin)?.isVisible = true
                    }
                }
            } catch (e: Exception) { Log.w(TAG, "Caught: " + e.message) }
        }
    }

    sheet.findViewById<View>(R.id.actionShareHeader)?.setOnClickListener {
        sheet.dismiss()
        shareApp(activity)
    }

    sheet.findViewById<View>(R.id.headerSection)?.setOnClickListener {
        activity.isNavigatingDeeper = true
        sheet.dismiss()
        activity.editProfileLauncher.launch(
            Intent(activity, EditProfileActivity::class.java).apply {
                putExtra("USERNAME", username)
            }
        )
    }

    sheet.findViewById<View>(R.id.actionContacts)?.setOnClickListener {
        sheet.dismiss()
        activity.startActivity(Intent(activity, ContactsActivity::class.java).apply {
            putExtra("USERNAME", username)
        })
    }

    sheet.findViewById<View>(R.id.actionFavorites)?.setOnClickListener {
        sheet.dismiss()
        val favoritesChat = ChatInfo(
            id = "favorites_$username",
            name = activity.getString(R.string.favorites),
            type = "favorites",
            lastMessageText = "",
            lastMessageTime = 0L
        )
        activity.navigateToChat(favoritesChat, username)
    }

    // Admin panel — show for super admins
    val adminPanel = sheet.findViewById<View>(R.id.actionAdmin)
    if (adminPanel != null) {
        val isSuperAdmin = SessionManager.session.value.isSuperAdmin
        adminPanel.isVisible = isSuperAdmin
        adminPanel.setOnClickListener {
            activity.isNavigatingDeeper = true
            sheet.dismiss()
            activity.settingsActivityLauncher.launch(
                Intent(activity, lavender.client.android.SuperAdminActivity::class.java)
            )
        }
    }

    sheet.findViewById<View>(R.id.actionThemes)?.setOnClickListener {
        sheet.dismiss()
        activity.startActivity(Intent(activity, ThemesActivity::class.java).apply {
            putExtra("username", username)
        })
    }

    sheet.findViewById<View>(R.id.actionUpdate)?.setOnClickListener {
        sheet.dismiss()
        activity.updateCoordinator?.checkManualUpdate()
    }

    sheet.findViewById<View>(R.id.actionToggleLanguage)?.setOnClickListener {
        sheet.dismiss()
        toggleLanguage(activity)
    }

    sheet.findViewById<View>(R.id.actionAdditionalSettings)?.setOnClickListener {
        activity.isNavigatingDeeper = true
        sheet.dismiss()
        showAdditionalSettingsSheet(activity) { showSettingsSheet(activity) }
    }

    sheet.setOnDismissListener {
        if (!activity.isNavigatingDeeper) onBack?.invoke()
    }

    sheet.show()
}

internal fun showAdditionalSettingsSheet(activity: ChatListActivity) {
    showAdditionalSettingsSheet(activity, null)
}

internal fun showAdditionalSettingsSheet(activity: ChatListActivity, onBack: (() -> Unit)?) {
    val username = SessionManager.session.value.username
    val isSuperAdmin = SessionManager.session.value.isSuperAdmin
    val sheet = StandardBottomSheet(activity, R.layout.bottom_sheet_additional_settings)

    sheet.findViewById<View>(R.id.actionAdmin)?.isVisible = isSuperAdmin

    sheet.findViewById<View>(R.id.actionSecurity)?.setOnClickListener {
        activity.isNavigatingDeeper = true
        sheet.dismiss()
        activity.settingsActivityLauncher.launch(
            Intent(activity, lavender.client.android.SecurityActivity::class.java).apply {
                putExtra("username", username)
            }
        )
    }

    sheet.findViewById<View>(R.id.actionNotifications)?.setOnClickListener {
        activity.isNavigatingDeeper = true
        sheet.dismiss()
        activity.settingsActivityLauncher.launch(
            Intent(activity, lavender.client.android.ui.notification.NotificationActivity::class.java)
        )
    }

    sheet.findViewById<View>(R.id.actionClearCache)?.setOnClickListener {
        sheet.dismiss()
        activity.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    CacheUtils.clearAllWithGlide(activity)
                }
                Toast.makeText(activity, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("ChatListActivity", "Error clearing cache", e)
            }
        }
    }

    sheet.findViewById<View>(R.id.actionAbout)?.setOnClickListener {
        activity.isNavigatingDeeper = true
        sheet.dismiss()
        showAboutDialog(activity) { showAdditionalSettingsSheet(activity, onBack) }
    }

    sheet.findViewById<View>(R.id.actionAdmin)?.setOnClickListener {
        activity.isNavigatingDeeper = true
        sheet.dismiss()
        activity.settingsActivityLauncher.launch(
            Intent(activity, lavender.client.android.SuperAdminActivity::class.java)
        )
    }

    sheet.findViewById<View>(R.id.actionServers)?.setOnClickListener {
        activity.isNavigatingDeeper = true
        sheet.dismiss()
        activity.settingsActivityLauncher.launch(
            Intent(activity, ServersActivity::class.java)
        )
    }

    sheet.findViewById<View>(R.id.actionDeleteProfile)?.setOnClickListener {
        sheet.dismiss()
        confirmDeleteProfile(activity)
    }

    sheet.findViewById<View>(R.id.actionLogout)?.setOnClickListener {
        sheet.dismiss()
        GrpcClient.disconnect()
        SessionManager.logout(activity)
        val intent = Intent(activity, ChatListActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
    }

    sheet.setOnDismissListener {
        if (!activity.isNavigatingDeeper) onBack?.invoke()
    }

    sheet.show()
}

internal fun confirmDeleteProfile(activity: ChatListActivity) {
    val username = SessionManager.session.value.username
    AlertDialog.Builder(activity)
        .setTitle(R.string.delete_profile)
        .setMessage(R.string.delete_profile_confirm)
        .setPositiveButton(R.string.delete) { _, _ ->
            GrpcClient.deleteProfile(username) { success, _ ->
                activity.lifecycleScope.launch {
                    if (success) {
                        Toast.makeText(activity, R.string.profile_deleted, Toast.LENGTH_LONG).show()
                        GrpcClient.disconnect()
                        SessionManager.logout(activity)
                        val intent = Intent(activity, ChatListActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        activity.startActivity(intent)
                    } else {
                        Toast.makeText(activity, R.string.failed_to_delete_profile, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

internal fun showAboutDialog(activity: ChatListActivity) {
    showAboutDialog(activity, null)
}

internal fun showAboutDialog(activity: ChatListActivity, onBack: (() -> Unit)?) {
    val sheet = StandardBottomSheet(activity, R.layout.dialog_about)
    val appVersion = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""
    } catch (_: Exception) { "" }
    val appVersionText = sheet.findViewById<TextView>(R.id.appVersionText)
    if (appVersion.isNotEmpty()) {
        appVersionText?.text = activity.getString(R.string.app_version_format, appVersion)
    } else {
        appVersionText?.visibility = View.GONE
    }
    val serverVersion = GrpcClient.serverVersion.value
    val serverVersionText = sheet.findViewById<TextView>(R.id.serverVersionText)
    if (serverVersion.isNotEmpty()) {
        serverVersionText?.text = activity.getString(R.string.server_version_format, serverVersion)
    } else {
        serverVersionText?.visibility = View.GONE
    }
    sheet.findViewById<View>(R.id.btnWhatsNew)?.setOnClickListener {
        activity.isNavigatingDeeper = true
        sheet.dismiss()
        activity.startActivity(Intent(activity, ChangelogActivity::class.java))
    }
    sheet.findViewById<View>(R.id.btnFeedback)?.setOnClickListener {
        activity.isNavigatingDeeper = true
        sheet.dismiss()
        openFeedbackChat(activity)
    }
    sheet.findViewById<View>(R.id.btnShare)?.setOnClickListener {
        val shareText = activity.getString(R.string.share_app_description) + "\nhttp://13.140.25.249"
        val intent = Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
        }
        activity.startActivity(android.content.Intent.createChooser(intent, activity.getString(R.string.share_app)))
    }
    sheet.findViewById<View>(R.id.btnClose)?.setOnClickListener { sheet.dismiss() }
    sheet.setOnDismissListener {
        if (!activity.isNavigatingDeeper) onBack?.invoke()
    }

    sheet.show()
}

private fun openFeedbackChat(activity: ChatListActivity) {
    val adminId = GrpcClient.adminUserId.value
    if (adminId.isNullOrEmpty()) {
        GrpcClient.loadUsers()
        activity.lifecycleScope.launch {
            kotlinx.coroutines.delay(1500)
            val retryId = GrpcClient.adminUserId.value
            if (retryId.isNullOrEmpty()) {
                android.widget.Toast.makeText(activity, R.string.admin_not_found, android.widget.Toast.LENGTH_SHORT).show()
            } else {
                doOpenFeedbackChat(activity, retryId)
            }
        }
        return
    }
    doOpenFeedbackChat(activity, adminId)
}

private fun doOpenFeedbackChat(activity: ChatListActivity, adminUserId: String) {
    val username = lavender.client.android.data.session.SessionManager.session.value.username
    if (adminUserId == lavender.client.android.data.session.SessionManager.session.value.userId) {
        activity.lifecycleScope.launch {
            android.widget.Toast.makeText(activity, R.string.admin_not_found, android.widget.Toast.LENGTH_SHORT).show()
        }
        return
    }
    val adminUsername = GrpcClient.allUsers.value.firstOrNull { it.userId == adminUserId }?.username
    if (adminUsername.isNullOrEmpty()) {
        activity.lifecycleScope.launch {
            android.widget.Toast.makeText(activity, R.string.admin_not_found, android.widget.Toast.LENGTH_SHORT).show()
        }
        return
    }
    GrpcClient.createDirectChat(username, adminUsername) { chatId ->
        if (chatId != null) {
            val chatInfo = lavender.client.android.data.models.ChatInfo(
                id = chatId, name = adminUsername, type = "direct",
                participants = "[\"$username\",\"$adminUsername\"]"
            )
            activity.lifecycleScope.launch { activity.navigateToChat(chatInfo, username) }
        } else {
            activity.lifecycleScope.launch {
                android.widget.Toast.makeText(activity, R.string.connection_failed, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

internal fun shareApp(activity: ChatListActivity) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.share_app))
        putExtra(Intent.EXTRA_TEXT, activity.getString(R.string.share_app_description))
    }
    activity.startActivity(Intent.createChooser(shareIntent, activity.getString(R.string.share_app)))
}

internal fun toggleLanguage(activity: ChatListActivity) {
    val prefs = activity.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)
    val currentLang = prefs.getString("language", "ru") ?: "ru"
    val newLang = if (currentLang == "ru") "en" else "ru"
    prefs.edit { putString("language", newLang) }

    // Sync to server
    activity.lifecycleScope.launch {
        try {
            GrpcClient.updateUserSettingsV2(activity, locale = newLang)
        } catch (e: Exception) {
            android.util.Log.e("ChatListToolbar", "Failed to sync language to server", e)
        }
    }

    activity.recreate()
}
