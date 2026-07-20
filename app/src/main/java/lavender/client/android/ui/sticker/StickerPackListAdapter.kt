package lavender.client.android.ui.sticker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import lavender.client.android.R
import lavender.client.android.data.models.StickerPack
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

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

    override fun onViewRecycled(holder: PackViewHolder) {
        super.onViewRecycled(holder)
        holder.unbind()
    }

    inner class PackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val coverView: LottieAnimationView = itemView.findViewById(R.id.lottieCover)
        private val coverImageView: ImageView? = itemView.findViewById(R.id.coverImageView)
        private val titleText: TextView = itemView.findViewById(R.id.tvTitle)
        private val countText: TextView = itemView.findViewById(R.id.tvStickerCount)
        private val statusText: TextView = itemView.findViewById(R.id.tvStatus)

        fun bind(pack: StickerPack) {
            val ctx = itemView.context
            titleText.text = pack.title
            countText.text = ctx.resources.getQuantityString(R.plurals.sticker_count, pack.stickers.size, pack.stickers.size)

            // Apply theme to card background
            try {
                val theme = ThemeStore.currentTheme()
                val surfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
                val cardView = itemView as? com.google.android.material.card.MaterialCardView
                cardView?.setCardBackgroundColor(surfaceColor)
            } catch (_: Exception) {}

            val statusRes = when (pack.status) {
                "approved" -> R.string.sticker_approved
                "pending" -> R.string.sticker_pending
                "rejected" -> R.string.sticker_rejected
                else -> R.string.sticker_draft
            }
            val statusColor = when (pack.status) {
                "approved" -> Color.parseColor("#4CAF50")
                "pending" -> Color.parseColor("#FFC107")
                "rejected" -> Color.parseColor("#F44336")
                else -> Color.GRAY
            }
            statusText.setText(statusRes)
            statusText.setTextColor(statusColor)

            val coverSticker = pack.stickers.firstOrNull { it.id == pack.coverStickerId }
                ?: pack.stickers.firstOrNull()

            if (coverSticker != null) {
                val url = coverSticker.lottieUrl
                val isLottie = url.endsWith(".json", ignoreCase = true)
                if (isLottie) {
                    coverView.visibility = View.VISIBLE
                    coverImageView?.visibility = View.GONE
                    coverView.setAnimation(url)
                    coverView.repeatCount = 0
                    coverView.playAnimation()
                } else {
                    coverView.visibility = View.GONE
                    coverImageView?.let { iv ->
                        iv.visibility = View.VISIBLE
                        Glide.with(itemView.context).load(url).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_placeholder).centerCrop().into(iv)
                    }
                }
            } else {
                coverView.visibility = View.GONE
                coverImageView?.visibility = View.GONE
            }

            itemView.setOnClickListener { onPackClick(pack) }
            itemView.setOnLongClickListener { onPackLongClick?.invoke(pack); true }
        }

        fun unbind() {
            coverView.cancelAnimation()
            coverView.clearAnimation()
        }
    }

    class PackDiffCallback : DiffUtil.ItemCallback<StickerPack>() {
        override fun areItemsTheSame(oldItem: StickerPack, newItem: StickerPack): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: StickerPack, newItem: StickerPack): Boolean = oldItem == newItem
    }
}
