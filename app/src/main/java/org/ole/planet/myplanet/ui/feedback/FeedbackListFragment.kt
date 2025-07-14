package org.ole.planet.myplanet.ui.feedback

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import io.realm.Realm
import io.realm.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.MainApplication.Companion.isServerReachable
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment.Companion.showNoData
import org.ole.planet.myplanet.callback.EnhancedSyncListener
import org.ole.planet.myplanet.databinding.FragmentFeedbackListBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.RealmUserModel
import org.ole.planet.myplanet.service.SyncManager
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment.OnFeedbackSubmittedListener
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.DialogUtils
import org.ole.planet.myplanet.utilities.ServerUrlMapper
import org.ole.planet.myplanet.utilities.SharedPrefManager

class FeedbackListFragment : Fragment(), OnFeedbackSubmittedListener {
    private lateinit var fragmentFeedbackListBinding: FragmentFeedbackListBinding
    private lateinit var mRealm: Realm
    var userModel: RealmUserModel? = null
    private var feedbackList: RealmResults<RealmFeedback>? = null
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    lateinit var prefManager: SharedPrefManager
    lateinit var settings: SharedPreferences

    // NEW: Progressive loading properties
    private var isDataLoading = false
    private var dataRefreshHandler: Handler? = null
    private var loadingIndicator: View? = null
    private var loadingText: TextView? = null
    private var dataReadyCounter = 0

    private val serverUrlMapper = ServerUrlMapper()
    private val serverUrl: String
        get() = settings.getString("serverURL", "") ?: ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefManager = SharedPrefManager(requireContext())
        settings = requireActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize loading indicator and refresh handler
        setupLoadingIndicator(view)
        setupDataRefreshHandler()

