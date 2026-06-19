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
import android.widget.CompoundButton
import android.widget.CheckBox
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
 * Navigation stack for bottom sheets.
 */
object SheetNavigator {
    private val stack = mutableListOf<StandardBottomSheet>()
    
    fun push(sheet: StandardBottomSheet) {
        stack.lastOrNull()?.dismiss()
        stack.add(sheet)
    }
    
    fun pop(): StandardBottomSheet? {
        if (stack.isNotEmpty()) {
            val popped = stack.removeAt(stack.lastIndex)
            popped.dismiss()
            stack.lastOrNull()?.show()
        }
        return stack.lastOrNull()
    }
    
    fun clear() {
        stack.forEach { it.dismiss() }
        stack.clear()
    }
    
    fun current(): StandardBottomSheet? = stack.lastOrNull()
    
    fun size(): Int = stack.size
}

/**
 * Action Item for Bottom Sheets.
 */
data class SheetAction(
    val id: Int,
    val iconRes: Int,
    val text: CharSequence,
    val isPrimary: Boolean = false,
    val badge: Int = 0, // 0 = no badge, >0 = show badge with number
    val onClick: () -> Unit
)

/**
 * Standard Bottom Sheet widget.
 */
open class StandardBottomSheet(
    val context: Context, 
    layoutId: Int = R.layout.widget_standard_bottom_sheet,
    theme: Theme = ThemeStore.currentTheme()
) : ThemedWidget {
    var dialog: BottomSheetDialog? = null
        protected set
    protected var root: View? = null
    protected var dragHandle: View? = null
    protected var titleView: TextView? = null
    protected var contentContainer: LinearLayout? = null
    protected var backButton: ImageView? = null
    protected var hasBackStack = false

    init {
        initViews(layoutId)
        applyTheme(theme)
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
        backButton = view.findViewById(R.id.backButton)
        
        backButton?.setOnClickListener {
            SheetNavigator.pop()
        }
    }

    override fun applyTheme(theme: Theme) {
        dialog?.let { ThemeApplier.applyToDialog(it, theme) }
        
        try {
            val bgColor = ThemeUtils.parseSafeColor(theme.backgroundColor, Color.BLACK)
            val primaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
            val onSurfaceColor = ThemeUtils.parseSafeColor(theme.onSurfaceColor, Color.WHITE)
            val textPrimaryColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
            
            root?.setBackgroundColor(bgColor)
            dragHandle?.backgroundTintList = ColorStateList.valueOf(primaryColor)
            titleView?.setTextColor(primaryColor)

            // Theme any InputLayouts and EditTexts
            root?.let { findAndThemeInputs(it, theme, primaryColor, onSurfaceColor, textPrimaryColor) }
        } catch (_: Exception) {}
    }

    protected fun findAndThemeInputs(view: View, theme: Theme, primaryColor: Int, onSurfaceColor: Int, textPrimaryColor: Int) {
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
            // Fix deprecation: use endIconTintList for end icons including password toggle
            if (view.endIconMode == TextInputLayout.END_ICON_PASSWORD_TOGGLE) {
                view.setEndIconTintList(ColorStateList.valueOf(ThemeUtils.adjustAlpha(onSurfaceColor, 0.6f)))
            }
        } else if (view is EditText) {
            view.setTextColor(textPrimaryColor)
            view.setHintTextColor(ThemeUtils.adjustAlpha(textPrimaryColor, 0.5f))
            view.highlightColor = ThemeUtils.adjustAlpha(primaryColor, 0.3f)
            
            // Apply cursor color
            view.textCursorDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setSize((2 * context.resources.displayMetrics.density).toInt(), 0)
                setColor(primaryColor)
            }
        } else if (view is android.widget.CompoundButton) {
            // CheckBox, Switch, Toggle — themed text color + tint
            view.buttonTintList = ColorStateList.valueOf(primaryColor)
            view.setTextColor(textPrimaryColor)
        } else if (view is android.widget.Button) {
            view.isAllCaps = false
            view.transformationMethod = null // Crucial to disable Caps
            
            // Force consistent height for all buttons in widgets
            view.minimumHeight = (56 * context.resources.displayMetrics.density).toInt()
            
            val isCancelType = view.id == R.id.btnCancel || view.id == R.id.btnClose || 
                             view.id == R.id.btnReset || view.id == android.R.id.button2 ||
                             view.id == R.id.deleteGroupButton
            
            val isActionType = view.id == R.id.btnJoin || view.id == R.id.btnRegister || 
                              view.id == R.id.btnLogin || view.id == R.id.btnSave ||
                              view.id == R.id.actionButton || view.id == R.id.btnUpdate ||
                              view.id == R.id.btnSend || view.id == R.id.changeAvatarButton
            
            // Set consistent corner radius
            if (view is com.google.android.material.button.MaterialButton) {
                view.cornerRadius = (28 * context.resources.displayMetrics.density).toInt()
            }

            if (isCancelType) {
                view.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                if (view is com.google.android.material.button.MaterialButton) {
                    view.strokeColor = ColorStateList.valueOf(primaryColor)
                    view.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
                    view.rippleColor = ColorStateList.valueOf(ThemeUtils.adjustAlpha(primaryColor, 0.1f))
                }
                view.setTextColor(primaryColor)
            } else {
                // Main action buttons: 30% transparency = 70% opacity (0.7f)
                val alpha = if (isActionType) 0.7f else 1.0f
                view.backgroundTintList = ColorStateList.valueOf(ThemeUtils.adjustAlpha(primaryColor, alpha))
                val onP = ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE)
                view.setTextColor(onP)
                if (view is com.google.android.material.button.MaterialButton) {
                    view.rippleColor = ColorStateList.valueOf(ThemeUtils.adjustAlpha(onP, 0.2f))
                    view.strokeWidth = 0
                }
            }
        } else if (view is android.widget.Spinner) {
            if (view is androidx.appcompat.widget.AppCompatSpinner) {
                view.setPopupBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(
                        primaryColor
                    )
                )
                view.setBackgroundColor(primaryColor)
            }
        } else if (view is android.widget.ProgressBar) {
            view.indeterminateTintList = ColorStateList.valueOf(primaryColor)
        } else if (view is ImageView && view !is de.hdodenhof.circleimageview.CircleImageView) {
            view.imageTintList = ColorStateList.valueOf(primaryColor)
        } else if (view is TextView) {
            if (view.id == R.id.forgotPasswordButton || view.id == R.id.actionShareHeader) {
                view.setTextColor(primaryColor)
            } else if (view.id != R.id.titleText) {
                view.setTextColor(textPrimaryColor)
            }
        }
        
        // Always recurse if it's a ViewGroup, even if it matched one of the above (like TextInputLayout)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findAndThemeInputs(view.getChildAt(i), theme, primaryColor, onSurfaceColor, textPrimaryColor)
            }
        }
    }

    fun <T : View> findViewById(id: Int): T? = root?.findViewById(id)

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
        updateBackButton()
        dialog?.apply {
            @Suppress("DEPRECATION")
            window?.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            )
            @Suppress("DEPRECATION")
            behavior.peekHeight = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        }
        dialog?.show()
    }

    fun showWithNavigation() {
        SheetNavigator.push(this)
        show()
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    private fun updateBackButton() {
        val hasBack = SheetNavigator.size() > 1
        backButton?.visibility = if (hasBack) View.VISIBLE else View.GONE
        if (hasBack) {
            val primaryColor = ThemeUtils.parseSafeColor(ThemeStore.currentTheme().primaryColor, Color.BLUE)
            backButton?.imageTintList = ColorStateList.valueOf(primaryColor)
        }
    }
}

