package lavender.client.android.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R
import lavender.client.android.theme.Theme
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import lavender.client.android.theme.ui.ThemeApplier
import java.util.concurrent.ConcurrentHashMap

import android.widget.EditText
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Base for the themed widget system.
 */
interface ThemedWidget {
    fun applyTheme(theme: Theme)
}

/**
 * Manager for widgets with caching support.
 */
object WidgetManager {
    private val widgetCache = ConcurrentHashMap<String, Any>()
    
    @Suppress("UNCHECKED_CAST")
    fun <T> getOrCreate(key: String, creator: () -> T): T {
        return widgetCache.getOrPut(key) { creator() as Any } as T
    }
    
    fun clearCache() {
        widgetCache.clear()
    }
}

/**
 * Action Item for Bottom Sheets.
 */
data class SheetAction(
    val id: Int,
    val iconRes: Int,
    val text: CharSequence,
    val isPrimary: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Standard Bottom Sheet widget.
 */
open class StandardBottomSheet(val context: Context, layoutId: Int = R.layout.widget_standard_bottom_sheet) : ThemedWidget {
    var dialog: BottomSheetDialog? = null
        protected set
    protected var root: View? = null
    protected var dragHandle: View? = null
    protected var titleView: TextView? = null
    protected var contentContainer: LinearLayout? = null

    init {
        initViews(layoutId)
        applyTheme(ThemeStore.currentTheme())
    }

    private fun initViews(layoutId: Int) {
        val view = LayoutInflater.from(context).inflate(layoutId, null, false)
        dialog = BottomSheetDialog(context).apply {
            setContentView(view)
        }
        
        root = view
        dragHandle = view.findViewById(R.id.dragHandle)
        titleView = view.findViewById(R.id.titleText)
        contentContainer = view.findViewById(R.id.contentContainer)
    }

    override fun applyTheme(theme: Theme) {
        dialog?.let { ThemeApplier.applyToDialog(it, theme) }
        
        try {
            val bgColor = ThemeUtils.parseSafeColor(theme.backgroundColor, Color.BLACK)
            val primaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
            val onSurfaceColor = ThemeUtils.parseSafeColor(theme.onSurfaceColor, Color.WHITE)
            
            root?.setBackgroundColor(bgColor)
            dragHandle?.backgroundTintList = ColorStateList.valueOf(primaryColor)
            titleView?.setTextColor(primaryColor)

            // Theme any InputLayouts and EditTexts
            root?.let { findAndThemeInputs(it, theme, primaryColor, onSurfaceColor) }
        } catch (_: Exception) {}
    }

    protected fun findAndThemeInputs(view: View, theme: Theme, primaryColor: Int, onSurfaceColor: Int) {
        if (view is TextInputLayout) {
            val strokeColorStateList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(primaryColor, ThemeUtils.adjustAlpha(onSurfaceColor, 0.3f))
            )
            view.boxBackgroundColor = ThemeUtils.parseSafeColor(theme.surfaceColor, Color.DKGRAY)
            view.setBoxStrokeColorStateList(strokeColorStateList)
            view.hintTextColor = ColorStateList.valueOf(primaryColor)
            view.defaultHintTextColor = ColorStateList.valueOf(ThemeUtils.adjustAlpha(onSurfaceColor, 0.7f))
            view.setStartIconTintList(ColorStateList.valueOf(primaryColor))
            view.setEndIconTintList(ColorStateList.valueOf(primaryColor))
        } else if (view is EditText) {
            view.setTextColor(onSurfaceColor)
            view.setHintTextColor(ThemeUtils.adjustAlpha(onSurfaceColor, 0.5f))
            view.textCursorDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setSize((2 * context.resources.displayMetrics.density).toInt(), 0)
                setColor(primaryColor)
            }
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findAndThemeInputs(view.getChildAt(i), theme, primaryColor, onSurfaceColor)
            }
        }
    }

    open fun setTitle(title: CharSequence?): StandardBottomSheet {
        titleView?.text = title
        titleView?.visibility = if (title.isNullOrEmpty()) View.GONE else View.VISIBLE
        return this
    }

    open fun setContent(view: View): StandardBottomSheet {
        contentContainer?.removeAllViews()
        contentContainer?.addView(view)
        return this
    }
    
    open fun setContent(layoutId: Int): View {
        contentContainer?.removeAllViews()
        return LayoutInflater.from(context).inflate(layoutId, contentContainer, true)
    }

    open fun setCancelable(cancelable: Boolean): StandardBottomSheet {
        dialog?.setCancelable(cancelable)
        return this
    }

    open fun setOnDismissListener(listener: () -> Unit): StandardBottomSheet {
        dialog?.setOnDismissListener { listener() }
        return this
    }

    fun isShowing(): Boolean = dialog?.isShowing == true

    fun show() {
        applyTheme(ThemeStore.currentTheme())
        dialog?.show()
    }

    fun dismiss() {
        dialog?.dismiss()
    }
}

