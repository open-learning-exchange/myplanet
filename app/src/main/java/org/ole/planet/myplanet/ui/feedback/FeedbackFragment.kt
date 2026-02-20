package org.ole.planet.myplanet.ui.feedback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnFeedbackSubmittedListener
import org.ole.planet.myplanet.databinding.FragmentFeedbackBinding
import org.ole.planet.myplanet.model.RealmUser
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.services.UserSessionManager
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class FeedbackFragment : DialogFragment(), View.OnClickListener {
    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var feedbackRepository: FeedbackRepository
    @Inject
    lateinit var userSessionManager: UserSessionManager
    private var model: RealmUser ?= null
    var user: String? = ""

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
        binding.btnSubmit.setOnClickListener(this)
        binding.btnCancel.setOnClickListener(this)
        setupFormValidation()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            model = userSessionManager.getUserModelSuspending()
            user = model?.name
        }
    }

    private fun setupFormValidation() {
        binding.etMessage.doAfterTextChanged { text ->
            if (text.isNullOrBlank()) {
                binding.tlMessage.error = getString(R.string.please_enter_feedback)
            } else {
                binding.tlMessage.error = null
            }
        }

        binding.rgUrgent.setOnCheckedChangeListener { _, _ ->
            binding.tlUrgent.error = null
        }

        binding.rgType.setOnCheckedChangeListener { _, _ ->
            binding.tlType.error = null
        }
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
        val feedback = feedbackRepository.createFeedback(user, urgent, type, message, item, state)
        viewLifecycleOwner.lifecycleScope.launch {
            feedbackRepository.saveFeedback(feedback)
            Utilities.toast(activity, getString(R.string.feedback_saved))
        }
        Toast.makeText(activity, R.string.thank_you_your_feedback_has_been_submitted, Toast.LENGTH_SHORT).show()
        mListener?.onFeedbackSubmitted()
        dismiss()
    }

    private fun clearError() {
        binding.tlUrgent.error = null
        binding.tlType.error = null
        binding.tlMessage.error = null
    }

}
