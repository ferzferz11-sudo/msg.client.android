package lavender.client.android.ui.chat.widget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R

/**
 * MentionItem — участник чата для меншена.
 */
data class MentionItem(
    val id: String,
    val name: String,
    val description: String = "",
    val emoji: String = "🤖",
    val mentionTag: String = ""  // e.g. "developer"
)

/**
 * Adapter для списка агентов в mention popup (HermesChat).
 * Использует item_mention_agent.xml с emoji-иконками.
 */
class MentionAdapter(
    private val onClick: (MentionItem) -> Unit
) : ListAdapter<MentionItem, MentionAdapter.MentionHolder>(MentionDiffCallback()) {

    private var filter: String = ""
    private var fullList: List<MentionItem> = emptyList()

    fun setItems(items: List<MentionItem>, filter: String = "") {
        this.fullList = items
        this.filter = filter.lowercase()
        val filtered = if (this.filter.isEmpty()) items
        else items.filter {
            it.name.lowercase().contains(this.filter) ||
            it.mentionTag.lowercase().contains(this.filter)
        }
        submitList(filtered)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MentionHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mention_agent, parent, false)
        return MentionHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: MentionHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MentionHolder(
        itemView: View,
        private val onClick: (MentionItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val emoji: TextView = itemView.findViewById(R.id.mentionAgentEmoji)
        private val name: TextView = itemView.findViewById(R.id.mentionAgentName)
        private val description: TextView = itemView.findViewById(R.id.mentionAgentDescription)
        private val tag: TextView = itemView.findViewById(R.id.mentionAgentTag)

        fun bind(item: MentionItem) {
            emoji.text = item.emoji
            name.text = item.name
            description.text = item.description
            description.isVisible = item.description.isNotEmpty()
            tag.text = if (item.mentionTag.isNotEmpty()) "@${item.mentionTag}" else "@${item.name.lowercase()}"
            itemView.setOnClickListener { onClick(item) }
        }
    }
}

class MentionDiffCallback : DiffUtil.ItemCallback<MentionItem>() {
    override fun areItemsTheSame(oldItem: MentionItem, newItem: MentionItem): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: MentionItem, newItem: MentionItem): Boolean =
        oldItem == newItem
}
