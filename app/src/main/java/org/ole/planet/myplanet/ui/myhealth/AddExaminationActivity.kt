package org.ole.planet.myplanet.ui.myhealth

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
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import io.realm.Realm
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityAddExaminationBinding
import org.ole.planet.myplanet.model.RealmExamination
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole/planet.myplanet.utilities.AndroidDecrypter.Companion.decrypt
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.encrypt
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.DimenUtils.dpToPx
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.GsonUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getBoolean
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.TimeUtils.getAge
import org.ole.planet.myplanet.utilities.Utilities

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.repository.ExaminationRepository

@AndroidEntryPoint
class AddExaminationActivity : AppCompatActivity(), CompoundButton.OnCheckedChangeListener {
    @Inject
    lateinit var examinationRepository: ExaminationRepository
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    private lateinit var binding: ActivityAddExaminationBinding
    var userId: String? = null
    var user: RealmUserModel? = null
    private var currentUser: RealmUserModel? = null
    private var pojo: RealmMyHealthPojo? = null
    var health: RealmMyHealth? = null
    private var customDiag: MutableSet<String?>? = null
    private var mapConditions: HashMap<String?, Boolean>? = null
    var allowSubmission = true
    private lateinit var config: ChipCloudConfig
    private var examination: RealmMyHealthPojo? = null
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
        currentUser = userProfileDbHandler.userModel
        mapConditions = HashMap()
        userId = intent.getStringExtra("userId")

        lifecycleScope.launch {
            val (loadedPojo, loadedUser) = examinationRepository.getHealthAndUser(userId ?: "")
            pojo = loadedPojo
            user = loadedUser
            health = examinationRepository.getDecryptedHealth(pojo, user)
            initExamination()
        }

        validateFields()
        findViewById<View>(R.id.btn_save).setOnClickListener {
            if (!allowSubmission) {
                scrollToView(binding.etBloodpressure)
            }
            if (isValidInput && allowSubmission) {
                saveData()
            }
        }
    }

    private fun initExamination() {
        if (intent.hasExtra("id")) {
            lifecycleScope.launch {
                examination = examinationRepository.getExamination(intent.getStringExtra("id")!!)
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
            val conditions = GsonUtils.gson.fromJson(examination?.conditions, JsonObject::class.java)
            for (s in conditions.keySet()) {
                if (!mainList.contains(s) && getBoolean(s, conditions)) {
                    chipCloud.addChip(s)
                    chipCloud.setDeleteListener { _: Int, s1: String? ->
                        customDiag?.remove(Constants.LABELS[s1])
                    }
                    customDiag?.add(s)
                }
            }
        }
    }

    private fun showCheckbox(examination: RealmMyHealthPojo?) {
        val arr = resources.getStringArray(R.array.diagnosis_list)
        binding.containerCheckbox.removeAllViews()
        for (s in arr) {
            val c = CheckBox(this)
            c.buttonTintList = ContextCompat.getColorStateList(this, R.color.daynight_textColor)
            c.setTextColor(ContextCompat.getColor(this, R.color.daynight_textColor))

            if (examination != null) {
                val conditions = GsonUtils.gson.fromJson(examination.conditions, JsonObject::class.java)
                c.isChecked = getBoolean(s, conditions)
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
        val sign = RealmExamination().apply {
            allergies = "${binding.etAllergies.text}".trim()
            createdBy = currentUser?._id
            immunizations = "${binding.etImmunization.text}".trim()
            tests = "${binding.etLabtest.text}".trim()
            xrays = "${binding.etXray.text}".trim()
            treatments = "${binding.etTreatments.text}".trim()
            referrals = "${binding.etReferrals.text}".trim()
            notes = "${binding.etObservation.text}".trim()
            diagnosis = "${binding.etDiag.text}".trim()
            medications = "${binding.etMedications.text}".trim()
        }
        otherConditions

        lifecycleScope.launch {
            val success = examinationRepository.saveExamination(
                examination,
                health,
                user,
                currentUser,
                sign,
                mapConditions ?: emptyMap(),
                getFloat("${binding.etTemperature.text}".trim()),
                getInt("${binding.etPulseRate.text}".trim()),
                getFloat("${binding.etHeight.text}".trim()),
                getFloat("${binding.etWeight.text}".trim()),
                "${binding.etVision.text}".trim(),
                "${binding.etHearing.text}".trim(),
                "${binding.etBloodpressure.text}".trim(),
                hasInfo
            )
            if (success) {
                Utilities.toast(this@AddExaminationActivity, getString(R.string.added_successfully))
                super.finish()
            } else {
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
