package lavender.client.android.ui.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.ai.MarketplaceAgent

class MarketplaceAgentAdapter(
    private val onItemClick: (MarketplaceAgent) -> Unit
) : ListAdapter<MarketplaceAgent, MarketplaceAgentAdapter.ViewHolder>(AgentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_marketplace_agent_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val agentIcon: TextView = itemView.findViewById(R.id.agentIcon)
        private val agentName: TextView = itemView.findViewById(R.id.agentName)
        private val agentDescription: TextView = itemView.findViewById(R.id.agentDescription)
        private val agentModel: TextView = itemView.findViewById(R.id.agentModel)
        private val agentRating: RatingBar = itemView.findViewById(R.id.agentRating)
        private val ratingText: TextView = itemView.findViewById(R.id.ratingText)
        private val installCount: TextView = itemView.findViewById(R.id.installCount)

        fun bind(agent: MarketplaceAgent) {
            agentIcon.text = getAgentEmoji(agent.providerType.value)
            agentName.text = agent.name
            agentDescription.text = agent.description
            agentModel.text = agent.model
            agentRating.rating = agent.avgRating
            ratingText.text = String.format("%.1f", agent.avgRating)
            installCount.text = "${agent.installCount} installs"

            itemView.setOnClickListener { onItemClick(agent) }
        }

        private fun getAgentEmoji(providerType: String): String {
            return when (providerType) {
                "mimo" -> "🤖"
                "openrouter" -> "🧠"
                "webhook" -> "🔗"
                "websocket" -> "📡"
                "subprocess" -> "⚙️"
                "mcp" -> "🔌"
                "local" -> "💻"
                else -> "🤖"
            }
        }
    }

    class AgentDiffCallback : DiffUtil.ItemCallback<MarketplaceAgent>() {
        override fun areItemsTheSame(oldItem: MarketplaceAgent, newItem: MarketplaceAgent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MarketplaceAgent, newItem: MarketplaceAgent): Boolean {
            return oldItem == newItem
        }
    }
}
