package org.ole.planet.myplanet.di

import android.content.Context
import dagger.hilt.android.EntryPointAccessors

object DiUtils {
    fun appEntryPoint(context: Context): AppEntryPoint {
        return EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoint::class.java)
    }
}
