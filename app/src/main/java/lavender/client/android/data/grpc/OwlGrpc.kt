package lavender.client.android.data.grpc

import android.util.Log
import io.grpc.MethodDescriptor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import lavender.client.android.data.proto.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

// ======= OWL AI Assistant — completely separate from Hermes orchestrator =======

// OWL streaming state — isolated from Hermes hermesTyping/hermesResponses
private val _owlResponses = MutableSharedFlow<OwlResponseProto>(extraBufferCapacity = 64)
val owlResponses: SharedFlow<OwlResponseProto> = _owlResponses

private val _owlTyping = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
val owlTyping: SharedFlow<Boolean> = _owlTyping

// ======= OWL Marshallers =======

class OwlRequestMarshaller : MethodDescriptor.Marshaller<OwlRequestProto> {
    override fun stream(v: OwlRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
        if (v.message.isNotEmpty()) cos.writeString(2, v.message)
        if (v.sessionId.isNotEmpty()) cos.writeString(3, v.sessionId)
        cos.flush()
        return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): OwlRequestProto = OwlRequestProto()
}

class OwlResponseMarshaller : MethodDescriptor.Marshaller<OwlResponseProto> {
    override fun stream(v: OwlResponseProto): java.io.InputStream {
        val baos = ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.text.isNotEmpty()) cos.writeString(1, v.text)
        if (v.finished) cos.writeBool(2, v.finished)
        if (v.error.isNotEmpty()) cos.writeString(3, v.error)
        cos.flush()
        return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): OwlResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var text = ""
        var finished = false
        var error = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> text = cis.readString()
                2 -> finished = cis.readBool()
                3 -> error = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return OwlResponseProto(text, finished, error)
    }
}

// ======= OWL Bot Commands — unary RPCs =======

suspend fun processBotCommand(
    userId: String,
    username: String,
    chatId: String,
    command: String,
    args: List<String>
): BotCommandResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("OwlGrpc", "processBotCommand: channel dead")
        return@withContext BotCommandResponseProto(success = false, errorMessage = "Connection lost")
    }

    val methodDesc = MethodDescriptor.newBuilder<BotCommandRequestProto, BotCommandResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/ProcessBotCommand")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<BotCommandRequestProto> {
            override fun stream(v: BotCommandRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
                if (v.username.isNotEmpty()) cos.writeString(2, v.username)
                if (v.chatId.isNotEmpty()) cos.writeString(3, v.chatId)
                if (v.command.isNotEmpty()) cos.writeString(4, v.command)
                v.args.forEach { arg -> cos.writeString(5, arg) }
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): BotCommandRequestProto = BotCommandRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<BotCommandResponseProto> {
            override fun stream(v: BotCommandResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): BotCommandResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var success = false
                var responseText = ""
                var isError = false
                var errorMessage = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        2 -> responseText = cis.readString()
                        3 -> isError = cis.readBool()
                        4 -> errorMessage = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return BotCommandResponseProto(success, responseText, isError, errorMessage)
            }
        })
        .build()

    val request = BotCommandRequestProto(
        userId = userId,
        username = username,
        chatId = chatId,
        command = command,
        args = args
    )

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<BotCommandResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<BotCommandResponseProto>() {
        override fun onMessage(message: BotCommandResponseProto) {
            result.complete(message)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(
                BotCommandResponseProto(success = false, errorMessage = "Connection error: ${status.code}")
            )
        }
    }, io.grpc.Metadata())

    call.sendMessage(request)
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(30000) { result.await() }
        ?: BotCommandResponseProto(success = false, errorMessage = "Timeout")
}

