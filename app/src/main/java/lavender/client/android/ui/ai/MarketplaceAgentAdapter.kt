package lavender.client.android.ui.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.ai.MarketplaceAgent

class MarketplaceAgentAdapter(
    private val onItemClick: (MarketplaceAgent) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Any>()
    private var showSkeleton = false

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_SKELETON = 1
        private const val SKELETON_COUNT = 6
    }

    fun submitList(agents: List<MarketplaceAgent>) {
        showSkeleton = false
        items.clear()
        items.addAll(agents)
        notifyDataSetChanged()
    }

    fun showSkeleton() {
        showSkeleton = true
        items.clear()
        repeat(SKELETON_COUNT) { items.add(Unit) }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return if (showSkeleton) TYPE_SKELETON else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_SKELETON) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_marketplace_skeleton, parent, false)
            SkeletonViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_marketplace_agent_card, parent, false)
            AgentViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AgentViewHolder && !showSkeleton && position < items.size) {
            @Suppress("UNCHECKED_CAST")
            holder.bind(items[position] as MarketplaceAgent)
        }
    }

    inner class AgentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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

    class SkeletonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
