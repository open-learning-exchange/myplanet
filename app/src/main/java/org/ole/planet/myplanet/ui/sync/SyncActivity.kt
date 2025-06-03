package org.ole.planet.myplanet.ui.sync

import android.Manifest
import android.app.Activity
import android.content.*
import android.graphics.drawable.AnimationDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.*
import android.view.*
import android.webkit.URLUtil
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.*
import io.realm.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.ole.planet.myplanet.BuildConfig
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.MainApplication.Companion.createLog
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseResourceFragment.Companion.backgroundDownload
import org.ole.planet.myplanet.base.BaseResourceFragment.Companion.getAllLibraryList
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.*
import org.ole.planet.myplanet.datamanager.*
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.Service.*
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.service.*
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.team.AdapterTeam.OnUserSelectedListener
import org.ole.planet.myplanet.utilities.*
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.androidDecrypter
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Constants.autoSynFeature
import org.ole.planet.myplanet.utilities.DialogUtils.getUpdateDialog
import org.ole.planet.myplanet.utilities.DialogUtils.showAlert
import org.ole.planet.myplanet.utilities.DialogUtils.showSnack
import org.ole.planet.myplanet.utilities.DialogUtils.showWifiSettingDialog
import org.ole.planet.myplanet.utilities.DownloadUtils.downloadAllFiles
import org.ole.planet.myplanet.utilities.NetworkUtils.extractProtocol
import org.ole.planet.myplanet.utilities.NetworkUtils.getCustomDeviceName
import org.ole.planet.myplanet.utilities.NetworkUtils.isNetworkConnectedFlow
import org.ole.planet.myplanet.utilities.NotificationUtil.cancelAll
import org.ole.planet.myplanet.utilities.Utilities.getRelativeTime
import org.ole.planet.myplanet.utilities.Utilities.openDownloadService
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri
import androidx.core.content.edit
import org.ole.planet.myplanet.utilities.FileUtils.availableOverTotalMemoryFormattedString
import kotlin.isInitialized

