package org.ole.planet.myplanet.services.upload

import dagger.Lazy
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.model.RealmApkLog
import org.ole.planet.myplanet.model.RealmCourseActivity
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNewsLog
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.SharedPrefManager

@Singleton
class UploadConfigs @Inject constructor(
    private val voicesRepository: VoicesRepository,
    private val submissionsRepository: SubmissionsRepository,
    private val activitiesRepository: ActivitiesRepository,
    private val teamsSyncRepository: Lazy<TeamsSyncRepository>,
    private val sharedPrefManager: SharedPrefManager,
    private val userRepository: UserRepository,
    private val surveysRepository: SurveysRepository
) {
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
        serializer = UploadSerializer.Async { task ->
            val user = userRepository.getUserById(task.assignee ?: "")
            RealmTeamTask.serialize(task, user)
        },
        idExtractor = { it.id }
    )

    val TeamActivities = UploadConfig(
        modelClass = RealmTeamLog::class,
        endpoint = "team_activities",
        queryBuilder = { query -> query.isNull("_rev") },
        serializer = UploadSerializer.WithContext { log, context -> teamsSyncRepository.get().serializeTeamActivities(log, context) },
        idExtractor = { it.id }
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
        serializer = UploadSerializer.Simple { org.ole.planet.myplanet.repository.serializeResourceActivities(it) },
        idExtractor = { it._id }
    )

    val ResourceActivitiesSync = UploadConfig(
        modelClass = RealmResourceActivity::class,
        endpoint = "admin_activities",
        queryBuilder = { query ->
            query.isNull("_rev").equalTo("type", "sync")
        },
        serializer = UploadSerializer.Simple { org.ole.planet.myplanet.repository.serializeResourceActivities(it) },
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
        queryBuilder = { query ->
            query.beginGroup()
                .isNull("meetupId").or().isEmpty("meetupId")
                .endGroup()
                .or()
                .beginGroup()
                .equalTo("updated", true)
                .endGroup()
        },
        serializer = UploadSerializer.Simple(RealmMeetup::serialize),
        idExtractor = { it.id },
        responseHandler = ResponseHandler.Custom("id", "rev"),
        additionalUpdates = { _, meetup, uploadedItem ->
            meetup.meetupId = uploadedItem.remoteId
            meetup.meetupIdRev = uploadedItem.remoteRev
            meetup.updated = false
        }
    )

    val AdoptedSurveys = UploadConfig(
        modelClass = RealmStepExam::class,
        endpoint = "exams",
        queryBuilder = { query ->
            query.isNotNull("sourceSurveyId").isNull("_rev")
        },
        serializer = UploadSerializer.Async { exam ->
            val questions = surveysRepository.getExamQuestions(exam.id ?: "")
            RealmStepExam.serializeExam(exam, questions)
        },
        idExtractor = { it.id }
    )

    val Feedback = UploadConfig(
        modelClass = RealmFeedback::class,
        endpoint = "feedback",
        queryBuilder = { query ->
            query.equalTo("isUploaded", false)
        },
        serializer = UploadSerializer.Simple(RealmFeedback::serializeFeedback),
        idExtractor = { it.id },
        additionalUpdates = { _, feedback, _ ->
            feedback.isUploaded = true
        }
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

    // POST/PUT Methods (Phase 4)

    val ExamResults = UploadConfig(
        modelClass = RealmSubmission::class,
        endpoint = "submissions",
        queryBuilder = { query ->
            query.equalTo("type", "exam")
                .isNotNull("parentId").isNotNull("userId")
                .beginGroup()
                .isNull("_id").or().isEmpty("_id")
                .endGroup()
        },
        serializer = UploadSerializer.Async { submission ->
            submissionsRepository.getExamUploadPayload(submission)
        },
        idExtractor = { it.id },
        dbIdExtractor = { it._id },  // Enables POST/PUT logic
        filterGuests = true,
        guestUserIdExtractor = { it.userId }
    )

    val Submissions = UploadConfig(
        modelClass = RealmSubmission::class,
        endpoint = "submissions",
        queryBuilder = { query ->
            query.equalTo("status", "complete")
                .beginGroup()
                    .equalTo("isUpdated", true)
                    .or()
                    .isEmpty("_id")
                .endGroup()
        },
        serializer = UploadSerializer.AsyncContext { submission, context ->
            submissionsRepository.serializeSubmission(submission, context, sharedPrefManager.getPlanetCode(), sharedPrefManager.getParentCode())
        },
        idExtractor = { it.id },
        dbIdExtractor = { it._id },  // Enables POST/PUT logic
        additionalUpdates = { _, submission, _ ->
            submission.isUpdated = false
        }
    )

    fun getResourcesConfig(user: RealmUser?): UploadConfig<RealmMyLibrary> {
        return UploadConfig(
            modelClass = RealmMyLibrary::class,
            endpoint = "resources",
            queryBuilder = { query -> query.isNull("_rev") },
            serializer = UploadSerializer.Simple { library ->
                RealmMyLibrary.serialize(library, user)
            },
            idExtractor = { it.id },
            additionalUpdates = { realm, library, uploadedItem ->
                val planetCode = user?.planetCode?.takeIf { it.isNotBlank() }
                    ?: sharedPrefManager.getPlanetCode()

                if (library.isPrivate && !library.privateFor.isNullOrBlank()) {
                    val teamResource = realm.createObject(
                        RealmMyTeam::class.java,
                        UUID.randomUUID().toString()
                    )
                    teamResource.teamId = library.privateFor
                    teamResource.title = library.title
                    teamResource.resourceId = uploadedItem.remoteId
                    teamResource.docType = "resourceLink"
                    teamResource.updated = true
                    teamResource.teamType = "local"
                    teamResource.teamPlanetCode = planetCode
                    teamResource.sourcePlanet = planetCode
                }
            }
        )
    }

    val Rating = UploadConfig(
        modelClass = RealmRating::class,
        endpoint = "ratings",
        queryBuilder = { query ->
            query.equalTo("isUpdated", true)
        },
        serializer = UploadSerializer.Simple(RealmRating::serializeRating),
        idExtractor = { it.id },
        dbIdExtractor = { it._id },  // Enables POST/PUT logic
        filterGuests = true,
        guestUserIdExtractor = { it.userId },
        additionalUpdates = { _, rating, _ ->
            rating.isUpdated = false
        }
    )
}
