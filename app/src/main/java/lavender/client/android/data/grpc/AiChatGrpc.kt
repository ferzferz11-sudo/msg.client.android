package lavender.client.android.data.grpc

import android.util.Log
import io.grpc.MethodDescriptor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import lavender.client.android.data.proto.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import lavender.client.android.data.grpc.GrpcClientExtensions.*

// ======= AI Chat (unified for OWL + Hermes) — v1.1.2.3 =======
// Replaces OwlGrpc.chatWithOwl + HermesGrpc.chatWithOrchestrator
// Provides single streaming entry point for both AI types

// AI Chat streaming state — unified flows
private val _aiChatResponses = MutableSharedFlow<AIChatResponseProto>(extraBufferCapacity = 64)
val aiChatResponses: SharedFlow<AIChatResponseProto> = _aiChatResponses

private val _aiChatTyping = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
val aiChatTyping: SharedFlow<Boolean> = _aiChatTyping

// ======= AI Chat Marshallers =======

class AIChatRequestMarshaller : MethodDescriptor.Marshaller<AIChatRequestProto> {
    override fun stream(v: AIChatRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
        if (v.sessionId.isNotEmpty()) cos.writeString(2, v.sessionId)
        if (v.message.isNotEmpty()) cos.writeString(3, v.message)
        if (v.agentId.isNotEmpty()) cos.writeString(4, v.agentId)
        cos.flush()
        return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): AIChatRequestProto = AIChatRequestProto()
}

class AIChatResponseMarshaller : MethodDescriptor.Marshaller<AIChatResponseProto> {
    override fun stream(v: AIChatResponseProto): java.io.InputStream {
        val baos = ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.token.isNotEmpty()) cos.writeString(1, v.token)
        if (v.finished) cos.writeBool(2, v.finished)
        if (v.error.isNotEmpty()) cos.writeString(3, v.error)
        cos.flush()
        return ByteArrayInputStream(baos.toByteArray())
    }
    override fun parse(s: java.io.InputStream): AIChatResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var token = ""
        var finished = false
        var error = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> token = cis.readString()
                2 -> finished = cis.readBool()
                3 -> error = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return AIChatResponseProto(token, finished, error)
    }
}

// ======= Main streaming method: ChatWithAI =======

fun chatWithAI(
    userId: String,
    sessionId: String,
    message: String,
    agentId: String = "",
    scope: CoroutineScope,
    onResponse: (token: String, finished: Boolean, error: String) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        var retryDelay = 3000L
        val maxRetryDelay = 30000L
        var attempt = 0
        val maxRetries = 10

        while (attempt < maxRetries && isActive) {
            val channel = RealGrpcClient.getChannel()
            if (channel == null || channel.isShutdown || channel.isTerminated) {
                Log.w("AiChatGrpc", "chatWithAI: channel dead, waiting ${retryDelay}ms...")
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
                attempt++
                continue
            }

            try {
                val methodDesc = MethodDescriptor.newBuilder<AIChatRequestProto, AIChatResponseProto>()
                    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                    .setFullMethodName("messenger.ChatService/ChatWithAI")
                    .setRequestMarshaller(AIChatRequestMarshaller())
                    .setResponseMarshaller(AIChatResponseMarshaller())
                    .build()

                val request = AIChatRequestProto(
                    userId = userId,
                    sessionId = sessionId,
                    message = message,
                    agentId = agentId
                )

                _aiChatTyping.emit(true)
                val streamDone = kotlinx.coroutines.CompletableDeferred<Boolean>()
                var hadError = false

                val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
                call.start(object : io.grpc.ClientCall.Listener<AIChatResponseProto>() {
                    override fun onMessage(msg: AIChatResponseProto) {
                        _aiChatResponses.tryEmit(msg)
                        onResponse(msg.token, msg.finished, msg.error)
                        retryDelay = 3000L
                        attempt = 0
                    }
                    override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                        _aiChatTyping.tryEmit(false)
                        if (!status.isOk) {
                            Log.w("AiChatGrpc", "chatWithAI closed: ${status.code} ${status.description}")
                            hadError = true
                            val errResp = AIChatResponseProto(
                                token = "",
                                finished = true,
                                error = status.description ?: "Connection error: ${status.code}"
                            )
                            _aiChatResponses.tryEmit(errResp)
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
                    Log.d("AiChatGrpc", "Retrying chatWithAI in ${retryDelay}ms (attempt $attempt/$maxRetries)")
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AiChatGrpc", "chatWithAI error: ${e.message}")
                _aiChatTyping.tryEmit(false)
                val errResp = AIChatResponseProto(
                    token = "",
                    finished = true,
                    error = e.message ?: "Unknown error"
                )
                _aiChatResponses.tryEmit(errResp)
                onResponse("", true, errResp.error)

                attempt++
                if (attempt < maxRetries) {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
                }
            }
        }

        if (attempt >= maxRetries) {
            Log.e("AiChatGrpc", "chatWithAI: max retries exceeded")
            val errResp = AIChatResponseProto(
                token = "",
                finished = true,
                error = "Connection lost after $maxRetries attempts"
            )
            _aiChatResponses.tryEmit(errResp)
            onResponse("", true, errResp.error)
        }
    }
}

// ======= Unary RPCs =======

suspend fun getAIChatHistory(
    sessionId: String,
    userId: String,
    limit: Int = 50
): List<AIChatMessageProto> = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("AiChatGrpc", "getAIChatHistory: channel dead")
        return@withContext emptyList()
    }

    val methodDesc = MethodDescriptor.newBuilder<GetAIChatHistoryRequestProto, GetAIChatHistoryResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/GetAIChatHistory")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GetAIChatHistoryRequestProto> {
            override fun stream(v: GetAIChatHistoryRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.sessionId.isNotEmpty()) cos.writeString(1, v.sessionId)
                if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
                if (v.limit > 0) cos.writeInt32(3, v.limit)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): GetAIChatHistoryRequestProto = GetAIChatHistoryRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<GetAIChatHistoryResponseProto> {
            override fun stream(v: GetAIChatHistoryResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GetAIChatHistoryResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                val messages = mutableListOf<AIChatMessageProto>()
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
                                    var role = ""; var content = ""; var agentId = ""; var createdAt = ""
                                    while (!inner.isAtEnd) {
                                        val innerTag = inner.readTag()
                                        if (innerTag == 0) break
                                        when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                            1 -> role = inner.readString()
                                            2 -> content = inner.readString()
                                            3 -> agentId = inner.readString()
                                            4 -> createdAt = inner.readString()
                                            else -> inner.skipField(innerTag)
                                        }
                                    }
                                    messages.add(AIChatMessageProto(role, content, agentId, createdAt))
                                } catch (_: Exception) {}
                            }
                        }
                        else -> cis.skipField(tag)
                    }
                }
                return GetAIChatHistoryResponseProto(messages)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<List<AIChatMessageProto>>()

    call.start(object : io.grpc.ClientCall.Listener<GetAIChatHistoryResponseProto>() {
        override fun onMessage(message: GetAIChatHistoryResponseProto) {
            result.complete(message.messages)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(emptyList())
        }
    }, io.grpc.Metadata())

    call.sendMessage(GetAIChatHistoryRequestProto(sessionId, userId, limit))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: emptyList()
}

