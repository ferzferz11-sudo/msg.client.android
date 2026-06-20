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

enum class SortOption(val label: String) {
    RATING("Rating"),
    INSTALLS("Installs"),
    NAME("Name")
}

class MarketplaceViewModel(application: Application) : AndroidViewModel(application) {

    private val _agents = MutableStateFlow<List<MarketplaceAgent>>(emptyList())
    val agents: StateFlow<List<MarketplaceAgent>> = _agents.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.RATING)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _filterProvider = MutableStateFlow<String?>(null)
    val filterProvider: StateFlow<String?> = _filterProvider.asStateFlow()

    private val _filterToolsEnabled = MutableStateFlow<Boolean?>(null)
    val filterToolsEnabled: StateFlow<Boolean?> = _filterToolsEnabled.asStateFlow()

    private var currentOffset = 0
    private var totalAgents = 0
    private var currentQuery = ""
    private var allAgents = mutableListOf<MarketplaceAgent>()

    fun loadAgents(query: String = "") {
        currentQuery = query
        currentOffset = 0
        allAgents.clear()
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = AiV2ChatUseCase.listMarketplaceAgents(query = query, limit = 20, offset = 0)
                result.onSuccess { (agents, total) ->
                    allAgents.addAll(agents)
                    totalAgents = total
                    currentOffset = agents.size
                    applyFiltersAndSort()
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
                    allAgents.addAll(agents)
                    totalAgents = total
                    currentOffset += agents.size
                    applyFiltersAndSort()
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load more"
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        applyFiltersAndSort()
    }

    fun setFilterProvider(provider: String?) {
        _filterProvider.value = provider
        applyFiltersAndSort()
    }

    fun setFilterToolsEnabled(enabled: Boolean?) {
        _filterToolsEnabled.value = enabled
        applyFiltersAndSort()
    }

    fun clearError() {
        _error.value = null
    }

    private fun applyFiltersAndSort() {
        var filtered = allAgents.toList()

        _filterProvider.value?.let { provider ->
            if (provider.isNotEmpty()) {
                filtered = filtered.filter { it.providerType.value == provider }
            }
        }

        _filterToolsEnabled.value?.let { toolsEnabled ->
            filtered = filtered.filter { it.toolsEnabled == toolsEnabled }
        }

        val sorted = when (_sortOption.value) {
            SortOption.RATING -> filtered.sortedByDescending { it.avgRating }
            SortOption.INSTALLS -> filtered.sortedByDescending { it.installCount }
            SortOption.NAME -> filtered.sortedBy { it.name }
        }

        _agents.value = sorted
    }
}
