package org.ole.planet.myplanet.ui.sync

import android.Manifest
import android.content.*
import android.graphics.drawable.AnimationDrawable
import android.os.Build
import android.os.Bundle
import android.text.*
import android.view.*
import android.webkit.URLUtil
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.*
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.realm.*
import okhttp3.ResponseBody
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.*
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.*
import org.ole.planet.myplanet.datamanager.Service.*
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.service.*
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.team.AdapterTeam.OnUserSelectedListener
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.AndroidDecrypter
import org.ole.planet.myplanet.utilities.*
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Constants.autoSynFeature
import org.ole.planet.myplanet.utilities.DialogUtils.getUpdateDialog
import org.ole.planet.myplanet.utilities.DialogUtils.showAlert
import org.ole.planet.myplanet.utilities.DialogUtils.showSnack
import org.ole.planet.myplanet.utilities.DialogUtils.showWifiSettingDialog
import org.ole.planet.myplanet.utilities.NetworkUtils.getCustomDeviceName
import org.ole.planet.myplanet.utilities.NotificationUtil.cancellAll
import org.ole.planet.myplanet.utilities.Utilities.getRelativeTime
import org.ole.planet.myplanet.utilities.Utilities.openDownloadService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

abstract class SyncActivity : ProcessUserDataActivity(), SyncListener, CheckVersionCallback,
    OnUserSelectedListener, ConfigurationIdListener {
    private lateinit var syncDate: TextView
    lateinit var lblLastSyncDate: TextView
    private lateinit var intervalLabel: TextView
    lateinit var tvNodata: TextView
//    lateinit var spinner: Spinner
    lateinit var radioGroup: RadioGroup
    private lateinit var radioGroupServers: RadioGroup
    private lateinit var syncSwitch: SwitchCompat
    var convertedDate = 0
    private var connectionResult = false
    lateinit var mRealm: Realm
    private lateinit var editor: SharedPreferences.Editor
    private var syncTimeInteval = intArrayOf(60 * 60, 3 * 60 * 60)
    lateinit var syncIcon: ImageView
    lateinit var syncIconDrawable: AnimationDrawable
    lateinit var inputLayoutName: TextInputLayout
    lateinit var inputLayoutPassword: TextInputLayout
    lateinit var prefData: SharedPrefManager
    lateinit var profileDbHandler: UserProfileDbHandler
    private lateinit var protocol_checkin: RadioGroup
    private lateinit var serverUrl: EditText
    private lateinit var serverPassword: EditText
//    private lateinit var serverAddresses: Spinner
    private var teamList = ArrayList<String?>()
    private var teamAdapter: ArrayAdapter<String?>? = null
    var selectedTeamId: String? = null
    lateinit var positiveAction: View
    lateinit var processedUrl: String
    var isSync = false
    var forceSync = false
    lateinit var btnSignIn: Button
    lateinit var defaultPref: SharedPreferences
    lateinit var service: Service
    private var currentDialog: MaterialDialog? = null
    private var serverConfigAction = ""
    private var selectedServerUrl: String? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        editor = settings.edit()
        mRealm = DatabaseService(this).realmInstance
        mRealm = Realm.getDefaultInstance()
        requestAllPermissions()
        customProgressDialog = DialogUtils.getCustomProgressDialog(this)
        prefData = SharedPrefManager(this)
        profileDbHandler = UserProfileDbHandler(this)
        defaultPref = PreferenceManager.getDefaultSharedPreferences(this)
        processedUrl = Utilities.getUrl()
    }

    override fun onConfigurationIdReceived(id: String, code:String) {
        val savedId = settings.getString("configurationId", null)
        if (serverConfigAction == "sync") {
            if (savedId == null) {
                editor.putString("configurationId", id).apply()
                editor.putString("communityName", code).apply()
                currentDialog?.let { continueSync(it) }
            } else if (id == savedId) {
                currentDialog?.let { continueSync(it) }
            } else {
                clearDataDialog(getString(R.string.you_want_to_connect_to_a_different_server))
            }
        } else if (serverConfigAction == "save") {
            if (savedId == null || id == savedId) {
                currentDialog?.let { saveConfigAndContinue(it) }
            } else {
                clearDataDialog(getString(R.string.you_want_to_connect_to_a_different_server))
            }
        }
    }

    private fun clearDataDialog(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(getString(R.string.clear_data)) { _, _ ->
                clearRealmDb()
                clearSharedPref()
                restartApp()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun clearSharedPref() {
        val keysToKeep = setOf(prefData.FIRSTLAUNCH)
        val tempStorage = HashMap<String, Boolean>()
        for (key in keysToKeep) {
            tempStorage[key] = settings.getBoolean(key, false)
        }
        editor.clear().commit()
        val editor = editor
        for ((key, value) in tempStorage) {
            editor.putBoolean(key, value)
        }
        editor.commit()
    }

    private fun clearRealmDb(){
        val realm = Realm.getDefaultInstance()
        realm.executeTransaction { transactionRealm ->
            transactionRealm.deleteAll()
        }
        realm.close()
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val mainIntent = Intent.makeRestartActivityTask(intent?.component)
        startActivity(mainIntent)
        Runtime.getRuntime().exit(0)
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
//        spinner = dialog.findViewById(R.id.intervalDropper) as Spinner
        radioGroup = dialog.findViewById(R.id.intervalDropper) as RadioGroup
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
            radioGroupServers!!.visibility = View.VISIBLE
        } else {
            radioGroupServers!!.visibility = View.GONE
            intervalLabel.visibility = View.GONE
        }
    }

    @Throws(Exception::class)
    fun isServerReachable(processedUrl: String?): Boolean {
        customProgressDialog?.setText(getString(R.string.connecting_to_server))
        customProgressDialog?.show()
        val apiInterface = client?.create(ApiInterface::class.java)
        apiInterface?.isPlanetAvailable("$processedUrl/_all_dbs")?.enqueue(
            object : Callback<ResponseBody?> { override fun onResponse(call: Call<ResponseBody?>, response: Response<ResponseBody?>) {
                try {
                    customProgressDialog?.dismiss()
                    val ss = response.body()?.string()
                    val myList = ss?.split(",".toRegex())?.dropLastWhile { it.isEmpty() }?.let { listOf(*it.toTypedArray()) }
                    if ((myList?.size ?: 0) < 8) {
                        alertDialogOkay(getString(R.string.check_the_server_address_again_what_i_connected_to_wasn_t_the_planet_server))
                    } else {
                        startSync()
                    }
                } catch (e: Exception) {
                    alertDialogOkay(getString(R.string.device_couldn_t_reach_server_check_and_try_again))
                    customProgressDialog?.dismiss()
                }
            }

                override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                    alertDialogOkay(getString(R.string.device_couldn_t_reach_server_check_and_try_again))
                    customProgressDialog?.dismiss()
                }
            })
        return connectionResult
    }

    private fun dateCheck(dialog: MaterialDialog) {
        // Check if the user never synced
        syncDate = dialog.findViewById(R.id.lastDateSynced) as TextView
        syncDate.text = "${getString(R.string.last_sync_date)}${convertDate()}"
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

    private fun saveSyncInfoToPreference() {
        editor.putBoolean("autoSync", syncSwitch.isChecked)
        editor.putInt("autoSyncInterval", syncTimeInteval[radioGroupServers!!.checkedRadioButtonId])
        radioGroupServers?.let { editor.putInt("autoSyncPosition", it.checkedRadioButtonId) }
        editor.commit()
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
            val db_users = mRealm.where(RealmUserModel::class.java).equalTo("name", username).findAll()
            for (user in db_users) {
                if (user._id?.isEmpty() == true) {
                    if (username == user.name && password == user.password) {
                        saveUserInfoPref(settings, password, user)
                        return true
                    }
                } else {
                    if (AndroidDecrypter(username, password, user.derived_key, user.salt)) {
                        if (isManagerMode && !user.isManager()) return false
                        saveUserInfoPref(settings, password, user)
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

    fun startSync() {
        SyncManager.instance?.start(this@SyncActivity)
    }

    private fun saveConfigAndContinue(dialog: MaterialDialog): String {
        dialog.dismiss()
        saveSyncInfoToPreference()
        var processedUrl = ""
        val protocol = settings.getString("serverProtocol", "")
        var url = "${(dialog.customView?.findViewById<View>(R.id.input_server_url) as EditText).text}"
        val pin = "${(dialog.customView?.findViewById<View>(R.id.input_server_Password) as EditText).text}"
        editor.putString("customDeviceName", "${(dialog.customView?.findViewById<View>(R.id.deviceName) as EditText).text}").apply()
        url = protocol + url
        if (isUrlValid(url)) processedUrl = setUrlParts(url, pin)
        return processedUrl
    }

    override fun onSyncStarted() {
        customProgressDialog?.setText(getString(R.string.syncing_data_please_wait))
        customProgressDialog?.show()
    }

    override fun onSyncFailed(msg: String?) {
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
        customProgressDialog?.dismiss()
        if (::syncIconDrawable.isInitialized) {
            runOnUiThread {
                syncIconDrawable = syncIcon.drawable as AnimationDrawable
                syncIconDrawable.stop()
                syncIconDrawable.selectDrawable(0)
                syncIcon.invalidateDrawable(syncIconDrawable)
                showSnack(findViewById(android.R.id.content), getString(R.string.sync_completed))
                downloadAdditionalResources()
                cancellAll(this)
            }
        }
    }

    private fun downloadAdditionalResources() {
        val storedJsonConcatenatedLinks = settings.getString("concatenated_links", null)
        if (storedJsonConcatenatedLinks != null) {
            val storedConcatenatedLinks: ArrayList<String> = Gson().fromJson(storedJsonConcatenatedLinks, object : TypeToken<ArrayList<String>>() {}.type)
            openDownloadService(context, storedConcatenatedLinks, true)
        }
    }

    fun forceSyncTrigger(): Boolean {
        if (settings.getLong(getString(R.string.last_syncs), 0) <= 0) {
            lblLastSyncDate.text = getString(R.string.last_synced_never)
        } else {
            lblLastSyncDate.text = "${getString(R.string.last_sync)} ${getRelativeTime(settings.getLong(getString(R.string.last_syncs), 0))}"
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
        cal_last_Sync.timeInMillis = settings.getLong("LastSync", 0)
        cal_today.timeInMillis = Date().time
        val msDiff = Calendar.getInstance().timeInMillis - cal_last_Sync.timeInMillis
        val daysDiff = TimeUnit.MILLISECONDS.toDays(msDiff)
        return if (daysDiff >= maxDays) {
            val alertDialogBuilder = AlertDialog.Builder(this)
            alertDialogBuilder.setMessage(
                getString(R.string.it_has_been_more_than) + (daysDiff - 1) + getString(
                    R.string.days_since_you_last_synced_this_device
                ) + getString(R.string.connect_it_to_the_server_over_wifi_and_sync_it_to_reactivate_this_tablet)
            )
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
        val handler = UserProfileDbHandler(this)
        handler.onLogin()
        handler.onDestory()
        editor.putBoolean(Constants.KEY_LOGIN, true).commit()
        openDashboard()
    }

    fun settingDialog() {
        val dialogServerUrlBinding = DialogServerUrlBinding.inflate(LayoutInflater.from(this))
        protocol_checkin = dialogServerUrlBinding.radioProtocol
        serverUrl = dialogServerUrlBinding.inputServerUrl
        serverPassword = dialogServerUrlBinding.inputServerPassword
        radioGroupServers = dialogServerUrlBinding.serverUrls

        dialogServerUrlBinding.deviceName.setText(NetworkUtils.getDeviceName())
        val builder = MaterialDialog.Builder(this)
            .customView(dialogServerUrlBinding.root, true)
            .positiveText(R.string.btn_sync)
            .negativeText(R.string.btn_sync_cancel)
            .neutralText(R.string.btn_sync_save)
            .onPositive { dialog: MaterialDialog, _: DialogAction? ->
                serverConfigAction = "sync"
                val protocol = "${settings.getString("serverProtocol", "")}"
                var url = "${serverUrl.text}"
                val pin = "${serverPassword.text}"
                editor.putString("serverURL", url).apply()
                url = protocol + url
                if (isUrlValid(url)) {
                    currentDialog = dialog
                    service.getMinApk(this, url, pin)
                }
            }
            .onNeutral { dialog: MaterialDialog, _: DialogAction? ->
                serverConfigAction = "save"
                val protocol = "${settings.getString("serverProtocol", "")}"
                var url = "${serverUrl.text}"
                val pin = "${serverPassword.text}"
                url = protocol + url
                if (isUrlValid(url)) {
                    currentDialog = dialog
                    service.getMinApk(this, url, pin)
                }
            }
        if (!prefData.getMANUALCONFIG()) {
            dialogServerUrlBinding.manualConfiguration.isChecked = false
            showConfigurationUIElements(dialogServerUrlBinding, false)
        } else {
            dialogServerUrlBinding.manualConfiguration.isChecked = true
            showConfigurationUIElements(dialogServerUrlBinding, true)
            dialogServerUrlBinding.serverUrls.visibility = View.GONE
        }
        val dialog = builder.build()
        positiveAction = dialog.getActionButton(DialogAction.POSITIVE)
        dialogServerUrlBinding.manualConfiguration.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                prefData.setMANUALCONFIG(true)
                editor.putString("serverURL", "").apply()
                editor.putString("serverPin", "").apply()
                dialogServerUrlBinding.radioHttp.isChecked = true
                editor.putString("serverProtocol", getString(R.string.http_protocol)).apply()
                showConfigurationUIElements(dialogServerUrlBinding, true)
                val communities: List<RealmCommunity> = mRealm.where(RealmCommunity::class.java).sort("weight", Sort.ASCENDING).findAll()
                val nonEmptyCommunities: MutableList<RealmCommunity> = ArrayList()
                for (community in communities) {
                    if (community.isValid && !TextUtils.isEmpty(community.name)) {
                        nonEmptyCommunities.add(community)
                    }
                }
                dialogServerUrlBinding.switchServerUrl.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
                    editor.putBoolean("switchCloudUrl", b).apply()
                    radioGroupServers.visibility = if (b) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                    setUrlAndPin(dialogServerUrlBinding.switchServerUrl.isChecked)
                }
                serverUrl.addTextChangedListener(MyTextWatcher(serverUrl))
                dialogServerUrlBinding.switchServerUrl.isChecked = settings.getBoolean("switchCloudUrl", false)
                setUrlAndPin(settings.getBoolean("switchCloudUrl", false))
                protocol_semantics()
            } else {
                prefData.setMANUALCONFIG(false)
                showConfigurationUIElements(dialogServerUrlBinding, false)
                editor.putBoolean("switchCloudUrl", false).apply()
            }
        }
        dialogServerUrlBinding.radioProtocol.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            when (checkedId) {
                R.id.radio_http -> editor.putString("serverProtocol", getString(R.string.http_protocol)).apply()
                R.id.radio_https -> editor.putString("serverProtocol", getString(R.string.https_protocol)).apply()
            }
        }
        dialogServerUrlBinding.clearData.setOnClickListener {
            clearDataDialog(getString(R.string.are_you_sure_you_want_to_clear_data))
        }

        val teams: List<RealmMyTeam> = mRealm.where(RealmMyTeam::class.java).isEmpty("teamId").equalTo("status", "active").findAll()
        if (teams.isNotEmpty() && "${dialogServerUrlBinding.inputServerUrl.text}" != "") {
            dialogServerUrlBinding.team.visibility = View.VISIBLE
            teamAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, teamList)
            teamAdapter?.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            teamList.clear()
            teamList.add("select team")
            for (team in teams) {
                if (team.isValid) {
                    teamList.add(team.name)
                }
            }
            dialogServerUrlBinding.team.adapter = teamAdapter
            val lastSelection = prefData.getSELECTEDTEAMID()
            if (!lastSelection.isNullOrEmpty()) {
                for (i in teams.indices) {
                    val team = teams[i]
                    if (team._id != null && team._id == lastSelection && team.isValid) {
                        val lastSelectedPosition = i + 1
                        dialogServerUrlBinding.team.setSelection(lastSelectedPosition)
                        break
                    }
                }
            }
            dialogServerUrlBinding.team.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View, position: Int, id: Long) {
                    if (position > 0) {
                        val selectedTeam = teams[position - 1]
                        val currentTeamId = prefData.getSELECTEDTEAMID()
                        if (currentTeamId != selectedTeam._id) {
                            prefData.setSELECTEDTEAMID(selectedTeam._id)
                            if (this@SyncActivity is LoginActivity) {
                                this@SyncActivity.getTeamMembers()
                            }
                            dialog.dismiss()
                        }
                    }
                }

                override fun onNothingSelected(parentView: AdapterView<*>?) {}
            }
        } else if (teams.isNotEmpty() && "${dialogServerUrlBinding.inputServerUrl.text}" == "") {
            dialogServerUrlBinding.team.visibility = View.GONE
        } else {
            dialogServerUrlBinding.team.visibility = View.GONE
        }
        dialog.show()
        sync(dialog)

        radioGroupServers.setOnCheckedChangeListener { group, checkedId ->
            val radioButton = group.findViewById<RadioButton>(checkedId)
            val actualUrl = radioButton.text.toString().replace(Regex("[\\p{So}\\s]"), "")
            serverUrl.setText(actualUrl)
            serverPassword.setText(getPinForUrl(actualUrl))
        }
    }

    private fun showConfigurationUIElements(binding: DialogServerUrlBinding, show: Boolean) {
        binding.radioProtocol.visibility = if (show) View.VISIBLE else View.GONE
        binding.switchServerUrl.visibility = if (show) View.VISIBLE else View.GONE
        binding.ltProtocol.visibility = if (show) View.VISIBLE else View.GONE
        binding.ltIntervalLabel.visibility = if (show) View.VISIBLE else View.GONE
        binding.syncSwitch.visibility = if (show) View.VISIBLE else View.GONE
        binding.ltDeviceName.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            radioGroupServers!!.visibility = View.GONE
            serverUrl.visibility = View.VISIBLE
            if (settings.getString("serverURL", "") == "https://planet.learning.ole.org") {
                editor.putString("serverURL", "").apply()
                editor.putString("serverPin", "").apply()
            }
            if (settings.getString("serverProtocol", "") == getString(R.string.http_protocol)) {
                binding.radioHttp.isChecked = true
                editor.putString("serverProtocol", getString(R.string.http_protocol)).apply()
            }
            if (settings.getString("serverProtocol", "") == getString(R.string.https_protocol) && settings.getString("serverURL", "") != "" && settings.getString("serverURL", "") != "https://planet.learning.ole.org") {
                binding.radioHttps.isChecked = true
                editor.putString("serverProtocol", getString(R.string.https_protocol)).apply()
            }
            serverUrl.setText(settings.getString("serverURL", "")?.let { removeProtocol(it) })
            serverPassword.setText(settings.getString("serverPin", ""))
            serverUrl.isEnabled = true
            serverPassword.isEnabled = true
        } else {
            serverUrl.visibility = View.GONE
            radioGroupServers!!.visibility = View.VISIBLE

            val serverList = listOf(
                "🌎 planet.learning.ole.org",
                "🇬🇹 planet.guatemala.ole.org",
                "🇬🇹 planet.sanpablo.ole.org",
            )
            val serverName = listOf(
                "Planet Learning",
                "Planet Guatemala",
                "Planet San Pablo"
            )
            radioGroupServers.removeAllViews()
            for (server in serverList) {
                val radioButton = RadioButton(this)
                radioButton.text = server
                radioButton.id = View.generateViewId()
                radioGroupServers.addView(radioButton)
            }
            val planetLearningRadioButton = radioGroupServers.getChildAt(0) as RadioButton
            planetLearningRadioButton.performClick()
            val planetLearningUrl = "planet.learning.ole.org"
            serverUrl.setText(planetLearningUrl.replace(Regex("^https?://"), ""))
            serverPassword.setText(getPinForUrl(planetLearningUrl))

            val storedUrl = settings.getString("serverURL", null)
            val storedPin = settings.getString("serverPin", null)
            if (storedUrl != null) {
                val urlWithoutProtocol = storedUrl.replace(Regex("^https?://"), "")
                for (i in 0 until radioGroupServers.childCount) {
                    val radioButton = radioGroupServers.getChildAt(i) as RadioButton
                    if (radioButton.text.toString().contains(urlWithoutProtocol)) {
                        radioButton.isChecked = true
                        break
                    }
                }
                serverUrl.setText(urlWithoutProtocol)
                serverPassword.setText(storedPin)
            }

            serverUrl.isEnabled = false
            serverPassword.isEnabled = false
            editor.putString("serverProtocol", getString(R.string.https_protocol)).apply()
        }
    }

    private fun getPinForUrl(url: String): String {
        val pinMap = mapOf(
            "planet.learning.ole.org" to "1983",
            "planet.guatemala.ole.org" to "5562",
            "planet.sanpablo.ole.org" to "0948",
        )
        return pinMap[url] ?: ""
    }

    private fun onChangeServerUrl() {
        val selected = findViewById<RadioButton>(radioGroupServers.checkedRadioButtonId)
        if (selected != null) {
            serverUrl.setText(selected.text.toString().replace(Regex("[\\p{So}\\s]"), ""))
            protocol_checkin.check(R.id.radio_https)
            settings.getString("serverProtocol", getString(R.string.https_protocol))
            serverPassword.setText(getPinForUrl(selected.text.toString().replace(Regex("[\\p{So}\\s]"), "")))
            serverPassword.isEnabled = true
        }
    }

    private fun setUrlAndPin(checked: Boolean) {
        if (checked) {
            onChangeServerUrl()
        } else {
            serverUrl.setText(settings.getString("serverURL", "")?.let { removeProtocol(it) })
            serverPassword.setText(settings.getString("serverPin", ""))
            protocol_checkin.check(
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
        protocol_checkin.isEnabled = !checked
    }

    private fun protocol_semantics() {
        protocol_checkin.setOnCheckedChangeListener { _: RadioGroup?, i: Int ->
            when (i) {
                R.id.radio_http -> editor.putString("serverProtocol", getString(R.string.http_protocol)).apply()
                R.id.radio_https -> editor.putString("serverProtocol", getString(R.string.https_protocol)).apply()
            }
        }
    }

    private fun removeProtocol(url: String): String {
        var url = url
        url = url.replaceFirst(getString(R.string.https_protocol).toRegex(), "")
        url = url.replaceFirst(getString(R.string.http_protocol).toRegex(), "")
        return url
    }

    private fun continueSync(dialog: MaterialDialog) {
        processedUrl = saveConfigAndContinue(dialog)
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
                    showAlert(context, "Error", getString(R.string.planet_server_not_reachable))
                }
            }
        })
    }

    override fun onSuccess(success: String?) {
        if (customProgressDialog?.isShowing() == true && success?.contains("Crash") == true) {
            customProgressDialog?.dismiss()
        }
        if (::btnSignIn.isInitialized) {
            showSnack(btnSignIn, success)
        }
        editor.putLong("lastUsageUploaded", Date().time).apply()
        if (::lblLastSyncDate.isInitialized) {
            lblLastSyncDate.text = "${getString(R.string.last_sync)}${getRelativeTime(Date().time)} >>"
        }
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
        customProgressDialog?.setText(getString(R.string.checking_version))
        customProgressDialog?.show()
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
        customProgressDialog?.dismiss()
        if (!blockSync) continueSyncProcess() else {
            syncIconDrawable.stop()
            syncIconDrawable.selectDrawable(0)
        }
    }

    private fun continueSyncProcess() {
        try {
            if (isSync) {
                isServerReachable(processedUrl)
            } else if (forceSync) {
                isServerReachable(processedUrl)
                startUpload()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
    }
}
