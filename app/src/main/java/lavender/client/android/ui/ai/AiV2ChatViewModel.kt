package lavender.client.android.ui.ai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.ai.AiV2ChatManager
import lavender.client.android.data.ai.AiV2ChatMessage
import lavender.client.android.data.ai.AiV2ChatUseCase
import lavender.client.android.data.ai.AiV2StreamState

/**
 * AiV2ChatViewModel — ViewModel for AI v2 chat.
 * Collects from AiV2ChatManager and delegates to AiV2ChatUseCase.
 */
class AiV2ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<AiV2ChatMessage>>(emptyList())
    val messages: StateFlow<List<AiV2ChatMessage>> = _messages.asStateFlow()

    private val _streamState = MutableStateFlow(AiV2StreamState())
    val streamState: StateFlow<AiV2StreamState> = _streamState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _rateLimitEvent = MutableStateFlow(false)
    val rateLimitEvent: StateFlow<Boolean> = _rateLimitEvent.asStateFlow()

    private val _sessionId = MutableStateFlow("")

    init {
        observeStreaming()
    }

    private fun observeStreaming() {
        viewModelScope.launch {
            AiV2ChatManager.aiResponses.collect { message ->
                if (message.sessionId == _sessionId.value || _sessionId.value.isEmpty()) {
                    updateStreamingMessage(message)
                }
            }
        }

        viewModelScope.launch {
            AiV2ChatManager.streamState.collect { state ->
                _streamState.value = state
            }
        }
    }

    private fun updateStreamingMessage(message: AiV2ChatMessage) {
        if (message.error.isNotEmpty()) {
            _streamState.value = _streamState.value.copy(isTyping = false)
            if (message.error.contains("rate limit", ignoreCase = true)) {
                _rateLimitEvent.value = true
            }
            val errorText = formatAiError(message.error)
            val errorMsg = AiV2ChatMessage(
                role = "assistant",
                content = errorText,
                agentId = message.agentId,
                agentName = message.agentName,
                timestamp = System.currentTimeMillis()
            )
            _messages.value = _messages.value + errorMsg
            return
        }

        val current = _messages.value.toMutableList()
        val lastIndex = current.indexOfLast {
            it.role == "assistant" && it.isStreaming
        }

        if (lastIndex >= 0) {
            // Update existing streaming message
            val existing = current[lastIndex]
            current[lastIndex] = existing.copy(
                content = existing.content + message.content,
                agentId = message.agentId.ifEmpty { existing.agentId },
                agentName = message.agentName.ifEmpty { existing.agentName },
                isStreaming = message.isStreaming,
                toolCalls = message.toolCalls.ifEmpty { existing.toolCalls },
                hasRagContext = message.hasRagContext || existing.hasRagContext,
                modelUsed = message.modelUsed.ifEmpty { existing.modelUsed },
                tokenCount = message.tokenCount
            )
        } else if (message.content.isNotEmpty() || message.toolCalls.isNotEmpty()) {
            // Add new streaming message
            current.add(message.copy(
                content = message.content,
                isStreaming = message.isStreaming
            ))
        }

        _messages.value = current
    }

    fun addMessage(message: AiV2ChatMessage) {
        _messages.value = _messages.value + message
    }

    fun sendMessage(
        userId: String,
        sessionId: String,
        message: String,
        agentId: String = "",
        imageUri: String? = null,
        images: List<ByteArray> = emptyList()
    ) {
        if (_sessionId.value.isEmpty() && sessionId.isEmpty()) {
            // New session — will be set from server response
        } else if (_sessionId.value.isEmpty()) {
            _sessionId.value = sessionId
        }

        viewModelScope.launch {
            try {
                AiV2ChatUseCase.chat(
                    userId = userId,
                    sessionId = _sessionId.value,
                    message = message,
                    agentId = agentId,
                    images = images,
                    imageUri = if (images.isEmpty()) imageUri else null,
                    scope = viewModelScope
                )
            } catch (e: Exception) {
                val errorMsg = AiV2ChatMessage(
                    role = "assistant",
                    content = formatAiError(e.message ?: "Unknown error"),
                    timestamp = System.currentTimeMillis()
                )
                _messages.value = _messages.value + errorMsg
            }
        }
    }

    fun loadHistory(sessionId: String) {
        _sessionId.value = sessionId
        viewModelScope.launch {
            try {
                val history = AiV2ChatUseCase.getChatHistory(sessionId)
                if (history.isNotEmpty()) {
                    _messages.value = history
                }
            } catch (e: Exception) {
                val errorMsg = AiV2ChatMessage(
                    role = "assistant",
                    content = formatAiError(e.message ?: "Failed to load history"),
                    timestamp = System.currentTimeMillis()
                )
                _messages.value = _messages.value + errorMsg
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun formatAiError(error: String): String {
        if (error.contains("PARTNER_API_BUDGET_EXHAUSTED", ignoreCase = true) ||
            error.contains("402", ignoreCase = true) && error.contains("budget", ignoreCase = true)) {
            return "\u26A0\uFE0F Бюджет API для генерации изображений исчерпан. Пополните баланс в панели управления API и попробуйте снова."
        }
        return "\u26A0\uFE0F $error"
    }

    fun clearRateLimitEvent() {
        _rateLimitEvent.value = false
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }
}