        fragmentFeedbackListBinding.rvFeedback.layoutManager = LinearLayoutManager(activity)
        loadInitialFeedbackData()
    }

    private fun setupLoadingIndicator(view: View) {
        // The loading indicator already exists in the XML layout
        loadingIndicator = fragmentFeedbackListBinding.loadingIndicator
        loadingText = fragmentFeedbackListBinding.loadingText
    }

    private fun createLoadingViews(parentView: View) {
        // Since loading indicator already exists in XML, just get references to it
        loadingIndicator = fragmentFeedbackListBinding.loadingIndicator
        loadingText = fragmentFeedbackListBinding.loadingText
    }

    private fun setupDataRefreshHandler() {
        dataRefreshHandler = Handler(Looper.getMainLooper())
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
        isDataLoading = true
        dataReadyCounter = 0

        SyncManager.instance?.start(object : EnhancedSyncListener {
            override fun onSyncStarted() {
                activity?.runOnUiThread {
                    if (isAdded && !requireActivity().isFinishing) {
                        // Show both progress dialog and loading indicator
                        customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                        customProgressDialog?.setText(getString(R.string.syncing_feedback))
                        customProgressDialog?.show()

                        showLoadingState()
                        startPeriodicDataRefresh()
                    }
                }
            }

            override fun onProgressUpdate(processName: String, itemsProcessed: Int) {
                activity?.runOnUiThread {
                    if (isAdded) {
                        // Update loading text with progress
                        loadingText?.text = "Loading $processName: $itemsProcessed items"

                        // Update progress dialog
                        customProgressDialog?.setText("$processName: $itemsProcessed items processed")
                    }
                }
            }

            override fun onDataReady(dataType: String) {
                activity?.runOnUiThread {
                    if (isAdded) {
                        dataReadyCounter++
                        loadingText?.text = "$dataType data ready"

                        // Refresh data immediately when ready
                        refreshFeedbackDataSilently()

                        // Dismiss progress dialog after first data is ready but keep loading indicator
                        if (dataReadyCounter == 1) {
                            customProgressDialog?.dismiss()
                            customProgressDialog = null
                        }
                    }
                }
            }

            override fun onSyncComplete() {
                activity?.runOnUiThread {
                    if (isAdded) {
                        // Dismiss progress dialog if still showing
                        customProgressDialog?.dismiss()
                        customProgressDialog = null

                        // Final data refresh and hide loading
                        refreshFeedbackData()
                        hideLoadingState()
                        stopPeriodicDataRefresh()

                        prefManager.setFeedbackSynced(true)
                        isDataLoading = false
                    }
                }
            }

            override fun onSyncFailed(msg: String?) {
                activity?.runOnUiThread {
                    if (isAdded) {
                        customProgressDialog?.dismiss()
                        customProgressDialog = null
                        hideLoadingState()
                        stopPeriodicDataRefresh()
                        isDataLoading = false

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

    // NEW: Progressive loading UI methods
    private fun showLoadingState() {
        loadingIndicator?.visibility = View.VISIBLE
        loadingText?.text = "Preparing feedback sync..."

        // Hide empty state message while loading
        fragmentFeedbackListBinding.tvMessage.visibility = View.GONE
    }

    private fun hideLoadingState() {
        loadingIndicator?.visibility = View.GONE
    }

    private fun startPeriodicDataRefresh() {
        val refreshRunnable = object : Runnable {
            override fun run() {
                if (isDataLoading && isAdded) {
                    refreshFeedbackDataSilently()
                    dataRefreshHandler?.postDelayed(this, 2000) // Refresh every 2 seconds
                }
            }
        }
        dataRefreshHandler?.postDelayed(refreshRunnable, 2000)
    }

    private fun stopPeriodicDataRefresh() {
        dataRefreshHandler?.removeCallbacksAndMessages(null)
    }

    // Silent refresh that doesn't show/hide loading states
    private fun refreshFeedbackDataSilently() {
        if (!isAdded || requireActivity().isFinishing) return

        try {
            var list: List<RealmFeedback>? = mRealm.where(RealmFeedback::class.java)
                .equalTo("owner", userModel?.name).findAll()

            if (userModel?.isManager() == true) {
                list = mRealm.where(RealmFeedback::class.java).findAll()
            }

            if (list?.isNotEmpty() == true) {
                val adapterFeedback = AdapterFeedback(requireActivity(), list)
                fragmentFeedbackListBinding.rvFeedback.adapter = adapterFeedback

                val itemCount = list.size

                // Only show "no data" if we're not loading and list is actually empty
                if (!isDataLoading) {
                    showNoData(fragmentFeedbackListBinding.tvMessage, itemCount, "feedback")
                } else {
                    fragmentFeedbackListBinding.tvMessage.visibility = View.GONE
                }

                updateTextViewsVisibility(itemCount)

                // Re-setup listener for real-time updates
                setupFeedbackListener()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Enhanced version of existing refreshFeedbackData
    private fun refreshFeedbackData() {
        refreshFeedbackDataSilently()

        // Show appropriate state after loading is complete
        if (!isDataLoading) {
            val itemCount = feedbackList?.size ?: 0
            showNoData(fragmentFeedbackListBinding.tvMessage, itemCount, "feedback")
        }
    }

    private fun setupFeedbackListener() {
        // Remove existing listener to avoid duplicates
        feedbackList?.removeAllChangeListeners()

        feedbackList = mRealm.where(RealmFeedback::class.java)
            .equalTo("owner", userModel?.name).findAllAsync()

        feedbackList?.addChangeListener { results ->
            if (!isDataLoading) {
                updatedFeedbackList(results)
            }
        }
    }

    private fun loadInitialFeedbackData() {
        var list: List<RealmFeedback>? = mRealm.where(RealmFeedback::class.java)
            .equalTo("owner", userModel?.name).findAll()

        if (userModel?.isManager() == true) {
            list = mRealm.where(RealmFeedback::class.java).findAll()
        }

        val adapterFeedback = AdapterFeedback(requireActivity(), list)
        fragmentFeedbackListBinding.rvFeedback.adapter = adapterFeedback

        val itemCount = list?.size ?: 0

        // Only show no data message if we're not currently loading
        if (!isDataLoading) {
            showNoData(fragmentFeedbackListBinding.tvMessage, itemCount, "feedback")
        }

        updateTextViewsVisibility(itemCount)
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when user returns to fragment
        if (!isDataLoading) {
            refreshFeedbackDataSilently()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        customProgressDialog?.dismiss()
        customProgressDialog = null
        stopPeriodicDataRefresh()
        dataRefreshHandler = null

        feedbackList?.removeAllChangeListeners()

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

            // Only show no data message if we're not currently loading
            if (!isDataLoading) {
                showNoData(fragmentFeedbackListBinding.tvMessage, itemCount, "feedback")
            }

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
