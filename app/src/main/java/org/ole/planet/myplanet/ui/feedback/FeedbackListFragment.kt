package org.ole.planet.myplanet.ui.feedback

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.RealmChangeListener
import io.realm.RealmResults
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment.Companion.showNoData
import org.ole.planet.myplanet.callback.SyncListener
import org.ole.planet.myplanet.databinding.FragmentFeedbackListBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.di.AppPreferences
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment.OnFeedbackSubmittedListener
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager

@AndroidEntryPoint
class FeedbackListFragment : Fragment(), OnFeedbackSubmittedListener {
    private lateinit var fragmentFeedbackListBinding: FragmentFeedbackListBinding
    private lateinit var mRealm: Realm
    var userModel: RealmUserModel? = null
    private var feedbackList: RealmResults<RealmFeedback>? = null
    private var feedbackChangeListener: RealmChangeListener<RealmResults<RealmFeedback>>? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    lateinit var prefManager: SharedPrefManager

    @Inject
    @AppPreferences
    lateinit var settings: SharedPreferences
    @Inject
    lateinit var databaseService: DatabaseService
    private val serverUrlMapper = ServerUrlMapper()
    
    @Inject
    lateinit var syncManager: SyncManager
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefManager = SharedPrefManager(requireContext())

        startFeedbackSync()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentFeedbackListBinding = FragmentFeedbackListBinding.inflate(inflater, container, false)
        mRealm = databaseService.realmInstance
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
        val isFastSync = settings.getBoolean("fastSync", false)
        if (isFastSync && !prefManager.isFeedbackSynced()) {
            checkServerAndStartSync()
        }
    }

    private fun checkServerAndStartSync() {
        val mapping = serverUrlMapper.processUrl(serverUrl)

        lifecycleScope.launch(Dispatchers.IO) {
            updateServerIfNecessary(mapping)
            withContext(Dispatchers.Main) {
                startSyncManager()
            }
        }
    }

    private fun startSyncManager() {
        syncManager.start(object : SyncListener {
            override fun onSyncStarted() {
                activity?.runOnUiThread {
                    if (isAdded && !requireActivity().isFinishing) {
                        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                        customProgressDialog?.setText(getString(R.string.syncing_feedback))
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

                        prefManager.setFeedbackSynced(true)
                    }
                }
            }

            override fun onSyncFailed(msg: String?) {
                activity?.runOnUiThread {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null

                        Snackbar.make(fragmentFeedbackListBinding.root, "Sync failed: ${msg ?: "Unknown error"}", Snackbar.LENGTH_LONG)
                            .setAction("Retry") { startFeedbackSync() }.show()
                    }
                }
            }
        }, "full", listOf("feedback"))
    }

    private suspend fun updateServerIfNecessary(mapping: ServerUrlMapper.UrlMapping) {
        serverUrlMapper.updateServerIfNecessary(mapping, settings) { url ->
            isServerReachable(url)
        }
    }

    private fun setupFeedbackListener() {
        feedbackChangeListener?.let { listener ->
            feedbackList?.removeChangeListener(listener)
        }
        feedbackList = mRealm.where(RealmFeedback::class.java)
            .equalTo("owner", userModel?.name).findAllAsync()

        feedbackChangeListener = feedbackList?.addChangeListener { results ->
            updatedFeedbackList(results)
        }
    }

    private fun refreshFeedbackData() {
        if (!isAdded || requireActivity().isFinishing) return

        try {
            var list: List<RealmFeedback>? = mRealm.where(RealmFeedback::class.java)
                .equalTo("owner", userModel?.name).findAll()

            if (userModel?.isManager() == true) {
                list = mRealm.where(RealmFeedback::class.java).findAll()
            }

            val adapterFeedback = AdapterFeedback(requireActivity(), list)
            fragmentFeedbackListBinding.rvFeedback.adapter = adapterFeedback

            val itemCount = list?.size ?: 0
            showNoData(fragmentFeedbackListBinding.tvMessage, itemCount, "feedback")
            updateTextViewsVisibility(itemCount)

            setupFeedbackListener()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentFeedbackListBinding.rvFeedback.layoutManager = LinearLayoutManager(activity)

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

    override fun onDestroyView() {
        feedbackChangeListener?.let { listener ->
            feedbackList?.removeChangeListener(listener)
        }
        if (this::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
        }
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        customProgressDialog?.dismiss()
        customProgressDialog = null
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
