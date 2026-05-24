package lavender.client.android.data.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import lavender.client.android.R

class LavenderMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("push_receive_enabled", true)) {
            Log.d("FCM", "onMessageReceived: push_receive_enabled is false, skipping")
            return
        }

        Log.d("FCM", "onMessageReceived called from: ${remoteMessage.from}")

        val type = remoteMessage.data["type"]
        if (type == "VOIP_CALL") {
            val callId = remoteMessage.data["call_id"] ?: ""
            val senderId = remoteMessage.data["sender_id"] ?: ""
            handleIncomingCall(callId, senderId)
            return
        }

        // Извлекаем данные (приоритет payload из data, затем notification)
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: "Новое сообщение"
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: ""
        val roomId = remoteMessage.data["room_id"] ?: "general"

        // Показываем уведомление
        showNotification(title, body, roomId)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")

        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val username = prefs.getString("username", "")

        if (!username.isNullOrEmpty()) {
            // Use common sync logic from SessionManager
            lavender.client.android.data.session.SessionManager.syncFcmToken(this, username)
        }
    }

    private fun handleIncomingCall(callId: String, senderId: String) {
        Log.d("FCM", "Handling incoming VOIP call: $callId from $senderId")
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val serverAddress = prefs.getString("server_address", "82.146.43.235") ?: "82.146.43.235"

        // Ensure we are connected to receive signaling
        lavender.client.android.data.grpc.GrpcClient.connect(serverAddress, context = applicationContext)
        lavender.client.android.data.calls.CallManager.init(applicationContext)
        lavender.client.android.data.grpc.GrpcClient.startCallSession()

        // Show a notification or launch a full-screen Intent for the call
        // In a real app, you would start a Foreground Service here
        showCallNotification(senderId, callId)
    }

    private fun showCallNotification(senderId: String, callId: String) {
        val channelId = "lavender_calls"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, lavender.client.android.CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALL_ID", callId)
            putExtra("RECEIVER_ID", senderId)
            putExtra("IS_INCOMING", true)
        }
        val pendingIntent = PendingIntent.getActivity(this, callId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Звонки Lavender",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null) // Use custom ringtone or handle in activity
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle("Входящий звонок")
            .setContentText("Звонит $senderId")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)

        notificationManager.notify(callId.hashCode(), notificationBuilder.build())
    }

    private fun showNotification(title: String, body: String, roomId: String) {
        val channelId = "lavender_messages"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Создаем канал уведомлений (обязательно для Android 8.0+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Сообщения Lavender",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        val username = prefs.getString("username", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        val serverAddress = prefs.getString("server_address", "") ?: ""

        val intent = if (username.isNotEmpty() && password.isNotEmpty()) {
            // Логин есть — летим сразу в NewChatActivity
            Intent(this, lavender.client.android.NewChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("USERNAME", username)
                putExtra("PASSWORD", password)
                putExtra("SERVER_ADDRESS", serverAddress)
                putExtra("ROOM_ID", roomId)
                putExtra("CHAT_NAME", title)
                putExtra("IS_DIRECT", !roomId.startsWith("group_") && roomId != "general")
                putExtra("from_notification", true)
            }
        } else {
            // Логина нет — идем в SplashActivity
            Intent(this, lavender.client.android.SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("ROOM_ID", roomId)
                putExtra("from_notification", true)
            }
        }

        // Уникальный PendingIntent для каждой комнаты
        val pendingIntent = PendingIntent.getActivity(
            this,
            roomId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setColor(ContextCompat.getColor(this, R.color.lavender_mist))
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // Добавляем ID комнаты в экстра для возможности удаления программно
        val extras = android.os.Bundle()
        extras.putString("room_id", roomId)
        notificationBuilder.addExtras(extras)

        // Применяем стиль (если выбран messaging)
        val style = prefs.getString("notification_style", "standard")
        if (style == "messaging") {
            val user = androidx.core.app.Person.Builder().setName(title).build()
            val messagingStyle = NotificationCompat.MessagingStyle(user)
                .addMessage(body, System.currentTimeMillis(), user)

            if (roomId != "general") {
                messagingStyle.setConversationTitle(title)
                messagingStyle.setGroupConversation(!roomId.startsWith("direct_"))
            }
            notificationBuilder.setStyle(messagingStyle)
        }

        notificationManager.notify(roomId.hashCode(), notificationBuilder.build())
        Log.d("FCM", "Notification shown for room: $roomId")
    }

    companion object {
        fun dismissNotificationsForRoom(context: Context, roomId: String) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 1. Direct cancel using the stable ID we used to notify
            val notifId = roomId.hashCode()
            Log.d("FCM", "Dismissing notifications for room: $roomId (ID: $notifId)")
            notificationManager.cancel(notifId)

            // 2. Fallback for older notifications or different ID schemes
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                try {
                    val active = notificationManager.activeNotifications
                    active.forEach { notification ->
                        val extras = notification.notification.extras
                        val notifRoomId = extras.getString("room_id")
                        if (notifRoomId == roomId) {
                            Log.d("FCM", "Found active notification for room $roomId, cancelling by ID: ${notification.id}")
                            notificationManager.cancel(notification.id)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FCM", "Error dismissing notifications: ${e.message}")
                }
            }
        }
    }
}