package lavender.client.android.data.ai

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AiV2ChatManager — single entry point for AI v2 chat state management.
 * Owns all SharedFlows / StateFlows for AI v2.
 * Use cases (AiV2ChatUseCase) emit here;
 * ViewModels collect from here.
 */
object AiV2ChatManager {

    // ====== Streaming ======

    private val _aiResponses = MutableSharedFlow<AiV2ChatMessage>(extraBufferCapacity = 64)
    val aiResponses: SharedFlow<AiV2ChatMessage> = _aiResponses.asSharedFlow()

    // ====== Agents ======

    private val _agents = MutableStateFlow<List<AiV2Agent>>(emptyList())
    val agents: StateFlow<List<AiV2Agent>> = _agents.asStateFlow()

    // ====== Tools ======

    private val _tools = MutableStateFlow<List<AiV2Tool>>(emptyList())
    val tools: StateFlow<List<AiV2Tool>> = _tools.asStateFlow()

    // ====== Stream State ======

    private val _streamState = MutableStateFlow(AiV2StreamState())
    val streamState: StateFlow<AiV2StreamState> = _streamState.asStateFlow()

    // ====== Emitters ======

    fun emitResponse(message: AiV2ChatMessage) {
        _aiResponses.tryEmit(message)
        _streamState.value = _streamState.value.copy(
            isStreaming = message.isStreaming,
            isTyping = message.isStreaming && message.content.isEmpty(),
            tokens = if (message.content.isNotEmpty()) _streamState.value.tokens + message.content else _streamState.value.tokens,
            error = null,
            finished = !message.isStreaming,
            agentId = message.agentId,
            agentName = message.agentName,
            toolCalls = message.toolCalls,
            hasRagContext = message.hasRagContext,
            modelUsed = message.modelUsed,
            tokenCount = message.tokenCount
        )
    }

    fun setAgents(agents: List<AiV2Agent>) {
        _agents.value = agents
    }

    fun setTools(tools: List<AiV2Tool>) {
        _tools.value = tools
    }
}
