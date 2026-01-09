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
    private lateinit var syncDateTextView: TextView
    lateinit var lastSyncDateTextView: TextView
    lateinit var signInButton: Button
    lateinit var versionTextView: TextView
    lateinit var availableSpaceTextView: TextView
    lateinit var guestLoginButton: Button
    lateinit var becomeMemberButton: Button
    lateinit var feedbackButton: Button
    lateinit var openCommunityButton: Button
    lateinit var languageButton: Button
    lateinit var usernameEditText: EditText
    lateinit var passwordEditText: EditText
    private lateinit var syncIntervalLabel: TextView
    lateinit var syncIntervalSpinner: Spinner
    private lateinit var autoSyncSwitch: SwitchCompat
    lateinit var editor: SharedPreferences.Editor
    private var syncTimeInterval = intArrayOf(60 * 60, 3 * 60 * 60)
    lateinit var syncStatusImageView: ImageView
    lateinit var syncStatusAnimationDrawable: AnimationDrawable
    lateinit var sharedPrefManager: SharedPrefManager
    @Inject
    lateinit var userSession: UserSessionManager
    lateinit var cloudSpinner: Spinner
    lateinit var protocolRadioGroup: RadioGroup
    lateinit var serverUrlEditText: EditText
    lateinit var serverPasswordEditText: EditText
    lateinit var serverAddressesRecyclerView: RecyclerView
    lateinit var syncToServerTextView: TextView
    var selectedTeamId: String? = null
    lateinit var positiveActionButton: View
    lateinit var neutralActionButton: View
    lateinit var processedUrl: String
    var isSyncing = false
    var isForceSync = false
    var hasSyncFailed = false
    lateinit var defaultSharedPreferences: SharedPreferences
    lateinit var dataService: DataService
    var currentMaterialDialog: MaterialDialog? = null
    var serverConfigActionType = ""
    var shouldCheckServer = true
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
                            showSyncInProgress()
                        }

                        is SyncManager.SyncStatus.Success -> {
                            handleSyncSuccess()
                        }

                        is SyncManager.SyncStatus.Error -> {
                            handleSyncError(status.message)
                        }
                    }
                }
            }
        }
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        editor = settings.edit()
        requestAllPermissions()
        sharedPrefManager = SharedPrefManager(this)
        defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        processedUrl = UrlUtils.getUrl()
    }

    override fun onConfigurationIdReceived(id: String, code: String, url: String, defaultUrl: String, isAlternativeUrl: Boolean, callerActivity: String) {
        val savedId = settings.getString("configurationId", null)

        when (callerActivity) {
            "LoginActivity", "DashboardActivity"-> {
                if (isAlternativeUrl) {
                    ServerConfigUtils.saveAlternativeUrl(url, settings.getString("serverPin", "") ?: "", settings, editor)
                }
                isSyncing = false
                isForceSync = true
                configurationRepository.checkVersion(this, settings)
            }
            else -> {
                if (serverConfigActionType == "sync") {
                    if (savedId == null) {
                        editor.putString("configurationId", id).apply()
                        editor.putString("communityName", code).apply()
                        currentMaterialDialog?.let {
                            proceedWithSync(it, url, isAlternativeUrl, defaultUrl)
                        }
                    } else if (id == savedId) {
                        currentMaterialDialog?.let {
                            proceedWithSync(it, url, isAlternativeUrl, defaultUrl)
                        }
                    } else {
                        showClearDataDialog(getString(R.string.you_want_to_connect_to_a_different_server), false)
                    }
                } else if (serverConfigActionType == "save") {
                    if (savedId == null || id == savedId) {
                        currentMaterialDialog?.let {
                            val binding = serverDialogBinding ?: return@let
                            saveServerConfiguration(it, binding, "", false, defaultUrl)
                        }
                    } else {
                        showClearDataDialog(getString(R.string.you_want_to_connect_to_a_different_server), false)
                    }
                }
            }
        }
    }

    fun showClearDataDialog(message: String, saveManualConfig: Boolean, onCancel: () -> Unit = {}) {
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
                        sharedPrefManager.setManualConfig(config)
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

    fun initializeSyncComponents(binding: DialogServerUrlBinding) {
        syncIntervalSpinner = binding.intervalDropper
        autoSyncSwitch = binding.syncSwitch
        syncIntervalLabel = binding.intervalLabel
        autoSyncSwitch.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            setSyncIntervalSpinnerVisibility(isChecked)
        }
        autoSyncSwitch.isChecked = settings.getBoolean("autoSync", true)
        updateLastSyncDateDisplay(binding)
    }

    private fun setSyncIntervalSpinnerVisibility(isVisible: Boolean) {
        if (isVisible) {
            syncIntervalLabel.visibility = View.VISIBLE
            syncIntervalSpinner.visibility = View.VISIBLE
        } else {
            syncIntervalSpinner.visibility = View.GONE
            syncIntervalLabel.visibility = View.GONE
        }
    }

    suspend fun checkServerConnectionAndSync(serverUrl: String?, syncType: String): Boolean {
        ApiClient.ensureInitialized()
        val apiInterface = client.create(ApiInterface::class.java)
        try {
            val url = if (settings.getBoolean("isAlternativeUrl", false)) {
                if (serverUrl?.contains("/db") == true) {
                    serverUrl.replace("/db", "") + "/db/_all_dbs"
                } else {
                    "$serverUrl/db/_all_dbs"
                }
            } else {
                "$serverUrl/_all_dbs"
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
                        startSync(syncType)
                    }
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        hasSyncFailed = true
        val protocol = extractProtocol("$serverUrl")
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

    private fun updateLastSyncDateDisplay(binding: DialogServerUrlBinding) {
        // Check if the user never synced
        syncDateTextView = binding.lastDateSynced
        syncDateTextView.text = getString(R.string.last_sync_date, getLastSyncTimeAsString())
        setupSyncIntervalDropdown()
    }

    // Converts OS date to human date
    private fun getLastSyncTimeAsString(): String {
        val lastSynced = settings.getLong("LastSync", 0)
        return if (lastSynced == 0L) {
            " Never Synced"
        } else {
            TimeUtils.getRelativeTime(lastSynced)
        }
    }

    private fun setupSyncIntervalDropdown() {
        val list: MutableList<String> = ArrayList()
        list.add("1 " + getString(R.string.hour))
        list.add("3 " + getString(R.string.hours))
        val spinnerArrayAdapter = ArrayAdapter(this, R.layout.spinner_item, list)
        spinnerArrayAdapter.setDropDownViewResource(R.layout.spinner_item)
        syncIntervalSpinner.adapter = spinnerArrayAdapter
    }

    private fun saveSyncSettings() {
        editor.putBoolean("autoSync", autoSyncSwitch.isChecked)
        editor.putInt("autoSyncInterval", syncTimeInterval[syncIntervalSpinner.selectedItemPosition])
        editor.putInt("autoSyncPosition", syncIntervalSpinner.selectedItemPosition)
        editor.apply()
    }

    fun authenticateUserLocal(sharedPreferences: SharedPreferences?, username: String?, password: String?, isManagerMode: Boolean): Boolean {
        return try {
            if (sharedPreferences != null) {
                this.settings = sharedPreferences
            }
            val isEmpty = databaseService.withRealm { realm -> realm.isEmpty }
            if (isEmpty) {
                alertDialogOkay(getString(R.string.server_not_configured_properly_connect_this_device_with_planet_server))
                false
            } else {
                validateLocalCredentials(username, password, isManagerMode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun validateLocalCredentials(username: String?, password: String?, isManagerMode: Boolean): Boolean {
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

    fun startSync(syncType: String) {
        syncManager.start(null, syncType)
    }

    private fun saveServerConfiguration(
        dialog: MaterialDialog,
        binding: DialogServerUrlBinding,
        url: String,
        isAlternativeUrl: Boolean,
        defaultUrl: String
    ): String {
        dialog.dismiss()
        saveSyncInfoToPreference()
        return if (isAlternativeUrl) {
            saveAlternativeUrlConfig(binding, url, defaultUrl)
        } else {
            saveStandardUrlConfig(binding)
        }
    }

    private fun saveAlternativeUrlConfig(
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

    private fun saveStandardUrlConfig(binding: DialogServerUrlBinding): String {
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

    private fun showSyncInProgress() {
        customProgressDialog.setText(getString(R.string.syncing_data_please_wait))
        customProgressDialog.show()
        isProgressDialogShowing = true
    }

    private fun handleSyncError(errorMessage: String?) {
        if (isProgressDialogShowing) {
            customProgressDialog.dismiss()
        }
        if (::syncStatusAnimationDrawable.isInitialized) {
            syncStatusAnimationDrawable = syncStatusImageView.drawable as AnimationDrawable
            syncStatusAnimationDrawable.stop()
            syncStatusAnimationDrawable.selectDrawable(0)
            syncStatusImageView.invalidateDrawable(syncStatusAnimationDrawable)
        }
        runOnUiThread {
            showAlert(this@SyncActivity, getString(R.string.sync_failed), errorMessage)
            showWifiSettingDialog(this@SyncActivity)
        }
    }

    private fun handleSyncSuccess() {
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

                    if (::syncStatusAnimationDrawable.isInitialized) {
                        syncStatusAnimationDrawable = syncStatusImageView.drawable as AnimationDrawable
                        syncStatusAnimationDrawable.stop()
                        syncStatusAnimationDrawable.selectDrawable(0)
                        syncStatusImageView.invalidateDrawable(syncStatusAnimationDrawable)
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

                    val betaAutoDownload = defaultSharedPreferences.getBoolean("beta_auto_download", false)
                    if (betaAutoDownload) {
                        withContext(Dispatchers.IO) {
                            databaseService.withRealm { realm ->
                                backgroundDownload(
                                    downloadAllFiles(getAllLibraryList(realm)),
                                    activityContext
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
            if (::lastSyncDateTextView.isInitialized) {
                lastSyncDateTextView.text = getString(R.string.last_sync, TimeUtils.getRelativeTime(Date().time))
            }

            versionTextView.text = getString(R.string.app_version)
            availableSpaceTextView.text = buildString {
                append(getString(R.string.available_space_colon))
                append(" ")
                append(FileUtils.availableOverTotalMemoryFormattedString(this@SyncActivity))
            }

            usernameEditText.hint = getString(R.string.hint_name)
            passwordEditText.hint = getString(R.string.password)
            signInButton.text = getString(R.string.btn_sign_in)
            guestLoginButton.text = getString(R.string.btn_guest_login)
            becomeMemberButton.text = getString(R.string.become_a_member)
            feedbackButton.text = getString(R.string.feedback)
            openCommunityButton.text = getString(R.string.open_community)
            val currentLanguage = LocaleUtils.getLanguage(this)
            languageButton.text = getLanguageString(currentLanguage)
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

    fun checkForceSyncTrigger(): Boolean {
        if (::lastSyncDateTextView.isInitialized) {
            if (settings.getLong(getString(R.string.last_syncs), 0) <= 0) {
                lastSyncDateTextView.text = getString(R.string.last_synced_never)
            } else {
                val lastSyncMillis = settings.getLong(getString(R.string.last_syncs), 0)
                var relativeTime = TimeUtils.getRelativeTime(lastSyncMillis)

                if (relativeTime.matches(secondsAgoRegex)) {
                    relativeTime = getString(R.string.a_few_seconds_ago)
                }

                lastSyncDateTextView.text = getString(R.string.last_sync, relativeTime)
            }
        }
        if (autoSynFeature(Constants.KEY_AUTOSYNC_, applicationContext) && autoSynFeature(Constants.KEY_AUTOSYNC_WEEKLY, applicationContext)) {
            return performForcedSyncCheck(7)
        } else if (autoSynFeature(Constants.KEY_AUTOSYNC_, applicationContext) && autoSynFeature(Constants.KEY_AUTOSYNC_MONTHLY, applicationContext)) {
            return performForcedSyncCheck(30)
        }
        return false
    }

    fun showWifiDialogOnIntent() {
        if (intent.getBooleanExtra("showWifiDialog", false)) {
            showWifiSettingDialog(this)
        }
    }

    private fun performForcedSyncCheck(maxDaysSinceLastSync: Int): Boolean {
        todayCalendar = Calendar.getInstance(Locale.ENGLISH)
        lastSyncCalendar = Calendar.getInstance(Locale.ENGLISH)
        val lastSyncTime = settings.getLong("LastSync", -1)
        if (lastSyncTime <= 0) {
            return false
        }
        lastSyncCalendar.timeInMillis = lastSyncTime
        todayCalendar.timeInMillis = System.currentTimeMillis()
        val msDiff = todayCalendar.timeInMillis - lastSyncCalendar.timeInMillis
        val daysDiff = TimeUnit.MILLISECONDS.toDays(msDiff)
        return if (daysDiff >= maxDaysSinceLastSync) {
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

    fun handleLoginTasks() {
        userSession.onLoginAsync(
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

    fun showServerSettingsDialog() {
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

        positiveActionButton = dialog.getActionButton(DialogAction.POSITIVE)
        neutralActionButton = dialog.getActionButton(DialogAction.NEUTRAL)

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

        neutralActionButton.setOnClickListener { onNeutralButtonClick(dialog) }

        dialog.setOnDismissListener { serverDialogBinding = null }
        dialog.show()
        sync(binding)
        if (!sharedPrefManager.getManualConfig()) {
            dialog.getActionButton(DialogAction.NEUTRAL).text = getString(R.string.show_more)
        }
    }
    fun proceedWithSync(dialog: MaterialDialog, url: String, isAlternativeUrl: Boolean, defaultUrl: String) {
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

            isSyncing = true
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
        if (::signInButton.isInitialized) {
            showSnack(signInButton, success)
        }
        editor.putLong("lastUsageUploaded", Date().time).apply()
        if (::lastSyncDateTextView.isInitialized) {
            lastSyncDateTextView.text = getString(R.string.message_placeholder, "${getString(R.string.last_sync, TimeUtils.getRelativeTime(Date().time))} >>")
        }
        hasSyncFailed = false
    }

    override fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean) {
        val builder = getUpdateDialog(this, info, customProgressDialog, lifecycleScope)
        if (cancelable || getCustomDeviceName(this).endsWith("###")) {
            builder.setNegativeButton(R.string.update_later) { _: DialogInterface?, _: Int ->
                resumeSyncProcess()
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

    fun startBroadcastReceiver() {
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
            showServerSettingsDialog()
        }
        if (customProgressDialog.isShowing() == true) {
            customProgressDialog.dismiss()
        }
        if (!blockSync) {
            resumeSyncProcess()
        } else {
            if (::syncStatusAnimationDrawable.isInitialized) {
                syncStatusAnimationDrawable.stop()
                syncStatusAnimationDrawable.selectDrawable(0)
            }
        }
    }

    private fun resumeSyncProcess() {
        try {
            lifecycleScope.launch {
                if (isSyncing) {
                    isServerReachable(processedUrl, "sync")
                } else if (isForceSync) {
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
                positiveActionButton.isEnabled = "$s".trim { it <= ' ' }.isNotEmpty() && URLUtil.isValidUrl("${settings.getString("serverProtocol", "")}$s")
            }
        }
        override fun afterTextChanged(editable: Editable) {}
    }

    override fun onDestroy() {
        super.onDestroy()
    }
    companion object {
        lateinit var todayCalendar: Calendar
        lateinit var lastSyncCalendar: Calendar
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
