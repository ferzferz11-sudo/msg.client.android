package lavender.client.android.data.proto

// ======= AI Services v2 — ChatWithAIV2 =======
// Server proto: messenger.AIService/ChatWithAIV2

data class ChatWithAIV2RequestProto(
    val sessionId: String = "",
    val message: String = "",
    val images: List<ByteArray> = emptyList(),
    val agentId: String = "",
    val toolCalls: List<ToolCallV2Proto> = emptyList()
)

data class ChatWithAIV2ResponseProto(
    val token: String = "",
    val finished: Boolean = false,
    val error: String = "",
    val agentId: String = "",
    val agentName: String = "",
    val toolCalls: List<ToolCallRequestV2Proto> = emptyList(),
    val hasRagContext: Boolean = false,
    val modelUsed: String = "",
    val tokenCount: Int = 0
)

data class ToolCallRequestV2Proto(
    val id: String = "",
    val name: String = "",
    val arguments: String = ""
)

data class ToolCallV2Proto(
    val id: String = "",
    val name: String = "",
    val arguments: String = "",
    val result: String = ""
)

// ======= AI Services v2 — Agent CRUD =======
// Server proto: messenger.AIService/CreateAIAgent, UpdateAIAgent, etc.

data class AgentInfoV2Proto(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val providerType: String = "",
    val model: String = "",
    val systemPrompt: String = "",
    val toolsEnabled: Boolean = false,
    val ragEnabled: Boolean = false,
    val isPreset: Boolean = false,
    val isPublic: Boolean = false,
    val maxTokens: Int = 0,
    val temperature: Float = 0.7f,
    val createdBy: String = "",
    val capabilities: AgentCapabilitiesV2Proto = AgentCapabilitiesV2Proto()
)

data class AgentCapabilitiesV2Proto(
    val supportsImages: Boolean = false,
    val supportsTools: Boolean = false,
    val supportsStreaming: Boolean = false,
    val maxTokens: Int = 0
)

data class CreateAIAgentRequestProto(
    val name: String = "",
    val description: String = "",
    val providerType: String = "",
    val providerConfig: String = "",
    val systemPrompt: String = "",
    val model: String = "",
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val toolsEnabled: Boolean = false,
    val toolWhitelist: List<String> = emptyList(),
    val ragEnabled: Boolean = false,
    val ragConfig: String = "",
    val rateLimit: Int = 0,
    val isPublic: Boolean = false
)

data class CreateAIAgentResponseProto(
    val success: Boolean = false,
    val agentId: String = "",
    val error: String = ""
)

data class UpdateAIAgentRequestProto(
    val agentId: String = "",
    val name: String = "",
    val description: String = "",
    val providerConfig: String = "",
    val systemPrompt: String = "",
    val model: String = "",
    val maxTokens: Int = 0,
    val temperature: Float = 0f,
    val toolsEnabled: Boolean = false,
    val toolWhitelist: List<String> = emptyList(),
    val ragEnabled: Boolean = false,
    val ragConfig: String = "",
    val rateLimit: Int = 0,
    val isPublic: Boolean = false
)

data class UpdateAIAgentResponseProto(
    val success: Boolean = false,
    val error: String = ""
)

data class DeleteAIAgentRequestProto(
    val agentId: String = ""
)

data class DeleteAIAgentResponseProto(
    val success: Boolean = false,
    val error: String = ""
)

data class GetAIAgentRequestProto(
    val agentId: String = ""
)

data class GetAIAgentResponseProto(
    val agent: AgentInfoV2Proto = AgentInfoV2Proto()
)

data class ListAIAgentsRequestProto(
    val includePublic: Boolean = false
)

data class ListAIAgentsResponseProto(
    val agents: List<AgentInfoV2Proto> = emptyList()
)

data class CloneAIAgentRequestProto(
    val agentId: String = "",
    val newName: String = ""
)

data class CloneAIAgentResponseProto(
    val success: Boolean = false,
    val agentId: String = "",
    val error: String = ""
)

// ======= AI Services v2 — Tools =======
// Server proto: messenger.AIService/ListAITools

data class ListAIToolsRequestProto(
    val dummy: Boolean = false
)

data class ListAIToolsResponseProto(
    val tools: List<ToolInfoV2Proto> = emptyList()
)

data class ToolInfoV2Proto(
    val name: String = "",
    val description: String = "",
    val parametersSchema: String = "",
    val requiredRole: String = ""
)
