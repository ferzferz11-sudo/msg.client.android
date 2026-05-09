package lavender.client.android.ui.adapter

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import lavender.client.android.R
import lavender.client.android.data.proto.CustomThemeProto
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

class ThemeAdapter(
    private val onThemeClick: (CustomThemeProto) -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
    private var currentThemeId: String
) : RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder>() {

    private var themes = listOf<CustomThemeProto>()
    private val selectedThemes = mutableSetOf<CustomThemeProto>()

    fun setThemes(newThemes: List<CustomThemeProto>) {
        val diffResult = DiffUtil.calculateDiff(ThemeDiffCallback(themes, newThemes))
        themes = newThemes
        diffResult.dispatchUpdatesTo(this)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setCurrentThemeId(id: String) {
        if (currentThemeId == id) return
        currentThemeId = id
        notifyDataSetChanged()
    }

    fun getSelectedThemes(): List<CustomThemeProto> = selectedThemes.toList()

    @SuppressLint("NotifyDataSetChanged")
    fun clearSelection() {
        selectedThemes.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun toggleSelection(theme: CustomThemeProto) {
        if (selectedThemes.contains(theme)) {
            selectedThemes.remove(theme)
        } else {
            selectedThemes.add(theme)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedThemes.size)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectSingle(theme: CustomThemeProto) {
        if (selectedThemes.size == 1 && selectedThemes.contains(theme)) {
            selectedThemes.clear()
        } else {
            selectedThemes.clear()
            selectedThemes.add(theme)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedThemes.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ThemeViewHolder(inflater.inflate(R.layout.item_theme, parent, false))
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        val theme = themes[position]
        holder.bind(theme, currentThemeId, selectedThemes.contains(theme))
    }

    override fun getItemCount(): Int = themes.size

    inner class ThemeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val themeName: TextView = itemView.findViewById(R.id.themeName)
        private val themeColorsInfo: TextView = itemView.findViewById(R.id.themeColorsInfo)
        private val themeColorPreview: View = itemView.findViewById(R.id.themeColorPreview)
        private val editIndicator: ImageView = itemView.findViewById(R.id.editIndicator)
        private val modeIndicator: ImageView = itemView.findViewById(R.id.modeIndicator)

        @SuppressLint("SetTextI18n")
        fun bind(theme: CustomThemeProto, currentId: String, isSelected: Boolean) {
            themeName.text = theme.name
            val isCurrent = theme.id == currentId
            
            editIndicator.isVisible = false
            
            val context = itemView.context
            val currentTheme = ThemeStore.currentTheme()
            val textPrimary = currentTheme.textPrimaryColor.toColorInt()
            val onSurface = currentTheme.onSurfaceColor.toColorInt()
            val primaryColor = currentTheme.primaryColor.toColorInt()

            themeName.setTextColor(textPrimary)
            themeColorsInfo.setTextColor(adjustAlpha(onSurface, 0.7f))
            modeIndicator.imageTintList = ColorStateList.valueOf(adjustAlpha(onSurface, 0.7f))

            itemView.alpha = 1.0f
            if (isSelected) {
                cardView.setCardBackgroundColor(adjustAlpha(primaryColor, 0.2f))
                cardView.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
                cardView.strokeColor = primaryColor
                cardView.cardElevation = (4 * context.resources.displayMetrics.density)
            } else if (isCurrent) {
                cardView.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
                cardView.strokeColor = adjustAlpha(primaryColor, 0.4f)
                cardView.cardElevation = 0f
                
                try {
                    val surfaceColor = theme.surfaceColor.toColorInt()
                    cardView.setCardBackgroundColor(adjustAlpha(surfaceColor, 0.15f))
                } catch (_: Exception) {
                    cardView.setCardBackgroundColor(adjustAlpha(primaryColor, 0.1f))
                }
            } else {
                cardView.strokeWidth = 0
                cardView.cardElevation = 0f
                cardView.setCardBackgroundColor(adjustAlpha(onSurface, 0.05f))
            }

            // Preview colors
            try {
                val bgColorStr = if (theme.id == "dark") "#1E1E2E" else theme.backgroundColor
                val bgColor = bgColorStr.toColorInt()
                val isLight = ThemeUtils.isLight(bgColor)
                
                modeIndicator.setImageResource(if (isLight) R.drawable.ic_light_mode else R.drawable.ic_theme_dark)

                if (theme.id == "dark") {
                    themeColorPreview.backgroundTintList = ColorStateList.valueOf("#1E1E2E".toColorInt())
                    themeColorsInfo.text = context.getString(R.string.dark_theme)
                } else {
                    val pColor = theme.primaryColor.toColorInt()
                    themeColorPreview.backgroundTintList = ColorStateList.valueOf(pColor)
                    themeColorsInfo.text = "${theme.primaryColor} / ${theme.surfaceColor}"
                }
            } catch (_: Exception) {
                themeColorPreview.backgroundTintList = ColorStateList.valueOf(Color.GRAY)
                modeIndicator.setImageResource(R.drawable.ic_theme_dark)
            }

            itemView.setOnClickListener {
                selectSingle(theme)
            }
            
            itemView.setOnLongClickListener {
                toggleSelection(theme)
                true
            }
        }
    }


    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private class ThemeDiffCallback(
        private val oldList: List<CustomThemeProto>,
        private val newList: List<CustomThemeProto>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean = 
            oldList[oldItemPosition].id == newList[newItemPosition].id
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
            oldList[oldItemPosition] == newList[newItemPosition]
    }
}
