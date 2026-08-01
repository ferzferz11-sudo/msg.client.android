package lavender.client.android.ui.sticker
import android.util.Log

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
    private var filteredBitmap: Bitmap? = null

    private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val cropOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)
        style = Paint.Style.FILL
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
    private var savedImageMatrix = Matrix()
    private var imageRect = RectF()
    private var viewRect = RectF()

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var draggingImage = false
    private var draggingText = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (editorMode == EditorMode.CROP) {
                val factor = detector.scaleFactor
                val focusX = detector.focusX
                val focusY = detector.focusY
                imageMatrix.postScale(factor, factor, focusX, focusY)
                constrainImage()
                invalidate()
                return true
            }
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

    fun setImageUri(uri: android.net.Uri) {
        try {
            // Subsample large images to avoid OOM
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            val maxDim = 2048
            var sampleSize = 1
            while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            if (bitmap != null) {
                originalBitmap?.recycle()
                originalBitmap = bitmap
                applyFilter(currentFilter)
                fitImageToView()
                invalidate()
            }
        } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }
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

        savedImageMatrix.set(imageMatrix)
        imageRect.set(dx, dy, dx + bmpW * scale, dy + bmpH * scale)
        viewRect.set(0f, 0f, viewW, viewH)
    }

    fun resetImagePosition() {
        imageMatrix.set(savedImageMatrix)
        invalidate()
    }

    fun applyCrop(): Boolean {
        val bmp = filteredBitmap ?: originalBitmap ?: return false
        if (width <= 0 || height <= 0) return false

        val cx = width / 2f
        val cy = height / 2f
        val cropSize = min(width, height) * 0.85f
        val half = cropSize / 2f
        val cropRect = RectF(cx - half, cy - half, cx + half, cy + half)

        val invMatrix = Matrix()
        if (!imageMatrix.invert(invMatrix)) return false
        val mappedRect = RectF()
        invMatrix.mapRect(mappedRect, cropRect)

        val left = mappedRect.left.toInt().coerceIn(0, bmp.width - 1)
        val top = mappedRect.top.toInt().coerceIn(0, bmp.height - 1)
        val right = mappedRect.right.toInt().coerceIn(left + 1, bmp.width)
        val bottom = mappedRect.bottom.toInt().coerceIn(top + 1, bmp.height)

        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return false

        val cropped = Bitmap.createBitmap(bmp, left, top, w, h)

        // Preserve filter state: crop both filtered and original bitmaps
        val hasFilter = filteredBitmap != null && filteredBitmap !== originalBitmap
        if (hasFilter) {
            filteredBitmap?.recycle()
            filteredBitmap = cropped
        }
        originalBitmap?.recycle()
        originalBitmap = cropped

        imageMatrix.reset()
        fitImageToView()
        invalidate()
        return true
    }

    private fun constrainImage() {
        val bmp = filteredBitmap ?: originalBitmap ?: return
        val values = FloatArray(9)
        imageMatrix.getValues(values)
        val scaleX = values[Matrix.MSCALE_X]
        val scaleY = values[Matrix.MSCALE_Y]

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        // Constrain scale
        val minScale = min(viewW / bmp.width, viewH / bmp.height) * 0.5f
        val maxScale = max(viewW / bmp.width, viewH / bmp.height) * 2f
        val currentScale = max(scaleX, scaleY)

        if (currentScale < minScale) {
            val factor = minScale / currentScale
            imageMatrix.postScale(factor, factor, viewW / 2f, viewH / 2f)
        } else if (currentScale > maxScale) {
            val factor = maxScale / currentScale
            imageMatrix.postScale(factor, factor, viewW / 2f, viewH / 2f)
        }

        // Constrain translation — image must cover the crop rectangle
        imageMatrix.getValues(values)
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]
        val bmpW = bmp.width * values[Matrix.MSCALE_X]
        val bmpH = bmp.height * values[Matrix.MSCALE_Y]

        val cx = viewW / 2f
        val cy = viewH / 2f
        val cropSize = min(viewW, viewH) * 0.85f
        val half = cropSize / 2f
        val cropLeft = cx - half
        val cropTop = cy - half
        val cropRight = cx + half
        val cropBottom = cy + half

        var dx = 0f
        var dy = 0f

        // Image left edge must be <= crop left
        if (transX > cropLeft) dx = cropLeft - transX
        // Image right edge must be >= crop right
        if (transX + bmpW < cropRight) dx = cropRight - (transX + bmpW)
        // Image top edge must be <= crop top
        if (transY > cropTop) dy = cropTop - transY
        // Image bottom edge must be >= crop bottom
        if (transY + bmpH < cropBottom) dy = cropBottom - (transY + bmpH)

        if (dx != 0f || dy != 0f) {
            imageMatrix.postTranslate(dx, dy)
        }
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
        canvas.drawLine(right, bottom, right, bottom - cornerLen, cornerPaint)
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
                draggingImage = false

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

                if (editorMode == EditorMode.CROP && !scaleDetector.isInProgress) {
                    draggingImage = true
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingImage && editorMode == EditorMode.CROP && !scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    imageMatrix.postTranslate(dx, dy)
                    constrainImage()
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
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
                draggingImage = false
                draggingText = false
            }
        }
        return super.onTouchEvent(event)
    }

    fun getCroppedBitmap(): Bitmap? {
        val bmp = filteredBitmap ?: originalBitmap ?: return null

        if (editorMode == EditorMode.CROP) {
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

            val w = right - left
            val h = bottom - top
            if (w <= 0 || h <= 0) return null

            val cropped = Bitmap.createBitmap(bmp, left, top, w, h)

            val outputSize = min(cropped.width, cropped.height)
            val output = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val srcLeft = (cropped.width - outputSize) / 2
            val srcTop = (cropped.height - outputSize) / 2
            val srcRect = Rect(srcLeft, srcTop, srcLeft + outputSize, srcTop + outputSize)
            canvas.drawBitmap(cropped, srcRect, RectF(0f, 0f, outputSize.toFloat(), outputSize.toFloat()), imagePaint)

            if (cropped !== bmp) cropped.recycle()

            drawTextOverlaysOnBitmap(output, cropOffsetX = left.toFloat(), cropOffsetY = top.toFloat())

            return output
        }

        // In TEXT/FILTER mode: return full bitmap with text overlays
        val output = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bmp, 0f, 0f, imagePaint)
        drawTextOverlaysOnBitmap(output)
        return output
    }

    private fun drawTextOverlaysOnBitmap(bitmap: Bitmap, cropOffsetX: Float = 0f, cropOffsetY: Float = 0f) {
        if (textOverlays.isEmpty()) return
        val canvas = Canvas(bitmap)
        val invMatrix = Matrix()
        if (!imageMatrix.invert(invMatrix)) return

        textOverlays.forEach { text ->
            if (text.text.isEmpty()) return@forEach
            text.textPaint.color = text.color

            val pts = floatArrayOf(text.x, text.y)
            invMatrix.mapPoints(pts)
            val px = pts[0] - cropOffsetX
            val py = pts[1] - cropOffsetY

            val scale = bitmap.width.toFloat() / (filteredBitmap ?: originalBitmap ?: return@forEach).width.toFloat()
            text.textPaint.textSize = text.fontSize * scale

            canvas.save()
            canvas.translate(px, py)
            canvas.scale(text.scaleX, text.scaleY)
            canvas.drawText(text.text, 0f, 0f, text.textPaint)
            canvas.restore()
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
        textOverlays.clear()
    }
}
