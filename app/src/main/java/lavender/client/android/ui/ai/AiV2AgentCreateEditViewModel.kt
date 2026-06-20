package lavender.client.android.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import lavender.client.android.data.ai.AiV2Agent
import lavender.client.android.data.ai.AiV2ChatUseCase
import lavender.client.android.data.proto.CreateAIAgentRequestProto
import lavender.client.android.data.proto.UpdateAIAgentRequestProto

/**
 * AiV2AgentCreateEditViewModel — ViewModel for creating/editing AI v2 agents.
 */
class AiV2AgentCreateEditViewModel(application: Application) : AndroidViewModel(application) {

    private val _agent = MutableLiveData<AiV2Agent?>()
    val agent: LiveData<AiV2Agent?> = _agent

    private val _saveResult = MutableLiveData<Boolean>()
    val saveResult: LiveData<Boolean> = _saveResult

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun loadAgent(agentId: String) {
        viewModelScope.launch {
            try {
                val agent = AiV2ChatUseCase.getAgent(agentId)
                _agent.value = agent
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load agent"
            }
        }
    }

    fun createAgent(
        name: String,
        description: String,
        providerType: String,
        model: String,
        systemPrompt: String,
        providerConfig: String,
        toolsEnabled: Boolean,
        ragEnabled: Boolean,
        isPublic: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val request = CreateAIAgentRequestProto(
                    name = name,
                    description = description,
                    providerType = providerType,
                    providerConfig = providerConfig,
                    systemPrompt = systemPrompt,
                    model = model,
                    toolsEnabled = toolsEnabled,
                    ragEnabled = ragEnabled,
                    isPublic = isPublic
                )
                val result = AiV2ChatUseCase.createAgent(request)
                result.fold(
                    onSuccess = { _saveResult.value = true },
                    onFailure = { _error.value = it.message }
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create agent"
            }
        }
    }

    fun updateAgent(
        agentId: String,
        name: String,
        description: String,
        model: String,
        systemPrompt: String,
        providerConfig: String,
        toolsEnabled: Boolean,
        ragEnabled: Boolean,
        isPublic: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val request = UpdateAIAgentRequestProto(
                    agentId = agentId,
                    name = name,
                    description = description,
                    providerConfig = providerConfig,
                    systemPrompt = systemPrompt,
                    model = model,
                    toolsEnabled = toolsEnabled,
                    ragEnabled = ragEnabled,
                    isPublic = isPublic
                )
                val result = AiV2ChatUseCase.updateAgent(request)
                result.fold(
                    onSuccess = { _saveResult.value = true },
                    onFailure = { _error.value = it.message }
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to update agent"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
