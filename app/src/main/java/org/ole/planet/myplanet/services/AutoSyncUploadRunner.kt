package org.ole.planet.myplanet.services

import android.util.Log
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.ole.planet.myplanet.callback.OnSuccessListener

class AutoSyncUploadRunner @Inject constructor(
    private val uploadManager: UploadManager
) {
    suspend fun runAll(listener: OnSuccessListener): Throwable? {
        return supervisorScope {
            val semaphore = Semaphore(MAX_CONCURRENT_GROUPS)
            val firstError = AtomicReference<Throwable?>(null)

            fun recordFailure(e: Throwable) {
                Log.e("AutoSyncUploadRunner", "error: ${e.message}", e)
                firstError.compareAndSet(null, e)
            }

            suspend fun runGroup(block: suspend () -> Unit) {
                semaphore.withPermit {
                    try {
                        block()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        recordFailure(e)
                    }
                }
            }

            val groupA = async {
                runGroup {
                    uploadManager.uploadExamResult(listener)
                    uploadManager.uploadAdoptedSurveys()
                    uploadManager.uploadSubmissions()
                }
            }

            // Resources go before teams: markResourceUploaded creates team links for
            // private resources, and uploadTeams must see them in the same sync.
            val groupB = async {
                runGroup {
                    uploadManager.uploadResourceActivities("")
                    uploadManager.uploadRating()
                    uploadManager.uploadResource(listener)
                    uploadManager.uploadUserActivities(listener)
                    uploadManager.uploadTeams()
                    uploadManager.uploadTeamTask()
                    uploadManager.uploadMeetups()
                }
            }

            val feedback = async {
                runGroup {
                    uploadManager.uploadFeedback()
                }
            }

            val achievement = async {
                runGroup {
                    uploadManager.uploadAchievement()
                }
            }

            val courseActivities = async {
                runGroup {
                    uploadManager.uploadCourseActivities()
                }
            }

            val searchActivity = async {
                runGroup {
                    uploadManager.uploadSearchActivity()
                }
            }

            val news = async {
                runGroup {
                    uploadManager.uploadNews()
                }
            }

            val crashLog = async {
                runGroup {
                    uploadManager.uploadCrashLog()
                }
            }

            listOf(
                groupA,
                groupB,
                feedback,
                achievement,
                courseActivities,
                searchActivity,
                news,
                crashLog
            ).awaitAll()

            uploadManager.uploadActivities(null)

            firstError.get()
        }
    }

    companion object {
        // Each group can fan out to UploadCoordinator, PhotoUploader or
        // ActivitiesRepositoryImpl.uploadActivities, each capped at 6 requests,
        // so 2 groups keep the worst case at ~12 concurrent POSTs to the server.
        const val MAX_CONCURRENT_GROUPS = 2
    }
}
