package org.ole.planet.myplanet.ui.feedback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentFeedbackBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class FeedbackFragment : DialogFragment(), View.OnClickListener {
    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SubmitFeedbackViewModel by viewModels()
    private var model: RealmUserModel? = null
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

        viewModel.submitStatus.observe(viewLifecycleOwner) { result ->
            result.onSuccess {
                Utilities.toast(activity, R.string.feedback_saved.toString())
                Toast.makeText(
                    activity,
                    R.string.thank_you_your_feedback_has_been_submitted,
                    Toast.LENGTH_SHORT,
                ).show()
                mListener?.onFeedbackSubmitted()
                dismiss()
            }
            result.onFailure {
                Toast.makeText(activity, it.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
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
        val item = arguments?.getString("item")
        val state = arguments?.getString("state")
        viewModel.submitFeedback(user, urgent, type, message, item, state)
    }

    private fun clearError() {
        binding.tlUrgent.error = ""
        binding.tlType.error = ""
        binding.tlMessage.error = ""
    }
}