/**
 * Bottom Sheet for showing loading state.
 */
class LoadingBottomSheet(context: Context, theme: Theme = ThemeStore.currentTheme()) : StandardBottomSheet(context, R.layout.dialog_loading, theme) {
    fun setMessage(text: CharSequence?): LoadingBottomSheet {
        root?.findViewById<TextView>(R.id.loading_text)?.text = text
        return this
    }
    
    init {
        setCancelable(false)
    }
}

/**
 * Bottom Sheet with a list of actions (icon + text).
 */
class ActionBottomSheet(context: Context, theme: Theme = ThemeStore.currentTheme()) : StandardBottomSheet(context, theme = theme) {
    
    fun setActions(actions: List<SheetAction>): ActionBottomSheet {
        contentContainer?.removeAllViews()
        val theme = ThemeStore.currentTheme()
        val txtColor = ThemeUtils.parseSafeColor(theme.textPrimaryColor, Color.WHITE)
        val primColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)

        actions.forEach { action ->
            val itemView = LayoutInflater.from(context).inflate(R.layout.widget_action_item, contentContainer, false)
            val icon = itemView.findViewById<ImageView>(R.id.actionIcon)
            val text = itemView.findViewById<TextView>(R.id.actionText)
            val badge = itemView.findViewById<TextView>(R.id.actionBadge)

            icon.setImageResource(action.iconRes)
            icon.imageTintList = ColorStateList.valueOf(primColor)

            text.text = action.text
            text.setTextColor(if (action.isPrimary) primColor else txtColor)

            // Show badge if count > 0
            if (action.badge > 0) {
                badge.text = if (action.badge > 99) "99+" else action.badge.toString()
                badge.visibility = View.VISIBLE
            } else {
                badge.visibility = View.GONE
            }

            itemView.setOnClickListener {
                action.onClick()
                dismiss()
            }
            contentContainer?.addView(itemView)
        }
        return this
    }

    override fun setTitle(title: CharSequence?): ActionBottomSheet {
        super.setTitle(title)
        return this
    }

    override fun setContent(view: View): ActionBottomSheet {
        super.setContent(view)
        return this
    }

    override fun setCancelable(cancelable: Boolean): ActionBottomSheet {
        super.setCancelable(cancelable)
        return this
    }

    override fun setOnDismissListener(listener: () -> Unit): ActionBottomSheet {
        super.setOnDismissListener(listener)
        return this
    }
}

