package lavender.client.android.ui.remote

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.RemoteAgentInfo

class RemoteAgentViewModel(application: Application) : AndroidViewModel(application) {

    private val gatewayManager = HermesGatewayManager(application.applicationContext)

    // ===== Agent list =====
    private val _agents = MutableStateFlow<List<RemoteAgentInfo>>(emptyList())
    val agents: StateFlow<List<RemoteAgentInfo>> = _agents.asStateFlow()

    // ===== Selected agent =====
    private val _selectedAgent = MutableStateFlow<RemoteAgentInfo?>(null)
    val selectedAgent: StateFlow<RemoteAgentInfo?> = _selectedAgent.asStateFlow()

    // ===== Connection status =====
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // ===== Loading =====
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ===== Error =====
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ===== Token list =====
    private val _tokenList = MutableStateFlow<List<TokenInfo>>(emptyList())
    val tokenList: StateFlow<List<TokenInfo>> = _tokenList.asStateFlow()

    // ===== Generated token (one-shot display) =====
    private val _generatedToken = MutableStateFlow<String?>(null)
    val generatedToken: StateFlow<String?> = _generatedToken.asStateFlow()

    // ===== Messages for chat =====
    private val _messages = MutableStateFlow<List<RemoteAgentMessage>>(emptyList())
    val messages: StateFlow<List<RemoteAgentMessage>> = _messages.asStateFlow()

