package org.ole.planet.myplanet.repository

import android.content.SharedPreferences
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.model.MyPlanet

interface ConfigurationsRepository {
    fun checkHealth(listener: OnSuccessListener)
    fun checkVersion(callback: CheckVersionCallback, settings: SharedPreferences)
    fun checkServerAvailability(callback: PlanetAvailableListener?)

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
