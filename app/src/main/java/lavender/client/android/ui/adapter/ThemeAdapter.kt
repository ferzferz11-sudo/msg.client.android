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
import androidx.recyclerview.widget.ListAdapter
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
) : ListAdapter<CustomThemeProto, ThemeAdapter.ThemeViewHolder>(ThemeDiffCallback()) {

    private val selectedThemes = mutableSetOf<CustomThemeProto>()

    fun setThemes(newThemes: List<CustomThemeProto>) {
        submitList(newThemes)
    }

    fun setCurrentThemeId(id: String) {
        if (currentThemeId == id) return
        currentThemeId = id
        notifyItemRangeChanged(0, itemCount)
    }

    fun getSelectedThemes(): List<CustomThemeProto> = selectedThemes.toList()

    fun clearSelection() {
        selectedThemes.clear()
        notifyItemRangeChanged(0, itemCount)
        onSelectionChanged(0)
    }

    fun toggleSelection(theme: CustomThemeProto) {
        if (selectedThemes.contains(theme)) {
            selectedThemes.remove(theme)
        } else {
            selectedThemes.add(theme)
        }
        val index = currentList.indexOf(theme)
        if (index != -1) notifyItemChanged(index)
        onSelectionChanged(selectedThemes.size)
    }

    fun selectSingle(theme: CustomThemeProto) {
        if (selectedThemes.size == 1 && selectedThemes.contains(theme)) {
            selectedThemes.clear()
        } else {
            selectedThemes.clear()
            selectedThemes.add(theme)
        }
        notifyItemRangeChanged(0, itemCount)
        onSelectionChanged(selectedThemes.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ThemeViewHolder(inflater.inflate(R.layout.item_theme, parent, false))
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        val theme = getItem(position)
        holder.bind(theme, currentThemeId, selectedThemes.contains(theme))
    }

    inner class ThemeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val themeName: TextView = itemView.findViewById(R.id.themeName)
        private val themeColorsInfo: TextView = itemView.findViewById(R.id.themeColorsInfo)
        private val themeColorPreview: View = itemView.findViewById(R.id.themeColorPreview)
        private val appliedIndicator: ImageView = itemView.findViewById(R.id.appliedIndicator)
        private val editIndicator: ImageView = itemView.findViewById(R.id.editIndicator)
        private val modeIndicator: ImageView = itemView.findViewById(R.id.modeIndicator)

        @SuppressLint("SetTextI18n")
        fun bind(theme: CustomThemeProto, currentId: String, isSelected: Boolean) {
            themeName.text = theme.name
            val isCurrent = theme.id == currentId
            appliedIndicator.isVisible = isCurrent
            
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
            val baseBgColor = adjustAlpha(onSurface, 0.05f)
            cardView.setCardBackgroundColor(baseBgColor)
            cardView.cardElevation = 0f
            
            if (isSelected) {
                cardView.setCardBackgroundColor(adjustAlpha(primaryColor, 0.2f))
                cardView.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
                cardView.strokeColor = primaryColor
            } else if (isCurrent) {
                cardView.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
                cardView.strokeColor = adjustAlpha(primaryColor, 0.4f)
                cardView.setCardBackgroundColor(adjustAlpha(onSurface, 0.1f))
            } else {
                cardView.strokeWidth = 0
            }

            // Preview colors
            try {
                val bgColorStr = if (theme.id == "dark") "#1E1E2E" else theme.backgroundColor
                val bgColor = bgColorStr.toColorInt()
                val isLight = ThemeUtils.isLight(bgColor)
                
                modeIndicator.setImageResource(if (isLight) R.drawable.ic_light_mode else R.drawable.ic_theme_dark)

                if (theme.id == "dark") {
                    val pColor = "#5F9EA0".toColorInt()
                    themeColorPreview.backgroundTintList = ColorStateList.valueOf(pColor)
                    themeColorsInfo.text = context.getString(R.string.theme_palette)
                    if (isCurrent) {
                        appliedIndicator.imageTintList = ColorStateList.valueOf(if (ThemeUtils.isLight(pColor)) Color.BLACK else Color.WHITE)
                    }
                } else {
                    val pColor = theme.primaryColor.toColorInt()
                    themeColorPreview.backgroundTintList = ColorStateList.valueOf(pColor)
                    themeColorsInfo.text = "${theme.primaryColor} / ${theme.surfaceColor}"
                    if (isCurrent) {
                        appliedIndicator.imageTintList = ColorStateList.valueOf(if (ThemeUtils.isLight(pColor)) Color.BLACK else Color.WHITE)
                    }
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

    class ThemeDiffCallback : DiffUtil.ItemCallback<CustomThemeProto>() {
        override fun areItemsTheSame(oldItem: CustomThemeProto, newItem: CustomThemeProto): Boolean =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CustomThemeProto, newItem: CustomThemeProto): Boolean =
            oldItem == newItem
    }
}
