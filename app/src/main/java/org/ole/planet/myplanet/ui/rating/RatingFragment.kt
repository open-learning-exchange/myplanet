package org.ole.planet.myplanet.ui.rating

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RatingBar
import android.widget.RatingBar.OnRatingBarChangeListener
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentRatingBinding
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class RatingFragment : DialogFragment() {
    private var _binding: FragmentRatingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RatingViewModel by viewModels(
        factoryProducer = { defaultViewModelProviderFactory },
        extrasProducer = { defaultViewModelCreationExtras }
    )
    private var currentUser: RealmUserModel? = null
    var id: String? = ""
    var type: String? = ""
    var title: String? = ""
    lateinit var settings: SharedPreferences
    private var ratingListener: OnRatingChangeListener? = null
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
        settings = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
                        is RatingViewModel.RatingUiState.Loading -> {}
                        is RatingViewModel.RatingUiState.Success -> {
                            state.existingRating?.let { rating ->
                                binding.ratingBar.rating = rating.rate.toFloat()
                                binding.etComment.setText(rating.comment)
                            }
                        }
                        is RatingViewModel.RatingUiState.Error -> {
                            Utilities.toast(activity, state.message)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.userState.collect { user ->
                    currentUser = user
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.submitState.collect { state ->
                    when (state) {
                        is RatingViewModel.SubmitState.Submitting -> {
                            binding.btnSubmit.isEnabled = false
                        }
                        is RatingViewModel.SubmitState.Success -> {
                            binding.btnSubmit.isEnabled = true
                            Utilities.toast(activity, "Thank you, your rating is submitted.")
                            ratingListener?.onRatingChanged()
                            dismiss()
                        }
                        is RatingViewModel.SubmitState.Error -> {
                            binding.btnSubmit.isEnabled = true
                            Utilities.toast(activity, state.message)
                        }
                        RatingViewModel.SubmitState.Idle -> {
                            binding.btnSubmit.isEnabled = true
                        }
                    }
                }
            }
        }
    }
    
    private fun loadRatingData() {
        val userId = settings.getString("userId", "") ?: ""
        if (type != null && id != null && userId.isNotEmpty()) {
            viewModel.loadRatingData(type!!, id!!, userId)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun submitRating() {
        val comment = binding.etComment.text.toString()
        val rating = binding.ratingBar.rating
        val userId = settings.getString("userId", "") ?: ""
        
        if (type != null && id != null && title != null && currentUser != null && userId.isNotEmpty()) {
            viewModel.submitRating(
                type = type!!,
                itemId = id!!,
                title = title!!,
                userId = userId,
                rating = rating,
                comment = comment
            )
        }
    }


    companion object {
        @JvmStatic
        fun newInstance(type: String?, id: String?, title: String?): RatingFragment {
            val fragment = RatingFragment()
            val b = Bundle()
            b.putString("id", id)
            b.putString("title", title)
            b.putString("type", type)
            fragment.arguments = b
            return fragment
        }
    }
}
