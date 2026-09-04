package org.ole.planet.myplanet.utils

import android.app.PendingIntent
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

object FileUtils {
    private const val TAG = "FileUtils"

    @Volatile private var cachedExternalFilesDir: File? = null

    fun warmUp(context: Context) {
        if (cachedExternalFilesDir == null) {
            cachedExternalFilesDir = context.applicationContext.getExternalFilesDir(null)
        }
    }

    fun getExternalFilesDir(context: Context): File? {
        return cachedExternalFilesDir ?: context.getExternalFilesDir(null).also { cachedExternalFilesDir = it }
    }

    fun getOlePath(context: Context): String {
        return getExternalFilesDir(context)?.let { "$it/ole/" } ?: ""
    }

    fun getLibraryFile(externalFilesDir: File, libraryId: String, address: String): File {
        return File(externalFilesDir, "ole/$libraryId/$address")
    }

    private fun resolveFilePath(context: Context, folder: String, filename: String): File {
        val baseDirectory = File(getExternalFilesDir(context), folder)

        return if (filename.contains("/")) {
            val subDirPath = filename.substring(0, filename.lastIndexOf('/'))
            val fullDir = File(baseDirectory, subDirPath)
            val actualFilename = filename.substring(filename.lastIndexOf('/') + 1)
            File(fullDir, actualFilename)
        } else {
            File(baseDirectory, filename)
        }
    }

    fun getSDPathFromUrl(context: Context, url: String?): File {
        val segments = parseUrlSegments(url)
        return resolveFilePath(context, "/ole/${getIdFromSegments(segments)}", getResourceRelativePathFromSegments(segments))
    }

    fun getResourceRelativePathFromUrl(url: String?): String {
        return getResourceRelativePathFromSegments(parseUrlSegments(url))
    }

    private fun parseUrlSegments(url: String?): List<String>? {
        return try {
            url?.toUri()?.pathSegments
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse path segments from url", e)
            null
        }
    }

    private fun getIdFromSegments(segments: List<String>?): String {
        if (segments == null) return ""
        val idx = segments.indexOf("resources")
        return if (idx != -1 && idx + 1 < segments.size) segments[idx + 1] else ""
    }