suspend fun getBotCommands(userId: String = ""): List<BotCommandInfoProto> = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("OwlGrpc", "getBotCommands: channel dead")
        return@withContext emptyList()
    }

    val methodDesc = MethodDescriptor.newBuilder<GetBotCommandsRequestProto, GetBotCommandsResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/GetBotCommands")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GetBotCommandsRequestProto> {
            override fun stream(v: GetBotCommandsRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): GetBotCommandsRequestProto = GetBotCommandsRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<GetBotCommandsResponseProto> {
            override fun stream(v: GetBotCommandsResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GetBotCommandsResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                val commands = mutableListOf<BotCommandInfoProto>()
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
                                    var command = ""; var description = ""; var usage = ""; var category = ""
                                    while (!inner.isAtEnd) {
                                        val innerTag = inner.readTag()
                                        if (innerTag == 0) break
                                        when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                            1 -> command = inner.readString()
                                            2 -> description = inner.readString()
                                            3 -> usage = inner.readString()
                                            4 -> category = inner.readString()
                                            else -> inner.skipField(innerTag)
                                        }
                                    }
                                    commands.add(BotCommandInfoProto(command, description, usage, category))
                                } catch (_: Exception) {}
                            }
                        }
                        else -> cis.skipField(tag)
                    }
                }
                return GetBotCommandsResponseProto(commands)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<List<BotCommandInfoProto>>()

    call.start(object : io.grpc.ClientCall.Listener<GetBotCommandsResponseProto>() {
        override fun onMessage(message: GetBotCommandsResponseProto) {
            result.complete(message.commands)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(emptyList())
        }
    }, io.grpc.Metadata())

    call.sendMessage(GetBotCommandsRequestProto(userId = userId))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: emptyList()
}

suspend fun getOWLStatus(userId: String = ""): OWLStatusResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("OwlGrpc", "getOWLStatus: channel dead")
        return@withContext OWLStatusResponseProto(available = false, status = "offline")
    }

    val methodDesc = MethodDescriptor.newBuilder<OWLStatusRequestProto, OWLStatusResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/GetOWLStatus")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<OWLStatusRequestProto> {
            override fun stream(v: OWLStatusRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): OWLStatusRequestProto = OWLStatusRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<OWLStatusResponseProto> {
            override fun stream(v: OWLStatusResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): OWLStatusResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var available = false; var model = ""; var queueLength = 0; var status = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> available = cis.readBool()
                        2 -> model = cis.readString()
                        3 -> queueLength = cis.readInt32()
                        4 -> status = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return OWLStatusResponseProto(available, model, queueLength, status)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<OWLStatusResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<OWLStatusResponseProto>() {
        override fun onMessage(message: OWLStatusResponseProto) {
            result.complete(message)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(OWLStatusResponseProto(available = false, status = "offline"))
        }
    }, io.grpc.Metadata())

    call.sendMessage(OWLStatusRequestProto(userId = userId))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() }
        ?: OWLStatusResponseProto(available = false, status = "offline")
}

// ======= Server Notifications =======

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
                Log.w("OwlGrpc", "subscribeNotifications: channel dead, waiting...")
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
                                            } catch (_: Exception) {}
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

                Log.d("OwlGrpc", "Subscribing to notifications (retryDelay=${retryDelay}ms)")
                retryDelay = 3000L // Reset on successful subscribe

                val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
                val streamDone = kotlinx.coroutines.CompletableDeferred<Unit>()

                call.start(object : io.grpc.ClientCall.Listener<ServerNotificationProto>() {
                    override fun onMessage(msg: ServerNotificationProto) {
                        _serverNotifications.tryEmit(msg)
                    }
                    override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                        if (!status.isOk) {
                            Log.w("OwlGrpc", "subscribeNotifications closed: ${status.code} ${status.description}")
                        }
                        streamDone.complete(Unit)
                    }
                }, io.grpc.Metadata())
                call.sendMessage(request)
                call.halfClose()
                call.request(Int.MAX_VALUE)

                // Block until stream closes, then retry
                streamDone.await()
                Log.w("OwlGrpc", "Notification stream ended, reconnecting in ${retryDelay}ms...")
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)

            } catch (e: CancellationException) {
                throw e // Don't swallow cancellation
            } catch (e: Exception) {
                Log.e("OwlGrpc", "subscribeNotifications error, retrying in ${retryDelay}ms", e)
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
        Log.w("OwlGrpc", "getNotificationHistory: channel dead")
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
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                            7 -> isRead = inner.readBool()
                                            else -> inner.skipField(innerTag)
                                        }
                                    }
                                    notifications.add(ServerNotificationProto(id, type, title, message, timestamp, metadata, isRead))
                                } catch (_: Exception) {}
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
        Log.w("OwlGrpc", "markNotificationsRead: channel dead")
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
        Log.w("OwlGrpc", "getUnreadCount: channel dead")
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

