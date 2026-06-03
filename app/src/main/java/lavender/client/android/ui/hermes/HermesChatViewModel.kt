package lavender.client.android.ui.hermes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.*
import lavender.client.android.data.repository.HermesRepository

class HermesChatViewModel : ViewModel() {

    private val repository = HermesRepository()

    // Current session
    private val _currentSession = MutableStateFlow<HermesSession?>(null)
    val currentSession: StateFlow<HermesSession?> = _currentSession.asStateFlow()

    // Messages
    private val _messages = MutableStateFlow<List<HermesMessage>>(emptyList())
    val messages: StateFlow<List<HermesMessage>> = _messages.asStateFlow()

    // Typing indicator
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    // Current agent (for direct agent chat)
    private val _currentAgent = MutableStateFlow<AgentInfo?>(null)
    val currentAgent: StateFlow<AgentInfo?> = _currentAgent.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Accumulated streaming content
    private var streamingContent = ""
    private var streamingAgentId = ""
    private var streamingAgentName = ""

    /**
     * Create a new Hermes session
     */
    fun createSession(userId: String, agentId: String = "", mode: String = "single") {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = repository.createSession(userId, agentId, mode)
            result.onSuccess { session ->
                _currentSession.value = session
                if (agentId.isNotEmpty()) {
                    _currentSession.value = session.copy(activeAgentId = agentId)
                }
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to create session"
            }
            _isLoading.value = false
        }
    }

    /**
     * Load history for current session
     */
    fun loadHistory() {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            val history = repository.getHistory(session.id, 50)
            _messages.value = history
            _isLoading.value = false
        }
    }

    /**
     * Delete current session
     */
    fun deleteSession() {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            repository.deleteSession(session.id, session.userId)
            _currentSession.value = null
            _messages.value = emptyList()
        }
    }

    /**
     * Send a message to the orchestrator
     */
    fun sendMessage(text: String, agentId: String = "", mode: String = "") {
        val session = _currentSession.value ?: return
        val userId = session.userId

        // Add user message
        val userMessage = HermesMessage(
            role = "user",
            content = text,
            timestamp = System.currentTimeMillis()
        )
        _messages.value = _messages.value + userMessage

        // Reset streaming state
        streamingContent = ""
        streamingAgentId = ""
        streamingAgentName = ""

        // Add streaming placeholder message
        val agentMessage = HermesMessage(
            role = "assistant",
            content = "",
            agentId = agentId,
            timestamp = System.currentTimeMillis(),
            isStreaming = true
        )
        _messages.value = _messages.value + agentMessage

        // Send via gRPC
        GrpcClient.chatWithOrchestrator(
            userId = userId,
            sessionId = session.id,
            message = text,
            agentId = agentId,
            mode = mode,
            scope = viewModelScope
        ) { token, finished, error, respAgentId, respAgentName ->
            if (error != null) {
                // Error
                val errorMsg = _messages.value.toMutableList()
                if (errorMsg.isNotEmpty()) {
                    errorMsg[errorMsg.size - 1] = errorMsg.last().copy(
                        content = "Ошибка: $error",
                        isStreaming = false
                    )
                }
                _messages.value = errorMsg
                _isTyping.value = false
                return@chatWithOrchestrator
            }

            // Accumulate streaming content
            streamingContent += token
            if (respAgentId.isNotEmpty()) streamingAgentId = respAgentId
            if (respAgentName.isNotEmpty()) streamingAgentName = respAgentName

            // Update last message
            val updatedMessages = _messages.value.toMutableList()
            if (updatedMessages.isNotEmpty()) {
                val lastIdx = updatedMessages.size - 1
                updatedMessages[lastIdx] = updatedMessages[lastIdx].copy(
                    content = streamingContent,
                    agentId = streamingAgentId,
                    agentName = streamingAgentName,
                    isStreaming = !finished
                )
                _messages.value = updatedMessages
            }

            if (finished) {
                _isTyping.value = false
                streamingContent = ""
                streamingAgentId = ""
                streamingAgentName = ""
            }
        }
    }

    /**
     * Switch to direct agent chat
     */
    fun switchAgent(agentId: String, agents: List<AgentInfo>) {
        val agent = agents.find { it.id == agentId }
        _currentAgent.value = agent
        _currentSession.value = _currentSession.value?.copy(activeAgentId = agentId)
    }

    /**
     * Clear error
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Set session from external source (e.g., Activity intent)
     */
    fun setSession(session: HermesSession) {
        _currentSession.value = session
    }

    /**
     * Set current agent from external source
     */
    fun setCurrentAgent(agent: AgentInfo) {
        _currentAgent.value = agent
    }
}
