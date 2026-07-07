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
import lavender.client.android.data.ai.AgentReview

class ReviewAdapter : ListAdapter<AgentReview, ReviewAdapter.ViewHolder>(ReviewDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userId: TextView = itemView.findViewById(R.id.userId)
        private val rating: RatingBar = itemView.findViewById(R.id.rating)
        private val reviewText: TextView = itemView.findViewById(R.id.reviewText)
        private val createdAt: TextView = itemView.findViewById(R.id.createdAt)

        fun bind(review: AgentReview) {
            userId.text = review.userId.takeIf { !it.contains("-") } ?: "User"
            rating.rating = review.rating.toFloat()
            reviewText.text = review.review
            createdAt.text = review.createdAt
        }
    }

    class ReviewDiffCallback : DiffUtil.ItemCallback<AgentReview>() {
        override fun areItemsTheSame(oldItem: AgentReview, newItem: AgentReview): Boolean {
            return oldItem.userId == newItem.userId && oldItem.createdAt == newItem.createdAt
        }

        override fun areContentsTheSame(oldItem: AgentReview, newItem: AgentReview): Boolean {
            return oldItem == newItem
        }
    }
}
