package lavender.client.android.ui.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.R
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

class SelectableUserAdapter(
    private val scope: CoroutineScope,
    private var avatarCache: Map<String, String> = emptyMap(),
    private var onlineUsers: List<String> = emptyList(),
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<SelectableUserAdapter.ViewHolder>() {

    private var users = listOf<String>()
    private val selectedUsers = mutableSetOf<String>()

    // Cached theme values
    private var cachedPrimary: Int = 0
    private var cachedSecondary: Int = 0
    private var cachedTextPrimary: Int = 0
    private var cachedSurface: Int = 0
    private var colorsInitialized = false
    private var currentTheme: lavender.client.android.theme.Theme? = null

    private fun initColors() {
        if (colorsInitialized) return
        val theme = ThemeStore.currentTheme()
        currentTheme = theme
        try {
            cachedPrimary = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
            cachedSecondary = ThemeUtils.parseSafeColor(theme.textSecondaryColor, Color.GRAY)
            cachedTextPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
            cachedSurface = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
            colorsInitialized = true
        } catch (_: Exception) {}
    }

    fun setUsers(newUsers: List<String>) {
        scope.launch(Dispatchers.Default) {
            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = users.size
                override fun getNewListSize() = newUsers.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int) = users[oldPos] == newUsers[newPos]
                override fun areContentsTheSame(oldPos: Int, newPos: Int) = users[oldPos] == newUsers[newPos]
            })
            withContext(Dispatchers.Main) {
                users = newUsers
                diffResult.dispatchUpdatesTo(this@SelectableUserAdapter)
            }
        }
    }

    fun updateAvatarCache(newCache: Map<String, String>) {
        this.avatarCache = newCache
        notifyDataSetChanged()
    }

    fun setOnlineUsers(newOnlineUsers: List<String>) {
        if (onlineUsers == newOnlineUsers) return
        val oldOnline = onlineUsers
        onlineUsers = newOnlineUsers
        users.forEachIndexed { index, username ->
            if (oldOnline.contains(username) != newOnlineUsers.contains(username)) {
                notifyItemChanged(index, "status")
            }
        }
    }

    fun getSelectedUsers(): List<String> = selectedUsers.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_selectable, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        initColors()
        val username = users[position]
        val isOnline = onlineUsers.contains(username)
        holder.bind(username, selectedUsers.contains(username), avatarCache[username], isOnline, cachedPrimary, cachedSecondary, cachedTextPrimary, cachedSurface, currentTheme)
        
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
        private val userAvatar: ShapeableImageView = itemView.findViewById(R.id.userAvatar)
        private val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)
        private val checkBox: CheckBox = itemView.findViewById(R.id.userCheckBox)
        private val cardView: com.google.android.material.card.MaterialCardView = itemView as com.google.android.material.card.MaterialCardView

        fun bind(username: String, isSelected: Boolean, avatarUrl: String?, isOnline: Boolean, primary: Int, secondary: Int, textPrimary: Int, surface: Int, theme: lavender.client.android.theme.Theme?) {
            usernameText.text = username
            checkBox.isChecked = isSelected
            
            checkBox.buttonTintList = ColorStateList.valueOf(if (isSelected) primary else secondary)
            usernameText.setTextColor(textPrimary)
            cardView.setCardBackgroundColor(surface)

            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(itemView.context)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_default_avatar)
                    .circleCrop()
                    .into(userAvatar)
                userAvatar.clearColorFilter()
            } else {
                theme?.let { ThemeUtils.applyDefaultAvatar(userAvatar, it) }
            }
            
            statusIndicator.setBackgroundResource(
                if (isOnline) R.drawable.status_online_dot else R.drawable.status_offline_dot
            )
        }
    }
}
