package lavender.client.android.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView
import lavender.client.android.R
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

class MentionAdapter(
    private val onUserClick: (String) -> Unit
) : RecyclerView.Adapter<MentionAdapter.MentionViewHolder>() {

    private var users = listOf<String>()
    private var avatarCache = mapOf<String, String>()

    fun setUsers(newUsers: List<String>, cache: Map<String, String>) {
        users = newUsers
        avatarCache = cache
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MentionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_mention, parent, false)
        return MentionViewHolder(view)
    }

    override fun onBindViewHolder(holder: MentionViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user, avatarCache[user])
    }

    override fun getItemCount(): Int = users.size

    inner class MentionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarView: CircleImageView = itemView.findViewById(R.id.mentionAvatar)
        private val usernameView: TextView = itemView.findViewById(R.id.mentionUsername)

        fun bind(username: String, avatarUrl: String?) {
            usernameView.text = username
            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_default_avatar)
                    .circleCrop()
                    .into(avatarView)
                avatarView.clearColorFilter()
            } else {
                ThemeUtils.applyDefaultAvatar(avatarView, ThemeStore.currentTheme())
            }
            itemView.setOnClickListener { onUserClick(username) }
        }
    }
}