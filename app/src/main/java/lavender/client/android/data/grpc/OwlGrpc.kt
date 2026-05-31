package lavender.client.android.data.grpc

import io.grpc.MethodDescriptor
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import lavender.client.android.data.proto.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

// ======= OWL AI Assistant =======

class OWLRequestMarshaller : MethodDescriptor.Marshaller<OWLRequestProto> {
    override fun stream(v: OWLRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
        if (v.message.isNotEmpty()) cos.writeString(2, v.message)
        if (v.sessionId.isNotEmpty()) cos.writeString(3, v.sessionId)
        if (v.model.isNotEmpty()) cos.writeString(4, v.model)
        if (v.apiKey.isNotEmpty()) cos.writeString(5, v.apiKey)
        cos.flush()
        return ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(s: java.io.InputStream): OWLRequestProto = OWLRequestProto()
}

class OWLResponseMarshaller : MethodDescriptor.Marshaller<OWLResponseProto> {
    override fun stream(v: OWLResponseProto): java.io.InputStream {
        val baos = ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.text.isNotEmpty()) cos.writeString(1, v.text)
        if (v.finished) cos.writeBool(2, v.finished)
        if (v.error.isNotEmpty()) cos.writeString(3, v.error)
        cos.flush()
        return ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(s: java.io.InputStream): OWLResponseProto {
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
        return OWLResponseProto(text, finished, error)
    }
}

// OWL streaming state
private val _owlResponses = MutableSharedFlow<OWLResponseProto>(extraBufferCapacity = 64)
val owlResponses: SharedFlow<OWLResponseProto> = _owlResponses

private val _owlTyping = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
val owlTyping: SharedFlow<Boolean> = _owlTyping

fun chatWithOWL(
    userId: String,
    message: String,
    chatId: String,
    modelId: String,
    apiKey: String,
    scope: CoroutineScope,
    onResponse: (OWLResponseProto) -> Unit
) {
    val channel = RealGrpcClient.getChannel() ?: return
    val methodDesc = MethodDescriptor.newBuilder<OWLRequestProto, OWLResponseProto>()
        .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
        .setFullMethodName("messenger.ChatService/ChatWithOWL")
        .setRequestMarshaller(OWLRequestMarshaller())
        .setResponseMarshaller(OWLResponseMarshaller())
        .build()

    val request = OWLRequestProto(
        userId = userId,
        message = message,
        sessionId = chatId,
        model = modelId,
        apiKey = apiKey
    )

    scope.launch(Dispatchers.IO) {
        try {
            _owlTyping.emit(true)
            val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)

            call.start(object : io.grpc.ClientCall.Listener<OWLResponseProto>() {
                override fun onMessage(message: OWLResponseProto) {
                    _owlResponses.tryEmit(message)
                    onResponse(message)
                }

                override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                    _owlTyping.tryEmit(false)
                    if (!status.isOk) {
                        val errorResp = OWLResponseProto(
                            text = "",
                            finished = true,
                            error = status.description ?: "Connection error: ${status.code}"
                        )
                        _owlResponses.tryEmit(errorResp)
                        onResponse(errorResp)
                    }
                }
            }, io.grpc.Metadata())

            call.sendMessage(request)
            call.halfClose()
            call.request(Int.MAX_VALUE)
        } catch (e: Exception) {
            _owlTyping.emit(false)
            val errorResp = OWLResponseProto(text = "", finished = true, error = e.message ?: "Unknown error")
            _owlResponses.tryEmit(errorResp)
            onResponse(errorResp)
        }
    }
}

// ===== Unary RPC helpers =====

suspend fun createOwlChat(userId: String, name: String): String = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel() ?: return@withContext ""
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
                var chatId = ""
                var success = false
                var msg = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> chatId = cis.readString()
                        2 -> success = cis.readBool()
                        3 -> msg = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return CreateOwlChatResponseProto(chatId, success, msg)
            }
        })
        .build()

    val request = CreateOwlChatRequestProto(userId = userId, name = name)
    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<String>()

    call.start(object : io.grpc.ClientCall.Listener<CreateOwlChatResponseProto>() {
        override fun onMessage(message: CreateOwlChatResponseProto) {
            if (message.success) {
                result.complete(message.chatId)
            } else {
                result.complete("")
            }
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete("")
        }
    }, io.grpc.Metadata())

    call.sendMessage(request)
    call.halfClose()
    call.request(1)

    return@withContext result.await()
}

suspend fun deleteOwlChat(chatId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel() ?: return@withContext false
    val methodDesc = MethodDescriptor.newBuilder<DeleteOwlChatRequestProto, DeleteOwlChatResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/DeleteOwlChat")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<DeleteOwlChatRequestProto> {
            override fun stream(v: DeleteOwlChatRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.chatId.isNotEmpty()) cos.writeString(1, v.chatId)
                if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): DeleteOwlChatRequestProto = DeleteOwlChatRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<DeleteOwlChatResponseProto> {
            override fun stream(v: DeleteOwlChatResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): DeleteOwlChatResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var success = false
                var msg = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        2 -> msg = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return DeleteOwlChatResponseProto(success, msg)
            }
        })
        .build()

    val request = DeleteOwlChatRequestProto(chatId = chatId, userId = userId)
    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<Boolean>()

    call.start(object : io.grpc.ClientCall.Listener<DeleteOwlChatResponseProto>() {
        override fun onMessage(message: DeleteOwlChatResponseProto) {
            result.complete(message.success)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(false)
        }
    }, io.grpc.Metadata())

    call.sendMessage(request)
    call.halfClose()
    call.request(1)

    return@withContext result.await()
}

suspend fun updateOwlSettings(chatId: String, userId: String, apiKey: String, model: String): Boolean = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel() ?: return@withContext false
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
                var success = false
                var msg = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        2 -> msg = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return UpdateOwlSettingsResponseProto(success, msg)
            }
        })
        .build()

    val request = UpdateOwlSettingsRequestProto(chatId = chatId, userId = userId, apiKey = apiKey, model = model)
    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<Boolean>()

    call.start(object : io.grpc.ClientCall.Listener<UpdateOwlSettingsResponseProto>() {
        override fun onMessage(message: UpdateOwlSettingsResponseProto) {
            result.complete(message.success)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(false)
        }
    }, io.grpc.Metadata())

    call.sendMessage(request)
    call.halfClose()
    call.request(1)

    return@withContext result.await()
}

suspend fun getOwlHistory(chatId: String, userId: String): List<OwlHistoryMessageProto> = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel() ?: return@withContext emptyList()
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
                            val msgBytes = cis.readByteArray()
                            if (msgBytes.isNotEmpty()) {
                                try {
                                    messages.add(OwlHistoryMessageProto.parseFrom(msgBytes))
                                } catch (_: Exception) {}
                            }
                        }
                        else -> cis.skipField(tag)
                    }
                }
                return GetOwlHistoryResponseProto(messages)
            }
        })
        .build()

    val request = GetOwlHistoryRequestProto(chatId = chatId, userId = userId)
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

    call.sendMessage(request)
    call.halfClose()
    call.request(1)

    return@withContext result.await()
}

fun getOwlSettingApiKey(chatId: String): String {
    // TODO: implement server-side per-chat settings retrieval
    // For now, return empty (will use local prefs)
    return ""
}

fun getOwlSettingModel(chatId: String): String {
    return ""
}
