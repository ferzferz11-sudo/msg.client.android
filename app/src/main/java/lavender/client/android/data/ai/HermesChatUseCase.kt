package lavender.client.android.data.ai

import android.util.Log
import kotlinx.coroutines.*
import lavender.client.android.data.grpc.RealGrpcClient
import lavender.client.android.data.grpc.chatWithOrchestrator

/**
 * HermesChatUseCase — orchestrates Hermes multi-agent chat with retry, timeout, backoff.
 * Transport delegated to HermesGrpc; this class owns the streaming logic
 * and emits domain AiChatMessage to AiChatManager.
 */
object HermesChatUseCase {
    private const val TAG = "HermesChatUseCase"
    private const val MAX_RETRIES = 10
    private const val INITIAL_RETRY_DELAY_MS = 3000L
    private const val MAX_RETRY_DELAY_MS = 30000L
    private const val STREAM_TIMEOUT_MS = 120_000L

    /**
     * Chat with Hermes orchestrator — streaming with retry and timeout.
     * Collects tokens via callback and emits domain AiChatMessage to AiChatManager.
     */
    suspend fun chat(
        userId: String,
        sessionId: String,
        message: String,
        agentId: String = "",
        mode: String = "",
        scope: CoroutineScope
    ) = withContext(Dispatchers.IO) {
        var retryDelay = INITIAL_RETRY_DELAY_MS
        var attempt = 0

        while (attempt < MAX_RETRIES && isActive) {
            try {
                val channel = RealGrpcClient.getChannel()
                if (channel == null || channel.isShutdown || channel.isTerminated) {
                    Log.w(TAG, "Channel dead, waiting ${retryDelay}ms...")
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                    attempt++
                    continue
                }

                emitHermesTyping(true)
                val streamDone = CompletableDeferred<Boolean>()
                var hadError = false
                var timeoutJob: Job? = null

                chatWithOrchestrator(
                    userId = userId,
                    sessionId = sessionId,
                    message = message,
                    agentId = agentId,
                    mode = mode,
                    scope = scope,
                    onResponse = { token, finished, error, respAgentId, respAgentName ->
                        timeoutJob?.cancel()
                        val msg = AiChatMessage(
                            sessionId = sessionId,
                            role = "assistant",
                            content = token,
                            agentId = respAgentId,
                            agentName = respAgentName,
                            source = AiSource.HERMES,
                            isStreaming = !finished
                        )
                        emitHermesResponse(msg)
                        retryDelay = INITIAL_RETRY_DELAY_MS

                        if (finished && error != null) {
                            hadError = true
                        }

                        timeoutJob = scope.launch {
                            delay(STREAM_TIMEOUT_MS)
                            if (!finished) {
                                Log.w(TAG, "Stream timeout after ${STREAM_TIMEOUT_MS}ms")
                                hadError = true
                                emitError("Response timeout (${STREAM_TIMEOUT_MS / 1000}s). Please try again.")
                                streamDone.complete(true)
                            }
                        }
                    }
                )

                timeoutJob = scope.launch {
                    delay(STREAM_TIMEOUT_MS)
                    Log.w(TAG, "Initial stream timeout after ${STREAM_TIMEOUT_MS}ms")
                    hadError = true
                    emitError("Response timeout (${STREAM_TIMEOUT_MS / 1000}s). Please try again.")
                    streamDone.complete(true)
                }

                val streamHadError = streamDone.await()
                timeoutJob.cancel()
                emitHermesTyping(false)

                if (!streamHadError) return@withContext  // Success

                attempt++
                if (attempt < MAX_RETRIES) {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "chat error", e)
                emitError("Unexpected error: ${e.message}")
                attempt++
                if (attempt < MAX_RETRIES) {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                }
            }
        }

        if (attempt >= MAX_RETRIES) {
            Log.w(TAG, "Max retries exceeded ($MAX_RETRIES)")
            emitError("Connection lost after $MAX_RETRIES attempts")
        }
    }

    private fun emitError(errorText: String) {
        emitHermesResponse(
            AiChatMessage(
                role = "assistant",
                content = "",
                source = AiSource.HERMES,
                isStreaming = false
            )
        )
        emitHermesTyping(false)
    }

    // ====== Agent Management ======

    suspend fun listAgents(userId: String = ""): List<lavender.client.android.data.models.AgentInfo> {
        val protoList = lavender.client.android.data.grpc.listAgents(userId)
        val agents = protoList.map { it.toDomain() }
        AiChatManager.setHermesAgents(agents)
        return agents
    }

