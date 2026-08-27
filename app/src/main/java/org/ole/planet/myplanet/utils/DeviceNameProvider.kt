package org.ole.planet.myplanet.utils

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.services.SharedPrefManager

/**
 * Injectable source of the device's custom name so domain repositories can serialize it into
 * uploaded documents without depending on [android.content.Context] or [NetworkUtils]. Mirrors
 * [TimeProvider]: the Android-aware lookup lives in the implementation, callers only see a string.
 */
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
