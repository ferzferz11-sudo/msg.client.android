package lavender.client.android.ui.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.ai.AiV2Agent

/**
 * AiV2AgentListAdapter — adapter for AI v2 agent list.
 */
class AiV2AgentListAdapter(
    private val onItemClick: (AiV2Agent) -> Unit,
    private val onDeleteClick: (AiV2Agent) -> Unit
) : ListAdapter<AiV2Agent, AiV2AgentListAdapter.ViewHolder>(AgentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ai_v2_agent_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
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
            agentIcon.text = getAgentEmoji(agent)
            agentName.text = agent.name
            agentProvider.text = agent.providerType.value
            agentDescription.text = agent.description
            agentModel.text = "Model: ${agent.model}"

            if (agent.toolsEnabled) {
                toolsIndicator.visibility = View.VISIBLE
                toolsIndicator.text = "🔧 Tools"
            } else {
                toolsIndicator.visibility = View.GONE
            }

            if (agent.ragEnabled) {
                ragIndicator.visibility = View.VISIBLE
                ragIndicator.text = "📚 RAG"
            } else {
                ragIndicator.visibility = View.GONE
            }

            // Show delete button only for non-preset agents
            if (!agent.isPreset) {
                deleteButton.visibility = View.VISIBLE
                deleteButton.setOnClickListener {
                    onDeleteClick(agent)
                }
            } else {
                deleteButton.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onItemClick(agent)
            }
        }

        private fun getAgentEmoji(agent: AiV2Agent): String {
            return when {
                agent.id == "reve" -> "🎨"
                agent.id == "vision" -> "👁"
                agent.providerType.value == "mimo" -> "🤖"
                agent.providerType.value == "openrouter" -> "🧠"
                agent.providerType.value == "webhook" -> "🔗"
                agent.providerType.value == "websocket" -> "📡"
                agent.providerType.value == "subprocess" -> "⚙️"
                agent.providerType.value == "mcp" -> "🔌"
                agent.providerType.value == "local" -> "💻"
                agent.providerType.value == "reve-2.0" -> "🎨"
                else -> "🤖"
            }
        }
    }

    class AgentDiffCallback : DiffUtil.ItemCallback<AiV2Agent>() {
        override fun areItemsTheSame(oldItem: AiV2Agent, newItem: AiV2Agent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AiV2Agent, newItem: AiV2Agent): Boolean {
            return oldItem == newItem
        }
    }
}
