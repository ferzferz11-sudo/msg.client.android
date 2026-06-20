package lavender.client.android.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.ai.AgentReview
import lavender.client.android.data.ai.AgentStats
import lavender.client.android.data.ai.AiV2ChatUseCase

class AgentDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val _stats = MutableStateFlow<AgentStats?>(null)
    val stats: StateFlow<AgentStats?> = _stats.asStateFlow()

    private val _reviews = MutableStateFlow<List<AgentReview>>(emptyList())
    val reviews: StateFlow<List<AgentReview>> = _reviews.asStateFlow()

    private val _avgRating = MutableStateFlow(0f)
    val avgRating: StateFlow<Float> = _avgRating.asStateFlow()

    private val _reviewCount = MutableStateFlow(0)
    val reviewCount: StateFlow<Int> = _reviewCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _shareCode = MutableStateFlow<String?>(null)
    val shareCode: StateFlow<String?> = _shareCode.asStateFlow()

    private val _installResult = MutableStateFlow<Boolean?>(null)
    val installResult: StateFlow<Boolean?> = _installResult.asStateFlow()

    private val _rateResult = MutableStateFlow<Pair<Float, Int>?>(null)
    val rateResult: StateFlow<Pair<Float, Int>?> = _rateResult.asStateFlow()

    fun loadAgentDetails(agentId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val statsResult = AiV2ChatUseCase.getAgentStats(agentId)
                statsResult.onSuccess { s ->
                    _stats.value = s
                    _avgRating.value = s.avgRating
                    _reviewCount.value = s.reviewCount
                }

                val reviewsResult = AiV2ChatUseCase.getAgentReviews(agentId, limit = 20)
                reviewsResult.onSuccess { (reviews, ratingInfo) ->
                    _reviews.value = reviews
                    _avgRating.value = ratingInfo.first
                    _reviewCount.value = ratingInfo.second
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load details"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun rateAgent(agentId: String, rating: Int, review: String = "") {
        viewModelScope.launch {
            val result = AiV2ChatUseCase.rateAgent(agentId, rating, review)
            result.onSuccess { (_, ratingInfo) ->
                _rateResult.value = ratingInfo
                _avgRating.value = ratingInfo.first
                _reviewCount.value = ratingInfo.second
            }
            result.onFailure { e ->
                _error.value = e.message ?: "Failed to rate agent"
            }
        }
    }

    fun shareAgent(agentId: String) {
        viewModelScope.launch {
            val result = AiV2ChatUseCase.shareAgent(agentId)
            result.onSuccess { code ->
                _shareCode.value = code
            }
            result.onFailure { e ->
                _error.value = e.message ?: "Failed to generate share code"
            }
        }
    }

    fun installAgent(shareCode: String, newName: String = "") {
        viewModelScope.launch {
            val result = AiV2ChatUseCase.installAgent(shareCode, newName)
            result.onSuccess {
                _installResult.value = true
            }
            result.onFailure { e ->
                _error.value = e.message ?: "Failed to install agent"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearShareCode() {
        _shareCode.value = null
    }

    fun clearInstallResult() {
        _installResult.value = null
    }

    fun clearRateResult() {
        _rateResult.value = null
    }
}
