package lavender.client.android.ui.hermes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.models.HermesMessage
import java.text.SimpleDateFormat
import java.util.*

class HermesChatAdapter : RecyclerView.Adapter<HermesChatAdapter.ViewHolder>() {

    private val messages = mutableListOf<HermesMessage>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_AGENT = 1
        private const val VIEW_TYPE_TYPING = 2
    }

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return when {
            msg.isStreaming && msg.role != "user" -> VIEW_TYPE_TYPING
            msg.role == "user" -> VIEW_TYPE_USER
            else -> VIEW_TYPE_AGENT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_hermes_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]
        holder.bind(msg)
    }

    override fun getItemCount(): Int = messages.size

    fun setMessages(newMessages: List<HermesMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // User message views
        private val userContainer: LinearLayout = itemView.findViewById(R.id.userMessageContainer)
        private val userMessageText: TextView = itemView.findViewById(R.id.messageText)
        private val userMessageTime: TextView = itemView.findViewById(R.id.messageTime)

        // Agent message views
        private val agentContainer: LinearLayout = itemView.findViewById(R.id.agentMessageContainer)
        private val agentIcon: TextView = itemView.findViewById(R.id.agentIcon)
        private val agentName: TextView = itemView.findViewById(R.id.agentName)
        private val agentMessageText: TextView = itemView.findViewById(R.id.agentMessageText)
        private val agentMessageTime: TextView = itemView.findViewById(R.id.agentMessageTime)

        // Typing views
        private val typingContainer: LinearLayout = itemView.findViewById(R.id.typingContainer)

        fun bind(msg: HermesMessage) {
            when {
                msg.isStreaming && msg.role != "user" -> {
                    // Show typing indicator
                    userContainer.visibility = View.GONE
                    agentContainer.visibility = View.GONE
                    typingContainer.visibility = View.VISIBLE
                }
                msg.role == "user" -> {
                    // Show user message
                    userContainer.visibility = View.VISIBLE
                    agentContainer.visibility = View.GONE
                    typingContainer.visibility = View.GONE

                    userMessageText.text = msg.content
                    userMessageTime.text = timeFormat.format(Date(msg.timestamp))
                }
                else -> {
                    // Show agent message
                    userContainer.visibility = View.GONE
                    agentContainer.visibility = View.VISIBLE
                    typingContainer.visibility = View.GONE

                    agentMessageText.text = msg.content
                    agentMessageTime.text = timeFormat.format(Date(msg.timestamp))

                    if (msg.agentName.isNotEmpty()) {
                        agentName.text = msg.agentName
                        agentName.visibility = View.VISIBLE
                    } else {
                        agentName.visibility = View.GONE
                    }

                    agentIcon.text = if (msg.agentIcon.isNotEmpty()) msg.agentIcon else "🤖"
                }
            }
        }
    }
}
