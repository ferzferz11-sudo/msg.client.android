package lavender.client.android.ui.audio

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import lavender.client.android.R
import kotlin.random.Random

class AudioWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private val waveformPaint: Paint
    private val playbackPaint: Paint
    
    private val waveformBars = mutableListOf<Float>()
    private var playbackProgress = 0f
    private var animator: ValueAnimator? = null
    
    init {
        waveformPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.audio_waveform_default)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        playbackPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.audio_waveform_playing)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (waveformBars.isEmpty()) return
        
        val barWidth = width.toFloat() / waveformBars.size
        val barSpacing = barWidth * 0.2f
        val actualBarWidth = barWidth - barSpacing
        
        for (i in waveformBars.indices) {
            val barHeight = waveformBars[i] * height * 0.8f
            val x = i * barWidth + barSpacing / 2
            val y = (height - barHeight) / 2
            
            // Draw default waveform
            canvas.drawRect(
                x,
                y,
                x + actualBarWidth,
                y + barHeight,
                waveformPaint
            )
            
            // Draw playback progress
            if (i < waveformBars.size * playbackProgress) {
                canvas.drawRect(
                    x,
                    y,
                    x + actualBarWidth,
                    y + barHeight,
                    playbackPaint
                )
            }
        }
    }
    
    fun generateRandomWaveform() {
        waveformBars.clear()
        val barCount = 40
        
        for (i in 0 until barCount) {
            waveformBars.add(Random.nextFloat() * 0.7f + 0.3f)
        }
        
        invalidate()
    }
    
    fun setWaveformData(data: List<Float>) {
        waveformBars.clear()
        waveformBars.addAll(data)
        invalidate()
    }
    
    fun setPlaybackProgress(progress: Float) {
        playbackProgress = progress.coerceIn(0f, 1f)
        invalidate()
    }
    
    fun startAnimation() {
        animator?.cancel()
        
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000 
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                setPlaybackProgress(animator.animatedValue as Float)
            }
            start()
        }
    }
    
    fun stopAnimation() {
        animator?.cancel()
        animator = null
        setPlaybackProgress(0f)
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}
