package lavender.client.android.data

import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Window

/**
 * Centralized backward-compat wrappers for deprecated Android APIs.
 * All @Suppress("DEPRECATION") annotations are concentrated here.
 */
object CompatUtils {

    /** getParcelableExtra — deprecated in API 33 (typed generic version). */
    fun <T> getParcelableExtra(intent: Intent, key: String, clazz: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, clazz)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }
    }

    /** getParcelableExtra from Bundle — deprecated in API 33. */
    fun <T> getParcelableExtra(bundle: Bundle?, key: String, clazz: Class<T>): T? {
        if (bundle == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelable(key, clazz)
        } else {
            @Suppress("DEPRECATION")
            bundle.getParcelable(key)
        }
    }

    /** MediaRecorder() no-arg constructor — deprecated in API 31. */
    fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            throw IllegalArgumentException("Use MediaRecorder(context) on API 31+")
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    /** Window.setNavigationBarColor — deprecated in API 35 (no-op on 35+). */
    fun setNavigationBarColor(window: Window, color: Int) {
        if (Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            window.navigationBarColor = color
        }
    }

    /** AudioManager.isSpeakerphoneOn — deprecated in API 31. */
    fun setSpeakerphoneOn(audioManager: AudioManager, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                val speakerDevice = audioManager.availableCommunicationDevices
                    .find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                speakerDevice?.let { audioManager.setCommunicationDevice(it) }
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        }
    }
}
