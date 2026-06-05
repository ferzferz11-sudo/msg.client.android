package lavender.client.android.ui.hermes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.models.*
import lavender.client.android.data.repository.HermesRepository

class AgentListViewModel : ViewModel() {

    private val repository = HermesRepository()

    private val _presets = MutableStateFlow<List<AgentPreset>>(emptyList())
    val presets: StateFlow<List<AgentPreset>> = _presets.asStateFlow()

    private val _customAgents = MutableStateFlow<List<AgentInfo>>(emptyList())
    val customAgents: StateFlow<List<AgentInfo>> = _customAgents.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadAgents() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val presets = repository.getPresets()
                _presets.value = presets
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load presets"
            }

            _isLoading.value = false
        }
    }

    fun loadUserAgents(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val agents = repository.getUserAgents(userId)
                _customAgents.value = agents
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load user agents"
            }
            _isLoading.value = false
        }
    }

    fun createAgent(
        userId: String,
        presetId: String,
        customName: String,
        callback: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.createAgent(userId, presetId, customName)
            result.onSuccess { agent ->
                _customAgents.value = _customAgents.value + agent
                callback(true, agent.id)
            }.onFailure { e ->
                callback(false, e.message ?: "Failed to create agent")
            }
        }
    }

    fun deleteAgent(agentId: String, userId: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteAgent(agentId, userId)
            result.onSuccess {
                _customAgents.value = _customAgents.value.filter { it.id != agentId }
                callback(true)
            }.onFailure {
                callback(false)
            }
        }
    }

    fun updateAgentModel(agentId: String, userId: String, model: String) {
        viewModelScope.launch {
            val result = repository.updateAgent(agentId, userId, model = model)
            result.onSuccess {
                loadUserAgents(userId)
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to update model"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}