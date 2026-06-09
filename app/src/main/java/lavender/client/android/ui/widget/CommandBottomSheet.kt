package lavender.client.android.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import lavender.client.android.R
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

/**
 * Bottom sheet for bot commands in AI chats (OWL + Hermes).
 * Shows command name + description, tap sends command to input.
 * Styled like AIBottomSheet / ActionBottomSheet.
 */
class CommandBottomSheet(
    context: Context,
    private val commands: List<CommandInfo>,
    private val onCommandSelected: (CommandInfo) -> Unit,
    theme: lavender.client.android.theme.Theme = ThemeStore.currentTheme()
) : StandardBottomSheet(context, R.layout.widget_ai_bottom_sheet, theme) {

    data class CommandInfo(
        val command: String,
        val description: String,
        val iconRes: Int = R.drawable.ic_info
    )

    fun buildAndShow() {
        buildContent()
        show()
    }

    private fun buildContent() {
        contentContainer?.removeAllViews()
        val theme = ThemeStore.currentTheme()
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
        val secondaryColor = ThemeUtils.parseSafeColor(theme.textSecondaryColor, Color.GRAY)

        // Section header
        val headerView = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_header, contentContainer, false) as TextView
        headerView.text = "Команды"
        contentContainer?.addView(headerView)

        // Command items
        commands.forEach { cmd ->
            val itemView = LayoutInflater.from(context)
                .inflate(R.layout.widget_action_item, contentContainer, false)

            val icon = itemView.findViewById<ImageView>(R.id.actionIcon)
            val text = itemView.findViewById<TextView>(R.id.actionText)
            val badge = itemView.findViewById<TextView>(R.id.actionBadge)

            icon.setImageResource(cmd.iconRes)
            icon.imageTintList = ColorStateList.valueOf(primColor)
            text.text = "${cmd.command} — ${cmd.description}"
            text.setTextColor(txtColor)
            text.textSize = 13f
            badge.visibility = View.GONE

            itemView.setOnClickListener {
                onCommandSelected(cmd)
                dismiss()
            }

            contentContainer?.addView(itemView)
        }
    }
}
