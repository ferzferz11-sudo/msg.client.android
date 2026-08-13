package lavender.client.android.data.fcm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.session.SessionManager
import lavender.client.android.data.session.CredentialStore
import lavender.client.android.data.models.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: return
        val results = getResultsFromIntent(intent)
        val replyText = results?.getCharSequence(REPLY_KEY)?.toString() ?: return

        if (replyText.isBlank()) return

        val repliedToMessageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: ""
        val repliedToUser = intent.getStringExtra(EXTRA_SENDER) ?: ""
        val repliedToText = intent.getStringExtra(EXTRA_ORIGINAL_TEXT) ?: ""

        Log.d("FCM", "Reply from notification: room=$roomId, text=$replyText, replyTo=$repliedToMessageId")

        val pendingResult = goAsync()
        val session = SessionManager.session.value
        val username = session.username
        val serverAddress = CredentialStore.getServerAddress(context) ?: return

        if (username.isEmpty()) {
            Log.w("FCM", "Reply failed: no username")
            pendingResult.finish()
            return
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                if (GrpcClient.connectionStatus.value != lavender.client.android.data.grpc.ConnectionStatus.READY) {
                    GrpcClient.connect(serverAddress, context = context)
                    kotlinx.coroutines.delay(2000)
                }

                val myId = GrpcClient.getUserId() ?: username
                val msg = Message(
                    id = "temp_reply_${UUID.randomUUID()}",
                    roomId = roomId,
                    user = username,
                    userId = myId,
                    text = replyText,
                    timestamp = System.currentTimeMillis(),
                    repliedToMessageId = repliedToMessageId,
                    repliedToUser = repliedToUser,
                    repliedToText = repliedToText
                )

                GrpcClient.sendMessageV2(msg) { result ->
                    if (result != null) {
                        Log.d("FCM", "Reply sent successfully to room: $roomId")
                        dismissNotification(context, roomId)
                    } else {
                        Log.e("FCM", "Reply failed for room: $roomId")
                    }
                }
            } catch (e: Exception) {
                Log.e("FCM", "Reply error: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun dismissNotification(context: Context, roomId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(roomId.hashCode())
    }

    companion object {
        const val REPLY_KEY = "reply_text"
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_ORIGINAL_TEXT = "original_text"

        fun getResultsFromIntent(intent: Intent): android.os.Bundle? {
            return RemoteInput.getResultsFromIntent(intent)
        }
    }
}
