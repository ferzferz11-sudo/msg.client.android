package lavender.client.android.ui.chatlist

import com.google.android.material.tabs.TabLayout
import lavender.client.android.R

/**
 * Tab setup for ChatListActivity.
 */
internal fun setupTabs(activity: ChatListActivity) {
    activity.tabLayout?.let { tabs ->
        tabs.addTab(tabs.newTab().setText(R.string.tab_all))
        tabs.addTab(tabs.newTab().setText(R.string.tab_groups))
        tabs.addTab(tabs.newTab().setText(R.string.tab_ai))

        // Add Company tab only if user has a company
        val hasCompany = lavender.client.android.data.session.SessionManager.session.value.hasCompany
        if (hasCompany) {
            tabs.addTab(tabs.newTab().setText(R.string.tab_company))
        }

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val filter = when (tab?.position) {
                    0 -> "all"
                    1 -> "groups"
                    2 -> "ai"
                    3 -> if (hasCompany) "company" else "all"
                    else -> "all"
                }
                activity.viewModel.setTabFilter(filter)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
}
