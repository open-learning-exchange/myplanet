package org.ole.planet.myplanet.services

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.ole.planet.myplanet.callback.OnSuccessListener

@HiltWorker
class UserDataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val uploadManager: UploadManager,
    private val uploadToShelfService: UploadToShelfService
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        val uploadType = inputData.getString("uploadType") ?: ""

        try {
            if (uploadType == "login") {
                val deferred = CompletableDeferred<String?>()
                uploadManager.uploadUserActivities(object : OnSuccessListener {
                    override fun onSuccess(success: String?) {
                        deferred.complete(success)
                    }
                })
                val successMsg = deferred.await()
                val outputData = Data.Builder().putString("successMessage", successMsg).build()
                return@coroutineScope Result.success(outputData)
            } else if (uploadType == "bulk") {
                uploadManager.uploadAchievement()
                uploadManager.uploadNews()
                uploadManager.uploadResourceActivities("")
                uploadManager.uploadCourseActivities()
                uploadManager.uploadSearchActivity()
                uploadManager.uploadRating()
                uploadManager.uploadTeamTask()
                uploadManager.uploadMeetups()
                uploadManager.uploadAdoptedSurveys()
                uploadManager.uploadSubmissions()
                uploadManager.uploadCrashLog()

                val tasks = listOf(
                    async {
                        val d = CompletableDeferred<Unit>()
                        uploadToShelfService.uploadUserData {
                            uploadToShelfService.uploadHealth()
                            d.complete(Unit)
                        }
                        d.await()
                    },
                    async {
                        val d = CompletableDeferred<Unit>()
                        uploadManager.uploadUserActivities(object : OnSuccessListener {
                            override fun onSuccess(success: String?) {
                                d.complete(Unit)
                            }
                        })
                        d.await()
                    },
                    async {
                        val d = CompletableDeferred<Unit>()
                        uploadManager.uploadExamResult(object : OnSuccessListener {
                            override fun onSuccess(success: String?) {
                                d.complete(Unit)
                            }
                        })
                        d.await()
                    },
                    async { uploadManager.uploadFeedback() },
                    async {
                        val d = CompletableDeferred<Unit>()
                        uploadManager.uploadResource(object : OnSuccessListener {
                            override fun onSuccess(success: String?) {
                                d.complete(Unit)
                            }
                        })
                        d.await()
                        uploadManager.uploadTeams()
                    },
                    async {
                        val d = CompletableDeferred<Unit>()
                        uploadManager.uploadSubmitPhotos(object : OnSuccessListener {
                            override fun onSuccess(success: String?) {
                                d.complete(Unit)
                            }
                        })
                        d.await()
                    },
                    async {
                        val d = CompletableDeferred<Unit>()
                        uploadManager.uploadActivities(object : OnSuccessListener {
                            override fun onSuccess(success: String?) {
                                d.complete(Unit)
                            }
                        })
                        d.await()
                    }
                )
                tasks.awaitAll()
                return@coroutineScope Result.success()
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("UserDataWorker", "Error uploading user data", e)
            Result.failure()
        }
    }
}
