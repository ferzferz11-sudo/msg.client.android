package lavender.client.android.data.fcm

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import lavender.client.android.data.calls.CallManager

class CallActionService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val callId = intent?.getStringExtra("CALL_ID")

        if (action != null && callId != null) {
            Log.d("FCM", "Call action: $action for call: $callId")

            when (action) {
                "DECLINE" -> {
                    CallManager.init(applicationContext)
                    CallManager.rejectCall()
                    val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                    nm.cancel(callId.hashCode())
                }
            }
        }

        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
