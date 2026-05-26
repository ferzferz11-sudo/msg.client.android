package lavender.client.android.ui.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import lavender.client.android.R
import lavender.client.android.theme.Theme
import lavender.client.android.theme.ThemeUtils

class ParticipantAdapter(
    private var theme: Theme,
    private val isAdmin: Boolean,
    private val creator: String,
    private val onRemoveClick: (String) -> Unit,
    private val onAvatarClick: (String, String) -> Unit,
    private val onLongClick: (String) -> Unit
) : RecyclerView.Adapter<ParticipantAdapter.ViewHolder>() {

    private var participants = listOf<String>()
    private var avatarCache = mapOf<String, String>()
    private var onlineUsers = setOf<String>()

    fun updateData(newParticipants: List<String>, newOnlineUsers: Set<String>, newAvatarCache: Map<String, String>) {
        this.participants = newParticipants
        this.onlineUsers = newOnlineUsers
        this.avatarCache = newAvatarCache
        notifyDataSetChanged()
    }

    fun updateTheme(newTheme: Theme) {
        this.theme = newTheme
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_participant, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val user = participants[position]
        holder.bind(user, theme, isAdmin, creator, onlineUsers.contains(user), avatarCache[user])
    }

    override fun getItemCount() = participants.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameText: TextView = view.findViewById(R.id.participantName)
        private val avatarView: ImageView = view.findViewById(R.id.participantAvatar)
        private val statusDot: View = view.findViewById(R.id.statusIndicator)
        private val btnRemove: View = view.findViewById(R.id.btnRemove)

        fun bind(username: String, theme: Theme, isMeAdmin: Boolean, creator: String, isOnline: Boolean, avatarUrl: String?) {
            val context = itemView.context
            val trimmedUser = username.trim()
            val isAdminLabel = if (trimmedUser == creator.trim() && creator.isNotEmpty()) " ${context.getString(R.string.admin_label)}" else ""
            nameText.text = "$trimmedUser$isAdminLabel"

            val textPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.BLACK)
            val primary = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
            
            nameText.setTextColor(textPrimary)
            statusDot.isVisible = true
            statusDot.setBackgroundResource(if (isOnline) R.drawable.status_online_dot else R.drawable.status_offline_dot)

            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(context).load(avatarUrl).placeholder(R.drawable.ic_default_avatar).into(avatarView)
            } else {
                ThemeUtils.applyDefaultAvatar(avatarView, theme)
            }

            if (avatarView is de.hdodenhof.circleimageview.CircleImageView) {
                avatarView.borderColor = primary
                avatarView.borderWidth = (1.5f * context.resources.displayMetrics.density).toInt()
            }

            avatarView.setOnClickListener { onAvatarClick(username, avatarUrl ?: "") }

            btnRemove.isVisible = isMeAdmin && username != creator
            btnRemove.setOnClickListener { onRemoveClick(username) }

            itemView.setOnLongClickListener {
                onLongClick(username)
                true
            }
        }
    }
}
