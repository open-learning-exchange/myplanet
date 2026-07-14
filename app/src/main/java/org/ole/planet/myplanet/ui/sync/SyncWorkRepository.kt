package org.ole.planet.myplanet.ui.sync

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.ole.planet.myplanet.services.UserDataWorker

@Singleton
class SyncWorkRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun uploadLoginData(): LiveData<SyncUiState> {
        val workRequest = OneTimeWorkRequest.Builder(UserDataWorker::class.java)
            .setInputData(workDataOf(UserDataWorker.KEY_UPLOAD_TYPE to UserDataWorker.UPLOAD_TYPE_LOGIN))
            .build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(
            "UploadUserData_Login",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        return workManager.getWorkInfoByIdLiveData(workRequest.id).map { workInfo ->
            mapWorkInfoToState(workInfo)
        }
    }

    fun uploadBulkData(): LiveData<SyncUiState> {
        val workRequest = OneTimeWorkRequest.Builder(UserDataWorker::class.java)
            .setInputData(workDataOf(UserDataWorker.KEY_UPLOAD_TYPE to UserDataWorker.UPLOAD_TYPE_BULK))
            .build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(
            "UploadUserData_Bulk",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        return workManager.getWorkInfoByIdLiveData(workRequest.id).map { workInfo ->
            mapWorkInfoToState(workInfo)
        }
    }

    private fun mapWorkInfoToState(workInfo: WorkInfo?): SyncUiState {
        return when (workInfo?.state) {
            WorkInfo.State.SUCCEEDED -> {
                val message = workInfo.outputData.getString(UserDataWorker.KEY_SUCCESS_MESSAGE)
                SyncUiState.Success(message)
            }
            WorkInfo.State.FAILED -> SyncUiState.Error("Upload failed")
            WorkInfo.State.RUNNING -> SyncUiState.Loading
            else -> SyncUiState.Idle
        }
    }
}

sealed class SyncUiState {
    object Idle : SyncUiState()
    object Loading : SyncUiState()
    data class Success(val message: String?) : SyncUiState()
    data class Error(val message: String?) : SyncUiState()
}
