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
    private val onSelectionChanged: (Int) -> Unit
) : ListAdapter<Message, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {
    
    private val selectedPositions = mutableSetOf<Int>()
    
    fun getSelectedMessages(): List<Message> {
        return selectedPositions.map { getItem(it) }
    }
    
    fun updateUsername(newUsername: String) {
        currentUsername = newUsername
        notifyItemRangeChanged(0, itemCount)
    }
    
    fun clearSelection() {
        val previousSelected = selectedPositions.toSet()
        selectedPositions.clear()
        previousSelected.forEach { notifyItemChanged(it) }
        onSelectionChanged(0)
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

        // Check if previous message was also at the same minute
        val isSameMinute = previousMessage != null &&
            (currentMessage.timestamp / 60000 == previousMessage.timestamp / 60000)

        // Check if this is an outgoing message (from current user)
        val isOutgoing = currentMessage.user == currentUsername

        // Hide user and time for consecutive messages in the same minute
        val shouldHideUser = isConsecutive || isOutgoing
        val shouldHideTime = isConsecutive && isSameMinute

        // Check if this message is selected
        val isSelected = selectedPositions.contains(position)

        holder.bind(currentMessage, shouldHideUser, isOutgoing, isSelected, shouldHideTime) {
            // Handle message click - use holder.bindingAdapterPosition to get current position
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition == RecyclerView.NO_POSITION) return@bind

            // Only allow selecting own messages for deletion
            if (!isOutgoing && selectedPositions.isEmpty()) return@bind
            if (!isOutgoing && !selectedPositions.contains(currentPosition)) return@bind

            if (selectedPositions.contains(currentPosition)) {
                selectedPositions.remove(currentPosition)
            } else {
                selectedPositions.add(currentPosition)
            }
            notifyItemChanged(currentPosition)
            onSelectionChanged(selectedPositions.size)
        }
    }
    
    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageContainer: View = itemView.findViewById(R.id.messageContainer)
        private val userText: TextView = itemView.findViewById(R.id.userText)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        
        fun bind(message: Message, shouldHideUser: Boolean, isOutgoing: Boolean, isSelected: Boolean, shouldHideTime: Boolean, onClick: () -> Unit) {
            userText.text = message.user
            messageText.text = message.text
            
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            timeText.text = timeFormat.format(Date(message.timestamp))
            timeText.visibility = if (shouldHideTime) View.GONE else View.VISIBLE
            
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
            
            // Handle selection state - hide individual delete button, it's in toolbar now
            deleteButton.visibility = View.GONE
            
            if (isSelected) {
                messageContainer.alpha = 0.5f
                itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.lavender_mist_alpha))
            } else {
                messageContainer.alpha = 1.0f
                itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            
            // Hide user for consecutive messages or outgoing messages (user knows they sent it)
            if (shouldHideUser) {
                userText.visibility = View.GONE
            } else {
                userText.visibility = View.VISIBLE
            }
            
            // Adjust margins for consecutive messages
            val outerParams = itemView.layoutParams as ViewGroup.MarginLayoutParams
            outerParams.topMargin = if (shouldHideUser) 4 else 16
            itemView.layoutParams = outerParams
            
            // Handle click on message container
            messageContainer.setOnClickListener {
                onClick()
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
