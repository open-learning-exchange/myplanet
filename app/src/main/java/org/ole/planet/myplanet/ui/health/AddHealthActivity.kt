package org.ole.planet.myplanet.ui.health

import android.app.DatePickerDialog
import android.os.Bundle
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
import org.ole.planet.myplanet.databinding.ActivityAddHealthBinding
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils
import org.ole.planet.myplanet.utils.TimeUtils
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class AddHealthActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddHealthBinding
    @Inject
    lateinit var userRepository: UserRepository
    var userId: String? = null
    private var myHealth: RealmMyHealth? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddHealthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdgeWithKeyboard(this, binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        userId = intent.getStringExtra("userId")
        findViewById<View>(R.id.btn_submit).setOnClickListener {
            createMyHealth()
            Utilities.toast(this@AddHealthActivity, getString(R.string.my_health_saved_successfully))
        }

        val contactTypes = resources.getStringArray(R.array.contact_type)
        val contactAdapter = ArrayAdapter(this, R.layout.become_a_member_spinner_layout, contactTypes)
        findViewById<Spinner>(R.id.spn_contact_type).adapter = contactAdapter

        initViews()
        val datePickerClickListener = View.OnClickListener {
            val now = Calendar.getInstance()
            val dpd = DatePickerDialog(this, { _, year, month, dayOfMonth ->
                val selectedDate = String.format(Locale.US, "%02d-%02d-%04d", dayOfMonth, month + 1, year)
                binding.etBirthdateLayout.editText?.setText(selectedDate)
            }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
            dpd.datePicker.maxDate = System.currentTimeMillis()
            dpd.show()
        }
        binding.etBirthdateLayout.editText?.setOnClickListener(datePickerClickListener)
        findViewById<ImageView>(R.id.iv_date_picker).setOnClickListener(datePickerClickListener)
    }

    private fun createMyHealth() {
        val fname = "${binding.etFname.editText?.text}".trim { ch -> ch <= ' ' }
        val mname = "${binding.etMname.editText?.text}".trim { ch -> ch <= ' ' }
        val lname = "${binding.etLname.editText?.text}".trim { ch -> ch <= ' ' }
        val email = "${binding.etEmail.editText?.text}".trim { ch -> ch <= ' ' }
        val dob = "${binding.etBirthdateLayout.editText?.text}".trim { ch -> ch <= ' ' }
        val birthPlace = "${binding.etBirthplace.editText?.text}".trim { ch -> ch <= ' ' }
        val phone = "${binding.etPhone.editText?.text}".trim { ch -> ch <= ' ' }
        val emergencyName = "${binding.etEmergency.editText?.text}".trim { ch -> ch <= ' ' }
        val emergencyContact = "${binding.etContact.editText?.text}".trim { ch -> ch <= ' ' }
        val emergencyType = "${binding.spnContactType.selectedItem}".trim { ch -> ch <= ' ' }
        val specialNeeds = "${binding.etSpecialNeed.editText?.text}".trim { ch -> ch <= ' ' }
        val otherNeed = "${binding.etOtherNeed.editText?.text}".trim { ch -> ch <= ' ' }

        val userData = mapOf(
            "firstName" to fname,
            "middleName" to mname,
            "lastName" to lname,
            "email" to email,
            "dob" to dob,
            "birthPlace" to birthPlace,
            "phoneNumber" to phone,
            "emergencyContactName" to emergencyName,
            "emergencyContact" to emergencyContact,
            "emergencyContactType" to emergencyType,
            "specialNeeds" to specialNeeds,
            "notes" to otherNeed
        )

        lifecycleScope.launch {
            userId?.let { userRepository.updateUserHealthProfile(it, userData) }
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
            val userModel = userId?.let { userRepository.getUserById(it) }
            val decodedHealth = userId?.let { userRepository.getHealthProfile(it) }

            val healthData = HealthData(
                decodedHealth,
                userModel?.firstName,
                userModel?.middleName,
                userModel?.lastName,
                userModel?.email,
                userModel?.phoneNumber,
                userModel?.dob,
                userModel?.birthPlace
            )

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
            binding.etBirthdateLayout.editText?.setText(TimeUtils.formatDateToDDMMYYYY(healthData.dob))
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
