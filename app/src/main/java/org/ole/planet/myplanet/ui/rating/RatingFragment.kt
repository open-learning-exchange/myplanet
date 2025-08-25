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
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentRatingBinding
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.repository.RatingRepository
import org.ole.planet.myplanet.utilities.Constants
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class RatingFragment : DialogFragment() {
    private var _binding: FragmentRatingBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var ratingRepository: RatingRepository
    private var model: RealmUserModel? = null
    private var id: String? = ""
    private var type: String? = ""
    private var title: String? = ""
    private lateinit var settings: SharedPreferences
    private var ratingListener: OnRatingChangeListener? = null
    private var previousRating: RealmRating? = null

    fun setListener(listener: OnRatingChangeListener?) {
        this.ratingListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.AppTheme_Dialog_NoActionBar_MinWidth)
        arguments?.let {
            id = it.getString("id")
            type = it.getString("type")
            title = it.getString("title")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRatingBinding.inflate(inflater, container, false)
        settings = requireActivity().getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val userId = settings.getString("userId", "") ?: ""
        lifecycleScope.launch {
            model = ratingRepository.getUserModel(userId)
            previousRating = ratingRepository.getRating(type ?: "", id ?: "", userId)
            previousRating?.let {
                binding.ratingBar.rating = it.rate.toFloat()
                binding.etComment.setText(it.comment)
            }
        }

        binding.ratingBar.onRatingBarChangeListener =
            OnRatingBarChangeListener { _, _, fromUser ->
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
        super.onDestroyView()
        _binding = null
    }

    private fun saveRating() {
        val comment = binding.etComment.text.toString()
        val rating = binding.ratingBar.rating
        val userId = settings.getString("userId", "") ?: ""

        lifecycleScope.launch {
            val ratingObject = previousRating ?: RealmRating().apply {
                _id = UUID.randomUUID().toString()
            }
            model = ratingRepository.getUserModel(userId)
            setData(model, ratingObject, comment, rating)
            ratingRepository.saveRating(ratingObject)

            Utilities.toast(activity, "Thank you, your rating is submitted.")
            ratingListener?.onRatingChanged()
            dismiss()
        }
    }

    private fun setData(model: RealmUserModel?, ratingObject: RealmRating, comment: String, rating: Float) {
        ratingObject.isUpdated = true
        ratingObject.comment = comment
        ratingObject.rate = rating.toInt()
        ratingObject.time = Date().time
        ratingObject.userId = model?.id
        ratingObject.createdOn = model?.parentCode
        ratingObject.parentCode = model?.parentCode
        ratingObject.planetCode = model?.planetCode
        ratingObject.user = Gson().toJson(model?.serialize())
        ratingObject.type = type
        ratingObject.item = id
        ratingObject.title = title
    }

    companion object {
        @JvmStatic
        fun newInstance(type: String?, id: String?, title: String?): RatingFragment {
            return RatingFragment().apply {
                arguments = Bundle().apply {
                    putString("id", id)
                    putString("title", title)
                    putString("type", type)
                }
            }
        }
    }
}
