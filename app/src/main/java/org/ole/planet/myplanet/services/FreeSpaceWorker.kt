package org.ole.planet.myplanet.services

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.FileUtils

@HiltWorker
class FreeSpaceWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val resourcesRepository: ResourcesRepository
) : CoroutineWorker(context, workerParams) {

    private var deletedFiles = 0
    private var freedBytes = 0L

    override suspend fun doWork(): Result {
        return try {
            setProgress(workDataOf("progress" to 0, "status" to "Starting cleanup..."))

            // Mark all resources as offline = false in database
            resourcesRepository.markAllResourcesOffline(false)

            val rootFile = File(FileUtils.getOlePath(applicationContext))

            withContext(Dispatchers.IO) {
                deleteRecursive(rootFile)
            }

            Result.success(workDataOf("deletedFiles" to deletedFiles, "freedBytes" to freedBytes))
        } catch (e: Exception) {
            e.printStackTrace()
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
}
