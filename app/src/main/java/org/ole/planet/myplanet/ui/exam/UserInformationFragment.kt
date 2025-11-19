package org.ole.planet.myplanet.ui.exam

import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
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
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.service.UploadManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.navigation.NavigationHelper
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.TimeUtils
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class UserInformationFragment : BaseDialogFragment(), View.OnClickListener {
    private lateinit var fragmentUserInformationBinding: FragmentUserInformationBinding
    var dob: String? = ""
    @Inject
    lateinit var databaseService: DatabaseService
    @Inject
    lateinit var submissionRepository: SubmissionRepository
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var userProfileDbHandler: UserProfileDbHandler
    var userModel: RealmUserModel? = null
    var shouldHideElements: Boolean? = null
    @Inject
    lateinit var uploadManager: UploadManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentUserInformationBinding = FragmentUserInformationBinding.inflate(inflater, container, false)
        userModel = userProfileDbHandler.userModel
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
        var calculatedAge = 0

        if (fragmentUserInformationBinding.llNames.isVisible) {
            fname = "${fragmentUserInformationBinding.etFname.text}".trim()
            lname = "${fragmentUserInformationBinding.etLname.text}".trim()
            mName = "${fragmentUserInformationBinding.etMname.text}".trim()
        }

        val user = JsonObject()

        if (fragmentUserInformationBinding.ltYob.isVisible) {
            yob = "${fragmentUserInformationBinding.etYob.text}".trim()

            if (yob.isEmpty()) {
                fragmentUserInformationBinding.etYob.error =
                    getString(R.string.year_of_birth_cannot_be_empty)
                return
            }

            val yobInt = yob.toIntOrNull()
            if (yobInt == null) {
                fragmentUserInformationBinding.etYob.error =
                    getString(R.string.please_enter_a_valid_year_of_birth)
                return
            }

            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            if (yobInt < 1900 || yobInt > currentYear) {
                fragmentUserInformationBinding.etYob.error =
                    getString(R.string.please_enter_a_valid_year_between_1900_and, currentYear)
                return
            }

            calculatedAge = currentYear - yobInt
        }

        if (fname.isNotEmpty()) user.addProperty("firstName", fname)
        if (mName.isNotEmpty()) user.addProperty("middleName", mName)
        if (lname.isNotEmpty()) user.addProperty("lastName", lname)

        if (fragmentUserInformationBinding.llEmailLang.isVisible) {
            val email = fragmentUserInformationBinding.etEmail.text.toString().trim()
            val lang = fragmentUserInformationBinding.spnLang.selectedItem.toString()
            if (email.isNotEmpty()) user.addProperty("email", email)
            if (lang.isNotEmpty()) user.addProperty("language", lang)
        }

        if (fragmentUserInformationBinding.llPhoneDob.isVisible) {
            val phone = fragmentUserInformationBinding.etPhone.text.toString().trim()
            if (phone.isNotEmpty()) user.addProperty("phoneNumber", phone)

            if (!dob.isNullOrEmpty()) {
                val birthDateISO = TimeUtils.convertToISO8601(dob!!)
                user.addProperty("birthDate", birthDateISO)
            }
        }

        if (yob.isNotEmpty()) user.addProperty("age", calculatedAge.toString())

        if (fragmentUserInformationBinding.llLevel.isVisible) {
            val level = fragmentUserInformationBinding.spnLevel.selectedItem.toString()
            if (level.isNotEmpty()) user.addProperty("level", level)
        }

        if (fragmentUserInformationBinding.rbGender.isVisible) {
            val rbSelected = requireView().findViewById<RadioButton>(fragmentUserInformationBinding.rbGender.checkedRadioButtonId)
            if (rbSelected != null) {
                val gender = rbSelected.tag.toString()
                if (gender.isNotEmpty()) user.addProperty("gender", gender)
            }
        }

        user.addProperty("betaEnabled", false)

        val teamId = arguments?.getString("teamId")

        if (!teamId.isNullOrEmpty()) {
            saveSubmission(user)
        } else if (TextUtils.isEmpty(id)) {
            val userId = userModel?.id
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    userRepository.updateProfileFields(userId, user)
                    Utilities.toast(MainApplication.context, getString(R.string.user_profile_updated))
                    if (isAdded) dialog?.dismiss()
                } catch (_: Exception) {
                    Utilities.toast(MainApplication.context, getString(R.string.unable_to_update_user))
                    if (isAdded) dialog?.dismiss()
                }
            }
        } else {
            saveSubmission(user)
        }
    }

    private fun saveSubmission(user: JsonObject) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val submissionId = id
                if (submissionId.isNullOrEmpty()) {
                    Utilities.toast(
                        MainApplication.context,
                        "Error: Unable to save submission - no ID provided"
                    )
                    if (isAdded) dialog?.dismiss()
                    return@launch
                }

                submissionRepository.markSubmissionComplete(submissionId, user)

                withContext(Dispatchers.Main) {
                    Utilities.toast(
                        MainApplication.context,
                        getString(R.string.thank_you_for_taking_this_survey)
                    )
                    if (isAdded) {
                        dialog?.dismiss()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Utilities.toast(MainApplication.context, "Error saving submission: ${e.message}")
                    if (isAdded) {
                        dialog?.dismiss()
                    }
                }
            }
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
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                uploadSubmissions()
            }
        }
    }

    private suspend fun uploadSubmissions() {
        try {
            withContext(Dispatchers.IO) {
                uploadManager.uploadSubmissions()
            }
            uploadExamResultWrapper()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun uploadExamResultWrapper() {
        try {
            val successListener = object : SuccessListener {
                override fun onSuccess(success: String?) {}
            }
            uploadManager.uploadExamResult(successListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    companion object {
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
}
