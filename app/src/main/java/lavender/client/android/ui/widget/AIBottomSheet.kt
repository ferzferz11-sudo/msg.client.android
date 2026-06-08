package lavender.client.android.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import lavender.client.android.R
import lavender.client.android.data.models.AIChatInfo
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

/**
 * AI Bottom Sheet — redesigned for v1.1.2.0.
 *
 * Layout (top to bottom):
 * 1. Unified AI chats list (all types mixed, sorted by creation time)
 *    - Each item: icon, name, settings gear
 *    - Long press → popup menu with "Delete" and "Settings"
 * 2. Divider
 * 3. "Лава ИИ" (Hermes Orchestrator) section — create new chat button
 * 4. "OWL агент" section — create new chat button
 */
class AIBottomSheet(
    context: Context,
    private val existingChats: List<AIChatInfo> = emptyList(),
    private val onChatClick: (AIChatInfo) -> Unit = {},
    private val onDeleteChat: (AIChatInfo) -> Unit = {},
    private val onSettingsClick: (AIChatInfo) -> Unit = {},
    private val onCreateHermesChat: () -> Unit = {},
    private val onCreateOwlChat: () -> Unit = {},
    theme: lavender.client.android.theme.Theme = ThemeStore.currentTheme()
) : StandardBottomSheet(context, R.layout.widget_ai_bottom_sheet, theme) {

    fun buildAndShow() {
        buildContent()
        show()
    }

    private fun buildContent() {
        contentContainer?.removeAllViews()
        val theme = ThemeStore.currentTheme()
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)

        // === Section 1: Existing AI chats (unified list) ===
        if (existingChats.isNotEmpty()) {
            // Section header
            val headerView = LayoutInflater.from(context)
                .inflate(R.layout.widget_section_header, contentContainer, false) as TextView
            headerView.text = "Мои AI чаты"
            contentContainer?.addView(headerView)

            // Chat items
            existingChats.forEach { chat ->
                val itemView = LayoutInflater.from(context)
                    .inflate(R.layout.widget_ai_chat_item, contentContainer, false)

                val icon = itemView.findViewById<ImageView>(R.id.chatIcon)
                val text = itemView.findViewById<TextView>(R.id.chatName)
                val settingsBtn = itemView.findViewById<ImageView>(R.id.chatSettings)
                val typeLabel = itemView.findViewById<TextView>(R.id.chatTypeLabel)

                // Icon based on type
                if (chat.type == "hermes") {
                    icon.setImageResource(R.drawable.ic_hermes)
                    typeLabel.text = "Лава ИИ"
                } else {
                    icon.setImageResource(R.drawable.ic_owl)
                    typeLabel.text = "OWL"
                }
                icon.imageTintList = ColorStateList.valueOf(primColor)

                text.text = chat.name
                text.setTextColor(txtColor)

                // Settings gear
                settingsBtn.imageTintList = ColorStateList.valueOf(txtColor)
                settingsBtn.setOnClickListener {
                    onSettingsClick(chat)
                    dismiss()
                }

                // Tap → open chat
                itemView.setOnClickListener {
                    onChatClick(chat)
                    dismiss()
                }

                // Long press → popup menu with delete + settings
                itemView.setOnLongClickListener { anchor ->
                    showChatPopupMenu(anchor, chat, primColor, txtColor)
                    true
                }

                contentContainer?.addView(itemView)
            }

            // Divider between chats and create sections
            val divider = LayoutInflater.from(context)
                .inflate(R.layout.widget_section_divider, contentContainer, false)
            contentContainer?.addView(divider)
        }

        // === Section 2: Hermes (Лава ИИ) ===
        val hermesHeader = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_header, contentContainer, false) as TextView
        hermesHeader.text = "Лава ИИ (Оркестратор)"
        contentContainer?.addView(hermesHeader)

        val hermesCreate = LayoutInflater.from(context)
            .inflate(R.layout.widget_action_item, contentContainer, false)
        val hermesIcon = hermesCreate.findViewById<ImageView>(R.id.actionIcon)
        val hermesText = hermesCreate.findViewById<TextView>(R.id.actionText)
        hermesIcon.setImageResource(R.drawable.ic_add)
        hermesIcon.imageTintList = ColorStateList.valueOf(primColor)
        hermesText.text = "Создать новый чат"
        hermesText.setTextColor(primColor)
        hermesCreate.setOnClickListener {
            onCreateHermesChat()
            dismiss()
        }
        contentContainer?.addView(hermesCreate)

        // === Section 3: OWL ===
        val owlDivider = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_divider, contentContainer, false)
        contentContainer?.addView(owlDivider)

        val owlHeader = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_header, contentContainer, false) as TextView
        owlHeader.text = "OWL агент"
        contentContainer?.addView(owlHeader)

        val owlCreate = LayoutInflater.from(context)
            .inflate(R.layout.widget_action_item, contentContainer, false)
        val owlIcon = owlCreate.findViewById<ImageView>(R.id.actionIcon)
        val owlText = owlCreate.findViewById<TextView>(R.id.actionText)
        owlIcon.setImageResource(R.drawable.ic_add)
        owlIcon.imageTintList = ColorStateList.valueOf(primColor)
        owlText.text = "Создать новый чат"
        owlText.setTextColor(primColor)
        owlCreate.setOnClickListener {
            onCreateOwlChat()
            dismiss()
        }
        contentContainer?.addView(owlCreate)
    }

    private fun showChatPopupMenu(anchor: View, chat: AIChatInfo, primColor: Int, txtColor: Int) {
        val popup = PopupMenu(context, anchor, Gravity.END)
        popup.menu.add(0, 1, 0, "Настройки")
        popup.menu.add(0, 2, 1, "Удалить")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    onSettingsClick(chat)
                    dismiss()
                    true
                }
                2 -> {
                    onDeleteChat(chat)
                    dismiss()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}
