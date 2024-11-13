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
import com.google.gson.Gson
import com.google.gson.JsonObject
import fisk.chipcloud.ChipCloud
import fisk.chipcloud.ChipCloudConfig
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityAddExaminationBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyHealth
import org.ole.planet.myplanet.model.RealmMyHealth.RealmMyHealthProfile
import org.ole.planet.myplanet.model.RealmMyHealthPojo
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.decrypt
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.encrypt
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateIv
import org.ole.planet.myplanet.utilities.AndroidDecrypter.Companion.generateKey
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.DimenUtils.dpToPx
import org.ole.planet.myplanet.utilities.JsonUtils.getBoolean
import org.ole.planet.myplanet.utilities.JsonUtils.getString
import org.ole.planet.myplanet.utilities.TimeUtils.getAge
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date
import java.util.Locale

class AddExaminationActivity : AppCompatActivity(), CompoundButton.OnCheckedChangeListener {
    private lateinit var activityAddExaminationBinding: ActivityAddExaminationBinding
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
    private fun initViews() {
        config = Utilities.getCloudConfig().selectMode(ChipCloud.SelectMode.close)
        activityAddExaminationBinding.btnAddDiag.setOnClickListener {
            customDiag?.add("${activityAddExaminationBinding.etOtherDiag.text}")
            activityAddExaminationBinding.etOtherDiag.setText(R.string.empty_text)
            showOtherDiagnosis()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityAddExaminationBinding = ActivityAddExaminationBinding.inflate(layoutInflater)
        setContentView(activityAddExaminationBinding.root)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        customDiag = HashSet()
        initViews()
        currentUser = UserProfileDbHandler(this).userModel
        mapConditions = HashMap()
        mRealm = DatabaseService().realmInstance
        userId = intent.getStringExtra("userId")
        pojo = mRealm.where(RealmMyHealthPojo::class.java).equalTo("_id", userId).findFirst()
        if (pojo == null) {
            pojo = mRealm.where(RealmMyHealthPojo::class.java).equalTo("userId", userId).findFirst()
        }
        user = mRealm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
        if (pojo != null && !TextUtils.isEmpty(pojo?.data)) {
            health = Gson().fromJson(decrypt(pojo?.data, user?.key, user?.iv), RealmMyHealth::class.java)
        }
        if (health == null) {
            initHealth()
        }
        initExamination()
        validateFields()
        findViewById<View>(R.id.btn_save).setOnClickListener {
            if (!isValidInput || !allowSubmission) {
                Utilities.toast(this, getString(R.string.invalid_input))
                return@setOnClickListener
            }
            saveData()
        }
    }

    private fun initExamination() {
        if (intent.hasExtra("id")) {
            examination = mRealm.where(RealmMyHealthPojo::class.java).equalTo("_id", intent.getStringExtra("id")).findFirst()!!
            activityAddExaminationBinding.etTemperature.setText(getString(R.string.number_placeholder, examination?.temperature))
            activityAddExaminationBinding.etPulseRate.setText(getString(R.string.number_placeholder, examination?.pulse))
            activityAddExaminationBinding.etBloodpressure.setText(getString(R.string.message_placeholder, examination?.bp))
            activityAddExaminationBinding.etHeight.setText(getString(R.string.number_placeholder, examination?.height))
            activityAddExaminationBinding.etWeight.setText(getString(R.string.number_placeholder, examination?.weight))
            activityAddExaminationBinding.etVision.setText(examination?.vision)
            activityAddExaminationBinding.etHearing.setText(examination?.hearing)
            val encrypted = user?.let { examination?.getEncryptedDataAsJson(it) }
            activityAddExaminationBinding.etObservation.setText(getString(getString(R.string.note_), encrypted))
            activityAddExaminationBinding.etDiag.setText(getString(getString(R.string.diagno), encrypted))
            activityAddExaminationBinding.etTreatments.setText(getString(getString(R.string.treat), encrypted))
            activityAddExaminationBinding.etMedications.setText(getString(getString(R.string.medicay), encrypted))
            activityAddExaminationBinding.etImmunization.setText(getString(getString(R.string.immunizations), encrypted))
            activityAddExaminationBinding.etAllergies.setText(getString(getString(R.string.allergy), encrypted))
            activityAddExaminationBinding.etXray.setText(getString(getString(R.string.xrays), encrypted))
            activityAddExaminationBinding.etLabtest.setText(getString(getString(R.string.tests), encrypted))
            activityAddExaminationBinding.etReferrals.setText(getString(getString(R.string.referral), encrypted))
        }
        showCheckbox(examination)
        showOtherDiagnosis()
    }

    private fun validateFields() {
        allowSubmission = true
        activityAddExaminationBinding.etBloodpressure.addTextChangedListener(object :
            TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (!"${activityAddExaminationBinding.etBloodpressure.text}".contains("/")) {
                    activityAddExaminationBinding.etBloodpressure.error = getString(R.string.blood_pressure_should_be_numeric_systolic_diastolic)
                    allowSubmission = false
                } else {
                    val sysDia = "${activityAddExaminationBinding.etBloodpressure.text}"
                        .trim { it <= ' ' }
                        .split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (sysDia.size > 2 || sysDia.isEmpty()) {
                        activityAddExaminationBinding.etBloodpressure.error = getString(R.string.blood_pressure_should_be_systolic_diastolic)
                        allowSubmission = false
                    } else {
                        try {
                            val sys = sysDia[0].toInt()
                            val dis = sysDia[1].toInt()
                            if (sys < 60 || dis < 40 || sys > 300 || dis > 200) {
                                activityAddExaminationBinding.etBloodpressure.error = getString(R.string.bp_must_be_between_60_40_and_300_200)
                                allowSubmission = false
                            } else {
                                allowSubmission = true
                            }
                        } catch (e: Exception) {
                            activityAddExaminationBinding.etBloodpressure.error = getString(R.string.systolic_and_diastolic_must_be_numbers)
                            allowSubmission = false
                        }
                    }
                }
            }
        })
    }

    private fun showOtherDiagnosis() {
        activityAddExaminationBinding.containerOtherDiagnosis.removeAllViews()
        val chipCloud = ChipCloud(this, activityAddExaminationBinding.containerOtherDiagnosis, config)
        for (s in customDiag?: emptySet()) {
            chipCloud.addChip(s)
        }
        chipCloud.setDeleteListener { _: Int, s1: String? -> customDiag?.remove(s1) }
        preloadCustomDiagnosis(chipCloud)
    }

    private fun preloadCustomDiagnosis(chipCloud: ChipCloud) {
        val arr = resources.getStringArray(R.array.diagnosis_list)
        val mainList = listOf(*arr)
        if (customDiag?.isEmpty() == true && examination != null) {
            val conditions = Gson().fromJson(examination?.conditions, JsonObject::class.java)
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
        activityAddExaminationBinding.containerCheckbox.removeAllViews()
        for (s in arr) {
            val c = CheckBox(this)
            c.buttonTintList = ContextCompat.getColorStateList(this, R.color.daynight_textColor)
            c.setTextColor(ContextCompat.getColor(this, R.color.daynight_textColor))

            if (examination != null) {
                val conditions = Gson().fromJson(examination.conditions, JsonObject::class.java)
                c.isChecked = getBoolean(s, conditions)
            }
            c.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            c.text = s
            c.tag = s
            c.setOnCheckedChangeListener(this)
            activityAddExaminationBinding.containerCheckbox.addView(c)
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
        sign.allergies = "${activityAddExaminationBinding.etAllergies.text}".trim { it <= ' ' }
        sign.createdBy = currentUser?._id
        examination?.bp = "${activityAddExaminationBinding.etBloodpressure.text}".trim { it <= ' ' }
        examination?.setTemperature(getFloat("${activityAddExaminationBinding.etTemperature.text}".trim { it <= ' ' }))
        examination?.pulse = getInt("${activityAddExaminationBinding.etPulseRate.text}".trim { it <= ' ' })
        examination?.setWeight(getFloat("${activityAddExaminationBinding.etWeight.text}".trim { it <= ' ' }))
        examination?.height = getFloat("${activityAddExaminationBinding.etHeight.text}".trim { it <= ' ' })
        otherConditions
        examination?.conditions = Gson().toJson(mapConditions)
        examination?.hearing = "${activityAddExaminationBinding.etHearing.text}".trim { it <= ' ' }
        sign.immunizations = "${activityAddExaminationBinding.etImmunization.text}".trim { it <= ' ' }
        sign.tests = "${activityAddExaminationBinding.etLabtest.text}".trim { it <= ' ' }
        sign.xrays = "${activityAddExaminationBinding.etXray.text}".trim { it <= ' ' }
        examination?.vision = "${activityAddExaminationBinding.etVision.text}".trim { it <= ' ' }
        sign.treatments = "${activityAddExaminationBinding.etTreatments.text}".trim { it <= ' ' }
        sign.referrals = "${activityAddExaminationBinding.etReferrals.text}".trim { it <= ' ' }
        sign.notes = "${activityAddExaminationBinding.etObservation.text}".trim { it <= ' ' }
        sign.diagnosis = "${activityAddExaminationBinding.etDiag.text}".trim { it <= ' ' }
        sign.medications = "${activityAddExaminationBinding.etMedications.text}".trim { it <= ' ' }
        examination?.date = Date().time
        examination?.isUpdated = true
        examination?.isHasInfo = hasInfo
        pojo?.isUpdated = true
        try {
            examination?.data = encrypt(Gson().toJson(sign), user?.key!!, user?.iv!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mRealm.commitTransaction()
        Utilities.toast(this, getString(R.string.added_successfully))
        super.finish()
    }

    private val hasInfo: Boolean
        get() = !TextUtils.isEmpty("${activityAddExaminationBinding.etAllergies.text}") ||
                !TextUtils.isEmpty("${activityAddExaminationBinding.etDiag.text}") ||
                !TextUtils.isEmpty("${activityAddExaminationBinding.etImmunization.text}") ||
                !TextUtils.isEmpty("${activityAddExaminationBinding.etMedications.text}") ||
                !TextUtils.isEmpty("${activityAddExaminationBinding.etObservation.text}") ||
                !TextUtils.isEmpty("${activityAddExaminationBinding.etReferrals.text}") ||
                !TextUtils.isEmpty("${activityAddExaminationBinding.etLabtest.text}") ||
                !TextUtils.isEmpty("${activityAddExaminationBinding.etTreatments.text}") ||
                !TextUtils.isEmpty("${activityAddExaminationBinding.etXray.text}")
    private val isValidInput: Boolean
        get() {
            val isValidTemp = getFloat("${activityAddExaminationBinding.etTemperature.text}".trim { it <= ' ' }) in 30.0..40.0 ||
                        getFloat("${activityAddExaminationBinding.etTemperature.text}".trim { it <= ' ' }) == 0f
            val isValidPulse = getInt("${activityAddExaminationBinding.etPulseRate.text}".trim { it <= ' ' }) in 40..120 ||
                    getFloat("${activityAddExaminationBinding.etPulseRate.text}".trim { it <= ' ' }) == 0f
            val isValidHeight = getFloat("${activityAddExaminationBinding.etHeight.text}".trim { it <= ' ' }) in 1.0..250.0 ||
                    getFloat("${activityAddExaminationBinding.etHeight.text}".trim { it <= ' ' }) == 0f
            val isValidWeight = getFloat("${activityAddExaminationBinding.etWeight.text}".trim { it <= ' ' }) in 1.0..150.0 ||
                    getFloat("${activityAddExaminationBinding.etWeight.text}".trim { it <= ' ' }) == 0f
            if (!isValidTemp) {
                activityAddExaminationBinding.etTemperature.error = getString(R.string.invalid_input_must_be_between_30_and_40)
            }
            if (!isValidPulse) {
                activityAddExaminationBinding.etPulseRate.error = getString(R.string.invalid_input_must_be_between_40_and_120)
            }
            if (!isValidHeight) {
                activityAddExaminationBinding.etHeight.error = getString(R.string.invalid_input_must_be_between_1_and_250)
            }
            if (!isValidWeight) {
                activityAddExaminationBinding.etWeight.error = getString(R.string.invalid_input_must_be_between_1_and_150)
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
            pojo?.data = encrypt(Gson().toJson(health), user?.key, user?.iv)
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
        alertDialogBuilder.setMessage(R.string.are_you_sure_you_want_to_exit_your_data_will_be_lost)
        alertDialogBuilder.setPositiveButton(getString(R.string.yes_i_want_to_exit)) { _: DialogInterface?, _: Int -> super.finish() }
            .setNegativeButton(getString(R.string.cancel), null)
        alertDialogBuilder.show()
    }

    override fun onCheckedChanged(compoundButton: CompoundButton, b: Boolean) {
        val text = "${compoundButton.text}".trim { it <= ' ' }
        mapConditions?.set(text, b)
    }
}
