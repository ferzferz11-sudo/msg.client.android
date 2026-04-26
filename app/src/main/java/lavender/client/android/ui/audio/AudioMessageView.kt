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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import lavender.client.android.R
import lavender.client.android.audio.AudioPlayerManager

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
        waveformView.generateRandomWaveform()
        
        updateUI()
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
