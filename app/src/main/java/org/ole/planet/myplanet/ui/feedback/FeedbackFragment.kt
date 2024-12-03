package org.ole.planet.myplanet.ui.feedback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentFeedbackBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date
import java.util.UUID

class FeedbackFragment : DialogFragment(), View.OnClickListener {
    private lateinit var fragmentFeedbackBinding: FragmentFeedbackBinding
    private lateinit var mRealm: Realm
    private lateinit var databaseService: DatabaseService
    private var model: RealmUserModel ?= null
    var user: String? = ""

    interface OnFeedbackSubmittedListener {
        fun onFeedbackSubmitted()
    }

    private var mListener: OnFeedbackSubmittedListener? = null
    fun setOnFeedbackSubmittedListener(listener: OnFeedbackSubmittedListener?) {
        mListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.AppTheme_Dialog_NoActionBar_MinWidth)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentFeedbackBinding = FragmentFeedbackBinding.inflate(inflater, container, false)
        databaseService = DatabaseService()
        mRealm = databaseService.realmInstance
        model = UserProfileDbHandler(requireContext()).userModel
        user = model?.name
        fragmentFeedbackBinding.btnSubmit.setOnClickListener(this)
        fragmentFeedbackBinding.btnCancel.setOnClickListener(this)
        return fragmentFeedbackBinding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
        }
    }

    override fun onClick(view: View) {
        if (view.id == R.id.btn_submit) {
            clearError()
            validateAndSaveData()
        } else if (view.id == R.id.btn_cancel) {
            dismiss()
        }
    }

    private fun validateAndSaveData() {
        val message = fragmentFeedbackBinding.etMessage.text.toString().trim { it <= ' ' }
        if (message.isEmpty()) {
            fragmentFeedbackBinding.tlMessage.error = getString(R.string.please_enter_feedback)
            return
        }
        val rbUrgent = requireView().findViewById<RadioButton>(fragmentFeedbackBinding.rgUrgent.checkedRadioButtonId)
        val rbType = requireView().findViewById<RadioButton>(fragmentFeedbackBinding.rgType.checkedRadioButtonId)
        if (rbUrgent == null) {
            fragmentFeedbackBinding.tlUrgent.error = getString(R.string.feedback_priority_is_required)
            return
        }
        if (rbType == null) {
            fragmentFeedbackBinding.tlType.error = getString(R.string.feedback_type_is_required)
            return
        }
        val urgent = rbUrgent.text.toString()
        val type = rbType.text.toString()
        val arguments = arguments
        if (arguments != null) {
            val argumentArray = getArgumentArray(message)
            mRealm.executeTransactionAsync(Realm.Transaction { realm: Realm ->
                saveData(realm, urgent, type, argumentArray)
            }, Realm.Transaction.OnSuccess {
                Utilities.toast(activity, R.string.feedback_saved.toString())
            })
        } else mRealm.executeTransactionAsync(Realm.Transaction { realm: Realm ->
            saveData(realm, urgent, type, message)
        }, Realm.Transaction.OnSuccess {
            Utilities.toast(activity, R.string.feedback_saved.toString())
        })
        Toast.makeText(activity, R.string.thank_you_your_feedback_has_been_submitted, Toast.LENGTH_SHORT).show()
        if (mListener != null) {
            mListener?.onFeedbackSubmitted()
        }
    }

    private fun getArgumentArray(message: String?): Array<String?> {
        val argumentArray = arrayOfNulls<String>(3)
        argumentArray[0] = message
        argumentArray[1] = requireArguments().getString("item")
        argumentArray[2] = requireArguments().getString("state")
        return argumentArray
    }

    private fun clearError() {
        fragmentFeedbackBinding.tlUrgent.error = ""
        fragmentFeedbackBinding.tlType.error = ""
        fragmentFeedbackBinding.tlMessage.error = ""
    }

    private fun saveData(realm: Realm, urgent: String, type: String, message: String) {
        val feedback = realm.createObject(RealmFeedback::class.java, UUID.randomUUID().toString())
        feedback.title = "Question regarding /"
        feedback.openTime = Date().time
        feedback.url = "/"
        feedback.owner = user
        feedback.source = user
        feedback.status = "Open"
        feedback.priority = urgent
        feedback.type = type
        feedback.parentCode = "dev"
        val `object` = JsonObject()
        `object`.addProperty("message", message)
        `object`.addProperty("time", Date().time.toString() + "")
        `object`.addProperty("user", user + "")
        val msgArray = JsonArray()
        msgArray.add(`object`)
        feedback.setMessages(msgArray)
        dismiss()
    }

    private fun saveData(realm: Realm, urgent: String, type: String, argumentArray: Array<String?>) {
        val feedback = realm.createObject(RealmFeedback::class.java, UUID.randomUUID().toString())
        feedback.title = "Question regarding /" + argumentArray[2]
        feedback.openTime = Date().time
        feedback.url = "/" + argumentArray[2]
        feedback.owner = user
        feedback.source = user
        feedback.status = "Open"
        feedback.priority = urgent
        feedback.type = type
        feedback.parentCode = "dev"
        feedback.state = argumentArray[2]
        feedback.item = argumentArray[1]
        val `object` = JsonObject()
        `object`.addProperty("message", argumentArray[0])
        `object`.addProperty("time", Date().time.toString() + "")
        `object`.addProperty("user", user + "")
        val msgArray = JsonArray()
        msgArray.add(`object`)
        feedback.setMessages(msgArray)
        dismiss()
    }
}
