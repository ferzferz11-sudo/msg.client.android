package lavender.client.android.data.calls

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Handles playing ringtones for incoming calls and dial tones for outgoing calls.
 */
class CallSoundManager(context: Context) {
    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    @Volatile private var toneGenerator: ToneGenerator? = null
    private var dialToneJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val TAG = "CallSoundManager"

    fun startRingtone() {
        stop()
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer.create(appContext, notification)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringtone", e)
        }
    }

    fun startDialTone() {
        stop()
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            dialToneJob = scope.launch {
                try {
                    while (isActive && toneGenerator != null) {
                        toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 1000)
                        delay(3000)
                    }
                } catch (e: Exception) { Log.w(TAG, "Caught: " + e.message) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start dial tone", e)
            startFallbackDialTone()
        }
    }

    private fun startFallbackDialTone() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer.create(appContext, notification)
            mediaPlayer?.isLooping = true
            mediaPlayer?.start()
        } catch (e: Exception) { Log.w(TAG, "Caught: " + e.message) }
    }

    fun stop() {
        dialToneJob?.cancel()
        dialToneJob = null
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

    fun destroy() {
        stop()
        scope.cancel()
    }

    companion object {
        private const val TAG = "CallSoundManager"
    }
}
