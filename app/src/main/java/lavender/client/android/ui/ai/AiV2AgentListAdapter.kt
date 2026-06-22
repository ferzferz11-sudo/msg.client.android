package lavender.client.android.ui.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.ai.AiV2Agent

class AiV2AgentListAdapter(
    private val onItemClick: (AiV2Agent) -> Unit,
    private val onDeleteClick: ((AiV2Agent) -> Unit)? = null
) : RecyclerView.Adapter<AiV2AgentListAdapter.ViewHolder>() {

    private val items = mutableListOf<AiV2Agent>()

    fun submitList(agents: List<AiV2Agent>) {
        items.clear()
        items.addAll(agents)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_v2_agent_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val agentIcon: TextView = itemView.findViewById(R.id.agentIcon)
        private val agentName: TextView = itemView.findViewById(R.id.agentName)
        private val agentProvider: TextView = itemView.findViewById(R.id.agentProvider)
        private val agentDescription: TextView = itemView.findViewById(R.id.agentDescription)
        private val agentModel: TextView = itemView.findViewById(R.id.agentModel)
        private val toolsIndicator: TextView = itemView.findViewById(R.id.toolsIndicator)
        private val ragIndicator: TextView = itemView.findViewById(R.id.ragIndicator)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)

        fun bind(agent: AiV2Agent) {
            agentIcon.text = getAgentEmoji(agent.id)
            agentName.text = agent.name
            agentProvider.text = agent.providerType.value
            agentDescription.text = agent.description
            agentModel.text = agent.model
            toolsIndicator.visibility = if (agent.toolsEnabled) View.VISIBLE else View.GONE
            ragIndicator.visibility = if (agent.ragEnabled) View.VISIBLE else View.GONE

            if (agent.isPreset) {
                deleteButton.visibility = View.GONE
            } else {
                deleteButton.visibility = if (onDeleteClick != null) View.VISIBLE else View.GONE
                deleteButton.setOnClickListener {
                    onDeleteClick?.invoke(agent)
                }
            }

            itemView.setOnClickListener { onItemClick(agent) }
        }

        private fun getAgentEmoji(agentId: String): String {
            return when (agentId) {
                "reve" -> "🎨"
                "vision" -> "👁"
                "mimo" -> "🤖"
                "assistant" -> "🧠"
                "developer" -> "💻"
                "devops" -> "⚙️"
                "architect" -> "🏗️"
                "writer" -> "✍️"
                "analyst" -> "📊"
                "translator" -> "🌐"
                else -> "🤖"
            }
        }
    }
}
