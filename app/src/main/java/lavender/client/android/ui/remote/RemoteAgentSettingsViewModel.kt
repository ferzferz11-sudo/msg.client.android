package lavender.client.android.ui.remote

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.grpc.GrpcClient
import lavender.client.android.data.session.SessionManager

data class RemoteAgentSettingsUiState(
    val isLoading: Boolean = false,
    val tokens: List<TokenInfo> = emptyList(),
    val selectedAgentId: String = "",
    val selectedAgentName: String = "",
    val selectedToken: String = "",
    val agentStatus: String = "",
    val isTunnelActive: Boolean = false,
    val tunnelAddress: String = "",
    val error: String? = null,
    val successMessage: String? = null
)

class RemoteAgentSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RemoteAgentSettingsUiState())
    val uiState: StateFlow<RemoteAgentSettingsUiState> = _uiState.asStateFlow()

    private val userId: String = SessionManager.session.value.userId
    private val prefs = application.getSharedPreferences("lavender_prefs", android.content.Context.MODE_PRIVATE)

    companion object {
        private const val PREF_AGENT_ID = "remote_agent_id"
        private const val PREF_AGENT_NAME = "remote_agent_name"
        private const val PREF_AGENT_TOKEN = "remote_agent_token"
        private val DEFAULT_AGENT_SCRIPT_PATH = "/root/msg.remote.agent/hermes_remote_agent.py"
    }

    init {
        restoreSelectedAgent()
    }

    fun loadTokens() {
        viewModelScope.launch {
            try {
                val response = GrpcClient.listAgentTokens(userId)
                val tokens = mutableListOf<TokenInfo>()
                if (response.success) {
                    tokens.addAll(response.tokens.map { proto ->
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
                    })
                }
                _uiState.value = _uiState.value.copy(tokens = tokens)
            } catch (e: Exception) {
                Log.e("RemoteAgentSettings", "Failed to load tokens: ${e.message}")
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun generateToken(agentName: String, capabilities: List<String>, ttlHours: Int) {
        viewModelScope.launch {
            try {
                val agentId = "agent_${System.currentTimeMillis()}"
                val resp = GrpcClient.generateAgentToken(
                    agentId = agentId,
                    agentName = agentName,
                    capabilities = capabilities,
                    ttlHours = ttlHours,
                    adminUserId = userId
                )
                if (resp.success) {
                    val expiresAt = if (resp.expiresAt > 0) {
                        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                            .format(java.util.Date(resp.expiresAt * 1000))
                    } else ""

                    val agentCmd = "python3 $DEFAULT_AGENT_SCRIPT_PATH --server <server:port> --token ${resp.token}"

                    val newToken = TokenInfo(
                        id = 0,
                        agentId = agentId,
                        agentName = agentName,
                        tokenHash = resp.token.take(16),
                        capabilities = capabilities,
                        createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date()),
                        expiresAt = expiresAt,
                        revoked = false,
                        createdBy = userId,
                        fullToken = resp.token,
                        command = agentCmd
                    )

                    _uiState.value = _uiState.value.copy(
                        tokens = _uiState.value.tokens + newToken,
                        selectedAgentId = agentId,
                        selectedAgentName = agentName,
                        selectedToken = resp.token
                    )
                    saveSelectedAgent()
                    _uiState.value = _uiState.value.copy(successMessage = "Token generated")
                } else {
                    _uiState.value = _uiState.value.copy(error = resp.error)
                }
            } catch (e: Exception) {
                Log.e("RemoteAgentSettings", "Failed to generate token", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun revokeToken(token: TokenInfo) {
        viewModelScope.launch {
            try {
                val resp = GrpcClient.revokeAgentToken(token.agentId, userId)
                if (resp.success) {
                    loadTokens()
                    _uiState.value = _uiState.value.copy(successMessage = "Token revoked")
                } else {
                    _uiState.value = _uiState.value.copy(error = "Failed to revoke token")
                }
            } catch (e: Exception) {
                Log.e("RemoteAgentSettings", "Failed to revoke token", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun startAgentOnServer() {
        val state = _uiState.value
        if (state.selectedToken.isEmpty()) {
            _uiState.value = state.copy(error = "Generate token first")
            return
        }

        viewModelScope.launch {
            try {
                val resp = GrpcClient.startAgentOnServer(
                    agentId = state.selectedAgentId,
                    agentName = state.selectedAgentName,
                    token = state.selectedToken,
                    serverAddress = "",
                    adminUserId = userId
                )
                if (resp.success) {
                    _uiState.value = _uiState.value.copy(successMessage = "Agent started: ${resp.pid}")
                } else {
                    _uiState.value = _uiState.value.copy(error = resp.error)
                }
            } catch (e: Exception) {
                Log.e("RemoteAgentSettings", "Failed to start agent", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun stopAgentOnServer() {
        val state = _uiState.value
        if (state.selectedAgentId.isEmpty()) {
            _uiState.value = state.copy(error = "Agent not selected")
            return
        }

        viewModelScope.launch {
            try {
                val resp = GrpcClient.stopAgentOnServer(state.selectedAgentId, userId)
                if (resp.success) {
                    _uiState.value = _uiState.value.copy(successMessage = "Agent stopped")
                } else {
                    _uiState.value = _uiState.value.copy(error = resp.error)
                }
            } catch (e: Exception) {
                Log.e("RemoteAgentSettings", "Failed to stop agent", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun checkAgentStatus() {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                if (state.selectedAgentId.isNotEmpty()) {
                    val status = GrpcClient.getRemoteAgentStatus(state.selectedAgentId)
                    _uiState.value = _uiState.value.copy(agentStatus = status.status)
                } else {
                    _uiState.value = _uiState.value.copy(agentStatus = "not_running")
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(agentStatus = "error")
            }
        }
    }

    fun setTunnelActive(isActive: Boolean, address: String = "") {
        _uiState.value = _uiState.value.copy(isTunnelActive = isActive, tunnelAddress = address)
        if (isActive) {
            _uiState.value = _uiState.value.copy(
                selectedAgentId = "gateway_agent",
                selectedAgentName = "Agent via Gateway"
            )
            saveSelectedAgent()
        }
    }

    private fun restoreSelectedAgent() {
        _uiState.value = _uiState.value.copy(
            selectedAgentId = prefs.getString(PREF_AGENT_ID, "") ?: "",
            selectedAgentName = prefs.getString(PREF_AGENT_NAME, "") ?: "",
            selectedToken = prefs.getString(PREF_AGENT_TOKEN, "") ?: ""
        )
    }

    private fun saveSelectedAgent() {
        val state = _uiState.value
        prefs.edit()
            .putString(PREF_AGENT_ID, state.selectedAgentId)
            .putString(PREF_AGENT_NAME, state.selectedAgentName)
            .putString(PREF_AGENT_TOKEN, state.selectedToken)
            .apply()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccess() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
