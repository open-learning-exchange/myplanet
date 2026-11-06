package org.ole.planet.myplanet.utils

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * The files a stopped or interrupted download leaves behind so it can resume later: `<name>.tmp`
 * plus its `.tmp.etag` validator and, when recorded, its `.tmp.size`. They hold real bytes, so they
 * are shown in Settings → Storage and cleared once they are too old to be worth resuming.
 */
object PartialDownloads {

    /** Partial files untouched for this long are deleted on app start. */
    val STALE_AFTER_MILLIS: Long = TimeUnit.DAYS.toMillis(30)

    /** A partial written to this recently may be downloading right now, so clearing leaves it alone. */
    val ACTIVE_WINDOW_MILLIS: Long = TimeUnit.MINUTES.toMillis(2)

    private const val PARTIAL_SUFFIX = ".tmp"
    private val SIDECAR_SUFFIXES = listOf(".tmp.etag", ".tmp.size")

    data class Summary(val bytes: Long, val count: Int)

    fun isPartialFile(name: String): Boolean =
        name.endsWith(PARTIAL_SUFFIX) || SIDECAR_SUFFIXES.any { name.endsWith(it) }

    /** Bytes held by partial files under [oleDir], and how many downloads they belong to. */
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

    /**
     * Deletes every partial download under [oleDir] last written more than [olderThanMillis] before
     * [now], with its sidecar files, plus sidecars whose partial file is already gone.
     * Returns the number of bytes freed.
     */
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
