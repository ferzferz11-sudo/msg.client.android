package lavender.client.android.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.*
import lavender.client.android.R
import lavender.client.android.data.ai.AgentStatus
import lavender.client.android.data.ai.AiV2Agent
import lavender.client.android.data.ai.AiV2ChatUseCase
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

/**
 * AI Bottom Sheet — redesigned flow:
 *
 * Layout (top to bottom):
 * 1. "My Agents" section
 *    - List of user's custom agents with checkboxes (for quick chat creation)
 * 2. "Manage agents" button → AiV2AgentListActivity
 * 3. "Create custom agent" button → AiAgentSetupActivity
 * 4. Footer: summary, hint, "Start chat" button
 */
class AIBottomSheet(
    context: Context,
    private val onCreateAiChat: (agentId: String, agentName: String) -> Unit = { _, _ -> },
    private val onCreateMultiAgentChat: (agentIds: List<String>, agentNames: List<String>) -> Unit = { _, _ -> },
    private val onAddCustomAgent: () -> Unit = {},
    private val onOpenAiAgentList: () -> Unit = {},
    private val onOpenRemoteAgents: () -> Unit = {},
    private val onOpenAgentSettings: (agentId: String) -> Unit = { _ -> },
    theme: lavender.client.android.theme.Theme = ThemeStore.currentTheme()
) : StandardBottomSheet(context, R.layout.widget_ai_bottom_sheet, theme) {

    private val selectedAgents = mutableSetOf<AiV2Agent>()
    private var myAgents = listOf<AiV2Agent>()
    private var isLoadingAgents = true
    private val agentCheckBoxes = mutableListOf<Pair<AiV2Agent, ImageView>>()
    private var agentLoadJob: Job? = null
    private val agentScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var summaryText: TextView? = null
    private var createChatButtonView: View? = null
    private var footerContainer: android.widget.LinearLayout? = null

    fun buildAndShow() {
        isLoadingAgents = true
        selectedAgents.clear()
        buildContent()
        show()
        loadPresetAgents()
    }

    fun rebuildContent() {
        if (isShowing()) {
            val theme = ThemeStore.currentTheme()
            applyTheme(theme)
            isLoadingAgents = true
            selectedAgents.clear()
            buildContent()
            show()
            loadPresetAgents()
        }
    }

    private fun loadPresetAgents() {
        agentLoadJob?.cancel()
        agentLoadJob = agentScope.launch {
            try {
                val agents = AiV2ChatUseCase.listAgents(includePublic = false)
                myAgents = agents
                android.util.Log.d("AIBottomSheet", "Loaded ${myAgents.size} my agents")
            } catch (e: Exception) {
                android.util.Log.e("AIBottomSheet", "Failed to load agents: ${e.message}", e)
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
        footerContainer?.removeAllViews()
        agentCheckBoxes.clear()
        summaryText = null
        createChatButtonView = null

        footerContainer = root?.findViewById(R.id.footerContainer)

        val theme = ThemeStore.currentTheme()
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
        val surfaceColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)

        titleView?.text = context.getString(R.string.ai_sheet_title)

        // === Section 1: My Agents (quick chat with checkboxes) ===
        if (isLoadingAgents) {
            val loadingText = TextView(context).apply {
                text = context.getString(R.string.ai_loading_agents)
                textSize = 14f
                setTextColor(txtColor)
                alpha = 0.6f
                setPadding(dp(16), dp(16), dp(16), dp(16))
                gravity = android.view.Gravity.CENTER
            }
            contentContainer?.addView(loadingText)
        } else if (myAgents.isEmpty()) {
            val emptyText = TextView(context).apply {
                text = context.getString(R.string.ai_no_agents)
                textSize = 14f
                setTextColor(txtColor)
                alpha = 0.6f
                setPadding(dp(16), dp(16), dp(16), dp(16))
                gravity = android.view.Gravity.CENTER
            }
            contentContainer?.addView(emptyText)
        } else {
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
                val checkView = createCheckView(primColor) {
                    if (it) selectedAgents.add(agent) else selectedAgents.remove(agent)
                    updateCreateChatButton()
                }
                icon.setImageResource(R.drawable.ic_agents)
                icon.imageTintList = ColorStateList.valueOf(primColor)
                val displayName = agent.name.ifEmpty { agent.id }
                val status = AgentStatus.fromProviderConfig(agent.providerConfig)
                val statusDot = when (status) {
                    AgentStatus.AVAILABLE -> "\uD83D\uDFE2"
                    AgentStatus.SERVER_KEY -> "\uD83D\uDFE1"
                    AgentStatus.NEEDS_KEY -> "\uD83D\uDD34"
                }
                text.text = "${getAgentEmoji(agent.id)} $displayName $statusDot"
                text.setTextColor(txtColor)
                badge.visibility = View.GONE

                row.setOnLongClickListener {
                    onOpenAgentSettings(agent.id)
                    dismiss()
                    true
                }
                row.setOnClickListener { checkView.performClick() }

                val container = row.findViewById<android.widget.LinearLayout>(R.id.actionRoot)
                container?.addView(checkView, 1)

                row.setBackgroundResource(R.drawable.bg_action_item_hover)
                contentContainer?.addView(row)
                agentCheckBoxes.add(agent to checkView)
            }
        }

        // === Section 3: AI Agents (manage) ===
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

        // === "Создать своего агента" button ===
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

        // === Summary + Create chat button in fixed footer ===
        summaryText = TextView(context).apply {
            text = context.getString(R.string.ai_select_agents_hint)
            textSize = 13f
            setTextColor(txtColor)
            alpha = 0.7f
            setPadding(dp(16), dp(8), dp(16), dp(0))
        }
        footerContainer?.addView(summaryText)

        val longPressHint = TextView(context).apply {
            text = context.getString(R.string.ai_long_press_to_settings)
            textSize = 12f
            setTextColor(txtColor)
            alpha = 0.5f
            setPadding(dp(16), dp(2), dp(16), dp(8))
        }
        footerContainer?.addView(longPressHint)

        // "Start chat" button — fixed at bottom, disabled until agent selected
        val startSelectedBtn = LayoutInflater.from(context)
            .inflate(R.layout.widget_action_item, footerContainer, false)
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
        startSelectedBtn.isEnabled = false
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
        footerContainer?.addView(startSelectedBtn)

        // Restore checkbox states
        for ((agent, cv) in agentCheckBoxes) {
            if (agent in selectedAgents) restoreCheckState(cv, true)
        }
        updateCreateChatButton()
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

    private fun createCheckView(tint: Int, onToggle: (Boolean) -> Unit): ImageView {
        var checked = false
        val size = dp(22)
        return ImageView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(6)
            }
            setPadding(dp(2), dp(2), dp(2), dp(2))
            setImageResource(R.drawable.ic_check_box_outline)
            imageTintList = ColorStateList.valueOf(tint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            isClickable = true
            isFocusable = true
            setOnClickListener {
                checked = !checked
                setImageResource(if (checked) R.drawable.ic_checked_small else R.drawable.ic_check_box_outline)
                onToggle(checked)
            }
        }
    }

    private fun restoreCheckState(checkView: ImageView, checked: Boolean) {
        checkView.setImageResource(if (checked) R.drawable.ic_checked_small else R.drawable.ic_check_box_outline)
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
            "hermes" -> "🔬"
            else -> "🤖"
        }
    }
}
