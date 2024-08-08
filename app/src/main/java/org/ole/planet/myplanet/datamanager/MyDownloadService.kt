package org.ole.planet.myplanet.datamanager

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.realm.Realm
import okhttp3.ResponseBody
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.NotificationUtil
import org.ole.planet.myplanet.utilities.Utilities
import retrofit2.Call
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.pow
import kotlin.math.roundToInt

class MyDownloadService(context: Context, params: WorkerParameters) : Worker(context, params) {
    private var count = 0
    private var data = ByteArray(1024 * 4)
    private var outputFile: File? = null
    private var notificationBuilder: NotificationCompat.Builder? = null
    private var notificationManager: NotificationManager? = null
    private var totalFileSize = 0
    private var preferences: SharedPreferences? = null
    private var url: String? = null
    private lateinit var urls: Array<String>
    private var currentIndex = 0
    private var request: Call<ResponseBody>? = null
    private var completeAll = false
    private var fromSync = false
    private val databaseService: DatabaseService by lazy {
        DatabaseService(applicationContext)
    }

    private val mRealm: Realm by lazy {
        databaseService.realmInstance
    }

    override fun doWork(): Result {
        preferences = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val urlsKey = inputData.getString("urls_key")
        val settings = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        urls = (settings.getStringSet(urlsKey, emptySet())?.toList() ?: return Result.failure()).toTypedArray()

        fromSync = inputData.getBoolean("fromSync", false)

        if (urls.isEmpty()) {
            return Result.failure()
        }

        notificationBuilder = NotificationCompat.Builder(applicationContext, "11")
        NotificationUtil.setChannel(notificationManager)
        val notification = notificationBuilder?.setSmallIcon(R.mipmap.ic_launcher)
            ?.setContentTitle("OLE Download")?.setContentText("Downloading File...")
            ?.setAutoCancel(true)?.build()
        notificationManager?.notify(0, notification)

        for (i in urls.indices) {
            url = urls[i]
            currentIndex = i
            initDownload(fromSync)
        }

        return Result.success()
    }

    private fun initDownload(fromSync: Boolean) {
        val retrofitInterface = ApiClient.client?.create(ApiInterface::class.java)
        if (retrofitInterface != null) {
            request = retrofitInterface.downloadFile(Utilities.header, url)
            try {
                val r = request?.execute()
                if (r != null) {
                    if (r.code() == 200) {
                        val responseBody = r.body()
                        if (!checkStorage(responseBody?.contentLength() ?: 0L)) {
                            responseBody?.let { downloadFile(it) }
                        }
                    } else {
                        downloadFiled(if (r.code() == 404) "File Not found " else "Connection failed", fromSync)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                e.localizedMessage?.let { downloadFiled(it, fromSync) }
                e.printStackTrace()
            }
        }
    }

    private fun downloadFiled(message: String, fromSync: Boolean) {
        notificationBuilder?.setContentText(message)
        notificationManager?.notify(0, notificationBuilder?.build())
        val d = Download()
        completeAll = false
        d.failed = true
        d.message = message
        sendIntent(d, fromSync)
    }

    @Throws(IOException::class)
    private fun downloadFile(body: ResponseBody?) {
        val fileSize = body?.contentLength()
        val bis: InputStream = BufferedInputStream(body?.byteStream(), 1024 * 8)
        outputFile = FileUtils.getSDPathFromUrl(url)
        val output: OutputStream = FileOutputStream(outputFile)
        var total: Long = 0
        val startTime = System.currentTimeMillis()
        var timeCount = 1
        while (bis.read(data).also { count = it } != -1) {
            total += count.toLong()
            fileSize?.let {
                totalFileSize = (it / 1024.0.pow(1.0)).toInt()
            } ?: run {
                totalFileSize = 0
            }

            val current = (total / 1024.0.pow(1.0)).roundToInt().toDouble()
            val progress = fileSize?.let { (total * 100 / it).toInt() } ?: 0
            val currentTime = System.currentTimeMillis() - startTime
            val download = Download()
            download.fileName = FileUtils.getFileNameFromUrl(url)
            download.totalFileSize = totalFileSize
            if (currentTime > 1000 * timeCount) {
                download.currentFileSize = current.toInt()
                download.progress = progress
                sendNotification(download)
                timeCount++
            }
            output.write(data, 0, count)
        }
        closeStreams(output, bis)
    }

    private fun checkStorage(fileSize: Long): Boolean {
        if (!FileUtils.externalMemoryAvailable()) {
            downloadFiled("Download Failed : SD card Not available", fromSync)
            return true
        } else if (fileSize > FileUtils.availableExternalMemorySize) {
            downloadFiled("Download Failed : Not enough storage in SD card", fromSync)
            return true
        }
        return false
    }

    @Throws(IOException::class)
    private fun closeStreams(output: OutputStream, bis: InputStream) {
        output.flush()
        output.close()
        bis.close()
        onDownloadComplete()
    }

    private fun sendNotification(download: Download) {
        download.fileName = "Downloading : " + FileUtils.getFileNameFromUrl(url)
        sendIntent(download, fromSync)
        notificationBuilder?.setProgress(100, download.progress, false)
        notificationBuilder?.setContentText("Downloading file " + download.currentFileSize + "/" + totalFileSize + " KB")
        notificationManager?.notify(0, notificationBuilder?.build())
    }

    private fun sendIntent(download: Download, fromSync: Boolean) {
        val intent = Intent(DashboardActivity.MESSAGE_PROGRESS)
        intent.putExtra("download", download)
        intent.putExtra("fromSync", fromSync)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun onDownloadComplete() {
        if ((outputFile?.length() ?: 0) > 0) {
            changeOfflineStatus()
        }
        val download = Download()
        download.fileName = FileUtils.getFileNameFromUrl(url)
        download.fileUrl = url
        download.progress = 100
        if (currentIndex == urls.size - 1) {
            completeAll = true
            download.completeAll = true
        }
        sendIntent(download, fromSync)
        notificationManager?.cancel(0)
        notificationBuilder?.setProgress(0, 0, false)
        notificationBuilder?.setContentText("File Downloaded")
        notificationManager?.notify(0, notificationBuilder?.build())
    }

    private fun changeOfflineStatus() {
        val currentFileName = FileUtils.getFileNameFromUrl(url)
        mRealm.executeTransaction { realm: Realm ->
            val matchingItems = realm.where(RealmMyLibrary::class.java)
                .equalTo("resourceLocalAddress", currentFileName)
                .findAll()
            if (!matchingItems.isEmpty()) {
                for (item in matchingItems) {
                    item.resourceOffline = true
                    item.downloadedRev = item._rev
                }
            }
        }
    }
}
