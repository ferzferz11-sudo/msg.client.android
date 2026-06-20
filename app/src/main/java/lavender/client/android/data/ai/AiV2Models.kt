package lavender.client.android.data.ai

// ======= AI Services v2 — Domain Models =======

enum class AiProviderType(val value: String) {
    OPENROUTER("openrouter"),
    LOCAL("local"),
    MIMO("mimo"),
    WEBHOOK("webhook"),
    WEBSOCKET("websocket"),
    SUBPROCESS("subprocess"),
    MCP("mcp");

    companion object {
        fun fromString(value: String): AiProviderType {
            return entries.find { it.value == value } ?: OPENROUTER
        }
    }
}

data class AiV2Agent(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val providerType: AiProviderType = AiProviderType.OPENROUTER,
    val model: String = "",
    val systemPrompt: String = "",
    val toolsEnabled: Boolean = false,
    val ragEnabled: Boolean = false,
    val isPreset: Boolean = false,
    val isPublic: Boolean = false,
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val createdBy: String = "",
    val capabilities: AiAgentCapabilities = AiAgentCapabilities()
)

data class AiAgentCapabilities(
    val supportsImages: Boolean = false,
    val supportsTools: Boolean = false,
    val supportsStreaming: Boolean = false,
    val maxTokens: Int = 0
)

data class AiV2ToolCall(
    val id: String = "",
    val name: String = "",
    val arguments: String = "",
    var result: String = ""
)

data class AiV2StreamState(
    val isStreaming: Boolean = false,
    val isTyping: Boolean = false,
    val tokens: List<String> = emptyList(),
    val error: String? = null,
    val finished: Boolean = false,
    val agentId: String = "",
    val agentName: String = "",
    val toolCalls: List<AiV2ToolCall> = emptyList(),
    val hasRagContext: Boolean = false,
    val modelUsed: String = "",
    val tokenCount: Int = 0
)

data class AiV2Tool(
    val name: String = "",
    val description: String = "",
    val parametersSchema: String = "",
    val requiredRole: String = ""
)

data class AiV2ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sessionId: String = "",
    val role: String = "",
    val content: String = "",
    val agentId: String = "",
    val agentName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val toolCalls: List<AiV2ToolCall> = emptyList(),
    val hasRagContext: Boolean = false,
    val modelUsed: String = "",
    val tokenCount: Int = 0
)

data class AiV2ChatSession(
    val id: String = "",
    val agentId: String = "",
    val agentName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ======= AI Marketplace — Domain Models =======

data class MarketplaceAgent(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val providerType: AiProviderType = AiProviderType.OPENROUTER,
    val model: String = "",
    val toolsEnabled: Boolean = false,
    val ragEnabled: Boolean = false,
    val isPreset: Boolean = false,
    val isPublic: Boolean = false,
    val avgRating: Float = 0f,
    val installCount: Int = 0
)

data class AgentStats(
    val installCount: Int = 0,
    val avgRating: Float = 0f,
    val reviewCount: Int = 0
)

data class AgentReview(
    val userId: String = "",
    val rating: Int = 0,
    val review: String = "",
    val createdAt: String = ""
)

data class UsageStat(
    val agentId: String = "",
    val agentName: String = "",
    val totalTokens: Int = 0,
    val requestCount: Int = 0,
    val periodStart: String = ""
)
