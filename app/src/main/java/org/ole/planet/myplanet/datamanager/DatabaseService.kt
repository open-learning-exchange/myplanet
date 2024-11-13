package org.ole.planet.myplanet.datamanager

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.log.LogLevel
import org.ole.planet.myplanet.model.*

class DatabaseService() {
    private val config: RealmConfiguration

    init {
        RealmLog.level = LogLevel.DEBUG // Set log level
        // Define the Realm configuration
        config = RealmConfiguration.create(
            schema = setOf(
                RealmAchievement::class, RealmAnswer::class, RealmApkLog::class,
                RealmCertification::class, RealmChatHistory::class, RealmCommunity::class,
                RealmCourseActivity::class, RealmCourseProgress::class, RealmCourseStep::class,
                RealmDictionary::class, RealmExamQuestion::class, RealmFeedback::class,
                RealmMeetup::class, RealmMessage::class, RealmMyCourse::class, RealmMyHealth::class,
                RealmMyHealthPojo::class, RealmMyLibrary::class, RealmMyLife::class,
                RealmMyPersonal::class, RealmMyTeam::class, RealmNews::class, RealmNewsLog::class,
                RealmNotification::class, RealmOfflineActivity::class, RealmRating::class,
                RealmRemovedLog::class, RealmResourceActivity::class, RealmSearchActivity::class,
                RealmStepExam::class, RealmSubmission::class, RealmSubmitPhotos::class, RealmTag::class, RealmTeamLog::class, RealmTeamNotification::class, RealmTeamTask::class,
                RealmUserChallengeActions::class, RealmUserModel::class),
            name = "default.realm",
            schemaVersion = 4
        )
    }

    // Lazily open a Realm instance with the configuration
    val realmInstance: Realm by lazy {
        Realm.open(config)
    }
}

//class DatabaseService(context: Context) {
//    init {
//        Realm.init(context)
//        RealmLog.setLevel(LogLevel.DEBUG)
//        val config = RealmConfiguration.Builder()
//            .name(Realm.DEFAULT_REALM_NAME)
//            .deleteRealmIfMigrationNeeded()
//            .schemaVersion(4)
//            .allowWritesOnUiThread(true)
//            .build()
//        Realm.setDefaultConfiguration(config)
//    }
//
//    val realmInstance: Realm
//        get() {
//            return Realm.getDefaultInstance()
//        }
//}