package org.ole.planet.myplanet.ui.feedback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentFeedbackBinding
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class FeedbackFragment : DialogFragment(), View.OnClickListener {
    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var feedbackRepository: FeedbackRepository
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
        _binding = FragmentFeedbackBinding.inflate(inflater, container, false)
        model = UserProfileDbHandler(requireContext()).userModel
        user = model?.name
        binding.btnSubmit.setOnClickListener(this)
        binding.btnCancel.setOnClickListener(this)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
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
        val message = binding.etMessage.text.toString().trim { it <= ' ' }
        if (message.isEmpty()) {
            binding.tlMessage.error = getString(R.string.please_enter_feedback)
            return
        }
        val rbUrgent = requireView().findViewById<RadioButton>(binding.rgUrgent.checkedRadioButtonId)
        val rbType = requireView().findViewById<RadioButton>(binding.rgType.checkedRadioButtonId)
        if (rbUrgent == null) {
            binding.tlUrgent.error = getString(R.string.feedback_priority_is_required)
            return
        }
        if (rbType == null) {
            binding.tlType.error = getString(R.string.feedback_type_is_required)
            return
        }
        val urgent = rbUrgent.text.toString()
        val type = rbType.text.toString()
        val arguments = arguments
        val feedback = if (arguments != null) {
            val argumentArray = getArgumentArray(message)
            saveData(urgent, type, argumentArray)
        } else {
            saveData(urgent, type, message)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            feedbackRepository.saveFeedback(feedback)
            Utilities.toast(activity, R.string.feedback_saved.toString())
        }
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
        binding.tlUrgent.error = ""
        binding.tlType.error = ""
        binding.tlMessage.error = ""
    }

    private fun saveData(urgent: String, type: String, message: String): RealmFeedback {
        val feedback = RealmFeedback()
        feedback.id = UUID.randomUUID().toString()
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
        return feedback
    }

    private fun saveData(urgent: String, type: String, argumentArray: Array<String?>): RealmFeedback {
        val feedback = RealmFeedback()
        feedback.id = UUID.randomUUID().toString()
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
        return feedback
    }
}
