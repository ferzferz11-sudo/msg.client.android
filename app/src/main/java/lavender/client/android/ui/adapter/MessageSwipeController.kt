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
    private val onSwipe: (Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

    private val replyIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_reply_swipe)
    private val paint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.lavender_mist)
        isAntiAlias = true
    }
    
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        onSwipe(position)
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

            if (dX > 0) {
                val iconMargin = (height - 24f * recyclerView.resources.displayMetrics.density) / 2
                val iconTop = itemView.top + iconMargin
                val iconBottom = itemView.bottom - iconMargin
                val iconLeft = itemView.left + 16f * recyclerView.resources.displayMetrics.density
                val iconRight = iconLeft + 24f * recyclerView.resources.displayMetrics.density

                replyIcon?.setBounds(iconLeft.toInt(), iconTop.toInt(), iconRight.toInt(), iconBottom.toInt())
                
                // Draw circle background
                val circleRadius = 18f * recyclerView.resources.displayMetrics.density
                val circleX = (iconLeft + iconRight) / 2
                val circleY = (iconTop + iconBottom) / 2
                
                val alpha = (dX / 100f).coerceIn(0f, 1f)
                paint.alpha = (alpha * 255).toInt()
                c.drawCircle(circleX, circleY, circleRadius, paint)
                
                replyIcon?.alpha = (alpha * 255).toInt()
                replyIcon?.draw(c)
            }
        }
        
        // Limit max swipe distance
        val translationX = dX.coerceAtMost(150f * recyclerView.resources.displayMetrics.density)
        super.onChildDraw(c, recyclerView, viewHolder, translationX, dY, actionState, isCurrentlyActive)
    }
}
