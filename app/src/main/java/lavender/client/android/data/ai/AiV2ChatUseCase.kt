package lavender.client.android.data.ai

import android.util.Log
import kotlinx.coroutines.*
import lavender.client.android.data.grpc.RealGrpcClient
import lavender.client.android.data.proto.*

/**
 * AiV2ChatUseCase — orchestrates AI v2 chat with tool calling loop.
 * Transport delegated to GrpcAIv2Client; this class owns the tool calling logic
 * and emits domain AiV2ChatMessage to AiV2ChatManager.
 */
object AiV2ChatUseCase {
    private const val TAG = "AiV2ChatUseCase"
    private const val MAX_TOOL_ITERATIONS = 10

    /**
     * Chat with AI v2 — streaming with tool calling loop.
     * If server sends tool_calls, client sends them back (server executes tools).
     */
    suspend fun chat(
        userId: String,
        sessionId: String,
        message: String,
        agentId: String = "",
        images: List<ByteArray> = emptyList(),
        scope: CoroutineScope
    ) = withContext(Dispatchers.IO) {
        var currentSessionId = sessionId
        var iteration = 0

        while (iteration < MAX_TOOL_ITERATIONS) {
            val result = executeStream(
                userId = userId,
                sessionId = currentSessionId,
                message = if (iteration == 0) message else "",
                agentId = agentId,
                images = if (iteration == 0) images else emptyList(),
                toolCalls = emptyList(),
                scope = scope
            )

            if (result == null) break

            // If no tool calls, we're done
            if (result.toolCalls.isEmpty()) break

            // Send tool calls back to server
            iteration++
            if (iteration >= MAX_TOOL_ITERATIONS) {
                Log.w(TAG, "Max tool iterations reached ($MAX_TOOL_ITERATIONS)")
                break
            }

            // Server executes tools and continues streaming
            val followUpResult = executeStream(
                userId = userId,
                sessionId = currentSessionId,
                message = "",
                agentId = agentId,
                images = emptyList(),
                toolCalls = result.toolCalls.map { toolCall ->
                    ToolCallV2Proto(
                        id = toolCall.id,
                        name = toolCall.name,
                        arguments = toolCall.arguments,
                        result = "Executed by server"
                    )
                },
                scope = scope
            )

            if (followUpResult == null || followUpResult.toolCalls.isEmpty()) break
        }
    }

    /**
     * Execute a single streaming attempt.
     * Returns the final response if it contains tool calls, null otherwise.
     */
    private suspend fun executeStream(
        userId: String,
        sessionId: String,
        message: String,
        agentId: String,
        images: List<ByteArray>,
        toolCalls: List<ToolCallV2Proto>,
        scope: CoroutineScope
    ): ChatWithAIV2ResponseProto? = withContext(Dispatchers.IO) {
        val client = RealGrpcClient.aiV2Client
        var finalResponse: ChatWithAIV2ResponseProto? = null

        client.chatWithAIV2(
            sessionId = sessionId,
            message = message,
            agentId = agentId,
            images = images,
            toolCalls = toolCalls,
            scope = scope,
            onResponse = { token: String, finished: Boolean, error: String?, imageUrl: String, respAgentId: String, respAgentName: String, respToolCalls: List<ToolCallRequestV2Proto>, hasRagContext: Boolean, modelUsed: String, tokenCount: Int ->
                // Emit to manager
                AiV2ChatManager.emitResponse(
                    AiV2ChatMessage(
                        sessionId = sessionId,
                        role = "assistant",
                        content = token,
                        imageUrl = imageUrl,
                        agentId = respAgentId,
                        agentName = respAgentName,
                        isStreaming = finished.not(),
                        toolCalls = respToolCalls.map { tc: ToolCallRequestV2Proto -> tc.toDomain() },
                        hasRagContext = hasRagContext,
                        modelUsed = modelUsed,
                        tokenCount = tokenCount
                    )
                )

                if (finished) {
                    finalResponse = ChatWithAIV2ResponseProto(
                        token = token,
                        finished = finished,
                        error = error ?: "",
                        imageUrl = imageUrl,
                        agentId = respAgentId,
                        agentName = respAgentName,
                        toolCalls = respToolCalls,
                        hasRagContext = hasRagContext,
                        modelUsed = modelUsed,
                        tokenCount = tokenCount
                    )
                }
            }
        )

        return@withContext finalResponse
    }

    // ======= Agent CRUD =======

