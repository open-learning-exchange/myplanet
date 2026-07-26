package org.ole.planet.myplanet.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.model.MyPlanet

class ConfigurationsRepositoryTest {

    @Test
    fun `test CheckVersionCallback onUpdateAvailable`() {
        var onUpdateAvailableCalled = false
        var receivedInfo: MyPlanet? = null
        var receivedCancelable = false

        val callback = object : ConfigurationsRepository.CheckVersionCallback {
            override fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean) {
                onUpdateAvailableCalled = true
                receivedInfo = info
                receivedCancelable = cancelable
            }

            override fun onError(msg: String, blockSync: Boolean) {}
        }

        val testInfo = MyPlanet().apply {
            minapkcode = 10
            latestapkcode = 12
        }

        callback.onUpdateAvailable(testInfo, true)

        assertTrue(onUpdateAvailableCalled)
        assertEquals(testInfo, receivedInfo)
        assertTrue(receivedCancelable)
    }

    @Test
    fun `test CheckVersionCallback onCheckingVersion default implementation`() {
        val callback = object : ConfigurationsRepository.CheckVersionCallback {
            override fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean) {}
            override fun onError(msg: String, blockSync: Boolean) {}
        }

        callback.onCheckingVersion()
    }

    @Test
    fun `test CheckVersionCallback onError`() {
        var onErrorCalled = false
        var receivedMsg = ""
        var receivedBlockSync = false

        val callback = object : ConfigurationsRepository.CheckVersionCallback {
            override fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean) {}

            override fun onError(msg: String, blockSync: Boolean) {
                onErrorCalled = true
                receivedMsg = msg
                receivedBlockSync = blockSync
            }
        }

        callback.onError("Test Error", false)

        assertTrue(onErrorCalled)
        assertEquals("Test Error", receivedMsg)
        assertFalse(receivedBlockSync)
    }
}
