package lavender.client.android

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lavender.client.android.theme.ThemeStore
import lavender.client.android.theme.ThemeUtils
import kotlin.math.abs
import android.content.ContentValues

class FullScreenImageActivity : AppCompatActivity() {
    private lateinit var imageView: ImageView
    private lateinit var btnClose: ImageButton
    private lateinit var btnDownload: ImageButton
    private lateinit var loadingProgress: ProgressBar
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var thumbnailsRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var tvImageCounter: android.widget.TextView
    private lateinit var bottomBar: android.view.View

    private var imageUrls: List<String> = emptyList()
    private var currentIndex: Int = 0
    private var thumbnailAdapter: ThumbnailAdapter? = null

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
        btnDownload = findViewById(R.id.btnDownload)
        loadingProgress = findViewById(R.id.loadingProgress)

        // For image viewer, we use classic deep dark background for better focus
        val bgColor = android.graphics.Color.BLACK
        val primaryColor = android.graphics.Color.WHITE
        val iconColor = android.graphics.Color.WHITE
        
        findViewById<View>(android.R.id.content).setBackgroundColor(bgColor)
        loadingProgress.indeterminateTintList = ColorStateList.valueOf(primaryColor)
        btnClose.imageTintList = ColorStateList.valueOf(iconColor)
        btnDownload.imageTintList = ColorStateList.valueOf(iconColor)

        // Handle Window Insets for the top buttons
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.activityRoot) ?: btnClose) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Adjust Close button
            val closeLp = btnClose.layoutParams as ViewGroup.MarginLayoutParams
            closeLp.topMargin = systemBars.top + (16 * resources.displayMetrics.density).toInt()
            btnClose.layoutParams = closeLp
            
            // Adjust Download button
            val downloadLp = btnDownload.layoutParams as ViewGroup.MarginLayoutParams
            downloadLp.topMargin = systemBars.top + (16 * resources.displayMetrics.density).toInt()
            btnDownload.layoutParams = downloadLp

            // Adjust Top Overlay height to ensure it covers system bar and buttons
            findViewById<View>(R.id.topOverlay)?.let { overlay ->
                val overlayLp = overlay.layoutParams
                overlayLp.height = systemBars.top + (80 * resources.displayMetrics.density).toInt()
                overlay.layoutParams = overlayLp
            }
            
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

        // Setup thumbnail navigation
        thumbnailsRecyclerView = findViewById(R.id.thumbnailsRecyclerView)
        tvImageCounter = findViewById(R.id.tvImageCounter)
        bottomBar = findViewById(R.id.bottomBar)

        if (imageUrls.size > 1) {
            bottomBar.visibility = View.VISIBLE
            thumbnailAdapter = ThumbnailAdapter(imageUrls) { position ->
                currentIndex = position
                loadImage(imageUrls[currentIndex])
                updateThumbnailHighlight()
            }
            thumbnailsRecyclerView.adapter = thumbnailAdapter
            thumbnailsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
            updateThumbnailHighlight()
        } else {
            bottomBar.visibility = View.GONE
        }

        loadImage(imageUrls[currentIndex])

        btnClose.setOnClickListener {
            finish()
        }

        btnDownload.setOnClickListener {
            downloadImage()
        }
    }

    private fun downloadImage() {
        val currentUrl = imageUrls.getOrNull(currentIndex) ?: return
        val finalUrl = if (currentUrl.startsWith("http")) currentUrl.trim() 
                      else "${lavender.client.android.data.session.CredentialStore.getHttpServerUrl(this@FullScreenImageActivity)}" + currentUrl.trim().let { if (it.startsWith("/")) it else "/$it" }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = Glide.with(this@FullScreenImageActivity)
                    .asBitmap()
                    .load(finalUrl)
                    .submit()
                    .get()

                saveImageToGallery(bitmap)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FullScreenImageActivity, getString(R.string.image_saved_to_gallery), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FullScreenImageActivity, getString(R.string.save_image_error, e.message), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap) {
        val filename = "${getString(R.string.filename_prefix)}_${System.currentTimeMillis()}.jpg"
        var fos: java.io.OutputStream? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentResolver?.also { resolver ->
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = java.io.File(imagesDir, filename)
            fos = java.io.FileOutputStream(image)
        }
        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }
    }

    private fun loadImage(imageUrl: String) {
        if (imageUrl.isNotEmpty()) {
            val finalUrl = if (imageUrl.startsWith("http")) imageUrl.trim() 
                          else "${lavender.client.android.data.session.CredentialStore.getHttpServerUrl(this@FullScreenImageActivity)}" + imageUrl.trim().let { if (it.startsWith("/")) it else "/$it" }

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
            updateThumbnailHighlight()
        }
    }

    private fun showPreviousImage() {
        if (imageUrls.size > 1) {
            currentIndex = if (currentIndex - 1 < 0) imageUrls.size - 1 else currentIndex - 1
            loadImage(imageUrls[currentIndex])
            updateThumbnailHighlight()
        }
    }

    private fun updateThumbnailHighlight() {
        if (imageUrls.size <= 1) return
        tvImageCounter.text = "${currentIndex + 1} / ${imageUrls.size}"
        thumbnailAdapter?.setSelectedPosition(currentIndex)
        val layoutManager = thumbnailsRecyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
        val targetPos = currentIndex
        val firstVisible = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0
        val lastVisible = layoutManager?.findLastCompletelyVisibleItemPosition() ?: 0
        if (targetPos < firstVisible || targetPos > lastVisible) {
            thumbnailsRecyclerView.smoothScrollToPosition(targetPos)
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

    private class ThumbnailAdapter(
        private val urls: List<String>,
        private val onThumbnailClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ThumbnailAdapter.ThumbnailViewHolder>() {

        private var selectedPosition = 0

        fun setSelectedPosition(position: Int) {
            val old = selectedPosition
            selectedPosition = position
            if (old != position) {
                notifyItemChanged(old)
                notifyItemChanged(position)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_thumbnail, parent, false)
            return ThumbnailViewHolder(view)
        }

        override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
            val url = urls[position]
            val fullUrl = if (url.startsWith("http")) url.trim()
                else "${lavender.client.android.data.session.CredentialStore.getHttpServerUrl(holder.itemView.context)}" + url.trim().let { if (it.startsWith("/")) it else "/$it" }

            Glide.with(holder.itemView.context)
                .load(fullUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .placeholder(R.drawable.ic_image_placeholder)
                .into(holder.thumbnailImage)

            val isSelected = position == selectedPosition
            holder.thumbnailImage.alpha = if (isSelected) 1.0f else 0.5f
            holder.selectedBorder.visibility = if (isSelected) View.VISIBLE else View.GONE
            if (isSelected) {
                holder.selectedBorder.background = android.graphics.drawable.GradientDrawable().apply {
                    setStroke(3, android.graphics.Color.WHITE)
                    setColor(android.graphics.Color.TRANSPARENT)
                    cornerRadius = 8f * holder.itemView.resources.displayMetrics.density
                }
            }

            holder.itemView.setOnClickListener { onThumbnailClick(position) }
        }

        override fun getItemCount(): Int = urls.size

        class ThumbnailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val thumbnailImage: ImageView = itemView.findViewById(R.id.thumbnailImage)
            val selectedBorder: View = itemView.findViewById(R.id.selectedBorder)
        }
    }
}
