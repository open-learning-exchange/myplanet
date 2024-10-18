package org.ole.planet.myplanet.ui.userprofile

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.databinding.ActivityBecomeMemberBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.ui.sync.LoginActivity
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

class BecomeMemberActivity : BaseActivity() {
    private lateinit var activityBecomeMemberBinding: ActivityBecomeMemberBinding
    var dob: String = ""
//    lateinit var settings: SharedPreferences
    var guest: Boolean = false
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
        textChangedListener(mRealm)

        if (guest) {
            activityBecomeMemberBinding.etUsername.setText(username)
            activityBecomeMemberBinding.etUsername.isFocusable = false
        }

        activityBecomeMemberBinding.etUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s.toString()

                val firstChar = if (input.isNotEmpty()) input[0] else '\u0000'
                var hasInvalidCharacters = false
                val hasSpecialCharacters: Boolean
                var hasDiacriticCharacters = false

                val normalizedText = Normalizer.normalize(s, Normalizer.Form.NFD)

                for (element in input) {
                    if (element != '_' && element != '.' && element != '-'
                        && !Character.isDigit(element) && !Character.isLetter(element)) {
                        hasInvalidCharacters = true
                        break
                    }
                }

                val regex = ".*[ßäöüéèêæÆœøØ¿àìòùÀÈÌÒÙáíóúýÁÉÍÓÚÝâîôûÂÊÎÔÛãñõÃÑÕëïÿÄËÏÖÜŸåÅŒçÇðÐ].*"
                val pattern = Pattern.compile(regex)
                val matcher = pattern.matcher(input)

                hasSpecialCharacters = matcher.matches()
                hasDiacriticCharacters = !normalizedText.codePoints().allMatch { codePoint: Int ->
                    Character.isLetterOrDigit(codePoint) || codePoint == '.'.code || codePoint == '-'.code || codePoint == '_'.code
                }

                if (!Character.isDigit(firstChar) && !Character.isLetter(firstChar)) {
                    activityBecomeMemberBinding.etUsername.error = getString(R.string.must_start_with_letter_or_number)
                } else if (hasInvalidCharacters || hasDiacriticCharacters || hasSpecialCharacters) {
                        activityBecomeMemberBinding.etUsername.error = getString(R.string.only_letters_numbers_and_are_allowed)
                } else {
                    val lowercaseText = input.lowercase()
                    if (input != lowercaseText) {
                        activityBecomeMemberBinding.etUsername.setText(lowercaseText)
                        activityBecomeMemberBinding.etUsername.setSelection(lowercaseText.length)
                    }
                    activityBecomeMemberBinding.etUsername.error = null
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        activityBecomeMemberBinding.btnCancel.setOnClickListener {
            finish()
        }

        activityBecomeMemberBinding.btnSubmit.setOnClickListener {
            val userName: String = activityBecomeMemberBinding.etUsername.text.toString()
            var password: String? = activityBecomeMemberBinding.etPassword.text.toString()
            val rePassword: String = activityBecomeMemberBinding.etRePassword.text.toString()
            val fName: String = activityBecomeMemberBinding.etFname.text.toString()
            val lName: String = activityBecomeMemberBinding.etLname.text.toString()
            val mName: String = activityBecomeMemberBinding.etMname.text.toString()
            val email: String = activityBecomeMemberBinding.etEmail.text.toString()
            val language: String = activityBecomeMemberBinding.spnLang.selectedItem.toString()
            val phoneNumber: String = activityBecomeMemberBinding.etPhone.text.toString()
            val birthDate: String = dob
            val level: String = activityBecomeMemberBinding.spnLevel.selectedItem.toString()
            var gender: String? = null
          
            val firstChar = if (userName.isNotEmpty()) {
                userName[0]
            } else {
                null
            }
            val hasInvalidCharacters = userName.any { char ->
                char != '_' && char != '.' && char != '-' && !Character.isDigit(char) && !Character.isLetter(char)
            }

            val normalizedText = Normalizer.normalize(username, Normalizer.Form.NFD)

            val regex = ".*[ßäöüéèêæÆœøØ¿àìòùÀÈÌÒÙáíóúýÁÉÍÓÚÝâîôûÂÊÎÔÛãñõÃÑÕëïÿÄËÏÖÜŸåÅŒçÇðÐ].*"
            val pattern = Pattern.compile(regex)
            val matcher = pattern.matcher(userName)

            val hasSpecialCharacters = matcher.matches()
            val hasDiacriticCharacters = !normalizedText.codePoints().allMatch { codePoint: Int ->
                Character.isLetterOrDigit(codePoint) || codePoint == '.'.code || codePoint == '-'.code || codePoint == '_'.code
            }

            if (TextUtils.isEmpty(userName)) {
                activityBecomeMemberBinding.etUsername.error = getString(R.string.please_enter_a_username)
            } else if (userName.contains(" ")) {
                activityBecomeMemberBinding.etUsername.error = getString(R.string.invalid_username)
            } else if (firstChar != null && !Character.isDigit(firstChar) && !Character.isLetter(firstChar)) {
                activityBecomeMemberBinding.etUsername.error = getString(R.string.must_start_with_letter_or_number)
            } else if (hasInvalidCharacters || hasSpecialCharacters || hasDiacriticCharacters) {
               activityBecomeMemberBinding.etUsername.error = getString(R.string.only_letters_numbers_and_are_allowed)
            } else if (TextUtils.isEmpty(password)) {
                activityBecomeMemberBinding.etPassword.error = getString(R.string.please_enter_a_password)
            } else if (password != rePassword) {
                activityBecomeMemberBinding.etRePassword.error = getString(R.string.password_doesn_t_match)
            } else if (!TextUtils.isEmpty(email) && !Utilities.isValidEmail(email)) {
                activityBecomeMemberBinding.etEmail.error = getString(R.string.invalid_email)
            } else if (activityBecomeMemberBinding.rbGender.checkedRadioButtonId == -1) {
                Utilities.toast(this, getString(R.string.please_select_gender))
            } else {
                if (activityBecomeMemberBinding.male.isChecked) {
                    gender = "male"
                } else if (activityBecomeMemberBinding.female.isChecked) {
                    gender = "female"
                }

                if (TextUtils.isEmpty(password) && !TextUtils.isEmpty(phoneNumber)) {
                    activityBecomeMemberBinding.etRePassword.setText(phoneNumber)
                    password = phoneNumber
                }

                checkMandatoryFieldsAndAddMember(
                    userName, password, rePassword, fName, lName, mName, email, language, level,
                    phoneNumber, birthDate, gender, mRealm
                )
            }
        }
    }

