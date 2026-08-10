package org.ole.planet.myplanet.repository

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
import org.ole.planet.myplanet.services.UserDataWorker
import org.ole.planet.myplanet.services.sync.TransactionSyncManager

@Singleton
class SyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionSyncManager: dagger.Lazy<TransactionSyncManager>
) {
    suspend fun syncDashboardKeyId(role: String?): SyncUiState {
        return try {
            transactionSyncManager.get().syncDashboardKeyId(role)
            SyncUiState.Success(null)
        } catch (e: Exception) {
            SyncUiState.Error(e.message)
        }
    }

    fun uploadLoginData(): Flow<SyncUiState> {
        val workRequest = OneTimeWorkRequest.Builder(UserDataWorker::class.java)
            .setInputData(workDataOf(UserDataWorker.KEY_UPLOAD_TYPE to UserDataWorker.UPLOAD_TYPE_LOGIN))
            .build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(
            "UploadUserData_Login",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        return workManager.getWorkInfoByIdFlow(workRequest.id).map { workInfo ->
            mapWorkInfoToState(workInfo)
        }
    }

    fun uploadBulkData(): Flow<SyncUiState> {
        val workRequest = OneTimeWorkRequest.Builder(UserDataWorker::class.java)
            .setInputData(workDataOf(UserDataWorker.KEY_UPLOAD_TYPE to UserDataWorker.UPLOAD_TYPE_BULK))
            .build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(
            "UploadUserData_Bulk",
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

sealed class SyncUiState {
    object Idle : SyncUiState()
    object Loading : SyncUiState()
    data class Success(val message: String?) : SyncUiState()
    data class Error(val message: String?) : SyncUiState()
}
