package lavender.client.android.data.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import lavender.client.android.R
import kotlinx.coroutines.*
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.calls.CallManager

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
            val senderName = remoteMessage.data["sender_name"] ?: senderId
            handleIncomingCall(callId, senderId, senderName)
            return
        }
        if (type == "CALL_ENDED") {
            val callId = remoteMessage.data["call_id"] ?: ""
            Log.d("FCM", "Call ended push received for call: $callId")
            CallManager.handleCallEndedPush(callId)
            dismissCallNotification(callId)
            return
        }

        // Извлекаем данные (приоритет payload из data, затем notification)
        val title = remoteMessage.data["title"] ?: remoteMessage.notification?.title ?: getString(R.string.new_message)
        val body = remoteMessage.data["body"] ?: remoteMessage.notification?.body ?: ""
        val roomId = remoteMessage.data["room_id"] ?: "general"

        // Показываем уведомление
        showNotification(title, body, roomId)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        Log.d("FCM", "Refreshed token: $token")

        // Token sync is handled by SessionManager
        val session = lavender.client.android.data.session.SessionManager.session.value
        if (session.isLoggedIn) {
            lavender.client.android.data.session.SessionManager.syncFcmToken(this, session.username)
        }
    }

    private fun handleIncomingCall(callId: String, senderId: String, senderName: String) {
        Log.d("FCM", "Handling incoming VOIP call: $callId from $senderId ($senderName)")
        val serverAddress = lavender.client.android.data.session.CredentialStore.getServerAddress(this)
            ?: "82.146.43.235"

        CallManager.init(applicationContext)
        GrpcClient.connect(serverAddress, context = applicationContext)

        CoroutineScope(Dispatchers.IO).launch {
            for (i in 1..10) {
                if (GrpcClient.connectionStatus.value == lavender.client.android.data.grpc.ConnectionStatus.READY) {
                    GrpcClient.startCallSession()
                    break
                }
                delay(500)
            }
        }

        showCallNotification(senderId, callId, senderName)

        val callIntent = android.content.Intent(this, lavender.client.android.CallActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("CALL_ID", callId)
            putExtra("RECEIVER_ID", senderId)
            putExtra("SENDER_NAME", senderName)
            putExtra("IS_INCOMING", true)
        }
        startActivity(callIntent)
    }

    private fun dismissCallNotification(callId: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(callId.hashCode())
        Log.d("FCM", "Dismissed call notification for call: $callId")
    }

    private fun showCallNotification(senderId: String, callId: String, senderName: String = senderId) {
        val channelId = "lavender_calls"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, lavender.client.android.CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("CALL_ID", callId)
            putExtra("RECEIVER_ID", senderId)
            putExtra("SENDER_NAME", senderName)
            putExtra("IS_INCOMING", true)
        }
        val pendingIntent = PendingIntent.getActivity(this, callId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.lavender_calls_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val declineIntent = PendingIntent.getService(
            this, callId.hashCode() + 1,
            Intent(this, CallActionService::class.java).apply {
                action = "DECLINE"
                putExtra("CALL_ID", callId)
                putExtra("SENDER_ID", senderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(getString(R.string.incoming_call))
            .setContentText(getString(R.string.call_from, senderName))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setSound(ringtoneUri)
            .addAction(R.drawable.ic_notification_small, getString(R.string.decline_call), declineIntent)

        notificationManager.notify(callId.hashCode(), notificationBuilder.build())
    }

    private fun showNotification(title: String, body: String, roomId: String) {
        val channelId = "lavender_messages_v2"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Enable notification channel to allow direct reply
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val existingChannel = notificationManager.getNotificationChannel(channelId)
            if (existingChannel != null && !existingChannel.importance.equals(NotificationManager.IMPORTANCE_HIGH)) {
                existingChannel.importance = NotificationManager.IMPORTANCE_HIGH
                notificationManager.createNotificationChannel(existingChannel)
            }
        }

        // Delete old channel (created with wrong importance, can't be changed)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            notificationManager.getNotificationChannel("lavender_messages")?.let {
                notificationManager.deleteNotificationChannel("lavender_messages")
            }
        }

        // Создаем канал уведомлений (обязательно для Android 8.0+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val channel = NotificationChannel(
                channelId,
                getString(R.string.lavender_messages_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.lavender_messages_channel_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                enableLights(true)
                setShowBadge(true)
                setSound(defaultSoundUri, android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Read credentials from secure storage
        val session = lavender.client.android.data.session.SessionManager.session.value
        val username = session.username
        val serverAddress = lavender.client.android.data.session.CredentialStore.getServerAddress(this) ?: ""

        val intent = if (username.isNotEmpty()) {
            // Логин есть — летим сразу в NewChatActivity
            Intent(this, lavender.client.android.NewChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("USERNAME", username)
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
            .setFullScreenIntent(pendingIntent, true)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val soundPrefs = getSharedPreferences("notification_sounds", MODE_PRIVATE)
        val customSoundUri = soundPrefs.getString(roomId, null)
        if (customSoundUri != null) {
            notificationBuilder.setSound(Uri.parse(customSoundUri))
        } else {
            val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            notificationBuilder.setSound(defaultSound)
        }

        // Apply DND bypass if enabled (requires NOTIFICATION_POLICY_ACCESS)
        val prefs = getSharedPreferences("lavender_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("push_bypass_dnd", false)) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = notificationManager.getNotificationChannel(channelId)
                channel?.setBypassDnd(true)
            }
        }

        // Добавляем ID комнаты в экстра для возможности удаления программно
        val extras = android.os.Bundle()
        extras.putString("room_id", roomId)
        notificationBuilder.addExtras(extras)

        // Reply action (inline reply from notification)
        val replyIntent = Intent(this, NotificationReplyReceiver::class.java).apply {
            putExtra(NotificationReplyReceiver.EXTRA_ROOM_ID, roomId)
            putExtra("room_id", roomId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            roomId.hashCode() + 10000,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(NotificationReplyReceiver.REPLY_KEY)
            .setLabel(getString(R.string.reply))
            .build()
        notificationBuilder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.send_24,
                getString(R.string.reply),
                replyPendingIntent
            ).addRemoteInput(remoteInput).build()
        )

        // Применяем стиль — MessagingStyle обязателен для inline reply на Android 12+
        val style = prefs.getString("notification_style", "standard")
        val useMessagingStyle = style == "messaging" || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
        if (useMessagingStyle) {
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
        fun setNotificationSound(context: Context, roomId: String, soundUri: String?) {
            val prefs = context.getSharedPreferences("notification_sounds", MODE_PRIVATE)
            if (soundUri != null) {
                prefs.edit().putString(roomId, soundUri).apply()
            } else {
                prefs.edit().remove(roomId).apply()
            }
        }

        fun getNotificationSound(context: Context, roomId: String): Uri? {
            val prefs = context.getSharedPreferences("notification_sounds", MODE_PRIVATE)
            val uri = prefs.getString(roomId, null)
            return if (uri != null) Uri.parse(uri) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

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

        fun showNotificationFromStream(context: Context, title: String, body: String, roomId: String) {
            val channelId = "lavender_messages_v2"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val channel = NotificationChannel(
                    channelId,
                    context.getString(R.string.lavender_messages_channel),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.lavender_messages_channel_desc)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 300, 200, 300)
                    enableLights(true)
                    setShowBadge(true)
                    setSound(defaultSoundUri, android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, lavender.client.android.NewChatActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("USERNAME", "")
                putExtra("SERVER_ADDRESS", lavender.client.android.data.session.CredentialStore.getServerAddress(context) ?: "")
                putExtra("ROOM_ID", roomId)
                putExtra("CHAT_NAME", title)
                putExtra("IS_DIRECT", !roomId.startsWith("group_") && roomId != "general")
                putExtra("from_notification", true)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                roomId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setColor(ContextCompat.getColor(context, R.color.lavender_mist))
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true)
                .setVibrate(longArrayOf(0, 300, 200, 300))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            val soundPrefs = context.getSharedPreferences("notification_sounds", Context.MODE_PRIVATE)
            val customSoundUri = soundPrefs.getString(roomId, null)
            if (customSoundUri != null) {
                notificationBuilder.setSound(Uri.parse(customSoundUri))
            } else {
                val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                notificationBuilder.setSound(defaultSound)
            }

            val extras = android.os.Bundle()
            extras.putString("room_id", roomId)
            notificationBuilder.addExtras(extras)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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
            Log.d("FCM", "Stream notification shown for room: $roomId")
        }
    }
}
