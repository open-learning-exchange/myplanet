package org.ole.planet.myplanet.utils

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.services.SharedPrefManager

interface DeviceNameProvider {
    fun getCustomDeviceName(): String
}

@Singleton
class SharedPrefDeviceNameProvider @Inject constructor(
    private val sharedPrefManager: SharedPrefManager
) : DeviceNameProvider {
    override fun getCustomDeviceName(): String = sharedPrefManager.getCustomDeviceName()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceNameModule {
    @Binds
    @Singleton
    abstract fun bindDeviceNameProvider(impl: SharedPrefDeviceNameProvider): DeviceNameProvider
}
