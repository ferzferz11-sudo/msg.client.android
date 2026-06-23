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
    private val onOpenAiAgentList: () -> Unit = {},
    private val onOpenRemoteAgents: () -> Unit = {},
    theme: lavender.client.android.theme.Theme = ThemeStore.currentTheme()
) : StandardBottomSheet(context, R.layout.widget_ai_bottom_sheet, theme) {

    private val selectedAgents = mutableSetOf<AiV2Agent>()
    private var presetAgents = listOf<AiV2Agent>()
    private var myAgents = listOf<AiV2Agent>()
    private var isLoadingAgents = true
    private val agentCheckBoxes = mutableListOf<Pair<AiV2Agent, CheckBox>>()
    private var agentLoadJob: Job? = null
    private var summaryText: TextView? = null
    private var createChatButtonView: View? = null

    fun buildAndShow() {
        isLoadingAgents = true
        buildContent()
        show()
        loadPresetAgents()
    }

    fun rebuildContent() {
        if (isShowing()) {
            val theme = ThemeStore.currentTheme()
            applyTheme(theme)
            isLoadingAgents = true
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
                val agents = AiV2ChatUseCase.listAgents(includePublic = true)
                presetAgents = agents.filter { it.isPreset }
                myAgents = agents.filter { !it.isPreset }
                android.util.Log.d("AIBottomSheet", "Loaded ${agents.size} agents, ${presetAgents.size} presets, ${myAgents.size} my agents")
            } catch (e: Exception) {
                android.util.Log.e("AIBottomSheet", "Failed to load agents: ${e.message}", e)
                presetAgents = emptyList()
                myAgents = emptyList()
            }
            isLoadingAgents = false
            if (isShowing()) {
                buildContent()
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

        // === Section 1: Quick actions ===

        // "Начать чат с ИИ" button
        val startChatBtn = LayoutInflater.from(context)
            .inflate(R.layout.widget_action_item, contentContainer, false)
        val startChatIcon = startChatBtn.findViewById<ImageView>(R.id.actionIcon)
        val startChatText = startChatBtn.findViewById<TextView>(R.id.actionText)
        val startChatBadge = startChatBtn.findViewById<TextView>(R.id.actionBadge)
        startChatIcon.setImageResource(R.drawable.ic_add)
        startChatIcon.imageTintList = ColorStateList.valueOf(primColor)
        startChatText.text = context.getString(R.string.ai_start_chat)
        startChatText.setTextColor(primColor)
        startChatBadge.visibility = View.GONE
        startChatBtn.setOnClickListener {
            val defaultAgent = presetAgents.firstOrNull()
            if (defaultAgent != null) {
                onCreateAiChat(defaultAgent.id, defaultAgent.name)
            } else {
                onCreateAiChat("assistant", "Assistant")
            }
            dismiss()
        }
        startChatBtn.setBackgroundResource(R.drawable.bg_action_item_hover)
        contentContainer?.addView(startChatBtn)

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

        // === Divider ===
        val divider = LayoutInflater.from(context)
            .inflate(R.layout.widget_section_divider, contentContainer, false)
        contentContainer?.addView(divider)

        // === Section 2: AI Agents (manage) ===
        val aiAgentsOpen = LayoutInflater.from(context)
            .inflate(R.layout.widget_action_item, contentContainer, false)
        val aiAgentsIcon = aiAgentsOpen.findViewById<ImageView>(R.id.actionIcon)
        val aiAgentsText = aiAgentsOpen.findViewById<TextView>(R.id.actionText)
        val aiAgentsBadge = aiAgentsOpen.findViewById<TextView>(R.id.actionBadge)
        aiAgentsIcon.setImageResource(R.drawable.ic_agents)
        aiAgentsIcon.imageTintList = ColorStateList.valueOf(primColor)
        aiAgentsText.text = context.getString(R.string.ai_manage_agents)
        aiAgentsText.setTextColor(primColor)
        aiAgentsBadge.visibility = View.GONE
        aiAgentsOpen.setOnClickListener {
            onOpenAiAgentList()
            dismiss()
        }
        aiAgentsOpen.setBackgroundResource(R.drawable.bg_action_item_hover)
        contentContainer?.addView(aiAgentsOpen)

        // === Section 3: My Agents (quick chat) ===
        if (myAgents.isNotEmpty()) {
            val divider2 = LayoutInflater.from(context)
                .inflate(R.layout.widget_section_divider, contentContainer, false)
            contentContainer?.addView(divider2)

            val myAgentsHeader = TextView(context).apply {
                text = context.getString(R.string.ai_my_agents)
                textSize = 13f
                setTextColor(txtColor)
                alpha = 0.6f
                setPadding(dp(16), dp(8), dp(16), dp(4))
            }
            contentContainer?.addView(myAgentsHeader)

            for (agent in myAgents) {
                val row = LayoutInflater.from(context)
                    .inflate(R.layout.widget_action_item, contentContainer, false)
                val icon = row.findViewById<ImageView>(R.id.actionIcon)
                val text = row.findViewById<TextView>(R.id.actionText)
                val badge = row.findViewById<TextView>(R.id.actionBadge)
                val checkbox = CheckBox(context).apply {
                    buttonTintList = ColorStateList.valueOf(primColor)
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) selectedAgents.add(agent) else selectedAgents.remove(agent)
                        updateCreateChatButton()
                    }
                }
                icon.setImageResource(R.drawable.ic_agents)
                icon.imageTintList = ColorStateList.valueOf(primColor)
                text.text = "${getAgentEmoji(agent.id)} ${agent.name}"
                text.setTextColor(txtColor)
                badge.visibility = View.GONE

                val params = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                checkbox.layoutParams = params

                val container = row.findViewById<android.widget.LinearLayout>(R.id.actionRoot)
                container?.addView(checkbox)

                row.setBackgroundResource(R.drawable.bg_action_item_hover)
                contentContainer?.addView(row)
                agentCheckBoxes.add(agent to checkbox)
            }

            // Summary text
            summaryText = TextView(context).apply {
                text = context.getString(R.string.ai_select_agents_hint)
                textSize = 13f
                setTextColor(txtColor)
                alpha = 0.7f
                setPadding(dp(16), dp(8), dp(16), dp(0))
            }
            contentContainer?.addView(summaryText)

            // "Start chat" button
            val startSelectedBtn = LayoutInflater.from(context)
                .inflate(R.layout.widget_action_item, contentContainer, false)
            val startSelectedIcon = startSelectedBtn.findViewById<ImageView>(R.id.actionIcon)
            val startSelectedText = startSelectedBtn.findViewById<TextView>(R.id.actionText)
            val startSelectedBadge = startSelectedBtn.findViewById<TextView>(R.id.actionBadge)
            startSelectedIcon.setImageResource(R.drawable.ic_add)
            startSelectedIcon.imageTintList = ColorStateList.valueOf(Color.WHITE)
            startSelectedText.text = context.getString(R.string.ai_start_chat)
            startSelectedText.setTextColor(Color.WHITE)
            startSelectedBadge.visibility = View.GONE
            startSelectedBtn.setBackgroundColor(primColor)
            startSelectedBtn.alpha = 0.5f
            createChatButtonView = startSelectedBtn
            startSelectedBtn.setOnClickListener {
                if (selectedAgents.isNotEmpty()) {
                    if (selectedAgents.size == 1) {
                        val agent = selectedAgents.first()
                        onCreateAiChat(agent.id, agent.name)
                    } else {
                        onCreateMultiAgentChat(
                            selectedAgents.map { it.id },
                            selectedAgents.map { it.name }
                        )
                    }
                    dismiss()
                }
            }
            contentContainer?.addView(startSelectedBtn)
        }
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

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
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
