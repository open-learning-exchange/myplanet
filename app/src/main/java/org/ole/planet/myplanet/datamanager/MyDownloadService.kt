package org.ole.planet.myplanet.datamanager

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.realm.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity.Companion.MESSAGE_PROGRESS
import org.ole.planet.myplanet.utilities.FileUtils.availableExternalMemorySize
import org.ole.planet.myplanet.utilities.FileUtils.externalMemoryAvailable
import org.ole.planet.myplanet.utilities.FileUtils.getFileNameFromUrl
import org.ole.planet.myplanet.utilities.FileUtils.getSDPathFromUrl
import org.ole.planet.myplanet.utilities.NotificationUtil
import org.ole.planet.myplanet.utilities.Utilities.header
import retrofit2.Call
import java.io.*
import kotlin.math.pow
import kotlin.math.roundToInt

class MyDownloadService : Service() {
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
        DatabaseService(this)
    }

    private val mRealm: Realm by lazy {
        databaseService.realmInstance
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val urlsKey = intent?.getStringExtra("urls_key") ?: "url_list_key"

        val urlSet = preferences?.getStringSet(urlsKey, emptySet())

        if (urlSet.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        urls = urlSet.toTypedArray()
        fromSync = intent?.getBooleanExtra("fromSync", false) == true

        initNotification()

        CoroutineScope(Dispatchers.IO).launch {
            for (i in urls.indices) {
                url = urls[i]
                currentIndex = i
                initDownload(fromSync)
            }
        }

        return START_STICKY
    }

    private fun initNotification() {
        notificationBuilder = NotificationCompat.Builder(this, "11")
        NotificationUtil.setChannel(notificationManager)
        val notification = notificationBuilder?.setSmallIcon(R.mipmap.ic_launcher)
            ?.setContentTitle("OLE Download")
            ?.setContentText("Downloading File...")
            ?.setAutoCancel(true)
            ?.build()
        startForeground(1, notification)
    }

    private fun initDownload(fromSync: Boolean) {
        val retrofitInterface = ApiClient.client?.create(ApiInterface::class.java)
        if (retrofitInterface != null) {
            try {
                request = retrofitInterface.downloadFile(header, url)
                val response = request?.execute()

                when {
                    response == null -> {
                        downloadFailed("Null response from server", fromSync)
                    }
                    response.isSuccessful -> {
                        val responseBody = response.body()
                        if (!checkStorage(responseBody?.contentLength() ?: 0L)) {
                            responseBody?.let { downloadFile(it) }
                        }
                    }
                    else -> {
                        downloadFailed(if (response.code() == 404) "File Not found" else "Connection failed (${response.code()})", fromSync)
                    }
                }
            } catch (e: IOException) {
                e.localizedMessage?.let { downloadFailed(it, fromSync) }
            }
        } else {
            downloadFailed("Network client initialization failed", fromSync)
        }
    }

    private fun downloadFailed(message: String, fromSync: Boolean) {
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
        outputFile = getSDPathFromUrl(url)
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
            download.fileName = getFileNameFromUrl(url)
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
        if (!externalMemoryAvailable()) {
            downloadFailed("Download Failed : SD card Not available", fromSync)
            return true
        } else if (fileSize > availableExternalMemorySize) {
            downloadFailed("Download Failed : Not enough storage in SD card", fromSync)
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
        download.fileName = "Downloading : ${getFileNameFromUrl(url)}"
        sendIntent(download, fromSync)
        notificationBuilder?.setProgress(100, download.progress, false)
        notificationBuilder?.setContentText("Downloading file ${download.currentFileSize}/$totalFileSize KB")
        notificationManager?.notify(0, notificationBuilder?.build())
    }

    private fun sendIntent(download: Download, fromSync: Boolean) {
        val intent = Intent(MESSAGE_PROGRESS)
        intent.putExtra("download", download)
        intent.putExtra("fromSync", fromSync)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun onDownloadComplete() {
        if ((outputFile?.length() ?: 0) > 0) {
            changeOfflineStatus()
        }
        val download = Download()
        download.fileName = getFileNameFromUrl(url)
        download.fileUrl = url
        download.progress = 100
        if (currentIndex == urls.size - 1) {
            completeAll = true
            download.completeAll = true
            stopSelf()
        }
        sendIntent(download, fromSync)
        notificationManager?.cancel(0)
        notificationBuilder?.setProgress(0, 0, false)
        notificationBuilder?.setContentText("File Downloaded")
        notificationManager?.notify(0, notificationBuilder?.build())
    }

    private fun changeOfflineStatus() {
        val currentFileName = getFileNameFromUrl(url)
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

    companion object {
        const val PREFS_NAME = "MyPrefsFile"

        fun startService(context: Context, urlsKey: String, fromSync: Boolean) {
            val intent = Intent(context, MyDownloadService::class.java).apply {
                putExtra("urls_key", urlsKey)
                putExtra("fromSync", fromSync)
            }
            context.startService(intent)
        }
    }
}
