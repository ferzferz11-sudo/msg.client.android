package lavender.client.android.data.models

// ======= Hermes Multi-Agent Orchestrator Models =======

data class HermesMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String = "",           // "user", "assistant", "agent", "system"
    val content: String = "",
    val agentId: String = "",
    val agentName: String = "",
    val agentIcon: String = "",      // emoji icon of the agent
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)

data class AgentInfo(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val role: String = "",
    val isPreset: Boolean = false,
    val icon: String = "",           // emoji
    val model: String = ""           // OpenRouter model ID
)

data class AgentPreset(
    val id: String = "",
    val name: String = "",
    val role: String = "",
    val description: String = "",
    val icon: String = "",           // emoji
    val maxTokens: Int = 0
)

data class RemoteAgentInfo(
    val id: String = "",
    val name: String = "",
    val host: String = "",
    val ipAddress: String = "",
    val os: String = "",
    val status: String = "",         // "connected", "disconnected", "busy", "error"
    val capabilities: List<String> = emptyList(),
    val activeTasks: Int = 0,
    val lastHeartbeat: String = ""
)

data class HermesSession(
    val id: String = "",
    val userId: String = "",
    val activeAgentId: String = "",
    val mode: String = "single",     // "single", "parallel", "pipeline"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// ======= OWL Bot Models =======

data class OwlMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderEmoji: String = "🦉",
    val timestamp: Long = System.currentTimeMillis(),
    val isCurrentUser: Boolean = false,
    val isTyping: Boolean = false
)
