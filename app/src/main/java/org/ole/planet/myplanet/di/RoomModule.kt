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
import org.ole.planet.myplanet.data.room.dao.CommunityDao
import org.ole.planet.myplanet.data.room.dao.DictionaryDao
import org.ole.planet.myplanet.data.room.dao.MyLifeDao
import org.ole.planet.myplanet.data.room.dao.PersonalDao
import org.ole.planet.myplanet.data.room.dao.RetryDao

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
}
