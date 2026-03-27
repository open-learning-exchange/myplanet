package org.ole.planet.myplanet.ui.ratings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.RatingBar.OnRatingBarChangeListener
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentRatingBinding
import org.ole.planet.myplanet.services.SharedPrefManager
import org.ole.planet.myplanet.utils.Utilities

@AndroidEntryPoint
class RatingsFragment : DialogFragment() {
    private var _binding: FragmentRatingBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var viewModel: RatingsViewModel
    @Inject
    lateinit var sharedPrefManager: SharedPrefManager
    var id: String? = ""
    var type: String? = ""
    var title: String? = ""
    private var ratingListener: OnRatingChangeListener? = null
    private var isUserReady = false
    private var currentSubmitState: RatingsViewModel.SubmitState = RatingsViewModel.SubmitState.Idle
    fun setListener(listener: OnRatingChangeListener?) {
        this.ratingListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.AppTheme_Dialog_NoActionBar_MinWidth)
        if (arguments != null) {
            id = requireArguments().getString("id")
            type = requireArguments().getString("type")
            title = requireArguments().getString("title")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRatingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
        loadRatingData()
    }
    
    private fun setupUI() {
        binding.ratingBar.onRatingBarChangeListener =
            OnRatingBarChangeListener { _: RatingBar?, _: Float, fromUser: Boolean ->
                if (fromUser) {
                    binding.ratingError.visibility = View.GONE
                }
            }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.userStatusText.text = getString(R.string.loading_user_profile)
        binding.userStatusContainer.isVisible = true
        updateSubmitButtonState()
        binding.btnSubmit.setOnClickListener {
            if (binding.ratingBar.rating.toDouble() == 0.0) {
                binding.ratingError.visibility = View.VISIBLE
                binding.ratingError.text = getString(R.string.kindly_give_a_rating)
            } else {
                submitRating()
            }
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.ratingState.collect { state ->
                    when (state) {
                        is RatingsViewModel.RatingUiState.Loading -> {}
                        is RatingsViewModel.RatingUiState.Success -> {
                            state.existingRating?.let { rating ->
                                binding.ratingBar.rating = rating.rate.toFloat()
                                binding.etComment.setText(rating.comment)
                            }
                        }
                        is RatingsViewModel.RatingUiState.Error -> {
                            Utilities.toast(activity, state.message)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userState.collect { user ->
                    isUserReady = user != null
                    binding.userStatusContainer.isVisible = !isUserReady
                    updateSubmitButtonState()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.submitState.collect { state ->
                    currentSubmitState = state
                    when (state) {
                        is RatingsViewModel.SubmitState.Success -> {
                            Utilities.toast(activity, "Thank you, your rating is submitted.")
                            ratingListener?.onRatingChanged()
                            dismiss()
                        }
                        is RatingsViewModel.SubmitState.Error -> {
                            Utilities.toast(activity, state.message)
                        }
                        RatingsViewModel.SubmitState.Submitting,
                        RatingsViewModel.SubmitState.Idle -> Unit
                    }
                    updateSubmitButtonState()
                }
            }
        }
    }
    
    private fun loadRatingData() {
        val userId = sharedPrefManager.getUserId()
        val t = type ?: return
        val i = id ?: return
        if (userId.isNotEmpty()) {
            viewModel.loadRatingData(t, i, userId)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun submitRating() {
        val comment = binding.etComment.text.toString()
        val rating = binding.ratingBar.rating
        val userId = sharedPrefManager.getUserId()

        val t = type ?: return
        val i = id ?: return
        val ttl = title ?: return
        if (userId.isNotEmpty()) {
            viewModel.submitRating(
                type = t,
                itemId = i,
                title = ttl,
                userId = userId,
                rating = rating,
                comment = comment
            )
        }
    }

    private fun updateSubmitButtonState() {
        val isSubmitting = currentSubmitState is RatingsViewModel.SubmitState.Submitting
        binding.btnSubmit.isEnabled = isUserReady && !isSubmitting
        binding.submitProgress.isVisible = isSubmitting
    }

    companion object {
        @JvmStatic
        fun newInstance(type: String?, id: String?, title: String?): RatingsFragment {
            val fragment = RatingsFragment()
            val b = Bundle()
            b.putString("id", id)
            b.putString("title", title)
            b.putString("type", type)
            fragment.arguments = b
            return fragment
        }
    }
}
