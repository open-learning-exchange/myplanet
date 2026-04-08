package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import org.ole.planet.myplanet.repository.UserRepository

@Deprecated("Use SharedInternalEntryPoint instead")
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceEntryPoint {
    @ApplicationScope
    fun applicationScope(): CoroutineScope

    fun userRepository(): UserRepository
}
