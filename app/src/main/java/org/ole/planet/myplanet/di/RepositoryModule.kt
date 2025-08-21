package org.ole.planet.myplanet.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.repository.CourseProgressRepository
import org.ole.planet.myplanet.repository.CourseProgressRepositoryImpl
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.CourseRepositoryImpl
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
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCourseProgressRepository(impl: CourseProgressRepositoryImpl): CourseProgressRepository

    @Binds
    @Singleton
    abstract fun bindCourseRepository(impl: CourseRepositoryImpl): CourseRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindRatingRepository(impl: RatingRepositoryImpl): RatingRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindSubmissionRepository(impl: SubmissionRepositoryImpl): SubmissionRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
}
