package lavender.client.android.data.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import lavender.client.android.data.grpc.GrpcClient

class ScreenStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d("ScreenState", "Screen OFF — switching stream to empty room")
                GrpcClient.startChatV2("") { /* ignore */ }
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d("ScreenState", "Screen ON")
            }
        }
    }

    companion object {
        private var receiver: ScreenStateReceiver? = null

        fun register(context: Context) {
            if (receiver != null) return
            receiver = ScreenStateReceiver()
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            context.applicationContext.registerReceiver(receiver, filter)
            Log.d("ScreenState", "Registered screen state receiver")
        }

        fun unregister(context: Context) {
            receiver?.let {
                try {
                    context.applicationContext.unregisterReceiver(it)
                } catch (_: Exception) {}
                receiver = null
                Log.d("ScreenState", "Unregistered screen state receiver")
            }
        }
    }
}
