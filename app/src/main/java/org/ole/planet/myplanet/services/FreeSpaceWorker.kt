package org.ole.planet.myplanet.services

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.FileUtils

@HiltWorker
class FreeSpaceWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val resourcesRepository: ResourcesRepository,
    private val dispatcherProvider: DispatcherProvider
) : CoroutineWorker(context, workerParams) {

    private var deletedFiles = 0
    private var freedBytes = 0L

    override suspend fun doWork(): Result {
        return try {
            setProgress(workDataOf("progress" to 0, "status" to "Starting cleanup..."))

            val rootFile = File(FileUtils.getOlePath(applicationContext))

            withContext(dispatcherProvider.io) {
                // Files are deleted before their Realm flags are cleared, one resource
                // at a time, so cancelling or failing mid-run never leaves resources
                // marked as not-downloaded while their files still exist on disk.
                val children = rootFile.listFiles() ?: return@withContext
                val clearedResourceIds = mutableSetOf<String>()
                for (child in children) {
                    if (isStopped) break
                    val wasDirectory = child.isDirectory
                    // cv/ holds achievement attachments that may not be uploaded yet
                    if (wasDirectory && child.name == CV_DIR_NAME) continue
                    val deletedBefore = deletedFiles
                    deleteRecursive(child)
                    if (wasDirectory && (deletedFiles > deletedBefore || !child.exists())) {
                        clearedResourceIds.add(child.name)
                    }
                    if (clearedResourceIds.size >= MARK_BATCH_SIZE) {
                        resourcesRepository.markResourcesAsNotOffline(clearedResourceIds.toSet())
                        clearedResourceIds.clear()
                    }
                }
                if (clearedResourceIds.isNotEmpty()) {
                    resourcesRepository.markResourcesAsNotOffline(clearedResourceIds)
                }
            }

            Result.success(workDataOf("deletedFiles" to deletedFiles, "freedBytes" to freedBytes))
        } catch (e: Exception) {
            Log.e(TAG, "Error in FreeSpaceWorker", e)
            Result.failure()
        }
    }

    private suspend fun deleteRecursive(fileOrDirectory: File) {
        if (isStopped) return

        if (fileOrDirectory.isDirectory) {
            val children = fileOrDirectory.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteRecursive(child)
                }
            }
        }

        if (fileOrDirectory.exists()) {
            val length = fileOrDirectory.length()
            if (fileOrDirectory.delete()) {
                deletedFiles++
                freedBytes += length

                // Report progress every 10 files or so to avoid spamming updates
                if (deletedFiles % 10 == 0) {
                     setProgress(workDataOf(
                         "deletedFiles" to deletedFiles,
                         "freedBytes" to freedBytes
                     ))
                }
            }
        }
    }

    companion object {
        private const val TAG = "FreeSpaceWorker"
        private const val CV_DIR_NAME = "cv"
        private const val MARK_BATCH_SIZE = 25
    }
}