// ======= OWL Chat creation =======

suspend fun createOwlChat(userId: String, name: String = ""): CreateOwlChatResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("OwlGrpc", "createOwlChat: channel dead")
        return@withContext CreateOwlChatResponseProto()
    }

    val methodDesc = MethodDescriptor.newBuilder<CreateOwlChatRequestProto, CreateOwlChatResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/CreateOwlChat")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<CreateOwlChatRequestProto> {
            override fun stream(v: CreateOwlChatRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
                if (v.name.isNotEmpty()) cos.writeString(2, v.name)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): CreateOwlChatRequestProto = CreateOwlChatRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<CreateOwlChatResponseProto> {
            override fun stream(v: CreateOwlChatResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): CreateOwlChatResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var chatId = ""; var name = ""; var success = false; var message = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> chatId = cis.readString()
                        2 -> success = cis.readBool()
                        3 -> message = cis.readString()
                        4 -> name = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return CreateOwlChatResponseProto(chatId, name, success, message)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<CreateOwlChatResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<CreateOwlChatResponseProto>() {
        override fun onMessage(message: CreateOwlChatResponseProto) {
            result.complete(message)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(CreateOwlChatResponseProto())
        }
    }, io.grpc.Metadata())

    call.sendMessage(CreateOwlChatRequestProto(userId = userId, name = name))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: CreateOwlChatResponseProto()
}

// ======= OWL Chat streaming =======

fun chatWithOwl(
    userId: String,
    sessionId: String,
    message: String,
    scope: CoroutineScope,
    onResponse: (text: String, finished: Boolean, error: String) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        var retryDelay = 3000L
        val maxRetryDelay = 30000L
        var attempt = 0
        val maxRetries = 10

        while (attempt < maxRetries && isActive) {
            val channel = RealGrpcClient.getChannel()
            if (channel == null || channel.isShutdown || channel.isTerminated) {
                Log.w("OwlGrpc", "chatWithOwl: channel dead, waiting ${retryDelay}ms...")
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
                attempt++
                continue
            }

            try {
                val methodDesc = MethodDescriptor.newBuilder<OwlRequestProto, OwlResponseProto>()
                    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                    .setFullMethodName("messenger.ChatService/ChatWithOWL")
                    .setRequestMarshaller(OwlRequestMarshaller())
                    .setResponseMarshaller(OwlResponseMarshaller())
                    .build()

                val request = OwlRequestProto(
                    userId = userId,
                    message = message,
                    sessionId = sessionId
                )

                _owlTyping.emit(true)
                val streamDone = kotlinx.coroutines.CompletableDeferred<Boolean>()
                var hadError = false

                val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
                call.start(object : io.grpc.ClientCall.Listener<OwlResponseProto>() {
                    override fun onMessage(msg: OwlResponseProto) {
                        _owlResponses.tryEmit(msg)
                        onResponse(msg.text, msg.finished, msg.error)
                        retryDelay = 3000L
                        attempt = 0
                    }
                    override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                        _owlTyping.tryEmit(false)
                        if (!status.isOk) {
                            Log.w("OwlGrpc", "chatWithOwl closed: ${status.code} ${status.description}")
                            hadError = true
                            val errResp = OwlResponseProto(text = "", finished = true,
                                error = status.description ?: "Connection error: ${status.code}")
                            _owlResponses.tryEmit(errResp)
                            onResponse("", true, errResp.error)
                        }
                        streamDone.complete(hadError)
                    }
                }, io.grpc.Metadata())
                call.sendMessage(request)
                call.halfClose()
                call.request(Int.MAX_VALUE)

                val streamHadError = streamDone.await()
                if (!streamHadError) {
                    return@launch
                }

                attempt++
                if (attempt < maxRetries) {
                    Log.d("OwlGrpc", "Retrying chatWithOwl in ${retryDelay}ms (attempt $attempt/$maxRetries)")
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("OwlGrpc", "chatWithOwl error: ${e.message}")
                _owlTyping.tryEmit(false)
                val errResp = OwlResponseProto(text = "", finished = true, error = e.message ?: "Unknown error")
                _owlResponses.tryEmit(errResp)
                onResponse("", true, errResp.error)

                attempt++
                if (attempt < maxRetries) {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
                }
            }
        }

        if (attempt >= maxRetries) {
            Log.e("OwlGrpc", "chatWithOwl: max retries exceeded")
            val errResp = OwlResponseProto(text = "", finished = true, error = "Connection lost after $maxRetries attempts")
            _owlResponses.tryEmit(errResp)
            onResponse("", true, errResp.error)
        }
    }
}

