package lavender.client.android.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView
import lavender.client.android.R

class UserAdapter(
    private val onUserClick: (String) -> Unit,
    private val onUserLongClick: ((String) -> Unit)? = null,
    private val onSelectionChanged: ((Int) -> Unit)? = null,
    private val avatarCache: Map<String, String>,
    private var onlineUsers: List<String> = emptyList()
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private var users = listOf<String>()
    private val selectedUsers = mutableSetOf<String>()

    fun setUsers(newUsers: List<String>) {
        users = newUsers
        notifyDataSetChanged()
    }

    fun setOnlineUsers(newOnlineUsers: List<String>) {
        onlineUsers = newOnlineUsers
        notifyDataSetChanged()
    }

    fun getSelectedUser(): String? = if (selectedUsers.isNotEmpty()) selectedUsers.first() else null

    fun getSelectedUsers(): List<String> = selectedUsers.toList()

    fun clearSelection() {
        selectedUsers.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    fun toggleSelection(username: String) {
        if (selectedUsers.contains(username)) {
            selectedUsers.remove(username)
        } else {
            selectedUsers.add(username)
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedUsers.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_selectable, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        holder.bind(user, selectedUsers.contains(user))
    }

    override fun getItemCount(): Int = users.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val usernameText: TextView = itemView.findViewById(R.id.usernameText)
        private val userAvatar: CircleImageView = itemView.findViewById(R.id.userAvatar)
        private val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)
        private val checkBox: CheckBox = itemView.findViewById(R.id.userCheckBox)

        fun bind(username: String, isSelected: Boolean) {
            usernameText.text = username
            val isOnline = onlineUsers.contains(username)
            statusIndicator.isVisible = isOnline
            checkBox.isChecked = isSelected
            checkBox.isVisible = selectedUsers.isNotEmpty()

            val avatarUrl = avatarCache[username]
            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(itemView.context).load(avatarUrl).placeholder(R.drawable.ic_default_avatar).circleCrop().into(userAvatar)
            } else {
                userAvatar.setImageResource(R.drawable.ic_default_avatar)
            }

            itemView.setOnClickListener {
                onUserClick(username)
            }
            itemView.setOnLongClickListener {
                onUserLongClick?.invoke(username)
                true
            }
        }
    }
}
