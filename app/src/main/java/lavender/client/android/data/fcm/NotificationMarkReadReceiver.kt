package lavender.client.android.data.fcm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.grpc.ConnectionStatus
import lavender.client.android.data.session.SessionManager
import lavender.client.android.data.session.CredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationMarkReadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: return
        Log.d("FCM", "Mark as read from notification: room=$roomId")

        val pendingResult = goAsync()
        val session = SessionManager.session.value
        val username = session.username
        val serverAddress = CredentialStore.getServerAddress(context)

        if (username.isEmpty()) {
            Log.w("FCM", "MarkRead failed: no username")
            pendingResult.finish()
            return
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                if (GrpcClient.connectionStatus.value != ConnectionStatus.READY) {
                    if (serverAddress.isNullOrEmpty()) {
                        Log.w("FCM", "MarkRead failed: no server address")
                        return@launch
                    }
                    GrpcClient.connect(serverAddress, context = context)
                    kotlinx.coroutines.delay(2000)
                }

                GrpcClient.markRead(roomId, username) {
                    Log.d("FCM", "Mark as read completed for room: $roomId")
                    dismissNotification(context, roomId)
                    val broadcast = Intent(ACTION_CHAT_MARKED_READ).apply {
                        putExtra(EXTRA_ROOM_ID, roomId)
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(broadcast)
                }
            } catch (e: Exception) {
                Log.e("FCM", "MarkRead error: ${e.message}", e)
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
        const val EXTRA_ROOM_ID = "room_id"
        const val ACTION_CHAT_MARKED_READ = "lavender.client.android.ACTION_CHAT_MARKED_READ"
    }
}
