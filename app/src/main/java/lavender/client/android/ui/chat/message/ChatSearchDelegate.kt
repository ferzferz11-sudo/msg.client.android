package lavender.client.android.ui.chat.message

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.graphics.toColorInt
import lavender.client.android.R
import lavender.client.android.ui.adapter.MessageAdapter
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils

/**
 * Search bar: text input, next/prev navigation, results count, theming.
 */
class ChatSearchDelegate(
    private val activity: AppCompatActivity
) {
    lateinit var searchBar: LinearLayout
    lateinit var searchInput: EditText
    lateinit var searchNext: ImageButton
    lateinit var searchPrev: ImageButton
    lateinit var searchResultsCount: TextView
    lateinit var toolbarContent: View

    private var adapter: MessageAdapter? = null
    private var searchResults = listOf<Int>()
    private var currentSearchIndex = -1

    var getToolbarDelegate: (() -> ChatToolbarDelegate)? = null

    fun initViews() {
        searchBar = activity.findViewById(R.id.searchBar)
        searchInput = activity.findViewById(R.id.searchInput)
        searchNext = activity.findViewById(R.id.searchNext)
        searchPrev = activity.findViewById(R.id.searchPrev)
        searchResultsCount = activity.findViewById(R.id.searchResultsCount)
        toolbarContent = activity.findViewById(R.id.toolbarContent)
    }

    fun setAdapter(adapter: MessageAdapter) {
        this.adapter = adapter
    }

    fun setupListeners() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        searchNext.setOnClickListener { navigateSearch(1) }
        searchPrev.setOnClickListener { navigateSearch(-1) }
    }

    fun show() {
        searchBar.isVisible = true
        toolbarContent.isVisible = false
        getToolbarDelegate?.invoke()?.setNavigationIcon(R.drawable.ic_close)
        val theme = ThemeStore.currentTheme()
        try {
            val prim = theme.primaryColor.toColorInt()
            val onPrim = theme.onPrimaryColor.toColorInt()
            searchBar.setBackgroundColor(prim)
            searchInput.setTextColor(onPrim)
            searchInput.setHintTextColor(ThemeUtils.adjustAlpha(onPrim, 0.6f))
            searchResultsCount.setTextColor(onPrim)
            searchInput.highlightColor = ThemeUtils.adjustAlpha(onPrim, 0.3f)
            searchInput.textCursorDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setSize((2 * activity.resources.displayMetrics.density).toInt(), 0)
                setColor(onPrim)
            }
            val tint = ColorStateList.valueOf(onPrim)
            activity.findViewById<ImageButton>(R.id.searchPrev)?.imageTintList = tint
            activity.findViewById<ImageButton>(R.id.searchNext)?.imageTintList = tint
        } catch (_: Exception) {}
        searchInput.requestFocus()
        (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(searchInput, 0)
    }

    fun hide() {
        searchBar.isVisible = false
        toolbarContent.isVisible = true
        searchInput.text.clear()
        searchResults = emptyList()
        currentSearchIndex = -1
        searchResultsCount.text = ""
        adapter?.setSearchHighlight(null)
        (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(searchInput.windowToken, 0)
        getToolbarDelegate?.invoke()?.setNavigationIcon(R.drawable.ic_back_arrow)
    }

    fun isVisible(): Boolean = searchBar.isVisible

    private fun performSearch(query: String) {
        adapter?.setSearchHighlight(query)
        if (query.isEmpty()) {
            searchResults = emptyList(); currentSearchIndex = -1; searchResultsCount.text = ""; return
        }
        val results = mutableListOf<Int>()
        val messages = adapter?.currentList ?: emptyList()
        for (i in messages.indices) {
            if (messages[i].text.contains(query, ignoreCase = true)) results.add(i)
        }
        searchResults = results
        if (searchResults.isNotEmpty()) {
            currentSearchIndex = searchResults.size - 1
            navigateSearch(0)
        } else {
            currentSearchIndex = -1
            searchResultsCount.text = "0/0"
        }
    }

    private fun navigateSearch(direction: Int) {
        if (searchResults.isEmpty()) return
        currentSearchIndex += direction
        if (currentSearchIndex < 0) currentSearchIndex = searchResults.size - 1
        if (currentSearchIndex >= searchResults.size) currentSearchIndex = 0
        val messagesRecyclerView = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.messagesRecyclerView)
        messagesRecyclerView.scrollToPosition(searchResults[currentSearchIndex])
        searchResultsCount.text = activity.getString(R.string.search_results_format, currentSearchIndex + 1, searchResults.size)
    }
}
