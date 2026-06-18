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

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val filter = when (tab?.position) {
                    0 -> "all"
                    1 -> "groups"
                    2 -> "ai"
                    else -> "all"
                }
                activity.viewModel.setTabFilter(filter)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
}
