package lavender.client.android.ui.chatlist

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient

/**
 * Manages Fast Mode state for the chat list.
 *
 * Fast mode disables avatars, animations, and heavy graphics for a snappier UI.
 * State is persisted locally in SharedPreferences and synced to server via
 * UpdateUserSettings custom map.
 */
object FastModeManager {
    private const val TAG = "FastModeManager"
    private const val PREFS_NAME = "lavender_prefs"
    private const val KEY_FAST_MODE = "chat_list_fast_mode"
    private const val SERVER_KEY = "chat_list_mode"
    private const val SERVER_VALUE_FAST = "fast"
    private const val SERVER_VALUE_FULL = "full"

    @Volatile
    private var cachedFastMode: Boolean? = null

    /** Returns true if fast mode is enabled. Reads from cache, then SharedPreferences. */
    fun isFastMode(context: Context): Boolean {
        cachedFastMode?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getBoolean(KEY_FAST_MODE, false)
        cachedFastMode = value
        return value
    }

    /** Sets fast mode locally and syncs to server. */
    fun setFastMode(context: Context, enabled: Boolean) {
        cachedFastMode = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_FAST_MODE, enabled)
        }
        // Sync to server in background
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val custom = mapOf(SERVER_KEY to if (enabled) SERVER_VALUE_FAST else SERVER_VALUE_FULL)
                GrpcClient.updateUserSettingsV2(context, custom = custom)
                Log.d(TAG, "Fast mode synced to server: $enabled")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync fast mode to server: ${e.message}")
            }
        }
    }

    /** Restores fast mode from server settings on login/startup. */
    fun restoreFromServer(context: Context, custom: Map<String, String>) {
        val serverValue = custom[SERVER_KEY]
        if (serverValue != null) {
            val enabled = serverValue == SERVER_VALUE_FAST
            cachedFastMode = enabled
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putBoolean(KEY_FAST_MODE, enabled)
            }
            Log.d(TAG, "Fast mode restored from server: $enabled")
        }
    }
}
