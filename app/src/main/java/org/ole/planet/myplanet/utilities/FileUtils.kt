package org.ole.planet.myplanet.utilities

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.text.TextUtils
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

object FileUtils {
    private val SD_PATH = MainApplication.context.getExternalFilesDir(null).toString() + "/ole"
    @JvmStatic
    @Throws(IOException::class)
    fun fullyReadFileToBytes(f: File): ByteArray {
        val size = f.length().toInt()
        val bytes = ByteArray(size)
        val tmpBuff = ByteArray(size)
        val fis = FileInputStream(f)
        try {
            var read = fis.read(bytes, 0, size)
            if (read < size) {
                var remain = size - read
                while (remain > 0) {
                    read = fis.read(tmpBuff, 0, remain)
                    System.arraycopy(tmpBuff, 0, bytes, size - remain, read)
                    remain -= read
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            throw e
        } finally {
            fis.close()
        }
        return bytes
    }

    private fun createFilePath(folder: String, filename: String): File {
        val directory = File(MainApplication.context.getExternalFilesDir(null), folder)
        if (!directory.exists()) {
            try {
                if (!directory.mkdirs()) {
                    throw IOException("Failed to create directory: ${directory.absolutePath}")
                }
            } catch (e: IOException) {
                e.printStackTrace()
                throw RuntimeException("Failed to create directory: ${directory.absolutePath}", e)
            }
        }
        return File(directory, filename)
    }

    @JvmStatic
    fun getSDPathFromUrl(url: String?): File {
        return createFilePath("/ole/" + getIdFromUrl(url), getFileNameFromUrl(url))
    }

    @JvmStatic
    fun checkFileExist(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val f = getSDPathFromUrl(url)
        return f.exists()
    }

    @JvmStatic
    fun getFileNameFromUrl(url: String?): String {
        try {
            if (url != null) {
                return url.substring(url.lastIndexOf("/") + 1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    @JvmStatic
    fun getIdFromUrl(url: String?): String {
        try {
            val sp = url?.substring(url.indexOf("resources/"))?.split("/".toRegex())
                ?.dropLastWhile { it.isEmpty() }
                ?.toTypedArray()
            return sp?.get(1) ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    @JvmStatic
    fun getFileExtension(address: String?): String {
        if (TextUtils.isEmpty(address)) return ""
        val filenameArray = address?.split("\\.".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
        return filenameArray?.get(filenameArray.size - 1) ?: ""
    }

    @JvmStatic
    fun installApk(activity: Context, file: String?) {
        try {
            if (!file?.endsWith("apk")!!) return
            val toInstall = getSDPathFromUrl(file)
            toInstall.setReadable(true, false)
            val apkUri: Uri
            val intent: Intent
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".provider", toInstall)
                intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.setData(apkUri)
            } else {
                apkUri = Uri.fromFile(toInstall)
                intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getMimeType(url: String): String? {
        var type: String? = null
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        if (extension != null) {
            val mime = MimeTypeMap.getSingleton()
            type = mime.getMimeTypeFromExtension(extension)
        }
        return type
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
        val buffer = ByteArray(1024)
        var read: Int
        while (`in`.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
        }
    }

    @JvmStatic
    fun getRealPathFromURI(context: Context, contentUri: Uri?): String? {
        var cursor: Cursor? = null
        return try {
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            cursor = contentUri?.let { context.contentResolver.query(it, proj, null, null, null) }
            val column_index = cursor?.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor?.moveToFirst()
            cursor?.getString(column_index ?: 0)
        } finally {
            cursor?.close()
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun convertStreamToString(`is`: InputStream?): String {
        val reader = BufferedReader(InputStreamReader(`is`))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line).append("\n")
        }
        reader.close()
        return sb.toString()
    }

    @JvmStatic
    @Throws(Exception::class)
    fun getStringFromFile(fl: File?): String {
        val fin = FileInputStream(fl)
        val ret = convertStreamToString(fin)
        fin.close()
        return ret
    }

    @JvmStatic
    fun openOleFolder(): Intent {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        val uri = Uri.parse(Utilities.SD_PATH)  // Ensure org.ole.planet.myplanet.utilities.Utilities.SD_PATH is the correct path
        intent.setDataAndType(uri, "*/*")
        return Intent.createChooser(intent, "Open folder")
    }

    @JvmStatic
    fun getImagePath(context: Context, uri: Uri?): String? {
        var cursor = uri?.let { context.contentResolver.query(it, null, null, null, null) }
        return if (cursor != null && cursor.moveToFirst()) {
            var document_id = cursor.getString(0)
            document_id = document_id.substring(document_id.lastIndexOf(":") + 1)
            cursor.close()
            cursor = context.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, MediaStore.Images.Media._ID + " = ? ", arrayOf(document_id), null)
            if (cursor != null && cursor.moveToFirst()) {
                val path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
                cursor.close()
                path
            } else {
                // Handle the case when the cursor is empty or null
                null // or return an appropriate default value or handle the error accordingly
            }
        } else {
            // Handle the case when the cursor is empty or null
            null // or return an appropriate default value or handle the error accordingly
        }
    }

    @JvmStatic
    fun getMediaType(path: String): String {
        val ext = getFileExtension(path)
        if (ext.equals("jpg", ignoreCase = true) || ext.equals("png", ignoreCase = true))
            return "image"
        else if (ext.equals("mp4", ignoreCase = true))
            return "mp4"
        else if (ext.equals("mp3", ignoreCase = true) || ext.equals("aac", ignoreCase = true))
            return "audio"
        return ""
    }

    // Disk space utilities
    @JvmStatic
    val totalInternalMemorySize: Long
        /**
         * @return Total internal memory capacity.
         */
        get() {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            return totalBlocks * blockSize
        }

    @JvmStatic
    val availableInternalMemorySize: Long
        /**
         * Find space left in the internal memory.
         */
        get() {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val availableBlocks = stat.availableBlocksLong
            return availableBlocks * blockSize
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

    @JvmStatic
    val totalExternalMemorySize: Long
        /**
         * @return Total capacity of the external memory
         */
        get() = if (externalMemoryAvailable()) {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            totalBlocks * blockSize
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
        var size = size
        var suffix: String? = null
        if (size >= 1024) {
            suffix = "KB"
            size /= 1024
        }
        if (size >= 1024) {
            suffix = "MB"
            size /= 1024
        }
        if (size >= 1024) {
            suffix = "GB"
            size /= 1024
        }
        val resultBuffer = StringBuilder(size.toString())
        var commaOffset = resultBuffer.length - 3
        while (commaOffset > 0) {
            resultBuffer.insert(commaOffset, ',')
            commaOffset -= 3
        }
        if (suffix != null) resultBuffer.append(suffix)
        return resultBuffer.toString()
    }

    @JvmStatic
    val totalMemoryCapacity: Long
        @RequiresApi(Build.VERSION_CODES.O)
        get() = getStorageStats(MainApplication.context).first

    @JvmStatic
    val totalAvailableMemory: Long
        @RequiresApi(Build.VERSION_CODES.O)
        get() = getStorageStats(MainApplication.context).second

    @JvmStatic
    val totalAvailableMemoryRatio: Long
        @RequiresApi(Build.VERSION_CODES.O)
        get() {
            val total = totalMemoryCapacity
            val available = totalAvailableMemory
            return Math.round(available.toDouble() / total.toDouble() * 100)
        }

    @JvmStatic
    val availableOverTotalMemoryFormattedString: String
        @RequiresApi(Build.VERSION_CODES.O)
        get() {
            val context = MainApplication.context
            val available = totalAvailableMemory
            val total = totalMemoryCapacity
            return context.getString(R.string.available_space_colon) + formatSize(available) + "/" + formatSize(total)
        }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun getStorageStats(context: Context): Pair<Long, Long> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
        } else {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = blockSize * totalBlocks
            val availableBytes = blockSize * availableBlocks
            return Pair(totalBytes, availableBytes)
        }
    }
    fun extractFileName(filePath: String?): String?{
        if(filePath.isNullOrEmpty()) return null
        val regex = Regex(".+/(.+\\.[a-zA-Z0-9]+)")
        return regex.find(filePath)?.groupValues?.get(1)
    }

    fun nameWithoutExtension(fileName: String?): String?{
        extractFileName(fileName)
        val nameWithExtension = FileUtils.extractFileName(fileName)
        val nameWithoutExtension = nameWithExtension?.substringBeforeLast(".")
        return nameWithoutExtension
    }


}
