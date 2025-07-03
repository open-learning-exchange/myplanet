package org.ole.planet.myplanet.ui.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.PermissionActivity
import org.ole.planet.myplanet.callback.SecurityDataCallback
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.RealmAchievement
import org.ole.planet.myplanet.model.RealmApkLog
import org.ole.planet.myplanet.model.RealmCourseActivity
import org.ole.planet.myplanet.model.RealmCourseProgress
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmMeetup
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmNews
import org.ole.planet.myplanet.model.RealmNewsLog
import org.ole.planet.myplanet.model.RealmOfflineActivity
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmResourceActivity
import org.ole.planet.myplanet.model.RealmSearchActivity
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmSubmitPhotos
import org.ole.planet.myplanet.model.RealmTeamLog
import org.ole.planet.myplanet.model.RealmTeamTask
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UploadToShelfService
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.DialogUtils.showAlert
import org.ole.planet.myplanet.utilities.DialogUtils.showError
import org.ole.planet.myplanet.utilities.FileUtils.installApk
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.Utilities.getUrl
import java.io.File
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

abstract class ProcessUserDataActivity : PermissionActivity(), SuccessListener {
    lateinit var settings: SharedPreferences
    val customProgressDialog: DialogUtils.CustomProgressDialog by lazy {
        DialogUtils.CustomProgressDialog(this)
    }

