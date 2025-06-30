package org.ole.planet.myplanet.utilities

import android.app.PendingIntent
import android.app.usage.StorageStatsManager
import android.content.*
import android.content.pm.PackageInstaller
import android.database.Cursor
import android.net.Uri
import android.os.*
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.text.TextUtils
import android.text.format.Formatter
import org.ole.planet.myplanet.MainApplication.Companion.context
import java.io.*
import java.util.UUID
import androidx.core.net.toUri
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object FileUtils {
    @JvmStatic
    @Throws(IOException::class)
    fun fullyReadFileToBytes(f: File): ByteArray = f.readBytes()

    private fun createFilePath(folder: String, filename: String): File {
        val baseDirectory = File(context.getExternalFilesDir(null), folder)

        if (filename.contains("/")) {
            val subDirPath = filename.substring(0, filename.lastIndexOf('/'))
            val fullDir = File(baseDirectory, subDirPath)

            try {
                if (!fullDir.exists() && !fullDir.mkdirs()) {
                    throw IOException("Failed to create directory: ${fullDir.absolutePath}")
                }
            } catch (e: IOException) {
                e.printStackTrace()
                throw RuntimeException("Failed to create directory: ${fullDir.absolutePath}", e)
            }

            val actualFilename = filename.substring(filename.lastIndexOf('/') + 1)
            return File(fullDir, actualFilename)
        } else {
            try {
                if (!baseDirectory.exists() && !baseDirectory.mkdirs()) {
                    throw IOException("Failed to create directory: ${baseDirectory.absolutePath}")
                }
            } catch (e: IOException) {
                e.printStackTrace()
                throw RuntimeException("Failed to create directory: ${baseDirectory.absolutePath}", e)
            }
            return File(baseDirectory, filename)
        }
    }

    @JvmStatic
    fun getSDPathFromUrl(url: String?): File {
        return createFilePath("/ole/${getIdFromUrl(url)}", getFileNameFromUrl(url))
    }

    @JvmStatic
    fun checkFileExist(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val f = getSDPathFromUrl(url)
        return f.exists()
    }

    @JvmStatic
    fun getFileNameFromLocalAddress(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return path.substringAfterLast('/')
    }

    @JvmStatic
    fun getFileNameFromUrl(url: String?): String {
        return try {
            url?.toUri()?.lastPathSegment?.let {
                URLDecoder.decode(it, StandardCharsets.UTF_8.name())
            } ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @JvmStatic
    fun getIdFromUrl(url: String?): String {
        return try {
            url?.toUri()?.pathSegments?.let { segments ->
                val idx = segments.indexOf("resources")
                if (idx != -1 && idx + 1 < segments.size) segments[idx + 1] else ""
            } ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @JvmStatic
    fun getFileExtension(address: String?): String {
        return address?.let { File(it).extension } ?: ""
    }

    @JvmStatic
    fun installApk(activity: Context, file: String?) {
        if (!file?.endsWith("apk")!!) return
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
            e.printStackTrace()
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

    @JvmStatic
    fun copyAssets(context: Context) {
        val tiles = arrayOf("dhulikhel.mbtiles", "somalia.mbtiles")
        val assetManager = context.assets
        try {
            for (s in tiles) {
                var out: OutputStream
                val `in`: InputStream = assetManager.open(s)
                val outFile = File(Environment.getExternalStorageDirectory().toString() + "/osmdroid", s)
                out = FileOutputStream(outFile)
                copyFile(`in`, out)
                out.close()
                `in`.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun copyFile(`in`: InputStream, out: OutputStream) {
        `in`.copyTo(out)
    }

    @JvmStatic
    fun getRealPathFromURI(context: Context, contentUri: Uri?): String? {
        var cursor: Cursor? = null
        return try {
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            cursor = contentUri?.let { context.contentResolver.query(it, proj, null, null, null) }
            val columnIndex = cursor?.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor?.moveToFirst()
            cursor?.getString(columnIndex ?: 0)
        } finally {
            cursor?.close()
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun convertStreamToString(`is`: InputStream?): String {
        return `is`?.bufferedReader()?.use { it.readText() } ?: ""
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getStringFromFile(fl: File?): String {
        return fl?.inputStream()?.bufferedReader()?.use { it.readText() } ?: ""
    }

    @JvmStatic
    fun openOleFolder(): Intent {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        val uri = Utilities.SD_PATH.toUri()  // Ensure org.ole.planet.myplanet.utilities.Utilities.SD_PATH is the correct path
        intent.setDataAndType(uri, "*/*")
        return Intent.createChooser(intent, "Open folder")
    }

    @JvmStatic
    fun getImagePath(context: Context, uri: Uri?): String? {
        if (uri == null) return null
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA)
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { firstCursor ->
                if (firstCursor.moveToFirst()) {
                    val idIndex = firstCursor.getColumnIndex(MediaStore.Images.Media._ID)
                    if (idIndex >= 0) {
                        val documentId = firstCursor.getString(idIndex)
                        val selection = "${MediaStore.Images.Media._ID} = ?"
                        val args = arrayOf(documentId)
                        context.contentResolver.query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            projection,
                            selection,
                            args,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                                if (dataIndex >= 0) return cursor.getString(dataIndex)
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun externalMemoryAvailable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    @JvmStatic
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
    @JvmStatic
    fun formatSize(size: Long): String {
        return Formatter.formatFileSize(context, size)
    }

    @JvmStatic
    val totalMemoryCapacity: Long
        get() = getStorageStats(context).first

    @JvmStatic
    val totalAvailableMemory: Long
        get() = getStorageStats(context).second

    @JvmStatic
    val totalAvailableMemoryRatio: Long
        get() {
            val total = totalMemoryCapacity
            val available = totalAvailableMemory
            return Math.round(available.toDouble() / total.toDouble() * 100)
        }

    @JvmStatic
    val availableOverTotalMemoryFormattedString: String
        get() {
            val available = totalAvailableMemory
            val total = totalMemoryCapacity
            return formatSize(available) + "/" + formatSize(total)
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
    private fun extractFileName(filePath: String?): String? {
        return filePath?.let { File(it).name.takeIf { name -> name.isNotEmpty() } }
    }

    fun nameWithoutExtension(fileName: String?): String? {
        return extractFileName(fileName)?.substringBeforeLast('.')
    }
}
