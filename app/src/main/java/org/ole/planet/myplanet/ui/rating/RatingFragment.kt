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
import com.google.gson.Gson
import io.realm.Realm
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.callback.OnRatingChangeListener
import org.ole.planet.myplanet.databinding.FragmentRatingBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmRating
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.Utilities
import java.util.Date
import java.util.UUID

class RatingFragment : DialogFragment() {
    private lateinit var fragmentRatingBinding: FragmentRatingBinding
    private lateinit var databaseService: DatabaseService
    lateinit var mRealm: Realm
    var model: RealmUserModel? = null
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
        fragmentRatingBinding = FragmentRatingBinding.inflate(inflater, container, false)
        databaseService = DatabaseService()
        mRealm = databaseService.realmInstance
        settings = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return fragmentRatingBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        model = mRealm.where(RealmUserModel::class.java)
            .equalTo("id", settings.getString("userId", "")).findFirst()
        previousRating = mRealm.where(RealmRating::class.java).equalTo("type", type)
            .equalTo("userId", settings.getString("userId", "")).equalTo("item", id).findFirst()
        if (previousRating != null) {
            fragmentRatingBinding.ratingBar.rating = previousRating?.rate?.toFloat() ?: 0.0f
            fragmentRatingBinding.etComment.setText(previousRating?.comment)
        }
        fragmentRatingBinding.ratingBar.onRatingBarChangeListener =
            OnRatingBarChangeListener { _: RatingBar?, _: Float, fromUser: Boolean ->
                if (fromUser) {
                    fragmentRatingBinding.ratingError.visibility = View.GONE
                }
            }
        fragmentRatingBinding.btnCancel.setOnClickListener { dismiss() }
        fragmentRatingBinding.btnSubmit.setOnClickListener {
            if (fragmentRatingBinding.ratingBar.rating.toDouble() == 0.0) {
                fragmentRatingBinding.ratingError.visibility = View.VISIBLE
                fragmentRatingBinding.ratingError.text = getString(R.string.kindly_give_a_rating)
            } else {
                saveRating()
            }
        }
    }

    private fun saveRating() {
        val comment = fragmentRatingBinding.etComment.text.toString()
        val rating = fragmentRatingBinding.ratingBar.rating
        mRealm.executeTransactionAsync(Realm.Transaction { realm: Realm ->
            var ratingObject = realm.where(RealmRating::class.java)
                .equalTo("type", type)
                .equalTo("userId", settings.getString("userId", ""))
                .equalTo("item", id).findFirst()
            if (ratingObject == null) ratingObject = realm.createObject(RealmRating::class.java, UUID.randomUUID().toString())
            model = realm.where(RealmUserModel::class.java).equalTo("id", settings.getString("userId", "")).findFirst()
            setData(model, ratingObject, comment, rating)
        }, Realm.Transaction.OnSuccess {
            Utilities.toast(activity, "Thank you, your rating is submitted.")
            if (ratingListener != null) ratingListener?.onRatingChanged()
            dismiss()
        })
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
