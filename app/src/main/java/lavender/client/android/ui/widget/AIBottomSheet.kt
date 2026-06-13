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
 * AI Bottom Sheet — redesigned for v1.1.2.8.
 *
 * Layout (top to bottom):
 * 1. Notifications
 * 2. Divider
 * 3. "Лава ИИ (Оркестратор)" section
 *    - Create new chat button
 *    - Existing Hermes chats list (if any)
 * 4. Divider
 * 5. "OWL агент" section
 *    - Create new chat button
 *    - Existing OWL chats list (if any)
 */
class AIBottomSheet(
    context: Context,
    private val existingChats: MutableList<AIChatInfo> = mutableListOf(),
    private val onChatClick: (AIChatInfo) -> Unit = {},
    private val onDeleteChat: (AIChatInfo) -> Unit = {},
    private val onSettingsClick: (AIChatInfo) -> Unit = {},
    private val onCreateHermesChat: () -> Unit = {},
    private val onCreateOwlChat: () -> Unit = {},
    private val onOpenNotifications: () -> Unit = {},
    private val onOpenRemoteAgents: () -> Unit = {},
    private var unreadNotifCount: Int = 0,
    theme: lavender.client.android.theme.Theme = ThemeStore.currentTheme()
) : StandardBottomSheet(context, R.layout.widget_ai_bottom_sheet, theme) {

    fun buildAndShow() {
        buildContent()
        show()
    }

    fun rebuildContent() {
        if (isShowing()) {
            buildContent()
        }
    }

    fun updateChats(chats: List<AIChatInfo>) {
        existingChats.clear()
        existingChats.addAll(chats)
    }

    fun removeChat(chatId: String) {
        existingChats.removeAll { it.id == chatId }
    }

    private fun buildContent() {
        contentContainer?.removeAllViews()
        val theme = ThemeStore.currentTheme()
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)

        // === Section 0: Notifications with badge ===
        val notifItem = LayoutInflater.from(context)
            .inflate(R.layout.widget_action_item, contentContainer, false)
        val notifIcon = notifItem.findViewById<ImageView>(R.id.actionIcon)
        val notifText = notifItem.findViewById<TextView>(R.id.actionText)
        val notifBadge = notifItem.findViewById<TextView>(R.id.actionBadge)
        notifIcon.setImageResource(R.drawable.ic_notifications)
        notifIcon.imageTintList = ColorStateList.valueOf(primColor)
        notifText.text = "Уведомления"
        notifText.setTextColor(txtColor)
        if (unreadNotifCount > 0) {
            notifBadge.text = if (unreadNotifCount > 99) "99+" else unreadNotifCount.toString()
            notifBadge.visibility = View.VISIBLE
        } else {
            notifBadge.visibility = View.GONE
        }
        notifItem.setOnClickListener {
            onOpenNotifications()
            dismiss()
        }
        contentContainer?.addView(notifItem)

        // Divider after notifications
        val notifDivider = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_divider, contentContainer, false)
        contentContainer?.addView(notifDivider)

        // Separate chats by type
        val hermesChats = existingChats.filter { it.type == "hermes" }
        val owlChats = existingChats.filter { it.type == "owl" }

        // === Section 1: Hermes (Лава ИИ Оркестратор) ===
        val hermesHeader = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_header, contentContainer, false) as TextView
        hermesHeader.text = "🎼 Лава ИИ (Оркестратор)"
        contentContainer?.addView(hermesHeader)

        // Hermes chat list
        if (hermesChats.isNotEmpty()) {
            hermesChats.forEach { chat ->
                val itemView = buildChatItemView(chat, primColor, txtColor)
                contentContainer?.addView(itemView)
            }
        }

        // Create Hermes chat button
        val hermesCreate = LayoutInflater.from(context)
            .inflate(R.layout.widget_action_item, contentContainer, false)
        val hermesIcon = hermesCreate.findViewById<ImageView>(R.id.actionIcon)
        val hermesText = hermesCreate.findViewById<TextView>(R.id.actionText)
        val hermesBadge = hermesCreate.findViewById<TextView>(R.id.actionBadge)
        hermesIcon.setImageResource(R.drawable.ic_hermes)
        hermesIcon.imageTintList = ColorStateList.valueOf(primColor)
        hermesText.text = "Создать новый чат"
        hermesText.setTextColor(primColor)
        hermesBadge.visibility = View.GONE
        hermesCreate.setOnClickListener {
            onCreateHermesChat()
            dismiss()
        }
        hermesCreate.setBackgroundResource(R.drawable.bg_action_item_hover)
        contentContainer?.addView(hermesCreate)

        // === Section 2: OWL агент ===
        val owlDivider = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_divider, contentContainer, false)
        contentContainer?.addView(owlDivider)

        val owlHeader = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_header, contentContainer, false) as TextView
        owlHeader.text = "🦉 OWL агент"
        contentContainer?.addView(owlHeader)

        // OWL chat list
        if (owlChats.isNotEmpty()) {
            owlChats.forEach { chat ->
                val itemView = buildChatItemView(chat, primColor, txtColor)
                contentContainer?.addView(itemView)
            }
        }

        // Create OWL chat button
        val owlCreate = LayoutInflater.from(context)
            .inflate(R.layout.widget_action_item, contentContainer, false)
        val owlIcon = owlCreate.findViewById<ImageView>(R.id.actionIcon)
        val owlText = owlCreate.findViewById<TextView>(R.id.actionText)
        val owlBadge = owlCreate.findViewById<TextView>(R.id.actionBadge)
        owlIcon.setImageResource(R.drawable.ic_owl)
        owlIcon.imageTintList = ColorStateList.valueOf(primColor)
        owlText.text = "Создать новый чат"
        owlText.setTextColor(primColor)
        owlBadge.visibility = View.GONE
        owlCreate.setOnClickListener {
            onCreateOwlChat()
            dismiss()
        }
        owlCreate.setBackgroundResource(R.drawable.bg_action_item_hover)
        contentContainer?.addView(owlCreate)

        // === Section 3: Remote Agents ===
        val remoteDivider = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_divider, contentContainer, false)
        contentContainer?.addView(remoteDivider)

        val remoteHeader = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_header, contentContainer, false) as TextView
        remoteHeader.text = "🖥 Агенты"
        contentContainer?.addView(remoteHeader)

        // Open Remote Agents button
        val remoteOpen = LayoutInflater.from(context)
            .inflate(R.layout.widget_action_item, contentContainer, false)
        val remoteIcon = remoteOpen.findViewById<ImageView>(R.id.actionIcon)
        val remoteText = remoteOpen.findViewById<TextView>(R.id.actionText)
        val remoteBadge = remoteOpen.findViewById<TextView>(R.id.actionBadge)
        remoteIcon.setImageResource(R.drawable.ic_agents)
        remoteIcon.imageTintList = ColorStateList.valueOf(primColor)
        remoteText.text = "Управление агентами"
        remoteText.setTextColor(primColor)
        remoteBadge.visibility = View.GONE
        remoteOpen.setOnClickListener {
            onOpenRemoteAgents()
            dismiss()
        }
        remoteOpen.setBackgroundResource(R.drawable.bg_action_item_hover)
        contentContainer?.addView(remoteOpen)
    }

    private fun buildChatItemView(chat: AIChatInfo, primColor: Int, txtColor: Int): View {
        val itemView = LayoutInflater.from(context)
            .inflate(R.layout.widget_ai_chat_item, contentContainer, false)

        val icon = itemView.findViewById<ImageView>(R.id.chatIcon)
        val text = itemView.findViewById<TextView>(R.id.chatName)
        val settingsBtn = itemView.findViewById<ImageView>(R.id.chatSettings)

        if (chat.type == "hermes") {
            icon.setImageResource(R.drawable.ic_hermes)
        } else {
            icon.setImageResource(R.drawable.ic_owl)
        }
        icon.imageTintList = ColorStateList.valueOf(primColor)

        text.text = chat.name
        text.setTextColor(txtColor)

        settingsBtn.imageTintList = ColorStateList.valueOf(txtColor)
        settingsBtn.setOnClickListener {
            onSettingsClick(chat)
            dismiss()
        }

        itemView.setOnClickListener {
            onChatClick(chat)
            dismiss()
        }

        itemView.setOnLongClickListener { anchor ->
            showChatPopupMenu(anchor, chat, primColor, txtColor)
            true
        }

        return itemView
    }

    private fun showChatPopupMenu(anchor: View, chat: AIChatInfo, primColor: Int, txtColor: Int) {
        val popup = PopupMenu(context, anchor, Gravity.END)
        popup.menu.add(0, 1, 0, "Настройки")
        popup.menu.add(0, 2, 1, "Удалить")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    onSettingsClick(chat)
                    // Do NOT dismiss — let the caller decide (rebuild or dismiss)
                    true
                }
                2 -> {
                    onDeleteChat(chat)
                    // Do NOT dismiss — let the caller decide (rebuild or dismiss)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}
