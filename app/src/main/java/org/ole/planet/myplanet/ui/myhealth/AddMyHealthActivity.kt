package org.ole.planet.myplanet.ui.myhealth

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityAddMyHealthBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealth.RealmMyHealthProfile
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.decrypt
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.encrypt
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class AddMyHealthActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddMyHealthBinding
    @Inject
    lateinit var databaseService: DatabaseService
    var userId: String? = null
    private var myHealth: RealmMyHealth? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddMyHealthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdgeWithKeyboard(this, binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        userId = intent.getStringExtra("userId")
        findViewById<View>(R.id.btn_submit).setOnClickListener {
            createMyHealth()
            Utilities.toast(this@AddMyHealthActivity, getString(R.string.my_health_saved_successfully))
        }

        val contactTypes = resources.getStringArray(R.array.contact_type)
        val contactAdapter = ArrayAdapter(this, R.layout.become_a_member_spinner_layout, contactTypes)
        findViewById<Spinner>(R.id.spn_contact_type).adapter = contactAdapter

        initViews()
        val datePickerClickListener = View.OnClickListener {
            val now = Calendar.getInstance()
            val dpd = DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                binding.etBirthdateLayout.editText?.setText(selectedDate)
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
            dpd.datePicker.maxDate = System.currentTimeMillis()
            dpd.show()
        }
        binding.etBirthdateLayout.editText?.setOnClickListener(datePickerClickListener)
        findViewById<ImageView>(R.id.iv_date_picker).setOnClickListener(datePickerClickListener)
    }

    private fun createMyHealth() {
        lifecycleScope.launch {
            databaseService.executeTransactionAsync { realm ->
                val userModel = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
                val health = RealmMyHealthProfile()
                userModel?.firstName = "${binding.etFname.editText?.text}".trim { ch -> ch <= ' ' }
                userModel?.middleName = "${binding.etMname.editText?.text}".trim { ch -> ch <= ' ' }
                userModel?.lastName = "${binding.etLname.editText?.text}".trim { ch -> ch <= ' ' }
                userModel?.email = "${binding.etEmail.editText?.text}".trim { ch -> ch <= ' ' }
                userModel?.dob = "${binding.etBirthdateLayout.editText?.text}".trim { ch -> ch <= ' ' }
                userModel?.birthPlace = "${binding.etBirthplace.editText?.text}".trim { ch -> ch <= ' ' }
                userModel?.phoneNumber = "${binding.etPhone.editText?.text}".trim { ch -> ch <= ' ' }
                health.emergencyContactName = "${binding.etEmergency.editText?.text}".trim { ch -> ch <= ' ' }
                health.emergencyContact = "${binding.etContact.editText?.text}".trim { ch -> ch <= ' ' }
                health.emergencyContactType = "${binding.spnContactType.selectedItem}"
                health.specialNeeds = "${binding.etSpecialNeed.editText?.text}".trim { ch -> ch <= ' ' }
                health.notes = "${binding.etOtherNeed.editText?.text}".trim { ch -> ch <= ' ' }
                if (myHealth == null) {
                    myHealth = RealmMyHealth()
                }
                if (TextUtils.isEmpty(myHealth?.userKey)) {
                    myHealth?.userKey = generateKey()
                }
                myHealth?.profile = health
                var healthPojo = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
                if (healthPojo == null) {
                    healthPojo = realm.where(RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()
                }
                if (healthPojo == null) {
                    healthPojo = realm.createObject(RealmMyHealthPojo::class.java, userId)
                }
                healthPojo.isUpdated = true
                healthPojo.userId = userModel?._id
                try {
                    val key = userModel?.key ?: generateKey().also { newKey -> userModel?.key = newKey }
                    val iv = userModel?.iv ?: generateIv().also { newIv -> userModel?.iv = newIv }
                    healthPojo.data = encrypt(Gson().toJson(myHealth), key, iv)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            finish()
        }
    }

    private fun initViews() {
        populate()
    }

    private fun populate() {
        databaseService.withRealm { realm ->
            val userModel = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
            val healthPojo = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
                ?: realm.where(RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()
            if (healthPojo != null && !TextUtils.isEmpty(healthPojo.data)) {
                myHealth = Gson().fromJson(
                    decrypt(healthPojo.data, userModel?.key, userModel?.iv),
                    RealmMyHealth::class.java
                )
                val health = myHealth?.profile
                binding.etEmergency.editText?.setText(health?.emergencyContactName)
                binding.etContact.editText?.setText(health?.emergencyContact)
                val contactTypes = resources.getStringArray(R.array.contact_type)
                val contactTypeIndex = contactTypes.indexOf(health?.emergencyContactType)
                if (contactTypeIndex >= 0) {
                    binding.spnContactType.setSelection(contactTypeIndex)
                }
                binding.etSpecialNeed.editText?.setText(health?.specialNeeds)
                binding.etOtherNeed.editText?.setText(health?.notes)
            }
            if (userModel != null) {
                binding.etFname.editText?.setText(userModel.firstName)
                binding.etMname.editText?.setText(userModel.middleName)
                binding.etLname.editText?.setText(userModel.lastName)
                binding.etEmail.editText?.setText(userModel.email)
                binding.etPhone.editText?.setText(userModel.phoneNumber)
                binding.etBirthdateLayout.editText?.setText(userModel.dob)
                binding.etBirthplace.editText?.setText(userModel.birthPlace)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}