    suspend fun createAgent(request: CreateAIAgentRequestProto): Result<AiV2Agent> {
        return try {
            val response = RealGrpcClient.aiV2Client.createAgent(request)
            if (response.success) {
                val agent = RealGrpcClient.aiV2Client.getAgent(response.agentId)
                if (agent != null) {
                    Result.success(agent.toDomain())
                } else {
                    Result.failure(Exception("Agent created but not found"))
                }
            } else {
                Result.failure(Exception(response.error.ifEmpty { "Failed to create agent" }.toString()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "createAgent error", e)
            Result.failure(e)
        }
    }

    suspend fun updateAgent(request: UpdateAIAgentRequestProto): Result<Boolean> {
        return try {
            val response = RealGrpcClient.aiV2Client.updateAgent(request)
            if (response.success) Result.success(true)
                else Result.failure(Exception(response.error.ifEmpty { "Failed to update agent" }.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "updateAgent error", e)
            Result.failure(e)
        }
    }

    suspend fun deleteAgent(agentId: String): Result<Boolean> {
        return try {
            val response = RealGrpcClient.aiV2Client.deleteAgent(agentId)
            if (response.success) Result.success(true)
                else Result.failure(Exception(response.error.ifEmpty { "Failed to delete agent" }.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "deleteAgent error", e)
            Result.failure(e)
        }
    }

    suspend fun getAgent(agentId: String): AiV2Agent? {
        return try {
            RealGrpcClient.aiV2Client.getAgent(agentId)?.toDomain()
        } catch (e: Exception) {
            Log.e(TAG, "getAgent error", e)
            null
        }
    }

    suspend fun listAgents(includePublic: Boolean = false): List<AiV2Agent> {
        return try {
            val agents = RealGrpcClient.aiV2Client.listAgents(includePublic).map { agent: AgentInfoV2Proto -> agent.toDomain() }
            AiV2ChatManager.setAgents(agents)
            agents
        } catch (e: Exception) {
            Log.e(TAG, "listAgents error", e)
            emptyList()
        }
    }

    suspend fun cloneAgent(agentId: String, newName: String): Result<String> {
        return try {
            val response = RealGrpcClient.aiV2Client.cloneAgent(agentId, newName)
            if (response.success) Result.success(response.agentId)
            else Result.failure(Exception(response.error.ifEmpty { "Failed to clone agent" }.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "cloneAgent error", e)
            Result.failure(e)
        }
    }

    // ======= Tools =======

    suspend fun listTools(): List<AiV2Tool> {
        return try {
            val tools = RealGrpcClient.aiV2Client.listTools().map { it.toDomain() }
            AiV2ChatManager.setTools(tools)
            tools
        } catch (e: Exception) {
            Log.e(TAG, "listTools error", e)
            emptyList()
        }
    }

    // ======= AI Chat Settings =======

    suspend fun getChatSettings(sessionId: String): AIChatSettingsProto {
        return try {
            RealGrpcClient.aiV2Client.getChatSettings(sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "getChatSettings error", e)
            AIChatSettingsProto()
        }
    }

    suspend fun updateChatSettings(sessionId: String, apiKey: String = "", model: String = ""): UpdateAIChatSettingsResponseProto {
        return try {
            RealGrpcClient.aiV2Client.updateChatSettings(sessionId, apiKey, model)
        } catch (e: Exception) {
            Log.e(TAG, "updateChatSettings error", e)
            UpdateAIChatSettingsResponseProto(message = e.message ?: "Unknown error")
        }
    }

    // ======= Marketplace =======

    suspend fun listMarketplaceAgents(query: String = "", limit: Int = 20, offset: Int = 0): Result<Pair<List<MarketplaceAgent>, Int>> {
        return try {
            val response = RealGrpcClient.aiV2Client.listMarketplaceAgents(query, limit, offset)
            val agents = response.agents.map { it.toMarketplaceAgent() }
            Result.success(agents to response.total)
        } catch (e: Exception) {
            Log.e(TAG, "listMarketplaceAgents error", e)
            Result.failure(e)
        }
    }

    suspend fun getAgentStats(agentId: String): Result<AgentStats> {
        return try {
            val response = RealGrpcClient.aiV2Client.getAgentStats(agentId)
            Result.success(AgentStats(
                installCount = response.installCount,
                avgRating = response.avgRating,
                reviewCount = response.reviewCount
            ))
        } catch (e: Exception) {
            Log.e(TAG, "getAgentStats error", e)
            Result.failure(e)
        }
    }

    suspend fun getAgentReviews(agentId: String, limit: Int = 20): Result<Pair<List<AgentReview>, Pair<Float, Int>>> {
        return try {
            val response = RealGrpcClient.aiV2Client.getAgentReviews(agentId, limit)
            val reviews = response.reviews.map { it.toDomain() }
            Result.success(reviews to (response.avgRating to response.reviewCount))
        } catch (e: Exception) {
            Log.e(TAG, "getAgentReviews error", e)
            Result.failure(e)
        }
    }

    suspend fun rateAgent(agentId: String, rating: Int, review: String = ""): Result<Pair<Boolean, Pair<Float, Int>>> {
        return try {
            val response = RealGrpcClient.aiV2Client.rateAgent(agentId, rating, review)
            if (response.success) {
                Result.success(response.success to (response.avgRating to response.reviewCount))
            } else {
                Result.failure(Exception("Failed to rate agent"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "rateAgent error", e)
            Result.failure(e)
        }
    }

    suspend fun shareAgent(agentId: String): Result<String> {
        return try {
            val response = RealGrpcClient.aiV2Client.shareAgent(agentId)
            if (response.success) {
                Result.success(response.shareCode)
            } else {
                Result.failure(Exception("Failed to generate share code"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "shareAgent error", e)
            Result.failure(e)
        }
    }

    suspend fun installAgent(shareCode: String, newName: String = ""): Result<String> {
        return try {
            val response = RealGrpcClient.aiV2Client.installAgent(shareCode, newName)
            if (response.success) {
                Result.success(response.agentId)
            } else {
                Result.failure(Exception(response.error.ifEmpty { "Failed to install agent" }))
            }
        } catch (e: Exception) {
            Log.e(TAG, "installAgent error", e)
            Result.failure(e)
        }
    }

    suspend fun getUsageStats(): Result<Pair<List<UsageStat>, Pair<Int, Int>>> {
        return try {
            val response = RealGrpcClient.aiV2Client.getUsageStats()
            val stats = response.stats.map { it.toDomain() }
            Result.success(stats to (response.totalTokens to response.totalRequests))
        } catch (e: Exception) {
            Log.e(TAG, "getUsageStats error", e)
            Result.failure(e)
        }
    }
}
