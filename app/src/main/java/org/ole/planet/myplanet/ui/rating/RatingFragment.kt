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
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentRatingBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Utilities

@AndroidEntryPoint
class RatingFragment : DialogFragment() {
    private var _binding: FragmentRatingBinding? = null
    private val binding get() = _binding!!
    @Inject
    lateinit var databaseService: DatabaseService
    var id: String? = ""
    var type: String? = ""
    var title: String? = ""
    lateinit var settings: SharedPreferences
    private var ratingListener: OnRatingChangeListener? = null
    private var previousRating: RealmRating? = null
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
        previousRating = databaseService.withRealm { realm ->
            realm.where(RealmRating::class.java).equalTo("type", type)
                .equalTo("userId", settings.getString("userId", ""))
                .equalTo("item", id).findFirst()
                ?.let { realm.copyFromRealm(it) }
        }
        previousRating?.let {
            binding.ratingBar.rating = it.rate.toFloat()
            binding.etComment.setText(it.comment)
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
        val rating = binding.ratingBar.rating
        viewLifecycleOwner.lifecycleScope.launch {
            databaseService.executeTransactionAsync { realm ->
                var ratingObject = realm.where(RealmRating::class.java)
                    .equalTo("type", type)
                    .equalTo("userId", settings.getString("userId", ""))
                    .equalTo("item", id).findFirst()
                if (ratingObject == null) {
                    ratingObject = realm.createObject(RealmRating::class.java, UUID.randomUUID().toString())
                }
                val model = realm.where(RealmUserModel::class.java)
                    .equalTo("id", settings.getString("userId", ""))
                    .findFirst()
                setData(model, ratingObject, comment, rating)
            }
            Utilities.toast(activity, "Thank you, your rating is submitted.")
            ratingListener?.onRatingChanged()
            dismiss()
        }
    }

    private fun setData(model: RealmUserModel?, ratingObject: RealmRating?, comment: String, rating: Float) {
        ratingObject?.isUpdated = true
        ratingObject?.comment = comment
        ratingObject?.rate = rating.toInt()
        ratingObject?.time = Date().time
        ratingObject?.userId = model?.id
        ratingObject?.createdOn = model?.parentCode
        ratingObject?.parentCode = model?.parentCode
        ratingObject?.planetCode = model?.planetCode
        ratingObject?.user = Gson().toJson(model?.serialize())
        ratingObject?.type = type
        ratingObject?.item = id
        ratingObject?.title = title
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
