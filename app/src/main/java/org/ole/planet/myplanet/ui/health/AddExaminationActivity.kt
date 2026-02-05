package org.ole.planet.myplanet.ui.health

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityAddExaminationBinding
import org.ole.planet.myplanet.model.RealmExamination
import org.ole.planet.myplanet.model.RealmHealthExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealth.RealmMyHealthProfile
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.HealthRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.AndroidDecrypter.Companion.decrypt
import org.ole.planet.myplanet.utils.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utils.Constants
import org.ole.planet.myplanet.utils.DimenUtils.dpToPx
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.TimeUtils.getAge
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class AddExaminationActivity : AppCompatActivity(), CompoundButton.OnCheckedChangeListener {
    @Inject
    lateinit var userSessionManager: UserSessionManager
    @Inject
    lateinit var healthRepository: HealthRepository

    private lateinit var binding: ActivityAddExaminationBinding
    var userId: String? = null
    var user: RealmUser? = null
    private var currentUser: RealmUser? = null
    private var pojo: RealmHealthExamination? = null
    var health: RealmMyHealth? = null
    private var customDiag: MutableSet<String?>? = null
    private var mapConditions: HashMap<String?, Boolean>? = null
    var allowSubmission = true
    private lateinit var config: ChipCloudConfig
    private var examination: RealmHealthExamination? = null
    private var textWatcher: TextWatcher? = null

    private fun initViews() {
        config = Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.close)
        binding.btnAddDiag.setOnClickListener {
            customDiag?.add("${binding.etOtherDiag.text}")
            binding.etOtherDiag.setText(R.string.empty_text)
            showOtherDiagnosis()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddExaminationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdgeWithKeyboard(this, binding.root)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        customDiag = HashSet()
        initViews()
        currentUser = userSessionManager.userModel
        mapConditions = HashMap()
        userId = intent.getStringExtra("userId")
        if (TextUtils.isEmpty(userId)) {
            Utilities.toast(this, "Invalid user ID")
            finish()
            return
        }

        lifecycleScope.launch {
            pojo = healthRepository.getHealthProfile(userId!!)
            user = healthRepository.getUser(userId!!)

            healthRepository.ensureUserEncryptionKeys(userId!!)
            // Refresh user to get keys
            user = healthRepository.getUser(userId!!)

            if (pojo != null && !TextUtils.isEmpty(pojo?.data)) {
                try {
                    health = JsonUtils.gson.fromJson(decrypt(pojo?.data, user?.key, user?.iv), RealmMyHealth::class.java)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (health == null) {
                initHealth()
            }
            initExamination()
            validateFields()
        }

        findViewById<View>(R.id.btn_save).setOnClickListener {
            if(!allowSubmission){
                scrollToView(binding.etBloodpressure)
            }
            if (!isValidInput || !allowSubmission) {
                return@setOnClickListener
            }
            saveData()
        }
    }

    private suspend fun initExamination() {
        if (intent.hasExtra("id")) {
            val id = intent.getStringExtra("id")!!
            examination = healthRepository.getExamination(id)
            binding.etTemperature.setText(getString(R.string.float_placeholder, examination?.temperature))
            binding.etPulseRate.setText(getString(R.string.number_placeholder, examination?.pulse))
            binding.etBloodpressure.setText(getString(R.string.message_placeholder, examination?.bp))
            binding.etHeight.setText(getString(R.string.float_placeholder, examination?.height))
            binding.etWeight.setText(getString(R.string.float_placeholder, examination?.weight))
            binding.etVision.setText(examination?.vision)
            binding.etHearing.setText(examination?.hearing)
            val encrypted = user?.let { examination?.getEncryptedDataAsJson(it) }
            binding.etObservation.setText(JsonUtils.getString("notes", encrypted))
            binding.etDiag.setText(JsonUtils.getString("diagnosis", encrypted))
            binding.etTreatments.setText(JsonUtils.getString("treatments", encrypted))
            binding.etMedications.setText(JsonUtils.getString("medications", encrypted))
            binding.etImmunization.setText(JsonUtils.getString("immunizations", encrypted))
            binding.etAllergies.setText(JsonUtils.getString("allergies", encrypted))
            binding.etXray.setText(JsonUtils.getString("xrays", encrypted))
            binding.etLabtest.setText(JsonUtils.getString("tests", encrypted))
            binding.etReferrals.setText(JsonUtils.getString("referrals", encrypted))
        }
        showCheckbox(examination)
        showOtherDiagnosis()
    }

    private fun validateFields() {
        allowSubmission = true
        textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (!"${binding.etBloodpressure.text}".contains("/")) {
                    binding.etBloodpressure.error = getString(R.string.blood_pressure_should_be_numeric_systolic_diastolic)
                    allowSubmission = false
                } else {
                    val sysDia = "${binding.etBloodpressure.text}"
                        .trim { it <= ' ' }
                        .split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (sysDia.size > 2 || sysDia.isEmpty()) {
                        binding.etBloodpressure.error = getString(R.string.blood_pressure_should_be_systolic_diastolic)
                        allowSubmission = false
                    } else {
                        try {
                            val sys = sysDia[0].toInt()
                            val dis = sysDia[1].toInt()
                            if (sys < 60 || dis < 40 || sys > 300 || dis > 200) {
                                binding.etBloodpressure.error = getString(R.string.bp_must_be_between_60_40_and_300_200)
                                allowSubmission = false
                            } else {
                                allowSubmission = true
                            }
                        } catch (e: Exception) {
                            binding.etBloodpressure.error = getString(R.string.systolic_and_diastolic_must_be_numbers)
                            allowSubmission = false
                        }
                    }
                }
            }
        }
        binding.etBloodpressure.addTextChangedListener(textWatcher)
    }

    private fun showOtherDiagnosis() {
        binding.containerOtherDiagnosis.removeAllViews()
        val chipCloud = ChipCloud(this, binding.containerOtherDiagnosis, config)
        for (s in customDiag?: emptySet()) {
            if (s.isNullOrBlank()) {
                    continue
            } else {
                    chipCloud.addChip(s)
            }
        }
        chipCloud.setDeleteListener { _: Int, s1: String? -> customDiag?.remove(s1) }
        preloadCustomDiagnosis(chipCloud)
    }

    private fun preloadCustomDiagnosis(chipCloud: ChipCloud) {
        val arr = resources.getStringArray(R.array.diagnosis_list)
        val mainList = listOf(*arr)
        if (customDiag?.isEmpty() == true && examination != null) {
            val conditions = JsonUtils.gson.fromJson(examination?.conditions, JsonObject::class.java)
            for (s in conditions.keySet()) {
                if (!mainList.contains(s) && JsonUtils.getBoolean(s, conditions)) {
                    chipCloud.addChip(s)
                    chipCloud.setDeleteListener { _: Int, s1: String? ->
                        customDiag?.remove(Constants.LABELS[s1])
                    }
                    customDiag?.add(s)
                }
            }
        }
    }

    private fun showCheckbox(examination: RealmHealthExamination?) {
        val arr = resources.getStringArray(R.array.diagnosis_list)
        binding.containerCheckbox.removeAllViews()
        for (s in arr) {
            val c = CheckBox(this)
            c.buttonTintList = ContextCompat.getColorStateList(this, R.color.daynight_textColor)
            c.setTextColor(ContextCompat.getColor(this, R.color.daynight_textColor))

            if (examination != null) {
                val conditions = JsonUtils.gson.fromJson(examination.conditions, JsonObject::class.java)
                c.isChecked = JsonUtils.getBoolean(s, conditions)
            }
            c.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            c.text = s
            c.tag = s
            c.setOnCheckedChangeListener(this)
            binding.containerCheckbox.addView(c)
        }
    }

    private val otherConditions: Unit
        get() {
            for (s in customDiag?: emptySet()) {
                mapConditions?.set(s, true)
            }
        }

    private fun initHealth() {
        try {
            health = RealmMyHealth()
            val profile = RealmMyHealthProfile()
            health?.lastExamination = Date().time
            health?.userKey = generateKey()
            health?.profile = profile
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveData() {
        lifecycleScope.launch {
            try {
                if (health == null) initHealth()
                health?.lastExamination = Date().time

                val examFields = RealmHealthExamination()
                examFields.bp = "${binding.etBloodpressure.text}".trim { it <= ' ' }
                examFields.setTemperature(getFloat("${binding.etTemperature.text}".trim { it <= ' ' }))
                examFields.pulse = getInt("${binding.etPulseRate.text}".trim { it <= ' ' })
                examFields.setWeight(getFloat("${binding.etWeight.text}".trim { it <= ' ' }))
                examFields.height = getFloat("${binding.etHeight.text}".trim { it <= ' ' })

                otherConditions
                examFields.conditions = JsonUtils.gson.toJson(mapConditions)

                examFields.hearing = "${binding.etHearing.text}".trim { it <= ' ' }
                examFields.vision = "${binding.etVision.text}".trim { it <= ' ' }
                examFields.isHasInfo = hasInfo
                examFields.age = user?.dob?.let { getAge(it) }!!
                examFields.isSelfExamination = currentUser?._id == pojo?._id // pojo is profile

                val sign = RealmExamination()
                sign.allergies = "${binding.etAllergies.text}".trim { it <= ' ' }
                sign.createdBy = currentUser?._id
                sign.immunizations = "${binding.etImmunization.text}".trim { it <= ' ' }
                sign.tests = "${binding.etLabtest.text}".trim { it <= ' ' }
                sign.xrays = "${binding.etXray.text}".trim { it <= ' ' }
                sign.treatments = "${binding.etTreatments.text}".trim { it <= ' ' }
                sign.referrals = "${binding.etReferrals.text}".trim { it <= ' ' }
                sign.notes = "${binding.etObservation.text}".trim { it <= ' ' }
                sign.diagnosis = "${binding.etDiag.text}".trim { it <= ' ' }
                sign.medications = "${binding.etMedications.text}".trim { it <= ' ' }

                healthRepository.saveExamination(
                    userId!!,
                    intent.getStringExtra("id"),
                    health!!,
                    sign,
                    examFields
                )

                Utilities.toast(this@AddExaminationActivity, getString(R.string.added_successfully))
                super.finish()
            } catch (e: Exception) {
                e.printStackTrace()
                Utilities.toast(this@AddExaminationActivity, getString(R.string.unable_to_add_health_record))
            }
        }
    }

    private fun scrollToView(view: View) {
        binding.rootScrollView.post {
            binding.rootScrollView.smoothScrollTo(0, view.top)
            view.requestFocus()
        }
    }

    private val hasInfo: Boolean
        get() = !TextUtils.isEmpty("${binding.etAllergies.text}") ||
                !TextUtils.isEmpty("${binding.etDiag.text}") ||
                !TextUtils.isEmpty("${binding.etImmunization.text}") ||
                !TextUtils.isEmpty("${binding.etMedications.text}") ||
                !TextUtils.isEmpty("${binding.etObservation.text}") ||
                !TextUtils.isEmpty("${binding.etReferrals.text}") ||
                !TextUtils.isEmpty("${binding.etLabtest.text}") ||
                !TextUtils.isEmpty("${binding.etTreatments.text}") ||
                !TextUtils.isEmpty("${binding.etXray.text}")
    private val isValidInput: Boolean
        get() {
            val scrollView = binding.rootScrollView

            val isValidTemp = (getFloat("${binding.etTemperature.text}".trim { it <= ' ' }) in 30.0..40.0 ||
                        getFloat("${binding.etTemperature.text}".trim { it <= ' ' }) == 0f) &&
                    "${binding.etTemperature.text}".trim { it <= ' ' }.isNotEmpty()
            val isValidPulse = (getInt("${binding.etPulseRate.text}".trim { it <= ' ' }) in 40..120 ||
                    getFloat("${binding.etPulseRate.text}".trim { it <= ' ' }) == 0f) &&
                    "${binding.etPulseRate.text}".trim { it <= ' ' }.isNotEmpty()
            val isValidHeight = (getFloat("${binding.etHeight.text}".trim { it <= ' ' }) in 1.0..250.0 ||
                    getFloat("${binding.etHeight.text}".trim { it <= ' ' }) == 0f) &&
                    "${binding.etHeight.text}".trim { it <= ' ' }.isNotEmpty()
            val isValidWeight = (getFloat("${binding.etWeight.text}".trim { it <= ' ' }) in 1.0..150.0 ||
                    getFloat("${binding.etWeight.text}".trim { it <= ' ' }) == 0f) &&
                    "${binding.etWeight.text}".trim { it <= ' ' }.isNotEmpty()
            if (!isValidTemp) {
                binding.etTemperature.error = getString(R.string.invalid_input_must_be_between_30_and_40)
                scrollToView(binding.etTemperature)
                Utilities.toast(this, getString(R.string.invalid_input_must_be_between_30_and_40))
            }
            if (!isValidPulse) {
                binding.etPulseRate.error = getString(R.string.invalid_input_must_be_between_40_and_120)
                Utilities.toast(this, getString(R.string.invalid_input_must_be_between_40_and_120))
                scrollToView(binding.etPulseRate)
            }
            if (!isValidHeight) {
                binding.etHeight.error = getString(R.string.invalid_input_must_be_between_1_and_250)
                Utilities.toast(this, getString(R.string.invalid_input_must_be_between_1_and_250))
                scrollToView(binding.etHeight)
            }
            if (!isValidWeight) {
                binding.etWeight.error = getString(R.string.invalid_input_must_be_between_1_and_150)
                Utilities.toast(this, getString(R.string.invalid_input_must_be_between_1_and_150))
                scrollToView(binding.etWeight)
            }
            return isValidTemp && isValidHeight && isValidPulse && isValidWeight
        }

    //    private float getFloat(String trim) {
    //    }
    private fun getInt(trim: String): Int {
        return try {
            trim.toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun getFloat(trim: String): Float {
        return try {
            String.format(Locale.getDefault(), "%.1f", trim.toFloat()).toFloat()
        } catch (e: Exception) {
            getInt(trim).toFloat()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun finish() {
        val alertDialogBuilder = AlertDialog.Builder(this, R.style.AlertDialogTheme)
        alertDialogBuilder.setMessage(R.string.cancel_adding_examination)
        alertDialogBuilder.setPositiveButton(getString(R.string.yes_i_want_to_exit)) { _: DialogInterface?, _: Int -> super.finish() }
            .setNegativeButton(getString(R.string.cancel), null)
        alertDialogBuilder.show()
    }

    override fun onCheckedChanged(compoundButton: CompoundButton, b: Boolean) {
        val text = "${compoundButton.text}".trim { it <= ' ' }
        mapConditions?.set(text, b)
    }

    override fun onDestroy() {
        binding.etBloodpressure.removeTextChangedListener(textWatcher)
        textWatcher = null
        super.onDestroy()
    }
}
