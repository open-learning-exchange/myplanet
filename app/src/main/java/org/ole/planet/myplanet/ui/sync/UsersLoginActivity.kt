package org.ole.planet.myplanet.ui.sync

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.drawable.AnimationDrawable
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import io.realm.Realm
import io.realm.Sort
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.ActivityUsersLoginBinding
import org.ole.planet.myplanet.databinding.AlertGuestLoginBinding
import org.ole.planet.myplanet.databinding.DialogServerUrlBinding
import org.ole.planet.myplanet.databinding.LayoutChildLoginBinding
import org.ole.planet.myplanet.databinding.LayoutUserListBinding
import org.ole.planet.myplanet.datamanager.ManagerSync
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.datamanager.Service.CheckVersionCallback
import org.ole.planet.myplanet.datamanager.Service.PlanetAvailableListener
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmCommunity
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.community.HomeCommunityDialogFragment
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.team.AdapterTeam.OnUserSelectedListener
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity
import org.ole.planet.myplanet.ui.userprofile.TeamListAdapter
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.Utilities
import java.text.Normalizer
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class UsersLoginActivity : SyncActivity(), CheckVersionCallback, OnUserSelectedListener, TeamListAdapter.OnItemClickListener {
    private lateinit var activityUsersLoginBinding: ActivityUsersLoginBinding
    private lateinit var serverUrl: EditText
    private lateinit var serverUrlProtocol: EditText
    private lateinit var serverPassword: EditText
    private lateinit var processedUrl: String
    private lateinit var protocolCheckin: RadioGroup
    private lateinit var positiveAction: View
    private var isSync = false
    private var forceSync = false
    private var guest = false
    private lateinit var defaultPref: SharedPreferences
    private lateinit var service: Service
    private lateinit var spnCloud: Spinner
    private lateinit var prefData: SharedPrefManager
    private lateinit var profileDbHandler: UserProfileDbHandler
    var users: List<RealmUserModel>? = null
    var mAdapter: TeamListAdapter? = null
    private lateinit var cal_today: Calendar
    private lateinit var cal_last_Sync: Calendar
    private var selectedTeamId: String? = null
    var teamList: ArrayList<String> = ArrayList()
    var teamAdapter: ArrayAdapter<String>? = null

//    private var tvAvailableSpace: TextView? = null
//    private var previouslyLoggedIn: TextView? = null
//    private var customDeviceName: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityUsersLoginBinding = ActivityUsersLoginBinding.inflate(layoutInflater)
        setContentView(activityUsersLoginBinding.root)

        prefData = SharedPrefManager(this)
        profileDbHandler = UserProfileDbHandler(this)

//        tvAvailableSpace!!.text = FileUtils.getAvailableOverTotalMemoryFormattedString()
        changeLogoColor()
        service = Service(this)
        defaultPref = PreferenceManager.getDefaultSharedPreferences(this)
        declareElements()
        declareMoreElements()
        showWifiDialog()
        registerReceiver()
        forceSync = intent.getBooleanExtra("forceSync", false)
        processedUrl = Utilities.getUrl()
        if (forceSync) {
            isSync = false
        }
        if (intent.hasExtra("versionInfo")) {
            onUpdateAvailable(
                (intent.getSerializableExtra("versionInfo") as MyPlanet?)!!,
                intent.getBooleanExtra("cancelable", false)
            )
        } else {
            service.checkVersion(this, settings)
        }
        checkUsagesPermission()
        setUpChildMode()
        forceSyncTrigger()

        if (Utilities.getUrl().isNotEmpty()) {
            activityUsersLoginBinding.openCommunity.visibility = View.VISIBLE
            activityUsersLoginBinding.openCommunity.setOnClickListener {
                HomeCommunityDialogFragment().show(supportFragmentManager, "")
            }
            HomeCommunityDialogFragment().show(supportFragmentManager, "")
        } else {
            activityUsersLoginBinding.openCommunity.visibility = View.GONE
        }
        activityUsersLoginBinding.btnFeedback.setOnClickListener {
            FeedbackFragment().show(supportFragmentManager, "")
//            showUserList()
        }
//        previouslyLoggedIn!!.setOnClickListener { showUserList() }
        guest = intent.getBooleanExtra("guest", false)
        val username = intent.getStringExtra("username")

        if (guest) {
            val existingUsers = prefData.getSAVEDUSERS()
            var newUserExists = false
            for (user in existingUsers) {
                if (user.name == username) {
                    newUserExists = true
                    break
                }
            }

            if (newUserExists) {
                val iterator: MutableIterator<User> = existingUsers.iterator() as MutableIterator<User>
                while (iterator.hasNext()) {
                    val user = iterator.next()
                    if (user.name == username) {
                        iterator.remove()
                    }
                }
                prefData.setSAVEDUSERS(existingUsers)
            }
        }
        getTeamMembers()
    }

    private fun showUserList() {
        val layoutUserListBinding = LayoutUserListBinding.inflate(LayoutInflater.from(this))
        val view: View = layoutUserListBinding.root
        val builder = AlertDialog.Builder(this@UsersLoginActivity)
        builder.setTitle(R.string.select_user_to_login)
            .setView(view)
            .setNegativeButton(R.string.dismiss, null)
        val existingUsers = prefData.getSAVEDUSERS()
        val adapter = UserListAdapter(this@UsersLoginActivity, existingUsers)
        adapter.setOnItemClickListener(object : UserListAdapter.OnItemClickListener {
            override fun onItemClickGuest(name: String) {
                val model = mRealm.copyFromRealm(RealmUserModel.createGuestUser(name, mRealm, settings))
                if (model == null) {
                    Utilities.toast(this@UsersLoginActivity, getString(R.string.unable_to_login))
                } else {
                    saveUserInfoPref(settings, "", model)
                    onLogin()
                }
            }

            override fun onItemClickMember(name: String, password: String) {
                submitForm(name, password)
            }
        })
        layoutUserListBinding.listUser.adapter = adapter
        layoutUserListBinding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                adapter.filter.filter(charSequence)
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        val dialog = builder.create()
        dialog.show()
    }

    private fun forceSyncTrigger(): Boolean {
        lblLastSyncDate = activityUsersLoginBinding.lblLastSyncDate
        lblLastSyncDate.text = getString(R.string.last_sync) + Utilities.getRelativeTime(settings.getLong(getString(R.string.last_syncs), 0)) + " >>"
        if (Constants.autoSynFeature(Constants.KEY_AUTOSYNC_, applicationContext) && Constants.autoSynFeature(Constants.KEY_AUTOSYNC_WEEKLY, applicationContext)) {
            return checkForceSync(7)
        } else if (Constants.autoSynFeature(Constants.KEY_AUTOSYNC_, applicationContext) && Constants.autoSynFeature(Constants.KEY_AUTOSYNC_MONTHLY, applicationContext)) {
            return checkForceSync(30)
        }
        return false
    }

    private fun showWifiDialog() {
        if (intent.getBooleanExtra("showWifiDialog", false)) {
            DialogUtils.showWifiSettingDialog(this)
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
            alertDialogBuilder.setMessage(getString(R.string.it_has_been_more_than)
                    + (daysDiff - 1) + getString(R.string.days_since_you_last_synced_this_device)
                    + getString(R.string.connect_it_to_the_server_over_wifi_and_sync_it_to_reactivate_this_tablet))
            alertDialogBuilder.setPositiveButton(R.string.okay) { _: DialogInterface?, _: Int ->
                Toast.makeText(applicationContext, getString(R.string.connect_to_the_server_over_wifi_and_sync_your_device_to_continue), Toast.LENGTH_LONG).show()
            }
            alertDialogBuilder.show()
            true
        } else {
            false
        }
    }

    private fun declareElements() {
        if (!defaultPref.contains("beta_addImageToMessage")) {
            defaultPref.edit().putBoolean("beta_addImageToMessage", true).apply()
        }
//        customDeviceName!!.text = getCustomDeviceName()
        activityUsersLoginBinding.btnSignin.setOnClickListener {
            if (TextUtils.isEmpty(activityUsersLoginBinding.inputName.text.toString())) {
                activityUsersLoginBinding.inputName.error = getString(R.string.err_msg_name)
            } else if (TextUtils.isEmpty(activityUsersLoginBinding.inputPassword.text.toString())) {
                activityUsersLoginBinding.inputPassword.error = getString(R.string.err_msg_password)
            } else {
                submitForm(activityUsersLoginBinding.inputName.text.toString(), activityUsersLoginBinding.inputPassword.text.toString())
            }
        }
        if (!settings.contains("serverProtocol")) settings.edit().putString("serverProtocol", "http://").apply()
        activityUsersLoginBinding.becomeMember.setOnClickListener {
            becomeAMember()
        }
        activityUsersLoginBinding.imgBtnSetting.setOnClickListener {
            settingDialog()
        }
        activityUsersLoginBinding.btnGuestLogin.setOnClickListener {
            showGuestLoginDialog()
        }
//        switchChildMode!!.isChecked = settings.getBoolean("isChild", false)
//        switchChildMode!!.setOnCheckedChangeListener { _: CompoundButton?, b: Boolean ->
//
//            settings.edit().putBoolean("isChild", b).apply()
//            recreate()
//        }
    }

    private fun becomeAMember() {
        if (Utilities.getUrl().isNotEmpty()) {
            startActivity(Intent(this, BecomeMemberActivity::class.java))
        } else {
            Utilities.toast(this, getString(R.string.please_enter_server_url_first))
            settingDialog()
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
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    val input = s.toString()
                    val firstChar = if (input.isNotEmpty()) input[0] else '\u0000'
                    var hasInvalidCharacters = false
                    var hasSpecialCharacters = false
                    var hasDiacriticCharacters = false
                    val normalizedText = Normalizer.normalize(s, Normalizer.Form.NFD)
                    for (element in input) {
                        if (element != '_' && element != '.' && element != '-' && !Character.isDigit(element) && !Character.isLetter(element)) {
                            hasInvalidCharacters = true
                            break
                        }
                    }
                    val regex = ".*[ßäöüéèêæÆœøØ¿àìòùÀÈÌÒÙáíóúýÁÉÍÓÚÝâîôûÂÊÎÔÛãñõÃÑÕëïÿÄËÏÖÜŸåÅŒçÇðÐ].*"
                    val pattern = Pattern.compile(regex)
                    val matcher = pattern.matcher(input)
                    hasSpecialCharacters = matcher.matches()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        hasDiacriticCharacters = !normalizedText.codePoints().allMatch { codePoint: Int -> Character.isLetterOrDigit(codePoint) || codePoint == '.'.code || codePoint == '-'.code || codePoint == '_'.code }
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
                            hasDiacriticCharacters = !normalizedText.codePoints().allMatch { codePoint: Int -> Character.isLetterOrDigit(codePoint) || codePoint == '.'.code || codePoint == '-'.code || codePoint == '_'.code }
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
                        if (existingUser._id.contains("guest")) {
                            showGuestDialog(username)
                        } else if (existingUser._id.contains("org.couchdb.user:")) {
                            showUserAlreadyMemberDialog(username)
                        }
                    } else {
                        val model = mRealm.copyFromRealm(RealmUserModel.createGuestUser(username, mRealm, settings))
                        if (model == null) {
                            Utilities.toast(this@UsersLoginActivity, getString(R.string.unable_to_login))
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
            if (mRealm != null && !mRealm.isClosed) {
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
            val model = mRealm.copyFromRealm(RealmUserModel.createGuestUser(username, mRealm, settings))
            if (model == null) {
                Utilities.toast(this@UsersLoginActivity, getString(R.string.unable_to_login))
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
//            activityUsersLoginBinding.userName!!.text = username
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun continueSync(dialog: MaterialDialog) {
        processedUrl = saveConfigAndContinue(dialog)
        if (TextUtils.isEmpty(processedUrl)) return
        isSync = true
        if (checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) && settings.getBoolean("firstRun", true)) {
            clearInternalStorage()
        }
        Service(this).isPlanetAvailable(object : PlanetAvailableListener {
            override fun isAvailable() { Service(this@UsersLoginActivity).checkVersion(this@UsersLoginActivity, settings) }

            override fun notAvailable() {
                if (!isFinishing) { DialogUtils.showAlert(this@UsersLoginActivity, "Error", getString(R.string.planet_server_not_reachable)) }
            }
        })
    }

    private fun declareMoreElements() {
        syncIcon = activityUsersLoginBinding.syncIcon
        try {
            mRealm = Realm.getDefaultInstance()
            syncIcon.setImageDrawable(resources.getDrawable(R.drawable.login_file_upload_animation))
            syncIcon.scaleType
            syncIconDrawable = syncIcon.drawable as AnimationDrawable
            syncIcon.setOnClickListener {
                syncIconDrawable.start()
                isSync = false
                forceSync = true
                service.checkVersion(this, settings)
            }
            declareHideKeyboardElements()
            activityUsersLoginBinding.lblVersion.text = resources.getText(R.string.version).toString() + " " + resources.getText(R.string.app_version)
//            activityUsersLoginBinding.userName!!.addTextChangedListener(MyTextWatcher(activityUsersLoginBinding.userName!!))
//            activityUsersLoginBinding.inputPassword!!.addTextChangedListener(MyTextWatcher(activityUsersLoginBinding.inputPassword!!))
//            activityUsersLoginBinding.inputPassword!!.setOnEditorActionListener { _: TextView?, actionId: Int, event: KeyEvent? ->
//                if (actionId == EditorInfo.IME_ACTION_DONE || event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) {
//                    activityUsersLoginBinding.btnSignin!!.performClick()
//                    return@setOnEditorActionListener true
//                }
//                false
//            }

//            setUplanguageButton()
            if (defaultPref.getBoolean("saveUsernameAndPassword", false)) {
                activityUsersLoginBinding.inputName.setText(settings.getString(getString(R.string.login_user), ""))
                activityUsersLoginBinding.inputPassword.setText(settings.getString(getString(R.string.login_password), ""))
            }
            if (NetworkUtils.isNetworkConnected()) {
                service.syncPlanetServers(mRealm) { success: String? ->
                    Utilities.toast(this@UsersLoginActivity, success)
                }
            }
//            activityUsersLoginBinding.userName!!.addTextChangedListener(object : TextWatcher {
//                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
//
//                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
//                    val lowercaseText = s.toString().lowercase()
//                    if (s.toString() != lowercaseText) {
//                        activityUsersLoginBinding.userName!!.text = lowercaseText
//                        activityUsersLoginBinding.userName!!.setSelection(lowercaseText.length)
//                    }
//                }
//
//                override fun afterTextChanged(s: Editable) {}
//            })
        } finally {
            if (mRealm != null && !mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

//    private fun setUplanguageButton() {
//        val languageKey = resources.getStringArray(R.array.language_keys)
//        val languages = resources.getStringArray(R.array.language)
//        val pref = PreferenceManager.getDefaultSharedPreferences(this)
//        val index = listOf(*languageKey).indexOf(pref.getString("app_language", "en"))
//        btnLang!!.text = languages[index]
//        btnLang!!.setOnClickListener {
//            AlertDialog.Builder(this)
//                .setTitle(R.string.select_language)
//                .setSingleChoiceItems(resources.getStringArray(R.array.language), index, null)
//                .setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int ->
//                dialog.dismiss()
//                val selectedPosition = (dialog as AlertDialog).listView.checkedItemPosition
//                val lang = languageKey[selectedPosition]
//                LocaleHelper.setLocale(this@UsersLoginActivity, lang)
//                recreate()
//            }.setNegativeButton(R.string.cancel, null).show()
//        }
//    }

    private fun submitForm(name: String, password: String) {
        if (forceSyncTrigger()) {
            return
        }
        val editor = settings.edit()
        editor.putString("loginUserName", name)
        editor.putString("loginUserPassword", password)
        val isLoggedIn = authenticateUser(settings, name, password, false)
        if (isLoggedIn) {
            val existingUser = prefData.getSAVEDUSERS()
            for (user in existingUser) {
                if (user.name == name) {
                    user.password = password
                    user.source = "member"
                    break
                }
            }

            prefData.setSAVEDUSERS(existingUser)
            Toast.makeText(applicationContext, getString(R.string.thank_you), Toast.LENGTH_SHORT).show()
            onLogin()
            saveUsers(activityUsersLoginBinding.inputName!!.text.toString(), activityUsersLoginBinding.inputPassword.text.toString(), "member")
        } else {
            ManagerSync.getInstance().login(name, password, object : SyncListener {
                override fun onSyncStarted() {
                    progressDialog.setMessage(getString(R.string.please_wait))
                    progressDialog.show()
                }

                override fun onSyncComplete() {
                    progressDialog.dismiss()
                    Utilities.log("on complete")
                    val log = authenticateUser(settings, name, password, true)
                    if (log) {
                        Toast.makeText(applicationContext, getString(R.string.thank_you), Toast.LENGTH_SHORT).show()
                        onLogin()
                        saveUsers(activityUsersLoginBinding.inputName!!.text.toString(), activityUsersLoginBinding.inputPassword.text.toString(), "member")
                    } else {
                        alertDialogOkay(getString(R.string.err_msg_login))
                    }
                    syncIconDrawable.stop()
                    syncIconDrawable.selectDrawable(0)
                }

                override fun onSyncFailed(msg: String) {
                    Utilities.toast(this@UsersLoginActivity, msg)
                    progressDialog.dismiss()
                    syncIconDrawable.stop()
                    syncIconDrawable.selectDrawable(0)
                }
            })
        }
        editor.apply()
    }

    private fun saveUsers(name: String, password: String, source: String) {
        if (source === "guest") {
            val newUser = User("", name, password, "", "guest")
            val existingUsers: MutableList<User> = ArrayList(
                prefData.getSAVEDUSERS()
            )
            var newUserExists = false
            for ((_, name1) in existingUsers) {
                if (name1 == newUser.name.trim { it <= ' ' }) {
                    newUserExists = true
                    break
                }
            }
            if (!newUserExists) {
                existingUsers.add(newUser)
                prefData.setSAVEDUSERS(existingUsers)
            }
        } else if (source === "member") {
            var userProfile = profileDbHandler.userModel.userImage
            var fullName = profileDbHandler.userModel.fullName
            if (userProfile == null) {
                userProfile = ""
            }
            if (fullName.trim { it <= ' ' }.isEmpty()) {
                fullName = profileDbHandler.userModel.name
            }
            val newUser = User(fullName, name, password, userProfile, "member")
            val existingUsers: MutableList<User> = ArrayList(
                prefData.getSAVEDUSERS()
            )
            var newUserExists = false
            for ((fullName1) in existingUsers) {
                if (fullName1 == newUser.fullName.trim { it <= ' ' }) {
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

    private fun onLogin() {
        val handler = UserProfileDbHandler(this)
        handler.onLogin()
        handler.onDestory()
        editor.putBoolean(Constants.KEY_LOGIN, true).apply()
        openDashboard()
    }

    private fun settingDialog() {
        try {
            mRealm = Realm.getDefaultInstance()
            val dialogServerUrlBinding = DialogServerUrlBinding.inflate(LayoutInflater.from(this))
            spnCloud = dialogServerUrlBinding.spnCloud
            protocolCheckin = dialogServerUrlBinding.radioProtocol
            serverUrl = dialogServerUrlBinding.inputServerUrl
            serverPassword = dialogServerUrlBinding.inputServerPassword
            serverUrlProtocol = dialogServerUrlBinding.inputServerUrlProtocol
            dialogServerUrlBinding.deviceName.setText(NetworkUtils.getDeviceName())

            val builder = MaterialDialog.Builder(this@UsersLoginActivity)
            builder.title(R.string.action_settings)
                .customView(dialogServerUrlBinding.root, true)
                .positiveText(R.string.btn_sync)
                .negativeText(R.string.btn_sync_cancel)
                .neutralText(R.string.btn_sync_save)
                .onPositive { dialog: MaterialDialog?, _: DialogAction? -> continueSync(dialog!!) }
                .onNeutral { dialog: MaterialDialog?, _: DialogAction? ->
                    if (selectedTeamId == null) {
                        saveConfigAndContinue(dialog)
                    } else {
                        val url = serverUrlProtocol.text.toString() + serverUrl.text.toString()
                        if (isUrlValid(url)) {
                            prefData.setSELECTEDTEAMID(selectedTeamId)
                            getTeamMembers()
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
            dialogServerUrlBinding.manualConfiguration.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    prefData.setMANUALCONFIG(true)
                    settings.edit().putString("serverURL", "").apply()
                    settings.edit().putString("serverPin", "").apply()
                    dialogServerUrlBinding.radioHttp.isChecked = true
                    settings.edit().putString("serverProtocol", getString(R.string.http_protocol)).apply()
                    showConfigurationUIElements(dialogServerUrlBinding, true)
                    val communities: List<RealmCommunity> = mRealm.where(RealmCommunity::class.java).sort("weight", Sort.ASCENDING).findAll()
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
                        dialogServerUrlBinding.spnCloud.visibility = if (b) View.VISIBLE else View.GONE
                        setUrlAndPin(dialogServerUrlBinding.switchServerUrl.isChecked)
                    }

                    serverUrl.addTextChangedListener(MyTextWatcher(serverUrl))
                    dialogServerUrlBinding.switchServerUrl.isChecked = settings.getBoolean("switchCloudUrl", false)
                    setUrlAndPin(settings.getBoolean("switchCloudUrl", false))
                    protocolSemantics()
                } else {
                    prefData.setMANUALCONFIG(false)
                    showConfigurationUIElements(dialogServerUrlBinding, false)
                    settings.edit().putBoolean("switchCloudUrl", false).apply()
                }
            }
            dialogServerUrlBinding.radioProtocol.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    R.id.radio_http -> settings.edit().putString("serverProtocol", getString(R.string.http_protocol)).apply()
                    R.id.radio_https -> settings.edit().putString("serverProtocol", getString(R.string.https_protocol)).apply()
                }
            }
            dialog.show()
            sync(dialog)
        } finally {
            if (mRealm != null && !mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

    private fun showConfigurationUIElements(binding: DialogServerUrlBinding, show: Boolean) {
        binding.radioProtocol.visibility = if (show) View.VISIBLE else View.GONE
        binding.switchServerUrl.visibility = if (show) View.VISIBLE else View.GONE
        binding.ltProtocol.visibility = if (show) View.VISIBLE else View.GONE
        binding.ltIntervalLabel.visibility = if (show) View.VISIBLE else View.GONE
        binding.ltSyncSwitch.visibility = if (show) View.VISIBLE else View.GONE
        binding.ltDeviceName.visibility = if (show) View.VISIBLE else View.GONE

        if (show) {
            if (settings.getString("serverURL", "") == "https://planet.learning.ole.org") {
                settings.edit().putString("serverURL", "").apply()
                settings.edit().putString("serverPin", "").apply()
            }
            if (settings.getString("serverProtocol", "") == getString(R.string.http_protocol)) {
                binding.radioHttp.isChecked = true
                settings.edit().putString("serverProtocol", getString(R.string.http_protocol)).apply()
            }
            if (settings.getString("serverProtocol", "") == getString(R.string.https_protocol)
                && settings.getString("serverURL", "") != ""
                && settings.getString("serverURL", "") != "https://planet.learning.ole.org"
            ) {
                binding.radioHttps.isChecked = true
                settings.edit().putString("serverProtocol", getString(R.string.https_protocol)).apply()
            }
            serverUrl.setText(removeProtocol(settings.getString("serverURL", "")))
            serverPassword.setText(settings.getString("serverPin", ""))
            serverUrl.isEnabled = true
            serverPassword.isEnabled = true
        } else {
            serverUrl.setText("planet.learning.ole.org")
            serverPassword.setText("1983")
            serverUrl.isEnabled = false
            serverPassword.isEnabled = false
            settings.edit().putString("serverProtocol", getString(R.string.https_protocol)).apply()
            serverUrlProtocol.setText(getString(R.string.https_protocol))
        }

        try {
            mRealm = Realm.getDefaultInstance()
            val teams: List<RealmMyTeam> = mRealm.where(RealmMyTeam::class.java).isEmpty("teamId").findAll()
            if (teams.isNotEmpty() && show && binding.inputServerUrl.text.toString() != "") {
                binding.team.visibility = View.VISIBLE
                teamAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, teamList)
                teamAdapter!!.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                teamList.clear()
                teamList.add("Select team")
                for (team in teams) {
                    if (team.isValid) {
                        teamList.add(team.name)
                    }
                }
                binding.team.adapter = teamAdapter
                binding.team.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View, position: Int, id: Long) {
                        if (position > 0) {
                            val selectedTeam = teams[position - 1]
                            if (selectedTeam != null) {
                                selectedTeamId = selectedTeam._id
                            }
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
            if (mRealm != null && !mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

    private fun onChangeServerUrl() {
        try {
            mRealm = Realm.getDefaultInstance()
            val selected = spnCloud.selectedItem as RealmCommunity
            if (selected.isValid) {
                serverUrl.setText(selected.localDomain)
                protocolCheckin.check(R.id.radio_https)
                settings.getString("serverProtocol", "https://")
                serverPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                serverPassword.setText(if (selected.weight == 0) "1983" else "")
                serverPassword.isEnabled = selected.weight != 0
            }
        } finally {
            if (mRealm != null && !mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

    private fun setUrlAndPin(checked: Boolean) {
        if (checked) {
            onChangeServerUrl()
        } else {
            serverUrl.setText(removeProtocol(settings.getString("serverURL", "")))
            serverPassword.setText(settings.getString("serverPin", ""))
            protocolCheckin.check(
                if (TextUtils.equals(settings.getString("serverProtocol", ""), "http://")) R.id.radio_http else R.id.radio_https
            )
            serverUrlProtocol.setText(settings.getString("serverProtocol", ""))
        }
        serverUrl.isEnabled = !checked
        serverPassword.isEnabled = !checked
        serverPassword.clearFocus()
        serverUrl.clearFocus()
        protocolCheckin.isEnabled = !checked
    }

    private fun protocolSemantics() {
        settings.edit().putString("serverProtocol", serverUrlProtocol.text.toString()).apply()
        protocolCheckin.setOnCheckedChangeListener { _: RadioGroup?, i: Int ->
            when (i) {
                R.id.radio_http -> serverUrlProtocol.setText(getString(R.string.http_protocol))
                R.id.radio_https -> serverUrlProtocol.setText(getString(R.string.https_protocol))
            }
            settings.edit().putString("serverProtocol", serverUrlProtocol.text.toString()).apply()
        }
    }

    override fun onSuccess(s: String) {
        Utilities.log("Sync completed ")
        if (progressDialog.isShowing && s.contains("Crash")) progressDialog.dismiss()
        DialogUtils.showSnack(activityUsersLoginBinding.btnSignin, s)
        settings.edit().putLong("lastUsageUploaded", Date().time).apply()

        // Update last sync text
        lblLastSyncDate.text = getString(R.string.last_sync) + Utilities.getRelativeTime(Date().time) + " >>"
    }

    override fun onUpdateAvailable(info: MyPlanet, cancelable: Boolean) {
        try {
            mRealm = Realm.getDefaultInstance()
            val builder = DialogUtils.getUpdateDialog(this, info, progressDialog)
            if (cancelable || NetworkUtils.getCustomDeviceName(this).endsWith("###")) {
                builder.setNegativeButton(R.string.update_later) { _: DialogInterface?, _: Int -> continueSyncProcess() }
            } else {
                mRealm.executeTransactionAsync { realm: Realm -> realm.deleteAll() }
            }
            builder.setCancelable(cancelable)
            builder.show()
        } finally {
            if (mRealm != null && !mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

    override fun onCheckingVersion() {
        progressDialog.setMessage(getString(R.string.checking_version))
        progressDialog.show()
    }

    private fun registerReceiver() {
        val bManager = LocalBroadcastManager.getInstance(this)
        val intentFilter = IntentFilter()
        intentFilter.addAction(DashboardActivity.MESSAGE_PROGRESS)
        bManager.registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onError(msg: String, block: Boolean) {
        Utilities.toast(this, msg)
        if (msg.startsWith("Config")) {
            settingDialog()
        }
        progressDialog.dismiss()
        if (!block) continueSyncProcess() else {
            syncIconDrawable.stop()
            syncIconDrawable.selectDrawable(0)
        }
    }

    private fun continueSyncProcess() {
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
            val layoutChildLoginBinding = LayoutChildLoginBinding.inflate(layoutInflater)
            AlertDialog.Builder(this).setView(layoutChildLoginBinding.root)
                .setTitle(R.string.please_enter_your_password)
                .setPositiveButton(R.string.login) { _: DialogInterface?, _: Int ->
                    val password = layoutChildLoginBinding.etChildPassword.text.toString()
                    if (authenticateUser(settings, userModel.name, password, false)) {
                        Toast.makeText(applicationContext, getString(R.string.thank_you), Toast.LENGTH_SHORT).show()
                        onLogin()
                    } else {
                        alertDialogOkay(getString(R.string.err_msg_login))
                    }
                }.setNegativeButton(R.string.cancel, null).show()
        } finally {
            if (mRealm != null && !mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mRealm != null && !mRealm.isClosed) mRealm.close()
    }

    private fun removeProtocol(url: String?): String {
        var url = url
        url = url!!.replaceFirst(getString(R.string.https_protocol).toRegex(), "")
        url = url.replaceFirst(getString(R.string.http_protocol).toRegex(), "")
        return url
    }

    inner class MyTextWatcher(private val view: View) : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
        override fun onTextChanged(s: CharSequence, i: Int, i1: Int, i2: Int) {
            val protocol = if (serverUrlProtocol == null) settings.getString("serverProtocol", "http://") else serverUrlProtocol.text.toString()
            if (view.id == R.id.input_server_url) positiveAction.isEnabled = s.toString().trim { it <= ' ' }.isNotEmpty() && URLUtil.isValidUrl(protocol + s.toString())
        }

        override fun afterTextChanged(editable: Editable) {}
    }

    fun getCustomDeviceName(): String? {
        return settings.getString("customDeviceName", NetworkUtils.getDeviceName())
    }

    private fun getTeamMembers() {
        selectedTeamId = prefData.getSELECTEDTEAMID().toString()
        users = RealmMyTeam.getUsers(selectedTeamId, mRealm, "")

        val userList = (users as MutableList<RealmUserModel>?)!!.map {
            User(it.fullName ?: "", it.name ?: "", "", it.userImage ?: "", "team")
        }

        Log.d("userList", userList.toString())

        val existingUsers = prefData.getSAVEDUSERS().toMutableList()

        // Remove items from existingUsers where source is "team"
        val updatedExistingUsers = existingUsers.filter { it.source != "team" }
        prefData.setSAVEDUSERS(updatedExistingUsers)

        val usersToRemove = mutableListOf<User>()

        for (user in userList) {
            val existingUser = updatedExistingUsers.find { it.name == user.name }
            if (existingUser != null) {
                usersToRemove.add(user)
            }
        }

        val updatedUserList = userList - usersToRemove
        prefData.setSAVEDUSERS(updatedUserList)

        Log.d("users", prefData.getSAVEDUSERS().toString())
        mAdapter = if (mAdapter == null) {
            TeamListAdapter(prefData.getSAVEDUSERS().toMutableList(), this, this)
        } else {
            mAdapter!!.clearList()
            TeamListAdapter(prefData.getSAVEDUSERS().toMutableList(), this, this)
        }

        activityUsersLoginBinding.recyclerView.layoutManager = LinearLayoutManager(this)
        activityUsersLoginBinding.recyclerView.adapter = mAdapter

        val layoutManager: RecyclerView.LayoutManager = object : LinearLayoutManager(this) {
            override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
                return RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        activityUsersLoginBinding.recyclerView.layoutManager = layoutManager
        activityUsersLoginBinding.recyclerView.isNestedScrollingEnabled = true
        activityUsersLoginBinding.recyclerView.setHasFixedSize(true)
    }

    override fun onItemClick(user: User) {
        if (user.password.isEmpty() && user.source != "guest") {
            Glide.with(this)
                .load(user.image)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(activityUsersLoginBinding.userProfile)

            activityUsersLoginBinding.inputName.setText(user.name)
        } else {
            if (user.source == "guest"){
                val model = mRealm.copyFromRealm(RealmUserModel.createGuestUser(user.name, mRealm, settings))
                if (model == null) {
                    Utilities.toast(this, getString(R.string.unable_to_login))
                } else {
                    saveUserInfoPref(settings, "", model)
                    onLogin()
                }
            } else {
                submitForm(user.name, user.password)
            }
        }
    }
}
