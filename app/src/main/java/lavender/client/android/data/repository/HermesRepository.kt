package lavender.client.android.data.repository

import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.*
import lavender.client.android.data.proto.*

class HermesRepository {

    suspend fun getAgents(userId: String = ""): List<AgentInfo> {
        return try {
            GrpcClient.listAgents(userId).map { proto ->
                AgentInfo(
                    id = proto.id,
                    name = proto.name,
                    description = proto.description,
                    role = proto.role,
                    isPreset = proto.isPreset,
                    icon = proto.icon
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPresets(): List<AgentPreset> {
        return try {
            GrpcClient.listAgentPresets().map { proto ->
                AgentPreset(
                    id = proto.id,
                    name = proto.name,
                    role = proto.role,
                    description = proto.description,
                    icon = proto.icon,
                    maxTokens = proto.maxTokens
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getUserAgents(userId: String): List<AgentInfo> {
        return try {
            GrpcClient.listUserAgents(userId).map { proto ->
                AgentInfo(
                    id = proto.id,
                    name = proto.name,
                    description = proto.description,
                    role = proto.role,
                    isPreset = proto.isPreset,
                    icon = proto.icon
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createAgent(
        userId: String,
        presetId: String,
        customName: String = "",
        customPrompt: String = "",
        model: String = "",
        maxTokens: Int = 0
    ): Result<AgentInfo> {
        return try {
            val response = GrpcClient.createAgent(userId, presetId, customName, customPrompt, model, maxTokens)
            if (response.success && response.agent != null) {
                Result.success(
                    AgentInfo(
                        id = response.agent.id,
                        name = response.agent.name,
                        description = response.agent.description,
                        role = response.agent.role,
                        isPreset = response.agent.isPreset,
                        icon = response.agent.icon
                    )
                )
            } else {
                Result.failure(Exception(response.message.ifEmpty { "Failed to create agent" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateAgent(
        agentId: String,
        userId: String,
        name: String = "",
        systemPrompt: String = "",
        model: String = "",
        maxTokens: Int = 0
    ): Result<Boolean> {
        return try {
            val success = GrpcClient.updateAgent(agentId, userId, name, systemPrompt, model, maxTokens)
            if (success) Result.success(true)
            else Result.failure(Exception("Failed to update agent"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteAgent(agentId: String, userId: String): Result<Boolean> {
        return try {
            val success = GrpcClient.deleteAgent(agentId, userId)
            if (success) Result.success(true)
            else Result.failure(Exception("Failed to delete agent"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createSession(
        userId: String,
        agentId: String = "",
        mode: String = ""
    ): Result<HermesSession> {
        return try {
            val response = GrpcClient.createHermesSession(userId, agentId, mode)
            if (response.success) {
                Result.success(
                    HermesSession(
                        id = response.sessionId,
                        userId = userId,
                        activeAgentId = agentId,
                        mode = mode.ifEmpty { "single" }
                    )
                )
            } else {
                val errorMsg = response.message.ifEmpty { "Failed to create session" }
                lavender.client.android.data.models.AppLog.error(
                    "HermesRepository:createSession",
                    "userId=$userId agentId=$agentId mode=$mode error=$errorMsg"
                )
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            lavender.client.android.data.models.AppLog.error(
                "HermesRepository:createSession",
                "userId=$userId agentId=$agentId mode=$mode",
                e
            )
            Result.failure(e)
        }
    }

    suspend fun deleteSession(sessionId: String, userId: String): Result<Boolean> {
        return try {
            val success = GrpcClient.deleteHermesSession(sessionId, userId)
            if (success) Result.success(true)
            else Result.failure(Exception("Failed to delete session"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHistory(sessionId: String, limit: Int = 50): List<HermesMessage> {
        return try {
            GrpcClient.getOrchestratorHistory(sessionId, limit).map { proto ->
                HermesMessage(
                    role = proto.role,
                    content = proto.content,
                    agentId = proto.agentId,
                    agentName = proto.agentName,
                    timestamp = parseTimestamp(proto.createdAt),
                    isStreaming = false
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getRemoteAgents(filterStatus: String = ""): List<RemoteAgentInfo> {
        return try {
            GrpcClient.listRemoteAgents(filterStatus).map { proto ->
                RemoteAgentInfo(
                    id = proto.id,
                    name = proto.name,
                    host = proto.host,
                    ipAddress = proto.ipAddress,
                    os = proto.os,
                    status = proto.status,
                    capabilities = proto.capabilities,
                    activeTasks = proto.activeTasks,
                    lastHeartbeat = proto.lastHeartbeat
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseTimestamp(createdAt: String): Long {
        if (createdAt.isEmpty()) return System.currentTimeMillis()
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(createdAt)?.time
                ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}