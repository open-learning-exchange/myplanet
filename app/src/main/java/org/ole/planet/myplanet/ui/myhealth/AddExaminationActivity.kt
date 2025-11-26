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
import org.ole.planet.myplanet.utilities.GsonUtils
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
    lateinit var mRealm: Realm
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
        mRealm = databaseService.realmInstance
        userId = intent.getStringExtra("userId")
        pojo = mRealm.where(RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
        if (pojo == null) {
            pojo = mRealm.where(RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()
        }
        user = mRealm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
        if (user != null && (user?.key == null || user?.iv == null)) {
            if (!mRealm.isInTransaction) mRealm.beginTransaction()
            user?.key = generateKey()
            user?.iv = generateIv()
            mRealm.commitTransaction()
        }
        if (pojo != null && !TextUtils.isEmpty(pojo?.data)) {
            health = GsonUtils.gson.fromJson(decrypt(pojo?.data, user?.key, user?.iv), RealmMyHealth::class.java)
        }
        if (health == null) {
            initHealth()
        }
        initExamination()
        validateFields()
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

    private fun initExamination() {
        if (intent.hasExtra("id")) {
            examination = mRealm.where(RealmMyHealthPojo::class.java).equalTo("_id", intent.getStringExtra("id")).findFirst()!!
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

    private fun initHealth() {
        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        health = RealmMyHealth()
        val profile = RealmMyHealthProfile()
        health?.lastExamination = Date().time
        health?.userKey = generateKey()
        health?.profile = profile
        mRealm.commitTransaction()
    }

    private fun saveData() {
        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        createPojo()
        if (examination == null) {
            val userId = generateIv()
            examination = mRealm.createObject(RealmMyHealthPojo::class.java, userId)
            examination?.userId = userId
        }
        examination?.profileId = health?.userKey
        examination?.creatorId = health?.userKey
        examination?.gender = user?.gender
        examination?.age = user?.dob?.let { getAge(it) }!!
        examination?.isSelfExamination = currentUser?._id == pojo?._id
        examination?.date = Date().time
        examination?.planetCode = user?.planetCode
        val sign = RealmExamination()
        sign.allergies = "${binding.etAllergies.text}".trim { it <= ' ' }
        sign.createdBy = currentUser?._id
        examination?.bp = "${binding.etBloodpressure.text}".trim { it <= ' ' }
        examination?.setTemperature(getFloat("${binding.etTemperature.text}".trim { it <= ' ' }))
        examination?.pulse = getInt("${binding.etPulseRate.text}".trim { it <= ' ' })
        examination?.setWeight(getFloat("${binding.etWeight.text}".trim { it <= ' ' }))
        examination?.height = getFloat("${binding.etHeight.text}".trim { it <= ' ' })
        otherConditions
        examination?.conditions = GsonUtils.gson.toJson(mapConditions)
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
            examination?.data = encrypt(GsonUtils.gson.toJson(sign), key, iv)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mRealm.commitTransaction()
        Utilities.toast(this, getString(R.string.added_successfully))
        super.finish()
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

    private fun createPojo() {
        try {
            if (pojo == null) {
                pojo = mRealm.createObject(RealmMyHealthPojo::class.java, userId)
                pojo?.userId = user?._id
            }
            health?.lastExamination = Date().time
            val userKey = user?.key
            val userIv = user?.iv
            if (userKey != null && userIv != null) {
                pojo?.data = encrypt(GsonUtils.gson.toJson(health), userKey, userIv)
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
        if (this::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
        }
        binding.etBloodpressure.removeTextChangedListener(textWatcher)
        textWatcher = null
        super.onDestroy()
    }
}
