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
import lavender.client.android.R

class LavenderMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM", "onMessageReceived called")
        Log.d("FCM", "From: ${remoteMessage.from}")
        Log.d("FCM", "Notification title: ${remoteMessage.notification?.title}")
        Log.d("FCM", "Notification body: ${remoteMessage.notification?.body}")
        Log.d("FCM", "Data: ${remoteMessage.data}")

        // Extract title and body from data payload (for background messages)
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "Новое сообщение"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: ""

        // Save to history for testing
        NotificationHistory.add(title, body, remoteMessage.from)

        // Show notification
        showNotification(title, body)
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

    private fun showNotification(title: String, body: String) {
        Log.d("FCM", "showNotification called with title: $title, body: $body")

        val channelId = "lavender_messages"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Сообщения Lavender",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
        Log.d("FCM", "Notification shown")
    }
}
