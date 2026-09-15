package org.ole.planet.myplanet.ui.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.URLUtil
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BasePermissionActivity
import org.ole.planet.myplanet.callback.OnChangedListener
import org.ole.planet.myplanet.callback.OnSuccessListener
import org.ole.planet.myplanet.model.Download
import org.ole.planet.myplanet.repository.SyncRepository
import org.ole.planet.myplanet.repository.SyncUiState
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UploadToShelfService
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DialogUtils
import org.ole.planet.myplanet.utils.DialogUtils.showAlert
import org.ole.planet.myplanet.utils.DialogUtils.showError
import org.ole.planet.myplanet.utils.FileUtils.installApk
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
abstract class ProcessUserDataActivity : BasePermissionActivity(), OnSuccessListener {
    @Inject
    lateinit var syncRepository: SyncRepository


    @Inject
    lateinit var prefData: SharedPrefManager

    @Inject
    lateinit var uploadToShelfService: UploadToShelfService

    @Inject
    lateinit var userRepository: UserRepository

    val customProgressDialog: DialogUtils.CustomProgressDialog by lazy {
        DialogUtils.CustomProgressDialog(this)
    }

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

    fun checkDownloadResult(download: Download?) {
        lifecycleScope.launch {
            if (!isFinishing && !isDestroyed) {
                customProgressDialog.show()
                customProgressDialog.setText("${getString(R.string.downloading)} ${download?.progress}% ${getString(R.string.complete)}")
                customProgressDialog.setProgress(download?.progress ?: 0)
                if (download?.completeAll == true) {
                    safelyDismissDialog()
                    installApk(this@ProcessUserDataActivity, download.fileUrl)
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
            .putExtra("from_login", true)
        startActivity(dashboard)
        finish()
    }

    fun changeLogoColor() {
        val logo = findViewById<ImageView>(R.id.logoImageView)
        logo.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
    }

    fun setUrlParts(url: String, password: String): String {
        val uri = url.toUri()

        var couchdbURL: String
        val urlUser: String
        val urlPwd: String
        if (url.contains("@")) {
            val (u, p) = UrlUtils.getUserInfo(uri.userInfo)
            urlUser = u
            urlPwd = p
            couchdbURL = url
        } else if (TextUtils.isEmpty(password)) {
            showAlert(this, "", getString(R.string.pin_is_required))
            return ""
        } else {
            urlUser = "satellite"
            urlPwd = password
            val port = if (uri.port == -1) (if (uri.scheme == "http") 80 else 443) else uri.port
            couchdbURL = "${uri.scheme}://$urlUser:$urlPwd@${uri.host}:$port"
        }

        prefData.setServerPin(password)
        prefData.setUrlScheme(uri.scheme ?: "")
        prefData.setUrlHost(uri.host ?: "")
        prefData.setServerUrl(url)
        prefData.setCouchdbUrl(couchdbURL)
        prefData.setUrlUser(urlUser)
        prefData.setUrlPwd(urlPwd)

        return UrlUtils.dbUrl(couchdbURL)
    }

    fun isUrlValid(url: String): Boolean {
        if (!URLUtil.isValidUrl(url)) {
            showAlert(this, getString(R.string.invalid_url), getString(R.string.please_enter_valid_url_to_continue))
            return false
        }
        if (url == Constants.HTTP_PROTOCOL || url == Constants.HTTPS_PROTOCOL) {
            showAlert(this, getString(R.string.invalid_url), getString(R.string.please_enter_valid_url_to_continue))
            return false
        }
        return true
    }

    fun startUpload(source: String, userName: String? = null, securityCallback: OnChangedListener? = null) {
        when (source) {
            "becomeMember" -> uploadMemberData(userName, securityCallback)
            "login" -> uploadLoginData()
            else -> uploadBulkData()
        }
    }

    private fun uploadMemberData(userName: String?, securityCallback: OnChangedListener?) {
        uploadToShelfService.uploadSingleUserData(userName, object : OnSuccessListener {
            override fun onSuccess(success: String?) {
                uploadToShelfService.uploadSingleUserHealth("org.couchdb.user:${userName}", object : OnSuccessListener {
                    override fun onSuccess(success: String?) {
                        userName?.let { name ->
                            fetchAndLogUserSecurityData(name, securityCallback)
                        } ?: run {
                            securityCallback?.onChanged()
                        }
                    }
                })
            }
        })
    }

    private fun uploadLoginData() {
        val flow = syncRepository.uploadLoginData()

        collectWhenStarted(flow.takeWhile { value ->
            if (value is SyncUiState.Success) {
                onSuccess(value.message)
                false
            } else if (value is SyncUiState.Error) {
                false
            } else {
                true
            }
        }) {}
    }

    private fun uploadBulkData() {
        customProgressDialog.setText(this.getString(R.string.uploading_data_to_server_please_wait))
        customProgressDialog.show()

        val flow = syncRepository.uploadBulkData()

        collectWhenStarted(flow.takeWhile { value ->
            if (value is SyncUiState.Success) {
                safelyDismissDialog()
                Toast.makeText(this@ProcessUserDataActivity, "upload complete", Toast.LENGTH_SHORT).show()
                false
            } else if (value is SyncUiState.Error) {
                safelyDismissDialog()
                false
            } else {
                true
            }
        }) {}
    }

    protected fun hideKeyboard(view: View?) {
        val `in` = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        `in`.hideSoftInputFromWindow(view?.windowToken, 0)
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
            val (u, p) = UrlUtils.getUserInfo(uri.userInfo)
            return arrayOf(u, p)
        }
    }

    fun fetchAndLogUserSecurityData(name: String, securityCallback: OnChangedListener? = null) {
        lifecycleScope.launch {
            try {
                userRepository.fetchUserSecurityData(name)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(dispatcherProvider.main) {
                    securityCallback?.onChanged()
                }
            }
        }
    }
}
