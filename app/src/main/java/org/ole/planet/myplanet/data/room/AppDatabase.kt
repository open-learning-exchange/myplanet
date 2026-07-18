package org.ole.planet.myplanet.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
import org.ole.planet.myplanet.model.RealmApkLog
import org.ole.planet.myplanet.model.RealmCertification
import org.ole.planet.myplanet.model.RealmChatHistory
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.model.RealmCourseActivity
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.model.RealmNewsLog
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmRetryOperation
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.model.RealmRemovedLog
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmTag
import org.ole.planet.myplanet.model.RealmTeamNotification
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserChallengeActions

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
        RealmMyLife::class,
        RealmMyPersonal::class,
        RealmRetryOperation::class,
        RealmCommunity::class,
        RealmApkLog::class,
        RealmUserChallengeActions::class,
        RealmTeamNotification::class,
        RealmCertification::class,
        RealmChatHistory::class,
        RealmFeedback::class,
        RealmRating::class,
        RealmTag::class,
        RealmMeetup::class,
        RealmSearchActivity::class,
        RealmCourseActivity::class,
        RealmResourceActivity::class,
        RealmSubmitPhotos::class,
        RealmNewsLog::class,
        RealmTeamLog::class,
        RealmOfflineActivity::class,
        RealmCourseProgress::class,
        RealmRemovedLog::class,
        RealmTeamTask::class,
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
}