// ======= OWL Settings =======

suspend fun getOwlSettings(chatId: String, userId: String): GetOwlSettingsResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("OwlGrpc", "getOwlSettings: channel dead")
        return@withContext GetOwlSettingsResponseProto()
    }

    val methodDesc = MethodDescriptor.newBuilder<GetOwlSettingsRequestProto, GetOwlSettingsResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/GetOwlSettings")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GetOwlSettingsRequestProto> {
            override fun stream(v: GetOwlSettingsRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId)
                if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): GetOwlSettingsRequestProto = GetOwlSettingsRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<GetOwlSettingsResponseProto> {
            override fun stream(v: GetOwlSettingsResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GetOwlSettingsResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var apiKey = ""; var model = ""; var isUsingCustomKey = false
                var remaining = 0; var limit = 0; var windowSeconds = 0
                val freeModels = mutableListOf<FreeModelInfoProto>()
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> apiKey = cis.readString()
                        2 -> model = cis.readString()
                        3 -> isUsingCustomKey = cis.readBool()
                        4 -> {
                            val len = cis.readRawVarint32()
                            val msgBytes = cis.readRawBytes(len)
                            if (msgBytes.isNotEmpty()) {
                                try {
                                    val inner = com.google.protobuf.CodedInputStream.newInstance(msgBytes)
                                    var modelId = ""; var displayName = ""; var sortOrder = 0
                                    while (!inner.isAtEnd) {
                                        val innerTag = inner.readTag()
                                        if (innerTag == 0) break
                                        when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                            1 -> modelId = inner.readString()
                                            2 -> displayName = inner.readString()
                                            3 -> sortOrder = inner.readInt32()
                                            else -> inner.skipField(innerTag)
                                        }
                                    }
                                    if (modelId.isNotEmpty()) {
                                        freeModels.add(FreeModelInfoProto(modelId, displayName, sortOrder))
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        5 -> remaining = cis.readInt32()
                        6 -> limit = cis.readInt32()
                        7 -> windowSeconds = cis.readInt32()
                        else -> cis.skipField(tag)
                    }
                }
                return GetOwlSettingsResponseProto(apiKey, model, isUsingCustomKey, freeModels, remaining, limit, windowSeconds)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<GetOwlSettingsResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<GetOwlSettingsResponseProto>() {
        override fun onMessage(message: GetOwlSettingsResponseProto) {
            result.complete(message)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(GetOwlSettingsResponseProto())
        }
    }, io.grpc.Metadata())

    call.sendMessage(GetOwlSettingsRequestProto(chatId = chatId, userId = userId))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: GetOwlSettingsResponseProto()
}

suspend fun updateOwlSettings(chatId: String, userId: String, apiKey: String, model: String): UpdateOwlSettingsResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("OwlGrpc", "updateOwlSettings: channel dead")
        return@withContext UpdateOwlSettingsResponseProto(success = false, message = "Connection lost")
    }

    val methodDesc = MethodDescriptor.newBuilder<UpdateOwlSettingsRequestProto, UpdateOwlSettingsResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/UpdateOwlSettings")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<UpdateOwlSettingsRequestProto> {
            override fun stream(v: UpdateOwlSettingsRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId)
                if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
                if (v.apiKey.isNotEmpty()) cos.writeString(3, v.apiKey)
                if (v.model.isNotEmpty()) cos.writeString(4, v.model)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): UpdateOwlSettingsRequestProto = UpdateOwlSettingsRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<UpdateOwlSettingsResponseProto> {
            override fun stream(v: UpdateOwlSettingsResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): UpdateOwlSettingsResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var success = false; var message = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        2 -> message = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return UpdateOwlSettingsResponseProto(success, message)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<UpdateOwlSettingsResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<UpdateOwlSettingsResponseProto>() {
        override fun onMessage(message: UpdateOwlSettingsResponseProto) {
            result.complete(message)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(
                UpdateOwlSettingsResponseProto(success = false, message = "Connection error: ${status.code}")
            )
        }
    }, io.grpc.Metadata())

    call.sendMessage(UpdateOwlSettingsRequestProto(chatId = chatId, userId = userId, apiKey = apiKey, model = model))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() }
        ?: UpdateOwlSettingsResponseProto(success = false, message = "Timeout")
}

