package lavender.client.android.data.grpc

import android.util.Log
import io.grpc.MethodDescriptor
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import lavender.client.android.data.proto.*
import lavender.client.android.data.models.AppLog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

// ======= Hermes Multi-Agent Orchestrator =======

class OrchestratorRequestMarshaller : MethodDescriptor.Marshaller<OrchestratorRequestProto> {
    override fun stream(v: OrchestratorRequestProto): java.io.InputStream {
        val baos = ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
        if (v.sessionId.isNotEmpty()) cos.writeString(2, v.sessionId)
        if (v.message.isNotEmpty()) cos.writeString(3, v.message)
        if (v.agentId.isNotEmpty()) cos.writeString(4, v.agentId)
        if (v.mode.isNotEmpty()) cos.writeString(5, v.mode)
        cos.flush()
        return ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(s: java.io.InputStream): OrchestratorRequestProto = OrchestratorRequestProto()
}

class OrchestratorResponseMarshaller : MethodDescriptor.Marshaller<OrchestratorResponseProto> {
    override fun stream(v: OrchestratorResponseProto): java.io.InputStream {
        val baos = ByteArrayOutputStream()
        val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
        if (v.token.isNotEmpty()) cos.writeString(1, v.token)
        if (v.finished) cos.writeBool(2, v.finished)
        if (v.error.isNotEmpty()) cos.writeString(3, v.error)
        if (v.agentId.isNotEmpty()) cos.writeString(4, v.agentId)
        if (v.agentName.isNotEmpty()) cos.writeString(5, v.agentName)
        cos.flush()
        return ByteArrayInputStream(baos.toByteArray())
    }

    override fun parse(s: java.io.InputStream): OrchestratorResponseProto {
        val cis = com.google.protobuf.CodedInputStream.newInstance(s)
        var token = ""
        var finished = false
        var error = ""
        var agentId = ""
        var agentName = ""
        while (!cis.isAtEnd) {
            val tag = cis.readTag()
            if (tag == 0) break
            when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                1 -> token = cis.readString()
                2 -> finished = cis.readBool()
                3 -> error = cis.readString()
                4 -> agentId = cis.readString()
                5 -> agentName = cis.readString()
                else -> cis.skipField(tag)
            }
        }
        return OrchestratorResponseProto(token, finished, error, agentId, agentName)
    }
}

// Hermes streaming state
private val _hermesResponses = MutableSharedFlow<OrchestratorResponseProto>(extraBufferCapacity = 64)
val hermesResponses: SharedFlow<OrchestratorResponseProto> = _hermesResponses

private val _hermesTyping = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
val hermesTyping: SharedFlow<Boolean> = _hermesTyping

// ======= Main streaming method: ChatWithOrchestrator =======

