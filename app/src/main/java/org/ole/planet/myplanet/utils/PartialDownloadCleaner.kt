package org.ole.planet.myplanet.utils

import java.io.File
import java.util.concurrent.TimeUnit

object PartialDownloadCleaner {
    val STALE_AFTER_MILLIS: Long = TimeUnit.DAYS.toMillis(30)
    val ACTIVE_WINDOW_MILLIS: Long = TimeUnit.MINUTES.toMillis(2)
    private const val PARTIAL_SUFFIX = ".tmp"
    private val SIDECAR_SUFFIXES = listOf(".tmp.etag", ".tmp.size")

    data class Summary(val bytes: Long, val count: Int)

    fun isPartialFile(name: String): Boolean =
        name.endsWith(PARTIAL_SUFFIX) || SIDECAR_SUFFIXES.any { name.endsWith(it) }

    fun summarize(oleDir: File): Summary {
        if (!oleDir.isDirectory) return Summary(0L, 0)
        var bytes = 0L
        var count = 0
        oleDir.walkTopDown().filter { it.isFile && isPartialFile(it.name) }.forEach { file ->
            bytes += file.length()
            if (file.name.endsWith(PARTIAL_SUFFIX)) count++
        }
        return Summary(bytes, count)
    }

    fun delete(oleDir: File, now: Long, olderThanMillis: Long): Long {
        if (!oleDir.isDirectory) return 0L
        val cutoff = now - olderThanMillis
        var freed = 0L
        oleDir.walkTopDown().filter { it.isFile && isPartialFile(it.name) }.toList().forEach { file ->
            val partial = partialFileFor(file)
            val removable = !partial.exists() || partial.lastModified() <= cutoff
            if (removable) {
                val length = file.length()
                if (file.delete()) freed += length
            }
        }
        return freed
    }

    private fun partialFileFor(file: File): File {
        val sidecar = SIDECAR_SUFFIXES.firstOrNull { file.name.endsWith(it) } ?: return file
        return File(file.parentFile, file.name.removeSuffix(sidecar) + PARTIAL_SUFFIX)
    }
}
