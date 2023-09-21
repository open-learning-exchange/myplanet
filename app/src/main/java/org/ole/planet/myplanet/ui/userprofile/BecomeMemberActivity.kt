package org.ole.planet.myplanet.ui.userprofile

import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.Spinner
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_become_member.btn_cancel
import kotlinx.android.synthetic.main.activity_become_member.btn_submit
import kotlinx.android.synthetic.main.activity_become_member.et_email
import kotlinx.android.synthetic.main.activity_become_member.et_fname
import kotlinx.android.synthetic.main.activity_become_member.et_lname
import kotlinx.android.synthetic.main.activity_become_member.et_mname
import kotlinx.android.synthetic.main.activity_become_member.et_password
import kotlinx.android.synthetic.main.activity_become_member.et_phone
import kotlinx.android.synthetic.main.activity_become_member.et_re_password
import kotlinx.android.synthetic.main.activity_become_member.et_username
import kotlinx.android.synthetic.main.activity_become_member.pbar
import kotlinx.android.synthetic.main.activity_become_member.rb_gender
import kotlinx.android.synthetic.main.activity_become_member.spn_lang
import kotlinx.android.synthetic.main.activity_become_member.spn_level
import kotlinx.android.synthetic.main.activity_become_member.txt_dob
import kotlinx.android.synthetic.main.activity_become_member.view.female
import kotlinx.android.synthetic.main.activity_become_member.view.male
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils
import java.util.Calendar
import java.util.Locale

class BecomeMemberActivity : BaseActivity() {