    private fun getResourceRelativePathFromSegments(segments: List<String>?): String {
        if (segments == null) return ""
        return try {
            val idx = segments.indexOf("resources")
            if (idx != -1 && idx + 2 < segments.size) {
                segments.subList(idx + 2, segments.size).joinToString("/") {
                    URLDecoder.decode(it, StandardCharsets.UTF_8.name())
                }
            } else {
                getFileNameFromSegments(segments)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve resource relative path from url", e)
            getFileNameFromSegments(segments)
        }
    }

    private fun getFileNameFromSegments(segments: List<String>?): String {
        val lastSegment = segments?.lastOrNull() ?: return ""
        return try {
            URLDecoder.decode(lastSegment, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode file name from url segment", e)
            ""
        }
    }

    /**
     * Resolves an HTML resource's entry file (e.g. from `openWhichFile`, which may nest the
     * entry point in a subfolder like `sudoku/index.html`) against its download directory,
     * defaulting to `index.html` when unset and refusing to resolve outside [baseDirectory].
     */
    fun resolveHtmlEntryFile(baseDirectory: File, relativePath: String?): File? {
        val candidate = relativePath?.takeIf { it.isNotBlank() } ?: "index.html"
        if (candidate.startsWith("/") || candidate.startsWith("\\") || candidate.contains("..")) {
            return null
        }
        return try {
            val canonicalBase = baseDirectory.canonicalFile
            val resolved = File(canonicalBase, candidate).canonicalFile
            if (resolved.path == canonicalBase.path || resolved.path.startsWith(canonicalBase.path + File.separator)) {
                resolved
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve HTML entry file", e)
            null
        }
    }

    private val previewImageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp")
    private val previewImageNameHints = listOf("cover", "thumbnail", "thumb", "screenshot", "poster")

    fun findHtmlCoverImage(resourceDir: File): File? {
        if (!resourceDir.isDirectory) return null
        var largestFile: File? = null
        var maxBytes: Long = -1L

        for (file in resourceDir.walkTopDown().maxDepth(4)) {
            if (!file.isFile) continue
            if (file.extension.lowercase() !in previewImageExtensions) continue

            val nameLower = file.nameWithoutExtension.lowercase()
            if (previewImageNameHints.any { nameLower.contains(it) }) {
                return file
            }

            val length = file.length()
            if (largestFile == null || length > maxBytes) {
                largestFile = file
                maxBytes = length
            }
        }

        return largestFile
    }

    fun checkFileExist(context: Context, url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val f = getSDPathFromUrl(context, url)
        return f.exists() && f.length() > 0
    }

    fun getFileNameFromLocalAddress(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return path.substringAfterLast('/')
    }

    fun getFileNameFromUrl(url: String?): String {
        return try {
            val segments = url?.toUri()?.pathSegments
            getFileNameFromSegments(segments)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract file name from url", e)
            ""
        }
    }

    fun getIdFromUrl(url: String?): String {
        return try {
            val segments = url?.toUri()?.pathSegments
            getIdFromSegments(segments)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract resource id from url", e)
            ""
        }
    }

    fun getFileExtension(address: String?): String {
        return address?.let { File(it).extension.lowercase() } ?: ""
    }

    fun installApk(activity: Context, file: String?) {
        if (file?.endsWith("apk") != true) return
        val toInstall = File(file)
        if (!toInstall.exists()) return
        try {
            val packageInstaller = activity.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            addApkToInstallSession(toInstall, session)
            val intent = Intent(activity, activity.javaClass)
            val pendingIntent = PendingIntent.getActivity(activity, 0, intent,
                PendingIntent.FLAG_IMMUTABLE)
            val intentSender = pendingIntent.intentSender
            session.commit(intentSender)
            session.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install APK", e)
        }
    }

    @Throws(IOException::class)
    private fun addApkToInstallSession(apkFile: File, session: PackageInstaller.Session) {
        session.openWrite("my_app_session", 0, -1).use { output ->
            apkFile.inputStream().use { input ->
                input.copyTo(output)
                session.fsync(output)
            }
        }
    }


    fun getMimeType(fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        val ext = MimeTypeMap.getFileExtensionFromUrl(fileName)?.lowercase(Locale.getDefault())
        return if (!ext.isNullOrBlank()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        } else {
            null
        }
    }

    fun getDisplayName(context: Context, uri: Uri, timeProvider: TimeProvider): String {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
        }
        return name ?: uri.lastPathSegment ?: "image_${timeProvider.now()}.jpg"
    }

    fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }

    fun copyUriToFile(context: Context, sourceUri: Uri, destinationFile: File) {
        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

    fun resolveUriToPath(context: Context, uri: Uri?): String? {
        uri ?: return null
        if (uri.scheme == "file") return uri.path
        return try {
            val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex >= 0) cursor.getString(columnIndex) else null
                } else null
            }
            val destinationFile = File(context.cacheDir, displayName ?: UUID.randomUUID().toString())
            copyUriToFile(context, uri, destinationFile)
            destinationFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @Throws(Exception::class)
    fun getStringFromFile(fl: File?): String {
        return fl?.inputStream()?.bufferedReader()?.use { it.readText() } ?: ""
    }

    fun openOleFolder(context: Context): Intent {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        val uri = getOlePath(context).toUri()
        intent.setDataAndType(uri, "*/*")
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        return Intent.createChooser(intent, "Open folder")
    }

    fun externalMemoryAvailable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    val availableExternalMemorySize: Long
        /**
         * Find space left in the external memory.
         */
        get() =// Not the best way to check, shows internal memory
            // when there is not external memory mounted
            if (externalMemoryAvailable()) {
                val path = Environment.getExternalStorageDirectory()
                val stat = StatFs(path.path)
                val blockSize = stat.blockSizeLong
                val availableBlocks = stat.availableBlocksLong
                availableBlocks * blockSize
            } else {
                0
            }

    /**
     * Coverts Bytes to KB/MB/GB and changes magnitude accordingly.
     *
     * @param size
     * @return A string with size followed by an appropriate suffix
     */
    fun formatSize(context: Context, size: Long): String {
        return Formatter.formatFileSize(context, size)
    }

    fun totalMemoryCapacity(context: Context): Long = getStorageStats(context).first

    fun totalAvailableMemory(context: Context): Long = getStorageStats(context).second

    fun totalAvailableMemoryRatio(context: Context): Long {
        val (total, available) = getStorageStats(context)
        return (available.toDouble() / total.toDouble() * 100).roundToLong()
    }

    fun availableOverTotalMemoryFormattedString(context: Context): String {
        val (total, available) = getStorageStats(context)
        return formatSize(context, available) + "/" + formatSize(context, total)
    }

    private fun getStorageStats(context: Context): Pair<Long, Long> {
        val storageStatsManager =
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolume = storageManager.primaryStorageVolume

        // Get UUID of the internal storage
        val uuid =
            storageVolume.uuid?.let { UUID.fromString(it) } ?: StorageManager.UUID_DEFAULT

        // Get the total bytes and available bytes
        val totalBytes = storageStatsManager.getTotalBytes(uuid)
        val availableBytes = storageStatsManager.getFreeBytes(uuid)

        return Pair(totalBytes, availableBytes)
    }
    fun nameWithoutExtension(fileName: String?): String? {
        return fileName?.let { File(it).name.takeIf { name -> name.isNotEmpty() } }?.substringBeforeLast('.')
    }

    fun openPdf(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open PDF", e)
            Toast.makeText(context, "Could not open PDF. File saved at: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }
}
