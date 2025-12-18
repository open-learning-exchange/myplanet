package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.model.MyPlanet

interface ConfigurationRepository {
    fun checkHealth(listener: SuccessListener)
    fun checkVersion(callback: CheckVersionCallback, settings: SharedPreferences)
    fun checkServerAvailability(callback: PlanetAvailableListener?)
    suspend fun validateResourceChecksum(path: String?): Boolean

    interface CheckVersionCallback {
        fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean)
        fun onCheckingVersion()
        fun onError(msg: String, blockSync: Boolean)
    }

    interface PlanetAvailableListener {
        fun isAvailable()
        fun notAvailable()
    }
}