suspend fun getAIChatSettings(
    sessionId: String,
    userId: String
): AIChatSettingsProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("AiChatGrpc", "getAIChatSettings: channel dead")
        return@withContext AIChatSettingsProto()
    }

    val methodDesc = MethodDescriptor.newBuilder<GetAIChatSettingsRequestProto, AIChatSettingsProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/GetAIChatSettings")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GetAIChatSettingsRequestProto> {
            override fun stream(v: GetAIChatSettingsRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.sessionId.isNotEmpty()) cos.writeString(1, v.sessionId)
                if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): GetAIChatSettingsRequestProto = GetAIChatSettingsRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<AIChatSettingsProto> {
            override fun stream(v: AIChatSettingsProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): AIChatSettingsProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var sessionId = ""; var userApiKey = ""; var model = ""
                var isUsingCustomKey = false; var remaining = 0; var limit = 0; var windowSeconds = 0
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> sessionId = cis.readString()
                        2 -> userApiKey = cis.readString()
                        3 -> model = cis.readString()
                        4 -> isUsingCustomKey = cis.readBool()
                        5 -> remaining = cis.readInt32()
                        6 -> limit = cis.readInt32()
                        7 -> windowSeconds = cis.readInt32()
                        else -> cis.skipField(tag)
                    }
                }
                return AIChatSettingsProto(sessionId, userApiKey, model, isUsingCustomKey, remaining, limit, windowSeconds)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<AIChatSettingsProto>()

    call.start(object : io.grpc.ClientCall.Listener<AIChatSettingsProto>() {
        override fun onMessage(message: AIChatSettingsProto) {
            result.complete(message)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(AIChatSettingsProto())
        }
    }, io.grpc.Metadata())

    call.sendMessage(GetAIChatSettingsRequestProto(sessionId, userId))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: AIChatSettingsProto()
}

suspend fun updateAIChatSettings(
    sessionId: String,
    userId: String,
    apiKey: String = "",
    model: String = ""
): UpdateAIChatSettingsResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("AiChatGrpc", "updateAIChatSettings: channel dead")
        return@withContext UpdateAIChatSettingsResponseProto(success = false, message = "Connection lost")
    }

    val methodDesc = MethodDescriptor.newBuilder<UpdateAIChatSettingsRequestProto, UpdateAIChatSettingsResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/UpdateAIChatSettings")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<UpdateAIChatSettingsRequestProto> {
            override fun stream(v: UpdateAIChatSettingsRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.sessionId.isNotEmpty()) cos.writeString(1, v.sessionId)
                if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
                if (v.apiKey.isNotEmpty()) cos.writeString(3, v.apiKey)
                if (v.model.isNotEmpty()) cos.writeString(4, v.model)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): UpdateAIChatSettingsRequestProto = UpdateAIChatSettingsRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<UpdateAIChatSettingsResponseProto> {
            override fun stream(v: UpdateAIChatSettingsResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): UpdateAIChatSettingsResponseProto {
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
                return UpdateAIChatSettingsResponseProto(success, message)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<UpdateAIChatSettingsResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<UpdateAIChatSettingsResponseProto>() {
        override fun onMessage(message: UpdateAIChatSettingsResponseProto) {
            result.complete(message)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(UpdateAIChatSettingsResponseProto(success = false, message = "Connection error: ${status.code}"))
        }
    }, io.grpc.Metadata())

    call.sendMessage(UpdateAIChatSettingsRequestProto(sessionId, userId, apiKey, model))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() }
        ?: UpdateAIChatSettingsResponseProto(success = false, message = "Timeout")
}
