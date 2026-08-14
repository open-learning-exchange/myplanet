package org.ole.planet.myplanet.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.ChatSyncRepository
import org.ole.planet.myplanet.repository.CommunityRepository
import org.ole.planet.myplanet.repository.CommunitySyncRepository
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.FeedbackSyncRepository
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TagsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.UserSyncRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.services.sync.TransactionSyncManager
import org.ole.planet.myplanet.utils.DispatcherProvider

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(dispatcherProvider: DispatcherProvider): CoroutineScope {
        return CoroutineScope(SupervisorJob() + dispatcherProvider.io)
    }

    @Provides
    @Singleton
    fun provideUploadToShelfService(
        @ApplicationContext context: Context,
        @AppPreferences preferences: SharedPreferences,
        sharedPrefManager: SharedPrefManager,
        userRepository: UserRepository,
        userSyncRepository: UserSyncRepository,
        healthRepository: HealthRepository,
        @ApplicationScope appScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        apiInterface: ApiInterface
    ): UploadToShelfService {
        return UploadToShelfService(context, preferences, sharedPrefManager, userRepository, userSyncRepository, healthRepository, appScope, dispatcherProvider, apiInterface)
    }

    @Provides
    @Singleton
    fun provideTransactionSyncManager(
        apiInterface: ApiInterface,
        @ApplicationContext context: Context,
        voicesRepository: VoicesRepository,
        chatRepository: ChatSyncRepository,
        feedbackRepository: FeedbackSyncRepository,
        sharedPrefManager: SharedPrefManager,
        userRepository: UserRepository,
        userSyncRepository: UserSyncRepository,
        activitiesRepository: ActivitiesRepository,
        teamsRepository: Lazy<TeamsRepository>,
        teamsSyncRepository: Lazy<TeamsSyncRepository>,
        notificationsRepository: NotificationsRepository,
        tagsRepository: TagsRepository,
        ratingsRepository: RatingsRepository,
        submissionsRepository: SubmissionsRepository,
        coursesRepository: CoursesRepository,
        communityRepository: CommunitySyncRepository,
        healthRepository: HealthRepository,
        progressRepository: ProgressRepository,
        surveysRepository: SurveysRepository,
        @ApplicationScope scope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        userSessionManager: org.ole.planet.myplanet.services.UserSessionManager
    ): TransactionSyncManager {
        return TransactionSyncManager(apiInterface, context, voicesRepository, chatRepository, feedbackRepository, sharedPrefManager, userRepository, userSyncRepository, activitiesRepository, teamsRepository, teamsSyncRepository, notificationsRepository, tagsRepository, ratingsRepository, submissionsRepository, coursesRepository, communityRepository, healthRepository, progressRepository, surveysRepository, scope, dispatcherProvider, userSessionManager)
    }
}