fun chatWithOrchestrator(
    userId: String,
    sessionId: String,
    message: String,
    agentId: String = "",
    mode: String = "",
    scope: CoroutineScope,
    onResponse: (token: String, finished: Boolean, error: String?, agentId: String, agentName: String) -> Unit
) {
    scope.launch(Dispatchers.IO) {
        var retryDelay = 3000L
        val maxRetryDelay = 30000L
        var attempt = 0
        val maxRetries = 10
        // Stream timeout: 120 seconds (reset on each message)
        val streamTimeoutMs = 120_000L

        while (attempt < maxRetries && isActive) {
            val channel = RealGrpcClient.getChannel()
            if (channel == null || channel.isShutdown || channel.isTerminated) {
                Log.w("HermesGrpc", "chatWithOrchestrator: channel dead, waiting ${retryDelay}ms...")
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
                attempt++
                continue
            }

            try {
                val methodDesc = MethodDescriptor.newBuilder<OrchestratorRequestProto, OrchestratorResponseProto>()
                    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
                    .setFullMethodName("messenger.ChatService/ChatWithOrchestrator")
                    .setRequestMarshaller(OrchestratorRequestMarshaller())
                    .setResponseMarshaller(OrchestratorResponseMarshaller())
                    .build()

                val request = OrchestratorRequestProto(
                    userId = userId,
                    sessionId = sessionId,
                    message = message,
                    agentId = agentId,
                    mode = mode
                )

                _hermesTyping.emit(true)
                val streamDone = kotlinx.coroutines.CompletableDeferred<Boolean>()
                var hadError = false
                var timeoutJob: kotlinx.coroutines.Job? = null

                val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
                call.start(object : io.grpc.ClientCall.Listener<OrchestratorResponseProto>() {
                    override fun onMessage(message: OrchestratorResponseProto) {
                        timeoutJob?.cancel()
                        _hermesResponses.tryEmit(message)
                        onResponse(message.token, message.finished, message.error.takeIf { it.isNotEmpty() }, message.agentId, message.agentName)
                        retryDelay = 3000L
                        attempt = 0
                        timeoutJob = launch {
                            delay(streamTimeoutMs)
                            if (!message.finished) {
                                Log.w("HermesGrpc", "chatWithOrchestrator: stream timeout after ${streamTimeoutMs}ms")
                                hadError = true
                                val errorResp = OrchestratorResponseProto(
                                    token = "", finished = true,
                                    error = "Таймаут ожидания ответа (${streamTimeoutMs/1000}с). Попробуйте ещё раз.",
                                    agentId = "", agentName = ""
                                )
                                _hermesResponses.tryEmit(errorResp)
                                onResponse("", true, errorResp.error, "", "")
                                streamDone.complete(true)
                            }
                        }
                    }
                    override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                        timeoutJob?.cancel()
                        _hermesTyping.tryEmit(false)
                        if (!status.isOk) {
                            Log.w("HermesGrpc", "chatWithOrchestrator closed: ${status.code} ${status.description}")
                            hadError = true
                            val errorResp = OrchestratorResponseProto(
                                token = "",
                                finished = true,
                                error = status.description ?: "Connection error: ${status.code}",
                                agentId = "",
                                agentName = ""
                            )
                            _hermesResponses.tryEmit(errorResp)
                            onResponse("", true, errorResp.error, "", "")
                        }
                        streamDone.complete(hadError)
                    }
                }, io.grpc.Metadata())
                call.sendMessage(request)
                call.halfClose()
                call.request(Int.MAX_VALUE)

                timeoutJob = launch {
                    delay(streamTimeoutMs)
                    Log.w("HermesGrpc", "chatWithOrchestrator: initial stream timeout after ${streamTimeoutMs}ms")
                    hadError = true
                    val errorResp = OrchestratorResponseProto(
                        token = "", finished = true,
                        error = "Таймаут ожидания ответа (${streamTimeoutMs/1000}с). Попробуйте ещё раз.",
                        agentId = "", agentName = ""
                    )
                    _hermesResponses.tryEmit(errorResp)
                    onResponse("", true, errorResp.error, "", "")
                    streamDone.complete(true)
                }

                val streamHadError = streamDone.await()
                timeoutJob?.cancel()
                if (!streamHadError) {
                    // Stream completed normally — no retry needed
                    return@launch
                }

                // Stream had error — retry
                attempt++
                if (attempt < maxRetries) {
                    Log.d("HermesGrpc", "Retrying chatWithOrchestrator in ${retryDelay}ms (attempt $attempt/$maxRetries)")
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("HermesGrpc", "chatWithOrchestrator error: ${e.message}")
                _hermesTyping.tryEmit(false)
                val errorResp = OrchestratorResponseProto(
                    token = "",
                    finished = true,
                    error = e.message ?: "Unknown error",
                    agentId = "",
                    agentName = ""
                )
                _hermesResponses.tryEmit(errorResp)
                onResponse("", true, errorResp.error, "", "")

                attempt++
                if (attempt < maxRetries) {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
                }
            }
        }

        if (attempt >= maxRetries) {
            Log.e("HermesGrpc", "chatWithOrchestrator: max retries exceeded")
            val errorResp = OrchestratorResponseProto(
                token = "",
                finished = true,
                error = "Connection lost after $maxRetries attempts",
                agentId = "",
                agentName = ""
            )
            _hermesResponses.tryEmit(errorResp)
            onResponse("", true, errorResp.error, "", "")
        }
    }
}

// ======= Unary RPC helpers =======

suspend fun listAgents(userId: String = ""): List<AgentInfoProto> = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("HermesGrpc", "listAgents: channel dead")
        return@withContext emptyList()
    }
    val methodDesc = MethodDescriptor.newBuilder<ListAgentsRequestProto, ListAgentsResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/ListAgents")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<ListAgentsRequestProto> {
            override fun stream(v: ListAgentsRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): ListAgentsRequestProto = ListAgentsRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<ListAgentsResponseProto> {
            override fun stream(v: ListAgentsResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): ListAgentsResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                val agents = mutableListOf<AgentInfoProto>()
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
                                    var id = ""; var name = ""; var description = ""; var systemPrompt = ""; var isPreset = false; var model = ""
                                    while (!inner.isAtEnd) {
                                        val innerTag = inner.readTag()
                                        if (innerTag == 0) break
                                        when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                            1 -> id = inner.readString()
                                            2 -> name = inner.readString()
                                            3 -> description = inner.readString()
                                            4 -> isPreset = inner.readBool()       // field 4 = is_preset (bool)
                                            5 -> systemPrompt = inner.readString()  // field 5 = system_prompt (string)
                                            6 -> model = inner.readString()         // field 6 = model (string)
                                            else -> inner.skipField(innerTag)
                                        }
                                    }
                                    agents.add(AgentInfoProto(id, name, description, systemPrompt, isPreset, model))
                                } catch (_: Exception) {}
                            }
                        }
                        else -> cis.skipField(tag)
                    }
                }
                return ListAgentsResponseProto(agents)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<List<AgentInfoProto>>()

    call.start(object : io.grpc.ClientCall.Listener<ListAgentsResponseProto>() {
        override fun onMessage(message: ListAgentsResponseProto) {
            result.complete(message.agents)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(emptyList())
        }
    }, io.grpc.Metadata())

    call.sendMessage(ListAgentsRequestProto(userId))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: emptyList()
}

