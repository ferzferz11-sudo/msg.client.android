package lavender.client.android.ui.chatlist

import com.google.android.material.tabs.TabLayout
import lavender.client.android.R
import lavender.client.android.data.models.ChatInfo

/**
 * Tab setup for ChatListActivity.
 * Fixed tabs: All, Groups (always visible).
 * Dynamic tabs: AI (if non-archived AI chats exist), per-company, Archive (always rightmost if archived exist).
 */

private var isUpdatingTabs = false

internal fun setupTabs(activity: ChatListActivity) {
    activity.tabLayout?.let { tabs ->
        // Clear existing to avoid duplicates on recreation
        tabs.removeAllTabs()
        
        tabs.addTab(tabs.newTab().setText(R.string.tab_all).setTag("all"))
        tabs.addTab(tabs.newTab().setText(R.string.tab_groups).setTag("groups"))

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (isUpdatingTabs) return
                val filter = resolveTabFilter(tab)
                activity.viewModel.setTabFilter(filter)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
}

private fun resolveTabFilter(tab: TabLayout.Tab?): String {
    val tag = tab?.tag as? String
    return tag ?: "all"
}

/**
 * Update all dynamic tabs: AI, per-company, Archive.
 * Robust implementation that avoids constant removal/addition if nothing changed.
 */
internal fun updateDynamicTabs(activity: ChatListActivity, chats: List<ChatInfo>) {
    val tabs = activity.tabLayout ?: return
    
    // 1. Determine required dynamic tabs (only show AI if there are non-archived AI chats)
    val hasAiChats = chats.any { (it.type == "owl" || it.type == "hermes") && !it.isArchived }
    val hasArchived = chats.any { it.isArchived }
    val companies = chats
        .filter { it.companyId.isNotEmpty() && !it.isArchived }
        .groupBy { it.companyId }
        .keys
        .sorted()

    val currentTags = mutableListOf<String>()
    for (i in 0 until tabs.tabCount) {
        (tabs.getTabAt(i)?.tag as? String)?.let { currentTags.add(it) }
    }

    val requiredTags = mutableListOf("all", "groups")
    if (hasAiChats) requiredTags.add("ai")
    for (companyId in companies) requiredTags.add("company:$companyId")
    if (hasArchived) requiredTags.add("archive")

    // If tabs already match, do nothing to avoid selection resets
    if (currentTags == requiredTags) return

    isUpdatingTabs = true
    try {
        val selectedTag = tabs.getTabAt(tabs.selectedTabPosition)?.tag as? String ?: "all"
        
        // Use a more surgical update instead of removeAllTabs to keep state better
        // But for simplicity and correctness of order, we'll rebuild if tags don't match
        tabs.removeAllTabs()
        
        tabs.addTab(tabs.newTab().setText(R.string.tab_all).setTag("all"))
        tabs.addTab(tabs.newTab().setText(R.string.tab_groups).setTag("groups"))

        val companyNameCache = activity.viewModel.companyNameCache
        
        if (hasAiChats) {
            tabs.addTab(tabs.newTab().setText(R.string.tab_ai).setTag("ai"))
        }

        for (companyId in companies) {
            val name = companyNameCache[companyId] ?: companyId
            tabs.addTab(tabs.newTab().setText(name).setTag("company:$companyId"))
        }

        if (hasArchived) {
            tabs.addTab(tabs.newTab().setText(R.string.tab_archive).setTag("archive"))
        }

        // Restore selection
        for (i in 0 until tabs.tabCount) {
            if (tabs.getTabAt(i)?.tag == selectedTag) {
                tabs.getTabAt(i)?.select()
                break
            }
        }
    } finally {
        isUpdatingTabs = false
    }
}
