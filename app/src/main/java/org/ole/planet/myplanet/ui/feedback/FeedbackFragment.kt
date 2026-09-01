package org.ole.planet.myplanet.ui.feedback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnChangedListener
import org.ole.planet.myplanet.databinding.FragmentFeedbackBinding
import org.ole.planet.myplanet.utils.Utilities
import org.ole.planet.myplanet.utils.collectLatestWhenStarted

@AndroidEntryPoint
class FeedbackFragment : DialogFragment(), View.OnClickListener {
    private var _binding: FragmentFeedbackBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FeedbackComposerViewModel by viewModels()

    private var mListener: OnChangedListener? = null
    fun setOnFeedbackSubmittedListener(listener: OnChangedListener?) {
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
        collectLatestWhenStarted(viewModel.isSubmitting) { isSubmitting ->
            binding.btnSubmit.isEnabled = !isSubmitting
            binding.btnCancel.isEnabled = !isSubmitting
        }
        collectLatestWhenStarted(viewModel.events) { event ->
            when (event) {
                is FeedbackComposerViewModel.SubmitEvent.Saved -> {
                    Utilities.toast(activity, getString(R.string.feedback_saved))
                    mListener?.onChanged()
                    dismiss()
                }
                is FeedbackComposerViewModel.SubmitEvent.Error -> {
                    val msg = event.message ?: "An error occurred"
                    Utilities.toast(activity, getString(R.string.error, msg))
                }
            }
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
        viewModel.submitFeedback(urgent, type, message, item, state)
    }

    private fun clearError() {
        binding.tlUrgent.error = null
        binding.tlType.error = null
        binding.tlMessage.error = null
    }

}
