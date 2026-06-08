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
    val channel = RealGrpcClient.getChannel() ?: run { Log.w("OwlGrpc", "subscribeNotifications: channel is null"); return }
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
                        else -> cis.skipField(tag)
                    }
                }
                return ServerNotificationProto(id, type, title, message, timestamp, metadata)
            }
        })
        .build()

    val request = SubscribeNotificationsRequestProto(userId = userId, types = types)

    scope.launch(Dispatchers.IO) {
        try {
            val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
            call.start(object : io.grpc.ClientCall.Listener<ServerNotificationProto>() {
                override fun onMessage(msg: ServerNotificationProto) {
                    _serverNotifications.tryEmit(msg)
                }
                override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                    if (!status.isOk) {
                        Log.w("OwlGrpc", "subscribeNotifications closed: ${status.code} ${status.description}")
                    }
                }
            }, io.grpc.Metadata())
            call.sendMessage(request)
            call.halfClose()
            call.request(Int.MAX_VALUE)
        } catch (e: Exception) {
            Log.e("OwlGrpc", "subscribeNotifications error", e)
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
                                            else -> inner.skipField(innerTag)
                                        }
                                    }
                                    notifications.add(ServerNotificationProto(id, type, title, message, timestamp, metadata))
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

// ======= OWL Chat streaming =======

fun chatWithOwl(
    userId: String,
    sessionId: String,
    message: String,
    scope: CoroutineScope,
    onResponse: (text: String, finished: Boolean, error: String) -> Unit
) {
    val channel = RealGrpcClient.getChannel() ?: run { Log.w("OwlGrpc", "chatWithOwl: channel is null"); return }
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

    scope.launch(Dispatchers.IO) {
        try {
            _owlTyping.emit(true)
            val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)

            call.start(object : io.grpc.ClientCall.Listener<OwlResponseProto>() {
                override fun onMessage(msg: OwlResponseProto) {
                    _owlResponses.tryEmit(msg)
                    onResponse(msg.text, msg.finished, msg.error)
                }

                override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                    _owlTyping.tryEmit(false)
                    if (!status.isOk) {
                        val errResp = OwlResponseProto(text = "", finished = true,
                            error = status.description ?: "Connection error: ${status.code}")
                        _owlResponses.tryEmit(errResp)
                        onResponse("", true, errResp.error)
                    }
                }
            }, io.grpc.Metadata())

            call.sendMessage(request)
            call.halfClose()
            call.request(Int.MAX_VALUE)
        } catch (e: Exception) {
            _owlTyping.emit(false)
            val errResp = OwlResponseProto(text = "", finished = true, error = e.message ?: "Unknown error")
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
                var apiKey = ""; var model = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> apiKey = cis.readString()
                        2 -> model = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return GetOwlSettingsResponseProto(apiKey, model)
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
