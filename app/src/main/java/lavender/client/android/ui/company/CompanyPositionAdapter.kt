package lavender.client.android.ui.company

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.data.proto.CompanyPositionProto

class CompanyPositionAdapter(
    private val onPositionClick: (CompanyPositionProto) -> Unit,
    private val onMoreClick: (CompanyPositionProto, View) -> Unit
) : ListAdapter<CompanyPositionProto, CompanyPositionAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_company_position, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pos = getItem(position)
        holder.bind(pos)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivIcon = view.findViewById<ImageView>(R.id.ivIcon)
        private val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        private val tvLevel = view.findViewById<TextView>(R.id.tvLevel)
        private val btnMore = view.findViewById<ImageButton>(R.id.btnMore)

        fun bind(position: CompanyPositionProto) {
            val ctx = itemView.context
            tvTitle.text = position.title
            val levelText = when (position.level) {
                0 -> ctx.getString(R.string.employee)
                1 -> ctx.getString(R.string.manager)
                2 -> ctx.getString(R.string.top_manager)
                3 -> ctx.getString(R.string.owner)
                else -> "${ctx.getString(R.string.position_level)} ${position.level}"
            }
            tvLevel.text = levelText

            val iconRes = when (position.level) {
                0 -> R.drawable.ic_account_circle
                1 -> R.drawable.ic_account_circle
                2 -> R.drawable.ic_account_circle
                3 -> R.drawable.ic_star_filled
                else -> R.drawable.ic_account_circle
            }
            ivIcon.setImageResource(iconRes)

            // Hide more button for default positions (level 0-3 with standard titles)
            val isDefaultPosition = position.level in 0..3 && position.title in listOf(
                ctx.getString(R.string.employee), ctx.getString(R.string.manager),
                ctx.getString(R.string.top_manager), ctx.getString(R.string.owner)
            )
            btnMore.visibility = if (isDefaultPosition) View.GONE else View.VISIBLE

            itemView.setOnClickListener { onPositionClick(position) }
            btnMore.setOnClickListener { onMoreClick(position, it) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CompanyPositionProto>() {
            override fun areItemsTheSame(oldItem: CompanyPositionProto, newItem: CompanyPositionProto) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: CompanyPositionProto, newItem: CompanyPositionProto) = oldItem == newItem
        }
    }
}
