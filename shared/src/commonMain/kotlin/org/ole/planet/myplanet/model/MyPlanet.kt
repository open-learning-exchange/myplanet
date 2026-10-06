package org.ole.planet.myplanet.model

import kotlinx.serialization.Serializable
import org.ole.planet.myplanet.utils.JavaSerializable

/** The server's `/versions` document. */
@Serializable
class MyPlanet : JavaSerializable {
    var planetVersion: String? = null
    var minapkcode = 0
    var latestapkcode = 0
    var apkpath: String? = null
    var appname: String? = null
    var localapkpath: String? = null
    override fun toString(): String {
        return appname ?: ""
    }

    companion object
}
