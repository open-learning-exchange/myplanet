package org.ole.planet.myplanet.services

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.data.api.ApiInterface
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.di.ApplicationScope
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.repository.UserSyncRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.SecurePrefs
import org.ole.planet.myplanet.utils.UrlUtils

@Singleton
class UploadToShelfService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dbService: DatabaseService,
    @AppPreferences private val sharedPreferences: SharedPreferences,
    private val sharedPrefManager: SharedPrefManager,
    private val userRepository: UserRepository,
    private val userSyncRepository: UserSyncRepository,
    private val healthRepository: HealthRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val apiInterface: ApiInterface
) {

    fun uploadUserData(listener: OnSuccessListener) {
        appScope.launch(dispatcherProvider.io) {
            try {
                val userModels = userRepository.getPendingSyncUsers(100)

                if (userModels.isEmpty()) return@launch

                val password = SecurePrefs.getPassword(context, sharedPreferences) ?: ""
                userModels.forEach { model ->
                    try {
                        val header = "Basic ${Base64.encodeToString(("${model.name}:${password}").toByteArray(), Base64.NO_WRAP)}"
                        val userExists = userSyncRepository.checkIfUserExists(header, model)

                        if (!userExists) {
                            userSyncRepository.uploadNewUser(model) { userId: String, examinationId: String -> healthRepository.updateExaminationUserId(userId, examinationId) }
                        } else if (model.isUpdated) {
                            userSyncRepository.updateExistingUser(header, model)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                uploadToShelf(object : OnSuccessListener {
                    override fun onSuccess(success: String?) {
                        listener.onSuccess(success)
                    }
                })
            } catch (e: Exception) {
                withContext(dispatcherProvider.main) {
                    listener.onSuccess("Error during user data sync: ${e.localizedMessage}")
                }
            }
        }
    }

    fun uploadSingleUserData(userName: String?, listener: OnSuccessListener) {
        appScope.launch(dispatcherProvider.io) {
            try {
                val userModel = if (userName != null) userRepository.getUserByName(userName) else null

                if (userModel != null) {
                    try {
                        val password = SecurePrefs.getPassword(context, sharedPreferences) ?: ""
                        val header = "Basic ${Base64.encodeToString(("${userModel.name}:${password}").toByteArray(), Base64.NO_WRAP)}"

                        val userExists = userSyncRepository.checkIfUserExists(header, userModel)

                        if (!userExists) {
                            userSyncRepository.uploadNewUser(userModel) { userId: String, examinationId: String -> healthRepository.updateExaminationUserId(userId, examinationId) }
                        } else if (userModel.isUpdated) {
                            userSyncRepository.updateExistingUser(header, userModel)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                uploadSingleUserToShelf(userName, listener)
            } catch (e: Exception) {
                withContext(dispatcherProvider.main) {
                    listener.onSuccess("Error during user data sync: ${e.localizedMessage}")
                }
            }
        }
    }

    fun uploadHealth() {
        appScope.launch(dispatcherProvider.io) {
            val myHealths = healthRepository.getUpdatedHealthExaminations()
            val uploadedHealths = healthRepository.uploadHealthData(myHealths)
            healthRepository.markHealthExaminationsUploaded(uploadedHealths)
        }
    }

    fun uploadSingleUserHealth(userId: String?, listener: OnSuccessListener?) {
        appScope.launch(dispatcherProvider.io) {
            try {
                if (userId.isNullOrEmpty()) return@launch

                val myHealths = healthRepository.getUpdatedHealthForUser(userId)
                val uploadedHealths = healthRepository.uploadHealthData(myHealths)
                healthRepository.markHealthExaminationsUploaded(uploadedHealths)

                withContext(dispatcherProvider.main) {
                    listener?.onSuccess("Health data for user $userId uploaded successfully")
                }
            } catch (e: Throwable) {
                withContext(dispatcherProvider.main) {
                    listener?.onSuccess("Error uploading health data for user $userId: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun uploadToShelf(listener: OnSuccessListener) {
        appScope.launch(dispatcherProvider.io) {
            val unmanagedUsers = userRepository.getSyncedUsers()

            if (unmanagedUsers.isEmpty()) {
                withContext(dispatcherProvider.main) {
                    listener.onSuccess("Sync with server completed successfully")
                }
                return@launch
            }

            try {
                val semaphore = Semaphore(5)
                supervisorScope {
                    unmanagedUsers.map { model ->
                        async {
                            semaphore.withPermit {
                                try {
                                    userSyncRepository.uploadShelfData(model)
                                } catch (e: Throwable) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }.awaitAll()
                }
                withContext(dispatcherProvider.main) {
                    listener.onSuccess("Sync with server completed successfully")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(dispatcherProvider.main) {
                    listener.onSuccess("Unable to update documents: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun uploadSingleUserToShelf(userName: String?, listener: OnSuccessListener) {
        appScope.launch(dispatcherProvider.io) {
            try {
                val model = userName?.let { userRepository.getSyncedUserByName(it) }

                if (model != null) {
                    userSyncRepository.uploadShelfData(model)
                }
                withContext(dispatcherProvider.main) {
                    listener.onSuccess("Single user shelf sync completed successfully")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(dispatcherProvider.main) {
                    listener.onSuccess("Unable to update document: ${e.localizedMessage}")
                }
            }
        }
    }

}
