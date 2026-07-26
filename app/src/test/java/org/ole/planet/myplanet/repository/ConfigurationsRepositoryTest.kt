package org.ole.planet.myplanet.repository

import org.junit.Test
import org.ole.planet.myplanet.model.MyPlanet

class ConfigurationsRepositoryTest {

    @Test
    fun `CheckVersionCallback default onCheckingVersion does not throw exception`() {
        val callback = object : ConfigurationsRepository.CheckVersionCallback {
            override fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean) {}
            override fun onError(msg: String, blockSync: Boolean) {}
        }

        // This should run without throwing any exceptions since the default implementation is empty.
        callback.onCheckingVersion()
    }
}