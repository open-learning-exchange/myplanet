package org.ole.planet.myplanet.utils

/** Priority of a line written through [AppLog], in the platform log's order. */
enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

/**
 * Where [AppLog] lines end up, so shared code logs without the platform's logging API. The
 * Android implementation is [AndroidLogSink].
 */
fun interface LogSink {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?)

    companion object {
        /** Drops every line. */
        val NONE = LogSink { _, _, _, _ -> }
    }
}

/**
 * Platform-free logging entry point. Each function takes `(tag, msg, tr)` in the same order as
 * the platform log, so a call site moves over by swapping the receiver only.
 *
 * [sink] starts as [LogSink.NONE] and the application installs its platform sink before any
 * other app code runs; until then (and in plain JVM tests that never install one) lines are
 * dropped rather than printed.
 */
object AppLog {
    @Volatile
    var sink: LogSink = LogSink.NONE

    fun v(tag: String, msg: String, tr: Throwable? = null) = sink.log(LogLevel.VERBOSE, tag, msg, tr)

    fun d(tag: String, msg: String, tr: Throwable? = null) = sink.log(LogLevel.DEBUG, tag, msg, tr)

    fun i(tag: String, msg: String, tr: Throwable? = null) = sink.log(LogLevel.INFO, tag, msg, tr)

    fun w(tag: String, msg: String, tr: Throwable? = null) = sink.log(LogLevel.WARN, tag, msg, tr)

    fun e(tag: String, msg: String, tr: Throwable? = null) = sink.log(LogLevel.ERROR, tag, msg, tr)
}
