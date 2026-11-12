package org.ole.planet.myplanet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.utils.SystemTimeProvider
import org.ole.planet.myplanet.utils.TimeProvider

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    @Singleton
    fun provideTimeProvider(): TimeProvider = SystemTimeProvider()
}
