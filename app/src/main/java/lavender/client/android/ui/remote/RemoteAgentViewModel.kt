package lavender.client.android.ui.remote

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.models.AppLog
import lavender.client.android.data.models.RemoteAgentInfo

class RemoteAgentViewModel(application: Application) : AndroidViewModel(application) {

    private val gatewayManager = HermesGatewayManager(application.applicationContext)
    private val prefs = application.getSharedPreferences("lavender_prefs", Context.MODE_PRIVATE)

    // ===== Agent list =====
    private val _agents = MutableStateFlow<List<RemoteAgentInfo>>(emptyList())
    val agents: StateFlow<List<RemoteAgentInfo>> = _agents.asStateFlow()

    // ===== Selected agent =====
    private val _selectedAgent = MutableStateFlow<RemoteAgentInfo?>(null)
    val selectedAgent: StateFlow<RemoteAgentInfo?> = _selectedAgent.asStateFlow()

    init {
        restoreSelectedAgent()
    }

    private fun restoreSelectedAgent() {
        val agentId = prefs.getString("remote_agent_id", "") ?: ""
        val agentName = prefs.getString("remote_agent_name", "") ?: ""
        if (agentId.isNotEmpty() && agentName.isNotEmpty()) {
            _selectedAgent.value = RemoteAgentInfo(
                id = agentId, name = agentName,
                host = prefs.getString("remote_agent_host", "") ?: "",
                status = "restored"
            )
            android.util.Log.d("RemoteAgentVM", "Restored agent: $agentName ($agentId)")
        }
    }

    fun persistSelectedAgent(agent: RemoteAgentInfo) {
        prefs.edit()
            .putString("remote_agent_id", agent.id)
            .putString("remote_agent_name", agent.name)
            .putString("remote_agent_host", agent.host)
            .apply()
    }

    // ===== Connection status =====
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // ===== Loading =====
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ===== Error (critical — shown as Toast) =====
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ===== Info message (non-critical — logged only, no Toast) =====
    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

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

    // ===== Agent load flag =====
    private var agentsLoaded = false

