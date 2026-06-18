package lavender.client.android.data.ai

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import lavender.client.android.data.models.AgentInfo
import lavender.client.android.data.models.RemoteAgentInfo

/**
 * AiChatManager — single entry point for AI chat state management.
 * Owns all SharedFlows / StateFlows for Hermes and OWL AI backends.
 * Use cases (HermesChatUseCase, OwlChatUseCase) emit here;
 * ViewModels collect from here.
 */
object AiChatManager {

    // ====== Hermes Orchestrator ======

    private val _hermesResponses = MutableSharedFlow<AiChatMessage>(extraBufferCapacity = 64)
    val hermesResponses: SharedFlow<AiChatMessage> = _hermesResponses

    private val _hermesTyping = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
    val hermesTyping: SharedFlow<Boolean> = _hermesTyping

    private val _hermesAgents = MutableStateFlow<List<AgentInfo>>(emptyList())
    val hermesAgents: StateFlow<List<AgentInfo>> = _hermesAgents.asStateFlow()

    private val _hermesSettings = MutableStateFlow<AiChatSettings?>(null)
    val hermesSettings: StateFlow<AiChatSettings?> = _hermesSettings.asStateFlow()

    private val _remoteAgents = MutableStateFlow<List<RemoteAgentInfo>>(emptyList())
    val remoteAgents: StateFlow<List<RemoteAgentInfo>> = _remoteAgents.asStateFlow()

    // ====== OWL AI ======

    private val _owlResponses = MutableSharedFlow<AiChatMessage>(extraBufferCapacity = 64)
    val owlResponses: SharedFlow<AiChatMessage> = _owlResponses

    private val _owlTyping = MutableSharedFlow<Boolean>(extraBufferCapacity = 8)
    val owlTyping: SharedFlow<Boolean> = _owlTyping

    private val _owlSettings = MutableStateFlow<AiChatSettings?>(null)
    val owlSettings: StateFlow<AiChatSettings?> = _owlSettings.asStateFlow()

    // ====== Hermes emitters (called from HermesChatUseCase) ======

    fun emitHermesResponse(message: AiChatMessage) {
        _hermesResponses.tryEmit(message)
    }

    fun emitHermesTyping(typing: Boolean) {
        _hermesTyping.tryEmit(typing)
    }

    fun setHermesAgents(agents: List<AgentInfo>) {
        _hermesAgents.value = agents
    }

    fun updateHermesSettings(settings: AiChatSettings) {
        _hermesSettings.value = settings
    }

    fun setRemoteAgents(agents: List<RemoteAgentInfo>) {
        _remoteAgents.value = agents
    }

    // ====== OWL emitters (called from OwlChatUseCase) ======

    fun emitOwlResponse(message: AiChatMessage) {
        _owlResponses.tryEmit(message)
    }

    fun emitOwlTyping(typing: Boolean) {
        _owlTyping.tryEmit(typing)
    }

    fun updateOwlSettings(settings: AiChatSettings) {
        _owlSettings.value = settings
    }
}
