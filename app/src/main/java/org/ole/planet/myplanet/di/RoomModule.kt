package org.ole.planet.myplanet.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.ole.planet.myplanet.data.room.AppDatabase
import org.ole.planet.myplanet.data.room.dao.ApkLogDao
import org.ole.planet.myplanet.data.room.dao.UserChallengeActionsDao
import org.ole.planet.myplanet.data.room.dao.CertificationDao
import org.ole.planet.myplanet.data.room.dao.ChatDao
import org.ole.planet.myplanet.data.room.dao.CommunityDao
import org.ole.planet.myplanet.data.room.dao.CourseActivityDao
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.DictionaryDao
import org.ole.planet.myplanet.data.room.dao.FeedbackDao
import org.ole.planet.myplanet.data.room.dao.MeetupDao
import org.ole.planet.myplanet.data.room.dao.MyLifeDao
import org.ole.planet.myplanet.data.room.dao.NotificationDao
import org.ole.planet.myplanet.data.room.dao.NewsLogDao
import org.ole.planet.myplanet.data.room.dao.OfflineActivityDao
import org.ole.planet.myplanet.data.room.dao.PersonalDao
import org.ole.planet.myplanet.data.room.dao.RatingDao
import org.ole.planet.myplanet.data.room.dao.RetryDao
import org.ole.planet.myplanet.data.room.dao.ResourceActivityDao
import org.ole.planet.myplanet.data.room.dao.RemovedLogDao
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.data.room.dao.SubmitPhotosDao
import org.ole.planet.myplanet.data.room.dao.TagDao
import org.ole.planet.myplanet.data.room.dao.TeamNotificationDao
import org.ole.planet.myplanet.data.room.dao.TeamLogDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao

@Module
@InstallIn(SingletonComponent::class)
object RoomModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "myplanet_room.db")
            // Drop-and-resync migration strategy: on any schema change the local Room store is
            // rebuilt and repopulated from the server, so no hand-written migrations are needed.
            .fallbackToDestructiveMigration(true)
            .build()
    }

    @Provides
    fun provideDictionaryDao(database: AppDatabase): DictionaryDao {
        return database.dictionaryDao()
    }

    @Provides
    fun provideMyLifeDao(database: AppDatabase): MyLifeDao {
        return database.myLifeDao()
    }

    @Provides
    fun providePersonalDao(database: AppDatabase): PersonalDao {
        return database.personalDao()
    }

    @Provides
    fun provideRetryDao(database: AppDatabase): RetryDao {
        return database.retryDao()
    }

    @Provides
    fun provideCommunityDao(database: AppDatabase): CommunityDao {
        return database.communityDao()
    }

    @Provides
    fun provideApkLogDao(database: AppDatabase): ApkLogDao {
        return database.apkLogDao()
    }


    @Provides
    fun provideUserChallengeActionsDao(database: AppDatabase): UserChallengeActionsDao {
        return database.userChallengeActionsDao()
    }

    @Provides
    fun provideTeamNotificationDao(database: AppDatabase): TeamNotificationDao {
        return database.teamNotificationDao()
    }

    @Provides
    fun provideCertificationDao(database: AppDatabase): CertificationDao {
        return database.certificationDao()
    }

    @Provides
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    fun provideFeedbackDao(database: AppDatabase): FeedbackDao {
        return database.feedbackDao()
    }

    @Provides
    fun provideRatingDao(database: AppDatabase): RatingDao {
        return database.ratingDao()
    }

    @Provides
    fun provideTagDao(database: AppDatabase): TagDao {
        return database.tagDao()
    }

    @Provides
    fun provideMeetupDao(database: AppDatabase): MeetupDao {
        return database.meetupDao()
    }

    @Provides
    fun provideSearchActivityDao(database: AppDatabase): SearchActivityDao {
        return database.searchActivityDao()
    }

    @Provides
    fun provideCourseActivityDao(database: AppDatabase): CourseActivityDao {
        return database.courseActivityDao()
    }

    @Provides
    fun provideResourceActivityDao(database: AppDatabase): ResourceActivityDao {
        return database.resourceActivityDao()
    }

    @Provides
    fun provideSubmitPhotosDao(database: AppDatabase): SubmitPhotosDao {
        return database.submitPhotosDao()
    }

    @Provides
    fun provideNewsLogDao(database: AppDatabase): NewsLogDao {
        return database.newsLogDao()
    }

    @Provides
    fun provideTeamLogDao(database: AppDatabase): TeamLogDao {
        return database.teamLogDao()
    }

    @Provides
    fun provideOfflineActivityDao(database: AppDatabase): OfflineActivityDao {
        return database.offlineActivityDao()
    }

    @Provides
    fun provideCourseProgressDao(database: AppDatabase): CourseProgressDao {
        return database.courseProgressDao()
    }

    @Provides
    fun provideRemovedLogDao(database: AppDatabase): RemovedLogDao {
        return database.removedLogDao()
    }

    @Provides
    fun provideTeamTaskDao(database: AppDatabase): TeamTaskDao {
        return database.teamTaskDao()
    }

    @Provides
    fun provideNotificationDao(database: AppDatabase): NotificationDao {
        return database.notificationDao()
    }
}
