package org.ole.planet.myplanet.ui.sync

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.os.Build
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import io.realm.Realm
import java.util.Locale
import org.ole.planet.myplanet.*
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.*
import org.ole.planet.myplanet.datamanager.*
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.ui.SettingActivity
import org.ole.planet.myplanet.ui.community.HomeCommunityDialogFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.userprofile.*
import org.ole.planet.myplanet.utilities.*
import org.ole.planet.myplanet.utilities.FileUtils.availableOverTotalMemoryFormattedString
import org.ole.planet.myplanet.utilities.Utilities.getUrl
import org.ole.planet.myplanet.utilities.Utilities.toast

class LoginActivity : SyncActivity(), TeamListAdapter.OnItemClickListener {
    private lateinit var activityLoginBinding: ActivityLoginBinding
    private var guest = false
    var users: List<RealmUserModel>? = null
    private var mAdapter: TeamListAdapter? = null
    private var backPressedTime: Long = 0
    private val backPressedInterval: Long = 2000
    private var teamList = java.util.ArrayList<String?>()
    private var teamAdapter: ArrayAdapter<String?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityLoginBinding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(activityLoginBinding.root)
        lblLastSyncDate = activityLoginBinding.lblLastSyncDate
        btnSignIn = activityLoginBinding.btnSignin
        syncIcon = activityLoginBinding.syncIcon
        lblVersion = activityLoginBinding.lblVersion
        tvAvailableSpace = activityLoginBinding.tvAvailableSpace
        btnGuestLogin = activityLoginBinding.btnGuestLogin
        becomeMember = activityLoginBinding.becomeMember
        btnFeedback = activityLoginBinding.btnFeedback
        openCommunity = activityLoginBinding.openCommunity
        btnLang = activityLoginBinding.btnLang
        inputName = activityLoginBinding.inputName
        inputPassword = activityLoginBinding.inputPassword
        service = Service(this)

