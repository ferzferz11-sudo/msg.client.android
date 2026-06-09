package lavender.client.android.ui.owl

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import lavender.client.android.data.models.OwlMessage
import lavender.client.android.data.grpc.owlTyping
import lavender.client.android.data.grpc.owlResponses
import lavender.client.android.data.grpc.chatWithOwl

/**
 * OwlChatViewModel — ViewModel для чата с OWL AI.
 *
 * Управляет:
 * - Сообщениями OWL
 * - Отправкой запросов к OWL через gRPC ChatWithOWL (отдельный поток от Hermes)
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

    // Accumulate streaming response text
    private var currentOwlResponse = StringBuilder()

    init {
        // Collect OWL typing state (separate from Hermes orchestrator)
        viewModelScope.launch {
            owlTyping.collect { typing ->
                _isTyping.value = typing
            }
        }

        // Collect OWL responses (separate from Hermes orchestrator)
        viewModelScope.launch {
            owlResponses.collect { response ->
                if (response.text.isNotEmpty() && !response.finished) {
                    // Accumulate streaming chunks
                    currentOwlResponse.append(response.text)
                }
                if (response.finished) {
                    _isTyping.value = false
                    _isLoading.value = false
                    if (response.error.isNotEmpty()) {
                        addMessage(
                            id = "owl-error-${System.currentTimeMillis()}",
                            content = "Ошибка: ${response.error}",
                            senderId = "owl",
                            senderName = "🦉 OWL",
                            isCurrentUser = false
                        )
                    } else if (currentOwlResponse.isNotEmpty()) {
                        addMessage(
                            id = "owl-${System.currentTimeMillis()}",
                            content = currentOwlResponse.toString(),
                            senderId = "owl",
                            senderName = "🦉 OWL",
                            isCurrentUser = false
                        )
                        currentOwlResponse = StringBuilder()
                    }
                }
            }
        }
    }

    /**
     * Отправить сообщение к OWL AI через ChatWithOWL gRPC.
     */
    fun sendToOwl(message: String, userId: String, chatId: String) {
        _isLoading.value = true
        _isTyping.value = true
        currentOwlResponse = StringBuilder()

        viewModelScope.launch {
            try {
                chatWithOwl(
                    userId = userId,
                    sessionId = chatId,
                    message = message,
                    scope = viewModelScope,
                    onResponse = { text, finished, error ->
                        // Responses are collected via owlResponses SharedFlow
                        // This callback is for logging/debugging
                        if (error.isNotEmpty()) {
                            Log.e("OwlChatViewModel", "OWL error: $error")
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("OwlChatViewModel", "sendToOwl error", e)
                _error.value = "Ошибка отправки: ${e.message}"
                _isLoading.value = false
                _isTyping.value = false
            }
        }
    }

    /**
     * Add a user message to the list (called from Activity before sending to OWL).
     */
    fun addUserMessage(
        id: String,
        content: String,
        senderId: String,
        senderName: String,
        isCurrentUser: Boolean
    ) {
        val message = OwlMessage(
            id = id,
            content = content,
            senderId = senderId,
            senderName = senderName,
            senderEmoji = "",
            timestamp = System.currentTimeMillis(),
            isCurrentUser = isCurrentUser
        )
        _owlMessages.value = _owlMessages.value + message
    }

    /**
     * Add a bot/system message to the list (called from Activity after bot command response).
     */
    fun addBotMessage(
        id: String,
        content: String,
        senderId: String,
        senderName: String
    ) {
        val message = OwlMessage(
            id = id,
            content = content,
            senderId = senderId,
            senderName = senderName,
            senderEmoji = "🤖",
            timestamp = System.currentTimeMillis(),
            isCurrentUser = false
        )
        _owlMessages.value = _owlMessages.value + message
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
