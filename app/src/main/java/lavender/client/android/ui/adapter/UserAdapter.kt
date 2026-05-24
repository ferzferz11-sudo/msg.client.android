package lavender.client.android.ui.adapter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.R
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

class UserAdapter(
    private val scope: CoroutineScope,
    private val onUserClick: (String) -> Unit,
    private val onUserLongClick: ((String) -> Unit)? = null,
    private val onSelectionChanged: ((Int) -> Unit)? = null,
    private val avatarCache: Map<String, String>,
    private var onlineUsers: List<String> = emptyList()
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private var users = listOf<String>()
    private var fullUsersList = listOf<String>()
    private val selectedUsers = mutableSetOf<String>()

    // Pre-calculated theme values for performance
    private var cachedPrimaryColor: Int = 0
    private var cachedOnSurface: Int = 0
    private var cachedTextPrimary: Int = 0
    private var cachedTextSecondary: Int = 0
    private var cachedPrimaryAlpha: Int = 0
    private var cachedSurfaceAlpha: Int = 0
    private var density: Float = 1f
    private var colorsInitialized = false
    private var currentTheme: lavender.client.android.theme.Theme? = null

    private fun initColors(view: View) {
        if (colorsInitialized) return
        val theme = ThemeStore.currentTheme()
        currentTheme = theme
        cachedPrimaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
        cachedOnSurface = ThemeUtils.parseSafeColor(theme.onSurfaceColor, Color.GRAY)
        cachedTextPrimary = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        cachedTextSecondary = ThemeUtils.parseSafeColor(theme.textSecondaryColor, Color.LTGRAY)
        cachedPrimaryAlpha = adjustAlpha(cachedPrimaryColor, 0.2f)
        cachedSurfaceAlpha = adjustAlpha(cachedOnSurface, 0.05f)
        density = view.resources.displayMetrics.density
        colorsInitialized = true
    }

    fun updateTheme() {
        colorsInitialized = false
        notifyItemRangeChanged(0, itemCount)
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
                fullUsersList = newUsers
                diffResult.dispatchUpdatesTo(this@UserAdapter)
            }
        }
    }

    fun filter(query: String) {
        scope.launch(Dispatchers.Default) {
            val filtered = if (query.isEmpty()) {
                fullUsersList
            } else {
                val q = query.lowercase()
                fullUsersList.filter { it.lowercase().contains(q) }
            }
            
            val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = users.size
                override fun getNewListSize() = filtered.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int) = users[oldPos] == filtered[newPos]
                override fun areContentsTheSame(oldPos: Int, newPos: Int) = users[oldPos] == filtered[newPos]
            })
            withContext(Dispatchers.Main) {
                users = filtered
                diffResult.dispatchUpdatesTo(this@UserAdapter)
            }
        }
    }

    fun setOnlineUsers(newOnlineUsers: List<String>) {
        if (onlineUsers == newOnlineUsers) return
        val oldOnline = onlineUsers
        onlineUsers = newOnlineUsers
        users.forEachIndexed { index, username ->
            val wasOnline = oldOnline.contains(username)
            val isOnline = newOnlineUsers.contains(username)
            if (wasOnline != isOnline) {
                notifyItemChanged(index, "status")
            }
        }
    }

    fun getSelectedUser(): String? = if (selectedUsers.isNotEmpty()) selectedUsers.first() else null
    fun getSelectedUsers(): List<String> = selectedUsers.toList()

    fun clearSelection() {
        if (selectedUsers.isEmpty()) return
        selectedUsers.clear()
        // Use payload to only update checkbox/selection UI, skipping avatar rebinding
        notifyItemRangeChanged(0, itemCount, "selection_mode")
        onSelectionChanged?.invoke(0)
    }

    fun toggleSelection(username: String) {
        if (selectedUsers.contains(username)) {
            selectedUsers.remove(username)
        } else {
            selectedUsers.add(username)
        }
        val index = users.indexOf(username)
        if (index != -1) {
            notifyItemChanged(index, "selection")
        }
        onSelectionChanged?.invoke(selectedUsers.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_selectable, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(users[position], selectedUsers.contains(users[position]))
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            for (payload in payloads) {
                when (payload) {
                    "status" -> holder.updateStatus(users[position])
                    "selection", "selection_mode" -> holder.updateSelection(selectedUsers.contains(users[position]), selectedUsers.isNotEmpty())
                }
            }
        }
    }

    override fun getItemCount(): Int = users.size

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val usernameText: TextView = itemView.findViewById(R.id.usernameText)
        private val userAvatar: ShapeableImageView = itemView.findViewById(R.id.userAvatar)
        private val statusIndicator: View = itemView.findViewById(R.id.statusIndicator)
        private val checkBox: CheckBox = itemView.findViewById(R.id.userCheckBox)

        fun updateStatus(username: String) {
            statusIndicator.isVisible = onlineUsers.contains(username)
        }

        fun updateSelection(isSelected: Boolean, isSelectionMode: Boolean) {
            initColors(itemView)
            checkBox.isChecked = isSelected
            checkBox.isVisible = isSelectionMode
            checkBox.buttonTintList = ColorStateList.valueOf(if (isSelected) cachedPrimaryColor else cachedTextSecondary)
            
            if (isSelected) {
                cardView.setCardBackgroundColor(cachedPrimaryAlpha)
                cardView.strokeWidth = (2 * density).toInt()
                cardView.strokeColor = cachedPrimaryColor
                cardView.cardElevation = (4 * density)
            } else {
                cardView.strokeWidth = 0
                cardView.cardElevation = 0f
                cardView.setCardBackgroundColor(cachedSurfaceAlpha)
            }
        }

        fun bind(username: String, isSelected: Boolean) {
            initColors(itemView)
            usernameText.text = username
            updateStatus(username)
            updateSelection(isSelected, selectedUsers.isNotEmpty())
            
            usernameText.setTextColor(cachedTextPrimary)
            itemView.alpha = 1.0f

            val avatarUrl = avatarCache[username]
            if (!avatarUrl.isNullOrEmpty()) {
                Glide.with(itemView.context).load(avatarUrl).placeholder(R.drawable.ic_default_avatar).circleCrop().into(userAvatar)
                userAvatar.clearColorFilter()
            } else {
                currentTheme?.let { ThemeUtils.applyDefaultAvatar(userAvatar, it) }
            }

            itemView.setOnClickListener { onUserClick(username) }
            itemView.setOnLongClickListener {
                onUserLongClick?.invoke(username)
                true
            }
        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
}