        activityLoginBinding.tvAvailableSpace.text = buildString {
            append(getString(R.string.available_space_colon))
            append(" ")
            append(availableOverTotalMemoryFormattedString)
        }
        changeLogoColor()
        declareElements()
        declareMoreElements()
        showWifiDialog()
        registerReceiver()
        forceSync = intent.getBooleanExtra("forceSync", false)
        processedUrl = getUrl()
        if (forceSync) {
            isSync = false
        }
        val versionInfo = if (Build.VERSION.SDK_INT >= TIRAMISU) {
            intent.getSerializableExtra("versionInfo", MyPlanet::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("versionInfo") as? MyPlanet
        }

        if (versionInfo != null) {
            onUpdateAvailable(versionInfo, intent.getBooleanExtra("cancelable", false))
        } else {
            service.checkVersion(this, settings)
        }
        checkUsagesPermission()
        forceSyncTrigger()

        val url = getUrl()
        if (url.isNotEmpty() && url != "/db") {
            activityLoginBinding.openCommunity.visibility = View.VISIBLE
            activityLoginBinding.openCommunity.setOnClickListener {
                HomeCommunityDialogFragment().show(supportFragmentManager, "")
            }
            HomeCommunityDialogFragment().show(supportFragmentManager, "")
        } else {
            activityLoginBinding.openCommunity.visibility = View.GONE
        }
        activityLoginBinding.btnFeedback.setOnClickListener {
            FeedbackFragment().show(supportFragmentManager, "")
        }

        guest = intent.getBooleanExtra("guest", false)
        val username = intent.getStringExtra("username")
        val password = intent.getStringExtra("password")
        val autoLogin = intent.getBooleanExtra("auto_login", false)

        if (guest) {
            resetGuestAsMember(username)
        }

        if (autoLogin && username != null && password != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                submitForm(username, password)
            }, 500)
        }

        getTeamMembers()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (System.currentTimeMillis() - backPressedTime < backPressedInterval) {
                    finish()
                } else {
                    toast(this@LoginActivity, getString(R.string.press_back_again_to_exit))
                    backPressedTime = System.currentTimeMillis()
                }
            }
        })
        val selectDarkModeButton = findViewById<ImageButton>(R.id.themeToggleButton)
        selectDarkModeButton?.setOnClickListener{
            SettingActivity.SettingFragment.darkMode(this)
        }
    }

    private fun declareElements() {
        if (!defaultPref.contains("beta_addImageToMessage")) {
            defaultPref.edit { putBoolean("beta_addImageToMessage", true) }
        }
        activityLoginBinding.customDeviceName.text = getCustomDeviceName()
        btnSignIn.setOnClickListener {
            if (TextUtils.isEmpty(activityLoginBinding.inputName.text.toString())) {
                activityLoginBinding.inputName.error = getString(R.string.err_msg_name)
            } else if (TextUtils.isEmpty(activityLoginBinding.inputPassword.text.toString())) {
                activityLoginBinding.inputPassword.error = getString(R.string.err_msg_password)
            } else {
                if (mRealm.isClosed) {
                    mRealm = Realm.getDefaultInstance()
                }
                val enterUserName = activityLoginBinding.inputName.text.toString().trimEnd()
                val user = mRealm.where(RealmUserModel::class.java).equalTo("name", enterUserName).findFirst()
                if (user == null || !user.isArchived) {
                    submitForm(enterUserName, activityLoginBinding.inputPassword.text.toString())
                } else {
                    val builder = AlertDialog.Builder(this)
                    builder.setMessage("member ${activityLoginBinding.inputName.text} is archived")
                    builder.setCancelable(false)
                    builder.setPositiveButton("ok") { dialog: DialogInterface, _: Int ->
                        dialog.dismiss()
                        activityLoginBinding.inputName.setText(R.string.empty_text)
                        activityLoginBinding.inputPassword.setText(R.string.empty_text)
                    }
                    val dialog = builder.create()
                    dialog.show()
                }
            }
        }
        if (!settings.contains("serverProtocol")) settings.edit {
            putString("serverProtocol", "http://")
        }
        activityLoginBinding.becomeMember.setOnClickListener {
            activityLoginBinding.inputName.setText(R.string.empty_text)
            becomeAMember()
        }

        activityLoginBinding.imgBtnSetting.setOnClickListener {
            activityLoginBinding.inputName.setText(R.string.empty_text)
            settingDialog()
        }

        activityLoginBinding.btnGuestLogin.setOnClickListener {
            if (getUrl().isNotEmpty()) {
                activityLoginBinding.inputName.setText(R.string.empty_text)
                showGuestLoginDialog()
            } else {
                toast(this, getString(R.string.please_enter_server_url_first))
                settingDialog()
            }
        }
    }

    private fun declareMoreElements() {
        try {
            mRealm = Realm.getDefaultInstance()
            syncIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.login_file_upload_animation))
            syncIcon.scaleType
            syncIconDrawable = syncIcon.drawable as AnimationDrawable
            syncIcon.setOnClickListener {
                val protocol = settings.getString("serverProtocol", "")
                val serverUrl = "${settings.getString("serverURL", "")}"
                val serverPin = "${settings.getString("serverPin", "")}"

                val url = if (serverUrl.startsWith("http://") || serverUrl.startsWith("https://")) {
                    serverUrl
                } else {
                    "$protocol$serverUrl"
                }
                syncIconDrawable.start()

                val dialogServerUrlBinding = DialogServerUrlBinding.inflate(LayoutInflater.from(this))
                val contextWrapper = ContextThemeWrapper(this, R.style.AlertDialogTheme)
                val builder = MaterialDialog.Builder(contextWrapper).customView(dialogServerUrlBinding.root, true)
                val dialog = builder.build()
                currentDialog = dialog
                service.getMinApk(this, url, serverPin, this, "LoginActivity")
            }
            declareHideKeyboardElements()
            activityLoginBinding.lblVersion.text = getString(R.string.version, resources.getText(R.string.app_version))
            activityLoginBinding.inputName.addTextChangedListener(MyTextWatcher(activityLoginBinding.inputName))
            activityLoginBinding.inputPassword.addTextChangedListener(MyTextWatcher(activityLoginBinding.inputPassword))
            activityLoginBinding.inputPassword.setOnEditorActionListener { _: TextView?, actionId: Int, event: KeyEvent? ->
                if (actionId == EditorInfo.IME_ACTION_DONE || event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    btnSignIn.performClick()
                    return@setOnEditorActionListener true
                }
                false
            }
            setUpLanguageButton()
            if (NetworkUtils.isNetworkConnected) {
                service.syncPlanetServers { success: String? ->
                    toast(this, success)
                }
            }
            activityLoginBinding.inputName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    val lowercaseText = s.toString().lowercase()
                    if (s.toString() != lowercaseText) {
                        activityLoginBinding.inputName.setText(lowercaseText)
                        activityLoginBinding.inputName.setSelection(lowercaseText.length)
                    }
                }

                override fun afterTextChanged(s: Editable) {}
            })
            if(getUrl().isNotEmpty()){
                updateTeamDropdown()
            }
        } finally {
            if (!mRealm.isClosed) {
                mRealm.close()
            }
        }
    }

    fun updateTeamDropdown() {
        if (mRealm.isClosed) {
            mRealm = Realm.getDefaultInstance()
        }
        val teams: List<RealmMyTeam>? = mRealm.where(RealmMyTeam::class.java)
            ?.isEmpty("teamId")?.equalTo("status", "active")?.findAll()

        if (!teams.isNullOrEmpty()) {
            activityLoginBinding.team.visibility = View.VISIBLE
            teamAdapter = ArrayAdapter(this, R.layout.spinner_item_white, teamList)
            teamAdapter?.setDropDownViewResource(R.layout.custom_simple_list_item_1)
            teamList.clear()
            teamList.add(getString(R.string.select_team))
            for (team in teams) {
                if (team.isValid) {
                    teamList.add(team.name)
                }
            }
            activityLoginBinding.team.adapter = teamAdapter
            val lastSelection = prefData.getSelectedTeamId()
            if (!lastSelection.isNullOrEmpty()) {
                for (i in teams.indices) {
                    val team = teams[i]
                    if (team._id != null && team._id == lastSelection && team.isValid) {
                        val lastSelectedPosition = i + 1
                        activityLoginBinding.team.setSelection(lastSelectedPosition)
                        break
                    }
                }
            }

            activityLoginBinding.team.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View?, position: Int, id: Long) {
                    if (position > 0) {
                        val selectedTeam = teams[position - 1]
                        val currentTeamId = prefData.getSelectedTeamId()
                        if (currentTeamId != selectedTeam._id) {
                            prefData.setSelectedTeamId(selectedTeam._id)
                            getTeamMembers()
                        }
                    }
                }

                override fun onNothingSelected(parentView: AdapterView<*>?) {}
            }
        } else {
            activityLoginBinding.team.visibility = View.GONE
        }
    }

    private fun setUpLanguageButton() {
        updateLanguageButtonText()

        activityLoginBinding.btnLang.setOnClickListener {
            showLanguageSelectionDialog()
        }
    }

    private fun updateLanguageButtonText() {
        val currentLanguage = LocaleHelper.getLanguage(this)
        activityLoginBinding.btnLang.text = getLanguageString(currentLanguage)
    }

    private fun showLanguageSelectionDialog() {
        val currentLanguage = LocaleHelper.getLanguage(this)
        val options = arrayOf(
            getString(R.string.english),
            getString(R.string.spanish),
            getString(R.string.somali),
            getString(R.string.nepali),
            getString(R.string.arabic),
            getString(R.string.french)
        )
        val languageCodes = arrayOf("en", "es", "so", "ne", "ar", "fr")
        val checkedItem = languageCodes.indexOf(currentLanguage)

        AlertDialog.Builder(this, R.style.AlertDialogTheme)
            .setTitle(getString(R.string.select_language))
            .setSingleChoiceItems(
                ArrayAdapter(this, R.layout.checked_list_item, options),
                checkedItem
            ) { dialog, which ->
                val selectedLanguage = languageCodes[which]
                if (selectedLanguage != currentLanguage) {
                    LocaleHelper.setLocale(this, selectedLanguage)
                    recreate()
                    dialog.dismiss()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateConfiguration(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase))
    }

    private fun declareHideKeyboardElements() {
        val constraintLayout = findViewById<View>(R.id.constraintLayout)
        constraintLayout.setOnTouchListener { view: View?, event: MotionEvent? ->
            when (event?.action) {
                MotionEvent.ACTION_DOWN -> {
                }
                MotionEvent.ACTION_UP -> {
                    view?.let {
                        hideKeyboard(it)
                        it.performClick()
                    }
                }
            }
            true
        }
    }

    fun getTeamMembers() {
        selectedTeamId = prefData.getSelectedTeamId().toString()
        if (selectedTeamId?.isNotEmpty() == true) {
            users = RealmMyTeam.getUsers(selectedTeamId, mRealm, "membership")
            val userList = (users as? MutableList<RealmUserModel>)?.map {
                User(it.name ?: "", it.name ?: "", "", it.userImage ?: "", "team")
            } ?: emptyList()

            val existingUsers = prefData.getSavedUsers().toMutableList()
            val filteredExistingUsers = existingUsers.filter { it.source != "team" }
            val updatedUserList = userList.filterNot { user -> filteredExistingUsers.any { it.name == user.name } } + filteredExistingUsers
            prefData.setSavedUsers(updatedUserList)
        }

        updateTeamDropdown()

        if (mAdapter == null) {
            mAdapter = TeamListAdapter(prefData.getSavedUsers().toMutableList(), this, this)
            activityLoginBinding.recyclerView.layoutManager = LinearLayoutManager(this)
            activityLoginBinding.recyclerView.adapter = mAdapter
        } else {
            mAdapter?.updateList(prefData.getSavedUsers().toMutableList())
        }

        activityLoginBinding.recyclerView.isNestedScrollingEnabled = true
        activityLoginBinding.recyclerView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        activityLoginBinding.recyclerView.isVerticalScrollBarEnabled = true

        activityLoginBinding.recyclerView.post {
            mAdapter?.notifyDataSetChanged()
        }
    }

    override fun onItemClick(user: User) {
        if (user.password?.isEmpty() == true && user.source != "guest") {
            Glide.with(this)
                .load(user.image)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(activityLoginBinding.userProfile)

            activityLoginBinding.inputName.setText(user.name)
        } else {
            if (user.source == "guest"){
                val model = RealmUserModel.createGuestUser(user.name, mRealm, settings)?.let { mRealm.copyFromRealm(it) }
                if (model == null) {
                    toast(this, getString(R.string.unable_to_login))
                } else {
                    saveUserInfoPref(settings, "", model)
                    onLogin()
                }
            } else {
                submitForm(user.name, user.password)
            }
        }
    }

    private fun submitForm(name: String?, password: String?) {
        if (forceSyncTrigger()) {
            return
        }
        settings.edit {
            putString("loginUserName", name)
            putString("loginUserPassword", password)
            val isLoggedIn = authenticateUser(settings, name, password, false)
            if (isLoggedIn) {
                Toast.makeText(context, getString(R.string.welcome, name), Toast.LENGTH_SHORT)
                    .show()
                onLogin()
                saveUsers(name, password, "member")
            } else {
                ManagerSync.instance?.login(name, password, object : SyncListener {
                    override fun onSyncStarted() {
                        customProgressDialog.setText(getString(R.string.please_wait))
                        customProgressDialog.show()
                    }

                    override fun onSyncComplete() {
                        customProgressDialog.dismiss()
                        val log = authenticateUser(settings, name, password, true)
                        if (log) {
                            Toast.makeText(applicationContext, getString(R.string.thank_you), Toast.LENGTH_SHORT).show()
                            onLogin()
                            saveUsers(name, password, "member")
                        } else {
                            alertDialogOkay(getString(R.string.err_msg_login))
                        }
                        syncIconDrawable.stop()
                        syncIconDrawable.selectDrawable(0)
                    }

                    override fun onSyncFailed(msg: String?) {
                        toast(context, msg)
                        customProgressDialog.dismiss()
                        syncIconDrawable.stop()
                        syncIconDrawable.selectDrawable(0)
                    }
                })
            }
        }
    }

    internal fun showGuestDialog(username: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("$username is already a guest")
        builder.setMessage("Continue only if this is you")
        builder.setCancelable(false)
        builder.setNegativeButton("cancel") { dialog: DialogInterface, _: Int -> dialog.dismiss() }
        builder.setPositiveButton("continue") { dialog: DialogInterface, _: Int ->
            dialog.dismiss()
            val model = RealmUserModel.createGuestUser(username, mRealm, settings)?.let { mRealm.copyFromRealm(it) }
            if (model == null) {
                toast(this, getString(R.string.unable_to_login))
            } else {
                saveUserInfoPref(settings, "", model)
                onLogin()
            }
        }
        val dialog = builder.create()
        dialog.show()
    }

    internal fun showUserAlreadyMemberDialog(username: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("$username is already a member")
        builder.setMessage("Continue to login if this is you")
        builder.setCancelable(false)
        builder.setNegativeButton("Cancel") { dialog: DialogInterface, _: Int -> dialog.dismiss() }
        builder.setPositiveButton("login") { dialog: DialogInterface, _: Int ->
            dialog.dismiss()
            activityLoginBinding.inputName.setText(username)
        }
        val dialog = builder.create()
        dialog.show()
    }

    fun saveUsers(name: String?, password: String?, source: String) {
        if (source === "guest") {
            val newUser = User("", name, password, "", "guest")
            val existingUsers: MutableList<User> = ArrayList(
                prefData.getSavedUsers()
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
                prefData.setSavedUsers(existingUsers)
            }
        } else if (source === "member") {
            var userProfile = profileDbHandler.userModel?.userImage
            val userName: String? = profileDbHandler.userModel?.name
            if (userProfile == null) {
                userProfile = ""
            }
            val newUser = User(userName, name, password, userProfile, "member")
            val existingUsers: MutableList<User> = ArrayList(prefData.getSavedUsers())
            var newUserExists = false
            for ((fullName1) in existingUsers) {
                if (fullName1 == newUser.fullName?.trim { it <= ' ' }) {
                    newUserExists = true
                    break
                }
            }
            if (!newUserExists) {
                existingUsers.add(newUser)
                prefData.setSavedUsers(existingUsers)
            }
        }
    }

    private fun becomeAMember() {
        if (getUrl().isNotEmpty()) {
            startActivity(Intent(this, BecomeMemberActivity::class.java))
        } else {
            toast(this, getString(R.string.please_enter_server_url_first))
            settingDialog()
        }
    }

    fun getCustomDeviceName(): String? {
        return settings.getString("customDeviceName", NetworkUtils.getDeviceName())
    }

    private fun resetGuestAsMember(username: String?) {
        val existingUsers = prefData.getSavedUsers().toMutableList()
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
            prefData.setSavedUsers(existingUsers)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!mRealm.isClosed) {
            mRealm.close()
        }
    }
}
