package lavender.client.android.data.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import lavender.client.android.MainActivity
import lavender.client.android.SplashActivity
import lavender.client.android.R

class LavenderMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Check if receiving is enabled
        val prefs = getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("push_receive_enabled", true)) {
            Log.d("FCM", "onMessageReceived: push_receive_enabled is false, skipping")
            return
        }

        Log.d("FCM", "onMessageReceived called")
        Log.d("FCM", "From: ${remoteMessage.from}")
        Log.d("FCM", "Notification title: ${remoteMessage.notification?.title}")
        Log.d("FCM", "Notification body: ${remoteMessage.notification?.body}")
        Log.d("FCM", "Data: ${remoteMessage.data}")

        // Extract title and body from data payload (for background messages)
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "Новое сообщение"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: ""

        // Extract room_id from data payload
        val roomId = remoteMessage.data["room_id"] ?: "general"

        // Save to history for testing
        NotificationHistory.add(title, body, remoteMessage.from)

        // Show notification with room_id
        showNotification(title, body, roomId)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")

        // Get saved username from SharedPreferences
        val prefs = getSharedPreferences("LavenderPrefs", Context.MODE_PRIVATE)
        val username = prefs.getString("username", "")

        if (username != null && username.isNotEmpty()) {
            // Register new token on server
            lavender.client.android.data.grpc.GrpcClient.registerToken(username, token)
            Log.d("FCM", "New token registered for user: $username")
        }
    }

    companion object {
        fun dismissNotificationsForRoom(context: Context, roomId: String) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val activeNotifications = notificationManager.activeNotifications
                for (notification in activeNotifications) {
                    val extras = notification.notification.extras
                    val notifRoomId = extras.getString("room_id")
                    if (notifRoomId == roomId) {
                        notificationManager.cancel(notification.id)
                    }
                }
            } else {
                notificationManager.cancelAll()
            }
        }
    }

    private fun showNotification(title: String, body: String, roomId: String) {
        Log.d("FCM", "showNotification called with title: $title, body: $body, room_id: $roomId")

        val channelId = "lavender_messages"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Сообщения Lavender",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("room_id", roomId)
            putExtra("from_notification", true)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

        val prefs = getSharedPreferences("ChatPrefs", Context.MODE_PRIVATE)
        val style = prefs.getString("notification_style", "standard") ?: "standard"

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            
        // Add room_id to extras so we can filter and dismiss later
        val extras = android.os.Bundle()
        extras.putString("room_id", roomId)
        notificationBuilder.addExtras(extras)

        when (style) {
            "messaging" -> {
                val user = androidx.core.app.Person.Builder()
                    .setName(title)
                    .build()
                
                val messagingStyle = NotificationCompat.MessagingStyle(user)
                    .addMessage(body, System.currentTimeMillis(), user)
                
                if (roomId != "general" && roomId != "general_chat") {
                    messagingStyle.setConversationTitle("Chat: $roomId")
                    messagingStyle.setGroupConversation(true)
                }

                notificationBuilder.setStyle(messagingStyle)
            }
            "big_text" -> {
                notificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
            }
        }
        
        // Always set basic fields for compatibility and non-styled display
        notificationBuilder.setContentTitle(title)
        notificationBuilder.setContentText(body)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
        Log.d("FCM", "Notification shown with room_id: $roomId")
    }
}
