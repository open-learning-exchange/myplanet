package org.ole.planet.myplanet.ui.exam

import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseDialogFragment
import org.ole.planet.myplanet.callback.SuccessListener
import org.ole.planet.myplanet.databinding.FragmentUserInformationBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class UserInformationFragment : BaseDialogFragment(), View.OnClickListener {
    private lateinit var fragmentUserInformationBinding: FragmentUserInformationBinding
    var dob: String? = ""

    companion object {
        private const val TAG = "UserInformationFragment"

        fun getInstance(id: String?, teamId: String?, shouldHideElements: Boolean): UserInformationFragment {
            val f = UserInformationFragment()
            setArgs(f, id, teamId, shouldHideElements)
            return f
        }

        private fun setArgs(f: UserInformationFragment, id: String?, teamId: String?, shouldHideElements: Boolean) {
            val b = Bundle()
            b.putString("sub_id", id)
            b.putString("teamId", teamId)
            b.putBoolean("shouldHideElements", shouldHideElements)
            f.arguments = b
        }
    }

    @Inject
    lateinit var databaseService: DatabaseService
    @Inject
    lateinit var submissionRepository: SubmissionRepository
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    private var submission: RealmSubmission? = null
    var userModel: RealmUserModel? = null
    var shouldHideElements: Boolean? = null
    @Inject
    lateinit var uploadManager: UploadManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentUserInformationBinding = FragmentUserInformationBinding.inflate(inflater, container, false)
        userModel = userProfileDbHandler.userModel
        if (!TextUtils.isEmpty(id)) {
            viewLifecycleOwner.lifecycleScope.launch {
                submission = id?.let { submissionRepository.getSubmissionById(it) }
            }
        }
        shouldHideElements = arguments?.getBoolean("shouldHideElements") == true
        initViews()
        return fragmentUserInformationBinding.root
    }

    private fun initViews() {
        if (shouldHideElements == true) {
            fragmentUserInformationBinding.btnAdditionalFields.visibility = View.VISIBLE
            fragmentUserInformationBinding.btnAdditionalFields.setOnClickListener(this)
            fragmentUserInformationBinding.ltYob.visibility = View.VISIBLE
            fragmentUserInformationBinding.llNames.visibility = View.GONE
            fragmentUserInformationBinding.llEmailLang.visibility = View.GONE
            fragmentUserInformationBinding.llPhoneDob.visibility = View.GONE
            fragmentUserInformationBinding.llLevel.visibility = View.GONE
        } else {
            fragmentUserInformationBinding.btnAdditionalFields.visibility = View.GONE
            val langArray = resources.getStringArray(R.array.language)
            val levelArray = resources.getStringArray(R.array.level)
            val adapterLang = ArrayAdapter(requireContext(), R.layout.become_a_member_spinner_layout, langArray)
            val adapterLevel = ArrayAdapter(requireContext(), R.layout.become_a_member_spinner_layout, levelArray)
            adapterLang.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            adapterLevel.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            fragmentUserInformationBinding.spnLang.adapter = adapterLang
            fragmentUserInformationBinding.spnLevel.adapter = adapterLevel
//        fragmentUserInformationBinding.etEmail.setText(getString(R.string.message_placeholder, userModel?.email))
//        fragmentUserInformationBinding.etFname.setText(getString(R.string.message_placeholder, userModel?.firstName))
//        fragmentUserInformationBinding.etLname.setText(getString(R.string.message_placeholder, userModel?.lastName))
//        fragmentUserInformationBinding.etPhone.setText(getString(R.string.message_placeholder, userModel?.phoneNumber))
//        fragmentUserInformationBinding.txtDob.text = getString(R.string.message_placeholder, userModel?.dob)
//        dob = userModel?.dob
        }
        fragmentUserInformationBinding.txtDob.setOnClickListener(this)
        fragmentUserInformationBinding.btnCancel.setOnClickListener(this)
        fragmentUserInformationBinding.btnSubmit.setOnClickListener(this)
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btn_cancel -> if (isAdded) {
                dialog?.dismiss()
            }
            R.id.btn_submit -> submitForm()
            R.id.txt_dob -> showDatePickerDialog()
            R.id.btnAdditionalFields -> toggleAdditionalFields()
        }
    }

    private fun toggleAdditionalFields() {
        val isAdditionalFieldsVisible = fragmentUserInformationBinding.llNames.isVisible
        if (isAdditionalFieldsVisible) {
            fragmentUserInformationBinding.etFname.setText("")
            fragmentUserInformationBinding.etLname.setText("")
            fragmentUserInformationBinding.etMname.setText("")
            fragmentUserInformationBinding.etPhone.setText("")
            fragmentUserInformationBinding.etEmail.setText("")
            fragmentUserInformationBinding.txtDob.text = getString(R.string.birth_date)
        } else {
            fragmentUserInformationBinding.etYob.setText("")
            fragmentUserInformationBinding.etYob.error = null
        }

        fragmentUserInformationBinding.btnAdditionalFields.text = if (isAdditionalFieldsVisible) getString(R.string.show_additional_fields) else getString(R.string.hide_additional_fields)
        fragmentUserInformationBinding.llNames.visibility = if (isAdditionalFieldsVisible) View.GONE else View.VISIBLE
        fragmentUserInformationBinding.llEmailLang.visibility = if (isAdditionalFieldsVisible) View.GONE else View.VISIBLE
        fragmentUserInformationBinding.llPhoneDob.visibility = if (isAdditionalFieldsVisible) View.GONE else View.VISIBLE
        fragmentUserInformationBinding.llLevel.visibility = if (isAdditionalFieldsVisible) View.GONE else View.VISIBLE
        fragmentUserInformationBinding.ltYob.visibility = if (isAdditionalFieldsVisible) View.VISIBLE else View.GONE
    }

    private fun submitForm() {
        var fname = ""
        var lname = ""
        var mName = ""
        var yob = ""

        if (fragmentUserInformationBinding.llNames.isVisible) {
            fname = "${fragmentUserInformationBinding.etFname.text}".trim()
            lname = "${fragmentUserInformationBinding.etLname.text}".trim()
            mName = "${fragmentUserInformationBinding.etMname.text}".trim()
        }

        val user = JsonObject()

        if (fragmentUserInformationBinding.ltYob.isVisible) {
            yob = "${fragmentUserInformationBinding.etYob.text}".trim()
            Log.d(TAG, "submitForm: Collecting Year of Birth (YOB) from input field")
            Log.d(TAG, "submitForm: YOB entered by user: '$yob'")

            if (yob.isEmpty()) {
                Log.w(TAG, "submitForm: YOB validation failed - empty input")
                fragmentUserInformationBinding.etYob.error =
                    getString(R.string.year_of_birth_cannot_be_empty)
                return
            }

            val yobInt = yob.toIntOrNull()
            if (yobInt == null) {
                Log.w(TAG, "submitForm: YOB validation failed - not a valid integer: '$yob'")
                fragmentUserInformationBinding.etYob.error =
                    getString(R.string.please_enter_a_valid_year_of_birth)
                return
            }

            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            if (yobInt < 1900 || yobInt > currentYear) {
                Log.w(TAG, "submitForm: YOB validation failed - out of range: $yobInt (must be 1900-$currentYear)")
                fragmentUserInformationBinding.etYob.error =
                    getString(R.string.please_enter_a_valid_year_between_1900_and, currentYear)
                return
            }

            Log.d(TAG, "submitForm: YOB validation passed - Adding to user data as 'age': '$yob'")
            user.addProperty("age", yob)
            Log.d(TAG, "submitForm: AGE FIELD SET - value='$yob', type=year_of_birth")
        }

        if (fname.isNotEmpty() || lname.isNotEmpty()) {
            user.addProperty("name", "$fname $lname")
        }
        if (fname.isNotEmpty()) user.addProperty("firstName", fname)
        if (lname.isNotEmpty()) user.addProperty("lastName", lname)
        if (mName.isNotEmpty()) user.addProperty("middleName", mName)

        if (fragmentUserInformationBinding.llPhoneDob.isVisible) {
            val phone = fragmentUserInformationBinding.etPhone.text.toString().trim()
            if (phone.isNotEmpty()) user.addProperty("phoneNumber", phone)

            if (!dob.isNullOrEmpty()) user.addProperty("birthDate", dob)
        }

        if (fragmentUserInformationBinding.llEmailLang.isVisible) {
            val email = fragmentUserInformationBinding.etEmail.text.toString().trim()
            val lang = fragmentUserInformationBinding.spnLang.selectedItem.toString()

            if (email.isNotEmpty()) user.addProperty("email", email)
            if (lang.isNotEmpty()) user.addProperty("language", lang)
        }

        if (fragmentUserInformationBinding.llLevel.isVisible) {
            val level = fragmentUserInformationBinding.spnLevel.selectedItem.toString()
            if (level.isNotEmpty()) user.addProperty("level", level)
        }

        if (fragmentUserInformationBinding.rbGender.isVisible) {
            val rbSelected = requireView().findViewById<RadioButton>(fragmentUserInformationBinding.rbGender.checkedRadioButtonId)
            if (rbSelected != null) {
                val gender = rbSelected.tag.toString()
                user.addProperty("gender", gender)
            }
        }

        // Log all collected user information
        Log.d(TAG, "submitForm: USER INFORMATION SUBMITTED:")
        user.keySet().forEach { key ->
            val value = user.get(key)?.asString ?: ""
            Log.d(TAG, "submitForm:   $key: '$value'")
        }
        Log.d(TAG, "submitForm: Submission ID: ${if (TextUtils.isEmpty(id)) "NEW USER PROFILE UPDATE" else id}")

        if (TextUtils.isEmpty(id)) {
            val userId = userModel?.id
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    databaseService.executeTransactionAsync { realm ->
                        val model = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
                        if (model != null) {
                            Log.d(TAG, "submitForm: Updating user profile for userId='$userId'")
                            user.keySet().forEach { key ->
                                val value = user.get(key).asString
                                when (key) {
                                    "firstName" -> model.firstName = value
                                    "lastName" -> model.lastName = value
                                    "middleName" -> model.middleName = value
                                    "email" -> model.email = value
                                    "language" -> model.language = value
                                    "phoneNumber" -> model.phoneNumber = value
                                    "birthDate" -> model.birthPlace = value
                                    "level" -> model.level = value
                                    "gender" -> model.gender = value
                                    "age" -> {
                                        model.age = value
                                        Log.d(TAG, "submitForm: ✓ AGE SAVED TO DATABASE - model.age='$value'")
                                    }
                                }
                            }
                            model.isUpdated = true
                            Log.d(TAG, "submitForm: model.isUpdated set to true - will be uploaded during sync")
                        } else {
                            Log.w(TAG, "submitForm: User model not found for userId='$userId'")
                        }
                    }
                    Log.d(TAG, "submitForm: User profile update successful")
                    Utilities.toast(MainApplication.context, getString(R.string.user_profile_updated))
                    if (isAdded) dialog?.dismiss()
                } catch (e: Exception) {
                    Log.e(TAG, "submitForm: Failed to update user profile", e)
                    Utilities.toast(MainApplication.context, getString(R.string.unable_to_update_user))
                    if (isAdded) dialog?.dismiss()
                }
            }
        } else {
            saveSubmission(user)
        }
    }

    private fun saveSubmission(user: JsonObject) {
        Log.d(TAG, "saveSubmission: Saving user information with submission")

        // Log age specifically if present
        if (user.has("age")) {
            val ageValue = user.get("age").asString
            Log.d(TAG, "saveSubmission: ✓ AGE FIELD IN SUBMISSION - value='$ageValue'")
        } else {
            Log.d(TAG, "saveSubmission: No age field in submission")
        }

        id?.let { submissionId ->
            Log.d(TAG, "saveSubmission: submissionId='$submissionId'")
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val sub = submission ?: submissionRepository.getSubmissionById(submissionId)
                    sub?.let {
                        val userJsonString = user.toString()
                        it.user = userJsonString
                        it.status = "complete"
                        submissionRepository.saveSubmission(it)
                        Log.d(TAG, "saveSubmission: ✓ SUBMISSION SAVED TO DATABASE")
                        Log.d(TAG, "saveSubmission: User data JSON: $userJsonString")
                        Log.d(TAG, "saveSubmission: Submission status set to 'complete' - ready for upload")
                    } ?: run {
                        Log.w(TAG, "saveSubmission: Submission not found for id='$submissionId'")
                    }
                    if (isAdded) {
                        dialog?.dismiss()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "saveSubmission: Failed to save submission", e)
                    if (isAdded) {
                        dialog?.dismiss()
                    }
                }
            }
        } ?: run {
            Log.w(TAG, "saveSubmission: submissionId is null, cannot save")
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        val safeTeamId = arguments?.getString("teamId") ?: ""
        if (safeTeamId == "") {
            return
        } else {
            Utilities.toast(activity, getString(R.string.thank_you_for_taking_this_survey))
            val settings = MainApplication.context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            checkAvailableServer(settings)
            val activity = requireActivity()
            if (activity is AppCompatActivity) {
                NavigationHelper.popBackStack(activity.supportFragmentManager)
            }
        }
    }

    private fun checkAvailableServer(settings: SharedPreferences) {
        val updateUrl = "${settings.getString("serverURL", "")}"
        val serverUrlMapper = ServerUrlMapper()
        val mapping = serverUrlMapper.processUrl(updateUrl)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val primaryAvailable = withTimeoutOrNull(15000) {
                    MainApplication.isServerReachable(mapping.primaryUrl)
                } ?: false
                
                val alternativeAvailable = withTimeoutOrNull(15000) {
                    mapping.alternativeUrl?.let { MainApplication.isServerReachable(it) } == true
                } ?: false

                if (!primaryAvailable && alternativeAvailable) {
                    mapping.alternativeUrl?.let { alternativeUrl ->
                        val uri = updateUrl.toUri()
                        val editor = settings.edit()

                        serverUrlMapper.updateUrlPreferences(editor, uri, alternativeUrl, mapping.primaryUrl, settings)
                    }
                }

                uploadSubmissions()
            } catch (_: Exception) {
                uploadSubmissions()
            }
        }
    }

    private fun uploadSubmissions() {
        MainApplication.applicationScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    uploadManager.uploadSubmissions()
                }

                withContext(Dispatchers.Main) {
                    uploadExamResultWrapper()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun uploadExamResultWrapper() {
        val successListener = object : SuccessListener {
            override fun onSuccess(success: String?) {}
        }

        uploadManager.uploadExamResult(successListener)
    }

    private fun showDatePickerDialog() {
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog(
            requireContext(), { _, i, i1, i2 ->
                dob = String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2)
                fragmentUserInformationBinding.txtDob.text = dob
            }, now[Calendar.YEAR], now[Calendar.MONTH], now[Calendar.DAY_OF_MONTH]
        )
        dpd.setTitle(getString(R.string.select_date_of_birth))
        dpd.datePicker.maxDate = now.timeInMillis
        dpd.show()
    }

    override val key: String
        get() = "sub_id"
}
