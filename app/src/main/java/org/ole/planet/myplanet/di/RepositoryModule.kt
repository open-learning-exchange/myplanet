package org.ole.planet.myplanet.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.ChatRepositoryImpl
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.CourseRepositoryImpl
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.FeedbackRepositoryImpl
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.LibraryRepositoryImpl
import org.ole.planet.myplanet.repository.MyPersonalRepository
import org.ole.planet.myplanet.repository.MyPersonalRepositoryImpl
import org.ole.planet.myplanet.repository.NewsRepository
import org.ole.planet.myplanet.repository.NewsRepositoryImpl
import org.ole.planet.myplanet.repository.NotificationRepository
import org.ole.planet.myplanet.repository.NotificationRepositoryImpl
import org.ole.planet.myplanet.repository.RatingRepository
import org.ole.planet.myplanet.repository.RatingRepositoryImpl
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.SubmissionRepositoryImpl
import org.ole.planet.myplanet.repository.TagRepository
import org.ole.planet.myplanet.repository.TagRepositoryImpl
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.repository.TeamRepositoryImpl
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.UserRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCourseRepository(impl: CourseRepositoryImpl): CourseRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindFeedbackRepository(impl: FeedbackRepositoryImpl): FeedbackRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindMyPersonalRepository(impl: MyPersonalRepositoryImpl): MyPersonalRepository

    @Binds
    @Singleton
    abstract fun bindNewsRepository(impl: NewsRepositoryImpl): NewsRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindRatingRepository(impl: RatingRepositoryImpl): RatingRepository

    @Binds
    @Singleton
    abstract fun bindSubmissionRepository(impl: SubmissionRepositoryImpl): SubmissionRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds
    @Singleton
    abstract fun bindTeamRepository(impl: TeamRepositoryImpl): TeamRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
}
