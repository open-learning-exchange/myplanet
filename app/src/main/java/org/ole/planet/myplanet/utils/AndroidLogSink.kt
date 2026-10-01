package org.ole.planet.myplanet.utils

import android.util.Log

/**
 * [LogSink] that writes to logcat. A line without a throwable goes through the two-argument
 * [Log] overload and one with a throwable through the three-argument one, so the output is
 * byte-for-byte what a direct [Log] call at the same level would produce.
 */
object AndroidLogSink : LogSink {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) {
            when (level) {
                LogLevel.VERBOSE -> Log.v(tag, message)
                LogLevel.DEBUG -> Log.d(tag, message)
                LogLevel.INFO -> Log.i(tag, message)
                LogLevel.WARN -> Log.w(tag, message)
                LogLevel.ERROR -> Log.e(tag, message)
            }
        } else {
            when (level) {
                LogLevel.VERBOSE -> Log.v(tag, message, throwable)
                LogLevel.DEBUG -> Log.d(tag, message, throwable)
                LogLevel.INFO -> Log.i(tag, message, throwable)
                LogLevel.WARN -> Log.w(tag, message, throwable)
                LogLevel.ERROR -> Log.e(tag, message, throwable)
            }
        }
    }
}
