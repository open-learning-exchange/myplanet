package org.ole.planet.myplanet.ui.userprofile

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.callback.SecurityDataCallback
import org.ole.planet.myplanet.databinding.ActivityBecomeMemberBinding
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils.CustomProgressDialog
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import javax.inject.Inject
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils

@AndroidEntryPoint
class BecomeMemberActivity : BaseActivity() {
    @Inject
    lateinit var userRepository: UserRepository
    private lateinit var activityBecomeMemberBinding: ActivityBecomeMemberBinding
    var dob: String = ""
    var guest: Boolean = false
    private var usernameWatcher: TextWatcher? = null
    private var passwordWatcher: TextWatcher? = null
    
    private data class MemberInfo(
        val username: String,
        var password: String,
        val rePassword: String,
        val fName: String,
        val lName: String,
        val mName: String,
        val email: String,
        val language: String,
        val level: String,
        val phoneNumber: String,
        val birthDate: String,
        val gender: String?
    )

    private fun selectedGender(): String? = when {
        activityBecomeMemberBinding.male.isChecked -> "male"
        activityBecomeMemberBinding.female.isChecked -> "female"
        else -> null
    }

    private fun showDatePickerDialog() {
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog(
            this, { _, i, i1, i2 ->
                dob = String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2)
                activityBecomeMemberBinding.txtDob.text = dob
            }, now[Calendar.YEAR], now[Calendar.MONTH], now[Calendar.DAY_OF_MONTH]
        )
        dpd.setTitle(getString(R.string.select_date_of_birth))
        dpd.datePicker.maxDate = now.timeInMillis
        dpd.show()
    }

    private fun collectMemberInfo() = MemberInfo(
        activityBecomeMemberBinding.etUsername.text.toString(),
        activityBecomeMemberBinding.etPassword.text.toString(),
        activityBecomeMemberBinding.etRePassword.text.toString(),
        activityBecomeMemberBinding.etFname.text.toString(),
        activityBecomeMemberBinding.etLname.text.toString(),
        activityBecomeMemberBinding.etMname.text.toString(),
        activityBecomeMemberBinding.etEmail.text.toString(),
        activityBecomeMemberBinding.spnLang.selectedItem.toString(),
        activityBecomeMemberBinding.spnLevel.selectedItem.toString(),
        activityBecomeMemberBinding.etPhone.text.toString(),
        dob,
        selectedGender()
    )

    private fun validateMemberInfo(info: MemberInfo): Boolean {
        return when {
            info.password.isEmpty() -> {
                activityBecomeMemberBinding.etPassword.error = getString(R.string.please_enter_a_password)
                false
            }
            info.password != info.rePassword -> {
                activityBecomeMemberBinding.etRePassword.error = getString(R.string.password_doesn_t_match)
                false
            }
            info.email.isNotEmpty() && !Utilities.isValidEmail(info.email) -> {
                activityBecomeMemberBinding.etEmail.error = getString(R.string.invalid_email)
                false
            }
            info.gender == null -> {
                Utilities.toast(this, getString(R.string.please_select_gender))
                false
            }
            else -> true
        }
    }

    private fun buildMemberJson(info: MemberInfo) = JsonObject().apply {
        addProperty("name", info.username)
        addProperty("firstName", info.fName)
        addProperty("lastName", info.lName)
        addProperty("middleName", info.mName)
        addProperty("password", info.password)
        addProperty("isUserAdmin", false)
        addProperty("joinDate", Calendar.getInstance().timeInMillis)
        addProperty("email", info.email)
        addProperty("planetCode", settings.getString("planetCode", ""))
        addProperty("parentCode", settings.getString("parentCode", ""))
        addProperty("language", info.language)
        addProperty("level", info.level)
        addProperty("phoneNumber", info.phoneNumber)
        addProperty("birthDate", info.birthDate)
        addProperty("gender", info.gender)
        addProperty("type", "user")
        addProperty("betaEnabled", false)
        addProperty("androidId", NetworkUtils.getUniqueIdentifier())
        addProperty("uniqueAndroidId", VersionUtils.getAndroidId(MainApplication.context))
        addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context))
        val roles = JsonArray().apply { add("learner") }
        add("roles", roles)
    }

    private fun addMember(info: MemberInfo) {
        val obj = buildMemberJson(info)
        val customProgressDialog = CustomProgressDialog(this).apply {
            setText(getString(R.string.creating_member_account))
            show()
        }

        Service(this).becomeMember(obj, object : Service.CreateUserCallback {
            override fun onSuccess(success: String) {
                runOnUiThread { Utilities.toast(this@BecomeMemberActivity, success) }
            }
        }, object : SecurityDataCallback {
            override fun onSecurityDataUpdated() {
                runOnUiThread {
                    customProgressDialog.dismiss()
                    autoLoginNewMember(info.username, info.password)
                }
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityBecomeMemberBinding = ActivityBecomeMemberBinding.inflate(layoutInflater)
        setContentView(activityBecomeMemberBinding.root)
        EdgeToEdgeUtils.setupEdgeToEdgeWithKeyboard(this, activityBecomeMemberBinding.root)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val languages = resources.getStringArray(R.array.language)
        val lnAadapter = ArrayAdapter(this, R.layout.become_a_member_spinner_layout, languages)
        activityBecomeMemberBinding.spnLang.adapter = lnAadapter
        activityBecomeMemberBinding.txtDob.setOnClickListener {
            showDatePickerDialog()
        }
        val levels = resources.getStringArray(R.array.level)
        val lvAdapter  = ArrayAdapter(this, R.layout.become_a_member_spinner_layout, levels)
        activityBecomeMemberBinding.spnLevel.adapter = lvAdapter

        val username = intent.getStringExtra("username") ?: ""
        guest = intent.getBooleanExtra("guest", false)

        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        setupTextWatchers()

        if (guest) {
            activityBecomeMemberBinding.etUsername.setText(username)
            activityBecomeMemberBinding.etUsername.isFocusable = false
        }


        activityBecomeMemberBinding.btnCancel.setOnClickListener {
            finish()
        }

        activityBecomeMemberBinding.btnSubmit.setOnClickListener {
            val info = collectMemberInfo()
            lifecycleScope.launch {
                val validationResult = userRepository.validateUsername(info.username)
                withContext(Dispatchers.Main) {
                    when (validationResult) {
                        is UsernameValidationResult.Valid -> {
                            if (validateMemberInfo(info)) {
                                addMember(info)
                            }
                        }
                        is UsernameValidationResult.Taken -> {
                            activityBecomeMemberBinding.etUsername.error = getString(R.string.username_taken)
                        }
                        is UsernameValidationResult.Invalid -> {
                            activityBecomeMemberBinding.etUsername.error = validationResult.reason
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        activityBecomeMemberBinding.etUsername.removeTextChangedListener(usernameWatcher)
        activityBecomeMemberBinding.etPassword.removeTextChangedListener(passwordWatcher)
        usernameWatcher = null
        passwordWatcher = null
        super.onDestroy()
    }

    private fun autoLoginNewMember(username: String, password: String) {
        lifecycleScope.launch {
            userRepository.cleanupDuplicateUsers()
            val intent = Intent(this@BecomeMemberActivity, LoginActivity::class.java)
            intent.putExtra("username", username)
            intent.putExtra("password", password)
            intent.putExtra("auto_login", true)
            if (guest) {
                intent.putExtra("guest", guest)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }

    private fun setupTextWatchers() {
        usernameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString() ?: ""
                lifecycleScope.launch {
                    val validationResult = userRepository.validateUsername(input)
                    withContext(Dispatchers.Main) {
                        when (validationResult) {
                            is UsernameValidationResult.Valid -> {
                                val lowercase = input.lowercase()
                                if (input != lowercase) {
                                    activityBecomeMemberBinding.etUsername.setText(lowercase)
                                    activityBecomeMemberBinding.etUsername.setSelection(lowercase.length)
                                }
                                activityBecomeMemberBinding.etUsername.error = null
                            }
                            is UsernameValidationResult.Taken -> {
                                activityBecomeMemberBinding.etUsername.error = getString(R.string.username_taken)
                            }
                            is UsernameValidationResult.Invalid -> {
                                activityBecomeMemberBinding.etUsername.error = validationResult.reason
                            }
                        }
                    }
                }
            }
        }
        activityBecomeMemberBinding.etUsername.addTextChangedListener(usernameWatcher)

        passwordWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (activityBecomeMemberBinding.etPassword.text.toString().isEmpty()) {
                    activityBecomeMemberBinding.etRePassword.setText("")
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        }
        activityBecomeMemberBinding.etPassword.addTextChangedListener(passwordWatcher)
    }
}
