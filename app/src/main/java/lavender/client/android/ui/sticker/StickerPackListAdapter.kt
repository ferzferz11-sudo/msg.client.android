package lavender.client.android.ui.sticker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import lavender.client.android.R
import lavender.client.android.data.models.StickerPack

class StickerPackListAdapter(
    private val onPackClick: (StickerPack) -> Unit,
    private val onPackLongClick: ((StickerPack) -> Unit)? = null
) : ListAdapter<StickerPack, StickerPackListAdapter.PackViewHolder>(PackDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker_pack, parent, false)
        return PackViewHolder(view)
    }

    override fun onBindViewHolder(holder: PackViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val coverView: LottieAnimationView = itemView.findViewById(R.id.lottieCover)
        private val titleText: TextView = itemView.findViewById(R.id.tvTitle)
        private val countText: TextView = itemView.findViewById(R.id.tvStickerCount)
        private val statusText: TextView = itemView.findViewById(R.id.tvStatus)

        fun bind(pack: StickerPack) {
            titleText.text = pack.title
            countText.text = "${pack.stickers.size} stickers"

            val statusColor = when (pack.status) {
                "approved" -> Color.parseColor("#4CAF50")
                "pending" -> Color.parseColor("#FFC107")
                "rejected" -> Color.parseColor("#F44336")
                else -> Color.GRAY
            }
            statusText.text = pack.status.replaceFirstChar { it.uppercase() }
            statusText.setTextColor(statusColor)

            val coverSticker = pack.stickers.firstOrNull { it.id == pack.coverStickerId }
                ?: pack.stickers.firstOrNull()

            if (coverSticker != null) {
                coverView.setAnimation(coverSticker.lottieUrl)
                coverView.repeatCount = 0
                coverView.playAnimation()
                coverView.visibility = View.VISIBLE
            } else {
                coverView.visibility = View.GONE
            }

            itemView.setOnClickListener { onPackClick(pack) }
            itemView.setOnLongClickListener { onPackLongClick?.invoke(pack); true }
        }
    }

    class PackDiffCallback : DiffUtil.ItemCallback<StickerPack>() {
        override fun areItemsTheSame(oldItem: StickerPack, newItem: StickerPack): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: StickerPack, newItem: StickerPack): Boolean = oldItem == newItem
    }
}
