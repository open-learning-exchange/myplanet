package org.ole.planet.myplanet.ui.myhealth

import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityAddMyHealthBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealth.RealmMyHealthProfile
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.decrypt
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.encrypt
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utilities.Utilities

class AddMyHealthActivity : AppCompatActivity() {
    private lateinit var activityAddMyHealthBinding: ActivityAddMyHealthBinding
    lateinit var realm: Realm
    private var healthPojo: RealmMyHealthPojo? = null
    private var userModelB: RealmUserModel? = null
    var userId: String? = null
    var key: String? = null
    var iv: String? = null
    var myHealth: RealmMyHealth? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityAddMyHealthBinding = ActivityAddMyHealthBinding.inflate(layoutInflater)
        setContentView(activityAddMyHealthBinding.root)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)
        realm = DatabaseService(this).realmInstance
        userId = intent.getStringExtra("userId")
        healthPojo = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
        if (healthPojo == null) {
            healthPojo = realm.where(RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()
        }
        userModelB = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
        key = userModelB!!.key
        iv = userModelB!!.iv
        findViewById<View>(R.id.btn_submit).setOnClickListener {
            createMyHealth()
            Utilities.toast(this@AddMyHealthActivity, getString(R.string.my_health_saved_successfully))
        }
        initViews()
    }

    private fun createMyHealth() {
        if (!realm.isInTransaction) realm.beginTransaction()
        val health = RealmMyHealthProfile()
        userModelB!!.firstName = activityAddMyHealthBinding.etFname.editText!!.text.toString().trim { it <= ' ' }
        userModelB!!.middleName = activityAddMyHealthBinding.etMname.editText!!.text.toString().trim { it <= ' ' }
        userModelB!!.lastName = activityAddMyHealthBinding.etLname.editText!!.text.toString().trim { it <= ' ' }
        userModelB!!.email = activityAddMyHealthBinding.etEmail.editText!!.text.toString().trim { it <= ' ' }
        userModelB!!.dob = activityAddMyHealthBinding.etBirthdate.editText!!.text.toString().trim { it <= ' ' }
        userModelB!!.birthPlace = activityAddMyHealthBinding.etBirthplace.editText!!.text.toString().trim { it <= ' ' }
        userModelB!!.phoneNumber = activityAddMyHealthBinding.etPhone.editText!!.text.toString().trim { it <= ' ' }
        health.emergencyContactName = activityAddMyHealthBinding.etEmergency.editText!!.text.toString().trim { it <= ' ' }
        health.emergencyContact = activityAddMyHealthBinding.etContact.editText!!.text.toString().trim { it <= ' ' }
        health.emergencyContactType = activityAddMyHealthBinding.spnContactType.selectedItem.toString()
        health.specialNeeds = activityAddMyHealthBinding.etSpecialNeed.editText!!.text.toString().trim { it <= ' ' }
        health.notes = activityAddMyHealthBinding.etOtherNeed.editText!!.text.toString().trim { it <= ' ' }
        if (myHealth == null) {
            myHealth = RealmMyHealth()
        }
        if (TextUtils.isEmpty(myHealth!!.userKey)) {
            myHealth!!.userKey = generateKey()
        }
        myHealth!!.profile = health
        if (healthPojo == null) {
            healthPojo = realm.createObject(RealmMyHealthPojo::class.java, userId)
        }
        healthPojo!!.isUpdated = true
        healthPojo!!.userId = userModelB!!._id
        try {
            healthPojo!!.data = encrypt(Gson().toJson(myHealth), key!!, iv!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        realm.commitTransaction()
        finish()
    }

    private fun initViews() {
        populate()
    }

    private fun populate() {
        if (healthPojo != null && !TextUtils.isEmpty(healthPojo!!.data)) {
            myHealth = Gson().fromJson(
                decrypt(healthPojo!!.data, userModelB!!.key, userModelB!!.iv),
                RealmMyHealth::class.java
            )
            val health = myHealth!!.profile
            activityAddMyHealthBinding.etEmergency.editText!!.setText(health!!.emergencyContactName)
            activityAddMyHealthBinding.etSpecialNeed.editText!!.setText(health.specialNeeds)
            activityAddMyHealthBinding.etOtherNeed.editText!!.setText(health.notes)
        }
        if (userModelB != null) {
            activityAddMyHealthBinding.etFname.editText!!.setText(userModelB!!.firstName)
            activityAddMyHealthBinding.etMname.editText!!.setText(userModelB!!.middleName)
            activityAddMyHealthBinding.etLname.editText!!.setText(userModelB!!.lastName)
            activityAddMyHealthBinding.etEmail.editText!!.setText(userModelB!!.email)
            activityAddMyHealthBinding.etPhone.editText!!.setText(userModelB!!.phoneNumber)
            activityAddMyHealthBinding.etBirthdate.editText!!.setText(userModelB!!.dob)
            activityAddMyHealthBinding.etBirthplace.editText!!.setText(userModelB!!.birthPlace)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::realm.isInitialized && !realm.isClosed) {
            realm.close()
        }
    }
}