suspend fun listAgentPresets(): List<AgentPresetInfoProto> = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("HermesGrpc", "listAgentPresets: channel dead")
        return@withContext emptyList()
    }
    val methodDesc = MethodDescriptor.newBuilder<ListAgentPresetsRequestProto, ListAgentPresetsResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/ListAgentPresets")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<ListAgentPresetsRequestProto> {
            override fun stream(v: ListAgentPresetsRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.dummy) cos.writeBool(1, v.dummy)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): ListAgentPresetsRequestProto = ListAgentPresetsRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<ListAgentPresetsResponseProto> {
            override fun stream(v: ListAgentPresetsResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): ListAgentPresetsResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                val presets = mutableListOf<AgentPresetInfoProto>()
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
                                    var id = ""; var name = ""; var role = ""; var description = ""; var icon = ""; var maxTokens = 0
                                    while (!inner.isAtEnd) {
                                        val innerTag = inner.readTag()
                                        if (innerTag == 0) break
                                        when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                            1 -> id = inner.readString()
                                            2 -> name = inner.readString()
                                            3 -> role = inner.readString()
                                            4 -> description = inner.readString()
                                            5 -> icon = inner.readString()
                                            6 -> maxTokens = inner.readInt32()
                                            else -> inner.skipField(innerTag)
                                        }
                                    }
                                    presets.add(AgentPresetInfoProto(id, name, role, description, icon, maxTokens))
                                } catch (_: Exception) {}
                            }
                        }
                        else -> cis.skipField(tag)
                    }
                }
                return ListAgentPresetsResponseProto(presets)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<List<AgentPresetInfoProto>>()

    call.start(object : io.grpc.ClientCall.Listener<ListAgentPresetsResponseProto>() {
        override fun onMessage(message: ListAgentPresetsResponseProto) {
            result.complete(message.presets)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(emptyList())
        }
    }, io.grpc.Metadata())

    call.sendMessage(ListAgentPresetsRequestProto())
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: emptyList()
}

suspend fun createAgent(
    userId: String,
    presetId: String,
    customName: String = "",
    customPrompt: String = "",
    model: String = "",
    maxTokens: Int = 0
): CreateAgentResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("HermesGrpc", "createAgent: channel dead")
        return@withContext CreateAgentResponseProto()
    }
    val methodDesc = MethodDescriptor.newBuilder<CreateAgentRequestProto, CreateAgentResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/CreateAgent")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<CreateAgentRequestProto> {
            override fun stream(v: CreateAgentRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
                if (v.presetId.isNotEmpty()) cos.writeString(2, v.presetId)
                if (v.customName.isNotEmpty()) cos.writeString(3, v.customName)
                if (v.customPrompt.isNotEmpty()) cos.writeString(4, v.customPrompt)
                if (v.model.isNotEmpty()) cos.writeString(5, v.model)
                if (v.maxTokens > 0) cos.writeInt32(6, v.maxTokens)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): CreateAgentRequestProto = CreateAgentRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<CreateAgentResponseProto> {
            override fun stream(v: CreateAgentResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): CreateAgentResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var agentId = ""; var success = false; var message = ""
                var agentIdInner = ""; var agentName = ""; var agentDesc = ""; var agentRole = ""; var agentIsPreset = false; var agentIcon = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()       // field 1 = success (bool)
                        2 -> agentId = cis.readString()     // field 2 = agent_id (string)
                        3 -> message = cis.readString()     // field 3 = error (string)
                        4 -> {
                            val len = cis.readRawVarint32()
                            val msgBytes = cis.readRawBytes(len)
                            if (msgBytes.isNotEmpty()) {
                                try {
                                    val inner = com.google.protobuf.CodedInputStream.newInstance(msgBytes)
                                    while (!inner.isAtEnd) {
                                        val innerTag = inner.readTag()
                                        if (innerTag == 0) break
                                        when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                            1 -> agentIdInner = inner.readString()
                                            2 -> agentName = inner.readString()
                                            3 -> agentDesc = inner.readString()
                                            4 -> agentRole = inner.readString()
                                            5 -> agentIsPreset = inner.readBool()
                                            6 -> agentIcon = inner.readString()
                                            else -> inner.skipField(innerTag)
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        else -> cis.skipField(tag)
                    }
                }
                val agent = if (agentIdInner.isNotEmpty()) AgentInfoProto(agentIdInner, agentName, agentDesc, agentRole, agentIsPreset, agentIcon) else null
                return CreateAgentResponseProto(agentId, success, message, agent)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<CreateAgentResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<CreateAgentResponseProto>() {
        override fun onMessage(message: CreateAgentResponseProto) {
            result.complete(message)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(CreateAgentResponseProto())
        }
    }, io.grpc.Metadata())

    call.sendMessage(CreateAgentRequestProto(userId, presetId, customName, customPrompt, model, maxTokens))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: CreateAgentResponseProto()
}

suspend fun updateAgent(
    agentId: String,
    userId: String,
    name: String = "",
    systemPrompt: String = "",
    model: String = "",
    maxTokens: Int = 0
): Boolean = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) return@withContext false

    val methodDesc = MethodDescriptor.newBuilder<UpdateAgentRequestProto, UpdateAgentResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/UpdateAgent")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<UpdateAgentRequestProto> {
            override fun stream(v: UpdateAgentRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
                if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
                if (v.name.isNotEmpty()) cos.writeString(3, v.name)
                if (v.systemPrompt.isNotEmpty()) cos.writeString(4, v.systemPrompt)
                if (v.model.isNotEmpty()) cos.writeString(5, v.model)
                if (v.maxTokens > 0) cos.writeInt32(6, v.maxTokens)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): UpdateAgentRequestProto = UpdateAgentRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<UpdateAgentResponseProto> {
            override fun stream(v: UpdateAgentResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): UpdateAgentResponseProto {
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
                return UpdateAgentResponseProto(success, message)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<Boolean>()

    call.start(object : io.grpc.ClientCall.Listener<UpdateAgentResponseProto>() {
        override fun onMessage(message: UpdateAgentResponseProto) {
            result.complete(message.success)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(false)
        }
    }, io.grpc.Metadata())

    call.sendMessage(UpdateAgentRequestProto(agentId, userId, name, systemPrompt, model, maxTokens))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: false
}

suspend fun deleteAgent(agentId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) return@withContext false

    val methodDesc = MethodDescriptor.newBuilder<DeleteAgentRequestProto, DeleteAgentResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/DeleteAgent")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<DeleteAgentRequestProto> {
            override fun stream(v: DeleteAgentRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
                if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): DeleteAgentRequestProto = DeleteAgentRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<DeleteAgentResponseProto> {
            override fun stream(v: DeleteAgentResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): DeleteAgentResponseProto {
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
                return DeleteAgentResponseProto(success, message)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<Boolean>()

    call.start(object : io.grpc.ClientCall.Listener<DeleteAgentResponseProto>() {
        override fun onMessage(message: DeleteAgentResponseProto) {
            result.complete(message.success)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(false)
        }
    }, io.grpc.Metadata())

    call.sendMessage(DeleteAgentRequestProto(agentId, userId))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: false
}

suspend fun listUserAgents(userId: String): List<AgentInfoProto> = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) return@withContext emptyList()

    val methodDesc = MethodDescriptor.newBuilder<ListUserAgentsRequestProto, ListUserAgentsResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/ListUserAgents")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<ListUserAgentsRequestProto> {
            override fun stream(v: ListUserAgentsRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): ListUserAgentsRequestProto = ListUserAgentsRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<ListUserAgentsResponseProto> {
            override fun stream(v: ListUserAgentsResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): ListUserAgentsResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                val agents = mutableListOf<AgentInfoProto>()
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
                                    var id = ""; var name = ""; var description = ""; var systemPrompt = ""; var isPreset = false; var model = ""
                                    while (!inner.isAtEnd) {
                                        val innerTag = inner.readTag()
                                        if (innerTag == 0) break
                                        when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                            1 -> id = inner.readString()
                                            2 -> name = inner.readString()
                                            3 -> description = inner.readString()
                                            4 -> isPreset = inner.readBool()       // field 4 = is_preset (bool)
                                            5 -> systemPrompt = inner.readString()  // field 5 = system_prompt (string)
                                            6 -> model = inner.readString()         // field 6 = model (string)
                                            else -> inner.skipField(innerTag)
                                        }
                                    }
                                    agents.add(AgentInfoProto(id, name, description, systemPrompt, isPreset, model))
                                } catch (_: Exception) {}
                            }
                        }
                        else -> cis.skipField(tag)
                    }
                }
                return ListUserAgentsResponseProto(agents)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<List<AgentInfoProto>>()

    call.start(object : io.grpc.ClientCall.Listener<ListUserAgentsResponseProto>() {
        override fun onMessage(message: ListUserAgentsResponseProto) {
            result.complete(message.agents)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(emptyList())
        }
    }, io.grpc.Metadata())

    call.sendMessage(ListUserAgentsRequestProto(userId))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: emptyList()
}

