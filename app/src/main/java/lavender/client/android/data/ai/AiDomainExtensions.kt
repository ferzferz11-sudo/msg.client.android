package lavender.client.android.data.ai

import lavender.client.android.data.models.AgentInfo
import lavender.client.android.data.models.RemoteAgentInfo
import lavender.client.android.data.proto.*

// ====== AgentInfo ======

fun AgentInfoProto.toDomain(): AgentInfo = AgentInfo(
    id = id,
    name = name,
    description = description,
    role = role,
    isPreset = isPreset,
    icon = icon,
    model = model
)

// ====== RemoteAgentInfo ======

fun RemoteAgentInfoProto.toDomain(): RemoteAgentInfo = RemoteAgentInfo(
    id = id,
    name = name,
    host = host,
    ipAddress = ipAddress,
    os = os,
    status = status,
    capabilities = capabilitiesList,
    activeTasks = activeTasks,
    lastHeartbeat = lastHeartbeat
)

// ====== Hermes Settings ======

fun GetHermesSettingsResponseProto.toDomain(sessionId: String, userId: String): AiChatSettings =
    AiChatSettings(
        sessionId = sessionId,
        userId = userId,
        source = AiSource.HERMES,
        apiKey = apiKey,
        model = model,
        isUsingCustomKey = isUsingCustomKey,
        remaining = remaining,
        limit = limit,
        windowSeconds = windowSeconds
    )

// ====== Hermes History ======

fun OrchestratorHistoryMessageProto.toDomain(sessionId: String): AiChatMessage = AiChatMessage(
    sessionId = sessionId,
    role = role,
    content = content,
    agentId = agentId,
    agentName = agentName,
    source = AiSource.HERMES,
    timestamp = System.currentTimeMillis(),
    isStreaming = false
)

// ====== OWL Response streaming ======

fun OwlResponseProto.toDomain(sessionId: String, role: String = "assistant"): AiChatMessage = AiChatMessage(
    sessionId = sessionId,
    role = role,
    content = text,
    source = AiSource.OWL,
    timestamp = System.currentTimeMillis(),
    isStreaming = !finished
)

// ====== OWL Settings ======

fun GetOwlSettingsResponseProto.toDomain(chatId: String, userId: String): AiChatSettings =
    AiChatSettings(
        sessionId = chatId,
        userId = userId,
        source = AiSource.OWL,
        apiKey = apiKey,
        model = model,
        isUsingCustomKey = isUsingCustomKey,
        remaining = remaining,
        limit = limit,
        windowSeconds = windowSeconds
    )

// ====== OWL History ======

fun OwlHistoryMessageProto.toDomain(sessionId: String): AiChatMessage = AiChatMessage(
    sessionId = sessionId,
    role = role,
    content = content,
    source = AiSource.OWL,
    timestamp = System.currentTimeMillis(),
    isStreaming = false
)
