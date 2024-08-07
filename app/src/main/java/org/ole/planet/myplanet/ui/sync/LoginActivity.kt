package org.ole.planet.myplanet.ui.sync

import android.content.*
import android.graphics.drawable.AnimationDrawable
import android.os.*
import android.os.Build.VERSION_CODES.TIRAMISU
import android.text.*
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.*
import com.bumptech.glide.Glide
import io.realm.Realm
import org.ole.planet.myplanet.*
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.*
import org.ole.planet.myplanet.datamanager.*
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.ui.community.HomeCommunityDialogFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.userprofile.*
import org.ole.planet.myplanet.utilities.*
import org.ole.planet.myplanet.utilities.FileUtils.availableOverTotalMemoryFormattedString
import org.ole.planet.myplanet.utilities.Utilities.getUrl
import org.ole.planet.myplanet.utilities.Utilities.toast
import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

class LoginActivity : SyncActivity(), TeamListAdapter.OnItemClickListener {
    private lateinit var activityLoginBinding: ActivityLoginBinding
    private var guest = false
    var users: List<RealmUserModel>? = null
    private var mAdapter: TeamListAdapter? = null
    private var backPressedTime: Long = 0
    private val backPressedInterval: Long = 2000
    private var fallbackLanguage: String = "en"
    private var teamList = java.util.ArrayList<String?>()
    private var teamAdapter: ArrayAdapter<String?>? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityLoginBinding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(activityLoginBinding.root)
        lblLastSyncDate = activityLoginBinding.lblLastSyncDate
        btnSignIn = activityLoginBinding.btnSignin
        syncIcon = activityLoginBinding.syncIcon
        service = Service(this)

        activityLoginBinding.tvAvailableSpace.text = availableOverTotalMemoryFormattedString
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

