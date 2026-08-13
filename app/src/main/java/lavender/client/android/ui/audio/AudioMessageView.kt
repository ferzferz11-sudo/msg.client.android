package lavender.client.android.ui.audio

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import lavender.client.android.R
import lavender.client.android.audio.AudioPlayerManager
import lavender.client.android.theme.ThemeUtils

class AudioMessageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    private val playButton: ImageView
    private val pauseButton: ImageView
    private val durationText: TextView
    private val waveformView: AudioWaveformView
    
    private var audioUrl: String = ""
    private var duration: Int = 0
    private var isPlaying: Boolean = false
    private var onPlayClickListener: ((String) -> Unit)? = null
    private var onPauseClickListener: (() -> Unit)? = null
    
    private val audioPlayerManager: AudioPlayerManager by lazy { 
        AudioPlayerManager.getInstance(context) 
    }
    
    init {
        LayoutInflater.from(context).inflate(R.layout.audio_message_view, this, true)
        
        playButton = findViewById(R.id.playButton)
        pauseButton = findViewById(R.id.pauseButton)
        durationText = findViewById(R.id.durationText)
        waveformView = findViewById(R.id.waveformView)
        
        setupClickListeners()
        updateUI()
    }
    
    private fun setupClickListeners() {
        playButton.setOnClickListener {
            if (audioUrl.isNotEmpty()) {
                audioPlayerManager.playAudio(audioUrl)
                onPlayClickListener?.invoke(audioUrl)
                observePlayerState()
            }
        }
        
        pauseButton.setOnClickListener {
            audioPlayerManager.pauseAudio()
            onPauseClickListener?.invoke()
        }
    }
    
    fun setAudioData(url: String, durationSeconds: Int) {
        this.audioUrl = url
        this.duration = durationSeconds

        durationText.text = formatDuration(durationSeconds)

        // Show random waveform instantly, then replace with real data
        waveformView.generateRandomWaveform()
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            val data = WaveformExtractor.extract(url)
            if (data.isNotEmpty()) waveformView.setWaveformData(data)
        }

        updateUI()
    }

    fun applyTheme(theme: lavender.client.android.theme.Theme, isOutgoing: Boolean) {
        val primaryColor = ThemeUtils.parseSafeColor(theme.primaryColor, ContextCompat.getColor(context, R.color.lavender_mist))
        val onPrimaryColor = ThemeUtils.parseSafeColor(theme.onPrimaryColor, Color.WHITE)
        val incomingTextColor = ThemeUtils.parseSafeColor(theme.incomingTextColor, Color.WHITE)
        val outgoingTextColor = ThemeUtils.parseSafeColor(theme.outgoingTextColor, Color.WHITE)

        // Apply theme to buttons (circle background)
        // For incoming messages: background = primaryColor, icon = onPrimaryColor
        // For outgoing messages: background = onPrimaryColor, icon = primaryColor (to contrast with bubble)
        val buttonBgColor = if (isOutgoing) onPrimaryColor else primaryColor
        val iconColor = if (isOutgoing) primaryColor else onPrimaryColor
        
        playButton.backgroundTintList = android.content.res.ColorStateList.valueOf(buttonBgColor)
        pauseButton.backgroundTintList = android.content.res.ColorStateList.valueOf(buttonBgColor)
        playButton.imageTintList = android.content.res.ColorStateList.valueOf(iconColor)
        pauseButton.imageTintList = android.content.res.ColorStateList.valueOf(iconColor)

        // Apply theme to duration text
        val txtColor = if (isOutgoing) outgoingTextColor else incomingTextColor
        durationText.setTextColor(txtColor)

        // Apply theme to waveform
        val waveformColor = if (isOutgoing) {
            ThemeUtils.adjustAlpha(outgoingTextColor, 0.4f)
        } else {
            ThemeUtils.adjustAlpha(incomingTextColor, 0.4f)
        }
        val playbackColor = if (isOutgoing) outgoingTextColor else incomingTextColor
        
        waveformView.setWaveformColors(waveformColor, playbackColor)
    }
    
    fun setPlaying(playing: Boolean) {
        isPlaying = playing
        updateUI()
    }
    
    private var collectionJob: kotlinx.coroutines.Job? = null

    private fun observePlayerState() {
        collectionJob?.cancel()
        
        val lifecycleOwner = findViewTreeLifecycleOwner()
        if (lifecycleOwner != null) {
            collectionJob = lifecycleOwner.lifecycleScope.launch {
                // Collect playing state
                launch {
                    audioPlayerManager.isPlaying.collect { _ ->
                        if (audioPlayerManager.isCurrentAudio(audioUrl)) {
                            updateUI()
                        }
                    }
                }
                // Collect position
                launch {
                    audioPlayerManager.currentPosition.collect { position ->
                        if (audioPlayerManager.isCurrentAudio(audioUrl)) {
                            val durationMs = audioPlayerManager.duration.value
                            if (durationMs > 0) {
                                waveformView.setPlaybackProgress(position.toFloat() / durationMs)
                                durationText.text = formatDuration((position / 1000).toInt())
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observePlayerState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        collectionJob?.cancel()
    }
    
    private fun updateUI() {
        if (audioUrl.isEmpty()) {
            playButton.visibility = View.GONE
            pauseButton.visibility = View.GONE
            durationText.text = "0:00"
            return
        }
        
        // Check if this audio is currently playing
        val isCurrent = audioPlayerManager.isCurrentAudio(audioUrl)
        val isCurrentlyPlaying = isCurrent && audioPlayerManager.isPlaying.value
        
        if (isCurrentlyPlaying) {
            playButton.visibility = View.GONE
            pauseButton.visibility = View.VISIBLE
        } else {
            playButton.visibility = View.VISIBLE
            pauseButton.visibility = View.GONE
            durationText.text = formatDuration(duration)
        }

        if (!isCurrent) {
            waveformView.setPlaybackProgress(0f)
        }
    }
    
    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", minutes, secs)
    }
    
    fun setOnPlayClickListener(listener: (String) -> Unit) {
        onPlayClickListener = listener
    }
    
    fun setOnPauseClickListener(listener: () -> Unit) {
        onPauseClickListener = listener
    }
}
