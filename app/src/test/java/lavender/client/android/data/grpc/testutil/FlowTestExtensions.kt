package lavender.client.android.data.grpc.testutil

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import app.cash.turbine.test
import kotlin.time.Duration.Companion.seconds

/**
 * Extension-функции для тестирования StateFlow и SharedFlow.
 */

/**
 * Следит за StateFlow и собирает все эмиссии за timeout.
 * Возвращает список значений.
 */
suspend fun <T> StateFlow<T>.testCollect(
    timeout: kotlin.time.Duration = 2.seconds,
    block: suspend () -> Unit
): List<T> {
    val results = mutableListOf<T>()
    val job = kotlinx.coroutines.coroutineScope {
        val collectJob = kotlinx.coroutines.launch {
            this@testCollect.collect { results.add(it) }
        }
        try {
            withTimeout(timeout) {
                block()
            }
        } finally {
            collectJob.cancel()
        }
    }
    return results
}

/**
 * Проверяет, что StateFlow содержит ожидаемое значение.
 */
suspend fun <T> StateFlow<T>.assertValue(expected: T, message: String? = null) {
    val actual = this.value
    if (actual != expected) {
        throw AssertionError(
            "${message ?: "StateFlow value mismatch"}: expected=$expected, actual=$actual"
        )
    }
}

/**
 * Проверяет, что StateFlow содержит список ожидаемых значений.
 */
suspend fun <T> StateFlow<List<T>>.assertListContains(expected: T, message: String? = null) {
    val actual = this.value
    if (!actual.contains(expected)) {
        throw AssertionError(
            "${message ?: "StateList does not contain expected item"}: expected=$expected, actual=$actual"
        )
    }
}

/**
 * Проверяет, что StateFlow пуст.
 */
suspend fun <T> StateFlow<List<T>>.assertEmpty(message: String? = null) {
    val actual = this.value
    if (actual.isNotEmpty()) {
        throw AssertionError(
            "${message ?: "StateList should be empty"}: actual=$actual"
        )
    }
}

/**
 * Проверяет, что StateFlow не пуст.
 */
suspend fun <T> StateFlow<List<T>>.assertNotEmpty(message: String? = null) {
    val actual = this.value
    if (actual.isEmpty()) {
        throw AssertionError(
            "${message ?: "StateList should not be empty"}"
        )
    }
}

/**
 * Проверяет, что StateFlow имеет определённый размер.
 */
suspend fun <T> StateFlow<List<T>>.assertSize(expected: Int, message: String? = null) {
    val actual = this.value.size
    if (actual != expected) {
        throw AssertionError(
            "${message ?: "StateList size mismatch"}: expected=$expected, actual=$actual"
        )
    }
}

/**
 * Проверяет, что StateFlow<String?> содержит null.
 */
suspend fun StateFlow<String?>.assertNull(message: String? = null) {
    val actual = this.value
    if (actual != null) {
        throw AssertionError(
            "${message ?: "StateFlow should be null"}: actual=$actual"
        )
    }
}

/**
 * Проверяет, что StateFlow<String?> не null и содержит подстроку.
 */
suspend fun StateFlow<String?>.assertContains(substring: String, message: String? = null) {
    val actual = this.value
    if (actual == null || !actual.contains(substring)) {
        throw AssertionError(
            "${message ?: "StateFlow should contain '$substring'"}: actual=$actual"
        )
    }
}
