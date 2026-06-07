package lavender.client.android.ui.owl

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.models.OwlMessage
import lavender.client.android.data.grpc.hermesTyping
import lavender.client.android.data.grpc.hermesResponses

/**
 * OwlChatViewModel — ViewModel для чата с OWL AI.
 *
 * Управляет:
 * - Сообщениями OWL
 * - Отправкой запросов к OWL через gRPC ChatWithOWL
 * - Typing indicator
 */
class OwlChatViewModel : ViewModel() {

    private val _owlMessages = MutableStateFlow<List<OwlMessage>>(emptyList())
    val owlMessages: StateFlow<List<OwlMessage>> = _owlMessages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Collect typing state from HermesGrpc (shared flow)
        viewModelScope.launch {
            hermesTyping.collect { typing ->
                _isTyping.value = typing
            }
        }

        // Collect responses from HermesGrpc (shared flow)
        viewModelScope.launch {
            hermesResponses.collect { response ->
                if (response.finished) {
                    _isTyping.value = false
                    if (response.error.isNotEmpty()) {
                        addMessage(
                            id = "owl-error-${System.currentTimeMillis()}",
                            content = "Ошибка: ${response.error}",
                            senderId = "owl",
                            senderName = "🦉 OWL",
                            isCurrentUser = false
                        )
                    }
                }
            }
        }
    }

    /**
     * Отправить сообщение к OWL AI.
     */
    fun sendToOwl(message: String, userId: String, chatId: String) {
        _isLoading.value = true
        _isTyping.value = true

        // For now, we use the existing ChatWithOWL gRPC method
        // This is a simplified version — in production we'd have a dedicated OWL streaming call
        viewModelScope.launch {
            try {
                // Use the existing OWL session manager approach
                // The actual gRPC call is handled by the activity for simplicity
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e("OwlChatViewModel", "sendToOwl error", e)
                _error.value = "Ошибка отправки: ${e.message}"
                _isLoading.value = false
                _isTyping.value = false
            }
        }
    }

    /**
     * Добавить сообщение в список.
     */
    fun addMessage(
        id: String,
        content: String,
        senderId: String,
        senderName: String,
        isCurrentUser: Boolean,
        senderEmoji: String = "🦉"
    ) {
        val message = OwlMessage(
            id = id,
            content = content,
            senderId = senderId,
            senderName = senderName,
            senderEmoji = senderEmoji,
            timestamp = System.currentTimeMillis(),
            isCurrentUser = isCurrentUser
        )
        _owlMessages.value = _owlMessages.value + message
    }

    /**
     * Очистить ошибку.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Очистить историю.
     */
    fun clearHistory() {
        _owlMessages.value = emptyList()
    }
}
