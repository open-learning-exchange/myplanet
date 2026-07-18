package org.ole.planet.myplanet.services.upload

import dagger.Lazy
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.data.room.dao.ApkLogDao
import org.ole.planet.myplanet.data.room.dao.CourseActivityDao
import org.ole.planet.myplanet.data.room.dao.CourseProgressDao
import org.ole.planet.myplanet.data.room.dao.NewsLogDao
import org.ole.planet.myplanet.data.room.dao.ResourceActivityDao
import org.ole.planet.myplanet.data.room.dao.SearchActivityDao
import org.ole.planet.myplanet.data.room.dao.SubmitPhotosDao
import org.ole.planet.myplanet.data.room.dao.TeamLogDao
import org.ole.planet.myplanet.data.room.dao.TeamTaskDao
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
import org.ole.planet.myplanet.repository.EventsRepository
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.RatingsRepository
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
    private val surveysRepository: SurveysRepository,
    private val feedbackRepository: FeedbackRepository,
    private val ratingsRepository: RatingsRepository,
    private val eventsRepository: EventsRepository,
    private val apkLogDao: ApkLogDao,
    private val searchActivityDao: SearchActivityDao,
    private val courseActivityDao: CourseActivityDao,
    private val courseProgressDao: CourseProgressDao,
    private val resourceActivityDao: ResourceActivityDao,
    private val submitPhotosDao: SubmitPhotosDao,
    private val newsLogDao: NewsLogDao,
    private val teamLogDao: TeamLogDao,
    private val teamTaskDao: TeamTaskDao
) {
    val NewsActivities = RoomUploadConfig(
        endpoint = "myplanet_activities",
        modelClassName = "RealmNewsLog",
        fetchPendingItems = { newsLogDao.getPendingUploads() },
        serializer = UploadSerializer.Simple(RealmNewsLog::serialize),
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                newsLogDao.markUploaded(result.localId, result.remoteId, result.remoteRev) == 0
            }
        }
    )

    val CourseProgress = RoomUploadConfig(
        endpoint = "courses_progress",
        modelClassName = "RealmCourseProgress",
        fetchPendingItems = { courseProgressDao.getPendingUploads() },
        serializer = UploadSerializer.Simple(RealmCourseProgress::serializeProgress),
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                courseProgressDao.markUploaded(result.localId, result.remoteId, result.remoteRev) == 0
            }
        }
    )

    val TeamTask = RoomUploadConfig(
        endpoint = "tasks",
        modelClassName = "RealmTeamTask",
        fetchPendingItems = { teamTaskDao.getPendingUploads() },
        serializer = UploadSerializer.Async { task ->
            val user = userRepository.getUserById(task.assignee ?: "")
            RealmTeamTask.serialize(task, user)
        },
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                teamTaskDao.markUploaded(result.localId, result.remoteId, result.remoteRev) == 0
            }
        }
    )

    val TeamActivities = RoomUploadConfig(
        endpoint = "team_activities",
        modelClassName = "RealmTeamLog",
        fetchPendingItems = { teamLogDao.getPendingUploads() },
        serializer = UploadSerializer.WithContext { log, context -> teamsSyncRepository.get().serializeTeamActivities(log, context) },
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                teamLogDao.markUploaded(result.localId, result.remoteId, result.remoteRev) == 0
            }
        }
    )

    val SearchActivity = RoomUploadConfig(
        endpoint = "search_activities",
        modelClassName = "RealmSearchActivity",
        fetchPendingItems = { searchActivityDao.getPendingUploads() },
        serializer = UploadSerializer.Simple { it.serialize() },
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                searchActivityDao.markUploaded(
                    localId = result.localId,
                    remoteId = result.remoteId,
                    rev = result.remoteRev
                ) == 0
            }
        }
    )

    val ResourceActivities = RoomUploadConfig(
        endpoint = "resource_activities",
        modelClassName = "RealmResourceActivity",
        fetchPendingItems = { resourceActivityDao.getPendingUploads() },
        serializer = UploadSerializer.Simple { org.ole.planet.myplanet.repository.serializeResourceActivities(it) },
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                resourceActivityDao.markUploaded(result.localId, result.remoteId, result.remoteRev) == 0
            }
        }
    )

    val ResourceActivitiesSync = RoomUploadConfig(
        endpoint = "admin_activities",
        modelClassName = "RealmResourceActivity",
        fetchPendingItems = { resourceActivityDao.getPendingSyncUploads() },
        serializer = UploadSerializer.Simple { org.ole.planet.myplanet.repository.serializeResourceActivities(it) },
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                resourceActivityDao.markUploaded(result.localId, result.remoteId, result.remoteRev) == 0
            }
        }
    )

    val CourseActivities = RoomUploadConfig(
        endpoint = "course_activities",
        modelClassName = "RealmCourseActivity",
        fetchPendingItems = { courseActivityDao.getPendingUploads() },
        serializer = UploadSerializer.Simple(RealmCourseActivity::serializeSerialize),
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                courseActivityDao.markUploaded(
                    localId = result.localId,
                    remoteId = result.remoteId,
                    rev = result.remoteRev
                ) == 0
            }
        }
    )

    // Migrated to Room: uses the database-agnostic RoomUploadConfig path in UploadCoordinator.
    val Meetups = RoomUploadConfig(
        endpoint = "meetups",
        modelClassName = "RealmMeetup",
        fetchPendingItems = { eventsRepository.getPendingMeetupUploads() },
        serializer = UploadSerializer.Simple(RealmMeetup::serialize),
        idExtractor = { it.id },
        responseHandler = ResponseHandler.Custom("id", "rev"),
        markUploaded = { results ->
            results.filter { result ->
                !eventsRepository.markMeetupUploaded(result.localId, result.remoteId, result.remoteRev)
            }
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

    // Migrated to Room: uses the database-agnostic RoomUploadConfig path in UploadCoordinator.
    val Feedback = RoomUploadConfig(
        endpoint = "feedback",
        modelClassName = "RealmFeedback",
        fetchPendingItems = { feedbackRepository.getPendingFeedback() },
        serializer = UploadSerializer.Simple(RealmFeedback::serializeFeedback),
        idExtractor = { it.id },
        markUploaded = { results ->
            // Mark each uploaded feedback; rows that no longer exist are reported as failures.
            results.filter { result -> !feedbackRepository.markFeedbackUploaded(result.localId) }
        }
    )

    // Migrated to Room: uses the database-agnostic RoomUploadConfig path in UploadCoordinator.
    val CrashLog = RoomUploadConfig(
        endpoint = "apk_logs",
        modelClassName = "RealmApkLog",
        fetchPendingItems = { apkLogDao.getPending() },
        serializer = UploadSerializer.WithContext(RealmApkLog::serialize),
        idExtractor = { it.id },
        markUploaded = { results ->
            // A row is "pending" until it has a _rev; set it here. Rows that no longer exist
            // (0 updated) are reported back as local failures.
            results.filter { result -> apkLogDao.markUploaded(result.localId, result.remoteRev) == 0 }
        }
    )

    val SubmitPhotos = RoomUploadConfig(
        endpoint = "submissions",
        modelClassName = "RealmSubmitPhotos",
        fetchPendingItems = { submitPhotosDao.getUnuploaded() },
        serializer = UploadSerializer.Simple(RealmSubmitPhotos::serializeRealmSubmitPhotos),
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                submitPhotosDao.markUploaded(result.localId, result.remoteRev, result.remoteId) == 0
            }
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

    // Migrated to Room: uses the database-agnostic RoomUploadConfig path in UploadCoordinator.
    // Guest filtering is folded into getPendingRatingUploads()'s DAO query.
    val Rating = RoomUploadConfig(
        endpoint = "ratings",
        modelClassName = "RealmRating",
        fetchPendingItems = { ratingsRepository.getPendingRatingUploads() },
        serializer = UploadSerializer.Simple(RealmRating::serializeRating),
        idExtractor = { it.id },
        dbIdExtractor = { it._id }, // Enables POST/PUT logic
        markUploaded = { results ->
            results.filter { result -> !ratingsRepository.markRatingUploaded(result.localId) }
        }
    )
}
