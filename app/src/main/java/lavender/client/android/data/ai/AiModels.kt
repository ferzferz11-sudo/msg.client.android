package lavender.client.android.data.ai

/**
 * AI source type — distinguishes between OWL and Hermes AI backends.
 */
enum class AiSource {
    OWL,
    HERMES
}

/**
 * Unified AI chat session — domain model independent of proto.
 */
data class AiChatSession(
    val id: String = "",
    val userId: String = "",
    val source: AiSource = AiSource.OWL,
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val activeAgentId: String = "",       // Hermes-specific
    val mode: String = "single",           // Hermes-specific: single/parallel/pipeline
    val isUsingCustomKey: Boolean = false, // OWL-specific
    val model: String = ""                 // OWL-specific: OpenRouter model ID
)

/**
 * Unified AI chat message — domain model independent of proto.
 */
data class AiChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sessionId: String = "",
    val role: String = "",           // "user", "assistant", "agent", "system"
    val content: String = "",
    val agentId: String = "",        // Hermes: which agent produced this
    val agentName: String = "",      // Hermes: agent display name
    val source: AiSource = AiSource.OWL,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

/**
 * AI chat settings — unified for both OWL and Hermes.
 */
data class AiChatSettings(
    val sessionId: String = "",
    val userId: String = "",
    val source: AiSource = AiSource.OWL,
    val apiKey: String = "",
    val model: String = "",
    val isUsingCustomKey: Boolean = false,
    val remaining: Int = 0,
    val limit: Int = 0,
    val windowSeconds: Int = 0
)

/**
 * AI streaming state — represents the current state of an AI response stream.
 */
data class AiStreamState(
    val isStreaming: Boolean = false,
    val isTyping: Boolean = false,
    val tokens: List<String> = emptyList(),
    val error: String? = null,
    val finished: Boolean = false,
    val agentId: String = "",
    val agentName: String = ""
)
