package msg.client.android.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import msg.client.android.R
import msg.client.android.data.models.Message
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private var currentUsername: String,
    private val onDeleteMessage: (Message) -> Unit
) : ListAdapter<Message, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {
    
    private var selectedPosition = -1
    
    fun updateUsername(newUsername: String) {
        currentUsername = newUsername
        notifyDataSetChanged()
    }
    
    fun clearSelection() {
        val previousPosition = selectedPosition
        selectedPosition = -1
        if (previousPosition != -1) {
            notifyItemChanged(previousPosition)
        }
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
        
        // Check if this message is selected
        val isSelected = selectedPosition == position
        
        holder.bind(currentMessage, isConsecutive, isOutgoing, isSelected, onDeleteMessage) {
            // Handle message click
            if (selectedPosition == position) {
                // Deselect if already selected
                selectedPosition = -1
                notifyItemChanged(position)
            } else {
                // Select this message
                val previousPosition = selectedPosition
                selectedPosition = position
                if (previousPosition != -1) {
                    notifyItemChanged(previousPosition)
                }
                notifyItemChanged(position)
            }
        }
    }
    
    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageContainer: View = itemView.findViewById(R.id.messageContainer)
        private val userText: TextView = itemView.findViewById(R.id.userText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        
        fun bind(message: Message, isConsecutive: Boolean, isOutgoing: Boolean, isSelected: Boolean, onDeleteMessage: (Message) -> Unit, onClick: () -> Unit) {
            userText.text = message.user
            messageText.text = message.text
            
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            timeText.text = timeFormat.format(Date(message.timestamp))
            
            // Set background and alignment based on message type
            val params = messageContainer.layoutParams as android.widget.LinearLayout.LayoutParams
            val context = itemView.context
            
            val typedValue = android.util.TypedValue()
            if (isOutgoing) {
                messageContainer.setBackgroundResource(R.drawable.bg_message_outgoing)
                params.gravity = android.view.Gravity.END
                
                context.theme.resolveAttribute(android.R.attr.textColorPrimaryInverse, typedValue, true)
                val colorOnPrimary = if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
                
                messageText.setTextColor(colorOnPrimary)
                userText.setTextColor(colorOnPrimary)
                timeText.setTextColor(colorOnPrimary)
            } else {
                messageContainer.setBackgroundResource(R.drawable.bg_message_incoming)
                params.gravity = android.view.Gravity.START
                
                context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                val colorOnSecondary = if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
                
                messageText.setTextColor(colorOnSecondary)
                userText.setTextColor(colorOnSecondary)
                timeText.setTextColor(colorOnSecondary)
            }
            messageContainer.layoutParams = params
            
            // Handle selection state
            if (isSelected && isOutgoing) {
                // Show delete button for selected outgoing messages
                deleteButton.visibility = View.VISIBLE
                messageContainer.alpha = 0.7f
            } else {
                deleteButton.visibility = View.GONE
                messageContainer.alpha = 1.0f
            }
            
            // Hide user for consecutive messages or outgoing messages (user knows they sent it)
            if (isConsecutive || isOutgoing) {
                userText.visibility = View.GONE
            } else {
                userText.visibility = View.VISIBLE
            }
            
            // Adjust margins for consecutive messages
            val outerParams = itemView.layoutParams as ViewGroup.MarginLayoutParams
            outerParams.topMargin = if (isConsecutive) 4 else 16
            itemView.layoutParams = outerParams
            
            // Handle click on message container
            messageContainer.setOnClickListener {
                onClick()
            }
            
            // Handle delete button click
            deleteButton.setOnClickListener {
                onDeleteMessage(message)
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
