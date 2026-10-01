package org.ole.planet.myplanet.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.repository.UserLookupRepository
import org.ole.planet.myplanet.repository.UserRepository

@Module
@InstallIn(SingletonComponent::class)
object UserLookupModule {
    @Provides
    fun provideUserLookupRepository(userRepository: UserRepository): UserLookupRepository = userRepository
}