    /**
     * Ensure an agent is selected. Tries loading from server first,
     * falls back to default local agent if server unavailable.
     * Must be called from a coroutine context.
     */
    private suspend fun ensureAgentSelected(): Boolean {
        if (_selectedAgent.value != null) return true

        if (!agentsLoaded) {
            agentsLoaded = true
            try {
                val result = GrpcClient.listRemoteAgents()
                val loadedAgents = result.map { proto ->
                    RemoteAgentInfo(
                        id = proto.id, name = proto.name,
                        host = proto.host, ipAddress = proto.ipAddress,
                        os = proto.os, status = proto.status,
                        capabilities = proto.capabilities,
                        activeTasks = proto.activeTasks,
                        lastHeartbeat = proto.lastHeartbeat
                    )
                }
                _agents.value = loadedAgents
                if (loadedAgents.isNotEmpty()) {
                    selectAgent(loadedAgents.first())
                    return true
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.info("RemoteAgentVM.ensureAgentSelected",
                    "Server agent list unavailable: ${e.message}")
            }
        }

        if (_agents.value.isNotEmpty()) {
            selectAgent(_agents.value.first())
            return true
        }

        // Fallback: create default local agent with meaningful name
        val gwSettings = gatewayManager.loadSettings()
        val agentName = if (gwSettings.sshHost.isNotEmpty()) {
            "Агент @ ${gwSettings.sshHost}"
        } else if (prefs.getString("remote_agent_name", "")?.isNotEmpty() == true) {
            prefs.getString("remote_agent_name", "Lava Agent") ?: "Lava Agent"
        } else {
            "Lava Agent"
        }
        val defaultAgent = RemoteAgentInfo(
            id = "default",
            name = agentName,
            host = gwSettings.sshHost.ifEmpty { "" },
            status = "local"
        )
        _agents.value = listOf(defaultAgent)
        selectAgent(defaultAgent)
        return true
    }

    /**
     * Load remote agents from server.
     * Non-critical: failures are logged but NOT shown to user as errors.
     */
    fun loadAgents() {
        viewModelScope.launch {
            _isLoading.value = true
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
                agentsLoaded = true
                if (_selectedAgent.value == null && _agents.value.isNotEmpty()) {
                    selectAgent(_agents.value.first())
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.d("RemoteAgentViewModel", "loadAgents cancelled")
                throw e
            } catch (e: Exception) {
                android.util.Log.e("RemoteAgentViewModel", "loadAgents error", e)
                AppLog.info("RemoteAgentVM.loadAgents",
                    "Agent list unavailable: ${e.message}")
            }
            _isLoading.value = false
        }
    }

    /**
     * Select an agent to interact with
     */
    fun selectAgent(agent: RemoteAgentInfo) {
        _selectedAgent.value = agent
        _isConnected.value = agent.status == "connected" || agent.status == "local" || agent.status == "restored"
        persistSelectedAgent(agent)
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
                    loadTokens(adminUserId)
                } else {
                    _error.value = response.error.ifEmpty { "Failed to generate token" }
                    AppLog.error("RemoteAgentVM.generateToken", "Server returned error: ${response.error}")
                }
            } catch (e: Exception) {
                AppLog.error("RemoteAgentVM.generateToken", "Failed to generate token: ${e.message}", e)
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
                    AppLog.error("RemoteAgentVM.revokeToken", "Server returned error: ${response.error}")
                }
            } catch (e: Exception) {
                AppLog.error("RemoteAgentVM.revokeToken", "Failed to revoke token: ${e.message}", e)
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
     * Send a task to the selected remote agent via DeployAgentTask.
     * Ensures agent is selected before sending (loads from server or creates default).
     */
    fun sendMessage(text: String, userId: String, taskType: String = "shell") {
        viewModelScope.launch {
            if (!ensureAgentSelected()) {
                _error.value = "Агент не выбран"
                return@launch
            }
            val agent = _selectedAgent.value ?: run {
                _error.value = "Агент не выбран"
                return@launch
            }

            val userMsg = RemoteAgentMessage(
                id = java.util.UUID.randomUUID().toString(),
                content = text,
                isUser = true,
                timestamp = System.currentTimeMillis(),
                taskType = taskType
            )
            _messages.value = _messages.value + userMsg

            _isTyping.value = true
            try {
                val tunnelActive = RemoteAgentManager.isTunnelActive()
                val settings = gatewayManager.loadSettings()
                val response = GrpcClient.deployAgentTask(
                    agentId = agent.id,
                    taskType = taskType,
                    params = mapOf("command" to text),
                    tunnelMode = if (tunnelActive) 1 else 0,
                    tunnelHost = settings.sshHost,
                    tunnelPort = settings.sshPort,
                    tunnelUser = settings.sshUser,
                    tunnelServerHost = settings.serverHost,
                    tunnelServerPort = settings.serverPort,
                    tunnelLocalPort = settings.localPort
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
                        content = "Ошибка (exit=${response.exitCode}): $errText",
                        isUser = false,
                        timestamp = System.currentTimeMillis()
                    )
                    _messages.value = _messages.value + agentMsg
                    AppLog.error("RemoteAgentVM.sendMessage", "Task failed: $errText (exit=${response.exitCode})")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                AppLog.info("RemoteAgentVM.sendMessage", "Task cancelled by user (navigation)")
            } catch (e: Exception) {
                AppLog.error("RemoteAgentVM.sendMessage", "Task error: ${e.message}", e)
                val agentMsg = RemoteAgentMessage(
                    id = java.util.UUID.randomUUID().toString(),
                    content = "Ошибка: ${e.message}",
                    isUser = false,
                    timestamp = System.currentTimeMillis()
                )
                _messages.value = _messages.value + agentMsg
            }
            _isTyping.value = false
        }
    }

    /**
     * Send a task with streaming — collects stdout/stderr/progress in real-time.
     * Ensures agent is selected before sending (loads from server or creates default).
     */
    fun sendMessageStreaming(text: String, userId: String, taskType: String = "shell") {
        viewModelScope.launch {
            if (!ensureAgentSelected()) {
                _error.value = "Агент не выбран"
                return@launch
            }
            val agent = _selectedAgent.value ?: run {
                _error.value = "Агент не выбран"
                return@launch
            }

            val userMsg = RemoteAgentMessage(
                id = java.util.UUID.randomUUID().toString(),
                content = text,
                isUser = true,
                timestamp = System.currentTimeMillis(),
                taskType = taskType
            )
            _messages.value = _messages.value + userMsg

            val streamMsgId = java.util.UUID.randomUUID().toString()
            val streamMsg = RemoteAgentMessage(
                id = streamMsgId,
                content = "",
                isUser = false,
                timestamp = System.currentTimeMillis(),
                isStreaming = true
            )
            _messages.value = _messages.value + streamMsg

            _isTyping.value = true
            try {
                val tunnelActive = RemoteAgentManager.isTunnelActive()
                val settings = gatewayManager.loadSettings()
                val flow = try {
                    GrpcClient.deployAgentTaskStream(
                        agentId = agent.id,
                        taskType = taskType,
                        params = mapOf("command" to text),
                        tunnelMode = if (tunnelActive) 1 else 0,
                        tunnelHost = settings.sshHost,
                        tunnelPort = settings.sshPort,
                        tunnelUser = settings.sshUser,
                        tunnelServerHost = settings.serverHost,
                        tunnelServerPort = settings.serverPort,
                        tunnelLocalPort = settings.localPort
                    )
                } catch (e: Exception) {
                    AppLog.info("RemoteAgentVM.sendMessageStreaming", "Streaming not supported, falling back to unary")
                    val response = GrpcClient.deployAgentTask(
                        agentId = agent.id,
                        taskType = taskType,
                        params = mapOf("command" to text),
                        tunnelMode = if (tunnelActive) 1 else 0,
                        tunnelHost = settings.sshHost,
                        tunnelPort = settings.sshPort,
                        tunnelUser = settings.sshUser,
                        tunnelServerHost = settings.serverHost,
                        tunnelServerPort = settings.serverPort,
                        tunnelLocalPort = settings.localPort
                    )
                    kotlinx.coroutines.flow.flow {
                        if (response.success) {
                            emit(lavender.client.android.data.proto.DeployAgentTaskStreamResponseProto(
                                taskId = response.taskId,
                                status = "completed",
                                stdout = response.stdout,
                                stderr = response.stderr,
                                exitCode = response.exitCode,
                                done = true
                            ))
                        } else {
                            val errText = if (response.stderr.isNotEmpty()) response.stderr else response.message
                            emit(lavender.client.android.data.proto.DeployAgentTaskStreamResponseProto(
                                taskId = response.taskId,
                                status = "failed",
                                error = errText,
                                done = true
                            ))
                        }
                    }
                }
                var stdoutBuf = StringBuilder()
                var stderrBuf = StringBuilder()
                flow.collect { update ->
                    if (update.done) {
                        // Сервер теперь при финальном done=True отправляет полные буферы
                        // в полях stdout/stderr (из TaskResult). Используем их как основные,
                        // а накопленные чанки — как fallback если сервер не прислал.
                        val finalStdout = if (update.stdout.isNotEmpty()) {
                            update.stdout
                        } else {
                            stdoutBuf.toString()
                        }
                        val finalStderr = if (update.stderr.isNotEmpty()) {
                            update.stderr
                        } else {
                            stderrBuf.toString()
                        }
                        val finalContent = buildString {
                            if (finalStdout.isNotEmpty()) append(finalStdout)
                            if (finalStderr.isNotEmpty()) {
                                if (isNotEmpty()) append("\n")
                                append(finalStderr)
                            }
                            if (update.error.isNotEmpty()) {
                                if (isNotEmpty()) append("\n")
                                append("Ошибка: ${update.error}")
                            }
                            if (isEmpty()) append("(no output)")
                        }
                        val finalMsg = RemoteAgentMessage(
                            id = streamMsgId,
                            content = finalContent,
                            isUser = false,
                            timestamp = System.currentTimeMillis(),
                            isStreaming = false
                        )
                        _messages.value = _messages.value.map {
                            if (it.id == streamMsgId) finalMsg else it
                        }
                    } else {
                        if (update.stdoutChunk.isNotEmpty()) {
                            stdoutBuf.append(update.stdoutChunk)
                        }
                        if (update.stderrChunk.isNotEmpty()) {
                            stderrBuf.append(update.stderrChunk)
                        }
                        val currentContent = buildString {
                            if (stdoutBuf.isNotEmpty()) append(stdoutBuf)
                            if (stderrBuf.isNotEmpty()) {
                                if (isNotEmpty()) append("\n--- stderr ---\n")
                                append(stderrBuf)
                            }
                            if (update.progress.isNotEmpty()) {
                                if (isNotEmpty()) append("\n")
                                append(update.progress)
                            }
                        }
                        val updatedMsg = RemoteAgentMessage(
                            id = streamMsgId,
                            content = currentContent,
                            isUser = false,
                            timestamp = System.currentTimeMillis(),
                            isStreaming = true
                        )
                        _messages.value = _messages.value.map {
                            if (it.id == streamMsgId) updatedMsg else it
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                AppLog.info("RemoteAgentVM.sendMessageStreaming", "Stream cancelled by user (navigation)")
            } catch (e: Exception) {
                AppLog.error("RemoteAgentVM.sendMessageStreaming", "Stream error: ${e.message}", e)
                val errMsg = RemoteAgentMessage(
                    id = streamMsgId,
                    content = "Ошибка: ${e.message}",
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    isStreaming = false
                )
                _messages.value = _messages.value.map {
                    if (it.id == streamMsgId) errMsg else it
                }
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
