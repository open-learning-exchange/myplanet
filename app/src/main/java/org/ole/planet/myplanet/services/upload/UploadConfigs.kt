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
import org.ole.planet.myplanet.model.ApkLog
import org.ole.planet.myplanet.model.CourseActivity
import org.ole.planet.myplanet.model.CourseProgress
import org.ole.planet.myplanet.model.Feedback
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.NewsLog
import org.ole.planet.myplanet.model.Rating
import org.ole.planet.myplanet.model.ResourceActivity
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.SubmitPhotos
import org.ole.planet.myplanet.model.TeamLog
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.EventsRepository
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
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
    private val resourcesRepository: ResourcesRepository,
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
        modelClassName = "NewsLog",
        fetchPendingItems = { newsLogDao.getPendingUploads() },
        serializer = UploadSerializer.Simple(NewsLog::serialize),
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                newsLogDao.markUploaded(result.localId, result.remoteId, result.remoteRev) == 0
            }
        }
    )

    val CourseProgress = RoomUploadConfig(
        endpoint = "courses_progress",
        modelClassName = "CourseProgress",
        fetchPendingItems = { courseProgressDao.getPendingUploads() },
        serializer = UploadSerializer.Simple(org.ole.planet.myplanet.model.CourseProgress::serializeProgress),
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                courseProgressDao.markUploaded(result.localId, result.remoteId, result.remoteRev) == 0
            }
        }
    )

    val TeamTask = RoomUploadConfig(
        endpoint = "tasks",
        modelClassName = "TeamTask",
        fetchPendingItems = { teamTaskDao.getPendingUploads() },
        serializer = UploadSerializer.Async { task ->
            val user = userRepository.getUserById(task.assignee ?: "")
            org.ole.planet.myplanet.model.TeamTask.serialize(task, user)
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
        modelClassName = "TeamLog",
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
        modelClassName = "SearchActivity",
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
        modelClassName = "ResourceActivity",
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
        modelClassName = "ResourceActivity",
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
        modelClassName = "CourseActivity",
        fetchPendingItems = { courseActivityDao.getPendingUploads() },
        serializer = UploadSerializer.Simple(CourseActivity::serialize),
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
        modelClassName = "Meetup",
        fetchPendingItems = { eventsRepository.getPendingMeetupUploads() },
        serializer = UploadSerializer.Simple(Meetup::serialize),
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
        fetchPendingItems = { surveysRepository.getPendingAdoptedSurveys() },
        serializer = UploadSerializer.Async { exam ->
            val questions = surveysRepository.getExamQuestions(exam.id ?: "")
            RealmStepExam.serializeExam(exam, questions)
        },
        idExtractor = { it.id }
    )

    // Migrated to Room: uses the database-agnostic RoomUploadConfig path in UploadCoordinator.
    val Feedback = RoomUploadConfig(
        endpoint = "feedback",
        modelClassName = "Feedback",
        fetchPendingItems = { feedbackRepository.getPendingFeedback() },
        serializer = UploadSerializer.Simple(org.ole.planet.myplanet.model.Feedback::serializeFeedback),
        idExtractor = { it.id },
        markUploaded = { results ->
            // Mark each uploaded feedback; rows that no longer exist are reported as failures.
            results.filter { result -> !feedbackRepository.markFeedbackUploaded(result.localId) }
        }
    )

    // Migrated to Room: uses the database-agnostic RoomUploadConfig path in UploadCoordinator.
    val CrashLog = RoomUploadConfig(
        endpoint = "apk_logs",
        modelClassName = "ApkLog",
        fetchPendingItems = { apkLogDao.getPending() },
        serializer = UploadSerializer.WithContext(ApkLog::serialize),
        idExtractor = { it.id },
        markUploaded = { results ->
            // A row is "pending" until it has a _rev; set it here. Rows that no longer exist
            // (0 updated) are reported back as local failures.
            results.filter { result -> apkLogDao.markUploaded(result.localId, result.remoteRev) == 0 }
        }
    )

    val SubmitPhotos = RoomUploadConfig(
        endpoint = "submissions",
        modelClassName = "SubmitPhotos",
        fetchPendingItems = { submitPhotosDao.getUnuploaded() },
        serializer = UploadSerializer.Simple(org.ole.planet.myplanet.model.SubmitPhotos::serialize),
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
        fetchPendingItems = { submissionsRepository.getPendingExamResults() },
        serializer = UploadSerializer.Async { submission ->
            submissionsRepository.getExamUploadPayload(submission)
        },
        idExtractor = { it.id },
        dbIdExtractor = { it._id },
        filterGuests = true,
        guestUserIdExtractor = { it.userId }
    )

    val Submissions = UploadConfig(
        modelClass = RealmSubmission::class,
        endpoint = "submissions",
        fetchPendingItems = { submissionsRepository.getPendingSubmissionsForUpload() },
        serializer = UploadSerializer.AsyncContext { submission, context ->
            submissionsRepository.serializeSubmission(submission, context, sharedPrefManager.getPlanetCode(), sharedPrefManager.getParentCode())
        },
        idExtractor = { it.id },
        dbIdExtractor = { it._id },
        additionalUpdates = { submission, _ ->
            submission.isUpdated = false
        }
    )

    // Migrated to Room: uses the database-agnostic RoomUploadConfig path in UploadCoordinator.
    // The private-resource team-link creation moves into the repository's markResourceUploaded.
    fun getResourcesConfig(user: RealmUser?): RoomUploadConfig<RealmMyLibrary> {
        return RoomUploadConfig(
            endpoint = "resources",
            modelClassName = "RealmMyLibrary",
            fetchPendingItems = { resourcesRepository.getPendingResourceUploads() },
            serializer = UploadSerializer.Simple { library ->
                RealmMyLibrary.serialize(library, user)
            },
            idExtractor = { it.id },
            markUploaded = { results ->
                results.filter { result ->
                    !resourcesRepository.markResourceUploaded(
                        result.localId,
                        result.remoteId,
                        result.remoteRev,
                        user?.planetCode
                    )
                }
            }
        )
    }

    // Migrated to Room: uses the database-agnostic RoomUploadConfig path in UploadCoordinator.
    // Guest filtering is folded into getPendingRatingUploads()'s DAO query.
    val Rating = RoomUploadConfig(
        endpoint = "ratings",
        modelClassName = "Rating",
        fetchPendingItems = { ratingsRepository.getPendingRatingUploads() },
        serializer = UploadSerializer.Simple(org.ole.planet.myplanet.model.Rating::serializeRating),
        idExtractor = { it.id },
        dbIdExtractor = { it._id }, // Enables POST/PUT logic
        markUploaded = { results ->
            results.filter { result -> !ratingsRepository.markRatingUploaded(result.localId) }
        }
    )
}
