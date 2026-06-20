package lavender.client.android.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.ai.AiV2ChatUseCase
import lavender.client.android.data.ai.MarketplaceAgent

class MarketplaceViewModel(application: Application) : AndroidViewModel(application) {

    private val _agents = MutableStateFlow<List<MarketplaceAgent>>(emptyList())
    val agents: StateFlow<List<MarketplaceAgent>> = _agents.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var currentOffset = 0
    private var totalAgents = 0
    private var currentQuery = ""

    fun loadAgents(query: String = "") {
        currentQuery = query
        currentOffset = 0
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = AiV2ChatUseCase.listMarketplaceAgents(query = query, limit = 20, offset = 0)
                result.onSuccess { (agents, total) ->
                    _agents.value = agents
                    totalAgents = total
                    currentOffset = agents.size
                }
                result.onFailure { e ->
                    _error.value = e.message ?: "Failed to load marketplace"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load marketplace"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value || currentOffset >= totalAgents) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val result = AiV2ChatUseCase.listMarketplaceAgents(
                    query = currentQuery,
                    limit = 20,
                    offset = currentOffset
                )
                result.onSuccess { (agents, total) ->
                    _agents.value = _agents.value + agents
                    totalAgents = total
                    currentOffset += agents.size
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load more"
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
