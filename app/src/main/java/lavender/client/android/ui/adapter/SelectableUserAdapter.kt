package lavender.client.android.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView
import lavender.client.android.R

class SelectableUserAdapter(
    private val avatarCache: Map<String, String> = emptyMap(),
    private var onlineUsers: List<String> = emptyList(),
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<SelectableUserAdapter.ViewHolder>() {

    private var users = listOf<String>()
    private val selectedUsers = mutableSetOf<String>()

    fun setUsers(newUsers: List<String>) {
        users = newUsers
        notifyDataSetChanged()
    }

    fun setOnlineUsers(users: List<String>) {
        onlineUsers = users
        notifyDataSetChanged()
    }

    fun getSelectedUsers(): List<String> = selectedUsers.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_selectable, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val username = users[position]
        val isOnline = onlineUsers.contains(username)
        holder.bind(username, selectedUsers.contains(username), avatarCache[username], isOnline)
        
        holder.itemView.setOnClickListener {
            if (selectedUsers.contains(username)) {
                selectedUsers.remove(username)
            } else {
                selectedUsers.add(username)
            }
            notifyItemChanged(position)
            onSelectionChanged(selectedUsers.size)
        }
    }

    override fun getItemCount(): Int = users.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val usernameText: TextView = itemView.findViewById(R.id.usernameText)
        private val userAvatar: CircleImageView = itemView.findViewById(R.id.userAvatar)
        private val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)
        private val checkBox: CheckBox = itemView.findViewById(R.id.userCheckBox)

        fun bind(username: String, isSelected: Boolean, avatarUrl: String?, isOnline: Boolean) {
            usernameText.text = username
            checkBox.isChecked = isSelected
            
            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_default_avatar)
                    .circleCrop()
                    .into(userAvatar)
            } else {
                userAvatar.setImageResource(R.drawable.ic_default_avatar)
            }
            
            statusIndicator.setBackgroundResource(
                if (isOnline) R.drawable.status_online_dot else R.drawable.status_offline_dot
            )
        }
    }
}
