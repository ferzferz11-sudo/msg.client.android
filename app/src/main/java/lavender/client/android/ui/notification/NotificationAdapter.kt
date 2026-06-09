package lavender.client.android.ui.notification

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.proto.ServerNotificationProto

/**
 * NotificationAdapter — адаптер для списка уведомлений.
 * Отображает иконку по типу, заголовок, сообщение и timestamp.
 * Непрочитанные уведомления выделяются bold title и accent-фоном.
 */
class NotificationAdapter(
    private val onNotificationClick: (ServerNotificationProto) -> Unit = {}
) : ListAdapter<ServerNotificationProto, NotificationAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view, onNotificationClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        view: View,
        private val onClick: (ServerNotificationProto) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val icon: TextView = view.findViewById(R.id.notificationIcon)
        private val title: TextView = view.findViewById(R.id.notificationTitle)
        private val message: TextView = view.findViewById(R.id.notificationMessage)
        private val timestamp: TextView = view.findViewById(R.id.notificationTimestamp)

        fun bind(notif: ServerNotificationProto) {
            icon.text = iconForType(notif.type)
            title.text = notif.title.ifEmpty { notif.type }
            message.text = notif.message
            timestamp.text = notif.timestamp

            // Visual distinction for unread notifications
            if (!notif.isRead) {
                title.setTypeface(null, Typeface.BOLD)
                title.alpha = 1.0f
                message.alpha = 1.0f
                // Subtle accent background for unread
                val bgColor = ContextCompat.getColor(itemView.context, R.color.notification_unread_bg)
                itemView.setBackgroundColor(bgColor)
            } else {
                title.setTypeface(null, Typeface.NORMAL)
                title.alpha = 0.85f
                message.alpha = 0.7f
                // Transparent background for read
                itemView.setBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.transparent))
            }

            itemView.setOnClickListener { onClick(notif) }
        }

        private fun iconForType(type: String): String {
            return when (type) {
                "deploy" -> "🚀"
                "deploy_done" -> "✅"
                "deploy_error" -> "❌"
                "restart" -> "🔄"
                "warning" -> "⚠️"
                "error" -> "🔴"
                "info" -> "ℹ️"
                else -> "📋"
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ServerNotificationProto>() {
        override fun areItemsTheSame(a: ServerNotificationProto, b: ServerNotificationProto) =
            a.id == b.id

        override fun areContentsTheSame(a: ServerNotificationProto, b: ServerNotificationProto) =
            a == b
    }
}
