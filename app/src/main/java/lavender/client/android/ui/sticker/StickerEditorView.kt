package lavender.client.android.ui.sticker

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class StickerEditorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    enum class EditorMode { CROP, TEXT, FILTER }

    data class TextOverlay(
        var text: String = "",
        var x: Float = 0f,
        var y: Float = 0f,
        var fontSize: Float = 48f,
        var color: Int = Color.WHITE,
        var scaleX: Float = 1f,
        var scaleY: Float = 1f
    ) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }
        var textWidth = 0f
        var textHeight = 0f

        fun measure() {
            textPaint.textSize = fontSize
            textWidth = textPaint.measureText(text)
            val fm = textPaint.fontMetrics
            textHeight = fm.descent - fm.ascent
        }
    }

    private var originalBitmap: Bitmap? = null
    private var displayBitmap: Bitmap? = null
    private var filteredBitmap: Bitmap? = null

    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val cropOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val cropBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textOverlays = mutableListOf<TextOverlay>()

    var editorMode = EditorMode.CROP
        set(value) {
            field = value
            invalidate()
        }

    var currentFilter: FilterType = FilterType.ORIGINAL
        set(value) {
            field = value
            applyFilter(value)
            invalidate()
        }

    var activeTextOverlay: TextOverlay? = null
        private set

    private var imageMatrix = Matrix()
    private var imageRect = RectF()
    private var viewRect = RectF()

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            activeTextOverlay?.let { text ->
                val factor = detector.scaleFactor
                text.fontSize = max(16f, min(120f, text.fontSize * factor))
                text.measure()
                invalidate()
                return true
            }
            return false
        }
    })

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var draggingText = false

    fun setImageUri(uri: android.net.Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap != null) {
                originalBitmap?.recycle()
                originalBitmap = bitmap
                applyFilter(currentFilter)
                fitImageToView()
                invalidate()
            }
        } catch (_: Exception) {}
    }

    fun setImageBitmap(bitmap: Bitmap) {
        originalBitmap?.recycle()
        originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        applyFilter(currentFilter)
        fitImageToView()
        invalidate()
    }

    private fun fitImageToView() {
        val bmp = filteredBitmap ?: originalBitmap ?: return
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        val viewW = width.toFloat().coerceAtLeast(1f)
        val viewH = height.toFloat().coerceAtLeast(1f)

        val scale = min(viewW / bmpW, viewH / bmpH)
        val dx = (viewW - bmpW * scale) / 2f
        val dy = (viewH - bmpH * scale) / 2f

        imageMatrix.reset()
        imageMatrix.postScale(scale, scale)
        imageMatrix.postTranslate(dx, dy)

        imageRect.set(dx, dy, dx + bmpW * scale, dy + bmpH * scale)
        viewRect.set(0f, 0f, viewW, viewH)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitImageToView()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = filteredBitmap ?: originalBitmap ?: return

        canvas.save()
        canvas.drawBitmap(bmp, imageMatrix, imagePaint)
        canvas.restore()

        if (editorMode == EditorMode.CROP) {
            drawCropOverlay(canvas)
        }

        textOverlays.forEach { drawTextOverlay(canvas, it) }
    }

    private fun drawCropOverlay(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val cropSize = min(width, height) * 0.85f
        val half = cropSize / 2f
        val left = cx - half
        val top = cy - half
        val right = cx + half
        val bottom = cy + half

        canvas.drawRect(0f, 0f, width.toFloat(), top, cropOverlayPaint)
        canvas.drawRect(0f, bottom, width.toFloat(), height.toFloat(), cropOverlayPaint)
        canvas.drawRect(0f, top, left, bottom, cropOverlayPaint)
        canvas.drawRect(right, top, width.toFloat(), bottom, cropOverlayPaint)

        val cornerLen = 24f
        val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(left, top + cornerLen, left, top, cornerPaint)
        canvas.drawLine(left, top, left + cornerLen, top, cornerPaint)

        canvas.drawLine(right - cornerLen, top, right, top, cornerPaint)
        canvas.drawLine(right, top, right, top + cornerLen, cornerPaint)

        canvas.drawLine(left, bottom - cornerLen, left, bottom, cornerPaint)
        canvas.drawLine(left, bottom, left + cornerLen, bottom, cornerPaint)

        canvas.drawLine(right - cornerLen, bottom, right, bottom, cornerPaint)
        canvas.drawLine(right, bottom - cornerLen, right, bottom, cornerPaint)
    }

    private fun drawTextOverlay(canvas: Canvas, text: TextOverlay) {
        if (text.text.isEmpty()) return
        text.textPaint.color = text.color
        text.textPaint.textSize = text.fontSize

        canvas.save()
        canvas.translate(text.x, text.y)
        canvas.scale(text.scaleX, text.scaleY)
        canvas.drawText(text.text, 0f, 0f, text.textPaint)
        canvas.restore()
    }

    fun addTextOverlay(text: String, color: Int = Color.WHITE): TextOverlay {
        val overlay = TextOverlay(
            text = text,
            x = width / 2f,
            y = height / 2f,
            fontSize = 48f,
            color = color
        )
        overlay.measure()
        textOverlays.add(overlay)
        activeTextOverlay = overlay
        invalidate()
        return overlay
    }

    fun removeTextOverlay(overlay: TextOverlay) {
        textOverlays.remove(overlay)
        if (activeTextOverlay == overlay) activeTextOverlay = textOverlays.lastOrNull()
        invalidate()
    }

    fun clearTextOverlays() {
        textOverlays.clear()
        activeTextOverlay = null
        invalidate()
    }

    fun setTextColor(color: Int) {
        activeTextOverlay?.let {
            it.color = color
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                draggingText = false

                if (editorMode == EditorMode.TEXT) {
                    for (i in textOverlays.indices.reversed()) {
                        val t = textOverlays[i]
                        val halfW = t.textWidth * t.scaleX / 2f + 20f
                        val halfH = t.textHeight * t.scaleY / 2f + 10f
                        if (event.x in (t.x - halfW)..(t.x + halfW) &&
                            event.y in (t.y - halfH)..(t.y + halfH)) {
                            activeTextOverlay = t
                            draggingText = true
                            invalidate()
                            return true
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingText && activeTextOverlay != null && !scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    activeTextOverlay!!.x += dx
                    activeTextOverlay!!.y += dy
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                draggingText = false
            }
        }
        return super.onTouchEvent(event)
    }

    fun getCroppedBitmap(): Bitmap? {
        val bmp = filteredBitmap ?: originalBitmap ?: return null
        val cx = width / 2f
        val cy = height / 2f
        val cropSize = min(width, height) * 0.85f
        val half = cropSize / 2f

        val cropRect = RectF(cx - half, cy - half, cx + half, cy + half)

        val invMatrix = Matrix()
        imageMatrix.invert(invMatrix)
        val mappedRect = RectF()
        invMatrix.mapRect(mappedRect, cropRect)

        val left = mappedRect.left.toInt().coerceIn(0, bmp.width - 1)
        val top = mappedRect.top.toInt().coerceIn(0, bmp.height - 1)
        val right = mappedRect.right.toInt().coerceIn(left + 1, bmp.width)
        val bottom = mappedRect.bottom.toInt().coerceIn(top + 1, bmp.height)

        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return null

        val cropped = Bitmap.createBitmap(bmp, left, top, width, height)

        val outputSize = min(cropped.width, cropped.height)
        val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val srcLeft = (cropped.width - outputSize) / 2
        val srcTop = (cropped.height - outputSize) / 2
        val srcRect = Rect(srcLeft, srcTop, srcLeft + outputSize, srcTop + outputSize)
        canvas.drawBitmap(cropped, srcRect, RectF(0f, 0f, outputSize.toFloat(), outputSize.toFloat()), imagePaint)

        if (cropped !== bmp) cropped.recycle()

        drawTextOverlaysOnBitmap(output)

        return output
    }

    private fun drawTextOverlaysOnBitmap(bitmap: Bitmap) {
        if (textOverlays.isEmpty()) return
        val canvas = Canvas(bitmap)
        val scale = bitmap.width.toFloat() / width.toFloat()

        textOverlays.forEach { text ->
            if (text.text.isEmpty()) return@forEach
            text.textPaint.color = text.color
            text.textPaint.textSize = text.fontSize * scale
            val px = text.x * scale
            val py = text.y * scale
            canvas.drawText(text.text, px, py, text.textPaint)
        }
    }

    enum class FilterType { ORIGINAL, GRAYSCALE, SEPIA, WARM, COOL, BRIGHTNESS }

    private fun applyFilter(type: FilterType) {
        val bmp = originalBitmap ?: return
        filteredBitmap?.let { if (it !== bmp) it.recycle() }
        filteredBitmap = when (type) {
            FilterType.ORIGINAL -> null
            else -> applyColorFilter(bmp, type)
        }
    }

    private fun applyColorFilter(src: Bitmap, type: FilterType): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, src.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        val colorMatrix = when (type) {
            FilterType.GRAYSCALE -> ColorMatrix().apply { setSaturation(0f) }
            FilterType.SEPIA -> ColorMatrix().apply {
                setSaturation(0f)
                val sepia = ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, 30f,
                    0f, 1.0f, 0f, 0f, 15f,
                    0f, 0f, 0.8f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                postConcat(sepia)
            }
            FilterType.WARM -> ColorMatrix(floatArrayOf(
                1.1f, 0f, 0f, 0f, 20f,
                0f, 1.0f, 0f, 0f, 10f,
                0f, 0f, 0.9f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            FilterType.COOL -> ColorMatrix(floatArrayOf(
                0.9f, 0f, 0f, 0f, 0f,
                0f, 1.0f, 0f, 0f, 5f,
                0f, 0f, 1.1f, 0f, 20f,
                0f, 0f, 0f, 1f, 0f
            ))
            FilterType.BRIGHTNESS -> ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, 30f,
                0f, 1f, 0f, 0f, 30f,
                0f, 0f, 1f, 0f, 30f,
                0f, 0f, 0f, 1f, 0f
            ))
            else -> ColorMatrix()
        }

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    fun destroy() {
        originalBitmap?.recycle()
        originalBitmap = null
        filteredBitmap?.recycle()
        filteredBitmap = null
        displayBitmap = null
        textOverlays.clear()
    }
}
