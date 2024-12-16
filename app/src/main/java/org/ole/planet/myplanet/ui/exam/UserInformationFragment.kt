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
import org.ole.planet.myplanet.databinding.FragmentUserInformationBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Calendar
import java.util.Locale

class UserInformationFragment : BaseDialogFragment(), View.OnClickListener {
    private lateinit var fragmentUserInformationBinding: FragmentUserInformationBinding
    var dob: String? = ""
    lateinit var mRealm: Realm
    private var submissions: RealmSubmission? = null
    var userModel: RealmUserModel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentUserInformationBinding = FragmentUserInformationBinding.inflate(inflater, container, false)
        mRealm = DatabaseService(requireActivity()).realmInstance
        userModel = UserProfileDbHandler(requireContext()).userModel
        if (!TextUtils.isEmpty(id)) {
            submissions = mRealm.where(RealmSubmission::class.java).equalTo("id", id).findFirst()
        }
        initViews()
        return fragmentUserInformationBinding.root
    }

    private fun initViews() {
        val langArray = resources.getStringArray(R.array.language).toMutableList()
        val levelArray = resources.getStringArray(R.array.level).toMutableList()
        val adapterLang = ArrayAdapter(requireContext(), R.layout.spinner_item_white, langArray)
        val adapterLevel = ArrayAdapter(requireContext(), R.layout.spinner_item_white, levelArray)
        adapterLang.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        adapterLevel.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fragmentUserInformationBinding.spnLang.adapter = adapterLang
        fragmentUserInformationBinding.spnLevel.adapter = adapterLevel
        fragmentUserInformationBinding.spnLang.post {
            val selectedView = fragmentUserInformationBinding.spnLang.selectedView as? TextView
            selectedView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.daynight_textColor))
        }
        fragmentUserInformationBinding.spnLevel.post {
            val selectedView = fragmentUserInformationBinding.spnLevel.selectedView as? TextView
            selectedView?.setTextColor(ContextCompat.getColor(requireContext(), R.color.daynight_textColor))
        }
        fragmentUserInformationBinding.etEmail.setText(getString(R.string.message_placeholder, userModel?.email))
        fragmentUserInformationBinding.etFname.setText(getString(R.string.message_placeholder, userModel?.firstName))
        fragmentUserInformationBinding.etLname.setText(getString(R.string.message_placeholder, userModel?.lastName))
        fragmentUserInformationBinding.etPhone.setText(getString(R.string.message_placeholder, userModel?.phoneNumber))
        fragmentUserInformationBinding.txtDob.text = getString(R.string.message_placeholder, userModel?.dob)
        dob = userModel?.dob
        fragmentUserInformationBinding.btnCancel.setOnClickListener(this)
        fragmentUserInformationBinding.btnSubmit.setOnClickListener(this)
        fragmentUserInformationBinding.txtDob.setOnClickListener(this)
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btn_cancel -> if (isAdded) {
                dialog?.dismiss()
            }
            R.id.btn_submit -> submitForm()
            R.id.txt_dob -> showDatePickerDialog()
        }
    }

    private fun submitForm() {
        val fname = "${fragmentUserInformationBinding.etFname.text}".trim { it <= ' ' }
        val lname = "${fragmentUserInformationBinding.etLname.text}".trim { it <= ' ' }
        val mName = "${fragmentUserInformationBinding.etMname.text}".trim { it <= ' ' }
        val phone = "${fragmentUserInformationBinding.etPhone.text}".trim { it <= ' ' }
        val email = "${fragmentUserInformationBinding.etEmail.text}".trim { it <= ' ' }
        var gender = ""
        val rbSelected = requireView().findViewById<RadioButton>(fragmentUserInformationBinding.rbGender.checkedRadioButtonId)
        if (rbSelected != null) {
            gender = rbSelected.text.toString()
        }
        val level = "${fragmentUserInformationBinding.spnLevel.selectedItem}"
        val lang = "${fragmentUserInformationBinding.spnLang.selectedItem}"
        if (TextUtils.isEmpty(id)) {
            val userId = userModel?.id
            val finalGender = gender
            mRealm.executeTransactionAsync({ realm: Realm ->
                val model = realm.where(RealmUserModel::class.java).equalTo("id", userId).findFirst()
                if (model != null) {
                    if (!TextUtils.isEmpty(fname)) model.firstName = fname
                    if (!TextUtils.isEmpty(lname)) model.lastName = lname
                    if (!TextUtils.isEmpty(email)) model.email = email
                    if (!TextUtils.isEmpty(lang)) model.language = lang
                    if (!TextUtils.isEmpty(phone)) model.phoneNumber = phone
                    if (!TextUtils.isEmpty(dob)) model.birthPlace = dob
                    if (!TextUtils.isEmpty(level)) model.level = level
                    if (!TextUtils.isEmpty(finalGender)) model.gender = finalGender
                    model.isUpdated = true
                }
            }, {
                Utilities.toast(MainApplication.context, getString(R.string.user_profile_updated))
                if (isAdded) {
                    dialog?.dismiss()
                }
            }) {
                Utilities.toast(MainApplication.context, getString(R.string.unable_to_update_user))
                if (isAdded) {
                    dialog?.dismiss()
                }
            }
        } else {
            val user = JsonObject()
            user.addProperty("name", "$fname $lname")
            user.addProperty("firstName", fname)
            user.addProperty("middleName", mName)
            user.addProperty("lastName", lname)
            user.addProperty("email", email)
            user.addProperty("language", lang)
            user.addProperty("phoneNumber", phone)
            user.addProperty("birthDate", dob)
            user.addProperty("gender", gender)
            user.addProperty("level", level)
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
        Utilities.toast(activity, getString(R.string.thank_you_for_taking_this_survey))
        BaseExamFragment.navigateToSurveyList(requireActivity())
    }

    private fun showDatePickerDialog() {
        val now = Calendar.getInstance()
        val dpd = DatePickerDialog(requireActivity(), { _, i, i1, i2 ->
            dob = String.format(Locale.US, "%04d-%02d-%02d", i, i1 + 1, i2)
            fragmentUserInformationBinding.txtDob.text = dob
        }, now[Calendar.YEAR], now[Calendar.MONTH], now[Calendar.DAY_OF_MONTH])
        dpd.show()
    }

    override val key: String
        get() = "sub_id"

    companion object {
        fun getInstance(id: String?): UserInformationFragment {
            val f = UserInformationFragment()
            setArgs(f, id)
            return f
        }

        private fun setArgs(f: UserInformationFragment, id: String?) {
            val b = Bundle()
            b.putString("sub_id", id)
            f.arguments = b
        }
    }
}
