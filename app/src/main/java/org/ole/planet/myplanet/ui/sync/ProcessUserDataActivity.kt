package org.ole.planet.myplanet.ui.sync

import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.text.TextUtils
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.PermissionActivity
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UploadToShelfService
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.utilities.DialogUtils.showAlert
import org.ole.planet.myplanet.utilities.DialogUtils.showError
import org.ole.planet.myplanet.utilities.FileUtils.installApk
import org.ole.planet.myplanet.utilities.Utilities
import kotlin.math.roundToInt

abstract class ProcessUserDataActivity : PermissionActivity(), SuccessListener {
    lateinit var settings: SharedPreferences
    @JvmField
    var progressDialog: ProgressDialog? = null
    @JvmField
    var broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DashboardActivity.MESSAGE_PROGRESS && progressDialog != null) {
                val download = intent.getParcelableExtra<Download>("download")
                checkDownloadResult(download, progressDialog!!)
            }
        }
    }

    fun validateEditText(textField: EditText, textLayout: TextInputLayout, err_message: String?): Boolean {
        if (textField.text.toString().trim { it <= ' ' }.isEmpty()) {
            textLayout.error = err_message
            requestFocus(textField)
            return false
        } else {
            textLayout.isErrorEnabled = false
        }
        return true
    }

    fun checkDownloadResult(download: Download?, progressDialog: ProgressDialog) {
        if (!download!!.failed) {
            progressDialog.setMessage(getString(R.string.downloading) + download.progress + "% " + getString(
                    R.string.complete
                ))
            if (download.completeAll) {
                progressDialog.dismiss()
                installApk(this, download.fileUrl!!)
            }
        } else {
            progressDialog.dismiss()
            showError(progressDialog, download.message!!)
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
        logo.setColorFilter(alphaWhite, PorterDuff.Mode.SRC_ATOP)
    }

    fun setUrlParts(url: String, password: String): String {
        val editor = settings.edit()
        val uri = Uri.parse(url)
        var couchdbURL: String
        val url_user: String
        val url_pwd: String
        if (url.contains("@")) {
            val userinfo = getUserInfo(uri)
            url_user = userinfo[0]
            url_pwd = userinfo[1]
            couchdbURL = url
        } else if (TextUtils.isEmpty(password)) {
            showAlert(this, "", getString(R.string.pin_is_required))
            return ""
        } else {
            url_user = "satellite"
            url_pwd = password
            couchdbURL = uri.scheme + "://" + url_user + ":" + url_pwd + "@" + uri.host + ":" + if (uri.port == -1) (if (uri.scheme == "http") 80 else 443) else uri.port
        }
        editor.putString("serverPin", password)
        saveUrlScheme(editor, uri, url, couchdbURL)
        editor.putString("url_user", url_user)
        editor.putString("url_pwd", url_pwd)
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

    fun startUpload() {
        progressDialog!!.setMessage(getString(R.string.uploading_data_to_server_please_wait))
        progressDialog!!.show()
        Utilities.log("Upload : upload started")
        UploadToShelfService.instance?.uploadUserData {
            UploadToShelfService.instance!!.uploadHealth()
        }
        UploadManager.instance?.uploadUserActivities(this)
        UploadManager.instance!!.uploadExamResult(this)
        UploadManager.instance!!.uploadFeedback(this)
        UploadManager.instance!!.uploadAchievement()
        UploadManager.instance!!.uploadResourceActivities("")
        UploadManager.instance!!.uploadCourseActivities()
        UploadManager.instance!!.uploadSearchActivity()
        UploadManager.instance!!.uploadNews()
        UploadManager.instance!!.uploadTeams()
        UploadManager.instance!!.uploadResource(this)
        UploadManager.instance!!.uploadRating()
        UploadManager.instance!!.uploadTeamTask()
        UploadManager.instance!!.uploadCrashLog(this)
        UploadManager.instance!!.uploadSubmitPhotos(this)
        UploadManager.instance!!.uploadActivities(this)
        Toast.makeText(this, getString(R.string.uploading_activities_to_server_please_wait), Toast.LENGTH_SHORT).show()
    }

    protected fun hideKeyboard(view: View) {
        val `in` = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        `in`.hideSoftInputFromWindow(view.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    }

    fun saveUserInfoPref(settings: SharedPreferences, password: String?, user: RealmUserModel) {
        this.settings = settings
        val editor = settings.edit()
        editor.putString("userId", user.id)
        editor.putString("name", user.name)
        editor.putString("password", password)
        editor.putString("firstName", user.firstName)
        editor.putString("lastName", user.lastName)
        editor.putString("middleName", user.middleName)
        editor.putBoolean("isUserAdmin", user.userAdmin!!)
        editor.putLong("lastLogin", System.currentTimeMillis())
        editor.apply()
    }

    fun alertDialogOkay(Message: String?) {
        val builder1 = AlertDialog.Builder(this)
        builder1.setMessage(Message)
        builder1.setCancelable(true)
        builder1.setNegativeButton(R.string.okay) { dialog: DialogInterface, _: Int -> dialog.cancel() }
        val alert11 = builder1.create()
        alert11.show()
    }

    private fun getUserInfo(uri: Uri): Array<String> {
        val ar = arrayOf("", "")
        val info = uri.userInfo!!.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (info.size > 1) {
            ar[0] = info[0]
            ar[1] = info[1]
        }
        return ar
    }

    private fun saveUrlScheme(editor: SharedPreferences.Editor, uri: Uri, url: String?, couchdbURL: String?) {
        editor.putString("url_Scheme", uri.scheme)
        editor.putString("url_Host", uri.host)
        editor.putInt("url_Port", if (uri.port == -1) (if (uri.scheme == "http") 80 else 443) else uri.port)
        editor.putString("serverURL", url)
        editor.putString("couchdbURL", couchdbURL)
    }
}
