package org.ole.planet.myplanet.ui.sync

import android.Manifest
import android.app.ProgressDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.drawable.AnimationDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.URLUtil
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.textfield.TextInputLayout
import io.realm.Realm
import io.realm.Sort
import okhttp3.ResponseBody
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.AlertGuestLoginBinding
import org.ole.planet.myplanet.databinding.DialogServerUrlBinding
import org.ole.planet.myplanet.databinding.LayoutChildLoginBinding
import org.ole.planet.myplanet.datamanager.ApiClient.client
import org.ole.planet.myplanet.datamanager.ApiInterface
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.ManagerSync.Companion.instance
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.datamanager.Service.CheckVersionCallback
import org.ole.planet.myplanet.datamanager.Service.PlanetAvailableListener
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.RealmUserModel.Companion.createGuestUser
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.team.AdapterTeam.OnUserSelectedListener
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.AndroidDecrypter
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Constants.autoSynFeature
import org.ole.planet.myplanet.utilities.DialogUtils.getUpdateDialog
import org.ole.planet.myplanet.utilities.DialogUtils.showAlert
import org.ole.planet.myplanet.utilities.DialogUtils.showSnack
import org.ole.planet.myplanet.utilities.DialogUtils.showWifiSettingDialog
import org.ole.planet.myplanet.utilities.LocaleHelper
import org.ole.planet.myplanet.utilities.NetworkUtils.getCustomDeviceName
import org.ole.planet.myplanet.utilities.NetworkUtils.getDeviceName
import org.ole.planet.myplanet.utilities.NetworkUtils.isNetworkConnected
import org.ole.planet.myplanet.utilities.NotificationUtil.cancellAll
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.Utilities.getRelativeTime
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.text.Normalizer
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Objects
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


