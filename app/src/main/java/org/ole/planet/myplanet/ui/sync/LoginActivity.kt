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
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.*
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import io.realm.Realm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.*
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.*
import org.ole.planet.myplanet.datamanager.*
import org.ole.planet.myplanet.model.*
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.TransactionSyncManager
import org.ole.planet.myplanet.ui.SettingActivity
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
    private var teamList = java.util.ArrayList<String?>()
    private var teamAdapter: ArrayAdapter<String?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityLoginBinding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(activityLoginBinding.root)
        lblLastSyncDate = activityLoginBinding.lblLastSyncDate
        btnSignIn = activityLoginBinding.btnSignin
        syncIcon = activityLoginBinding.syncIcon
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

        val autoLogin = intent.getBooleanExtra("autoLogin", false)
        guest = intent.getBooleanExtra("guest", false)
        val username = intent.getStringExtra("username")
        val password = intent.getStringExtra("password")

        if (autoLogin && !username.isNullOrEmpty() && !password.isNullOrEmpty()) {
            PerformanceLogger.markEvent("Auto-login detected, initiating background sync")

            // Show a progress dialog
            customProgressDialog = DialogUtils.getCustomProgressDialog(this)
            customProgressDialog?.setText("Preparing your account...")
            customProgressDialog?.show()

            // Start tracking time
            val loginStartTime = System.currentTimeMillis()

            // Start a background operation
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // First sync the user data
                    withContext(Dispatchers.IO) {
                        PerformanceLogger.markEvent("Starting syncFirstBatch")
                        val syncManager = SyncManager.instance
                        syncManager?.mRealm = Realm.getDefaultInstance()
                        syncManager?.initializeSync()
                        syncManager?.syncFirstBatch()
                        PerformanceLogger.markEvent("syncFirstBatch completed")
                    }

                    Log.d("LoginActivity", "Username: $username, Password: ${password.replace(Regex("."), "*")}")

                    // Set up retry parameters
                    val maxRetries = 5
                    val initialRetryDelay = 1000L // 1 second initial delay
                    var currentRetry = 0
                    var loginSuccess = false

                    // Retry loop with exponential backoff
                    while (currentRetry < maxRetries && !loginSuccess) {
                        // Update progress dialog
                        withContext(Dispatchers.Main) {
                            customProgressDialog?.setText("Finalizing account setup... (Attempt ${currentRetry + 1})")
                        }

                        // Log the retry attempt
                        val currentAttemptTime = System.currentTimeMillis()
                        val elapsedTime = currentAttemptTime - loginStartTime
                        PerformanceLogger.markEvent("Auto-login attempt #${currentRetry + 1} at ${elapsedTime}ms from start")

                        // First check if user data is available - this is the key improvement
                        val userDataAvailable = checkUserDataAvailability(username)
                        PerformanceLogger.markEvent("User data availability check: $userDataAvailable")

                        if (!userDataAvailable) {
                            Log.d("LoginActivity", "User data not yet available for authentication")
                            // Skip authentication attempt if data isn't ready yet
                            currentRetry++

                            if (currentRetry < maxRetries) {
                                // Calculate delay with exponential backoff (1s, 2s, 4s, 8s...)
                                val retryDelay = initialRetryDelay * (1 shl currentRetry)
                                PerformanceLogger.markEvent("User data not ready, waiting ${retryDelay}ms before retry #${currentRetry + 1}")
                                delay(retryDelay)
                                continue // Skip to next iteration
                            } else {
                                break // Exit loop if max retries reached
                            }
                        }

                        // Attempt login now that we know data is available
                        PerformanceLogger.markEvent("Attempting authentication with available user data")
                        loginSuccess = authenticateUser(settings, username, password, false)

                        if (loginSuccess) {
                            // Success! Break out of retry loop
                            val totalTime = System.currentTimeMillis() - loginStartTime
                            PerformanceLogger.markEvent("Auto-login successful after ${currentRetry + 1} attempts, total time: ${totalTime}ms")
                            Log.d("LoginActivity", "Auto-login SUCCESS after ${currentRetry + 1} attempts in ${totalTime}ms")
                            break
                        } else {
                            // Log failure and prepare for retry
                            PerformanceLogger.markEvent("Auto-login attempt #${currentRetry + 1} failed despite user data being available")
                            Log.d("LoginActivity", "Auto-login failed attempt #${currentRetry + 1}")

                            // Debug why authentication failed
                            withContext(Dispatchers.IO) {
                                debugAuthenticationIssue(username, password)
                            }

                            // Check if this was the last retry
                            if (currentRetry < maxRetries - 1) {
                                // Calculate delay with exponential backoff (1s, 2s, 4s, 8s...)
                                val retryDelay = initialRetryDelay * (1 shl currentRetry)
                                PerformanceLogger.markEvent("Waiting ${retryDelay}ms before next retry")

                                // Wait before retrying
                                delay(retryDelay)

                                // Force database refresh before next attempt
                                withContext(Dispatchers.IO) {
                                    try {
                                        TransactionSyncManager.syncDb(Realm.getDefaultInstance(), "tablet_users")
                                        PerformanceLogger.markEvent("Forced additional tablet_users sync before retry #${currentRetry + 2}")
                                    } catch (e: Exception) {
                                        Log.e("LoginActivity", "Error during forced sync: ${e.message}")
                                    }
                                }
                            }

                            currentRetry++
                        }
                    }

                    // Hide the progress dialog
                    customProgressDialog?.dismiss()

                    // Final outcome
                    if (loginSuccess) {
                        // Handle successful login
                        Toast.makeText(this@LoginActivity, getString(R.string.welcome, username), Toast.LENGTH_SHORT).show()
                        saveUsers(username, password, "member")
                        onLogin()
                    } else {
                        // Handle all retries failed
                        val totalTime = System.currentTimeMillis() - loginStartTime
                        PerformanceLogger.markEvent("Auto-login failed after $maxRetries attempts, total time: ${totalTime}ms")
                        Log.d("LoginActivity", "Auto-login FAILED after $maxRetries attempts in ${totalTime}ms")

                        // One last attempt to sync user data
                        withContext(Dispatchers.IO) {
                            try {
                                PerformanceLogger.markEvent("Final attempt to sync user data after all retries failed")
                                TransactionSyncManager.syncDb(Realm.getDefaultInstance(), "tablet_users")
                                delay(1000) // Give it a moment to process

                                // Check one last time
                                val finalCheck = checkUserDataAvailability(username)
                                PerformanceLogger.markEvent("Final user data check: $finalCheck")

                                // Try one last login if data is available
                                if (finalCheck) {
                                    val finalLoginSuccess = authenticateUser(settings, username, password, false)
                                    PerformanceLogger.markEvent("Final login attempt result: $finalLoginSuccess")

                                    if (finalLoginSuccess) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(this@LoginActivity, getString(R.string.welcome, username), Toast.LENGTH_SHORT).show()
                                            saveUsers(username, password, "member")
                                            onLogin()
                                            return@withContext // Exit early on success
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("LoginActivity", "Error in final sync attempt: ${e.message}")
                            }
                        }

                        // Fall back to manual entry if we got here (final attempt failed)
                        activityLoginBinding.inputName.setText(username)
                        activityLoginBinding.inputPassword.setText(password)
                        Toast.makeText(this@LoginActivity, "manual sign in", Toast.LENGTH_SHORT).show()
                    }

                } catch (e: Exception) {
                    // Handle exceptions
                    val totalTime = System.currentTimeMillis() - loginStartTime
                    PerformanceLogger.markEvent("Error during auto-login after ${totalTime}ms: ${e.message}")
                    Log.e("LoginActivity", "Auto-login error: ${e.message}", e)

                    customProgressDialog?.dismiss()

                    // Fall back to manual login
                    activityLoginBinding.inputName.setText(username)
                    activityLoginBinding.inputPassword.setText(password)
                    Toast.makeText(this@LoginActivity, "manual sign in", Toast.LENGTH_SHORT).show()
                }
            }
        }

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
        val selectDarkModeButton = findViewById<ImageButton>(R.id.themeToggleButton)
        selectDarkModeButton?.setOnClickListener{
            SettingActivity.SettingFragment.darkMode(this)
        }
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
                    activityLoginBinding.btnSignin.performClick()
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

    private fun getLanguageString(languageCode: String): String {
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

        if (mAdapter == null) {
            mAdapter = TeamListAdapter(prefData.getSavedUsers().toMutableList(), this, this)
            activityLoginBinding.recyclerView.layoutManager = LinearLayoutManager(this)
            activityLoginBinding.recyclerView.adapter = mAdapter
        } else {
            mAdapter?.updateList(prefData.getSavedUsers().toMutableList())
        }

        activityLoginBinding.recyclerView.isNestedScrollingEnabled = false
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
            Toast.makeText(context, getString(R.string.welcome, name), Toast.LENGTH_SHORT).show()
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
                    hasDiacriticCharacters = !normalizedText.codePoints().allMatch { codePoint: Int -> Character.isLetterOrDigit(codePoint) || codePoint == '.'.code || codePoint == '-'.code || codePoint == '_'.code }
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
            val builder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
            builder.setTitle(R.string.btn_guest_login)
                .setView(v)
                .setPositiveButton(R.string.login, null)
                .setNegativeButton(R.string.cancel, null)
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
                        hasDiacriticCharacters = !normalizedText.codePoints().allMatch {
                            codePoint -> Character.isLetterOrDigit(codePoint) || codePoint == '.'.code || codePoint == '-'.code || codePoint == '_'.code
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

    fun checkUserDataAvailability(username: String): Boolean {
        val realm = Realm.getDefaultInstance()
        try {
            val user = realm.where(RealmUserModel::class.java)
                .equalTo("name", username)
                .findFirst()

            // Check if user exists and has necessary authentication data
            return user != null &&
                    ((user._id?.isEmpty() == true && !user.password.isNullOrEmpty()) ||
                            (user._id?.isNotEmpty() == true && !user.derived_key.isNullOrEmpty() && !user.salt.isNullOrEmpty()))
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error checking user data: ${e.message}")
            return false
        } finally {
            realm.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!mRealm.isClosed) {
            mRealm.close()
        }
    }
}
