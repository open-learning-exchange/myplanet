package org.ole.planet.myplanet.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.ole.planet.myplanet.data.room.dao.AchievementDao
import org.ole.planet.myplanet.data.room.dao.ApkLogDao
import org.ole.planet.myplanet.data.room.dao.UserChallengeActionsDao
import org.ole.planet.myplanet.data.room.dao.CertificationDao
import org.ole.planet.myplanet.data.room.dao.ChatDao
import org.ole.planet.myplanet.data.room.dao.CommunityDao
import org.ole.planet.myplanet.data.room.dao.CourseActivityDao
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.DictionaryDao
import org.ole.planet.myplanet.data.room.dao.FeedbackDao
import org.ole.planet.myplanet.data.room.dao.HealthExaminationDao
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
import org.ole.planet.myplanet.data.room.entity.DictionaryEntity
import org.ole.planet.myplanet.model.Achievement
import org.ole.planet.myplanet.model.ApkLog
import org.ole.planet.myplanet.model.Certification
import org.ole.planet.myplanet.model.ChatHistory
import org.ole.planet.myplanet.model.Community
import org.ole.planet.myplanet.model.CourseActivity
import org.ole.planet.myplanet.model.CourseProgress
import org.ole.planet.myplanet.model.Feedback
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MyLife
import org.ole.planet.myplanet.model.Personal
import org.ole.planet.myplanet.model.NewsLog
import org.ole.planet.myplanet.model.AppNotification
import org.ole.planet.myplanet.model.OfflineActivity
import org.ole.planet.myplanet.model.Rating
import org.ole.planet.myplanet.model.RetryOperation
import org.ole.planet.myplanet.model.ResourceActivity
import org.ole.planet.myplanet.model.RemovedLog
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.model.SubmitPhotos
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.TeamNotification
import org.ole.planet.myplanet.model.TeamLog
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.UserChallengeActions

/**
 * Room database that is progressively replacing the legacy Realm store.
 *
 * Entities and DAOs are added here as each domain is migrated off Realm. Because the migration
 * uses a drop-and-resync strategy (data is re-pulled from the Planet/CouchDB server on first
 * launch), destructive schema migrations are acceptable and configured in the Hilt module.
 */
@Database(
    entities = [
        DictionaryEntity::class,
        MyLife::class,
        Personal::class,
        RetryOperation::class,
        Community::class,
        ApkLog::class,
        UserChallengeActions::class,
        TeamNotification::class,
        Certification::class,
        ChatHistory::class,
        Feedback::class,
        Rating::class,
        RealmTag::class,
        Meetup::class,
        SearchActivity::class,
        CourseActivity::class,
        ResourceActivity::class,
        SubmitPhotos::class,
        NewsLog::class,
        TeamLog::class,
        OfflineActivity::class,
        CourseProgress::class,
        RemovedLog::class,
        TeamTask::class,
        AppNotification::class,
        Achievement::class,
        HealthExamination::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun myLifeDao(): MyLifeDao
    abstract fun personalDao(): PersonalDao
    abstract fun retryDao(): RetryDao
    abstract fun communityDao(): CommunityDao
    abstract fun apkLogDao(): ApkLogDao
    abstract fun userChallengeActionsDao(): UserChallengeActionsDao
    abstract fun teamNotificationDao(): TeamNotificationDao
    abstract fun certificationDao(): CertificationDao
    abstract fun chatDao(): ChatDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun ratingDao(): RatingDao
    abstract fun tagDao(): TagDao
    abstract fun meetupDao(): MeetupDao
    abstract fun searchActivityDao(): SearchActivityDao
    abstract fun courseActivityDao(): CourseActivityDao
    abstract fun resourceActivityDao(): ResourceActivityDao
    abstract fun submitPhotosDao(): SubmitPhotosDao
    abstract fun newsLogDao(): NewsLogDao
    abstract fun teamLogDao(): TeamLogDao
    abstract fun offlineActivityDao(): OfflineActivityDao
    abstract fun courseProgressDao(): CourseProgressDao
    abstract fun removedLogDao(): RemovedLogDao
    abstract fun teamTaskDao(): TeamTaskDao
    abstract fun notificationDao(): NotificationDao
    abstract fun achievementDao(): AchievementDao
    abstract fun healthExaminationDao(): HealthExaminationDao
}
