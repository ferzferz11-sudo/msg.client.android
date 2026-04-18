package msg.client.android.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import msg.client.android.R
import msg.client.android.data.models.Message
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(private var currentUsername: String) : ListAdapter<Message, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {
    
    fun updateUsername(newUsername: String) {
        currentUsername = newUsername
        notifyDataSetChanged()
    }
    
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
        
        // Check if this is an outgoing message (from current user)
        val isOutgoing = currentMessage.user == currentUsername
        
        holder.bind(currentMessage, isConsecutive, isOutgoing)
    }
    
    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userText: TextView = itemView.findViewById(R.id.userText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        
        fun bind(message: Message, isConsecutive: Boolean, isOutgoing: Boolean) {
            userText.text = message.user
            messageText.text = message.text
            
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            timeText.text = timeFormat.format(Date(message.timestamp))
            
            // Set background based on message type
            val backgroundColor = if (isOutgoing) {
                ContextCompat.getColor(itemView.context, R.color.outgoing_message_background)
            } else {
                ContextCompat.getColor(itemView.context, R.color.incoming_message_background)
            }
            
            // Create drawable with rounded corners and color using GradientDrawable
            val drawable = android.graphics.drawable.GradientDrawable()
            drawable.cornerRadius = 8f
            drawable.setColor(backgroundColor)
            itemView.background = drawable
            
            // Set text color for outgoing messages
            if (isOutgoing) {
                messageText.setTextColor(ContextCompat.getColor(itemView.context, R.color.outgoing_message_text))
                userText.setTextColor(ContextCompat.getColor(itemView.context, R.color.outgoing_message_text))
            }
            
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
