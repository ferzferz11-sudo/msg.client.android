package lavender.client.android.data.ai

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import lavender.client.android.data.grpc.RealGrpcClient
import lavender.client.android.data.grpc.chatWithOwl

/**
 * OwlChatUseCase — orchestrates OWL AI chat with retry, timeout, backoff.
 * Transport delegated to OwlGrpc; this class owns streaming logic
 * and emits domain AiChatMessage to AiChatManager.
 */
object OwlChatUseCase {
    private const val TAG = "OwlChatUseCase"
    private const val MAX_RETRIES = 10
    private const val INITIAL_RETRY_DELAY_MS = 3000L
    private const val MAX_RETRY_DELAY_MS = 30000L
    private const val STREAM_TIMEOUT_MS = 120_000L

    /**
     * Chat with OWL AI — streaming with retry and timeout.
     * Collects tokens via callback and emits domain AiChatMessage to AiChatManager.
     */
    suspend fun chat(
        userId: String,
        sessionId: String,
        message: String,
        scope: CoroutineScope
    ) = withContext(Dispatchers.IO) {
        var retryDelay = INITIAL_RETRY_DELAY_MS
        var attempt = 0

        while (attempt < MAX_RETRIES && isActive) {
            try {
                val result = executeStream(
                    userId = userId,
                    sessionId = sessionId,
                    message = message,
                    scope = scope
                )
                if (result) return@withContext  // Success

                // Stream had error — retry
                attempt++
                if (attempt < MAX_RETRIES) {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "chat error", e)
                attempt++
                if (attempt < MAX_RETRIES) {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                }
            }
        }

        if (attempt >= MAX_RETRIES) {
            Log.w(TAG, "Max retries exceeded ($MAX_RETRIES)")
            emitError("Connection lost after $MAX_RETRIES attempts")
        }
    }

    /**
     * Execute a single streaming attempt. Returns true on success, false on error.
     */
    private suspend fun executeStream(
        userId: String,
        sessionId: String,
        message: String,
        scope: CoroutineScope
    ): Boolean = withContext(Dispatchers.IO) {
        val channel = RealGrpcClient.getChannel()
        if (channel == null || channel.isShutdown || channel.isTerminated) {
            Log.w(TAG, "Channel dead, will retry...")
            return@withContext false
        }

        AiChatManager.emitOwlTyping(true)
        val streamDone = CompletableDeferred<Boolean>()
        var hadError = false
        var timeoutJob: Job? = null

        try {
            chatWithOwl(
                userId = userId,
                sessionId = sessionId,
                message = message,
                scope = scope,
                onResponse = { text, finished, error ->
                    timeoutJob?.cancel()
                    val msg = AiChatMessage(
                        sessionId = sessionId,
                        role = "assistant",
                        content = text,
                        source = AiSource.OWL,
                        isStreaming = !finished
                    )
                    AiChatManager.emitOwlResponse(msg)

                    if (finished && error.isNotEmpty()) {
                        hadError = true
                    }

                    timeoutJob = scope.launch {
                        delay(STREAM_TIMEOUT_MS)
                        if (!finished) {
                            Log.w(TAG, "Stream timeout after ${STREAM_TIMEOUT_MS}ms")
                            hadError = true
                            emitError("Response timeout (${STREAM_TIMEOUT_MS / 1000}s). Please try again.")
                            streamDone.complete(true)
                        }
                    }
                }
            )

            timeoutJob = scope.launch {
                delay(STREAM_TIMEOUT_MS)
                Log.w(TAG, "Initial stream timeout after ${STREAM_TIMEOUT_MS}ms")
                hadError = true
                emitError("Response timeout (${STREAM_TIMEOUT_MS / 1000}s). Please try again.")
                streamDone.complete(true)
            }

            val streamHadError = streamDone.await()
            timeoutJob.cancel()
            return@withContext !streamHadError
        } catch (e: Exception) {
            AiChatManager.emitOwlTyping(false)
            Log.e(TAG, "executeStream error", e)
            emitError(e.message ?: "Unknown error")
            return@withContext false
        } finally {
            AiChatManager.emitOwlTyping(false)
        }
    }

    private fun emitError(errorText: String) {
        AiChatManager.emitOwlResponse(
            AiChatMessage(
                role = "assistant",
                content = "",
                source = AiSource.OWL,
                isStreaming = false
            )
        )
        AiChatManager.emitOwlTyping(false)
    }

    // ====== Settings ======

    suspend fun getSettings(chatId: String, userId: String): AiChatSettings? {
        return try {
            val proto = lavender.client.android.data.grpc.getOwlSettings(chatId, userId)
            val settings = proto.toDomain(chatId, userId)
            AiChatManager.updateOwlSettings(settings)
            settings
        } catch (e: Exception) {
            Log.e(TAG, "getSettings error", e)
            null
        }
    }

    suspend fun updateSettings(chatId: String, userId: String, apiKey: String, model: String): Boolean {
        return try {
            val result = lavender.client.android.data.grpc.updateOwlSettings(chatId, userId, apiKey, model)
            if (result.success) {
                getSettings(chatId, userId) // Refresh
            }
            result.success
        } catch (e: Exception) {
            Log.e(TAG, "updateSettings error", e)
            false
        }
    }

    suspend fun getHistory(chatId: String, userId: String): List<AiChatMessage> {
        return try {
            val proto = lavender.client.android.data.grpc.getOwlHistory(chatId, userId)
            proto.map { it.toDomain(chatId) }
        } catch (e: Exception) {
            Log.e(TAG, "getHistory error", e)
            emptyList()
        }
    }

    suspend fun createChat(userId: String, name: String = ""): String {
        return try {
            val result = lavender.client.android.data.grpc.createOwlChat(userId, name)
            result.chatId
        } catch (e: Exception) {
            Log.e(TAG, "createChat error", e)
            ""
        }
    }
}
