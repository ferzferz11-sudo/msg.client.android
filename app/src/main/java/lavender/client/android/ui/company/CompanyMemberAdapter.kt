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
import com.bumptech.glide.Glide
import lavender.client.android.R
import lavender.client.android.data.proto.CompanyMemberProto

class CompanyMemberAdapter(
    private val onMemberClick: (CompanyMemberProto) -> Unit,
    private val onMoreClick: (CompanyMemberProto, View) -> Unit
) : ListAdapter<CompanyMemberProto, CompanyMemberAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_company_member, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = getItem(position)
        holder.bind(member)
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivAvatar = view.findViewById<de.hdodenhof.circleimageview.CircleImageView>(R.id.ivAvatar)
        private val tvName = view.findViewById<TextView>(R.id.tvName)
        private val tvPosition = view.findViewById<TextView>(R.id.tvPosition)
        private val btnMore = view.findViewById<ImageButton>(R.id.btnMore)

        fun bind(member: CompanyMemberProto) {
            tvName.text = member.username
            tvPosition.text = formatPosition(member.position?.title, member.position?.level ?: 0)

            if (member.avatarUrl.isNotEmpty()) {
                Glide.with(itemView.context).load(member.avatarUrl).placeholder(R.drawable.ic_default_avatar).into(ivAvatar)
            }

            itemView.setOnClickListener { onMemberClick(member) }
            btnMore.setOnClickListener { onMoreClick(member, it) }
        }

        private fun formatPosition(title: String?, level: Int): String {
            val context = itemView.context
            val englishNames = mapOf(0 to "Employee", 1 to "Manager", 2 to "Top Manager", 3 to "Owner")
            val levelName = when (level) {
                0 -> context.getString(R.string.employee)
                1 -> context.getString(R.string.manager)
                2 -> context.getString(R.string.top_manager)
                3 -> context.getString(R.string.owner)
                else -> title ?: ""
            }
            if (title.isNullOrEmpty()) return levelName
            val englishName = englishNames[level]
            return if (englishName != null && title.equals(englishName, ignoreCase = true)) {
                levelName
            } else if (title != levelName) {
                "$title ($levelName)"
            } else {
                levelName
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CompanyMemberProto>() {
            override fun areItemsTheSame(oldItem: CompanyMemberProto, newItem: CompanyMemberProto) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: CompanyMemberProto, newItem: CompanyMemberProto) = oldItem == newItem
        }
    }
}
