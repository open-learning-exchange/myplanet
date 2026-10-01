package org.ole.planet.myplanet.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.repository.VoicesEditActions
import org.ole.planet.myplanet.repository.VoicesRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class VoicesEditActionsModule {
    @Binds
    abstract fun bindVoicesEditActions(repository: VoicesRepository): VoicesEditActions
}
