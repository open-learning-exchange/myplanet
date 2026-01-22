package org.ole.planet.myplanet.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.di.WorkerDependenciesEntryPoint
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.utils.FileUtils
import java.io.File

class FreeSpaceWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private lateinit var resourcesRepository: ResourcesRepository
    private var totalFiles = 0
    private var deletedFiles = 0
    private var freedBytes = 0L

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, WorkerDependenciesEntryPoint::class.java)
        resourcesRepository = entryPoint.resourcesRepository()

        return try {
            setProgress(workDataOf("progress" to 0, "status" to "Starting cleanup..."))

            // Mark all resources as offline = false in database
            resourcesRepository.markAllResourcesOffline(false)

            val rootFile = File(FileUtils.getOlePath(applicationContext))

            // First count total files for progress calculation (optional, but good for percentage)
            // For now, we will just report deleted count and size.

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
