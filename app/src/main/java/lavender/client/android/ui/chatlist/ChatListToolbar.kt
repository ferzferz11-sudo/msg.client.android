package lavender.client.android.ui.chatlist

import android.content.Intent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import lavender.client.android.EditProfileActivity
import lavender.client.android.ContactsActivity
import lavender.client.android.R
import lavender.client.android.ServersActivity
import lavender.client.android.ThemesActivity
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.session.SessionManager
import lavender.client.android.data.cache.CacheUtils
import lavender.client.android.ui.widget.StandardBottomSheet
import lavender.client.android.data.grpc.*

/**
 * Toolbar setup and settings sheet logic for ChatListActivity.
 * Extracted to reduce ChatListActivity from 1470 to ~800 lines.
 */

internal fun setupToolbarActions(activity: ChatListActivity, username: String) {
    activity.ivToolbarUserAvatar?.setOnClickListener {
        showSettingsSheet(activity)
    }
    activity.tvToolbarTitle?.setOnClickListener {
        showSettingsSheet(activity)
    }
}

internal fun showSettingsSheet(activity: ChatListActivity) {
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

    sheet.findViewById<View>(R.id.actionShareHeader)?.setOnClickListener {
        sheet.dismiss()
        shareApp(activity)
    }

    sheet.findViewById<View>(R.id.headerSection)?.setOnClickListener {
        sheet.dismiss()
        activity.startActivity(Intent(activity, EditProfileActivity::class.java).apply {
            putExtra("USERNAME", username)
        })
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
        sheet.dismiss()
        showAdditionalSettingsSheet(activity)
    }

    sheet.show()
}

internal fun showAdditionalSettingsSheet(activity: ChatListActivity) {
    val username = SessionManager.session.value.username
    val isSuperAdmin = SessionManager.session.value.isSuperAdmin
    val sheet = StandardBottomSheet(activity, R.layout.bottom_sheet_additional_settings)

    sheet.findViewById<View>(R.id.actionAdmin)?.isVisible = isSuperAdmin

    sheet.findViewById<View>(R.id.actionSecurity)?.setOnClickListener {
        sheet.dismiss()
        activity.startActivity(Intent(activity, lavender.client.android.SecurityActivity::class.java).apply {
            putExtra("username", username)
        })
    }

    sheet.findViewById<View>(R.id.actionNotifications)?.setOnClickListener {
        sheet.dismiss()
        activity.startActivity(Intent(activity, lavender.client.android.NotificationActivity::class.java))
    }

    sheet.findViewById<View>(R.id.actionClearCache)?.setOnClickListener {
        sheet.dismiss()
        try {
            runBlocking(Dispatchers.IO) {
                CacheUtils.clearAllWithGlide(activity)
            }
            Toast.makeText(activity, R.string.cache_cleared, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("ChatListActivity", "Error clearing cache", e)
        }
    }

    sheet.findViewById<View>(R.id.actionAbout)?.setOnClickListener {
        sheet.dismiss()
        showAboutDialog(activity)
    }

    sheet.findViewById<View>(R.id.actionAdmin)?.setOnClickListener {
        sheet.dismiss()
        activity.startActivity(Intent(activity, lavender.client.android.SuperAdminActivity::class.java))
    }

    sheet.findViewById<View>(R.id.actionServers)?.setOnClickListener {
        sheet.dismiss()
        activity.startActivity(Intent(activity, ServersActivity::class.java))
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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
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
                activity.runOnUiThread {
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
    val sheet = StandardBottomSheet(activity, R.layout.dialog_about)
    try {
        val versionName = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: ""
        sheet.findViewById<TextView>(R.id.aboutLogoVersion)?.text = activity.getString(R.string.app_version_format, versionName)
    } catch (_: Exception) {}
    sheet.findViewById<View>(R.id.btnClose)?.setOnClickListener { sheet.dismiss() }
    sheet.show()
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
    val prefs = activity.getSharedPreferences("lavender_prefs", android.content.Context.MODE_PRIVATE)
    val currentLang = prefs.getString("language", "ru") ?: "ru"
    val newLang = if (currentLang == "ru") "en" else "ru"
    prefs.edit().putString("language", newLang).apply()

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
