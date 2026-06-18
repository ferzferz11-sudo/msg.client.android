package lavender.client.android.data.grpc.testutil

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

suspend fun <T> StateFlow<T>.assertValue(expected: T, message: String? = null) {
    val actual = this.value
    if (actual != expected) {
        throw AssertionError(
            "${message ?: "StateFlow value mismatch"}: expected=$expected, actual=$actual"
        )
    }
}

suspend fun <T> StateFlow<List<T>>.assertListContains(expected: T, message: String? = null) {
    val actual = this.value
    if (!actual.contains(expected)) {
        throw AssertionError(
            "${message ?: "StateList does not contain expected item"}: expected=$expected, actual=$actual"
        )
    }
}

suspend fun <T> StateFlow<List<T>>.assertEmpty(message: String? = null) {
    val actual = this.value
    if (actual.isNotEmpty()) {
        throw AssertionError(
            "${message ?: "StateList should be empty"}: actual=$actual"
        )
    }
}

suspend fun <T> StateFlow<List<T>>.assertNotEmpty(message: String? = null) {
    val actual = this.value
    if (actual.isEmpty()) {
        throw AssertionError(
            "${message ?: "StateList should not be empty"}"
        )
    }
}

suspend fun <T> StateFlow<List<T>>.assertSize(expected: Int, message: String? = null) {
    val actual = this.value.size
    if (actual != expected) {
        throw AssertionError(
            "${message ?: "StateList size mismatch"}: expected=$expected, actual=$actual"
        )
    }
}

suspend fun StateFlow<String?>.assertNull(message: String? = null) {
    val actual = this.value
    if (actual != null) {
        throw AssertionError(
            "${message ?: "StateFlow should be null"}: actual=$actual"
        )
    }
}

suspend fun StateFlow<String?>.assertContains(substring: String, message: String? = null) {
    val actual = this.value
    if (actual == null || !actual.contains(substring)) {
        throw AssertionError(
            "${message ?: "StateFlow should contain '$substring'"}: actual=$actual"
        )
    }
}
