package lavender.client.android.data.calls

import android.content.Context
import android.content.Intent
import lavender.client.android.CallActivity
import lavender.client.android.ConferenceLobbyActivity

/**
 * Centralized navigation for call-related screens to keep Activities clean.
 */
object CallNavigator {

    fun startCall(context: Context, receiverId: String, senderName: String = "", isVideo: Boolean = true) {
        val intent = Intent(context, CallActivity::class.java).apply {
            putExtra("RECEIVER_ID", receiverId)
            putExtra("SENDER_NAME", senderName)
            putExtra("IS_INCOMING", false)
            putExtra("IS_VIDEO_CALL", isVideo)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun startConference(context: Context, roomId: String) {
        val intent = Intent(context, ConferenceLobbyActivity::class.java).apply {
            putExtra("ROOM_ID", roomId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun joinConference(context: Context, roomId: String) {
        val intent = Intent(context, ConferenceLobbyActivity::class.java).apply {
            putExtra("ROOM_ID", roomId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun navigateToCall(context: Context, callId: String, receiverId: String, isIncoming: Boolean, isConference: Boolean = false, roomId: String = "", creatorId: String = "") {
        val intent = Intent(context, CallActivity::class.java).apply {
            putExtra("CALL_ID", callId)
            putExtra("RECEIVER_ID", receiverId)
            putExtra("IS_INCOMING", isIncoming)
            putExtra("IS_CONFERENCE", isConference)
            putExtra("ROOM_ID", roomId)
            if (creatorId.isNotEmpty()) putExtra("CREATOR", creatorId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
    }
}
