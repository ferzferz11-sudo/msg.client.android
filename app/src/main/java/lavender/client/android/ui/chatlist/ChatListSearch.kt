package lavender.client.android.ui.chatlist

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.theme.ThemeStore

/**
 * Search setup for ChatListActivity — overflow menu item.
 */
internal fun setupSearchMenu(activity: ChatListActivity) {
    activity.toolbar?.inflateMenu(R.menu.chat_list_menu)

    // Tint overflow icon white
    try {
        val theme = ThemeStore.currentTheme()
        val iconColor = lavender.client.android.theme.ThemeUtils.parseSafeColor(theme.onPrimaryColor, android.graphics.Color.WHITE)
        activity.toolbar?.overflowIcon?.let {
            val wrapped = androidx.core.graphics.drawable.DrawableCompat.wrap(it)
            androidx.core.graphics.drawable.DrawableCompat.setTint(wrapped, iconColor)
            activity.toolbar?.overflowIcon = wrapped
        }
    } catch (_: Exception) {}

    activity.toolbar?.setOnMenuItemClickListener { menuItem ->
        when (menuItem.itemId) {
            R.id.action_search -> {
                showSearchView(activity)
                true
            }
            else -> false
        }
    }
}

private fun showSearchView(activity: ChatListActivity) {
    // Create a SearchView in the toolbar
    val searchView = SearchView(activity).apply {
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
        setOnCloseListener {
            activity.toolbar?.menu?.clear()
            setupSearchMenu(activity)
            activity.viewModel.loadChats()
            false
        }
    }

    // Replace menu with search view and expand immediately
    activity.toolbar?.menu?.clear()
    val searchItem = activity.toolbar?.menu?.add(Menu.NONE, R.id.action_search, Menu.NONE, R.string.search)
    searchItem?.actionView = searchView
    searchItem?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    // Post to ensure overflow menu is closed and toolbar is laid out
    activity.toolbar?.post {
        searchItem?.expandActionView()
        searchView.requestFocus()
        // Force keyboard show via WindowInsetsController (API 30+) with fallback
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            activity.window.insetsController?.show(android.view.WindowInsets.Type.ime())
        } else {
            @Suppress("DEPRECATION")
            val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            @Suppress("DEPRECATION")
            imm?.showSoftInput(searchView.findFocus(), android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
