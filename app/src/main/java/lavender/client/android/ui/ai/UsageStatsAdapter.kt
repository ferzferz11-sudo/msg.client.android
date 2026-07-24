package lavender.client.android.ui.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.ai.UsageStat

class UsageStatsAdapter : ListAdapter<UsageStat, UsageStatsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_usage_stat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val agentName: TextView = view.findViewById(R.id.agentName)
        private val tokens: TextView = view.findViewById(R.id.tokens)
        private val requests: TextView = view.findViewById(R.id.requests)
        private val period: TextView = view.findViewById(R.id.period)

        fun bind(stat: UsageStat) {
            agentName.text = stat.agentName.ifEmpty { itemView.context.getString(R.string.agent) }
            tokens.text = itemView.context.getString(R.string.marketplace_tokens_count, formatNumber(stat.totalTokens))
            requests.text = itemView.context.getString(R.string.marketplace_requests_count, stat.requestCount)
            period.text = formatDate(stat.periodStart)
        }

        private fun formatNumber(n: Int): String {
            return when {
                n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
                n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
                else -> n.toString()
            }
        }

        private fun formatDate(iso: String): String {
            return try {
                val date = java.time.Instant.parse(iso)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
            } catch (e: Exception) {
                iso
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<UsageStat>() {
        override fun areItemsTheSame(a: UsageStat, b: UsageStat) = a.agentId == b.agentId
        override fun areContentsTheSame(a: UsageStat, b: UsageStat) = a == b
    }
}
