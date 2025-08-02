package org.ole.planet.myplanet.di

import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.CourseRepositoryImpl
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.LibraryRepositoryImpl
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.SubmissionRepositoryImpl
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.UserRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideUserRepository(
        databaseService: DatabaseService,
        @AppPreferences preferences: SharedPreferences,
        apiInterface: ApiInterface
    ): UserRepository {
        return UserRepositoryImpl(databaseService, preferences, apiInterface)
    }

    @Provides
    @Singleton
    fun provideLibraryRepository(
        databaseService: DatabaseService,
        apiInterface: ApiInterface
    ): LibraryRepository {
        return LibraryRepositoryImpl(databaseService, apiInterface)
    }

    @Provides
    @Singleton
    fun provideCourseRepository(
        databaseService: DatabaseService,
        apiInterface: ApiInterface
    ): CourseRepository {
        return CourseRepositoryImpl(databaseService, apiInterface)
    }

    @Provides
    @Singleton
    fun provideSubmissionRepository(
        databaseService: DatabaseService
    ): SubmissionRepository {
        return SubmissionRepositoryImpl(databaseService)
    }
}