suspend fun createHermesSession(
    userId: String,
    agentId: String = "",
    mode: String = ""
): CreateHermesSessionResponseProto = withContext(Dispatchers.IO) {
    android.util.Log.d("HermesGrpc", "createHermesSession: userId=$userId agentId=$agentId mode=$mode")
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        android.util.Log.e("HermesGrpc", "createHermesSession: CHANNEL IS NULL OR DEAD! channel=$channel isShutdown=${channel?.isShutdown} isTerminated=${channel?.isTerminated}")
        lavender.client.android.data.models.AppLog.error(
            "HermesGrpc:createHermesSession",
            "Channel is null or dead! userId=$userId channel=$channel isShutdown=${channel?.isShutdown} isTerminated=${channel?.isTerminated}"
        )
        return@withContext CreateHermesSessionResponseProto()
    }

    android.util.Log.d("HermesGrpc", "createHermesSession: channel OK, creating call...")

    val methodDesc = MethodDescriptor.newBuilder<CreateHermesSessionRequestProto, CreateHermesSessionResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/CreateHermesSession")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<CreateHermesSessionRequestProto> {
            override fun stream(v: CreateHermesSessionRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.userId.isNotEmpty()) cos.writeString(1, v.userId)
                // field 2 = name (server expects name, not agentId)
                // If agentId is provided use it as name, otherwise use empty string
                if (v.agentId.isNotEmpty()) cos.writeString(2, v.agentId)
                // Note: field 3 (mode) is not in server proto, skip it
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): CreateHermesSessionRequestProto = CreateHermesSessionRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<CreateHermesSessionResponseProto> {
            override fun stream(v: CreateHermesSessionResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): CreateHermesSessionResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var sessionId = ""; var success = false; var message = ""; var name = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()       // field 1 = success (bool)
                        2 -> sessionId = cis.readString()   // field 2 = session_id (string)
                        3 -> message = cis.readString()     // field 3 = error (string)
                        4 -> name = cis.readString()        // field 4 = name (string)
                        else -> cis.skipField(tag)
                    }
                }
                return CreateHermesSessionResponseProto(sessionId, success, message, name)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<CreateHermesSessionResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<CreateHermesSessionResponseProto>() {
        override fun onMessage(message: CreateHermesSessionResponseProto) {
            android.util.Log.d("HermesGrpc", "createHermesSession: response success=${message.success} sessionId=${message.sessionId} message=${message.message}")
            result.complete(message)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            android.util.Log.d("HermesGrpc", "createHermesSession: onClose status=${status.code} desc=${status.description}")
            if (!result.isCompleted) {
                lavender.client.android.data.models.AppLog.error(
                    "HermesGrpc:createHermesSession",
                    "Stream closed before response! status=${status.code} desc=${status.description}"
                )
                result.complete(CreateHermesSessionResponseProto())
            }
        }
    }, io.grpc.Metadata())

    call.sendMessage(CreateHermesSessionRequestProto(userId, agentId, mode))
    call.halfClose()
    call.request(1)

    val response = withTimeoutOrNull(10000) { result.await() } ?: CreateHermesSessionResponseProto()
    android.util.Log.d("HermesGrpc", "createHermesSession: final result success=${response.success} sessionId=${response.sessionId}")
    return@withContext response
}

