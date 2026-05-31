package lavender.client.android.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R

data class OwlMessage(
    val text: String,
    val isUser: Boolean,
    val isTyping: Boolean = false
)

class OwlMessageAdapter : RecyclerView.Adapter<OwlMessageAdapter.ViewHolder>() {

    private val messages = mutableListOf<OwlMessage>()

    fun addMessage(msg: OwlMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(text: String) {
        if (messages.isNotEmpty()) {
            val last = messages[messages.size - 1]
            messages[messages.size - 1] = last.copy(text = text, isTyping = false)
            notifyItemChanged(messages.size - 1)
        }
    }

    fun showTyping() {
        hideTyping()
        messages.add(OwlMessage("", isUser = false, isTyping = true))
        notifyItemInserted(messages.size - 1)
    }

    fun hideTyping() {
        val idx = messages.indexOfFirst { it.isTyping }
        if (idx >= 0) {
            messages.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }

    fun isLastMessageUser(): Boolean {
        return messages.isNotEmpty() && messages.last().isUser
    }

    fun updateLastAssistantMessage(text: String) {
        // Find the last non-user, non-typing message
        val idx = messages.indexOfLast { !it.isUser && !it.isTyping && it.text.isNotEmpty() }
        if (idx >= 0) {
            messages[idx] = messages[idx].copy(text = text)
            // Direct text update without full rebind to avoid flicker
            notifyItemChanged(idx, PAYLOAD_TEXT)
        } else {
            // No assistant message yet — add one
            addMessage(OwlMessage(text = text, isUser = false))
        }
    }

    companion object {
        const val PAYLOAD_TEXT = "text_update"
    }

    fun removeLastMessage() {
        if (messages.isNotEmpty()) {
            messages.removeAt(messages.size - 1)
            notifyItemRemoved(messages.size)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_owl_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]
        holder.bind(msg)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_TEXT)) {
            // Partial update — just change text, no full rebind
            holder.updateText(messages[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = messages.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userContainer: LinearLayout = itemView.findViewById(R.id.userMessageContainer)
        private val userText: TextView = itemView.findViewById(R.id.userMessageText)
        private val owlContainer: LinearLayout = itemView.findViewById(R.id.owlMessageContainer)
        private val owlText: TextView = itemView.findViewById(R.id.owlMessageText)
        private val typingContainer: LinearLayout = itemView.findViewById(R.id.typingContainer)

        fun bind(msg: OwlMessage) {
            if (msg.isTyping) {
                userContainer.visibility = View.GONE
                owlContainer.visibility = View.GONE
                typingContainer.visibility = View.VISIBLE
            } else if (msg.isUser) {
                userContainer.visibility = View.VISIBLE
                owlContainer.visibility = View.GONE
                typingContainer.visibility = View.GONE
                userText.text = msg.text
            } else {
                userContainer.visibility = View.GONE
                owlContainer.visibility = View.VISIBLE
                typingContainer.visibility = View.GONE
                owlText.text = msg.text
            }
        }

        // Partial update — just change text without touching visibility
        fun updateText(msg: OwlMessage) {
            if (!msg.isUser && !msg.isTyping) {
                owlText.text = msg.text
            } else if (msg.isUser) {
                userText.text = msg.text
            }
        }
    }
}
