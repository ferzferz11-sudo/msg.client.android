package lavender.client.android.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.ai.AiV2ChatUseCase
import lavender.client.android.data.ai.UsageStat

class UsageStatsViewModel(application: Application) : AndroidViewModel(application) {

    private val _stats = MutableStateFlow<List<UsageStat>>(emptyList())
    val stats: StateFlow<List<UsageStat>> = _stats.asStateFlow()

    private val _totalTokens = MutableStateFlow(0)
    val totalTokens: StateFlow<Int> = _totalTokens.asStateFlow()

    private val _totalRequests = MutableStateFlow(0)
    val totalRequests: StateFlow<Int> = _totalRequests.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = AiV2ChatUseCase.getUsageStats()
                result.onSuccess { (stats, totals) ->
                    _stats.value = stats
                    _totalTokens.value = totals.first
                    _totalRequests.value = totals.second
                }
                result.onFailure { e ->
                    _error.value = e.message ?: "Failed to load stats"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load stats"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
