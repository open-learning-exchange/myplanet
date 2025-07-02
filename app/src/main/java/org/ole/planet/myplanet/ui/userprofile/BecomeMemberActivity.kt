package org.ole.planet.myplanet.ui.userprofile

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.callback.SecurityDataCallback
import org.ole.planet.myplanet.databinding.ActivityBecomeMemberBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils.CustomProgressDialog
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import androidx.core.content.edit

class BecomeMemberActivity : BaseActivity() {
    private lateinit var activityBecomeMemberBinding: ActivityBecomeMemberBinding
    var dob: String = ""
    var guest: Boolean = false

    companion object {
        private const val DIACRITIC_REGEX = ".*[ßäöüéèêæÆœøØ¿àìòùÀÈÌÒÙáíóúýÁÉÍÓÚÝâîôûÂÊÎÔÛãñõÃÑÕëïÿÄËÏÖÜŸåÅŒçÇðÐ].*"
        private val DIACRITIC_PATTERN: Pattern = Pattern.compile(DIACRITIC_REGEX)
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

    private fun hasInvalidCharacters(input: String) =
        input.any { it != '_' && it != '.' && it != '-' && !it.isDigit() && !it.isLetter() }

    private fun hasSpecialCharacters(input: String) = DIACRITIC_PATTERN.matcher(input).matches()

    private fun hasDiacriticCharacters(input: String): Boolean {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        return !normalized.codePoints().allMatch {
            Character.isLetterOrDigit(it) || it == '.'.code || it == '-'.code || it == '_'.code
        }
    }

    private fun usernameValidationError(username: String, realm: Realm? = null): String? {
        val firstChar = username.firstOrNull()
        return when {
            username.isEmpty() -> getString(R.string.please_enter_a_username)
            username.contains(" ") -> getString(R.string.invalid_username)
            firstChar != null && !firstChar.isDigit() && !firstChar.isLetter() -> getString(R.string.must_start_with_letter_or_number)
            hasInvalidCharacters(username) || hasSpecialCharacters(username) || hasDiacriticCharacters(username) -> getString(R.string.only_letters_numbers_and_are_allowed)
            realm != null && RealmUserModel.isUserExists(realm, username) -> getString(R.string.username_taken)
            else -> null
        }
    }

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

    private fun validateMemberInfo(info: MemberInfo, realm: Realm): Boolean {
        usernameValidationError(info.username, realm)?.let {
            activityBecomeMemberBinding.etUsername.error = it
            return false
        }

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

    private fun addMember(info: MemberInfo, realm: Realm) {
        val obj = buildMemberJson(info)
        val customProgressDialog = CustomProgressDialog(this).apply {
            setText(getString(R.string.creating_member_account))
            show()
        }

        Service(this).becomeMember(realm, obj, object : Service.CreateUserCallback {
            override fun onSuccess(message: String) {
                runOnUiThread { Utilities.toast(this@BecomeMemberActivity, message) }
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
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val mRealm: Realm = DatabaseService(this).realmInstance
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
        setupTextWatchers(mRealm)

        if (guest) {
            activityBecomeMemberBinding.etUsername.setText(username)
            activityBecomeMemberBinding.etUsername.isFocusable = false
        }


        activityBecomeMemberBinding.btnCancel.setOnClickListener {
            finish()
        }

        activityBecomeMemberBinding.btnSubmit.setOnClickListener {
            val info = collectMemberInfo()
            if (validateMemberInfo(info, mRealm)) {
                addMember(info, mRealm)
            }
        }
    }

    private fun autoLoginNewMember(username: String, password: String) {
        val intent = Intent(this, LoginActivity::class.java)
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

    private fun setupTextWatchers(mRealm: Realm) {
        activityBecomeMemberBinding.etUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString() ?: ""
                val error = usernameValidationError(input, mRealm)
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
        })

        activityBecomeMemberBinding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (activityBecomeMemberBinding.etPassword.text.toString().isEmpty()) {
                    activityBecomeMemberBinding.etRePassword.setText("")
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
        })
    }
}