abstract class SyncActivity : ProcessUserDataActivity(), SyncListener, CheckVersionCallback,
    OnUserSelectedListener, ConfigurationIdListener {
    private lateinit var syncDate: TextView
    lateinit var lblLastSyncDate: TextView
    lateinit var btnSignin: Button
    lateinit var lblVersion: TextView
    lateinit var tvAvailableSpace: TextView
    lateinit var btnGuestLogin: Button
    lateinit var becomeMember: Button
    lateinit var btnFeedback: Button
    lateinit var openCommunity: Button
    lateinit var btnLang: Button
    lateinit var inputName: EditText
    lateinit var inputPassword: EditText
    private lateinit var intervalLabel: TextView
    lateinit var spinner: Spinner
    private lateinit var syncSwitch: SwitchCompat
    lateinit var mRealm: Realm
    lateinit var editor: SharedPreferences.Editor
    private var syncTimeInterval = intArrayOf(60 * 60, 3 * 60 * 60)
    lateinit var syncIcon: ImageView
    lateinit var syncIconDrawable: AnimationDrawable
    lateinit var prefData: SharedPrefManager
    lateinit var profileDbHandler: UserProfileDbHandler
    private lateinit var spnCloud: Spinner
    private lateinit var protocolCheckIn: RadioGroup
    private lateinit var serverUrl: EditText
    private lateinit var serverPassword: EditText
    private lateinit var serverAddresses: RecyclerView
    private lateinit var syncToServerText: TextView
    var selectedTeamId: String? = null
    lateinit var positiveAction: View
    private lateinit var neutralAction: View
    lateinit var processedUrl: String
    var isSync = false
    var forceSync = false
    private var syncFailed = false
    lateinit var btnSignIn: Button
    lateinit var defaultPref: SharedPreferences
    lateinit var service: Service
    var currentDialog: MaterialDialog? = null
    private var serverConfigAction = ""
    private var serverCheck = true
    private var showAdditionalServers = false
    private var serverAddressAdapter : ServerAddressAdapter? = null
    private lateinit var serverListAddresses: List<ServerAddressesModel>
    private var isProgressDialogShowing = false

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        editor = settings.edit()
        mRealm = DatabaseService(this).realmInstance
        mRealm = Realm.getDefaultInstance()
        requestAllPermissions()
        prefData = SharedPrefManager(this)
        profileDbHandler = UserProfileDbHandler(this)
        defaultPref = PreferenceManager.getDefaultSharedPreferences(this)
        processedUrl = Utilities.getUrl()
    }

    override fun onConfigurationIdReceived(id: String, code: String, url: String, defaultUrl: String, isAlternativeUrl: Boolean, callerActivity: String) {
        val savedId = settings.getString("configurationId", null)

        when (callerActivity) {
            "LoginActivity", "DashboardActivity"-> {
                if (isAlternativeUrl) {
                    processAlternativeUrl(url, settings, editor, defaultUrl)
                }
                isSync = false
                forceSync = true
                service.checkVersion(this, settings)
            }
            else -> {
                if (serverConfigAction == "sync") {
                    if (savedId == null) {
                        editor.putString("configurationId", id).apply()
                        editor.putString("communityName", code).apply()
                        currentDialog?.let {
                            continueSync(it, url, isAlternativeUrl, defaultUrl)
                        }
                    } else if (id == savedId) {
                        currentDialog?.let {
                            continueSync(it, url, isAlternativeUrl, defaultUrl)
                        }
                    } else {
                        clearDataDialog(getString(R.string.you_want_to_connect_to_a_different_server), false)
                    }
                } else if (serverConfigAction == "save") {
                    if (savedId == null || id == savedId) {
                        currentDialog?.let { saveConfigAndContinue(it, "", false, defaultUrl) }
                    } else {
                        clearDataDialog(getString(R.string.you_want_to_connect_to_a_different_server), false)
                    }
                }
            }
        }
    }

    fun processAlternativeUrl(url: String, settings: SharedPreferences, editor: SharedPreferences.Editor, defaultUrl: String): String {
        val password = "${settings.getString("serverPin", "")}"
        val uri = url.toUri()
        val couchdbURL: String
        val urlUser: String
        val urlPwd: String

        if (url.contains("@")) {
            val userinfo = getUserInfo(uri)
            urlUser = userinfo[0]
            urlPwd = userinfo[1]
            couchdbURL = url
        } else {
            urlUser = "satellite"
            urlPwd = password
            couchdbURL = "${uri.scheme}://$urlUser:$urlPwd@${uri.host}:${if (uri.port == -1) (if (uri.scheme == "http") 80 else 443) else uri.port}"
        }

        editor.putString("serverPin", password)
        editor.putString("url_user", urlUser)
        editor.putString("url_pwd", urlPwd)
        editor.putString("url_Scheme", uri.scheme)
        editor.putString("url_Host", uri.host)
        editor.putString("alternativeUrl", url)
        editor.putString("processedAlternativeUrl", couchdbURL)
        editor.putBoolean("isAlternativeUrl", true)
        editor.apply()

        return couchdbURL
    }

    private fun clearDataDialog(message: String, config: Boolean, onCancel: () -> Unit = {}) {
        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setMessage(message)
            .setPositiveButton(getString(R.string.clear_data)) { dialog, _ ->
                (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false

                lifecycleScope.launch {
                    try {
                        customProgressDialog.setText(getString(R.string.clearing_data))
                        customProgressDialog.show()

                        clearRealmDb()
                        prefData.setManualConfig(config)
                        clearSharedPref()

                        delay(500)
                        restartApp()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        customProgressDialog.dismiss()
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                onCancel()
            }
            .setCancelable(false)
            .show()
    }

    private fun clearInternalStorage() {
        val myDir = File(Utilities.SD_PATH)
        if (myDir.isDirectory) {
            val children = myDir.list()
            if (children != null) {
                for (i in children.indices) {
                    File(myDir, children[i]).delete()
                }
            }
        }
        editor.putBoolean("firstRun", false).apply()
    }

    fun sync(dialog: MaterialDialog) {
        spinner = dialog.findViewById(R.id.intervalDropper) as Spinner
        syncSwitch = dialog.findViewById(R.id.syncSwitch) as SwitchCompat
        intervalLabel = dialog.findViewById(R.id.intervalLabel) as TextView
        syncSwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            setSpinnerVisibility(isChecked)
        }
        syncSwitch.isChecked = settings.getBoolean("autoSync", true)
        dateCheck(dialog)
    }

    private fun setSpinnerVisibility(isChecked: Boolean) {
        if (isChecked) {
            intervalLabel.visibility = View.VISIBLE
            spinner.visibility = View.VISIBLE
        } else {
            spinner.visibility = View.GONE
            intervalLabel.visibility = View.GONE
        }
    }

    suspend fun isServerReachable(processedUrl: String?, type: String): Boolean {
        return withContext(Dispatchers.IO) {
            val apiInterface = client?.create(ApiInterface::class.java)
            try {
                val response = if (settings.getBoolean("isAlternativeUrl", false)){
                     if (processedUrl?.contains("/db") == true) {
                         val processedUrlWithoutDb = processedUrl.replace("/db", "")
                         apiInterface?.isPlanetAvailable("$processedUrlWithoutDb/db/_all_dbs")?.execute()
                    } else {
                         apiInterface?.isPlanetAvailable("$processedUrl/db/_all_dbs")?.execute()
                    }
                } else {
                    apiInterface?.isPlanetAvailable("$processedUrl/_all_dbs")?.execute()
                }

                when {
                    response?.isSuccessful == true -> {
                        val ss = response.body()?.string()
                        val myList = ss?.split(",")?.dropLastWhile { it.isEmpty() }

                        if ((myList?.size ?: 0) < 8) {
                            withContext(Dispatchers.Main) {
                                customProgressDialog.dismiss()
                                alertDialogOkay(context.getString(R.string.check_the_server_address_again_what_i_connected_to_wasn_t_the_planet_server))
                            }
                            false
                        } else {
                            withContext(Dispatchers.Main) {
                                startSync(type)
                            }
                            true
                        }
                    }
                    else -> {
                        syncFailed = true
                        val protocol = extractProtocol("$processedUrl")
                        val errorMessage = when (protocol) {
                            context.getString(R.string.http_protocol) -> context.getString(R.string.device_couldn_t_reach_local_server)
                            context.getString(R.string.https_protocol) -> context.getString(R.string.device_couldn_t_reach_nation_server)
                            else -> ""
                        }
                        withContext(Dispatchers.Main) {
                            customProgressDialog.dismiss()
                            alertDialogOkay(errorMessage)
                        }
                        false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private fun dateCheck(dialog: MaterialDialog) {
        // Check if the user never synced
        syncDate = dialog.findViewById(R.id.lastDateSynced) as TextView
        syncDate.text = getString(R.string.last_sync_date, convertDate())
        syncDropdownAdd()
    }

    // Converts OS date to human date
    private fun convertDate(): String {
        val lastSynced = settings.getLong("LastSync", 0)
        return if (lastSynced == 0L) {
            " Never Synced"
        } else {
            getRelativeTime(lastSynced)
        }
    }

    private fun syncDropdownAdd() {
        val list: MutableList<String> = ArrayList()
        list.add("1 " + getString(R.string.hour))
        list.add("3 " + getString(R.string.hours))
        val spinnerArrayAdapter = ArrayAdapter(this, R.layout.spinner_item, list)
        spinnerArrayAdapter.setDropDownViewResource(R.layout.spinner_item)
        spinner.adapter = spinnerArrayAdapter
    }

    private fun saveSyncInfoToPreference() {
        editor.putBoolean("autoSync", syncSwitch.isChecked)
        editor.putInt("autoSyncInterval", syncTimeInterval[spinner.selectedItemPosition])
        editor.putInt("autoSyncPosition", spinner.selectedItemPosition)
        editor.apply()
    }

    fun authenticateUser(settings: SharedPreferences?, username: String?, password: String?, isManagerMode: Boolean): Boolean {
        return try {
            if (settings != null) {
                this.settings = settings
            }
            if (mRealm.isEmpty) {
                alertDialogOkay(getString(R.string.server_not_configured_properly_connect_this_device_with_planet_server))
                false
            } else {
                checkName(username, password, isManagerMode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun checkName(username: String?, password: String?, isManagerMode: Boolean): Boolean {
        try {
            val user = mRealm.where(RealmUserModel::class.java).equalTo("name", username).findFirst()
            user?.let {
                if (it._id?.isEmpty() == true) {
                    if (username == it.name && password == it.password) {
                        saveUserInfoPref(settings, password, it)
                        return true
                    }
                } else {
                    if (androidDecrypter(username, password, it.derived_key, it.salt)) {
                        if (isManagerMode && !it.isManager()) return false
                        saveUserInfoPref(settings, password, it)
                        return true
                    }
                }
            }
        } catch (err: Exception) {
            err.printStackTrace()
            return false
        }
        return false
    }

    fun startSync(type: String) {
        SyncManager.instance?.start(this@SyncActivity, type)
    }

    private fun saveConfigAndContinue(dialog: MaterialDialog, url: String, isAlternativeUrl: Boolean, defaultUrl: String): String {
        dialog.dismiss()
        saveSyncInfoToPreference()

        return if (isAlternativeUrl) {
            val password = if (settings.getString("serverPin", "") != "") {
                settings.getString("serverPin", "")!!
            } else {
                (dialog.customView?.findViewById<View>(R.id.input_server_Password) as EditText).text.toString()
            }

            val uri = Uri.parse(url)
            val couchdbURL: String
            val urlUser: String
            val urlPwd: String

            if (url.contains("@")) {
                val userinfo = getUserInfo(uri)
                urlUser = userinfo[0]
                urlPwd = userinfo[1]
                couchdbURL = url
            } else {
                urlUser = "satellite"
                urlPwd = password
                couchdbURL = "${uri.scheme}://$urlUser:$urlPwd@${uri.host}:${if (uri.port == -1) (if (uri.scheme == "http") 80 else 443) else uri.port}"
            }

            editor.putString("serverPin", password)
            editor.putString("url_user", urlUser)
            editor.putString("url_pwd", urlPwd)
            editor.putString("url_Scheme", uri.scheme)
            editor.putString("url_Host", uri.host)
            editor.putString("alternativeUrl", url)
            editor.putString("processedAlternativeUrl", couchdbURL)
            editor.putBoolean("isAlternativeUrl", true)
            editor.apply()

            if (isUrlValid(url)) setUrlParts(defaultUrl, urlPwd) else ""

            couchdbURL
        } else {
            val protocol = settings.getString("serverProtocol", "")
            var url = (dialog.customView?.findViewById<View>(R.id.input_server_url) as EditText).text.toString()
            val pin = (dialog.customView?.findViewById<View>(R.id.input_server_Password) as EditText).text.toString()

            editor.putString("customDeviceName", (dialog.customView?.findViewById<View>(R.id.deviceName) as EditText).text.toString()).apply()

            url = protocol + url

            if (isUrlValid(url)) setUrlParts(url, pin) else ""
        }
    }

    override fun onSyncStarted() {
        customProgressDialog.setText(getString(R.string.syncing_data_please_wait))
        customProgressDialog.show()
        isProgressDialogShowing = true
    }

    override fun onSyncFailed(msg: String?) {
        if (isProgressDialogShowing) {
            customProgressDialog.dismiss()
        }
        if (::syncIconDrawable.isInitialized) {
            syncIconDrawable = syncIcon.drawable as AnimationDrawable
            syncIconDrawable.stop()
            syncIconDrawable.selectDrawable(0)
            syncIcon.invalidateDrawable(syncIconDrawable)
        }
        runOnUiThread {
            showAlert(this@SyncActivity, getString(R.string.sync_failed), msg)
            showWifiSettingDialog(this@SyncActivity)
        }
    }

    override fun onSyncComplete() {
        val activityContext = this@SyncActivity

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var attempt = 0

                while (true) {
                    val realm = Realm.getDefaultInstance()
                    var dataInserted = false

                    try {
                        realm.refresh()
                        val realmResults = realm.where(RealmUserModel::class.java).findAll()
                        if (!realmResults.isEmpty()) {
                            dataInserted = true
                            break
                        }
                    } finally {
                        realm.close()
                    }
                    attempt++
                    delay(1000)
                }

                withContext(Dispatchers.Main) {
                    forceSyncTrigger()
                    val syncedUrl = settings.getString("serverURL", null)?.let { removeProtocol(it) }
                    if (syncedUrl != null && serverListAddresses.any { it.url.replace(Regex("^https?://"), "") == syncedUrl }) {
                        editor.putString("pinnedServerUrl", syncedUrl).apply()
                    }

                    customProgressDialog.dismiss()

                    if (::syncIconDrawable.isInitialized) {
                        syncIconDrawable = syncIcon.drawable as AnimationDrawable
                        syncIconDrawable.stop()
                        syncIconDrawable.selectDrawable(0)
                        syncIcon.invalidateDrawable(syncIconDrawable)
                    }

                    lifecycleScope.launch {
                        createLog("synced successfully", "")
                    }

                    lifecycleScope.launch(Dispatchers.IO) {
                        val pendingLanguage = settings.getString("pendingLanguageChange", null)
                        if (pendingLanguage != null) {
                            withContext(Dispatchers.Main) {
                                editor.remove("pendingLanguageChange").apply()

                                LocaleHelper.setLocale(this@SyncActivity, pendingLanguage)
                                updateUIWithNewLanguage()
                            }
                        }
                    }

                    showSnack(activityContext.findViewById(android.R.id.content), getString(R.string.sync_completed))

                    if (settings.getBoolean("isAlternativeUrl", false)) {
                        editor.putString("alternativeUrl", "")
                        editor.putString("processedAlternativeUrl", "")
                        editor.putBoolean("isAlternativeUrl", false)
                        editor.apply()
                    }

                    downloadAdditionalResources()

                    val betaAutoDownload = defaultPref.getBoolean("beta_auto_download", false)
                    if (betaAutoDownload) {
                        withContext(Dispatchers.IO) {
                            val downloadRealm = Realm.getDefaultInstance()
                            try {
                                backgroundDownload(downloadAllFiles(getAllLibraryList(downloadRealm)))
                            } finally {
                                downloadRealm.close()
                            }
                        }
                    }

                    cancelAll(activityContext)

                    if (activityContext is LoginActivity) {
                        activityContext.updateTeamDropdown()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateUIWithNewLanguage() {
        try {
            if (::lblLastSyncDate.isInitialized) {
                lblLastSyncDate.text = getString(R.string.last_sync, getRelativeTime(Date().time))
            }

            lblVersion.text = getString(R.string.app_version)
            tvAvailableSpace.text = buildString {
                append(getString(R.string.available_space_colon))
                append(" ")
                append(availableOverTotalMemoryFormattedString)
            }

            inputName.hint = getString(R.string.hint_name)
            inputPassword.hint = getString(R.string.password)
            btnSignin.text = getString(R.string.btn_sign_in)
            btnGuestLogin.text = getString(R.string.btn_guest_login)
            becomeMember.text = getString(R.string.become_a_member)
            btnFeedback.text = getString(R.string.feedback)
            openCommunity.text = getString(R.string.open_community)
            val currentLanguage = LocaleHelper.getLanguage(this)
            btnLang.text = getLanguageString(currentLanguage)
            invalidateOptionsMenu()
        } catch (e: Exception) {
            e.printStackTrace()
            recreate()
        }
    }

    fun getLanguageString(languageCode: String): String {
        return when (languageCode) {
            "en" -> getString(R.string.english)
            "es" -> getString(R.string.spanish)
            "so" -> getString(R.string.somali)
            "ne" -> getString(R.string.nepali)
            "ar" -> getString(R.string.arabic)
            "fr" -> getString(R.string.french)
            else -> getString(R.string.english)
        }
    }

    private fun downloadAdditionalResources() {
        val storedJsonConcatenatedLinks = settings.getString("concatenated_links", null)
        if (storedJsonConcatenatedLinks != null) {
            val storedConcatenatedLinks: ArrayList<String> = Json.decodeFromString(storedJsonConcatenatedLinks)
            openDownloadService(context, storedConcatenatedLinks, true)
        }
    }

    fun forceSyncTrigger(): Boolean {
        if (settings.getLong(getString(R.string.last_syncs), 0) <= 0) {
            lblLastSyncDate.text = getString(R.string.last_synced_never)
        } else {
            val lastSyncMillis = settings.getLong(getString(R.string.last_syncs), 0)
            var relativeTime = getRelativeTime(lastSyncMillis)

            if (relativeTime.matches(Regex("^\\d{1,2} seconds ago$"))) {
                relativeTime = getString(R.string.a_few_seconds_ago)
            }

            lblLastSyncDate.text = getString(R.string.last_sync, relativeTime)
        }
        if (autoSynFeature(Constants.KEY_AUTOSYNC_, applicationContext) && autoSynFeature(Constants.KEY_AUTOSYNC_WEEKLY, applicationContext)) {
            return checkForceSync(7)
        } else if (autoSynFeature(Constants.KEY_AUTOSYNC_, applicationContext) && autoSynFeature(Constants.KEY_AUTOSYNC_MONTHLY, applicationContext)) {
            return checkForceSync(30)
        }
        return false
    }

    fun showWifiDialog() {
        if (intent.getBooleanExtra("showWifiDialog", false)) {
            showWifiSettingDialog(this)
        }
    }

    private fun checkForceSync(maxDays: Int): Boolean {
        cal_today = Calendar.getInstance(Locale.ENGLISH)
        cal_last_Sync = Calendar.getInstance(Locale.ENGLISH)
        val lastSyncTime = settings.getLong("LastSync", -1)
        if (lastSyncTime <= 0) {
            return false
        }
        cal_last_Sync.timeInMillis = lastSyncTime
        cal_today.timeInMillis = System.currentTimeMillis()
        val msDiff = cal_today.timeInMillis - cal_last_Sync.timeInMillis
        val daysDiff = TimeUnit.MILLISECONDS.toDays(msDiff)
        return if (daysDiff >= maxDays) {
            val alertDialogBuilder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
            alertDialogBuilder.setMessage("${getString(R.string.it_has_been_more_than)}${(daysDiff - 1)}${getString(R.string.days_since_you_last_synced_this_device)}${getString(R.string.connect_it_to_the_server_over_wifi_and_sync_it_to_reactivate_this_tablet)}")
            alertDialogBuilder.setPositiveButton(R.string.okay) { _: DialogInterface?, _: Int ->
                Toast.makeText(applicationContext, getString(R.string.connect_to_the_server_over_wifi_and_sync_your_device_to_continue), Toast.LENGTH_LONG).show()
            }
            alertDialogBuilder.show()
            true
        } else {
            false
        }
    }

    fun onLogin() {
        editor.putBoolean(Constants.KEY_LOGIN, true).commit()
        openDashboard()

        MainApplication.applicationScope.launch(Dispatchers.IO) {
            try {
                val handler = UserProfileDbHandler(this@SyncActivity)
                handler.onLogin()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val serverUrl = settings.getString("serverURL", "")
            if (!serverUrl.isNullOrEmpty()) {
                val canReachServer = MainApplication.isServerReachable(serverUrl)
                if (canReachServer) {
                    withContext(Dispatchers.Main) {
                        startUpload("login")
                    }
                    withContext(Dispatchers.Default) {
                        val backgroundRealm = Realm.getDefaultInstance()
                        try {
                            TransactionSyncManager.syncDb(backgroundRealm, "login_activities")
                        } finally {
                            backgroundRealm.close()
                        }
                    }
                }
            }
        }
    }

    fun settingDialog() {
        val dialogServerUrlBinding = DialogServerUrlBinding.inflate(LayoutInflater.from(this))
        spnCloud = dialogServerUrlBinding.spnCloud
        protocolCheckIn = dialogServerUrlBinding.radioProtocol
        serverUrl = dialogServerUrlBinding.inputServerUrl
        serverPassword = dialogServerUrlBinding.inputServerPassword
        serverAddresses = dialogServerUrlBinding.serverUrls
        syncToServerText = dialogServerUrlBinding.syncToServerText

        dialogServerUrlBinding.deviceName.setText(NetworkUtils.getDeviceName())
        val contextWrapper = ContextThemeWrapper(this, R.style.AlertDialogTheme)
        val builder = MaterialDialog.Builder(contextWrapper)
        builder.customView(dialogServerUrlBinding.root, true)
            .positiveText(R.string.btn_sync)
            .negativeText(R.string.btn_sync_cancel)
            .neutralText(R.string.btn_sync_save)
            .onPositive { dialog: MaterialDialog, _: DialogAction? ->
                performSync(dialog)
            }
        val dialog = builder.build()
        positiveAction = dialog.getActionButton(DialogAction.POSITIVE)
        neutralAction = dialog.getActionButton(DialogAction.NEUTRAL)
        if (!prefData.getManualConfig()) {
            dialogServerUrlBinding.manualConfiguration.isChecked = false
            showConfigurationUIElements(dialogServerUrlBinding, false, dialog)
        } else {
            dialogServerUrlBinding.manualConfiguration.isChecked = true
            showConfigurationUIElements(dialogServerUrlBinding, true, dialog)
        }
        val configurationId = settings.getString("configurationId", null)

        dialogServerUrlBinding.manualConfiguration.setOnCheckedChangeListener(null)

        dialogServerUrlBinding.manualConfiguration.setOnClickListener {
            if (configurationId != null) {
                dialogServerUrlBinding.manualConfiguration.isChecked = prefData.getManualConfig()
                if (prefData.getManualConfig()) {
                    clearDataDialog(getString(R.string.switching_off_manual_configuration_to_clear_data), false)
                } else {
                    clearDataDialog(getString(R.string.switching_on_manual_configuration_to_clear_data), true)
                }
            } else {
                val newCheckedState = !prefData.getManualConfig()
                prefData.setManualConfig(newCheckedState)
                if (newCheckedState) {
                    prefData.setManualConfig(true)
                    editor.putString("serverURL", "").apply()
                    editor.putString("serverPin", "").apply()
                    dialogServerUrlBinding.radioHttp.isChecked = true
                    editor.putString("serverProtocol", getString(R.string.http_protocol)).apply()
                    showConfigurationUIElements(dialogServerUrlBinding, true, dialog)
                    val communities: List<RealmCommunity> = mRealm.where(RealmCommunity::class.java).sort("weight", Sort.ASCENDING).findAll()
                    val nonEmptyCommunities: MutableList<RealmCommunity> = ArrayList()
                    for (community in communities) {
                        if (community.isValid && !TextUtils.isEmpty(community.name)) {
                            nonEmptyCommunities.add(community)
                        }
                    }
                    dialogServerUrlBinding.spnCloud.adapter = ArrayAdapter(this, R.layout.spinner_item_white, nonEmptyCommunities)
                    dialogServerUrlBinding.spnCloud.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                            onChangeServerUrl()
                        }

                        override fun onNothingSelected(adapterView: AdapterView<*>?) {}
                    }
                    dialogServerUrlBinding.switchServerUrl.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
                        editor.putBoolean("switchCloudUrl", b).apply()
                        dialogServerUrlBinding.spnCloud.visibility = if (b) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                        setUrlAndPin(dialogServerUrlBinding.switchServerUrl.isChecked)
                    }
                    serverUrl.addTextChangedListener(MyTextWatcher(serverUrl))
                    dialogServerUrlBinding.switchServerUrl.isChecked = settings.getBoolean("switchCloudUrl", false)
                    setUrlAndPin(settings.getBoolean("switchCloudUrl", false))
                    protocolSemantics()
                }
                else {
                    prefData.setManualConfig(false)
                    showConfigurationUIElements(dialogServerUrlBinding, false, dialog)
                    editor.putBoolean("switchCloudUrl", false).apply()
                }
            }
        }
        dialogServerUrlBinding.radioProtocol.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            when (checkedId) {
                R.id.radio_http -> editor.putString("serverProtocol", getString(R.string.http_protocol)).apply()
                R.id.radio_https -> editor.putString("serverProtocol", getString(R.string.https_protocol)).apply()
            }
        }
        dialogServerUrlBinding.clearData.setOnClickListener {
            clearDataDialog(getString(R.string.are_you_sure_you_want_to_clear_data), false)
        }

        val isFastSync = settings.getBoolean("fastSync", false)
        dialogServerUrlBinding.fastSync.isChecked = isFastSync
        dialogServerUrlBinding.fastSync.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
            editor.putBoolean("fastSync", b).apply()
        }

        showAdditionalServers = false

        if (::serverListAddresses.isInitialized && settings.getString("serverURL", "")?.isNotEmpty() == true) {
            val filteredList = getFilteredServerList()
            serverAddressAdapter?.updateList(filteredList)

            val pinnedUrl = settings.getString("serverURL", "")
            val pinnedIndex = filteredList.indexOfFirst {
                it.url.replace(Regex("^https?://"), "") == pinnedUrl?.replace(
                    Regex("^https?://"),
                    ""
                )
            }
            if (pinnedIndex != -1) {
                serverAddressAdapter?.setSelectedPosition(pinnedIndex)
            }
        }

        neutralAction.setOnClickListener {
            if (!prefData.getManualConfig()) {
                showAdditionalServers = !showAdditionalServers
                val filteredList = getFilteredServerList()
                serverAddressAdapter?.updateList(filteredList)

                val pinnedUrl = settings.getString("serverURL", "")
                val pinnedIndex = filteredList.indexOfFirst {
                    it.url.replace(Regex("^https?://"), "") == pinnedUrl?.replace(Regex("^https?://"), "")
                }
                if (pinnedIndex != -1) {
                    serverAddressAdapter?.setSelectedPosition(pinnedIndex)
                }

                dialog.getActionButton(DialogAction.NEUTRAL).text =
                    if (showAdditionalServers) {
                        getString(R.string.show_less)
                    } else {
                        getString(R.string.show_more)
                    }
            } else {
                serverConfigAction = "save"
                val protocol = "${settings.getString("serverProtocol", "")}"
                var url = "${serverUrl.text}"
                val pin = "${serverPassword.text}"
                url = protocol + url
                if (isUrlValid(url)) {
                    currentDialog = dialog
                    service.getMinApk(this, url, pin, this, "SyncActivity")
                }
            }
        }
        dialog.show()
        sync(dialog)
        if (!prefData.getManualConfig()) {
            dialog.getActionButton(DialogAction.NEUTRAL).text = getString(R.string.show_more)
        }
    }

    private fun showConfigurationUIElements(binding: DialogServerUrlBinding, manualSelected: Boolean, dialog: MaterialDialog) {
        serverAddresses.visibility = if (manualSelected) View.GONE else View.VISIBLE
        syncToServerText.visibility = if (manualSelected) View.GONE else View.VISIBLE
        positiveAction.visibility = if (manualSelected) View.VISIBLE else View.GONE
        dialog.getActionButton(DialogAction.NEUTRAL).text =
            if (manualSelected) {
                getString(R.string.btn_sync_save)
            } else {
                if (showAdditionalServers) {
                    getString(R.string.show_less)
                } else {
                    getString(R.string.show_more)
                }
            }
        binding.ltAdvanced.visibility = if (manualSelected) View.VISIBLE else View.GONE
        binding.switchServerUrl.visibility = if (manualSelected) View.VISIBLE else View.GONE

        if (manualSelected) {
            if (settings.getString("serverProtocol", "") == getString(R.string.http_protocol)) {
                binding.radioHttp.isChecked = true
                editor.putString("serverProtocol", getString(R.string.http_protocol)).apply()
            } else if (settings.getString("serverProtocol", "") == getString(R.string.https_protocol)) {
                binding.radioHttps.isChecked = true
                editor.putString("serverProtocol", getString(R.string.https_protocol)).apply()
            }
            serverUrl.setText(settings.getString("serverURL", "")?.let { removeProtocol(it) })
            serverPassword.setText(settings.getString("serverPin", ""))
            serverUrl.isEnabled = true
            serverPassword.isEnabled = true
        } else {
            serverAddresses.layoutManager = LinearLayoutManager(this)
            serverListAddresses = listOf(
                ServerAddressesModel(getString(R.string.sync_planet_learning), BuildConfig.PLANET_LEARNING_URL),
                ServerAddressesModel(getString(R.string.sync_guatemala), BuildConfig.PLANET_GUATEMALA_URL),
                ServerAddressesModel(getString(R.string.sync_san_pablo), BuildConfig.PLANET_SANPABLO_URL),
                ServerAddressesModel(getString(R.string.sync_planet_earth), BuildConfig.PLANET_EARTH_URL),
                ServerAddressesModel(getString(R.string.sync_somalia), BuildConfig.PLANET_SOMALIA_URL),
                ServerAddressesModel(getString(R.string.sync_vi), BuildConfig.PLANET_VI_URL),
                ServerAddressesModel(getString(R.string.sync_xela), BuildConfig.PLANET_XELA_URL),
                ServerAddressesModel(getString(R.string.sync_uriur), BuildConfig.PLANET_URIUR_URL),
                ServerAddressesModel(getString(R.string.sync_ruiru), BuildConfig.PLANET_RUIRU_URL),
                ServerAddressesModel(getString(R.string.sync_embakasi), BuildConfig.PLANET_EMBAKASI_URL),
                ServerAddressesModel(getString(R.string.sync_cambridge), BuildConfig.PLANET_CAMBRIDGE_URL),
                //ServerAddressesModel(getString(R.string.sync_egdirbmac), BuildConfig.PLANET_EGDIRBMAC_URL),
            )

            val storedUrl = settings.getString("serverURL", null)
            val storedPin = settings.getString("serverPin", null)
            val urlWithoutProtocol = storedUrl?.replace(Regex("^https?://"), "")

            serverAddressAdapter = ServerAddressAdapter(getFilteredServerList(), { serverListAddress ->
                val actualUrl = serverListAddress.url.replace(Regex("^https?://"), "")
                binding.inputServerUrl.setText(actualUrl)
                binding.inputServerPassword.setText(getPinForUrl(actualUrl))
                val protocol = if (actualUrl == BuildConfig.PLANET_XELA_URL || actualUrl == BuildConfig.PLANET_SANPABLO_URL ||  actualUrl == BuildConfig.PLANET_URIUR_URL) "http://" else "https://"
                editor.putString("serverProtocol", protocol).apply()
                if (serverCheck) {
                    performSync(dialog)
                }}, { _, _ ->
                    clearDataDialog(getString(R.string.you_want_to_connect_to_a_different_server), false) {
                        serverAddressAdapter?.revertSelection()
                    }
                },
                urlWithoutProtocol
            )

            serverAddresses.adapter = serverAddressAdapter

            if (urlWithoutProtocol != null) {
                val position = serverListAddresses.indexOfFirst { it.url.replace(Regex("^https?://"), "") == urlWithoutProtocol }
                if (position != -1) {
                    serverAddressAdapter?.setSelectedPosition(position)
                    binding.inputServerUrl.setText(urlWithoutProtocol)
                    binding.inputServerPassword.setText(settings.getString("serverPin", ""))
                }
            }

            if (!prefData.getManualConfig()) {
                serverAddresses.visibility = View.VISIBLE
                if (storedUrl != null && !syncFailed) {
                    val position = serverListAddresses.indexOfFirst { it.url.replace(Regex("^https?://"), "") == urlWithoutProtocol }
                    if (position != -1) {
                        serverAddressAdapter?.setSelectedPosition(position)
                        binding.inputServerUrl.setText(urlWithoutProtocol)
                        binding.inputServerPassword.setText(storedPin)
                    }
                } else if (syncFailed) {
                    serverAddressAdapter?.clearSelection()
                }
            } else if (storedUrl != null) {
                val position = serverListAddresses.indexOfFirst { it.url.replace(Regex("^https?://"), "") == urlWithoutProtocol }
                if (position != -1) {
                    serverAddressAdapter?.setSelectedPosition(position)
                    binding.inputServerUrl.setText(urlWithoutProtocol)
                    binding.inputServerPassword.setText(storedPin)
                }
            }
            serverUrl.isEnabled = false
            serverPassword.isEnabled = false
            editor.putString("serverProtocol", getString(R.string.https_protocol)).apply()
        }
    }

    private fun getFilteredServerList(): List<ServerAddressesModel> {
        val pinnedUrl = settings.getString("pinnedServerUrl", null)
        val pinnedServer = serverListAddresses.find { it.url == pinnedUrl }

        return if (showAdditionalServers) {
            serverListAddresses
        } else {
            val topThree = serverListAddresses.take(3).toMutableList()
            if (pinnedServer != null && !topThree.contains(pinnedServer)) {
                listOf(pinnedServer) + topThree
            } else {
                topThree
            }
        }
    }

    private fun performSync(dialog: MaterialDialog) {
        serverConfigAction = "sync"
        val protocol = "${settings.getString("serverProtocol", "")}"
        var url = "${serverUrl.text}"
        val pin = "${serverPassword.text}"
        editor.putString("serverURL", url).apply()
        url = protocol + url
        if (isUrlValid(url)) {
            currentDialog = dialog
            service.getMinApk(this, url, pin, this, "SyncActivity")
        }
    }

    private fun getPinForUrl(url: String): String {
        val pinMap = mapOf(
            BuildConfig.PLANET_LEARNING_URL to BuildConfig.PLANET_LEARNING_PIN,
            BuildConfig.PLANET_GUATEMALA_URL to BuildConfig.PLANET_GUATEMALA_PIN,
            BuildConfig.PLANET_SANPABLO_URL to BuildConfig.PLANET_SANPABLO_PIN,
            BuildConfig.PLANET_EARTH_URL to BuildConfig.PLANET_EARTH_PIN,
            BuildConfig.PLANET_SOMALIA_URL to BuildConfig.PLANET_SOMALIA_PIN,
            BuildConfig.PLANET_VI_URL to BuildConfig.PLANET_VI_PIN,
            BuildConfig.PLANET_XELA_URL to BuildConfig.PLANET_XELA_PIN,
            BuildConfig.PLANET_URIUR_URL to BuildConfig.PLANET_URIUR_PIN,
            BuildConfig.PLANET_RUIRU_URL to BuildConfig.PLANET_RUIRU_PIN,
            BuildConfig.PLANET_EMBAKASI_URL to BuildConfig.PLANET_EMBAKASI_PIN,
            BuildConfig.PLANET_CAMBRIDGE_URL to BuildConfig.PLANET_CAMBRIDGE_PIN,
//            BuildConfig.PLANET_EGDIRBMAC_URL to BuildConfig.PLANET_EGDIRBMAC_PIN,
        )
        return pinMap[url] ?: ""
    }

    private fun onChangeServerUrl() {
        val selected = spnCloud.selectedItem
        if (selected is RealmCommunity && selected.isValid) {
            serverUrl.setText(selected.localDomain)
            protocolCheckIn.check(R.id.radio_https)
            settings.getString("serverProtocol", getString(R.string.https_protocol))
            serverPassword.setText(if (selected.weight == 0) "1983" else "")
            serverPassword.isEnabled = selected.weight != 0
        }
    }

    private fun setUrlAndPin(checked: Boolean) {
        if (checked) {
            onChangeServerUrl()
        } else {
            serverUrl.setText(settings.getString("serverURL", "")?.let { removeProtocol(it) })
            serverPassword.setText(settings.getString("serverPin", ""))
            protocolCheckIn.check(
                if (TextUtils.equals(settings.getString("serverProtocol", ""), "http://")) {
                    R.id.radio_http
                } else {
                    R.id.radio_https
                }
            )
        }
        serverUrl.isEnabled = !checked
        serverPassword.isEnabled = !checked
        serverPassword.clearFocus()
        serverUrl.clearFocus()
        protocolCheckIn.isEnabled = !checked
    }

    private fun protocolSemantics() {
        protocolCheckIn.setOnCheckedChangeListener { _: RadioGroup?, i: Int ->
            when (i) {
                R.id.radio_http -> editor.putString("serverProtocol", getString(R.string.http_protocol)).apply()
                R.id.radio_https -> editor.putString("serverProtocol", getString(R.string.https_protocol)).apply()
            }
        }
    }

    private fun removeProtocol(url: String): String {
        var modifiedUrl = url
        modifiedUrl = modifiedUrl.replaceFirst(getString(R.string.https_protocol).toRegex(), "")
        modifiedUrl = modifiedUrl.replaceFirst(getString(R.string.http_protocol).toRegex(), "")
        return modifiedUrl
    }

    fun continueSync(dialog: MaterialDialog, url: String, isAlternativeUrl: Boolean, defaultUrl: String) {
        processedUrl = saveConfigAndContinue(dialog, url, isAlternativeUrl, defaultUrl)
        if (TextUtils.isEmpty(processedUrl)) return
        isSync = true
        if (checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) && settings.getBoolean("firstRun", true)) {
            clearInternalStorage()
        }
        Service(this).isPlanetAvailable(object : PlanetAvailableListener {
            override fun isAvailable() {
                Service(context).checkVersion(this@SyncActivity, settings)
            }
            override fun notAvailable() {
                if (!isFinishing) {
                    syncFailed = true
                    showAlert(context, "Error", getString(R.string.planet_server_not_reachable))
                }
            }
        })
    }

    override fun onSuccess(success: String?) {
        if (customProgressDialog.isShowing() == true && success?.contains("Crash") == true) {
            customProgressDialog.dismiss()
        }
        if (::btnSignIn.isInitialized) {
            showSnack(btnSignIn, success)
        }
        editor.putLong("lastUsageUploaded", Date().time).apply()
        if (::lblLastSyncDate.isInitialized) {
            lblLastSyncDate.text = getString(R.string.message_placeholder, "${getString(R.string.last_sync, getRelativeTime(Date().time))} >>")
        }
        syncFailed = false
    }

    override fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean) {
        mRealm = Realm.getDefaultInstance()
        val builder = getUpdateDialog(this, info, customProgressDialog)
        if (cancelable || getCustomDeviceName(this).endsWith("###")) {
            builder.setNegativeButton(R.string.update_later) { _: DialogInterface?, _: Int ->
                continueSyncProcess()
            }
        } else {
            mRealm.executeTransactionAsync { realm: Realm -> realm.deleteAll() }
        }
        builder.setCancelable(cancelable)
        builder.show()
    }

    override fun onCheckingVersion() {
        customProgressDialog.setText(getString(R.string.checking_version))
        customProgressDialog.show()
    }

    fun registerReceiver() {
        val bManager = LocalBroadcastManager.getInstance(this)
        val intentFilter = IntentFilter()
        intentFilter.addAction(DashboardActivity.MESSAGE_PROGRESS)
        bManager.registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onError(msg: String, blockSync: Boolean) {
        Utilities.toast(this, msg)
        if (msg.startsWith("Config")) {
            settingDialog()
        }
        customProgressDialog.dismiss()
        if (!blockSync) continueSyncProcess() else {
            syncIconDrawable.stop()
            syncIconDrawable.selectDrawable(0)
        }
    }

    private fun continueSyncProcess() {
        try {
            lifecycleScope.launch {
                if (isSync) {
                    isServerReachable(processedUrl, "sync")
                } else if (forceSync) {
                    isServerReachable(processedUrl, "upload")
                    startUpload("")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setSyncFailed(newValue: Boolean) {
        syncFailed = newValue
    }

    override fun onSelectedUser(userModel: RealmUserModel) {
        mRealm = Realm.getDefaultInstance()
        val layoutChildLoginBinding = LayoutChildLoginBinding.inflate(layoutInflater)
        AlertDialog.Builder(this).setView(layoutChildLoginBinding.root)
            .setTitle(R.string.please_enter_your_password)
            .setPositiveButton(R.string.login) { _: DialogInterface?, _: Int ->
                val password = "${layoutChildLoginBinding.etChildPassword.text}"
                if (authenticateUser(settings, userModel.name, password, false)) {
                    Toast.makeText(applicationContext, getString(R.string.thank_you), Toast.LENGTH_SHORT).show()
                    onLogin()
                } else {
                    alertDialogOkay(getString(R.string.err_msg_login))
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    inner class MyTextWatcher(var view: View?) : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
        override fun onTextChanged(s: CharSequence, i: Int, i1: Int, i2: Int) {
            if (view?.id == R.id.input_server_url) {
                positiveAction.isEnabled = "$s".trim { it <= ' ' }.isNotEmpty() && URLUtil.isValidUrl("${settings.getString("serverProtocol", "")}$s")
            }
        }
        override fun afterTextChanged(editable: Editable) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
        }
    }

    companion object {
        lateinit var cal_today: Calendar
        lateinit var cal_last_Sync: Calendar

        suspend fun clearRealmDb() {
            withContext(Dispatchers.IO) {
                val realm = Realm.getDefaultInstance()
                try {
                    realm.executeTransaction { transactionRealm ->
                        transactionRealm.deleteAll()
                    }
                } finally {
                    realm.close()
                }
            }
        }

        fun clearSharedPref() {
            val settings = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val editor = settings.edit()
            val keysToKeep = setOf(SharedPrefManager(context).firstLaunch, SharedPrefManager(context).manualConfig )
            val tempStorage = HashMap<String, Boolean>()
            for (key in keysToKeep) {
                tempStorage[key] = settings.getBoolean(key, false)
            }
            editor.clear().apply()
            for ((key, value) in tempStorage) {
                editor.putBoolean(key, value)
            }
            editor.commit()

            val preferences = PreferenceManager.getDefaultSharedPreferences(context)
            preferences.edit { clear() }
        }

        fun restartApp() {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val mainIntent = Intent.makeRestartActivityTask(intent?.component)
            context.startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }
}
