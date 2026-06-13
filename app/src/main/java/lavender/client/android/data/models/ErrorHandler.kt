package lavender.client.android.data.models

import android.util.Log

/**
 * Единый обработчик ошибок для автоматического добавления в AppLog.
 *
 * Использование:
 *   ErrorHandler.handle("Source", throwable)                    // ERROR level
 *   ErrorHandler.handle("Source", "message")                   // ERROR level with custom message
 *   ErrorHandler.handle("Source", "message", throwable)        // ERROR level with both
 *   ErrorHandler.log("Source", "info message")                 // INFO level
 */
object ErrorHandler {
    private const val TAG = "ErrorHandler"

    fun handle(source: String, throwable: Throwable) {
        val message = throwable.message ?: throwable.javaClass.simpleName
        when (throwable) {
            is kotlinx.coroutines.CancellationException -> {
                AppLog.info(source, "Cancelled: $message")
            }
            is java.net.UnknownHostException,
            is java.net.ConnectException,
            is java.net.SocketTimeoutException -> {
                AppLog.error(source, "Network error: $message", throwable)
                Log.e(TAG, "[$source] Network error", throwable)
            }
            is io.grpc.StatusRuntimeException -> {
                AppLog.error(source, "gRPC error [${throwable.status.code}]: ${throwable.status.description}", throwable)
                Log.e(TAG, "[$source] gRPC error", throwable)
            }
            is SecurityException -> {
                AppLog.error(source, "Permission denied: $message", throwable)
                Log.e(TAG, "[$source] Permission error", throwable)
            }
            else -> {
                AppLog.error(source, message, throwable)
                Log.e(TAG, "[$source] Error", throwable)
            }
        }
    }

    fun handle(source: String, customMessage: String, throwable: Throwable? = null) {
        if (throwable != null) {
            when (throwable) {
                is kotlinx.coroutines.CancellationException -> {
                    AppLog.info(source, customMessage)
                }
                is java.net.UnknownHostException,
                is java.net.ConnectException,
                is java.net.SocketTimeoutException -> {
                    AppLog.error(source, "$customMessage: ${throwable.message}", throwable)
                    Log.e(TAG, "[$source] Network error", throwable)
                }
                is io.grpc.StatusRuntimeException -> {
                    AppLog.error(source, "$customMessage [${throwable.status.code}]: ${throwable.status.description}", throwable)
                    Log.e(TAG, "[$source] gRPC error", throwable)
                }
                else -> {
                    AppLog.error(source, "$customMessage: ${throwable.message}", throwable)
                    Log.e(TAG, "[$source] Error", throwable)
                }
            }
        } else {
            AppLog.error(source, customMessage)
            Log.e(TAG, "[$source] Error (no throwable)")
        }
    }

    fun log(source: String, message: String) {
        AppLog.info(source, message)
        Log.d(TAG, "[$source] $message")
    }

    fun warn(source: String, message: String) {
        AppLog.warn(source, message)
        Log.w(TAG, "[$source] $message")
    }
}
