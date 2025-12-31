package org.ole.planet.myplanet.ui.health

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
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.data.DatabaseService
import org.ole.planet.myplanet.databinding.ActivityAddMyHealthBinding
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealth.RealmMyHealthProfile
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.decrypt
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.encrypt
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.JsonUtils
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
                val oldProfile = myHealth?.profile
                val health = RealmMyHealthProfile()
                userModel?.firstName = "${binding.etFname.editText?.text}".trim { ch -> ch <= ' ' }
                userModel?.middleName = "${binding.etMname.editText?.text}".trim { ch -> ch <= ' ' }
                userModel?.lastName = "${binding.etLname.editText?.text}".trim { ch -> ch <= ' ' }
                userModel?.email = "${binding.etEmail.editText?.text}".trim { ch -> ch <= ' ' }
                userModel?.dob = "${binding.etBirthdateLayout.editText?.text}".trim { ch -> ch <= ' ' }
                userModel?.birthPlace = "${binding.etBirthplace.editText?.text}".trim { ch -> ch <= ' ' }
                userModel?.phoneNumber = "${binding.etPhone.editText?.text}".trim { ch -> ch <= ' ' }
                health.emergencyContactName = "${binding.etEmergency.editText?.text}".trim { ch -> ch <= ' ' }
                val emergencyContact = "${binding.etContact.editText?.text}".trim { ch -> ch <= ' ' }
                health.emergencyContact = if (TextUtils.isEmpty(emergencyContact)) {
                    oldProfile?.emergencyContact ?: ""
                } else {
                    emergencyContact
                }
                val emergencyContactType = "${binding.spnContactType.selectedItem}".trim { ch -> ch <= ' ' }
                health.emergencyContactType = if (TextUtils.isEmpty(emergencyContactType)) {
                    oldProfile?.emergencyContactType ?: ""
                } else {
                    emergencyContactType
                }
                health.specialNeeds = "${binding.etSpecialNeed.editText?.text}".trim { ch -> ch <= ' ' }
                health.notes = "${binding.etOtherNeed.editText?.text}".trim { ch -> ch <= ' ' }
                if (myHealth == null) {
                    myHealth = RealmMyHealth()
                }
                if (TextUtils.isEmpty(myHealth?.userKey)) {
                    myHealth?.userKey = generateKey()
                }
                myHealth?.profile = health
                var healthPojo = realm.where(RealmHealthExamination::class.java).equalTo("_id", userId).findFirst()
                if (healthPojo == null) {
                    healthPojo = realm.where(RealmHealthExamination::class.java).equalTo("userId", userId).findFirst()
                }
                if (healthPojo == null) {
                    healthPojo = realm.createObject(RealmHealthExamination::class.java, userId)
                }
                healthPojo.isUpdated = true
                healthPojo.userId = userModel?._id
                try {
                    val key = userModel?.key ?: generateKey().also { newKey -> userModel?.key = newKey }
                    val iv = userModel?.iv ?: generateIv().also { newIv -> userModel?.iv = newIv }
                    healthPojo.data = encrypt(JsonUtils.gson.toJson(myHealth), key, iv)
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
        val progressBar = findViewById<View>(R.id.progressBar)
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val healthData = databaseService.withRealmAsync { realm ->
                val userModel = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
                val healthPojo = realm.where(RealmHealthExamination::class.java).equalTo("_id", userId).findFirst()
                    ?: realm.where(RealmHealthExamination::class.java).equalTo("userId", userId).findFirst()

                var decodedHealth: RealmMyHealth? = null
                if (healthPojo != null && !TextUtils.isEmpty(healthPojo.data)) {
                    try {
                        decodedHealth = JsonUtils.gson.fromJson(
                            decrypt(healthPojo.data, userModel?.key, userModel?.iv),
                            RealmMyHealth::class.java
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                HealthData(
                    decodedHealth,
                    userModel?.firstName,
                    userModel?.middleName,
                    userModel?.lastName,
                    userModel?.email,
                    userModel?.phoneNumber,
                    userModel?.dob,
                    userModel?.birthPlace
                )
            }

            progressBar.visibility = View.GONE
            myHealth = healthData.myHealth
            val health = myHealth?.profile

            binding.etEmergency.editText?.setText(health?.emergencyContactName)
            binding.etContact.editText?.setText(health?.emergencyContact)
            val contactTypes = resources.getStringArray(R.array.contact_type)
            val contactType = health?.emergencyContactType
            if (!contactType.isNullOrEmpty()) {
                val index = contactTypes.indexOf(contactType)
                if (index >= 0) {
                    binding.spnContactType.setSelection(index)
                }
            }
            binding.etSpecialNeed.editText?.setText(health?.specialNeeds)
            binding.etOtherNeed.editText?.setText(health?.notes)

            binding.etFname.editText?.setText(healthData.firstName)
            binding.etMname.editText?.setText(healthData.middleName)
            binding.etLname.editText?.setText(healthData.lastName)
            binding.etEmail.editText?.setText(healthData.email)
            binding.etPhone.editText?.setText(healthData.phoneNumber)
            binding.etBirthdateLayout.editText?.setText(healthData.dob)
            binding.etBirthplace.editText?.setText(healthData.birthPlace)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

    data class HealthData(
        val myHealth: RealmMyHealth?,
        val firstName: String?,
        val middleName: String?,
        val lastName: String?,
        val email: String?,
        val phoneNumber: String?,
        val dob: String?,
        val birthPlace: String?
    )
}
