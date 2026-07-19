package lavender.client.android.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.models.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PinnedMessageAdapter(
    private val onClick: (Message) -> Unit
) : ListAdapter<Message, PinnedMessageAdapter.ViewHolder>(DiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSender: TextView = view.findViewById(R.id.tvSender)
        val tvText: TextView = view.findViewById(R.id.tvText)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pinned_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = getItem(position)
        holder.tvSender.text = msg.user
        holder.tvText.text = msg.text.ifEmpty { holder.itemView.context.getString(R.string.pinned_message) }
        holder.tvTime.text = formatTime(msg.timestamp)
        holder.itemView.setOnClickListener { onClick(msg) }
    }

    private fun formatTime(ts: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(ts))
    }

    class DiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(a: Message, b: Message) = a.id == b.id
        override fun areContentsTheSame(a: Message, b: Message) = a == b
    }
}
