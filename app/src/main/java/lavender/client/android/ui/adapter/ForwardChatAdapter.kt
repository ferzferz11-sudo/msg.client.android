package lavender.client.android.ui.adapter
import android.util.Log

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView
import lavender.client.android.R
import lavender.client.android.data.models.ChatInfo
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import org.json.JSONArray

class ForwardChatAdapter(
    private val chats: List<ChatInfo>,
    private val currentUsername: String,
    private val avatarCache: Map<String, String>,
    private val onChatSelected: (ChatInfo) -> Unit
) : RecyclerView.Adapter<ForwardChatAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chat = chats[position]
        holder.bind(chat)
    }

    override fun getItemCount(): Int = chats.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val chatName: TextView = view.findViewById(R.id.tvChatName)
        private val chatType: TextView = view.findViewById(R.id.tvChatType)
        private val participantAvatars: LinearLayout = view.findViewById(R.id.participantAvatars)
        private val unreadCount: TextView = view.findViewById(R.id.tvUnreadCount)

        fun bind(chat: ChatInfo) {
            val context = itemView.context
            chatName.text = if (chat.type == "saved_messages") context.getString(R.string.saved_messages) else chat.getDisplayName(currentUsername)
            chatType.text = when (chat.type) {
                "direct" -> context.getString(R.string.direct_chat_type)
                "group" -> context.getString(R.string.group_chat_type)
                "saved_messages" -> context.getString(R.string.saved_messages_description)
                else -> chat.type.replaceFirstChar { it.uppercase() }
            }
            unreadCount.visibility = View.GONE
            
            // Set colors from theme
            val theme = ThemeStore.currentTheme()
            try {
                (itemView as? com.google.android.material.card.MaterialCardView)?.setCardBackgroundColor(
                    android.content.res.ColorStateList.valueOf(theme.surfaceColor.toColorInt())
                )
                chatName.setTextColor(theme.textPrimaryColor.toColorInt())
                chatType.setTextColor(theme.textSecondaryColor.toColorInt())
            } catch (e: Exception) { Log.w(TAG, "Caught: " + e.message) }

            loadAvatars(chat)

            itemView.setOnClickListener { onChatSelected(chat) }
        }

        private fun loadAvatars(chat: ChatInfo) {
            participantAvatars.removeAllViews()
            val context = itemView.context
            
            if (chat.type == "saved_messages") {
                val avatar = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(52.dpToPx(), 52.dpToPx())
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setImageResource(R.drawable.ic_star)
                    val theme = ThemeStore.currentTheme()
                    imageTintList = android.content.res.ColorStateList.valueOf(theme.primaryColor.toColorInt())
                    val p = 12.dpToPx()
                    setPadding(p, p, p, p)
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(ThemeUtils.adjustAlpha(theme.primaryColor.toColorInt(), 0.15f))
                    }
                }
                participantAvatars.addView(avatar)
                return
            }

            if (chat.avatarUrl.isNotEmpty()) {
                val iv = CircleImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(52.dpToPx(), 52.dpToPx())
                }
                Glide.with(context).load(chat.avatarUrl).placeholder(R.drawable.ic_default_avatar).into(iv)
                participantAvatars.addView(iv)
            } else {
                try {
                    val arr = JSONArray(chat.participants)
                    for (i in 0 until arr.length().coerceAtMost(if (chat.type == "direct") 1 else 3)) {
                        val u = arr.getString(i)
                        if (chat.type == "direct" && u == currentUsername && arr.length() > 1) continue
                        
                        val iv = CircleImageView(context).apply {
                            val size = 52.dpToPx()
                            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                                if (i > 0) marginStart = (-15).dpToPx()
                            }
                            borderWidth = 2.dpToPx()
                            borderColor = ThemeStore.currentTheme().surfaceColor.toColorInt()
                        }
                        
                        val url = avatarCache[u]
                        if (!url.isNullOrEmpty()) {
                            Glide.with(context).load(url).placeholder(R.drawable.ic_default_avatar).into(iv)
                        } else {
                            ThemeUtils.applyDefaultAvatar(iv, ThemeStore.currentTheme())
                        }
                        participantAvatars.addView(iv)
                    }
                } catch (_: Exception) {
                    val iv = CircleImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(52.dpToPx(), 52.dpToPx())
                    }
                    ThemeUtils.applyDefaultAvatar(iv, ThemeStore.currentTheme())
                    participantAvatars.addView(iv)
                }
            }
        }

        private fun Int.dpToPx(): Int = (this * itemView.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TAG = "ForwardChatAdapter"
    }
}
