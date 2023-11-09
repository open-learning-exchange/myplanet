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
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.URLUtil
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.viewbinding.ViewBinding
import com.afollestad.materialdialogs.DialogAction
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.textfield.TextInputLayout
import io.realm.Realm
import io.realm.Sort
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.ActivityChildLoginBinding
import org.ole.planet.myplanet.databinding.ActivityLoginBinding
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
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.community.HomeCommunityDialogFragment
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.team.AdapterTeam.OnUserSelectedListener
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.LocaleHelper
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.SharedPrefManager
import org.ole.planet.myplanet.utilities.Utilities
import java.text.Normalizer
import java.util.Arrays
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class LoginActivity : SyncActivity(), CheckVersionCallback, OnUserSelectedListener {
    private var serverUrl: EditText? = null
    private var serverUrlProtocol: EditText? = null
    private var serverPassword: EditText? = null
    private var processedUrl: String? = null
    private var protocol_checkin: RadioGroup? = null
    private var inputName: EditText? = null
    private var inputPassword: EditText? = null
    private var inputLayoutName: TextInputLayout? = null
    private var inputLayoutPassword: TextInputLayout? = null
    private var btnSignIn: Button? = null
    private var becomeMember: Button? = null
    private var btnGuestLogin: Button? = null
    private var btnLang: Button? = null
    private var openCommunity: Button? = null
    private var btnFeedback: Button? = null
    private var positiveAction: View? = null
    private var imgBtnSetting: ImageButton? = null
    private var isSync = false
    private var forceSync = false
    private var guest = false
    private var switchChildMode: SwitchCompat? = null
    private var defaultPref: SharedPreferences? = null
    private var service: Service? = null
    private var spnCloud: Spinner? = null
    private var tvAvailableSpace: TextView? = null
    private var previouslyLoggedIn: TextView? = null
    private var customDeviceName: TextView? = null
    private var lblVersion: TextView? = null
    var prefData: SharedPrefManager? = null
    private var profileDbHandler: UserProfileDbHandler? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activityLoginBinding: ViewBinding
        activityLoginBinding = if (settings.getBoolean("isChild", false)) {
            ActivityChildLoginBinding.inflate(layoutInflater)
        } else {
            ActivityLoginBinding.inflate(layoutInflater)
        }
        setContentView(activityLoginBinding.root)
        prefData = SharedPrefManager(this)
        profileDbHandler = UserProfileDbHandler(this)
        if (activityLoginBinding is ActivityLoginBinding) {
            inputName = activityLoginBinding.inputName
            inputPassword = activityLoginBinding.inputPassword
            inputLayoutName = activityLoginBinding.inputLayoutName
            inputLayoutPassword = activityLoginBinding.inputLayoutPassword
            btnSignIn = activityLoginBinding.btnSignin
            imgBtnSetting = activityLoginBinding.imgBtnSetting
            tvAvailableSpace = activityLoginBinding.tvAvailableSpace
            previouslyLoggedIn = activityLoginBinding.previouslyLoggedIn
            openCommunity = activityLoginBinding.openCommunity
            lblLastSyncDate = activityLoginBinding.lblLastSyncDate
            btnFeedback = activityLoginBinding.btnFeedback
            customDeviceName = activityLoginBinding.customDeviceName
            becomeMember = activityLoginBinding.becomeMember
            btnGuestLogin = activityLoginBinding.btnGuestLogin
            switchChildMode = activityLoginBinding.switchChildMode
            syncIcon = activityLoginBinding.syncIcon
            lblVersion = activityLoginBinding.lblVersion
            btnLang = activityLoginBinding.btnLang
        } else {
            inputName = (activityLoginBinding as ActivityChildLoginBinding).inputName
            inputPassword = activityLoginBinding.inputPassword
            inputLayoutName = activityLoginBinding.inputLayoutName
            inputLayoutPassword = activityLoginBinding.inputLayoutPassword
            btnSignIn = activityLoginBinding.btnSignin
            imgBtnSetting = activityLoginBinding.imgBtnSetting
            tvAvailableSpace = activityLoginBinding.tvAvailableSpace
            previouslyLoggedIn = activityLoginBinding.previouslyLoggedIn
            openCommunity = activityLoginBinding.openCommunity
            lblLastSyncDate = activityLoginBinding.lblLastSyncDate
            btnFeedback = activityLoginBinding.btnFeedback
            customDeviceName = activityLoginBinding.customDeviceName
            becomeMember = activityLoginBinding.becomeMember
            btnGuestLogin = activityLoginBinding.btnGuestLogin
            switchChildMode = activityLoginBinding.switchChildMode
            syncIcon = activityLoginBinding.syncIcon
            lblVersion = activityLoginBinding.lblVersion
            btnLang = activityLoginBinding.btnLang
        }
        tvAvailableSpace!!.text = FileUtils.getAvailableOverTotalMemoryFormattedString()
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
            service!!.checkVersion(this, settings)
        }
        checkUsagesPermission()
        setUpChildMode()
        forceSyncTrigger()
        if (!Utilities.getUrl().isEmpty()) {
            openCommunity!!.visibility = View.VISIBLE
            openCommunity!!.setOnClickListener { v: View? ->
                inputName!!.setText("")
                HomeCommunityDialogFragment().show(supportFragmentManager, "")
            }
            HomeCommunityDialogFragment().show(supportFragmentManager, "")
        } else {
            openCommunity!!.visibility = View.GONE
        }
        btnFeedback!!.setOnClickListener { view: View? ->
            inputName!!.setText("")
            FeedbackFragment().show(supportFragmentManager, "")
        }
        previouslyLoggedIn!!.setOnClickListener { view: View? -> showUserList() }
        guest = intent.getBooleanExtra("guest", false)
        val username = intent.getStringExtra("username")
        if (guest) {
            val existingUsers = prefData!!.getSAVEDUSERS()
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
                prefData!!.setSAVEDUSERS(existingUsers)
            }
        }
    }

    private fun showUserList() {
        val layoutUserListBinding = LayoutUserListBinding.inflate(LayoutInflater.from(this))
        val view: View = layoutUserListBinding.root
        val builder = AlertDialog.Builder(this@LoginActivity)
        builder.setTitle(R.string.select_user_to_login)
            .setView(view)
            .setNegativeButton(R.string.dismiss, null)
        val existingUsers = prefData!!.getSAVEDUSERS()
        val adapter = UserListAdapter(this@LoginActivity, existingUsers)
        adapter.setOnItemClickListener(object : UserListAdapter.OnItemClickListener {
            override fun onItemClickGuest(name: String) {
                val model =
                    mRealm.copyFromRealm(RealmUserModel.createGuestUser(name, mRealm, settings))
                if (model == null) {
                    Utilities.toast(this@LoginActivity, getString(R.string.unable_to_login))
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
        lblLastSyncDate.text =
            getString(R.string.last_sync) + Utilities.getRelativeTime(
                settings.getLong(getString(R.string.last_syncs), 0)
            ) + " >>"
        if (Constants.autoSynFeature(
                Constants.KEY_AUTOSYNC_,
                applicationContext
            ) && Constants.autoSynFeature(
                Constants.KEY_AUTOSYNC_WEEKLY, applicationContext
            )
        ) {
            return checkForceSync(7)
        } else if (Constants.autoSynFeature(
                Constants.KEY_AUTOSYNC_,
                applicationContext
            ) && Constants.autoSynFeature(
                Constants.KEY_AUTOSYNC_MONTHLY, applicationContext
            )
        ) {
            return checkForceSync(30)
        }
        return false
    }

    private fun showWifiDialog() {
        if (intent.getBooleanExtra("showWifiDialog", false)) {
            DialogUtils.showWifiSettingDialog(this)
        }
    }

    fun checkForceSync(maxDays: Int): Boolean {
        cal_today = Calendar.getInstance(Locale.ENGLISH)
        cal_last_Sync = Calendar.getInstance(Locale.ENGLISH)
        cal_last_Sync.setTimeInMillis(settings.getLong("LastSync", 0))
        cal_today.setTimeInMillis(Date().time)
        val msDiff = Calendar.getInstance().timeInMillis - cal_last_Sync.getTimeInMillis()
        val daysDiff = TimeUnit.MILLISECONDS.toDays(msDiff)
        return if (daysDiff >= maxDays) {
            Log.e("Sync Date ", "Expired - ")
            val alertDialogBuilder = AlertDialog.Builder(this)
            alertDialogBuilder.setMessage(
                getString(R.string.it_has_been_more_than) + (daysDiff - 1) + getString(
                    R.string.days_since_you_last_synced_this_device
                ) + getString(R.string.connect_it_to_the_server_over_wifi_and_sync_it_to_reactivate_this_tablet)
            )
            alertDialogBuilder.setPositiveButton(R.string.okay) { arg0: DialogInterface?, arg1: Int ->
                Toast.makeText(
                    applicationContext,
                    getString(R.string.connect_to_the_server_over_wifi_and_sync_your_device_to_continue),
                    Toast.LENGTH_LONG
                ).show()
            }
            alertDialogBuilder.show()
            true
        } else {
            Log.e("Sync Date ", "Not up to  - $maxDays")
            false
        }
    }

    fun declareElements() {
        if (!defaultPref!!.contains("beta_addImageToMessage")) {
            defaultPref!!.edit().putBoolean("beta_addImageToMessage", true).commit()
        }
        customDeviceName!!.text = getCustomDeviceName()
        btnSignIn!!.setOnClickListener { view: View? ->
            if (TextUtils.isEmpty(
                    inputName!!.text.toString()
                )
            ) {
                inputName!!.error = getString(R.string.err_msg_name)
            } else if (TextUtils.isEmpty(inputPassword!!.text.toString())) {
                inputPassword!!.error = getString(R.string.err_msg_password)
            } else {
                submitForm(inputName!!.text.toString(), inputPassword!!.text.toString())
            }
        }
        if (!settings.contains("serverProtocol")) settings.edit()
            .putString("serverProtocol", "http://").commit()
        becomeMember!!.setOnClickListener { v: View? ->
            inputName!!.setText("")
            becomeAMember()
        }
        imgBtnSetting!!.setOnClickListener { view: View? ->
            inputName!!.setText("")
            settingDialog()
        }
        btnGuestLogin!!.setOnClickListener { view: View? ->
            inputName!!.setText("")
            showGuestLoginDialog()
        }
        switchChildMode!!.isChecked = settings.getBoolean("isChild", false)
        switchChildMode!!.setOnCheckedChangeListener { compoundButton: CompoundButton?, b: Boolean ->
            inputName!!.setText("")
            settings.edit().putBoolean("isChild", b).commit()
            recreate()
        }
    }

    private fun becomeAMember() {
        if (!Utilities.getUrl().isEmpty()) {
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
                override fun beforeTextChanged(
                    s: CharSequence,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    val input = s.toString()
                    val firstChar = if (input.length > 0) input[0] else '\u0000'
                    var hasInvalidCharacters = false
                    var hasSpecialCharacters = false
                    var hasDiacriticCharacters = false
                    val normalizedText = Normalizer.normalize(s, Normalizer.Form.NFD)
                    for (i in 0 until input.length) {
                        val c = input[i]
                        if (c != '_' && c != '.' && c != '-' && !Character.isDigit(c) && !Character.isLetter(
                                c
                            )
                        ) {
                            hasInvalidCharacters = true
                            break
                        }
                    }
                    val regex =
                        ".*[ßäöüéèêæÆœøØ¿àìòùÀÈÌÒÙáíóúýÁÉÍÓÚÝâîôûÂÊÎÔÛãñõÃÑÕëïÿÄËÏÖÜŸåÅŒçÇðÐ].*"
                    val pattern = Pattern.compile(regex)
                    val matcher = pattern.matcher(input)
                    hasSpecialCharacters = matcher.matches()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        hasDiacriticCharacters = !normalizedText.codePoints()
                            .allMatch { codePoint: Int -> Character.isLetterOrDigit(codePoint) || codePoint == '.'.code || codePoint == '-'.code || codePoint == '_'.code }
                    }
                    if (!Character.isDigit(firstChar) && !Character.isLetter(firstChar)) {
                        alertGuestLoginBinding.etUserName.error =
                            getString(R.string.must_start_with_letter_or_number)
                    } else if (hasInvalidCharacters || hasDiacriticCharacters || hasSpecialCharacters) {
                        alertGuestLoginBinding.etUserName.error =
                            getString(R.string.only_letters_numbers_and_are_allowed)
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
            login.setOnClickListener { view: View? ->
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
                    alertGuestLoginBinding.etUserName.error =
                        getString(R.string.username_cannot_be_empty)
                    isValid = false
                }
                if (firstChar != null && !Character.isDigit(firstChar) && !Character.isLetter(
                        firstChar
                    )
                ) {
                    alertGuestLoginBinding.etUserName.error =
                        getString(R.string.must_start_with_letter_or_number)
                    isValid = false
                } else {
                    for (c in username.toCharArray()) {
                        if (c != '_' && c != '.' && c != '-' && !Character.isDigit(c) && !Character.isLetter(
                                c
                            )
                        ) {
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
                        alertGuestLoginBinding.etUserName.error =
                            getString(R.string.only_letters_numbers_and_are_allowed)
                        isValid = false
                    }
                }
                if (isValid) {
                    val existingUser =
                        mRealm.where(RealmUserModel::class.java).equalTo("name", username)
                            .findFirst()
                    dialog.dismiss()
                    if (existingUser != null) {
                        Log.d("model", existingUser._id.toString())
                        if (existingUser._id.contains("guest")) {
                            showGuestDialog(username)
                        } else if (existingUser._id.contains("org.couchdb.user:")) {
                            showUserAlreadyMemberDialog(username)
                        }
                    } else {
                        val model = mRealm.copyFromRealm(
                            RealmUserModel.createGuestUser(
                                username,
                                mRealm,
                                settings
                            )
                        )
                        if (model == null) {
                            Utilities.toast(this@LoginActivity, getString(R.string.unable_to_login))
                        } else {
                            saveUsers(username, "", "guest")
                            saveUserInfoPref(settings, "", model)
                            onLogin()
                        }
                    }
                }
            }
            cancel.setOnClickListener { view: View? -> dialog.dismiss() }
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
        builder.setNegativeButton("cancel") { dialog: DialogInterface, which: Int -> dialog.dismiss() }
        builder.setPositiveButton("continue") { dialog: DialogInterface, which: Int ->
            dialog.dismiss()
            val model =
                mRealm.copyFromRealm(RealmUserModel.createGuestUser(username, mRealm, settings))
            if (model == null) {
                Utilities.toast(this@LoginActivity, getString(R.string.unable_to_login))
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
        builder.setNegativeButton("Cancel") { dialog: DialogInterface, which: Int -> dialog.dismiss() }
        builder.setPositiveButton("login") { dialog: DialogInterface, which: Int ->
            dialog.dismiss()
            inputName!!.setText(username)
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun continueSync(dialog: MaterialDialog) {
        processedUrl = saveConfigAndContinue(dialog)
        if (TextUtils.isEmpty(processedUrl)) return
        isSync = true
        if (checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) && settings.getBoolean(
                "firstRun",
                true
            )
        ) {
            clearInternalStorage()
        }
        Service(this).isPlanetAvailable(object : PlanetAvailableListener {
            override fun isAvailable() {
                Service(this@LoginActivity).checkVersion(this@LoginActivity, settings)
            }

            override fun notAvailable() {
                if (!isFinishing) {
                    DialogUtils.showAlert(
                        this@LoginActivity,
                        "Error",
                        getString(R.string.planet_server_not_reachable)
                    )
                }
            }
        })
    }

    fun declareMoreElements() {
        try {
            mRealm = Realm.getDefaultInstance()
            syncIcon.setImageDrawable(resources.getDrawable(R.drawable.login_file_upload_animation))
            syncIcon.scaleType
            syncIconDrawable = syncIcon.drawable as AnimationDrawable
            syncIcon.setOnClickListener { v: View? ->
                syncIconDrawable.start()
                isSync = false
                forceSync = true
                service!!.checkVersion(this, settings)
            }
            declareHideKeyboardElements()
            lblVersion!!.text = resources.getText(R.string.version)
                .toString() + " " + resources.getText(R.string.app_version)
            inputName!!.addTextChangedListener(MyTextWatcher(inputName!!))
            inputPassword!!.addTextChangedListener(MyTextWatcher(inputPassword!!))
            inputPassword!!.setOnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
                if (actionId == EditorInfo.IME_ACTION_DONE || event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    btnSignIn!!.performClick()
                    return@setOnEditorActionListener true
                }
                false
            }
            setUplanguageButton()
            if (defaultPref!!.getBoolean("saveUsernameAndPassword", false)) {
                inputName!!.setText(settings.getString(getString(R.string.login_user), ""))
                inputPassword!!.setText(settings.getString(getString(R.string.login_password), ""))
            }
            if (NetworkUtils.isNetworkConnected()) {
                service!!.syncPlanetServers(mRealm) { success: String? ->
                    Utilities.toast(
                        this@LoginActivity,
                        success
                    )
                }
            }
            inputName!!.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    val lowercaseText = s.toString().lowercase()
                    if (s.toString() != lowercaseText) {
                        inputName!!.setText(lowercaseText)
                        inputName!!.setSelection(lowercaseText.length)
                    }
                }

                override fun afterTextChanged(s: Editable) {}
            })
        } finally {
            if (mRealm != null && !mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

    private fun setUplanguageButton() {
        val languageKey = resources.getStringArray(R.array.language_keys)
        val languages = resources.getStringArray(R.array.language)
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        val index = Arrays.asList(*languageKey).indexOf(pref.getString("app_language", "en"))
        btnLang!!.text = languages[index]
        btnLang!!.setOnClickListener { view: View? ->
            AlertDialog.Builder(this).setTitle(R.string.select_language).setSingleChoiceItems(
                resources.getStringArray(R.array.language), index, null
            ).setPositiveButton(R.string.ok) { dialog: DialogInterface, whichButton: Int ->
                dialog.dismiss()
                val selectedPosition = (dialog as AlertDialog).listView.checkedItemPosition
                val lang = languageKey[selectedPosition]
                LocaleHelper.setLocale(this@LoginActivity, lang)
                recreate()
            }.setNegativeButton(R.string.cancel, null).show()
        }
    }

    /**
     * Form  Validation
     */
    private fun submitForm(name: String, password: String) {
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
            saveUsers(inputName!!.text.toString(), inputPassword!!.text.toString(), "member")
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
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.thank_you),
                            Toast.LENGTH_SHORT
                        ).show()
                        onLogin()
                        saveUsers(
                            inputName!!.text.toString(),
                            inputPassword!!.text.toString(),
                            "member"
                        )
                    } else {
                        alertDialogOkay(getString(R.string.err_msg_login))
                    }
                    syncIconDrawable.stop()
                    syncIconDrawable.selectDrawable(0)
                }

                override fun onSyncFailed(msg: String) {
                    Utilities.toast(this@LoginActivity, msg)
                    progressDialog.dismiss()
                    syncIconDrawable.stop()
                    syncIconDrawable.selectDrawable(0)
                }
            })
        }
        editor.commit()
    }

    private fun saveUsers(name: String, password: String, source: String) {
        if (source === "guest") {
            val newUser = User("", name, password, "", "guest")
            val existingUsers: MutableList<User> = ArrayList(
                prefData!!.getSAVEDUSERS()
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
                prefData!!.setSAVEDUSERS(existingUsers)
            }
        } else if (source === "member") {
            var userProfile = profileDbHandler!!.userModel.userImage
            var fullName = profileDbHandler!!.userModel.fullName
            if (userProfile == null) {
                userProfile = ""
            }
            if (fullName.trim { it <= ' ' }.length == 0) {
                fullName = profileDbHandler!!.userModel.name
            }
            val newUser = User(fullName, name, password, userProfile, "member")
            val existingUsers: MutableList<User> = ArrayList(
                prefData!!.getSAVEDUSERS()
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
                prefData!!.setSAVEDUSERS(existingUsers)
            }
        }
    }

    private fun onLogin() {
        val handler = UserProfileDbHandler(this)
        handler.onLogin()
        handler.onDestory()
        editor.putBoolean(Constants.KEY_LOGIN, true).commit()
        openDashboard()
    }

    fun settingDialog() {
        var sRealm: Realm? = null
        try {
            sRealm = Realm.getDefaultInstance()
            val dialogServerUrlBinding = DialogServerUrlBinding.inflate(LayoutInflater.from(this))
            val builder = MaterialDialog.Builder(this@LoginActivity)
            builder.title(R.string.action_settings)
                .customView(dialogServerUrlBinding.root, true)
                .positiveText(R.string.btn_sync)
                .negativeText(R.string.btn_sync_cancel)
                .neutralText(R.string.btn_sync_save)
                .onPositive { dialog: MaterialDialog, which: DialogAction? -> continueSync(dialog) }
                .onNeutral { dialog: MaterialDialog?, which: DialogAction? ->
                    saveConfigAndContinue(
                        dialog
                    )
                }
            val dialog = builder.build()
            positiveAction = dialog.getActionButton(DialogAction.POSITIVE)
            spnCloud = dialogServerUrlBinding.spnCloud
            val communities: List<RealmCommunity> = sRealm.where(
                RealmCommunity::class.java
            ).sort("weight", Sort.ASCENDING).findAll()
            val nonEmptyCommunities: MutableList<RealmCommunity> = ArrayList()
            for (community in communities) {
                if (community.isValid && !TextUtils.isEmpty(community.name)) {
                    nonEmptyCommunities.add(community)
                }
            }
            dialogServerUrlBinding.spnCloud.adapter =
                ArrayAdapter(this, android.R.layout.simple_list_item_1, nonEmptyCommunities)
            dialogServerUrlBinding.spnCloud.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        adapterView: AdapterView<*>?,
                        view: View,
                        i: Int,
                        l: Long
                    ) {
                        onChangeServerUrl()
                    }

                    override fun onNothingSelected(adapterView: AdapterView<*>?) {}
                }
            protocol_checkin = dialogServerUrlBinding.radioProtocol
            serverUrl = dialogServerUrlBinding.inputServerUrl
            serverPassword = dialogServerUrlBinding.inputServerPassword
            serverUrlProtocol = dialogServerUrlBinding.inputServerUrlProtocol
            dialogServerUrlBinding.switchServerUrl.setOnCheckedChangeListener { compoundButton: CompoundButton?, b: Boolean ->
                settings.edit().putBoolean("switchCloudUrl", b).commit()
                dialogServerUrlBinding.spnCloud.visibility = if (b) View.VISIBLE else View.GONE
                setUrlAndPin(dialogServerUrlBinding.switchServerUrl.isChecked)
                Log.d("checked", dialogServerUrlBinding.switchServerUrl.isChecked.toString())
            }
            serverUrl!!.addTextChangedListener(MyTextWatcher(serverUrl!!))
            dialogServerUrlBinding.deviceName.setText(getCustomDeviceName())
            dialogServerUrlBinding.switchServerUrl.isChecked =
                settings.getBoolean("switchCloudUrl", false)
            setUrlAndPin(settings.getBoolean("switchCloudUrl", false))
            protocol_semantics()
            dialog.show()
            sync(dialog)
        } finally {
            if (sRealm != null && !sRealm.isClosed) {
                sRealm.close()
            }
        }
    }

    private fun onChangeServerUrl() {
        try {
            mRealm = Realm.getDefaultInstance()
            val selected = spnCloud!!.selectedItem as RealmCommunity ?: return
            if (selected.isValid) {
                serverUrl!!.setText(selected.localDomain)
                protocol_checkin!!.check(R.id.radio_https)
                settings.getString("serverProtocol", "https://")
                serverPassword!!.transformationMethod = PasswordTransformationMethod.getInstance()
                serverPassword!!.setText(if (selected.weight == 0) "1983" else "")
                serverPassword!!.isEnabled = selected.weight != 0
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
            serverUrl!!.setText(removeProtocol(settings.getString("serverURL", "")))
            serverPassword!!.setText(settings.getString("serverPin", ""))
            protocol_checkin!!.check(
                if (TextUtils.equals(
                        settings.getString("serverProtocol", ""),
                        "http://"
                    )
                ) R.id.radio_http else R.id.radio_https
            )
            serverUrlProtocol!!.setText(settings.getString("serverProtocol", ""))
            serverPassword!!.transformationMethod = null
        }
        serverUrl!!.isEnabled = !checked
        serverPassword!!.isEnabled = !checked
        serverPassword!!.clearFocus()
        serverUrl!!.clearFocus()
        protocol_checkin!!.isEnabled = !checked
    }

    private fun protocol_semantics() {
        settings.edit().putString("serverProtocol", serverUrlProtocol!!.text.toString()).commit()
        protocol_checkin!!.setOnCheckedChangeListener { radioGroup: RadioGroup?, i: Int ->
            when (i) {
                R.id.radio_http -> serverUrlProtocol!!.setText(getString(R.string.http_protocol))
                R.id.radio_https -> serverUrlProtocol!!.setText(getString(R.string.https_protocol))
            }
            settings.edit().putString("serverProtocol", serverUrlProtocol!!.text.toString())
                .commit()
        }
    }

    override fun onSuccess(s: String) {
        Utilities.log("Sync completed ")
        if (progressDialog.isShowing && s.contains("Crash")) progressDialog.dismiss()
        DialogUtils.showSnack(btnSignIn, s)
        settings.edit().putLong("lastUsageUploaded", Date().time).commit()

        // Update last sync text
        lblLastSyncDate.text =
            getString(R.string.last_sync) + Utilities.getRelativeTime(
                Date().time
            ) + " >>"
    }

    override fun onUpdateAvailable(info: MyPlanet, cancelable: Boolean) {
        try {
            mRealm = Realm.getDefaultInstance()
            val builder = DialogUtils.getUpdateDialog(this, info, progressDialog)
            if (cancelable || NetworkUtils.getCustomDeviceName(this).endsWith("###")) {
                builder.setNegativeButton(R.string.update_later) { dialogInterface: DialogInterface?, i: Int -> continueSyncProcess() }
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

    fun continueSyncProcess() {
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
                .setPositiveButton(R.string.login) { dialogInterface: DialogInterface?, i: Int ->
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
            if (mRealm != null && !mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mRealm != null && !mRealm.isClosed) mRealm.close()
    }

    fun removeProtocol(url: String?): String {
        var url = url
        url = url!!.replaceFirst(getString(R.string.https_protocol).toRegex(), "")
        url = url.replaceFirst(getString(R.string.http_protocol).toRegex(), "")
        return url
    }

    public inner class MyTextWatcher(private val view: View) : TextWatcher {
        override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}
        override fun onTextChanged(s: CharSequence, i: Int, i1: Int, i2: Int) {
            val protocol = if (serverUrlProtocol == null) settings.getString(
                "serverProtocol",
                "http://"
            ) else serverUrlProtocol!!.text.toString()
            if (view.id == R.id.input_server_url) positiveAction!!.isEnabled = s.toString()
                .trim { it <= ' ' }.length > 0 && URLUtil.isValidUrl(protocol + s.toString())
        }

        override fun afterTextChanged(editable: Editable) {
            when (view.id) {
                R.id.input_name -> validateEditText(
                    inputName,
                    inputLayoutName,
                    getString(R.string.err_msg_name)
                )

                R.id.input_password -> validateEditText(
                    inputPassword,
                    inputLayoutPassword,
                    getString(R.string.err_msg_password)
                )

                else -> {}
            }
        }
    }

    fun getCustomDeviceName(): String? {
        return settings.getString("customDeviceName", NetworkUtils.getDeviceName())
    }

    companion object {
        var cal_today: Calendar? = null
        var cal_last_Sync: Calendar? = null
    }
}