// ======= Free OpenRouter Models =======

suspend fun getFreeModels(): List<FreeModelInfoProto> = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("OwlGrpc", "getFreeModels: channel dead")
        return@withContext emptyList()
    }

    val methodDesc = MethodDescriptor.newBuilder<GetFreeModelsRequestProto, GetFreeModelsResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/GetFreeModels")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GetFreeModelsRequestProto> {
            override fun stream(v: GetFreeModelsRequestProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GetFreeModelsRequestProto = GetFreeModelsRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<GetFreeModelsResponseProto> {
            override fun stream(v: GetFreeModelsResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GetFreeModelsResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                val models = mutableListOf<FreeModelInfoProto>()
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
                                    var modelId = ""; var displayName = ""; var sortOrder = 0
                                    while (!inner.isAtEnd) {
                                        val innerTag = inner.readTag()
                                        if (innerTag == 0) break
                                        when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                            1 -> modelId = inner.readString()
                                            2 -> displayName = inner.readString()
                                            3 -> sortOrder = inner.readInt32()
                                            else -> inner.skipField(innerTag)
                                        }
                                    }
                                    if (modelId.isNotEmpty()) {
                                        models.add(FreeModelInfoProto(modelId, displayName, sortOrder))
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        else -> cis.skipField(tag)
                    }
                }
                return GetFreeModelsResponseProto(models)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<List<FreeModelInfoProto>>()

    call.start(object : io.grpc.ClientCall.Listener<GetFreeModelsResponseProto>() {
        override fun onMessage(message: GetFreeModelsResponseProto) {
            result.complete(message.models)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(emptyList())
        }
    }, io.grpc.Metadata())

    call.sendMessage(GetFreeModelsRequestProto())
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: emptyList()
}

// ======= OWL History =======

suspend fun getOwlHistory(chatId: String, userId: String): List<OwlHistoryMessageProto> = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("OwlGrpc", "getOwlHistory: channel dead")
        return@withContext emptyList()
    }

    val methodDesc = MethodDescriptor.newBuilder<GetOwlHistoryRequestProto, GetOwlHistoryResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/GetOwlHistory")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GetOwlHistoryRequestProto> {
            override fun stream(v: GetOwlHistoryRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId)
                if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): GetOwlHistoryRequestProto = GetOwlHistoryRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<GetOwlHistoryResponseProto> {
            override fun stream(v: GetOwlHistoryResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GetOwlHistoryResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                val messages = mutableListOf<OwlHistoryMessageProto>()
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
                                    var role = ""; var content = ""; var createdAt = ""
                                    while (!inner.isAtEnd) {
                                        val innerTag = inner.readTag()
                                        if (innerTag == 0) break
                                        when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                            1 -> role = inner.readString()
                                            2 -> content = inner.readString()
                                            3 -> createdAt = inner.readString()
                                            else -> inner.skipField(innerTag)
                                        }
                                    }
                                    messages.add(OwlHistoryMessageProto(role = role, content = content, createdAt = createdAt))
                                } catch (_: Exception) {}
                            }
                        }
                        else -> cis.skipField(tag)
                    }
                }
                return GetOwlHistoryResponseProto(messagesList = messages)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<List<OwlHistoryMessageProto>>()

    call.start(object : io.grpc.ClientCall.Listener<GetOwlHistoryResponseProto>() {
        override fun onMessage(message: GetOwlHistoryResponseProto) {
            result.complete(message.messagesList)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(emptyList())
        }
    }, io.grpc.Metadata())

    call.sendMessage(GetOwlHistoryRequestProto(chatId = chatId, userId = userId))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: emptyList()
}
