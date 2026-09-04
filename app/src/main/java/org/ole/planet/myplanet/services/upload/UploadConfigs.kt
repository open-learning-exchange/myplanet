package org.ole.planet.myplanet.services.upload

import android.content.Context
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.model.ApkLog
import org.ole.planet.myplanet.model.CourseActivity
import org.ole.planet.myplanet.model.CourseProgress
import org.ole.planet.myplanet.model.Feedback
import org.ole.planet.myplanet.model.Meetup
import org.ole.planet.myplanet.model.MyLibrary
import org.ole.planet.myplanet.model.NewsLog
import org.ole.planet.myplanet.model.Rating
import org.ole.planet.myplanet.model.ResourceActivity
import org.ole.planet.myplanet.model.SearchActivity
import org.ole.planet.myplanet.model.StepExam
import org.ole.planet.myplanet.model.Submission
import org.ole.planet.myplanet.model.SubmitPhotos
import org.ole.planet.myplanet.model.TeamLog
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.ActivitiesRepository
import org.ole.planet.myplanet.repository.DiagnosticsRepository
import org.ole.planet.myplanet.repository.EventsRepository
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.repository.SubmissionsRepository
import org.ole.planet.myplanet.repository.SurveysRepository
import org.ole.planet.myplanet.repository.TeamsSyncRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.VoicesRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.NetworkUtils
import org.ole.planet.myplanet.utils.VersionUtils

