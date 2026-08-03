package lavender.client.android.ui.sticker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import lavender.client.android.R
import lavender.client.android.data.models.StickerPack

class StickerPackAdapter(
    private val onPackClick: (StickerPack) -> Unit
) : ListAdapter<StickerPack, StickerPackAdapter.PackViewHolder>(PackDiffCallback()) {

    private var selectedPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker_pack_tab, parent, false)
        return PackViewHolder(view)
    }

    override fun onViewRecycled(holder: PackViewHolder) {
        super.onViewRecycled(holder)
        holder.unbind()
    }

    override fun onBindViewHolder(holder: PackViewHolder, position: Int) {
        val pack = getItem(position)
        holder.bind(pack, position == selectedPosition)
        holder.itemView.setOnClickListener {
            val old = selectedPosition
            selectedPosition = holder.bindingAdapterPosition
            notifyItemChanged(old)
            notifyItemChanged(selectedPosition)
            onPackClick(pack)
        }
    }

    fun selectFirst() {
        if (itemCount > 0 && selectedPosition != 0) {
            val old = selectedPosition
            selectedPosition = 0
            notifyItemChanged(old)
            notifyItemChanged(0)
        }
    }

    inner class PackViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val coverView: LottieAnimationView = itemView.findViewById(R.id.lottieCover)
        private val selectedIndicator: View = itemView.findViewById(R.id.selectedIndicator)
        private val tvPackName: TextView = itemView.findViewById(R.id.tvPackName)

        fun unbind() {
            coverView.cancelAnimation()
            coverView.clearAnimation()
        }

        fun bind(pack: StickerPack, isSelected: Boolean) {
            selectedIndicator.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
            tvPackName.text = pack.title.ifEmpty { pack.name }

            val coverSticker = pack.stickers.firstOrNull { it.id == pack.coverStickerId }
                ?: pack.stickers.firstOrNull()

            if (coverSticker != null) {
                val url = coverSticker.lottieUrl
                if (url.isNotEmpty()) {
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        coverView.setFailureListener { e ->
                            android.util.Log.e("Lottie", "Failed to load: $url", e)
                            coverView.visibility = View.GONE
                        }
                        coverView.setAnimationFromUrl(url)
                    } else {
                        coverView.setAnimation(url)
                    }
                    coverView.repeatCount = 0
                    coverView.playAnimation()
                    coverView.visibility = View.VISIBLE
                } else {
                    coverView.visibility = View.GONE
                }
            } else {
                coverView.visibility = View.GONE
            }
        }
    }

    class PackDiffCallback : DiffUtil.ItemCallback<StickerPack>() {
        override fun areItemsTheSame(oldItem: StickerPack, newItem: StickerPack): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: StickerPack, newItem: StickerPack): Boolean = oldItem == newItem
    }
}
