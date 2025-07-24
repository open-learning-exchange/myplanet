package org.ole.planet.myplanet.utilities

import android.content.Context
import android.content.ContextWrapper
import androidx.appcompat.app.AppCompatActivity

fun Context.findAppCompatActivity(): AppCompatActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is AppCompatActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
