package lavender.client.android.ui.company

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.proto.CompanyChatInfoProto

class CompanyChatAdapter(
    private val onChatClick: (CompanyChatInfoProto) -> Unit
) : ListAdapter<CompanyChatInfoProto, CompanyChatAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_company_chat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chat = getItem(position)
        holder.bind(chat)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivChatIcon = view.findViewById<ImageView>(R.id.ivChatIcon)
        private val tvChatName = view.findViewById<TextView>(R.id.tvChatName)
        private val tvAccessLevel = view.findViewById<TextView>(R.id.tvAccessLevel)

        fun bind(chat: CompanyChatInfoProto) {
            val ctx = itemView.context
            tvChatName.text = chat.chatId
            val accessText = when (chat.accessLevel) {
                "member" -> ctx.getString(R.string.access_member)
                "management" -> ctx.getString(R.string.access_management)
                "owner_only" -> ctx.getString(R.string.access_owner_only)
                else -> chat.accessLevel
            }
            tvAccessLevel.text = accessText

            itemView.setOnClickListener { onChatClick(chat) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CompanyChatInfoProto>() {
            override fun areItemsTheSame(oldItem: CompanyChatInfoProto, newItem: CompanyChatInfoProto) = oldItem.chatId == newItem.chatId
            override fun areContentsTheSame(oldItem: CompanyChatInfoProto, newItem: CompanyChatInfoProto) = oldItem == newItem
        }
    }
}
