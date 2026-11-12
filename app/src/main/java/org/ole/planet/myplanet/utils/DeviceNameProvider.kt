package org.ole.planet.myplanet.utils

import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.services.SharedPrefManager

interface DeviceNameProvider {
    fun getDeviceName(): String
    fun getCustomDeviceName(): String
}

@Singleton
class SharedPrefDeviceNameProvider @Inject constructor(
    private val sharedPrefManager: SharedPrefManager
) : DeviceNameProvider {
    override fun getDeviceName(): String = NetworkUtils.getDeviceName()
    override fun getCustomDeviceName(): String = sharedPrefManager.getCustomDeviceName()
}
