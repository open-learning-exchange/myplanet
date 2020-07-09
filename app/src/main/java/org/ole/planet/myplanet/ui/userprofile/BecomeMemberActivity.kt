package org.ole.planet.myplanet.ui.userprofile

import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_become_member.*
import kotlinx.android.synthetic.main.fragment_course_detail.*
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.sync.SyncActivity
import org.ole.planet.myplanet.utilities.NetworkUtils
import org.ole.planet.myplanet.utilities.Utilities
import org.ole.planet.myplanet.utilities.VersionUtils
import java.util.*

class BecomeMemberActivity : BaseActivity() {

    var dob: String = "";
    lateinit var settings: SharedPreferences
    private fun showDatePickerDialog() {
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog(this, DatePickerDialog.OnDateSetListener { datePicker, i, i1, i2 ->
            dob = String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2)
            txt_dob.text = dob
        }, now[Calendar.YEAR],
                now[Calendar.MONTH],
                now[Calendar.DAY_OF_MONTH])
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

        settings = getSharedPreferences(SyncActivity.PREFS_NAME, Context.MODE_PRIVATE)
        textChangedListener(mRealm)

        btn_cancel.setOnClickListener {
            finish()
        }
        btn_submit.setOnClickListener {
            var username: String? = et_username.text.toString()
            var password: String? = et_password.text.toString()
            var repassword: String? = et_re_password.text.toString()
            var fname: String? = et_fname.text.toString()
            var lname: String? = et_lname.text.toString()
            var mname: String? = et_mname.text.toString()
            var email: String? = et_email.text.toString()
            var language: String? = spn_lang.selectedItem.toString()
            var phoneNumber: String? = et_phone.text.toString()
            var birthDate: String? = dob
            var level: String? = spn_level.selectedItem.toString()

            var rb: RadioButton? = findViewById<View>(rb_gender.checkedRadioButtonId) as RadioButton?
            var gender: String? = ""
            if (rb != null)
                gender = rb.text.toString()
            else {
                Utilities.toast(this, "Please select gender")
            }
            if (username!!.isEmpty()) {
                et_username.error = "Please enter a username"
            }
            else if (username.contains(" ")){
                et_username.error = "Invalid username"
            }
            if (password!!.isEmpty()) {
                et_password.error = "Please enter a password"
            }
            else if (password != repassword) {
                et_re_password.error = "Password doesn't match"
            }
            if (email!!.isNotEmpty() && !Utilities.isValidEmail(email)) {
                et_email.error = "Invalid email."
            }
            if (level == null) {
                Utilities.toast(this, "Level is required")
            }
            if (password!!.isEmpty() && phoneNumber!!.isNotEmpty()) {
                et_re_password.setText(phoneNumber)
                password = phoneNumber
                ///Add dialog that using phone as password , Agree / disagree
            }

            checkMandatoryFieldsAndAddMember(username, password, repassword, fname, lname, mname, email, language, level, phoneNumber, birthDate, gender, mRealm)

        }
    }

    private fun checkMandatoryFieldsAndAddMember(username: String, password: String, repassword: String?, fname: String?, lname: String?, mname: String?, email: String?, language: String?, level: String?, phoneNumber: String?, birthDate: String?, gender: String?, mRealm: Realm) {
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
            obj.addProperty("customDeviceName", NetworkUtils.getCustomDeviceName(MainApplication.context))
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
                    et_username.error = "username taken"
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
