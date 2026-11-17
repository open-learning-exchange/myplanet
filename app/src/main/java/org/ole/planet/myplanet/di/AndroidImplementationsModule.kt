package org.ole.planet.myplanet.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.services.UserDataUploadScheduler
import org.ole.planet.myplanet.services.WorkManagerUserDataUploadScheduler
import org.ole.planet.myplanet.utils.DeviceNameProvider
import org.ole.planet.myplanet.utils.SharedPrefDeviceNameProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class AndroidImplementationsModule {

    @Binds
    @Singleton
    abstract fun bindUserDataUploadScheduler(
        impl: WorkManagerUserDataUploadScheduler
    ): UserDataUploadScheduler

    @Binds
    @Singleton
    abstract fun bindDeviceNameProvider(
        impl: SharedPrefDeviceNameProvider
    ): DeviceNameProvider
}
