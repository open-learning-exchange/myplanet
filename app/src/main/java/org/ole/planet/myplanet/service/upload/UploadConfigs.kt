package org.ole.planet.myplanet.service.upload

import org.ole.planet.myplanet.model.RealmApkLog
import org.ole.planet.myplanet.model.RealmCourseActivity
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmNewsLog
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamTask

object UploadConfigs {
    val NewsActivities = UploadConfig(
        modelClass = RealmNewsLog::class,
        endpoint = "myplanet_activities",
        queryBuilder = { query ->
            query.isNull("_id").or().isEmpty("_id")
        },
        serializer = UploadSerializer.Simple(RealmNewsLog::serialize),
        idExtractor = { it.id }
    )

    val CourseProgress = UploadConfig(
        modelClass = RealmCourseProgress::class,
        endpoint = "courses_progress",
        queryBuilder = { query -> query.isNull("_id") },
        filterGuests = true,
        guestUserIdExtractor = { it.userId },
        serializer = UploadSerializer.Simple(RealmCourseProgress::serializeProgress),
        idExtractor = { it.id }
    )

    val TeamTask = UploadConfig(
        modelClass = RealmTeamTask::class,
        endpoint = "tasks",
        queryBuilder = { query ->
            query.beginGroup()
                .isNull("_id").or().isEmpty("_id").or().equalTo("isUpdated", true)
                .endGroup()
        },
        serializer = UploadSerializer.WithRealm(RealmTeamTask::serialize),
        idExtractor = { it.id }
    )

    val TeamActivitiesRefactored = UploadConfig(
        modelClass = RealmTeamLog::class,
        endpoint = "team_activities",
        queryBuilder = { query -> query.isNull("_rev") },
        serializer = UploadSerializer.WithContext(RealmTeamLog::serializeTeamActivities),
        idExtractor = { it._id }
    )

    val SearchActivity = UploadConfig(
        modelClass = RealmSearchActivity::class,
        endpoint = "search_activities",
        queryBuilder = { query -> query.isEmpty("_rev") },
        serializer = UploadSerializer.Simple { it.serialize() },
        idExtractor = { it._id }
    )

    val ResourceActivities = UploadConfig(
        modelClass = RealmResourceActivity::class,
        endpoint = "resource_activities",
        queryBuilder = { query ->
            query.isNull("_rev").notEqualTo("type", "sync")
        },
        serializer = UploadSerializer.Simple(RealmResourceActivity::serializeResourceActivities),
        idExtractor = { it._id }
    )

    val ResourceActivitiesSync = UploadConfig(
        modelClass = RealmResourceActivity::class,
        endpoint = "admin_activities",
        queryBuilder = { query ->
            query.isNull("_rev").equalTo("type", "sync")
        },
        serializer = UploadSerializer.Simple(RealmResourceActivity::serializeResourceActivities),
        idExtractor = { it._id }
    )

    val CourseActivities = UploadConfig(
        modelClass = RealmCourseActivity::class,
        endpoint = "course_activities",
        queryBuilder = { query ->
            query.isNull("_rev").notEqualTo("type", "sync")
        },
        serializer = UploadSerializer.Simple(RealmCourseActivity::serializeSerialize),
        idExtractor = { it._id }
    )

    val Meetups = UploadConfig(
        modelClass = RealmMeetup::class,
        endpoint = "meetups",
        queryBuilder = { query -> query },
        serializer = UploadSerializer.Simple(RealmMeetup::serialize),
        idExtractor = { it.id },
        responseHandler = ResponseHandler.Custom("id", "rev"),
        additionalUpdates = { _, meetup, uploadedItem ->
            meetup.meetupId = uploadedItem.remoteId
            meetup.meetupIdRev = uploadedItem.remoteRev
        }
    )

    val AdoptedSurveys = UploadConfig(
        modelClass = RealmStepExam::class,
        endpoint = "exams",
        queryBuilder = { query ->
            query.isNotNull("sourceSurveyId").isNull("_rev")
        },
        serializer = UploadSerializer.WithRealm(RealmStepExam::serializeExam),
        idExtractor = { it.id }
    )

    val Feedback = UploadConfig(
        modelClass = RealmFeedback::class,
        endpoint = "feedback",
        queryBuilder = { query -> query },
        serializer = UploadSerializer.Simple(RealmFeedback::serializeFeedback),
        idExtractor = { it.id }
    )

    val CrashLog = UploadConfig(
        modelClass = RealmApkLog::class,
        endpoint = "apk_logs",
        queryBuilder = { query -> query.isNull("_rev") },
        serializer = UploadSerializer.WithContext(RealmApkLog::serialize),
        idExtractor = { it.id }
    )

    val SubmitPhotos = UploadConfig(
        modelClass = RealmSubmitPhotos::class,
        endpoint = "submissions",
        queryBuilder = { query -> query.equalTo("uploaded", false) },
        serializer = UploadSerializer.Simple(RealmSubmitPhotos::serializeRealmSubmitPhotos),
        idExtractor = { it.id },
        additionalUpdates = { _, photo, _ ->
            photo.uploaded = true
        }
    )
}
