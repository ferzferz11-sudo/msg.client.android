package lavender.client.android.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.*
import lavender.client.android.R
import lavender.client.android.data.ai.AiV2Agent
import lavender.client.android.data.ai.AiV2ChatUseCase
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

/**
 * AI Bottom Sheet — redesigned flow:
 *
 * Layout (top to bottom):
 * 1. "Добавить агента" section
 *    - List of preset agents with checkboxes (for quick chat creation)
 *    - "Создать своего агента" button
 * 2. Divider
 * 3. "Создать чат" section
 *    - Shows selected agents from checkboxes above
 *    - "Начать чат" button (creates chat with selected agents)
 * 4. Divider
 * 5. Remote Agents
 * 6. Divider
 * 7. Notifications (at the bottom)
 */
class AIBottomSheet(
    context: Context,
    private val onCreateAiChat: (agentId: String, agentName: String) -> Unit = { _, _ -> },
    private val onCreateMultiAgentChat: (agentIds: List<String>, agentNames: List<String>) -> Unit = { _, _ -> },
    private val onAddCustomAgent: () -> Unit = {},
    private val onOpenNotifications: () -> Unit = {},
    private val onOpenRemoteAgents: () -> Unit = {},
    private var unreadNotifCount: Int = 0,
    theme: lavender.client.android.theme.Theme = ThemeStore.currentTheme()
) : StandardBottomSheet(context, R.layout.widget_ai_bottom_sheet, theme) {

    private val selectedAgents = mutableSetOf<AiV2Agent>()
    private var presetAgents = listOf<AiV2Agent>()
    private val agentCheckBoxes = mutableListOf<Pair<AiV2Agent, CheckBox>>()
    private var agentLoadJob: Job? = null
    private var summaryText: TextView? = null
    private var createChatButtonView: View? = null

    fun buildAndShow() {
        buildContent()
        show()
        loadPresetAgents()
    }

    fun rebuildContent() {
        if (isShowing()) {
            val theme = ThemeStore.currentTheme()
            applyTheme(theme)
            buildContent()
            show()
            loadPresetAgents()
        }
    }

    private fun loadPresetAgents() {
        agentLoadJob?.cancel()
        val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        agentLoadJob = scope.launch {
            try {
                presetAgents = AiV2ChatUseCase.listAgents(includePublic = true).filter { it.isPreset }
                if (isShowing()) {
                    buildContent()
                }
            } catch (e: Exception) {
                presetAgents = emptyList()
                if (isShowing()) {
                    buildContent()
                }
            }
        }
    }

    private fun buildContent() {
        contentContainer?.removeAllViews()
        agentCheckBoxes.clear()
        selectedAgents.clear()
        summaryText = null
        createChatButtonView = null

        val theme = ThemeStore.currentTheme()
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)

        titleView?.text = context.getString(R.string.ai_sheet_title)

        // === Section 1: Add Agent (presets with checkboxes) ===
        val addAgentHeader = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_header, contentContainer, false) as TextView
        addAgentHeader.text = context.getString(R.string.ai_add_agent)
        contentContainer?.addView(addAgentHeader)

        if (presetAgents.isNotEmpty()) {
            for (agent in presetAgents) {
                val agentItem = LayoutInflater.from(context)
                    .inflate(R.layout.item_ai_agent_selectable, contentContainer, false)
                val checkbox = agentItem.findViewById<CheckBox>(R.id.agentCheckbox)
                val emoji = agentItem.findViewById<TextView>(R.id.agentEmoji)
                val name = agentItem.findViewById<TextView>(R.id.agentName)
                val desc = agentItem.findViewById<TextView>(R.id.agentDescription)

                emoji.text = getAgentEmoji(agent.id)
                name.text = agent.name
                name.setTextColor(txtColor)
                desc.text = agent.description
                desc.setTextColor(txtColor)

                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedAgents.add(agent)
                    } else {
                        selectedAgents.remove(agent)
                    }
                    updateCreateChatButton()
                }

                agentCheckBoxes.add(agent to checkbox)
                contentContainer?.addView(agentItem)
            }
        } else {
            val loadingText = TextView(context).apply {
                text = context.getString(R.string.ai_loading_agents)
                textSize = 14f
                setPadding(16, 8, 16, 8)
                setTextColor(txtColor)
            }
            contentContainer?.addView(loadingText)
        }

        // "Создать своего агента" button
        val addCustomAgent = LayoutInflater.from(context)
            .inflate(R.layout.widget_action_item, contentContainer, false)
        val addCustomIcon = addCustomAgent.findViewById<ImageView>(R.id.actionIcon)
        val addCustomText = addCustomAgent.findViewById<TextView>(R.id.actionText)
        val addCustomBadge = addCustomAgent.findViewById<TextView>(R.id.actionBadge)
        addCustomIcon.setImageResource(R.drawable.ic_add)
        addCustomIcon.imageTintList = ColorStateList.valueOf(primColor)
        addCustomText.text = context.getString(R.string.ai_create_custom_agent)
        addCustomText.setTextColor(primColor)
        addCustomBadge.visibility = View.GONE
        addCustomAgent.setOnClickListener {
            onAddCustomAgent()
            dismiss()
        }
        addCustomAgent.setBackgroundResource(R.drawable.bg_action_item_hover)
        contentContainer?.addView(addCustomAgent)

        // === Section 2: Create Chat ===
        val createChatDivider = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_divider, contentContainer, false)
        contentContainer?.addView(createChatDivider)

        val createChatHeader = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_header, contentContainer, false) as TextView
        createChatHeader.text = context.getString(R.string.ai_create_chat)
        contentContainer?.addView(createChatHeader)

        // Selected agents summary
        summaryText = TextView(context).apply {
            text = context.getString(R.string.ai_select_agents_hint)
            textSize = 14f
            setPadding(16, 8, 16, 4)
            setTextColor(txtColor)
        }
        contentContainer?.addView(summaryText)

        // Create chat button
        val createChatBtn = LayoutInflater.from(context)
            .inflate(R.layout.widget_action_item, contentContainer, false)
        val createChatIcon = createChatBtn.findViewById<ImageView>(R.id.actionIcon)
        val createChatText = createChatBtn.findViewById<TextView>(R.id.actionText)
        val createChatBadge = createChatBtn.findViewById<TextView>(R.id.actionBadge)
        createChatIcon.setImageResource(R.drawable.ic_add)
        createChatIcon.imageTintList = ColorStateList.valueOf(primColor)
        createChatText.text = context.getString(R.string.ai_start_chat)
        createChatText.setTextColor(primColor)
        createChatBadge.visibility = View.GONE
        createChatBtn.isEnabled = false
        createChatBtn.alpha = 0.5f
        createChatBtn.setOnClickListener {
            if (selectedAgents.size == 1) {
                val agent = selectedAgents.first()
                onCreateAiChat(agent.id, agent.name)
            } else if (selectedAgents.size > 1) {
                val ids = selectedAgents.map { it.id }
                val names = selectedAgents.map { it.name }
                onCreateMultiAgentChat(ids, names)
            }
            dismiss()
        }
        createChatBtn.setBackgroundResource(R.drawable.bg_action_item_hover)
        createChatButtonView = createChatBtn
        contentContainer?.addView(createChatBtn)

        // === Section 3: Remote Agents ===
        val remoteDivider = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_divider, contentContainer, false)
        contentContainer?.addView(remoteDivider)

        val remoteHeader = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_header, contentContainer, false) as TextView
        remoteHeader.text = context.getString(R.string.remote_agent_title)
        contentContainer?.addView(remoteHeader)

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

        // === Section 4: Notifications (at the bottom) ===
        val notifDivider = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_divider, contentContainer, false)
        contentContainer?.addView(notifDivider)

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
    }

    private fun updateCreateChatButton() {
        if (selectedAgents.isEmpty()) {
            summaryText?.text = context.getString(R.string.ai_select_agents_hint)
        } else if (selectedAgents.size == 1) {
            val agent = selectedAgents.first()
            summaryText?.text = "${context.getString(R.string.ai_selected_agent)}: ${agent.name}"
        } else {
            summaryText?.text = "${context.getString(R.string.ai_selected_agents)}: ${selectedAgents.size}"
        }

        createChatButtonView?.let { btn ->
            btn.isEnabled = selectedAgents.isNotEmpty()
            btn.alpha = if (selectedAgents.isNotEmpty()) 1.0f else 0.5f
        }
    }

    private fun getAgentEmoji(agentId: String): String {
        return when (agentId) {
            "reve" -> "🎨"
            "vision" -> "👁"
            "mimo" -> "🤖"
            "assistant" -> "🧠"
            "developer" -> "💻"
            "devops" -> "⚙️"
            "architect" -> "🏗️"
            "writer" -> "✍️"
            "analyst" -> "📊"
            "translator" -> "🌐"
            else -> "🤖"
        }
    }
}
