package lavender.client.android.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView
import lavender.client.android.R

class UserAdapter(
    private val onUserClick: (String) -> Unit,
    private val avatarCache: Map<String, String> = emptyMap()
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private var users = listOf<String>()
    private var selectedUser: String? = null

    fun setUsers(newUsers: List<String>) {
        users = newUsers
        notifyDataSetChanged()
    }

    fun setSelectedUser(username: String?) {
        selectedUser = username
        notifyDataSetChanged()
    }

    fun getSelectedUser(): String? = selectedUser

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val username = users[position]
        holder.bind(username, username == selectedUser, avatarCache[username])
        holder.itemView.setOnClickListener {
            selectedUser = username
            notifyDataSetChanged()
            onUserClick(username)
        }
    }

    override fun getItemCount(): Int = users.size

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val usernameText: TextView = itemView.findViewById(R.id.usernameText)
        private val userAvatar: CircleImageView = itemView.findViewById(R.id.userAvatar)
        private val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)

        fun bind(username: String, isSelected: Boolean, avatarUrl: String?) {
            usernameText.text = username
            
            // Highlight if selected
            itemView.alpha = if (isSelected) 1.0f else 0.7f
            itemView.setBackgroundResource(if (isSelected) R.drawable.rounded_background else 0)
            
            // Load avatar
            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_default_avatar)
                    .circleCrop()
                    .into(userAvatar)
            } else {
                userAvatar.setImageResource(R.drawable.ic_default_avatar)
            }
            
            // Hide status indicator for now as it's not always relevant in this adapter
            statusIndicator.visibility = View.GONE
        }
    }
}
