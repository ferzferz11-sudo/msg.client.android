package msg.client.android.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import msg.client.android.R
import msg.client.android.data.models.Message
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter : ListAdapter<Message, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val currentMessage = getItem(position)
        val previousMessage = if (position > 0) getItem(position - 1) else null
        
        // Check if this is a continuation of the same user's message
        val isConsecutive = previousMessage != null &&
            previousMessage.user == currentMessage.user
        
        holder.bind(currentMessage, isConsecutive)
    }
    
    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userText: TextView = itemView.findViewById(R.id.userText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        
        fun bind(message: Message, isConsecutive: Boolean) {
            userText.text = message.user
            messageText.text = message.text
            
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            timeText.text = timeFormat.format(Date(message.timestamp))
            
            // Hide user and time for consecutive messages
            if (isConsecutive) {
                userText.visibility = View.GONE
                timeText.visibility = View.GONE
                // Reduce top margin for consecutive messages
                (itemView.layoutParams as ViewGroup.MarginLayoutParams).topMargin = 2
            } else {
                userText.visibility = View.VISIBLE
                timeText.visibility = View.VISIBLE
                // Normal margin for first message in group
                (itemView.layoutParams as ViewGroup.MarginLayoutParams).topMargin = 16
            }
        }
    }
}

class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
    override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
        return oldItem.timestamp == newItem.timestamp && oldItem.user == newItem.user
    }
    
    override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
        return oldItem == newItem
    }
}
