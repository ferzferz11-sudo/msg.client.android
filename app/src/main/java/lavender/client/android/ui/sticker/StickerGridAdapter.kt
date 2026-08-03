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
    private val onStickerClick: (Sticker) -> Unit,
    private val onStickerLongClick: ((Sticker) -> Unit)? = null
) : ListAdapter<Sticker, StickerGridAdapter.StickerViewHolder>(StickerDiffCallback()) {

    private var favoriteIds: Set<String> = emptySet()

    fun setFavoriteIds(ids: Set<String>) {
        favoriteIds = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sticker_grid, parent, false)
        return StickerViewHolder(view)
    }

    override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: StickerViewHolder) {
        super.onViewRecycled(holder)
        holder.unbind()
    }

    inner class StickerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val lottieView: LottieAnimationView = itemView.findViewById(R.id.lottieView)
        private val thumbnailView: ImageView = itemView.findViewById(R.id.thumbnailView)
        private val favoriteIndicator: TextView = itemView.findViewById(R.id.favoriteIndicator)

        fun bind(sticker: Sticker) {
            val url = sticker.lottieUrl
            val isLottie = url.endsWith(".json", ignoreCase = true)

            if (isLottie) {
                lottieView.visibility = View.VISIBLE
                thumbnailView.visibility = View.GONE
                lottieView.repeatCount = 0
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    lottieView.setFailureListener { e ->
                        android.util.Log.e("Lottie", "Failed to load: $url", e)
                        lottieView.visibility = View.GONE
                        thumbnailView.visibility = View.VISIBLE
                        Glide.with(itemView.context).load(sticker.thumbnailUrl).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_placeholder).centerCrop().into(thumbnailView)
                    }
                    lottieView.setAnimationFromUrl(url)
                } else {
                    lottieView.setAnimation(url)
                }
                lottieView.playAnimation()
            } else {
                lottieView.visibility = View.GONE
                thumbnailView.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(url)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(thumbnailView)
            }

            favoriteIndicator.visibility = if (sticker.id in favoriteIds) View.VISIBLE else View.GONE

            lottieView.setOnClickListener { onStickerClick(sticker) }
            lottieView.setOnLongClickListener { onStickerLongClick?.invoke(sticker); true }
            thumbnailView.setOnClickListener { onStickerClick(sticker) }
            thumbnailView.setOnLongClickListener { onStickerLongClick?.invoke(sticker); true }
        }

        fun unbind() {
            lottieView.cancelAnimation()
            lottieView.clearAnimation()
            Glide.with(itemView.context).clear(thumbnailView)
        }
    }

    class StickerDiffCallback : DiffUtil.ItemCallback<Sticker>() {
        override fun areItemsTheSame(oldItem: Sticker, newItem: Sticker): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Sticker, newItem: Sticker): Boolean = oldItem == newItem
    }
}
