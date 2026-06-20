package lavender.client.android.data.ai

import lavender.client.android.data.proto.*

// ======= Proto -> Domain Mapping =======

fun AgentInfoV2Proto.toDomain(): AiV2Agent = AiV2Agent(
    id = id,
    name = name,
    description = description,
    providerType = AiProviderType.fromString(providerType),
    model = model,
    systemPrompt = systemPrompt,
    toolsEnabled = toolsEnabled,
    ragEnabled = ragEnabled,
    isPreset = isPreset,
    isPublic = isPublic,
    maxTokens = maxTokens,
    temperature = temperature,
    createdBy = createdBy,
    capabilities = capabilities.toDomain()
)

fun AgentCapabilitiesV2Proto.toDomain(): AiAgentCapabilities = AiAgentCapabilities(
    supportsImages = supportsImages,
    supportsTools = supportsTools,
    supportsStreaming = supportsStreaming,
    maxTokens = maxTokens
)

fun ToolInfoV2Proto.toDomain(): AiV2Tool = AiV2Tool(
    name = name,
    description = description,
    parametersSchema = parametersSchema,
    requiredRole = requiredRole
)

fun ToolCallRequestV2Proto.toDomain(): AiV2ToolCall = AiV2ToolCall(
    id = id,
    name = name,
    arguments = arguments
)

fun AiV2ToolCall.toProto(): ToolCallV2Proto = ToolCallV2Proto(
    id = id,
    name = name,
    arguments = arguments,
    result = result
)

fun ChatWithAIV2ResponseProto.toStreamState(): AiV2StreamState = AiV2StreamState(
    isStreaming = !finished,
    isTyping = !finished && token.isEmpty(),
    tokens = if (token.isNotEmpty()) listOf(token) else emptyList(),
    error = error.takeIf { it.isNotEmpty() },
    finished = finished,
    agentId = agentId,
    agentName = agentName,
    toolCalls = toolCalls.map { it.toDomain() },
    hasRagContext = hasRagContext,
    modelUsed = modelUsed,
    tokenCount = tokenCount
)

fun ChatWithAIV2ResponseProto.toChatMessage(sessionId: String): AiV2ChatMessage = AiV2ChatMessage(
    sessionId = sessionId,
    role = "assistant",
    content = token,
    agentId = agentId,
    agentName = agentName,
    timestamp = System.currentTimeMillis(),
    isStreaming = !finished,
    toolCalls = toolCalls.map { it.toDomain() },
    hasRagContext = hasRagContext,
    modelUsed = modelUsed,
    tokenCount = tokenCount
)
