package lavender.client.android.ui.audio

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import lavender.client.android.R
import lavender.client.android.audio.AudioRecorder
import lavender.client.android.data.proto.CustomThemeProto // Добавлен импорт
import java.io.File

class AudioRecordingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val recordButton: ImageView
    private val cancelButton: ImageView
    private val timerText: TextView
    private val statusText: TextView
    private val recordingIndicator: View
    private val waveformView: AudioWaveformView

    private var audioRecorder: AudioRecorder? = null
    private var isRecording = false
    private var startTime: Long = 0
    private var currentTheme: CustomThemeProto? = null // Переменная для хранения темы

    private var onRecordingStarted: (() -> Unit)? = null
    private var onRecordingFinished: ((File?, Int) -> Unit)? = null
    private var onRecordingCancelled: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.audio_recording_view, this, true)

        recordButton = findViewById(R.id.recordButton)
        cancelButton = findViewById(R.id.cancelButton)
        timerText = findViewById(R.id.timerText)
        statusText = findViewById(R.id.statusText)
        recordingIndicator = findViewById(R.id.recordingIndicator)
        waveformView = findViewById(R.id.waveformView)

        setupClickListeners()
        updateUI()
    }

    // Твоя функция для применения прозрачности
    fun applyCustomTheme(theme: CustomThemeProto?) {
        this.currentTheme = theme
        updateColors()
    }

    private fun updateColors() {
        val theme = currentTheme
        val bgColor = if (theme != null) Color.TRANSPARENT else ContextCompat.getColor(context, R.color.pale_lilac)
        setBackgroundColor(bgColor)
        if (childCount > 0) {
            getChildAt(0).setBackgroundColor(bgColor)
        }

        val primColor = try {
            theme?.primaryColor?.let { Color.parseColor(it) } ?: ContextCompat.getColor(context, R.color.lavender_mist)
        } catch (_: Exception) {
            ContextCompat.getColor(context, R.color.lavender_mist)
        }
        val onPrimColor = try {
            theme?.onPrimaryColor?.let { Color.parseColor(it) } ?: Color.WHITE
        } catch (_: Exception) {
            Color.WHITE
        }
        val textPrimary = try {
            theme?.textPrimaryColor?.let { Color.parseColor(it) } ?: Color.BLACK
        } catch (_: Exception) {
            Color.BLACK
        }

        val secondaryTxtColor = try {
            theme?.textSecondaryColor?.let { Color.parseColor(it) } ?: Color.GRAY
        } catch (_: Exception) {
            Color.GRAY
        }

        if (isRecording) {
            recordButton.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.RED)
            recordButton.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            recordButton.alpha = 0.5f
        } else {
            recordButton.backgroundTintList = android.content.res.ColorStateList.valueOf(primColor)
            recordButton.imageTintList = android.content.res.ColorStateList.valueOf(onPrimColor)
            recordButton.alpha = 0.5f
        }

        cancelButton.backgroundTintList = android.content.res.ColorStateList.valueOf(primColor)
        cancelButton.imageTintList = android.content.res.ColorStateList.valueOf(onPrimColor)
        cancelButton.alpha = 0.5f
        timerText.setTextColor(textPrimary)
        statusText.setTextColor(secondaryTxtColor)
        
        // Set waveform colors
        waveformView.setWaveformColors(
            lavender.client.android.theme.ThemeUtils.adjustAlpha(textPrimary, 0.4f),
            primColor
        )
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

            startTimer()
            waveformView.generateRandomWaveform()
            startWaveformUpdates()

            updateUI()
        } else {
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

    fun cancel() {
        cancelRecording()
    }

    private fun cleanup() {
        audioRecorder = null
        isRecording = false
        stopTimer()
        stopWaveformUpdates()
        waveformView.stopAnimation()
        updateUI()
    }

    private var timerRunnable: Runnable? = null
    private var waveformRunnable: Runnable? = null
    private var timerHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun startWaveformUpdates() {
        waveformRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val amplitude = audioRecorder?.getMaxAmplitude() ?: 0
                    val normalized = (amplitude.toFloat() / 32767f).coerceIn(0.1f, 1f)
                    waveformView.addAmplitude(normalized)
                    timerHandler.postDelayed(this, 100)
                }
            }
        }
        timerHandler.post(waveformRunnable!!)
    }

    private fun stopWaveformUpdates() {
        waveformRunnable?.let { timerHandler.removeCallbacks(it) }
        waveformRunnable = null
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                    timerText.text = formatTime(elapsed)

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
        updateColors()

        if (isRecording) {
            recordButton.setImageResource(R.drawable.ic_stop)
            recordingIndicator.visibility = VISIBLE
            waveformView.visibility = VISIBLE
            statusText.text = context.getString(R.string.recording)
            timerText.visibility = VISIBLE

            val pulse = ObjectAnimator.ofFloat(recordButton, "scaleX", 1f, 1.1f, 1f)
            pulse.duration = 1000
            pulse.repeatCount = ObjectAnimator.INFINITE
            recordButton.tag = pulse
            pulse.start()
        } else {
            recordButton.setImageResource(R.drawable.ic_mic)
            recordingIndicator.visibility = GONE
            waveformView.visibility = GONE
            statusText.text = context.getString(R.string.tap_to_record)
            timerText.visibility = GONE

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
