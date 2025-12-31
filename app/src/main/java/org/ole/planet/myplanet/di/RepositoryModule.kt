package org.ole.planet.myplanet.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.repository.ActivityRepository
import org.ole.planet.myplanet.repository.ActivityRepositoryImpl
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.ChatRepositoryImpl
import org.ole.planet.myplanet.repository.ConfigurationRepository
import org.ole.planet.myplanet.repository.ConfigurationRepositoryImpl
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.CoursesRepositoryImpl
import org.ole.planet.myplanet.repository.EventsRepository
import org.ole.planet.myplanet.repository.EventsRepositoryImpl
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.FeedbackRepositoryImpl
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.repository.LifeRepositoryImpl
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.NotificationsRepositoryImpl
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.repository.PersonalsRepositoryImpl
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.ProgressRepositoryImpl
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.RatingsRepositoryImpl
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.ResourcesRepositoryImpl
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepositoryImpl
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.SurveysRepositoryImpl
import org.ole.planet.myplanet.repository.TagsRepository
import org.ole.planet.myplanet.repository.TagsRepositoryImpl
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.TeamsRepositoryImpl
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.UserRepositoryImpl
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.repository.VoicesRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindActivityRepository(impl: ActivityRepositoryImpl): ActivityRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindConfigurationRepository(impl: ConfigurationRepositoryImpl): ConfigurationRepository

    @Binds
    @Singleton
    abstract fun bindCoursesRepository(impl: CoursesRepositoryImpl): CoursesRepository

    @Binds
    @Singleton
    abstract fun bindEventsRepository(impl: EventsRepositoryImpl): EventsRepository

    @Binds
    @Singleton
    abstract fun bindFeedbackRepository(impl: FeedbackRepositoryImpl): FeedbackRepository

    @Binds
    @Singleton
    abstract fun bindLifeRepository(impl: LifeRepositoryImpl): LifeRepository

    @Binds
    @Singleton
    abstract fun bindNotificationsRepository(impl: NotificationsRepositoryImpl): NotificationsRepository

    @Binds
    @Singleton
    abstract fun bindPersonalRepository(impl: PersonalsRepositoryImpl): PersonalsRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    @Singleton
    abstract fun bindRatingsRepository(impl: RatingsRepositoryImpl): RatingsRepository

    @Binds
    @Singleton
    abstract fun bindResourcesRepository(impl: ResourcesRepositoryImpl): ResourcesRepository

    @Binds
    @Singleton
    abstract fun bindSubmissionsRepository(impl: SubmissionsRepositoryImpl): SubmissionsRepository

    @Binds
    @Singleton
    abstract fun bindSurveysRepository(impl: SurveysRepositoryImpl): SurveysRepository

    @Binds
    @Singleton
    abstract fun bindTagsRepository(impl: TagsRepositoryImpl): TagsRepository

    @Binds
    @Singleton
    abstract fun bindTeamsRepository(impl: TeamsRepositoryImpl): TeamsRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindVoicesRepository(impl: VoicesRepositoryImpl): VoicesRepository
}
