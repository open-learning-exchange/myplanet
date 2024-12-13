package org.ole.planet.myplanet.datamanager

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.types.RealmObject
import org.ole.planet.myplanet.model.*
import kotlin.reflect.KClass

class DatabaseService() {
    private val config: RealmConfiguration

    init {
        RealmLog.level = LogLevel.DEBUG
        config = RealmConfiguration.Builder(
            schema = setOf(
                RealmAchievement::class, RealmAnswer::class, RealmApkLog::class, RealmCertification::class,
                RealmChatHistory::class, RealmCommunity::class, RealmCourseActivity::class,
                RealmCourseProgress::class, RealmCourseStep::class, RealmDictionary::class,
                RealmExamQuestion::class, RealmFeedback::class, RealmMeetup::class, RealmMessage::class,
                RealmMyCourse::class, RealmMyHealth::class, RealmMyHealthPojo::class, RealmMyLibrary::class,
                RealmMyLife::class, RealmMyPersonal::class, RealmMyTeam::class, RealmNews::class,
                RealmNewsLog::class, RealmNotification::class, RealmOfflineActivity::class,
                RealmRating::class, RealmRemovedLog::class, RealmResourceActivity::class,
                RealmSearchActivity::class, RealmStepExam::class, RealmSubmission::class,
                RealmSubmitPhotos::class, RealmTag::class, RealmTeamLog::class, RealmTeamNotification::class,
                RealmTeamTask::class, RealmUserChallengeActions::class, RealmUserModel::class
            ) as Set<KClass<out RealmObject>>
        )
            .directory("default.realm")
            .schemaVersion(4)
            .build()
    }

    val realmInstance: Realm by lazy {
        Realm.open(config)
    }
}
