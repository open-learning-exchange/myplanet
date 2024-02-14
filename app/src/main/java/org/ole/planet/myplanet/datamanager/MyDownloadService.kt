package org.ole.planet.myplanet.datamanager

import android.app.IntentService
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.realm.Realm
import io.realm.RealmConfiguration
import okhttp3.ResponseBody
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.sync.SyncActivity
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

class MyDownloadService : IntentService("Download Service") {
    var count = 0
    var data = ByteArray(1024 * 4)
    var outputFile: File? = null
    private var notificationBuilder: NotificationCompat.Builder? = null
    private var notificationManager: NotificationManager? = null
    private var totalFileSize = 0
    private var preferences: SharedPreferences? = null
    private var url: String? = null
    private var urls: ArrayList<String>? = null
    private var currentIndex = 0
    private var mRealm: Realm? = null
    private var request: Call<ResponseBody>? = null
    private var completeAll = false
    override fun onHandleIntent(intent: Intent?) {
        preferences = applicationContext.getSharedPreferences(SyncActivity.PREFS_NAME, MODE_PRIVATE)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (urls == null) {
            stopSelf()
        }
        notificationBuilder = NotificationCompat.Builder(this, "11")
        NotificationUtil.setChannel(notificationManager!!)
        val noti = notificationBuilder!!.setSmallIcon(R.mipmap.ic_launcher).setContentTitle("OLE Download")
            .setContentText("Downloading File...").setAutoCancel(true).build()
        notificationManager!!.notify(0, noti)
        urls = intent!!.getStringArrayListExtra("urls")
        realmConfig()
        for (i in urls!!.indices) {
            url = urls!![i]
            currentIndex = i
            initDownload()
        }
    }

    private fun initDownload() {
        Utilities.log("File url $url")
        val retrofitInterface = ApiClient.client?.create(ApiInterface::class.java)
        if (retrofitInterface != null) {
            request = retrofitInterface.downloadFile(Utilities.header, url)
            try {
                val r = request?.execute()
                if (r != null) {
                    if (r.code() == 200) {
                        val responseBody = r.body()
                        if (!checkStorage(responseBody!!.contentLength())) {
                            downloadFile(responseBody)
                        }
                    } else {
                        downloadFiled(if (r.code() == 404) "File Not found " else "Connection failed")
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                e.localizedMessage?.let { downloadFiled(it) }
                e.printStackTrace()
            }
        }

    }

    private fun downloadFiled(message: String) {
        notificationBuilder!!.setContentText(message)
        notificationManager!!.notify(0, notificationBuilder!!.build())
        val d = Download()
        completeAll = false
        d.failed = true
        d.message = message
        sendIntent(d)
        stopSelf()
    }

    @Throws(IOException::class)
    private fun downloadFile(body: ResponseBody?) {
        val fileSize = body!!.contentLength()
        val bis: InputStream = BufferedInputStream(body.byteStream(), 1024 * 8)
        outputFile = FileUtils.getSDPathFromUrl(url)
        val output: OutputStream = FileOutputStream(outputFile)
        var total: Long = 0
        val startTime = System.currentTimeMillis()
        var timeCount = 1
        while (bis.read(data).also { count = it } != -1) {
            total += count.toLong()
            totalFileSize = (fileSize / 1024.0.pow(1.0)).toInt()
            val current = (total / 1024.0.pow(1.0)).roundToInt().toDouble()
            val progress = (total * 100 / fileSize).toInt()
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
            downloadFiled("Download Failed : SD card Not available")
            return true
        } else if (fileSize > FileUtils.availableExternalMemorySize) {
            downloadFiled("Download Failed : Not enough storage in SD card")
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
        sendIntent(download)
        notificationBuilder!!.setProgress(100, download.progress, false)
        notificationBuilder!!.setContentText("Downloading file " + download.currentFileSize + "/" + totalFileSize + " KB")
        notificationManager!!.notify(0, notificationBuilder!!.build())
    }

    private fun sendIntent(download: Download) {
        val intent = Intent(DashboardActivity.MESSAGE_PROGRESS)
        intent.putExtra("download", download)
        LocalBroadcastManager.getInstance(this@MyDownloadService).sendBroadcast(intent)
    }

    private fun onDownloadComplete() {
        if (outputFile!!.length() > 0) changeOfflineStatus()
        val download = Download()
        download.fileName = FileUtils.getFileNameFromUrl(url)
        download.fileUrl = url
        download.progress = 100
        if (currentIndex == urls!!.size - 1) {
            completeAll = true
            download.completeAll = true
        }
        sendIntent(download)
        notificationManager!!.cancel(0)
        notificationBuilder!!.setProgress(0, 0, false)
        notificationBuilder!!.setContentText("File Downloaded")
        notificationManager!!.notify(0, notificationBuilder!!.build())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!completeAll) {
            stopDownload()
        }
    }

    private fun stopDownload() {
        if (request != null && outputFile != null) {
            request!!.cancel()
            outputFile!!.delete()
            notificationManager!!.cancelAll()
        }
    }

    private fun changeOfflineStatus() {
        val currentFileName = FileUtils.getFileNameFromUrl(url)
        mRealm!!.executeTransaction { realm: Realm ->
            val obj = realm.where(
                RealmMyLibrary::class.java
            ).equalTo("resourceLocalAddress", currentFileName).findFirst()
            if (obj != null) {
                obj.resourceOffline = true
                obj.downloadedRev = obj.get_rev()
            } else {
                Utilities.log("object Is null")
            }
        }
    }

    fun realmConfig() {
        Realm.init(this)
        val config = RealmConfiguration.Builder().name(Realm.DEFAULT_REALM_NAME)
            .deleteRealmIfMigrationNeeded().schemaVersion(4).build()
        Realm.setDefaultConfiguration(config)
        mRealm = Realm.getInstance(config)
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        notificationManager!!.cancel(0)
    }
}
