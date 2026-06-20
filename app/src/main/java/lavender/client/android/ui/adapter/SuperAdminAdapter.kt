package lavender.client.android.ui.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import lavender.client.android.data.proto.UserInfoProto
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import java.text.SimpleDateFormat
import java.util.*

class SuperAdminAdapter(
    private val onUserClick: (UserInfoProto) -> Unit,
    private val onUserLongClick: (UserInfoProto) -> Unit,
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
    }

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
                else if (old is ChatInfo && new is ChatInfo) old.id == new.id
                else false
            }
            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                val old = items[oldPos]
                val new = newItems[newPos]
                val oldId = if (old is UserInfoProto) old.username else (old as ChatInfo).id
                val newId = if (new is UserInfoProto) new.username else (new as ChatInfo).id
                return old == new && selectedIds.contains(oldId) == selectedIds.contains(newId)
            }
        })
        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    fun toggleSelection(id: String) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        val index = items.indexOfFirst { 
            if (it is UserInfoProto) it.username == id else (it as ChatInfo).id == id 
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
                if (it is UserInfoProto) it.username == id else (it as ChatInfo).id == id 
            }
            if (index != -1) notifyItemChanged(index)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is UserInfoProto) TYPE_USER else TYPE_CHAT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserViewHolder(inflater.inflate(R.layout.item_user_super_admin, parent, false))
        } else {
            ChatViewHolder(inflater.inflate(R.layout.item_chat, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is UserViewHolder) {
            holder.bind(item as UserInfoProto)
        } else if (holder is ChatViewHolder) {
            holder.bind(item as ChatInfo)
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
