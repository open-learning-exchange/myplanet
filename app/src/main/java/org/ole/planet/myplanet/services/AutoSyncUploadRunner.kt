package org.ole.planet.myplanet.services

import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.atomic.AtomicReference
import org.ole.planet.myplanet.callback.OnSuccessListener

class AutoSyncUploadRunner @Inject constructor(
    private val uploadManager: UploadManager
) {
    suspend fun runAll(listener: OnSuccessListener): Throwable? {
        return supervisorScope {
            val semaphore = Semaphore(3)
            val firstError = AtomicReference<Throwable?>(null)

            fun recordFailure(e: Throwable) {
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

            val groupB = async {
                runGroup {
                    uploadManager.uploadUserActivities(listener)
                    uploadManager.uploadTeams()
                    uploadManager.uploadTeamTask()
                    uploadManager.uploadMeetups()
                }
            }

            val groupC = async {
                runGroup {
                    uploadManager.uploadResourceActivities("")
                    uploadManager.uploadRating()
                    uploadManager.uploadResource(listener)
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
                groupC,
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
}
