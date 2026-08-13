package lavender.client.android.data.calls

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Manages audio modes and speakerphone settings for calls.
 */
class AudioModeManager(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var originalMode: Int = audioManager.mode
    var isSpeakerphoneOn: Boolean = false
        private set

    fun setCallMode() {
        originalMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeakerphoneOn(true)
    }

    fun restoreMode() {
        audioManager.mode = originalMode
        setSpeakerphoneOn(false)
    }

    fun toggleSpeakerphone(): Boolean {
        isSpeakerphoneOn = !isSpeakerphoneOn
        setSpeakerphoneOn(isSpeakerphoneOn)
        return isSpeakerphoneOn
    }

    fun setSpeakerphoneOn(enabled: Boolean) {
        isSpeakerphoneOn = enabled
        lavender.client.android.data.CompatUtils.setSpeakerphoneOn(audioManager, enabled)
    }
}