suspend fun deleteHermesSession(sessionId: String, userId: String): Boolean = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) return@withContext false

    val methodDesc = MethodDescriptor.newBuilder<DeleteHermesSessionRequestProto, DeleteHermesSessionResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/DeleteHermesSession")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<DeleteHermesSessionRequestProto> {
            override fun stream(v: DeleteHermesSessionRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.sessionId.isNotEmpty()) cos.writeString(1, v.sessionId)
                if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): DeleteHermesSessionRequestProto = DeleteHermesSessionRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<DeleteHermesSessionResponseProto> {
            override fun stream(v: DeleteHermesSessionResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): DeleteHermesSessionResponseProto {
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
                return DeleteHermesSessionResponseProto(success, message)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<Boolean>()

    call.start(object : io.grpc.ClientCall.Listener<DeleteHermesSessionResponseProto>() {
        override fun onMessage(message: DeleteHermesSessionResponseProto) {
            result.complete(message.success)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(false)
        }
    }, io.grpc.Metadata())

    call.sendMessage(DeleteHermesSessionRequestProto(sessionId, userId))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: false
}

suspend fun getOrchestratorHistory(sessionId: String, limit: Int = 50): List<OrchestratorHistoryMessageProto> = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) return@withContext emptyList()

    val methodDesc = MethodDescriptor.newBuilder<GetOrchestratorHistoryRequestProto, GetOrchestratorHistoryResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/GetOrchestratorHistory")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GetOrchestratorHistoryRequestProto> {
            override fun stream(v: GetOrchestratorHistoryRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.sessionId.isNotEmpty()) cos.writeString(1, v.sessionId)
                if (v.limit > 0) cos.writeInt32(2, v.limit)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): GetOrchestratorHistoryRequestProto = GetOrchestratorHistoryRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<GetOrchestratorHistoryResponseProto> {
            override fun stream(v: GetOrchestratorHistoryResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GetOrchestratorHistoryResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                val messages = mutableListOf<OrchestratorHistoryMessageProto>()
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
                                    var role = ""; var content = ""; var agentId = ""; var agentName = ""; var createdAt = ""
                                    while (!inner.isAtEnd) {
                                        val innerTag = inner.readTag()
                                        if (innerTag == 0) break
                                        when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                            1 -> role = inner.readString()
                                            2 -> content = inner.readString()
                                            3 -> agentId = inner.readString()
                                            4 -> agentName = inner.readString()
                                            5 -> createdAt = inner.readString()
                                            else -> inner.skipField(innerTag)
                                        }
                                    }
                                    messages.add(OrchestratorHistoryMessageProto(role, content, agentId, agentName, createdAt))
                                } catch (_: Exception) {}
                            }
                        }
                        else -> cis.skipField(tag)
                    }
                }
                return GetOrchestratorHistoryResponseProto(messages)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<List<OrchestratorHistoryMessageProto>>()

    call.start(object : io.grpc.ClientCall.Listener<GetOrchestratorHistoryResponseProto>() {
        override fun onMessage(message: GetOrchestratorHistoryResponseProto) {
            result.complete(message.messagesList)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(emptyList())
        }
    }, io.grpc.Metadata())

    call.sendMessage(GetOrchestratorHistoryRequestProto(sessionId, limit))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: emptyList()
}

suspend fun listRemoteAgents(filterStatus: String = ""): List<RemoteAgentInfoProto> = withContext(Dispatchers.IO) {
    // FUTURE — placeholder, server implements ListRemoteAgents
    Log.d("HermesGrpc", "listRemoteAgents: FUTURE — not yet implemented")
    return@withContext emptyList<RemoteAgentInfoProto>()
}

// ======= Hermes Settings =======

