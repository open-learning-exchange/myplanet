package org.ole.planet.myplanet.ui.sync

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.AnimationDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.webkit.URLUtil
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.MainApplication.Companion.createLog
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseResourceFragment.Companion.backgroundDownload
import org.ole.planet.myplanet.base.BaseResourceFragment.Companion.getAllLibraryList
import org.ole.planet.myplanet.data.ApiClient
import org.ole.planet.myplanet.data.ApiClient.client
import org.ole.planet.myplanet.data.ApiInterface
import org.ole.planet.myplanet.data.DataService
import org.ole.planet.myplanet.data.DataService.ConfigurationIdListener
import org.ole.planet.myplanet.databinding.DialogServerUrlBinding
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.ServerAddress
import org.ole.planet.myplanet.repository.ConfigurationRepository
import org.ole.planet.myplanet.service.UserSessionManager
import org.ole.planet.myplanet.service.sync.SyncManager
import org.ole.planet.myplanet.service.sync.TransactionSyncManager
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.androidDecrypter
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Constants.autoSynFeature
import org.ole.planet.myplanet.utilities.DialogUtils.getUpdateDialog
import org.ole.planet.myplanet.utilities.DialogUtils.showAlert
import org.ole.planet.myplanet.utilities.DialogUtils.showSnack
import org.ole.planet.myplanet.utilities.DialogUtils.showWifiSettingDialog
import org.ole.planet.myplanet.utilities.DownloadUtils.downloadAllFiles
import org.ole.planet.myplanet.utilities.DownloadUtils.openDownloadService
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.LocaleUtils
import org.ole.planet.myplanet.utilities.NetworkUtils.extractProtocol
import org.ole.planet.myplanet.utilities.NetworkUtils.getCustomDeviceName
import org.ole.planet.myplanet.utilities.NetworkUtils.isNetworkConnectedFlow
import org.ole.planet.myplanet.utilities.NotificationUtils.cancelAll
import org.ole.planet.myplanet.utilities.ServerConfigUtils
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.UrlUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
abstract class SyncActivity : ProcessUserDataActivity(), ConfigurationRepository.CheckVersionCallback,
    ConfigurationIdListener {
    private var serverDialogBinding: DialogServerUrlBinding? = null
    private lateinit var syncDate: TextView
    lateinit var lblLastSyncDate: TextView
    lateinit var btnSignIn: Button
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
    lateinit var editor: SharedPreferences.Editor
    private var syncTimeInterval = intArrayOf(60 * 60, 3 * 60 * 60)
    lateinit var syncIcon: ImageView
    lateinit var syncIconDrawable: AnimationDrawable
    lateinit var prefData: SharedPrefManager
    @Inject
    lateinit var profileDbHandler: UserSessionManager
    lateinit var spnCloud: Spinner
    lateinit var protocolCheckIn: RadioGroup
    lateinit var serverUrl: EditText
    lateinit var serverPassword: EditText
    lateinit var serverAddresses: RecyclerView
    lateinit var syncToServerText: TextView
    var selectedTeamId: String? = null
    lateinit var positiveAction: View
    lateinit var neutralAction: View
    lateinit var processedUrl: String
    var isSync = false
    var forceSync = false
    var syncFailed = false
    lateinit var defaultPref: SharedPreferences
    @Inject
    lateinit var service: DataService
    var currentDialog: MaterialDialog? = null
    var serverConfigAction = ""
    var serverCheck = true
    var showAdditionalServers = false
    var serverAddressAdapter: ServerAddressAdapter? = null
    var serverListAddresses: List<ServerAddress> = emptyList()
    private var isProgressDialogShowing = false
    @Inject
    lateinit var configurationRepository: ConfigurationRepository

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var transactionSyncManager: TransactionSyncManager

    @Inject
    lateinit var broadcastService: org.ole.planet.myplanet.service.BroadcastService

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                syncManager.syncStatus.collect { status ->
                    when (status) {
                        is SyncManager.SyncStatus.Idle -> {
                            // Do nothing
                        }

                        is SyncManager.SyncStatus.Syncing -> {
                            onSyncStarted()
                        }

                        is SyncManager.SyncStatus.Success -> {
                            onSyncComplete()
                        }

                        is SyncManager.SyncStatus.Error -> {
                            onSyncFailed(status.message)
                        }
                    }
                }
            }
        }
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        editor = settings.edit()
        requestAllPermissions()
        prefData = SharedPrefManager(this)
        defaultPref = PreferenceManager.getDefaultSharedPreferences(this)
        processedUrl = UrlUtils.getUrl()
    }

    override fun onConfigurationIdReceived(id: String, code: String, url: String, defaultUrl: String, isAlternativeUrl: Boolean, callerActivity: String) {
        val savedId = settings.getString("configurationId", null)

        when (callerActivity) {
            "LoginActivity", "DashboardActivity"-> {
                if (isAlternativeUrl) {
                    ServerConfigUtils.saveAlternativeUrl(url, settings.getString("serverPin", "") ?: "", settings, editor)
                }
                isSync = false
                forceSync = true
                configurationRepository.checkVersion(this, settings)
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
                        currentDialog?.let {
                            val binding = serverDialogBinding ?: return@let
                            saveConfigAndContinue(it, binding, "", false, defaultUrl)
                        }
                    } else {
                        clearDataDialog(getString(R.string.you_want_to_connect_to_a_different_server), false)
                    }
                }
            }
        }
    }

    fun clearDataDialog(message: String, config: Boolean, onCancel: () -> Unit = {}) {
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
        val myDir = File(FileUtils.getOlePath(this))
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

    fun sync(binding: DialogServerUrlBinding) {
        spinner = binding.intervalDropper
        syncSwitch = binding.syncSwitch
        intervalLabel = binding.intervalLabel
        syncSwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            setSpinnerVisibility(isChecked)
        }
        syncSwitch.isChecked = settings.getBoolean("autoSync", true)
        dateCheck(binding)
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
        ApiClient.ensureInitialized()
        val apiInterface = client.create(ApiInterface::class.java)
        try {
            val url = if (settings.getBoolean("isAlternativeUrl", false)) {
                if (processedUrl?.contains("/db") == true) {
                    processedUrl.replace("/db", "") + "/db/_all_dbs"
                } else {
                    "$processedUrl/db/_all_dbs"
                }
            } else {
                "$processedUrl/_all_dbs"
            }
            val response = apiInterface.isPlanetAvailableSuspend(url)

            if (response.isSuccessful) {
                val ss = response.body()?.string()
                val myList = ss?.split(",")?.dropLastWhile { it.isEmpty() }

                return if ((myList?.size ?: 0) < 8) {
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
        } catch (e: Exception) {
            e.printStackTrace()
        }

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
        return false
    }

    private fun dateCheck(binding: DialogServerUrlBinding) {
        // Check if the user never synced
        syncDate = binding.lastDateSynced
        syncDate.text = getString(R.string.last_sync_date, convertDate())
        syncDropdownAdd()
    }

    // Converts OS date to human date
    private fun convertDate(): String {
        val lastSynced = settings.getLong("LastSync", 0)
        return if (lastSynced == 0L) {
            " Never Synced"
        } else {
            TimeUtils.getRelativeTime(lastSynced)
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
            val isEmpty = databaseService.withRealm { realm -> realm.isEmpty }
            if (isEmpty) {
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
            val user = databaseService.withRealm { realm ->
                realm.where(RealmUserModel::class.java).equalTo("name", username).findFirst()?.let { realm.copyFromRealm(it) }
            }
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
        syncManager.start(null, type)
    }

    private fun saveConfigAndContinue(
        dialog: MaterialDialog,
        binding: DialogServerUrlBinding,
        url: String,
        isAlternativeUrl: Boolean,
        defaultUrl: String
    ): String {
        dialog.dismiss()
        saveSyncInfoToPreference()
        return if (isAlternativeUrl) {
            handleAlternativeUrlSave(binding, url, defaultUrl)
        } else {
            handleRegularUrlSave(binding)
        }
    }

    private fun handleAlternativeUrlSave(
        binding: DialogServerUrlBinding,
        url: String,
        defaultUrl: String
    ): String {
        val password = if (settings.getString("serverPin", "") != "") {
            settings.getString("serverPin", "")!!
        } else {
            binding.inputServerPassword.text.toString()
        }

        val couchdbURL = ServerConfigUtils.saveAlternativeUrl(url, password, settings, editor)
        if (isUrlValid(url)) setUrlParts(defaultUrl, password)
        return couchdbURL
    }

    private fun handleRegularUrlSave(binding: DialogServerUrlBinding): String {
        val protocol = settings.getString("serverProtocol", "")
        var url = binding.inputServerUrl.text.toString()
        val pin = binding.inputServerPassword.text.toString()

        editor.putString(
            "customDeviceName",
            binding.deviceName.text.toString()
        ).apply()

        url = protocol + url
        return if (isUrlValid(url)) setUrlParts(url, pin) else ""
    }

    private fun onSyncStarted() {
        customProgressDialog.setText(getString(R.string.syncing_data_please_wait))
        customProgressDialog.show()
        isProgressDialogShowing = true
    }

    private fun onSyncFailed(msg: String?) {
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

    private fun onSyncComplete() {
        val activityContext = this@SyncActivity
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var attempt = 0
                while (true) {
                    val hasUser = databaseService.withRealm { realm ->
                        realm.where(RealmUserModel::class.java).findAll().isNotEmpty()
                    }
                    if (hasUser) {
                        break
                    }
                    attempt++
                    delay(1000)
                }

                withContext(Dispatchers.Main) {
                    forceSyncTrigger()
                    val syncedUrl = settings.getString("serverURL", null)?.let { ServerConfigUtils.removeProtocol(it) }
                    if (
                        syncedUrl != null &&
                        serverListAddresses.isNotEmpty() &&
                        serverListAddresses.any { it.url.replace(urlProtocolRegex, "") == syncedUrl }
                    ) {
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

                                LocaleUtils.setLocale(this@SyncActivity, pendingLanguage)
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
                            databaseService.withRealm { realm ->
                                backgroundDownload(
                                    downloadAllFiles(getAllLibraryList(realm)),
                                    activityContext,
                                    configurationRepository
                                )
                            }
                        }
                    }

                    cancelAll(activityContext)

                    if (activityContext is LoginActivity) {
                        activityContext.invalidateTeamsCacheAndReload()
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
                lblLastSyncDate.text = getString(R.string.last_sync, TimeUtils.getRelativeTime(Date().time))
            }

            lblVersion.text = getString(R.string.app_version)
            tvAvailableSpace.text = buildString {
                append(getString(R.string.available_space_colon))
                append(" ")
                append(FileUtils.availableOverTotalMemoryFormattedString(this@SyncActivity))
            }

            inputName.hint = getString(R.string.hint_name)
            inputPassword.hint = getString(R.string.password)
            btnSignIn.text = getString(R.string.btn_sign_in)
            btnGuestLogin.text = getString(R.string.btn_guest_login)
            becomeMember.text = getString(R.string.become_a_member)
            btnFeedback.text = getString(R.string.feedback)
            openCommunity.text = getString(R.string.open_community)
            val currentLanguage = LocaleUtils.getLanguage(this)
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
        if (::lblLastSyncDate.isInitialized) {
            if (settings.getLong(getString(R.string.last_syncs), 0) <= 0) {
                lblLastSyncDate.text = getString(R.string.last_synced_never)
            } else {
                val lastSyncMillis = settings.getLong(getString(R.string.last_syncs), 0)
                var relativeTime = TimeUtils.getRelativeTime(lastSyncMillis)

                if (relativeTime.matches(secondsAgoRegex)) {
                    relativeTime = getString(R.string.a_few_seconds_ago)
                }

                lblLastSyncDate.text = getString(R.string.last_sync, relativeTime)
            }
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
        profileDbHandler.onLoginAsync(
            callback = {},
            onError = { error ->
                error.printStackTrace()
            }
        )

        editor.putBoolean(Constants.KEY_LOGIN, true).commit()
        openDashboard()
        isNetworkConnectedFlow.onEach { isConnected ->
            if (isConnected) {
                val serverUrl = settings.getString("serverURL", "")
                if (!serverUrl.isNullOrEmpty()) {
                    MainApplication.applicationScope.launch {
                        val canReachServer = MainApplication.isServerReachable(serverUrl)
                        if (canReachServer) {
                            withContext(Dispatchers.Main) {
                                startUpload("login")
                            }
                            transactionSyncManager.syncDb("login_activities")
                        }
                    }
                }
            }
        }.launchIn(MainApplication.applicationScope)
    }

    fun settingDialog() {
        serverDialogBinding = DialogServerUrlBinding.inflate(LayoutInflater.from(this))
        val binding = serverDialogBinding!!
        initServerDialog(binding)

        val contextWrapper = ContextThemeWrapper(this, R.style.AlertDialogTheme)
        val dialog = MaterialDialog.Builder(contextWrapper)
            .customView(binding.root, true)
            .positiveText(R.string.sync)
            .negativeText(R.string.txt_cancel)
            .neutralText(R.string.btn_sync_save)
            .onPositive { d: MaterialDialog, _: DialogAction? -> performSync(d) }
            .build()

        positiveAction = dialog.getActionButton(DialogAction.POSITIVE)
        neutralAction = dialog.getActionButton(DialogAction.NEUTRAL)

        handleManualConfiguration(binding, settings.getString("configurationId", null), dialog)
        setRadioProtocolListener(binding)
        binding.clearData.setOnClickListener {
            clearDataDialog(getString(R.string.are_you_sure_you_want_to_clear_data), false)
        }
        setupFastSyncOption(binding)

        showAdditionalServers = false
        if (serverListAddresses.isNotEmpty() && settings.getString("serverURL", "")?.isNotEmpty() == true) {
            refreshServerList()
        }

        neutralAction.setOnClickListener { onNeutralButtonClick(dialog) }

        dialog.setOnDismissListener { serverDialogBinding = null }
        dialog.show()
        sync(binding)
        if (!prefData.getManualConfig()) {
            dialog.getActionButton(DialogAction.NEUTRAL).text = getString(R.string.show_more)
        }
    }
    fun continueSync(dialog: MaterialDialog, url: String, isAlternativeUrl: Boolean, defaultUrl: String) {
        runOnUiThread {
            dialog.dismiss()

            processedUrl = if (isAlternativeUrl) {
                val password = settings.getString("serverPin", "") ?: ""
                val couchdbURL = ServerConfigUtils.saveAlternativeUrl(url, password, settings, editor)
                if (isUrlValid(url)) setUrlParts(defaultUrl, password)
                couchdbURL
            } else {
                val protocol = settings.getString("serverProtocol", "")
                val savedUrl = settings.getString("serverURL", "") ?: ""
                val pin = settings.getString("serverPin", "") ?: ""
                val fullUrl = protocol + savedUrl
                if (isUrlValid(fullUrl)) setUrlParts(fullUrl, pin) else ""
            }

            if (TextUtils.isEmpty(processedUrl)) {
                return@runOnUiThread
            }

            isSync = true
            if (checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) && settings.getBoolean("firstRun", true)) {
                clearInternalStorage()
            }

            lifecycleScope.launch {
                isServerReachable(processedUrl, "sync")
            }
        }
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
            lblLastSyncDate.text = getString(R.string.message_placeholder, "${getString(R.string.last_sync, TimeUtils.getRelativeTime(Date().time))} >>")
        }
        syncFailed = false
    }

    override fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean) {
        val builder = getUpdateDialog(this, info, customProgressDialog, lifecycleScope, configurationRepository)
        if (cancelable || getCustomDeviceName(this).endsWith("###")) {
            builder.setNegativeButton(R.string.update_later) { _: DialogInterface?, _: Int ->
                continueSyncProcess()
            }
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                databaseService.executeTransactionAsync { realm -> realm.deleteAll() }
            }
        }
        builder.setCancelable(cancelable)
        builder.show()
    }

    override fun onCheckingVersion() {}

    fun registerReceiver() {
        lifecycleScope.launch {
            broadcastService.events.collect { intent ->
                if (intent.action == DashboardActivity.MESSAGE_PROGRESS) {
                    broadcastReceiver.onReceive(this@SyncActivity, intent)
                }
            }
        }
    }

    override fun onError(msg: String, blockSync: Boolean) {
        Utilities.toast(this, msg)
        if (msg.startsWith("Config")) {
            settingDialog()
        }
        if (customProgressDialog.isShowing() == true) {
            customProgressDialog.dismiss()
        }
        if (!blockSync) {
            continueSyncProcess()
        } else {
            if (::syncIconDrawable.isInitialized) {
                syncIconDrawable.stop()
                syncIconDrawable.selectDrawable(0)
            }
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
    }
    companion object {
        lateinit var cal_today: Calendar
        lateinit var cal_last_Sync: Calendar
        private val secondsAgoRegex by lazy { Regex("^\\d{1,2} seconds ago$") }
        private val urlProtocolRegex by lazy { Regex("^https?://") }

        suspend fun clearRealmDb() {
            withContext(Dispatchers.IO) {
                val databaseService = (context.applicationContext as MainApplication).databaseService
                databaseService.withRealm { realm ->
                    realm.executeTransaction { transactionRealm ->
                        transactionRealm.deleteAll()
                    }
                }
            }
        }

        suspend fun clearSharedPref() {
            withContext(Dispatchers.IO) {
                val settings = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                val editor = settings.edit()
                val keysToKeep =
                    setOf(SharedPrefManager.FIRST_LAUNCH, SharedPrefManager.MANUAL_CONFIG)
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
        }

        fun restartApp() {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val mainIntent = Intent.makeRestartActivityTask(intent?.component)
            context.startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }
}
