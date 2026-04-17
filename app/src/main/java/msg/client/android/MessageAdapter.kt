package msg.client.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import msg.client.android.data.models.Message
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(private val messages: MutableList<Message>) : 
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {
    
    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userText: TextView = itemView.findViewById(android.R.id.text1)
        val messageText: TextView = itemView.findViewById(android.R.id.text2)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return MessageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(Date(message.timestamp))
        
        holder.userText.text = "${message.user} ($timestamp)"
        holder.messageText.text = message.text
    }
    
    override fun getItemCount() = messages.size
    
    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
}
