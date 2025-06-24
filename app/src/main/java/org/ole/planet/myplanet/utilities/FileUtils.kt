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
import org.ole.planet.myplanet.MainApplication.Companion.context
import java.io.*
import java.util.UUID
import androidx.core.net.toUri
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object FileUtils {
    @JvmStatic
    @Throws(IOException::class)
    fun fullyReadFileToBytes(f: File): ByteArray {
        return f.inputStream().use { it.readBytes() }
    }

    private fun createFilePath(folder: String, filename: String): File {
        val baseDirectory = File(context.getExternalFilesDir(null), folder)
        val file = File(baseDirectory, filename)
        val dir = file.parentFile
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("Failed to create directory: ${dir.absolutePath}")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw RuntimeException("Failed to create directory: ${dir.absolutePath}", e)
        }
        return file
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
            if (url.isNullOrEmpty()) return ""
            val id = getIdFromUrl(url)
            if (id.isEmpty()) return ""
            val parts = url.split("/resources/$id/")
            if (parts.size > 1) {
                val encodedFileName = parts[1]
                URLDecoder.decode(encodedFileName, StandardCharsets.UTF_8.toString())
            } else {
                ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @JvmStatic
    fun getIdFromUrl(url: String?): String {
        return try {
            if (url.isNullOrEmpty()) return ""
            val index = url.indexOf("resources/")
            if (index == -1) return ""
            val sp = url.substring(index).split("/").filter { it.isNotEmpty() }
            sp.getOrNull(1) ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    @JvmStatic
    fun getFileExtension(address: String?): String {
        if (TextUtils.isEmpty(address)) return ""
        val filenameArray = address?.split("\\.".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
        return filenameArray?.get(filenameArray.size - 1) ?: ""
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
        session.openWrite("my_app_session", 0, -1).use { out ->
            apkFile.inputStream().use { it.copyTo(out) }
            session.fsync(out)
        }
    }

    @JvmStatic
    fun copyAssets(context: Context) {
        val tiles = arrayOf("dhulikhel.mbtiles", "somalia.mbtiles")
        val assetManager = context.assets
        try {
            tiles.forEach { name ->
                val input = assetManager.open(name)
                val outFile = File(Environment.getExternalStorageDirectory().toString() + "/osmdroid", name)
                FileOutputStream(outFile).use { output ->
                    input.use { copyFile(it, output) }
                }
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
        if (contentUri == null) return null
        val proj = arrayOf(MediaStore.Images.Media.DATA)
        return context.contentResolver.query(contentUri, proj, null, null, null)?.use { cursor ->
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            if (cursor.moveToFirst()) cursor.getString(columnIndex) else null
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
        return fl?.inputStream()?.use { convertStreamToString(it) } ?: ""
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
        val projection = arrayOf(MediaStore.Images.Media._ID)
        return runCatching {
            val id = queryDocumentId(context, uri, projection) ?: return null
            queryPathById(context, id, arrayOf(MediaStore.Images.Media.DATA))
        }.getOrElse {
            it.printStackTrace()
            null
        }
    }

    private fun queryDocumentId(context: Context, uri: Uri, projection: Array<String>): String? {
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(MediaStore.Images.Media._ID)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private fun queryPathById(context: Context, id: String, projection: Array<String>): String? {
        val selection = "${MediaStore.Images.Media._ID} = ?"
        val selectionArgs = arrayOf(id)
        return context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
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
        var result = size.toDouble()
        val units = arrayOf("", "KB", "MB", "GB")
        var index = 0
        while (result >= 1024 && index < units.lastIndex) {
            result /= 1024
            index++
        }
        return String.format("%,.0f%s", result, units[index])
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
    private fun extractFileName(filePath: String?): String?{
        if(filePath.isNullOrEmpty()) return null
        val regex = Regex(".+/(.+\\.[a-zA-Z0-9]+)")
        return regex.find(filePath)?.groupValues?.get(1)
    }

    fun nameWithoutExtension(fileName: String?): String? {
        val nameWithExtension = extractFileName(fileName)
        return nameWithExtension?.substringBeforeLast('.')
    }
}
