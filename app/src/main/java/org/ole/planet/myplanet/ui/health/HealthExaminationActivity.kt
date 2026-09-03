package org.ole.planet.myplanet.ui.health

import android.content.DialogInterface
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityHealthExaminationBinding
import org.ole.planet.myplanet.model.Examination
import org.ole.planet.myplanet.model.HealthExamination
import org.ole.planet.myplanet.model.MyHealth
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.AndroidDecrypter.Companion.encrypt
import org.ole.planet.myplanet.utils.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utils.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utils.DimenUtils.dpToPx
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils
import org.ole.planet.myplanet.utils.JsonUtils
import org.ole.planet.myplanet.utils.JsonUtils.getString
import org.ole.planet.myplanet.utils.TimeUtils.getAge
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectWhenStarted

@AndroidEntryPoint
class HealthExaminationActivity : AppCompatActivity(), CompoundButton.OnCheckedChangeListener {
    @Inject
    lateinit var userSessionManager: UserSessionManager

    private val viewModel: HealthExaminationViewModel by viewModels()
    private lateinit var binding: ActivityHealthExaminationBinding
    var userId: String? = null
    var user: UserEntity? = null
    private var currentUser: UserEntity? = null
    private var pojo: HealthExamination? = null
    var health: MyHealth? = null
    private var customDiag: MutableSet<String?>? = null
    private var mapConditions: HashMap<String?, Boolean>? = null
    var allowSubmission = true
    private var examination: HealthExamination? = null
    private var conditionsMap: Map<String, Boolean> = emptyMap()
    private fun initViews() {
        binding.btnAddDiag.setOnClickListener {
            val text = binding.etOtherDiag.text.toString().trim()
            if (text.isNotEmpty()) {
                customDiag?.add(text)
                binding.etOtherDiag.setText(R.string.empty_text)
                showOtherDiagnosis()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHealthExaminationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdgeWithKeyboard(this, binding.root, lightStatusBar = false)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        customDiag = HashSet()
        initViews()
        lifecycleScope.launch {
            currentUser = userSessionManager.getUserModel()
        }
        mapConditions = HashMap()
        userId = intent.getStringExtra("userId")
        val btnSave = findViewById<View>(R.id.btn_save)
        btnSave.isEnabled = false
        btnSave.setOnClickListener {
            if(!allowSubmission){
                scrollToView(binding.etBloodpressure)
            }
            if (!isValidInput || !allowSubmission) {
                return@setOnClickListener
            }
            saveData()
        }

        viewModel.loadData(userId, intent.getStringExtra("id"))

        lifecycleScope.launch {
            val state = viewModel.state.first { !it.isLoading }
            user = state.user
            pojo = state.pojo
            health = state.health
            examination = state.examination
            conditionsMap = state.conditionsMap

            initExamination()
            validateFields()
            btnSave.isEnabled = true
        }

        collectWhenStarted(viewModel.isSaving) { isSaving ->
            btnSave.isEnabled = !isSaving
        }

        collectWhenStarted(viewModel.saveResult) { success ->
            if (success) {
                Utilities.toast(this@HealthExaminationActivity, getString(R.string.added_successfully))
                closeActivity()
            } else {
                Utilities.toast(this@HealthExaminationActivity, getString(R.string.unable_to_add_health_record))
            }
        }
    }

    private fun initExamination() {
        if (examination != null) {
            binding.etTemperature.setText(getString(R.string.float_placeholder, examination?.temperature))
            binding.etPulseRate.setText(getString(R.string.number_placeholder, examination?.pulse))
            binding.etBloodpressure.setText(getString(R.string.message_placeholder, examination?.bp))
            binding.etHeight.setText(getString(R.string.float_placeholder, examination?.height))
            binding.etWeight.setText(getString(R.string.float_placeholder, examination?.weight))
            binding.etVision.setText(examination?.vision)
            binding.etHearing.setText(examination?.hearing)
            val encrypted = user?.let { examination?.getEncryptedDataAsJson(it) }
            binding.etObservation.setText(getString(getString(R.string.note_), encrypted))
            binding.etDiag.setText(getString(getString(R.string.diagnosis), encrypted))
            binding.etTreatments.setText(getString(getString(R.string.treatments), encrypted))
            binding.etMedications.setText(getString(getString(R.string.medications), encrypted))
            binding.etImmunization.setText(getString(getString(R.string.immunizations), encrypted))
            binding.etAllergies.setText(getString(getString(R.string.allergies), encrypted))
            binding.etXray.setText(getString(getString(R.string.xrays), encrypted))
            binding.etLabtest.setText(getString(getString(R.string.tests), encrypted))
            binding.etReferrals.setText(getString(getString(R.string.referrals), encrypted))
        }
        showCheckbox(examination)
        showOtherDiagnosis()
    }

    private fun validateFields() {
        allowSubmission = true
        binding.etBloodpressure.doOnTextChanged { _, _, _, _ ->
            if (!"${binding.etBloodpressure.text}".contains("/")) {
                binding.etBloodpressure.error = getString(R.string.blood_pressure_should_be_numeric_systolic_diastolic)
                allowSubmission = false
            } else {
                val sysDia = "${binding.etBloodpressure.text}"
                    .trim { it <= ' ' }
                    .split("/").dropLastWhile { it.isEmpty() }.toTypedArray()
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

    private fun showOtherDiagnosis() {
        binding.containerOtherDiagnosis.removeAllViews()
        preloadCustomDiagnosis()
        val chipContext = ContextThemeWrapper(this, R.style.Theme_App_Chip)
        for (s in customDiag ?: emptySet()) {
            if (s.isNullOrBlank()) {
                continue
            }
            val chip = Chip(chipContext).apply {
                text = s
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    customDiag?.remove(s)
                    showOtherDiagnosis()
                }
            }
            binding.containerOtherDiagnosis.addView(chip)
        }
    }

    private fun preloadCustomDiagnosis() {
        val arr = resources.getStringArray(R.array.diagnosis_list)
        val mainList = listOf(*arr)
        if (customDiag?.isEmpty() == true && examination != null) {
            for ((s, value) in conditionsMap) {
                if (!mainList.contains(s) && value) {
                    customDiag?.add(s)
                }
            }
        }
    }

    private fun showCheckbox(examination: HealthExamination?) {
        val arr = resources.getStringArray(R.array.diagnosis_list)
        binding.containerCheckbox.removeAllViews()
        for (s in arr) {
            val c = CheckBox(this)
            c.buttonTintList = ContextCompat.getColorStateList(this, R.color.daynight_textColor)
            c.setTextColor(ContextCompat.getColor(this, R.color.daynight_textColor))

            if (examination != null) {
                c.isChecked = conditionsMap[s] ?: false
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

    private fun saveData() {
        // Prepare data synchronously (or in a lightweight way)
        try {
            createPojo()
            if (examination == null) {
                val odUserId = generateIv()
                examination = HealthExamination()
                examination?._id = odUserId
                examination?.userId = odUserId
            }
            examination?.profileId = health?.userKey
            examination?.creatorId = health?.userKey
            examination?.gender = user?.gender
            examination?.age = user?.dob?.let { getAge(it) } ?: 0
            examination?.isSelfExamination = currentUser?._id == pojo?._id
            examination?.date = Date().time
            examination?.planetCode = user?.planetCode
            val sign = Examination()
            sign.allergies = "${binding.etAllergies.text}".trim { it <= ' ' }
            sign.createdBy = currentUser?._id
            examination?.bp = "${binding.etBloodpressure.text}".trim { it <= ' ' }
            examination?.setTemperature(getFloat("${binding.etTemperature.text}".trim { it <= ' ' }))
            examination?.pulse = getInt("${binding.etPulseRate.text}".trim { it <= ' ' })
            examination?.setWeight(getFloat("${binding.etWeight.text}".trim { it <= ' ' }))
            examination?.height = getFloat("${binding.etHeight.text}".trim { it <= ' ' })
            otherConditions
            examination?.conditions = JsonUtils.gson.toJson(mapConditions)
            examination?.hearing = "${binding.etHearing.text}".trim { it <= ' ' }
            sign.immunizations = "${binding.etImmunization.text}".trim { it <= ' ' }
            sign.tests = "${binding.etLabtest.text}".trim { it <= ' ' }
            sign.xrays = "${binding.etXray.text}".trim { it <= ' ' }
            examination?.vision = "${binding.etVision.text}".trim { it <= ' ' }
            sign.treatments = "${binding.etTreatments.text}".trim { it <= ' ' }
            sign.referrals = "${binding.etReferrals.text}".trim { it <= ' ' }
            sign.notes = "${binding.etObservation.text}".trim { it <= ' ' }
            sign.diagnosis = "${binding.etDiag.text}".trim { it <= ' ' }
            sign.medications = "${binding.etMedications.text}".trim { it <= ' ' }
            examination?.date = Date().time
            examination?.isUpdated = true
            examination?.isHasInfo = hasInfo
            pojo?.isUpdated = true
            try {
                val key = user?.key ?: generateKey().also { user?.key = it }
                val iv = user?.iv ?: generateIv().also { user?.iv = it }
                examination?.data = encrypt(JsonUtils.gson.toJson(sign), key, iv)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Delegate save to ViewModel
            viewModel.saveExamination(examination, pojo, user)

        } catch (e: Exception) {
            e.printStackTrace()
            Utilities.toast(this@HealthExaminationActivity, getString(R.string.unable_to_add_health_record))
        }
    }

    private fun closeActivity() {
        super.finish()
    }

    private fun scrollToView(view: View) {
        binding.rootScrollView.post {
            binding.rootScrollView.smoothScrollTo(0, view.top)
            view.requestFocus()
        }
    }

    private val hasInfo: Boolean
        get() = "${binding.etAllergies.text}".isNotBlank() ||
                "${binding.etDiag.text}".isNotBlank() ||
                "${binding.etImmunization.text}".isNotBlank() ||
                "${binding.etMedications.text}".isNotBlank() ||
                "${binding.etObservation.text}".isNotBlank() ||
                "${binding.etReferrals.text}".isNotBlank() ||
                "${binding.etLabtest.text}".isNotBlank() ||
                "${binding.etTreatments.text}".isNotBlank() ||
                "${binding.etXray.text}".isNotBlank()
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

    private fun createPojo() {
        try {
            if (pojo == null) {
                pojo = HealthExamination()
                pojo?._id = userId.orEmpty()
                pojo?.userId = user?._id
            }
            health?.lastExamination = Date().time
            val userKey = user?.key
            val userIv = user?.iv
            if (userKey != null && userIv != null) {
                pojo?.data = encrypt(JsonUtils.gson.toJson(health), userKey, userIv)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Utilities.toast(this, getString(R.string.unable_to_add_health_record))
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
        user = null
        currentUser = null
        pojo = null
        health = null
        examination = null
        customDiag = null
        mapConditions = null
        super.onDestroy()
    }
}
