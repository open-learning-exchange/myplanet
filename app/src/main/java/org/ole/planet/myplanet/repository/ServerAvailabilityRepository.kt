package org.ole.planet.myplanet.repository

interface ServerAvailabilityRepository {
    fun isPlanetAvailable(callback: PlanetAvailableListener?)

    interface PlanetAvailableListener {
        fun isAvailable()
        fun notAvailable()
    }
}

