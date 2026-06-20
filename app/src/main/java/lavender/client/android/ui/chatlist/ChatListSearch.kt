package lavender.client.android.ui.chatlist

import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lavender.client.android.R

/**
 * Search setup for ChatListActivity.
 */
internal fun setupSearchMenu(activity: ChatListActivity) {
    activity.toolbar?.inflateMenu(R.menu.chat_list_search)
    activity.toolbar?.setOnMenuItemClickListener { menuItem ->
        if (menuItem.itemId == R.id.action_search) {
            true
        } else {
            false
        }
    }

    val searchItem = activity.toolbar?.menu?.findItem(R.id.action_search)
    activity.searchView = searchItem?.actionView as? SearchView
    searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
        override fun onMenuItemActionExpand(item: MenuItem): Boolean {
            return true
        }
        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
            activity.viewModel.loadChats()
            return true
        }
    })

    activity.searchView?.apply {
        queryHint = activity.getString(R.string.search_chats)
        setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true

            override fun onQueryTextChange(newText: String?): Boolean {
                activity.searchDebounceJob?.cancel()
                activity.searchDebounceJob = activity.lifecycleScope.launch {
                    delay(ChatListActivity.SEARCH_DEBOUNCE_MS)
                    val query = newText ?: ""
                    if (query.isEmpty()) {
                        activity.viewModel.loadChats()
                    } else {
                        activity.viewModel.searchChats(query)
                    }
                }
                return true
            }
        })
    }
}
