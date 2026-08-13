package lavender.client.android.ui.company

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import lavender.client.android.R
import lavender.client.android.data.proto.InviteCodeInfoProto

class InviteCodeAdapter(
    private val onShare: (InviteCodeInfoProto) -> Unit,
    private val onRevoke: (InviteCodeInfoProto) -> Unit
) : ListAdapter<InviteCodeInfoProto, InviteCodeAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_invite_code, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCode: TextView = itemView.findViewById(R.id.tvCode)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvUsage: TextView = itemView.findViewById(R.id.tvUsage)
        private val tvExpiry: TextView = itemView.findViewById(R.id.tvExpiry)
        private val btnShare: MaterialButton = itemView.findViewById(R.id.btnShare)
        private val btnRevoke: MaterialButton = itemView.findViewById(R.id.btnRevoke)

        fun bind(item: InviteCodeInfoProto) {
            tvCode.text = item.code

            val context = itemView.context
            if (item.isActive) {
                tvStatus.text = context.getString(R.string.company_code_active)
                tvStatus.setTextColor(0xFF4CAF50.toInt())
                btnRevoke.isEnabled = true
                btnRevoke.alpha = 1.0f
            } else {
                tvStatus.text = context.getString(R.string.company_code_expired)
                tvStatus.setTextColor(0xFFF44336.toInt())
                btnRevoke.isEnabled = false
                btnRevoke.alpha = 0.5f
            }

            tvUsage.text = context.getString(R.string.company_code_uses, item.useCount, item.maxUses)

            if (item.expiresAt.isNotEmpty()) {
                tvExpiry.text = "Expires: ${item.expiresAt}"
                tvExpiry.visibility = View.VISIBLE
            } else {
                tvExpiry.visibility = View.GONE
            }

            btnShare.setOnClickListener { onShare(item) }
            btnRevoke.setOnClickListener { onRevoke(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<InviteCodeInfoProto>() {
        override fun areItemsTheSame(oldItem: InviteCodeInfoProto, newItem: InviteCodeInfoProto): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: InviteCodeInfoProto, newItem: InviteCodeInfoProto): Boolean {
            return oldItem == newItem
        }
    }
}
