package org.ole.planet.myplanet.ui.feedback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import io.realm.Realm
import io.realm.RealmResults
import org.ole.planet.myplanet.base.BaseRecyclerFragment
import org.ole.planet.myplanet.databinding.FragmentFeedbackListBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment.OnFeedbackSubmittedListener

class FeedbackListFragment : Fragment(), OnFeedbackSubmittedListener {
    private lateinit var fragmentFeedbackListBinding: FragmentFeedbackListBinding
    private lateinit var mRealm: Realm
    var userModel: RealmUserModel? = null
    private var feedbackList: RealmResults<RealmFeedback>? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentFeedbackListBinding = FragmentFeedbackListBinding.inflate(inflater, container, false)
        mRealm = DatabaseService(requireActivity()).realmInstance
        userModel = UserProfileDbHandler(requireContext()).userModel

        fragmentFeedbackListBinding.fab.setOnClickListener {
            val feedbackFragment = FeedbackFragment()
            feedbackFragment.setOnFeedbackSubmittedListener(this)
            if (!childFragmentManager.isStateSaved) {
                feedbackFragment.show(childFragmentManager, "")
            }
        }

        feedbackList = mRealm.where(RealmFeedback::class.java)
            .equalTo("owner", userModel?.name).findAllAsync()

        feedbackList?.addChangeListener { results ->
            updatedFeedbackList(results)
        }

        return fragmentFeedbackListBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentFeedbackListBinding.rvFeedback.layoutManager = LinearLayoutManager(activity)
        var list: List<RealmFeedback>? = mRealm.where(RealmFeedback::class.java)
            .equalTo("owner", userModel?.name).findAll()
        if (userModel?.isManager() == true) list = mRealm.where(RealmFeedback::class.java).findAll()
        val adapterFeedback = AdapterFeedback(requireActivity(), list)
        fragmentFeedbackListBinding.rvFeedback.adapter = adapterFeedback

        val itemCount = feedbackList?.size ?: 0
        BaseRecyclerFragment.showNoData(fragmentFeedbackListBinding.tvMessage, itemCount, "feedback")

        updateTextViewsVisibility(itemCount)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
        }
    }

    override fun onFeedbackSubmitted() {
        mRealm.executeTransactionAsync(
            Realm.Transaction { },
            Realm.Transaction.OnSuccess {
                var updatedList = mRealm.where(RealmFeedback::class.java)
                    .equalTo("owner", userModel?.name).findAll()
                if (userModel?.isManager() == true) {
                    updatedList = mRealm.where(RealmFeedback::class.java).findAll()
                }
                updatedFeedbackList(updatedList)
            })
    }

    private fun updatedFeedbackList(updatedList: RealmResults<RealmFeedback>?) {
        activity?.runOnUiThread {
            val adapterFeedback = updatedList?.let { AdapterFeedback(requireActivity(), it) }
            fragmentFeedbackListBinding.rvFeedback.adapter = adapterFeedback
            adapterFeedback?.notifyDataSetChanged()

            val itemCount = updatedList?.size ?: 0
            BaseRecyclerFragment.showNoData(fragmentFeedbackListBinding.tvMessage, itemCount, "feedback")
            updateTextViewsVisibility(itemCount)
        }
    }

    private fun updateTextViewsVisibility(itemCount: Int) {
        val visibility = if (itemCount == 0) View.GONE else View.VISIBLE
        fragmentFeedbackListBinding.tvTitle.visibility = visibility
        fragmentFeedbackListBinding.tvType.visibility = visibility
        fragmentFeedbackListBinding.tvPriority.visibility = visibility
        fragmentFeedbackListBinding.tvStatus.visibility = visibility
        fragmentFeedbackListBinding.tvOpenDate.visibility = visibility
    }
}
