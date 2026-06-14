package lavender.client.android.data.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Модель для хранения лога ошибки/события
 */
data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "ERROR", // ERROR, WARN, INFO
    val source: String = "", // откуда вызвано: класс:метод:строка
    val message: String = "",
    val stackTrace: String = ""
) {
    fun formattedTime(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
    }

    fun toDisplayString(): String {
        return "[${formattedTime()}] [$level] $source: $message"
    }

    fun toFullString(): String {
        return buildString {
            appendLine(toDisplayString())
            if (stackTrace.isNotEmpty()) {
                appendLine("Stack trace:")
                append(stackTrace)
            }
        }
    }
}

/**
 * Глобальный логгер для сохранения ошибок и событий
 * Доступен из любого места приложения
 */
object AppLog {
    private val logs = mutableListOf<LogEntry>()
    private const val MAX_LOGS = 500

    fun error(source: String, message: String, throwable: Throwable? = null) {
        add(LogEntry(
            level = "ERROR",
            source = source,
            message = message,
            stackTrace = throwable?.stackTraceToString() ?: ""
        ))
    }

    fun warn(source: String, message: String) {
        add(LogEntry(level = "WARN", source = source, message = message))
    }

    fun info(source: String, message: String) {
        add(LogEntry(level = "INFO", source = source, message = message))
    }

    private fun add(entry: LogEntry) {
        synchronized(logs) {
            logs.add(entry)
            // Удаляем старые если превышен лимит
            while (logs.size > MAX_LOGS) {
                logs.removeAt(0)
            }
        }
        // Также пишем в Android Log
        android.util.Log.d("AppLog", entry.toDisplayString())
    }

    fun getAll(): List<LogEntry> = synchronized(logs) { logs.toList() }

    fun getErrors(): List<LogEntry> = synchronized(logs) { logs.filter { it.level == "ERROR" } }

    fun clear() = synchronized(logs) { logs.clear() }

    fun getLogsText(): String = synchronized(logs) {
        if (logs.isEmpty()) return@synchronized context.getString(R.string.logs_empty)
        buildString {
            logs.forEach { entry ->
                appendLine(entry.toDisplayString())
                if (entry.stackTrace.isNotEmpty()) {
                    appendLine("--- Stack trace ---")
                    appendLine(entry.stackTrace)
                    appendLine("---")
                }
                appendLine()
            }
        }
    }
}