suspend fun getHermesSettings(sessionId: String, userId: String): GetHermesSettingsResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("HermesGrpc", "getHermesSettings: channel dead")
        return@withContext GetHermesSettingsResponseProto()
    }

    val methodDesc = MethodDescriptor.newBuilder<GetHermesSettingsRequestProto, GetHermesSettingsResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/GetHermesSettings")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GetHermesSettingsRequestProto> {
            override fun stream(v: GetHermesSettingsRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.sessionId.isNotEmpty()) cos.writeString(1, v.sessionId)
                if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): GetHermesSettingsRequestProto = GetHermesSettingsRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<GetHermesSettingsResponseProto> {
            override fun stream(v: GetHermesSettingsResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GetHermesSettingsResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var apiKey = ""; var model = ""; var isUsingCustomKey = false
                var remaining = 0; var limit = 0; var windowSeconds = 0
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> apiKey = cis.readString()
                        2 -> model = cis.readString()
                        3 -> isUsingCustomKey = cis.readBool()
                        4 -> remaining = cis.readInt32()
                        5 -> limit = cis.readInt32()
                        6 -> windowSeconds = cis.readInt32()
                        else -> cis.skipField(tag)
                    }
                }
                return GetHermesSettingsResponseProto(apiKey, model, isUsingCustomKey, remaining, limit, windowSeconds)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<GetHermesSettingsResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<GetHermesSettingsResponseProto>() {
        override fun onMessage(message: GetHermesSettingsResponseProto) {
            result.complete(message)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(GetHermesSettingsResponseProto())
        }
    }, io.grpc.Metadata())

    call.sendMessage(GetHermesSettingsRequestProto(sessionId = sessionId, userId = userId))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() } ?: GetHermesSettingsResponseProto()
}

suspend fun updateHermesSettings(sessionId: String, userId: String, apiKey: String, model: String): UpdateHermesSettingsResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        Log.w("HermesGrpc", "updateHermesSettings: channel dead")
        return@withContext UpdateHermesSettingsResponseProto(success = false, message = "Connection lost")
    }

    val methodDesc = MethodDescriptor.newBuilder<UpdateHermesSettingsRequestProto, UpdateHermesSettingsResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/UpdateHermesSettings")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<UpdateHermesSettingsRequestProto> {
            override fun stream(v: UpdateHermesSettingsRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.sessionId.isNotEmpty()) cos.writeString(1, v.sessionId)
                if (v.userId.isNotEmpty()) cos.writeString(2, v.userId)
                if (v.apiKey.isNotEmpty()) cos.writeString(3, v.apiKey)
                if (v.model.isNotEmpty()) cos.writeString(4, v.model)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): UpdateHermesSettingsRequestProto = UpdateHermesSettingsRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<UpdateHermesSettingsResponseProto> {
            override fun stream(v: UpdateHermesSettingsResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): UpdateHermesSettingsResponseProto {
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
                return UpdateHermesSettingsResponseProto(success, message)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<UpdateHermesSettingsResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<UpdateHermesSettingsResponseProto>() {
        override fun onMessage(message: UpdateHermesSettingsResponseProto) {
            result.complete(message)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(
                UpdateHermesSettingsResponseProto(success = false, message = "Connection error: ${status.code}")
            )
        }
    }, io.grpc.Metadata())

    call.sendMessage(UpdateHermesSettingsRequestProto(sessionId = sessionId, userId = userId, apiKey = apiKey, model = model))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() }
        ?: UpdateHermesSettingsResponseProto(success = false, message = "Timeout")
}

// ======= Agent Token Management =======

suspend fun generateAgentToken(
    agentId: String,
    agentName: String,
    capabilities: List<String>,
    ttlHours: Int,
    adminUserId: String
): GenerateAgentTokenResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        return@withContext GenerateAgentTokenResponseProto(success = false, error = "Channel dead")
    }
    val methodDesc = MethodDescriptor.newBuilder<GenerateAgentTokenRequestProto, GenerateAgentTokenResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/GenerateAgentToken")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GenerateAgentTokenRequestProto> {
            override fun stream(v: GenerateAgentTokenRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
                if (v.agentName.isNotEmpty()) cos.writeString(2, v.agentName)
                v.capabilities.forEach { cos.writeString(3, it) }
                if (v.ttlHours != 0) cos.writeInt32(4, v.ttlHours)
                if (v.adminUserId.isNotEmpty()) cos.writeString(5, v.adminUserId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): GenerateAgentTokenRequestProto = GenerateAgentTokenRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<GenerateAgentTokenResponseProto> {
            override fun stream(v: GenerateAgentTokenResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GenerateAgentTokenResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var success = false; var token = ""; var error = ""; var expiresAt = 0L
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        2 -> token = cis.readString()
                        3 -> error = cis.readString()
                        4 -> expiresAt = cis.readInt64()
                        else -> cis.skipField(tag)
                    }
                }
                return GenerateAgentTokenResponseProto(success = success, token = token, error = error, expiresAt = expiresAt)
            }
        })
        .build()
    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = suspendCancellableCoroutine<GenerateAgentTokenResponseProto> { cont ->
        call.start(object : io.grpc.ClientCall.Listener<GenerateAgentTokenResponseProto>() {
            override fun onMessage(message: GenerateAgentTokenResponseProto) {
                cont.resumeWith(Result.success(message))
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (cont.isActive) {
                    cont.resumeWith(Result.success(GenerateAgentTokenResponseProto(success = false, error = status.description ?: status.code.toString())))
                }
            }
        }, io.grpc.Metadata())
    }
    call.sendMessage(GenerateAgentTokenRequestProto(
        agentId = agentId, agentName = agentName, capabilities = capabilities,
        ttlHours = ttlHours, adminUserId = adminUserId
    ))
    call.halfClose()
    call.request(1)
    return@withContext withTimeoutOrNull(15000) { result }
        ?: GenerateAgentTokenResponseProto(success = false, error = "Timeout")
}

