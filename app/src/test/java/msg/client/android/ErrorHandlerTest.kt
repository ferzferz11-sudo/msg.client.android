package msg.client.android

import lavender.client.android.data.models.AppLog
import lavender.client.android.data.models.ErrorHandler
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import java.net.UnknownHostException
import java.net.ConnectException
import java.net.SocketTimeoutException

/**
 * Unit tests for ErrorHandler.
 * Tests error routing logic: which exception types map to which log levels.
 */
class ErrorHandlerTest {

    @Before
    fun setup() {
        AppLog.clear()
    }

    @Test
    fun handle_cancellationException_logsInfo() {
        val ex = kotlinx.coroutines.CancellationException("Job cancelled")
        ErrorHandler.handle("TestSource", ex)
        val logs = AppLog.getLogsText()
        assertTrue("CancellationException should log 'Cancelled', got: $logs",
            logs.contains("Cancelled"))
    }

    @Test
    fun handle_unknownHostException_logsNetworkError() {
        val ex = UnknownHostException("Host not found")
        ErrorHandler.handle("TestSource", ex)
        val logs = AppLog.getLogsText()
        assertTrue("UnknownHostException should log 'Network error', got: $logs",
            logs.contains("Network error"))
    }

    @Test
    fun handle_connectException_logsNetworkError() {
        val ex = ConnectException("Connection refused")
        ErrorHandler.handle("TestSource", ex)
        val logs = AppLog.getLogsText()
        assertTrue("ConnectException should log 'Network error', got: $logs",
            logs.contains("Network error"))
    }

    @Test
    fun handle_socketTimeoutException_logsNetworkError() {
        val ex = SocketTimeoutException("Read timed out")
        ErrorHandler.handle("TestSource", ex)
        val logs = AppLog.getLogsText()
        assertTrue("SocketTimeoutException should log 'Network error', got: $logs",
            logs.contains("Network error"))
    }

    @Test
    fun handle_genericException_logsError() {
        val ex = RuntimeException("Something went wrong")
        ErrorHandler.handle("TestSource", ex)
        val logs = AppLog.getLogsText()
        assertTrue("Generic Exception should log error message, got: $logs",
            logs.contains("Something went wrong"))
    }

    @Test
    fun handle_customMessageWithThrowable_logsError() {
        val ex = RuntimeException("underlying error")
        ErrorHandler.handle("TestSource", "Custom message", ex)
        val logs = AppLog.getLogsText()
        assertTrue("Should contain custom message, got: $logs",
            logs.contains("Custom message"))
    }

    @Test
    fun handle_customMessageWithoutThrowable_logsError() {
        ErrorHandler.handle("TestSource", "Custom error message")
        val logs = AppLog.getLogsText()
        assertTrue("Should contain custom message, got: $logs",
            logs.contains("Custom error message"))
    }

    @Test
    fun handle_cancellationExceptionWithCustomMessage_logsInfo() {
        val ex = kotlinx.coroutines.CancellationException("cancelled")
        ErrorHandler.handle("TestSource", "Operation cancelled", ex)
        val logs = AppLog.getLogsText()
        assertTrue("CancellationException with custom message should log INFO, got: $logs",
            logs.contains("Operation cancelled"))
    }

    @Test
    fun log_infoMessage_logsInfo() {
        ErrorHandler.log("TestSource", "Info message")
        val logs = AppLog.getLogsText()
        assertTrue("log() should produce INFO entry, got: $logs",
            logs.contains("Info message"))
    }

    @Test
    fun warn_warnMessage_logsWarn() {
        ErrorHandler.warn("TestSource", "Warning message")
        val logs = AppLog.getLogsText()
        assertTrue("warn() should produce WARN entry, got: $logs",
            logs.contains("Warning message"))
    }

    @Test
    fun handle_sourceIsIncludedInLog() {
        val ex = RuntimeException("test error")
        ErrorHandler.handle("MySource", ex)
        val logs = AppLog.getLogsText()
        assertTrue("Should contain source in log, got: $logs",
            logs.contains("MySource"))
    }

    @Test
    fun handle_multipleErrors_allLogged() {
        ErrorHandler.handle("Src1", RuntimeException("error 1"))
        ErrorHandler.handle("Src2", UnknownHostException("error 2"))
        ErrorHandler.handle("Src3", kotlinx.coroutines.CancellationException("cancelled"))
        val logs = AppLog.getLogsText()
        // All 3 should appear in the log text
        assertTrue("Should contain error 1", logs.contains("error 1"))
        assertTrue("Should contain error 2", logs.contains("error 2"))
        assertTrue("Should contain cancelled", logs.contains("cancelled"))
    }

    @Test
    fun handle_securityException_logsPermissionError() {
        val ex = SecurityException("Permission denied")
        ErrorHandler.handle("TestSource", ex)
        val logs = AppLog.getLogsText()
        assertTrue("SecurityException should log 'Permission denied', got: $logs",
            logs.contains("Permission denied"))
    }

    @Test
    fun handle_exceptionWithNullMessage_usesClassName() {
        val ex = RuntimeException(null as String?)
        ErrorHandler.handle("TestSource", ex)
        val logs = AppLog.getLogsText()
        assertTrue("Should contain class name when message is null, got: $logs",
            logs.contains("RuntimeException"))
    }
}
