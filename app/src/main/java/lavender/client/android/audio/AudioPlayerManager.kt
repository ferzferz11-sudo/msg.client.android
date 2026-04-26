package lavender.client.android.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AudioPlayerManager private constructor(private val context: Context) {
    
    private var exoPlayer: ExoPlayer? = null
    private var currentAudioUrl: String = ""
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private var playbackPositionListener: (() -> Unit)? = null
    
    companion object {
        @Volatile
        private var INSTANCE: AudioPlayerManager? = null
        
        fun getInstance(context: Context): AudioPlayerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioPlayerManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private fun initializePlayer() {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : androidx.media3.common.Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }
                    
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            androidx.media3.common.Player.STATE_BUFFERING -> {
                                _isLoading.value = true
                            }
                            androidx.media3.common.Player.STATE_READY -> {
                                _isLoading.value = false
                                _duration.value = duration
                            }
                            androidx.media3.common.Player.STATE_ENDED -> {
                                _isPlaying.value = false
                                _currentPosition.value = 0L
                            }
                        }
                    }
                })
            }
        }
    }
    
    fun playAudio(audioUrl: String, playerView: PlayerView? = null) {
        initializePlayer()
        
        if (currentAudioUrl == audioUrl && _isPlaying.value) {
            // Same audio is already playing, pause it
            pauseAudio()
            return
        }
        
        if (currentAudioUrl != audioUrl) {
            // New audio, load it
            currentAudioUrl = audioUrl
            val mediaItem = MediaItem.fromUri(audioUrl)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
        }
        
        // Attach to PlayerView if provided
        playerView?.player = exoPlayer
        
        exoPlayer?.play()
        
        // Start position updates
        startPositionUpdates()
    }
    
    fun pauseAudio() {
        exoPlayer?.pause()
    }
    
    fun stopAudio() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        currentAudioUrl = ""
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }
    
    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }
    
    fun getCurrentAudioUrl(): String = currentAudioUrl
    
    fun isCurrentAudio(audioUrl: String): Boolean = currentAudioUrl == audioUrl
    
    private fun startPositionUpdates() {
        // Stop any existing position updates
        stopPositionUpdates()
        
        val updateRunnable = object : Runnable {
            override fun run() {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        _currentPosition.value = player.currentPosition
                        // Schedule next update
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 100)
                    } else if (player.playbackState == androidx.media3.common.Player.STATE_READY) {
                         // Even if not playing, we might have seeked
                         _currentPosition.value = player.currentPosition
                    }
                }
            }
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post(updateRunnable)
        playbackPositionListener = {
             // This is a dummy to keep track that we have a listener active if needed
        }
    }
    
    private fun stopPositionUpdates() {
        // In this simple implementation, the runnable stops itself if !isPlaying
        // But we could use a handler with a specific token if we wanted more control
    }
    
    fun release() {
        stopPositionUpdates()
        exoPlayer?.release()
        exoPlayer = null
        currentAudioUrl = ""
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }
    
    // Convenience methods for formatted time
    fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }
    
    fun getCurrentPositionFormatted(): String = formatTime(_currentPosition.value)
    fun getDurationFormatted(): String = formatTime(_duration.value)
}
