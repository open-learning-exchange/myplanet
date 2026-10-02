package org.ole.planet.myplanet.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

object Utilities {
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun isValidEmail(target: CharSequence): Boolean {
        return target.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(target).matches()
    }

    private fun getActivityFromContext(context: Context?): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }

    private fun isAppInForeground(): Boolean {
        return ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    fun toast(context: Context?, message: CharSequence?, duration: Int = Toast.LENGTH_LONG) {
        context ?: return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showToastIfValid(context, message, duration)
        } else {
            mainHandler.post {
                showToastIfValid(context, message, duration)
            }
        }
    }

    private fun showToastIfValid(context: Context, message: CharSequence?, duration: Int) {
        if (!isAppInForeground()) {
            return
        }

        val visualContext = getActivityFromContext(context)

        if (visualContext != null && !visualContext.isFinishing && !visualContext.isDestroyed) {
            try {
                Toast.makeText(visualContext, message, duration).show()
            } catch (e: IllegalAccessException) {
                e.printStackTrace()
            }
        }
    }

    fun getUserName(settings: SharedPreferences): String {
        return settings.getString("name", "") ?: ""
    }

    fun getMimeType(url: String?): String? {
        val extension = FileUtils.getFileExtension(url)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    fun warmUp() {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension("txt")
    }
}
