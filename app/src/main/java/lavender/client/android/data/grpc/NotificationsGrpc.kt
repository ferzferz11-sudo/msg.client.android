package lavender.client.android.data.grpc

import android.util.Log
import io.grpc.MethodDescriptor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import lavender.client.android.data.proto.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

private val _serverNotifications = MutableSharedFlow<ServerNotificationProto>(extraBufferCapacity = 32)
val serverNotifications: SharedFlow<ServerNotificationProto> = _serverNotifications

fun subscribeNotifications(
    userId: String,
    types: List<String> = emptyList(),
    scope: CoroutineScope
) {
    scope.launch(Dispatchers.IO) {
        var retryDelay = 3000L
        val maxRetryDelay = 30000L
        while (isActive) {
            val channel = RealGrpcClient.getChannel()
            if (channel == null || channel.isShutdown || channel.isTerminated) {
                Log.w("NotificationsGrpc", "subscribeNotifications: channel dead, waiting...")
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
                continue
            }

            try {
                val methodDesc = MethodDescriptor.newBuilder<SubscribeNotificationsRequestProto, ServerNotificationProto>()
                    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                    .setFullMethodName("messenger.ChatService/SubscribeNotifications")
                    .setRequestMarshaller(object : MethodDescriptor.Marshaller<SubscribeNotificationsRequestProto> {
                        override fun stream(v: SubscribeNotificationsRequestProto): java.io.InputStream {
                            val baos = ByteArrayOutputStream()
                            val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                            if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
                            v.types.forEach { t -> cos.writeString(2, t) }
                            cos.flush()
                            return ByteArrayInputStream(baos.toByteArray())
                        }
                        override fun parse(s: java.io.InputStream): SubscribeNotificationsRequestProto = SubscribeNotificationsRequestProto()
                    })
                    .setResponseMarshaller(object : MethodDescriptor.Marshaller<ServerNotificationProto> {
                        override fun stream(v: ServerNotificationProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
                        override fun parse(s: java.io.InputStream): ServerNotificationProto {
                            val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                            var id = ""; var type = ""; var title = ""; var message = ""; var timestamp = ""
                            var isRead = false
                            val metadata = mutableMapOf<String, String>()
                            while (!cis.isAtEnd) {
                                val tag = cis.readTag()
                                if (tag == 0) break
                                when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                                    1 -> id = cis.readString()
                                    2 -> type = cis.readString()
                                    3 -> title = cis.readString()
                                    4 -> message = cis.readString()
                                    5 -> timestamp = cis.readString()
                                    6 -> {
                                        val len = cis.readRawVarint32()
                                        val entryBytes = cis.readRawBytes(len)
                                        if (entryBytes.isNotEmpty()) {
                                            try {
                                                val inner = com.google.protobuf.CodedInputStream.newInstance(entryBytes)
                                                var key = ""; var value = ""
                                                while (!inner.isAtEnd) {
                                                    val innerTag = inner.readTag()
                                                    if (innerTag == 0) break
                                                    when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                                        1 -> key = inner.readString()
                                                        2 -> value = inner.readString()
                                                        else -> inner.skipField(innerTag)
                                                    }
                                                }
                                                if (key.isNotEmpty()) metadata[key] = value
                                            } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }
                                        }
                                    }
                                    7 -> isRead = cis.readBool()
                                    else -> cis.skipField(tag)
                                }
                            }
                            return ServerNotificationProto(id, type, title, message, timestamp, metadata, isRead)
                        }
                    })
                    .build()

                val request = SubscribeNotificationsRequestProto(userId = userId, types = types)

                Log.d("NotificationsGrpc", "Subscribing to notifications (retryDelay=${retryDelay}ms)")
                retryDelay = 3000L

                val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
                val streamDone = kotlinx.coroutines.CompletableDeferred<Unit>()

                call.start(object : io.grpc.ClientCall.Listener<ServerNotificationProto>() {
                    override fun onMessage(msg: ServerNotificationProto) {
                        _serverNotifications.tryEmit(msg)
                    }
                    override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                        if (!status.isOk) {
                            Log.w("NotificationsGrpc", "subscribeNotifications closed: ${status.code} ${status.description}")
                        }
                        streamDone.complete(Unit)
                    }
                }, io.grpc.Metadata())
                call.sendMessage(request)
                call.halfClose()
                call.request(Int.MAX_VALUE)

                streamDone.await()
                Log.w("NotificationsGrpc", "Notification stream ended, reconnecting in ${retryDelay}ms...")
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NotificationsGrpc", "subscribeNotifications error, retrying in ${retryDelay}ms", e)
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
            }
        }
    }
}

