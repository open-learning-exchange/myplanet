package org.ole.planet.myplanet.di

import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.CourseRepositoryImpl
import org.ole.planet.myplanet.repository.CourseProgressRepository
import org.ole.planet.myplanet.repository.CourseProgressRepositoryImpl
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.LibraryRepositoryImpl
import org.ole.planet.myplanet.repository.RatingRepository
import org.ole.planet.myplanet.repository.RatingRepositoryImpl
import org.ole.planet.myplanet.repository.SearchRepository
import org.ole.planet.myplanet.repository.SearchRepositoryImpl
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
        @AppPreferences preferences: SharedPreferences
    ): UserRepository {
        return UserRepositoryImpl(databaseService, preferences)
    }

    @Provides
    @Singleton
    fun provideLibraryRepository(
        databaseService: DatabaseService
    ): LibraryRepository {
        return LibraryRepositoryImpl(databaseService)
    }

    @Provides
    @Singleton
    fun provideCourseRepository(
        databaseService: DatabaseService
    ): CourseRepository {
        return CourseRepositoryImpl(databaseService)
    }

    @Provides
    @Singleton
    fun provideCourseProgressRepository(
        databaseService: DatabaseService
    ): CourseProgressRepository {
        return CourseProgressRepositoryImpl(databaseService)
    }

    @Provides
    @Singleton
    fun provideRatingRepository(
        databaseService: DatabaseService
    ): RatingRepository {
        return RatingRepositoryImpl(databaseService)
    }

    @Provides
    @Singleton
    fun provideSearchRepository(
        databaseService: DatabaseService
    ): SearchRepository {
        return SearchRepositoryImpl(databaseService)
    }

    @Provides
    @Singleton
    fun provideSubmissionRepository(
        databaseService: DatabaseService
    ): SubmissionRepository {
        return SubmissionRepositoryImpl(databaseService)
    }
}
