package lavender.client.android.ui.hermes

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import lavender.client.android.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lavender.client.android.data.db.AppDatabase
import lavender.client.android.data.db.ChatEntity
import lavender.client.android.data.db.toHermesMessage
import lavender.client.android.data.db.toMessageEntity
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.*
import lavender.client.android.data.repository.HermesRepository
import lavender.client.android.data.grpc.*

class HermesChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HermesRepository()
    private val prefs = application.getSharedPreferences("hermes_prefs", Context.MODE_PRIVATE)
    private val db = AppDatabase.getDatabase(application)
    private val chatDao = db.chatDao()
    private val messageDao = db.messageDao()

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

    // Agents registry — агенты как участники группового чата
    private val _agents = MutableStateFlow<List<AgentInfo>>(emptyList())
    val agents: StateFlow<List<AgentInfo>> = _agents.asStateFlow()

    // Accumulated streaming content
    private var streamingContent = ""
    private var streamingAgentId = ""
    private var streamingAgentName = ""

    /**
     * Create a new Hermes session
     */
    fun createSession(userId: String, agentId: String = "", mode: String = "single") {
        android.util.Log.d("HermesChatVM", "createSession: userId=$userId agentId=$agentId mode=$mode")
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = repository.createSession(userId, agentId, mode)
            result.onSuccess { session ->
                android.util.Log.d("HermesChatVM", "createSession SUCCESS: sessionId=${session.id}")
                _currentSession.value = session
                if (agentId.isNotEmpty()) {
                    _currentSession.value = session.copy(activeAgentId = agentId)
                }
                // Save the new chat to the database
                val chatEntity = ChatEntity(
                    id = session.id,
                    name = "Lava AI",
                    type = "hermes",
                    participants = "[\"$userId\"]",
                    createdAt = System.currentTimeMillis(),
                    lastMessageTime = System.currentTimeMillis(),
                    creator = userId,
                    lastMessageText = "New chat with Lava AI",
                    unreadCount = 0,
                    avatarUrl = "",
                    fullAvatarUrl = "",
                    lastMessageUsername = "",
                    muted = false,
                    activeAgentId = agentId,
                    agentMode = mode
                )
                chatDao.insertChats(listOf(chatEntity))
            }.onFailure { e ->
                android.util.Log.e("HermesChatVM", "createSession FAILED: ${e.message}", e)
                _error.value = e.message ?: "Failed to create session"
            }
            _isLoading.value = false
        }
    }

    /**
     * Set existing session (when opening from chat list)
     */
    fun setExistingSession(sessionId: String, userId: String, agentId: String = "", mode: String = "single") {
        _currentSession.value = HermesSession(
            id = sessionId,
            userId = userId,
            activeAgentId = agentId,
            mode = mode
        )
    }

    /**
     * Load history for current session.
     * 1. Load from local DB first (fast)
     * 2. Then fetch from server and merge
     */
    fun loadHistory() {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            // 1. Load from local DB first
            val localMessages: List<HermesMessage> = withContext(Dispatchers.IO) {
                messageDao.getMessagesForRoom(session.id).map { it.toHermesMessage() }
            }
            if (localMessages.isNotEmpty()) {
                _messages.value = localMessages
            }
            // 2. Fetch from server
            val history = repository.getHistory(session.id, 50)
            if (history.isNotEmpty()) {
                _messages.value = history
                // Save to local DB
                withContext(Dispatchers.IO) {
                    messageDao.insertMessages(history.map { it.toMessageEntity(session.id) })
                }
            }
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
            // Clear local messages for this session
            withContext(Dispatchers.IO) {
                messageDao.clearRoom(session.id)
            }
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
        // Save to local DB
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.insertMessages(listOf(userMessage.toMessageEntity(session.id)))
        }

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

        val defaultModel = prefs.getString("default_model", "openai/gpt-oss-120b:free") ?: ""
        val agent = _agents.value.find { it.id == agentId }
        val modelToSend = agent?.model?.ifEmpty { defaultModel } ?: defaultModel

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
                        content = getApplication<Application>().getString(R.string.error_colon, error),
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
                // Save final agent message to local DB
                val finalMessages = _messages.value
                if (finalMessages.isNotEmpty()) {
                    val lastMsg = finalMessages.last()
                    if (!lastMsg.isStreaming && lastMsg.role == "assistant") {
                        viewModelScope.launch(Dispatchers.IO) {
                            messageDao.insertMessages(listOf(lastMsg.toMessageEntity(session.id)))
                        }
                    }
                }
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

    /**
     * Add agent as chat participant.
     * Called from AgentListActivity or when orchestrator routes to a new agent.
     */
    fun addAgent(agent: AgentInfo) {
        val current = _agents.value.toMutableList()
        if (current.none { it.id == agent.id }) {
            current.add(agent)
            _agents.value = current
        }
    }

    /**
     * Remove agent from participants.
     */
    fun removeAgent(agentId: String) {
        _agents.value = _agents.value.filter { it.id != agentId }
    }

    /**
     * Get agent by ID.
     */
    fun getAgent(agentId: String): AgentInfo? {
        return _agents.value.find { it.id == agentId }
    }

    /**
     * Initialize with preset agents.
     */
    fun initPresetAgents() {
        val presets = listOf(
            AgentInfo("developer", "Developer", "Software development expert", "developer", true, "💻"),
            AgentInfo("designer", "Designer", "UI/UX design expert", "designer", true, "🎨"),
            AgentInfo("writer", "Writer", "Content writing expert", "writer", true, "✍️"),
            AgentInfo("analyst", "Analyst", "Data analysis expert", "analyst", true, "📊"),
            AgentInfo("translator", "Translator", "Translation expert", "translator", true, "🌐"),
            AgentInfo("researcher", "Researcher", "Research expert", "researcher", true, "🔬"),
            AgentInfo("tester", "Tester", "QA testing expert", "tester", true, "🧪"),
            AgentInfo("hermes-owl", "OWL", "General AI assistant", "assistant", true, "🦉")
        )
        _agents.value = presets
        // Set OWL as the default agent when the chat starts
        if (_currentAgent.value == null) {
            _currentAgent.value = presets.find { it.id == "hermes-owl" }
        }
    }
}