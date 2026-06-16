package lavender.client.android.ui.widget

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.ServersActivity
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.Message
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.session.SessionManager
import lavender.client.android.EditProfileActivity
import lavender.client.android.ui.chatlist.ChatListActivity
import lavender.client.android.ui.hermes.HermesChatActivity
import lavender.client.android.ui.owl.OwlChatActivity
import androidx.lifecycle.lifecycleScope

/**
 * ProfileBottomSheet — шторка профиля при тапе на аватар/тулбар.
 * Заменяет переход в ProfileActivity — всё в одном месте.
 */
class ProfileBottomSheet : BottomSheetDialogFragment() {

    companion object {
        fun newInstance() = ProfileBottomSheet()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()
        val username = CredentialStore.getUsername(context)
        val avatarUrl = GrpcClient.getAvatarCache()[username] ?: ""

        // Avatar
        val ivAvatar = view.findViewById<ImageView>(R.id.ivProfileAvatar)
        if (avatarUrl.isNotEmpty()) {
            Glide.with(this)
                .load(avatarUrl)
                .apply(RequestOptions.circleCropTransform().placeholder(R.drawable.ic_default_avatar))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(ivAvatar)
        }

        // Username
        view.findViewById<TextView>(R.id.tvProfileUsername)?.text = username

        // Edit Profile
        view.findViewById<View>(R.id.btnEditProfile)?.setOnClickListener {
            dismiss()
            context.startActivity(Intent(context, EditProfileActivity::class.java))
        }

        // Settings
        view.findViewById<View>(R.id.btnSettings)?.setOnClickListener {
            dismiss()
            // TODO: Open settings
        }

        // Servers
        view.findViewById<View>(R.id.btnServers)?.setOnClickListener {
            dismiss()
            context.startActivity(Intent(context, ServersActivity::class.java))
        }

        // Logout
        view.findViewById<View>(R.id.btnLogout)?.setOnClickListener {
            dismiss()
            GrpcClient.disconnect()
            SessionManager.logout(context)
            val intent = Intent(context, ChatListActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }
    }
}
