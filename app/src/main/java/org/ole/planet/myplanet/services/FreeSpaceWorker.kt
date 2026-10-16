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
import kotlinx.coroutines.CancellationException
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

    internal var elapsedMs: () -> Long = { System.nanoTime() / 1_000_000 }

    private var lastEmitTimeMs = -PROGRESS_INTERVAL_MS
    private var lastEmittedDeletedFiles = 0
    private var lastEmittedFreedBytes = 0L

    override suspend fun doWork(): Result {
        return try {
            setProgress(workDataOf("progress" to 0, "status" to "Starting cleanup..."))

            val rootFile = File(FileUtils.getOlePath(applicationContext))

            withContext(dispatcherProvider.io) {
                val children = rootFile.listFiles() ?: return@withContext
                val clearedResourceIds = mutableSetOf<String>()
                for (child in children) {
                    if (isStopped) break
                    val wasDirectory = child.isDirectory
                    if (wasDirectory && child.name == CV_DIR_NAME) continue
                    val deletedBefore = deletedFiles
                    deleteRecursive(child)
                    if (wasDirectory && (deletedFiles > deletedBefore || !child.exists())) {
                        clearedResourceIds.add(child.name)
                    }
                    if (clearedResourceIds.size >= MARK_BATCH_SIZE) {
                        resourcesRepository.markResourcesAsNotOffline(clearedResourceIds)
                        clearedResourceIds.clear()
                    }
                }
                if (clearedResourceIds.isNotEmpty()) {
                    resourcesRepository.markResourcesAsNotOffline(clearedResourceIds)
                }
            }

            if (!isStopped && (deletedFiles != lastEmittedDeletedFiles || freedBytes != lastEmittedFreedBytes)) {
                setProgress(workDataOf(
                    "deletedFiles" to deletedFiles,
                    "freedBytes" to freedBytes
                ))
                lastEmittedDeletedFiles = deletedFiles
                lastEmittedFreedBytes = freedBytes
            }

            Result.success(workDataOf("deletedFiles" to deletedFiles, "freedBytes" to freedBytes))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error in FreeSpaceWorker", e)
            Result.failure()
        }
    }

    private suspend fun deleteRecursive(fileOrDirectory: File) {
        fileOrDirectory.walkBottomUp().forEach { file ->
            if (isStopped) return

            val length = file.length()
            if (file.delete()) {
                deletedFiles++
                freedBytes += length

                val now = elapsedMs()
                if (now - lastEmitTimeMs >= PROGRESS_INTERVAL_MS) {
                    setProgress(workDataOf(
                        "deletedFiles" to deletedFiles,
                        "freedBytes" to freedBytes
                    ))
                    lastEmitTimeMs = now
                    lastEmittedDeletedFiles = deletedFiles
                    lastEmittedFreedBytes = freedBytes
                }
            }
        }
    }

    companion object {
        private const val TAG = "FreeSpaceWorker"
        private const val CV_DIR_NAME = "cv"
        private const val MARK_BATCH_SIZE = 25
        private const val PROGRESS_INTERVAL_MS = 500L
    }
}