        if (getUrl().isNotEmpty()) {
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

        if (guest) {
            resetGuestAsMember(username)
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
    }

    private fun declareElements() {
        if (!defaultPref.contains("beta_addImageToMessage")) {
            defaultPref.edit().putBoolean("beta_addImageToMessage", true).apply()
        }
        activityLoginBinding.customDeviceName.text = getCustomDeviceName()
        activityLoginBinding.btnSignin.setOnClickListener {
            if (TextUtils.isEmpty(activityLoginBinding.inputName.text.toString())) {
                activityLoginBinding.inputName.error = getString(R.string.err_msg_name)
            } else if (TextUtils.isEmpty(activityLoginBinding.inputPassword.text.toString())) {
                activityLoginBinding.inputPassword.error = getString(R.string.err_msg_password)
            } else {
                if (mRealm.isClosed) {
                    mRealm = Realm.getDefaultInstance()
                }
                val user = mRealm.where(RealmUserModel::class.java).equalTo("name", activityLoginBinding.inputName.text.toString()).findFirst()
                if (user == null || !user.isArchived) {
                    submitForm(activityLoginBinding.inputName.text.toString(), activityLoginBinding.inputPassword.text.toString())
                } else {
                    val builder = AlertDialog.Builder(this)
                    builder.setMessage("member " + activityLoginBinding.inputName.text.toString() + " is archived")
                    builder.setCancelable(false)
                    builder.setPositiveButton("Ok") { dialog: DialogInterface, _: Int ->
                        dialog.dismiss()
                        activityLoginBinding.inputName.setText(R.string.empty_text)
                        activityLoginBinding.inputPassword.setText(R.string.empty_text)
                    }
                    val dialog = builder.create()
                    dialog.show()
                }
            }
        }
        if (!settings.contains("serverProtocol")) settings.edit().putString("serverProtocol", "http://").apply()
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
                syncIconDrawable.start()
                isSync = false
                forceSync = true
                service.checkVersion(this, settings)
            }
            declareHideKeyboardElements()
            activityLoginBinding.lblVersion.text = getString(R.string.version, resources.getText(R.string.app_version))
            activityLoginBinding.inputName.addTextChangedListener(MyTextWatcher(activityLoginBinding.inputName))
            activityLoginBinding.inputPassword.addTextChangedListener(MyTextWatcher(activityLoginBinding.inputPassword))
            activityLoginBinding.inputPassword.setOnEditorActionListener { _: TextView?, actionId: Int, event: KeyEvent? ->
                if (actionId == EditorInfo.IME_ACTION_DONE || event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    activityLoginBinding.btnSignin.performClick()
                    return@setOnEditorActionListener true
                }
                false
            }
            setUpLanguageButton()
            if (defaultPref.getBoolean("saveUsernameAndPassword", false)) {
                activityLoginBinding.inputName.setText(settings.getString(getString(R.string.login_user), ""))
                activityLoginBinding.inputPassword.setText(settings.getString(getString(R.string.login_password), ""))
            }
            if (NetworkUtils.isNetworkConnected) {
                service.syncPlanetServers(mRealm) { success: String? ->
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
        if (mRealm == null || mRealm.isClosed) {
            mRealm = Realm.getDefaultInstance()
        }
        val teams: List<RealmMyTeam>? = mRealm?.where(RealmMyTeam::class.java)?.isEmpty("teamId")?.equalTo("status", "active")?.findAll()

        if (teams != null && teams.isNotEmpty()) {
            activityLoginBinding.team?.visibility = View.VISIBLE
            teamAdapter = ArrayAdapter(this, R.layout.spinner_item_white, teamList)
            teamAdapter?.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            teamList.clear()
            teamList.add("select team")
            for (team in teams) {
                if (team.isValid) {
                    teamList.add(team.name)
                }
            }
            activityLoginBinding.team?.adapter = teamAdapter
            val lastSelection = prefData.getSelectedTeamId()
            if (!lastSelection.isNullOrEmpty()) {
                for (i in teams.indices) {
                    val team = teams[i]
                    if (team._id != null && team._id == lastSelection && team.isValid) {
                        val lastSelectedPosition = i + 1
                        activityLoginBinding.team?.setSelection(lastSelectedPosition)
                        break
                    }
                }
            }

            activityLoginBinding.team?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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
            activityLoginBinding.team?.visibility = View.GONE
        }
    }

    private fun setUpLanguageButton() {
        val languageKey = resources.getStringArray(R.array.language_keys)
        val languages = resources.getStringArray(R.array.language)
        val currentLanguageKey = settings.getString(Constants.SELECTED_LANGUAGE, fallbackLanguage)
        val index = languageKey.indexOf(currentLanguageKey)
        activityLoginBinding.btnLang.text = languages[index]
        activityLoginBinding.btnLang.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.select_language)
                .setSingleChoiceItems(languages, index) { dialog, which ->
                    val selectedLanguageKey = languageKey[which]
                    if (selectedLanguageKey != LocaleHelper.getLanguage(this)) {
                        LocaleHelper.setLocale(this, selectedLanguageKey)
                        settings.edit().putString(Constants.SELECTED_LANGUAGE, selectedLanguageKey).apply()
                        updateLanguage(selectedLanguageKey)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    @Suppress("DEPRECATION")
    private fun updateLanguage(language: String) {
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
        recreate()
    }

    private fun declareHideKeyboardElements() {
        findViewById<View>(R.id.constraintLayout).setOnTouchListener { view: View?, _: MotionEvent? ->
            hideKeyboard(view)
            false
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

        if (mAdapter == null) {
            mAdapter = TeamListAdapter(prefData.getSavedUsers().toMutableList(), this, this)
            activityLoginBinding.recyclerView.layoutManager = LinearLayoutManager(this)
            activityLoginBinding.recyclerView.adapter = mAdapter
        } else {
            mAdapter?.updateList(prefData.getSavedUsers().toMutableList())
        }

        activityLoginBinding.recyclerView.isNestedScrollingEnabled = true
        activityLoginBinding.recyclerView.setHasFixedSize(true)
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
        val editor = settings.edit()
        editor.putString("loginUserName", name)
        editor.putString("loginUserPassword", password)
        val isLoggedIn = authenticateUser(settings, name, password, false)
        if (isLoggedIn) {
            Toast.makeText(applicationContext, getString(R.string.thank_you), Toast.LENGTH_SHORT).show()
            onLogin()
            saveUsers(activityLoginBinding.inputName.text.toString(), activityLoginBinding.inputPassword.text.toString(), "member")
        } else {
            ManagerSync.instance?.login(name, password, object : SyncListener {
                override fun onSyncStarted() {
                    customProgressDialog?.setText(getString(R.string.please_wait))
                    customProgressDialog?.show()
                }

                override fun onSyncComplete() {
                    customProgressDialog?.dismiss()
                    val log = authenticateUser(settings, name, password, true)
                    if (log) {
                        Toast.makeText(applicationContext, getString(R.string.thank_you), Toast.LENGTH_SHORT).show()
                        onLogin()
                        saveUsers(activityLoginBinding.inputName.text.toString(), activityLoginBinding.inputPassword.text.toString(), "member")
                    } else {
                        alertDialogOkay(getString(R.string.err_msg_login))
                    }
                    syncIconDrawable.stop()
                    syncIconDrawable.selectDrawable(0)
                }

                override fun onSyncFailed(msg: String?) {
                    toast(MainApplication.context, msg)
                    customProgressDialog?.dismiss()
                    syncIconDrawable.stop()
                    syncIconDrawable.selectDrawable(0)
                }
            })
        }
        editor.apply()
    }

    private fun showGuestLoginDialog() {
        try {
            mRealm = Realm.getDefaultInstance()
            mRealm.refresh()
            val alertGuestLoginBinding = AlertGuestLoginBinding.inflate(LayoutInflater.from(this))
            val v: View = alertGuestLoginBinding.root
            alertGuestLoginBinding.etUserName.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

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
                if (mRealm.isClosed) {
                    mRealm = Realm.getDefaultInstance()
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
                            hasDiacriticCharacters = !normalizedText.codePoints().allMatch {
                                    codePoint -> Character.isLetterOrDigit(codePoint) || codePoint == '.'.code || codePoint == '-'.code || codePoint == '_'.code
                            }
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
                        val model = RealmUserModel.createGuestUser(username, mRealm, settings)
                            ?.let { it1 ->
                                mRealm.copyFromRealm(it1)
                            }
                        if (model == null) {
                            toast(this, getString(R.string.unable_to_login))
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
            if (!mRealm.isClosed) {
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

    private fun showUserAlreadyMemberDialog(username: String) {
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
            var userName: String? = profileDbHandler.userModel?.name
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
