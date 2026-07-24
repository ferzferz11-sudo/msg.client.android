package lavender.client.android.ui.ai
import android.util.Log

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
import org.json.JSONObject

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
                var agent = AiV2ChatUseCase.getAgent(agentId)

                if (agent != null) {
                    val hasUserKey = try {
                        val config = JSONObject(agent.providerConfig)
                        config.optString("api_key", "").isNotEmpty() ||
                            config.optString("apiKey", "").isNotEmpty()
                    } catch (_: Exception) { false }

                    if (!hasUserKey) {
                        val chatSession = AiV2ChatUseCase.listAIChats().firstOrNull { it.agentId == agentId }
                        if (chatSession != null) {
                            try {
                                val settings = AiV2ChatUseCase.getChatSettings(chatSession.id)
                                if (settings.userApiKey.isNotEmpty()) {
                                    val config = JSONObject().apply {
                                        put("api_key", settings.userApiKey)
                                    }.toString()
                                    agent = agent.copy(providerConfig = config)
                                }
                            } catch (e: Exception) { Log.w("TAG", "Caught: " + e.message) }
                        }
                    }
                }

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
        isPublic: Boolean = false,
        temperature: Float = 0.7f,
        maxTokens: Int = 4096
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
                    isPublic = isPublic,
                    temperature = temperature,
                    maxTokens = maxTokens
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
        isPublic: Boolean = false,
        temperature: Float = 0f,
        maxTokens: Int = 0
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
                    isPublic = isPublic,
                    temperature = temperature,
                    maxTokens = maxTokens
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
