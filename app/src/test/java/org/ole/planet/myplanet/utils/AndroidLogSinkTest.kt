package org.ole.planet.myplanet.utils

import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.MainApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class AndroidLogSinkTest {
    private data class Line(val type: Int, val tag: String, val msg: String?, val throwable: Throwable?)

    private lateinit var original: LogSink

    @Before
    fun setUp() {
        original = AppLog.sink
        AppLog.sink = AndroidLogSink
    }

    @After
    fun tearDown() {
        AppLog.sink = original
    }

    private fun capture(block: () -> Unit): List<Line> {
        ShadowLog.reset()
        block()
        return ShadowLog.getLogs().map { Line(it.type, it.tag, it.msg, it.throwable) }
    }

    @Test
    fun `lines match direct Log calls at every level with and without a throwable`() {
        val error = IllegalStateException("boom")

        val direct = capture {
            Log.v("Tag", "verbose")
            Log.d("Tag", "debug")
            Log.i("Tag", "info")
            Log.w("Tag", "warn")
            Log.e("Tag", "error")
            Log.v("Tag", "verbose", error)
            Log.d("Tag", "debug", error)
            Log.i("Tag", "info", error)
            Log.w("Tag", "warn", error)
            Log.e("Tag", "error", error)
        }
        val viaAppLog = capture {
            AppLog.v("Tag", "verbose")
            AppLog.d("Tag", "debug")
            AppLog.i("Tag", "info")
            AppLog.w("Tag", "warn")
            AppLog.e("Tag", "error")
            AppLog.v("Tag", "verbose", error)
            AppLog.d("Tag", "debug", error)
            AppLog.i("Tag", "info", error)
            AppLog.w("Tag", "warn", error)
            AppLog.e("Tag", "error", error)
        }

        val priorities = listOf(Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR)
        assertEquals(priorities + priorities, direct.map { it.type })
        assertEquals(direct, viaAppLog)
    }

    @Test
    fun `constructing the application installs the android sink before onCreate`() {
        AppLog.sink = LogSink.NONE

        MainApplication()

        assertSame(AndroidLogSink, AppLog.sink)
    }
}