    private fun checkMandatoryFieldsAndAddMember(
        username: String, password: String, rePassword: String?, fName: String?, lName: String?,
        mName: String?, email: String?, language: String?, level: String?, phoneNumber: String?,
        birthDate: String?, gender: String?, mRealm: Realm
    ) {
        if (username.isNotEmpty() && password.isNotEmpty() && rePassword == password) {
            val obj = JsonObject()
            obj.addProperty("name", username)
            obj.addProperty("firstName", fName)
            obj.addProperty("lastName", lName)
            obj.addProperty("middleName", mName)
            obj.addProperty("password", password)
            obj.addProperty("isUserAdmin", false)
            obj.addProperty("joinDate", Calendar.getInstance().timeInMillis)
            obj.addProperty("email", email)
            obj.addProperty("planetCode", settings.getString("planetCode", ""))
            obj.addProperty("parentCode", settings.getString("parentCode", ""))
            obj.addProperty("language", language)
            obj.addProperty("level", level)
            obj.addProperty("phoneNumber", phoneNumber)
            obj.addProperty("birthDate", birthDate)
            obj.addProperty("gender", gender)
            obj.addProperty("type", "user")
            obj.addProperty("betaEnabled", false)
            obj.addProperty("androidId", NetworkUtils.getUniqueIdentifier())
            obj.addProperty("uniqueAndroidId", VersionUtils.getAndroidId(MainApplication.context))
            obj.addProperty(
                "customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context)
            )
            val roles = JsonArray()
            roles.add("learner")
            obj.add("roles", roles)
            activityBecomeMemberBinding.pbar.visibility = View.VISIBLE
            Service(this).becomeMember(mRealm, obj, object : Service.CreateUserCallback {
                override fun onSuccess(message: String) {
                    runOnUiThread {
                        startUpload("becomeMember")
                        activityBecomeMemberBinding.pbar.visibility = View.GONE
                        Utilities.toast(this@BecomeMemberActivity, message)
                    }
                    finish()
                }
            })

            val intent = Intent(this, LoginActivity::class.java)
            if (guest){
                intent.putExtra("username", username)
                intent.putExtra("guest", guest)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
        }
    }

    private fun textChangedListener(mRealm: Realm) {
        activityBecomeMemberBinding.etUsername.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (RealmUserModel.isUserExists(mRealm, activityBecomeMemberBinding.etUsername.text.toString())) {
                    activityBecomeMemberBinding.etUsername.error = getString(R.string.username_taken)
                    return
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }
        })

        activityBecomeMemberBinding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                activityBecomeMemberBinding.etRePassword.isEnabled = activityBecomeMemberBinding.etPassword.text.toString().isNotEmpty()
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }
        })
    }
}
