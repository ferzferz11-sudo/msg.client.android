package lavender.client.android.ui.sticker

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
import lavender.client.android.data.models.Sticker

class StickerGridAdapter(
    private val onStickerClick: (Sticker) -> Unit
) : ListAdapter<Sticker, StickerGridAdapter.StickerViewHolder>(StickerDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker_grid, parent, false)
        return StickerViewHolder(view)
    }

    override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class StickerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val lottieView: LottieAnimationView = itemView.findViewById(R.id.lottieView)
        private val thumbnailView: ImageView = itemView.findViewById(R.id.thumbnailView)

        fun bind(sticker: Sticker) {
            lottieView.setAnimation(sticker.lottieUrl)
            lottieView.repeatCount = Int.MAX_VALUE
            lottieView.playAnimation()

            lottieView.setOnClickListener {
                onStickerClick(sticker)
            }

            lottieView.setOnLongClickListener {
                true
            }
        }
    }

    class StickerDiffCallback : DiffUtil.ItemCallback<Sticker>() {
        override fun areItemsTheSame(oldItem: Sticker, newItem: Sticker): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Sticker, newItem: Sticker): Boolean = oldItem == newItem
    }
}
