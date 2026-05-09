package lavender.client.android.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.floatingactionbutton.FloatingActionButton

class LavenderFab @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.floatingActionButtonStyle
) : FloatingActionButton(context, attrs, defStyleAttr) {

    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                // Raise it higher as requested (32dp margin + system bottom bar)
                bottomMargin = (32 * resources.displayMetrics.density).toInt() + systemBars.bottom
                marginEnd = (16 * resources.displayMetrics.density).toInt()
            }
            insets
        }
    }
}