    suspend fun getSettings(sessionId: String, userId: String): AiChatSettings? {
        val proto = lavender.client.android.data.grpc.getHermesSettings(sessionId, userId)
        val settings = proto.toDomain(sessionId, userId)
        AiChatManager.updateHermesSettings(settings)
        return settings
    }

    suspend fun updateSettings(sessionId: String, userId: String, apiKey: String, model: String): Boolean {
        val result = lavender.client.android.data.grpc.updateHermesSettings(sessionId, userId, apiKey, model)
        if (result.success) {
            getSettings(sessionId, userId) // Refresh
        }
        return result.success
    }

    suspend fun getHistory(sessionId: String, limit: Int = 50): List<AiChatMessage> {
        val proto = lavender.client.android.data.grpc.getOrchestratorHistory(sessionId, limit)
        return proto.map { it.toDomain(sessionId) }
    }

    // ====== Remote Agent ======

    suspend fun listRemoteAgents(filterStatus: String = ""): List<lavender.client.android.data.models.RemoteAgentInfo> {
        val proto = lavender.client.android.data.grpc.listRemoteAgents(filterStatus)
        val agents = proto.map { it.toDomain() }
        AiChatManager.setRemoteAgents(agents)
        return agents
    }

    suspend fun deployTask(
        agentId: String,
        taskType: String,
        params: Map<String, String> = emptyMap(),
        workingDir: String = "",
        timeoutSec: Int = 60
    ): lavender.client.android.data.proto.DeployAgentTaskResponseProto {
        return lavender.client.android.data.grpc.deployAgentTask(
            agentId, taskType, params, workingDir, timeoutSec
        )
    }

    suspend fun deployTaskStream(
        agentId: String,
        taskType: String,
        params: Map<String, String> = emptyMap(),
        workingDir: String = "",
        timeoutSec: Int = 60
    ): kotlinx.coroutines.flow.Flow<lavender.client.android.data.proto.DeployAgentTaskStreamResponseProto> {
        return lavender.client.android.data.grpc.deployTaskStream(
            agentId, taskType, params, workingDir, timeoutSec
        )
    }

    suspend fun getRemoteAgentStatus(agentId: String): lavender.client.android.data.proto.GetRemoteAgentStatusResponseProto {
        return lavender.client.android.data.grpc.getRemoteAgentStatus(agentId)
    }

    // ====== Token Management ======

    suspend fun generateAgentToken(
        agentId: String, agentName: String, capabilities: List<String>,
        ttlHours: Int, adminUserId: String
    ): lavender.client.android.data.proto.GenerateAgentTokenResponseProto {
        return lavender.client.android.data.grpc.generateAgentToken(
            agentId, agentName, capabilities, ttlHours, adminUserId
        )
    }

    suspend fun revokeAgentToken(agentId: String, adminUserId: String): lavender.client.android.data.proto.RevokeAgentTokenResponseProto {
        return lavender.client.android.data.grpc.revokeAgentToken(agentId, adminUserId)
    }

    suspend fun listAgentTokens(adminUserId: String): lavender.client.android.data.proto.ListAgentTokensResponseProto {
        return lavender.client.android.data.grpc.listAgentTokens(adminUserId)
    }

    // ====== Agent Process Management ======

    suspend fun startAgent(
        agentId: String, agentName: String, token: String,
        serverAddress: String = "", capabilities: List<String> = listOf("shell", "git", "build", "file", "docker", "ai"),
        adminUserId: String = ""
    ): lavender.client.android.data.proto.StartAgentResponseProto {
        return lavender.client.android.data.grpc.startAgentOnServer(
            agentId, agentName, token, serverAddress, capabilities, adminUserId
        )
    }

    suspend fun stopAgent(agentId: String, adminUserId: String = ""): lavender.client.android.data.proto.StopAgentResponseProto {
        return lavender.client.android.data.grpc.stopAgentOnServer(agentId, adminUserId)
    }

    suspend fun getAgentProcessStatus(agentId: String, adminUserId: String = ""): lavender.client.android.data.proto.GetAgentProcessStatusResponseProto {
        return lavender.client.android.data.grpc.getAgentProcessStatus(agentId, adminUserId)
    }
}

private fun emitHermesResponse(message: AiChatMessage) = AiChatManager.emitHermesResponse(message)
private fun emitHermesTyping(typing: Boolean) = AiChatManager.emitHermesTyping(typing)
