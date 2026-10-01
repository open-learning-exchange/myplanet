package org.ole.planet.myplanet.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAppInfo @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AppInfo {
    override val packageName: String get() = context.packageName

    override fun versionCode(): Int = VersionUtils.getVersionCode(context)

    override fun versionName(): String? = VersionUtils.getVersionName(context)

    override fun androidId(): String? = VersionUtils.getAndroidId(context)
}
