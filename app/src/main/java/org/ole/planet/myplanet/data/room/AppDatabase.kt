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
import org.ole.planet.myplanet.data.room.dao.MyLibraryDao
import org.ole.planet.myplanet.data.room.dao.MyLifeDao
import org.ole.planet.myplanet.data.room.dao.NotificationDao
import org.ole.planet.myplanet.data.room.dao.NewsDao
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
import org.ole.planet.myplanet.data.room.dao.legacy.AnswerDao
import org.ole.planet.myplanet.data.room.dao.legacy.CourseDao
import org.ole.planet.myplanet.data.room.dao.legacy.CourseStepDao
import org.ole.planet.myplanet.data.room.dao.legacy.ExamDao
import org.ole.planet.myplanet.data.room.dao.legacy.QuestionDao
import org.ole.planet.myplanet.data.room.dao.legacy.SubmissionDao
import org.ole.planet.myplanet.data.room.dao.legacy.TeamDao
import org.ole.planet.myplanet.data.room.dao.legacy.UserDao
import org.ole.planet.myplanet.data.room.entity.legacy.RoomAnswerEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomCourseEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomCourseStepEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomExamEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomQuestionEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomSubmissionEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomTeamEntity
import org.ole.planet.myplanet.data.room.entity.legacy.RoomUserEntity
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
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.News
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
import org.ole.planet.myplanet.model.TagEntity
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
        TagEntity::class,
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
        News::class,
        MyLibrary::class,
        RoomUserEntity::class,
        RoomCourseEntity::class,
        RoomCourseStepEntity::class,
        RoomExamEntity::class,
        RoomQuestionEntity::class,
        RoomSubmissionEntity::class,
        RoomAnswerEntity::class,
        RoomTeamEntity::class,
    ],
    version = 3,
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
    abstract fun newsDao(): NewsDao
    abstract fun myLibraryDao(): MyLibraryDao
    abstract fun userDao(): UserDao
    abstract fun courseDao(): CourseDao
    abstract fun courseStepDao(): CourseStepDao
    abstract fun examDao(): ExamDao
    abstract fun questionDao(): QuestionDao
    abstract fun submissionDao(): SubmissionDao
    abstract fun answerDao(): AnswerDao
    abstract fun teamDao(): TeamDao
}
