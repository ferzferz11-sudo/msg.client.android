package lavender.client.android.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import lavender.client.android.R
import lavender.client.android.theme.Theme
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

/**
 * AI Bottom Sheet with grouped actions and section dividers.
 * Shows AI services organized in groups (Orchestrator, OWL).
 */
class AIBottomSheet(context: Context, theme: Theme = ThemeStore.currentTheme()) : StandardBottomSheet(context, R.layout.widget_ai_bottom_sheet, theme) {

    data class AISection(
        val title: String,
        val actions: List<SheetAction>
    )

    fun setSections(sections: List<AISection>): AIBottomSheet {
        contentContainer?.removeAllViews()
        val theme = ThemeStore.currentTheme()
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)

        sections.forEachIndexed { index, section ->
            // Section header
            val headerView = LayoutInflater.from(context).inflate(R.layout.widget_section_header, contentContainer, false) as TextView
            headerView.text = section.title
            contentContainer?.addView(headerView)

            // Section actions
            section.actions.forEach { action ->
                val itemView = LayoutInflater.from(context).inflate(R.layout.widget_action_item, contentContainer, false)
                val icon = itemView.findViewById<ImageView>(R.id.actionIcon)
                val text = itemView.findViewById<TextView>(R.id.actionText)
                val badge = itemView.findViewById<TextView>(R.id.actionBadge)

                icon.setImageResource(action.iconRes)
                icon.imageTintList = ColorStateList.valueOf(primColor)

                text.text = action.text
                text.setTextColor(if (action.isPrimary) primColor else txtColor)

                // Show badge if count > 0
                if (action.badge > 0) {
                    badge.text = if (action.badge > 99) "99+" else action.badge.toString()
                    badge.visibility = View.VISIBLE
                } else {
                    badge.visibility = View.GONE
                }

                itemView.setOnClickListener {
                    action.onClick()
                    dismiss()
                }
                contentContainer?.addView(itemView)
            }

            // Divider between sections (not after last)
            if (index < sections.size - 1) {
                val divider = LayoutInflater.from(context).inflate(R.layout.widget_section_divider, contentContainer, false)
                contentContainer?.addView(divider)
            }
        }
        return this
    }

    override fun setTitle(title: CharSequence?): AIBottomSheet {
        super.setTitle(title)
        return this
    }

    override fun setCancelable(cancelable: Boolean): AIBottomSheet {
        super.setCancelable(cancelable)
        return this
    }

    override fun setOnDismissListener(listener: () -> Unit): AIBottomSheet {
        super.setOnDismissListener(listener)
        return this
    }
}
