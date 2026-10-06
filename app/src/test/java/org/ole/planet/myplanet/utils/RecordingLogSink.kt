package org.ole.planet.myplanet.utils

import java.util.concurrent.CopyOnWriteArrayList
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * [LogSink] that records every [AppLog] line for assertions. As a JUnit rule
 * (`@get:Rule val logs = RecordingLogSink()`) it installs itself as [AppLog.sink] before each
 * test and puts the previous sink back afterwards, so no test leaks its sink into the next.
 */
class RecordingLogSink : TestWatcher(), LogSink {
    data class Entry(val level: LogLevel, val tag: String, val message: String, val throwable: Throwable?)

    private val recorded = CopyOnWriteArrayList<Entry>()
    private var previous: LogSink? = null

    val entries: List<Entry> get() = recorded.toList()

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        recorded.add(Entry(level, tag, message, throwable))
    }

    fun entries(level: LogLevel, tag: String): List<Entry> = recorded.filter { it.level == level && it.tag == tag }

    override fun starting(description: Description) {
        previous = AppLog.sink
        AppLog.sink = this
    }

    override fun finished(description: Description) {
        previous?.let { AppLog.sink = it }
        previous = null
        recorded.clear()
    }
}
