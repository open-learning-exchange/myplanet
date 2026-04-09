package org.ole.planet.myplanet.ui.sync

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.AnimationDrawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
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
import dagger.hilt.android.EntryPointAccessors
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
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.databinding.DialogServerUrlBinding
import org.ole.planet.myplanet.di.AutoSyncEntryPoint
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.ServerAddress
import org.ole.planet.myplanet.repository.CommunityRepository
import org.ole.planet.myplanet.repository.ConfigurationsRepository
import org.ole.planet.myplanet.repository.ResourcesRepository
import org.ole.planet.myplanet.services.ResourceDownloadCoordinator
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.services.sync.SyncManager
import org.ole.planet.myplanet.services.sync.TransactionSyncManager
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.Constants.PREFS_NAME
import org.ole.planet.myplanet.utils.Constants.autoSynFeature
import org.ole.planet.myplanet.utils.DialogUtils.getUpdateDialog
import org.ole.planet.myplanet.utils.DialogUtils.showAlert
import org.ole.planet.myplanet.utils.DialogUtils.showSnack
import org.ole.planet.myplanet.utils.DialogUtils.showWifiSettingDialog
import org.ole.planet.myplanet.utils.DownloadUtils.downloadAllFiles
import org.ole.planet.myplanet.utils.DownloadUtils.openDownloadService
import org.ole.planet.myplanet.utils.FileUtils
import org.ole.planet.myplanet.utils.LocaleUtils
import org.ole.planet.myplanet.utils.NetworkUtils.extractProtocol
import org.ole.planet.myplanet.utils.NetworkUtils.getCustomDeviceName
import org.ole.planet.myplanet.utils.NetworkUtils.isNetworkConnectedFlow
import org.ole.planet.myplanet.utils.NotificationUtils.cancelAll
import org.ole.planet.myplanet.utils.ServerConfigUtils
import org.ole.planet.myplanet.utils.TimeUtils
import org.ole.planet.myplanet.utils.UrlUtils
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
abstract class SyncActivity : ProcessUserDataActivity(), ConfigurationsRepository.CheckVersionCallback {
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
    private var syncTimeInterval = intArrayOf(60 * 60, 3 * 60 * 60)
    lateinit var syncIcon: ImageView
    lateinit var syncIconDrawable: AnimationDrawable
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
    val defaultPref: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
    }
    @Inject
    lateinit var databaseService: DatabaseService
    var currentDialog: MaterialDialog? = null
    var serverConfigAction = ""
    var serverCheck = true
    var showAdditionalServers = false
    var serverAddressAdapter: ServerAddressAdapter? = null
    var serverListAddresses: List<ServerAddress> = emptyList()
    private var isProgressDialogShowing = false
    @Inject
    lateinit var configurationsRepository: ConfigurationsRepository

    @Inject
    lateinit var communityRepository: CommunityRepository

    @Inject
    open lateinit var resourcesRepository: ResourcesRepository

    @Inject
    lateinit var resourceDownloadCoordinator: ResourceDownloadCoordinator

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var transactionSyncManager: TransactionSyncManager

    @Inject
    lateinit var broadcastService: org.ole.planet.myplanet.services.BroadcastService

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initSyncConfigurationCoordinator()
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                syncManager.syncStatus.collect { status ->
                    when (status) {
                        is SyncManager.SyncStatus.Idle -> {
                            // Do nothing
                        }

                        is SyncManager.SyncStatus.Syncing -> {
                            withContext(Dispatchers.Main) {
                                onSyncStarted()
                            }
                        }

                        is SyncManager.SyncStatus.Success -> {
                            syncManager.resetSyncStatus()
                            withContext(Dispatchers.Main) {
                                onSyncComplete()
                            }
                        }

                        is SyncManager.SyncStatus.Error -> {
                            syncManager.resetSyncStatus()
                            withContext(Dispatchers.Main) {
                                onSyncFailed(status.message)
                            }
                        }
                    }
                }
            }
        }
        settings = prefData.rawPreferences
        requestAllPermissions()
        processedUrl = UrlUtils.getUrl()
    }

    private lateinit var syncConfigurationCoordinator: SyncConfigurationCoordinator

    private fun initSyncConfigurationCoordinator() {
        syncConfigurationCoordinator = SyncConfigurationCoordinator(
            configurationsRepository,
            prefData,
            object : SyncConfigurationCoordinator.Callback {
                override fun showProgressDialog() {
                    customProgressDialog.setText(getString(R.string.check_apk_version))
                    customProgressDialog.show()
                }

                override fun dismissProgressDialog() {
                    customProgressDialog.dismiss()
                }

                override fun setSyncFailed(failed: Boolean) {
                    syncFailed = failed
                }

                override fun showErrorDialog(errorMessage: String) {
                    alertDialogOkay(errorMessage)
                }

                override fun onVersionCheckSuccess() {
                    isSync = false
                    forceSync = true
                    configurationsRepository.checkVersion(this@SyncActivity, prefData)
                }

                override fun onContinueSync(dialog: MaterialDialog, url: String, isAlternativeUrl: Boolean, defaultUrl: String) {
                    continueSync(dialog, url, isAlternativeUrl, defaultUrl)
                }

                override fun onSaveConfigAndContinue(dialog: MaterialDialog, binding: DialogServerUrlBinding, defaultUrl: String) {
                    saveConfigAndContinue(dialog, binding, "", false, defaultUrl)
                }

                override fun onClearDataDialog() {
                    clearDataDialog(getString(R.string.you_want_to_connect_to_a_different_server), false)
                }
            }
        )
    }

    fun checkMinApk(url: String, pin: String, callerActivity: String) {
        val callerContext = when (callerActivity) {
            "LoginActivity" -> CallerContext.LOGIN_ACTIVITY
            "DashboardActivity" -> CallerContext.DASHBOARD_ACTIVITY
            "SyncActivity" -> CallerContext.SYNC_ACTIVITY
            else -> CallerContext.OTHER
        }
        syncConfigurationCoordinator.checkMinApk(
            lifecycleScope,
            url,
            pin,
            callerContext,
            serverConfigAction,
            currentDialog,
            serverDialogBinding
        )
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

                        configurationsRepository.clearAllData()
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
        prefData.setFirstRun(false)
    }

    fun sync(binding: DialogServerUrlBinding) {
        spinner = binding.intervalDropper
        syncSwitch = binding.syncSwitch
        intervalLabel = binding.intervalLabel
        syncSwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            setSpinnerVisibility(isChecked)
        }
        syncSwitch.isChecked = prefData.getAutoSync()
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
        try {
            val isAlternativeUrl = prefData.isAlternativeUrl()
            val url = if (isAlternativeUrl) {
                if (processedUrl?.contains("/db") == true) {
                    processedUrl.replace("/db", "") + "/db/_all_dbs"
                } else {
                    "$processedUrl/db/_all_dbs"
                }
            } else {
                "$processedUrl/_all_dbs"
            }

            val isAvailable = configurationsRepository.checkServerAvailability(url)
            if (isAvailable) {
                startSync(type)
                return true
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
        customProgressDialog.dismiss()
        alertDialogOkay(errorMessage)
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
        val lastSynced = prefData.getLastSync()
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
        prefData.setAutoSync(syncSwitch.isChecked)
        prefData.setAutoSyncInterval(syncTimeInterval[spinner.selectedItemPosition])
        prefData.setAutoSyncPosition(spinner.selectedItemPosition)
    }

    suspend fun authenticateUser(settings: SharedPreferences?, username: String?, password: String?, isManagerMode: Boolean): Boolean {
        return try {
            if (settings != null) {
                this.settings = settings
            }
            if (!withContext(Dispatchers.IO) { userRepository.hasAtLeastOneUser() }) {
                alertDialogOkay(getString(R.string.server_not_configured_properly_connect_this_device_with_planet_server))
                false
            } else {
                val user = userRepository.authenticateUser(username, password, isManagerMode)
                if (user != null) {
                    saveUserInfoPref(this.settings, password, user)
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
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
        val password = prefData.getServerPin().ifEmpty {
            binding.inputServerPassword.text.toString()
        }

        val couchdbURL = ServerConfigUtils.saveAlternativeUrl(url, password, prefData)
        if (isUrlValid(url)) setUrlParts(defaultUrl, password)
        return couchdbURL
    }

    private fun handleRegularUrlSave(binding: DialogServerUrlBinding): String {
        val protocol = prefData.getServerProtocol()
        var url = binding.inputServerUrl.text.toString()
        val pin = binding.inputServerPassword.text.toString()

        prefData.setCustomDeviceName(binding.deviceName.text.toString())

        url = protocol + url
        return if (isUrlValid(url)) setUrlParts(url, pin) else ""
    }

    private suspend fun onSyncStarted() {
        withContext(Dispatchers.Main) {
            customProgressDialog.setText(getString(R.string.syncing_data_please_wait))
            customProgressDialog.show()
            isProgressDialogShowing = true
        }
    }

    private suspend fun onSyncFailed(msg: String?) {
        withContext(Dispatchers.Main) {
            if (isProgressDialogShowing) {
                customProgressDialog.dismiss()
            }
            if (::syncIconDrawable.isInitialized) {
                syncIconDrawable = syncIcon.drawable as AnimationDrawable
                syncIconDrawable.stop()
                syncIconDrawable.selectDrawable(0)
                syncIcon.invalidateDrawable(syncIconDrawable)
            }
            showAlert(this@SyncActivity, getString(R.string.sync_failed), msg)
            showWifiSettingDialog(this@SyncActivity)
        }
    }

    private suspend fun onSyncComplete() {
        val activityContext = this@SyncActivity
        try {
            var attempt = 0
            val maxAttempts = 3 // Maximum 3 seconds wait
            while (attempt < maxAttempts) {
                val hasUser = withContext(Dispatchers.IO) {
                    userRepository.hasAtLeastOneUser()
                }
                if (hasUser) {
                    break
                }
                attempt++
                delay(1000)
            }

            if (attempt >= maxAttempts) {
                Log.w("SyncActivity", "Timeout waiting for users to sync. Continuing anyway...")
            }

            withContext(Dispatchers.Main) {
                forceSyncTrigger()
                    val syncedUrl = prefData.getServerUrl().takeIf { it.isNotEmpty() }?.let { ServerConfigUtils.removeProtocol(it) }
                    if (
                        syncedUrl != null &&
                        serverListAddresses.isNotEmpty() &&
                        serverListAddresses.any { it.url.replace(urlProtocolRegex, "") == syncedUrl }
                    ) {
                        prefData.setPinnedServerUrl(syncedUrl)
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
                        val pendingLanguage = prefData.getPendingLanguageChange()
                        if (pendingLanguage != null) {
                            withContext(Dispatchers.Main) {
                                prefData.setPendingLanguageChange(null)

                                LocaleUtils.setLocale(this@SyncActivity, pendingLanguage)
                                recreate()
                            }
                        }
                    }

                    showSnack(activityContext.findViewById(android.R.id.content), getString(R.string.sync_completed))

                    if (prefData.isAlternativeUrl()) {
                        prefData.setAlternativeUrl("")
                        prefData.setProcessedAlternativeUrl("")
                        prefData.setIsAlternativeUrl(false)
                    }

                    downloadAdditionalResources()

                    val betaAutoDownload = defaultPref.getBoolean("beta_auto_download", false)
                    if (betaAutoDownload) {
                        withContext(Dispatchers.IO) {
                            resourceDownloadCoordinator.startBackgroundDownload(
                                downloadAllFiles(resourcesRepository.getAllLibrariesToSync())
                            )
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
        val storedJsonConcatenatedLinks = prefData.getConcatenatedLinks()
        if (storedJsonConcatenatedLinks != null) {
            val storedConcatenatedLinks: ArrayList<String> = Json.decodeFromString(storedJsonConcatenatedLinks)
            openDownloadService(context, storedConcatenatedLinks, true)
        }
    }

    fun forceSyncTrigger(): Boolean {
        if (::lblLastSyncDate.isInitialized) {
            if (prefData.getLastSync() <= 0) {
                lblLastSyncDate.text = getString(R.string.last_synced_never)
            } else {
                val lastSyncMillis = prefData.getLastSync()
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
        val lastSyncTime = prefData.getLastSync().let { if (it == 0L) -1L else it }
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

        prefData.setLoggedIn(true)
        openDashboard()
        isNetworkConnectedFlow.onEach { isConnected ->
            if (isConnected) {
                val serverUrl = prefData.getServerUrl()
                if (serverUrl.isNotEmpty()) {
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

        handleManualConfiguration(binding, prefData.getConfigurationId(), dialog)
        setRadioProtocolListener(binding)
        binding.clearData.setOnClickListener {
            clearDataDialog(getString(R.string.are_you_sure_you_want_to_clear_data), false)
        }
        setupFastSyncOption(binding)

        showAdditionalServers = false
        if (serverListAddresses.isNotEmpty() && prefData.getServerUrl().isNotEmpty()) {
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
                val password = prefData.getServerPin()
                val couchdbURL = ServerConfigUtils.saveAlternativeUrl(url, password, prefData)
                if (isUrlValid(url)) setUrlParts(defaultUrl, password)
                couchdbURL
            } else {
                val protocol = prefData.getServerProtocol()
                val savedUrl = prefData.getServerUrl()
                val pin = prefData.getServerPin()
                val fullUrl = protocol + savedUrl
                if (isUrlValid(fullUrl)) setUrlParts(fullUrl, pin) else ""
            }

            if (TextUtils.isEmpty(processedUrl)) {
                return@runOnUiThread
            }

            isSync = true
            if (checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) && prefData.getFirstRun()) {
                clearInternalStorage()
            }

            lifecycleScope.launch {
                isServerReachable(processedUrl, "sync")
            }
        }
    }

    override fun onSuccess(success: String?) {
        if (customProgressDialog.isShowing() && success?.contains("Crash") == true) {
            customProgressDialog.dismiss()
        }
        if (::btnSignIn.isInitialized) {
            showSnack(btnSignIn, success)
        }
        prefData.setLastUsageUploaded(Date().time)
        if (::lblLastSyncDate.isInitialized) {
            lblLastSyncDate.text = getString(R.string.message_placeholder, "${getString(R.string.last_sync, TimeUtils.getRelativeTime(Date().time))} >>")
        }
        syncFailed = false
    }

    override fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean) {
        val builder = getUpdateDialog(this, info, customProgressDialog, lifecycleScope, configurationsRepository)
        if (cancelable || getCustomDeviceName(this).endsWith("###")) {
            builder.setNegativeButton(R.string.update_later) { _: DialogInterface?, _: Int ->
                continueSyncProcess()
            }
        } else {
            lifecycleScope.launch(Dispatchers.IO) {
                configurationsRepository.clearAllData()
            }
        }
        builder.setCancelable(cancelable)
        builder.show()
    }

    override fun onCheckingVersion() {}

    fun registerReceiver() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                broadcastService.events.collect { intent ->
                    if (intent.action == DashboardActivity.MESSAGE_PROGRESS) {
                        broadcastReceiver.onReceive(this@SyncActivity, intent)
                    }
                }
            }
        }
    }

    override fun onError(msg: String, blockSync: Boolean) {
        Utilities.toast(this, msg)
        if (msg.startsWith("Config")) {
            settingDialog()
        }
        if (customProgressDialog.isShowing()) {
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

    override fun onDestroy() {
        super.onDestroy()
    }
    companion object {
        lateinit var cal_today: Calendar
        lateinit var cal_last_Sync: Calendar
        private val secondsAgoRegex by lazy { Regex("^\\d{1,2} seconds ago$") }
        private val urlProtocolRegex by lazy { Regex("^https?://") }

        suspend fun clearSharedPref() {
            withContext(Dispatchers.IO) {
                val spm = EntryPointAccessors.fromApplication(context, AutoSyncEntryPoint::class.java).sharedPrefManager()
                val prefs = spm.rawPreferences
                val editor = prefs.edit()
                val keysToKeep =
                    setOf(SharedPrefManager.FIRST_LAUNCH, SharedPrefManager.MANUAL_CONFIG)
                val tempStorage = HashMap<String, Boolean>()
                for (key in keysToKeep) {
                    tempStorage[key] = prefs.getBoolean(key, false)
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
