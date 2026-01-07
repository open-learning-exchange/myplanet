package org.ole.planet.myplanet.ui.sync

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.AnimationDrawable
import android.os.Build
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
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
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.DataService
import org.ole.planet.myplanet.databinding.ActivityLoginBinding
import org.ole.planet.myplanet.databinding.DialogServerUrlBinding
import org.ole.planet.myplanet.model.MyPlanet
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.model.User
import org.ole.planet.myplanet.ui.community.HomeCommunityDialogFragment
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment
import org.ole.planet.myplanet.ui.user.BecomeMemberActivity
import org.ole.planet.myplanet.ui.user.UserProfileAdapter
import org.ole.planet.myplanet.utilities.AuthUtils
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.FileUtils
import org.ole.planet.myplanet.utilities.LocaleUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.ThemeManager
import org.ole.planet.myplanet.utilities.UrlUtils.getUrl
import org.ole.planet.myplanet.utilities.Utilities.toast

@AndroidEntryPoint
class LoginActivity : SyncActivity(), UserProfileAdapter.OnItemClickListener {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var nameWatcher1: TextWatcher
    private lateinit var nameWatcher2: TextWatcher
    private lateinit var passwordWatcher: TextWatcher
    private var guest = false
    var users: List<RealmUserModel>? = null
    private var mAdapter: UserProfileAdapter? = null
    private var backPressedTime: Long = 0
    private val backPressedInterval: Long = 2000
    private var teamList = java.util.ArrayList<String?>()
    private var teamAdapter: ArrayAdapter<String?>? = null
    private var isUserInteracting = false
    private var cachedTeams: List<RealmMyTeam>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)
        lblLastSyncDate = binding.lblLastSyncDate
        btnSignIn = binding.btnSignin
        syncIcon = binding.syncIcon
        lblVersion = binding.lblVersion
        tvAvailableSpace = binding.tvAvailableSpace
        btnGuestLogin = binding.btnGuestLogin
        becomeMember = binding.becomeMember
        btnFeedback = binding.btnFeedback
        openCommunity = binding.openCommunity
        btnLang = binding.btnLang
        inputName = binding.inputName
        inputPassword = binding.inputPassword
        service = DataService(this)

        binding.tvAvailableSpace.text = buildString {
            append(getString(R.string.available_space_colon))
            append(" ")
            append(FileUtils.availableOverTotalMemoryFormattedString(this@LoginActivity))
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
            configurationRepository.checkVersion(this, settings)
        }
        checkUsagesPermission()
        forceSyncTrigger()

        val url = getUrl()
        if (url.isNotEmpty() && url != "/db") {
            binding.openCommunity.visibility = View.VISIBLE
            binding.openCommunity.setOnClickListener {
                HomeCommunityDialogFragment().show(supportFragmentManager, "")
            }
            HomeCommunityDialogFragment().show(supportFragmentManager, "")
        } else {
            binding.openCommunity.visibility = View.GONE
        }
        binding.btnFeedback.setOnClickListener {
            if (getUrl() != "/db") {
                FeedbackFragment().show(supportFragmentManager, "")
            } else {
                toast(this, getString(R.string.please_enter_server_url_first))
                settingDialog()
            }
        }

        guest = intent.getBooleanExtra("guest", false)
        val username = intent.getStringExtra("username")
        val password = intent.getStringExtra("password")
        val autoLogin = intent.getBooleanExtra("auto_login", false)

        if (guest) {
            resetGuestAsMember(username)
        }

        if (autoLogin && username != null && password != null) {
            lifecycleScope.launch {
                delay(500)
                submitForm(username, password)
            }
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
        val selectDarkModeButton = binding.themeToggleButton
        selectDarkModeButton.setOnClickListener {
            ThemeManager.showThemeDialog(this)
        }

        teamList.add(getString(R.string.loading))
        teamAdapter = ArrayAdapter(this, R.layout.spinner_item_white, teamList)
        teamAdapter?.setDropDownViewResource(R.layout.custom_simple_list_item_1)
        binding.team.adapter = teamAdapter
    }

    private fun declareElements() {
        binding.customDeviceName.text = getCustomDeviceName()
        btnSignIn.setOnClickListener {
            if (isFinishing || isDestroyed) {
                return@setOnClickListener
            }
            if (getUrl() != "/db") {
                if (TextUtils.isEmpty(binding.inputName.text.toString())) {
                    binding.inputName.error = getString(R.string.err_msg_name)
                } else if (TextUtils.isEmpty(binding.inputPassword.text.toString())) {
                    binding.inputPassword.error = getString(R.string.err_msg_password)
                } else {
                    val enterUserName = binding.inputName.text.toString().trimEnd()
                    binding.btnSignin.isEnabled = false
                    customProgressDialog.setText(getString(R.string.please_wait))
                    customProgressDialog.show()
                    lifecycleScope.launch {
                        val user = withContext(Dispatchers.IO) {
                            databaseService.withRealm { realm ->
                                realm.where(RealmUserModel::class.java)
                                    .equalTo("name", enterUserName).findFirst()
                                    ?.let { realm.copyFromRealm(it) }
                            }
                        }
                        if (user == null || !user.isArchived) {
                            submitForm(enterUserName, binding.inputPassword.text.toString())
                        } else {
                            val builder = AlertDialog.Builder(this@LoginActivity)
                            builder.setMessage("member ${binding.inputName.text} is archived")
                            builder.setCancelable(false)
                            builder.setPositiveButton("ok") { dialog: DialogInterface, _: Int ->
                                dialog.dismiss()
                                binding.inputName.setText(R.string.empty_text)
                                binding.inputPassword.setText(R.string.empty_text)
                            }
                            val dialog = builder.create()
                            dialog.show()
                        }
                        binding.btnSignin.isEnabled = true
                        customProgressDialog.dismiss()
                    }
                }
            } else {
                toast(this, getString(R.string.please_enter_server_url_first))
                settingDialog()
            }
        }
        if (!settings.contains("serverProtocol")) settings.edit {
            putString("serverProtocol", "http://")
        }
        binding.becomeMember.setOnClickListener {
            if (getUrl() != "/db") {
                binding.inputName.setText(R.string.empty_text)
                becomeAMember()
            } else {
                toast(this, getString(R.string.please_enter_server_url_first))
                settingDialog()
            }
        }

        binding.imgBtnSetting.setOnClickListener {
            binding.inputName.setText(R.string.empty_text)
            settingDialog()
        }

        binding.btnGuestLogin.setOnClickListener {
            if (getUrl() != "/db") {
                binding.inputName.setText(R.string.empty_text)
                showGuestLoginDialog()
            } else {
                toast(this, getString(R.string.please_enter_server_url_first))
                settingDialog()
            }
        }
    }

    private fun declareMoreElements() {
        syncIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.login_file_upload_animation))
        syncIcon.scaleType
        syncIconDrawable = syncIcon.drawable as AnimationDrawable
        syncIcon.setOnClickListener {
            if (getUrl() != "/db") {
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
            } else {
                toast(this, getString(R.string.please_enter_server_url_first))
                settingDialog()
            }
        }
        declareHideKeyboardElements()
        binding.lblVersion.text = getString(R.string.version, resources.getText(R.string.app_version))
        nameWatcher1 = MyTextWatcher(binding.inputName)
        passwordWatcher = MyTextWatcher(binding.inputPassword)
        binding.inputName.addTextChangedListener(nameWatcher1)
        binding.inputPassword.addTextChangedListener(passwordWatcher)
        binding.inputPassword.setOnEditorActionListener { _: TextView?, actionId: Int, event: KeyEvent? ->
            if (isFinishing || isDestroyed) {
                return@setOnEditorActionListener false
            }
            if (actionId == EditorInfo.IME_ACTION_DONE || event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER) {
                btnSignIn.performClick()
                return@setOnEditorActionListener true
            }
            false
        }
        setUpLanguageButton()
        if (NetworkUtils.isNetworkConnected) {
            lifecycleScope.launch {
                service.syncPlanetServers { success: String? ->
                    toast(this@LoginActivity, success)
                }
            }
        }
        nameWatcher2 = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                val lowercaseText = s.toString().lowercase()
                if (s.toString() != lowercaseText) {
                    binding.inputName.setText(lowercaseText)
                    binding.inputName.setSelection(lowercaseText.length)
                }
            }

            override fun afterTextChanged(s: Editable) {}
        }
        binding.inputName.addTextChangedListener(nameWatcher2)
        if (getUrl().isNotEmpty()) {
            loadTeamsAsync()
        }
    }

    fun loadTeamsAsync() {
        if (cachedTeams != null) {
            setupTeamDropdown(cachedTeams)
            return
        }
        lifecycleScope.launch {
            val teams = withContext(Dispatchers.IO) {
                databaseService.withRealm { realm ->
                    realm.where(RealmMyTeam::class.java)
                        .isEmpty("teamId")
                        .equalTo("status", "active")
                        .findAll()
                        ?.let { realm.copyFromRealm(it) }
                }
            }
            cachedTeams = teams
            setupTeamDropdown(teams)
        }
    }

    private fun setupTeamDropdown(teams: List<RealmMyTeam>?) {
        if (!teams.isNullOrEmpty()) {
            binding.team.visibility = View.VISIBLE
            teamAdapter = ArrayAdapter(this, R.layout.spinner_item_white, teamList)
            teamAdapter?.setDropDownViewResource(R.layout.custom_simple_list_item_1)
            teamList.clear()
            teamList.add(getString(R.string.select_team))
            for (team in teams) {
                if (team.isValid) {
                    teamList.add(team.name)
                }
            }
            binding.team.adapter = teamAdapter
            val lastSelection = prefData.getSelectedTeamId()
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
            binding.team.setOnTouchListener { _, _ ->
                isUserInteracting = true
                false
            }
            binding.team.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View?, position: Int, id: Long) {
                    if (isUserInteracting) {
                        if (position > 0) {
                            val selectedTeam = teams[position - 1]
                            val currentTeamId = prefData.getSelectedTeamId()
                            if (currentTeamId != selectedTeam._id) {
                                prefData.setSelectedTeamId(selectedTeam._id)
                                getTeamMembers()
                            }
                        }
                        isUserInteracting = false
                    }
                }
                override fun onNothingSelected(parentView: AdapterView<*>?) {
                    isUserInteracting = false
                }
            }
        } else {
            binding.team.visibility = View.GONE
        }
    }

    private fun setUpLanguageButton() {
        updateLanguageButtonText()

        binding.btnLang.setOnClickListener {
            showLanguageSelectionDialog()
        }
    }

    private fun updateLanguageButtonText() {
        val currentLanguage = LocaleUtils.getLanguage(this)
        binding.btnLang.text = getLanguageString(currentLanguage)
    }

    private fun showLanguageSelectionDialog() {
        val currentLanguage = LocaleUtils.getLanguage(this)
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
                    LocaleUtils.setLocale(this, selectedLanguage)
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
        super.attachBaseContext(LocaleUtils.onAttach(newBase))
    }

    private fun declareHideKeyboardElements() {
        binding.constraintLayout.setOnTouchListener { view, event ->
            when (event?.action) {
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
            users = databaseService.withRealm { realm ->
                RealmMyTeam.getUsers(selectedTeamId, realm, "membership").map { realm.copyFromRealm(it) }.toMutableList()
            }
            val userList = (users as? MutableList<RealmUserModel>)?.map {
                User(it.name ?: "", it.name ?: "", "", it.userImage ?: "", "team")
            } ?: emptyList()

            val existingUsers = prefData.getSavedUsers().toMutableList()
            val filteredExistingUsers = existingUsers.filter { it.source != "team" }
            val updatedUserList = userList.filterNot { user -> filteredExistingUsers.any { it.name == user.name } } + filteredExistingUsers
            prefData.setSavedUsers(updatedUserList)
        }

        if (mAdapter == null) {
        mAdapter = UserProfileAdapter(this)
            binding.recyclerView.layoutManager = LinearLayoutManager(this)
            binding.recyclerView.adapter = mAdapter
        }
        mAdapter?.submitList(prefData.getSavedUsers().toMutableList())

        binding.recyclerView.isNestedScrollingEnabled = true
        binding.recyclerView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        binding.recyclerView.isVerticalScrollBarEnabled = true

    }
    override fun onItemClick(user: User) {
        if (user.password?.isEmpty() == true && user.source != "guest") {
            Glide.with(this)
                .load(user.image)
                .placeholder(R.drawable.profile)
                .error(R.drawable.profile)
                .into(binding.userProfile)

            binding.inputName.setText(user.name)
        } else {
            if (user.source == "guest"){
                val model = databaseService.withRealm { realm ->
                    RealmUserModel.createGuestUser(user.name, realm, settings)?.let { realm.copyFromRealm(it) }
                }
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
        AuthUtils.login(this, name, password)
    }

    internal fun showGuestDialog(username: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("$username is already a guest")
        builder.setMessage("Continue only if this is you")
        builder.setCancelable(false)
        builder.setNegativeButton("cancel") { dialog: DialogInterface, _: Int -> dialog.dismiss() }
        builder.setPositiveButton("continue", null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                positiveButton.isEnabled = false
                lifecycleScope.launch {
                    val model = withContext(Dispatchers.IO) {
                        databaseService.withRealm { realm ->
                            RealmUserModel.createGuestUser(username, realm, settings)?.let { realm.copyFromRealm(it) }
                        }
                    }
                    if (model == null) {
                        toast(this@LoginActivity, getString(R.string.unable_to_login))
                        positiveButton.isEnabled = true
                    } else {
                        saveUserInfoPref(settings, "", model)
                        onLogin()
                        dialog.dismiss()
                    }
                }
            }
        }
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
            binding.inputName.setText(username)
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

    fun invalidateTeamsCacheAndReload() {
        cachedTeams = null
        loadTeamsAsync()
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

    override fun onResume() {
        super.onResume()
        binding.userProfile.setImageResource(R.drawable.profile)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::nameWatcher1.isInitialized) {
            binding.inputName.removeTextChangedListener(nameWatcher1)
        }
        if (this::nameWatcher2.isInitialized) {
            binding.inputName.removeTextChangedListener(nameWatcher2)
        }
        if (this::passwordWatcher.isInitialized) {
            binding.inputPassword.removeTextChangedListener(passwordWatcher)
        }
    }
}