/**
 * Bottom Sheet with a RecyclerView for list items.
 */
open class ListBottomSheet(
    context: Context, 
    layoutId: Int = R.layout.widget_standard_bottom_sheet,
    theme: Theme = ThemeStore.currentTheme()
) : StandardBottomSheet(context, layoutId, theme) {
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
        dialog?.setOnDismissListener { listener() }
        return this
    }
}

/**
 * Bottom Sheet with Search, Extra Input, and RecyclerView.
 */
class SearchableListBottomSheet(context: Context, theme: Theme = ThemeStore.currentTheme()) : 
    ListBottomSheet(context, R.layout.widget_searchable_list_bottom_sheet, theme) {
    val searchEditText: TextInputEditText? = root?.findViewById(R.id.searchEditText)
    val extraEditText: TextInputEditText? = root?.findViewById(R.id.extraEditText)
    val extraInputLayout: TextInputLayout? = root?.findViewById(R.id.extraInputLayout)
    val actionButton: com.google.android.material.button.MaterialButton? = root?.findViewById(R.id.actionButton)
    val progressBar: android.widget.ProgressBar? = root?.findViewById(R.id.progressBar)
    val createChatCheckbox: android.widget.CheckBox? = root?.findViewById(R.id.createChatCheckbox)
    val emptyStateText: android.widget.TextView? = root?.findViewById(R.id.emptyStateText)

    override fun applyTheme(theme: Theme) {
        super.applyTheme(theme)
        val primaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, Color.BLUE)
        actionButton?.let { btn ->
            val onPrimaryColor = ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE)
            btn.setBackgroundColor(primaryColor)
            btn.setTextColor(onPrimaryColor)
        }
        progressBar?.indeterminateTintList = ColorStateList.valueOf(primaryColor)
    }

    fun setLoading(loading: Boolean): SearchableListBottomSheet {
        progressBar?.isVisible = loading
        recyclerView?.isVisible = !loading
        if (loading) emptyStateText?.isVisible = false
        return this
    }

    fun setEmptyState(visible: Boolean, message: String? = null): SearchableListBottomSheet {
        emptyStateText?.isVisible = visible
        if (message != null) emptyStateText?.text = message
        recyclerView?.isVisible = !visible
        return this
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

    fun setCreateChatCheckboxVisible(visible: Boolean, text: String? = null): SearchableListBottomSheet {
        createChatCheckbox?.isVisible = visible
        if (text != null) createChatCheckbox?.text = text
        createChatCheckbox?.isChecked = true
        return this
    }

    fun isCreateChatChecked(): Boolean = createChatCheckbox?.isChecked == true

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
