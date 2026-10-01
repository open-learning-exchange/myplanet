package org.ole.planet.myplanet.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

class AppLogTest {
    private lateinit var original: LogSink

    @Before
    fun setUp() {
        original = AppLog.sink
    }

    @After
    fun tearDown() {
        AppLog.sink = original
    }

    @Test
    fun `each level forwards tag message and throwable to the sink`() {
        val sink = RecordingLogSink()
        AppLog.sink = sink
        val error = IllegalStateException("boom")

        AppLog.v("TagV", "verbose")
        AppLog.d("TagD", "debug", error)
        AppLog.i("TagI", "info")
        AppLog.w("TagW", "warn", error)
        AppLog.e("TagE", "error", error)

        assertEquals(
            listOf(
                RecordingLogSink.Entry(LogLevel.VERBOSE, "TagV", "verbose", null),
                RecordingLogSink.Entry(LogLevel.DEBUG, "TagD", "debug", error),
                RecordingLogSink.Entry(LogLevel.INFO, "TagI", "info", null),
                RecordingLogSink.Entry(LogLevel.WARN, "TagW", "warn", error),
                RecordingLogSink.Entry(LogLevel.ERROR, "TagE", "error", error),
            ),
            sink.entries
        )
    }

    @Test
    fun `lines without an installed sink are dropped`() {
        AppLog.sink = LogSink.NONE

        AppLog.e("Tag", "dropped", RuntimeException())
    }

    @Test
    fun `recording rule installs itself and restores the previous sink`() {
        val previous = LogSink { _, _, _, _ -> }
        AppLog.sink = previous
        val rule = RecordingLogSink()
        val description = Description.createTestDescription(AppLogTest::class.java, "rule")

        val body = object : Statement() {
            override fun evaluate() {
                assertSame(rule, AppLog.sink)
                AppLog.i("Tag", "recorded")
                assertEquals(1, rule.entries(LogLevel.INFO, "Tag").size)
            }
        }

        rule.apply(body, description).evaluate()

        assertSame(previous, AppLog.sink)
        assertTrue(rule.entries.isEmpty())
    }
}
