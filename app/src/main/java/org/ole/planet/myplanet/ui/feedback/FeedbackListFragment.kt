package org.ole.planet.myplanet.ui.feedback

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.base.BaseRecyclerFragment.Companion.showNoData
import org.ole.planet.myplanet.callback.OnBaseRealtimeSyncListener
import org.ole.planet.myplanet.callback.OnFeedbackSubmittedListener
import org.ole.planet.myplanet.databinding.FragmentFeedbackListBinding
import org.ole.planet.myplanet.model.RealmFeedback
import org.ole.planet.myplanet.model.TableDataUpdate
import org.ole.planet.myplanet.services.sync.RealtimeSyncManager
import org.ole.planet.myplanet.utils.DialogUtils

@AndroidEntryPoint
class FeedbackListFragment : Fragment(), OnFeedbackSubmittedListener {
    private var _binding: FragmentFeedbackListBinding? = null
    private val binding get() = _binding!!
    private var customProgressDialog: DialogUtils.CustomProgressDialog? = null
    private val viewModel: FeedbackListViewModel by viewModels()

    private val syncManagerInstance = RealtimeSyncManager.getInstance()
    private lateinit var onRealtimeSyncListener: OnBaseRealtimeSyncListener
    private lateinit var feedbackAdapter: FeedbackAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.startFeedbackSync()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFeedbackListBinding.inflate(inflater, container, false)

        binding.fab.setOnClickListener {
            val feedbackFragment = FeedbackFragment()
            feedbackFragment.setOnFeedbackSubmittedListener(this)
            if (!childFragmentManager.isStateSaved) {
                feedbackFragment.show(childFragmentManager, "")
            }
        }

        setupRealtimeSync()

        return binding.root
    }

    private fun setupRealtimeSync() {
        onRealtimeSyncListener = object : OnBaseRealtimeSyncListener() {
            override fun onTableDataUpdated(update: TableDataUpdate) {
                if (update.table == "feedback" && update.shouldRefreshUI) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        refreshFeedbackListData()
                    }
                }
            }

            override fun onSyncStarted() {}
            override fun onSyncComplete() {}
            override fun onSyncFailed(msg: String?) {}
        }
        syncManagerInstance.addListener(onRealtimeSyncListener)
    }

    private fun refreshFeedbackListData() {
        viewModel.refreshFeedback()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        feedbackAdapter = FeedbackAdapter()
        binding.rvFeedback.layoutManager = LinearLayoutManager(activity)
        binding.rvFeedback.adapter = feedbackAdapter
        observeFeedbackList()
        observeSyncStatus()
    }

    private fun observeFeedbackList() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.feedbackList.collect { feedbackList ->
                    updatedFeedbackList(feedbackList)
                }
            }
        }
    }

    private fun observeSyncStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.syncStatus.collect { status ->
                    when (status) {
                        is FeedbackListViewModel.SyncStatus.Idle -> {
                            customProgressDialog?.dismiss()
                            customProgressDialog = null
                        }
                        is FeedbackListViewModel.SyncStatus.Syncing -> {
                            if (customProgressDialog == null) {
                                customProgressDialog = DialogUtils.CustomProgressDialog(requireContext())
                                customProgressDialog?.setText(getString(R.string.syncing_feedback))
                            }
                            if (customProgressDialog?.isShowing == false) {
                                customProgressDialog?.show()
                            }
                        }
                        is FeedbackListViewModel.SyncStatus.Success -> {
                            customProgressDialog?.dismiss()
                            customProgressDialog = null
                        }
                        is FeedbackListViewModel.SyncStatus.Error -> {
                            customProgressDialog?.dismiss()
                            customProgressDialog = null
                            Snackbar.make(binding.root, "Sync failed: ${status.message}", Snackbar.LENGTH_LONG)
                                .setAction("Retry") { viewModel.startFeedbackSync() }.show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        if (::onRealtimeSyncListener.isInitialized) {
            syncManagerInstance.removeListener(onRealtimeSyncListener)
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
        refreshFeedbackListData()
    }

    private fun updatedFeedbackList(updatedList: List<RealmFeedback>?) {
        if (_binding == null) return
        feedbackAdapter.submitList(updatedList)
        val itemCount = updatedList?.size ?: 0
        showNoData(binding.tvMessage, itemCount, "feedback")
        updateTextViewsVisibility(itemCount)
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
