package org.ole.planet.myplanet.ui.userprofile

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_become_member.*
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseActivity
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Utilities
import java.util.*
import java.util.regex.Pattern

class BecomeMemberActivity : BaseActivity() {

    lateinit var dob: String;
    private fun showDatePickerDialog() {
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog(this, DatePickerDialog.OnDateSetListener { datePicker, i, i1, i2 ->
            dob = String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2)
            txt_dob.text = dob
        }, now[Calendar.YEAR],
                now[Calendar.MONTH],
                now[Calendar.DAY_OF_MONTH])
        dpd.show()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_become_member)
        var mRealm: Realm = DatabaseService(this).realmInstance;
        var user = UserProfileDbHandler(this).userModel;
        txt_dob.setOnClickListener {
            showDatePickerDialog()
        }

        btn_cancel.setOnClickListener {
            finish()
        }
        btn_submit.setOnClickListener {
            var username: String? = et_username.text.toString()
            var password : String? = et_password.text.toString()
            var repassword: String?  = et_re_password.text.toString()
            var fname: String?  = et_fname.text.toString()
            var lname: String?  = et_lname.text.toString()
            var mname : String? = et_mname.text.toString()
            var email : String? = et_email.text.toString()
            var language : String? = spn_lang.selectedItem.toString()
            var phoneNumber : String? = et_phone.text.toString()
            var birthDate : String? = txt_dob.text.toString()
            var level : String? = spn_level.selectedItem.toString()
            var rb: RadioButton? = findViewById<View>(rb_gender.checkedRadioButtonId) as RadioButton?
            var gender: String?  = ""
            if (rb != null)
                gender = rb.text.toString()
            if (username!!.isEmpty() || username.contains(" ")) {
                et_username.error = "Invalid username"
            } else if (!password.equals(repassword)) {
                et_re_password.error = "Password doesnot match username"
            }

            var obj = JsonObject()
            obj.addProperty("name", username )
            obj.addProperty("firstName", fname)
            obj.addProperty("lastName", lname)
            obj.addProperty("middleName", mname)
            obj.addProperty("password", password )
            obj.addProperty("repeatPassword", repassword )
            obj.addProperty("isUserAdmin", false)
            obj.addProperty("joinDate",Date().getTime())
            obj.addProperty("email", email)
            obj.addProperty("planetCode", user.phoneNumber)
            obj.addProperty("parentCode", user.parentCode)
            obj.addProperty("language", language)
            obj.addProperty("level", level)
            obj.addProperty("phoneNumber", phoneNumber)
            obj.addProperty("birthDate", birthDate)
            obj.addProperty("gender", gender)
            obj.addProperty("type", "user")
            obj.addProperty("betaEnabled", false)
            var roles = JsonArray()
            roles.add("learner")
            obj.add("roles", roles)
            pbar.visibility = View.VISIBLE
            Service(this).becomeMember(mRealm,obj) {res->
              runOnUiThread {
                  pbar.visibility = View.GONE
                  Utilities.toast(this, res)
              }
                finish()
            }
        }
    }
}
