package lavender.client.android.ui.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import lavender.client.android.R
import lavender.client.android.data.proto.CustomThemeProto

class ThemeAdapter(
    private val onThemeClick: (CustomThemeProto) -> Unit,
    private val onEditClick: (CustomThemeProto) -> Unit,
    private val onAddClick: () -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
    private var currentThemeId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_THEME = 0
        private const val TYPE_ADD = 1
    }

    private var themes = listOf<CustomThemeProto>()
    private val selectedPositions = mutableSetOf<Int>()

    fun setThemes(newThemes: List<CustomThemeProto>) {
        val diffResult = DiffUtil.calculateDiff(ThemeDiffCallback(themes, newThemes))
        themes = newThemes
        diffResult.dispatchUpdatesTo(this)
    }

    fun setCurrentThemeId(id: String) {
        if (currentThemeId == id) return
        currentThemeId = id
        notifyDataSetChanged()
    }

    fun getSelectedThemes(): List<CustomThemeProto> {
        return selectedPositions.map { themes[it] }
    }

    fun clearSelection() {
        val previousSelected = selectedPositions.toSet()
        selectedPositions.clear()
        previousSelected.forEach { notifyItemChanged(it) }
        onSelectionChanged(0)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == themes.size) TYPE_ADD else TYPE_THEME
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_ADD) {
            AddViewHolder(inflater.inflate(R.layout.item_theme, parent, false))
        } else {
            ThemeViewHolder(inflater.inflate(R.layout.item_theme, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ThemeViewHolder) {
            val isSelected = selectedPositions.contains(position)
            holder.bind(themes[position], currentThemeId, isSelected) {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos == RecyclerView.NO_POSITION) return@bind
                
                val theme = themes[currentPos]
                if (theme.id == "dark") {
                    onThemeClick(theme)
                    return@bind
                }

                if (selectedPositions.contains(currentPos)) {
                    selectedPositions.remove(currentPos)
                } else {
                    selectedPositions.add(currentPos)
                }
                notifyItemChanged(currentPos)
                onSelectionChanged(selectedPositions.size)
            }
        } else if (holder is AddViewHolder) {
            holder.bind(onAddClick)
        }
    }

    override fun getItemCount(): Int = themes.size + 1

    inner class AddViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val themeName: TextView = itemView.findViewById(R.id.themeName)
        private val themeColorsInfo: TextView = itemView.findViewById(R.id.themeColorsInfo)
        private val themeColorPreview: View = itemView.findViewById(R.id.themeColorPreview)
        private val editIndicator: ImageView = itemView.findViewById(R.id.editIndicator)
        private val radioButton: android.widget.RadioButton? = itemView.findViewById(R.id.themeRadioButton)

        fun bind(onClick: () -> Unit) {
            themeName.text = itemView.context.getString(R.string.add_theme)
            themeColorsInfo.text = itemView.context.getString(R.string.create)
            
            themeColorPreview.backgroundTintList = ColorStateList.valueOf(Color.GRAY)
            editIndicator.setImageResource(android.R.drawable.ic_input_add)
            editIndicator.isVisible = true
            radioButton?.isVisible = false
            
            cardView.setCardBackgroundColor(Color.TRANSPARENT)
            cardView.strokeWidth = (1 * itemView.resources.displayMetrics.density).toInt()
            cardView.strokeColor = Color.GRAY
            
            itemView.alpha = 1.0f
            itemView.setOnClickListener { onClick() }
        }
    }

    inner class ThemeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val themeName: TextView = itemView.findViewById(R.id.themeName)
        private val themeColorsInfo: TextView = itemView.findViewById(R.id.themeColorsInfo)
        private val themeColorPreview: View = itemView.findViewById(R.id.themeColorPreview)
        private val editIndicator: ImageView = itemView.findViewById(R.id.editIndicator)
        private val radioButton: android.widget.RadioButton? = itemView.findViewById(R.id.themeRadioButton)

        fun bind(theme: CustomThemeProto, currentId: String, isSelected: Boolean, onLongClick: () -> Unit) {
            themeName.text = theme.name
            val isCurrent = theme.id == currentId
            
            radioButton?.isVisible = true
            radioButton?.isChecked = isCurrent
            editIndicator.isVisible = false
            
            val context = itemView.context
            
            if (isSelected) {
                cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.lavender_mist_alpha))
                itemView.alpha = 0.7f
                cardView.strokeWidth = 0
            } else {
                itemView.alpha = 1.0f
                if (isCurrent) {
                    cardView.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
                    val primaryColorAttr = android.R.attr.colorPrimary
                    val typedValue = android.util.TypedValue()
                    context.theme.resolveAttribute(primaryColorAttr, typedValue, true)
                    cardView.strokeColor = typedValue.data
                    
                    try {
                        cardView.setCardBackgroundColor(Color.parseColor(theme.surfaceColor).let { 
                            Color.argb(40, Color.red(it), Color.green(it), Color.blue(it))
                        })
                    } catch (_: Exception) {
                        cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.lavender_mist_alpha))
                    }
                } else {
                    cardView.strokeWidth = 0
                    cardView.setCardBackgroundColor(Color.TRANSPARENT)
                }
            }

            // Preview colors
            try {
                if (theme.id == "dark") {
                    themeColorPreview.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1E1E2E"))
                    themeColorsInfo.text = context.getString(R.string.dark_theme)
                } else {
                    val pColor = Color.parseColor(theme.primaryColor)
                    themeColorPreview.backgroundTintList = ColorStateList.valueOf(pColor)
                    themeColorsInfo.text = context.getString(R.string.chat_last_message_format, "${theme.primaryColor} / ", theme.surfaceColor)
                }
            } catch (_: Exception) {
                themeColorPreview.backgroundTintList = ColorStateList.valueOf(Color.GRAY)
            }

            itemView.setOnClickListener {
                if (selectedPositions.isNotEmpty()) {
                    if (theme.id != "dark") {
                        onLongClick()
                    }
                } else {
                    onThemeClick(theme)
                }
            }

            itemView.setOnLongClickListener {
                if (theme.id != "dark") {
                    onLongClick()
                    true
                } else false
            }
        }
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
