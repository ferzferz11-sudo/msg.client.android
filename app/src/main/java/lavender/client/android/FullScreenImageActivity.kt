package lavender.client.android

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import kotlin.math.abs

class FullScreenImageActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var btnClose: ImageButton
    private lateinit var loadingProgress: ProgressBar
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private var imageUrls: List<String> = emptyList()
    private var currentIndex: Int = 0

    // Zoom state
    private var currentScale = 1f
    private val MIN_SCALE = 1f
    private val MAX_SCALE = 5f

    // Pan state
    private var lastX = 0f
    private var lastY = 0f
    private var offsetX = 0f
    private var offsetY = 0f

    // Flags to prevent jump after pinch
    private var isScaling = false
    private var wasScaling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Включаем Edge-to-Edge до super.onCreate. Это делает бары прозрачными
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_image)

        // Настраиваем контраст иконок (белые иконки на черном фоне)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        imageView = findViewById(R.id.fullScreenImageView)
        btnClose = findViewById(R.id.btnClose)
        loadingProgress = findViewById(R.id.loadingProgress)

        // For image viewer, we use classic deep dark background for better focus
        val bgColor = Color.BLACK
        val primaryColor = Color.WHITE
        val iconColor = Color.WHITE
        
        findViewById<View>(android.R.id.content).setBackgroundColor(bgColor)
        loadingProgress.indeterminateTintList = ColorStateList.valueOf(primaryColor)
        btnClose.imageTintList = ColorStateList.valueOf(iconColor)

        // Handle Window Insets for the close button (fix overlap with status bar)
        ViewCompat.setOnApplyWindowInsetsListener(btnClose) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val lp = view.layoutParams as ViewGroup.MarginLayoutParams
            // Margin from top = status bar height + 16dp
            lp.topMargin = systemBars.top + (16 * resources.displayMetrics.density).toInt()
            view.layoutParams = lp
            insets
        }

        // Get data from intent
        val imageUrl = intent.getStringExtra("image_url") ?: ""
        val imageUrlsList = intent.getStringArrayListExtra("image_urls")
        val currentIdx = intent.getIntExtra("current_index", 0)

        // Initialize gesture detector for swipe
        gestureDetector = GestureDetector(this, SwipeGestureListener())

        // Initialize scale gesture detector for pinch-to-zoom
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())

        // Load images
        if (imageUrlsList != null && imageUrlsList.isNotEmpty()) {
            imageUrls = imageUrlsList
            currentIndex = if (currentIdx >= 0 && currentIdx < imageUrlsList.size) currentIdx else 0
        } else {
            // Fallback: just show the single image
            imageUrls = listOf(imageUrl)
            currentIndex = 0
        }

        loadImage(imageUrls[currentIndex])

        btnClose.setOnClickListener {
            finish()
        }
    }

    private fun loadImage(imageUrl: String) {
        if (imageUrl.isNotEmpty()) {
            val finalUrl = if (imageUrl.startsWith("http")) imageUrl.trim() 
                          else "http://159.195.38.145:8082" + imageUrl.trim().let { if (it.startsWith("/")) it else "/$it" }

            // Reset zoom and pan when loading new image
            currentScale = MIN_SCALE
            offsetX = 0f
            offsetY = 0f
            imageView.scaleX = currentScale
            imageView.scaleY = currentScale
            imageView.translationX = offsetX
            imageView.translationY = offsetY

            loadingProgress.isVisible = true
            
            val theme = ThemeStore.currentTheme()
            val progressDrawable = androidx.swiperefreshlayout.widget.CircularProgressDrawable(this).apply {
                strokeWidth = 5f
                centerRadius = 40f
                val pColor = ThemeUtils.parseSafeColor(theme.primaryColor, android.graphics.Color.WHITE)
                setColorSchemeColors(pColor)
                start()
            }

            Glide.with(this)
                .load(finalUrl)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .placeholder(progressDrawable)
                .centerInside()
                .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean): Boolean {
                        loadingProgress.isVisible = false
                        return false
                    }
                    override fun onResourceReady(resource: android.graphics.drawable.Drawable, model: Any, target: Target<android.graphics.drawable.Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        loadingProgress.isVisible = false
                        return false
                    }
                })
                .into(imageView)
        }
    }

    private fun showNextImage() {
        if (imageUrls.size > 1) {
            currentIndex = (currentIndex + 1) % imageUrls.size
            loadImage(imageUrls[currentIndex])
        }
    }

    private fun showPreviousImage() {
        if (imageUrls.size > 1) {
            currentIndex = if (currentIndex - 1 < 0) imageUrls.size - 1 else currentIndex - 1
            loadImage(imageUrls[currentIndex])
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return super.onTouchEvent(event)

        // Always feed all events to both detectors
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Fresh touch — anchor drag position
                lastX = event.x
                lastY = event.y
                wasScaling = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second finger arrived — mark scaling started
                isScaling = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isScaling) {
                    // During pinch: update the focal-point-based translation so the
                    // image stays under the fingers, then keep lastX/lastY in sync
                    // with the primary pointer so there is no jump when fingers lift.
                    lastX = event.getX(0)
                    lastY = event.getY(0)
                } else if (!wasScaling && currentScale > MIN_SCALE) {
                    // Single-finger drag — only when we were NOT just scaling
                    val deltaX = event.x - lastX
                    val deltaY = event.y - lastY
                    offsetX += deltaX
                    offsetY += deltaY
                    imageView.translationX = offsetX
                    imageView.translationY = offsetY
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // One finger lifted during pinch — re-anchor to the remaining finger
                // so the next ACTION_MOVE doesn't produce a jump
                val remainingIndex = if (event.actionIndex == 0) 1 else 0
                lastX = event.getX(remainingIndex)
                lastY = event.getY(remainingIndex)
                wasScaling = true
                isScaling = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isScaling = false
                wasScaling = false
            }
        }

        return true
    }

    private inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null || currentScale > MIN_SCALE) return false

            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            return if (abs(diffX) > abs(diffY)) {
                // Horizontal swipe
                if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        // Swipe right - show previous image
                        showPreviousImage()
                    } else {
                        // Swipe left - show next image
                        showNextImage()
                    }
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.OnScaleGestureListener {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val prevScale = currentScale
            currentScale = (currentScale * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)

            // The ImageView's scale pivot is its centre (width/2, height/2).
            // detector.focusX/Y are in window coordinates.
            // We need the focal point expressed relative to the view centre
            // (which is the same coordinate space as translationX/Y).
            val viewCenterX = imageView.width / 2f
            val viewCenterY = imageView.height / 2f
            // focal point relative to view centre
            val focalRelX = detector.focusX - viewCenterX
            val focalRelY = detector.focusY - viewCenterY

            val scaleDiff = currentScale / prevScale
            // Scale the current offset around the focal point
            offsetX = focalRelX + (offsetX - focalRelX) * scaleDiff
            offsetY = focalRelY + (offsetY - focalRelY) * scaleDiff

            imageView.scaleX = currentScale
            imageView.scaleY = currentScale
            imageView.translationX = offsetX
            imageView.translationY = offsetY
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            wasScaling = true
            if (currentScale <= MIN_SCALE) {
                currentScale = MIN_SCALE
                offsetX = 0f
                offsetY = 0f
                imageView.animate()
                    .scaleX(currentScale)
                    .scaleY(currentScale)
                    .translationX(offsetX)
                    .translationY(offsetY)
                    .setDuration(200)
                    .start()
            }
        }
    }
}
