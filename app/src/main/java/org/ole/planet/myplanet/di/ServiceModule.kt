package org.ole.planet.myplanet.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.CommunityRepository
import org.ole.planet.myplanet.repository.CoursesRepository
import org.ole.planet.myplanet.repository.EventsRepository
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.NotificationsRepository
import org.ole.planet.myplanet.repository.PersonalsRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TagsRepository
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.UserSyncRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.services.sync.LoginSyncManager
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.services.sync.TransactionSyncManager
import org.ole.planet.myplanet.services.upload.AchievementUploader
import org.ole.planet.myplanet.services.upload.PhotoUploader
import org.ole.planet.myplanet.services.upload.UploadConfigs
import org.ole.planet.myplanet.services.upload.UploadCoordinator
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TimeProvider

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
    fun provideSyncManager(
        @ApplicationContext context: Context,
        sharedPrefManager: SharedPrefManager,
        apiInterface: ApiInterface,
        transactionSyncManager: TransactionSyncManager,
        resourcesRepository: ResourcesRepository,
        loginSyncManager: LoginSyncManager,
        @ApplicationScope scope: CoroutineScope,
        activitiesRepository: ActivitiesRepository,
        dispatcherProvider: DispatcherProvider,
        timeProvider: TimeProvider,
        teamsRepository: TeamsRepository,
        teamsSyncRepository: TeamsSyncRepository,
        coursesRepository: CoursesRepository,
        eventsRepository: EventsRepository
    ): SyncManager {
        return SyncManager(context, sharedPrefManager, apiInterface, transactionSyncManager, resourcesRepository, loginSyncManager, scope, activitiesRepository, dispatcherProvider, timeProvider, teamsRepository, teamsSyncRepository, coursesRepository, eventsRepository)
    }

    @Provides
    @Singleton
    fun provideUploadManager(
        @ApplicationContext context: Context,
        submissionsRepository: SubmissionsRepository,
        sharedPrefManager: SharedPrefManager,
        gson: Gson,
        uploadCoordinator: UploadCoordinator,
        personalsRepository: PersonalsRepository,
        userRepository: UserRepository,
        chatRepository: ChatRepository,
        voicesRepository: VoicesRepository,
        uploadConfigs: UploadConfigs,
        resourcesRepository: ResourcesRepository,
        teamsRepository: Lazy<TeamsRepository>,
        teamsSyncRepository: Lazy<TeamsSyncRepository>,
        activitiesRepository: ActivitiesRepository,
        apiInterface: ApiInterface,
        @ApplicationScope scope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
        photoUploader: PhotoUploader,
        achievementUploader: AchievementUploader,
        timeProvider: TimeProvider
    ): UploadManager {
        return UploadManager(context, submissionsRepository, sharedPrefManager, gson, uploadCoordinator, personalsRepository, userRepository, chatRepository, voicesRepository, uploadConfigs, resourcesRepository, teamsRepository, teamsSyncRepository, apiInterface, activitiesRepository, dispatcherProvider, scope, photoUploader, achievementUploader, timeProvider)
    }

    @Provides
    @Singleton
    fun provideRealtimeSyncManager(): RealtimeSyncManager {
        return RealtimeSyncManager.getInstance()
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
        databaseService: DatabaseService,
        @LegacyRealmDispatcher legacyRealmDispatcher: CoroutineDispatcher,
        @ApplicationContext context: Context,
        voicesRepository: VoicesRepository,
        chatRepository: ChatRepository,
        feedbackRepository: FeedbackRepository,
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
        communityRepository: CommunityRepository,
        healthRepository: HealthRepository,
        progressRepository: ProgressRepository,
        surveysRepository: SurveysRepository,
        @ApplicationScope scope: CoroutineScope,
        dispatcherProvider: DispatcherProvider
    ): TransactionSyncManager {
        return TransactionSyncManager(apiInterface, databaseService, legacyRealmDispatcher, context, voicesRepository, chatRepository, feedbackRepository, sharedPrefManager, userRepository, userSyncRepository, activitiesRepository, teamsRepository, teamsSyncRepository, notificationsRepository, tagsRepository, ratingsRepository, submissionsRepository, coursesRepository, communityRepository, healthRepository, progressRepository, surveysRepository, scope, dispatcherProvider)
    }
}
