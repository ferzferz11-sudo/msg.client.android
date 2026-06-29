package lavender.client.android.ui.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import de.hdodenhof.circleimageview.CircleImageView
import lavender.client.android.R
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.data.proto.AdminUserInfoProto
import lavender.client.android.data.proto.AdminUserSessionProto
import lavender.client.android.data.proto.UserInfoProto
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import java.text.SimpleDateFormat
import java.util.*

class SuperAdminAdapter(
    private val onUserClick: (Any) -> Unit,
    private val onUserLongClick: (Any) -> Unit,
    private val onChatClick: (ChatInfo) -> Unit,
    private val onChatLongClick: (ChatInfo) -> Unit,
    private val onlineUsers: Set<String>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items = listOf<Any>()
    private val selectedIds = mutableSetOf<String>()
    
    private var primaryColor: Int = Color.BLUE
    private var surfaceColor: Int = Color.DKGRAY
    private var textPrimary: Int = Color.WHITE
    private var textSecondary: Int = Color.LTGRAY
    private var onPrimary: Int = Color.WHITE
    
    private val timeFormat = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_CHAT = 1
        private const val TYPE_SESSION = 2
    }

    private val expandedUsers = mutableSetOf<String>()
    private val userSessions = mutableMapOf<String, List<AdminUserSessionProto>>()

    init {
        updateTheme()
    }

    fun updateTheme() {
        val theme = ThemeStore.currentTheme()
        primaryColor = try { theme.primaryColor.toColorInt() } catch (_: Exception) { Color.BLUE }
        surfaceColor = try { theme.surfaceColor.toColorInt() } catch (_: Exception) { Color.DKGRAY }
        textPrimary = try { theme.textPrimaryColor.toColorInt() } catch (_: Exception) { Color.WHITE }
        textSecondary = try { theme.textSecondaryColor.toColorInt() } catch (_: Exception) { Color.LTGRAY }
        onPrimary = try { theme.onPrimaryColor.toColorInt() } catch (_: Exception) { Color.WHITE }
        notifyDataSetChanged()
    }

    fun setItems(newItems: List<Any>) {
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                val old = items[oldPos]
                val new = newItems[newPos]
                return if (old is UserInfoProto && new is UserInfoProto) old.username == new.username
                else if (old is AdminUserInfoProto && new is AdminUserInfoProto) old.username == new.username
                else if (old is ChatInfo && new is ChatInfo) old.id == new.id
                else false
            }
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val old = items[oldPos]
                val new = newItems[newPos]
                val oldId = when (old) {
                    is UserInfoProto -> old.username
                    is AdminUserInfoProto -> old.username
                    else -> (old as ChatInfo).id
                }
                val newId = when (new) {
                    is UserInfoProto -> new.username
                    is AdminUserInfoProto -> new.username
                    else -> (new as ChatInfo).id
                }
                return old == new && selectedIds.contains(oldId) == selectedIds.contains(newId)
            }
        })
        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    fun setAdminItems(newItems: List<AdminUserInfoProto>) {
        val mixed = mutableListOf<Any>()
        for (user in newItems) {
            mixed.add(user)
            if (expandedUsers.contains(user.username)) {
                userSessions[user.username]?.forEach { mixed.add(it) }
            }
        }
        items = mixed
        notifyDataSetChanged()
    }

    fun toggleSessions(user: AdminUserInfoProto) {
        val username = user.username
        if (expandedUsers.contains(username)) {
            expandedUsers.remove(username)
        } else {
            expandedUsers.add(username)
        }
        setAdminItems(items.filterIsInstance<AdminUserInfoProto>())
    }

    fun setSessions(username: String, sessions: List<AdminUserSessionProto>) {
        userSessions[username] = sessions
        if (expandedUsers.contains(username)) {
            setAdminItems(items.filterIsInstance<AdminUserInfoProto>())
        }
    }

    fun isExpanded(username: String): Boolean = expandedUsers.contains(username)

    fun clearExpanded() {
        expandedUsers.clear()
        userSessions.clear()
    }

    fun toggleSelection(id: String) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        val index = items.indexOfFirst { 
            when (it) {
                is UserInfoProto -> it.username == id
                is AdminUserInfoProto -> it.username == id
                else -> (it as ChatInfo).id == id
            }
        }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    fun clearSelection() {
        val oldSelected = selectedIds.toList()
        selectedIds.clear()
        oldSelected.forEach { id ->
            val index = items.indexOfFirst { 
                when (it) {
                    is UserInfoProto -> it.username == id
                    is AdminUserInfoProto -> it.username == id
                    else -> (it as ChatInfo).id == id
                }
            }
            if (index != -1) notifyItemChanged(index)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is UserInfoProto, is AdminUserInfoProto -> TYPE_USER
            is AdminUserSessionProto -> TYPE_SESSION
            else -> TYPE_CHAT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserViewHolder(inflater.inflate(R.layout.item_user_super_admin, parent, false))
            TYPE_SESSION -> SessionViewHolder(inflater.inflate(R.layout.item_admin_session, parent, false))
            else -> ChatViewHolder(inflater.inflate(R.layout.item_chat, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is UserViewHolder -> when (item) {
                is UserInfoProto -> holder.bind(item)
                is AdminUserInfoProto -> holder.bindAdmin(item)
            }
            is SessionViewHolder -> holder.bind(item as AdminUserSessionProto)
            is ChatViewHolder -> holder.bind(item as ChatInfo)
        }
    }

    override fun getItemCount() = items.size

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view as MaterialCardView
        private val nameText = view.findViewById<TextView>(R.id.participantName)
        private val versionText = view.findViewById<TextView>(R.id.clientVersion)
        private val timeAgoText = view.findViewById<TextView>(R.id.timeAgoText)
        private val avatarView = view.findViewById<CircleImageView>(R.id.participantAvatar)
        private val statusDot = view.findViewById<View>(R.id.statusIndicator)
        private val lastMessageText: TextView? = view.findViewById(R.id.lastMessageText)
        private val chatCountText: TextView? = view.findViewById(R.id.chatCountText)
        private val adminBadge: View? = view.findViewById(R.id.adminBadge)

        fun bind(user: UserInfoProto) {
            val isSelected = selectedIds.contains(user.username)
            card.setCardBackgroundColor(if (isSelected) primaryColor else surfaceColor)
            nameText.text = user.username
            nameText.setTextColor(if (isSelected) onPrimary else textPrimary)
            
            val versionStr = if (user.lastClientVersion.isNotEmpty()) "v${user.lastClientVersion}" else ""
            versionText.text = versionStr
            versionText.setTextColor(if (isSelected) onPrimary else textSecondary)
            
            val timeAgoStr = user.lastSeenAt?.let { getTimeAgo(it.seconds * 1000, itemView.context) } ?: ""
            timeAgoText.text = timeAgoStr
            timeAgoText.setTextColor(if (isSelected) onPrimary else textSecondary)
            
            val isOnline = onlineUsers.contains(user.username)
            statusDot.isVisible = !isSelected
            statusDot.setBackgroundResource(if (isOnline) R.drawable.status_online_dot else R.drawable.status_offline_dot)

            lastMessageText?.isVisible = false
            chatCountText?.isVisible = false
            adminBadge?.isVisible = false

            if (user.avatarUrl.isNotEmpty()) {
                Glide.with(itemView.context).load(user.avatarUrl).placeholder(R.drawable.ic_default_avatar).into(avatarView)
                avatarView.clearColorFilter()
            } else {
                ThemeUtils.applyDefaultAvatar(avatarView, ThemeStore.currentTheme())
            }

            itemView.setOnClickListener { onUserClick(user) }
            itemView.setOnLongClickListener {
                onUserLongClick(user)
                true
            }
        }

        fun bindAdmin(user: AdminUserInfoProto) {
            val isSelected = selectedIds.contains(user.username)
            card.setCardBackgroundColor(if (isSelected) primaryColor else surfaceColor)
            nameText.text = user.username
            nameText.setTextColor(if (isSelected) onPrimary else textPrimary)
            
            val versionStr = if (user.lastClientVersion.isNotEmpty()) "v${user.lastClientVersion}" else ""
            versionText.text = versionStr
            versionText.setTextColor(if (isSelected) onPrimary else textSecondary)
            
            val timeAgoStr = user.lastSeenAt?.let { getTimeAgo(it.seconds * 1000, itemView.context) } ?: ""
            timeAgoText.text = timeAgoStr
            timeAgoText.setTextColor(if (isSelected) onPrimary else textSecondary)
            
            statusDot.isVisible = !isSelected
            statusDot.setBackgroundResource(if (user.isOnline) R.drawable.status_online_dot else R.drawable.status_offline_dot)

            if (user.lastMessageText.isNotEmpty()) {
                lastMessageText?.text = user.lastMessageText
                lastMessageText?.isVisible = true
                lastMessageText?.setTextColor(if (isSelected) onPrimary else textSecondary)
            } else {
                lastMessageText?.isVisible = false
            }

            if (user.chatCount > 0) {
                chatCountText?.text = itemView.context.resources.getQuantityString(R.plurals.chats_count, user.chatCount, user.chatCount)
                chatCountText?.isVisible = true
                chatCountText?.setTextColor(if (isSelected) onPrimary else textSecondary)
            } else {
                chatCountText?.isVisible = false
            }

            adminBadge?.isVisible = user.isSuperAdmin && !isSelected

            if (user.avatarUrl.isNotEmpty()) {
                Glide.with(itemView.context).load(user.avatarUrl).placeholder(R.drawable.ic_default_avatar).into(avatarView)
                avatarView.clearColorFilter()
            } else {
                ThemeUtils.applyDefaultAvatar(avatarView, ThemeStore.currentTheme())
            }

            itemView.setOnClickListener { onUserClick(user) }
            itemView.setOnLongClickListener {
                onUserLongClick(user)
                true
            }
        }
    }

    inner class SessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val deviceTypeIcon: ImageView = view.findViewById(R.id.deviceTypeIcon)
        private val deviceNameText: TextView = view.findViewById(R.id.deviceNameText)
        private val versionText: TextView = view.findViewById(R.id.sessionVersionText)
        private val ipText: TextView = view.findViewById(R.id.ipText)
        private val lastSeenText: TextView = view.findViewById(R.id.lastSeenText)
        private val onlineDot: View = view.findViewById(R.id.sessionOnlineDot)

        fun bind(session: AdminUserSessionProto) {
            val iconRes = when (session.deviceType.lowercase()) {
                "web" -> R.drawable.ic_web
                "android" -> R.drawable.ic_android
                else -> R.drawable.ic_device
            }
            deviceTypeIcon.setImageResource(iconRes)
            deviceTypeIcon.setColorFilter(textSecondary)

            deviceNameText.text = session.deviceName.ifEmpty { session.deviceType }
            deviceNameText.setTextColor(textPrimary)

            versionText.text = if (session.clientVersion.isNotEmpty()) "v${session.clientVersion}" else ""
            versionText.setTextColor(textSecondary)

            ipText.text = if (session.ipAddress.isNotEmpty() && session.ipAddress != "unknown") session.ipAddress else ""
            ipText.setTextColor(textSecondary)

            val lastSeenStr = session.lastSeenAt?.let { getTimeAgo(it.seconds * 1000, itemView.context) } ?: ""
            lastSeenText.text = lastSeenStr
            lastSeenText.setTextColor(textSecondary)

            onlineDot.setBackgroundResource(if (session.isOnline) R.drawable.status_online_dot else R.drawable.status_offline_dot)
        }
    }

    inner class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view as MaterialCardView
        private val nameText = view.findViewById<TextView>(R.id.tvChatName)
        private val typeText = view.findViewById<TextView>(R.id.tvChatType)

        fun bind(chat: ChatInfo) {
            val isSelected = selectedIds.contains(chat.id)
            card.setCardBackgroundColor(if (isSelected) primaryColor else surfaceColor)
            nameText.text = chat.name
            nameText.setTextColor(if (isSelected) onPrimary else textPrimary)
            
            val creationTime = timeFormat.format(Date(chat.createdAt))
            val description = if (chat.type.equals("direct", true)) {
                "${chat.type} - $creationTime\nID: ${chat.id}"
            } else {
                val adminStr = if (chat.creator.isNotEmpty()) "Admin: ${chat.creator}" else ""
                "${chat.type} - $creationTime\n$adminStr\nID: ${chat.id}"
            }
            
            typeText.text = description
            typeText.setTextColor(if (isSelected) onPrimary else textSecondary)
            
            itemView.setOnClickListener { onChatClick(chat) }
            itemView.setOnLongClickListener {
                onChatLongClick(chat)
                true
            }
        }
    }

    private fun getTimeAgo(timestampMillis: Long, context: Context): String {
        val now = System.currentTimeMillis()
        val diff = now - timestampMillis

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            seconds < 60 -> context.getString(R.string.just_now)
            minutes < 60 -> context.resources.getQuantityString(R.plurals.minutes_ago, minutes.toInt(), minutes.toInt())
            hours < 24 -> context.resources.getQuantityString(R.plurals.hours_ago, hours.toInt(), hours.toInt())
            days < 7 -> context.resources.getQuantityString(R.plurals.days_ago, days.toInt(), days.toInt())
            else -> timeFormat.format(Date(timestampMillis))
        }
    }
}
