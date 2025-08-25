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
import io.realm.Sort
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
    private var _binding: FragmentFeedbackListBinding? = null
    private val binding get() = _binding!!
    private lateinit var mRealm: Realm
    var userModel: RealmUserModel? = null
    private var feedbackList: RealmResults<RealmFeedback>? = null
    private val feedbackChangeListener =
        RealmChangeListener<RealmResults<RealmFeedback>> { results ->
            updatedFeedbackList(results)
        }
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
        _binding = FragmentFeedbackListBinding.inflate(inflater, container, false)
        mRealm = databaseService.realmInstance
        userModel = UserProfileDbHandler(requireContext()).userModel

        binding.fab.setOnClickListener {
            val feedbackFragment = FeedbackFragment()
            feedbackFragment.setOnFeedbackSubmittedListener(this)
            if (!childFragmentManager.isStateSaved) {
                feedbackFragment.show(childFragmentManager, "")
            }
        }

        setupFeedbackListener()

        return binding.root
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

                        Snackbar.make(binding.root, "Sync failed: ${msg ?: "Unknown error"}", Snackbar.LENGTH_LONG)
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
        feedbackList?.removeChangeListener(feedbackChangeListener)
        feedbackList = if (userModel?.isManager() == true) {
            mRealm.where(RealmFeedback::class.java)
                .sort("openTime", Sort.DESCENDING)
                .findAllAsync()
        } else {
            mRealm.where(RealmFeedback::class.java)
                .equalTo("owner", userModel?.name)
                .sort("openTime", Sort.DESCENDING)
                .findAllAsync()
        }
        feedbackList?.addChangeListener(feedbackChangeListener)
    }

    private fun refreshFeedbackData() {
        if (!isAdded || requireActivity().isFinishing) return

        try {
            val list: List<RealmFeedback>? = if (userModel?.isManager() == true) {
                mRealm.where(RealmFeedback::class.java)
                    .sort("openTime", Sort.DESCENDING)
                    .findAll()
            } else {
                mRealm.where(RealmFeedback::class.java)
                    .equalTo("owner", userModel?.name)
                    .sort("openTime", Sort.DESCENDING)
                    .findAll()
            }

            val adapterFeedback = AdapterFeedback(requireActivity(), list)
            binding.rvFeedback.adapter = adapterFeedback

            val itemCount = list?.size ?: 0
            showNoData(binding.tvMessage, itemCount, "feedback")
            updateTextViewsVisibility(itemCount)

            setupFeedbackListener()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvFeedback.layoutManager = LinearLayoutManager(activity)

        loadInitialFeedbackData()
    }

    private fun loadInitialFeedbackData() {
        val list: List<RealmFeedback>? = if (userModel?.isManager() == true) {
            mRealm.where(RealmFeedback::class.java)
                .sort("openTime", Sort.DESCENDING)
                .findAll()
        } else {
            mRealm.where(RealmFeedback::class.java)
                .equalTo("owner", userModel?.name)
                .sort("openTime", Sort.DESCENDING)
                .findAll()
        }
        val adapterFeedback = AdapterFeedback(requireActivity(), list)
        binding.rvFeedback.adapter = adapterFeedback

        val itemCount = list?.size ?: 0
        showNoData(binding.tvMessage, itemCount, "feedback")

        updateTextViewsVisibility(itemCount)
    }

    override fun onDestroyView() {
        feedbackList?.removeChangeListener(feedbackChangeListener)
        if (this::mRealm.isInitialized && !mRealm.isClosed) {
            mRealm.close()
        }
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        customProgressDialog?.dismiss()
        customProgressDialog = null
        super.onDestroy()
    }

    override fun onFeedbackSubmitted() {
        mRealm.executeTransactionAsync(
            Realm.Transaction { },
            Realm.Transaction.OnSuccess {
                val updatedList = if (userModel?.isManager() == true) {
                    mRealm.where(RealmFeedback::class.java)
                        .sort("openTime", Sort.DESCENDING)
                        .findAll()
                } else {
                    mRealm.where(RealmFeedback::class.java)
                        .equalTo("owner", userModel?.name)
                        .sort("openTime", Sort.DESCENDING)
                        .findAll()
                }
                updatedFeedbackList(updatedList)
            })
    }

    private fun updatedFeedbackList(updatedList: RealmResults<RealmFeedback>?) {
        activity?.runOnUiThread {
            val adapterFeedback = updatedList?.let { AdapterFeedback(requireActivity(), it) }
            binding.rvFeedback.adapter = adapterFeedback
            adapterFeedback?.notifyDataSetChanged()
            binding.rvFeedback.scrollToPosition(0)

            val itemCount = updatedList?.size ?: 0
            showNoData(binding.tvMessage, itemCount, "feedback")
            updateTextViewsVisibility(itemCount)
        }
    }

    private fun updateTextViewsVisibility(itemCount: Int) {
        val visibility = if (itemCount == 0) View.GONE else View.VISIBLE
        binding.tvTitle.visibility = visibility
        binding.tvType.visibility = visibility
        binding.tvPriority.visibility = visibility
        binding.tvStatus.visibility = visibility
        binding.tvOpenDate.visibility = visibility
    }

}