suspend fun revokeAgentToken(agentId: String, adminUserId: String): RevokeAgentTokenResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        return@withContext RevokeAgentTokenResponseProto(success = false, error = "Channel dead")
    }
    val methodDesc = MethodDescriptor.newBuilder<RevokeAgentTokenRequestProto, RevokeAgentTokenResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/RevokeAgentToken")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<RevokeAgentTokenRequestProto> {
            override fun stream(v: RevokeAgentTokenRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
                if (v.adminUserId.isNotEmpty()) cos.writeString(2, v.adminUserId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): RevokeAgentTokenRequestProto = RevokeAgentTokenRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<RevokeAgentTokenResponseProto> {
            override fun stream(v: RevokeAgentTokenResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): RevokeAgentTokenResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var success = false; var error = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        2 -> error = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return RevokeAgentTokenResponseProto(success = success, error = error)
            }
        })
        .build()
    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = suspendCancellableCoroutine<RevokeAgentTokenResponseProto> { cont ->
        call.start(object : io.grpc.ClientCall.Listener<RevokeAgentTokenResponseProto>() {
            override fun onMessage(message: RevokeAgentTokenResponseProto) {
                cont.resumeWith(Result.success(message))
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (cont.isActive) {
                    cont.resumeWith(Result.success(RevokeAgentTokenResponseProto(success = false, error = status.description ?: status.code.toString())))
                }
            }
        }, io.grpc.Metadata())
    }
    call.sendMessage(RevokeAgentTokenRequestProto(agentId = agentId, adminUserId = adminUserId))
    call.halfClose()
    call.request(1)
    return@withContext withTimeoutOrNull(10000) { result }
        ?: RevokeAgentTokenResponseProto(success = false, error = "Timeout")
}

suspend fun listAgentTokens(adminUserId: String): ListAgentTokensResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        return@withContext ListAgentTokensResponseProto(success = false, error = "Channel dead")
    }
    val methodDesc = MethodDescriptor.newBuilder<ListAgentTokensRequestProto, ListAgentTokensResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/ListAgentTokens")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<ListAgentTokensRequestProto> {
            override fun stream(v: ListAgentTokensRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.adminUserId.isNotEmpty()) cos.writeString(1, v.adminUserId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): ListAgentTokensRequestProto = ListAgentTokensRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<ListAgentTokensResponseProto> {
            override fun stream(v: ListAgentTokensResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): ListAgentTokensResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var success = false; var error = ""
                val tokens = mutableListOf<AgentTokenInfoProto>()
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        2 -> {
                            val len = cis.readRawVarint32()
                            val msgBytes = cis.readRawBytes(len)
                            if (msgBytes.isNotEmpty()) {
                                val inner = com.google.protobuf.CodedInputStream.newInstance(msgBytes)
                                var id = 0L; var agentId = ""; var agentName = ""; var tokenHash = ""
                                val capabilities = mutableListOf<String>()
                                var createdAt = ""; var expiresAt = ""; var revoked = false; var createdBy = ""
                                while (!inner.isAtEnd) {
                                    val innerTag = inner.readTag()
                                    if (innerTag == 0) break
                                    when (com.google.protobuf.WireFormat.getTagFieldNumber(innerTag)) {
                                        1 -> id = inner.readInt64()
                                        2 -> agentId = inner.readString()
                                        3 -> agentName = inner.readString()
                                        4 -> tokenHash = inner.readString()
                                        5 -> capabilities.add(inner.readString())
                                        6 -> createdAt = inner.readString()
                                        7 -> expiresAt = inner.readString()
                                        8 -> revoked = inner.readBool()
                                        9 -> createdBy = inner.readString()
                                        else -> inner.skipField(innerTag)
                                    }
                                }
                                tokens.add(AgentTokenInfoProto(
                                    id = id, agentId = agentId, agentName = agentName,
                                    tokenHash = tokenHash, capabilities = capabilities,
                                    createdAt = createdAt, expiresAt = expiresAt,
                                    revoked = revoked, createdBy = createdBy
                                ))
                            }
                        }
                        3 -> error = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return ListAgentTokensResponseProto(success = success, tokens = tokens, error = error)
            }
        })
        .build()
    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = suspendCancellableCoroutine<ListAgentTokensResponseProto> { cont ->
        call.start(object : io.grpc.ClientCall.Listener<ListAgentTokensResponseProto>() {
            override fun onMessage(message: ListAgentTokensResponseProto) {
                cont.resumeWith(Result.success(message))
            }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (cont.isActive) {
                    cont.resumeWith(Result.success(ListAgentTokensResponseProto(success = false, error = status.description ?: status.code.toString())))
                }
            }
        }, io.grpc.Metadata())
    }
    call.sendMessage(ListAgentTokensRequestProto(adminUserId = adminUserId))
    call.halfClose()
    call.request(1)
    return@withContext withTimeoutOrNull(10000) { result }
        ?: ListAgentTokensResponseProto(success = false, error = "Timeout")
}

// ======= Remote Agent task deployment =======