abstract class SyncActivity : ProcessUserDataActivity(), SyncListener, CheckVersionCallback,
    OnUserSelectedListener {
    private lateinit var syncDate: TextView
    lateinit var lblLastSyncDate: TextView
    private lateinit var intervalLabel: TextView
    lateinit var tvNodata: TextView
    lateinit var spinner: Spinner
    private lateinit var syncSwitch: SwitchCompat
    var convertedDate = 0
    private var connectionResult = false
    lateinit var mRealm: Realm
    private lateinit var editor: SharedPreferences.Editor
    private var syncTimeInteval = intArrayOf(60 * 60, 3 * 60 * 60)
    lateinit var syncIcon: ImageView
    lateinit var syncIconDrawable: AnimationDrawable
    lateinit var inputName: EditText
    lateinit var inputPassword: EditText
    private lateinit var serverUrl: EditText
    var serverUrlProtocol: EditText? = null
    private lateinit var serverPassword: EditText
    lateinit var inputLayoutName: TextInputLayout
    lateinit var inputLayoutPassword: TextInputLayout
    lateinit var prefData: SharedPrefManager
    lateinit var profileDbHandler: UserProfileDbHandler
    private lateinit var spnCloud: Spinner
    private lateinit var protocol_checkin: RadioGroup
    private var teamList = ArrayList<String?>()
    private var teamAdapter: ArrayAdapter<String?>? = null
    var selectedTeamId: String? = null
    lateinit var positiveAction: View
    lateinit var processedUrl: String
    var isSync = false
    var forceSync = false
    lateinit var btnSignIn: Button
    lateinit var becomeMember: Button
    lateinit var btnGuestLogin: Button
    lateinit var btnLang: Button
    lateinit var openCommunity: Button
    lateinit var btnFeedback: Button
    lateinit var customDeviceName: TextView
    lateinit var lblVersion: TextView
    lateinit var tvAvailableSpace: TextView
    private lateinit var defaultPref: SharedPreferences
    lateinit var imgBtnSetting: ImageButton
    lateinit var service: Service
    private lateinit var fallbackLanguage: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        editor = settings.edit()
        mRealm = DatabaseService(this).realmInstance
        requestAllPermissions()
        progressDialog = ProgressDialog(this)
        progressDialog?.setCancelable(false)
        prefData = SharedPrefManager(this)
        profileDbHandler = UserProfileDbHandler(this)
        defaultPref = PreferenceManager.getDefaultSharedPreferences(this)
        processedUrl = Utilities.getUrl()
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
        settings.edit().putBoolean("firstRun", false).apply()
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

    @Throws(Exception::class)
    fun isServerReachable(processedUrl: String?): Boolean {
        progressDialog?.setMessage(getString(R.string.connecting_to_server))
        progressDialog?.show()
        val apiInterface = client?.create(ApiInterface::class.java)
        Utilities.log("$processedUrl/_all_dbs")
        apiInterface?.isPlanetAvailable("$processedUrl/_all_dbs")?.enqueue(
            object : Callback<ResponseBody?> { override fun onResponse(call: Call<ResponseBody?>, response: Response<ResponseBody?>) {
                try {
                    progressDialog?.dismiss()
                    val ss = response.body()?.string()
                    val myList = ss?.split(",".toRegex())?.dropLastWhile { it.isEmpty() }
                        ?.let { listOf(*it.toTypedArray()) }
                    Utilities.log("List size $ss")
                    if ((myList?.size ?: 0) < 8) {
                        alertDialogOkay(getString(R.string.check_the_server_address_again_what_i_connected_to_wasn_t_the_planet_server))
                    } else {
                        startSync()
                    }
                } catch (e: Exception) {
                    alertDialogOkay(getString(R.string.device_couldn_t_reach_server_check_and_try_again))
                    progressDialog?.dismiss()
                }
            }

                override fun onFailure(call: Call<ResponseBody?>, t: Throwable) {
                    alertDialogOkay(getString(R.string.device_couldn_t_reach_server_check_and_try_again))
                    if (!mRealm.isClosed) {
                        mRealm.close()
                    }
                    progressDialog?.dismiss()
                }
            })
        return connectionResult
    }

    private fun declareHideKeyboardElements() {
        findViewById<View>(R.id.constraintLayout).setOnTouchListener { view: View?, _: MotionEvent? ->
            hideKeyboard(view)
            false
        }
    }

    private fun dateCheck(dialog: MaterialDialog) {
        // Check if the user never synced
        syncDate = dialog.findViewById(R.id.lastDateSynced) as TextView
        syncDate.text = "${getString(R.string.last_sync_date)}${convertDate()}"
        syncDropdownAdd()
    }

    // Converts OS date to human date
    private fun convertDate(): String {
        // Context goes here
        val lastSynced = settings.getLong("LastSync", 0)
        return if (lastSynced == 0L) {
            " Never Synced"
        } else {
            Utilities.getRelativeTime(lastSynced)
        }
        // <=== modify this when implementing this method
    }

    // Create items in the spinner
    private fun syncDropdownAdd() {
        val list: MutableList<String> = ArrayList()
        list.add("1 hour")
        list.add("3 hours")
        val spinnerArrayAdapter = ArrayAdapter(this, R.layout.spinner_item, list)
        spinnerArrayAdapter.setDropDownViewResource(R.layout.spinner_item)
        spinner.adapter = spinnerArrayAdapter
    }

    private fun saveSyncInfoToPreference() {
        editor.putBoolean("autoSync", syncSwitch.isChecked)
        editor.putInt("autoSyncInterval", syncTimeInteval[spinner.selectedItemPosition])
        editor.putInt("autoSyncPosition", spinner.selectedItemPosition)
        editor.commit()
    }

    fun authenticateUser(settings: SharedPreferences?, username: String?, password: String?, isManagerMode: Boolean): Boolean {
        return try {
            mRealm = Realm.getDefaultInstance()
            if (settings != null) {
                this.settings = settings
            }
            if (mRealm.isEmpty) {
                alertDialogOkay(getString(R.string.server_not_configured_properly_connect_this_device_with_planet_server))
                false
            } else {
                checkName(username, password, isManagerMode)
            }
        } finally {
            if (this::mRealm.isInitialized && !mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

    private fun checkName(username: String?, password: String?, isManagerMode: Boolean): Boolean {
        try {
            mRealm = Realm.getDefaultInstance()
//            val decrypt = AndroidDecrypter()
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
            if (this::mRealm.isInitialized && !mRealm.isClosed) {
                mRealm.close()
            }
            return false
        }
        return false
    }

    fun startSync() {
        Utilities.log("Start sync")
        SyncManager.instance?.start(this@SyncActivity)
    }

    private fun saveConfigAndContinue(dialog: MaterialDialog): String {
        dialog.dismiss()
        saveSyncInfoToPreference()
        var processedUrl = ""
        val protocol = (dialog.customView?.findViewById<View>(R.id.input_server_url_protocol) as EditText).text.toString()
        var url = (dialog.customView?.findViewById<View>(R.id.input_server_url) as EditText).text.toString()
        val pin = (dialog.customView?.findViewById<View>(R.id.input_server_Password) as EditText).text.toString()
        settings.edit().putString("customDeviceName", (dialog.customView?.findViewById<View>(R.id.deviceName) as EditText).text.toString()).apply()
        url = protocol + url
        if (isUrlValid(url)) processedUrl = setUrlParts(url, pin)
        return processedUrl
    }

    override fun onSyncStarted() {
        progressDialog?.setMessage(getString(R.string.syncing_data_please_wait))
        progressDialog?.show()
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
        progressDialog?.dismiss()
        if (::syncIconDrawable.isInitialized) {
            runOnUiThread {
                syncIconDrawable = syncIcon.drawable as AnimationDrawable
                syncIconDrawable.stop()
                syncIconDrawable.selectDrawable(0)
                syncIcon.invalidateDrawable(syncIconDrawable)
                showSnack(findViewById(android.R.id.content), getString(R.string.sync_completed))
                cancellAll(this)
            }
        }
    }

    fun declareElements() {
        if (!defaultPref.contains("beta_addImageToMessage")) {
            defaultPref.edit().putBoolean("beta_addImageToMessage", true).apply()
        }
        customDeviceName.text = getCustomDeviceName()
        if (::btnSignIn.isInitialized) {
            btnSignIn.setOnClickListener {
                if (TextUtils.isEmpty(inputName.text.toString())) {
                    inputName.error = getString(R.string.err_msg_name)
                } else if (TextUtils.isEmpty(inputPassword.text.toString())) {
                    inputPassword.error = getString(R.string.err_msg_password)
                } else {
                    val user = mRealm.where(RealmUserModel::class.java)
                        .equalTo("name", inputName.text.toString()).findFirst()
                    if (user == null || !user.isArchived) {
                        submitForm(inputName.text.toString(), inputPassword.text.toString())
                    } else {
                        val builder = AlertDialog.Builder(this)
                        builder.setMessage("member " + inputName.text.toString() + " is archived")
                        builder.setCancelable(false)
                        builder.setPositiveButton("Ok") { dialog: DialogInterface, _: Int ->
                            dialog.dismiss()
                            inputName.setText("")
                            inputPassword.setText("")
                        }
                        val dialog = builder.create()
                        dialog.show()
                    }
                }
            }
        }
        if (!settings.contains("serverProtocol")) settings.edit().putString("serverProtocol", "http://").apply()
        if (::becomeMember.isInitialized) {
            becomeMember.setOnClickListener {
                inputName.setText("")
                becomeAMember()
            }
        }
        if (::imgBtnSetting.isInitialized) {
            imgBtnSetting.setOnClickListener {
                inputName.setText("")
                settingDialog(this)
            }
        }
        if (::btnGuestLogin.isInitialized ) {
            btnGuestLogin.setOnClickListener {
                inputName.setText("")
                showGuestLoginDialog()
            }
        }
    }

    fun declareMoreElements() {
        try {
            mRealm = Realm.getDefaultInstance()
            syncIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.login_file_upload_animation))
            syncIcon.scaleType
            syncIconDrawable = syncIcon.drawable as AnimationDrawable
            syncIcon.setOnClickListener {
                syncIconDrawable.start()
                isSync = false
                forceSync = true
                service.checkVersion(this, settings) }
            declareHideKeyboardElements()
            lblVersion.text = "${resources.getText(R.string.version)} ${resources.getText(R.string.app_version)}"
            inputName.addTextChangedListener(MyTextWatcher(inputName))
            inputPassword.addTextChangedListener(MyTextWatcher(inputPassword))
            inputPassword.setOnEditorActionListener { _: TextView?, actionId: Int, event: KeyEvent? ->
                if (actionId == EditorInfo.IME_ACTION_DONE || event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    btnSignIn.performClick()
                    return@setOnEditorActionListener true
                }
                false
            }
            setUpLanguageButton()
            if (defaultPref.getBoolean("saveUsernameAndPassword", false)) {
                inputName.setText(settings.getString(getString(R.string.login_user), ""))
                inputPassword.setText(settings.getString(getString(R.string.login_password), ""))
            }
            if (isNetworkConnected()) {
                service.syncPlanetServers(mRealm) { success: String? ->
                    Utilities.toast(this, success)
                }
            }
            inputName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    val lowercaseText = s.toString().lowercase()
                    if (s.toString() != lowercaseText) {
                        inputName.setText(lowercaseText)
                        inputName.setSelection(lowercaseText.length)
                    }
                }

                override fun afterTextChanged(s: Editable) {}
            })
        } finally {
            if (this::mRealm.isInitialized && !mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

    private fun setUpLanguageButton() {
        val languageKey = resources.getStringArray(R.array.language_keys)
        val languages = resources.getStringArray(R.array.language)
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        val systemLanguage = Resources.getSystem().configuration.locale.language
        val languageKeyList = listOf(*languageKey)
        val index: Int
        if (languageKeyList.contains(systemLanguage)) {
            pref.edit().putString("app_language", systemLanguage).apply()
            index = languageKeyList.indexOf(systemLanguage)
        } else {
            fallbackLanguage = "en"
            pref.edit().putString("app_language", fallbackLanguage).apply()
            index = languageKeyList.indexOf(fallbackLanguage)
        }
        btnLang.text = languages[index]
        btnLang.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.select_language)
                .setSingleChoiceItems(languages, index, null)
                .setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int ->
                    dialog.dismiss()
                    val selectedPosition = (dialog as AlertDialog).listView.checkedItemPosition
                    val selectedLanguageKey = languageKey[selectedPosition]
                    if (selectedLanguageKey != pref.getString("app_language", fallbackLanguage)) {
                        LocaleHelper.setLocale(this, selectedLanguageKey)
                        pref.edit().putString("app_language", selectedLanguageKey).apply()
                        recreate()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    fun submitForm(name: String?, password: String?) {
        if (forceSyncTrigger()) {
            return
        }
        val editor = settings.edit()
        editor.putString("loginUserName", name)
        editor.putString("loginUserPassword", password)
        val isLoggedIn = authenticateUser(settings, name, password, false)
        if (isLoggedIn) {
            Toast.makeText(applicationContext, getString(R.string.thank_you), Toast.LENGTH_SHORT)
                .show()
            onLogin()
            saveUsers(inputName.text.toString(), inputPassword.text.toString(), "member")
        } else {
            instance?.login(name, password, object : SyncListener {
                override fun onSyncStarted() {
                    progressDialog?.setMessage(getString(R.string.please_wait))
                    progressDialog?.show()
                }

                override fun onSyncComplete() {
                    progressDialog?.dismiss()
                    Utilities.log("on complete")
                    val log = authenticateUser(settings, name, password, true)
                    if (log) {
                        Toast.makeText(applicationContext, getString(R.string.thank_you), Toast.LENGTH_SHORT).show()
                        onLogin()
                        saveUsers(inputName.text.toString(), inputPassword.text.toString(), "member")
                    } else {
                        alertDialogOkay(getString(R.string.err_msg_login))
                    }
                    syncIconDrawable.stop()
                    syncIconDrawable.selectDrawable(0)
                }

                override fun onSyncFailed(msg: String?) {
                    Utilities.toast(MainApplication.context, msg)
                    progressDialog?.dismiss()
                    syncIconDrawable.stop()
                    syncIconDrawable.selectDrawable(0)
                }
            })
        }
        editor.apply()
    }

    private fun becomeAMember() {
        if (Utilities.getUrl().isNotEmpty()) {
            startActivity(Intent(this, BecomeMemberActivity::class.java))
        } else {
            Utilities.toast(this, getString(R.string.please_enter_server_url_first))
            settingDialog(this)
        }
    }

    fun forceSyncTrigger(): Boolean {
        if (Objects.equals(getRelativeTime(settings.getLong(getString(R.string.last_syncs), 0)), "Jan 1, 1970")) {
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

//    fun forceSyncTrigger(): Boolean {
//        lblLastSyncDate.text = getString(R.string.last_sync) + Utilities.getRelativeTime(settings.getLong(getString(R.string.last_syncs), 0)) + " >>"
//        if (autoSynFeature(Constants.KEY_AUTOSYNC_, applicationContext) && autoSynFeature(Constants.KEY_AUTOSYNC_WEEKLY, applicationContext)) {
//            return checkForceSync(7)
//        } else if (autoSynFeature(Constants.KEY_AUTOSYNC_, applicationContext) && autoSynFeature(Constants.KEY_AUTOSYNC_MONTHLY, applicationContext)
//        ) {
//            return checkForceSync(30)
//        }
//        return false
//    }

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
            Log.e("Sync Date ", "Expired - ")
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
            Log.e("Sync Date ", "Not up to  - $maxDays")
            false
        }
    }

    private fun showGuestLoginDialog() {
        try {
            mRealm = Realm.getDefaultInstance()
            mRealm.refresh()
            editor = settings.edit()
            val alertGuestLoginBinding = AlertGuestLoginBinding.inflate(LayoutInflater.from(this))
            val v: View = alertGuestLoginBinding.root
            alertGuestLoginBinding.etUserName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    val input = s.toString()
                    val firstChar = if (input.isNotEmpty()) {
                        input[0]
                    } else {
                        '\u0000'
                    }
                    var hasInvalidCharacters = false
                    val hasSpecialCharacters: Boolean
                    var hasDiacriticCharacters = false
                    val normalizedText = Normalizer.normalize(s, Normalizer.Form.NFD)
                    for (element in input) {
                        if (element != '_' && element != '.' && element != '-' && !Character.isDigit(
                                element
                            ) && !Character.isLetter(element)) {
                            hasInvalidCharacters = true
                            break
                        }
                    }
                    val regex = ".*[ßäöüéèêæÆœøØ¿àìòùÀÈÌÒÙáíóúýÁÉÍÓÚÝâîôûÂÊÎÔÛãñõÃÑÕëïÿÄËÏÖÜŸåÅŒçÇðÐ].*"
                    val pattern = Pattern.compile(regex)
                    val matcher = pattern.matcher(input)
                    hasSpecialCharacters = matcher.matches()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        hasDiacriticCharacters = !normalizedText.codePoints()
                            .allMatch { codePoint: Int -> Character.isLetterOrDigit(codePoint) || codePoint == '.'.code || codePoint == '-'.code || codePoint == '_'.code }
                    }
                    if (!Character.isDigit(firstChar) && !Character.isLetter(firstChar)) {
                        alertGuestLoginBinding.etUserName.error = getString(R.string.must_start_with_letter_or_number)
                    } else if (hasInvalidCharacters || hasDiacriticCharacters || hasSpecialCharacters) {
                        alertGuestLoginBinding.etUserName.error = getString(R.string.only_letters_numbers_and_are_allowed)
                    } else {
                        val lowercaseText = input.lowercase()
                        if (input != lowercaseText) {
                            alertGuestLoginBinding.etUserName.setText(lowercaseText)
                            alertGuestLoginBinding.etUserName.setSelection(lowercaseText.length)
                        }
                        alertGuestLoginBinding.etUserName.error = null
                    }
                }

                override fun afterTextChanged(s: Editable) {}
            })
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Login As Guest")
                .setView(v)
                .setPositiveButton("Login", null)
                .setNegativeButton("Cancel", null)
            val dialog = builder.create()
            dialog.show()
            val login = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            login.setOnClickListener {
                if (mRealm.isEmpty) {
                    alertDialogOkay(getString(R.string.this_device_not_configured_properly_please_check_and_sync))
                    return@setOnClickListener
                }
                val username = alertGuestLoginBinding.etUserName.text.toString().trim { it <= ' ' }
                val firstChar = if (username.isEmpty()) null else username[0]
                var hasInvalidCharacters = false
                var hasDiacriticCharacters = false
                var hasSpecialCharacters = false
                var isValid = true
                val normalizedText = Normalizer.normalize(username, Normalizer.Form.NFD)
                val regex = ".*[ßäöüéèêæÆœøØ¿àìòùÀÈÌÒÙáíóúýÁÉÍÓÚÝâîôûÂÊÎÔÛãñõÃÑÕëïÿÄËÏÖÜŸåÅŒçÇðÐ].*"
                val pattern = Pattern.compile(regex)
                val matcher = pattern.matcher(username)
                if (TextUtils.isEmpty(username)) {
                    alertGuestLoginBinding.etUserName.error = getString(R.string.username_cannot_be_empty)
                    isValid = false
                }
                if (firstChar != null && !Character.isDigit(firstChar) && !Character.isLetter(firstChar)) {
                    alertGuestLoginBinding.etUserName.error = getString(R.string.must_start_with_letter_or_number)
                    isValid = false
                } else {
                    for (c in username.toCharArray()) {
                        if (c != '_' && c != '.' && c != '-' && !Character.isDigit(c) && !Character.isLetter(c)) {
                            hasInvalidCharacters = true
                            break
                        }
                        hasSpecialCharacters = matcher.matches()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            hasDiacriticCharacters = !normalizedText.codePoints()
                                .allMatch { codePoint: Int -> Character.isLetterOrDigit(codePoint) || codePoint == '.'.code || codePoint == '-'.code || codePoint == '_'.code }
                        }
                    }
                    if (hasInvalidCharacters || hasDiacriticCharacters || hasSpecialCharacters) {
                        alertGuestLoginBinding.etUserName.error = getString(R.string.only_letters_numbers_and_are_allowed)
                        isValid = false
                    }
                }
                if (isValid) {
                    val existingUser = mRealm.where(RealmUserModel::class.java).equalTo("name", username).findFirst()
                    dialog.dismiss()
                    if (existingUser != null) {
                        if (existingUser._id?.contains("guest") == true) {
                            showGuestDialog(username)
                        } else if (existingUser._id?.contains("org.couchdb.user:") == true) {
                            showUserAlreadyMemberDialog(username)
                        }
                    } else {
                        val model = createGuestUser(username, mRealm, settings)?.let { it1 ->
                            mRealm.copyFromRealm(it1)
                        }
                        if (model == null) {
                            Utilities.toast(this, getString(R.string.unable_to_login))
                        } else {
                            saveUsers(username, "", "guest")
                            saveUserInfoPref(settings, "", model)
                            onLogin()
                        }
                    }
                }
            }
            cancel.setOnClickListener { dialog.dismiss() }
        } finally {
            if (this::mRealm.isInitialized && !mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

    private fun showGuestDialog(username: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("$username is already a guest")
        builder.setMessage("Continue only if this is you")
        builder.setCancelable(false)
        builder.setNegativeButton("cancel") { dialog: DialogInterface, _: Int -> dialog.dismiss() }
        builder.setPositiveButton("continue") { dialog: DialogInterface, _: Int ->
            dialog.dismiss()
            val model = createGuestUser(username, mRealm, settings)?.let { mRealm.copyFromRealm(it) }
            if (model == null) {
                Utilities.toast(this, getString(R.string.unable_to_login))
            } else {
                saveUserInfoPref(settings, "", model)
                onLogin()
            }
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun showUserAlreadyMemberDialog(username: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("$username is already a member")
        builder.setMessage("Continue to login if this is you")
        builder.setCancelable(false)
        builder.setNegativeButton("Cancel") { dialog: DialogInterface, _: Int -> dialog.dismiss() }
        builder.setPositiveButton("login") { dialog: DialogInterface, _: Int ->
            dialog.dismiss()
            inputName.setText(username)
        }
        val dialog = builder.create()
        dialog.show()
    }

    fun saveUsers(name: String?, password: String?, source: String) {
        if (source === "guest") {
            val newUser = User("", name, password, "", "guest")
            val existingUsers: MutableList<User> = ArrayList(
                prefData.getSAVEDUSERS()
            )
            var newUserExists = false
            for ((_, name1) in existingUsers) {
                if (name1 == newUser.name?.trim { it <= ' ' }) {
                    newUserExists = true
                    break
                }
            }
            if (!newUserExists) {
                existingUsers.add(newUser)
                prefData.setSAVEDUSERS(existingUsers)
            }
        } else if (source === "member") {
            var userProfile = profileDbHandler.userModel?.userImage
            var fullName: String? = profileDbHandler.userModel?.getFullName()
            if (userProfile == null) {
                userProfile = ""
            }
            if (fullName?.trim { it <= ' ' }?.isEmpty() == true) {
                fullName = profileDbHandler.userModel?.name
            }
            val newUser = User(fullName, name, password, userProfile, "member")
            val existingUsers: MutableList<User> = ArrayList(prefData.getSAVEDUSERS())
            var newUserExists = false
            for ((fullName1) in existingUsers) {
                if (fullName1 == newUser.fullName?.trim { it <= ' ' }) {
                    newUserExists = true
                    break
                }
            }
            if (!newUserExists) {
                existingUsers.add(newUser)
                prefData.setSAVEDUSERS(existingUsers)
            }
        }
    }

    fun onLogin() {
        val handler = UserProfileDbHandler(this)
        handler.onLogin()
        handler.onDestory()
        editor.putBoolean(Constants.KEY_LOGIN, true).commit()
        openDashboard()
    }

    private fun settingDialog(activity: SyncActivity) {
        try {
            mRealm = Realm.getDefaultInstance()
            val dialogServerUrlBinding = DialogServerUrlBinding.inflate(LayoutInflater.from(this))
            spnCloud = dialogServerUrlBinding.spnCloud
            protocol_checkin = dialogServerUrlBinding.radioProtocol
            serverUrl = dialogServerUrlBinding.inputServerUrl
            serverPassword = dialogServerUrlBinding.inputServerPassword
            serverUrlProtocol = dialogServerUrlBinding.inputServerUrlProtocol
            dialogServerUrlBinding.deviceName.setText(getDeviceName())
            val builder = MaterialDialog.Builder(this)
            builder.customView(dialogServerUrlBinding.root, true)
                .positiveText(R.string.btn_sync)
                .negativeText(R.string.btn_sync_cancel)
                .neutralText(R.string.btn_sync_save)
                .onPositive { dialog: MaterialDialog, _: DialogAction? -> continueSync(dialog) }
                .onNeutral { dialog: MaterialDialog, _: DialogAction? ->
                    if (selectedTeamId == null) {
                        saveConfigAndContinue(dialog)
                    } else {
                        val url = "${serverUrlProtocol?.text}${serverUrl.text}"
                        if (isUrlValid(url)) {
                            prefData.setSELECTEDTEAMID(selectedTeamId)
                            (activity as LoginActivity).getTeamMembers()
                            saveConfigAndContinue(dialog)
                        } else {
                            saveConfigAndContinue(dialog)
                        }
                    }
                }
            if (!prefData.getMANUALCONFIG()) {
                dialogServerUrlBinding.manualConfiguration.isChecked = false
                showConfigurationUIElements(dialogServerUrlBinding, false)
            } else {
                dialogServerUrlBinding.manualConfiguration.isChecked = true
                showConfigurationUIElements(dialogServerUrlBinding, true)
            }
            val dialog = builder.build()
            positiveAction = dialog.getActionButton(DialogAction.POSITIVE)
            dialogServerUrlBinding.manualConfiguration.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
                if (isChecked) {
                    prefData.setMANUALCONFIG(true)
                    settings.edit().putString("serverURL", "").apply()
                    settings.edit().putString("serverPin", "").apply()
                    dialogServerUrlBinding.radioHttp.isChecked = true
                    settings.edit().putString("serverProtocol", getString(R.string.http_protocol)).apply()
                    showConfigurationUIElements(dialogServerUrlBinding, true)
                    val communities: List<RealmCommunity> = mRealm.where(
                        RealmCommunity::class.java
                    ).sort("weight", Sort.ASCENDING).findAll()
                    val nonEmptyCommunities: MutableList<RealmCommunity> = ArrayList()
                    for (community in communities) {
                        if (community.isValid && !TextUtils.isEmpty(community.name)) {
                            nonEmptyCommunities.add(community)
                        }
                    }
                    dialogServerUrlBinding.spnCloud.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, nonEmptyCommunities)
                    dialogServerUrlBinding.spnCloud.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(adapterView: AdapterView<*>?, view: View, i: Int, l: Long) {
                            onChangeServerUrl()
                        }

                        override fun onNothingSelected(adapterView: AdapterView<*>?) {}
                    }
                    dialogServerUrlBinding.switchServerUrl.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
                        settings.edit().putBoolean("switchCloudUrl", b).apply()
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
                    protocol_semantics()
                } else {
                    prefData.setMANUALCONFIG(false)
                    showConfigurationUIElements(dialogServerUrlBinding, false)
                    settings.edit().putBoolean("switchCloudUrl", false).apply()
                }
            }
            dialogServerUrlBinding.radioProtocol.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
                when (checkedId) {
                    R.id.radio_http -> settings.edit()
                        .putString("serverProtocol", getString(R.string.http_protocol)).apply()

                    R.id.radio_https -> settings.edit()
                        .putString("serverProtocol", getString(R.string.https_protocol)).apply()
                }
            }
            dialog.show()
            sync(dialog)
        } finally {
            if (this::mRealm.isInitialized && !mRealm.isClosed) {
                mRealm.close()
            }
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
            if (settings.getString("serverURL", "") == "https://planet.learning.ole.org") {
                settings.edit().putString("serverURL", "").apply()
                settings.edit().putString("serverPin", "").apply()
            }
            if (settings.getString("serverProtocol", "") == getString(R.string.http_protocol)) {
                binding.radioHttp.isChecked = true
                settings.edit().putString("serverProtocol", getString(R.string.http_protocol))
                    .apply()
            }
            if (settings.getString("serverProtocol", "") == getString(R.string.https_protocol)
                && settings.getString("serverURL", "") != ""
                && settings.getString("serverURL", "") != "https://planet.learning.ole.org"
            ) {
                binding.radioHttps.isChecked = true
                settings.edit().putString("serverProtocol", getString(R.string.https_protocol)).apply()
            }
            serverUrl.setText(settings.getString("serverURL", "")?.let { removeProtocol(it) })
            serverPassword.setText(settings.getString("serverPin", ""))
            serverUrl.isEnabled = true
            serverPassword.isEnabled = true
        } else {
            serverUrl.setText("planet.learning.ole.org")
            serverPassword.setText("1983")
            serverUrl.isEnabled = false
            serverPassword.isEnabled = false
            settings.edit().putString("serverProtocol", getString(R.string.https_protocol)).apply()
            serverUrlProtocol?.setText(getString(R.string.https_protocol))
        }
        try {
            mRealm = Realm.getDefaultInstance()
            val teams: List<RealmMyTeam> = mRealm.where(RealmMyTeam::class.java).isEmpty("teamId").findAll()
            if (teams.isNotEmpty() && show && binding.inputServerUrl.text.toString() != "") {
                binding.team.visibility = View.VISIBLE
                teamAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, teamList)
                teamAdapter?.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                teamList.clear()
                teamList.add("select team")
                for (team in teams) {
                    if (team.isValid) {
                        teamList.add(team.name)
                    }
                }
                binding.team.adapter = teamAdapter
                val lastSelection = prefData.getSELECTEDTEAMID()
                if (!lastSelection.isNullOrEmpty()) {
                    for (i in teams.indices) {
                        val team = teams[i]
                        if (team._id != null && team._id == lastSelection && team.isValid) {
                            val lastSelectedPosition = i + 1
                            binding.team.setSelection(lastSelectedPosition)
                            break
                        }
                    }
                }
                binding.team.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View, position: Int, id: Long) {
                        if (position > 0) {
                            val selectedTeam = teams[position - 1]
                            selectedTeamId = selectedTeam._id
                        }
                    }

                    override fun onNothingSelected(parentView: AdapterView<*>?) {
                        // Do nothing when nothing is selected
                    }
                }
            } else if (teams.isNotEmpty() && show && binding.inputServerUrl.text.toString() == "") {
                binding.team.visibility = View.GONE
            } else {
                binding.team.visibility = View.GONE
            }
        } finally {
            if (this::mRealm.isInitialized && !mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

    fun onChangeServerUrl() {
        try {
            mRealm = Realm.getDefaultInstance()
            val selected = spnCloud.selectedItem as RealmCommunity
            if (selected.isValid) {
                serverUrl.setText(selected.localDomain)
                protocol_checkin.check(R.id.radio_https)
                settings.getString("serverProtocol", getString(R.string.https_protocol))
                serverPassword.setText(if (selected.weight == 0) "1983" else "")
                serverPassword.isEnabled = selected.weight != 0
            }
        } finally {
            if (this::mRealm.isInitialized && !mRealm.isClosed) {
                mRealm.close()
            }
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
            serverUrlProtocol?.setText(settings.getString("serverProtocol", ""))
        }
        serverUrl.isEnabled = !checked
        serverPassword.isEnabled = !checked
        serverPassword.clearFocus()
        serverUrl.clearFocus()
        protocol_checkin.isEnabled = !checked
    }

    private fun protocol_semantics() {
        settings.edit().putString("serverProtocol", serverUrlProtocol?.text.toString()).apply()
        protocol_checkin.setOnCheckedChangeListener { _: RadioGroup?, i: Int ->
            when (i) {
                R.id.radio_http -> serverUrlProtocol?.setText(getString(R.string.http_protocol))
                R.id.radio_https -> serverUrlProtocol?.setText(getString(R.string.https_protocol))
            }
            settings.edit().putString("serverProtocol", serverUrlProtocol?.text.toString()).apply()
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
                Service(MainApplication.context).checkVersion(this@SyncActivity, settings)
            }

            override fun notAvailable() {
                if (!isFinishing) {
                    showAlert(
                        MainApplication.context,
                        "Error",
                        getString(R.string.planet_server_not_reachable)
                    )
                }
            }
        })
    }

    override fun onSuccess(success: String?) {
        Utilities.log("Sync completed ")
        if (progressDialog?.isShowing == true && success?.contains("Crash") == true) {
            progressDialog?.dismiss()
        }
        if (::btnSignIn.isInitialized) {
            showSnack(btnSignIn, success)
        }
        settings.edit().putLong("lastUsageUploaded", Date().time).apply()
        if (::lblLastSyncDate.isInitialized) {
            lblLastSyncDate.text =
                "${getString(R.string.last_sync)}${Utilities.getRelativeTime(Date().time)} >>"
        }
    }

    override fun onUpdateAvailable(info: MyPlanet?, cancelable: Boolean) {
        try {
            mRealm = Realm.getDefaultInstance()
            val builder = getUpdateDialog(this, info, progressDialog)
            if (cancelable || getCustomDeviceName(this).endsWith("###")) {
                builder.setNegativeButton(R.string.update_later) { _: DialogInterface?, _: Int ->
                    continueSyncProcess(forceSync = false, isSync = true)
                }
            } else {
                mRealm.executeTransactionAsync { realm: Realm -> realm.deleteAll() }
            }
            builder.setCancelable(cancelable)
            builder.show()
        } finally {
            if (this::mRealm.isInitialized && !mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

    override fun onCheckingVersion() {
        progressDialog?.setMessage(getString(R.string.checking_version))
        progressDialog?.show()
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
            settingDialog(this)
        }
        progressDialog?.dismiss()
        if (!blockSync) continueSyncProcess(forceSync = false, isSync = true) else {
            syncIconDrawable.stop()
            syncIconDrawable.selectDrawable(0)
        }
    }

    fun continueSyncProcess(forceSync: Boolean, isSync: Boolean) {
        Utilities.log("Upload : Continue sync process")
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
        try {
            mRealm = Realm.getDefaultInstance()
            val layoutChildLoginBinding = LayoutChildLoginBinding.inflate(
                layoutInflater
            )
            AlertDialog.Builder(this).setView(layoutChildLoginBinding.root)
                .setTitle(R.string.please_enter_your_password)
                .setPositiveButton(R.string.login) { _: DialogInterface?, _: Int ->
                    val password = layoutChildLoginBinding.etChildPassword.text.toString()
                    if (authenticateUser(settings, userModel.name, password, false)) {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.thank_you),
                            Toast.LENGTH_SHORT
                        ).show()
                        onLogin()
                    } else {
                        alertDialogOkay(getString(R.string.err_msg_login))
                    }
                }.setNegativeButton(R.string.cancel, null).show()
        } finally {
            if (this::mRealm.isInitialized && !mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

    fun getCustomDeviceName(): String? {
        return settings.getString("customDeviceName", getDeviceName())
    }

    fun resetGuestAsMember(username: String?) {
        val existingUsers = prefData.getSAVEDUSERS().toMutableList()
        var newUserExists = false
        for ((_, name) in existingUsers) {
            if (name == username) {
                newUserExists = true
                break
            }
        }
        if (newUserExists) {
            val iterator = existingUsers.iterator()
            while (iterator.hasNext()) {
                val (_, name) = iterator.next()
                if (name == username) {
                    iterator.remove()
                }
            }
            prefData.setSAVEDUSERS(existingUsers)
        }
    }

    inner class MyTextWatcher(var view: View?) : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
        override fun onTextChanged(s: CharSequence, i: Int, i1: Int, i2: Int) {
            val protocol = serverUrlProtocol?.text?.toString()
                ?: settings.getString(
                    "serverProtocol",
                    "http://"
                )
            if (view?.id == R.id.input_server_url) {
                positiveAction.isEnabled = "$s".trim { it <= ' ' }.isNotEmpty() && URLUtil.isValidUrl(protocol + "$s")
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
        const val PREFS_NAME = "OLE_PLANET"
        lateinit var cal_today: Calendar
        lateinit var cal_last_Sync: Calendar
    }
}
