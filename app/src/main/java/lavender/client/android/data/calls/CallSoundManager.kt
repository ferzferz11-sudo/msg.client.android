package lavender.client.android.data.calls

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.util.Log

/**
 * Handles playing ringtones for incoming calls and dial tones for outgoing calls.
 */
class CallSoundManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private val TAG = "CallSoundManager"

    /**
     * Plays the default system ringtone for incoming calls.
     */
    fun startRingtone() {
        stop()
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer.create(context, notification)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringtone", e)
        }
    }

    /**
     * Plays a dial tone or calling sound for outgoing calls.
     */
    fun startDialTone() {
        stop()
        try {
            // Using ToneGenerator for a professional "calling" sound
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            Thread {
                try {
                    while (toneGenerator != null) {
                        toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1000)
                        Thread.sleep(3000)
                    }
                } catch (e: InterruptedException) {
                    // Stop requested
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start dial tone", e)
            // Fallback to notification sound if ToneGenerator fails
            startFallbackDialTone()
        }
    }

    private fun startFallbackDialTone() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer.create(context, notification)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } catch (_: Exception) {}
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            
            toneGenerator?.stopTone()
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping sounds", e)
        }
    }
}
