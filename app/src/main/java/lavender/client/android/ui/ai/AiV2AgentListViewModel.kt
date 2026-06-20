package lavender.client.android.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.ai.AiV2Agent
import lavender.client.android.data.ai.AiV2ChatUseCase

/**
 * AiV2AgentListViewModel — ViewModel for AI v2 agent list.
 */
class AiV2AgentListViewModel(application: Application) : AndroidViewModel(application) {

    private val _agents = MutableStateFlow<List<AiV2Agent>>(emptyList())
    val agents: StateFlow<List<AiV2Agent>> = _agents.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentTab = 0 // 0=presets, 1=my, 2=public

    fun loadAgents(tab: Int = 0) {
        currentTab = tab
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val agents = when (tab) {
                    0 -> AiV2ChatUseCase.listAgents(includePublic = true).filter { it.isPreset }
                    1 -> AiV2ChatUseCase.listAgents(includePublic = false).filter { !it.isPreset }
                    2 -> AiV2ChatUseCase.listAgents(includePublic = true).filter { it.isPublic && !it.isPreset }
                    else -> emptyList()
                }
                _agents.value = agents
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load agents"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteAgent(agentId: String) {
        viewModelScope.launch {
            try {
                AiV2ChatUseCase.deleteAgent(agentId)
                loadAgents(currentTab)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete agent"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