    var dob: String = "";
    lateinit var settings: SharedPreferences
    private fun showDatePickerDialog() {
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog(
            this, { _, i, i1, i2 ->
                dob = String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2)
                txt_dob.text = dob
            }, now[Calendar.YEAR], now[Calendar.MONTH], now[Calendar.DAY_OF_MONTH]
        )
        dpd.datePicker.maxDate = now.timeInMillis
        dpd.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_become_member)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        var mRealm: Realm = DatabaseService(this).realmInstance;
        var user = UserProfileDbHandler(this).userModel;
        val languages = resources.getStringArray(R.array.language)
        val languageSpinner = findViewById(R.id.spn_lang) as Spinner
        val adapter = ArrayAdapter<String>(this, R.layout.become_a_member_spinner_layout, languages)
        languageSpinner.adapter = adapter
        txt_dob.setOnClickListener {
            showDatePickerDialog()
        }

        val username = intent.getStringExtra("username");

        settings = getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE)
        textChangedListener(mRealm)

        if (username != null) {
            et_username.setText(username)
        }
        et_username.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                s?.let {
                    val firstChar = if (it.isNotEmpty()) it[0] else null

                    val hasInvalidCharacters = it.any { char ->
                        char != '_' && char != '.' && char != '-' &&
                                !Character.isDigit(char) && !Character.isLetter(char)
                    }

                    if (firstChar != null && !Character.isDigit(firstChar) && !Character.isLetter(firstChar)) {
                        et_username.error = getString(R.string.must_start_with_letter_or_number)
                    } else if (hasInvalidCharacters) {
                        et_username.error = getString(R.string.only_letters_numbers_and_are_allowed)
                    } else {
                        val lowercaseText = it.toString().toLowerCase(Locale.ROOT)
                        if (it.toString() != lowercaseText) {
                            et_username.setText(lowercaseText)
                            et_username.setSelection(lowercaseText.length)
                        }
                        et_username.error = null
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        btn_cancel.setOnClickListener {
            finish()
        }
        btn_submit.setOnClickListener {
            var username: String? = et_username.text.toString()
            var password: String = et_password.text.toString()
            var repassword: String? = et_re_password.text.toString()
            var fname: String? = et_fname.text.toString()
            var lname: String? = et_lname.text.toString()
            var mname: String? = et_mname.text.toString()
            var email: String? = et_email.text.toString()
            var language: String? = spn_lang.selectedItem.toString()
            var phoneNumber: String = et_phone.text.toString()
            var birthDate: String? = dob
            var level: String? = spn_level.selectedItem.toString()

//            var rb: RadioButton? =
//                findViewById<View>(rb_gender.checkedRadioButtonId) as RadioButton?
            var gender: String? = null
//            if (rb != null) gender = rb.text.toString()
//            else {
//                Utilities.toast(this, getString(R.string.please_select_gender))
//            }
            val firstChar = if (username!!.isNotEmpty()) username[0] else null
            val hasInvalidCharacters = username.any { char ->
                char != '_' && char != '.' && char != '-' &&
                        !Character.isDigit(char) && !Character.isLetter(char)
            }

            if (TextUtils.isEmpty(username)) {
                et_username.error = getString(R.string.please_enter_a_username)
            } else if (username.contains(" ")) {
                et_username.error = getString(R.string.invalid_username)
            } else if (firstChar != null && !Character.isDigit(firstChar) && !Character.isLetter(firstChar)) {
                et_username.error = getString(R.string.must_start_with_letter_or_number)
            } else if (hasInvalidCharacters) {
                et_username.error = getString(R.string.only_letters_numbers_and_are_allowed)
            } else if (TextUtils.isEmpty(password)) {
                et_password.error = getString(R.string.please_enter_a_password)
            } else if (password != repassword) {
                et_re_password.error = getString(R.string.password_doesn_t_match)
            } else if (!TextUtils.isEmpty(email) && !Utilities.isValidEmail(email)) {
                et_email.error = getString(R.string.invalid_email)
            } else if (rb_gender.checkedRadioButtonId == -1) {
                Utilities.toast(this, getString(R.string.please_select_gender))
            } else if (level == null) {
                Utilities.toast(this, getString(R.string.level_is_required));
            } else {
                if (rb_gender.male.isChecked) {
                    gender = "male"
                } else if (rb_gender.female.isChecked) {
                    gender = "female"
                }

                if (TextUtils.isEmpty(password) && !TextUtils.isEmpty(phoneNumber)) {
                    et_re_password.setText(phoneNumber)
                    password = phoneNumber
                    ///Add dialog that using phone as password , Agree / disagree
                }

                checkMandatoryFieldsAndAddMember(
                    username,
                    password,
                    repassword,
                    fname,
                    lname,
                    mname,
                    email,
                    language,
                    level,
                    phoneNumber,
                    birthDate,
                    gender,
                    mRealm
                )
            }
        }
    }

    private fun checkMandatoryFieldsAndAddMember(
        username: String,
        password: String,
        repassword: String?,
        fname: String?,
        lname: String?,
        mname: String?,
        email: String?,
        language: String?,
        level: String?,
        phoneNumber: String?,
        birthDate: String?,
        gender: String?,
        mRealm: Realm
    ) {
        /**
         * Creates and adds a new member if the username and password
         * are not empty and password matches repassword.
         */
        if (username.isNotEmpty() && password.isNotEmpty() && repassword == password) {
            var obj = JsonObject()
            obj.addProperty("name", username)
            obj.addProperty("firstName", fname)
            obj.addProperty("lastName", lname)
            obj.addProperty("middleName", mname)
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
            var roles = JsonArray()
            roles.add("learner")
            obj.add("roles", roles)
            pbar.visibility = View.VISIBLE
            Service(this).becomeMember(mRealm, obj) { res ->
                runOnUiThread {
                    pbar.visibility = View.GONE
                    Utilities.toast(this, res)
                }
                finish()
            }
        }
    }

    private fun textChangedListener(mRealm: Realm) {
        et_username.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (RealmUserModel.isUserExists(mRealm, et_username.text.toString())) {
                    et_username.error = getString(R.string.username_taken)
                    return
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }
        })

        et_password.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                et_re_password.isEnabled = et_password.text.toString().isNotEmpty()
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }
        })
    }
}
