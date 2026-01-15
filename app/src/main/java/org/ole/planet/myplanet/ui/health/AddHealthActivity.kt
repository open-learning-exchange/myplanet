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
import org.ole.planet.myplanet.databinding.ActivityAddHealthBinding
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealth.RealmMyHealthProfile
import org.ole.planet.myplanet.repository.HealthData
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.repository.UserHealthUpdates
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class AddHealthActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddHealthBinding
    @Inject
    lateinit var healthRepository: HealthRepository
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
        val health = RealmMyHealthProfile()
        health.emergencyContactName = "${binding.etEmergency.editText?.text}".trim { ch -> ch <= ' ' }
        health.emergencyContact = "${binding.etContact.editText?.text}".trim { ch -> ch <= ' ' }
        health.emergencyContactType = "${binding.spnContactType.selectedItem}".trim { ch -> ch <= ' ' }
        health.specialNeeds = "${binding.etSpecialNeed.editText?.text}".trim { ch -> ch <= ' ' }
        health.notes = "${binding.etOtherNeed.editText?.text}".trim { ch -> ch <= ' ' }
        if (myHealth == null) {
            myHealth = RealmMyHealth()
        }
        myHealth?.profile = health

        val userUpdates = UserHealthUpdates(
            firstName = "${binding.etFname.editText?.text}".trim { ch -> ch <= ' ' },
            middleName = "${binding.etMname.editText?.text}".trim { ch -> ch <= ' ' },
            lastName = "${binding.etLname.editText?.text}".trim { ch -> ch <= ' ' },
            email = "${binding.etEmail.editText?.text}".trim { ch -> ch <= ' ' },
            dob = "${binding.etBirthdateLayout.editText?.text}".trim { ch -> ch <= ' ' },
            birthPlace = "${binding.etBirthplace.editText?.text}".trim { ch -> ch <= ' ' },
            phoneNumber = "${binding.etPhone.editText?.text}".trim { ch -> ch <= ' ' }
        )
        lifecycleScope.launch {
            userId?.let {
                healthRepository.saveHealthData(it, health, userUpdates)
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
            userId?.let {
                val healthData = healthRepository.getHealthDataForUser(it)
                progressBar.visibility = View.GONE
                myHealth = healthData?.myHealth
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
                binding.etFname.editText?.setText(healthData?.firstName)
                binding.etMname.editText?.setText(healthData?.middleName)
                binding.etLname.editText?.setText(healthData?.lastName)
                binding.etEmail.editText?.setText(healthData?.email)
                binding.etPhone.editText?.setText(healthData?.phoneNumber)
                binding.etBirthdateLayout.editText?.setText(TimeUtils.formatDateToDDMMYYYY(healthData?.dob))
                binding.etBirthplace.editText?.setText(healthData?.birthPlace)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

}
