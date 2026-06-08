package lavender.client.android.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
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
 *
 * Supports two modes:
 * - Normal mode: tap opens chat, long-press enters selection mode
 * - Selection mode: checkboxes appear, toolbar with delete/rename actions
 */
class AIBottomSheet(context: Context, theme: Theme = ThemeStore.currentTheme()) : StandardBottomSheet(context, R.layout.widget_ai_bottom_sheet, theme) {

    data class AISection(
        val title: String,
        val actions: List<SheetAction>
    )

    private var selectionMode = false
    private val selectedIds = mutableSetOf<Int>()
    private var onSelectionChanged: ((Set<Int>) -> Unit)? = null
    private var selectionToolbar: LinearLayout? = null
    private var btnRename: ImageButton? = null
    private var btnDelete: ImageButton? = null

    // Store item views for checkbox toggling
    private val chatItemViews = mutableMapOf<Int, View>()

    init {
        selectionToolbar = root?.findViewById(R.id.selectionToolbar)
        btnRename = root?.findViewById(R.id.btnRename)
        btnDelete = root?.findViewById(R.id.btnDelete)
    }

    fun setSections(sections: List<AISection>): AIBottomSheet {
        contentContainer?.removeAllViews()
        chatItemViews.clear()
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

                // Tap: open chat (or toggle selection in selection mode)
                itemView.setOnClickListener {
                    if (selectionMode) {
                        toggleSelection(action.id)
                        updateCheckboxes()
                    } else {
                        action.onClick()
                        dismiss()
                    }
                }

                // Long tap: enter selection mode (only for chat items with hashCode IDs)
                // Chat items use hashCode IDs which are typically large numbers
                // Action items use R.id.* which are positive and < 0x7f000000
                if (action.id < 0x7f000000 && action.id != 0) {
                    itemView.setOnLongClickListener {
                        if (!selectionMode) {
                            enterSelectionMode()
                            toggleSelection(action.id)
                            updateCheckboxes()
                        }
                        true
                    }
                    // Store reference for checkbox toggling
                    chatItemViews[action.id] = itemView
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

    private fun enterSelectionMode() {
        selectionMode = true
        selectionToolbar?.visibility = View.VISIBLE
        titleView?.text = "Выберите чаты"
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedIds.clear()
        selectionToolbar?.visibility = View.GONE
        titleView?.text = "AI Сервисы"
        updateCheckboxes()
    }

    private fun toggleSelection(id: Int) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        if (selectedIds.isEmpty()) {
            exitSelectionMode()
        }
        onSelectionChanged?.invoke(selectedIds)
    }

    private fun updateCheckboxes() {
        chatItemViews.forEach { (id, view) ->
            val icon = view.findViewById<ImageView>(R.id.actionIcon)
            val theme = ThemeStore.currentTheme()
            val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
            if (selectedIds.contains(id)) {
                // Show checked state — change icon background or alpha
                icon.alpha = 0.5f
            } else {
                icon.alpha = 1.0f
            }
        }
    }

    fun getSelectedChatIds(): List<String> {
        // Map hashCode back to original chat IDs
        // We need to find the original IDs from the chat name lookup
        return selectedIds.map { it.toString() }
    }

    fun setSelectedIds(ids: Set<Int>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        updateCheckboxes()
    }

    fun setOnSelectionChangedListener(listener: (Set<Int>) -> Unit): AIBottomSheet {
        onSelectionChanged = listener
        return this
    }

    fun setOnDeleteListener(listener: (Set<Int>) -> Unit): AIBottomSheet {
        btnDelete?.setOnClickListener {
            if (selectedIds.isNotEmpty()) {
                listener(selectedIds)
            }
        }
        return this
    }

    fun setOnRenameListener(listener: (Int) -> Unit): AIBottomSheet {
        btnRename?.setOnClickListener {
            if (selectedIds.size == 1) {
                listener(selectedIds.first())
            }
        }
        return this
    }

    fun getSelectedIds(): Set<Int> = selectedIds.toSet()

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
