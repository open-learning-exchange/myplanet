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

    fun startUpload(source: String, userName: String? = null, securityCallback: SecurityDataCallback? = null) {
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

        customProgressDialog.setText(context.getString(R.string.uploading_data_to_server_please_wait))
        customProgressDialog.show()
        UploadManager.instance?.uploadAchievement()
        UploadManager.instance?.uploadNews()
        UploadManager.instance?.uploadResourceActivities("")
        UploadManager.instance?.uploadCourseActivities()
        UploadManager.instance?.uploadSearchActivity()
        UploadManager.instance?.uploadTeams()
        UploadManager.instance?.uploadRating()
        UploadManager.instance?.uploadTeamTask()
        UploadManager.instance?.uploadMeetups()
        UploadManager.instance?.uploadSubmissions()
        UploadManager.instance?.uploadCrashLog()

        val asyncOperationsCounter = AtomicInteger(0)
        val totalAsyncOperations = 6

        fun checkAllOperationsComplete() {
            if (asyncOperationsCounter.incrementAndGet() == totalAsyncOperations) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        customProgressDialog.dismiss()
                        Toast.makeText(this@ProcessUserDataActivity, "upload complete", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        UploadToShelfService.instance?.uploadUserData {
            UploadToShelfService.instance?.uploadHealth()
            checkAllOperationsComplete()
        }

        UploadManager.instance?.uploadUserActivities(object : SuccessListener {
            override fun onSuccess(message: String?) {
                checkAllOperationsComplete()
            }
        })

        UploadManager.instance?.uploadExamResult(object : SuccessListener {
            override fun onSuccess(message: String?) {
                checkAllOperationsComplete()
            }
        })

        UploadManager.instance?.uploadFeedback(object : SuccessListener {
            override fun onSuccess(message: String?) {
                checkAllOperationsComplete()
            }
        })

        UploadManager.instance?.uploadResource(object : SuccessListener {
            override fun onSuccess(message: String?) {
                checkAllOperationsComplete()
            }
        })

        UploadManager.instance?.uploadSubmitPhotos(object : SuccessListener {
            override fun onSuccess(message: String?) {
                checkAllOperationsComplete()
            }
        })

        UploadManager.instance?.uploadActivities(object : SuccessListener {
            override fun onSuccess(message: String?) {
                checkAllOperationsComplete()
            }
        })
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
}
