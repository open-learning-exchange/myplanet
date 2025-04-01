package org.ole.planet.myplanet.ui.exam

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseDialogFragment
import org.ole.planet.myplanet.callback.OnHomeItemClickListener
import org.ole.planet.myplanet.databinding.FragmentUserInformationBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.team.TeamDetailFragment
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Calendar
import java.util.Locale
import androidx.core.view.isVisible

class UserInformationFragment : BaseDialogFragment(), View.OnClickListener {
    private lateinit var fragmentUserInformationBinding: FragmentUserInformationBinding
    var dob: String? = ""
    lateinit var mRealm: Realm
    private var submissions: RealmSubmission? = null
    var userModel: RealmUserModel? = null
    var shouldHideElements: Boolean? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentUserInformationBinding = FragmentUserInformationBinding.inflate(inflater, container, false)
        mRealm = DatabaseService(requireActivity()).realmInstance
        userModel = UserProfileDbHandler(requireContext()).userModel
        if (!TextUtils.isEmpty(id)) {
            submissions = mRealm.where(RealmSubmission::class.java).equalTo("id", id).findFirst()
        }
        shouldHideElements = arguments?.getBoolean("shouldHideElements") == true
        initViews()
        return fragmentUserInformationBinding.root
    }

    private fun initViews() {
        if (shouldHideElements == true) {
            fragmentUserInformationBinding.btnAdditionalFields.visibility = View.VISIBLE
            fragmentUserInformationBinding.btnAdditionalFields.setOnClickListener(this)
            fragmentUserInformationBinding.ltAge.visibility = View.VISIBLE
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
            fragmentUserInformationBinding.etAge.setText("")
            fragmentUserInformationBinding.etAge.error = null
        }

        fragmentUserInformationBinding.btnAdditionalFields.text = if (isAdditionalFieldsVisible) getString(R.string.show_additional_fields) else getString(R.string.hide_additional_fields)
        fragmentUserInformationBinding.llNames.visibility = if (isAdditionalFieldsVisible) View.GONE else View.VISIBLE
        fragmentUserInformationBinding.llEmailLang.visibility = if (isAdditionalFieldsVisible) View.GONE else View.VISIBLE
        fragmentUserInformationBinding.llPhoneDob.visibility = if (isAdditionalFieldsVisible) View.GONE else View.VISIBLE
        fragmentUserInformationBinding.llLevel.visibility = if (isAdditionalFieldsVisible) View.GONE else View.VISIBLE
        fragmentUserInformationBinding.ltAge.visibility = if (isAdditionalFieldsVisible) View.VISIBLE else View.GONE
    }

    private fun submitForm() {
        var fname = ""
        var lname = ""
        var mName = ""
        var age = ""

        if (fragmentUserInformationBinding.llNames.isVisible) {
            fname = fragmentUserInformationBinding.etFname.text.toString().trim()
            lname = fragmentUserInformationBinding.etLname.text.toString().trim()
            mName = fragmentUserInformationBinding.etMname.text.toString().trim()
        }

        val user = JsonObject()

        if (fragmentUserInformationBinding.ltAge.isVisible) {
            age = fragmentUserInformationBinding.etAge.text.toString().trim()

            if (age.isNotEmpty()) {
                val ageInt = age.toIntOrNull()
                if (ageInt == null) {
                    fragmentUserInformationBinding.etAge.error = getString(R.string.please_enter_a_valid_age)
                    return
                } else if (ageInt > 100) {
                    fragmentUserInformationBinding.etAge.error = getString(R.string.age_must_be_100_or_below)
                    return
                } else {
                    user.addProperty("age", age)
                }
            }
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

        if (TextUtils.isEmpty(id)) {
            val userId = userModel?.id
            mRealm.executeTransactionAsync({ realm ->
                val model = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
                if (model != null) {
                    user.keySet().forEach { key ->
                        when (key) {
                            "firstName" -> model.firstName = user.get(key).asString
                            "lastName" -> model.lastName = user.get(key).asString
                            "middleName" -> model.middleName = user.get(key).asString
                            "email" -> model.email = user.get(key).asString
                            "language" -> model.language = user.get(key).asString
                            "phoneNumber" -> model.phoneNumber = user.get(key).asString
                            "birthDate" -> model.birthPlace = user.get(key).asString
                            "level" -> model.level = user.get(key).asString
                            "gender" -> model.gender = user.get(key).asString
                            "age" -> model.age = user.get(key).asString
                        }
                    }
                    model.isUpdated = true
                }
            }, {
                Utilities.toast(MainApplication.context, getString(R.string.user_profile_updated))
                if (isAdded) dialog?.dismiss()
            }) {
                Utilities.toast(MainApplication.context, getString(R.string.unable_to_update_user))
                if (isAdded) dialog?.dismiss()
            }
        } else {
            saveSubmission(user)
        }
    }

    private fun saveSubmission(user: JsonObject) {
        if (!mRealm.isInTransaction) mRealm.beginTransaction()
        submissions?.user = user.toString()
        submissions?.status = "complete"
        mRealm.commitTransaction()
        if (isAdded) {
            dialog?.dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (teamId == null) {
            Utilities.toast(activity, getString(R.string.thank_you_for_taking_this_survey))
            BaseExamFragment.navigateToSurveyList(requireActivity())
        } else if (teamId == "") {
            return
        } else {
            Utilities.toast(activity, getString(R.string.thank_you_for_taking_this_survey))
            if (context is OnHomeItemClickListener) {
                val f = TeamDetailFragment()
                val b = Bundle()
                b.putString("id", teamId)
                b.putBoolean("isMyTeam", true)
                b.putInt("navigateToPage", 6)
                f.arguments = b
                (context as OnHomeItemClickListener).openCallFragment(f)
            }
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
