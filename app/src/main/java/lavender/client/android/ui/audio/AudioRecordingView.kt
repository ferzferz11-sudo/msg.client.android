package lavender.client.android.ui.audio

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import lavender.client.android.R
import lavender.client.android.audio.AudioRecorder
import java.io.File

class AudioRecordingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    private val recordButton: ImageView
    private val cancelButton: ImageView
    private val timerText: TextView
    private val recordingIndicator: View
    private val waveformView: AudioWaveformView
    
    private var audioRecorder: AudioRecorder? = null
    private var isRecording = false
    private var startTime: Long = 0
    
    private var onRecordingStarted: (() -> Unit)? = null
    private var onRecordingFinished: ((File?, Int) -> Unit)? = null
    private var onRecordingCancelled: (() -> Unit)? = null
    
    init {
        LayoutInflater.from(context).inflate(R.layout.audio_recording_view, this, true)
        
        recordButton = findViewById(R.id.recordButton)
        cancelButton = findViewById(R.id.cancelButton)
        timerText = findViewById(R.id.timerText)
        recordingIndicator = findViewById(R.id.recordingIndicator)
        waveformView = findViewById(R.id.waveformView)
        
        setupClickListeners()
        updateUI()
    }
    
    private fun setupClickListeners() {
        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
        
        cancelButton.setOnClickListener {
            cancelRecording()
        }
    }
    
    private fun startRecording() {
        audioRecorder = AudioRecorder(context)
        val outputFile = audioRecorder?.startRecording()
        
        if (outputFile != null) {
            isRecording = true
            startTime = System.currentTimeMillis()
            onRecordingStarted?.invoke()
            
            // Start timer
            startTimer()
            
            // Generate some initial waveform data and start animation
            waveformView.generateRandomWaveform()
            waveformView.startAnimation()
            
            updateUI()
        } else {
            // Show error message
            android.widget.Toast.makeText(context, "Failed to start recording", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopRecording() {
        val result = audioRecorder?.stopRecording()
        
        if (result != null) {
            val (file, duration) = result
            onRecordingFinished?.invoke(file, duration)
        }
        
        cleanup()
    }
    
    private fun cancelRecording() {
        audioRecorder?.cancelRecording()
        onRecordingCancelled?.invoke()
        cleanup()
    }
    
    private fun cleanup() {
        audioRecorder = null
        isRecording = false
        stopTimer()
        waveformView.stopAnimation()
        updateUI()
    }
    
    private var timerRunnable: Runnable? = null
    private var timerHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                    timerText.text = formatTime(elapsed)
                    
                    // Limit recording to 5 minutes
                    if (elapsed >= 300) {
                        stopRecording()
                    } else {
                        timerHandler.postDelayed(this, 100)
                    }
                }
            }
        }
        timerHandler.post(timerRunnable!!)
    }
    
    private fun stopTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = null
        timerText.text = "0:00"
    }
    
    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, secs)
    }
    
    private fun updateUI() {
        if (isRecording) {
            recordButton.setImageResource(R.drawable.ic_stop)
            recordButton.backgroundTintList = ContextCompat.getColorStateList(context, R.color.red)
            cancelButton.visibility = VISIBLE
            recordingIndicator.visibility = VISIBLE
            waveformView.visibility = VISIBLE
            
            // Pulse animation for record button
            val pulse = ObjectAnimator.ofFloat(recordButton, "scaleX", 1f, 1.1f, 1f)
            pulse.duration = 1000
            pulse.repeatCount = ObjectAnimator.INFINITE
            recordButton.tag = pulse
            pulse.start()
        } else {
            recordButton.setImageResource(R.drawable.ic_mic)
            recordButton.backgroundTintList = ContextCompat.getColorStateList(context, R.color.lavender_mist)
            cancelButton.visibility = GONE
            recordingIndicator.visibility = GONE
            waveformView.visibility = GONE
            
            // Stop pulse animation
            (recordButton.tag as? ObjectAnimator)?.cancel()
            recordButton.scaleX = 1f
            recordButton.scaleY = 1f
            recordButton.tag = null
        }
    }
    
    fun setOnRecordingStarted(listener: () -> Unit) {
        onRecordingStarted = listener
    }
    
    fun setOnRecordingFinished(listener: (File?, Int) -> Unit) {
        onRecordingFinished = listener
    }
    
    fun setOnRecordingCancelled(listener: () -> Unit) {
        onRecordingCancelled = listener
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cleanup()
    }
}
