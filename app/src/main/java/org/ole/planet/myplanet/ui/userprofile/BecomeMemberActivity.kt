package org.ole.planet.myplanet.ui.userprofile

import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.RadioButton
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
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils
import java.util.*

class BecomeMemberActivity : BaseActivity() {
    lateinit var binding: ActivityBecomeMemberBinding

    var dob: String = "";
    lateinit var settings: SharedPreferences
    private fun showDatePickerDialog() {
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog(
            this, DatePickerDialog.OnDateSetListener { datePicker, i, i1, i2 ->
                dob = String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2)
                binding.txtDob.text = dob
            }, now[Calendar.YEAR],
            now[Calendar.MONTH],
            now[Calendar.DAY_OF_MONTH]
        )
        dpd.datePicker.maxDate = now.timeInMillis
        dpd.show()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBecomeMemberBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setContentView(R.layout.activity_become_member)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        var mRealm: Realm = DatabaseService(this).realmInstance;
        var user = UserProfileDbHandler(this).userModel;
        val languages = resources.getStringArray(R.array.language)
        val adapter = ArrayAdapter<String>(this, R.layout.become_a_member_spinner_layout, languages)
        binding.spnLang.adapter = adapter
        binding.txtDob.setOnClickListener {
            showDatePickerDialog()
        }

        settings = getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE)
        textChangedListener(mRealm)

        binding.btnCancel.setOnClickListener {
            finish()
        }
        binding.btnSubmit.setOnClickListener {
            var username: String? = binding.etUsername.text.toString()
            var password: String? = binding.etPassword.text.toString()
            var repassword: String? = binding.etRePassword.text.toString()
            var fname: String? = binding.etFname.text.toString()
            var lname: String? = binding.etLname.text.toString()
            var mname: String? = binding.etMname.text.toString()
            var email: String? = binding.etEmail.text.toString()
            var language: String? = binding.spnLang.selectedItem.toString()
            var phoneNumber: String? = binding.etPhone.text.toString()
            var birthDate: String? = dob
            var level: String? = binding.spnLevel.selectedItem.toString()

            var rb: RadioButton? =
                findViewById<View>(binding.rbGender.checkedRadioButtonId) as RadioButton?
            var gender: String? = ""
            if (rb != null)
                gender = rb.text.toString()
            else {
                Utilities.toast(this, "Please select gender")
            }
            if (username!!.isEmpty()) {
                binding.etUsername.error = "Please enter a username"
            } else if (username.contains(" ")) {
                binding.etUsername.error = "Invalid username"
            }
            if (password!!.isEmpty()) {
                binding.etPassword.error = "Please enter a password"
            } else if (password != repassword) {
                binding.etRePassword.error = "Password doesn't match"
            }
            if (email!!.isNotEmpty() && !Utilities.isValidEmail(email)) {
                binding.etEmail.error = "Invalid email."
            }
            if (level == null) {
                Utilities.toast(this, "Level is required")
            }
            if (password!!.isEmpty() && phoneNumber!!.isNotEmpty()) {
                binding.etRePassword.setText(phoneNumber)
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
            //            obj.addProperty("repeatPassword", repassword )
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
            obj.addProperty("macAddress", NetworkUtils.getMacAddr())
            obj.addProperty("androidId", NetworkUtils.getMacAddr())
            obj.addProperty("uniqueAndroidId", VersionUtils.getAndroidId(MainApplication.context))
            obj.addProperty(
                "customDeviceName",
                NetworkUtils.getCustomDeviceName(MainApplication.context)
            )
            var roles = JsonArray()
            roles.add("learner")
            obj.add("roles", roles)
            binding.pbar.visibility = View.VISIBLE
            Service(this).becomeMember(mRealm, obj) { res ->
                runOnUiThread {
                    binding.pbar.visibility = View.GONE
                    Utilities.toast(this, res)
                }
                finish()
            }
        }
    }

    private fun textChangedListener(mRealm: Realm) {
        binding.etUsername.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (RealmUserModel.isUserExists(mRealm, binding.etUsername.text.toString())) {
                    binding.etUsername.error = "username taken"
                    return
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }
        })

        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                binding.etRePassword.isEnabled = binding.etPassword.text.toString().isNotEmpty()
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }
        })
    }

}
