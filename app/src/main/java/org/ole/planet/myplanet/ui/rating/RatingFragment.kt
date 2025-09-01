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
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentRatingBinding
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class RatingFragment : DialogFragment() {
    private var _binding: FragmentRatingBinding? = null
    private val binding get() = _binding!!
    // Use the Activity as the ViewModelStoreOwner to provide required saved state extras
    private val viewModel by viewModels<RatingViewModel>(ownerProducer = { requireActivity() })
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
        viewModel.loadRating(type, id, settings.getString("userId", ""))
        viewModel.userRating.observe(viewLifecycleOwner) { rating ->
            rating?.let {
                binding.ratingBar.rating = it.rate?.toFloat() ?: 0.0f
                binding.etComment.setText(it.comment)
            }
        }
        viewModel.saveStatus.observe(viewLifecycleOwner) { saved ->
            if (saved == true) {
                Utilities.toast(activity, "Thank you, your rating is submitted.")
                ratingListener?.onRatingChanged()
                dismiss()
            }
        }
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
                saveRating()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun saveRating() {
        val comment = binding.etComment.text.toString()
        val rating = binding.ratingBar.rating.toInt()
        viewModel.saveRating(
            type,
            id,
            title,
            settings.getString("userId", ""),
            comment,
            rating
        )
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
