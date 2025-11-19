package org.ole.planet.myplanet.utilities

import com.google.gson.Gson

object GsonUtils {
    val gson: Gson by lazy {
        Gson()
    }
}
