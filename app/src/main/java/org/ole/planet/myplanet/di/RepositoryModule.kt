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
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.HealthRepositoryImpl
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.LibraryRepositoryImpl
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.repository.LifeRepositoryImpl
import org.ole.planet.myplanet.repository.MeetupRepository
import org.ole.planet.myplanet.repository.MeetupRepositoryImpl
import org.ole.planet.myplanet.repository.MyPersonalRepository
import org.ole.planet.myplanet.repository.MyPersonalRepositoryImpl
import org.ole.planet.myplanet.repository.NewsRepository
import org.ole.planet.myplanet.repository.NewsRepositoryImpl
import org.ole.planet.myplanet.repository.NotificationRepository
import org.ole.planet.myplanet.repository.NotificationRepositoryImpl
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.ProgressRepositoryImpl
import org.ole.planet.myplanet.repository.RatingRepository
import org.ole.planet.myplanet.repository.RatingRepositoryImpl
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.SubmissionRepositoryImpl
import org.ole.planet.myplanet.repository.SurveyRepository
import org.ole.planet.myplanet.repository.SurveyRepositoryImpl
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
    abstract fun bindHealthRepository(
        healthRepositoryImpl: HealthRepositoryImpl
    ): HealthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl
    ): UserRepository

    @Binds
    @Singleton
    abstract fun bindTeamRepository(
        teamRepositoryImpl: TeamRepositoryImpl
    ): TeamRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(
        tagRepositoryImpl: TagRepositoryImpl
    ): TagRepository

    @Binds
    @Singleton
    abstract fun bindSurveyRepository(
        surveyRepositoryImpl: SurveyRepositoryImpl
    ): SurveyRepository

    @Binds
    @Singleton
    abstract fun bindSubmissionRepository(
        submissionRepositoryImpl: SubmissionRepositoryImpl
    ): SubmissionRepository

    @Binds
    @Singleton
    abstract fun bindRatingRepository(
        ratingRepositoryImpl: RatingRepositoryImpl
    ): RatingRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(
        progressRepositoryImpl: ProgressRepositoryImpl
    ): ProgressRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(
        notificationRepositoryImpl: NotificationRepositoryImpl
    ): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindNewsRepository(
        newsRepositoryImpl: NewsRepositoryImpl
    ): NewsRepository

    @Binds
    @Singleton
    abstract fun bindMyPersonalRepository(
        myPersonalRepositoryImpl: MyPersonalRepositoryImpl
    ): MyPersonalRepository

    @Binds
    @Singleton
    abstract fun bindMeetupRepository(
        meetupRepositoryImpl: MeetupRepositoryImpl
    ): MeetupRepository

    @Binds
    @Singleton
    abstract fun bindLifeRepository(
        lifeRepositoryImpl: LifeRepositoryImpl
    ): LifeRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(
        libraryRepositoryImpl: LibraryRepositoryImpl
    ): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindFeedbackRepository(
        feedbackRepositoryImpl: FeedbackRepositoryImpl
    ): FeedbackRepository

    @Binds
    @Singleton
    abstract fun bindCourseRepository(
        courseRepositoryImpl: CourseRepositoryImpl
    ): CourseRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository
}
