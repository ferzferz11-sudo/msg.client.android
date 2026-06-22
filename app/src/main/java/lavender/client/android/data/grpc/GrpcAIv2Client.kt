package lavender.client.android.data.grpc

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import lavender.client.android.data.models.ErrorHandler
import lavender.client.android.data.proto.*

/**
 * GrpcAIv2Client — AI Services v2 gRPC client.
 * Replaces OwlGrpc + HermesGrpc + AiChatGrpc.
 *
 * Server proto: messenger.ChatService (ChatWithAIV2, Agent CRUD, ListAITools)
 */
class GrpcAIv2Client(
    private val getChannel: () -> io.grpc.ManagedChannel?,
    @Suppress("UNUSED_PARAMETER") private val getUserId: () -> String?,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "GrpcAIv2Client"
        private const val STREAM_TIMEOUT_MS = 120_000L
        private const val MAX_RETRIES = 10
        private const val INITIAL_RETRY_DELAY_MS = 3000L
        private const val MAX_RETRY_DELAY_MS = 30000L
    }

    // Streaming state
    private val _aiResponses = MutableSharedFlow<ChatWithAIV2ResponseProto>(extraBufferCapacity = 64)
    val aiResponses: SharedFlow<ChatWithAIV2ResponseProto> = _aiResponses

    private val _aiTyping = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
    val aiTyping: SharedFlow<Boolean> = _aiTyping

    // ======= ChatWithAIV2 — Main streaming method =======

    fun chatWithAIV2(
        sessionId: String,
        message: String,
        agentId: String = "",
        images: List<ByteArray> = emptyList(),
        toolCalls: List<ToolCallV2Proto> = emptyList(),
        scope: CoroutineScope,
        onResponse: (token: String, finished: Boolean, error: String?, imageUrl: String, agentId: String, agentName: String, toolCalls: List<ToolCallRequestV2Proto>, hasRagContext: Boolean, modelUsed: String, tokenCount: Int) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            var retryDelay = INITIAL_RETRY_DELAY_MS
            var attempt = 0

            while (attempt < MAX_RETRIES && isActive) {
                val channel = getChannel()
                if (channel == null || channel.isShutdown || channel.isTerminated) {
                    Log.w(TAG, "chatWithAIV2: channel dead, waiting ${retryDelay}ms...")
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                    attempt++
                    continue
                }

                try {
                    val methodDesc = io.grpc.MethodDescriptor.newBuilder<ChatWithAIV2RequestProto, ChatWithAIV2ResponseProto>()
                        .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
                        .setFullMethodName("messenger.ChatService/ChatWithAIV2")
                        .setRequestMarshaller(ChatWithAIV2RequestMarshaller())
                        .setResponseMarshaller(ChatWithAIV2ResponseMarshaller())
                        .build()

                    val request = ChatWithAIV2RequestProto(
                        sessionId = sessionId,
                        message = message,
                        images = images,
                        agentId = agentId,
                        toolCalls = toolCalls
                    )

                    _aiTyping.emit(true)
                    val streamDone = CompletableDeferred<Boolean>()
                    var hadError = false
                    var timeoutJob: Job? = null

                    val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
                    call.start(object : io.grpc.ClientCall.Listener<ChatWithAIV2ResponseProto>() {
                        override fun onMessage(msg: ChatWithAIV2ResponseProto) {
                            timeoutJob?.cancel()
                            _aiResponses.tryEmit(msg)
                            onResponse(msg.token, msg.finished, msg.error.takeIf { it.isNotEmpty() }, msg.imageUrl,
                                msg.agentId, msg.agentName, msg.toolCalls, msg.hasRagContext, msg.modelUsed, msg.tokenCount)
                            retryDelay = INITIAL_RETRY_DELAY_MS
                            attempt = 0
                            timeoutJob = launch {
                                delay(STREAM_TIMEOUT_MS)
                                if (!msg.finished) {
                                    Log.w(TAG, "chatWithAIV2: stream timeout after ${STREAM_TIMEOUT_MS}ms")
                                    hadError = true
                                    val errorResp = ChatWithAIV2ResponseProto(
                                        token = "", finished = true,
                                        error = "Response timeout (${STREAM_TIMEOUT_MS / 1000}s). Please try again."
                                    )
                                    _aiResponses.tryEmit(errorResp)
                                    onResponse("", true, errorResp.error, "", "", "", emptyList(), false, "", 0)
                                    streamDone.complete(true)
                                }
                            }
                        }
                        override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                            timeoutJob?.cancel()
                            _aiTyping.tryEmit(false)
                            if (!status.isOk) {
                                Log.w(TAG, "chatWithAIV2 closed: ${status.code} ${status.description}")
                                hadError = true
                                val errorResp = ChatWithAIV2ResponseProto(
                                    token = "", finished = true,
                                    error = status.description ?: "Connection error: ${status.code}"
                                )
                                _aiResponses.tryEmit(errorResp)
                                onResponse("", true, errorResp.error, "", "", "", emptyList(), false, "", 0)
                            }
                            streamDone.complete(hadError)
                        }
                    }, io.grpc.Metadata())
                    call.sendMessage(request)
                    call.halfClose()
                    call.request(Int.MAX_VALUE)

                    timeoutJob = launch {
                        delay(STREAM_TIMEOUT_MS)
                        Log.w(TAG, "chatWithAIV2: initial stream timeout after ${STREAM_TIMEOUT_MS}ms")
                        hadError = true
                        val errorResp = ChatWithAIV2ResponseProto(
                            token = "", finished = true,
                            error = "Response timeout (${STREAM_TIMEOUT_MS / 1000}s). Please try again."
                        )
                        _aiResponses.tryEmit(errorResp)
                        onResponse("", true, errorResp.error, "", "", "", emptyList(), false, "", 0)
                        streamDone.complete(true)
                    }

                    val streamHadError = streamDone.await()
                    timeoutJob.cancel()
                    _aiTyping.tryEmit(false)

                    if (!streamHadError) return@launch

                    attempt++
                    if (attempt < MAX_RETRIES) {
                        Log.d(TAG, "Retrying chatWithAIV2 in ${retryDelay}ms (attempt $attempt/$MAX_RETRIES)")
                        delay(retryDelay)
                        retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                    }

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ErrorHandler.handle("GrpcAIv2Client.chatWithAIV2", e)
                    _aiTyping.tryEmit(false)
                    val errorResp = ChatWithAIV2ResponseProto(
                        token = "", finished = true,
                        error = e.message ?: "Unknown error"
                    )
                    _aiResponses.tryEmit(errorResp)
                    onResponse("", true, errorResp.error, "", "", "", emptyList(), false, "", 0)

                    attempt++
                    if (attempt < MAX_RETRIES) {
                        delay(retryDelay)
                        retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                    }
                }
            }

            if (attempt >= MAX_RETRIES) {
                ErrorHandler.warn("GrpcAIv2Client.chatWithAIV2", "Max retries exceeded ($MAX_RETRIES)")
                val errorResp = ChatWithAIV2ResponseProto(
                    token = "", finished = true,
                    error = "Connection lost after $MAX_RETRIES attempts"
                )
                _aiResponses.tryEmit(errorResp)
                onResponse("", true, errorResp.error, "", "", "", emptyList(), false, "", 0)
            }
        }
    }

    // ======= Agent CRUD =======

    suspend fun createAgent(request: CreateAIAgentRequestProto): CreateAIAgentResponseProto = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) {
            return@withContext CreateAIAgentResponseProto(error = "Connection lost")
        }

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<CreateAIAgentRequestProto, CreateAIAgentResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/CreateAIAgent")
            .setRequestMarshaller(CreateAIAgentRequestMarshaller())
            .setResponseMarshaller(CreateAIAgentResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<CreateAIAgentResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<CreateAIAgentResponseProto>() {
            override fun onMessage(message: CreateAIAgentResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(CreateAIAgentResponseProto(error = "Connection error: ${status.code}"))
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)

        return@withContext withTimeoutOrNull(10000) { result.await() }
            ?: CreateAIAgentResponseProto(error = "Timeout")
    }

    suspend fun updateAgent(request: UpdateAIAgentRequestProto): UpdateAIAgentResponseProto = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) {
            return@withContext UpdateAIAgentResponseProto(error = "Connection lost")
        }

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<UpdateAIAgentRequestProto, UpdateAIAgentResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/UpdateAIAgent")
            .setRequestMarshaller(UpdateAIAgentRequestMarshaller())
            .setResponseMarshaller(UpdateAIAgentResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<UpdateAIAgentResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<UpdateAIAgentResponseProto>() {
            override fun onMessage(message: UpdateAIAgentResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(UpdateAIAgentResponseProto(error = "Connection error: ${status.code}"))
            }
        }, io.grpc.Metadata())

        call.sendMessage(request)
        call.halfClose()
        call.request(1)

        return@withContext withTimeoutOrNull(10000) { result.await() }
            ?: UpdateAIAgentResponseProto(error = "Timeout")
    }

    suspend fun deleteAgent(agentId: String): DeleteAIAgentResponseProto = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) {
            return@withContext DeleteAIAgentResponseProto(error = "Connection lost")
        }

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<DeleteAIAgentRequestProto, DeleteAIAgentResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/DeleteAIAgent")
            .setRequestMarshaller(DeleteAIAgentRequestMarshaller())
            .setResponseMarshaller(DeleteAIAgentResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<DeleteAIAgentResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<DeleteAIAgentResponseProto>() {
            override fun onMessage(message: DeleteAIAgentResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(DeleteAIAgentResponseProto(error = "Connection error: ${status.code}"))
            }
        }, io.grpc.Metadata())

        call.sendMessage(DeleteAIAgentRequestProto(agentId))
        call.halfClose()
        call.request(1)

        return@withContext withTimeoutOrNull(10000) { result.await() }
            ?: DeleteAIAgentResponseProto(error = "Timeout")
    }

    suspend fun getAgent(agentId: String): AgentInfoV2Proto? = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) return@withContext null

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<GetAIAgentRequestProto, GetAIAgentResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetAIAgent")
            .setRequestMarshaller(GetAIAgentRequestMarshaller())
            .setResponseMarshaller(GetAIAgentResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<GetAIAgentResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<GetAIAgentResponseProto>() {
            override fun onMessage(message: GetAIAgentResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(GetAIAgentResponseProto())
            }
        }, io.grpc.Metadata())

        call.sendMessage(GetAIAgentRequestProto(agentId))
        call.halfClose()
        call.request(1)

        val response = withTimeoutOrNull(10000) { result.await() } ?: GetAIAgentResponseProto()
        return@withContext response.agent.takeIf { it.id.isNotEmpty() }
    }

    suspend fun listAgents(includePublic: Boolean = false): List<AgentInfoV2Proto> = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) return@withContext emptyList()

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<ListAIAgentsRequestProto, ListAIAgentsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/ListAIAgents")
            .setRequestMarshaller(ListAIAgentsRequestMarshaller())
            .setResponseMarshaller(ListAIAgentsResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<ListAIAgentsResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<ListAIAgentsResponseProto>() {
            override fun onMessage(message: ListAIAgentsResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(ListAIAgentsResponseProto())
            }
        }, io.grpc.Metadata())

        call.sendMessage(ListAIAgentsRequestProto(includePublic))
        call.halfClose()
        call.request(1)

        val response = withTimeoutOrNull(10000) { result.await() } ?: ListAIAgentsResponseProto()
        return@withContext response.agents
    }

    suspend fun cloneAgent(agentId: String, newName: String): CloneAIAgentResponseProto = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) {
            return@withContext CloneAIAgentResponseProto(error = "Connection lost")
        }

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<CloneAIAgentRequestProto, CloneAIAgentResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/CloneAIAgent")
            .setRequestMarshaller(CloneAIAgentRequestMarshaller())
            .setResponseMarshaller(CloneAIAgentResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<CloneAIAgentResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<CloneAIAgentResponseProto>() {
            override fun onMessage(message: CloneAIAgentResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(CloneAIAgentResponseProto(error = "Connection error: ${status.code}"))
            }
        }, io.grpc.Metadata())

        call.sendMessage(CloneAIAgentRequestProto(agentId, newName))
        call.halfClose()
        call.request(1)

        return@withContext withTimeoutOrNull(10000) { result.await() }
            ?: CloneAIAgentResponseProto(error = "Timeout")
    }

    // ======= Marketplace =======

    suspend fun rateAgent(agentId: String, rating: Int, review: String): RateAIAgentResponseProto = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) {
            return@withContext RateAIAgentResponseProto()
        }

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<RateAIAgentRequestProto, RateAIAgentResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/RateAIAgent")
            .setRequestMarshaller(RateAIAgentRequestMarshaller())
            .setResponseMarshaller(RateAIAgentResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<RateAIAgentResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<RateAIAgentResponseProto>() {
            override fun onMessage(message: RateAIAgentResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(RateAIAgentResponseProto())
            }
        }, io.grpc.Metadata())

        call.sendMessage(RateAIAgentRequestProto(agentId, rating, review))
        call.halfClose()
        call.request(1)

        return@withContext withTimeoutOrNull(10000) { result.await() } ?: RateAIAgentResponseProto()
    }

    suspend fun getAgentReviews(agentId: String, limit: Int = 20): GetAIAgentReviewsResponseProto = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) {
            return@withContext GetAIAgentReviewsResponseProto()
        }

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<GetAIAgentReviewsRequestProto, GetAIAgentReviewsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetAIAgentReviews")
            .setRequestMarshaller(GetAIAgentReviewsRequestMarshaller())
            .setResponseMarshaller(GetAIAgentReviewsResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<GetAIAgentReviewsResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<GetAIAgentReviewsResponseProto>() {
            override fun onMessage(message: GetAIAgentReviewsResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(GetAIAgentReviewsResponseProto())
            }
        }, io.grpc.Metadata())

        call.sendMessage(GetAIAgentReviewsRequestProto(agentId, limit))
        call.halfClose()
        call.request(1)

        return@withContext withTimeoutOrNull(10000) { result.await() } ?: GetAIAgentReviewsResponseProto()
    }

    suspend fun listMarketplaceAgents(query: String = "", limit: Int = 20, offset: Int = 0): ListMarketplaceAgentsResponseProto = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) {
            return@withContext ListMarketplaceAgentsResponseProto()
        }

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<ListMarketplaceAgentsRequestProto, ListMarketplaceAgentsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/ListMarketplaceAgents")
            .setRequestMarshaller(ListMarketplaceAgentsRequestMarshaller())
            .setResponseMarshaller(ListMarketplaceAgentsResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<ListMarketplaceAgentsResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<ListMarketplaceAgentsResponseProto>() {
            override fun onMessage(message: ListMarketplaceAgentsResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(ListMarketplaceAgentsResponseProto())
            }
        }, io.grpc.Metadata())

        call.sendMessage(ListMarketplaceAgentsRequestProto(query, limit, offset))
        call.halfClose()
        call.request(1)

        return@withContext withTimeoutOrNull(10000) { result.await() } ?: ListMarketplaceAgentsResponseProto()
    }

    suspend fun getAgentStats(agentId: String): GetAIAgentStatsResponseProto = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) {
            return@withContext GetAIAgentStatsResponseProto()
        }

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<GetAIAgentStatsRequestProto, GetAIAgentStatsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetAIAgentStats")
            .setRequestMarshaller(GetAIAgentStatsRequestMarshaller())
            .setResponseMarshaller(GetAIAgentStatsResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<GetAIAgentStatsResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<GetAIAgentStatsResponseProto>() {
            override fun onMessage(message: GetAIAgentStatsResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(GetAIAgentStatsResponseProto())
            }
        }, io.grpc.Metadata())

        call.sendMessage(GetAIAgentStatsRequestProto(agentId))
        call.halfClose()
        call.request(1)

        return@withContext withTimeoutOrNull(10000) { result.await() } ?: GetAIAgentStatsResponseProto()
    }

    suspend fun shareAgent(agentId: String): ShareAIAgentResponseProto = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) {
            return@withContext ShareAIAgentResponseProto()
        }

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<ShareAIAgentRequestProto, ShareAIAgentResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/ShareAIAgent")
            .setRequestMarshaller(ShareAIAgentRequestMarshaller())
            .setResponseMarshaller(ShareAIAgentResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<ShareAIAgentResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<ShareAIAgentResponseProto>() {
            override fun onMessage(message: ShareAIAgentResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(ShareAIAgentResponseProto())
            }
        }, io.grpc.Metadata())

        call.sendMessage(ShareAIAgentRequestProto(agentId))
        call.halfClose()
        call.request(1)

        return@withContext withTimeoutOrNull(10000) { result.await() } ?: ShareAIAgentResponseProto()
    }

    suspend fun installAgent(shareCode: String, newName: String = ""): InstallAIAgentResponseProto = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) {
            return@withContext InstallAIAgentResponseProto(error = "Connection lost")
        }

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<InstallAIAgentRequestProto, InstallAIAgentResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/InstallAIAgent")
            .setRequestMarshaller(InstallAIAgentRequestMarshaller())
            .setResponseMarshaller(InstallAIAgentResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<InstallAIAgentResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<InstallAIAgentResponseProto>() {
            override fun onMessage(message: InstallAIAgentResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(InstallAIAgentResponseProto(error = "Connection error: ${status.code}"))
            }
        }, io.grpc.Metadata())

        call.sendMessage(InstallAIAgentRequestProto(shareCode, newName))
        call.halfClose()
        call.request(1)

        return@withContext withTimeoutOrNull(10000) { result.await() }
            ?: InstallAIAgentResponseProto(error = "Timeout")
    }

    suspend fun getUsageStats(): GetAIUsageStatsResponseProto = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) {
            return@withContext GetAIUsageStatsResponseProto()
        }

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<GetAIUsageStatsRequestProto, GetAIUsageStatsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetAIUsageStats")
            .setRequestMarshaller(GetAIUsageStatsRequestMarshaller())
            .setResponseMarshaller(GetAIUsageStatsResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<GetAIUsageStatsResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<GetAIUsageStatsResponseProto>() {
            override fun onMessage(message: GetAIUsageStatsResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(GetAIUsageStatsResponseProto())
            }
        }, io.grpc.Metadata())

        call.sendMessage(GetAIUsageStatsRequestProto())
        call.halfClose()
        call.request(1)

        return@withContext withTimeoutOrNull(10000) { result.await() } ?: GetAIUsageStatsResponseProto()
    }

    // ======= AI Chat Settings =======

    suspend fun getChatSettings(sessionId: String): AIChatSettingsProto = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) {
            return@withContext AIChatSettingsProto()
        }

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<GetAIChatSettingsRequestProto, AIChatSettingsProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetAIChatSettings")
            .setRequestMarshaller(GetAIChatSettingsRequestMarshaller())
            .setResponseMarshaller(AIChatSettingsResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<AIChatSettingsProto>()

        call.start(object : io.grpc.ClientCall.Listener<AIChatSettingsProto>() {
            override fun onMessage(message: AIChatSettingsProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(AIChatSettingsProto())
            }
        }, io.grpc.Metadata())

        call.sendMessage(GetAIChatSettingsRequestProto(sessionId))
        call.halfClose()
        call.request(1)

        return@withContext withTimeoutOrNull(10000) { result.await() } ?: AIChatSettingsProto()
    }

    suspend fun updateChatSettings(sessionId: String, apiKey: String = "", model: String = ""): UpdateAIChatSettingsResponseProto = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) {
            return@withContext UpdateAIChatSettingsResponseProto(message = "Connection lost")
        }

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<UpdateAIChatSettingsRequestProto, UpdateAIChatSettingsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/UpdateAIChatSettings")
            .setRequestMarshaller(UpdateAIChatSettingsRequestMarshaller())
            .setResponseMarshaller(UpdateAIChatSettingsResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<UpdateAIChatSettingsResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<UpdateAIChatSettingsResponseProto>() {
            override fun onMessage(message: UpdateAIChatSettingsResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(UpdateAIChatSettingsResponseProto(message = "Connection error: ${status.code}"))
            }
        }, io.grpc.Metadata())

        call.sendMessage(UpdateAIChatSettingsRequestProto(sessionId, apiKey, model))
        call.halfClose()
        call.request(1)

        return@withContext withTimeoutOrNull(10000) { result.await() }
            ?: UpdateAIChatSettingsResponseProto(message = "Timeout")
    }

    // ======= Tools =======

    suspend fun listTools(): List<ToolInfoV2Proto> = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) return@withContext emptyList()

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<ListAIToolsRequestProto, ListAIToolsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/ListAITools")
            .setRequestMarshaller(ListAIToolsRequestMarshaller())
            .setResponseMarshaller(ListAIToolsResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<ListAIToolsResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<ListAIToolsResponseProto>() {
            override fun onMessage(message: ListAIToolsResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(ListAIToolsResponseProto())
            }
        }, io.grpc.Metadata())

        call.sendMessage(ListAIToolsRequestProto())
        call.halfClose()
        call.request(1)

        val response = withTimeoutOrNull(10000) { result.await() } ?: ListAIToolsResponseProto()
        return@withContext response.tools
    }

    // ======= GetAIV2ChatHistory =======
    // Server proto: messenger.ChatService/GetAIV2ChatHistory

    suspend fun getAIV2ChatHistory(sessionId: String, limit: Int = 50): List<AIV2ChatMessageProto> = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) return@withContext emptyList()

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<GetAIV2ChatHistoryRequestProto, GetAIV2ChatHistoryResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/GetAIV2ChatHistory")
            .setRequestMarshaller(GetAIV2ChatHistoryRequestMarshaller())
            .setResponseMarshaller(GetAIV2ChatHistoryResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<GetAIV2ChatHistoryResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<GetAIV2ChatHistoryResponseProto>() {
            override fun onMessage(message: GetAIV2ChatHistoryResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(GetAIV2ChatHistoryResponseProto())
            }
        }, io.grpc.Metadata())

        call.sendMessage(GetAIV2ChatHistoryRequestProto(sessionId, limit))
        call.halfClose()
        call.request(1)

        val response = withTimeoutOrNull(15000) { result.await() } ?: GetAIV2ChatHistoryResponseProto()
        return@withContext response.messages
    }

    // ======= ListAIV2Chats =======
    // Server proto: messenger.ChatService/ListAIV2Chats

    suspend fun listAIV2Chats(): List<AIV2ChatInfoProto> = withContext(Dispatchers.IO) {
        val channel = getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) return@withContext emptyList()

        val methodDesc = io.grpc.MethodDescriptor.newBuilder<ListAIV2ChatsRequestProto, ListAIV2ChatsResponseProto>()
            .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("messenger.ChatService/ListAIV2Chats")
            .setRequestMarshaller(ListAIV2ChatsRequestMarshaller())
            .setResponseMarshaller(ListAIV2ChatsResponseMarshaller())
            .build()

        val call = channel.newCall(methodDesc, io.grpc.CallOptions.DEFAULT)
        val result = CompletableDeferred<ListAIV2ChatsResponseProto>()

        call.start(object : io.grpc.ClientCall.Listener<ListAIV2ChatsResponseProto>() {
            override fun onMessage(message: ListAIV2ChatsResponseProto) { result.complete(message) }
            override fun onClose(status: io.grpc.Status, trailers: io.grpc.Metadata) {
                if (!result.isCompleted) result.complete(ListAIV2ChatsResponseProto())
            }
        }, io.grpc.Metadata())

        call.sendMessage(ListAIV2ChatsRequestProto())
        call.halfClose()
        call.request(1)

        val response = withTimeoutOrNull(10000) { result.await() } ?: ListAIV2ChatsResponseProto()
        return@withContext response.chats
    }
}