    @JvmField
    var broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DashboardActivity.MESSAGE_PROGRESS) {
                val download: Download? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("download", Download::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("download")
                }
                val fromSync = intent.getBooleanExtra("fromSync", false)
                if (!fromSync) {
                    checkDownloadResult(download)
                }
            }
        }
    }

    fun validateEditText(textField: EditText, textLayout: TextInputLayout, errMessage: String?): Boolean {
        if (textField.text.toString().trim { it <= ' ' }.isEmpty()) {
            textLayout.error = errMessage
            requestFocus(textField)
            return false
        } else {
            textLayout.isErrorEnabled = false
        }
        return true
    }

    fun checkDownloadResult(download: Download?) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                customProgressDialog.show()
                customProgressDialog.setText("${getString(R.string.downloading)} ${download?.progress}% ${getString(R.string.complete)}")
                customProgressDialog.setProgress(download?.progress ?: 0)
                if (download?.completeAll == true) {
                    safelyDismissDialog()
                    installApk(this, download.fileUrl)
                } else {
                    safelyDismissDialog()
                    showError(customProgressDialog, download?.message)
                }
            }
        }
    }

    private fun safelyDismissDialog() {
        if (customProgressDialog.isShowing() && !isFinishing) {
            try {
                customProgressDialog.dismiss()
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
        }
    }

    fun openDashboard() {
        val dashboard = Intent(applicationContext, DashboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(dashboard)
        finish()
    }

    private fun requestFocus(view: View) {
        if (view.requestFocus()) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
    }

    fun changeLogoColor() {
        val logo = findViewById<ImageView>(R.id.logoImageView)
        val newColor = ContextCompat.getColor(this, android.R.color.white)
        val alpha = (Color.alpha(newColor) * 10).toFloat().roundToInt()
        val red = Color.red(newColor)
        val green = Color.green(newColor)
        val blue = Color.blue(newColor)
        val alphaWhite = Color.argb(alpha, red, green, blue)
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_NO ||
            (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM && currentNightMode == Configuration.UI_MODE_NIGHT_NO)) {
            logo.setColorFilter(alphaWhite, PorterDuff.Mode.SRC_ATOP)
        }
    }

    fun setUrlParts(url: String, password: String): String {
        val editor = settings.edit()
        val uri = url.toUri()
        var couchdbURL: String
        val urlUser: String
        val urlPwd: String
        if (url.contains("@")) {
            val userinfo = getUserInfo(uri)
            urlUser = userinfo[0]
            urlPwd = userinfo[1]
            couchdbURL = url
        } else if (TextUtils.isEmpty(password)) {
            showAlert(this, "", getString(R.string.pin_is_required))
            return ""
        } else {
            urlUser = "satellite"
            urlPwd = password
            couchdbURL = "${uri.scheme}://$urlUser:$urlPwd@${uri.host}:${if (uri.port == -1) (if (uri.scheme == "http") 80 else 443) else uri.port}"
        }
        editor.putString("serverPin", password)
        saveUrlScheme(editor, uri, url, couchdbURL)
        editor.putString("url_user", urlUser)
        editor.putString("url_pwd", urlPwd)
        editor.putString("url_Scheme", uri.scheme)
        editor.putString("url_Host", uri.host)
        editor.apply()
        if (!couchdbURL.endsWith("db")) {
            couchdbURL += "/db"
        }
        return couchdbURL
    }

    fun isUrlValid(url: String): Boolean {
        if (!URLUtil.isValidUrl(url) || url == "http://" || url == "https://") {
            showAlert(this, getString(R.string.invalid_url), getString(R.string.please_enter_valid_url_to_continue))
            return false
        }
        return true
    }

    // APPROACH 1: Sequential Execution Fix
// Replace the async section in your startUpload method with this:

    fun startUpload(source: String, userName: String? = null, securityCallback: SecurityDataCallback? = null) {
        val overallStartTime = System.currentTimeMillis()
        Log.d("UploadTiming", "=== UPLOAD PROCESS STARTED ===")
        Log.d("UploadTiming", "Source: $source, UserName: $userName")
        Log.d("UploadTiming", "Overall start time: ${Date(overallStartTime)}")

        // Add database diagnostics
        logDatabaseDiagnostics()

        if (source == "becomeMember") {
            UploadToShelfService.instance?.uploadSingleUserData(userName ,object : SuccessListener {
                override fun onSuccess(message: String?) {
                    UploadToShelfService.instance?.uploadSingleUserHealth("org.couchdb.user:${userName}", object : SuccessListener {
                        override fun onSuccess(healthMessage: String?) {
                            userName?.let { name ->
                                fetchAndLogUserSecurityData(name, securityCallback)
                            } ?: run {
                                securityCallback?.onSecurityDataUpdated()
                            }
                        }
                    })
                }
            })
            return
        } else if (source == "login") {
            UploadManager.instance?.uploadUserActivities(this@ProcessUserDataActivity)
            return
        }

        // Main upload process with sequential execution
        Log.d("UploadTiming", "Starting main upload process")
        customProgressDialog.setText(context.getString(R.string.uploading_data_to_server_please_wait))
        customProgressDialog.show()

        // Synchronous uploads (these are fast, keep as-is)
        val syncUploadsStartTime = System.currentTimeMillis()
        Log.d("UploadTiming", "Starting synchronous uploads")

        val uploadTimings = mutableMapOf<String, Long>()

        val achievementStartTime = System.currentTimeMillis()
        UploadManager.instance?.uploadAchievement()
        uploadTimings["uploadAchievement"] = System.currentTimeMillis() - achievementStartTime
        Log.d("UploadTiming", "uploadAchievement completed in ${uploadTimings["uploadAchievement"]}ms")

        val newsStartTime = System.currentTimeMillis()
        UploadManager.instance?.uploadNews()
        uploadTimings["uploadNews"] = System.currentTimeMillis() - newsStartTime
        Log.d("UploadTiming", "uploadNews completed in ${uploadTimings["uploadNews"]}ms")

        val resourceActivitiesStartTime = System.currentTimeMillis()
        UploadManager.instance?.uploadResourceActivities("")
        uploadTimings["uploadResourceActivities"] = System.currentTimeMillis() - resourceActivitiesStartTime
        Log.d("UploadTiming", "uploadResourceActivities completed in ${uploadTimings["uploadResourceActivities"]}ms")

        val courseActivitiesStartTime = System.currentTimeMillis()
        UploadManager.instance?.uploadCourseActivities()
        uploadTimings["uploadCourseActivities"] = System.currentTimeMillis() - courseActivitiesStartTime
        Log.d("UploadTiming", "uploadCourseActivities completed in ${uploadTimings["uploadCourseActivities"]}ms")

        val searchActivityStartTime = System.currentTimeMillis()
        UploadManager.instance?.uploadSearchActivity()
        uploadTimings["uploadSearchActivity"] = System.currentTimeMillis() - searchActivityStartTime
        Log.d("UploadTiming", "uploadSearchActivity completed in ${uploadTimings["uploadSearchActivity"]}ms")

        val teamsStartTime = System.currentTimeMillis()
        UploadManager.instance?.uploadTeams()
        uploadTimings["uploadTeams"] = System.currentTimeMillis() - teamsStartTime
        Log.d("UploadTiming", "uploadTeams completed in ${uploadTimings["uploadTeams"]}ms")

        val ratingStartTime = System.currentTimeMillis()
        UploadManager.instance?.uploadRating()
        uploadTimings["uploadRating"] = System.currentTimeMillis() - ratingStartTime
        Log.d("UploadTiming", "uploadRating completed in ${uploadTimings["uploadRating"]}ms")

        val teamTaskStartTime = System.currentTimeMillis()
        UploadManager.instance?.uploadTeamTask()
        uploadTimings["uploadTeamTask"] = System.currentTimeMillis() - teamTaskStartTime
        Log.d("UploadTiming", "uploadTeamTask completed in ${uploadTimings["uploadTeamTask"]}ms")

        val meetupsStartTime = System.currentTimeMillis()
        UploadManager.instance?.uploadMeetups()
        uploadTimings["uploadMeetups"] = System.currentTimeMillis() - meetupsStartTime
        Log.d("UploadTiming", "uploadMeetups completed in ${uploadTimings["uploadMeetups"]}ms")

        val submissionsStartTime = System.currentTimeMillis()
        UploadManager.instance?.uploadSubmissions()
        uploadTimings["uploadSubmissions"] = System.currentTimeMillis() - submissionsStartTime
        Log.d("UploadTiming", "uploadSubmissions completed in ${uploadTimings["uploadSubmissions"]}ms")

        val crashLogStartTime = System.currentTimeMillis()
        UploadManager.instance?.uploadCrashLog()
        uploadTimings["uploadCrashLog"] = System.currentTimeMillis() - crashLogStartTime
        Log.d("UploadTiming", "uploadCrashLog completed in ${uploadTimings["uploadCrashLog"]}ms")

        val syncUploadsDuration = System.currentTimeMillis() - syncUploadsStartTime
        Log.d("UploadTiming", "All synchronous uploads completed in ${syncUploadsDuration}ms")

        // SEQUENTIAL async uploads to avoid Realm transaction queue bottleneck
        val asyncStartTime = System.currentTimeMillis()
        val asyncTimings = mutableMapOf<String, Long>()

        Log.d("UploadTiming", "Starting SEQUENTIAL asynchronous uploads")

        fun completeUploadProcess() {
            val asyncDuration = System.currentTimeMillis() - asyncStartTime
            val totalDuration = System.currentTimeMillis() - overallStartTime

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    customProgressDialog.dismiss()
                    Toast.makeText(this@ProcessUserDataActivity, "upload complete", Toast.LENGTH_SHORT).show()
                }
            }

            // Log comprehensive timing summary
            Log.d("UploadTiming", "=== UPLOAD TIMING SUMMARY ===")
            Log.d("UploadTiming", "Synchronous uploads total: ${syncUploadsDuration}ms")
            uploadTimings.forEach { (operation, duration) ->
                Log.d("UploadTiming", "  - $operation: ${duration}ms")
            }
            Log.d("UploadTiming", "Asynchronous uploads total: ${asyncDuration}ms")
            asyncTimings.forEach { (operation, duration) ->
                Log.d("UploadTiming", "  - $operation: ${duration}ms")
            }
            Log.d("UploadTiming", "TOTAL UPLOAD PROCESS DURATION: ${totalDuration}ms (${totalDuration/1000.0}s)")
            Log.d("UploadTiming", "=== UPLOAD PROCESS COMPLETED ===")
        }

        // Chain 1: Upload user data with health
        val userDataStartTime = System.currentTimeMillis()
        Log.d("UploadTiming", "Step 1/6: Starting uploadUserData")
        UploadToShelfService.instance?.uploadUserData {
            asyncTimings["uploadUserData"] = System.currentTimeMillis() - userDataStartTime
            Log.d("UploadTiming", "uploadUserData completed in ${asyncTimings["uploadUserData"]}ms")

            val healthStartTime = System.currentTimeMillis()
            UploadToShelfService.instance?.uploadHealth()
            asyncTimings["uploadHealth"] = System.currentTimeMillis() - healthStartTime
            Log.d("UploadTiming", "uploadHealth completed in ${asyncTimings["uploadHealth"]}ms")

            // Chain 2: Upload user activities
            val userActivitiesStartTime = System.currentTimeMillis()
            Log.d("UploadTiming", "Step 2/6: Starting uploadUserActivities")
            UploadManager.instance?.uploadUserActivities(object : SuccessListener {
                override fun onSuccess(message: String?) {
                    asyncTimings["uploadUserActivities"] = System.currentTimeMillis() - userActivitiesStartTime
                    Log.d("UploadTiming", "uploadUserActivities completed in ${asyncTimings["uploadUserActivities"]}ms - Result: $message")

                    // Chain 3: Upload exam results
                    val examResultStartTime = System.currentTimeMillis()
                    Log.d("UploadTiming", "Step 3/6: Starting uploadExamResult")
                    UploadManager.instance?.uploadExamResult(object : SuccessListener {
                        override fun onSuccess(message: String?) {
                            asyncTimings["uploadExamResult"] = System.currentTimeMillis() - examResultStartTime
                            Log.d("UploadTiming", "uploadExamResult completed in ${asyncTimings["uploadExamResult"]}ms - Result: $message")

                            // Chain 4: Upload feedback
                            val feedbackStartTime = System.currentTimeMillis()
                            Log.d("UploadTiming", "Step 4/6: Starting uploadFeedback")
                            UploadManager.instance?.uploadFeedback(object : SuccessListener {
                                override fun onSuccess(message: String?) {
                                    asyncTimings["uploadFeedback"] = System.currentTimeMillis() - feedbackStartTime
                                    Log.d("UploadTiming", "uploadFeedback completed in ${asyncTimings["uploadFeedback"]}ms - Result: $message")

                                    // Chain 5: Upload resources
                                    val resourceStartTime = System.currentTimeMillis()
                                    Log.d("UploadTiming", "Step 5/6: Starting uploadResource")
                                    UploadManager.instance?.uploadResource(object : SuccessListener {
                                        override fun onSuccess(message: String?) {
                                            asyncTimings["uploadResource"] = System.currentTimeMillis() - resourceStartTime
                                            Log.d("UploadTiming", "uploadResource completed in ${asyncTimings["uploadResource"]}ms - Result: $message")

                                            // Chain 6: Upload submit photos
                                            val submitPhotosStartTime = System.currentTimeMillis()
                                            Log.d("UploadTiming", "Step 6/6: Starting uploadSubmitPhotos")
                                            UploadManager.instance?.uploadSubmitPhotos(object : SuccessListener {
                                                override fun onSuccess(message: String?) {
                                                    asyncTimings["uploadSubmitPhotos"] = System.currentTimeMillis() - submitPhotosStartTime
                                                    Log.d("UploadTiming", "uploadSubmitPhotos completed in ${asyncTimings["uploadSubmitPhotos"]}ms - Result: $message")

                                                    // Final: Upload activities
                                                    val activitiesStartTime = System.currentTimeMillis()
                                                    Log.d("UploadTiming", "Final step: Starting uploadActivities")
                                                    UploadManager.instance?.uploadActivities(object : SuccessListener {
                                                        override fun onSuccess(message: String?) {
                                                            asyncTimings["uploadActivities"] = System.currentTimeMillis() - activitiesStartTime
                                                            Log.d("UploadTiming", "uploadActivities completed in ${asyncTimings["uploadActivities"]}ms - Result: $message")

                                                            // Complete the process
                                                            completeUploadProcess()
                                                        }
                                                    })
                                                }
                                            })
                                        }
                                    })
                                }
                            })
                        }
                    })
                }
            })
        }
    }

    protected fun hideKeyboard(view: View?) {
        val `in` = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        `in`.hideSoftInputFromWindow(view?.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    }

    fun saveUserInfoPref(settings: SharedPreferences, password: String?, user: RealmUserModel?) {
        this.settings = settings
        settings.edit {
            putString("userId", user?.id)
            putString("name", user?.name)
            putString("password", password)
            putString("firstName", user?.firstName)
            putString("lastName", user?.lastName)
            putString("middleName", user?.middleName)
            user?.userAdmin?.let { putBoolean("isUserAdmin", it) }
            putLong("lastLogin", System.currentTimeMillis())
        }
    }

    fun alertDialogOkay(message: String?) {
        val builder1 = AlertDialog.Builder(this, R.style.AlertDialogTheme)
        builder1.setMessage(message)
        builder1.setCancelable(true)
        builder1.setNegativeButton(R.string.okay) { dialog: DialogInterface, _: Int -> dialog.cancel() }
        val alert11 = builder1.create()
        alert11.show()
    }

    companion object {
        fun getUserInfo(uri: Uri): Array<String> {
            val ar = arrayOf("", "")
            val info =
                uri.userInfo?.split(":".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
            if ((info?.size ?: 0) > 1) {
                ar[0] = "${info?.get(0)}"
                ar[1] = "${info?.get(1)}"
            }
            return ar
        }
    }

    private fun saveUrlScheme(editor: SharedPreferences.Editor, uri: Uri, url: String?, couchdbURL: String?) {
        editor.putString("url_Scheme", uri.scheme)
        editor.putString("url_Host", uri.host)
        editor.putInt("url_Port", if (uri.port == -1) (if (uri.scheme == "http") 80 else 443) else uri.port)
        editor.putString("serverURL", url)
        editor.putString("couchdbURL", couchdbURL)
    }

    fun fetchAndLogUserSecurityData(name: String, securityCallback: SecurityDataCallback? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiInterface = client?.create(ApiInterface::class.java)
                val userDocUrl = "${getUrl()}/tablet_users/org.couchdb.user:$name"
                val response = apiInterface?.getJsonObject(Utilities.header, userDocUrl)?.execute()

                if (response?.isSuccessful == true && response.body() != null) {
                    val userDoc = response.body()
                    val derivedKey = userDoc?.get("derived_key")?.asString
                    val salt = userDoc?.get("salt")?.asString
                    val passwordScheme = userDoc?.get("password_scheme")?.asString
                    val iterations = userDoc?.get("iterations")?.asString
                    val userId = userDoc?.get("_id")?.asString
                    val rev = userDoc?.get("_rev")?.asString
                    withContext(Dispatchers.Main) {
                        updateRealmUserSecurityData(name, userId, rev, derivedKey, salt, passwordScheme, iterations, securityCallback)
                    }

                } else {
                    withContext(Dispatchers.Main) {
                        securityCallback?.onSecurityDataUpdated()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    e.printStackTrace()
                    securityCallback?.onSecurityDataUpdated()
                }
            }
        }
    }

    private fun updateRealmUserSecurityData(name: String, userId: String?, rev: String?, derivedKey: String?, salt: String?, passwordScheme: String?, iterations: String?, securityCallback: SecurityDataCallback? = null) {
        try {
            val realm = DatabaseService(this).realmInstance
            realm.executeTransactionAsync({ transactionRealm ->
                val user = transactionRealm.where(RealmUserModel::class.java)
                    .equalTo("name", name)
                    .findFirst()

                if (user != null) {
                    user._id = userId
                    user._rev = rev
                    user.derived_key = derivedKey
                    user.salt = salt
                    user.password_scheme = passwordScheme
                    user.iterations = iterations
                    user.isUpdated = false
                }
            }, {
                securityCallback?.onSecurityDataUpdated()
            }) { error ->
                error.printStackTrace()
                securityCallback?.onSecurityDataUpdated()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            securityCallback?.onSecurityDataUpdated()
        }
    }

    fun logDatabaseDiagnostics() {
        try {
            val realm = DatabaseService(this).realmInstance
            val realmFile = File(realm.path)
            val dbSizeMB = realmFile.length() / (1024 * 1024)

            Log.d("DatabaseDiagnostics", "=== DATABASE DIAGNOSTICS ===")
            Log.d("DatabaseDiagnostics", "Database file path: ${realm.path}")
            Log.d("DatabaseDiagnostics", "Database size: ${dbSizeMB} MB (${realmFile.length()} bytes)")

            // Check record counts for all major tables
            Log.d("DatabaseDiagnostics", "=== RECORD COUNTS ===")
            Log.d("DatabaseDiagnostics", "RealmFeedback: ${realm.where(RealmFeedback::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmSubmission: ${realm.where(RealmSubmission::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmOfflineActivity: ${realm.where(RealmOfflineActivity::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmMyLibrary: ${realm.where(RealmMyLibrary::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmSubmitPhotos: ${realm.where(RealmSubmitPhotos::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmCourseProgress: ${realm.where(RealmCourseProgress::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmRating: ${realm.where(RealmRating::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmNews: ${realm.where(RealmNews::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmTeamTask: ${realm.where(RealmTeamTask::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmMyTeam: ${realm.where(RealmMyTeam::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmTeamLog: ${realm.where(RealmTeamLog::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmMeetup: ${realm.where(RealmMeetup::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmAchievement: ${realm.where(RealmAchievement::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmResourceActivity: ${realm.where(RealmResourceActivity::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmCourseActivity: ${realm.where(RealmCourseActivity::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmSearchActivity: ${realm.where(RealmSearchActivity::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmApkLog: ${realm.where(RealmApkLog::class.java).count()}")
            Log.d("DatabaseDiagnostics", "RealmNewsLog: ${realm.where(RealmNewsLog::class.java).count()}")

            realm.close()
        } catch (e: Exception) {
            Log.e("DatabaseDiagnostics", "Error getting database diagnostics: ${e.message}", e)
        }
    }

}
