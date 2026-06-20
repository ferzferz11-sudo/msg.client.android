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
 * AI Bottom Sheet — v2 shell layout.
 *
 * Layout (top to bottom):
 * 1. Notifications
 * 2. Divider
 * 3. AI Agents section
 *    - New AI Chat button
 *    - Manage Agents button
 * 4. Divider
 * 5. Remote Agents section
 */
class AIBottomSheet(
    context: Context,
    private val onCreateAiChat: () -> Unit = {},
    private val onManageAgents: () -> Unit = {},
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
            val theme = ThemeStore.currentTheme()
            applyTheme(theme)
            buildContent()
        }
    }

    private fun buildContent() {
        contentContainer?.removeAllViews()
        val theme = ThemeStore.currentTheme()
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)

        titleView?.text = context.getString(R.string.ai_sheet_title)

        // === Section 0: Notifications with badge ===
        val notifItem = LayoutInflater.from(context)
            .inflate(R.layout.widget_action_item, contentContainer, false)
        val notifIcon = notifItem.findViewById<ImageView>(R.id.actionIcon)
        val notifText = notifItem.findViewById<TextView>(R.id.actionText)
        val notifBadge = notifItem.findViewById<TextView>(R.id.actionBadge)
        notifIcon.setImageResource(R.drawable.ic_notifications)
        notifIcon.imageTintList = ColorStateList.valueOf(primColor)
        notifText.text = context.getString(R.string.ai_notifications)
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

        // === Section 1: AI Agents ===
        val aiHeader = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_header, contentContainer, false) as TextView
        aiHeader.text = context.getString(R.string.ai_agents_section)
        contentContainer?.addView(aiHeader)

        // New AI Chat button
        val aiChatCreate = LayoutInflater.from(context)
            .inflate(R.layout.widget_action_item, contentContainer, false)
        val aiChatIcon = aiChatCreate.findViewById<ImageView>(R.id.actionIcon)
        val aiChatText = aiChatCreate.findViewById<TextView>(R.id.actionText)
        val aiChatBadge = aiChatCreate.findViewById<TextView>(R.id.actionBadge)
        aiChatIcon.setImageResource(R.drawable.ic_add)
        aiChatIcon.imageTintList = ColorStateList.valueOf(primColor)
        aiChatText.text = context.getString(R.string.ai_create_new_chat)
        aiChatText.setTextColor(primColor)
        aiChatBadge.visibility = View.GONE
        aiChatCreate.setOnClickListener {
            onCreateAiChat()
            dismiss()
        }
        aiChatCreate.setBackgroundResource(R.drawable.bg_action_item_hover)
        contentContainer?.addView(aiChatCreate)

        // Manage Agents button
        val manageAgents = LayoutInflater.from(context)
            .inflate(R.layout.widget_action_item, contentContainer, false)
        val manageIcon = manageAgents.findViewById<ImageView>(R.id.actionIcon)
        val manageText = manageAgents.findViewById<TextView>(R.id.actionText)
        val manageBadge = manageAgents.findViewById<TextView>(R.id.actionBadge)
        manageIcon.setImageResource(R.drawable.ic_agents)
        manageIcon.imageTintList = ColorStateList.valueOf(primColor)
        manageText.text = context.getString(R.string.ai_manage_agents)
        manageText.setTextColor(primColor)
        manageBadge.visibility = View.GONE
        manageAgents.setOnClickListener {
            onManageAgents()
            dismiss()
        }
        manageAgents.setBackgroundResource(R.drawable.bg_action_item_hover)
        contentContainer?.addView(manageAgents)

        // === Section 2: Remote Agents ===
        val remoteDivider = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_divider, contentContainer, false)
        contentContainer?.addView(remoteDivider)

        val remoteHeader = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_header, contentContainer, false) as TextView
        remoteHeader.text = context.getString(R.string.remote_agent_title)
        contentContainer?.addView(remoteHeader)

        // Open Remote Agents button
        val remoteOpen = LayoutInflater.from(context)
            .inflate(R.layout.widget_action_item, contentContainer, false)
        val remoteIcon = remoteOpen.findViewById<ImageView>(R.id.actionIcon)
        val remoteText = remoteOpen.findViewById<TextView>(R.id.actionText)
        val remoteBadge = remoteOpen.findViewById<TextView>(R.id.actionBadge)
        remoteIcon.setImageResource(R.drawable.ic_agents)
        remoteIcon.imageTintList = ColorStateList.valueOf(primColor)
        remoteText.text = context.getString(R.string.ai_manage_agents)
        remoteText.setTextColor(primColor)
        remoteBadge.visibility = View.GONE
        remoteOpen.setOnClickListener {
            onOpenRemoteAgents()
            dismiss()
        }
        remoteOpen.setBackgroundResource(R.drawable.bg_action_item_hover)
        contentContainer?.addView(remoteOpen)
    }
}