    // ===== Typing =====
    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    /**
     * Load remote agents from server
     */
    fun loadAgents() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = GrpcClient.listRemoteAgents()
                android.util.Log.d("RemoteAgentViewModel", "loadAgents: got ${result.size} agents")
                _agents.value = result.map { proto ->
                    RemoteAgentInfo(
                        id = proto.id,
                        name = proto.name,
                        host = proto.host,
                        ipAddress = proto.ipAddress,
                        os = proto.os,
                        status = proto.status,
                        capabilities = proto.capabilities,
                        activeTasks = proto.activeTasks,
                        lastHeartbeat = proto.lastHeartbeat
                    )
                }
                if (_selectedAgent.value == null && _agents.value.isNotEmpty()) {
                    selectAgent(_agents.value.first())
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.d("RemoteAgentViewModel", "loadAgents cancelled")
                throw e // re-throw to keep structured concurrency
            } catch (e: Exception) {
                android.util.Log.e("RemoteAgentViewModel", "loadAgents error", e)
                _error.value = e.message ?: "Failed to load agents"
            }
            _isLoading.value = false
        }
    }

    /**
     * Select an agent to interact with
     */
    fun selectAgent(agent: RemoteAgentInfo) {
        _selectedAgent.value = agent
        _isConnected.value = agent.status == "connected"
    }

    /**
     * Generate a new agent token
     */
    fun generateToken(
        agentId: String,
        agentName: String,
        capabilities: List<String>,
        ttlHours: Int,
        adminUserId: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = GrpcClient.generateAgentToken(
                    agentId = agentId,
                    agentName = agentName,
                    capabilities = capabilities,
                    ttlHours = ttlHours,
                    adminUserId = adminUserId
                )
                if (response.success) {
                    _generatedToken.value = response.token
                    // Reload token list
                    loadTokens(adminUserId)
                } else {
                    _error.value = response.error.ifEmpty { "Failed to generate token" }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to generate token"
            }
            _isLoading.value = false
        }
    }

    /**
     * Revoke an agent token
     */
    fun revokeToken(agentId: String, adminUserId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = GrpcClient.revokeAgentToken(agentId, adminUserId)
                if (response.success) {
                    loadTokens(adminUserId)
                } else {
                    _error.value = response.error.ifEmpty { "Failed to revoke token" }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to revoke token"
            }
            _isLoading.value = false
        }
    }

    /**
     * Load token list for current user
     */
    fun loadTokens(adminUserId: String) {
        viewModelScope.launch {
            try {
                val response = GrpcClient.listAgentTokens(adminUserId)
                if (response.success) {
                    _tokenList.value = response.tokens.map { proto ->
                        TokenInfo(
                            id = proto.id,
                            agentId = proto.agentId,
                            agentName = proto.agentName,
                            tokenHash = proto.tokenHash,
                            capabilities = proto.capabilities,
                            createdAt = proto.createdAt,
                            expiresAt = proto.expiresAt,
                            revoked = proto.revoked,
                            createdBy = proto.createdBy
                        )
                    }
                }
            } catch (e: Exception) {
                // Silent — token list is optional
            }
        }
    }

    /**
     * Clear generated token after user has seen it
     */
    fun clearGeneratedToken() {
        _generatedToken.value = null
    }

    /**
     * Clear error
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Send a task to the selected remote agent via DeployAgentTask
     */
    fun sendMessage(text: String, userId: String, taskType: String = "shell") {
        val agent = _selectedAgent.value
        if (agent == null) {
            _error.value = "Агент не выбран"
            return
        }

        // Add user message with task type indicator
        val userMsg = RemoteAgentMessage(
            id = java.util.UUID.randomUUID().toString(),
            content = text,
            isUser = true,
            timestamp = System.currentTimeMillis(),
            taskType = taskType
        )
        _messages.value = _messages.value + userMsg

        // Send task to agent
        _isTyping.value = true
        viewModelScope.launch {
            try {
                val response = GrpcClient.deployAgentTask(
                    agentId = agent.id,
                    taskType = taskType,
                    params = mapOf("command" to text),
                    tunnelMode = if (gatewayManager.isTunnelActive()) 1 else 0,
                    tunnelHost = gatewayManager.loadSettings().sshHost,
                    tunnelPort = gatewayManager.loadSettings().sshPort,
                    tunnelUser = gatewayManager.loadSettings().sshUser,
                    tunnelServerHost = gatewayManager.loadSettings().serverHost,
                    tunnelServerPort = gatewayManager.loadSettings().serverPort,
                    tunnelLocalPort = gatewayManager.loadSettings().localPort
                )
                if (response.success) {
                    val output = if (response.stdout.isNotEmpty()) response.stdout else "(no output)"
                    val agentMsg = RemoteAgentMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        content = output,
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                    _messages.value = _messages.value + agentMsg
                } else {
                    val errText = if (response.stderr.isNotEmpty()) response.stderr else response.message
                    val agentMsg = RemoteAgentMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        content = "❌ Ошибка (exit=${response.exitCode}): $errText",
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                    _messages.value = _messages.value + agentMsg
                }
            } catch (e: Exception) {
                val agentMsg = RemoteAgentMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    content = "❌ Ошибка: ${e.message}",
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
                _messages.value = _messages.value + agentMsg
            }
            _isTyping.value = false
        }
    }

    /**
     * Refresh agent status (heartbeat)
     */
    fun refreshAgentStatus() {
        val agent = _selectedAgent.value ?: return
        viewModelScope.launch {
            try {
                val status = GrpcClient.getRemoteAgentStatus(agent.id)
                _isConnected.value = status.status == "connected"
                // Update agent in list
                val updated = _agents.value.map {
                    if (it.id == agent.id) it.copy(
                        status = status.status,
                        activeTasks = status.activeTasks,
                        lastHeartbeat = status.lastHeartbeat
                    ) else it
                }
                _agents.value = updated
                _selectedAgent.value = updated.find { it.id == agent.id }
            } catch (e: Exception) {
                // Silent
            }
        }
    }
}

/**
 * UI model for a token
 */
data class TokenInfo(
    val id: Long = 0,
    val agentId: String = "",
    val agentName: String = "",
    val tokenHash: String = "",
    val capabilities: List<String> = emptyList(),
    val createdAt: String = "",
    val expiresAt: String = "",
    val revoked: Boolean = false,
    val createdBy: String = "",
    val fullToken: String = "",
    val command: String = ""
)

/**
 * UI model for remote agent chat message
 */
data class RemoteAgentMessage(
    val id: String = "",
    val content: String = "",
    val isUser: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val taskType: String = ""
)
