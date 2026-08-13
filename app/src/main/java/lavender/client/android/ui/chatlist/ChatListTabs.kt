package lavender.client.android.ui.chatlist

import com.google.android.material.tabs.TabLayout
import lavender.client.android.R
import lavender.client.android.data.models.ChatInfo

/**
 * Tab setup for ChatListActivity.
 * Fixed tabs: All, Groups (always visible).
 * Dynamic tabs: AI (if AI chats exist), per-company, Archive (always rightmost if it has archived).
 */
internal fun setupTabs(activity: ChatListActivity) {
    activity.tabLayout?.let { tabs ->
        tabs.addTab(tabs.newTab().setText(R.string.tab_all))
        tabs.addTab(tabs.newTab().setText(R.string.tab_groups))

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val filter = resolveTabFilter(tab, tabs)
                activity.viewModel.setTabFilter(filter)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
}

/**
 * Resolve tab text to filter string.
 * Company tabs store "company:<id>" as tag.
 */
private fun resolveTabFilter(tab: TabLayout.Tab?, tabs: TabLayout): String {
    val tag = tab?.tag as? String
    if (tag != null && tag.startsWith("company:")) return tag

    val tabText = tab?.text?.toString() ?: return "all"
    return when (tabText) {
        tabs.context.getString(R.string.tab_all) -> "all"
        tabs.context.getString(R.string.tab_groups) -> "groups"
        tabs.context.getString(R.string.tab_ai) -> "ai"
        tabs.context.getString(R.string.tab_archive) -> "archive"
        else -> "all"
    }
}

/**
 * Update all dynamic tabs: AI, per-company, Archive.
 * Order: All, Groups, AI, Company1..., Archive.
 * Archive is always the rightmost tab.
 */
internal fun updateDynamicTabs(activity: ChatListActivity, chats: List<ChatInfo>) {
    activity.tabLayout?.let { tabs ->
        // 1. Remove all dynamic tabs (AI, company, archive)
        removeDynamicTabs(tabs)

        // 2. Calculate what dynamic tabs are needed
        val hasAiChats = chats.any { it.type == "owl" || it.type == "hermes" }
        val hasArchived = chats.any { it.isArchived }
        val companies = chats
            .filter { it.companyId.isNotEmpty() }
            .groupBy { it.companyId }
            .keys
            .sorted()

        // 3. Add dynamic tabs in order: AI, companies..., Archive (rightmost)
        val companyNameCache = activity.viewModel.companyNameCache
        var insertAt = 2 // after All(0), Groups(1)

        if (hasAiChats) {
            val aiTab = tabs.newTab().setText(R.string.tab_ai)
            aiTab.tag = "ai"
            tabs.addTab(aiTab, insertAt)
            insertAt++
        }

        for (companyId in companies) {
            val name = companyNameCache[companyId] ?: companyId
            val tab = tabs.newTab().setText(name)
            tab.tag = "company:$companyId"
            tabs.addTab(tab, insertAt)
            insertAt++
        }

        // Archive always last
        if (hasArchived) {
            val archiveTab = tabs.newTab().setText(R.string.tab_archive)
            archiveTab.tag = "archive"
            tabs.addTab(archiveTab, insertAt)
        }
    }
}

/**
 * Remove all dynamic tabs (identified by tag: "AI", "company:*", "archive").
 */
private fun removeDynamicTabs(tabs: TabLayout) {
    val toRemove = mutableListOf<Int>()
    for (i in 0 until tabs.tabCount) {
        val tag = tabs.getTabAt(i)?.tag as? String
        if (tag != null && (tag == "ai" || tag.startsWith("company:") || tag == "archive")) {
            toRemove.add(i)
        }
    }
    for (i in toRemove.reversed()) {
        tabs.removeTabAt(i)
    }
}
