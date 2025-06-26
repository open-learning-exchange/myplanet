package org.ole.planet.myplanet.ui.feedback

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import io.realm.Realm
import io.realm.RealmResults
import org.ole.planet.myplanet.databinding.FragmentFeedbackListBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment.OnFeedbackSubmittedListener
import org.ole.planet.myplanet.base.BaseRecyclerFragment.Companion.showNoData
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.SharedPrefManager

class FeedbackListFragment : Fragment(), OnFeedbackSubmittedListener {
    private lateinit var fragmentFeedbackListBinding: FragmentFeedbackListBinding
    private lateinit var mRealm: Realm
    var userModel: RealmUserModel? = null
    private var feedbackList: RealmResults<RealmFeedback>? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    lateinit var prefManager: SharedPrefManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefManager = SharedPrefManager(requireContext())

        // Start selective sync for feedback
        startFeedbackSync()
    }

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

        setupFeedbackListener()

        return fragmentFeedbackListBinding.root
    }

    private fun startFeedbackSync() {
        if (!prefManager.isFeedbackSynced()) {
            SyncManager.instance?.start(object : SyncListener {
                override fun onSyncStarted() {
                    activity?.runOnUiThread {
                        if (isAdded && !requireActivity().isFinishing) {
                            customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                            customProgressDialog?.setText("Syncing feedback...")
                            customProgressDialog?.show()
                        }
                    }
                }

                override fun onSyncComplete() {
                    activity?.runOnUiThread {
                        if (isAdded) {
                            customProgressDialog?.dismiss()
                            customProgressDialog = null

                            refreshFeedbackData()

                            Toast.makeText(requireContext(), "Feedback synced successfully", Toast.LENGTH_SHORT).show()
                            prefManager.setFeedbackSynced(true)
                        }
                    }
                }

                override fun onSyncFailed(message: String?) {
                    activity?.runOnUiThread {
                        if (isAdded) {
                            customProgressDialog?.dismiss()
                            customProgressDialog = null

                            // Show error message
                            Snackbar.make(fragmentFeedbackListBinding.root, "Sync failed: ${message ?: "Unknown error"}", Snackbar.LENGTH_LONG)
                                .setAction("Retry") { startFeedbackSync() }.show()
                        }
                    }
                }
            }, "full", listOf("feedback"))
        }
    }

    private fun setupFeedbackListener() {
        feedbackList = mRealm.where(RealmFeedback::class.java)
            .equalTo("owner", userModel?.name).findAllAsync()

        feedbackList?.addChangeListener { results ->
            updatedFeedbackList(results)
        }
    }

    private fun refreshFeedbackData() {
        if (!isAdded || requireActivity().isFinishing) return

        try {
            // Re-query feedback data after sync
            var list: List<RealmFeedback>? = mRealm.where(RealmFeedback::class.java)
                .equalTo("owner", userModel?.name).findAll()

            if (userModel?.isManager() == true) {
                list = mRealm.where(RealmFeedback::class.java).findAll()
            }

            // Update the adapter with fresh data
            val adapterFeedback = AdapterFeedback(requireActivity(), list)
            fragmentFeedbackListBinding.rvFeedback.adapter = adapterFeedback

            val itemCount = list?.size ?: 0
            showNoData(fragmentFeedbackListBinding.tvMessage, itemCount, "feedback")
            updateTextViewsVisibility(itemCount)

            // Update the listener with fresh data
            setupFeedbackListener()

        } catch (e: Exception) {
            Log.e("FeedbackListFragment", "Error refreshing feedback data: ${e.message}", e)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentFeedbackListBinding.rvFeedback.layoutManager = LinearLayoutManager(activity)

        // Initial load of feedback data
        loadInitialFeedbackData()
    }

    private fun loadInitialFeedbackData() {
        var list: List<RealmFeedback>? = mRealm.where(RealmFeedback::class.java)
            .equalTo("owner", userModel?.name).findAll()
        if (userModel?.isManager() == true) list = mRealm.where(RealmFeedback::class.java).findAll()
        val adapterFeedback = AdapterFeedback(requireActivity(), list)
        fragmentFeedbackListBinding.rvFeedback.adapter = adapterFeedback

        val itemCount = feedbackList?.size ?: 0
        showNoData(fragmentFeedbackListBinding.tvMessage, itemCount, "feedback")

        updateTextViewsVisibility(itemCount)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up progress dialog
        customProgressDialog?.dismiss()
        customProgressDialog = null

        // Clean up realm
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
            showNoData(fragmentFeedbackListBinding.tvMessage, itemCount, "feedback")
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
