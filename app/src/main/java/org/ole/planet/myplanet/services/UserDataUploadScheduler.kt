package org.ole.planet.myplanet.services

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.ole.planet.myplanet.repository.SyncUiState

interface UserDataUploadScheduler {
    fun enqueueUserDataUpload(uniqueWorkName: String, uploadType: String): Flow<SyncUiState>
}

@Singleton
class WorkManagerUserDataUploadScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : UserDataUploadScheduler {

    override fun enqueueUserDataUpload(uniqueWorkName: String, uploadType: String): Flow<SyncUiState> {
        val workRequest = OneTimeWorkRequest.Builder(UserDataWorker::class.java)
            .setInputData(workDataOf(UserDataWorker.KEY_UPLOAD_TYPE to uploadType))
            .build()
        val workManager = WorkManager.getInstance(context)
        val operation = workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP,
            workRequest
        )
        return flow {
            operation.await()
            emitAll(
                workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName).map { workInfos ->
                    mapWorkInfoToState(workInfos.firstOrNull())
                }
            )
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

@Module
@InstallIn(SingletonComponent::class)
abstract class UserDataUploadSchedulerModule {
    @Binds
    @Singleton
    abstract fun bindUserDataUploadScheduler(
        impl: WorkManagerUserDataUploadScheduler
    ): UserDataUploadScheduler
}
