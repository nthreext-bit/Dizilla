package com.example

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

object NetworkLogger {

    private const val TAG_NET_LOG = "NetLog"
    private const val MAX_LOGS = 500

    data class LogEntry(
        val timestamp: String,
        val host: String,
        val url: String,
        val type: String,
        val isBlocked: Boolean,
        val ruleOrReason: String
    ) {
        override fun toString(): String {
            val status = if (isBlocked) "BLOCKED" else "ALLOWED"
            return "[$timestamp] [$status] [$type] host=$host reason=$ruleOrReason\n  url=$url"
        }
    }

    private val logQueue = ArrayDeque<LogEntry>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun logRequest(
        host: String,
        url: String,
        type: String,
        isBlocked: Boolean,
        reason: String
    ) {
        val timestamp = timeFormat.format(Date())
        val entry = LogEntry(timestamp, host, url, type, isBlocked, reason)

        if (logQueue.size >= MAX_LOGS) {
            logQueue.removeFirst()
        }
        logQueue.addLast(entry)

        if (BuildConfig.DEBUG) {
            val status = if (isBlocked) "BLOCKED" else "ALLOWED"
            Log.d(TAG_NET_LOG, "[$status] [$type] host=$host | url=$url | reason=$reason")
        }
    }

    @Synchronized
    fun getRecentLogs(): List<LogEntry> {
        return logQueue.toList()
    }

    @Synchronized
    fun getRecentRequests(limit: Int = 50): List<LogEntry> {
        return logQueue.toList().reversed().take(limit)
    }

    @Synchronized
    fun clearLogs() {
        logQueue.clear()
    }

    @Synchronized
    fun exportLogsAsText(): String {
        val sb = StringBuilder()
        sb.append("=== DIZILLA NETWORK REQUEST LOG (Last ${logQueue.size} Requests) ===\n")
        sb.append("Generated at: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n")
        sb.append("Format: [Timestamp] [STATUS] [Type] Host -> Reason -> URL\n\n")

        for (entry in logQueue) {
            sb.append(entry.toString()).append("\n\n")
        }

        return sb.toString()
    }
}
