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
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityAddExaminationBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealth.RealmMyHealthProfile
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.myhealth.RealmExamination
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.decrypt
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.encrypt
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.DimenUtils.dpToPx
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.ole.planet.myplanet.utilities.JsonUtils.getBoolean
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.TimeUtils.getAge
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class AddExaminationActivity : AppCompatActivity(), CompoundButton.OnCheckedChangeListener {
    @Inject
    lateinit var databaseService: DatabaseService
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
    private val gson = Gson()
    private data class InitializationData(
        val user: RealmUserModel?,
        val pojo: RealmMyHealthPojo?,
        val examination: RealmMyHealthPojo?,
        val health: RealmMyHealth?,
    )
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
        validateFields()
        binding.btnSave.setOnClickListener {
            if(!allowSubmission){
                scrollToView(binding.etBloodpressure)
            }
            if (!isValidInput || !allowSubmission) {
                return@setOnClickListener
            }
            saveData()
        }
        startInitialization()
    }

    private fun startInitialization() {
        setLoadingState(true)
        lifecycleScope.launch {
            val localUserId = userId
            if (localUserId.isNullOrBlank()) {
                setLoadingState(false)
                Utilities.toast(this@AddExaminationActivity, getString(R.string.unable_to_add_health_record))
                super.finish()
                return@launch
            }
            try {
                val initData = loadInitialData(localUserId, intent.getStringExtra("id"))
                user = initData.user
                pojo = initData.pojo
                health = initData.health ?: createHealth()
                examination = initData.examination
                if (health?.userKey.isNullOrEmpty()) {
                    health?.userKey = generateKey()
                }
                initExamination()
            } catch (e: Exception) {
                e.printStackTrace()
                Utilities.toast(this@AddExaminationActivity, getString(R.string.unable_to_add_health_record))
            } finally {
                setLoadingState(false)
            }
        }
    }

    private suspend fun loadInitialData(userId: String, examinationId: String?): InitializationData {
        return withContext(Dispatchers.IO) {
            var generatedKey: String? = null
            var generatedIv: String? = null
            val result = databaseService.withRealm { realm ->
                val realmUser = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
                if (realmUser != null) {
                    if (realmUser.key.isNullOrEmpty()) {
                        generatedKey = generateKey()
                    }
                    if (realmUser.iv.isNullOrEmpty()) {
                        generatedIv = generateIv()
                    }
                }
                val userCopy = realmUser?.let { realm.copyFromRealm(it) }?.apply {
                    if (key.isNullOrEmpty() && generatedKey != null) {
                        key = generatedKey
                    }
                    if (iv.isNullOrEmpty() && generatedIv != null) {
                        iv = generatedIv
                    }
                }
                val pojoById = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
                val pojoByUser = realm.where(RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()
                val pojoCopy = (pojoById ?: pojoByUser)?.let { realm.copyFromRealm(it) }
                val examCopy = examinationId?.let {
                    realm.where(RealmMyHealthPojo::class.java).equalTo("_id", it).findFirst()?.let { exam ->
                        realm.copyFromRealm(exam)
                    }
                }
                val healthCopy = if (!pojoCopy?.data.isNullOrBlank()) {
                    val key = userCopy?.key ?: generatedKey
                    val iv = userCopy?.iv ?: generatedIv
                    if (!key.isNullOrBlank() && !iv.isNullOrBlank()) {
                        runCatching {
                            gson.fromJson(decrypt(pojoCopy?.data, key, iv), RealmMyHealth::class.java)
                        }.getOrNull()
                    } else {
                        null
                    }
                } else {
                    null
                }
                InitializationData(userCopy, pojoCopy, examCopy, healthCopy)
            }
            if (generatedKey != null || generatedIv != null) {
                databaseService.executeTransactionAsync { realm ->
                    realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()?.apply {
                        if (generatedKey != null && key.isNullOrEmpty()) {
                            key = generatedKey
                        }
                        if (generatedIv != null && iv.isNullOrEmpty()) {
                            iv = generatedIv
                        }
                    }
                }
            }
            result
        }
    }

    private fun initExamination() {
        examination?.let { exam ->
            binding.etTemperature.setText(getString(R.string.float_placeholder, exam.temperature))
            binding.etPulseRate.setText(getString(R.string.number_placeholder, exam.pulse))
            binding.etBloodpressure.setText(getString(R.string.message_placeholder, exam.bp))
            binding.etHeight.setText(getString(R.string.float_placeholder, exam.height))
            binding.etWeight.setText(getString(R.string.float_placeholder, exam.weight))
            binding.etVision.setText(exam.vision)
            binding.etHearing.setText(exam.hearing)
            val encrypted = user?.let { exam.getEncryptedDataAsJson(it) }
            binding.etObservation.setText(getString(getString(R.string.note_), encrypted))
            binding.etDiag.setText(getString(getString(R.string.diagno), encrypted))
            binding.etTreatments.setText(getString(getString(R.string.treat), encrypted))
            binding.etMedications.setText(getString(getString(R.string.medicay), encrypted))
            binding.etImmunization.setText(getString(getString(R.string.immunizations), encrypted))
            binding.etAllergies.setText(getString(getString(R.string.allergy), encrypted))
            binding.etXray.setText(getString(getString(R.string.xrays), encrypted))
            binding.etLabtest.setText(getString(getString(R.string.tests), encrypted))
            binding.etReferrals.setText(getString(getString(R.string.referral), encrypted))
        }
        showCheckbox(examination)
        showOtherDiagnosis()
    }

    private fun validateFields() {
        allowSubmission = true
        binding.etBloodpressure.addTextChangedListener(object :
            TextWatcher {
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
        })
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
            val conditions = gson.fromJson(examination?.conditions, JsonObject::class.java)
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
        mapConditions?.clear()
        val conditions = examination?.conditions?.takeIf { !it.isNullOrBlank() }?.let {
            gson.fromJson(it, JsonObject::class.java)
        }
        for (s in arr) {
            val c = CheckBox(this)
            c.buttonTintList = ContextCompat.getColorStateList(this, R.color.daynight_textColor)
            c.setTextColor(ContextCompat.getColor(this, R.color.daynight_textColor))

            val isChecked = conditions?.let { getBoolean(s, it) } ?: false
            if (isChecked) {
                mapConditions?.set(s, true)
            }
            c.isChecked = isChecked
            c.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            c.text = s
            c.tag = s
            c.setOnCheckedChangeListener(this)
            binding.containerCheckbox.addView(c)
        }
    }

    private fun createHealth(): RealmMyHealth {
        return RealmMyHealth().apply {
            profile = RealmMyHealthProfile()
            lastExamination = Date().time
            if (userKey.isNullOrEmpty()) {
                userKey = generateKey()
            }
        }
    }

    private fun saveData() {
        val localUser = user
        val localHealth = health ?: createHealth().also { health = it }
        if (localUser?.key.isNullOrBlank() || localUser.iv.isNullOrBlank()) {
            Utilities.toast(this, getString(R.string.unable_to_add_health_record))
            return
        }

        if (localHealth.userKey.isNullOrEmpty()) {
            localHealth.userKey = generateKey()
        }

        val now = Date().time
        localHealth.lastExamination = now
        val bpValue = "${binding.etBloodpressure.text}".trim { it <= ' ' }
        val temperatureValue = getFloat("${binding.etTemperature.text}".trim { it <= ' ' })
        val pulseValue = getInt("${binding.etPulseRate.text}".trim { it <= ' ' })
        val weightValue = getFloat("${binding.etWeight.text}".trim { it <= ' ' })
        val heightValue = getFloat("${binding.etHeight.text}".trim { it <= ' ' })
        val hearingValue = "${binding.etHearing.text}".trim { it <= ' ' }
        val visionValue = "${binding.etVision.text}".trim { it <= ' ' }
        val allergiesValue = "${binding.etAllergies.text}".trim { it <= ' ' }
        val immunizationsValue = "${binding.etImmunization.text}".trim { it <= ' ' }
        val labTestsValue = "${binding.etLabtest.text}".trim { it <= ' ' }
        val xrayValue = "${binding.etXray.text}".trim { it <= ' ' }
        val referralsValue = "${binding.etReferrals.text}".trim { it <= ' ' }
        val treatmentsValue = "${binding.etTreatments.text}".trim { it <= ' ' }
        val notesValue = "${binding.etObservation.text}".trim { it <= ' ' }
        val diagnosisValue = "${binding.etDiag.text}".trim { it <= ' ' }
        val medicationsValue = "${binding.etMedications.text}".trim { it <= ' ' }
        val conditionsMap = HashMap<String?, Boolean>().apply {
            mapConditions?.let { putAll(it) }
            for (s in customDiag ?: emptySet()) {
                if (!s.isNullOrBlank()) {
                    put(s, true)
                }
            }
        }
        mapConditions = HashMap(conditionsMap)
        val conditionsJson = gson.toJson(conditionsMap)
        val sign = RealmExamination().apply {
            allergies = allergiesValue
            createdBy = currentUser?._id
            immunizations = immunizationsValue
            tests = labTestsValue
            xrays = xrayValue
            treatments = treatmentsValue
            referrals = referralsValue
            notes = notesValue
            diagnosis = diagnosisValue
            medications = medicationsValue
        }
        val key = localUser.key!!
        val iv = localUser.iv!!
        val encryptedSign = runCatching { encrypt(gson.toJson(sign), key, iv) }.getOrElse {
            it.printStackTrace()
            Utilities.toast(this, getString(R.string.unable_to_add_health_record))
            return
        }
        val encryptedHealth = runCatching { encrypt(gson.toJson(localHealth), key, iv) }.getOrElse {
            it.printStackTrace()
            Utilities.toast(this, getString(R.string.unable_to_add_health_record))
            return
        }

        val userAge = localUser.dob?.let { getAge(it) } ?: 0
        val isSelfExam = currentUser?._id == pojo?._id
        val examId = examination?._id ?: generateIv()
        val pojoPrimaryKey = pojo?._id ?: userId ?: examId
        val userModelId = localUser.id ?: localUser._id ?: userId
        val hasInfoValue = hasInfo

        lifecycleScope.launch {
            setLoadingState(true)
            try {
                databaseService.executeTransactionAsync { realm ->
                    val realmUser = localUser.id?.let {
                        realm.where(RealmUserModel::class.java).equalTo("id", it).findFirst()
                    } ?: localUser._id?.let {
                        realm.where(RealmUserModel::class.java).equalTo("id", it).findFirst()
                    }
                    realmUser?.apply {
                        if (key.isNullOrEmpty()) {
                            key = localUser.key
                        }
                        if (iv.isNullOrEmpty()) {
                            iv = localUser.iv
                        }
                    }

                    val realmPojo = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", pojoPrimaryKey).findFirst()
                        ?: userModelId?.let {
                            realm.where(RealmMyHealthPojo::class.java).equalTo("userId", it).findFirst()
                        }
                        ?: realm.createObject(RealmMyHealthPojo::class.java, pojoPrimaryKey).apply {
                            this.userId = userModelId
                        }
                    realmPojo.apply {
                        this.userId = userModelId
                        isUpdated = true
                        data = encryptedHealth
                    }

                    val realmExam = realm.where(RealmMyHealthPojo::class.java).equalTo("_id", examId).findFirst()
                        ?: realm.createObject(RealmMyHealthPojo::class.java, examId).apply {
                            this.userId = userModelId
                        }
                    realmExam.apply {
                        userId = userModelId
                        profileId = localHealth.userKey
                        creatorId = localHealth.userKey
                        gender = localUser.gender
                        age = userAge
                        isSelfExamination = isSelfExam
                        date = now
                        planetCode = localUser.planetCode
                        bp = bpValue
                        setTemperature(temperatureValue)
                        pulse = pulseValue
                        setWeight(weightValue)
                        height = heightValue
                        conditions = conditionsJson
                        hearing = hearingValue
                        vision = visionValue
                        isUpdated = true
                        isHasInfo = hasInfoValue
                        data = encryptedSign
                    }
                }
                Utilities.toast(this@AddExaminationActivity, getString(R.string.added_successfully))
                super.finish()
            } catch (e: Exception) {
                e.printStackTrace()
                Utilities.toast(this@AddExaminationActivity, getString(R.string.unable_to_add_health_record))
            } finally {
                setLoadingState(false)
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.loadingIndicator.isVisible = isLoading
        binding.btnSave.isEnabled = !isLoading
        binding.btnAddDiag.isEnabled = !isLoading
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

}
