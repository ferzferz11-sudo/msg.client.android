package lavender.client.android.ui.company

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class CompanyPagerAdapter(
    activity: FragmentActivity,
    private val companyId: String
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CompanyListFragment.newInstance(companyId, CompanyListFragment.TYPE_MEMBERS)
            1 -> CompanyListFragment.newInstance(companyId, CompanyListFragment.TYPE_POSITIONS)
            2 -> CompanyListFragment.newInstance(companyId, CompanyListFragment.TYPE_CHATS)
            else -> CompanyListFragment.newInstance(companyId, CompanyListFragment.TYPE_MEMBERS)
        }
    }
}
