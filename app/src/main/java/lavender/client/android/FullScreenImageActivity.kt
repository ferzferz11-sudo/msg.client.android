package lavender.client.android

import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import kotlin.math.abs

class FullScreenImageActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var btnClose: ImageButton
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    
    private var imageUrls: List<String> = emptyList()
    private var currentIndex: Int = 0
    
    // Zoom state
    private var currentScale = 1f
    private val MIN_SCALE = 1f
    private val MAX_SCALE = 5f
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure black background and dark status bars
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_full_screen_image)

        imageView = findViewById(R.id.fullScreenImageView)
        btnClose = findViewById(R.id.btnClose)
        
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
            // Reset zoom when loading new image
            currentScale = MIN_SCALE
            imageView.scaleX = currentScale
            imageView.scaleY = currentScale
            
            Glide.with(this)
                .load(imageUrl)
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
        return if (event != null) {
            scaleGestureDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
        } else {
            super.onTouchEvent(event)
        }
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
            if (e1 == null) return false
            
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
            currentScale *= detector.scaleFactor
            currentScale = currentScale.coerceIn(MIN_SCALE, MAX_SCALE)
            imageView.scaleX = currentScale
            imageView.scaleY = currentScale
            return true
        }
        
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            // Optional: add animation to snap back to min scale if user zoomed out too much
            if (currentScale < MIN_SCALE) {
                currentScale = MIN_SCALE
                imageView.animate()
                    .scaleX(currentScale)
                    .scaleY(currentScale)
                    .setDuration(200)
                    .start()
            }
        }
    }
}