@Singleton
class UploadConfigs @Inject constructor(
    @ApplicationContext private val context: Context,
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
    private val diagnosticsRepository: DiagnosticsRepository,
    private val progressRepository: ProgressRepository
) {
    private val androidId: String? by lazy { VersionUtils.getAndroidId(context) }
    private val customDeviceName: String by lazy { sharedPrefManager.getCustomDeviceName() }

    val NewsActivities = RoomUploadConfig(
        endpoint = "myplanet_activities",
        modelClassName = "NewsLog",
        fetchPendingItems = { voicesRepository.getPendingNewsLogUploads() },
        serializer = UploadSerializer.Simple { log -> NewsLog.serialize(log, customDeviceName) },
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                !voicesRepository.markNewsLogUploaded(result.localId, result.remoteId, result.remoteRev)
            }
        }
    )

    val CourseProgress = RoomUploadConfig(
        endpoint = "courses_progress",
        modelClassName = "CourseProgress",
        fetchPendingItems = { progressRepository.getPendingCourseProgressUploads() },
        serializer = UploadSerializer.Simple(org.ole.planet.myplanet.model.CourseProgress::serializeProgress),
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                !progressRepository.markCourseProgressUploaded(result.localId, result.remoteId, result.remoteRev)
            }
        }
    )

    val TeamTask = RoomUploadConfig(
        endpoint = "tasks",
        modelClassName = "TeamTask",
        fetchPendingItems = { teamsSyncRepository.get().getPendingTaskUploads() },
        serializer = UploadSerializer.Async { task ->
            val user = userRepository.getUserById(task.assignee ?: "")
            org.ole.planet.myplanet.model.TeamTask.serialize(task, user)
        },
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                !teamsSyncRepository.get().markTaskUploaded(result.localId, result.remoteId, result.remoteRev)
            }
        }
    )

    val TeamActivities = RoomUploadConfig(
        endpoint = "team_activities",
        modelClassName = "TeamLog",
        fetchPendingItems = { teamsSyncRepository.get().getPendingTeamLogUploads() },
        serializer = UploadSerializer.Simple { log -> teamsSyncRepository.get().serializeTeamActivities(log) },
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                !teamsSyncRepository.get().markTeamLogUploaded(result.localId, result.remoteId, result.remoteRev)
            }
        }
    )

    val SearchActivity = RoomUploadConfig(
        endpoint = "search_activities",
        modelClassName = "SearchActivity",
        fetchPendingItems = { activitiesRepository.getPendingSearchActivityUploads() },
        serializer = UploadSerializer.Simple { it.serialize(androidId, customDeviceName) },
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                !activitiesRepository.markSearchActivityUploaded(
                    localId = result.localId,
                    remoteId = result.remoteId,
                    rev = result.remoteRev
                )
            }
        }
    )

    val ResourceActivities = RoomUploadConfig(
        endpoint = "resource_activities",
        modelClassName = "ResourceActivity",
        fetchPendingItems = { activitiesRepository.getPendingResourceActivityUploads() },
        serializer = UploadSerializer.Simple { org.ole.planet.myplanet.repository.serializeResourceActivities(it) },
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                !activitiesRepository.markResourceActivityUploaded(result.localId, result.remoteId, result.remoteRev)
            }
        }
    )

    val ResourceActivitiesSync = RoomUploadConfig(
        endpoint = "admin_activities",
        modelClassName = "ResourceActivity",
        fetchPendingItems = { activitiesRepository.getPendingResourceActivitySyncUploads() },
        serializer = UploadSerializer.Simple { org.ole.planet.myplanet.repository.serializeResourceActivities(it) },
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                !activitiesRepository.markResourceActivityUploaded(result.localId, result.remoteId, result.remoteRev)
            }
        }
    )

    val CourseActivities = RoomUploadConfig(
        endpoint = "course_activities",
        modelClassName = "CourseActivity",
        fetchPendingItems = { activitiesRepository.getPendingCourseActivityUploads() },
        serializer = UploadSerializer.Simple(CourseActivity::serialize),
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                !activitiesRepository.markCourseActivityUploaded(
                    localId = result.localId,
                    remoteId = result.remoteId,
                    rev = result.remoteRev
                )
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
        modelClass = StepExam::class,
        endpoint = "exams",
        fetchPendingItems = { surveysRepository.getPendingAdoptedSurveys() },
        serializer = UploadSerializer.Async { exam ->
            val questions = surveysRepository.getExamQuestions(exam.id ?: "")
            StepExam.serializeExam(exam, questions)
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
        fetchPendingItems = { diagnosticsRepository.getPendingApkLogs() },
        serializer = UploadSerializer.Simple { log -> ApkLog.serialize(log, customDeviceName) },
        idExtractor = { it.id },
        markUploaded = { results ->
            // A row is "pending" until it has a _rev; set it here. Rows that no longer exist
            // (0 updated) are reported back as local failures.
            results.filter { result -> !diagnosticsRepository.markApkLogUploaded(result.localId, result.remoteRev) }
        }
    )

    val SubmitPhotos = RoomUploadConfig(
        endpoint = "submissions",
        modelClassName = "SubmitPhotos",
        fetchPendingItems = { submissionsRepository.getPendingSubmitPhotosUploads() },
        serializer = UploadSerializer.Simple(org.ole.planet.myplanet.model.SubmitPhotos::serialize),
        idExtractor = { it.id },
        markUploaded = { results ->
            results.filter { result ->
                !submissionsRepository.markSubmitPhotosUploaded(result.localId, result.remoteId, result.remoteRev)
            }
        }
    )

    // POST/PUT Methods (Phase 4)

    val ExamResults = UploadConfig(
        modelClass = Submission::class,
        endpoint = "submissions",
        fetchPendingItems = { submissionsRepository.getPendingExamResults() },
        serializer = UploadSerializer.Async { submission ->
            val user = submission.userId?.let { userRepository.getUserById(it) }
            submissionsRepository.getExamUploadPayload(submission, user)
        },
        idExtractor = { it.id },
        dbIdExtractor = { it._id },
        filterGuests = true,
        guestUserIdExtractor = { it.userId }
    )

    val Submissions = UploadConfig(
        modelClass = Submission::class,
        endpoint = "submissions",
        fetchPendingItems = { submissionsRepository.getPendingSubmissionsForUpload() },
        serializer = UploadSerializer.Async { submission ->
            val user = submission.userId?.let { userRepository.getUserById(it) }
            submissionsRepository.serializeSubmission(submission, sharedPrefManager.getPlanetCode(), sharedPrefManager.getParentCode(), user)
        },
        idExtractor = { it.id },
        dbIdExtractor = { it._id },
        additionalUpdates = { submission, _ ->
            submission.isUpdated = false
        }
    )

    // Migrated to Room: uses the database-agnostic RoomUploadConfig path in UploadCoordinator.
    // The private-resource team-link creation moves into the repository's markResourceUploaded.
    fun getResourcesConfig(user: UserEntity?): RoomUploadConfig<MyLibrary> {
        return RoomUploadConfig(
            endpoint = "resources",
            modelClassName = "MyLibrary",
            fetchPendingItems = { resourcesRepository.getPendingResourceUploads() },
            serializer = UploadSerializer.Simple { library ->
                MyLibrary.serialize(library, user)
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
        serializer = UploadSerializer.Simple { rating -> org.ole.planet.myplanet.model.Rating.serializeRating(rating, customDeviceName) },
        idExtractor = { it.id },
        dbIdExtractor = { it._id }, // Enables POST/PUT logic
        markUploaded = { results ->
            results.filter { result -> !ratingsRepository.markRatingUploaded(result.localId) }
        }
    )
}
