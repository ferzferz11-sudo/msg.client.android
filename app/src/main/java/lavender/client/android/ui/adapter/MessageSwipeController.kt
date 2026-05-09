package lavender.client.android.ui.adapter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import lavender.client.android.R

class MessageSwipeController(
    context: Context,
    private val onSwipe: (Int, Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val replyIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_reply_swipe)
    private val backIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_back_arrow)
    private val paint = Paint().apply {
        try {
            val theme = lavender.client.android.theme.ThemeStore.currentTheme()
            color = android.graphics.Color.parseColor(theme.primaryColor)
        } catch (_: Exception) {
            color = ContextCompat.getColor(context, R.color.lavender_mist)
        }
        isAntiAlias = true
    }
    
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        onSwipe(position, direction)
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.3f

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val itemView = viewHolder.itemView
            val height = itemView.bottom.toFloat() - itemView.top.toFloat()
            val density = recyclerView.resources.displayMetrics.density

            if (dX < 0) { // Swiping LEFT to reply
                val iconSize = 24f * density
                val iconMargin = (height - iconSize) / 2
                val iconTop = itemView.top + iconMargin
                val iconBottom = itemView.bottom - iconMargin
                val iconRight = itemView.right - 16f * density
                val iconLeft = iconRight - iconSize

                replyIcon?.setBounds(iconLeft.toInt(), iconTop.toInt(), iconRight.toInt(), iconBottom.toInt())
                
                val circleRadius = 18f * density
                val circleX = (iconLeft + iconRight) / 2
                val circleY = (iconTop + iconBottom) / 2
                
                val alpha = (Math.abs(dX) / 100f).coerceIn(0f, 1f)
                paint.alpha = (alpha * 255).toInt()
                c.drawCircle(circleX, circleY, circleRadius, paint)
                
                replyIcon?.alpha = (alpha * 255).toInt()
                replyIcon?.draw(c)
            } else if (dX > 0) { // Swiping RIGHT to go back
                val iconSize = 24f * density
                val iconMargin = (height - iconSize) / 2
                val iconTop = itemView.top + iconMargin
                val iconBottom = itemView.bottom - iconMargin
                val iconLeft = itemView.left + 16f * density
                val iconRight = iconLeft + iconSize

                backIcon?.setBounds(iconLeft.toInt(), iconTop.toInt(), iconRight.toInt(), iconBottom.toInt())
                
                val circleRadius = 18f * density
                val circleX = (iconLeft + iconRight) / 2
                val circleY = (iconTop + iconBottom) / 2
                
                val alpha = (dX / 100f).coerceIn(0f, 1f)
                paint.alpha = (alpha * 255).toInt()
                c.drawCircle(circleX, circleY, circleRadius, paint)
                
                backIcon?.alpha = (alpha * 255).toInt()
                backIcon?.draw(c)
            }
        }
        
        // Limit max swipe distance and disable shift for RIGHT swipe
        val translationX = if (dX > 0) {
            0f
        } else {
            dX.coerceAtLeast(-150f * recyclerView.resources.displayMetrics.density)
        }
        super.onChildDraw(c, recyclerView, viewHolder, translationX, dY, actionState, isCurrentlyActive)
    }
}
