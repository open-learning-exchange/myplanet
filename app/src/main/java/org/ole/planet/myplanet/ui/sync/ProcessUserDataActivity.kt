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
import com.google.android.material.textfield.TextInputLayout
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.PermissionActivity
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UploadToShelfService
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.DialogUtils.showAlert
import org.ole.planet.myplanet.utilities.DialogUtils.showError
import org.ole.planet.myplanet.utilities.FileUtils.installApk
import kotlin.math.roundToInt
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

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
        if (customProgressDialog.isShowing() == true && !isFinishing) {
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

    fun startUpload(source: String) {
        if (source == "becomeMember") {
            // Original code for becomeMember
            UploadToShelfService.instance?.uploadUserData {
                UploadToShelfService.instance?.uploadHealth()
            }
            return
        } else if (source == "login") {
            // Original code for login
            UploadManager.instance?.uploadUserActivities(this@ProcessUserDataActivity)
            return
        }

        // Full upload process with optimizations
        val totalStartTime = System.currentTimeMillis()
        Log.d("UploadProcess", "Starting complete upload process at ${formatTime(totalStartTime)}")

        customProgressDialog.setText(context.getString(R.string.uploading_data_to_server_please_wait))
        customProgressDialog.show()

        // Create a counter to track completion of async operations
        val asyncOperationsCounter = AtomicInteger(0)
        val totalAsyncOperations = 6 // Count of async operations with callbacks

        // Create a list to store timing information for summary
        val timingResults = Collections.synchronizedList(mutableListOf<UploadTiming>())

        // Function to check if all operations are complete
        fun checkAllOperationsComplete() {
            if (asyncOperationsCounter.incrementAndGet() == totalAsyncOperations) {
                val totalEndTime = System.currentTimeMillis()
                val totalDuration = totalEndTime - totalStartTime

                // Generate summary report
                val sortedResults = timingResults.sortedByDescending { it.duration }

                val sb = StringBuilder()
                sb.appendLine("====== UPLOAD PROCESS SUMMARY ======")
                sb.appendLine("Total upload duration: ${totalDuration}ms (${totalDuration/1000.0}s)")
                sb.appendLine("\nProcess times (sorted by duration):")

                sortedResults.forEachIndexed { index, timing ->
                    sb.appendLine("${index + 1}. ${timing.name}: ${timing.duration}ms")
                }

                Log.d("UploadProcess", sb.toString())
                Log.d("UploadProcess", "Complete upload process finished in ${totalDuration}ms (${totalDuration/1000.0} seconds)")

                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        customProgressDialog.dismiss()
                        Toast.makeText(this@ProcessUserDataActivity, "upload complete", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Helper function to time operations
        fun timeOperation(name: String, operation: () -> Unit) {
            val startTime = System.currentTimeMillis()
            Log.d("UploadProcess", "Starting $name upload at ${formatTime(startTime)}")

            operation()

            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            timingResults.add(UploadTiming(name, duration))
            Log.d("UploadProcess", "$name upload completed in ${duration}ms")
        }

        // OPTIMIZATION: Group operations by their threading requirements

        // 1. First group: Operations that can be run sequentially and quickly
        timeOperation("Achievement") { UploadManager.instance?.uploadAchievement() }
        timeOperation("News") { UploadManager.instance?.uploadNews() }
        timeOperation("ResourceActivities") { UploadManager.instance?.uploadResourceActivities("") }
        timeOperation("CourseActivities") { UploadManager.instance?.uploadCourseActivities() }
        timeOperation("SearchActivity") { UploadManager.instance?.uploadSearchActivity() }
        timeOperation("Teams") { UploadManager.instance?.uploadTeams() }
        timeOperation("Rating") { UploadManager.instance?.uploadRating() }
        timeOperation("TeamTask") { UploadManager.instance?.uploadTeamTask() }
        timeOperation("Meetups") { UploadManager.instance?.uploadMeetups() }
        timeOperation("Submissions") { UploadManager.instance?.uploadSubmissions() }
        timeOperation("CrashLog") { UploadManager.instance?.uploadCrashLog() }

        // 2. Second group: Operations with callbacks that need to be handled properly

        // UserData + Health (sequential operation with its own callback)
        val userDataStartTime = System.currentTimeMillis()
        Log.d("UploadProcess", "Starting UserData upload at ${formatTime(userDataStartTime)}")

        UploadToShelfService.instance?.uploadUserData {
            val userDataEndTime = System.currentTimeMillis()
            val userDataDuration = userDataEndTime - userDataStartTime
            timingResults.add(UploadTiming("UserData", userDataDuration))
            Log.d("UploadProcess", "User data upload completed in ${userDataDuration}ms")

            // Health data needs to run after user data completes
            val healthStartTime = System.currentTimeMillis()
            Log.d("UploadProcess", "Starting Health data upload at ${formatTime(healthStartTime)}")

            UploadToShelfService.instance?.uploadHealth()

            val healthEndTime = System.currentTimeMillis()
            val healthDuration = healthEndTime - healthStartTime
            timingResults.add(UploadTiming("Health", healthDuration))
            Log.d("UploadProcess", "Health data upload completed in ${healthDuration}ms")

            checkAllOperationsComplete()
        }

        // UserActivities with callback
        val userActivitiesStartTime = System.currentTimeMillis()
        Log.d("UploadProcess", "Starting UserActivities upload at ${formatTime(userActivitiesStartTime)}")
        UploadManager.instance?.uploadUserActivities(object : SuccessListener {
            override fun onSuccess(message: String?) {
                val userActivitiesEndTime = System.currentTimeMillis()
                val userActivitiesDuration = userActivitiesEndTime - userActivitiesStartTime
                timingResults.add(UploadTiming("UserActivities", userActivitiesDuration))
                Log.d("UploadProcess", "User activities upload completed in ${userActivitiesDuration}ms")
                checkAllOperationsComplete()
            }
        })

        // ExamResult with callback
        val examResultStartTime = System.currentTimeMillis()
        Log.d("UploadProcess", "Starting ExamResult upload at ${formatTime(examResultStartTime)}")
        UploadManager.instance?.uploadExamResult(object : SuccessListener {
            override fun onSuccess(message: String?) {
                val examResultEndTime = System.currentTimeMillis()
                val examResultDuration = examResultEndTime - examResultStartTime
                timingResults.add(UploadTiming("ExamResult", examResultDuration))
                Log.d("UploadProcess", "Exam result upload completed in ${examResultDuration}ms: $message")
                checkAllOperationsComplete()
            }
        })

        // Feedback with callback
        val feedbackStartTime = System.currentTimeMillis()
        Log.d("UploadProcess", "Starting Feedback upload at ${formatTime(feedbackStartTime)}")
        UploadManager.instance?.uploadFeedback(object : SuccessListener {
            override fun onSuccess(message: String?) {
                val feedbackEndTime = System.currentTimeMillis()
                val feedbackDuration = feedbackEndTime - feedbackStartTime
                timingResults.add(UploadTiming("Feedback", feedbackDuration))
                Log.d("UploadProcess", "Feedback upload completed in ${feedbackDuration}ms: $message")
                checkAllOperationsComplete()
            }
        })

        // Resource with callback
        val resourceStartTime = System.currentTimeMillis()
        Log.d("UploadProcess", "Starting Resource upload at ${formatTime(resourceStartTime)}")
        UploadManager.instance?.uploadResource(object : SuccessListener {
            override fun onSuccess(message: String?) {
                val resourceEndTime = System.currentTimeMillis()
                val resourceDuration = resourceEndTime - resourceStartTime
                timingResults.add(UploadTiming("Resource", resourceDuration))
                Log.d("UploadProcess", "Resource upload completed in ${resourceDuration}ms: $message")
                checkAllOperationsComplete()
            }
        })

        // SubmitPhotos with callback
        val submitPhotosStartTime = System.currentTimeMillis()
        Log.d("UploadProcess", "Starting SubmitPhotos upload at ${formatTime(submitPhotosStartTime)}")
        UploadManager.instance?.uploadSubmitPhotos(object : SuccessListener {
            override fun onSuccess(message: String?) {
                val submitPhotosEndTime = System.currentTimeMillis()
                val submitPhotosDuration = submitPhotosEndTime - submitPhotosStartTime
                timingResults.add(UploadTiming("SubmitPhotos", submitPhotosDuration))
                Log.d("UploadProcess", "Submit photos upload completed in ${submitPhotosDuration}ms: $message")
                checkAllOperationsComplete()
            }
        })

        // Activities with callback (needs to run last as it tracks completion)
        val activitiesStartTime = System.currentTimeMillis()
        Log.d("UploadProcess", "Starting Activities upload at ${formatTime(activitiesStartTime)}")
        UploadManager.instance?.uploadActivities(object : SuccessListener {
            override fun onSuccess(message: String?) {
                val activitiesEndTime = System.currentTimeMillis()
                val activitiesDuration = activitiesEndTime - activitiesStartTime
                timingResults.add(UploadTiming("Activities", activitiesDuration))
                Log.d("UploadProcess", "Activities upload completed in ${activitiesDuration}ms: $message")
                checkAllOperationsComplete()
            }
        })

        runOnUiThread {
            Toast.makeText(this@ProcessUserDataActivity, getString(R.string.uploading_activities_to_server_please_wait), Toast.LENGTH_SHORT).show()
        }
    }

    // Helper function to format timestamp
    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    protected fun hideKeyboard(view: View?) {
        val `in` = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        `in`.hideSoftInputFromWindow(view?.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    }

    fun saveUserInfoPref(settings: SharedPreferences, password: String?, user: RealmUserModel) {
        this.settings = settings
        settings.edit {
            putString("userId", user.id)
            putString("name", user.name)
            putString("password", password)
            putString("firstName", user.firstName)
            putString("lastName", user.lastName)
            putString("middleName", user.middleName)
            user.userAdmin?.let { putBoolean("isUserAdmin", it) }
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
}

data class UploadTiming(val name: String, val duration: Long)