suspend fun deployAgentTask(
    agentId: String,
    taskType: String,
    params: Map<String, String> = emptyMap(),
    workingDir: String = "",
    timeoutSec: Int = 60
): DeployAgentTaskResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        return@withContext DeployAgentTaskResponseProto(taskId = "", success = false, message = "Channel dead")
    }
    val methodDesc = MethodDescriptor.newBuilder<DeployAgentTaskRequestProto, DeployAgentTaskResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/DeployAgentTask")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<DeployAgentTaskRequestProto> {
            override fun stream(v: DeployAgentTaskRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
                if (v.taskType.isNotEmpty()) cos.writeString(2, v.taskType)
                v.params.forEach { (k, v2) ->
                    // Encode map<string,string> entry: field 3, message with key(1) + value(2)
                    val entryBaos = ByteArrayOutputStream()
                    val entryCos = com.google.protobuf.CodedOutputStream.newInstance(entryBaos)
                    entryCos.writeString(1, k)
                    entryCos.writeString(2, v2)
                    entryCos.flush()
                    val entryBytes = entryBaos.toByteArray()
                    cos.writeTag(3, com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED)
                    cos.writeRawVarint32(entryBytes.size)
                    cos.writeRawBytes(entryBytes)
                }
                if (v.workingDir.isNotEmpty()) cos.writeString(4, v.workingDir)
                if (v.timeoutSec > 0) cos.writeInt32(5, v.timeoutSec)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): DeployAgentTaskRequestProto = DeployAgentTaskRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<DeployAgentTaskResponseProto> {
            override fun stream(v: DeployAgentTaskResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): DeployAgentTaskResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var success = false; var taskId = ""; var error = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> success = cis.readBool()
                        2 -> taskId = cis.readString()
                        3 -> error = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return DeployAgentTaskResponseProto(taskId = taskId, success = success, message = error)
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<DeployAgentTaskResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<DeployAgentTaskResponseProto>() {
        override fun onMessage(message: DeployAgentTaskResponseProto) {
            result.complete(message)
        }
        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(
                DeployAgentTaskResponseProto(taskId = "", success = false, message = status.description ?: status.code.toString())
            )
        }
    }, io.grpc.Metadata())

    call.sendMessage(DeployAgentTaskRequestProto(agentId, taskType, params, workingDir, timeoutSec))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(15000) { result.await() }
        ?: DeployAgentTaskResponseProto(taskId = "", success = false, message = "Timeout")
}

suspend fun getRemoteAgentStatus(agentId: String): GetRemoteAgentStatusResponseProto = withContext(Dispatchers.IO) {
    val channel = RealGrpcClient.getChannel()
    if (channel == null || channel.isShutdown || channel.isTerminated) {
        return@withContext GetRemoteAgentStatusResponseProto(status = "unavailable")
    }
    val methodDesc = MethodDescriptor.newBuilder<GetRemoteAgentStatusRequestProto, GetRemoteAgentStatusResponseProto>()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName("messenger.ChatService/GetRemoteAgentStatus")
        .setRequestMarshaller(object : MethodDescriptor.Marshaller<GetRemoteAgentStatusRequestProto> {
            override fun stream(v: GetRemoteAgentStatusRequestProto): java.io.InputStream {
                val baos = ByteArrayOutputStream()
                val cos = com.google.protobuf.CodedOutputStream.newInstance(baos)
                if (v.agentId.isNotEmpty()) cos.writeString(1, v.agentId)
                cos.flush()
                return ByteArrayInputStream(baos.toByteArray())
            }
            override fun parse(s: java.io.InputStream): GetRemoteAgentStatusRequestProto = GetRemoteAgentStatusRequestProto()
        })
        .setResponseMarshaller(object : MethodDescriptor.Marshaller<GetRemoteAgentStatusResponseProto> {
            override fun stream(v: GetRemoteAgentStatusResponseProto): java.io.InputStream = ByteArrayInputStream(ByteArray(0))
            override fun parse(s: java.io.InputStream): GetRemoteAgentStatusResponseProto {
                val cis = com.google.protobuf.CodedInputStream.newInstance(s)
                var id = ""; var name = ""; var status = ""; var host = ""
                val capabilities = mutableListOf<String>()
                var activeTasks = 0; var lastHeartbeat = ""
                while (!cis.isAtEnd) {
                    val tag = cis.readTag()
                    if (tag == 0) break
                    when (com.google.protobuf.WireFormat.getTagFieldNumber(tag)) {
                        1 -> id = cis.readString()
                        2 -> name = cis.readString()
                        3 -> status = cis.readString()
                        4 -> host = cis.readString()
                        5 -> capabilities.add(cis.readString())
                        6 -> activeTasks = cis.readInt32()
                        7 -> lastHeartbeat = cis.readString()
                        else -> cis.skipField(tag)
                    }
                }
                return GetRemoteAgentStatusResponseProto(
                    id = id, name = name, status = status, host = host,
                    capabilities = capabilities, activeTasks = activeTasks, lastHeartbeat = lastHeartbeat
                )
            }
        })
        .build()

    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
    val result = CompletableDeferred<GetRemoteAgentStatusResponseProto>()

    call.start(object : io.grpc.ClientCall.Listener<GetRemoteAgentStatusResponseProto>() {
        override fun onMessage(message: GetRemoteAgentStatusResponseProto) {
            result.complete(message)
        }
        override fun onClose(closeStatus: io.grpc.Status, trailers: io.grpc.Metadata) {
            if (!result.isCompleted) result.complete(
                GetRemoteAgentStatusResponseProto(status = "error: ${closeStatus.code}")
            )
        }
    }, io.grpc.Metadata())

    call.sendMessage(GetRemoteAgentStatusRequestProto(agentId))
    call.halfClose()
    call.request(1)

    return@withContext withTimeoutOrNull(10000) { result.await() }
        ?: GetRemoteAgentStatusResponseProto(status = "timeout")
}
