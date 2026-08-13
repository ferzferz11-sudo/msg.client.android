package lavender.client.android.ui.chat.message

import android.graphics.Color
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.protobuf.Timestamp
import de.hdodenhof.circleimageview.CircleImageView
import lavender.client.android.R
import lavender.client.android.data.proto.ProtoUtils
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

/**
 * Bottom sheet shown before initiating a call.
 * Displays user info, last seen, and audio/video call options.
 */
class PreCallSheet(
    private val activity: android.app.Activity,
    private val username: String,
    private val userId: String,
    private val avatarUrl: String?,
    private val lastSeenAt: Timestamp?,
    private val onAudioCall: (String, String) -> Unit,
    private val onVideoCall: (String, String) -> Unit
) {
    private var dialog: BottomSheetDialog? = null

    fun show() {
        dialog = BottomSheetDialog(activity)
        dialog?.setContentView(R.layout.sheet_pre_call)
        val view = dialog?.findViewById<android.view.View>(com.google.android.material.R.id.design_bottom_sheet) ?: return

        val theme = ThemeStore.currentTheme()
        val bgColor = ThemeUtils.parseSafeColor(theme.backgroundColor, Color.BLACK)
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val secondaryColor = ThemeUtils.parseSafeColor(theme.textSecondaryColor, Color.GRAY)

        view.setBackgroundColor(bgColor)

        // Avatar
        val avatar = view.findViewById<CircleImageView>(R.id.ivPreCallAvatar)
        if (!avatarUrl.isNullOrEmpty()) {
            Glide.with(activity.applicationContext).load(avatarUrl).placeholder(R.drawable.ic_default_avatar).into(avatar)
        } else {
            ThemeUtils.applyDefaultAvatar(avatar, theme)
        }

        // Name
        val tvName = view.findViewById<TextView>(R.id.tvPreCallName)
        tvName.text = username
        tvName.setTextColor(txtColor)

        // Last seen
        val tvLastSeen = view.findViewById<TextView>(R.id.tvPreCallLastSeen)
        val lastSeenText = ProtoUtils.formatLastSeen(lastSeenAt, activity)
        tvLastSeen.text = if (lastSeenText.isNotEmpty()) lastSeenText else activity.getString(R.string.offline)
        tvLastSeen.setTextColor(secondaryColor)

        // Audio call button
        val btnAudio = view.findViewById<android.widget.LinearLayout>(R.id.btnAudioCall)
        val ivAudio = view.findViewById<ImageView>(R.id.ivAudioCall)
        ivAudio.imageTintList = android.content.res.ColorStateList.valueOf(theme.primaryColor.toColorInt())
        btnAudio.setOnClickListener {
            dialog?.dismiss()
            onAudioCall(userId, username)
        }

        // Video call button
        val btnVideo = view.findViewById<android.widget.LinearLayout>(R.id.btnVideoCall)
        val ivVideo = view.findViewById<ImageView>(R.id.ivVideoCall)
        ivVideo.imageTintList = android.content.res.ColorStateList.valueOf(theme.primaryColor.toColorInt())
        btnVideo.setOnClickListener {
            dialog?.dismiss()
            onVideoCall(userId, username)
        }

        // Cancel button
        val btnCancel = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelCall)
        btnCancel.setTextColor(theme.primaryColor.toColorInt())
        btnCancel.setOnClickListener { dialog?.dismiss() }

        dialog?.show()
    }
}
