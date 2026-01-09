package org.ole.planet.myplanet.ui.user

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
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
import org.ole.planet.myplanet.callback.SecurityDataListener
import org.ole.planet.myplanet.data.DataService
import org.ole.planet.myplanet.databinding.ActivityBecomeMemberBinding
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils.CustomProgressDialog
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils

@AndroidEntryPoint
class BecomeMemberActivity : BaseActivity() {
    private lateinit var activityBecomeMemberBinding: ActivityBecomeMemberBinding
    var dob: String = ""
    var guest: Boolean = false
    private var usernameWatcher: TextWatcher? = null
    private var passwordWatcher: TextWatcher? = null

    companion object {
        private const val TAG = "BECOME_MEMBER"
    }

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

    private fun collectMemberInfo(): MemberInfo {
        val info = MemberInfo(
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
        Log.d(TAG, "Collected member info: username=${info.username}, email=${info.email}, gender=${info.gender}")
        return info
    }

    private fun validateMemberInfo(info: MemberInfo): Boolean {
        Log.d(TAG, "Validating member info for username: ${info.username}")
        return when {
            info.password.isEmpty() -> {
                Log.w(TAG, "Validation failed: password is empty")
                activityBecomeMemberBinding.etPassword.error = getString(R.string.please_enter_a_password)
                false
            }
            info.password != info.rePassword -> {
                Log.w(TAG, "Validation failed: passwords don't match")
                activityBecomeMemberBinding.etRePassword.error = getString(R.string.password_doesn_t_match)
                false
            }
            info.email.isNotEmpty() && !Utilities.isValidEmail(info.email) -> {
                Log.w(TAG, "Validation failed: invalid email - ${info.email}")
                activityBecomeMemberBinding.etEmail.error = getString(R.string.invalid_email)
                false
            }
            info.gender == null -> {
                Log.w(TAG, "Validation failed: gender not selected")
                Utilities.toast(this, getString(R.string.please_select_gender))
                false
            }
            else -> {
                Log.d(TAG, "Validation passed for username: ${info.username}")
                true
            }
        }
    }

    private fun buildMemberJson(info: MemberInfo) = JsonObject().apply {
        Log.d(TAG, "Building member JSON for username: ${info.username}")
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
        Log.d(TAG, "Member JSON built successfully for username: ${info.username}")
    }

    private fun addMember(info: MemberInfo) {
        Log.d(TAG, "Starting addMember process for username: ${info.username}")
        val obj = buildMemberJson(info)
        val customProgressDialog = CustomProgressDialog(this).apply {
            setText(getString(R.string.creating_member_account))
            show()
        }
        Log.d(TAG, "Progress dialog shown, calling DataService.becomeMember()")

        DataService(this).becomeMember(obj, object : DataService.CreateUserCallback {
            override fun onSuccess(success: String) {
                Log.d(TAG, "DataService.becomeMember callback onSuccess: $success")
                runOnUiThread { Utilities.toast(this@BecomeMemberActivity, success) }
            }
        }, object : SecurityDataListener {
            override fun onSecurityDataUpdated() {
                Log.d(TAG, "SecurityDataListener callback triggered - member creation completed")
                runOnUiThread {
                    customProgressDialog.dismiss()
                    Log.d(TAG, "Progress dialog dismissed, navigating to auto-login")
                    autoLoginNewMember(info.username, info.password)
                }
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "BecomeMemberActivity onCreate started")
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
        Log.d(TAG, "Intent extras - username: $username, guest: $guest")

        settings = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        setupTextWatchers()

        if (guest) {
            activityBecomeMemberBinding.etUsername.setText(username)
            activityBecomeMemberBinding.etUsername.isFocusable = false
            Log.d(TAG, "Guest mode - username field pre-filled and locked")
        }


        activityBecomeMemberBinding.btnCancel.setOnClickListener {
            Log.d(TAG, "Cancel button clicked")
            finish()
        }

        activityBecomeMemberBinding.btnSubmit.setOnClickListener {
            Log.d(TAG, "Submit button clicked - starting member registration")
            val info = collectMemberInfo()
            lifecycleScope.launch {
                Log.d(TAG, "Validating username: ${info.username}")
                val error = userRepository.validateUsername(info.username)
                withContext(Dispatchers.Main) {
                    if (error != null) {
                        Log.w(TAG, "Username validation failed: $error")
                        activityBecomeMemberBinding.etUsername.error = error
                    } else if (validateMemberInfo(info)) {
                        Log.d(TAG, "All validations passed, proceeding to add member")
                        addMember(info)
                    } else {
                        Log.w(TAG, "Member info validation failed")
                    }
                }
            }
        }
        Log.d(TAG, "BecomeMemberActivity onCreate completed")
    }

    override fun onDestroy() {
        Log.d(TAG, "BecomeMemberActivity onDestroy")
        activityBecomeMemberBinding.etUsername.removeTextChangedListener(usernameWatcher)
        activityBecomeMemberBinding.etPassword.removeTextChangedListener(passwordWatcher)
        usernameWatcher = null
        passwordWatcher = null
        super.onDestroy()
    }

    private fun autoLoginNewMember(username: String, password: String) {
        Log.d(TAG, "Starting auto-login for new member: $username")
        lifecycleScope.launch {
            Log.d(TAG, "Cleaning up duplicate users before auto-login")
            userRepository.cleanupDuplicateUsers()
            Log.d(TAG, "Duplicate users cleaned up, preparing intent for LoginActivity")
            val intent = Intent(this@BecomeMemberActivity, LoginActivity::class.java)
            intent.putExtra("username", username)
            intent.putExtra("password", password)
            intent.putExtra("auto_login", true)
            if (guest) {
                intent.putExtra("guest", guest)
                Log.d(TAG, "Guest flag added to auto-login intent")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            Log.d(TAG, "Launching LoginActivity with auto-login for username: $username")
            startActivity(intent)
            finish()
            Log.d(TAG, "BecomeMemberActivity finished, auto-login flow initiated")
        }
    }

    private fun setupTextWatchers() {
        usernameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString() ?: ""
                lifecycleScope.launch {
                    val error = userRepository.validateUsername(input)
                    withContext(Dispatchers.Main) {
                        if (error != null) {
                            activityBecomeMemberBinding.etUsername.error = error
                        } else {
                            val lowercase = input.lowercase()
                            if (input != lowercase) {
                                activityBecomeMemberBinding.etUsername.setText(lowercase)
                                activityBecomeMemberBinding.etUsername.setSelection(lowercase.length)
                            }
                            activityBecomeMemberBinding.etUsername.error = null
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
