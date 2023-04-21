package org.ole.planet.myplanet.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.data.repository.UsersRepositoryImpl
import org.ole.planet.myplanet.domain.UsersRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindUsersRepository(usersRepositoryImpl: UsersRepositoryImpl): UsersRepository
}