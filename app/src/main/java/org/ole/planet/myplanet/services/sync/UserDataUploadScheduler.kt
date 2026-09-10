package org.ole.planet.myplanet.services.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.repository.SyncUiState
import org.ole.planet.myplanet.services.UserDataWorker

interface UserDataUploadScheduler {
    fun enqueueUserDataUpload(uniqueWorkName: String, uploadType: String): Flow<SyncUiState>
}

@Singleton
class UserDataUploadSchedulerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UserDataUploadScheduler {

    override fun enqueueUserDataUpload(uniqueWorkName: String, uploadType: String): Flow<SyncUiState> {
        val workRequest = OneTimeWorkRequest.Builder(UserDataWorker::class.java)
            .setInputData(workDataOf(UserDataWorker.KEY_UPLOAD_TYPE to uploadType))
            .build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        return workManager.getWorkInfoByIdFlow(workRequest.id).map { workInfo ->
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
            WorkInfo.State.CANCELLED -> SyncUiState.Error("Upload cancelled")
            WorkInfo.State.RUNNING -> SyncUiState.Loading
            else -> SyncUiState.Idle
        }
    }
}