/**
 * Bottom Sheet with a list of actions (icon + text).
 */
class ActionBottomSheet(context: Context) : StandardBottomSheet(context) {
    
    fun setActions(actions: List<SheetAction>): ActionBottomSheet {
        contentContainer?.removeAllViews()
        val theme = ThemeStore.currentTheme()
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)

        actions.forEach { action ->
            val itemView = LayoutInflater.from(context).inflate(R.layout.widget_action_item, contentContainer, false)
            val icon = itemView.findViewById<ImageView>(R.id.actionIcon)
            val text = itemView.findViewById<TextView>(R.id.actionText)
            
            icon.setImageResource(action.iconRes)
            icon.imageTintList = ColorStateList.valueOf(primColor)
            
            text.text = action.text
            text.setTextColor(if (action.isPrimary) primColor else txtColor)
            
            itemView.setOnClickListener {
                action.onClick()
                dismiss()
            }
            contentContainer?.addView(itemView)
        }
        return this
    }
}

/**
 * Bottom Sheet with a RecyclerView for list items.
 */
open class ListBottomSheet(context: Context, layoutId: Int = R.layout.widget_standard_bottom_sheet) : StandardBottomSheet(context, layoutId) {
    var recyclerView: RecyclerView? = null
        private set

    init {
        if (contentContainer != null) {
            recyclerView = RecyclerView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                layoutManager = LinearLayoutManager(context)
                clipToPadding = false
                setPadding(0, 0, 0, (16 * context.resources.displayMetrics.density).toInt())
            }
            setContent(recyclerView!!)
        } else {
            // Fallback for custom layouts that already have a recyclerView ID
            recyclerView = root?.findViewById(R.id.recyclerView)
            recyclerView?.layoutManager = LinearLayoutManager(context)
        }
    }

    open fun setAdapter(adapter: RecyclerView.Adapter<*>): ListBottomSheet {
        recyclerView?.adapter = adapter
        return this
    }

    override fun setTitle(title: CharSequence?): ListBottomSheet {
        super.setTitle(title)
        return this
    }

    override fun setContent(view: View): ListBottomSheet {
        super.setContent(view)
        return this
    }

    override fun setCancelable(cancelable: Boolean): ListBottomSheet {
        super.setCancelable(cancelable)
        return this
    }

    override fun setOnDismissListener(listener: () -> Unit): ListBottomSheet {
        super.setOnDismissListener(listener)
        return this
    }
}

/**
 * Bottom Sheet with Search, Extra Input, and RecyclerView.
 */
class SearchableListBottomSheet(context: Context) : ListBottomSheet(context, R.layout.widget_searchable_list_bottom_sheet) {
    val searchEditText: TextInputEditText? = root?.findViewById(R.id.searchEditText)
    val extraEditText: TextInputEditText? = root?.findViewById(R.id.extraEditText)
    val extraInputLayout: TextInputLayout? = root?.findViewById(R.id.extraInputLayout)
    val actionButton: com.google.android.material.button.MaterialButton? = root?.findViewById(R.id.actionButton)

    override fun applyTheme(theme: Theme) {
        super.applyTheme(theme)
        actionButton?.let { btn ->
            val primaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
            val onPrimaryColor = ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE)
            btn.setBackgroundColor(primaryColor)
            btn.setTextColor(onPrimaryColor)
        }
    }

    fun setActionButtonText(text: CharSequence): SearchableListBottomSheet {
        actionButton?.text = text
        return this
    }

    fun setActionButtonEnabled(enabled: Boolean): SearchableListBottomSheet {
        actionButton?.isEnabled = enabled
        return this
    }

    fun setExtraInputVisible(visible: Boolean, hint: String? = null): SearchableListBottomSheet {
        extraInputLayout?.isVisible = visible
        extraEditText?.hint = hint
        return this
    }

    private var searchWatcher: android.text.TextWatcher? = null

    fun onSearchTextChanged(listener: (String) -> Unit): SearchableListBottomSheet {
        searchEditText?.let { et ->
            searchWatcher?.let { et.removeTextChangedListener(it) }
            val watcher = object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    listener(s.toString())
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            }
            et.addTextChangedListener(watcher)
            searchWatcher = watcher
        }
        return this
    }

    fun onActionClick(listener: () -> Unit): SearchableListBottomSheet {
        actionButton?.setOnClickListener {
            listener()
        }
        return this
    }

    // Covariant overrides for chaining
    override fun setTitle(title: CharSequence?): SearchableListBottomSheet {
        super.setTitle(title)
        return this
    }

    override fun setContent(view: View): SearchableListBottomSheet {
        super.setContent(view)
        return this
    }

    override fun setCancelable(cancelable: Boolean): SearchableListBottomSheet {
        super.setCancelable(cancelable)
        return this
    }

    override fun setOnDismissListener(listener: () -> Unit): SearchableListBottomSheet {
        super.setOnDismissListener(listener)
        return this
    }
}