suspend fun getNotificationHistory(
    userId: String,
    limit: Int = 50
): List<ServerNotificationProto> = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("NotificationsGrpc", "getNotificationHistory: channel dead")
        return@withContext emptyList()
    }

    val methodDesc = MethodDescriptor.newBuilder<GetNotificationHistoryRequestProto, GetNotificationHistoryResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/GetNotificationHistory")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GetNotificationHistoryRequestProto> {
            override fun stream(v: GetNotificationHistoryRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
                if (v.limit > 0) cos.writeInt32(2, v.limit)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): GetNotificationHistoryRequestProto = GetNotificationHistoryRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<GetNotificationHistoryResponseProto> {
            override fun stream(v: GetNotificationHistoryResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GetNotificationHistoryResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                val notifications = mutableListOf<ServerNotificationProto>()
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> {
                            val len = cis.readRawVarint32()
                            val msgBytes = cis.readRawBytes(len)
                            if (msgBytes.isNotEmpty()) {
                                try {
                                    val inner = com.google.protobuf.CodedInputStream.newInstance(msgBytes)
                                    var id = ""; var type = ""; var title = ""; var message = ""; var timestamp = ""
                                    var isRead = false
                                    val metadata = mutableMapOf<String, String>()
                                    while (!inner.isAtEnd) {
                                        val innerTag = inner.readTag()
                                        if (innerTag == 0) break
                                        when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                            1 -> id = inner.readString()
                                            2 -> type = inner.readString()
                                            3 -> title = inner.readString()
                                            4 -> message = inner.readString()
                                            5 -> timestamp = inner.readString()
                                            6 -> {
                                                val entryLen = inner.readRawVarint32()
                                                val entryBytes = inner.readRawBytes(entryLen)
                                                if (entryBytes.isNotEmpty()) {
                                                    try {
                                                        val entryCis = com.google.protobuf.CodedInputStream.newInstance(entryBytes)
                                                        var key = ""; var value = ""
                                                        while (!entryCis.isAtEnd) {
                                                            val entryTag = entryCis.readTag()
                                                            if (entryTag == 0) break
                                                            when (com.google.protobuf.WireFormat.getTagFieldNumber(entryTag)) {
                                                                1 -> key = entryCis.readString()
                                                                2 -> value = entryCis.readString()
                                                                else -> entryCis.skipField(entryTag)
                                                            }
                                                        }
                                                        if (key.isNotEmpty()) metadata[key] = value
                                                    } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }
                                                }
                                            }
                                            7 -> isRead = inner.readBool()
                                            else -> inner.skipField(innerTag)
                                        }
                                    }
                                    notifications.add(ServerNotificationProto(id, type, title, message, timestamp, metadata, isRead))
                                } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }
                            }
                        }
                        else -> cis.skipField(tag)
                    }
                }
                return GetNotificationHistoryResponseProto(notifications)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<List<ServerNotificationProto>>()

    call.start(object : io.grpc.ClientCall.Listener<GetNotificationHistoryResponseProto>() {
        override fun onMessage(message: GetNotificationHistoryResponseProto) {
            result.complete(message.notifications)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(emptyList())
        }
    }, io.grpc.Metadata())

    call.sendMessage(GetNotificationHistoryRequestProto(userId = userId, limit = limit))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: emptyList()
}

suspend fun markNotificationsRead(
    userId: String,
    notificationIds: List<String>
): Boolean = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("NotificationsGrpc", "markNotificationsRead: channel dead")
        return@withContext false
    }

    val methodDesc = MethodDescriptor.newBuilder<MarkNotificationReadRequestProto, MarkNotificationReadResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/MarkNotificationsRead")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<MarkNotificationReadRequestProto> {
            override fun stream(v: MarkNotificationReadRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
                v.notificationIds.forEach { id -> cos.writeString(2, id) }
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): MarkNotificationReadRequestProto = MarkNotificationReadRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<MarkNotificationReadResponseProto> {
            override fun stream(v: MarkNotificationReadResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): MarkNotificationReadResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var success = false
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        else -> cis.skipField(tag)
                    }
                }
                return MarkNotificationReadResponseProto(success)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<Boolean>()

    call.start(object : io.grpc.ClientCall.Listener<MarkNotificationReadResponseProto>() {
        override fun onMessage(message: MarkNotificationReadResponseProto) {
            result.complete(message.success)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(false)
        }
    }, io.grpc.Metadata())

    call.sendMessage(MarkNotificationReadRequestProto(userId = userId, notificationIds = notificationIds))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: false
}

suspend fun getUnreadCount(userId: String): Int = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("NotificationsGrpc", "getUnreadCount: channel dead")
        return@withContext 0
    }

    val methodDesc = MethodDescriptor.newBuilder<GetUnreadCountRequestProto, GetUnreadCountResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/GetUnreadCount")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GetUnreadCountRequestProto> {
            override fun stream(v: GetUnreadCountRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): GetUnreadCountRequestProto = GetUnreadCountRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<GetUnreadCountResponseProto> {
            override fun stream(v: GetUnreadCountResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GetUnreadCountResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var count = 0
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> count = cis.readInt32()
                        else -> cis.skipField(tag)
                    }
                }
                return GetUnreadCountResponseProto(count)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<Int>()

    call.start(object : io.grpc.ClientCall.Listener<GetUnreadCountResponseProto>() {
        override fun onMessage(message: GetUnreadCountResponseProto) {
            result.complete(message.count)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(0)
        }
    }, io.grpc.Metadata())

    call.sendMessage(GetUnreadCountRequestProto(userId = userId))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: 0
}
