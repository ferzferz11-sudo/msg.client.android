package lavender.client.android.ui.hermes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.models.AgentInfo
import lavender.client.android.data.models.AgentPreset

sealed class AgentListItem {
    data class PresetItem(val preset: AgentPreset) : AgentListItem()
    data class AgentItem(val agent: AgentInfo) : AgentListItem()
}

class AgentListAdapter(
    private val onAgentClick: (Any) -> Unit,
    private val onDeleteClick: (Any) -> Unit,
    private var showDeleteButton: Boolean = false
) : RecyclerView.Adapter<AgentListAdapter.ViewHolder>() {

    private var items = listOf<AgentListItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_agent_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun setItems(newItems: List<AgentListItem>, showDelete: Boolean) {
        items = newItems
        showDeleteButton = showDelete
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val agentIcon: TextView = itemView.findViewById(R.id.agentIcon)
        private val agentName: TextView = itemView.findViewById(R.id.agentName)
        private val agentRole: TextView = itemView.findViewById(R.id.agentRole)
        private val agentDescription: TextView = itemView.findViewById(R.id.agentDescription)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(item: AgentListItem) {
            when (item) {
                is AgentListItem.PresetItem -> {
                    agentIcon.text = item.preset.icon.ifElse { "🤖" }
                    agentName.text = item.preset.name
                    agentRole.text = item.preset.role
                    agentDescription.text = item.preset.description
                    deleteButton.visibility = View.GONE

                    itemView.setOnClickListener { onAgentClick(item.preset) }
                }
                is AgentListItem.AgentItem -> {
                    agentIcon.text = item.agent.icon.ifElse { "🤖" }
                    agentName.text = item.agent.name
                    agentRole.text = item.agent.role
                    agentDescription.text = item.agent.description
                    deleteButton.visibility = if (showDeleteButton) View.VISIBLE else View.GONE

                    itemView.setOnClickListener { onAgentClick(item.agent) }
                    deleteButton.setOnClickListener { onDeleteClick(item.agent) }
                }
            }
        }
    }

    private fun String.ifElse(default: () -> String) = if (isNotEmpty()) this else default()
}
