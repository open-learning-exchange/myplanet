package org.ole.planet.myplanet.utils

import javax.inject.Inject
import org.ole.planet.myplanet.BuildConfig

class AppVersionProvider @Inject constructor() {
    val versionName: String get() = BuildConfig.VERSION_NAME
}
